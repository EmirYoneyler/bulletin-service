# 010 · `createdAt` saat dilimi

- **Tarih:** 2026-11-05
- **Durum:** kabul edildi
- **Kart:** E0-7

## Bağlam
`Fund.createdAt` bir `LocalDateTime` ve `@PrePersist` içinde `LocalDateTime.now()` ile dolduruluyordu, yani JVM'in varsayılan saat dilimiyle. Container'da bu UTC olduğu için kayıtlar İstanbul saatinden 3 saat geri yazılıyordu. Projenin kuralı: tarih ve saat `Europe/Istanbul` (AGENT_PROMPT §3).

Seçenekler:
1. Container'a `TZ=Europe/Istanbul` vermek: yalnızca o ortamı düzeltir, IDE'de veya başka bir deploy'da sorun geri gelir.
2. Açılışta `TimeZone.setDefault(...)`: tüm JVM'i etkiler, kütüphanelerin davranışını gizlice değiştirir.
3. Saat dilimini kodda açıkça vermek: `LocalDateTime.now(TimeConfig.ISTANBUL)`.

## Karar
Seçenek 3. `Fund.prePersist` artık `LocalDateTime.now(TimeConfig.ISTANBUL)` kullanıyor. JPA geri çağrısı Spring bean'i alamadığı için `Clock` yerine sabit bölge kullanılıyor. Yeni kod, bean erişimi olan her yerde `TimeConfig`'teki `Clock`'u kullanmalı.

## Sonuçlar
- Değer, ortamdan bağımsız olarak İstanbul duvar saati. Sütun tipi (`timestamp(6)`) ve `/api/funds` yanıt biçimi (ofsetsiz `LocalDateTime`) değişmedi.
- **Mevcut kayıtlara dokunulmadı.** Hangi saat dilimiyle yazıldıkları bilinmediği için (Render ve yerel ortamlar farklı olabilir) toplu düzeltme yapılmadı. Eski kayıtlarda 3 saatlik kayma olabilir.
- Türkiye 2016'dan beri yaz saati uygulamadığı için yerel saat ile UTC arasındaki fark sabit 3 saat; ileride değişirse `ZoneId` kuralları bunu kendisi karşılar.
