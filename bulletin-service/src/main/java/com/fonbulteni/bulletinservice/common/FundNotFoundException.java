package com.fonbulteni.bulletinservice.common;

public class FundNotFoundException extends NotFoundException {

    public FundNotFoundException(Long id) {
        super("FUND_NOT_FOUND", "Fon bulunamadı: " + id);
    }
}
