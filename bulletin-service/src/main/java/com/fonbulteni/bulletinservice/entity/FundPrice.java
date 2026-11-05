package com.fonbulteni.bulletinservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Closing price of a fund on one trading day, as published by the source. */
@Entity
@Table(name = "fund_price")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundPrice {

    public enum Source {
        TEFAS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal price;

    /** Total fund size in TRY; null when the source does not report it. */
    @Column(name = "total_value", precision = 24, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "investor_count")
    private Integer investorCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
