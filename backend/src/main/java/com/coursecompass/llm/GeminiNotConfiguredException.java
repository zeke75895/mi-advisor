package com.coursecompass.llm;

public class GeminiNotConfiguredException extends GeminiException {

    public GeminiNotConfiguredException() {
        super("AI features are turned off: GEMINI_API_KEY is not set on the server");
    }
}
