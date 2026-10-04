package com.miadvisor;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miadvisor.llm.GeminiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "gemini.api-key=", "ml.service.url=http://localhost:0", "app.ai.user-limit=2", "app.ai.user-window=PT10M"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    private static final String NOTES = "{\"content\": \"" + "Mitochondria make ATP through cellular respiration. ".repeat(4) + "\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private GeminiClient gemini;

    @Test
    void thirdGenerationInTheWindowIs429WithRetryAfterAndNoGeminiCall() throws Exception {
        when(gemini.generateJson(anyString(), anyString(), any(), anyInt())).thenReturn(IntStream.range(0, 10)
                .mapToObj(i -> "{\"question\": \"Q" + i + "\", \"answer\": \"A" + i + "\"}")
                .collect(Collectors.joining(",", "{\"flashcards\": [", "]}")));
        String token = register();
        long courseId = course(token);
        String url = "/api/materials/" + courseId + "/generate-flashcards";

        generate(url, token).andExpect(status().isOk());
        generate(url, token).andExpect(status().isOk());
        generate(url, token)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail", containsString("2 AI generations in the last 10 minutes")));
        verify(gemini, times(2)).generateJson(anyString(), anyString(), any(), anyInt());

        // Another student still has their own allowance
        String other = register();
        generate("/api/materials/" + course(other) + "/generate-flashcards", other).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions generate(String url, String token) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(NOTES));
    }

    private String register() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"r-" + UUID.randomUUID() + "@example.com\", \"password\": \"correct-horse-battery\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long course(String token) throws Exception {
        String body = mvc.perform(post("/api/courses").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"courseCode\": \"BIO 181\", \"courseName\": \"Bio\"}"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}
