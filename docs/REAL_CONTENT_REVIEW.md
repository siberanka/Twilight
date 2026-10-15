# Real-content visual review — 2026-09-27

> Türkçe: [aşağıda](#türkçe)

> The screenshots of this report were retired on 4 October 2026 in favour of current captures; the measurements below remain. Current Java/Bedrock captures are in the [README](../README.md#visual-acceptance-tests).

Twilight development build 3 was tested with real tools, weapons, emoji, and menu images
from the operator's BoxPVP-v2 and Survival-v5 installations. Survival-v3 was not
present. The operator authorized publication of the reviewed screenshots below.
Source packs, plugin binaries, configuration, worlds, and raw logs remain private.

**Result: partial improvements, not full Java/Bedrock parity.** Third-person
volumetric geometry improved, but first-person framing, observer presentation,
and chat/UI acceptance remain open. Large Java GUI glyphs need a Bedrock layout
adapter and are now explicitly rejected in strict conversion.

## Method and environment

- Paper 26.2 build 121; Java client 26.2; Java 25.0.2; Geyser 2.11.3 build 1247;
  installed Bedrock client 26.52.
- Geyser's published support range ended at 26.51 during this run. This limits
  acceptance confidence; it does not prove the cause of every observed failure.
- Original Java model, display, texture, and animation metadata bytes were copied
  unchanged and checked against private source SHA-256 records. Only private test
  item associations and required atlas registrations were added.
- Repeatable random selection used seed `20260927`, with three flat BoxPVP items
  and three volumetric Survival items. Both clients selected the six hotbar slots.
- The server ran locally, with vanilla overrides disabled. Test players stayed in
  creative mode; model probes used stationary, invulnerable, non-attacking pigs.
- Changed item mappings were activated by a full server restart. The Turkish JVM
  locale caused Geyser to reject `definition`; isolated en/US JVM flags resolved it.
- All screenshots are actual client captures. Different skins, view sizes, and
  observer states prevent pixel-for-pixel comparison. In Bedrock third-person
  images, the red character on the right is the local player under test.

See [Geyser mapping installation](https://geysermc.org/wiki/geyser/custom-items/)
and [supported versions](https://geysermc.org/wiki/geyser/supported-versions/).

## Six item probes

The diagnostic pack converted 6/6 items, including three volumetric models, and
20 emoji into three font pages. It explicitly omitted two unsupported GUI glyphs.
Strict conversion rejected those GUI glyphs instead of publishing a degraded menu.

| Original model | Java reference | Bedrock final capture | Observation |
|---|---|---|---|
| `terraria:auto_generated/tin_axe` | Java | Bedrock | Visible; edge-on holding direction is broadly similar, grip/scale parity unaccepted. |
| `itemsadder:auto_generated/spinel_pickaxe` | Java | Bedrock | Visible; flat item pose and exact grip remain unaccepted. |
| `terraria:auto_generated/adamantite_sword` | Java | Bedrock | Visible; exact pose/scale parity remains unaccepted. |
| `ender_dragonset:axe` | Java | Bedrock | Local third-person silhouette and downward orientation broadly agree. Full multi-view acceptance remains open. |
| `mythic_weapons:pickaxe` | Java | Bedrock | Broken/repeated texture segments improved; local third-person orientation broadly agrees. First-person differs. |
| `gearforge_nature:sword` | Java | Bedrock | Local third-person silhouette broadly agrees; animation parity is not implemented. |

### Pickaxe texture correction

| Before final cuboid/UV/frame fixes | Final converted pack |
|---|---|
| Before: repeated/broken texture segments | After: continuous pickaxe texture |

This fixes rotated element X/Y signs, face UV rotation, omitted Java UV defaults,
and static sampling of the first authored animation frame. Animated textures do
not yet play their complete Java animation. Remote equipment differed from the
local third-person pose, so observer presentation is still an open check.

### Remaining first-person mismatch

| Unchanged Java reference | Bedrock final pack — failed parity |
|---|---|
| Java first-person pickaxe | Bedrock pickaxe has a different first-person angle and framing |

## Emoji and GUI probes

Four BoxPVP emoji and the Survival 4×4 emoji sheet were exported with original
code points. The Java chat probe uses `Agjp` on both sides to expose ascenders,
descenders, and baseline placement. Bedrock's chat and inventory UI did not
appear in this session, including after a fresh client launch and pack download.
Therefore inline emoji alignment is **blocked**, not passed.

| Java chat reference | Bedrock chat attempt — blocked |
|---|---|
| Java inline emoji reference | Bedrock chat UI absent during the probe |

The menu probes put each original glyph in a plain 54-slot inventory title.
These are font-rendering probes, not complete production plugin menus: production
spacing, slot layout, and plugin behavior were not recreated. The large Java
images overlap the plain test inventory and must not be presented as a correct
production menu layout.

| BoxPVP blank-menu glyph | Survival lands-menu glyph |
|---|---|
| Java blank-menu title probe | Java lands-menu title probe |

Their declared display sizes are 192×170 and 236×245, which cannot fit a Bedrock
16-pixel Unicode cell while retaining Java layout. Strict conversion now reports
`oversized bitmap glyphs require a Bedrock UI adapter`. Custom spacing also
requires a layout adapter. A Bedrock inventory attempt
showed no inventory UI; full GUI acceptance is unsupported/blocked.

## Live display bridge follow-up

A later development build adds a shared Bedrock entity and a Geyser item-display
translator. The original 34 BetterModel bone items now form an assembled model.
The test scene contains three stationary model instances (direct BetterModel and
MythicMobs integration), with 105 display entities in the Bedrock session.

| Java attack pose | Bedrock attack pose |
|---|---|
| Java original model attack | Bedrock converted model attack |

These are separate captures of the same looping animation, not synchronized
frames. Model assembly and attack movement are observed; exact timing, lighting,
all animation phases, and all provider models are not certified. The bridge uses
unchanged Java provider poses and independently generated Bedrock geometry.
See [bridge implementation and limits](DISPLAY_BRIDGE.md).

ModelEngine R4.1.1 subsequently enabled successfully on a separate local Paper
26.2 instance. Its generated salamander pack exposed an overlay-selection bug:
inactive older-version assets were being flattened into the current pack.
After selecting overlays by Minecraft's verified pack format and resolving
explicit vanilla texture references from the verified client cache, strict
conversion passed 70/70 offline definitions and 72/72 live-collected candidates
with no reported problems. The R4.1.0 result below remains historical.

The subsequent live ModelEngine probe exposed two independent display metadata
errors: base entity invisibility incorrectly hid visible Java display meshes,
and ignored zero view range exposed inactive fire layers. The bridge now follows
Java's visibility behavior for these cases. Two direct API models and one newly
spawned MythicMobs model appeared assembled in both clients after a full restart.
MythicMobs definitions were reloaded before the successful new spawn; earlier
control pigs without an attached model are not counted as passing model probes.

| Java, frozen ModelEngine attack | Bedrock, same frozen attack and camera position |
|---|---|
| Java ModelEngine attack | Bedrock ModelEngine attack |

The provider animation clock was frozen at approximately 0.20 seconds for this
pose comparison. The low pose intersects the platform in both clients. Different
client field of view and lighting prevent a pixel-equality claim. This verifies
the sampled pose, not complete animation timing, fire animation, or tint parity.

| Java, MythicMobs with ModelEngine | Bedrock, same stationary MythicMobs carrier |
|---|---|
| Java MythicMobs ModelEngine | Bedrock MythicMobs ModelEngine |

The front-facing centered model is the new MythicMobs spawn. Idle captures were
not synchronized to the same animation frame. All four saved test-player death
counters remained zero. These checks used invulnerable stationary carriers and
direct animation playback; production combat skills were not imported.

The earlier missing Bedrock HUD was no longer present after a later fresh join.
Earlier chat/UI failures therefore need a fresh content-specific retest; they
must not be attributed conclusively to the client/protocol version mismatch.

## Initial BetterModel, MythicMobs, and ModelEngine probes (before bridge)

| Probe | Java/server observation | Bedrock observation | Result |
|---|---|---|---|
| BetterModel 3.4.2-SNAPSHOT-516, original `meleesalamander` | Plugin enabled; direct spawn on a stationary pig displayed the model. | Assembled model absent after a full registry restart and fresh client join. | Failed entity parity. |
| MythicMobs 5.13.1-SNAPSHOT-88530541, stationary control pig | Spawn command succeeded; pig had no AI, zero damage, and invulnerability. | Control pigs visible. | Basic spawn/visibility observed; no combat/skills acceptance claim. |
| MythicMobs with BetterModel `model{mid=meleesalamander}` | Spawn command succeeded and the custom model appeared. | Custom salamander absent while control pigs remained visible. | Failed custom entity parity. |
| ModelEngine R4.1.0, local licensed JAR | Enable failed with `Unsupported NMS Version: 26.2`. | No functioning provider available for a spawn comparison. | Blocked by provider/server version. |

The BetterModel-generated resources were converted separately: **34/34 volumetric
bone items**, strict mode, zero reported problems. Geyser registered 35 custom
items, including its built-in item. The pack was downloaded by the Bedrock client.
This confirms item-resource conversion, not entity rig conversion. This initial result preceded the runtime bridge described above.

| Java stationary model/control scene | Bedrock after fresh join — custom models absent |
|---|---|
| Java BetterModel and MythicMobs stationary scene | Bedrock control pigs visible but custom salamanders absent |

The camera positions differ slightly, but both face the stationary test area.
The two control pigs provide a visible scene reference. Both test players had
20/20 health and zero recorded deaths. Test mobs had AI disabled; no combat skills
were loaded from production mob configurations. Stationary means the carrier did
not move; provider idle animations were not claimed to be frozen or accepted.

Twilight now skips hook registration for disabled providers, preventing secondary
closed-classloader warnings after ModelEngine's enable failure. BetterModel and
MythicMobs startup/spawn were tested using local plugin copies; no third-party
binaries or private models are included in this repository.

Primary provider references: [BetterModel commands](https://github.com/toxicity188/BetterModel/blob/v3/core/bukkit-core/src/main/kotlin/kr/toxicity/model/bukkit/command/Commands.kt),
[ModelEngine commands](https://wiki.mythiccraft.io/modelengine/Commands-and-Permissions),
and [MythicMobs configuration](https://wiki.mythiccraft.io/mythicmobs/config/config-mobs).
BetterModel is by toxicity188 and contributors; ModelEngine by Ticxo; MythicMobs
by Lumine. These are compatibility probes, not bundled dependencies.

## Build and evidence integrity

The latest local development build passed 67 tests across 17 suites, with zero failures or errors.
Regression tests include independent pose bases and rotated corners, left-hand
mirroring, Euler singularities, UV rotation/defaults, animation-frame selection,
tall static textures, font rejection, and persistent Geyser restart requirements.

The [development artifact](../artifacts/) includes the live bridge and visibility
fixes and automatic [cloud-anchor adaptation](CLOUD_ANCHORS_2026-09-29.md); it is not an accepted release. Screenshot hashes are in the
[initial manifest](images/acceptance/2026-09-27/sha256.json) and
[ModelEngine manifest](images/acceptance/2026-09-28/sha256.json). The private final
item test pack SHA-256 is
`99c00c4150e6a8ae02189e06e6bbec1df9918d7c8957dd499e55bb8b4a20373c`.

These screenshots demonstrate compatibility testing of operator-provided assets;
they do not grant a license to redistribute the original packs. Artwork and
Minecraft presentation remain attributable to their respective creators. Provider
names describe the tested integrations and imply no affiliation.

---

## Türkçe

### Gerçek içerik görsel incelemesi — 27 Eylül 2026

> Bu raporun ekran görüntüleri 4 Ekim 2026'da güncel görüntüler lehine kaldırıldı; aşağıdaki ölçümler
> geçerlidir. Güncel Java/Bedrock görüntüleri [README](../README.md#visual-acceptance-tests) içindedir.

Twilight geliştirme derlemesi 3, işletmecinin BoxPVP-v2 ve Survival-v5 kurulumlarındaki gerçek aletler, silahlar,
emojiler ve menü görselleriyle test edildi. Survival-v3 mevcut değildi. İşletmeci aşağıda incelenen ekran
görüntülerinin yayımlanmasına izin verdi. Kaynak paketleri, eklenti dosyaları, yapılandırma, dünyalar ve
ham günlükler özel kalır.

**Sonuç: kısmi iyileştirmeler, tam Java/Bedrock eşdeğerliği değil.** Üçüncü şahıs hacimli geometri
iyileşti, ancak birinci şahıs çerçeveleme, izleyici sunumu ve sohbet/arayüz kabulü açık kalıyor. Büyük
Java arayüz glifleri bir Bedrock yerleşim bağdaştırıcısı gerektiriyor ve artık katı dönüşümde açıkça
reddediliyor.

#### Yöntem ve ortam

- Paper 26.2 derleme 121; Java istemcisi 26.2; Java 25.0.2; Geyser 2.11.3 derleme 1247; kurulu Bedrock
  istemcisi 26.52.
- Bu çalıştırma sırasında Geyser'ın yayımlanmış destek aralığı 26.51'de bitiyordu. Bu kabul güvenini
  sınırlar; gözlenen her başarısızlığın nedenini kanıtlamaz.
- Özgün Java model, görüntü, doku ve animasyon meta veri baytları değiştirilmeden kopyalandı ve özel
  kaynak SHA-256 kayıtlarına karşı denetlendi. Yalnızca özel test eşya eşlemeleri ve gerekli atlas
  kayıtları eklendi.
- Tekrarlanabilir rastgele seçim `20260927` tohumunu kullandı: üç düz BoxPVP eşyası ve üç hacimli
  Survival eşyası. İki istemci de altı kısayol çubuğu yuvasını seçti.
- Sunucu yerelde, vanilla geçersiz kılmaları kapalı çalıştı. Test oyuncuları yaratıcı modda kaldı; model
  denemeleri sabit, hasar almayan, saldırmayan domuzlar kullandı.
- Değişen eşya eşlemeleri sunucunun tamamen yeniden başlatılmasıyla etkinleştirildi. Türkçe JVM yerel
  ayarı Geyser'ın `definition` değerini reddetmesine yol açtı; yalıtılmış en/US JVM bayrakları bunu çözdü.
- Tüm ekran görüntüleri gerçek istemci çekimleridir. Farklı kaplamalar, görüş boyutları ve izleyici
  durumları piksel piksel karşılaştırmayı engeller. Bedrock üçüncü şahıs görüntülerinde sağdaki kırmızı
  karakter test edilen yerel oyuncudur.

[Geyser eşleme kurulumu](https://geysermc.org/wiki/geyser/custom-items/) ve
[desteklenen sürümler](https://geysermc.org/wiki/geyser/supported-versions/) belgelerine bakın.

#### Altı eşya denemesi

Tanı paketi üç hacimli model dahil 6/6 eşyayı ve 20 emojiyi üç font sayfasına dönüştürdü. Desteklenmeyen
iki arayüz glifini açıkça dışarıda bıraktı. Katı dönüşüm bozulmuş bir menü yayımlamak yerine bu arayüz
gliflerini reddetti.

| Özgün model | Java referansı | Bedrock son görüntü | Gözlem |
|---|---|---|---|
| `terraria:auto_generated/tin_axe` | Java | Bedrock | Görünür; yandan tutuş yönü genel olarak benzer, tutuş/ölçek eşdeğerliği kabul edilmedi. |
| `itemsadder:auto_generated/spinel_pickaxe` | Java | Bedrock | Görünür; düz eşya pozu ve birebir tutuş kabul edilmedi. |
| `terraria:auto_generated/adamantite_sword` | Java | Bedrock | Görünür; birebir poz/ölçek eşdeğerliği kabul edilmedi. |
| `ender_dragonset:axe` | Java | Bedrock | Yerel üçüncü şahıs silüeti ve aşağı yönü genel olarak uyuşuyor. Tam çok görüşlü kabul açık. |
| `mythic_weapons:pickaxe` | Java | Bedrock | Kırık/tekrarlanan doku parçaları düzeldi; yerel üçüncü şahıs yönü genel olarak uyuşuyor. Birinci şahıs farklı. |
| `gearforge_nature:sword` | Java | Bedrock | Yerel üçüncü şahıs silüeti genel olarak uyuşuyor; animasyon eşdeğerliği uygulanmadı. |

##### Kazma dokusu düzeltmesi

| Son küboid/UV/kare düzeltmelerinden önce | Son dönüştürülmüş paket |
|---|---|
| Önce: tekrarlanan/kırık doku parçaları | Sonra: kesintisiz kazma dokusu |

Bu; döndürülmüş öğe X/Y işaretlerini, yüz UV dönüşünü, atlanan Java UV varsayılanlarını ve yazarın verdiği
ilk animasyon karesinin durağan örneklenmesini düzeltir. Animasyonlu dokular henüz tam Java animasyonlarını
oynatmıyor. Uzak ekipman yerel üçüncü şahıs pozundan farklıydı; bu yüzden izleyici sunumu hâlâ açık bir
denetim.

##### Kalan birinci şahıs uyumsuzluğu

| Değişmemiş Java referansı | Bedrock son paket — eşdeğerlik başarısız |
|---|---|
| Java birinci şahıs kazma | Bedrock kazmanın birinci şahıs açısı ve çerçevelemesi farklı |

#### Emoji ve arayüz denemeleri

Dört BoxPVP emojisi ve Survival 4×4 emoji sayfası özgün kod noktalarıyla dışa aktarıldı. Java sohbet
denemesi yükselen/alçalan kısımları ve taban çizgisi yerleşimini göstermek için iki tarafta da `Agjp`
kullanır. Bedrock'un sohbet ve envanter arayüzü bu oturumda, yeni bir istemci açılışı ve paket indirmesinden
sonra bile görünmedi. Bu yüzden satır içi emoji hizalaması geçmedi, **engellendi**.

| Java sohbet referansı | Bedrock sohbet denemesi — engellendi |
|---|---|
| Java satır içi emoji referansı | Deneme sırasında Bedrock sohbet arayüzü yok |

Menü denemeleri her özgün glifi düz 54 yuvalı bir envanter başlığına koyar. Bunlar tam üretim eklenti
menüleri değil, font çizim denemeleridir: üretim aralığı, yuva yerleşimi ve eklenti davranışı yeniden
üretilmedi. Büyük Java görselleri düz test envanteriyle çakışır ve doğru bir üretim menü yerleşimi olarak
sunulmamalıdır.

| BoxPVP boş menü glifi | Survival lands menü glifi |
|---|---|
| Java boş menü başlık denemesi | Java lands menü başlık denemesi |

Bildirilen görüntü boyutları 192×170 ve 236×245'tir; Java yerleşimini korurken Bedrock'un 16 piksellik
Unicode hücresine sığamazlar. Katı dönüşüm artık `oversized bitmap glyphs require a Bedrock UI adapter`
raporluyor. Özel aralık da bir yerleşim bağdaştırıcısı gerektirir. Bir Bedrock envanter denemesi envanter
arayüzü göstermedi; tam arayüz kabulü desteklenmiyor/engellendi. (Sonraki sürümler bu glifleri ve menü
yerleşimini destekler.)

#### Canlı görüntü köprüsü devamı

Sonraki bir geliştirme derlemesi ortak bir Bedrock varlığı ve bir Geyser eşya görüntüsü çevirmeni ekler.
Özgün 34 BetterModel kemik eşyası artık birleşik bir model oluşturuyor. Test sahnesi üç sabit model örneği
(doğrudan BetterModel ve MythicMobs entegrasyonu) ve Bedrock oturumunda 105 görüntü varlığı içerir.

| Java saldırı pozu | Bedrock saldırı pozu |
|---|---|
| Java özgün model saldırısı | Bedrock dönüştürülmüş model saldırısı |

Bunlar eşzamanlı kareler değil, aynı döngüsel animasyonun ayrı çekimleridir. Model birleştirme ve saldırı
hareketi gözlendi; birebir zamanlama, ışıklandırma, tüm animasyon evreleri ve tüm sağlayıcı modelleri
onaylanmadı. Köprü değişmemiş Java sağlayıcı pozlarını ve bağımsız üretilmiş Bedrock geometrisini kullanır.
[Köprü uygulaması ve sınırlarına](DISPLAY_BRIDGE.md) bakın.

ModelEngine R4.1.1 daha sonra ayrı bir yerel Paper 26.2 örneğinde başarıyla etkinleşti. Ürettiği semender
paketi bir kaplama seçimi hatasını ortaya çıkardı: etkin olmayan eski sürüm varlıkları geçerli pakete
düzleştiriliyordu. Kaplamaları Minecraft'ın doğrulanmış paket biçimine göre seçtikten ve açık vanilla doku
referanslarını doğrulanmış istemci önbelleğinden çözdükten sonra katı dönüşüm 70/70 çevrim dışı tanımı ve
72/72 canlı toplanan adayı raporlanmış sorun olmadan geçti. Aşağıdaki R4.1.0 sonucu tarihsel olarak kalır.

Sonraki canlı ModelEngine denemesi iki bağımsız görüntü meta veri hatasını ortaya çıkardı: temel varlık
görünmezliği görünür Java görüntü ağlarını yanlışlıkla gizliyordu ve yok sayılan sıfır görüş menzili
etkin olmayan ateş katmanlarını açığa çıkarıyordu. Köprü artık bu durumlarda Java'nın görünürlük
davranışını izliyor. İki doğrudan API modeli ve yeni doğurulan bir MythicMobs modeli tam yeniden
başlatmadan sonra iki istemcide de birleşik göründü. Başarılı yeni doğuştan önce MythicMobs tanımları
yeniden yüklendi; modeli bağlı olmayan önceki kontrol domuzları geçen model denemesi sayılmaz.

| Java, dondurulmuş ModelEngine saldırısı | Bedrock, aynı dondurulmuş saldırı ve kamera konumu |
|---|---|
| Java ModelEngine saldırısı | Bedrock ModelEngine saldırısı |

Sağlayıcı animasyon saati bu poz karşılaştırması için yaklaşık 0,20 saniyede donduruldu. Alçak poz iki
istemcide de platformla kesişiyor. Farklı istemci görüş alanı ve ışıklandırması piksel eşitliği iddiasını
engeller. Bu, tam animasyon zamanlamasını, ateş animasyonunu veya renk eşdeğerliğini değil, örneklenen pozu
doğrular.

| Java, ModelEngine ile MythicMobs | Bedrock, aynı sabit MythicMobs taşıyıcısı |
|---|---|
| Java MythicMobs ModelEngine | Bedrock MythicMobs ModelEngine |

Öne bakan ortalanmış model yeni MythicMobs doğuşudur. Bekleme görüntüleri aynı animasyon karesine
eşzamanlanmadı. Kaydedilmiş dört test oyuncusu ölüm sayacının hepsi sıfır kaldı. Bu denetimler hasar
almayan sabit taşıyıcılar ve doğrudan animasyon oynatımı kullandı; üretim savaş becerileri içe aktarılmadı.

Önceki eksik Bedrock HUD'u sonraki yeni bir katılımdan sonra artık görülmedi. Önceki sohbet/arayüz
başarısızlıkları bu yüzden içeriğe özgü yeni bir yeniden test gerektirir; kesin olarak istemci/protokol
sürüm uyumsuzluğuna bağlanmamalıdır.

#### İlk BetterModel, MythicMobs ve ModelEngine denemeleri (köprüden önce)

| Deneme | Java/sunucu gözlemi | Bedrock gözlemi | Sonuç |
|---|---|---|---|
| BetterModel 3.4.2-SNAPSHOT-516, özgün `meleesalamander` | Eklenti etkin; sabit bir domuz üzerinde doğrudan doğuş modeli gösterdi. | Tam kayıt yeniden başlatması ve yeni istemci katılımından sonra birleşik model yok. | Varlık eşdeğerliği başarısız. |
| MythicMobs 5.13.1-SNAPSHOT-88530541, sabit kontrol domuzu | Doğuş komutu başarılı; domuzun AI'ı kapalı, hasarı sıfır ve hasar almıyor. | Kontrol domuzları görünür. | Temel doğuş/görünürlük gözlendi; savaş/beceri kabul iddiası yok. |
| BetterModel `model{mid=meleesalamander}` ile MythicMobs | Doğuş komutu başarılı ve özel model göründü. | Kontrol domuzları görünür kalırken özel semender yok. | Özel varlık eşdeğerliği başarısız. |
| ModelEngine R4.1.0, yerel lisanslı JAR | Etkinleştirme `Unsupported NMS Version: 26.2` ile başarısız. | Doğuş karşılaştırması için çalışan sağlayıcı yok. | Sağlayıcı/sunucu sürümüyle engellendi. |

BetterModel'in ürettiği kaynaklar ayrıca dönüştürüldü: katı modda, sıfır raporlanmış sorunla **34/34
hacimli kemik eşyası**. Geyser yerleşik eşyası dahil 35 özel eşya kaydetti. Paket Bedrock istemcisi
tarafından indirildi. Bu, varlık iskeleti dönüşümünü değil, eşya kaynağı dönüşümünü doğrular. Bu ilk sonuç
yukarıda anlatılan çalışma zamanı köprüsünden önce geldi.

| Java sabit model/kontrol sahnesi | Yeni katılımdan sonra Bedrock — özel modeller yok |
|---|---|
| Java BetterModel ve MythicMobs sabit sahnesi | Bedrock kontrol domuzları görünür ama özel semenderler yok |

Kamera konumları biraz farklı, ancak ikisi de sabit test alanına bakıyor. İki kontrol domuzu görünür bir
sahne referansı sağlar. İki test oyuncusunun da canı 20/20 ve kayıtlı ölümü sıfırdı. Test moblarının AI'ı
kapalıydı; üretim mob yapılandırmalarından savaş becerisi yüklenmedi. Sabit, taşıyıcının hareket
etmediği anlamına gelir; sağlayıcı bekleme animasyonlarının dondurulduğu veya kabul edildiği iddia
edilmedi.

Twilight artık devre dışı sağlayıcılar için kanca kaydını atlıyor; bu, ModelEngine'in etkinleştirme
hatasından sonra ikincil kapalı sınıf yükleyici uyarılarını önler. BetterModel ve MythicMobs açılışı/doğuşu
yerel eklenti kopyalarıyla test edildi; bu depoda hiçbir üçüncü taraf dosyası veya özel model yoktur.

Birincil sağlayıcı referansları: [BetterModel komutları](https://github.com/toxicity188/BetterModel/blob/v3/core/bukkit-core/src/main/kotlin/kr/toxicity/model/bukkit/command/Commands.kt),
[ModelEngine komutları](https://wiki.mythiccraft.io/modelengine/Commands-and-Permissions) ve
[MythicMobs yapılandırması](https://wiki.mythiccraft.io/mythicmobs/config/config-mobs). BetterModel
toxicity188 ve katkıcılarına, ModelEngine Ticxo'ya, MythicMobs Lumine'e aittir. Bunlar paketlenmiş
bağımlılıklar değil, uyumluluk denemeleridir.

#### Derleme ve kanıt bütünlüğü

En son yerel geliştirme derlemesi 17 paketteki 67 testi sıfır başarısızlık veya hatayla geçti. Regresyon
testleri bağımsız poz tabanlarını ve döndürülmüş köşeleri, sol el aynalamasını, Euler tekilliklerini, UV
dönüşü/varsayılanlarını, animasyon karesi seçimini, uzun durağan dokuları, font reddini ve kalıcı Geyser
yeniden başlatma gereksinimlerini içerir.

[Geliştirme dosyası](../artifacts/) canlı köprüyü, görünürlük düzeltmelerini ve otomatik
[bulut çapası uyarlamasını](CLOUD_ANCHORS_2026-09-29.md) içerir; kabul edilmiş bir sürüm değildir. Ekran
görüntüsü karmaları [ilk manifestte](images/acceptance/2026-09-27/sha256.json) ve
[ModelEngine manifestinde](images/acceptance/2026-09-28/sha256.json) bulunur. Özel son eşya test paketinin
SHA-256 değeri `99c00c4150e6a8ae02189e06e6bbec1df9918d7c8957dd499e55bb8b4a20373c`.

Bu ekran görüntüleri işletmecinin sağladığı varlıkların uyumluluk testini gösterir; özgün paketleri yeniden
dağıtma lisansı vermez. Görseller ve Minecraft sunumu ilgili yaratıcılarına atfedilir. Sağlayıcı adları
test edilen entegrasyonları anlatır ve herhangi bir bağlılık ima etmez.
