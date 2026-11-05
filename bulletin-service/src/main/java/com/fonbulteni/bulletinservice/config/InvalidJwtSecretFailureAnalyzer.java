package com.fonbulteni.bulletinservice.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.core.Ordered;

/**
 * Turns {@link InvalidJwtSecretException} into a readable "APPLICATION FAILED TO START" report
 * with the exact fix, instead of a nested binding stack trace.
 */
public class InvalidJwtSecretFailureAnalyzer extends AbstractFailureAnalyzer<InvalidJwtSecretException>
        implements Ordered {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidJwtSecretException cause) {
        String action = """
                JWT_SECRET ortam değişkenini en az %d baytlık rastgele bir değerle ayarlayın, \
                ör. `openssl rand -base64 48`. Değer auth-service ile aynı olmalı.
                Yalnızca yerel geliştirme için SPRING_PROFILES_ACTIVE=dev ile sabit geliştirme anahtarı kullanılabilir."""
                .formatted(JwtProperties.MIN_SECRET_BYTES);
        return new FailureAnalysis(cause.getMessage(), action, cause);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
