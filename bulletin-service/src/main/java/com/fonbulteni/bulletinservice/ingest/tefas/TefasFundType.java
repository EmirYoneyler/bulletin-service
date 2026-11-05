package com.fonbulteni.bulletinservice.ingest.tefas;

/** Value of {@code fonTipi} in TEFAS requests. A fund is only found under its own type. */
public enum TefasFundType {
    /** Securities investment funds (yatırım fonları). */
    YAT,
    /** Pension funds (emeklilik fonları). */
    EMK,
    /** Exchange-traded funds (borsa yatırım fonları). */
    BYF
}
