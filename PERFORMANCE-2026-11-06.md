# TV performans incelemesi — 2026-11-06

## Kapsam ve yöntem
- Ağ ADB: 192.168.1.187:5555, com.nuvio.tv.turbo PID 18660. Uygulama yeniden başlatılmadı, tuş gönderilmedi, sayaçlar sıfırlanmadı, APK kurulmadı.
- Yerel HEAD 6ea060490 (derleme 9 kaynak anahtarı önbelleği); TV versionName 1.1.0-beta.11, son kurulum 15:02:48. APK commit kimliği paket bilgisinden bağımsız doğrulanmadı.
- PID loglarında 15:04:46 açılışı, 15:18–15:19 gezinme, 15:30 kaynak ekranı ve 15:31 oynatıcı geçişi var. Log tamponu eksik olabilir.

## Ölçümler
JankStats aşağıdaki sayıları yalnız gecikmiş kareler için kaydediyor; p50/p90 bütün karelerin yüzdeliği değildir. Süre toplamı kullanıcı bekleme süresi değildir.

| Ekran | Gecikmiş kare | p50 ms | p90 ms | En kötü ms |
|---|---:|---:|---:|---:|
| Ana sayfa | 61 | 98 | 203 | 304 |
| Detay | 38 | 91,5 | 177 | 246 |
| Kaynaklar | 57 | 46 | 80 | 122 |
| Oynatıcı geçişi (kısa örnek) | 10 | 69 | 185 | 234 |

- gfxinfo kümülatif: 1221 çizilen kare, 462 gecikmiş (%37,84); bütün karelerde p50 14 ms, p90 69 ms, p95 117 ms, p99 250 ms. Slow UI thread 337, slow draw commands 259, slow bitmap uploads 31. Sayaçlar örtüşebilir.
- Açılış ilk TurboPerf penceresi: en uzun callback aralığı 710 ms. 15:04:46.866 Choreographer: 84 kare atlandı. Bu iki ölçüm aynı metrik değildir ve toplanamaz.
- 15:30:37 kaynak penceresi: 541 callback, 25 geç, en uzun aralık 143 ms. Sonraki pencere 579/13/70 ms. Handoff'taki önceki 1169 ms ile aynı kumanda senaryosu olmadığından hızlanma oranı çıkarılamaz.
- Heap 15:24:57 30 MB -> 15:25:07 15 MB; 15:29:17 29 MB -> 15:29:27 15 MB. Bu örnek, önceki sızıntı şüphesini doğrulamıyor. GC toplam süresi ana iş parçacığının durma süresi değildir: örneğin 15:30:48 toplam 255,785 ms, pause yalnız 0,548 ms.

## Kaynak koduyla desteklenen adaylar
1. **Kaynak sonuçları geldikçe sayfalama kayboluyor.** StreamScreenViewModel.kt applySuccess: ilk yükte STREAM_FILTER_PAGE_SIZE uygulanıyor, sonraki sonuçta fullFiltered kullanılıyor. Sonraki eklenti cevabı listeyi bir anda büyütebilir. Açılmış sayfa sınırını koru; kullanıcı aşağı indikçe büyüt. Liste sıralamasını gezinme sırasında sabit tutmak odak UX'i için ayrıca değerlendirilmeli. Etkinin ms maliyeti henüz ayrı ölçülmedi.
2. **Liste gruplama/sıralama ana collector yolunda.** applySuccess, her başarılı eklenti sonucunda groupStreamsByProvider çalıştırıyor; direct-debrid hazırlama yolu ise zaten Default dispatcher kullanıyor. Saf hesaplamayı Default'a taşıyıp yalnız güncel nesil sonucunu yayımla; kısa sonuç birleştirme penceresi değerlendir. Nedensellik trace ile doğrulanmalı; mevcut JankStats tek başına sıralama süresini söylemez.
3. **Trakt açılışta otomatik çalışıyor ve hatalı istekler sürüyor.** TraktProgressService init -> refreshEvents().onStart immediate fetch. Loglarda 15:04:46, 15:05:46, 15:06:46, 15:08:46, 15:12:46, 15:20:46, 15:30:46 hata var. Backoff mevcut; sıkı sonsuz retry iddiası doğru olmaz. İlk çizimden sonra başlatma/ilgili ekranda etkinleştirme, önbelleği hemen gösterme ve bağlantı hatasını ayarlarda anlaşılır gösterme önerilir. Ağ isteğinin UI takılmasına doğrudan neden olduğu kanıtlanmadı.
4. **Ölçümün kendisi sürekli callback üretiyor.** TurboPerf her doFrame içinde yeniden postFrameCallback çağırıyor, lifecycle ile durmuyor. Boşta 600 callback/10 sn gerçek 600 çizilen kare demek değil. Perf modu opt-in olmalı; arka planda durmalı. JankStats da her gecikmiş karede uzun route şablonunu yazıyor; ekran adı + pencere özetiyle kısaltılabilir. Bu overhead'in TV maliyeti henüz ölçülmedi.
5. **Önbellek identity değil equality kullanıyor.** StreamProviderGrouping yorumunda identity denmesine rağmen WeakHashMap Stream.equals/hashCode kullanır. Stream yapısının hash maliyeti ve tekrar oluşturulan nesnelerde cache etkinliği profiler ile araştırılmalı; mevcut düzeltme başarısız kabul edilmemeli.

