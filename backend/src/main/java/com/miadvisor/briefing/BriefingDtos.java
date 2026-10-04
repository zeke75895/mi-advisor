package com.miadvisor.briefing;

public final class BriefingDtos {

    private BriefingDtos() {}

    /**
     * @param script the exact text that was spoken, closing disclaimer included
     * @param source "gemini" when Gemini wrote the script, "template" otherwise
     * @param audioBase64 MP3 audio, base64-encoded
     */
    public record BriefingResponse(
            Long courseId,
            String script,
            String source,
            String label,
            String disclaimer,
            String mimeType,
            String audioBase64) {}

    public record FeaturesResponse(boolean voiceBriefing) {}
}
