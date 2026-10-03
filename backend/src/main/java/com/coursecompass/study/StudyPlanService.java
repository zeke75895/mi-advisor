package com.coursecompass.study;

import com.coursecompass.common.BadRequestException;
import com.coursecompass.common.ConflictException;
import com.coursecompass.course.Course;
import com.coursecompass.course.CourseRepository;
import com.coursecompass.item.GradedItem;
import com.coursecompass.item.GradedItemRepository;
import com.coursecompass.llm.AiRateLimiter;
import com.coursecompass.llm.GeminiClient;
import com.coursecompass.llm.GeminiException;
import com.coursecompass.llm.PromptTemplate;
import com.coursecompass.rating.SelfRatingService;
import com.coursecompass.study.StudyDtos.StudyDay;
import com.coursecompass.study.StudyDtos.StudyPlanRequest;
import com.coursecompass.study.StudyDtos.StudyPlanResponse;
import com.coursecompass.study.StudyDtos.StudyTask;
import com.coursecompass.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Builds a 7-day study plan from upcoming deadlines (next 14 days, ungraded) and weak topics (latest
 * self-rating of 4 or lower), then asks Gemini to schedule them. Dates are worked out in the student's
 * time zone, each generation counts against their AI cap, and the latest plan is stored per user.
 */
@Service
public class StudyPlanService {

