package com.fonbulteni.bulletinservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One observation of an EVDS series (CPI, USD/TRY, gold, deposit rate). Monthly values sit on day 1. */
@Entity
@Table(name = "macro_point")
@Getter
@NoArgsConstructor
public class MacroPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "series_code", nullable = false, length = 40)
    private String seriesCode;

    @Column(name = "obs_date", nullable = false)
    private LocalDate obsDate;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal value;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
