package com.miadvisor.notes;

import com.miadvisor.common.BadRequestException;
import com.miadvisor.course.Course;
import com.miadvisor.course.CourseService;
import com.miadvisor.item.GradeCategory;
import com.miadvisor.llm.AiRateLimiter;
import com.miadvisor.llm.GeminiClient;
import com.miadvisor.llm.GeminiException;
import com.miadvisor.llm.PromptTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Suggests graded items (name, category, weight, due date) from a course's saved syllabus text. Gemini
 * reads it when available; otherwise, or if Gemini fails, a line-by-line pattern match finds entries
 * like "Midterm exam 25%". The student reviews and edits suggestions before anything is saved.
 */
@Service
public class SyllabusItemService {

    private static final Logger log = LoggerFactory.getLogger(SyllabusItemService.class);
    static final int MAX_ITEMS = 30;
    private static final int MAX_OUTPUT_TOKENS = 3000;

    // "Midterm exam 25%", "- Homework sets 1-6: 20%", "Final exam (cumulative) .... 25 %"
    private static final Pattern WEIGHTED_LINE =
            Pattern.compile("^\\s*(?:[-•*]\\s*)?([A-Za-z][^%\\n]{1,80}?)[\\s:.\\-–—]+(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final List<String> NOT_A_GRADED_ITEM = List.of(
            "late", "penalt", "per day", "lose", "deduct", "extra credit", "total", "bonus", "drop", "curve", "attendance policy");

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("items", Map.of(
                    "type", "ARRAY",
                    "maxItems", MAX_ITEMS,
                    "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "name", Map.of("type", "STRING"),
                                    "category", Map.of(
                                            "type", "STRING",
                                            "format", "enum",
                                            "enum", Arrays.stream(GradeCategory.values()).map(Enum::name).toList()),
                                    "weight", Map.of("type", "NUMBER"),
                                    "dueDate", Map.of("type", "STRING")),
                            "required", List.of("name", "category", "weight", "dueDate")))),
            "required", List.of("items"));

    private final CourseService courseService;
    private final NotesService notes;
    private final GeminiClient gemini;
    private final AiRateLimiter limiter;
    private final ObjectMapper json;
    private final Clock clock;
    private final PromptTemplate systemPrompt = PromptTemplate.load("syllabus-items.system.txt");
    private final PromptTemplate userPrompt = PromptTemplate.load("syllabus-items.user.txt");

    public SyllabusItemService(
            CourseService courseService,
            NotesService notes,
            GeminiClient gemini,
            AiRateLimiter limiter,
            ObjectMapper json,
            Clock clock) {
        this.courseService = courseService;
        this.notes = notes;
        this.gemini = gemini;
        this.limiter = limiter;
        this.json = json;
        this.clock = clock;
    }

    public record ItemSuggestion(String name, GradeCategory category, double weight, LocalDate dueDate) {}

    /**
     * @param source "gemini" or "pattern"
     * @param label "AI-generated" when Gemini produced the list
     */
    public record ExtractionResponse(
            Long courseId, String source, String label, List<ItemSuggestion> items, double totalWeight, List<String> warnings) {}

    public ExtractionResponse extract(Long userId, Long courseId) {
        Course course = courseService.getOwned(userId, courseId);
        String text = notes.textForAi(courseId).orElseThrow(() ->
                new BadRequestException("Upload the syllabus PDF (or paste it as notes) first."));

        List<String> warnings = new ArrayList<>();
        List<ItemSuggestion> items = null;
        String source = "pattern";
        if (gemini.isEnabled()) {
            limiter.acquire(userId); // RateLimitException propagates as 429
            try {
                items = fromGemini(course, text);
                source = "gemini";
            } catch (GeminiException e) {
                log.warn("Syllabus extraction fell back to pattern matching: {}", e.getMessage());
                warnings.add("The AI reader wasn't available, so items were found by simple pattern matching.");
            }
        }
        if (items == null) {
            items = fromPatterns(text);
        }

        double total = Math.round(items.stream().mapToDouble(ItemSuggestion::weight).sum() * 10) / 10.0;
        if (items.isEmpty()) {
            warnings.add("No weighted grade items were found. Add them by hand on the course page.");
        } else if (Math.abs(total - 100) > 0.5) {
            warnings.add("The weights add up to " + fmt(total) + "%, not 100%. Check them against your syllabus.");
        }
        if (items.stream().anyMatch(i -> i.dueDate() == null)) {
            warnings.add("Some items have no exact date in the syllabus. Add due dates so they show in your study plan.");
        }
        return new ExtractionResponse(courseId, source, "gemini".equals(source) ? "AI-generated" : null, items, total, warnings);
    }

    private List<ItemSuggestion> fromGemini(Course course, String text) {
        String prompt = userPrompt.render(Map.of(
                "course", course.getCourseCode() + " " + course.getCourseName(),
                "syllabus", text.replace("</syllabus>", "")));
        JsonNode root;
        try {
            root = json.readTree(gemini.generateJson(systemPrompt.text(), prompt, SCHEMA, MAX_OUTPUT_TOKENS));
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiException("Gemini returned invalid JSON", e);
        }
        Map<String, ItemSuggestion> byName = new LinkedHashMap<>();
        for (JsonNode n : root.path("items")) {
            String name = clean(n.path("name").asText(""));
            double weight = n.path("weight").asDouble(-1);
            if (name.isEmpty() || weight <= 0 || weight > 100) {
                continue;
            }
            byName.putIfAbsent(name.toLowerCase(Locale.ROOT),
                    new ItemSuggestion(name, category(n.path("category").asText(""), name), round(weight), date(n.path("dueDate").asText(""))));
        }
        return byName.values().stream().limit(MAX_ITEMS).toList();
    }

    List<ItemSuggestion> fromPatterns(String text) {
        Map<String, ItemSuggestion> byName = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            Matcher m = WEIGHTED_LINE.matcher(line);
            if (!m.find()) {
                continue;
            }
            String name = clean(m.group(1));
            String lower = line.toLowerCase(Locale.ROOT);
            double weight = Double.parseDouble(m.group(2));
            if (name.length() < 3 || weight <= 0 || weight > 100 || NOT_A_GRADED_ITEM.stream().anyMatch(lower::contains)) {
                continue;
            }
            byName.putIfAbsent(name.toLowerCase(Locale.ROOT), new ItemSuggestion(name, guessCategory(name), round(weight), null));
        }
        return byName.values().stream().limit(MAX_ITEMS).toList();
    }

    private static GradeCategory category(String raw, String name) {
        try {
            return GradeCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return guessCategory(name);
        }
    }

    static GradeCategory guessCategory(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.matches(".*\\b(exam|midterm|final|test)s?\\b.*")) {
            return GradeCategory.EXAM;
        }
        if (n.matches(".*\\bquiz(zes)?\\b.*")) {
            return GradeCategory.QUIZ;
        }
        if (n.matches(".*\\b(homework|hw|problem sets?|assignments?|exercises?)\\b.*")) {
            return GradeCategory.HOMEWORK;
        }
        return GradeCategory.PROJECT;
    }

    /** Accepts only real YYYY-MM-DD dates within a year of today; anything else means "no date". */
    private LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(raw.trim());
            LocalDate today = LocalDate.now(clock);
            return d.isBefore(today.minusYears(1)) || d.isAfter(today.plusYears(1)) ? null : d;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String clean(String name) {
        String n = name.replaceAll("\\s+", " ").strip().replaceAll("[\\s:.\\-–—(]+$", "");
        return n.length() > 200 ? n.substring(0, 200) : n;
    }

    private static double round(double weight) {
        return Math.round(weight * 10) / 10.0;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
