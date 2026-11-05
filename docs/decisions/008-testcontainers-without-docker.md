# 008 · Docker yokken entegrasyon testleri

- **Tarih:** 2026-11-05
- **Durum:** kabul edildi
- **Kart:** E0-1

## Bağlam
Veritabanı gerektiren testler Testcontainers (PostgreSQL) ile çalışır (AGENT_PROMPT §3). Testler yerel bir veritabanına bağlanmamalı. Ama `./mvnw verify` Docker'ı olmayan bir makinede de yeşil kalabilmeli.

## Karar
- Veritabanı gerektiren testler `@Tag("integration")` ve `@Testcontainers(disabledWithoutDocker = true)` taşır.
- Docker yoksa bu testler hata vermek yerine **atlanır** (JUnit raporunda "skipped" görünür).
- Testcontainers bağlantısı `@ServiceConnection` ile kurulur. Test ayarları `src/test/resources/application-test.properties` dosyasında, testler `@ActiveProfiles("test")` kullanır.
- İstenirse `-DexcludedGroups=integration` ile açıkça da dışlanabilir.

## Sonuçlar
- Docker'sız ortamda yeşil build, entegrasyon kapsamı olmadan yeşil olabilir. Bu yüzden **CI'da** (E0-5) entegrasyon testlerinin atlanmadığı kontrol edilmeli (rapordaki skipped sayısı).
- Yerelde Docker Desktop (Engine 29) ile Testcontainers 1.21.4 sorunsuz çalıştı.
