package com.fonbulteni.bulletinservice.bulletin;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A versioned prompt file under {@code src/main/resources/prompts/} (AGENT_PROMPT §7.6). Prompts live in
 * these files, never in Java strings.
 *
 * <p>Format: a front matter block with {@code version: <name>}, an optional preamble (change notes, not
 * sent to the model) and sections that start with a level-one heading holding a single lowercase word
 * ({@code # system}, {@code # user}, ...). Deeper headings belong to the section they are in. Sections may
 * contain {@code ${name}} variables, which {@link #render} fills; the fact placeholders a writer produces
 * ({@code {{key}}}) are a different syntax and are left alone.
 */
public record PromptTemplate(String resource, String version, Map<String, String> sections) {

    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\R(.*?)\\R---\\R", Pattern.DOTALL);
    private static final Pattern VERSION = Pattern.compile("(?m)^version:\\s*(\\S+)\\s*$");
    private static final Pattern SECTION = Pattern.compile("(?m)^# ([a-z]+)\\s*$");
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z]+)}");

    public PromptTemplate {
        sections = Map.copyOf(sections);
    }

    public static PromptTemplate load(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return parse(resource, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt file could not be read: " + resource, e);
        }
    }

    static PromptTemplate parse(String resource, String content) {
        String text = content.replace("\r\n", "\n");
        Matcher front = FRONT_MATTER.matcher(text);
        if (!front.find()) {
            throw new IllegalStateException(resource + ": front matter (--- version: ... ---) is missing");
        }
        Matcher version = VERSION.matcher(front.group(1));
        if (!version.find()) {
            throw new IllegalStateException(resource + ": front matter has no version");
        }
        String body = text.substring(front.end());

        Map<String, String> sections = new LinkedHashMap<>();
        Matcher heading = SECTION.matcher(body);
        String name = null;
        int start = 0;
        while (heading.find()) {
            if (name != null) {
                put(resource, sections, name, body.substring(start, heading.start()));
            }
            name = heading.group(1);
            start = heading.end();
        }
        if (name == null) {
            throw new IllegalStateException(resource + ": no sections (# system, # user, ...)");
        }
        put(resource, sections, name, body.substring(start));
        return new PromptTemplate(resource, version.group(1), sections);
    }

    private static void put(String resource, Map<String, String> sections, String name, String text) {
        if (sections.putIfAbsent(name, text.strip()) != null) {
            throw new IllegalStateException(resource + ": section '" + name + "' appears twice");
        }
    }

    public String section(String name) {
        String text = sections.get(name);
        if (text == null) {
            throw new IllegalArgumentException(resource + " has no section '" + name + "'");
        }
        return text;
    }

    /**
     * The section with every {@code ${name}} replaced by its value, inserted literally. A variable without
     * a value is a programming error and fails loudly instead of reaching the model as {@code ${name}}.
     */
    public String render(String sectionName, Map<String, String> values) {
        Matcher matcher = VARIABLE.matcher(section(sectionName));
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalArgumentException(resource + "#" + sectionName + ": no value for ${"
                        + matcher.group(1) + "}");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString().strip();
    }
}
