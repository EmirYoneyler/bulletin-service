package com.fonbulteni.bulletinservice.bulletin.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fonbulteni.bulletinservice.analytics.facts.Fact;
import com.fonbulteni.bulletinservice.bulletin.llm.WriterContext.FactRef;
import com.fonbulteni.bulletinservice.bulletin.llm.WriterContext.FundContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writer without a model: fluent Turkish built from {@link WriterContext} (directions and ranking only),
 * in the same placeholder format and JSON shape a model must produce (AGENT_PROMPT §4.1–4.2). Always
 * available; the default provider and the fallback when a model fails.
 *
 * <p>Rules of the templates: no digits, no advice or forecast wording, and a direction word is never
 * placed right next to a number ("ekside kapattı; haftalık değişim %-0,8", not "%-0,8 geriledi").
 * Each sentence has three variants chosen by week and fund, so the text changes from week to week but
 * the same input always gives the same output.
 */
public class TemplateLlmClient implements LlmClient {

    public static final String MODEL = "template-v1";

    private final ObjectMapper mapper;

    public TemplateLlmClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "template";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long started = System.nanoTime();
        WriterContext context = WriterContext.extract(request.user(), mapper)
                .orElseThrow(() -> new LlmException(LlmException.Kind.BAD_REQUEST,
                        "Şablon yazar için istemde " + WriterContext.START + " bloğu yok.", null));

