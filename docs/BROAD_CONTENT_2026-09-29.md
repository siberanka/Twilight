# Extended real-content checks — 29–30 September 2026

> Türkçe: [aşağıda](#türkçe)

> The screenshots of this report were retired on 4 October 2026 in favour of current captures; the measurements below remain. Current Java/Bedrock captures are in the [README](../README.md#visual-acceptance-tests).

**Automatic conversion is not lossless.** The extended run found and corrected
inventory preview rendering and one source-discovery blocker, but custom menu layouts, animated textures and
several source dependencies still prevent full Java/Bedrock parity.

## Inventory preview correction

The compiler previously used the first texture as the icon for a volumetric
item. This exposed texture sheets in the hotbar and inventory. It now renders
the model's faces into a static 64×64 icon using the inherited `display.gui`
rotation, translation and scale. Face UVs, quarter turns, element pivots/rescale,
depth, transparent holes, translucent surfaces and `gui_light` are handled
automatically. No per-item screenshots or replacement artwork are required.
Java source models and textures are unchanged.

The new live inventory contains 36 distinct models: tools, weapons, shield,
bows/crossbows, staff, hats, wings, backpack, furniture, a mob part and GUI icons.
Both clients show the same recognizable silhouette and GUI orientation for the
35 valid examples. Slot 29 (row 4, column 2) references a missing source texture:
Java shows its missing-texture marker and the diagnostic Bedrock pack leaves
the item unmapped as paper. It is not counted as a successful conversion.

This is a static model preview, not animation playback. GUI scale, rasterization
and lighting differ between the clients; pixel equality is not asserted. Runtime
tints, animated textures and special renderers remain outside this acceptance.
Nine new tests exercise the published item-atlas reference, silhouette, transform
order, inheritance, exposed face selection, UV rotation/mirroring, depth,
transparent holes, alpha blending and element rescale. The full local build
passed **77 tests across 18 suites**.

| Java: 36 source examples | Bedrock: automatic model previews |
|---|---|
| Java inventory | Bedrock inventory |

The [slot list and measurements](images/acceptance/2026-09-30-inventory/measurements.json)
identify every sample. The final fixture includes 70 BetterModel definitions and
36 item associations: **105/106 converted**, including 102 volumetric definitions.
The missing source texture and six unsupported GUI glyphs are reported; this is
a diagnostic pack, not a successful strict export.

## Whole-source compilation

The local compiler discovered the operator's BoxPVP-v2 and Survival-v5 sources
read-only. Each diagnostic build was repeated, then tried in strict mode. This
tests discovered item associations, fonts and sounds; it does not exercise every
unreferenced model or every production plugin's gameplay behavior.

| Source | Discovered sources | Item candidates | Converted | Volumetric | Glyphs exported | Sound definitions / files | Reported problem entries |
|---|---:|---:|---:|---:|---:|---:|---:|
| BoxPVP-v2 | 30 | 781 | 772 | 309 | 573 | 16 / 35 | 13 |
| Survival-v5 | 138 | 130 | 125 | 102 | 598 | 84 / 131 | 242 |

Both repeated builds produced identical pack hashes. Both strict builds rejected
the remaining problems and preserved the previously generated diagnostic pack.
Problem entries include aggregated font diagnostics, so these numbers are not
counts of individual failed glyphs. Source model/texture bytes were not repaired
or edited to make conversion pass. No production server was started or modified.

Survival initially stopped before conversion because `1.21.5` was rejected as an
overlay directory. Java 26.2's local `OverlayMetadataSection` directory validator
accepts dots and uppercase letters. Twilight now accepts these names while
rejecting `.` / `..` and path separators. Regression tests cover folders, ZIPs,
format boundaries and unsafe names. The final whole-source runs used the new
inventory renderer as well as this overlay correction.

Remaining diagnostics include missing face textures, special player-head/shield
renderers, oversized GUI glyphs, custom font spacing, named-font conflicts and
missing sound dependencies. The offline run disabled downloads and supplied the
verified client JAR; it did not have a verified external sound asset index.

## Client tests

The menu inventory contains 25 DeluxeMenus definitions and seven AuraSkills
definitions. Eight and six respectively contain image/offset placeholders.
These counts describe available inputs, not 32 completed plugin integration tests.

The fresh MythicMobs salamander's complete model is visible in both clients.
Its stationary, invulnerable carrier and ModelEngine binding were verified.
BetterModel's griffon was also sampled in walk and skill animations. These are
sequential samples, not matching animation frames.

A new deterministic selection uses seed `20260929`: chlorophyte axe, lead pickaxe,
ruby sword, Mythic Weapons axe, Soul Eater pickaxe and Valentine sword. Their
original geometry, display transforms and texture bytes are preserved. Twelve
BoxPVP emoji plus the 16-cell Survival emoji sheet use their authored code points.

The initial isolated diagnostic fixture contained 70 BetterModel definitions and six new
item associations: 76/76 definitions and 28 glyphs compiled. Six GUI glyphs are
explicitly rejected, including the Survival skills book, abilities, progress,
stats, Lands and BoxPVP menu images. Diagnostic mode permits observation of the
remaining content; this is not a successful strict conversion.

All six held items are visible in both clients. The three volumetric items have
clearly different first-person orientation and framing in Bedrock. Their raw-texture
hotbar icons in the baseline are corrected by the inventory renderer above.
The first-person orientation defect remains open. Flat-item scale/grip is not
certified. All 28 emoji appear in Bedrock chat, but their size relative to letters
and baseline placement differ. Visibility is a narrower result than visual parity.

The skills images are font-rendering probes in a 54-slot inventory. They do not
recreate AuraSkills leveling, abilities, requirements or placeholder processing.
All four Java skills backgrounds appeared in their probes; Bedrock showed a
plain chest without those backgrounds. Original DeluxeMenus main/warp definitions were tested separately; missing
ItemsAdder items or backend commands are not counted as Twilight conversion
successes. The main/warp menus open in both clients, with unresolved image/offset
dependencies also visible in Java. A dedicated navigation control verified actual
inventory click delivery: both clients produced a Bukkit slot-0 `LEFT` event and
opened `Basics Menu`. This exercised no economy or combat actions.

Four selected texture animation metadata files are present. The current texture
export samples an authored first frame; successful geometry compilation does not
establish animated-texture playback. Complete menu layout and gameplay parity
remain unaccepted.

Private source assets, generated resource packs, production configurations,
helper agents and raw logs remain outside version control. Only reviewed client
captures and sanitized measurements may accompany this report.

The [38 reviewed captures](images/acceptance/2026-09-30-inventory/results.json)
include the earlier held-item/chat/menu baseline, successful navigation, the new
inventory comparison, the full MythicMobs salamander retake and two additional
BetterModel griffon poses. Each pair records its actual build hash; older model
captures are not mislabeled as tests of the inventory build. [Image checksums](images/acceptance/2026-09-30-inventory/sha256.json)
are provided. All 134 distinct copied source files still match their original
hashes. Both current test players have 20 health, and all ten saved player death
counters remain zero. Connections and menu clicks use private scripts documented
under `ai/`; they do not require repeated manual reading of client dialogs.

| Skills background probe: Java | Skills background probe: Bedrock (unsupported) |
|---|---|
| Java abilities background | Bedrock missing background |

| Stationary MythicMobs model: Java | Stationary MythicMobs model: Bedrock |
|---|---|
| Java salamander | Bedrock salamander |

---

## Türkçe

### Genişletilmiş gerçek içerik denetimleri — 29–30 Eylül 2026

> Bu raporun ekran görüntüleri 4 Ekim 2026'da güncel görüntüler lehine kaldırıldı; aşağıdaki ölçümler
> geçerlidir. Güncel Java/Bedrock görüntüleri [README](../README.md#visual-acceptance-tests) içindedir.

**Otomatik dönüşüm kayıpsız değildir.** Genişletilmiş çalışma envanter önizleme çizimini ve bir kaynak
keşfi engelini bulup düzeltti; ancak özel menü yerleşimleri, animasyonlu dokular ve birkaç kaynak
bağımlılığı tam Java/Bedrock eşdeğerliğini hâlâ engelliyor.

#### Envanter önizleme düzeltmesi

Derleyici önceden hacimli bir eşyanın simgesi olarak ilk dokuyu kullanıyordu. Bu, kısayol çubuğunda ve
envanterde doku sayfalarını açığa çıkarıyordu. Artık modelin yüzlerini devralınan `display.gui` dönüşü,
ötelemesi ve ölçeğiyle durağan 64×64 bir simgeye çiziyor. Yüz UV'leri, çeyrek dönüşler, öğe
pivotları/yeniden ölçekleme, derinlik, saydam delikler, yarı saydam yüzeyler ve `gui_light`
kendiliğinden işlenir. Eşya başına ekran görüntüsü veya yedek görsel gerekmez. Java kaynak modelleri ve
dokuları değişmez.

Yeni canlı envanter 36 ayrı model içerir: aletler, silahlar, kalkan, yaylar/arbaletler, asa, şapkalar,
kanatlar, sırt çantası, mobilya, bir mob parçası ve arayüz simgeleri. İki istemci de geçerli 35 örnek
için aynı tanınabilir silüeti ve arayüz yönünü gösteriyor. Yuva 29 (satır 4, sütun 2) eksik bir kaynak
dokusuna başvuruyor: Java eksik doku işaretini gösteriyor, tanı amaçlı Bedrock paketi eşyayı kağıt olarak
eşlenmemiş bırakıyor. Başarılı dönüşüm sayılmaz.

Bu durağan bir model önizlemesidir, animasyon oynatımı değil. Arayüz ölçeği, rasterleştirme ve ışık
istemciler arasında farklıdır; piksel eşitliği iddia edilmez. Çalışma zamanı renklendirmeleri,
animasyonlu dokular ve özel çiziciler bu kabulün dışında kalır. Dokuz yeni test yayımlanmış eşya atlası
referansını, silüeti, dönüşüm sırasını, devralmayı, görünen yüz seçimini, UV döndürme/aynalamayı,
derinliği, saydam delikleri, alfa karışımını ve öğe yeniden ölçeklemesini sınar. Tam yerel derleme
**18 paketteki 77 testi** geçti.

| Java: 36 kaynak örneği | Bedrock: otomatik model önizlemeleri |
|---|---|
| Java envanteri | Bedrock envanteri |

[Yuva listesi ve ölçümler](images/acceptance/2026-09-30-inventory/measurements.json) her örneği
tanımlar. Son düzenek 70 BetterModel tanımı ve 36 eşya eşlemesi içerir: 102 hacimli tanım dahil
**105/106 dönüştürüldü**. Eksik kaynak dokusu ve desteklenmeyen altı arayüz glifi raporlanır; bu başarılı
bir katı dışa aktarım değil, tanı paketidir.

#### Tüm kaynağın derlenmesi

Yerel derleyici işletmecinin BoxPVP-v2 ve Survival-v5 kaynaklarını salt okunur keşfetti. Her tanı
derlemesi tekrarlandı, ardından katı modda denendi. Bu; keşfedilen eşya eşlemelerini, fontları ve
sesleri sınar; başvurulmayan her modeli veya her üretim eklentisinin oynanış davranışını sınamaz.

| Kaynak | Keşfedilen kaynaklar | Eşya adayları | Dönüştürülen | Hacimli | Dışa aktarılan glif | Ses tanımı / dosya | Raporlanan sorun girdisi |
|---|---:|---:|---:|---:|---:|---:|---:|
| BoxPVP-v2 | 30 | 781 | 772 | 309 | 573 | 16 / 35 | 13 |
| Survival-v5 | 138 | 130 | 125 | 102 | 598 | 84 / 131 | 242 |

İki tekrarlanan derleme de özdeş paket karmaları üretti. İki katı derleme de kalan sorunları reddetti ve
önceden üretilen tanı paketini korudu. Sorun girdileri toplanmış font tanılarını içerir; bu yüzden bu
sayılar tek tek başarısız gliflerin sayısı değildir. Dönüşümü geçirmek için kaynak model/doku baytları
onarılmadı veya düzenlenmedi. Hiçbir üretim sunucusu başlatılmadı veya değiştirilmedi.

Survival başlangıçta dönüşümden önce durdu, çünkü `1.21.5` bir kaplama (overlay) dizini olarak
reddediliyordu. Java 26.2'nin yerel `OverlayMetadataSection` dizin doğrulayıcısı noktaları ve büyük
harfleri kabul eder. Twilight artık `.` / `..` ve yol ayırıcılarını reddederken bu adları kabul ediyor.
Regresyon testleri klasörleri, ZIP'leri, biçim sınırlarını ve güvensiz adları kapsar. Son tüm kaynak
çalıştırmaları hem yeni envanter çizicisini hem bu kaplama düzeltmesini kullandı.

Kalan tanılar arasında eksik yüz dokuları, özel oyuncu kafası/kalkan çizicileri, aşırı büyük arayüz
glifleri, özel font aralığı, adlandırılmış font çakışmaları ve eksik ses bağımlılıkları var. Çevrim dışı
çalıştırma indirmeleri kapattı ve doğrulanmış istemci JAR'ını sağladı; doğrulanmış harici bir ses
varlığı dizini yoktu.

#### İstemci testleri

Menü envanteri 25 DeluxeMenus ve yedi AuraSkills tanımı içerir. Bunların sırasıyla sekizi ve altısı
görsel/kaydırma yer tutucuları içerir. Bu sayılar tamamlanmış 32 eklenti entegrasyon testini değil,
mevcut girdileri anlatır.

Yeni MythicMobs semenderinin tam modeli iki istemcide de görünür. Sabit, hasar almayan taşıyıcısı ve
ModelEngine bağlantısı doğrulandı. BetterModel'in griffonu da yürüme ve beceri animasyonlarında
örneklendi. Bunlar eşleşen animasyon kareleri değil, ardışık örneklerdir.

Yeni belirlenimci bir seçim `20260929` tohumunu kullanır: klorofit balta, kurşun kazma, yakut kılıç,
Mythic Weapons baltası, Soul Eater kazması ve Sevgililer Günü kılıcı. Özgün geometrileri, görüntü
dönüşümleri ve doku baytları korunur. On iki BoxPVP emojisi ve 16 hücreli Survival emoji sayfası
yazarın verdiği kod noktalarını kullanır.

İlk yalıtılmış tanı düzeneği 70 BetterModel tanımı ve altı yeni eşya eşlemesi içeriyordu: 76/76 tanım
ve 28 glif derlendi. Survival beceri kitabı, yetenekler, ilerleme, istatistikler, Lands ve BoxPVP menü
görselleri dahil altı arayüz glifi açıkça reddedilir. Tanı modu kalan içeriğin gözlemlenmesine izin
verir; bu başarılı bir katı dönüşüm değildir.

Elde tutulan altı eşyanın hepsi iki istemcide de görünür. Üç hacimli eşyanın Bedrock'ta birinci şahıs
yönü ve çerçevelemesi açıkça farklı. Temel çizgideki ham dokulu kısayol çubuğu simgeleri yukarıdaki
envanter çizicisiyle düzeltildi. Birinci şahıs yön hatası açık kalıyor. Düz eşya ölçeği/tutuşu
onaylanmadı. 28 emojinin hepsi Bedrock sohbetinde görünüyor, ancak harflere göre boyutları ve taban
çizgisi yerleşimleri farklı. Görünürlük, görsel eşdeğerlikten daha dar bir sonuçtur.

Beceri görselleri 54 yuvalı bir envanterde font çizim denemeleridir. AuraSkills seviye atlamayı,
yetenekleri, gereksinimleri veya yer tutucu işlemeyi yeniden üretmezler. Dört Java beceri arka planının
hepsi denemelerinde göründü; Bedrock bu arka planlar olmadan düz bir sandık gösterdi. Özgün DeluxeMenus
ana/ışınlanma tanımları ayrıca test edildi; eksik ItemsAdder eşyaları veya arka uç komutları Twilight
dönüşüm başarısı sayılmaz. Ana/ışınlanma menüleri iki istemcide de açılıyor; çözülmemiş görsel/kaydırma
bağımlılıkları Java'da da görünür. Özel bir gezinme denetimi gerçek envanter tıklamasının iletildiğini
doğruladı: iki istemci de Bukkit yuva 0 `LEFT` olayı üretti ve `Basics Menu`yü açtı. Ekonomi veya savaş
eylemi sınanmadı.

Seçilen dört doku animasyonu meta veri dosyası mevcut. Geçerli doku dışa aktarımı yazarın verdiği ilk
kareyi örnekler; başarılı geometri derlemesi animasyonlu doku oynatımını kanıtlamaz. Tam menü yerleşimi
ve oynanış eşdeğerliği kabul edilmedi.

Özel kaynak varlıkları, üretilen kaynak paketleri, üretim yapılandırmaları, yardımcı araçlar ve ham
günlükler sürüm denetimi dışında kalır. Bu rapora yalnızca incelenmiş istemci görüntüleri ve arındırılmış
ölçümler eşlik edebilir.

[İncelenen 38 görüntü](images/acceptance/2026-09-30-inventory/results.json) önceki elde tutma/sohbet/menü
temel çizgisini, başarılı gezinmeyi, yeni envanter karşılaştırmasını, tam MythicMobs semenderi yeniden
çekimini ve iki ek BetterModel griffon pozunu içerir. Her çift gerçek derleme karmasını kaydeder; eski
model görüntüleri envanter derlemesinin testleri olarak yanlış etiketlenmez.
[Görüntü sağlama toplamları](images/acceptance/2026-09-30-inventory/sha256.json) sağlanır. Kopyalanan 134
ayrı kaynak dosyanın hepsi hâlâ özgün karmalarıyla eşleşiyor. İki geçerli test oyuncusunun da canı 20 ve
kaydedilmiş on oyuncu ölüm sayacının hepsi sıfır. Bağlantılar ve menü tıklamaları `ai/` altında
belgelenen özel betikleri kullanır; istemci iletişim kutularının tekrar tekrar elle okunmasını gerektirmez.

| Beceri arka planı denemesi: Java | Beceri arka planı denemesi: Bedrock (desteklenmiyor) |
|---|---|
| Java yetenekler arka planı | Bedrock eksik arka plan |

| Durağan MythicMobs modeli: Java | Durağan MythicMobs modeli: Bedrock |
|---|---|
| Java semender | Bedrock semender |
