package com.fonbulteni.bulletinservice.portfolio;

import java.util.List;

/** Which funds a user's bulletin covers. Replaced by real per-user holdings in E6-1. */
public interface HoldingsProvider {

    List<Holding> holdingsOf(String email);
}
