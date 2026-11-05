# 004 · Reel getiri, TÜFE orantılama ve kıyaslar

- **Tarih:** 2026-11-05
- **Durum:** kabul edildi (canlı EVDS verisiyle doğrulama E1-5b'de)
- **Kart:** E2-2

## Bağlam
AGENT_PROMPT §7.3 reel getiri için `(1 + r) / (1 + π) − 1` formülünü verir. Kısa dönemlerde enflasyon, son aylık TÜFE değişiminin geometrik orantılanmasıyla bulunur (`π = (1 + π_ay)^(gün/30) − 1`); yılbaşından bu yana için gerçek endeks oranı kullanılır. Mevduat kıyası: `(1 + i)^(gün/365) − 1`. Bu kayıt formüllerin bıraktığı açık noktaları kapatır. Dönem getirisi kuralları ADR 011'de. Kod: `analytics.Inflation`, `BenchmarkMath`, `BenchmarkService`.

## Karar

### Hangi TÜFE ayı
- TÜİK, m ayının TÜFE'sini m+1 ayının ~3'ünde yayımlar. `asOf` ayın 3'ü ya da sonrasıysa önceki ay, değilse ondan bir önceki ay "yayımlanmış en son ay" sayılır. Bu sınıra kadar veritabanındaki en son ay kullanılır.
- Üretimde veritabanında yalnızca çekilmiş, yani yayımlanmış veri bulunur. Kural, geçmiş tarihli hesapların ileriye bakmamasını sağlar.
- Kullanılan ay sonuçla birlikte döner ve bültende gösterilir: `Inflation.note()` → "Ekim 2026 TÜFE'si kullanıldı."

### Dönem enflasyonu
| Dönem | Yöntem |
|---|---|
| 1 hafta | `(1 + π_ay)^(7/30) − 1` |
| Ay başından | `(1 + π_ay)^(ayın günü/30) − 1` |
| Yılbaşından | `TÜFE[m] / TÜFE[önceki yıl Aralık] − 1`. **İstisna:** m önceki yıldaysa (ocak başı), bu oran anlamsız olur (ör. Aralık/Aralık = 0); bunun yerine `(1 + π_ay)^(yılın günü/30) − 1` |
| 1 yıl | `TÜFE[m] / TÜFE[m − 12] − 1`, yani son açıklanan yıllık enflasyon. AGENT_PROMPT bu dönemi tanımlamıyordu |

`π_ay = TÜFE[m] / TÜFE[m − 1] − 1`. Gereken ay yoksa o dönem `null` olur, 0 değil.

### Kıyaslar
- **USD/TRY ve BIST 100:** Fon getirisiyle aynı dönem kuralları (ADR 011). BIST getiri endeksi kullanılır (ADR 003).
- **Mevduat:** `asOf`'tan önceki son haftalık TL mevduat faizi (EVDS yüzde verir, kesire çevrilir) her döneme `(1 + i)^(gün/365) − 1` ile uygulanır. Faiz 31 günden eskiyse kullanılmaz.
  - Faiz brüttür (stopaj hariç).
  - Uzun dönemlerde (yılbaşından, 1 yıl) faizin dönem boyunca sabit kaldığı varsayılır. Bu bir yaklaşımdır ve bültende "bugünkü faizle" diye anlatılmalıdır.
- **Altın:** EVDS'de yalnızca aylık (ADR 002). Haftalık kıyas yok. Son **tamamlanmış** ayın bir önceki aya ve önceki yılın Aralık ayına göre değişimi verilir. İçinde bulunulan ayın değeri kesinleşmediği için kullanılmaz.
- Kesirli üsler `double` ile hesaplanır, sonuç en kısa ondalık gösterimi üzerinden `DECIMAL64`'e çevrilir. `1.45^(365/365) − 1 = 0.45` gibi tam durumlar tam kalır.

## Sonuçlar
- Gün sayısı takvim günüdür (hafta = 7). Ay 30, yıl 365 gün kabul edilir (§7.3).
- Yılbaşından enflasyon m ayının sonuna kadar olan fiyat düzeyini ölçer, fon getirisi ise `asOf`'a kadar uzanır. Aradaki birkaç haftalık fark yaklaşım olarak kabul edildi; kullanılan ay bültende görünür.
- TÜFE baz değişimi (2003=100 → 2025=100, ADR 002): yalnızca oranlar kullanıldığı için tek bir seri içinde sorun yok. `TP.TUKFIY2025.GENEL` 12 ay öncesini içermiyorsa 1 yıllık enflasyon `null` olur; iki serinin zincirlenmesi gerekirse E1-5b'deki başlangıç tarihi kontrolüne göre ayrı kart açılır.
- Tüm kurallar elle hesaplanmış testlerle sabitlendi: `BenchmarkMathTest` 9, `InflationTest` 9, `BenchmarkServiceTest` 3.
