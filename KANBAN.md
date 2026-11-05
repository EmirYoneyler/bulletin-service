# Kanban: Fon Bülteni Kendini Yazsın

> Kurallar ve çalışma protokolü için bkz. [`AGENT_PROMPT.md`](./AGENT_PROMPT.md).
> Aynı anda yalnızca **1** kart `In Progress` olabilir. Her kart bittiğinde: test → bu dosyayı güncelle → commit → push.
> Kart biçimi: `ID · Başlık` + kabul kriterleri. Taşırken kartı bütün halinde (kriterleriyle) taşı.

---

## 🔴 Blocked

_(boş)_

---

## 🟡 In Progress

_(boş)_

---

## 🔵 To Do

### E0-1 · Repo temizliği
- [ ] `privet_log_data.md` içindeki merge conflict işaretleri temizlendi (veya dosya gerekmiyorsa kaldırıldı ve nedeni commit mesajına yazıldı)
- [ ] Test paketi `com.fonbulteni.bulletinservice` olarak düzeltildi, `./mvnw verify` yeşil
- [ ] Kök `.gitignore` eklendi (`target/`, `.env`, IDE dosyaları)

### E0-2 · Sırları ortam değişkenine taşı
- [ ] `spring.datasource.*` ve `jwt.secret` artık `${ENV:...}` ile okunuyor, gerçek değerler dosyadan silindi
- [ ] `.env.example` eklendi (tüm değişkenler, sahte değerlerle)
- [ ] README'ye gelecekte "eski JWT secret git geçmişinde var, üretimde döndürülmeli" notu düşmek için Backlog'a kart açıldı

### E0-3 · Yerel geliştirme ortamı
- [ ] `docker-compose.yml`: postgres + bulletin-service, healthcheck'li
- [ ] Dockerfile çok aşamalı (build + JRE), yerelde imaj oluşuyor
- [ ] `docker compose up` sonrası `GET /api/funds` 200 dönüyor (JWT gereksinimi varsa nasıl token alınacağı yazıldı)

### E0-4 · Flyway ve hata yönetimi
- [ ] Flyway eklendi, mevcut `funds` tablosu için `V1__baseline.sql`, `ddl-auto=validate`
- [ ] `@ControllerAdvice` + `FundNotFoundException` (404), tutarlı hata gövdesi
- [ ] Controller için `@WebMvcTest` testleri

### E0-5 · CI
- [ ] `.github/workflows/ci.yml`: her push ve PR'da JDK 17 + `./mvnw verify`
- [ ] README'ye CI rozeti eklendi

---

## ⚪ Backlog

### E1 · Veri alımı

#### E1-1 · Fon kimliği: TEFAS kodu
- [ ] `Fund`'a `tefasCode` (unique, nullable, ör. "AFT") eklendi. Migration `V2`
- [ ] `FundRequest/Response` güncellendi, mevcut uç noktalar kırılmadı

#### E1-2 · FundPrice tablosu
- [ ] `FundPrice(fund_id, date, price BigDecimal, totalValue, investorCount, source)`, `(fund_id, date)` unique
- [ ] Repository'de tarih aralığı sorguları

#### E1-3 · TefasClient
- [ ] `pytefas` / `tefas-crawler` projelerinin kullandığı TEFAS endpoint'i incelendi. İstek/yanıt biçimi `docs/decisions/001-tefas.md` dosyasına yazıldı
- [ ] Java `RestClient` ile istemci: zaman aşımı, User-Agent, istekler arası gecikme, 3 denemeli üstel geri çekilme
- [ ] Kaydedilmiş JSON fixture'larıyla testler (ağa çıkmadan)

#### E1-4 · Günlük fiyat işi
- [ ] `@Scheduled(cron="0 30 19 * * MON-FRI", zone="Europe/Istanbul")`: `tefasCode`'u olan fonları çekip upsert ediyor
- [ ] `POST /api/admin/ingest/tefas?from=&to=` ile geçmişe dönük doldurma (backfill), yalnızca admin
- [ ] Çalışma sonucu loglanıyor (kaç fon, kaç fiyat, hatalar)

