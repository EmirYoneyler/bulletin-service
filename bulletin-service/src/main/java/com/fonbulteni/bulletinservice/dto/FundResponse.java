package com.fonbulteni.bulletinservice.dto;

import com.fonbulteni.bulletinservice.entity.Fund;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class FundResponse {
    private Long id;
    private String name;
    private Fund.Category category;
    private Fund.RiskLevel riskLevel;
    private Double returnRate;
    private Double fundSize;
    private Fund.Period period;
    private Boolean isFutureTrade;
    private LocalDate expiryDate;
    private Double contractSize;
    private LocalDateTime createdAt;
}





















