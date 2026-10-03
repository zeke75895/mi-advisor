package com.coursecompass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coursecompass.llm.GeminiClient;
import com.coursecompass.ml.MlClient;
import com.coursecompass.ml.MlDtos.MlPredictResponse;
import com.coursecompass.ml.MlDtos.PathStep;
import com.coursecompass.ml.MlDtos.TopFeature;
import com.coursecompass.notes.TestPdfs;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Everything a student generates or enters is stored server-side and reloads on any device. */
@SpringBootTest(properties = {"gemini.api-key=", "ml.service.url=http://localhost:0"})
@AutoConfigureMockMvc
class PersistenceIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private GeminiClient gemini;

    @MockitoBean
    private MlClient mlClient;

    @Test
    void uploadedPdfBecomesNotesThatFeedFlashcardsWhichAreSaved() throws Exception {
        String token = register();
        long courseId = course(token);
        call(get("/api/courses/" + courseId + "/notes"), token).andExpect(status().isNoContent());
        call(get("/api/materials/" + courseId + "/flashcards"), token).andExpect(status().isNoContent());

        byte[] pdf = TestPdfs.withLines(
                "BIO 181 Week 3 notes",
                "Photosynthesis converts light energy into chemical energy stored in glucose.",
                "The Calvin cycle in the stroma uses ATP and NADPH from the light reactions.",
                "RuBisCO fixes carbon dioxide into a three-carbon sugar.");
        mvc.perform(multipart("/api/courses/" + courseId + "/notes/pdf")
                        .file(new MockMultipartFile("file", "week3.pdf", "application/pdf", pdf))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("PDF"))
                .andExpect(jsonPath("$.fileName").value("week3.pdf"))
                .andExpect(jsonPath("$.content", containsString("RuBisCO")));

        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenReturn(IntStream.range(0, 10)
                .mapToObj(i -> "{\"question\": \"Q" + i + "\", \"answer\": \"A" + i + "\"}")
                .collect(Collectors.joining(",", "{\"flashcards\": [", "]}")));
        // No content in the request: the PDF notes are used
        call(post("/api/materials/" + courseId + "/generate-flashcards").contentType(MediaType.APPLICATION_JSON).content("{}"), token)
                .andExpect(status().isOk());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateJson(anyString(), prompt.capture(), any(), anyInt());
        assertThat(prompt.getValue()).contains("Calvin cycle in the stroma");

        call(get("/api/materials/" + courseId + "/flashcards"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flashcards", hasSize(10)))
                .andExpect(jsonPath("$.label").value("AI-generated"))
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void notesCanBePastedAndRejectsNonPdfUploads() throws Exception {
        String token = register();
        long courseId = course(token);

        call(put("/api/courses/" + courseId + "/notes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"My pasted notes\"}"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("PASTE"));
        call(get("/api/courses/" + courseId + "/notes"), token)
                .andExpect(jsonPath("$.content").value("My pasted notes"))
                .andExpect(jsonPath("$.truncatedForAi").value(false));

        mvc.perform(multipart("/api/courses/" + courseId + "/notes/pdf")
                        .file(new MockMultipartFile("file", "notes.pdf", "application/pdf", "not a pdf".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("isn't a PDF")));
        call(get("/api/courses/" + courseId + "/notes"), register()).andExpect(status().isNotFound());
    }

    @Test
    void riskDetailsAndCheckInReloadAfterPredict() throws Exception {
        String token = register();
        long courseId = course(token);
        call(get("/api/courses/" + courseId + "/risk/latest"), token).andExpect(status().isNoContent());
        call(get("/api/courses/" + courseId + "/check-in/latest"), token).andExpect(status().isNoContent());

        when(mlClient.predict(any())).thenReturn(new MlPredictResponse(1, 0.91,
                List.of(new TopFeature("midterm_score", 0.59)),
                List.of(new PathStep("midterm_score", 55, 64.5, "<=")),
                "The model flagged this course as at risk.", List.of("avg_sleep_hours"), "Guidance, not a verdict.", "tree_v1"));
        call(post("/api/courses/" + courseId + "/predict").contentType(MediaType.APPLICATION_JSON).content("""
                {"attendanceRate": 0.72, "missedDeadlines": 4, "onTimeSubmissionRate": 0.65, "midtermScore": 55,
                 "avgWeeklyStudyHours": 3, "flashcardsReviewed": 40, "avgDaysStartedBeforeExam": 1,
                 "lateNightStudyPct": 0.45, "studySessionsLogged": 8}
                """), token).andExpect(status().isCreated());

        call(get("/api/courses/" + courseId + "/risk/latest"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atRisk").value(true))
                .andExpect(jsonPath("$.explanation").value("The model flagged this course as at risk."))
                .andExpect(jsonPath("$.topFeatures[0].name").value("midterm_score"))
                .andExpect(jsonPath("$.imputedFeatures[0]").value("avg_sleep_hours"));
        call(get("/api/courses/" + courseId + "/check-in/latest"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceRate").value(0.72))
                .andExpect(jsonPath("$.midtermScore").value(55.0))
                .andExpect(jsonPath("$.avgSleepHours").doesNotExist());
    }

    @Test
    void studyPlanUsesStudentTimeZoneAndIsSaved() throws Exception {
        String token = register();
        long courseId = course(token);
        // 11:30 pm on Oct 8 in New York is 03:30 on Oct 9 in UTC
        call(post("/api/courses/" + courseId + "/items").contentType(MediaType.APPLICATION_JSON).content(
                "{\"name\": \"Lab 4\", \"category\": \"HOMEWORK\", \"weight\": 5, \"dueDate\": \"2026-10-09T03:30:00Z\"}"), token)
                .andExpect(status().isCreated());
        String day = "{\"date\": \"x\", \"focus\": \"f\", \"tasks\": []}";
        when(gemini.generateJson(anyString(), anyString(), any(), anyInt()))
                .thenReturn("{\"days\": [" + String.join(",", Collections.nCopies(7, day)) + "]}");

        call(get("/api/study-plan/latest"), token).andExpect(status().isNoContent());
        call(post("/api/study-plan/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\": \"2026-10-05\", \"timeZone\": \"America/New_York\"}"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("America/New_York"))
                .andExpect(jsonPath("$.deadlinesConsidered[0]", containsString("due Thu Oct 8")));
        call(post("/api/study-plan/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\": \"2026-10-05\", \"timeZone\": \"UTC\"}"), token)
                .andExpect(jsonPath("$.deadlinesConsidered[0]", containsString("due Fri Oct 9")));
        call(post("/api/study-plan/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"timeZone\": \"Mars/Olympus\"}"), token)
                .andExpect(status().isBadRequest());

        call(get("/api/study-plan/latest"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andExpect(jsonPath("$.days", hasSize(7)));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String register() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"p-" + UUID.randomUUID() + "@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long course(String token) throws Exception {
        String body = call(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseCode\": \"BIO 181\", \"courseName\": \"Intro Biology\"}"), token)
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}
