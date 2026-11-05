# Fon Bülteni

Takip ettiğin yatırım fonlarını her pazartesi TEFAS fiyatlarıyla özetleyen, içindeki her sayı kaynağına kadar izlenebilen kişisel bülten servisi.

> Proje geliştirme aşamasında. Plan ve ilerleme: [`KANBAN.md`](KANBAN.md).

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
