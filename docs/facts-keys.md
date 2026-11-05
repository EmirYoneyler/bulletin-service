# Bülten olgu (fact) anahtarları

Bültendeki her sayı burada listelenen bir anahtara karşılık gelir (AGENT_PROMPT §4.2, §7.4). LLM metne sayı değil yer tutucu yazar, örneğin `{{fund.AAV.return.1w}}`. Java yer tutucuyu `FactFormatter` ile biçimlendirilmiş değerle değiştirir. Kodda tek kaynak `analytics.facts.FactKey`; bu tablo onunla aynı tutulur (`FactKeyTest` kontrol eder).

`{KOD}` bir TEFAS fon kodudur (3–10 büyük harf veya rakam).

## Biçim

| Birim | Örnek | Kural |
|---|---|---|
| `PERCENT` | `%3,2`, `%-0,9`, `%0,0` | Değer kesirdir (`0.032`). Gösterimde ×100, 1 ondalık, `HALF_UP`. Sıfıra yuvarlanan değer eksi işaretsiz yazılır |
| `TRY` | `1.250,50 TL` | 2 ondalık, binlik ayırıcı nokta |
| `INDEX` | `1.234,56` | 2 ondalık |
| eksik | `veri yok` | Değer hesaplanamadıysa. Asla `0` yazılmaz |

Yön (`UP`/`DOWN`/`FLAT`) **gösterilen** değere göre belirlenir: `%0,0` görünen her değer `FLAT` sayılır. Böylece metin "%0,0 yükseldi" diyemez.

## Fon

| Anahtar | Birim | Kaynak | Yöntem | Açıklama |
|---|---|---|---|---|
| `fund.{KOD}.return.1w` | PERCENT | TEFAS | `return.simple` | Haftalık getiri (önceki cuma → bu cuma) |
| `fund.{KOD}.return.mtd` | PERCENT | TEFAS | `return.simple` | Ay başından bu yana getiri |
| `fund.{KOD}.return.ytd` | PERCENT | TEFAS | `return.simple` | Yılbaşından bu yana getiri |
| `fund.{KOD}.return.1y` | PERCENT | TEFAS | `return.simple` | Son bir yıllık getiri |
| `fund.{KOD}.real.ytd` | PERCENT | COMPUTED | `return.real.cpi` | Yılbaşından bu yana reel getiri (TÜFE) |
| `fund.{KOD}.vol.63d` | PERCENT | COMPUTED | `volatility.log.63d.annualized` | Yıllıklandırılmış oynaklık, son 63 işlem günü |
| `fund.{KOD}.drawdown.1y` | PERCENT | COMPUTED | `drawdown.max` | Son bir yıldaki en büyük düşüş |

## Portföy

E6-1'e kadar adet bilgisi yok; portföy, izlenen fonların **eşit ağırlıklı** ortalamasıdır ve bültende bu açıkça yazılır (`BulletinFacts.equalWeighted`).

| Anahtar | Birim | Kaynak | Yöntem | Açıklama |
|---|---|---|---|---|
| `portfolio.return.1w` | PERCENT | COMPUTED | `portfolio.equal_weight` | Haftalık ortalama getiri |
| `portfolio.return.mtd` | PERCENT | COMPUTED | `portfolio.equal_weight` | Ay başından bu yana ortalama getiri |
| `portfolio.return.ytd` | PERCENT | COMPUTED | `portfolio.equal_weight` | Yılbaşından bu yana ortalama getiri |
| `portfolio.real.1w` | PERCENT | COMPUTED | `return.real.cpi` | Haftalık reel getiri (aylık TÜFE haftaya orantılanır) |

## Kıyaslar (TCMB EVDS)

| Anahtar | Birim | Kaynak | Yöntem | Açıklama |
|---|---|---|---|---|
| `bench.USDTRY.return.1w` | PERCENT | EVDS | `return.simple` | Dolar/TL haftalık değişim |
| `bench.USDTRY.return.mtd` | PERCENT | EVDS | `return.simple` | Dolar/TL ay başından bu yana |
| `bench.USDTRY.return.ytd` | PERCENT | EVDS | `return.simple` | Dolar/TL yılbaşından bu yana |
| `bench.BIST100.return.1w` | PERCENT | EVDS | `return.simple` | BIST 100 getiri endeksi, haftalık |
| `bench.BIST100.return.mtd` | PERCENT | EVDS | `return.simple` | BIST 100 getiri endeksi, ay başından |
| `bench.BIST100.return.ytd` | PERCENT | EVDS | `return.simple` | BIST 100 getiri endeksi, yılbaşından |
| `bench.DEPOSIT.return.1w` | PERCENT | COMPUTED | `deposit.compound` | Güncel TL mevduat faiziyle bir hafta |
| `bench.DEPOSIT.return.mtd` | PERCENT | COMPUTED | `deposit.compound` | Güncel TL mevduat faiziyle ay başından |
| `bench.DEPOSIT.return.ytd` | PERCENT | COMPUTED | `deposit.compound` | Güncel TL mevduat faiziyle yılbaşından |
| `bench.GOLD.return.mom` | PERCENT | EVDS | `return.monthly` | Külçe altın, son tamamlanan ayda aylık değişim |
| `bench.GOLD.return.ytd` | PERCENT | EVDS | `return.monthly` | Külçe altın, yılbaşından son tamamlanan aya |
| `bench.CPI.month` | PERCENT | EVDS | `cpi.monthly` | Son açıklanan aylık enflasyon (hangi ay olduğu `notes`'ta) |
| `bench.CPI.ytd` | PERCENT | COMPUTED | `cpi.ratio` | Yılbaşından bu yana enflasyon |

Yöntemlerin ayrıntısı: ADR 011 (getiri, oynaklık, düşüş), ADR 004 (reel getiri, TÜFE, mevduat, altın), ADR 003 (BIST 100).