## Öncelik ve UX
- Önce ana sayfa/detay 200–304 ms sıçramaları: odak değişimi, hero güncellemesi, görsel decode ve Compose yerleşimini ayrı trace dilimlerine ayır. Hero/slayt kullanıcı kararıyla korunmalı.
- Kaynak listesinde açılmış sayfayı ve seçili akış kimliğini koru; yeni sonuç sayısını görünür göster. Ölçülmeden odak kayması yaşandı denmez.
- Açılış, detay verisi hazır, ilk kaynak, tüm kaynaklar, route hazır, ilk video karesi için monotonic başlangıç/bitiş işaretleri ekle. Şu an player_open var; ilk kare ölçümü olmadığı için yeni oturumun oynatma başlangıcına ms değeri verilemez.
- Aynı kumanda senaryosuyla önce/sonra ölçüm yapılmadan iyileşme tamamlandı denmez. Bu incelemede uygulama kodu değiştirilmedi.

## Aynı gün düzeltme çalışması (TV doğrulaması bekliyor)
- Trakt derleme yapılandırması local.properties içinde tamamlandı (değerler gizli/Git dışı). Yetkilendirme endpoint'i uygulamanın API başlıklarıyla JSON yanıt verdi; hesap bağlantısı yapılmış sayılmaz.
- HDHub: oynatılan Pixeldrain URL'si güncel HDHub yanıtıyla birebir eşleştirildi. Dosya başında sadece sondaki SeekHead'e işaret eden indeks var. Sondaki indeks hem Cues hem Tracks konumunu içeriyor; track metadata gerçekten sonda (AVC + AAC + ASS). Kod bu ikinci indeksten sonra Cues'u önce okuyup boş track setini yayımlıyor. Tracks önceliği/asıl cluster'a dönüş düzeltildi; empty endTracks engellendi. Bu bulgu genel HDHub servis kesintisi değil, bu MKV düzeniyle ilgili.
- Plavio: img.hscow.com kaynağı addon yanıtıyla eşleşiyor; logda AVC donanım decoder 147 ms, 1696x720. bcdnxw.hakunaymatata.com denemesinde de AVC 122 ms/1280x546 var, sağlayıcı eşleşmesi ayrıca kesinleştirilmedi. Son macdn.hakunaymatata.com MP4 isteği 165 ms'de açılmış ama logda decoder/ilk kare yok. PC'de ilk 1 KB HTTP206; son 64KB isteği 10 sn'de timeout. TV oynatıcı bu denemede yaklaşık 3,1 sn sonra kapatılmış (15:36:42.740); açılış tamamlanmadan çıkıldığı için tek başına başarısız kaynak kanıtı değildir. Sunucu/CDN aralık erişim sorunu adayı; tek deneme ve PC ağıyla kesin kök neden sayılmaz.
- HTTP410 pengu.uk denemesi HDHub veya Plavio'ya bağlanamadı; Plavio hatası diye raporlanmamalı.
- Kod: kaynak sayfası açık sayfa sayısını korur, sıralama Default dispatcher'da; Trakt otomatik ilk iş 15 sn gecikir (manuel hemen); plugin senkronu ilk 20 sn ertelenir; manifest formatı algılarken beklenen JsonDataException kısa loglanır, dış format fallback korunur; perf sürekli callback yalnız DEBUG log property ile açıkken/foreground çalışır; predraw, kaynak hazır ve ilk video karesi/track işaretleri eklendi.
- Yeni APK TV'ye ayarlar korunarak kuruldu; speed derleme Success / 113758 ms. Activity launch TotalTime 1033 ms; bu ana sayfanın tamamen hazır olma süresi değildir. İlk log kontrolünde crash/Trakt progress hatası yok, default sürekli perf örneklemesi kapalı. 15 başlangıç karesinde %93,33 jank var; bu küçük başlangıç örneği önceki gezinme senaryosuyla kıyaslanamaz. Performans kazancı, Trakt hesap onayı ve HDHub/Plavio yeniden denemesi henüz doğrulanmadı.
- Seçili fullRelease regresyon testleri: 39 test, 0 failure/error. Release build başarılı (16 dk 20 sn). APK SHA256: 4b0eb5aba2dc06aad8b1d7a8282d65ba3abad944817a01c02f86e8f4d229d3ce.