        ObjectNode out = mapper.createObjectNode();
        out.put("summary", summary(context));
        ArrayNode notes = out.putArray("fundNotes");
        for (FundContext fund : context.funds()) {
            notes.addObject().put("code", fund.code()).put("text", fundNote(fund, context.seed()));
        }
        out.put("benchmarkNote", benchmarkNote(context));
        try {
            String text = mapper.writeValueAsString(out);
            return new LlmResponse(text, 0, 0, (System.nanoTime() - started) / 1_000_000, MODEL);
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, "Şablon çıktısı yazılamadı.", e);
        }
    }

    // --- summary ---

    private static String summary(WriterContext c) {
        List<String> sentences = new ArrayList<>();
        Optional<FactRef> portfolio = c.portfolioFact("portfolio.return.1w");
        if (portfolio.isEmpty()) {
            sentences.add("Bu hafta izlediğin fonlar için yeterli fiyat verisi yok.");
        } else {
            String p = ph(portfolio.get());
            sentences.add(pick(c.seed(), "summary", switch (portfolio.get().direction()) {
                case UP -> List.of(
                        "İzlediğin fonlar haftayı ortalamada artıda kapattı; ortalama haftalık değişim " + p + " oldu.",
                        "Bu hafta fonlarının ortalaması yükseldi, haftalık ortalama değişim " + p + ".",
                        "Haftayı artıda tamamladın: izlediğin fonların ortalama değişimi " + p + ".");
                case DOWN -> List.of(
                        "İzlediğin fonlar haftayı ortalamada ekside kapattı; ortalama haftalık değişim " + p + " oldu.",
                        "Bu hafta fonlarının ortalaması geriledi, haftalık ortalama değişim " + p + ".",
                        "Haftayı ekside tamamladın: izlediğin fonların ortalama değişimi " + p + ".");
                case FLAT -> List.of(
                        "İzlediğin fonlar bu hafta ortalamada yatay seyretti; ortalama değişim " + p + ".",
                        "Bu hafta fonlarının ortalamasında belirgin bir hareket olmadı (" + p + ").",
                        "Haftayı neredeyse değişmeden tamamladın: ortalama değişim " + p + ".");
            }));
        }
        breadth(c).ifPresent(sentences::add);
        sentences.addAll(topMovers(c));
        c.portfolioFact("portfolio.real.1w").ifPresent(real -> sentences.add(pick(c.seed(), "real", List.of(
                "Aylık enflasyon haftaya yayıldığında ortalama reel değişim " + ph(real) + ".",
                "Enflasyonu hesaba kattığında haftalık reel değişim " + ph(real) + " oldu.",
                "Enflasyondan arındırıldığında haftalık değişim " + ph(real) + "."))));
        return String.join(" ", sentences);
    }

    private static Optional<String> breadth(WriterContext c) {
        int up = 0;
        int down = 0;
        int total = 0;
        for (FundContext fund : c.funds()) {
            Optional<FactRef> week = fund.fact(weekKey(fund.code()));
            if (week.isPresent()) {
                total++;
                if (week.get().direction() == Fact.Direction.UP) {
                    up++;
                } else if (week.get().direction() == Fact.Direction.DOWN) {
                    down++;
                }
            }
        }
        if (total < 2) {
            return Optional.empty();
        }
        String key = "breadth";
        if (up == total) {
            return Optional.of(pick(c.seed(), key, List.of("Haftalık verisi olan fonlarının hepsi değer kazandı.",
                    "Fonlarının tamamı haftayı artıda kapattı.", "Bu hafta fonlarının hepsi yükseldi.")));
        }
        if (down == total) {
            return Optional.of(pick(c.seed(), key, List.of("Haftalık verisi olan fonlarının hepsi değer kaybetti.",
                    "Fonlarının tamamı haftayı ekside kapattı.", "Bu hafta fonlarının hepsi geriledi.")));
        }
        if (up > down && up * 2 > total) {
            return Optional.of(pick(c.seed(), key, List.of("Fonlarının çoğu haftayı artıda kapattı.",
                    "Fonlarının büyük kısmı bu hafta değer kazandı.", "Bu hafta yükselen fonların sayısı daha fazlaydı.")));
        }
        if (down > up && down * 2 > total) {
            return Optional.of(pick(c.seed(), key, List.of("Fonlarının çoğu haftayı ekside kapattı.",
                    "Fonlarının büyük kısmı bu hafta değer kaybetti.", "Bu hafta gerileyen fonların sayısı daha fazlaydı.")));
        }
        return Optional.of(pick(c.seed(), key, List.of("Fonlarında karışık bir hafta yaşandı.",
                "Bu hafta fonlarının bir kısmı yükselirken bir kısmı geriledi.", "Fonların arasında yön birliği yoktu.")));
    }

    private static List<String> topMovers(WriterContext c) {
        List<String> out = new ArrayList<>();
        if (c.topGainer() == null || c.topLoser() == null) {
            return out;
        }
        fundWeek(c, c.topGainer()).ifPresent(g -> {
            String p = ph(g);
            String code = c.topGainer();
            out.add(g.direction() == Fact.Direction.UP
                    ? pick(c.seed(), "top-gainer", List.of(
                            "Haftanın en iyi performansı " + code + " fonundan geldi (" + p + ").",
                            "En çok yükselen fon " + code + " oldu (" + p + ").",
                            code + " fonu haftanın öne çıkanı oldu (" + p + ")."))
                    : pick(c.seed(), "top-gainer", List.of(
                            "En az etkilenen fon " + code + " oldu (" + p + ").",
                            "Haftayı en iyi tamamlayan fon " + code + " oldu (" + p + ").",
                            "Fonların arasında en iyi sonuç " + code + " fonundaydı (" + p + ").")));
        });
        fundWeek(c, c.topLoser()).ifPresent(l -> {
            String p = ph(l);
            String code = c.topLoser();
            out.add(l.direction() == Fact.Direction.DOWN
                    ? pick(c.seed(), "top-loser", List.of(
                            "En çok gerileyen fon " + code + " oldu (" + p + ").",
                            "Haftanın en zayıf performansı " + code + " fonundaydı (" + p + ").",
                            code + " fonu haftayı en geride bitirdi (" + p + ")."))
                    : pick(c.seed(), "top-loser", List.of(
                            "En sınırlı değişim " + code + " fonundaydı (" + p + ").",
                            "Haftayı sıralamanın sonunda bitiren fon " + code + " oldu (" + p + ").",
                            code + " fonu sıralamada en sonda yer aldı (" + p + ").")));
        });
        return out;
    }

    // --- funds ---

    private static String fundNote(FundContext fund, int seed) {
        String code = fund.code();
        String salt = "fund:" + code;
        Optional<FactRef> week = fund.fact(weekKey(code));
        List<String> sentences = new ArrayList<>();
        if (week.isEmpty()) {
            sentences.add(code + " fonu için bu hafta yeterli fiyat verisi yok.");
        } else {
            String p = ph(week.get());
            sentences.add(pick(seed, salt, switch (week.get().direction()) {
                case UP -> List.of(
                        code + " fonu haftayı artıda kapattı; haftalık değişim " + p + " oldu.",
                        code + " fonu bu hafta değer kazandı, haftalık değişim " + p + ".",
                        "Bu hafta " + code + " fonunun değeri yükseldi: " + p + ".");
                case DOWN -> List.of(
                        code + " fonu haftayı ekside kapattı; haftalık değişim " + p + " oldu.",
                        code + " fonu bu hafta değer kaybetti, haftalık değişim " + p + ".",
                        "Bu hafta " + code + " fonunun değeri geriledi: " + p + ".");
                case FLAT -> List.of(
                        code + " fonu bu hafta yatay seyretti; haftalık değişim " + p + ".",
                        code + " fonunda bu hafta belirgin bir hareket olmadı (" + p + ").",
                        "Bu hafta " + code + " fonunun değeri neredeyse değişmedi: " + p + ".");
            }));
        }
        Optional<FactRef> mtd = fund.fact("fund." + code + ".return.mtd");
        Optional<FactRef> ytd = fund.fact("fund." + code + ".return.ytd");
        if (mtd.isPresent() && ytd.isPresent()) {
            sentences.add(pick(seed, salt + ":period", List.of(
                    "Ay başından bu yana değişim " + ph(mtd.get()) + ", yılbaşından bu yana " + ph(ytd.get()) + ".",
                    "Ay başından beri " + ph(mtd.get()) + ", yılın başından beri " + ph(ytd.get()) + " değişti.",
                    "Daha geniş bakınca: ay başından bu yana " + ph(mtd.get()) + ", yılbaşından bu yana " + ph(ytd.get()) + ".")));
        } else {
            mtd.ifPresent(m -> sentences.add("Ay başından bu yana değişim " + ph(m) + "."));
            ytd.ifPresent(y -> sentences.add("Yılbaşından bu yana değişim " + ph(y) + "."));
        }
        fund.fact("fund." + code + ".real.ytd").ifPresent(real -> sentences.add(
                "Enflasyondan arındırıldığında yılbaşından bu yana değişim " + ph(real) + "."));
        return String.join(" ", sentences);
    }

    // --- benchmarks ---

    private static String benchmarkNote(WriterContext c) {
        List<String> parts = new ArrayList<>();
        c.benchmarkFact("bench.USDTRY.return.1w").ifPresent(f -> parts.add("dolar/TL " + ph(f)));
        c.benchmarkFact("bench.BIST100.return.1w").ifPresent(f -> parts.add("BIST 100 getiri endeksi " + ph(f)));
        c.benchmarkFact("bench.DEPOSIT.return.1w").ifPresent(f -> parts.add("TL mevduat " + ph(f)));
        List<String> sentences = new ArrayList<>();
        if (!parts.isEmpty()) {
            String joined = String.join(", ", parts);
            sentences.add(pick(c.seed(), "bench", List.of(
                    "Aynı hafta " + joined + " değişti.",
                    "Kıyas için bu haftaki değişimler: " + joined + ".",
                    "Haftalık kıyaslar şöyle: " + joined + ".")));
        }
        c.benchmarkFact("bench.GOLD.return.mom").ifPresent(g -> sentences.add(pick(c.seed(), "gold", List.of(
                "Altının son tamamlanan aydaki değişimi " + ph(g) + ".",
                "Külçe altında son aylık değişim " + ph(g) + " oldu.",
                "Aylık bazda altın " + ph(g) + " değişti."))));
        c.benchmarkFact("bench.CPI.month").ifPresent(cpi -> sentences.add(pick(c.seed(), "cpi", List.of(
                "Son açıklanan aylık enflasyon " + ph(cpi) + ".",
                "Aylık enflasyon en son " + ph(cpi) + " olarak açıklandı.",
                "Son aylık TÜFE değişimi " + ph(cpi) + "."))));
        return String.join(" ", sentences);
    }

    // --- helpers ---

    private static Optional<FactRef> fundWeek(WriterContext c, String code) {
        return c.funds().stream().filter(f -> f.code().equals(code)).findFirst()
                .flatMap(f -> f.fact(weekKey(code)));
    }

    private static String weekKey(String code) {
        return "fund." + code + ".return.1w";
    }

    static String ph(FactRef fact) {
        return "{{" + fact.key() + "}}";
    }

    /** Deterministic variant: same week and salt → same sentence; different weeks and funds vary. */
    static String pick(int seed, String salt, List<String> variants) {
        return variants.get(Math.floorMod(seed * 31 + salt.hashCode(), variants.size()));
    }
}
