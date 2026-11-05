package com.fonbulteni.bulletinservice.dto;

import com.fonbulteni.bulletinservice.entity.Fund;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class FundRequest {
    // Only constraints the database already enforces (funds.name is NOT NULL, varchar(255)),
    // so requests that used to succeed keep succeeding.
    @NotBlank(message = "Fon adı boş olamaz.")
    @Size(max = 255, message = "Fon adı en fazla 255 karakter olabilir.")
    private String name;
    // Optional; stored upper case.
    @Pattern(regexp = "[A-Za-z0-9]{3,10}", message = "TEFAS kodu 3-10 harf veya rakamdan oluşmalı.")
    private String tefasCode;
    private Fund.Category category;
    private Fund.RiskLevel riskLevel;
    private Double returnRate;
    private Double fundSize;
    private Fund.Period period;
    private Boolean isFutureTrade;
    private LocalDate expiryDate;
    private Double contractSize;
}