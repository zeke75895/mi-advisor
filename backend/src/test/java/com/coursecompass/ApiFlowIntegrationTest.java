package com.coursecompass;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coursecompass.ml.MlClient;
import com.coursecompass.ml.MlDtos.MlPredictRequest;
import com.coursecompass.ml.MlDtos.MlPredictResponse;
import com.coursecompass.ml.MlDtos.PathStep;
import com.coursecompass.ml.MlDtos.TopFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Inline properties beat environment variables, so a developer's real GEMINI_API_KEY or
// ML_SERVICE_URL can't leak into these tests
@SpringBootTest(properties = {"gemini.api-key=", "ml.service.url=http://localhost:0"})
@AutoConfigureMockMvc
class ApiFlowIntegrationTest {

    private static final String FEATURES_WITHOUT_MIDTERM = """
            {"attendanceRate": 0.72, "missedDeadlines": 4, "onTimeSubmissionRate": 0.65,
             "avgPracticeQuizScore": 58, "avgWeeklyStudyHours": 3, "flashcardsReviewed": 40,
             "avgDaysStartedBeforeExam": 1, "lateNightStudyPct": 0.45, "studySessionsLogged": 8}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private MlClient mlClient;

    @Test
    void fullFlowFromRegisterToRecommendation() throws Exception {
        when(mlClient.predict(any())).thenReturn(new MlPredictResponse(
                1,
                0.9078,
                List.of(new TopFeature("midterm_score", 0.5891)),
                List.of(new PathStep("midterm_score", 55, 64.5, "<=")),
                "The model flagged this course as at risk. It checked: midterm score (55) is at or below 64.5.",
                List.of("avg_sleep_hours"),
                "This is guidance, not a verdict.",
                "tree_v1"));

        String token = register(uniqueEmail());
        long courseId = createCourse(token);

        long midtermId = id(postJson("/api/courses/" + courseId + "/items", token, """
                {"name": "Midterm", "category": "EXAM", "weight": 25, "pointsPossible": 100,
                 "pointsEarned": 55, "isGraded": true, "dueDate": "2026-10-01T18:00:00Z"}
                """).andExpect(status().isCreated()));
        long finalId = id(postJson("/api/courses/" + courseId + "/items", token, """
                {"name": "Final Exam", "category": "EXAM", "weight": 35, "pointsPossible": 100,
                 "dueDate": "2026-12-10T18:00:00Z"}
                """).andExpect(status().isCreated()));
        postJson("/api/courses/" + courseId + "/items", token, """
                {"name": "Homework", "category": "HOMEWORK", "weight": 40, "pointsPossible": 100,
                 "pointsEarned": 70, "isGraded": true, "dueDate": "2026-09-15T18:00:00Z"}
                """).andExpect(status().isCreated());

        postJson("/api/items/" + finalId + "/rating", token, "{\"rating\": 3}").andExpect(status().isCreated());
        postJson("/api/items/" + midtermId + "/rating", token, "{\"rating\": 4}").andExpect(status().isCreated());

        mvc.perform(get("/api/courses/" + courseId + "/items").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[1].name").value("Midterm"))
                .andExpect(jsonPath("$[1].isGraded").value(true))
                .andExpect(jsonPath("$[2].latestRating").value(3));

        // Projection works before any prediction: (25*55 + 35*30 + 40*70) / 100 = 52.25
        mvc.perform(get("/api/courses/" + courseId + "/projection").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedFinal").value(52.3))
                .andExpect(jsonPath("$.remainingWeight").value(0.35))
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.ratedItems").value(2));

        // midtermScore omitted -> filled from the graded "Midterm" item
        postJson("/api/courses/" + courseId + "/predict", token, FEATURES_WITHOUT_MIDTERM)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.atRisk").value(true))
                .andExpect(jsonPath("$.riskProbability").value(0.9078))
                .andExpect(jsonPath("$.midtermSource").value("graded_item"))
                .andExpect(jsonPath("$.modelVersion").value("tree_v1"));
        ArgumentCaptor<MlPredictRequest> sent = ArgumentCaptor.forClass(MlPredictRequest.class);
        verify(mlClient).predict(sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().midtermScore()).isEqualTo(55.0);

        // (25*55 + 35*30 + 40*70) / 100 = 52.25 projected, 35% remaining.
        // Only 2 items rated, so distress doesn't count: 0.4*0.9078 + 0.25 = 0.61 -> lean_withdraw
        mvc.perform(get("/api/courses/" + courseId + "/recommendation").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").value("lean_withdraw"))
                .andExpect(jsonPath("$.withdrawScore").value(0.61))
                .andExpect(jsonPath("$.signals.projectedFinal").value(52.3))
                // No GEMINI_API_KEY in tests -> template bullets, no AI label
                .andExpect(jsonPath("$.explanation.source").value("template"))
                .andExpect(jsonPath("$.explanation.label").doesNotExist())
                .andExpect(jsonPath("$.explanation.reasoning", hasSize(3)))
                .andExpect(jsonPath("$.signals.remainingWeight").value(0.35))
                .andExpect(jsonPath("$.signals.lowRatings").value(2))
                .andExpect(jsonPath("$.advisorNote").value("Talk to your advisor before withdrawing."))
                .andExpect(jsonPath("$.disclaimer", containsString("not a verdict")));
    }

    @Test
    void modelInfoIsPassedThroughAndCached() throws Exception {
        when(mlClient.modelInfo()).thenReturn(json.readTree(
                "{\"model_version\": \"tree_v1\", \"metrics\": {\"recall_at_risk\": 0.7727}}"));
        String token = register(uniqueEmail());

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/model-info").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.metrics.recall_at_risk").value(0.7727));
        }
        verify(mlClient, org.mockito.Mockito.atMost(1)).modelInfo();
        mvc.perform(get("/api/model-info")).andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersCannotSeeEachOthersCourses() throws Exception {
        long courseId = createCourse(register(uniqueEmail()));
        String otherToken = register(uniqueEmail());

        mvc.perform(get("/api/courses/" + courseId + "/items").header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        postJson("/api/courses/" + courseId + "/predict", otherToken, FEATURES_WITHOUT_MIDTERM)
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/courses").header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void validationErrorsReturnFieldMessages() throws Exception {
        String token = register(uniqueEmail());
        long courseId = createCourse(token);

        postJson("/api/courses/" + courseId + "/items", token, """
                {"name": "", "category": "EXAM", "weight": 150, "isGraded": true}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.weight").exists())
                .andExpect(jsonPath("$.errors.scoreConsistent").exists());

