package com.coursecompass;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"gemini.api-key=", "ml.service.url=http://localhost:0"})
@AutoConfigureMockMvc
class StudySessionIntegrationTest {

    private static final String TODAY = "2026-10-10";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void logsSessionsAndSummarizesByWeek() throws Exception {
        String token = register();
        long bio = course(token, "BIO 181");
        long ma = course(token, "MA 241");

        log(token, bio, "{\"minutes\": 60, \"studiedOn\": \"2026-10-10\", \"note\": \"Ch 3 review\"}").andExpect(status().isCreated());
        log(token, bio, "{\"minutes\": 90, \"studiedOn\": \"2026-10-04\"}").andExpect(status().isCreated());
        log(token, bio, "{\"minutes\": 30, \"studiedOn\": \"2026-10-03\"}").andExpect(status().isCreated()); // 8 days ago
        log(token, bio, "{\"minutes\": 120, \"studiedOn\": \"2026-09-01\"}").andExpect(status().isCreated()); // outside 4 weeks
        log(token, ma, "{\"minutes\": 45}").andExpect(status().isCreated()); // defaults to today

        call(get("/api/courses/" + bio + "/study-sessions?today=" + TODAY), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.last7DaysMinutes").value(150))
                // (60 + 90 + 30) minutes over 4 weeks = 0.75 h/week
                .andExpect(jsonPath("$.summary.avgWeeklyHours").value(0.8))
                .andExpect(jsonPath("$.summary.totalSessions").value(4))
                .andExpect(jsonPath("$.sessions", hasSize(4)))
                .andExpect(jsonPath("$.sessions[0].note").value("Ch 3 review"));

        call(get("/api/study-sessions/summary?today=" + TODAY), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last7DaysMinutes").value(195))
                .andExpect(jsonPath("$.dailyMinutes", hasSize(7)))
                .andExpect(jsonPath("$.dailyMinutes[0].date").value("2026-10-04"))
                .andExpect(jsonPath("$.dailyMinutes[0].minutes").value(90))
                .andExpect(jsonPath("$.dailyMinutes[6].minutes").value(105))
                .andExpect(jsonPath("$.courses", hasSize(2)));
    }

    @Test
    void newLoggersAreAveragedOverTheWeeksTheyActuallyTracked() throws Exception {
        String token = register();
        long bio = course(token, "BIO 181");
        log(token, bio, "{\"minutes\": 50, \"studiedOn\": \"2026-10-09\"}").andExpect(status().isCreated());
        log(token, bio, "{\"minutes\": 30, \"studiedOn\": \"2026-10-10\"}").andExpect(status().isCreated());

        // 80 minutes in the first week of logging = 1.3 h/week, not 80/4 weeks = 0.3
        call(get("/api/courses/" + bio + "/study-sessions?today=" + TODAY), token)
                .andExpect(jsonPath("$.summary.avgWeeklyHours").value(1.3));

        // Two weeks in: 80 + 120 minutes over 2 weeks = 1.7 h/week
        log(token, bio, "{\"minutes\": 120, \"studiedOn\": \"2026-10-01\"}").andExpect(status().isCreated());
        call(get("/api/courses/" + bio + "/study-sessions?today=" + TODAY), token)
                .andExpect(jsonPath("$.summary.avgWeeklyHours").value(1.7));
    }

    @Test
    void validatesAndScopesToOwner() throws Exception {
        String token = register();
        long bio = course(token, "BIO 181");

        log(token, bio, "{\"minutes\": 2}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.minutes").exists());
        log(token, bio, "{\"minutes\": 30, \"studiedOn\": \"2026-10-11\"}").andExpect(status().isBadRequest());

        String body = log(token, bio, "{\"minutes\": 30}").andReturn().getResponse().getContentAsString();
        long sessionId = json.readTree(body).get("id").asLong();
        String other = register();
        call(delete("/api/study-sessions/" + sessionId), other).andExpect(status().isNotFound());
        log(other, bio, "{\"minutes\": 30}").andExpect(status().isNotFound());

        call(delete("/api/study-sessions/" + sessionId), token).andExpect(status().isNoContent());
        call(get("/api/courses/" + bio + "/study-sessions?today=" + TODAY), token)
                .andExpect(jsonPath("$.summary.totalSessions").value(0));
    }

    private ResultActions log(String token, long courseId, String body) throws Exception {
        return call(post("/api/courses/" + courseId + "/study-sessions?today=" + TODAY)
                .contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r, String token)
            throws Exception {
        return mvc.perform(r.header("Authorization", "Bearer " + token));
    }

    private String register() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"s-" + UUID.randomUUID() + "@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long course(String token, String code) throws Exception {
        String body = call(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseCode\": \"" + code + "\", \"courseName\": \"Course\"}"), token)
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}
