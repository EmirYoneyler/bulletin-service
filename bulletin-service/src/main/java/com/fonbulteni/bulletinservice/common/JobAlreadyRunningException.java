package com.fonbulteni.bulletinservice.common;

public class JobAlreadyRunningException extends ConflictException {

    public JobAlreadyRunningException() {
        super("JOB_ALREADY_RUNNING", "Bir TEFAS veri aktarımı şu an çalışıyor. Bitince tekrar deneyin.");
    }
}
