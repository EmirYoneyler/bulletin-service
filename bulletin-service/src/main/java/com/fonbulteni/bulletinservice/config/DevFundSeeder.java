package com.fonbulteni.bulletinservice.config;

import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.repository.FundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Inserts five real TEFAS funds, one per category, in the {@code dev} profile only. Idempotent:
 * a fund whose TEFAS code already exists is left alone.
 *
 * <p>Codes, titles and fund types were read from TEFAS on 2026-11-05
 * (https://www.tefas.gov.tr/tr/fon-getirileri, fundType=YAT and fundType=EMK; the first active fund
 * by code in each type). {@code riskLevel} maps the TEFAS risk value 1–7 to DUSUK (1–2), ORTA (3–5),
 * YUKSEK (6–7). Every other field is deliberately left null: no invented returns or sizes.
 */
@Component
@Profile("dev")
public class DevFundSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevFundSeeder.class);

    record SeedFund(String tefasCode, String name, Fund.Category category, Fund.RiskLevel riskLevel) {
    }

    static final List<SeedFund> SEED = List.of(
            // TEFAS: Hisse Senedi Şemsiye Fonu, risk 6
            new SeedFund("AAV", "ATA PORTFÖY İKİNCİ HİSSE SENEDİ (TL) FONU (HİSSE SENEDİ YOĞUN FON)",
                    Fund.Category.HISSE, Fund.RiskLevel.YUKSEK),
            // TEFAS: Borçlanma Araçları Şemsiye Fonu, risk 1
            new SeedFund("AHU", "ATLAS PORTFÖY BİRİNCİ ÖZEL SEKTÖR BORÇLANMA ARAÇLARI FONU",
                    Fund.Category.TAHVIL, Fund.RiskLevel.DUSUK),
            // TEFAS: Para Piyasası Şemsiye Fonu, risk 1
            new SeedFund("AAL", "ATA PORTFÖY PARA PİYASASI (TL) FONU",
                    Fund.Category.PARA_PIYASASI, Fund.RiskLevel.DUSUK),
            // TEFAS: Karma Şemsiye Fonu, risk 6
            new SeedFund("IJB", "İŞ PORTFÖY DİJİTAL OYUN SEKTÖRÜ KARMA FON",
                    Fund.Category.KARMA, Fund.RiskLevel.YUKSEK),
            // TEFAS (EMK): OKS Standart Fon, risk 4
            new SeedFund("AAJ", "AGESA HAYAT VE EMEKLİLİK A.Ş. OKS STANDART EMEKLİLİK YATIRIM FONU",
                    Fund.Category.EMEKLILIK, Fund.RiskLevel.ORTA));

    private final FundRepository fundRepository;

    public DevFundSeeder(FundRepository fundRepository) {
        this.fundRepository = fundRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int inserted = 0;
        for (SeedFund seed : SEED) {
            if (fundRepository.existsByTefasCode(seed.tefasCode())) {
                continue;
            }
            fundRepository.save(Fund.builder()
                    .tefasCode(seed.tefasCode())
                    .name(seed.name())
                    .category(seed.category())
                    .riskLevel(seed.riskLevel())
                    .build());
            inserted++;
        }
        log.info("Dev seed: {} fund(s) inserted, {} already present", inserted, SEED.size() - inserted);
    }
}
