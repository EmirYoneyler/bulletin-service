package com.fonbulteni.bulletinservice.ingest.tefas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** Wire format of {@code fonGnlBlgSiraliGetir} (ADR 001). Field names are TEFAS's own. */
final class TefasHistoryApi {

    static final String PATH = "/fonGnlBlgSiraliGetir";

    private TefasHistoryApi() {
    }

    /** All keys are sent, unused filters as null, exactly like the TEFAS web page does. */
    record Request(
            String fonTipi,
            String fonKodu,
            String aramaMetni,
            String fonTurKod,
            String fonGrubu,
            String sfonTurKod,
            String basTarih,
            String bitTarih,
            int basSira,
            int bitSira,
            String fonTurAciklama,
            String dil,
            String kurucuKod) {

        static Request history(TefasFundType type, String code, String from, String to, int first, int last) {
            return new Request(type.name(), code, null, null, null, null, from, to, first, last, null, "TR", null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String errorCode, String errorMessage, List<Row> resultList, Integer toplamSayi) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Row(
            String fonKodu,
            String fonUnvan,
            String tarih,
            BigDecimal fiyat,
            BigDecimal tedPaySayisi,
            Integer kisiSayisi,
            BigDecimal portfoyBuyukluk) {
    }
}
