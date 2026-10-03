package com.coursecompass.study;

import com.coursecompass.common.BadRequestException;
import com.coursecompass.course.Course;
import com.coursecompass.course.CourseService;
import com.coursecompass.llm.AiRateLimiter;
import com.coursecompass.llm.GeminiClient;
import com.coursecompass.llm.GeminiException;
import com.coursecompass.llm.PromptTemplate;
import com.coursecompass.notes.NotesService;
import com.coursecompass.study.GeneratedMaterial.Kind;
import com.coursecompass.study.StudyDtos.Flashcard;
import com.coursecompass.study.StudyDtos.FlashcardResponse;
import com.coursecompass.study.StudyDtos.Question;
import com.coursecompass.study.StudyDtos.QuestionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Generates flashcards and practice questions with Gemini, from text in the request or the course's saved
 * notes. Each generation counts against the user's AI cap and the latest set is stored per course.
 */
@Service
public class StudyMaterialService {

    static final int FLASHCARD_COUNT = 10;
    static final int QUESTION_COUNT = 5;
    static final int MIN_FLASHCARDS = 5;
    static final int MIN_QUESTIONS = 3;
    static final int MIN_MATERIAL_CHARS = 100;
    private static final int MAX_OUTPUT_TOKENS = 6000;

    private static final Map<String, Object> FLASHCARD_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("flashcards", Map.of(
                    "type", "ARRAY",
                    "minItems", FLASHCARD_COUNT,
                    "maxItems", FLASHCARD_COUNT,
                    "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "question", Map.of("type", "STRING"),
                                    "answer", Map.of("type", "STRING")),
                            "required", List.of("question", "answer")))),
            "required", List.of("flashcards"));

    private static final Map<String, Object> QUESTION_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("questions", Map.of(
                    "type", "ARRAY",
                    "minItems", QUESTION_COUNT,
                    "maxItems", QUESTION_COUNT,
                    "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "question", Map.of("type", "STRING"),
                                    "options", Map.of(
                                            "type", "ARRAY",
                                            "minItems", 4,
                                            "maxItems", 4,
                                            "items", Map.of("type", "STRING")),
                                    "correctOptionIndex", Map.of("type", "INTEGER"),
                                    "explanation", Map.of("type", "STRING")),
                            "required", List.of("question", "options", "correctOptionIndex", "explanation")))),
            "required", List.of("questions"));

    private final CourseService courseService;
    private final GeminiClient gemini;
    private final AiRateLimiter limiter;
    private final NotesService notes;
    private final GeneratedMaterialRepository saved;
    private final ObjectMapper json;
    private final Clock clock;
    private final PromptTemplate systemPrompt = PromptTemplate.load("study-material.system.txt");
    private final PromptTemplate flashcardPrompt = PromptTemplate.load("flashcards.user.txt");
    private final PromptTemplate questionPrompt = PromptTemplate.load("questions.user.txt");

    public StudyMaterialService(
            CourseService courseService,
            GeminiClient gemini,
            AiRateLimiter limiter,
            NotesService notes,
            GeneratedMaterialRepository saved,
            ObjectMapper json,
            Clock clock) {
        this.courseService = courseService;
        this.gemini = gemini;
        this.limiter = limiter;
        this.notes = notes;
        this.saved = saved;
        this.json = json;
        this.clock = clock;
    }

    public FlashcardResponse flashcards(Long userId, Long courseId, String content) {
        Course course = courseService.getOwned(userId, courseId);
        String material = material(courseId, content);
        limiter.acquire(userId);
        JsonNode root = call(flashcardPrompt, course, material, FLASHCARD_SCHEMA);

        List<Flashcard> cards = new ArrayList<>();
        for (JsonNode card : root.path("flashcards")) {
            String q = card.path("question").asText("").strip();
            String a = card.path("answer").asText("").strip();
            if (!q.isEmpty() && !a.isEmpty() && cards.size() < FLASHCARD_COUNT) {
                cards.add(new Flashcard(q, a));
            }
        }
        if (cards.size() < MIN_FLASHCARDS) {
            throw new GeminiException("Gemini returned only " + cards.size() + " usable flashcards");
        }
        FlashcardResponse response = new FlashcardResponse(
                courseId, StudyDtos.AI_LABEL, StudyDtos.MATERIAL_DISCLAIMER, cards, Instant.now(clock));
        saved.save(new GeneratedMaterial(course, Kind.FLASHCARDS, toJson(response)));
        return response;
    }

    public Optional<FlashcardResponse> latestFlashcards(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);
        return saved.findFirstByCourseIdAndKindOrderByCreatedAtDescIdDesc(courseId, Kind.FLASHCARDS)
                .map(m -> fromJson(m.getContentJson(), FlashcardResponse.class));
    }

    public QuestionResponse questions(Long userId, Long courseId, String content) {
        Course course = courseService.getOwned(userId, courseId);
        String material = material(courseId, content);
        limiter.acquire(userId);
        JsonNode root = call(questionPrompt, course, material, QUESTION_SCHEMA);

        List<Question> questions = new ArrayList<>();
        for (JsonNode q : root.path("questions")) {
            String text = q.path("question").asText("").strip();
            String explanation = q.path("explanation").asText("").strip();
            List<String> options = new ArrayList<>();
            q.path("options").forEach(o -> options.add(o.asText("").strip()));
            int correct = q.path("correctOptionIndex").asInt(-1);
            boolean valid = !text.isEmpty()
                    && !explanation.isEmpty()
                    && options.size() == 4
                    && options.stream().noneMatch(String::isEmpty)
                    && options.stream().distinct().count() == 4
                    && correct >= 0
                    && correct < 4;
            if (valid && questions.size() < QUESTION_COUNT) {
                questions.add(new Question(text, List.copyOf(options), correct, explanation));
            }
        }
        if (questions.size() < MIN_QUESTIONS) {
            throw new GeminiException("Gemini returned only " + questions.size() + " valid questions");
        }
        QuestionResponse response = new QuestionResponse(
                courseId, StudyDtos.AI_LABEL, StudyDtos.MATERIAL_DISCLAIMER, questions, Instant.now(clock));
        saved.save(new GeneratedMaterial(course, Kind.QUESTIONS, toJson(response)));
        return response;
    }

    public Optional<QuestionResponse> latestQuestions(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);
        return saved.findFirstByCourseIdAndKindOrderByCreatedAtDescIdDesc(courseId, Kind.QUESTIONS)
                .map(m -> fromJson(m.getContentJson(), QuestionResponse.class));
    }

    /** Request text if given, otherwise the course's saved notes; checked before any AI quota is used. */
    private String material(Long courseId, String content) {
        String text = content != null && !content.isBlank()
                ? content.strip()
                : notes.textForAi(courseId).orElseThrow(() -> new BadRequestException(
                        "Add notes for this course first: paste them or upload a PDF."));
        if (text.length() < MIN_MATERIAL_CHARS) {
            throw new BadRequestException("Notes need at least " + MIN_MATERIAL_CHARS + " characters to make study material.");
        }
        return text;
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T fromJson(String raw, Class<T> type) {
        try {
            return json.readValue(raw, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored study material is unreadable", e);
        }
    }

    private JsonNode call(PromptTemplate template, Course course, String content, Map<String, Object> schema) {
        String user = template.render(Map.of(
                "course", course.getCourseCode() + " " + course.getCourseName(),
                // Keep the student's text from closing our delimiter early
                "material", content.replace("</material>", "")));
        String raw = gemini.generateJson(systemPrompt.text(), user, schema, MAX_OUTPUT_TOKENS);
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            throw new GeminiException("Gemini returned invalid JSON", e);
        }
    }
}
