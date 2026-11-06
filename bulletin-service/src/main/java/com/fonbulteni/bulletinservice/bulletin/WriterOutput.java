package com.fonbulteni.bulletinservice.bulletin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The writer's text in the JSON shape of {@code prompts/writer.v1.md}. Before rendering it holds
 * placeholders ({@code {{fund.AAV.return.1w}}}), never numbers; after {@link PlaceholderRenderer} the same
 * record holds the formatted values.
 *
 * @param fundNotes     at most one note per fund, in the order of the bulletin's fund list
 * @param benchmarkNote empty when there was nothing to compare with
 */
public record WriterOutput(String summary, List<FundNote> fundNotes, String benchmarkNote) {

    public record FundNote(String code, String text) {
    }

    public WriterOutput {
        fundNotes = List.copyOf(fundNotes);
        benchmarkNote = benchmarkNote == null ? "" : benchmarkNote;
    }

    public Optional<String> noteFor(String code) {
        return fundNotes.stream().filter(n -> n.code().equals(code)).map(FundNote::text).findFirst();
    }

    /** Every piece of text, for checks that look at all of it (guards). */
    public List<String> texts() {
        List<String> texts = new ArrayList<>();
        texts.add(summary);
        fundNotes.forEach(n -> texts.add(n.text()));
        if (!benchmarkNote.isEmpty()) {
            texts.add(benchmarkNote);
        }
        return texts;
    }

    /** The same structure with {@code change} applied to every piece of text. */
    public WriterOutput map(UnaryOperator<String> change) {
        return new WriterOutput(change.apply(summary),
                fundNotes.stream().map(n -> new FundNote(n.code(), change.apply(n.text()))).toList(),
                benchmarkNote.isEmpty() ? "" : change.apply(benchmarkNote));
    }
}
