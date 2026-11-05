package com.fonbulteni.bulletinservice.common;

public class DuplicateTefasCodeException extends ConflictException {

    public DuplicateTefasCodeException(String tefasCode) {
        super("TEFAS_CODE_TAKEN", "Bu TEFAS koduyla kayıtlı bir fon zaten var: " + tefasCode);
    }
}
