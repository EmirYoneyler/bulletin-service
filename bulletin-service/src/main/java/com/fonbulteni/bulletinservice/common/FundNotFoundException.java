package com.fonbulteni.bulletinservice.common;

public class FundNotFoundException extends NotFoundException {

    public FundNotFoundException(Long id) {
        super("FUND_NOT_FOUND", "Fon bulunamadı: " + id);
    }

    private FundNotFoundException(String message) {
        super("FUND_NOT_FOUND", message);
    }

    public static FundNotFoundException byTefasCode(String tefasCode) {
        return new FundNotFoundException("Bu TEFAS koduyla fon bulunamadı: " + tefasCode);
    }
}
