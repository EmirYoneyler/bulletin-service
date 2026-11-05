# CLAUDE.md

Bu repoda çalışırken ana talimat `AGENT_PROMPT.md`, iş listesi `KANBAN.md` dosyasıdır. İkisi de her oturumda yüklenir:

@AGENT_PROMPT.md
@KANBAN.md

## Hızlı hatırlatma
- Maven projesi `bulletin-service/` alt klasöründe: `cd bulletin-service && ./mvnw -q verify`
- Tek kart In Progress. Kart bitince test → KANBAN.md güncelle → commit → push.
- Commit yazarı her zaman `EMİR <emiryoneyler@gmail.com>`, Claude co-author:
  `git -c user.name="EMİR" -c user.email="emiryoneyler@gmail.com" commit ...` + `Co-Authored-By: Claude <noreply@anthropic.com>`
- Anthropic veya başka bir ücretli API kullanılmaz. Varsayılan LLM `template`, yerelde `ollama`.
- LLM rakam yazmaz, yer tutucu yazar (`{{fund.AFT.return.1w}}`).
- `--force` push yasak. Sır commit'lemek yasak.
