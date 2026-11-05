# 003 · BIST 100 kıyas verisi

- **Tarih:** 2026-11-05
- **Durum:** kabul edildi (canlı veri, EVDS anahtarıyla E1-5b'de doğrulanacak)
- **Kart:** E1-6

## Bağlam
Bültende fonların getirisi BIST 100 ile de karşılaştırılmak isteniyor. Kaynak ücretsiz ve kullanım koşullarına uygun olmalı. AGENT_PROMPT önce EVDS'ye bakılmasını söylüyor.

## Bulgular (2026-11-05)
EVDS'nin herkese açık arama servisinde (`searchResults`, "BIST 100" ve "XU100") iki güncel seri var. İkisi de `bie_mkbrgn` veri grubunda ve iş günü frekansında:

| Kod | EVDS adı |
|---|---|
| `TP.MK.F.BILESIK` | (FİYAT) BİST 100 Endeks (XU100), Kapanış Fiyatlarına Göre (Ocak 1986=0,01) |
| `TP.MK.G.BILESIK` | (GETİRİ) BİST 100 Endeks (XU100-CFNNTLTL), Kapanış Fiyatlarına Göre (27-12-1996=9,76) |

"EVDS Kullanım Şartları" (sitedeki doküman, 2026-11-05'te okundu), §3 "Bilgi Kullanımı ve Kaynak Gösterimi":
- EVDS'deki veriler **kaynak gösterilmek koşuluyla** üçüncü kişilerce kullanılabilir ve yayımlanabilir.
- Başka bir üründe kullanıldığında kullanıcılara **ek ücret olarak yansıtılmaz**.
- Dış kaynaklı veriler (BIST endeksi Borsa İstanbul kaynaklı) ilgili kuruluşun sorumluluğundadır. Yasak getirilmemiş.

## Karar
- Kaynak **TCMB EVDS**. Ayrı bir veri sağlayıcısı, anahtar veya kazıma gerekmiyor. Mevcut haftalık EVDS işi seriyi kendiliğinden çekiyor (`evds.series.bist100`).
- Seri **`TP.MK.G.BILESIK` (getiri endeksi)**. Fon fiyatları temettüleri içerdiği için adil kıyas getiri endeksiyle yapılır; fiyat endeksi fonları olduğundan iyi gösterir. Bültende "BIST 100 (getiri endeksi)" diye adlandırılmalı. Fiyat endeksi (`TP.MK.F.BILESIK`) gerekirse ayar değiştirilerek alınabilir.
- Bültenin kaynaklar bölümünde "TCMB EVDS (BIST 100: Borsa İstanbul)" yazmalı (E2-3 / E5-1).

## Sonuçlar
- BIST 100 kıyası EVDS anahtarına bağlı; anahtar yoksa diğer EVDS kıyaslarıyla birlikte bültende yer almaz.
- Bülten ücretli bir ürüne dönüşürse §3'teki "ek ücret olarak yansıtılmaz" koşulu yeniden değerlendirilmeli.
- Bu bir hukuki görüş değil; kullanım şartlarının okunmasına dayanan bir mühendislik kararı.
