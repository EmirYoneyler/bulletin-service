# Yazar örneği: Ollama + qwen2.5:7b-instruct

Kart E3-2'nin "Ollama ile gerçek çalıştırma" kriteri için üç gerçek çağrının kaydı. Çağrılar
`WriterLlmLiveTest` ile yapıldı; çıktılar elle düzeltilmedi.

| | |
|---|---|
| Tarih | 2026-11-06 |
| Model | `qwen2.5:7b-instruct` (7,6 milyar parametre, Q4_K_M, 4,7 GB) |
| Sunucu | `ollama/ollama:0.40.14`, `docker compose --profile llm`, **yalnızca CPU** (Docker'a 4 çekirdek; Ollama 2 iş parçacığı kullandı), bağlam penceresi 4096 token |
| Makine | AMD Ryzen 7 7840HS, 16 GB RAM. Makinede RTX 4050 var ama compose servisi GPU'ya erişmiyor |
| Girdi | `SampleFacts`: **testler için uydurulmuş sentetik değerler, piyasa verisi değil.** Model değerleri görmez; yalnız anahtarları, açıklamaları ve yönleri görür (§4.2) |
| İstem | `prompts/writer.v1.md` (deneme 1 ve 2 ara taslaklar, deneme 3 son hâli) |

Yeniden üretmek için:

```bash
docker compose --profile llm up -d ollama
docker compose exec ollama ollama pull qwen2.5:7b-instruct
cd bulletin-service && ./mvnw test -Plive -Dtest=WriterLlmLiveTest
```

Rapor `bulletin-service/target/live/` altına yazılır.

## Özet

| Deneme | İstem | Süre | Token (girdi / çıktı) | Sonuç |
|---|---|---|---|---|
| 1 | İlk taslak | 230 sn | 2272 / 249 | JSON şeması doğru, ama **8 değerin hepsini rakamla uydurdu**; IJB notu yok |
| 2 | + hazır yer tutucu listesi, sondaki hatırlatma, sıkıştırılmış bağlam JSON'u | 158 sn | 2566 / 373 | Rakam yok, tüm yer tutucular geçerli. AU1 (yatay) için "veri yok" dedi; "enflasyon yükseldi" |
| 3 | + "yön yalnız işarettir", "FLAT veri yok demek değildir", oranlar için "yükseldi" deme | 197 sn | 2695 / 397 | Rakam yok, AU1 doğru. "Enflasyon yükseldi" ve "mevduat arttı" sürdü; küçük dil hataları |

Çıkarımlar:

- **Deneme 1, NumberGuard'ın (E4-1) neden zorunlu olduğunu gösteriyor.** Model kuralı okuduğu hâlde her
  değeri uydurdu ve uydurmaların hiçbiri gerçek değerle aynı değildi (aşağıdaki tablo). Metinde rakam
  olması NumberGuard'ın ilk kuralı; bu metin bültene giremez, boru hattı yeniden dener ya da şablona düşer.
- Küçük bir model, anahtardan yer tutucu kurmaktansa hazır yer tutucuyu kopyalamakta çok daha başarılı.
  Liste ve sondaki hatırlatma rakam sızıntısını sıfıra indirdi.
- Rakam içermeyen ama yanlış anlam taşıyan cümleler kalıyor: "Son açıklanan aylık enflasyon %2,6
  yükseldi" bir önceki aya göre artış gibi okunur; model bunu bilemez. Bu, NumberGuard'ın yön kuralına
  bir ek olarak (oran anahtarlarının yanında "yükseldi/arttı" ihlal) E4-1'e not edildi.
- Süre üç denemede de **120 sn'lik Ollama varsayılan zaman aşımını aştı**. Bu makinede varsayılan
  ayarlarla Ollama yazarı her hafta zaman aşımına uğrar ve boru hattı şablon yazara düşer. GPU erişimi,
  zaman aşımı ve bağlam penceresi E3-4 kartında.
- Bağlam penceresi 4096 token; dört fonluk bu örnekte istem 2695 token. Fon sayısı arttıkça istem
  pencereyi aşabilir ve Ollama istemin başını (sistem kurallarını) sessizce keser. E3-4 kartında.

## Deneme 1: ilk taslak istem

Modelin yazdığı (yer tutucu olması gerekirken rakam):

```json
{
  "summary": "İzlediğin fonlar haftayı ortalamada artıda kapattı; ortalama haftalık değişim %0.5 oldu. Haftanın en iyisi AAV, en zayıfı AHU oldu.",
  "fundNotes": [
    { "code": "AAV", "text": "AAV fonu bu hafta değer kazandı; haftalık değişim %0.8." },
    { "code": "AU1", "text": "AU1 fonu haftayı yeterince değişmedi." },
    { "code": "AHU", "text": "AHU fonu haftayı ekside kapattı; haftalık değişim -0.3." }
  ],
  "benchmarkNote": "Aynı hafta dolar/TL %1.2 değişti, BIST 100 getiri endeksi %0.5 geriledi, mevduat faizi %0.3 arttı, altın fiyatları %0.4 düşüş gösterdi, enflasyon %0.6 yükseldi."
}
```

| Değer | Modelin yazdığı | Sentetik girdideki değer |
|---|---|---|
| Ortalama haftalık değişim | %0.5 | %0,8 |
| AAV haftalık | %0.8 | %3,2 |
| AHU haftalık | -0.3 | %-0,9 |
| Dolar/TL haftalık | %1.2 | %0,4 |
| BIST 100 haftalık | %0.5 (geriledi) | %-2,5 |
| Mevduat haftalık | %0.3 | %0,9 |
| Altın aylık | %0.4 (düşüş) | %-1,1 |
| TÜFE aylık | %0.6 | %2,6 |

## Deneme 3: son istem (writer.v1)

Ham çıktı (yer tutuculu):

```json
{
  "summary": "İzlediğim fonlar haftayı ortalamada artıda kapattı; ortalama haftalık değişim {{portfolio.return.1w}} oldu. Haftanın en iyisi AAV, en zayıfı AHU oldu.",
  "fundNotes": [
    { "code": "AAV", "text": "AAV fonu bu hafta değer kazandı; haftalık değişim {{fund.AAV.return.1w}}. Ay başından bu yana ve yılbaşından bu yana de yüksek getiriler sağladı." },
    { "code": "AHU", "text": "AHU fonu haftayı ekside kapattı. Haftalık değişim {{fund.AHU.return.1w}}. Ay başından bu yana da iyi performans gösterdi." },
    { "code": "AU1", "text": "AU1 fonu haftayı neredeyse değişmedi; haftalık değişim {{fund.AU1.return.1w}}." },
    { "code": "IJB", "text": "IJB fonu bu hafta yeterli fiyat verisi yok." }
  ],
  "benchmarkNote": "Aynı hafta dolar/TL {{bench.USDTRY.return.1w}} değişti. BIST 100 getiri endeksi {{bench.BIST100.return.1w}} geriledi. Güncel TL mevduat faiziyle bir haftalık getiri {{bench.DEPOSIT.return.1w}} arttı. Külçe altın fiyatının aylık değişimi {{bench.GOLD.return.mom}} azaldı. Son açıklanan aylık enflasyon (TÜFE) {{bench.CPI.month}} yükseldi."
}
```

`PlaceholderRenderer` sonrası (sentetik değerlerle):

> İzlediğim fonlar haftayı ortalamada artıda kapattı; ortalama haftalık değişim %0,8 oldu. Haftanın en
> iyisi AAV, en zayıfı AHU oldu.
>
> - **AAV**: AAV fonu bu hafta değer kazandı; haftalık değişim %3,2. Ay başından bu yana ve yılbaşından
>   bu yana de yüksek getiriler sağladı.
> - **AHU**: AHU fonu haftayı ekside kapattı. Haftalık değişim %-0,9. Ay başından bu yana da iyi
>   performans gösterdi.
> - **AU1**: AU1 fonu haftayı neredeyse değişmedi; haftalık değişim %0,0.
> - **IJB**: IJB fonu bu hafta yeterli fiyat verisi yok.
>
> Aynı hafta dolar/TL %0,4 değişti. BIST 100 getiri endeksi %-2,5 geriledi. Güncel TL mevduat faiziyle
> bir haftalık getiri %0,9 arttı. Külçe altın fiyatının aylık değişimi %-1,1 azaldı. Son açıklanan aylık
> enflasyon (TÜFE) %2,6 yükseldi.

Kalan sorunlar (rakam içermiyor, NumberGuard'ın bugünkü kurallarıyla yakalanmaz):

- Oranlar için "yükseldi/arttı": "enflasyon %2,6 yükseldi", "mevduat … %0,9 arttı" (E4-1'e not edildi)
- Dil hataları: "İzlediğim" ("İzlediğin" olmalı), "bu yana de" ("da" olmalı), "haftayı neredeyse değişmedi"
- Değerlendirici ifadeler: "yüksek getiriler sağladı", "iyi performans gösterdi" (E4-2 ve E8-3'te ele alınacak)
- Çift olumsuz okunan "%-2,5 geriledi", "%-1,1 azaldı" (istem bunu yasaklıyor, model uymadı)

Aynı girdiyle şablon yazar (`TemplateLlmClient`) rakam yazmaz, yalnız verilen anahtarları kullanır
(`WriterStepTest`) ve enflasyonu "yükseldi" diye değil "olarak açıklandı" diye anlatır; bu yüzden varsayılan
sağlayıcı ve yedek odur (§4.1).
