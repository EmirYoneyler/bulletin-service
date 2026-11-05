package com.fonbulteni.bulletinservice.service;

import com.fonbulteni.bulletinservice.common.DuplicateTefasCodeException;
import com.fonbulteni.bulletinservice.common.FundNotFoundException;
import com.fonbulteni.bulletinservice.dto.FundRequest;
import com.fonbulteni.bulletinservice.dto.FundResponse;
import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FundService {

    private final FundRepository fundRepository;

    public FundResponse createFund(FundRequest request) {
        String tefasCode = normalizeTefasCode(request.getTefasCode());
        if (tefasCode != null && fundRepository.existsByTefasCode(tefasCode)) {
            throw new DuplicateTefasCodeException(tefasCode);
        }
        Fund fund = Fund.builder()
                .name(request.getName())
                .tefasCode(tefasCode)
                .category(request.getCategory())
                .riskLevel(request.getRiskLevel())
                .returnRate(request.getReturnRate())
                .fundSize(request.getFundSize())
                .period(request.getPeriod())
                .isFutureTrade(request.getIsFutureTrade())
                .expiryDate(request.getExpiryDate())
                .contractSize(request.getContractSize())
                .build();
        fundRepository.save(fund);
        return toResponse(fund);
    }

    public List<FundResponse> getAllFunds() {
        return fundRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public FundResponse getFundById(Long id) {
        Fund fund = fundRepository.findById(id)
                .orElseThrow(() -> new FundNotFoundException(id));
        return toResponse(fund);
    }

    public FundResponse getFundByTefasCode(String code) {
        String tefasCode = normalizeTefasCode(code);
        return fundRepository.findByTefasCode(tefasCode)
                .map(this::toResponse)
                .orElseThrow(() -> FundNotFoundException.byTefasCode(tefasCode));
    }

    /** TEFAS codes are upper case; Locale.ROOT so "i" never becomes the Turkish dotted "İ". */
    static String normalizeTefasCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public List<FundResponse> getFundsByCategory(Fund.Category category) {
        return fundRepository.findByCategory(category).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<FundResponse> searchFunds(String name) {
        return fundRepository.findByNameContainingIgnoreCase(name).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<FundResponse> getPopularFunds() {
        return fundRepository.findAllByOrderByFundSizeDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private FundResponse toResponse(Fund fund) {
        return FundResponse.builder()
                .id(fund.getId())
                .name(fund.getName())
                .tefasCode(fund.getTefasCode())
                .category(fund.getCategory())
                .riskLevel(fund.getRiskLevel())
                .returnRate(fund.getReturnRate())
                .fundSize(fund.getFundSize())
                .period(fund.getPeriod())
                .isFutureTrade(fund.getIsFutureTrade())
                .expiryDate(fund.getExpiryDate())
                .contractSize(fund.getContractSize())
                .createdAt(fund.getCreatedAt())
                .build();
    }
}
