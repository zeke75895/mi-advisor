package com.coursecompass.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A prompt loaded from src/main/resources/prompts with {{placeholder}} variables. */
public final class PromptTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final String text;

    private PromptTemplate(String text) {
        this.text = text;
    }

    public static PromptTemplate load(String name) {
        String path = "prompts/" + name;
        try (InputStream in = PromptTemplate.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Prompt not found on classpath: " + path);
            }
            return new PromptTemplate(new String(in.readAllBytes(), StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Fills every placeholder; fails if one has no value so a broken prompt never reaches the LLM. */
    public String render(Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            if (value == null) {
                throw new IllegalArgumentException("No value for prompt placeholder: " + m.group(1));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    public String text() {
        return text;
    }
}
