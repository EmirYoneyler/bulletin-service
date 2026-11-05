# Ajan Görev Talimatı: Fon Bülteni Kendini Yazsın

> Bu dosya, bu repoda çalışacak bir kodlama ajanının (Claude Code vb.) talimatıdır.
> Ajan işe başlamadan önce bu dosyanın tamamını ve `KANBAN.md` dosyasını okur.
> Çalışma sırası ve iş listesi `KANBAN.md` içindedir. Bu dosya kuralları, bağlamı ve hedefi tanımlar.

---

## 1. Rol ve hedef

Kıdemli bir Java/Spring Boot geliştiricisisin. Görevin `bulletin-service` servisini dönüştürmek. Bugün fonların elle girildiği bir CRUD API'si. Hedef ise kendi kendine çalışan bir ürün:

> **Her pazartesi sabahı, kullanıcının takip ettiği / sahip olduğu fonları gerçek verilerle özetleyen,
> enflasyona göre reel getiriyi ve piyasa kıyaslamalarını gösteren, içindeki her sayı kaynağına kadar
> izlenebilen ve yatırım tavsiyesi dili içermeyen kişisel bir bülten. Bülten PDF olarak üretilir ve e-postayla gönderilir.**

Bu bir portföy projesi. Hem çalışması hem de bir işe alımcıya ya da mülakatçıya **mühendislik olgunluğu** göstermesi gerekiyor: test, gözlemlenebilirlik, belgelenmiş kararlar, temiz commit geçmişi.

## 2. Mevcut durum (başlangıç anında repoda görülenler)

- Repo kökü: `bulletin-service/` alt klasöründe bir Maven projesi var (Spring Boot 3.5.x, Java 17).
- Paket: `com.fonbulteni.bulletinservice` (controller, service, repository, entity, dto, config).
- `Fund` entity: `name, category (EMEKLILIK|HISSE|TAHVIL|KARMA|PARA_PIYASASI), riskLevel, returnRate, fundSize, period, isFutureTrade, expiryDate, contractSize, createdAt`. **TEFAS fon kodu alanı yok.**
- `FundController` → `/api/funds` altında CRUD, kategori, arama ve "popüler" uç noktaları.
- JWT (jjwt 0.12.3) ve Spring Security var. iText7 bağımlılığı zaten `pom.xml` içinde.
- `application.properties` içinde **DB şifresi ve `jwt.secret` açık metin olarak duruyor**. Bunlar ortam değişkenine taşınmalı.
- Test paketi adı uyumsuz: `src/test/java/com/fonbulteni/bulletin_service/` (alt çizgili), ana paket ise `bulletinservice`.
- Kök dizindeki `privet_log_data.md` içinde **çözülmemiş merge conflict işaretleri** (`<<<<<<<`, `=======`, `>>>>>>>`) var.
- README yok.
- Render.com'daki deploy'da daha önce PostgreSQL bağlantı sorunu yaşanmıştı ve sorun çözülmemiş olabilir.
- Mikroservis ailesi: `auth-service`, `bulletin-service`, `pdf-service`. Diğer ikisi **bu repoda değil**. Onlara bağımlı bir iş çıkarsa arayüzün arkasına al ve bu repoda bağımsız çalışan bir varsayılan uygulama yaz.

> Yukarıdaki bilgiler bu dosya yazılırken doğruydu. İlk iş olarak kodu kendin oku ve farklılık varsa `KANBAN.md` → "Notlar" bölümüne yaz.

## 3. Mimari hedef

```
                ┌──────────────── bulletin-service ───────────────────┐
TEFAS ─────────►│ ingest/  TefasClient  → FundPrice (günlük fiyat)     │
TCMB EVDS ─────►│ ingest/  EvdsClient   → MacroSeries (TÜFE, USD, altın)│
KAP (sonra) ───►│ ingest/  KapClient    → FundDisclosure               │
                │                                                      │
                │ analytics/  FundStatsService  (deterministik sayılar)│
                │      └─► BulletinFacts (tüm sayılar + kaynakları)     │
                │                                                      │
                │ bulletin/  BulletinPipeline                           │
                │   1. Writer   (LLM: facts → metin, yeni sayı üretmez) │
                │   2. NumberGuard (metindeki her sayı facts'te var mı?)│
                │   3. ComplianceGuard (kural filtresi + LLM eleştirmen)│
                │      └─► Bulletin (metin, sürüm, maliyet, izler)      │
                │                                                      │
                │ delivery/  PdfRenderer (iText7) → EmailSender         │
                │ scheduler/ günlük fiyat işi, pazartesi bülten işi     │
                └──────────────────────────────────────────────────────┘
```

