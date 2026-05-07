package com.fonbulteni.bulletinservice.controller;

import com.fonbulteni.bulletinservice.dto.FundRequest;
import com.fonbulteni.bulletinservice.dto.FundResponse;
import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.service.FundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class FundController {

    private final FundService fundService;

    @PostMapping
    public ResponseEntity<FundResponse> createFund(@RequestBody FundRequest request) {
        return ResponseEntity.ok(fundService.createFund(request));
    }

    @GetMapping
    public ResponseEntity<List<FundResponse>> getAllFunds() {
        return ResponseEntity.ok(fundService.getAllFunds());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FundResponse> getFundById(@PathVariable Long id) {
        return ResponseEntity.ok(fundService.getFundById(id));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<FundResponse>> getFundsByCategory(@PathVariable Fund.Category category) {
        return ResponseEntity.ok(fundService.getFundsByCategory(category));
    }

    @GetMapping("/search")
    public ResponseEntity<List<FundResponse>> searchFunds(@RequestParam String name) {
        return ResponseEntity.ok(fundService.searchFunds(name));
    }

    @GetMapping("/popular")
    public ResponseEntity<List<FundResponse>> getPopularFunds() {
        return ResponseEntity.ok(fundService.getPopularFunds());
    }
}
