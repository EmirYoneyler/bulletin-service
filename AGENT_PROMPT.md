# Ajan Görev Talimatı: Fon Bülteni Kendini Yazsın

> Bu dosya bu repoda çalışacak kodlama ajanının (Claude Code) **ana talimatıdır**.
> `CLAUDE.md` bu dosyayı her oturumda otomatik yükler. İş listesi ve sıra `KANBAN.md` içindedir.
> Bu dosya **ne** yapılacağını, **nasıl** yapılacağını ve **hangi kuralların asla çiğnenmeyeceğini** tanımlar.
> Bir kart ile bu dosya çelişirse bu dosya kazanır. Çelişkiyi `KANBAN.md` → Notlar'a yaz.

---

## 0. Her oturumun başında (kontrol listesi)

1. `git pull --rebase origin master`
2. Bu dosyayı ve `KANBAN.md`'yi baştan sona oku. `In Progress`'te kart varsa önce onu bitir.
3. `cd bulletin-service && ./mvnw -q verify` çalıştır. Kırmızıysa **ilk iş** onu düzeltmek (ayrı `fix` commit'i).
4. `Blocked` kartlarına bak. Engel kalkmış mı (ör. `.env`'e anahtar eklenmiş mi) kontrol et.
5. Sonra kanban döngüsüne gir (§9).

---

## 1. Ürün: ne inşa ediyoruz?

**Tek cümle:** Her pazartesi 08:00'de, kullanıcının takip ettiği veya sahip olduğu yatırım fonlarını TEFAS'ın gerçek fiyatlarıyla özetleyen, enflasyona göre reel getiriyi ve dolar/altın/mevduat kıyasını gösteren, **içindeki her sayı kaynağına kadar izlenebilen**, yatırım tavsiyesi dili içermeyen kişisel bir bülten. Bülten PDF olarak üretilir ve e-postayla gönderilir.

**Bugün:** fonların elle girildiği bir CRUD API'si (`/api/funds`).
**Hedef:** veriyi kendi toplayan, kendi hesaplayan, kendi yazan ve kendi gönderen bir servis.

**Neden önemli (portföy değeri):** Bu proje bir işe alımcıya şunları göstermeli:
- Dış veri entegrasyonu (resmî API'si olmayan bir kaynağa nazik ve dayanıklı erişim)
- Doğru finansal hesaplama (`BigDecimal`, açık yuvarlama kuralları, testle kanıt)
- LLM'i **güvenli** kullanmak: model sayı uyduramaz, tavsiye veremez, haber uyduramaz
- Mühendislik olgunluğu: test, CI, Flyway, gözlemlenebilirlik, belgelenmiş kararlar, temiz commit geçmişi

### Hedef bülten (örnek iskelet, sayılar temsilî)

```
FON BÜLTENİ · 3–7 Kasım 2026 haftası
Merhaba Emir,

HAFTANIN ÖZETİ
Takip ettiğin 4 fondan 3'ü haftayı artıda kapattı. Portföyün haftalık %1,8 değişti.
Aynı dönemde dolar/TL %0,4, gram altın %-1,1 değişti. Aylık enflasyon oranına göre
haftalık reel getirin yaklaşık %1,1.

FONLARIN
| Fon | Haftalık | Ay başından | Yılbaşından | Reel (YB) | Oynaklık (yıllık) |
| AFT | %3,2     | %5,1        | %41,0       | %6,3      | %24,5             |
...

DİKKAT ÇEKENLER
- En çok yükselen: AFT (%3,2). En çok düşen: XYZ (%-0,9).
- [Varsa, KAP kaynaklı] ABC fonunun hisse oranı %15'ten %30'a çıktı. (kaynak: KAP, bağlantı)

KIYAS
Haftalık: Portföyün %1,8 · USD/TRY %0,4 · Altın %-1,1 · Mevduat (haftalığa çevrilmiş) %0,9

KAYNAKLAR
TEFAS (fiyatlar, 07.11.2026), TCMB EVDS (TÜFE Ekim 2026, USD/TRY, altın, mevduat)

Bu bülten yalnızca bilgilendirme amaçlıdır, yatırım danışmanlığı kapsamında değildir.
```

---

## 2. Mevcut durum (bu dosya yazılırken repoda görülenler)

- Maven projesi `bulletin-service/` alt klasöründe: **Spring Boot 3.5.14, Java 17**, `mvnw` ve bir `Dockerfile` var.
- Paket `com.fonbulteni.bulletinservice` (controller, service, repository, entity, dto, config).
- `Fund` entity: `name, category (EMEKLILIK|HISSE|TAHVIL|KARMA|PARA_PIYASASI), riskLevel, returnRate, fundSize, period, isFutureTrade, expiryDate, contractSize, createdAt`. **TEFAS fon kodu alanı yok.**
- `FundController` → `/api/funds` altında CRUD, kategori, arama ve "popüler" uç noktaları.
- **Güvenlik fiilen kapalı:** `SecurityConfig` her şeye `permitAll()` veriyor, JWT filtresi yok. `JwtService` token üretip doğruluyor. Subject = e-posta, claim `role`.
- `application.properties`: `ddl-auto=update`, `show-sql=true`, **DB şifresi ve `jwt.secret` açık metin**. Port 8082.
- Bağımlılıklar: web, data-jpa, security, postgresql, lombok, jjwt 0.12.3, itext7-core 7.2.5.
- Test paketi uyumsuz: `src/test/java/com/fonbulteni/bulletin_service/` (alt çizgili).
- Kökteki `privet_log_data.md` bir aktivite logu ve içinde **çözülmemiş merge conflict işaretleri** var.
- README yok. CI yok. Flyway yok.
- Render.com deploy'unda daha önce PostgreSQL bağlantı sorunu yaşandı ve çözülmedi.
- Kardeş servisler `auth-service` ve `pdf-service` **bu repoda değil**. Onlara bağlı her şey bir arayüzün arkasına alınır ve bu repoda bağımsız çalışan varsayılan bir uygulama yazılır.

> İlk iş kodu kendin oku. Farklılık görürsen `KANBAN.md` → Notlar'a yaz ve gerçeğe göre çalış.

---

## 3. Sabit teknoloji kararları

Bunları yeniden tartışma. Değiştirmek gerekirse `docs/decisions/` altına bir karar kaydı (ADR) yaz.

| Konu | Karar |
|---|---|
| Dil / çatı | Java 17, Spring Boot 3.5.x (mevcut sürümde kal) |
| DB şeması | Flyway, `ddl-auto=validate` |
| HTTP istemci | Spring `RestClient` + `JdkClientHttpRequestFactory`, bağlanma/okuma zaman aşımları açık |
| Para / fiyat | `BigDecimal`, `MathContext.DECIMAL64`, gösterimde `RoundingMode.HALF_UP` ile 2 hane |
| Tarih / saat | `LocalDate` / `ZonedDateTime`, `ZoneId.of("Europe/Istanbul")` |
| JSON | Jackson (Boot ile gelen) |
| LLM | **Anthropic API kullanılmaz.** Bkz. §4 |
| PDF | iText7 (mevcut), Türkçe karakter için gömülü TTF (DejaVu Sans veya Noto Sans, lisansı serbest) |
| E-posta | Spring Mail (SMTP). Yerelde Mailpit |
| Test | JUnit 5, AssertJ, Mockito, `@WebMvcTest`, `MockRestServiceServer`. DB testleri Testcontainers (Postgres) ile ve `@Tag("integration")` |
| Yerel ortam | Docker Compose: postgres, mailpit, (profil: `llm`) ollama |
| CI | GitHub Actions |

---

## 4. LLM stratejisi: Anthropic anahtarı olmadan

**Hedef:** Uygulama hiçbir ücretli anahtar olmadan uçtan uca çalışır. LLM bir "güzelleştirici"dir, sistem ona bağımlı değildir.

### 4.1 Sağlayıcılar (hepsi aynı arayüzün arkasında)

```java
public interface LlmClient {
    LlmResponse complete(LlmRequest request); // system, user, jsonMode, maxTokens, temperature
    String name();
}
record LlmResponse(String text, int inputTokens, int outputTokens, long durationMs, String model) {}
```

| `llm.provider` | Sınıf | Anahtar | Ne zaman |
|---|---|---|---|
| `template` (**varsayılan**) | `TemplateLlmClient` | yok | Her zaman çalışır, testlerde ve CI'da, demoda yedek |
| `ollama` | `OpenAiCompatibleLlmClient` → `http://ollama:11434/v1` | yok | Yerelde, docker compose `llm` profiliyle. Model `llm.model`, varsayılan `qwen2.5:7b-instruct` |
| `openai-compatible` | `OpenAiCompatibleLlmClient` | `LLM_API_KEY` (ücretsiz katman) | Deploy'da. Ör. Google Gemini (`https://generativelanguage.googleapis.com/v1beta/openai/`) veya Groq (`https://api.groq.com/openai/v1`) ücretsiz katmanı |

- **Tek bir** `OpenAiCompatibleLlmClient` yaz (`POST {baseUrl}/chat/completions`). Ollama, Gemini ve Groq üçü de bu formatı destekliyor. Sağlayıcı farkı sadece `llm.base-url`, `llm.model`, `llm.api-key` ayarlarıdır.
- JSON çıktı için `response_format: {"type":"json_object"}` gönder. Desteklemeyen sağlayıcıda prompt ile iste ve çıktıyı yine doğrula.
- **Otomatik yedek:** LLM çağrısı hata verirse, zaman aşımına uğrarsa veya çıktı doğrulamadan 2 kez geçemezse pipeline `TemplateLlmClient` ile devam eder. Bülten yine gönderilir, `bulletin_runs`'a `fallback=true` yazılır.
- Zaman aşımı: Ollama için 120 sn (yerel CPU yavaş olabilir), uzak sağlayıcılar için 30 sn.
- Ücretsiz katman notu: Bu sağlayıcılar ücretsiz katmanda verileri eğitimde kullanabilir. Bültene giren veriler zaten kamuya açık fon verileri. **Kullanıcının e-postası ve adı LLM'e gönderilmez.** Kişiselleştirme selamlaması şablonla eklenir.

### 4.2 Yer tutucu tasarımı (LLM'in sayı uydurmasını yapısal olarak imkânsız kılar)

LLM metne **hiç rakam yazmaz**. Sayıların yerine `BulletinFacts` anahtarlarına işaret eden yer tutucular yazar. Java bunları gerçek, biçimlendirilmiş değerlerle değiştirir.

```
LLM çıktısı:  "{{fund.AFT.return.1w}} ile haftanın en iyisi AFT oldu; aynı dönemde dolar {{bench.USDTRY.return.1w}} değişti."
Render sonrası: "%3,2 ile haftanın en iyisi AFT oldu; aynı dönemde dolar %0,4 değişti."
```

- Writer prompt'una kullanılabilir anahtarların listesi ve her birinin Türkçe açıklaması verilir. Değerlerin kendisi **verilmez**. Sadece yön bilgisi verilir (`up`/`down`/`flat`, sıralama), böylece model "yükseldi/düştü" diyebilir ama rakam bilemez.
- `TemplateLlmClient` de aynı yer tutucu formatını üretir. Böylece render ve doğrulama kodu tek.

### 4.3 Bu yüzden NumberGuard şöyle çalışır (bkz. §7.5)

1. Render **öncesi** LLM metninde rakam (`\d`) **olmamalı**. Rakam varsa bu ihlaldir.
2. Her yer tutucu anahtarı `BulletinFacts` içinde var olmalı. Bilinmeyen anahtar ihlaldir.
3. Yön tutarlılığı: yer tutucunun hemen çevresinde "yükseldi/arttı/kazandırdı" geçiyorsa değer ≥ 0, "düştü/geriledi/kaybettirdi" geçiyorsa değer ≤ 0 olmalı.
4. İhlalde en fazla 2 yeniden deneme (ihlaller prompt'a geri bildirim olarak eklenir), sonra şablon yedeği.

---

## 5. Mimari

```
                 ┌──────────────────────── bulletin-service ────────────────────────┐
 TEFAS ─────────►│ ingest/tefas   TefasClient ──► FundPrice (günlük fiyat)           │
 TCMB EVDS ─────►│ ingest/evds    EvdsClient  ──► MacroPoint (TÜFE, USD, altın, faiz)│
 KAP (E7) ──────►│ ingest/kap     KapClient   ──► FundAllocation                     │
                 │                                                                    │
                 │ analytics/     FundStatsService, BenchmarkService (deterministik)  │
                 │                    └──► BulletinFacts (her sayı + kaynak + yöntem)  │
                 │                                                                    │
                 │ bulletin/      BulletinPipeline                                    │
                 │                  1. Writer (LlmClient, yer tutuculu metin)         │
                 │                  2. NumberGuard                                    │
                 │                  3. ComplianceGuard (kural filtresi [+ eleştirmen])│
                 │                  4. NumberGuard (tekrar)                           │
                 │                  5. Renderer (yer tutucu → değer)                  │
                 │                    └──► Bulletin + BulletinRun kayıtları           │
                 │                                                                    │
                 │ delivery/      PdfRenderer (iText7) ──► MailSender                 │
                 │ scheduler/     DailyPriceJob, WeeklyMacroJob, MondayBulletinJob    │
                 │ portfolio/     UserHolding, /api/me/holdings                        │
                 │ security/      JwtAuthFilter (auth-service ile aynı secret)         │
                 └────────────────────────────────────────────────────────────────────┘
```

Paket kökü `com.fonbulteni.bulletinservice`. Yeni modüller: `ingest.tefas`, `ingest.evds`, `ingest.kap`, `analytics`, `bulletin`, `bulletin.llm`, `bulletin.guard`, `delivery`, `scheduler`, `portfolio`, `security`, `common` (istisnalar, saat, para biçimleme). Mevcut `controller/service/repository/entity/dto` olduğu gibi kalır.

### 5.1 Veri modeli (Flyway migration'larıyla)

```sql
-- V1__baseline.sql : mevcut funds tablosu, olduğu gibi (önce gerçek şemayı JPA'dan çıkar)
-- V2: funds.tefas_code VARCHAR(10) UNIQUE NULL
fund_price      (id, fund_id FK, price_date DATE, price NUMERIC(20,6), total_value NUMERIC(24,2),
                 investor_count INT, source VARCHAR(20), fetched_at TIMESTAMPTZ,
                 UNIQUE(fund_id, price_date))
macro_point     (id, series_code VARCHAR(40), obs_date DATE, value NUMERIC(20,6), fetched_at,
                 UNIQUE(series_code, obs_date))
app_user_ref    (id, email VARCHAR UNIQUE, display_name, bulletin_enabled BOOL DEFAULT true)
                 -- auth-service'in kullanıcısının bu servisteki gölgesi, JWT'den ilk istekte oluşur
user_holding    (id, user_id FK, fund_id FK, units NUMERIC(20,6) NULL, added_at,
                 UNIQUE(user_id, fund_id))     -- units NULL = sadece izleme
bulletin        (id, user_id FK, week_start DATE, status VARCHAR(30), facts_json JSONB,
                 llm_text TEXT, rendered_text TEXT, pdf_path VARCHAR, prompt_version VARCHAR,
                 provider VARCHAR, fallback BOOL, created_at, sent_at,
                 UNIQUE(user_id, week_start))  -- idempotentlik
bulletin_run    (id, bulletin_id FK, step VARCHAR, attempt INT, duration_ms BIGINT,
                 input_tokens INT, output_tokens INT, result VARCHAR, detail TEXT, created_at)
fund_allocation (E7) (id, fund_id, report_month DATE, asset_class VARCHAR, weight NUMERIC(9,4), source_url)
```

`bulletin.status`: `PENDING → GENERATED → SENT` veya `FAILED_VALIDATION` / `FAILED_DELIVERY`.

---

## 6. Değişmez kurallar

Bunlar her karttan önceliklidir.

1. **LLM sayı üretmez.** Bütün sayılar Java'da hesaplanır, `BulletinFacts`'e konur ve yer tutucularla metne girer (§4.2).
2. **Sayı doğrulama zorunlu.** NumberGuard geçmeyen metin gönderilmez. Şablon yedeği de NumberGuard'dan geçer.
3. **Yatırım tavsiyesi dili yasak (SPK).** Kural filtresi her zaman çalışır. Her bülten sabit uyarıyla biter: *"Bu bülten yalnızca bilgilendirme amaçlıdır, yatırım danışmanlığı kapsamında değildir."* Geleceğe dair tahmin cümlesi kurulmaz ("yükselmesi bekleniyor" vb.).
4. **Uydurma haber yok.** Haber/bildirim içeriği yalnızca kaydedilmiş gerçek bir kaynaktan gelir ve bağlantısıyla gösterilir. Kaynak yoksa bölüm bültende yer almaz.
5. **Sırlar koda girmez.** Tüm sırlar ortam değişkeninden okunur (`${ENV:varsayılan}`). Gerçek değerler `.env` dosyasında durur ve `.env` `.gitignore`'dadır. `.env.example` güncel tutulur. Commit öncesi `git diff --cached` ile sızıntı kontrolü yap.
6. **Dış kaynaklara saygı.** TEFAS: günde bir çalışma, istekler arası ≥ 1,5 sn, açıklayıcı User-Agent (`fonbulteni-bot/1.0 (+github.com/EmirYoneyler/bulletin-service)`), 3 denemeli üstel geri çekilme, sonra o gün atlanır ve loglanır. Paralel istek atılmaz.
7. **Mevcut API kırılmaz.** `/api/funds` sözleşmesi korunur. Şema değişiklikleri geriye dönük uyumlu olur.
8. **Testler ağa çıkmaz.** Dış istemciler `MockRestServiceServer` ve kaydedilmiş fixture'larla, LLM sahte istemciyle test edilir. Canlı kontrol gerekiyorsa ayrı bir `@Tag("live")` testi yazılır, varsayılan olarak çalışmaz.
9. **Ücretli anahtar gerekmez.** Varsayılan yapılandırmayla (`llm.provider=template`, SMTP yok → PDF diske) uygulama `docker compose up` ile eksiksiz çalışır.
10. **Kişisel veri LLM'e gitmez.** E-posta, isim ve portföy adetleri prompt'a girmez. Portföyün TL değeri bile yalnızca yer tutucu olarak geçer.
11. **Uydurma yok, sahte başarı yok.** Bir dış kaynağa ulaşılamıyorsa bunu sahte veriyle gizleme. Fixture'lar `src/test/resources/fixtures/` altında durur ve dosya başında "kaydedildiği tarih ve kaynak" yazar. Gerçek yanıt alınamadıysa fixture'ın elle, belgelenen formata göre üretildiğini açıkça not et.

---

## 7. Teknik rehber (alan alan)

### 7.1 TEFAS (`ingest.tefas`)
- Resmî API yok. Açık kaynak `tefas-crawler` (Python) ve `pytefas` projelerinin kullandığı web uç noktasını incele. Bilinen biçim (doğrula, değişmiş olabilir):
  `POST https://www.tefas.gov.tr/api/DB/BindHistoryInfo`, `application/x-www-form-urlencoded`, alanlar `fontip=YAT`, `bastarih=dd.MM.yyyy`, `bittarih=dd.MM.yyyy`, `fonkod=AFT` (boşsa tüm fonlar). Yanıt JSON `data[]`: `TARIH` (epoch ms), `FONKODU`, `FONUNVAN`, `FIYAT`, `TEDPAYSAYISI`, `KISISAYISI`, `PORTFOYBUYUKLUK`.
  Portföy dağılımı için benzer `BindHistoryAllocation` uç noktası var (E7).
- Site bir WAF arkasında olabilir: önce ana sayfaya GET atıp çerez almak gerekebilir. Yurt dışı / bulut IP'leri engellenebilir. **Ajan ortamından canlı istek başarısız olursa** bunu Notlar'a yaz, istemciyi belgelenen formata göre fixture'la geliştir ve canlı doğrulamayı kullanıcıya bırak (kart Done olabilir, ama "canlı doğrulanmadı" notu düşülür).
- Tek istekte en fazla ~3 aylık aralık iste. Daha uzun backfill'i parçala.
- Günlük iş son 5 iş gününü yeniden çeker ve upsert eder. Böylece kaçan günler kendiliğinden dolar.
- Bulgularını `docs/decisions/001-tefas.md` dosyasına yaz.

### 7.2 TCMB EVDS (`ingest.evds`)
- Ücretsiz anahtar `EVDS_API_KEY` (evds2.tcmb.gov.tr üzerinden kullanıcı alır). Anahtar yoksa istemci devre dışı kalır, bültenin kıyas/reel bölümleri "veri yok" olarak atlanır ve uygulama çalışmaya devam eder.
- Güncel API'de anahtar **HTTP header** (`key: ...`) olarak gider, URL parametresi olarak değil. Dokümanı kontrol et.
- Gerekli seriler (kodları EVDS'de arayarak **doğrula**, buradakiler başlangıç tahminidir): TÜFE endeksi (`TP.FG.J0`), USD/TRY döviz alış (`TP.DK.USD.A.YTL`), gram altın TL fiyatı, TL mevduat faizi (ağırlıklı ortalama). Doğrulanan kodları `application.properties` → `evds.series.*` ve `docs/decisions/002-evds.md` dosyasına yaz.
- TÜFE aylıktır ve bir sonraki ayın ~3'ünde yayımlanır. Her zaman **mevcut en son ayı** kullan ve bültende hangi ayın kullanıldığını göster.

### 7.3 Analitik formülleri (`analytics`)
- Dönem getirisi: `r = P_son / P_ilk − 1`. `P_ilk` = dönem başından önceki son işlem günü fiyatı.
- Dönemler: 1 hafta (önceki cuma → bu cuma), ay başından, yılbaşından, 1 yıl.
- Yıllıklandırılmış oynaklık: son 63 işlem gününün günlük log getirilerinin standart sapması × √252.
- Maksimum düşüş: seçilen penceredeki zirveden en büyük yüzde düşüş.
- Reel getiri: `(1 + r) / (1 + π) − 1`. Dönem enflasyonu `π`, son aylık TÜFE değişiminin döneme geometrik orantılanmasıyla bulunur: `π = (1 + π_ay)^(gün/30) − 1`. Yılbaşından bu yana için gerçek TÜFE endeks oranı kullanılır. Yöntemi ADR'ye yaz.
- Mevduat kıyası: yıllık faiz `i` dönem için `(1 + i)^(gün/365) − 1`.
- Portföy getirisi: adetli fonlarda değer ağırlıklı. Adetsiz izleme listesinde eşit ağırlıklı ve bültende bu açıkça belirtilir.
- Her hesap için **elle hesaplanmış** beklenen değerlerle birim testi yazılır (küçük sabit fiyat serileri).
- Eksik veri: bir fonun dönem başı fiyatı yoksa o metrik `null` olur ve facts'e "veri yok" olarak girer. 0 olarak girmez.

### 7.4 BulletinFacts
- Değişmez `record`. Her değer bir `Fact`'tir: `key, value (BigDecimal), unit (PERCENT|TRY|INDEX), periodStart, periodEnd, source (TEFAS|EVDS|KAP|COMPUTED), method (ör. "return.simple"), direction (UP|DOWN|FLAT)`.
- Anahtar şeması: `fund.<KOD>.return.1w|mtd|ytd|1y`, `fund.<KOD>.vol.63d`, `fund.<KOD>.real.ytd`, `portfolio.return.1w`, `portfolio.value.change.1w`, `bench.USDTRY.return.1w`, `bench.GOLD.return.1w`, `bench.DEPOSIT.return.1w`, `bench.CPI.month` (ve diğerleri). Liste `docs/facts-keys.md` dosyasında tutulur.
- `FactFormatter`: Türkçe biçim (`%3,2`, `1.250,50 TL`, negatifte `%-0,9`). Bu, render ile PDF'in tek kaynağıdır.

### 7.5 NumberGuard ve ComplianceGuard (`bulletin.guard`)
- NumberGuard kuralları §4.3'te. Her ihlal `Violation(type, snippet, key?)` olarak döner. Testler: rakam içeren metin, bilinmeyen anahtar, yön çelişkisi ("düştü" + pozitif değer), temiz metin.
- ComplianceGuard:
  1. **Kural filtresi (her zaman):** `src/main/resources/compliance/forbidden.txt` dosyasındaki kökler, Türkçe eklerle birlikte regex ile yakalanır (`\b(al|sat|tut)(ın|ınız|malı|mak)\b`, `tavsiye`, `öner`, `kesin(likle)?`, `garanti`, `kaçırma`, `fırsat`, `hedef fiyat`, `beklen(iyor|ti)`, `yükselecek`, `düşecek` vb.). Yanlış pozitifleri önlemek için kelime sınırına ve beyaz listeye dikkat et ("Al" fon kodu olabilir, yer tutucu içi taranmaz).
  2. **LLM eleştirmen (isteğe bağlı, `compliance.critic.enabled`):** İşaretli cümleleri yeniden yazar, yer tutuculara dokunmaz. Küçük yerel modellerde zayıf kalabilir, bu yüzden kural filtresi ana savunmadır.
  3. Kural filtresi hâlâ ihlal buluyorsa cümle **silinir** (yeniden yazılmaz) ve run kaydına yazılır.

### 7.6 Prompt'lar (`src/main/resources/prompts/`)
- `writer.v1.md`: Rol (Türkçe yazan, sade, abartısız bir finans bülteni editörü). Girdi: anahtar listesi + açıklamalar + yön bilgileri. Kurallar: rakam yazma, sadece verilen anahtarları kullan, tavsiye ve tahmin yok, en fazla ~250 kelime. Çıktı JSON: `{ "summary": "...", "fundNotes": [{"code":"AFT","text":"..."}], "benchmarkNote": "..." }`.
- `critic.v1.md`: işaretli cümle + neden → yeniden yazılmış cümle (JSON).
- Prompt dosyalarının başında sürüm ve değişiklik notu olur. `bulletin.prompt_version`'a yazılır.
- Prompt'lar Java string'i içine gömülmez.

### 7.7 Güvenlik
- `JwtAuthFilter`: `Authorization: Bearer` → `JwtService` ile doğrulanır, `subject` (e-posta) ve `role` ile `SecurityContext` doldurulur.
- Yetki matrisi: `GET /api/funds/**` herkese açık (mevcut davranış), `POST/PUT/DELETE /api/funds/**` ve `/api/admin/**` için `ROLE_ADMIN`, `/api/me/**` için oturum gerekir.
- Bu değişiklik mevcut istemcileri kırabilir. Ayrı bir kart olarak yap ve README'ye yaz.
- Yerel geliştirme için `POST /api/dev/token` (yalnızca `dev` profilinde) test token'ı üretir.

### 7.8 PDF ve e-posta (`delivery`)
- `PdfRenderer` arayüzü + `ITextPdfRenderer`. Font `src/main/resources/fonts/` altına gömülür. "ğüşıöçĞÜŞİÖÇ" testi PDF metninden geri okunarak doğrulanır.
- Bölümler: başlık + hafta, özet, fon tablosu, dikkat çekenler, kıyas tablosu, kaynaklar, yasal uyarı. Tablolardaki sayılar **doğrudan facts'ten** gelir, LLM metninden gelmez.
- `MailSender`: SMTP ayarlanmışsa HTML gövde + PDF eki. Ayarlanmamışsa PDF `./out/bulletins/` altına yazılır ve log'a yolu düşülür. Yerelde compose'daki Mailpit (SMTP 1025, arayüz 8025) kullanılır.
- Üretimde ücretsiz seçenek: Gmail SMTP + uygulama şifresi (kullanıcı sağlar → Blocked kartı).

### 7.9 Zamanlanmış işler (`scheduler`)
- `DailyPriceJob`: `0 30 19 * * MON-FRI`, `Europe/Istanbul`
- `WeeklyMacroJob`: `0 0 7 * * MON`
- `MondayBulletinJob`: `0 0 8 * * MON`, her aktif kullanıcı için bülten üretir ve gönderir. `UNIQUE(user_id, week_start)` ile aynı hafta için ikinci kez gönderim engellenir.
- Çoklu instance ihtimaline karşı ShedLock (JDBC) kullanılır.
- Her işin admin altından elle tetiklenen bir eşi vardır (`POST /api/admin/jobs/{name}/run`).

---

## 8. Test stratejisi

- **Birim:** analitik formülleri, FactFormatter, NumberGuard, ComplianceGuard, yer tutucu renderer'ı. Hedef: bu paketlerde ≥ %80 satır kapsamı (JaCoCo raporu CI'da).
- **Dilim testleri:** `@WebMvcTest` (controller + güvenlik), `@DataJpaTest` + Testcontainers (`@Tag("integration")`).
- **İstemci testleri:** `MockRestServiceServer` + `src/test/resources/fixtures/{tefas,evds}/*.json`.
- **Pipeline uçtan uca:** sahte `LlmClient` ile. Senaryolar: (a) temiz çıktı, (b) rakam sızdıran çıktı → yeniden deneme → başarı, (c) sürekli kötü çıktı → şablon yedeği, (d) tavsiye cümlesi → silinir.
- **Değerlendirme seti (E8):** `src/test/resources/eval/` altında 5 sabit hafta. `./mvnw -Peval verify` gerçek sağlayıcıyla çalışır ve doğrulama başarı oranı, ortalama süre ve token sayısını `docs/eval-report.md` dosyasına yazar.
- Docker yoksa (ajan ortamında olabilir) integration testlerini `-DexcludedGroups=integration` ile atla. **CI'da hepsi çalışır.**

---

## 9. Kanban protokolü

Tek doğruluk kaynağı `KANBAN.md`. Sütunlar: Blocked · In Progress · To Do · Backlog · Done.

Her kart için:

1. **Seç.** `To Do`'nun en üstündeki kartı al. `To Do` boşsa `Backlog`'dan sıradaki 3 kartı `To Do`'ya taşı (sırayı koru, bağımlılıklara bak). Aynı anda **tek kart** `In Progress` olur.
2. **Başlat.** Kartı (kriterleriyle birlikte) `In Progress`'e taşı, `Başladı: YYYY-MM-DD` yaz. Bu taşımayı ayrıca commit'leme, kartın ilk commit'ine dahil et.
3. **Planla.** Kart büyükse (> ~300 satır değişiklik) kart altına 2–5 maddelik alt görev listesi yaz ve her alt görevi ayrı commit olarak ilerlet.
4. **Uygula.** Kabul kriterlerine bağlı kal, kapsamı genişletme. Yolda çıkan yeni işleri `Backlog`'a yeni kart olarak ekle (ID: epik + sıradaki numara).
5. **Doğrula.** `cd bulletin-service && ./mvnw -q verify` yeşil olmalı. Davranış değiştiyse uygulamayı ayağa kaldırıp en az bir isteği elle dene (`curl`) ve sonucu commit gövdesine bir satırla yaz.
6. **Kapat.** Kriter kutularını işaretle, kartı `Done`'un **en üstüne** taşı, `Bitti: YYYY-MM-DD · <kısa commit hash'leri>` ekle. Kriterden vazgeçtiysen üstünü çiz ve nedenini yaz.
7. **Commit + push** (kod ve `KANBAN.md` aynı commit'te):
   ```bash
   git add -A
   git diff --cached --stat            # sır/büyük dosya kontrolü
   git -c user.name="EMİR" -c user.email="emiryoneyler@gmail.com" commit -F- <<'EOF'
   feat(ingest): TEFAS günlük fiyat istemcisi [E1-3]

   RestClient tabanlı istemci, 3 denemeli geri çekilme, fixture testleri.
   Canlı istek ajan ortamından 403 döndü; kullanıcı tarafında doğrulanacak (Notlar).

   Co-Authored-By: Claude <noreply@anthropic.com>
   EOF
   git pull --rebase origin master && git push origin master
   ```
   - Tipler: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `build`. Başlık ≤ 72 karakter, Türkçe, emir kipinde değil, özet biçiminde.
   - Commit'ler **her zaman** `EMİR <emiryoneyler@gmail.com>` adına atılır ve **Claude co-author** olur. Global git ayarını değiştirme, yukarıdaki `-c` biçimini kullan.
   - Her commit kendi başına derlenir ve testleri geçer. Ara commit'ler de push edilir.
8. **Devam.** Kullanıcı onayı beklemeden sıradaki karta geç.

**Blocked:** Kullanıcının vermesi gereken bir şey gerekiyorsa (anahtar, hesap, panel erişimi, alan adı) kartı `Blocked`'a taşı ve şunları yaz: *ne gerekiyor, nereden alınır, nereye konacak (`.env` değişken adı), engel kalkınca ilk ne yapılacak.* Sonra commit + push yap ve engelsiz sıradaki karta geç. Engeli sahte değerle aşma.

**Push başarısızsa:** `git pull --rebase`, çatışmayı çöz, testleri yeniden çalıştır, tekrar push et. `--force` / `--force-with-lease` / geçmiş yeniden yazma **yasak**.

**Oturum sonu:** `KANBAN.md` → Oturum Günlüğü'ne satır ekle (tarih, biten kartlar, açık engeller, bir sonraki kart) ve push et.

---

## 10. Yapma listesi

- LLM'e ham sayı verme veya ondan sayı isteme.
- `Double`/`float` ile para hesaplama (mevcut `Fund` alanlarına dokunmadıkça).
- `ddl-auto=update`'e geri dönme. Şema değişikliği yalnızca Flyway ile.
- Testte gerçek ağa çıkma. Testi geçirmek için `@Disabled` ekleme (ekleyeceksen Backlog'a kart aç ve nedenini yaz).
- Sır, `.env`, `target/`, üretilmiş PDF (örnek hariç) commit'leme.
- Tek dev commit'le birden fazla kart bitirme.
- Kart kapsamı dışında "fırsat buldum" refaktörü. Gerekirse Backlog'a kart aç.
- Kullanıcıya yazılan metinde (bülten, README, hata mesajları) İngilizce-Türkçe karışık dil. Kod, sınıf ve değişken adları İngilizce, kullanıcıya görünen metin Türkçe.
- Anthropic veya başka bir ücretli API'yi varsayılan yapma.

---

## 11. Bitti tanımı (proje geneli)

- [ ] `cp .env.example .env && docker compose up` → uygulama, Postgres ve Mailpit ayağa kalkıyor. Hiçbir anahtar olmadan şablon yazarla bülten üretiliyor ve Mailpit'e düşüyor.
- [ ] `docker compose --profile llm up` → Ollama ile yerel LLM yazar çalışıyor. `llm.provider=openai-compatible` + ücretsiz anahtarla uzak sağlayıcı çalışıyor.
- [ ] Günlük iş TEFAS fiyatlarını çekiyor (canlı doğrulama kullanıcı tarafında yapıldı veya Notlar'da durumu yazılı).
- [ ] EVDS anahtarı varken reel getiri ve kıyaslar bültende, yokken bölüm temiz biçimde atlanıyor.
- [ ] Bültendeki her sayı `bulletin.facts_json` içindeki bir anahtara kadar izlenebiliyor. NumberGuard ve ComplianceGuard testleri yeşil.
- [ ] Pazartesi işi idempotent. `bulletin_run` tablosu her adımın süresini, token sayısını ve sonucunu tutuyor. `GET /api/admin/bulletin-runs` çalışıyor.
- [ ] GitHub Actions her push'ta yeşil. README'de CI ve kapsam rozeti var.
- [ ] README: tek cümlelik ürün tanımı, Mermaid mimari çizimi, ekran görüntüsü / örnek PDF, kurulum, ortam değişkenleri tablosu, "Tasarım kararları" (yer tutucu yaklaşımı, neden ücretli LLM yok, SPK dili, TEFAS nezaketi), "Bilinen sınırlar".
- [ ] Canlı deploy var ya da `Blocked`'ta tam olarak ne eksik olduğu yazılı.
