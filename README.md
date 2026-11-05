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

### Testler

```bash
cd bulletin-service
./mvnw verify
```

Veritabanı testleri Testcontainers ile çalışır ve Docker gerektirir. Docker yoksa atlanırlar; CI'da hiçbir test atlanamaz ([ADR 008](docs/decisions/008-testcontainers-without-docker.md)). Kapsam raporu: `bulletin-service/target/site/jacoco/index.html`.

## Ortam değişkenleri

Tam liste ve açıklamalar: [`.env.example`](.env.example). Sırlar yalnızca ortam değişkeninden okunur, koda girmez.

## Tasarım kararları

Karar kayıtları: [`docs/decisions/`](docs/decisions/README.md).

## Bilinen sınırlar

_(E8-1)_