        long itemId = id(postJson("/api/courses/" + courseId + "/items", token, """
                {"name": "Quiz 1", "category": "QUIZ", "weight": 5}
                """));
        postJson("/api/items/" + itemId + "/rating", token, "{\"rating\": 11}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
    }

    @Test
    void predictWithoutMidtermOrGradedMidtermIsBadRequest() throws Exception {
        String token = register(uniqueEmail());
        long courseId = createCourse(token);

        postJson("/api/courses/" + courseId + "/predict", token, FEATURES_WITHOUT_MIDTERM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("midtermScore")));
        verify(mlClient, never()).predict(any());
    }

    @Test
    void projectionOfEmptyCourseHasNullGrades() throws Exception {
        String token = register(uniqueEmail());
        long courseId = createCourse(token);

        mvc.perform(get("/api/courses/" + courseId + "/projection").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectedFinal").doesNotExist())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void recommendationBeforePredictIsConflict() throws Exception {
        String token = register(uniqueEmail());
        long courseId = createCourse(token);

        mvc.perform(get("/api/courses/" + courseId + "/recommendation").header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateRegistrationAndBadLogin() throws Exception {
        String email = uniqueEmail();
        register(email);

        postJson("/api/auth/register", null, credentials(email)).andExpect(status().isConflict());
        postJson("/api/auth/login", null, "{\"email\": \"" + email + "\", \"password\": \"wrong-password\"}")
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/login", null, credentials(email.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    private String register(String email) throws Exception {
        String body = postJson("/api/auth/register", null, credentials(email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long createCourse(String token) throws Exception {
        return id(postJson("/api/courses", token, """
                {"courseCode": "MA 241", "courseName": "Calculus II", "creditHours": 4, "semester": "Fall 2026"}
                """).andExpect(status().isCreated()));
    }

    private ResultActions postJson(String url, String token, String body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mvc.perform(request);
    }

    private long id(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private static String credentials(String email) {
        return "{\"email\": \"" + email + "\", \"password\": \"correct-horse-battery\"}";
    }

    private static String uniqueEmail() {
        return "student-" + UUID.randomUUID() + "@example.com";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
