# 011 · Getiri, oynaklık ve maksimum düşüş hesap kuralları

- **Tarih:** 2026-11-05
- **Durum:** kabul edildi
- **Kart:** E2-1

## Bağlam
Bültendeki fon sayıları deterministik olarak Java'da hesaplanır (AGENT_PROMPT §6.1, §7.3). Formüller AGENT_PROMPT'ta var. Bu kayıt, formüllerin bıraktığı açık noktalarda verilen kararları tutar. Kod: `analytics.ReturnMath`, `analytics.Periods`, `analytics.FundStatsService`.

## Karar
- **Biçim:** Sonuçlar kesir olarak tutulur (`0.032` = %3,2), `BigDecimal` ve `MathContext.DECIMAL64` ile. Gösterim için yuvarlama (`HALF_UP`, 2 hane) yalnızca biçimlendirmede yapılır (E2-3).
- **Dönem getirisi:** `r = P_son / P_ilk − 1`.
  - `P_ilk`: dönemin ilk gününden **kesin önceki** son kapanış.
  - `P_son`: `asOf` gününde ya da öncesindeki son kapanış; dönemin içinde olmalı.
  - Hafta: `asOf − 6` gün ile başlar. Cuma `asOf` için başlangıç fiyatı geçen cumanın kapanışıdır; cuma tatilse ondan önceki son işlem günü.
  - Ay başından: ayın 1'i. Yılbaşından: 1 Ocak. Bir yıl: `asOf − 1 yıl + 1 gün`.
- **Eksik veri `null`, asla 0:**
  - Başlangıç fiyatı yoksa sonuç `null`.
  - Dönem içinde hiç kapanış yoksa sonuç `null`.
  - **Başlangıç fiyatı dönem başından 14 günden eskiyse** sonuç `null`. Türkiye'de en uzun piyasa kapanışı (köprülü bayram) 9 gün; daha büyük bir boşluk verinin eksik olduğunu gösterir ve eski bir fiyatla hesaplanan getiri yanıltıcı olur.
- **Oynaklık:** `asOf`'a kadarki son 64 kapanışın 63 günlük log getirisinin **örneklem** standart sapması (`n − 1`), × √252. 64 kapanıştan azsa `null`. `BigDecimal`'da logaritma olmadığı için `ln` `double` ile alınır. Fiyatlar en çok 6 ondalık hanelidir, `double` hassasiyeti bunun çok üstünde. Ortalama, varyans ve karekök `DECIMAL64` ile hesaplanır.
- **Maksimum düşüş:** Son bir yıl içindeki kapanışlarda, o ana kadarki zirveye göre en büyük düşüş. Sonuç negatif ya da sıfır bir kesirdir (`-0.25` = %25 düşüş). Hiç düşmediyse `0`, iki kapanıştan azsa `null`.
- **Veri penceresi:** `FundStatsService` fon başına `[asOf − 1 yıl − 15 gün, asOf]` aralığını tek sorguyla okur. Bu aralık 1 yıllık getirinin başlangıç fiyatını (14 günlük tolerans dahil) ve oynaklık için 64 kapanışı kapsar. `asOf`'tan sonraki fiyatlar hiçbir hesaba girmez.

## Sonuçlar
- Her kural, beklenen değeri elle hesaplanmış testlerle sabitlendi (`ReturnMathTest` 24, `FundStatsServiceTest` 3).
- 14 günlük tolerans AGENT_PROMPT'ta yok; bilinçli bir ekleme. Gerekirse `ReturnMath.MAX_START_GAP_DAYS` değiştirilir.
- Reel getiri ve kıyaslar (TÜFE, mevduat) E2-2'de bu kurallar üzerine kurulacak.
