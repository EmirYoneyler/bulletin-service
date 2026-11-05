package com.fonbulteni.bulletinservice.controller;

import com.fonbulteni.bulletinservice.analytics.facts.BulletinWeek;
import com.fonbulteni.bulletinservice.analytics.facts.FactsAssembler;
import com.fonbulteni.bulletinservice.common.InvalidRequestException;
import com.fonbulteni.bulletinservice.dto.FactsPreviewResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/** Admin tools for bulletins. {@code /api/admin/**} requires {@code ROLE_ADMIN} (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/bulletins")
public class AdminBulletinController {

    private final FactsAssembler factsAssembler;
    private final Clock clock;

    public AdminBulletinController(FactsAssembler factsAssembler, Clock clock) {
        this.factsAssembler = factsAssembler;
        this.clock = clock;
    }

    /**
     * Every number the bulletin of {@code email} for the week containing {@code week} would use.
     * Without {@code week}: the latest completed week (what the Monday bulletin covers).
     */
    @GetMapping("/facts/preview")
    public FactsPreviewResponse previewFacts(
            @RequestParam
            @NotBlank(message = "E-posta boş olamaz.")
            @Email(message = "Geçerli bir e-posta adresi girin.")
            String email,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        LocalDate today = LocalDate.now(clock);
        BulletinWeek bulletinWeek = week == null ? BulletinWeek.latestCompleted(today) : BulletinWeek.containing(week);
        if (bulletinWeek.friday().isAfter(today)) {
            throw new InvalidRequestException("INVALID_WEEK",
                    "Bu hafta henüz bitmedi; önizleme cuma kapanışından sonra yapılabilir.");
        }
        return FactsPreviewResponse.from(factsAssembler.assemble(email, bulletinWeek));
    }
}