## Kullanıcı yeniden denemesi — TV log doğrulaması
- Kullanıcı yeni sonuçları olumlu bildirdi. PID 20186 logları salt okunur ağ ADB ile yeniden kontrol edildi; oynatma bölünmedi.
- HDHub/Pixeldrain: üç oturumda 4 track grubu, desteklenen video+audio; AVC/HEVC donanım decoder. player_open -> gerçek ilk kare: 4818, 5042, 5568 ms. Önceki boş track/decoder yok durumu bu denemelerde giderildi.
- Plavio macdn MP4: 2 track grubu, AVC donanım decoder init 188 ms; player_open -> ilk kare 4861 ms. Önceki kısa/sonuçsuz denemenin artık oynadığı doğrulandı.
- İlk kaynak sonucu: 267/258/198 ms. Tüm sonuçlar: 3092/301/200 ms; farklı/cache etkili oturumlar, karşılaştırmalı performans testi sayılmaz.
- Fatal crash ve Trakt progress fetch hatası: 0. Kullanıcının olumlu geri bildirimi var; hesap tokenları/oturum bilgileri okunmadı.

## Kalan arayüz takılması — 16:22 sonrası inceleme
- Kümülatif gfxinfo: 2630 render karesi, %43,19 jank; p50 17/p90 77/p95 125/p99 250 ms. Slow UI thread 718, slow draw commands 711, bitmap uploads 83; sayaçlar örtüşebilir, farklı kullanım önceki sürümle kıyaslanamaz.
- Son saklanan 120 gerçek render karesi (Flags=0): p50 39,5 ms, p90 86,3 ms, max 151,3 ms; 9 kare >100 ms.
- 16:14:14.982 / 16:22:21.249: Choreographer 50/52 skipped frame. 60 Hz kabulüyle yaklaşık 833/867 ms ölçeği; bu doğrudan ölçülmüş tek kare süresi değildir. Detay hero kompozisyonunu izleyen codec kaynak iadesi ve surface teardown ile çakışıyor. Kapanış/geçiş yolu güçlü aday; trace süresi olmadan kök neden sayılmaz.
- Fragmanlar: bir YouTube extraction başarısızlığı sonrası backend resolver localhost:443'e gidip ECONNREFUSED alıyor. NetworkModule Trailer retrofit boş TRAILER_API_URL için localhost fallback kullanıyor. Bu bazı fragmanları engeller; tüm oynatma kaynaklarının yavaşlığı demek değildir.
- PSS yaklaşık 236 MB. GC sonrası heap tekrar düşüyor; bu kayıt sızıntı kanıtı değil. En uzun görünen GC total 1,45 sn iken stop pause 2,782 ms; total GC süresi UI donması gibi raporlanmadı.
- Salt okunur kontrol; uygulama durdurulmadı, sayaçlar sıfırlanmadı, debug örneklemesi açılmadı, kod değiştirilmedi.
