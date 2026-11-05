# Fon Bülteni

[![CI](https://github.com/EmirYoneyler/bulletin-service/actions/workflows/ci.yml/badge.svg)](https://github.com/EmirYoneyler/bulletin-service/actions/workflows/ci.yml)

Takip ettiğin yatırım fonlarını her pazartesi TEFAS fiyatlarıyla özetleyen, içindeki her sayı kaynağına kadar izlenebilen kişisel bülten servisi.

> Proje geliştirme aşamasında. Plan ve ilerleme: [`KANBAN.md`](KANBAN.md). Bu README'nin tam hâli E8-1 kartında yazılacak.

## Ne yapar?

_(E8-1: örnek bülten ve PDF ön izlemesi)_

## Mimari

_(E8-1: Mermaid mimari çizimi ve servis haritası)_

## Yerel geliştirme

Gerekenler: Docker (Compose v2). Hiçbir ücretli anahtar gerekmez.

```bash
cp .env.example .env
docker compose up --build
```

| Servis | Adres |
|---|---|
| bulletin-service | http://localhost:8082/api/funds |
| PostgreSQL | `localhost:5433` (kullanıcı ve şifre `.env` dosyasında) |
| Mailpit (test e-postaları) | http://localhost:8025 |

### Yerel LLM (isteğe bağlı)

Ollama `llm` profilindedir ve varsayılan olarak başlamaz:

```bash
docker compose --profile llm up -d
docker compose exec ollama ollama pull qwen2.5:7b-instruct
```

Model bir kez indirilir ve `ollama-models` volume'ünde kalır.

### Kimlik doğrulama

> **Değişiklik (E0-6):** API artık yazma işlemleri için token istiyor. Fon okuma uçları eskisi gibi herkese açık.

Token'lar `auth-service` tarafından üretilir ve aynı `JWT_SECRET` ile imzalanır. İstekte `Authorization: Bearer <token>` başlığıyla gönderilir.

| Uç nokta | Kim erişebilir |
|---|---|
| `GET /api/funds/**` | herkes |
| `POST/PUT/PATCH/DELETE /api/funds/**`, `/api/admin/**` | `ADMIN` rolü |
| `/api/me/**` | oturum açmış her kullanıcı |
| diğer her şey | oturum açmış her kullanıcı (varsayılan olarak kapalı) |

Token yoksa veya geçersizse `401`, rol yetmiyorsa `403` döner. İkisi de diğer hatalarla aynı JSON biçimindedir.

Yerelde `auth-service` olmadan denemek için yalnızca `dev` profilinde bir test token'ı alınabilir (`.env.example` bu profili açar):

```bash
TOKEN=$(curl -s -X POST "http://localhost:8082/api/dev/token?email=ben@example.com&role=ADMIN" | jq -r .token)
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"name": "Örnek Fon"}' http://localhost:8082/api/funds
```

`dev` profili herkese `ADMIN` token'ı verir: **üretimde asla açılmamalıdır**.

### Fiyat verisi (TEFAS)

TEFAS kodu olan her fonun son 5 iş gününün fiyatları her hafta içi 19:30'da (İstanbul) çekilir ve kaydedilir. Kaçan günler sonraki çalışmalarda kendiliğinden dolar. Kaynak ve nezaket kuralları: [ADR 001](docs/decisions/001-tefas.md).

Elle tetiklemek veya bir fonun geçmişini doldurmak için (`ADMIN` token gerekir):

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/admin/jobs/daily-price/run
curl -X POST -H "Authorization: Bearer $TOKEN" \
     "http://localhost:8082/api/admin/ingest/tefas?code=AAV&from=2026-01-01&to=2026-11-04"
```

Yanıt, çalışmanın özetidir (yazılan ve değişmeyen satır, hatalı fonlar). Aynı anda tek bir TEFAS aktarımı çalışır; diğeri `409` alır. Geçmiş doldurma tek seferde en fazla 731 gündür.

### Makro veri (TCMB EVDS)

TÜFE, USD/TRY, altın, TL mevduat faizi ve BIST 100 (getiri endeksi, [ADR 003](docs/decisions/003-bist.md)) her pazartesi 07:00'de (İstanbul) TCMB EVDS'den çekilir. Bunun için ücretsiz kişisel bir anahtar gerekir: [evds3.tcmb.gov.tr](https://evds3.tcmb.gov.tr) → üye ol → Benim Sayfam → Profilim → "API Key Kopyala". Anahtarı `.env` dosyasında `EVDS_API_KEY` olarak verin. Seri kodları ve bilinen sınırlar: [ADR 002](docs/decisions/002-evds.md).

Anahtar yoksa uygulama yine çalışır: açılışta uyarı loglanır, iş "atlandı" (`SKIPPED`) olarak kaydedilir, bültenin kıyas ve reel getiri bölümleri yer almaz. Elle tetiklemek için: `POST /api/admin/jobs/weekly-macro/run` (`ADMIN`).

### Testler

```bash
cd bulletin-service
./mvnw verify
```

Veritabanı testleri Testcontainers ile çalışır ve Docker gerektirir. Docker yoksa atlanırlar; CI'da hiçbir test atlanamaz ([ADR 008](docs/decisions/008-testcontainers-without-docker.md)). Kapsam raporu: `bulletin-service/target/site/jacoco/index.html`.

Testler ağa çıkmaz. TEFAS'a gerçekten bağlanan kontroller `live` etiketlidir ve yalnızca açıkça istenince çalışır:

```bash
cd bulletin-service
./mvnw test -Plive
```

Docker'sız bir makinede veritabanı testlerini de atlamak için: `./mvnw verify -DexcludedGroups=integration,live`.

## Ortam değişkenleri

Tam liste ve açıklamalar: [`.env.example`](.env.example). Sırlar yalnızca ortam değişkeninden okunur, koda girmez.

## Tasarım kararları

Karar kayıtları: [`docs/decisions/`](docs/decisions/README.md).

## Bilinen sınırlar

- Kıyaslar (dolar, altın, mevduat, BIST 100) ve reel getiri TCMB EVDS anahtarı gerektirir. Anahtar yoksa bültende bu bölümler yer almaz.
- EVDS'de altın fiyatı yalnızca aylık; haftalık altın kıyası için günlük bir kaynak henüz yok (KANBAN E1-7).
- TEFAS'ın fiyat servisi resmî bir API değil; site değişirse veri alımı durabilir ([ADR 001](docs/decisions/001-tefas.md)).

_(Tam liste E8-1'de.)_