#### E1-5 · EvdsClient (TCMB)
- [ ] EVDS API anahtarı ortam değişkeninden okunuyor (`EVDS_API_KEY`). Anahtar yoksa istemci devre dışı ve uygulama çalışmaya devam ediyor
- [ ] Seriler: TÜFE (aylık), USD/TRY, gram altın, mevduat faizi. Seri kodları `docs/decisions/002-evds.md` dosyasında
- [ ] `MacroSeries(code, date, value)` tablosu, haftalık güncelleme işi, fixture testleri

#### E1-6 · BIST100 kıyas verisi
- [ ] Ücretsiz ve izinli bir kaynak araştırıldı ve karar belgelendi. Uygun kaynak yoksa kart `Blocked`'a taşındı ve kıyas tablosundan çıkarıldı

### E2 · Deterministik analitik

#### E2-1 · FundStatsService
- [ ] Bir fon ve tarih aralığı için haftalık/aylık/yılbaşından bu yana getiri, yıllıklandırılmış oynaklık, maksimum düşüş
- [ ] `BigDecimal` ve açık yuvarlama kuralları, elle hesaplanmış örneklerle testler

#### E2-2 · Reel getiri ve kıyaslama
- [ ] TÜFE'ye göre reel getiri (aylık TÜFE'nin döneme orantılanma yöntemi belgelendi)
- [ ] Aynı dönem için USD/TRY, altın, mevduat (ve varsa BIST100) getirisi

#### E2-3 · BulletinFacts
- [ ] Değişmez (immutable) `BulletinFacts` nesnesi: her sayı `{değer, birim, kaynak, tarih aralığı, hesaplama adı}` içeriyor
- [ ] Haftanın en çok yükselen/düşen fonları, kategori özetleri
- [ ] `GET /api/bulletins/facts/preview?userId=` ile JSON önizleme

### E3 · Bülten üretimi (LLM)

#### E3-1 · LlmClient soyutlaması
- [ ] `LlmClient` arayüzü, `AnthropicLlmClient` (model `llm.model` property'sinden, anahtar `ANTHROPIC_API_KEY`'den), `TemplateLlmClient` (yedek)
- [ ] Token ve maliyet ölçümü yanıt nesnesinde

#### E3-2 · Writer adımı
- [ ] Prompt dosyası `src/main/resources/prompts/writer.md`, sürümlü. Girdi yalnızca `BulletinFacts` JSON'u
- [ ] Prompt'ta açıkça: yeni sayı yok, tavsiye yok, Türkçe ve sade bir dil
- [ ] Çıktı yapısı: başlık, haftanın özeti, fon fon kısa notlar, kıyas tablosu açıklaması

#### E3-3 · Bulletin ve çalışma kaydı
- [ ] `Bulletin(id, userId, weekStart, status, content, promptVersion, createdAt)`
- [ ] `BulletinRun(bulletinId, step, durationMs, inputTokens, outputTokens, costUsd, retries, result)`

### E4 · Doğrulama ve uyum

#### E4-1 · NumberGuard
- [ ] Metinden sayı çıkarma (Türkçe biçimler: `%3,2`, `3,2%`, `1.250,50 TL`, `-0,8`)
- [ ] Toleranslı eşleştirme (yuvarlamadan kaynaklı ±0,05 puan). Eşleşmeyen sayılar listeleniyor
- [ ] Başarısızlıkta en fazla 2 yeniden üretme, sonra `FAILED_VALIDATION`
- [ ] Kasıtlı olarak yanlış sayı içeren metinlerle testler

#### E4-2 · ComplianceGuard
- [ ] Kural filtresi: yasaklı ifade listesi `compliance/forbidden-phrases.txt`, Türkçe ek varyasyonları regex ile yakalanıyor
- [ ] LLM eleştirmen adımı (`prompts/critic.md`): tavsiye gibi duran ya da abartılı cümleleri yeniden yazıyor, sayılara dokunmuyor. Ardından NumberGuard yeniden çalışıyor
- [ ] Sabit yasal uyarı metni her bültenin sonunda

#### E4-3 · BulletinPipeline
- [ ] facts → writer → numberGuard → compliance → numberGuard zinciri tek bir serviste
- [ ] `POST /api/bulletins/generate?userId=` (admin) ve uçtan uca entegrasyon testi (mock LLM)

### E5 · Teslimat

