package com.fonbulteni.bulletinservice.service;

import com.fonbulteni.bulletinservice.common.FundNotFoundException;
import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.repository.FundRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class FundServiceTest {

    private final FundRepository repository = mock(FundRepository.class);
    private final FundService service = new FundService(repository);

    @Test
    void missingFundThrowsFundNotFound() {
        given(repository.findById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFundById(42L))
                .isInstanceOf(FundNotFoundException.class)
                .hasMessage("Fon bulunamadı: 42")
                .extracting("code").isEqualTo("FUND_NOT_FOUND");
    }

    @Test
    void existingFundIsMapped() {
        given(repository.findById(1L)).willReturn(Optional.of(
                Fund.builder().id(1L).name("Örnek Fon").category(Fund.Category.TAHVIL).build()));

        assertThat(service.getFundById(1L).getName()).isEqualTo("Örnek Fon");
    }
}
