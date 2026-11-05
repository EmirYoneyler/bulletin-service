# 009 · JWT anahtarının başlangıçta doğrulanması

- **Tarih:** 2026-11-05
- **Durum:** kabul edildi
- **Kart:** E0-2

## Bağlam
`jwt.secret` önceden `application.properties` içinde açık metindi. Sırlar ortam değişkenine taşındı (`JWT_SECRET`). Değişken unutulursa uygulama sessizce boş ya da zayıf bir anahtarla çalışmamalı. jjwt zayıf anahtarı ancak token üretilirken reddeder, bu da hatayı ilk isteğe kadar geciktirir.

## Karar
- `jwt.*` ayarları `JwtProperties` record'una bağlanır (`@ConfigurationProperties`). Kurucu, anahtar boşsa veya **UTF-8 olarak 32 bayttan kısaysa** (HS256 için 256 bit) `InvalidJwtSecretException` atar. Uzunluk karakter değil bayt olarak ölçülür.
- `InvalidJwtSecretFailureAnalyzer` (`META-INF/spring.factories`) bu hatayı Türkçe, düz bir "APPLICATION FAILED TO START" raporuna çevirir: ne eksik ve nasıl düzeltilir (`openssl rand -base64 48`, auth-service ile aynı değer).
- `dev` profili (`application-dev.properties`) sabit, herkese açık bir geliştirme anahtarı içerir. `JWT_SECRET` ayarlıysa yine o kazanır.

## Sonuçlar
- `JWT_SECRET` yokken uygulama başlamaz. Testler `test` profiliyle kendi test anahtarını kullanır.
- Ortam değişkeni **tanımlı ama boş** ise (ör. `.env` içinde `JWT_SECRET=`) Spring bunu "ayarlanmış" sayar ve `dev` profilinde de başlatma reddedilir. `.env.example` bunu açıklar ve dolu bir sahte değer içerir.
- Eski açık metin anahtar git geçmişinde duruyor. Döndürülmesi E8-6'nın konusu.
