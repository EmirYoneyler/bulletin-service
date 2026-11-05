# TEFAS fixture'ları

Hepsi 2026-11-05 tarihinde `POST https://www.tefas.gov.tr/api/funds/fonGnlBlgSiraliGetir` uç noktasından **canlı olarak** kaydedildi (bkz. [ADR 001](../../../../../../docs/decisions/001-tefas.md)). Her dosyanın başındaki `_kayit` alanı isteği tarif eder; bu alan dışında yanıtlar değiştirilmedi.

| Dosya | İstek | Ne gösteriyor |
|---|---|---|
| `history-aav-normal.json` | AAV, 27.10.2026–04.11.2026 | Normal yanıt: 6 işlem günü, 29 Ekim tatili yok, yeniden eskiye sıralı |
| `history-empty-day.json` | AAV, 01.11.2026 (pazar) | Veri yok: `resultList: null`, `errorMessage: "Index 0 out of bounds for length 0"` |
| `history-range-too-long.json` | AAV, 07.08.2026–04.11.2026 | 1 aydan uzun aralık reddediliyor: `"Geçersiz veri: Tarih aralığı 1 ayı aşamaz"` |

Testlerdeki 5xx, zaman aşımı ve sayfalama senaryoları gerçek yanıt değildir; `MockRestServiceServer` ile testin içinde kurulur.
