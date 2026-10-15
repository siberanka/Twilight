# Additional real-model review — 2026-09-28

> Türkçe: [aşağıda](#türkçe)

> The screenshots of this report were retired on 4 October 2026 in favour of current captures; the measurements below remain. Current Java/Bedrock captures are in the [README](../README.md#visual-acceptance-tests).

**Overall visual acceptance remains incomplete.** All seven additional models produced visible
Bedrock geometry, but the comparison exposed lighting, particle and orientation
differences. A successful conversion count is not a successful visual test.
The BetterModel facing defect was subsequently corrected and retested below.
This review does not approve a release.

The operator supplied seven unchanged models from two local server installations.
Source and copied model SHA-256 values matched for every selected asset. Java used
the provider-generated original resource packs, with their bytes verified after
copying. The source servers were read-only; all spawning, conversion and diagnostic
changes took place on isolated local test servers.

## Baseline coverage and results

| Provider | Model | Sampled states | Observation |
|---|---|---|---|
| ModelEngine R4.1.1 | `bear_brown` | idle, attack | Assembled body, fish and attack pose visible; unwanted black particles in Bedrock. |
| ModelEngine R4.1.1 | `crab_hermit` | idle, hide | Body and shell visible; black particles obscure the small model. |
| ModelEngine R4.1.1 | `angel_gm_archer_one` | idle, attack1 | Body, wings and bow visible; particles and a pose/orientation discrepancy prevent acceptance. |
| ModelEngine R4.1.1 | `basket_with_health_potions` | static | Recognizable geometry, but almost black in Bedrock while brightly textured in Java; unwanted particles also present. |
| BetterModel 3.4.2-SNAPSHOT-516 | `pet_griffon_phoenix` | idle, interact | Textured body, wings and staff visible; orientation differs from Java. |
| BetterModel 3.4.2-SNAPSHOT-516 | `blacksmith_hm5_npc` | idle, wave | NPC and anvil visible; orientation differs from Java. |
| BetterModel 3.4.2-SNAPSHOT-516 | `owl_crate` | idle, open | Chest geometry and opening movement visible; orientation differs from Java. |

The matrix contains 13 Java/Bedrock pairs (26 original captures). Each pair uses
the same requested player location, yaw and pitch. Client FOV, resolution and
rendering differ; these are visual comparisons, not pixel equality tests.
ModelEngine animation clocks were frozen after a short advance. BetterModel
captures are sequential live samples and **do not establish matching animation
frames or timing**. No complete animation cycle or arbitrary-model guarantee is
inferred from these samples.

The two complete test packs converted 172/172 and 70/70 volumetric candidates,
respectively, with zero compiler problems. These counts include existing fixtures
and individual bone meshes; they are not counts of accepted mobs. The baseline
development JAR SHA-256 is
`ba1f08919c262375c866ae21f6f221221da513686b14d2f2e7b7192413932817`.

## Facing correction and follow-up

The runtime probe found display body yaw at -180 degrees while head yaw remained
zero. Java item displays have no independent head, but Bedrock rotated the mounted
mesh toward that head heading. The bridge now aligns head yaw with body yaw at
spawn and in relative/absolute movement. It ignores independent head-look updates.
The generated rig also stops applying actor yaw a second time.

The rebuilt JAR passed all 65 local tests across 16 suites. Its SHA-256 is
`ffa682351d2893e8b2bda5cb3c8d4acc52cd651267ce584cbd8768daf5e6fb5f`.
A full BetterModel conversion produced 70/70 meshes without compiler problems,
was deployed, and both clients rejoined through the connection script. The manual
probe pack was replaced by the compiler-generated pack before this follow-up.

All three BetterModel models now face the Java reference in the sampled scenes.
The owl also matched the reference heading after carrier rotations to 0, 90 and
-45 degrees, followed by restoration to 180 degrees. These checks exercise fresh
spawn and subsequent rotation updates. They do not establish frame-perfect
animation playback, pitch behavior, mount offsets or lighting parity.

The first four follow-up pairs were captured in rain. Weather was cleared during
the owl idle pair; the three dedicated heading comparisons used clear weather.
World weather differences are not treated as model conversion results. The NPC
and pet poses are still sequential live animation samples, not synchronized frames.
See the [follow-up results](images/acceptance/2026-09-28-matrix/yaw-fix/results.json)
and [hash manifest](images/acceptance/2026-09-28-matrix/yaw-fix/sha256.json).

| Java heading reference | Bedrock after correction |
|---|---|
| Owl yaw 0 Java | Owl yaw 0 Bedrock |
| Owl yaw 90 Java | Owl yaw 90 Bedrock |
| Owl yaw -45 Java | Owl yaw -45 Bedrock |

The same new JAR was also rebuilt into the ModelEngine pack (172/172 meshes,
zero compiler problems), deployed, and tested after a full restart and fresh
client join. All four models remained visible across seven paired samples.
Unwanted black particles and the basket's severe darkening persisted; full pose
acceptance remains open. These follow-up captures are included in the same
machine-readable results and image manifest.

## Reproduction and limits

Use licensed local copies of the selected models with their original provider.
Attach each model to a stationary invulnerable carrier with AI and gravity
disabled, enable creative mode for both test players, and download the converted
pack through Geyser. Reconnect through the local client-join automation, then
capture the same camera coordinates in each client. Restore idle after sampling.
The private `ai/MODEL_MATRIX.md` describes the automated local runner and its
guards; neither it nor the private asset packs are published.

Both players had 20/20 health after the capture sequences. All saved player death
counters in both test worlds remained zero. Production combat skills were not
imported. This is a direct-provider model test; the earlier MythicMobs integration
probe remains documented in [the real-content review](REAL_CONTENT_REVIEW.md).

The basket also triggered provider warnings about its authored eye height and
duplicate bone names. Its source was left unchanged. A controlled lighting probe replaced only the 25
stone platform blocks beneath it with glass. Bedrock then displayed normal basket
and bottle colors without changing the model, texture or Java pack. All 25 blocks
were restored to stone afterward. This implicates light sampling/occlusion around
the display anchor; it does not identify a complete generic correction. Zero-radius ModelEngine pivot clouds and Geyser's
minimum cloud radius are a candidate explanation for the unwanted particles,
not a confirmed fix. No blanket suppression of gameplay particles was introduced.

The screenshots are compatibility evidence for operator-provided artwork. They
do not license redistribution of the original models, textures or generated
packs. BetterModel is by toxicity188 and contributors, ModelEngine by Ticxo,
and MythicMobs by Lumine; artwork remains attributable to its respective creators.

## Paired captures

Java is the left column; Bedrock is the right column. See the [machine-readable results](images/acceptance/2026-09-28-matrix/results.json) and [image hashes](images/acceptance/2026-09-28-matrix/sha256.json).

| Java | Bedrock |
|---|---|
| bear_brown idle Java | bear_brown idle Bedrock |
| bear_brown attack Java | bear_brown attack Bedrock |
| crab_hermit idle Java | crab_hermit idle Bedrock |
| crab_hermit hide Java | crab_hermit hide Bedrock |
| angel_gm_archer_one idle Java | angel_gm_archer_one idle Bedrock |
| angel_gm_archer_one attack1 Java | angel_gm_archer_one attack1 Bedrock |
| basket_with_health_potions static Java | basket_with_health_potions static Bedrock |
| pet_griffon_phoenix idle Java | pet_griffon_phoenix idle Bedrock |
| pet_griffon_phoenix interact Java | pet_griffon_phoenix interact Bedrock |
| blacksmith_hm5_npc idle Java | blacksmith_hm5_npc idle Bedrock |
| blacksmith_hm5_npc wave Java | blacksmith_hm5_npc wave Bedrock |
| owl_crate idle Java | owl_crate idle Bedrock |
| owl_crate open Java | owl_crate open Bedrock |

## Basket lighting isolation

| Java over temporary glass platform | Bedrock over the same platform |
|---|---|
| Java basket glass lighting probe | Bedrock basket glass lighting probe |

Follow-up: the [29 September cloud-anchor regression](CLOUD_ANCHORS_2026-09-29.md)
confirms an automatic plugin fix for the unwanted particles. The subsequent
[mount-height regression](DISPLAY_SEATS_2026-09-29.md) corrects the basket lighting
on unchanged stone. The images here retain the earlier failing baseline.

Changing the platform is a diagnostic step, not a product fix. The normal stone
platform was restored. Unwanted particles remained visible throughout this probe.

---

## Türkçe

### Ek gerçek model incelemesi — 28 Eylül 2026

> Bu raporun ekran görüntüleri 4 Ekim 2026'da güncel görüntüler lehine kaldırıldı; aşağıdaki ölçümler
> geçerlidir. Güncel Java/Bedrock görüntüleri [README](../README.md#visual-acceptance-tests) içindedir.

**Genel görsel kabul eksik kalıyor.** Yedi ek modelin hepsi görünür Bedrock geometrisi üretti, ancak
karşılaştırma ışıklandırma, parçacık ve yön farklarını ortaya çıkardı. Başarılı dönüşüm sayısı başarılı
bir görsel test değildir. BetterModel yön hatası daha sonra düzeltildi ve aşağıda yeniden test edildi. Bu
inceleme bir sürümü onaylamaz.

İşletmeci iki yerel sunucu kurulumundan değiştirilmemiş yedi model sağladı. Seçilen her varlık için kaynak
ve kopyalanmış model SHA-256 değerleri eşleşti. Java, kopyalamadan sonra baytları doğrulanan,
sağlayıcının ürettiği özgün kaynak paketlerini kullandı. Kaynak sunucular salt okunurdu; tüm doğurma,
dönüşüm ve tanı değişiklikleri yalıtılmış yerel test sunucularında yapıldı.

#### Temel kapsam ve sonuçlar

| Sağlayıcı | Model | Örneklenen durumlar | Gözlem |
|---|---|---|---|
| ModelEngine R4.1.1 | `bear_brown` | bekleme, saldırı | Birleşik gövde, balık ve saldırı pozu görünür; Bedrock'ta istenmeyen siyah parçacıklar. |
| ModelEngine R4.1.1 | `crab_hermit` | bekleme, saklanma | Gövde ve kabuk görünür; siyah parçacıklar küçük modeli örtüyor. |
| ModelEngine R4.1.1 | `angel_gm_archer_one` | bekleme, saldırı1 | Gövde, kanatlar ve yay görünür; parçacıklar ve bir poz/yön farkı kabulü engelliyor. |
| ModelEngine R4.1.1 | `basket_with_health_potions` | durağan | Tanınabilir geometri, ancak Java'da parlak dokuluyken Bedrock'ta neredeyse siyah; istenmeyen parçacıklar da var. |
| BetterModel 3.4.2-SNAPSHOT-516 | `pet_griffon_phoenix` | bekleme, etkileşim | Dokulu gövde, kanatlar ve asa görünür; yön Java'dan farklı. |
| BetterModel 3.4.2-SNAPSHOT-516 | `blacksmith_hm5_npc` | bekleme, el sallama | NPC ve örs görünür; yön Java'dan farklı. |
| BetterModel 3.4.2-SNAPSHOT-516 | `owl_crate` | bekleme, açılma | Sandık geometrisi ve açılma hareketi görünür; yön Java'dan farklı. |

Matris 13 Java/Bedrock çifti (26 özgün görüntü) içerir. Her çift istenen aynı oyuncu konumunu, yaw ve
pitch değerini kullanır. İstemci FOV'u, çözünürlüğü ve çizimi farklıdır; bunlar piksel eşitliği testi
değil, görsel karşılaştırmalardır. ModelEngine animasyon saatleri kısa bir ilerlemeden sonra donduruldu.
BetterModel görüntüleri ardışık canlı örneklerdir ve **eşleşen animasyon karelerini veya zamanlamasını
kanıtlamaz**. Bu örneklerden tam bir animasyon döngüsü veya rastgele model güvencesi çıkarılmaz.

İki tam test paketi sırasıyla 172/172 ve 70/70 hacimli adayı sıfır derleyici sorunuyla dönüştürdü. Bu
sayılar mevcut düzenekleri ve tek tek kemik ağlarını içerir; kabul edilmiş mob sayıları değildir. Temel
geliştirme JAR SHA-256 değeri
`ba1f08919c262375c866ae21f6f221221da513686b14d2f2e7b7192413932817`.

#### Yön düzeltmesi ve devamı

Çalışma zamanı denemesi görüntünün gövde yaw değerini -180 derece, kafa yaw değerini ise sıfır buldu.
Java eşya görüntülerinin bağımsız bir kafası yoktur, ancak Bedrock bağlı ağı bu kafa yönüne döndürüyordu.
Köprü artık doğuşta ve göreli/mutlak harekette kafa yaw değerini gövde yaw değeriyle hizalar. Bağımsız
kafa bakış güncellemelerini yok sayar. Üretilen iskelet de aktör yaw değerini ikinci kez uygulamayı
bırakır.

Yeniden derlenen JAR, 16 paketteki 65 yerel testin hepsini geçti. SHA-256 değeri
`ffa682351d2893e8b2bda5cb3c8d4acc52cd651267ce584cbd8768daf5e6fb5f`. Tam bir BetterModel dönüşümü derleyici
sorunu olmadan 70/70 ağ üretti, dağıtıldı ve iki istemci de bağlantı betiğiyle yeniden katıldı. Bu
devamdan önce elle hazırlanan deneme paketi derleyicinin ürettiği paketle değiştirildi.

Üç BetterModel modelinin hepsi örneklenen sahnelerde artık Java referansına bakıyor. Baykuş da taşıyıcı
0, 90 ve -45 dereceye döndürülüp ardından 180 dereceye geri getirildikten sonra referans yönle eşleşti.
Bu denetimler yeni doğuşu ve sonraki dönüş güncellemelerini sınar. Kare kare kusursuz animasyon
oynatımını, pitch davranışını, binek kaymalarını veya ışık eşdeğerliğini kanıtlamaz.

İlk dört devam çifti yağmurda çekildi. Baykuş bekleme çifti sırasında hava açıldı; üç ayrı yön
karşılaştırması açık havada yapıldı. Dünya hava durumu farkları model dönüşüm sonucu sayılmaz. NPC ve
evcil hayvan pozları hâlâ eşzamanlı kareler değil, ardışık canlı animasyon örnekleridir.
[Devam sonuçlarına](images/acceptance/2026-09-28-matrix/yaw-fix/results.json) ve
[karma manifestine](images/acceptance/2026-09-28-matrix/yaw-fix/sha256.json) bakın.

| Java yön referansı | Düzeltmeden sonra Bedrock |
|---|---|
| Baykuş yaw 0 Java | Baykuş yaw 0 Bedrock |
| Baykuş yaw 90 Java | Baykuş yaw 90 Bedrock |
| Baykuş yaw -45 Java | Baykuş yaw -45 Bedrock |

Aynı yeni JAR ModelEngine paketine de yeniden derlendi (172/172 ağ, sıfır derleyici sorunu), dağıtıldı ve
tam yeniden başlatma ile yeni istemci katılımından sonra test edildi. Dört modelin hepsi yedi eşlenmiş
örnekte görünür kaldı. İstenmeyen siyah parçacıklar ve sepetin ağır kararması sürdü; tam poz kabulü açık
kalıyor. Bu devam görüntüleri aynı makine tarafından okunabilir sonuçlara ve görüntü manifestine dahildir.

#### Yeniden üretim ve sınırlar

Seçilen modellerin lisanslı yerel kopyalarını özgün sağlayıcılarıyla kullanın. Her modeli AI'ı ve
yerçekimi kapalı, sabit ve hasar almayan bir taşıyıcıya bağlayın, iki test oyuncusu için yaratıcı modu
açın ve dönüştürülmüş paketi Geyser üzerinden indirin. Yerel istemci katılım otomasyonuyla yeniden
bağlanın, ardından her istemcide aynı kamera koordinatlarını çekin. Örneklemeden sonra bekleme durumuna
dönün. Özel `ai/MODEL_MATRIX.md` otomatik yerel çalıştırıcıyı ve korumalarını anlatır; ne o ne de özel
varlık paketleri yayımlanır.

Çekim dizilerinden sonra iki oyuncunun da canı 20/20'ydi. İki test dünyasındaki tüm kayıtlı oyuncu ölüm
sayaçları sıfır kaldı. Üretim savaş becerileri içe aktarılmadı. Bu doğrudan sağlayıcı model testidir;
önceki MythicMobs entegrasyon denemesi [gerçek içerik incelemesinde](REAL_CONTENT_REVIEW.md) belgelenmiş
olarak kalır.

Sepet ayrıca yazarın verdiği göz yüksekliği ve yinelenen kemik adları hakkında sağlayıcı uyarıları
tetikledi. Kaynağı değiştirilmeden bırakıldı. Denetimli bir ışık denemesi yalnızca altındaki 25 taş
platform bloğunu camla değiştirdi. Bedrock bundan sonra modeli, dokuyu veya Java paketini değiştirmeden
sepet ve şişe renklerini olağan gösterdi. 25 bloğun hepsi sonra taşa geri döndürüldü. Bu, görüntü
çapası çevresindeki ışık örneklemesini/örtmeyi işaret eder; tam ve genel bir düzeltme belirlemez.
Sıfır yarıçaplı ModelEngine pivot bulutları ve Geyser'ın en küçük bulut yarıçapı istenmeyen parçacıklar
için olası bir açıklamadır, doğrulanmış bir düzeltme değil. Oynanış parçacıkları topluca bastırılmadı.

Ekran görüntüleri işletmecinin sağladığı görseller için uyumluluk kanıtıdır. Özgün modellerin, dokuların
veya üretilen paketlerin yeniden dağıtımına izin vermez. BetterModel toxicity188 ve katkıcılarına,
ModelEngine Ticxo'ya, MythicMobs Lumine'e aittir; görseller ilgili yaratıcılarına atfedilir.

#### Eşlenmiş görüntüler

Sol sütun Java, sağ sütun Bedrock'tur. [Makine tarafından okunabilir sonuçlara](images/acceptance/2026-09-28-matrix/results.json)
ve [görüntü karmalarına](images/acceptance/2026-09-28-matrix/sha256.json) bakın. Görüntü adları
yukarıdaki İngilizce tabloyla aynıdır: bear_brown (bekleme, saldırı), crab_hermit (bekleme, saklanma),
angel_gm_archer_one (bekleme, saldırı1), basket_with_health_potions (durağan), pet_griffon_phoenix
(bekleme, etkileşim), blacksmith_hm5_npc (bekleme, el sallama), owl_crate (bekleme, açılma).

#### Sepet ışıklandırma yalıtımı

| Geçici cam platform üzerinde Java | Aynı platform üzerinde Bedrock |
|---|---|
| Java sepet cam ışık denemesi | Bedrock sepet cam ışık denemesi |

Devamı: [29 Eylül bulut çapası regresyonu](CLOUD_ANCHORS_2026-09-29.md) istenmeyen parçacıklar için
otomatik bir eklenti düzeltmesini doğrular. Sonraki [binek yüksekliği regresyonu](DISPLAY_SEATS_2026-09-29.md)
değişmemiş taş üzerindeki sepet ışıklandırmasını düzeltir. Buradaki görüntüler önceki başarısız temel
çizgiyi korur.

Platformu değiştirmek bir ürün düzeltmesi değil, bir tanı adımıdır. Olağan taş platform geri getirildi.
İstenmeyen parçacıklar bu deneme boyunca görünür kaldı.
