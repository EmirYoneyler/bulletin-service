package com.fonbulteni.bulletinservice.bulletin;

import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.analytics.facts.Fact;
import com.fonbulteni.bulletinservice.analytics.facts.FactFormatter;
import com.fonbulteni.bulletinservice.bulletin.llm.TextRules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Last step of the pipeline (AGENT_PROMPT §4.2): every {@code {{key}}} becomes the fact's Turkish form
 * from {@link FactFormatter}, the same formatting the PDF tables use. This is the only place numbers enter
 * the text.
 *
 * <p>Spaces inside the braces are tolerated ({@code {{ fund.AAV.return.1w }}}). A key that is not in the
 * facts is a bug at this point, since NumberGuard rejects such text before rendering (E4-1); it fails
 * instead of printing the placeholder or guessing. A key whose value is missing renders as "veri yok",
 * like the tables.
 */
public final class PlaceholderRenderer {

    private PlaceholderRenderer() {
    }

    public static String render(String text, BulletinFacts facts) {
        Map<String, Fact> byKey = facts.byKey();
        List<String> unknown = keys(text).stream().filter(k -> !byKey.containsKey(k)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown placeholder key(s), text was not validated: " + unknown);
        }
        Matcher matcher = TextRules.PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Fact fact = byKey.get(matcher.group(1).strip());
            matcher.appendReplacement(out, Matcher.quoteReplacement(FactFormatter.format(fact)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static WriterOutput render(WriterOutput output, BulletinFacts facts) {
        return output.map(text -> render(text, facts));
    }

    /** The placeholder keys in the text, in order of first appearance, without surrounding spaces. */
    public static List<String> keys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = TextRules.PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group(1).strip());
        }
        return new ArrayList<>(keys);
    }
}
