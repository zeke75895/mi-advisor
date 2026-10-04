package com.miadvisor.voice;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Minimal client for ElevenLabs text-to-speech, returning MP3 bytes. */
@Component
public class ElevenLabsClient {

    static final String OUTPUT_FORMAT = "mp3_44100_128";

    private final RestClient restClient;
    private final String apiKey;
    private final String voiceId;
    private final String model;

    public ElevenLabsClient(
            RestClient.Builder builder,
            @Value("${elevenlabs.base-url}") String baseUrl,
            @Value("${elevenlabs.api-key:}") String apiKey,
            @Value("${elevenlabs.voice-id}") String voiceId,
            @Value("${elevenlabs.model}") String model,
            @Value("${elevenlabs.timeout}") Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(timeout);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.voiceId = voiceId;
        this.model = model;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Speaks the text with the configured voice and returns the MP3 audio. */
    public byte[] speak(String text) {
        if (!isEnabled()) {
            throw new VoiceNotConfiguredException();
        }
        Map<String, Object> body = Map.of(
                "text", text,
                "model_id", model,
                // Lower stability gives a warmer, more expressive read; the rest are ElevenLabs defaults
                "voice_settings", Map.of("stability", 0.45, "similarity_boost", 0.75, "style", 0.2));
        try {
            byte[] audio = restClient.post()
                    .uri("/text-to-speech/{voiceId}?output_format={format}", voiceId, OUTPUT_FORMAT)
                    .header("xi-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            if (audio == null || audio.length == 0) {
                throw new VoiceException("ElevenLabs returned no audio");
            }
            return audio;
        } catch (RestClientException e) {
            // The API key travels in a header, so the message (status + response body) never contains it
            String detail = e.getMessage() == null ? "" : e.getMessage();
            throw new VoiceException("ElevenLabs request failed: " + e.getClass().getSimpleName() + ": "
                    + detail.substring(0, Math.min(300, detail.length())), e);
        }
    }
}
