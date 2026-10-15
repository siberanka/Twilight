# Cloud-mounted display height and lighting — 29 September 2026

> Türkçe: [aşağıda](#türkçe)

> The screenshots of this report were retired on 4 October 2026 in favour of current captures; the measurements below remain. Current Java/Bedrock captures are in the [README](../README.md#visual-acceptance-tests).

The potion basket now retains its colors above the original stone platform.
Twilight corrects the attachment height of item displays riding Java area-effect
clouds. This applies automatically to matching entities, independently of model
names and providers. Textures, materials, world blocks and Java assets are unchanged.

Java 26.2 attaches a passenger at the cloud's full height. Item displays attach
at their feet and have zero eye height. Geyser's generic mount calculation instead
used 75% of the cloud height. For the tested basket, the cloud at Y=199.5 therefore
placed the display at Y=199.875 instead of Y=200. Its light sample fell inside the
stone platform. Using the Java attachment height corrects both position and light
sampling; no full-bright material or brightness override is introduced.

| Measurement | Value |
|---|---|
| Java cloud height | 0.5 blocks |
| Java cloud position | Y=199.5 |
| Java passenger position | Y=200 |
| Java display vehicle attachment / eye height | Zero / zero |
| Previous translated seat offset | Y=0.375 |
| Corrected translated seat offset | Y=0.5 |

The Java values were measured from unspawned vanilla entities in the isolated
Paper 26.2 runtime. Read-only Geyser inspection after a clean restart confirmed
the corrected offsets on live cloud-mounted displays. No diagnostic seat mutation
was used in that acceptance process. Non-cloud mounts retain their existing offset.

## Tests and limits

The local build passed 67 tests across 17 suites. Both isolated servers received
the same JAR. ModelEngine's bear, crab, archer and basket were sampled again in
seven Java/Bedrock pairs. The basket was also compared at noon and midnight above
unchanged stone: its colors remain visible and its brightness follows world time.
All four meshes remain visible and the earlier unwanted-particle fix remains active.

BetterModel's owl was checked in idle and open states after a clean restart with
the same build. Both meshes and their facing remain correct; sequential live
animation samples do not establish matching frame phase. This checkpoint contains
11 reviewed Java/Bedrock pairs (22 images).

A fresh stationary, invulnerable MythicMobs salamander was spawned through its API
and its ModelEngine binding was verified. Both clients displayed the model in
close shots, but those shots clip its head. The full-frame retake could not obtain
the foreground game window, so MythicMobs visual acceptance remains incomplete
and those images are excluded. An older persisted carrier had no ModelEngine
binding in Java either and is not counted as a successful conversion.

Follow-up: the [extended content checks](BROAD_CONTENT_2026-09-29.md) now include
the completed full-frame MythicMobs retake in both clients. Animation-frame
equality is still not claimed.

The [sample results](images/acceptance/2026-09-29-seats/results.json),
[image hashes](images/acceptance/2026-09-29-seats/sha256.json)
and [runtime measurements](images/acceptance/2026-09-29-seats/measurements.json)
preserve the evidence. The [earlier cloud report](CLOUD_ANCHORS_2026-09-29.md)
contains the dark-basket baseline. The original seven source-file hashes still
match; all ten saved player death counters remain zero. Test mobs are stationary
and invulnerable. Test connection and known overlays are handled by the private
`ai/client-join/join.py` automation, including the Bedrock away overlay.

This corrects one specific mount and lighting defect. Full animation timing,
materials, explicit brightness overrides, billboard behavior, glyphs and UI
parity remain open. Sequential screenshots are not frame-exact animation
comparisons. This is a development checkpoint, not an accepted release. Artwork
remains owned by its creators; the source asset packs are not redistributed.

| Java | Bedrock |
|---|---|
| Basket noon Java | Basket noon Bedrock |
| Basket midnight Java | Basket midnight Bedrock |

---

## Türkçe

### Buluta binen görüntü yüksekliği ve ışıklandırma — 29 Eylül 2026

> Bu raporun ekran görüntüleri 4 Ekim 2026'da güncel görüntüler lehine kaldırıldı; aşağıdaki ölçümler
> geçerlidir. Güncel Java/Bedrock görüntüleri [README](../README.md#visual-acceptance-tests) içindedir.

İksir sepeti artık özgün taş platformun üzerinde renklerini koruyor. Twilight, Java alan etkisi
bulutlarına binen eşya görüntülerinin bağlantı yüksekliğini düzeltir. Bu, model adlarından ve
sağlayıcılardan bağımsız olarak eşleşen varlıklara kendiliğinden uygulanır. Dokular, malzemeler,
dünya blokları ve Java dosyaları değişmez.

Java 26.2 bir yolcuyu bulutun tam yüksekliğine bağlar. Eşya görüntüleri ayaklarından bağlanır ve göz
yükseklikleri sıfırdır. Geyser'ın genel binme hesabı ise bulut yüksekliğinin %75'ini kullanıyordu.
Test edilen sepet için Y=199,5'teki bulut görüntüyü Y=200 yerine Y=199,875'e koyuyordu. Işık örneği taş
platformun içine düşüyordu. Java bağlantı yüksekliğini kullanmak hem konumu hem ışık örneklemesini
düzeltir; tam parlak malzeme veya parlaklık geçersiz kılma eklenmez.

| Ölçüm | Değer |
|---|---|
| Java bulut yüksekliği | 0,5 blok |
| Java bulut konumu | Y=199,5 |
| Java yolcu konumu | Y=200 |
| Java görüntü araç bağlantısı / göz yüksekliği | Sıfır / sıfır |
| Önceki çevrilmiş oturma kayması | Y=0,375 |
| Düzeltilmiş çevrilmiş oturma kayması | Y=0,5 |

Java değerleri yalıtılmış Paper 26.2 ortamında doğurulmamış vanilla varlıklardan ölçüldü. Temiz bir
yeniden başlatmadan sonra salt okunur Geyser incelemesi, canlı buluta binen görüntülerde düzeltilmiş
kaymaları doğruladı. Bu kabul sürecinde tanı amaçlı oturma değişikliği kullanılmadı. Bulut dışı
binekler mevcut kaymalarını korur.

#### Testler ve sınırlar

Yerel derleme 17 paketteki 67 testi geçti. İki yalıtılmış sunucu da aynı JAR'ı aldı. ModelEngine'in
ayısı, yengeci, okçusu ve sepeti yedi Java/Bedrock çiftinde yeniden örneklendi. Sepet ayrıca değişmemiş
taşın üzerinde öğlen ve gece yarısı karşılaştırıldı: renkleri görünür kalıyor ve parlaklığı dünya
zamanını izliyor. Dört ağ da görünür ve önceki istenmeyen parçacık düzeltmesi etkin kalıyor.

BetterModel'in baykuşu aynı derlemeyle temiz bir yeniden başlatmadan sonra boşta ve açık durumlarda
denetlendi. İki ağ ve yönleri doğru kalıyor; ardışık canlı animasyon örnekleri eşleşen kare evresi
kanıtlamaz. Bu kontrol noktası incelenmiş 11 Java/Bedrock çifti (22 görüntü) içerir.

Yeni, sabit ve hasar almayan bir MythicMobs semenderi API ile doğuruldu ve ModelEngine bağlantısı
doğrulandı. İki istemci de modeli yakın çekimlerde gösterdi, ancak bu çekimler kafasını kesiyor. Tam
kare yeniden çekim ön plandaki oyun penceresini alamadı; bu yüzden MythicMobs görsel kabulü eksik kaldı
ve bu görüntüler hariç tutuldu. Daha eski kalıcı bir taşıyıcının Java'da da ModelEngine bağlantısı
yoktu ve başarılı dönüşüm sayılmaz.

Devamı: [genişletilmiş içerik denetimleri](BROAD_CONTENT_2026-09-29.md) artık iki istemcide de
tamamlanmış tam kare MythicMobs yeniden çekimini içeriyor. Animasyon karesi eşitliği hâlâ iddia
edilmiyor.

[Örnek sonuçları](images/acceptance/2026-09-29-seats/results.json),
[görüntü karmaları](images/acceptance/2026-09-29-seats/sha256.json) ve
[çalışma zamanı ölçümleri](images/acceptance/2026-09-29-seats/measurements.json) kanıtı saklar.
[Önceki bulut raporu](CLOUD_ANCHORS_2026-09-29.md) karanlık sepet temel çizgisini içerir. Özgün yedi
kaynak dosya karması hâlâ eşleşiyor; kaydedilmiş on oyuncu ölüm sayacı da sıfır. Test mobları sabit ve
hasar almaz. Test bağlantısı ve bilinen kaplamalar, Bedrock uzakta kaplaması dahil, özel
`ai/client-join/join.py` otomasyonuyla yönetilir.

Bu, belirli bir binek ve ışıklandırma hatasını düzeltir. Tam animasyon zamanlaması, malzemeler, açık
parlaklık geçersiz kılmaları, billboard davranışı, glifler ve arayüz eşdeğerliği açık kalıyor.
Ardışık ekran görüntüleri kare kare birebir animasyon karşılaştırması değildir. Bu kabul edilmiş bir
sürüm değil, bir geliştirme kontrol noktasıdır. Görseller yaratıcılarına aittir; kaynak paketler
yeniden dağıtılmaz.

| Java | Bedrock |
|---|---|
| Sepet öğlen Java | Sepet öğlen Bedrock |
| Sepet gece yarısı Java | Sepet gece yarısı Bedrock |
