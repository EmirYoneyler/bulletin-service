package com.fonbulteni.bulletinservice.service;

import com.fonbulteni.bulletinservice.common.DuplicateTefasCodeException;
import com.fonbulteni.bulletinservice.common.FundNotFoundException;
import com.fonbulteni.bulletinservice.dto.FundRequest;
import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.repository.FundRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    // --- TEFAS code (E1-1) ---

    @Test
    void storesTefasCodeUpperCase() {
        FundRequest request = new FundRequest();
        request.setName("Fon");
        request.setTefasCode(" aav ");
        given(repository.existsByTefasCode("AAV")).willReturn(false);

        assertThat(service.createFund(request).getTefasCode()).isEqualTo("AAV");

        ArgumentCaptor<Fund> saved = ArgumentCaptor.forClass(Fund.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTefasCode()).isEqualTo("AAV");
    }

    @Test
    void rejectsDuplicateTefasCodeBeforeSaving() {
        FundRequest request = new FundRequest();
        request.setName("Fon");
        request.setTefasCode("AAV");
        given(repository.existsByTefasCode("AAV")).willReturn(true);

        assertThatThrownBy(() -> service.createFund(request))
                .isInstanceOf(DuplicateTefasCodeException.class)
                .extracting("code").isEqualTo("TEFAS_CODE_TAKEN");
        verify(repository, never()).save(any());
    }

    @Test
    void fundWithoutTefasCodeSkipsUniquenessCheck() {
        FundRequest request = new FundRequest();
        request.setName("Elle girilen fon");

        assertThat(service.createFund(request).getTefasCode()).isNull();
        verify(repository, never()).existsByTefasCode(any());
    }

    @Test
    void findsByTefasCodeCaseInsensitively() {
        given(repository.findByTefasCode("IJB")).willReturn(Optional.of(
                Fund.builder().id(3L).name("Karma Fon").tefasCode("IJB").build()));

        assertThat(service.getFundByTefasCode("ijb").getId()).isEqualTo(3L);
    }

    @Test
    void unknownTefasCodeThrowsNotFound() {
        given(repository.findByTefasCode("XYZ")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFundByTefasCode("xyz"))
                .isInstanceOf(FundNotFoundException.class)
                .hasMessage("Bu TEFAS koduyla fon bulunamadı: XYZ");
    }

    @Test
    void normalizationIgnoresTurkishDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            // "ijb".toUpperCase() in tr-TR would be "İJB" and never match.
            assertThat(FundService.normalizeTefasCode("ijb")).isEqualTo("IJB");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
