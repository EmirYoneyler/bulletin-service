# 001 · TEFAS fiyat geçmişi kaynağı

- **Tarih:** 2026-11-05
- **Durum:** kabul edildi
- **Kart:** E1-3

## Bağlam
TEFAS'ın resmî bir API'si yok. AGENT_PROMPT §7.1, açık kaynak `tefas-crawler`/`pytefas` projelerinin kullandığı `POST /api/DB/BindHistoryInfo` uç noktasını tarif ediyordu. 2026-11-05'te yapılan canlı denemelerde (kullanıcının makinesi, Türkiye; her istek arasında ≥ 2 sn; User-Agent `fonbulteni-bot/1.0 (+github.com/EmirYoneyler/bulletin-service)`):

- `https://www.tefas.gov.tr/` → 307 → `/tr`. Site Next.js ile yeniden yazılmış.
- `POST /api/DB/BindHistoryInfo` → **404** `{"fault":{"faultCode":"ERR-006","faultString":"Method not found or disabled!"}}`. Eski uç nokta kapalı.

## Bulgular
Sitenin JavaScript paketlerinde (`/_next/static/chunks/common-*.js`) "Tarihsel veriler" sayfasının kullandığı servis bulundu:

| | |
|---|---|
| Uç nokta | `POST https://www.tefas.gov.tr/api/funds/fonGnlBlgSiraliGetir` (sitenin kendi proxy'si). Sunucu tarafında aynı servis `https://tefasws.takasbank.com.tr/fug/fonport/portal/service/fonGnlBlgSiraliGetir` adresinde. |
| İstek | `Content-Type: application/json`. Gövde: `{"fonTipi":"YAT","fonKodu":"AAV","basTarih":"20261027","bitTarih":"20261104","basSira":1,"bitSira":100,"dil":"TR", ...}`. Tarihler `yyyyMMdd`. `basSira`/`bitSira` sayfalama (1'den başlar, iki uç dahil). Diğer filtre alanları (`aramaMetni`, `fonTurKod`, `fonGrubu`, `sfonTurKod`, `fonTurAciklama`, `kurucuKod`) `null`. |
| Yanıt | `{"errorCode":null,"errorMessage":null,"resultList":[...],"toplamSayi":6,"toplamSayfa":1}`. Satır: `fonKodu`, `fonUnvan`, `tarih` (**`yyyy-MM-dd` metni**, epoch ms değil), `fiyat`, `tedPaySayisi`, `kisiSayisi`, `portfoyBuyukluk`, `borsaBultenFiyat` (yalnızca BYF), `rn`. Satırlar yeniden eskiye sıralı. |
| Tatil / hafta sonu | Satır yok (ör. 29 Ekim Cumhuriyet Bayramı yanıtta yok). |
| Tarih aralığı sınırı | **En fazla 1 ay.** 90 günlük istek → `errorMessage: "Geçersiz veri: Tarih aralığı 1 ayı aşamaz"`, `resultList: null`. Tam bir takvim ayı (`20261004`–`20261104`) kabul edildi. |
| Boş sonuç | Veri olmayan aralık hata değil ama öyle görünüyor: `errorMessage: "Index 0 out of bounds for length 0"`, `resultList: null`. |
| Sayfa boyutu | `bitSira - basSira + 1 = 100` kabul edildi. Tek fon için bir ay en fazla ~23 satır, yani pratikte tek sayfa. |
| Çerez / WAF | İstek için çerez veya ön GET gerekmedi. Hiçbir istek reddedilmedi ya da yavaşlatılmadı. |

Kaydedilen yanıtlar: `bulletin-service/src/test/resources/fixtures/tefas/`.

## Karar
- `TefasClient`, sitenin proxy'sini (`tefas.base-url`, varsayılan `https://www.tefas.gov.tr/api/funds`) kullanır. Bu, sitenin tarayıcıdan zaten kullandığı adres. Doğrudan Takasbank servis adresi yalnızca belgelenir; gerekirse ayar değiştirilerek kullanılabilir.
- Aralık, **her parça en fazla bir takvim ayı** olacak biçimde bölünür: `[başlangıç, başlangıç + 1 ay − 1 gün]`. Kartın "≤ 90 gün" kriteri bu sınır yüzünden uygulanamaz.
- `errorMessage` için iki durum ayrılır. `"Index ... out of bounds"` "veri yok" sayılır ve boş liste döner. Diğer her mesaj (ör. "Geçersiz veri: ...") isteğin reddedildiği anlamına gelir: yeniden denenmez, `TefasRequestRejectedException` atılır.
- Yeniden deneme yalnızca geçici hatalarda yapılır: 5xx, 429, zaman aşımı veya bağlantı hatası. En fazla 3 deneme, üstel geri çekilme (varsayılan 2 sn, 4 sn). Sonra `TefasUnavailableException`; günlük iş o günü atlayıp loglar (§6.6).
- Ardışık iki istek arasında en az 1,5 sn beklenir (`tefas.request-interval`). Bu kural ayrı `fetchHistory` çağrıları için de geçerli. Paralel istek atılmaz.
- Fon tipi istekte zorunlu (`fonTipi`): yatırım fonları `YAT`, emeklilik fonları `EMK`. İstemci tipi parametre olarak alır.

## Sonuçlar
- TEFAS bu iç servisi haber vermeden değiştirebilir. Değişiklik ilk olarak `@Tag("live")` testinde ve günlük işin loglarında görünür. Bu ADR ve fixture'lar o zaman güncellenmeli.
- Sunucunun boş sonuçta bir Java istisna mesajı döndürmesi kırılgan bir sözleşme. Mesaj değişirse boş gün "reddedildi" hatasına dönüşür ve log'a düşer. Sessizce sahte veri üretmez.
