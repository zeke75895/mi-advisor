package com.miadvisor.briefing;

import com.miadvisor.briefing.BriefingDtos.BriefingResponse;
import com.miadvisor.common.BadRequestException;
import com.miadvisor.course.Course;
import com.miadvisor.course.CourseService;
import com.miadvisor.llm.AiRateLimiter;
import com.miadvisor.llm.ExplanationService;
import com.miadvisor.llm.GeminiClient;
import com.miadvisor.llm.GeminiException;
import com.miadvisor.llm.PromptTemplate;
import com.miadvisor.recommendation.FusionService;
import com.miadvisor.recommendation.RecommendationDtos.RecommendationResponse;
import com.miadvisor.recommendation.RecommendationDtos.Signals;
import com.miadvisor.recommendation.RecommendationService;
import com.miadvisor.voice.ElevenLabsClient;
import com.miadvisor.voice.VoiceNotConfiguredException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A ~30-second spoken briefing on a course: Gemini writes the script from the recommendation's signals,
 * ElevenLabs reads it aloud.
 *
 * <p>Only the course name and aggregate signals go to Gemini and ElevenLabs (no email or other personal
 * data). Gemini's script is checked like the recommendation rewrite: it may not introduce any number
 * that isn't in the prompt, otherwise a template script is used. The disclaimer is always appended by
 * code, so it can't be dropped or reworded. Nothing is stored; finished briefings are cached in memory so
 * replaying one doesn't spend Gemini or ElevenLabs quota again.
 */
@Service
public class BriefingService {

    static final String CLOSING = "Remember, this is guidance, not a verdict.";
    static final String ADVISOR_CLOSING = "Please talk to your advisor before you decide to withdraw.";
    static final String DISCLAIMER = "This is guidance, not a verdict, and it can be wrong.";

