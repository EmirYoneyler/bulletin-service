# Kanban: Fon Bülteni Kendini Yazsın

> Kurallar, teknik rehber ve çalışma protokolü: [`AGENT_PROMPT.md`](./AGENT_PROMPT.md) (özellikle §6 Değişmez kurallar ve §9 Kanban protokolü).
> Aynı anda yalnızca **1** kart `In Progress` olabilir. Her kart bittiğinde: `./mvnw -q verify` → bu dosyayı güncelle → commit → push.
> Kart biçimi: `ID · Başlık` + _Bağımlı:_ + _Rehber:_ + kabul kriterleri. Taşırken kartı bütün halinde taşı.
> Yeni kart açarken ID'yi epiğin sıradaki numarasıyla ver (ör. E1-7).

---

## 🔴 Blocked

### E1-5b · EVDS canlı doğrulama
_Bağımlı: E1-5 · Engel: EVDS API anahtarı · Açıldı: 2026-11-05_
- **Ne gerekiyor:** Kişisel, ücretsiz TCMB EVDS API anahtarı.
- **Nereden alınır:** https://evds3.tcmb.gov.tr → üye ol (e-posta doğrulaması) → "Benim Sayfam" → kullanıcı adı → "Profilim" → sayfanın altında "API Key Kopyala".
- **Nereye konacak:** Kök `.env` dosyasında `EVDS_API_KEY=...` (git'e girmez). Deploy'da aynı adla ortam değişkeni. Koda veya `application.properties`'e **yazılmaz**.
- **Engel kalkınca ilk iş:**
  - [ ] `cd bulletin-service && EVDS_API_KEY=... ./mvnw test -Plive -Dtest=EvdsClientLiveTest` yeşil (5 seri: TÜFE, USD, altın, mevduat, BIST 100; son 120 gün)
  - [ ] Bir seri için gerçek JSON yanıtı kaydedilip `fixtures/evds/` altına eklendi; elle üretilen fixture'larla biçim farkı varsa `EvdsResponseParser` ve ADR 002 güncellendi
  - [ ] `docker compose up` sonrası `POST /api/admin/jobs/weekly-macro/run` → `SUCCESS`, `macro_point` dolu
  - [ ] `TP.TUKFIY2025.GENEL` serisinin başlangıç tarihi not edildi (E2-2'de 2003=100 seriyle zincirleme gerekip gerekmediği için)

---

## 🟡 In Progress

_(boş)_

---

## 🔵 To Do

### E2-1 · FundStatsService
_Bağımlı: E1-2 · Rehber: §7.3_
- [ ] 1h / ay başından / yılbaşından / 1y getiri, 63 günlük yıllıklandırılmış oynaklık, maksimum düşüş
- [ ] "Dönem başından önceki son işlem günü" kuralı ve tatil/eksik gün davranışı testli
- [ ] Elle hesaplanmış beklenen değerlerle ≥ 10 birim testi. Eksik veride `null`, 0 değil

---

## ⚪ Backlog

### E1 · Veri alımı (devam)

#### E1-7 · Günlük altın fiyatı kaynağı
_Kaynak: E1-5 araştırması · Rehber: §6.6, §7.2_
- [ ] EVDS'de altın yalnızca aylık (`TP.MK.KUL.YTL`). Haftalık "altın" kıyası için ücretsiz ve kullanım koşullarına uygun günlük gram altın (TL) kaynağı araştırıldı. Karar ADR'de
- [ ] Uygun kaynak yoksa bültende altın yalnızca ay başından / yılbaşından kıyasında gösteriliyor ve README "Bilinen sınırlar"a yazıldı

### E2 · Deterministik analitik

#### E2-2 · Reel getiri ve kıyaslar
_Bağımlı: E1-5, E2-1 · Rehber: §7.3_
- [ ] `BenchmarkService`: USD/TRY, altın, mevduat (ve varsa BIST100) dönem getirisi
- [ ] Reel getiri, TÜFE orantılama yöntemi `docs/decisions/004-real-return.md` dosyasında
- [ ] Kullanılan TÜFE ayı sonuçla birlikte dönüyor ("Ekim 2026 TÜFE'si kullanıldı")

#### E2-3 · BulletinFacts + FactFormatter
_Bağımlı: E2-1 · Rehber: §7.4_
- [ ] `Fact` ve `BulletinFacts` record'ları, anahtar şeması `docs/facts-keys.md` dosyasında
- [ ] `FactsAssembler`: kullanıcı + hafta → facts (fonlar, portföy, kıyaslar, en çok yükselen/düşen, kaynak listesi)
- [ ] `FactFormatter` Türkçe biçim testleri (`%3,2`, `%-0,9`, `1.250,50 TL`, `null` → "veri yok")
- [ ] `GET /api/admin/bulletins/facts/preview?email=&week=` JSON önizleme

### E3 · Bülten üretimi (LLM, ücretsiz)

#### E3-1 · LlmClient soyutlaması
_Rehber: §4.1_
- [ ] `LlmClient`, `LlmRequest`, `LlmResponse` (token, süre, model)
- [ ] `TemplateLlmClient`: facts'teki yön ve sıralama bilgisiyle yer tutuculu, akıcı Türkçe metin üretiyor (en az 3 farklı cümle kalıbı, tekdüze görünmesin)
- [ ] `OpenAiCompatibleLlmClient`: `llm.base-url`, `llm.model`, `llm.api-key` (boş olabilir), `llm.timeout`, JSON modu. Ollama, Gemini ve Groq için örnek ayarlar `.env.example`'da
- [ ] `llm.provider=template|ollama|openai-compatible` ile bean seçimi. Varsayılan `template`
- [ ] `MockRestServiceServer` ile OpenAI formatı testleri (başarı, 429, zaman aşımı)

#### E3-2 · Writer adımı ve yer tutucu renderer'ı
_Bağımlı: E2-3, E3-1 · Rehber: §4.2, §7.6_
- [ ] `prompts/writer.v1.md`: rol, kurallar, anahtar listesi + açıklamalar + yönler (değer yok), JSON çıktı şeması
- [ ] `WriterStep`: facts → prompt → `LlmClient` → JSON parse → `WriterOutput`
- [ ] `PlaceholderRenderer`: `{{key}}` → `FactFormatter` çıktısı
- [ ] Ollama ile en az bir gerçek çalıştırma yapılabildiyse çıktı örneği `docs/samples/` altına kondu, yapılamadıysa Notlar'a yazıldı

#### E3-3 · Bulletin ve çalışma kaydı
_Rehber: §5.1_
- [ ] `V7`: `app_user_ref`, `bulletin`, `bulletin_run` tabloları. `bulletin(user_id, week_start)` unique
- [ ] `BulletinRunRecorder`: her adım için süre, token, deneme ve sonuç

### E4 · Doğrulama ve uyum

#### E4-1 · NumberGuard
_Bağımlı: E3-2 · Rehber: §4.3, §7.5_
- [ ] Kural 1: render öncesi metinde rakam yok. Kural 2: bilinmeyen anahtar yok. Kural 3: yön tutarlılığı
- [ ] `Violation` listesi. İhlaller bir sonraki denemede prompt'a geri bildirim olarak ekleniyor
- [ ] ≥ 12 test: rakam sızıntısı (`3,2`, `üç virgül iki` hariç tutulur), uydurma anahtar, "düştü" + pozitif değer, temiz metin

#### E4-2 · ComplianceGuard
_Rehber: §6.3, §7.5_
- [ ] `compliance/forbidden.txt` + ek varyasyonlu regex. Yer tutucu içi ve fon kodları taranmıyor
- [ ] İhlalli cümle önce eleştirmene (`compliance.critic.enabled=true` ise, `prompts/critic.v1.md`), hâlâ ihlalliyse siliniyor
- [ ] Sabit yasal uyarı ekleniyor
- [ ] ≥ 15 test: "almanızı öneririz", "kaçırmayın", "yükselecek", "kesinlikle", yanlış pozitif örnekleri ("altın", "Sat. günü" gibi)

#### E4-3 · BulletinPipeline
_Bağımlı: E3-3, E4-1, E4-2_
- [ ] facts → writer (≤ 3 deneme) → NumberGuard → Compliance → NumberGuard → render. Başarısızlıkta şablon yedeği (`fallback=true`)
- [ ] Şablon yedeği de guard'lardan geçemezse `FAILED_VALIDATION` (bu bir bug'dır, alarm logu)
- [ ] `POST /api/admin/bulletins/generate?email=&week=`
- [ ] §8'deki 4 uçtan uca senaryo testi (sahte LlmClient)

### E5 · Teslimat

#### E5-1 · PdfRenderer
_Bağımlı: E4-3 · Rehber: §7.8_
- [ ] `PdfRenderer` arayüzü + `ITextPdfRenderer`, gömülü TTF (lisans dosyasıyla birlikte)
- [ ] Bölümler: başlık, özet, fon tablosu (sayılar facts'ten), dikkat çekenler, kıyas, kaynaklar, uyarı
- [ ] Test: PDF'ten metin geri okunuyor, Türkçe karakterler ve uyarı metni var
- [ ] `docs/sample-bulletin.pdf` (demo verisiyle) + README için PNG ön izleme

#### E5-2 · E-posta gönderimi
_Rehber: §7.8_
- [ ] Spring Mail, `SMTP_HOST/PORT/USER/PASSWORD/FROM`. Ayarlı değilse PDF `out/bulletins/` altına yazılıyor
- [ ] HTML gövde (Thymeleaf veya basit şablon): özet + "PDF ekte". Uyarı metni gövdede de var
- [ ] GreenMail ile test. Yerelde Mailpit'te görüldü (mümkünse)

#### E5-3 · Pazartesi işi
_Bağımlı: E5-1, E5-2_
- [ ] `MondayBulletinJob` (`0 0 8 * * MON`, ShedLock): `bulletin_enabled` kullanıcılar için üret ve gönder
- [ ] İdempotent: aynı hafta için `SENT` varsa atla. `FAILED_DELIVERY` varsa bir kez yeniden dene
- [ ] Admin tetikleyicisi + test

### E6 · Kişiselleştirme

#### E6-1 · İzleme listesi ve portföy
_Bağımlı: E0-6, E2-3_
- [ ] `V8`: `user_holding`. `app_user_ref` JWT'den ilk istekte oluşturuluyor
- [ ] `GET/POST/DELETE /api/me/holdings` (`units` boş = sadece izleme), doğrulama + testler
- [ ] Facts'e portföy değeri, haftalık TL ve yüzde değişim. Adetsizse eşit ağırlık notu
- [ ] `PATCH /api/me/settings` (bülten aç/kapa)

### E7 · Zenginleştirme

#### E7-1 · Fon portföy dağılımı
- [ ] TEFAS `BindHistoryAllocation` veya KAP aylık raporları araştırıldı. Karar `docs/decisions/005-allocation.md` dosyasında
- [ ] `V9`: `fund_allocation`. Aylık değişim (ör. hisse %15 → %30) deterministik hesaplanıp facts'e kaynak bağlantısıyla ekleniyor
- [ ] Bültende "Dikkat çekenler" altında, eşik üstü değişimler (≥ 5 puan)

#### E7-2 · Haber bağlamı (isteğe bağlı)
- [ ] Yalnızca kaydedilmiş gerçek kaynaklar (KAP bildirimi / TCMB duyurusu / RSS). Her öğe başlık + tarih + bağlantı
- [ ] LLM yalnızca verilen başlıkları özetleyebilir, yeni olgu ekleyemez. Kaynak yoksa bölüm yok
- [ ] Testler: kaynak yokken bölümün hiç çıkmaması

#### E7-3 · Çok rollü yapı değerlendirmesi
_Bağımlı: E8-3_
- [ ] Tek writer ile writer + yorumcu + eleştirmen ayrımı eval setinde karşılaştırıldı (guard başarısı, süre, okunabilirlik notu)
- [ ] `docs/decisions/006-multi-agent.md`. Mimari yalnızca ölçülebilir kazanç varsa bölündü

### E8 · Dokümantasyon, gözlemlenebilirlik, deploy

#### E8-1 · README
- [ ] Tek cümlelik ürün tanımı, örnek PDF ön izlemesi, Mermaid mimari çizimi, servis haritası (auth → bulletin → pdf)
- [ ] Hızlı başlangıç (anahtarsız), LLM seçenekleri tablosu (template / ollama / ücretsiz uzak sağlayıcı), ortam değişkenleri tablosu
- [ ] "Tasarım kararları": yer tutucu yaklaşımı, guard'lar, neden ücretli LLM yok, SPK dili, TEFAS nezaket kuralları. "Bilinen sınırlar" ve "Güvenlik"

#### E8-2 · Gözlemlenebilirlik
- [ ] Actuator `health`, `info`, `metrics`. Micrometer sayaç ve zamanlayıcıları: `bulletin.generated`, `bulletin.fallback`, `guard.violations`, `llm.duration`, `ingest.rows`
- [ ] `GET /api/admin/bulletin-runs?limit=` ve `GET /api/admin/ingest-runs`

#### E8-3 · Değerlendirme seti
_Bağımlı: E4-3_
- [ ] `src/test/resources/eval/` altında 5 sabit hafta (yükselen, düşen, karışık, eksik veri, tek fon)
- [ ] `-Peval` profili seçilen sağlayıcıyla çalışıp `docs/eval-report.md` dosyasını üretiyor (guard başarı oranı, deneme sayısı, süre, token)
- [ ] Şablon ve Ollama sonuçları rapora işlendi (Ollama erişilemediyse not düşüldü)

#### E8-4 · Deploy
- [ ] Hedef seçildi (Render / Fly / Railway ücretsiz katman). `DATABASE_URL` → JDBC dönüşümü, `sslmode=require`, internal host teşhisi `docs/decisions/007-deploy.md` dosyasında
- [ ] Deploy'da `llm.provider=template` veya ücretsiz uzak sağlayıcı (Ollama bu katmanlarda çalışmaz)
- [ ] Canlı URL README'de. Panel erişimi gerekiyorsa `Blocked` + adım adım ne yapılacağı

#### E8-5 · Sürüm etiketi
- [ ] `v0.1.0` etiketi + GitHub Release notu (özellikler, demo PDF, bilinen sınırlar). Etiketleme yalnızca DoD (AGENT_PROMPT §11) sağlanınca

#### E8-6 · Secret rotasyonu
- [ ] README "Güvenlik": git geçmişindeki eski `jwt.secret` ve DB şifresi üretimde kullanılmamalı ve döndürülmeli. Kullanıcıya bırakılan adım `Blocked`'a yazıldı

---

## ✅ Done

### E1-6 · BIST100 kıyas verisi
_Başladı: 2026-11-05 · Bitti: 2026-11-05 · e71afde_
- [x] Ücretsiz ve kullanım koşullarına uygun bir kaynak araştırıldı (EVDS'de endeks serisi var mı önce oraya bak). Karar `docs/decisions/003-bist.md` dosyasında. EVDS'de `TP.MK.G.BILESIK` (BIST 100 getiri endeksi, iş günü) seçildi; EVDS kullanım şartları kaynak gösterilerek ve ek ücret alınmadan yayımlamaya izin veriyor. Seri `evds.series.bist100` olarak haftalık EVDS işine eklendi
- [x] ~~Uygun kaynak yoksa kıyaslardan çıkarıldı ve README "Bilinen sınırlar"a yazıldı~~ Uygun kaynak bulundu, çıkarmak gerekmedi. README "Bilinen sınırlar"a BIST'in de EVDS anahtarına bağlı olduğu yazıldı

_Elle (compose, anahtarsız): `POST /api/admin/jobs/weekly-macro/run` → `SKIPPED`, `targetCount: 5`. Canlı veri E1-5b'de doğrulanacak._

### E1-5 · EvdsClient (TCMB)
_Rehber: §7.2 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · 32ea04a, ee28640, 10a2e32_
- [x] Seri kodları EVDS'de doğrulandı: TÜFE endeksi, USD/TRY, gram altın, TL mevduat faizi. `docs/decisions/002-evds.md` + `evds.series.*`. TÜFE `TP.TUKFIY2025.GENEL` (eski `TP.FG.J0` arşivde), USD `TP.DK.USD.A.YTL`, altın `TP.MK.KUL.YTL` (**aylık**, günlük yok → E1-7), mevduat `TP.TRY.MT06`. Doğrulama sitenin herkese açık arama servisiyle, anahtarsız
- [x] Anahtar header'da (`EVDS_API_KEY`). Anahtar yoksa `EvdsClient` bean'i "disabled" moduna geçiyor ve uyarı logluyor
- [x] `V6`: `macro_point` tablosu. `WeeklyMacroJob` (`0 0 7 * * MON`) + admin tetikleyicisi (`POST /api/admin/jobs/weekly-macro/run`, ayrı `evds-ingest` kilidi). Anahtarsız çalışma `SKIPPED` olarak kaydediliyor
- [x] Fixture testleri (EVDS'nin gerçek yanıt biçimiyle: tarih `dd-MM-yyyy` veya `yyyy-M`, ondalık ayırıcı). Anahtar olmadığı için veri fixture'ları **elle üretildi** (yalnızca 403 canlı kayıt). Testler `31-02` gibi geçersiz tarihin sessizce 28 Şubat'a çevrildiğini yakaladı; ayrıştırıcı artık STRICT
- [x] Anahtar yoksa kart **Done** olabilir, ama canlı doğrulama için `Blocked`'a ayrı bir "E1-5b · EVDS canlı doğrulama" kartı açılır (kullanıcıdan anahtar). E1-5b açıldı; `EvdsClientLiveTest` hazır

Alt görevler:
1. [x] Araştırma (EVDS3, resmî kılavuz, seri kodları), ADR 002, fixture'lar, E1-7 kartı · 32ea04a
2. [x] `EvdsClient` + ayarlar + devre dışı mod + yanıt ayrıştırıcı + testler (14 test) · ee28640
3. [x] `V6` `macro_point` + `MacroIngestService` + `WeeklyMacroJob` + admin tetikleyicisi + E1-5b · 10a2e32

_Elle (compose, anahtarsız): açılışta "EVDS_API_KEY is not set" uyarısı; `POST /api/admin/jobs/weekly-macro/run` → 200 `SKIPPED`; V6 mevcut volume'de uygulandı, E1-4'ün `ingest_run` satırları yeniden adlandırılan sütunlarla korundu. **Canlı EVDS verisi doğrulanmadı** (E1-5b)._

### E1-4 · Günlük fiyat işi + backfill
_Bağımlı: E1-1, E1-2, E1-3 · Rehber: §7.9 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · 8790233, 6841be7, 1d41878_
- [x] ShedLock (JDBC) eklendi, `V4` shedlock tablosu. Sürüm 6.10.0 (7.x Spring 7 istiyor); kilit zamanı veritabanı saatinden (`usingDbTime`)
- [x] `DailyPriceJob` (`0 30 19 * * MON-FRI`, İstanbul): `tefas_code`'u olan her fon için son 5 iş gününü upsert ediyor. Bir fondaki hata diğerlerini durdurmuyor. Emeklilik fonları `EMK` tipiyle çekiliyor
- [x] `POST /api/admin/jobs/daily-price/run` ve `POST /api/admin/ingest/tefas?code=&from=&to=` (backfill). Üç yol da aynı `tefas-ingest` kilidini kullanıyor; kilit doluysa 409 `JOB_ALREADY_RUNNING`. Backfill en fazla 731 gün, bugünden ileri tarih yok
- [x] Özet log: fon sayısı, yazılan satır, atlanan, hatalar. `ingest_run` kaydı (`V5`). "Atlanan" iki sayaçla tutuluyor: değeri değişmediği için yazılmayan satır (`rows_unchanged`) ve o gün atlanan fon (`funds_failed` + `errors`)

Alt görevler:
1. [x] ShedLock 6.10 (7.x Spring 7 istiyor) + `V4` + kilit/zamanlama ayarı · 8790233
2. [x] `V5` `ingest_run` + `TefasPriceIngestService` (fon başına hata yalıtımı, sayaçlar) + testler · 6841be7
3. [x] `DailyPriceJob` + admin uç noktaları (aynı kilitle, meşgulse 409) + testler + elle deneme · 1d41878

_Elle (compose, dev, canlı TEFAS): günlük iş 6 fon (5 gerçek + eski test kaydı ZZ9, boş döndü, hata sayılmadı), 25 satır yazdı, ~7,6 sn sürdü. Çalışırken gönderilen backfill 409 aldı. AAV Ekim backfill'i 21 işlem günü buldu (29 Ekim tatil): 20 yazıldı, 1 değişmedi (30 Ekim günlük işten zaten vardı). Kaydedilen fiyatlar E1-3 fixture'larıyla aynı._

### E1-3 · TefasClient
_Rehber: §7.1, §6.6 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · e3fd9ec, 454a9b8, 68c715f_
- [x] Uç nokta araştırıldı. İstek/yanıt biçimi, çerez/WAF davranışı ve sınırlar `docs/decisions/001-tefas.md` dosyasında. Yeni uç nokta: `POST /api/funds/fonGnlBlgSiraliGetir` (JSON); eski `BindHistoryInfo` kapalı
- [x] `TefasClient.fetchHistory(code, from, to)`: aralığı ~~≤ 90 günlük~~ **≤ 1 aylık** parçalara böler (servis 1 aydan uzun aralığı reddediyor, ADR 001), istekler arası ≥ 1,5 sn (ayrı çağrılar arasında da), User-Agent, zaman aşımları, 3 denemeli üstel geri çekilme (yalnızca 5xx, 429, zaman aşımı). Emeklilik fonları için `TefasFundType.EMK`
- [x] Yanıt → `TefasPriceDto` (~~epoch ms~~ yeni serviste `tarih` zaten `yyyy-MM-dd` metni → `LocalDate`, sayı alanları `BigDecimal`)
- [x] `MockRestServiceServer` + `fixtures/tefas/*.json` testleri: normal yanıt, boş veri, 5xx sonrası başarılı deneme, kalıcı hata. Fixture'lar canlı yanıtlardan kaydedildi. Ek: loopback sunucuyla `Content-Length` regresyon testi
- [x] `@Tag("live")` canlı test (varsayılan kapalı, `./mvnw test -Plive`). Ajan ortamından canlı sonuç Notlar'a yazıldı: AAV (YAT) ve AAJ (EMK) için son 14 gün geldi

Alt görevler:
1. [x] Araştırma, ADR 001, canlı kaydedilmiş fixture'lar · e3fd9ec
2. [x] `TefasClient` + ayarlar + `MockRestServiceServer` testleri (17 test) · 454a9b8
3. [x] `@Tag("live")` test, varsayılan dışlama, canlı sonuç Notlar'a · 68c715f

### E1-2 · FundPrice tablosu
_Rehber: §5.1 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · c922aa6_
- [x] `V3`: `fund_price` tablosu, `UNIQUE(fund_id, price_date)`, `price_date` indeksli. Ek: `price > 0` ve `investor_count >= 0` kontrolleri, fon silinince fiyatları da siliniyor (`on delete cascade`)
- [x] `FundPriceRepository`: aralık sorgusu (iki uç dahil, eskiden yeniye), "verilen tarihten önceki son fiyat" (kesin önce; hafta sonu/tatil satırı olmadığı için kendiliğinden atlanıyor), upsert (`ON CONFLICT DO UPDATE`, native query). Upsert değer değişmediyse satıra dokunmuyor ve 0 dönüyor; böylece ingest işleri gerçekten yazılan satırı sayabilir
- [x] Testcontainers ile repository testleri (`FundPriceRepositoryTest`, 14 test)

_Elle (compose): mevcut volume'de V3 uygulandı (`1:BASELINE, 2:SQL, 3:SQL`), `ddl-auto=validate` ile uygulama açıldı, `GET /api/funds` → 200._

### E1-1 · Fon kimliği: TEFAS kodu
_Bağımlı: E0-4 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · 4cb12bc, 771520a_
- [x] `V2`: `funds.tefas_code VARCHAR(10) UNIQUE NULL`. Entity ve DTO'lar güncellendi. Kod büyük harfe çevrilip saklanıyor; aynı kod ikinci kez → 409 `TEFAS_CODE_TAKEN`
- [x] `GET /api/funds/by-code/{code}`. Mevcut uç noktalar ve testleri kırılmadı
- [x] `dev` profilinde örnek seed: 5 gerçek TEFAS fon kodu (farklı kategorilerden, kodlar TEFAS'tan doğrulandı). AAV (hisse), AHU (borçlanma araçları), AAL (para piyasası), IJB (karma), AAJ (emeklilik/OKS); kaynak ve tarih `DevFundSeeder` içinde

Alt görevler:
1. [x] `V2` + entity/DTO + `by-code` uç noktası + 409 + testler · 4cb12bc
2. [x] `dev` seed (doğrulanmış 5 kod) + Notlar · 771520a

_Elle (compose, dev): beş kodun hepsi `by-code` ile 200, log "Dev seed: 5 fund(s) inserted"._

### E0-7 · `createdAt` saat dilimi
_Rehber: §3 (Tarih / saat) · Kaynak: E0-4 elle doğrulaması · Başladı: 2026-11-05 · Bitti: 2026-11-05 · e20cdf3_
- [x] `Fund.prePersist` `LocalDateTime.now()` ile JVM'in varsayılan saat dilimini kullanıyor; container'da bu UTC (E0-4'te `createdAt` İstanbul saatinden 3 saat geri yazıldı). `Europe/Istanbul` ile tutarlı hâle getirildi: `LocalDateTime.now(TimeConfig.ISTANBUL)`
- [x] Mevcut kayıtlar ve `/api/funds` yanıt biçimi kırılmadı. Karar ADR'de ([010](docs/decisions/010-created-at-timezone.md)). Eski kayıtlar bilinmeyen saat dilimiyle yazıldığı için toplu düzeltilmedi

_Elle (compose): container saati 14:53 UTC iken yeni kaydın `createdAt` değeri 17:53._

### E0-6 · JWT güvenliği
_Bağımlı: E0-4 · Rehber: §7.7 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · bda2984, ddd734a_
- [x] `JwtAuthFilter` + `SecurityConfig` yetki matrisi (GET funds açık, yazma ve `/api/admin/**` ADMIN, `/api/me/**` oturumlu). Listede olmayan her yol varsayılan olarak oturum istiyor
- [x] `dev` profilinde `POST /api/dev/token?email=&role=` test token'ı üretiyor. Başka profilde yol 404; açılışta uyarı logu
- [x] `@WebMvcTest` ile 401/403/200 senaryoları (`SecurityConfigTest` 17, `DevTokenControllerTest` 6 test)
- [x] README'ye "API artık yazma işlemleri için token istiyor" notu (yetki tablosu ve dev token örneğiyle)

Alt görevler:
1. [x] `JwtAuthFilter`, yetki matrisi, `ApiError` biçiminde 401/403, CORS preflight, testler · bda2984
2. [x] `dev` token uç noktası + README notu · ddd734a

_Elle (compose, dev profili): USER token'la `POST /api/funds` → 403, ADMIN token'la → 200._

### E0-5 · CI
_Başladı: 2026-11-05 · Bitti: 2026-11-05 · 887502b, 74af71e_
- [x] `.github/workflows/ci.yml`: push ve PR'da, JDK 17 (temurin), Maven cache, `./mvnw -B verify` (integration testleri dahil, Testcontainers). Ek adım: CI'da atlanan test varsa job kırmızıya düşüyor (ADR 008)
- [x] JaCoCo raporu artifact olarak yükleniyor (`jacoco-report`, ilk koşuda ~265 KB)
- [x] İkinci job: `docker compose up -d` → `curl --retry` ile `/api/funds` smoke testi → `down`
- [x] Kök `README.md` iskeleti + CI rozeti (tam README E8-1'de)

_İlk koşu (run 42047332006) iki job'da da yeşil: derleme ve testler 1 dk 10 sn, compose duman testi 2 dk 20 sn. Yerelde kapsam yaklaşık %81 (komut bazında)._

### E0-4 · Flyway ve hata yönetimi
_Rehber: §3, §5.1 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · 235f512, c1b9dbb_
- [x] Flyway eklendi. `V1__baseline.sql` mevcut `Fund` entity'sinden birebir çıkarıldı. `ddl-auto=validate`. `baseline-on-migrate=true` (mevcut veritabanları için)
- [x] `common` paketinde `ApiError` record (timestamp, status, code, message, path) + `@RestControllerAdvice`. Ek alan: `fieldErrors` (yalnızca doğrulama hatalarında). Spring MVC'nin kendi hataları (405, 415, bozuk JSON, eksik parametre, bilinmeyen adres) da aynı biçimde dönüyor; beklenmeyen hata 500 ve iç ayrıntı sızdırmıyor
- [x] `FundNotFoundException` → 404, doğrulama hataları → 400 ve alan listesi. `FundRequest.name` için `@NotBlank` + `@Size(max = 255)`: yalnızca veritabanının zaten zorladığı kurallar, geçerli istekler kırılmadı
- [x] `FundController` için `@WebMvcTest` testleri (mutlu yol + 404 + 400): 15 test, gerçek `SecurityConfig` ile

Alt görevler:
1. [x] Flyway + `V1__baseline.sql` (Hibernate 6.6'nın Postgres DDL çıktısından) + boş ve eski veritabanı için Testcontainers testleri · 235f512
2. [x] `ApiError` + `@RestControllerAdvice` + doğrulama + `@WebMvcTest` · c1b9dbb

### E0-3 · Yerel geliştirme ortamı
_Rehber: §3, §11 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · 534ab26_
- [x] `docker-compose.yml`: `postgres:16` (healthcheck), `mailpit` (1025/8025), `bulletin-service` (`depends_on: condition: service_healthy`), `.env` dosyasından ortam. Postgres host'ta 5433'te (yerel kurulu PostgreSQL ile çakışmasın diye, `POSTGRES_HOST_PORT`)
- [x] `ollama` servisi `profiles: ["llm"]` altında, volume'lü. README'ye `docker compose exec ollama ollama pull qwen2.5:7b-instruct` notu. Yalnızca `--profile llm config` ile doğrulandı, imaj indirilmedi
- [x] Mevcut `Dockerfile` çok aşamalı hâle getirildi (maven build → `eclipse-temurin:17-jre`), root olmayan kullanıcı. Zaten iki aşamalıydı; `17-jdk-noble` + mvnw → `17-jre-noble`, `app` kullanıcısı (uid 999), `EXPOSE 8082`, `.dockerignore`
- [x] `docker compose up` sonrası `GET http://localhost:8082/api/funds` 200 dönüyor (yerelde Docker Desktop ile doğrulandı, `[]`)

### E0-2 · Sırları ortam değişkenine taşı
_Rehber: §6.5 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · f39fbba_
- [x] `spring.datasource.url/username/password` ve `jwt.secret` → `${DB_URL:...}`, `${DB_USER:...}`, `${DB_PASSWORD:}`, `${JWT_SECRET:}`. Gerçek değerler dosyadan silindi
- [x] `JWT_SECRET` boşsa uygulama açık bir hata mesajıyla başlamayı reddediyor (`dev` profili hariç, orada sabit bir geliştirme anahtarı var). Anahtar 32 bayttan kısaysa da reddediliyor. ADR 009
- [x] `.env.example`: bugüne kadarki tüm değişkenler, açıklamalı ve sahte değerli
- [x] E8-6 kartının (secret rotasyonu) varlığı kontrol edildi: Backlog'da duruyor

### E0-1 · Repo temizliği
_Rehber: §2 · Başladı: 2026-11-05 · Bitti: 2026-11-05 · 32a0216, 11e6eb6_
- [x] `privet_log_data.md` içindeki merge conflict işaretleri temizlendi (iki taraf da korunarak birleştirildi; karşı tarafta yalnızca boş satır vardı, HEAD'in iki satırı korundu)
- [x] Test paketi `com.fonbulteni.bulletinservice` olarak taşındı, `./mvnw verify` yeşil
- [x] Kök `.gitignore`: `target/`, `.env`, `out/`, `.idea/`, `*.iml`, `.vscode/`, `.DS_Store`
- [x] `application.properties` içinde `show-sql=false`

---

## Notlar
_(Ajan, kodda AGENT_PROMPT.md §2'den farklı bir şey bulursa, canlı doğrulanamayan entegrasyonları veya kendi verdiği önemli kararları buraya tarihle yazar.)_

**2026-11-05 · §2 ile karşılaştırma**
- `./mvnw verify` başlangıçta kırmızıydı: test sınıfı `com.fonbulteni.bulletin_service` paketinde olduğu için `@SpringBootConfiguration` bulunamıyordu. Ayrı `fix` commit'iyle düzeltildi (E0-1).
- Bağlam testi artık Testcontainers (`postgres:16-alpine`) ile çalışıyor, `@Tag("integration")` ve `disabledWithoutDocker = true`. Docker yoksa atlanır; E0-5'te CI'da atlanmadığı kontrol edilmeli. Yerel ortamda Docker Desktop (Engine 29) + Testcontainers 1.21.4 sorunsuz çalıştı.
- `Dockerfile` zaten çok aşamalı (`17-jdk-alpine` → `17-jre-alpine`), ama root kullanıcıyla çalışıyor ve `EXPOSE 8080` diyor (uygulama 8082'de). E0-3'te ele alınacak.
- `FundService.getFundById` bulunamayan fonda düz `RuntimeException` atıyor, yani bugün 500 dönüyor. E0-4'te 404'e çevrilecek.
- `FundController` üzerinde `@CrossOrigin(origins = "http://localhost:3000")` var (§2'de geçmiyor). Dokunulmadı.
- `JwtService`'te `role` claim'ini okuyan bir metot yok, yalnızca `extractEmail` ve `isTokenValid` var. E0-6'da eklenecek.
- `FundRequest` üzerinde doğrulama anotasyonu yok, `spring-boot-starter-validation` bağımlılığı da yok. E0-4'ün "doğrulama hataları → 400" kriteri bunu gerektiriyor.
- `privet_log_data.md` repo dışındaki bir betikle `boot: <zaman>` commit'leri alarak güncelleniyor. Uygulama kodu bu dosyaya dokunmuyor.
- Kökte git'in izlemediği boş bir `New folder/` dizini var. Dokunulmadı.

**2026-11-05 · Çalışma düzeni**
- Bir kartın kapanış commit'inin hash'i o commit'in içine yazılamadığı için Done kartında "(kapanış commit'i)" yazar; gerçek hash bir sonraki commit'te doldurulur.
- `docs/decisions/` altında 001–007 numaraları kartlar için ayrıldığından ajanın kendi kararları 008'den başlar. Dizin: `docs/decisions/README.md`.
- E0-2 elle doğrulama: `JWT_SECRET` olmadan jar "APPLICATION FAILED TO START" ile çıkıyor; `dev` profiliyle geçici bir Postgres'e karşı açıldı, `GET /api/funds` → 200 `[]`.
- E0-4: Ana sınıftaki `@EnableJpaRepositories`, `@EntityScan` ve `scanBasePackages` kaldırıldı. Hepsi zaten Boot'un varsayılanıydı (paketler ana paketin altında), ama açıkça yazılmaları `@WebMvcTest` dilimlerinin JPA olmadan açılmasını engelliyordu.
- E0-4: `common.TimeConfig` İstanbul saat dilimli bir `Clock` bean'i sağlıyor. Zamana bağlı yeni kod `LocalDateTime.now()` yerine bunu kullanmalı (bkz. E0-7).
- E0-6: auth-service bu repoda olmadığı için token'daki `role` claim'inin tam biçimi doğrulanamadı. Filtre `ADMIN`, `admin` ve `ROLE_ADMIN` biçimlerinin hepsini `ROLE_ADMIN`'e çeviriyor. Geçersiz ya da süresi dolmuş token isteği anonim bırakıyor; herkese açık GET'ler çalışmaya devam ediyor, korumalı yollar 401 dönüyor.
- E0-6: `@CrossOrigin(origins = "http://localhost:3000")` korunuyor ve Security'nin CORS desteği bunu okuyor, yani tarayıcı preflight'ı token istemiyor.

**2026-11-05 · TEFAS canlı gözlem (E1-1 sırasında, E1-3 için önemli)**
- Ajan ortamından (kullanıcının makinesi, Türkiye) TEFAS'a erişim **var**. Toplam 5 istek atıldı, aralarında ≥ 2 sn, User-Agent `fonbulteni-bot/1.0 (+github.com/EmirYoneyler/bulletin-service)`.
- **Site yenilenmiş:** `https://www.tefas.gov.tr/` → 307 → `/tr`, Next.js uygulaması.
- **AGENT_PROMPT §7.1'deki uç nokta artık çalışmıyor:** `POST /api/DB/BindHistoryInfo` (form `fontip=YAT&bastarih=03.11.2026&bittarih=04.11.2026&fonkod=`) → HTTP 404, JSON `{"fault":{"faultCode":"ERR-006","faultString":"Method not found or disabled!"}}`. E1-3'te yeni veri kaynağı araştırılmalı; eski biçim yalnızca referans.
- `/tr/fon-getirileri?fundType=YAT|EMK` sayfası sunucu tarafında `initialFundListingData` dizisini gömülü JSON olarak veriyor: `fonKodu`, `fonUnvan`, `fonTurAciklama`, `tefasDurum`, `getiri1a/3a/6a/1y/yb/3y/5y`, `riskDegeri`. 2026-11-05'te YAT 2135, EMK 402 fon. Getiriler yüzde ve güncel; **günlük fiyat geçmişi değil.** E1-3 için fiyat geçmişini veren yeni uç nokta (büyük olasılıkla sayfanın JS'inin çağırdığı bir API) bulunmalı.
- E1-1 seed'i bu listeden alındı; `DevFundSeeder` yalnızca doğrulanan alanları (kod, unvan, kategori, risk) dolduruyor. Seed, Flyway yerine `@Profile("dev")` bir `ApplicationRunner`: yalnızca dev'de çalışan bir Flyway konumu, aynı veritabanı dev olmadan açıldığında "missing migration" doğrulama hatası verirdi.

**2026-11-05 · TEFAS canlı doğrulama (E1-3)**
- Yeni servis canlı çalışıyor: `./mvnw test -Plive` → AAV (`YAT`) ve AAJ (`EMK`) için son 14 günün fiyatları geldi, 2/2 test yeşil.
- İlk canlı koşu **kırmızıydı**: her istek `500 {"error":"Proxy request failed"}` aldı, aynı istek curl ile 200 döndü. Neden: JDK istemcisi gövdeyi `Content-Length` olmadan akış olarak gönderiyordu. `BufferingClientHttpRequestFactory` ile düzeldi; `MockRestServiceServer` bunu yakalayamadığı için loopback sunuculu bir regresyon testi eklendi (düzeltme olmadan kırmızı olduğu görüldü). Ayrıntı ADR 001'de.
- Bu oturumda TEFAS'a atılan istekler: 5 (E1-1), 3 JS paketi + 2 sayfa + 5 API (araştırma), 6 canlı test isteği. Hepsi tek tek, aralarında ≥ 1,5 sn.

**2026-11-05 · EVDS (E1-5)**
- EVDS `evds2` → `evds3` taşınmış; servis kökü `https://evds3.tcmb.gov.tr/igmevdsms-dis/`. Resmî web servis kılavuzu okundu (ADR 002). Anahtarsız istek canlı denendi: 403.
- AGENT_PROMPT §7.2'deki `TP.FG.J0` artık arşivde (TÜFE 2003=100). Güncel seri `TP.TUKFIY2025.GENEL` (2025=100). E2-2'de baz değişimi dikkate alınmalı.
- EVDS'de altın yalnızca aylık; haftalık altın kıyası için E1-7 açıldı.
- `ingest_run.fund_count`/`funds_failed` V6'da `target_count`/`targets_failed` oldu (EVDS'de hedef seri). E1-4'ün admin yanıtındaki `fundCount`/`fundsFailed` alanları da `targetCount`/`targetsFailed` oldu; bu uçlar aynı gün eklendiği için dış istemci yok.
- İki iş de kilidi ortak `scheduler.JobLock` üzerinden alıyor; TEFAS ve EVDS kilitleri ayrı, birbirini beklemiyor.

## Oturum Günlüğü
| Tarih | Biten kartlar | Açık engeller | Sıradaki kart |
|---|---|---|---|
| 2026-11-05 | E0-1, E0-2, E0-3, E0-4, E0-5, E0-6, E0-7 (yeni), E1-1 · 85+ test yeşil, CI yeşil | Yok. Uyarı: TEFAS'ın eski `BindHistoryInfo` uç noktası 404 (Notlar); E1-5'te EVDS anahtarı gerekecek | E1-2 · FundPrice tablosu (To Do'da E1-2, E1-3, E1-4) |
| 2026-11-05 (2) | E1-2, E1-3 · 119 test yeşil (+2 `live`, ayrı çalışır) | Yok. TEFAS yeni servisi canlı doğrulandı (ADR 001); E1-5'te EVDS anahtarı gerekecek | E1-4 · Günlük fiyat işi + backfill |
| 2026-11-05 (3) | E1-4 · 149 test yeşil (+2 `live`); günlük iş canlı TEFAS'la denendi | Yok. E1-5 EVDS anahtarı olmadan fixture'la yapılacak, canlı doğrulama için E1-5b `Blocked`'a açılacak | E1-5 · EvdsClient (To Do'da E1-5, E1-6, E2-1) |
| 2026-11-05 (4) | E1-5 · 174 test yeşil (+3 `live`); EVDS anahtarsız devre dışı modda uçtan uca denendi | **E1-5b (Blocked):** EVDS API anahtarı gerekiyor → `.env` `EVDS_API_KEY` | E1-6 · BIST100 kıyas verisi (To Do'da E1-6, E2-1) |
| 2026-11-05 (5) | E1-6 · 174 test yeşil; BIST 100 getiri endeksi EVDS üzerinden | **E1-5b (Blocked):** EVDS API anahtarı → `.env` `EVDS_API_KEY` | E2-1 · FundStatsService |