#### E5-1 · PdfRenderer
- [ ] iText7 ile bülten PDF'i: başlık, özet, fon tablosu, kıyas tablosu, kaynaklar, yasal uyarı. Türkçe karakterler doğru görünüyor (font gömülü)
- [ ] `PdfRenderer` arayüzü: harici `pdf-service`'e geçiş ileride tek bir sınıfla yapılabilsin
- [ ] Örnek çıktı `docs/sample-bulletin.pdf` olarak commit'lendi (sahte/demo veriyle)

#### E5-2 · E-posta gönderimi
- [ ] Spring Mail, SMTP ayarları ortam değişkenlerinden. Yapılandırılmamışsa PDF diske yazılıyor ve loglanıyor
- [ ] HTML gövde (kısa özet) + PDF eki

#### E5-3 · Pazartesi işi
- [ ] `@Scheduled(cron="0 0 8 * * MON", zone="Europe/Istanbul")`: tüm aktif kullanıcılar için bülten üretip gönderiyor
- [ ] Aynı hafta için ikinci kez gönderimi engelleyen idempotentlik

### E6 · Kişiselleştirme

#### E6-1 · İzleme listesi ve portföy
- [ ] `UserHolding(userId, fundId, units, addedAt)`. `userId` JWT'den okunuyor (auth-service ile aynı secret/claim)
- [ ] `GET/POST/DELETE /api/me/holdings`
- [ ] Bültende "portföyün bu hafta ±X TL / %Y değişti" bölümü (sayılar BulletinFacts'ten)

### E7 · Zenginleştirme

#### E7-1 · KAP fon bildirimleri
- [ ] Fon portföy dağılım raporlarının erişim yolu araştırıldı ve belgelendi
- [ ] Dağılım değişimi ("hisse oranı %15 → %30") deterministik olarak hesaplanıp facts'e eklendi, kaynak bağlantısıyla

#### E7-2 · Haber bağlamı (isteğe bağlı)
- [ ] Yalnızca kaydedilmiş gerçek kaynaklar (KAP/TCMB duyuru/RSS), her biri bağlantılı
- [ ] "Haber yorumcusu" rolü ancak bu kaynaklar varsa çalışıyor. Yoksa bölüm bültende yer almıyor

#### E7-3 · Çok rollü yapı değerlendirmesi
- [ ] Tek writer ile writer+yorumcu+eleştirmen ayrımı 5 örnek haftada karşılaştırıldı (doğrulama başarısı, maliyet, okunabilirlik)
- [ ] Sonuç `docs/decisions/00X-multi-agent.md` dosyasına yazıldı. Mimari yalnızca kazanç varsa bölündü

### E8 · Dokümantasyon, gözlemlenebilirlik, deploy

#### E8-1 · README
- [ ] Ürün tek cümleyle, mimari diyagram (Mermaid), servis haritası (auth → bulletin → pdf), kurulum, ortam değişkenleri tablosu
- [ ] "Tasarım kararları" bölümü: neden LLM sayı üretmiyor, NumberGuard, SPK dili, TEFAS nezaket kuralları
- [ ] Örnek bülten PDF'ine bağlantı

#### E8-2 · Gözlemlenebilirlik
- [ ] Actuator health/metrics. Bülten başına süre, token ve maliyet metrikleri
- [ ] `GET /api/admin/bulletin-runs` ile son çalışmaların listesi

#### E8-3 · Değerlendirme seti
- [ ] `src/test/resources/eval/` içinde 5 sabit haftalık facts örneği
- [ ] Her biri için pipeline çalıştırılıyor. Doğrulama başarı oranı ve ortalama maliyet raporlanıyor (mock veya gerçek LLM, bayrağa göre)

#### E8-4 · Deploy
- [ ] Render (veya Fly/Railway) için yapılandırma. Postgres bağlantı sorunu teşhis edildi (SSL modu, internal URL, `DATABASE_URL` → JDBC dönüşümü)
- [ ] Canlı URL README'de. Panel erişimi gerekiyorsa kart `Blocked`'a taşınıp gerekenler yazıldı

#### E8-5 · Secret rotasyonu notu
- [ ] Git geçmişindeki eski `jwt.secret`'ın üretimde kullanılmaması gerektiği README "Güvenlik" bölümüne yazıldı

---

## ✅ Done

_(boş)_

---

## Notlar
_(Ajan, kodda AGENT_PROMPT.md'deki "Mevcut durum" bölümünden farklı bir şey bulursa buraya yazar.)_

## Oturum Günlüğü
| Tarih | Biten kartlar | Açık engeller |
|---|---|---|