    private static final Logger log = LoggerFactory.getLogger(BriefingService.class);
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final int MAX_SCRIPT_CHARS = 900;
    private static final int MAX_CACHE_ENTRIES = 30;
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("script", Map.of("type", "STRING")),
            "required", List.of("script"));

    private final CourseService courseService;
    private final RecommendationService recommendationService;
    private final GeminiClient gemini;
    private final ElevenLabsClient voice;
    private final AiRateLimiter limiter;
    private final ObjectMapper json;
    private final PromptTemplate systemPrompt = PromptTemplate.load("briefing.system.txt");
    private final PromptTemplate userPrompt = PromptTemplate.load("briefing.user.txt");
    private final Map<String, BriefingResponse> cache = new ConcurrentHashMap<>();

    public BriefingService(
            CourseService courseService,
            RecommendationService recommendationService,
            GeminiClient gemini,
            ElevenLabsClient voice,
            AiRateLimiter limiter,
            ObjectMapper json) {
        this.courseService = courseService;
        this.recommendationService = recommendationService;
        this.gemini = gemini;
        this.voice = voice;
        this.limiter = limiter;
        this.json = json;
    }

    public boolean isAvailable() {
        return voice.isEnabled();
    }

    public BriefingResponse brief(Long userId, Long courseId) {
        if (!voice.isEnabled()) {
            throw new VoiceNotConfiguredException();
        }
        Course course = courseService.getOwned(userId, courseId);
        RecommendationResponse rec = recommendationService.recommend(userId, courseId)
                .orElseThrow(() -> new BadRequestException(
                        "Run a risk check and grade or rate at least one item first, then try again."));

        String prompt = renderUserPrompt(course.getCourseName(), rec);
        BriefingResponse cached = cache.get(prompt);
        if (cached != null) {
            return cached;
        }
        limiter.acquire(userId);

        Script script = writeScript(prompt, course.getCourseName(), rec);
        String spoken = script.text() + " " + CLOSING + (rec.advisorNote() != null ? " " + ADVISOR_CLOSING : "");
        byte[] audio = voice.speak(spoken);

        BriefingResponse response = new BriefingResponse(
                courseId,
                spoken,
                script.source(),
                ExplanationService.AI_LABEL,
                (script.source().equals(ExplanationService.SOURCE_GEMINI)
                        ? "Script written by Gemini, voice by ElevenLabs. "
                        : "Voice by ElevenLabs. ") + DISCLAIMER,
                "audio/mpeg",
                Base64.getEncoder().encodeToString(audio));
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(prompt, response);
        return response;
    }

    private record Script(String text, String source) {}

    private Script writeScript(String prompt, String courseName, RecommendationResponse rec) {
        Script fallback = new Script(templateScript(courseName, rec), ExplanationService.SOURCE_TEMPLATE);
        if (!gemini.isEnabled()) {
            return fallback;
        }
        try {
            String raw = gemini.generateJson(systemPrompt.text(), prompt, RESPONSE_SCHEMA, 1200);
            return new Script(parseAndCheck(raw, prompt), ExplanationService.SOURCE_GEMINI);
        } catch (RuntimeException e) {
            log.warn("Using template briefing: {}", e.getMessage());
            return fallback;
        }
    }

    String parseAndCheck(String raw, String prompt) {
        JsonNode node;
        try {
            node = json.readTree(raw);
        } catch (Exception e) {
            throw new GeminiException("Gemini returned invalid JSON", e);
        }
        // Strip markdown the voice would otherwise read out
        String script = node.path("script").asText("").replaceAll("[*_#`>\\[\\]]", "").replaceAll("\\s+", " ").strip();
        if (script.length() < 40 || script.length() > MAX_SCRIPT_CHARS) {
            throw new GeminiException("Gemini script has the wrong length: " + script.length());
        }
        Set<String> invented = numbers(script);
        invented.removeAll(numbers(prompt));
        if (!invented.isEmpty()) {
            throw new GeminiException("Gemini introduced numbers not in the input: " + invented);
        }
        return script;
    }

    String renderUserPrompt(String courseName, RecommendationResponse rec) {
        Signals s = rec.signals();
        List<String> reasons = rec.explanation() != null && rec.explanation().reasoning() != null
                ? rec.explanation().reasoning()
                : rec.reasoning();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("course_name", courseName);
        values.put("recommendation", rec.recommendation().json());
        values.put("headline", rec.headline());
        values.put("risk_level", FusionService.riskLevel(s.modelRisk()));
        values.put("projected_final", Long.toString(Math.round(s.projectedFinal())));
        values.put("current_grade", s.currentGrade() == null ? "nothing graded yet" : Long.toString(Math.round(s.currentGrade())));
        values.put("remaining_pct", Long.toString(Math.round(s.remainingWeight() * 100)));
        values.put("required_for_c", requiredForC(s));
        values.put("reasoning_bullets", bullets(reasons));
        values.put("actions", bullets(rec.actions()));
        return userPrompt.render(values);
    }

    /** Used when Gemini is off or its script fails the checks. Same facts, fixed wording. */
    static String templateScript(String courseName, RecommendationResponse rec) {
        Signals s = rec.signals();
        StringBuilder out = new StringBuilder("Here's your briefing for ").append(courseName).append(". ");
        out.append(sentence(rec.headline())).append(' ');
        out.append("Our prediction puts this course at ").append(FusionService.riskLevel(s.modelRisk()))
                .append(" risk, and your projected final grade is ").append(Math.round(s.projectedFinal())).append(". ");
        out.append(requiredSentence(s)).append(' ');
        if (!rec.actions().isEmpty()) {
            out.append("A good next step: ").append(sentence(lowerFirst(rec.actions().get(0))));
        }
        return out.toString().strip();
    }

    static String requiredForC(Signals s) {
        if (s.requiredScore() == null) {
            return "nothing is left to grade";
        }
        if (s.requiredScore() <= 0) {
            return "a C is already secured";
        }
        if (s.requiredScore() > 100) {
            return "a C is no longer reachable (best possible final grade: " + (long) Math.floor(s.maxPossible()) + ")";
        }
        return (long) Math.ceil(s.requiredScore()) + "% average on the remaining work";
    }

    private static String requiredSentence(Signals s) {
        if (s.requiredScore() == null) {
            return "Everything in this course has been graded.";
        }
        if (s.requiredScore() <= 0) {
            return "You've already secured a C.";
        }
        if (s.requiredScore() > 100) {
            return "A C is no longer within reach; the best possible final grade is " + (long) Math.floor(s.maxPossible()) + ".";
        }
        return "To finish with a C, you'd need about " + (long) Math.ceil(s.requiredScore())
                + " percent on the remaining work.";
    }

    private static String sentence(String text) {
        String t = text.strip();
        return t.isEmpty() || ".!?".indexOf(t.charAt(t.length() - 1)) >= 0 ? t : t + ".";
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static Set<String> numbers(String text) {
        Set<String> found = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    private static String bullets(List<String> lines) {
        return lines.stream().map(l -> "- " + l).collect(Collectors.joining("\n"));
    }
}
