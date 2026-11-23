# Spotify Sözler – Android

Masaüstü sürümünün telefon uyarlaması: Spotify'da çalan şarkının sözleri
diğer uygulamaların üstünde, saydam ve senkronize kayar.

## APK nasıl alınır (Android Studio gerekmez)
1. GitHub'da boş bir repo aç, bu klasörün içeriğini yükle (main dalı).
2. Repo > Actions > "APK derle" > Run workflow (push'ta da kendiliğinden çalışır).
3. İş bitince sayfanın altındaki Artifacts > SpotifySozler-apk indir, zip'ten çıkan app-debug.apk'yı telefona kur.

Alternatif: Android Studio ile klasörü aç > Build > Build APK(s).

## İlk kurulum (telefonda)
1. Uygulamayı aç > "Bildirim erişimini aç" > Spotify Sözler'i etkinleştir.
   (Android 13+ üstünde yandan yüklenen uygulamalarda seçenek gri olabilir:
   Ayarlar > Uygulamalar > Spotify Sözler > ⋮ > "Kısıtlı ayarlara izin ver", sonra tekrar dene.)
2. "Üstte gösterme iznini aç" > izin ver.
3. "Başlat" > Spotify'da şarkı çal.

## Kısayol -> uygulama karşılıkları
Kilitle/aç ........ "Kilitli" anahtarı
Yazı boyutu ....... slider
Senkron ayarı ..... slider + ±0.1 sn düğmeleri
Çıkış ............. "Durdur" düğmesi veya bildirimdeki "Durdur"
"Masaüstünde kal / üstte göster" Android'de karşılığı yok; pencere hep üstte durur.
