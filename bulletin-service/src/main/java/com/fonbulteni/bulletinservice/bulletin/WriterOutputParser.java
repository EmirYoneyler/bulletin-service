package com.fonbulteni.bulletinservice.bulletin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.bulletin.WriterOutput.FundNote;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads a writer's answer into {@link WriterOutput}. Lenient about packaging, strict about content:
 * a JSON object wrapped in a Markdown fence or a sentence of chatter is accepted, but a missing summary,
 * a note for a fund that is not in the bulletin or the same fund twice is a bad answer
 * ({@link LlmException.Kind#BAD_RESPONSE}), which the pipeline retries or replaces with the template
 * writer. Numbers and placeholder keys are not checked here; that is NumberGuard's job (E4-1).
 */
public final class WriterOutputParser {

    private WriterOutputParser() {
    }

    public static WriterOutput parse(String raw, List<String> fundCodes, ObjectMapper mapper) {
        JsonNode root = readObject(raw, mapper);

        JsonNode summary = root.get("summary");
        if (summary == null || !summary.isTextual() || summary.asText().isBlank()) {
            throw bad("Yazar çıktısında 'summary' metni yok.");
        }

        List<FundNote> notes = new ArrayList<>();
        JsonNode fundNotes = root.get("fundNotes");
        if (fundNotes != null && !fundNotes.isNull()) {
            if (!fundNotes.isArray()) {
                throw bad("Yazar çıktısında 'fundNotes' bir liste değil.");
            }
            Set<String> seen = new HashSet<>();
            for (JsonNode note : fundNotes) {
                JsonNode code = note.get("code");
                JsonNode text = note.get("text");
                if (code == null || !code.isTextual() || text == null || !text.isTextual()) {
                    throw bad("Yazar çıktısında 'code' ve 'text' metni olmayan bir fon notu var.");
                }
                String normalized = code.asText().strip().toUpperCase(Locale.ROOT);
                if (!fundCodes.contains(normalized)) {
                    throw bad("Yazar çıktısında bültende olmayan bir fon kodu var: " + normalized);
                }
                if (!seen.add(normalized)) {
                    throw bad("Yazar çıktısında aynı fon için iki not var: " + normalized);
                }
                if (!text.asText().isBlank()) {
                    notes.add(new FundNote(normalized, text.asText().strip()));
                }
            }
            notes.sort(Comparator.comparingInt(n -> fundCodes.indexOf(n.code())));
        }

        JsonNode benchmark = root.get("benchmarkNote");
        if (benchmark != null && !benchmark.isNull() && !benchmark.isTextual()) {
            throw bad("Yazar çıktısında 'benchmarkNote' metin değil.");
        }
        String benchmarkNote = benchmark == null || benchmark.isNull() ? "" : benchmark.asText().strip();

        return new WriterOutput(summary.asText().strip(), notes, benchmarkNote);
    }

    /** The JSON object in the answer: the whole text, a fenced block, or the outermost braces. */
    private static JsonNode readObject(String raw, ObjectMapper mapper) {
        if (raw == null) {
            throw bad("Yazar boş yanıt döndü.");
        }
        int open = raw.indexOf('{');
        int close = raw.lastIndexOf('}');
        if (open < 0 || close < open) {
            throw bad("Yazar çıktısı JSON değil.");
        }
        try {
            JsonNode node = mapper.readTree(raw.substring(open, close + 1));
            if (node == null || !node.isObject()) {
                throw bad("Yazar çıktısı bir JSON nesnesi değil.");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, "Yazar çıktısı okunamadı: JSON bozuk.", e);
        }
    }

    private static LlmException bad(String message) {
        return new LlmException(LlmException.Kind.BAD_RESPONSE, message, null);
    }
}