Paket yapısı önerisi: `ingest`, `analytics`, `bulletin`, `delivery`, `scheduler`, `portfolio`. Mevcut `controller/service/...` yapısı olduğu gibi kalabilir. Yeni modüller yanına eklenir.

## 4. Değişmez kurallar

Bu kurallar herhangi bir kanban kartından daha önceliklidir.

1. **LLM sayı üretmez.** Bütün sayılar (getiri, oynaklık, reel getiri, kıyaslamalar) Java tarafında deterministik olarak hesaplanır ve `BulletinFacts` nesnesine konur. LLM sadece bu sayıları anlatır.
2. **Sayı doğrulama zorunludur.** `NumberGuard`, üretilen metindeki her sayısal ifadeyi (yüzde, TL, tarih hariç) `BulletinFacts` içindeki değerlerle toleranslı biçimde karşılaştırır. Eşleşmeyen sayı varsa metin en fazla 2 kez yeniden üretilir. Hâlâ geçmezse bülten **gönderilmez**, `FAILED_VALIDATION` olarak kaydedilir ve loglanır.
3. **Yatırım tavsiyesi dili yasaktır (SPK).** Önce kural tabanlı filtre çalışır ("al", "sat", "tut", "kesin", "garanti", "tavsiye ederiz", "kaçırmayın" vb., Türkçe ek varyasyonlarıyla birlikte). Ardından LLM eleştirmeni gelir. Her bülten sabit bir uyarı metniyle biter: *"Bu bülten yalnızca bilgilendirme amaçlıdır, yatırım danışmanlığı kapsamında değildir."*
4. **Uydurma haber yok.** Haber/yorum katmanı yalnızca gerçek ve kaydedilmiş bir kaynaktan (KAP bildirimi, TCMB duyurusu, RSS) gelen içerikle çalışır ve kaynağın bağlantısını bültende gösterir. Kaynak yoksa o bölüm bültende yer almaz.
5. **Sırlar koda girmez.** API anahtarları, DB şifresi ve JWT secret yalnızca ortam değişkeninden okunur. `application.properties` içinde `${ENV_VAR:...}` biçimi kullanılır. Gerçek anahtar hiçbir commit'e girmez. `.env.example` dosyası tutulur.
6. **Dış kaynaklara saygı.** TEFAS'ın resmi API'si yok: günde bir kez, fon başına tek istek, istekler arasında gecikme, açıklayıcı bir User-Agent, sonuçlar önbellekte. Hata durumunda üstel geri çekilme uygulanır, 3 denemeden sonra o gün atlanır ve loglanır.
7. **Mevcut API kırılmaz.** `/api/funds` uç noktaları çalışmaya devam eder. Şema değişiklikleri geriye dönük uyumlu olur.
8. **Her kart test içerir.** Yeni servis mantığı için birim testi, dış istemciler için kaydedilmiş örnek yanıtlarla (fixture) test yazılır. Testler ağa çıkmaz. LLM çağrıları testte sahte (mock) bir istemciyle yapılır.
9. **LLM erişimi soyutlanır.** `LlmClient` arayüzü ve varsayılan `AnthropicLlmClient` olur (model adı `llm.model` property'sinden okunur). Anahtar yoksa uygulama yine ayağa kalkar ve `TemplateLlmClient` (şablonla metin üreten, LLM'siz bir yedek) devreye girer. Böylece demo anahtarsız da çalışır.

## 5. Çalışma döngüsü: Kanban protokolü

Tek doğruluk kaynağı `KANBAN.md` dosyasıdır. Sütunlar: **Backlog → To Do → In Progress → Done**, ayrıca **Blocked**.

Her kart için şu döngüyü uygula:

