package com.fonbulteni.bulletinservice.repository;

import com.fonbulteni.bulletinservice.entity.Fund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FundRepository extends JpaRepository<Fund, Long> {
    List<Fund> findByCategory(Fund.Category category);
    List<Fund> findByRiskLevel(Fund.RiskLevel riskLevel);
    List<Fund> findByCategoryAndRiskLevel(Fund.Category category, Fund.RiskLevel riskLevel);
    List<Fund> findByNameContainingIgnoreCase(String name);
    List<Fund> findAllByOrderByFundSizeDesc();
}