    static final int PLAN_DAYS = 7;
    static final int DEADLINE_HORIZON_DAYS = 14;
    static final int WEAK_RATING = 4;
    static final int DEFAULT_HOURS_PER_DAY = 2;
    private static final int MAX_OUTPUT_TOKENS = 8000;
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US);

    private static final Map<String, Object> PLAN_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("days", Map.of(
                    "type", "ARRAY",
                    "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "date", Map.of("type", "STRING"),
                                    "focus", Map.of("type", "STRING"),
                                    "tasks", Map.of(
                                            "type", "ARRAY",
                                            "items", Map.of(
                                                    "type", "OBJECT",
                                                    "properties", Map.of(
                                                            "course", Map.of("type", "STRING"),
                                                            "task", Map.of("type", "STRING"),
                                                            "minutes", Map.of("type", "INTEGER")),
                                                    "required", List.of("course", "task", "minutes")))),
                            "required", List.of("date", "focus", "tasks")))),
            "required", List.of("days"));

    private final CourseRepository courses;
    private final GradedItemRepository items;
    private final SelfRatingService ratingService;
    private final GeminiClient gemini;
    private final ObjectMapper json;
    private final Clock clock;
    private final AiRateLimiter limiter;
    private final StudyPlanRecordRepository plans;
    private final UserRepository users;
    private final PromptTemplate systemPrompt = PromptTemplate.load("study-plan.system.txt");
    private final PromptTemplate userPrompt = PromptTemplate.load("study-plan.user.txt");

    public StudyPlanService(
            CourseRepository courses,
            GradedItemRepository items,
            SelfRatingService ratingService,
            GeminiClient gemini,
            ObjectMapper json,
            Clock clock,
            AiRateLimiter limiter,
            StudyPlanRecordRepository plans,
            UserRepository users) {
        this.limiter = limiter;
        this.plans = plans;
        this.users = users;
        this.courses = courses;
        this.items = items;
        this.ratingService = ratingService;
        this.gemini = gemini;
        this.json = json;
        this.clock = clock;
    }

    public StudyPlanResponse generate(Long userId, StudyPlanRequest request) {
        ZoneId zone = zone(request.timeZone());
        LocalDate start = request.startDate() != null ? request.startDate() : LocalDate.now(clock.withZone(zone));
        int hoursPerDay = request.hoursPerDay() != null ? request.hoursPerDay() : DEFAULT_HOURS_PER_DAY;
        List<LocalDate> dates = start.datesUntil(start.plusDays(PLAN_DAYS)).toList();

        List<Course> selected = courses.findByUserIdOrderByIdAsc(userId).stream()
                .filter(c -> request.courseIds() == null || request.courseIds().isEmpty()
                        || request.courseIds().contains(c.getId()))
                .toList();

        List<String> deadlines = new ArrayList<>();
        List<String> weakTopics = new ArrayList<>();
        for (Course course : selected) {
            Map<Long, Integer> ratings = ratingService.latestRatingsForCourse(course.getId());
            for (GradedItem item : items.findByCourseIdOrderByDueDateAscIdAsc(course.getId())) {
                String label = course.getCourseCode() + " " + item.getName();
                if (!item.isGraded() && item.getDueDate() != null) {
                    LocalDate due = item.getDueDate().atZoneSameInstant(zone).toLocalDate();
                    if (!due.isBefore(start) && due.isBefore(start.plusDays(DEADLINE_HORIZON_DAYS))) {
                        deadlines.add(label + " (" + item.getCategory().name().toLowerCase(Locale.ROOT) + ", "
                                + fmt(item.getWeight()) + "% of grade) due " + due.format(DUE));
                    }
                }
                Integer rating = ratings.get(item.getId());
                if (rating != null && rating <= WEAK_RATING) {
                    weakTopics.add(label + ": confidence " + rating + "/10");
                }
            }
        }
        if (deadlines.isEmpty() && weakTopics.isEmpty()) {
            throw new ConflictException("Nothing to plan yet: add due dates to upcoming items in the next "
                    + DEADLINE_HORIZON_DAYS + " days, or rate items you feel unsure about.");
        }

        limiter.acquire(userId);
        String prompt = userPrompt.render(Map.of(
                "dates", dates.stream().map(LocalDate::toString).collect(Collectors.joining(", ")),
                "hours_per_day", Integer.toString(hoursPerDay),
                "deadlines", bullets(deadlines),
                "weak_topics", bullets(weakTopics),
                "courses", selected.stream()
                        .map(c -> c.getCourseCode() + " " + c.getCourseName())
                        .collect(Collectors.joining("; "))));
        List<StudyDay> days = parse(gemini.generateJson(systemPrompt.text(), prompt, PLAN_SCHEMA, MAX_OUTPUT_TOKENS),
                dates, hoursPerDay);

        StudyPlanResponse response = new StudyPlanResponse(
                StudyDtos.AI_LABEL,
                StudyDtos.PLAN_DISCLAIMER,
                start,
                hoursPerDay,
                List.copyOf(deadlines),
                List.copyOf(weakTopics),
                days,
                zone.getId(),
                Instant.now(clock));
        try {
            plans.save(new StudyPlanRecord(users.getReferenceById(userId), json.writeValueAsString(response)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return response;
    }

    public Optional<StudyPlanResponse> latest(Long userId) {
        return plans.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId).map(p -> {
            try {
                return json.readValue(p.getPlanJson(), StudyPlanResponse.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Stored study plan is unreadable", e);
            }
        });
    }

    private ZoneId zone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return clock.getZone();
        }
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone: " + timeZone);
        }
    }

    /** Keeps the plan on the 7 dates we asked for and within the daily limit, whatever Gemini returns. */
    private List<StudyDay> parse(String raw, List<LocalDate> dates, int hoursPerDay) {
        JsonNode root;
        try {
            root = json.readTree(raw);
        } catch (Exception e) {
            throw new GeminiException("Gemini returned invalid JSON", e);
        }
        JsonNode days = root.path("days");
        if (days.size() < PLAN_DAYS) {
            throw new GeminiException("Gemini planned " + days.size() + " days instead of " + PLAN_DAYS);
        }
        List<StudyDay> plan = new ArrayList<>();
        for (int i = 0; i < PLAN_DAYS; i++) {
            JsonNode day = days.get(i);
            int budget = hoursPerDay * 60;
            List<StudyTask> tasks = new ArrayList<>();
            for (JsonNode t : day.path("tasks")) {
                String task = t.path("task").asText("").strip();
                int minutes = Math.max(5, Math.min(240, t.path("minutes").asInt(30)));
                if (task.isEmpty() || minutes > budget) {
                    continue;
                }
                budget -= minutes;
                tasks.add(new StudyTask(t.path("course").asText("").strip(), task, minutes));
            }
            LocalDate date = dates.get(i);
            plan.add(new StudyDay(
                    date,
                    date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US),
                    day.path("focus").asText("").strip(),
                    tasks));
        }
        return plan;
    }

    private static String bullets(List<String> lines) {
        return lines.isEmpty() ? "- none" : lines.stream().map(l -> "- " + l).collect(Collectors.joining("\n"));
    }

    private static String fmt(double weight) {
        return weight == Math.rint(weight) ? Long.toString((long) weight) : Double.toString(weight);
    }
}