1. **Seç.** `To Do` sütununun en üstündeki kartı al. `To Do` boşsa `Backlog`'dan sıradaki kartı `To Do`'ya taşı. Aynı anda **sadece bir kart** `In Progress` olabilir.
2. **Başlat.** Kartı `In Progress`'e taşı, başlangıç tarihini yaz.
3. **Uygula.** Kodu yaz. Kartın kabul kriterlerine bağlı kal, kapsamı genişletme. Yolda fark ettiğin yeni işleri ayrı kart olarak `Backlog`'a ekle.
4. **Doğrula.** `cd bulletin-service && ./mvnw -q verify` (veya `test`) komutu yeşil olmalı. Kırmızı testle commit atma.
5. **Kartı kapat.** Kabul kriterlerinin kutularını işaretle, kartı `Done`'a taşı, bitiş tarihini ve commit özetini ekle.
6. **Commit ve push** (kod + `KANBAN.md` aynı commit'te):
   ```bash
   git add -A
   git -c user.name="EMİR" -c user.email="emiryoneyler@gmail.com" commit -m "<tip>(<kapsam>): <özet> [<KART-ID>]

   <ne yapıldı ve neden, 1-3 satır>

   Co-Authored-By: Claude <noreply@anthropic.com>"
   git pull --rebase origin master
   git push origin master
   ```
   - Commit tipleri: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`.
   - Commit'ler **EMİR <emiryoneyler@gmail.com>** adına atılır, Claude co-author olarak eklenir (GitHub katkı grafiğinde görünmesi için).
   - Büyük bir kart birden fazla commit'e bölünebilir. Her ara commit de testleri geçmeli ve push edilmeli. Kart yalnızca sonuncusunda `Done`'a taşınır.
7. **Devam et.** Sıradaki karta geç. Kullanıcıdan onay bekleme.

**Engel durumu:** Bir kart kullanıcının vermesi gereken bir şeye takılırsa (API anahtarı, Render paneli, alan adı, SMTP hesabı) kartı `Blocked`'a taşı ve tam olarak neyin gerektiğini yaz. Commit + push yap ve engellenmeyen sıradaki karta geç. Engeli aşmak için sahte değer uydurma.

**Push başarısız olursa:** `git pull --rebase` ile çatışmayı çöz ve tekrar dene. `--force` asla kullanılmaz.

**Oturum sonu:** Her çalışma oturumunun sonunda `KANBAN.md` → "Oturum Günlüğü" bölümüne bir satır ekle (tarih, biten kartlar, açık engeller) ve push et.

## 6. Kod kalitesi beklentileri

- DTO'lar için `record` tercih edilir. Para ve fiyat için `BigDecimal` kullanılır, `Double` kullanılmaz (yeni kodda).
- Zaman dilimi `Europe/Istanbul`, `@Scheduled` cron ifadelerinde `zone` belirtilir.
- Hatalar için `@ControllerAdvice` ile tutarlı hata gövdesi döner. `RuntimeException` fırlatmak yerine özel istisnalar kullanılır.
- Veritabanı şeması için Flyway kullanılır (`ddl-auto=update` yerine `validate`). Mevcut tabloyu temsil eden bir baseline migration ile başlanır.
- Dış istemciler için zaman aşımı yapılandırılır. Hiçbir çağrı süresiz beklemez.
- Her bülten üretiminde şunlar loglanır ve `bulletin_runs` tablosuna yazılır: adım adı, süre, token sayısı, tahmini maliyet, yeniden deneme sayısı, doğrulama sonucu.

## 7. Bitti tanımı (proje geneli)

Proje şu koşullar sağlandığında tamamlanmış sayılır:

- [ ] Uygulama `docker compose up` ile Postgres'le birlikte yerelde ayağa kalkıyor. LLM anahtarı olmadan da şablon yedekle bülten üretiyor.
- [ ] Günlük fiyat işi seçili fonların TEFAS fiyatlarını çekiyor. EVDS'den TÜFE, USD/TRY ve altın verisi geliyor.
- [ ] Bir kullanıcı için haftalık bülten uçtan uca üretiliyor: facts → yazar → sayı doğrulama → uyum kontrolü → PDF → e-posta.
- [ ] Bültende reel getiri ve kıyaslamalar (BIST100, USD/TRY, altın, mevduat) var. Bültendeki her sayı `BulletinFacts`'e kadar izlenebiliyor.
- [ ] CI'da (GitHub Actions) testler her push'ta çalışıyor ve yeşil.
- [ ] README'de mimari çizim, örnek bülten PDF'i, kurulum adımları, tasarım kararları ve "neden bu şekilde" bölümü var.
- [ ] Canlı bir deploy var veya `Blocked` kartında neyin eksik olduğu açıkça yazılı.
