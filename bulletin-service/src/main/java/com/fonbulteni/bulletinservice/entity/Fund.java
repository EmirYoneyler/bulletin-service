package com.fonbulteni.bulletinservice.entity;

import com.fonbulteni.bulletinservice.common.TimeConfig;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "funds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** TEFAS fund code (e.g. "AAV"), always upper case. Null for funds entered manually without one. */
    @Column(name = "tefas_code", length = 10, unique = true)
    private String tefasCode;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    private Double returnRate;

    private Double fundSize;

    @Enumerated(EnumType.STRING)
    private Period period;

    private Boolean isFutureTrade;

    private LocalDate expiryDate;

    private Double contractSize;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        // Explicit zone: the JVM default is UTC in containers (ADR 010).
        this.createdAt = LocalDateTime.now(TimeConfig.ISTANBUL);
    }

    public enum Category {
        EMEKLILIK, HISSE, TAHVIL, KARMA, PARA_PIYASASI
    }

    public enum RiskLevel {
        DUSUK, ORTA, YUKSEK
    }

    public enum Period {
        AYLIK, UC_AYLIK, ALTI_AYLIK, YILLIK
    }
}