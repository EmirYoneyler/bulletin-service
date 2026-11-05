# EVDS fixture'ları

EVDS veri servisi kullanıcıya özel bir anahtar istiyor ve 2026-11-05'te anahtar yoktu (bkz. [ADR 002](../../../../../../docs/decisions/002-evds.md)). Bu yüzden:

| Dosya | Kaynak | Ne gösteriyor |
|---|---|---|
| `error-403-no-key.json` | **Canlı kayıt**, 2026-11-05, anahtarsız `GET https://evds3.tcmb.gov.tr/igmevdsms-dis/series=TP.DK.USD.A.YTL&...&type=json` | Anahtar yokken dönen 403 gövdesi |
| `usdtry-daily.json` | **Elle üretildi** | Günlük seri, `gg-aa-yyyy` tarih, bir gün `null` |
| `cpi-monthly.json` | **Elle üretildi** | Aylık seri, `yyyy-A` tarih |
| `deposit-weekly-comma.json` | **Elle üretildi** | Haftalık seri, ondalık virgül |
| `empty.json` | **Elle üretildi** | Aralıkta gözlem yok |

Elle üretilen dosyaların değerleri bilinçli olarak gerçek dışıdır (ör. USD/TRY 10,0000). Biçim, EVDS'nin önceki sürümünden bilinen JSON yanıtıdır ve anahtar gelince E1-5b'de canlı yanıtla karşılaştırılmalıdır.
