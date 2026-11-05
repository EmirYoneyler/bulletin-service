# 002 · TCMB EVDS: uç nokta ve seri kodları

- **Tarih:** 2026-11-05
- **Durum:** kabul edildi (seri kodları doğrulandı; veri yanıtının biçimi anahtar gelince doğrulanacak, E1-5b)
- **Kart:** E1-5

## Bağlam
Reel getiri ve kıyaslar için TÜFE, USD/TRY, altın ve TL mevduat faizi gerekiyor (AGENT_PROMPT §7.2). Kaynak TCMB'nin Elektronik Veri Dağıtım Sistemi (EVDS). Veri servisi kullanıcıya özel ücretsiz bir anahtar istiyor. **Bu oturumda anahtar yoktu.**

## Bulgular (2026-11-05)
- `https://evds2.tcmb.gov.tr/` artık `https://evds3.tcmb.gov.tr/` adresine yönleniyor. Servis kökü değişti: `https://evds3.tcmb.gov.tr/igmevdsms-dis/`.
- Resmî "EVDS Web Servis Kılavuzu" (sitedeki Dokümanlar sayfası, `documents/showDocument?docId=8`):
  - Veri: `GET {kök}/series=KOD1-KOD2&startDate=gg-aa-yyyy&endDate=gg-aa-yyyy&type=json`. Birden çok seri `-` ile ayrılır.
  - Anahtar **`key` adlı HTTP başlığında** gider. Anahtarsız istek canlı denendi: `403 {"status":"403","message":"Required request header 'key' is not present"}`.
  - Tek istek en fazla **1000 gözlem** döner; daha uzun aralıkta bitiş tarihinden geriye 1000 gözlem gelir.
  - `frequency` verilmezse serilerin **ortak** frekansı kullanılır: günlük bir seriyle aylık bir seri aynı istekte istenirse günlük seri aylığa çevrilir.
  - Ondalık ayırıcı varsayılan olarak nokta (`decimalSeperator` parametresi verilmezse).
  - Kılavuz, verinin günde en fazla bir kez çekilmesini istiyor.
- Seri kodları, sitenin herkese açık arama servisiyle (`searchResults`, `serieList/fe`) doğrulandı:

| Ne | Kod | Ad (EVDS) | Frekans | Not |
|---|---|---|---|---|
| TÜFE endeksi | `TP.TUKFIY2025.GENEL` | Genel Endeks (`bie_tukfiy2025`, 2025=100) | aylık | AGENT_PROMPT'taki `TP.FG.J0` (2003=100) artık **"Arşiv"** olarak işaretli |
| USD/TRY | `TP.DK.USD.A.YTL` | (USD) ABD Doları (Döviz Alış) | günlük | |
| Altın | `TP.MK.KUL.YTL` | Külçe Altın Satış Fiyatı (TL/Gr) | **aylık** | EVDS'de günlük gram altın serisi bulunamadı |
| TL mevduat faizi | `TP.TRY.MT06` | Toplam (TL Mevduat, Akım, %) | haftalık (cuma) | Yeni açılan mevduatların ağırlıklı ortalama faizi, yıllık % |

## Karar
- `EvdsClient` her seriyi **ayrı istekle** çeker (frekansı korumak için), anahtarı `key` başlığında gönderir ve aralığı en fazla 3 yıllık parçalara böler (günlük seride ~780 iş günü, 1000 sınırının altında).
- Seri kodları `evds.series.cpi|usdtry|gold|deposit` ayarlarında; varsayılanlar yukarıdaki tablo.
- `EVDS_API_KEY` boşsa istemci **devre dışı** kalır. Açılışta uyarı loglanır, haftalık iş "atlandı" (`SKIPPED`) kaydı bırakır, uygulama çalışmaya devam eder (§6.9).
- Yeniden deneme yok: haftalık iş son 400 günü her hafta yeniden okur, kaçan hafta sonraki çalışmada dolar.
- Yanıt biçimi (`items[]`, `Tarih` alanı, değer alanı seri kodunun noktaları alt çizgiye çevrilmiş hâli, değer metin ya da `null`) EVDS'nin önceki sürümünden bilinen biçime göre yazıldı. Tarih `gg-aa-yyyy` (günlük ve haftalık) ya da `yyyy-A` (aylık, ayın ilk günü sayılır). Ayrıştırıcı hem `.` hem `,` ondalık ayırıcısını kabul eder.

## Sonuçlar
- **Canlı doğrulanmadı.** Anahtar gelince yapılacaklar `Blocked` → E1-5b kartında. Yanıt biçimi farklı çıkarsa yalnızca `EvdsResponseParser` ve fixture'lar değişir.
- **Altın aylık.** Haftalık bültende "altın haftalık değişim" EVDS'den hesaplanamaz. Ay başından ve yılbaşından değişim hesaplanabilir. Günlük altın kaynağı için Backlog'a E1-7 açıldı.
- **TÜFE baz yılı değişti.** 2025=100 serisinin başlangıcından önceki dönemleri kapsayan hesaplarda (ör. 1 yıllık reel getiri) eski 2003=100 seriyle zincirleme gerekebilir. Bu E2-2'nin konusu; serinin başlangıç tarihi anahtarla doğrulanmalı.
- Test fixture'ları (`fixtures/evds/`) **elle üretildi**, değerleri bilinçli olarak gerçek dışı (ör. USD/TRY 10,0000). Yalnızca 403 yanıtı canlı kayıttır.
