package com.fonbulteni.bulletinservice.bulletin.llm;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared rules for writer text. A writer may not put numbers in its text (AGENT_PROMPT §4.2), but some
 * names legitimately contain digits: fund codes such as {@code AU1} and fixed names such as
 * {@code BIST 100}. The digit check (NumberGuard, E4-1) looks at the text after removing exactly these.
 */
public final class TextRules {

    /** Proper names with digits a writer may use as is. */
    public static final List<String> NAMES_WITH_DIGITS = List.of("BIST 100");

    public static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]+)}}");

    private TextRules() {
    }

    /** The text without placeholders, allowed names and the given fund codes (as whole words). */
    public static String withoutAllowedDigitTokens(String text, Collection<String> fundCodes) {
        String result = PLACEHOLDER.matcher(text).replaceAll(" ");
        for (String name : NAMES_WITH_DIGITS) {
            result = result.replace(name, " ");
        }
        for (String code : fundCodes) {
            result = result.replaceAll("(?<![\\p{L}\\p{N}])" + Pattern.quote(code) + "(?![\\p{L}\\p{N}])", " ");
        }
        return result;
    }
}
