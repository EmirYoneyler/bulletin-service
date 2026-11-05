package com.fonbulteni.bulletinservice.portfolio;

import com.fonbulteni.bulletinservice.repository.FundRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Until per-user holdings exist (E6-1), every user follows every fund that has a TEFAS code, with no
 * unit counts, so portfolio figures are equal-weighted.
 */
@Component
public class AllTrackedFundsHoldingsProvider implements HoldingsProvider {

    private final FundRepository funds;

    public AllTrackedFundsHoldingsProvider(FundRepository funds) {
        this.funds = funds;
    }

    @Override
    public List<Holding> holdingsOf(String email) {
        return funds.findByTefasCodeIsNotNullOrderByTefasCodeAsc().stream()
                .map(fund -> new Holding(fund, null))
                .toList();
    }
}
