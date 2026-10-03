package com.coursecompass.study;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coursecompass.llm.GeminiClient;
import com.coursecompass.llm.GeminiNotConfiguredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"gemini.api-key=", "ml.service.url=http://localhost:0"})
@AutoConfigureMockMvc
class StudyApiIntegrationTest {

    private static final String NOTES = "{\"content\": \"" + "Photosynthesis turns light energy into chemical energy. ".repeat(4) + "\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private GeminiClient gemini;

    @Test
    void generatesFlashcardsAndQuestionsForOwnCourse() throws Exception {
        String token = register();
        long courseId = course(token);
        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenReturn(IntStream.range(0, 10)
                .mapToObj(i -> "{\"question\": \"Q" + i + "\", \"answer\": \"A" + i + "\"}")
                .collect(Collectors.joining(",", "{\"flashcards\": [", "]}")));

        post("/api/materials/" + courseId + "/generate-flashcards", token, NOTES)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("AI-generated"))
                .andExpect(jsonPath("$.flashcards", hasSize(10)))
                .andExpect(jsonPath("$.flashcards[0].question").value("Q0"));

        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenReturn("""
                {"questions": [
                  {"question": "Q1", "options": ["a", "b", "c", "d"], "correctOptionIndex": 2, "explanation": "E1"},
                  {"question": "Q2", "options": ["a", "b", "c", "d"], "correctOptionIndex": 0, "explanation": "E2"},
                  {"question": "Q3", "options": ["a", "b", "c", "d"], "correctOptionIndex": 1, "explanation": "E3"}
                ]}
                """);
        post("/api/materials/" + courseId + "/generate-questions", token, NOTES)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("AI-generated"))
                .andExpect(jsonPath("$.questions", hasSize(3)))
                .andExpect(jsonPath("$.questions[0].correctOptionIndex").value(2));
    }

    @Test
    void materialRequestsAreValidatedAndScopedToOwner() throws Exception {
        String token = register();
        long courseId = course(token);

        post("/api/materials/" + courseId + "/generate-flashcards", token, "{\"content\": \"too short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("100 characters")));
        post("/api/materials/" + courseId + "/generate-flashcards", token, "{\"content\": \"" + "x".repeat(20001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
        post("/api/materials/" + courseId + "/generate-flashcards", token, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Add notes")));
        post("/api/materials/" + courseId + "/generate-flashcards", register(), NOTES).andExpect(status().isNotFound());
    }

    @Test
    void aiErrorsMapTo503And502() throws Exception {
        String token = register();
        long courseId = course(token);

        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenThrow(new GeminiNotConfiguredException());
        post("/api/materials/" + courseId + "/generate-questions", token, NOTES)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", containsString("GEMINI_API_KEY")));

        doReturn("{\"questions\": []}").when(gemini).generateJson(anyString(), anyString(), any(), anyInt());
        post("/api/materials/" + courseId + "/generate-questions", token, NOTES)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail", containsString("try again")));
    }

    @Test
    void studyPlanUsesDeadlinesAndWeakTopicsAndEnforcesDatesAndDailyLimit() throws Exception {
        String token = register();
        long courseId = course(token);
        long finalId = item(token, courseId, "{\"name\": \"Final Exam\", \"category\": \"EXAM\", \"weight\": 35,"
                + " \"dueDate\": \"2026-10-08T18:00:00Z\"}");
        item(token, courseId, "{\"name\": \"Lab 9\", \"category\": \"HOMEWORK\", \"weight\": 5,"
                + " \"dueDate\": \"2026-12-01T18:00:00Z\"}"); // beyond the 14-day horizon
        post("/api/items/" + finalId + "/rating", token, "{\"rating\": 3}").andExpect(status().isCreated());

        // Gemini returns wrong dates and an over-budget task; the service must correct both
        String day = "{\"date\": \"1999-01-01\", \"focus\": \"Review\", \"tasks\": ["
                + "{\"course\": \"MA 241\", \"task\": \"Practice problems\", \"minutes\": 60},"
                + "{\"course\": \"MA 241\", \"task\": \"Way too long\", \"minutes\": 200}]}";
        when(gemini.generateJson(anyString(), anyString(), any(), anyInt()))
                .thenReturn("{\"days\": [" + String.join(",", java.util.Collections.nCopies(7, day)) + "]}");

        post("/api/study-plan/generate", token, "{\"startDate\": \"2026-10-05\", \"hoursPerDay\": 2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("AI-generated"))
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].date").value("2026-10-05"))
                .andExpect(jsonPath("$.days[0].dayOfWeek").value("Monday"))
                .andExpect(jsonPath("$.days[6].date").value("2026-10-11"))
                .andExpect(jsonPath("$.days[0].tasks", hasSize(1)))
                .andExpect(jsonPath("$.deadlinesConsidered", hasSize(1)))
                .andExpect(jsonPath("$.deadlinesConsidered[0]", startsWith("MA 241 Final Exam")))
                .andExpect(jsonPath("$.weakTopicsConsidered", hasItem("MA 241 Final Exam: confidence 3/10")));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateJson(anyString(), prompt.capture(), any(), anyInt());
        org.assertj.core.api.Assertions.assertThat(prompt.getValue())
                .contains("2026-10-05, 2026-10-06")
                .contains("Daily study limit: 2 hours")
                .doesNotContain("Lab 9");
    }

    @Test
    void studyPlanWithNothingToPlanIsConflict() throws Exception {
        String token = register();
        course(token);

        post("/api/study-plan/generate", token, "{}").andExpect(status().isConflict());
    }

    private ResultActions post(String url, String token, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String register() throws Exception {
        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"s-" + UUID.randomUUID() + "@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long course(String token) throws Exception {
        return id(post("/api/courses", token, "{\"courseCode\": \"MA 241\", \"courseName\": \"Calculus II\"}"));
    }

    private long item(String token, long courseId, String body) throws Exception {
        return id(post("/api/courses/" + courseId + "/items", token, body));
    }

    private long id(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
