# New samples and composite transforms — 30 September–1 October 2026

> Türkçe: [aşağıda](#türkçe)

> The screenshots of this report were retired on 4 October 2026 in favour of current captures; the measurements below remain. Current Java/Bedrock captures are in the [README](../README.md#visual-acceptance-tests).

New real-source samples exposed a conversion defect: static composite children
lost their individual Java display transforms. BetterModel's `demon_knight`
head contains 27 child models with different `fixed` rotations. Merging their
cubes and applying the first child's pose separated the horns and tilted other
parts. `blue_wizard` also showed an incorrect head angle.

The compiler now retains each child mesh and its authored context transforms.
Display entities and attachables get separate child bones; GUI rendering applies
each child's GUI pose and lighting with a shared depth buffer. Generated sprite
children are retained beside cuboid children. This is automatic conversion, with
no model-name exceptions or edits to the source assets.

## Scope and build checks

- 36 item models and 24 emoji not included in the two earlier item selections.
  Selection used a fixed seed and preferred different namespaces and model kinds.
- Four new provider models: BetterModel `blue_wizard` (211 elements) and
  `demon_knight` (323), ModelEngine `bl_earth_small_spider` (49) and
  `angel_gm_lancer_one` (24). The lancer was also bound through MythicMobs.
- Provider discovery used the live item collector. The BetterModel fixture
  compiled 167/168 mappings, including 36 new item definitions and six previously
  held items. The ModelEngine fixture compiled 278/278 mappings: legacy and
  modern mappings for 139 descriptors, **not 278 distinct mobs**.
- Both repeated builds produced identical packs. Strict mode accepted the
  ModelEngine fixture. It rejected the BetterModel fixture's missing source
  texture and preserved the diagnostic pack byte for byte.
- 80 tests in 18 suites passed locally. New regressions exercise independent
  composite GUI transforms and shared depth, mixed sprite/cuboid children,
  separate held-item poses, fixed rotations and left-hand fallbacks.
- 163 source-manifest entries covering 135 distinct copied files and four
  original model files were hash-verified unchanged. Third-party source packs,
  models, server configurations and raw logs are excluded from the repository.

The development [artifact](../artifacts/README.md) has SHA-256
`152fd07575e4a2c26b910f5ba9974ebf08c6e501eeb04e43f9c4c8f1010f24c5`.
Baseline captures use the preceding `8cb2ceff…` build; each published pair records
its full build hash in [results.json](images/acceptance/2026-09-30-composites/results.json).

## Client observations

| Java reference | Bedrock before | Bedrock after |
| --- | --- | --- |
| Java demon knight | Detached head parts before correction | Assembled head after correction |
| Java blue wizard | Incorrect head tilt before correction | Corrected child pose |

| Sample | Checks | Observed result |
| --- | --- | --- |
| blue_wizard | idle, walk | Incorrect head tilt corrected; mesh remains assembled. |
| demon_knight | idle, walk, guard, hammer_attack_1 | Detached head/horn pieces corrected; guard and attack poses reach Bedrock. |
| bl_earth_small_spider | idle, walk, fangs_attack | Geometry visible in both clients; pose samples retained for comparison. |
| angel_gm_lancer_one | idle, walk, attack2 | Geometry and sampled pose changes visible in both clients. |
| MythicMobs lancer | stationary invulnerable carrier and ModelEngine binding | Full model visible in both clients. |
| 36-item inventory | weapons, furniture, cosmetics, model parts, icons | 35 Bedrock previews rendered; 34 have usable Java references. See exceptions below. |
| Six held items | lance, crossbow, dagger, staff, pickaxe, shield | Models visible; first-person angle, position and scale still differ. |
| 24 emoji | four lines with adjacent `Agjp` text | Glyphs transfer; baseline, size and lower-line clipping remain differences. |

The final MythicMobs pair was retaken on 1 October. The saved carrier had lost
its ModelEngine binding after the isolated server restart, before conversion;
it was not counted as a working sample. A fresh stationary MythicMobs spawn
restored the provider binding, verified through ModelEngine's API before capture.
Provider binding persistence across restart remains unverified.

Inventory slot 26 (`spectra_aurelium_skills:skill_ship`) refers to `#missing`;
Java shows the missing-texture pattern and diagnostic Bedrock output leaves
unmapped paper. Slot 19 (`iasurvival:item/shields/ruby_shield`) has a missing-texture
Java reference in this fixture while Bedrock renders a red shield. Its Java
reference needs investigation; it is **not** counted as a visual parity pass.
Neither source was modified to make the test pass.

| Java inventory | Bedrock inventory |
| --- | --- |
| 36 Java references including two missing-texture cells | 35 rendered previews and one unmapped paper item |

World cameras used the same commanded positions and directions in the two
clients. Client aspect ratios, UI scales and renderers differ. After-fix
BetterModel animations ran normally and were captured sequentially; their
screenshots do not establish synchronized frame timing. Earlier zero-speed
BetterModel samples are retained only to show the composite assembly defect.
Spectator cameras can reveal a translucent invisible carrier on Java.

The item tests use paper with the selected item-model component. They validate
appearance, not weapon damage, crossbow loading or production plugin gameplay.
The inventory is a test container, not evidence that custom menu backgrounds or
skills behave identically. Previous [menu failures](BROAD_CONTENT_2026-09-29.md)
remain open. Full animated-texture playback, tint/material equivalence,
first-person pose parity and glyph/UI layout are not accepted by this checkpoint.

## Evidence and test safety

Client connection and dialog handling used reusable scripts. Capture automation
now checks that both test players are still connected before taking a pair;
disconnected-menu captures and a paused held-item capture were excluded and
retaken. Fixtures use stationary, invulnerable carriers. Test players use
creative/spectator mode, return to a safe platform, and have zero deaths in all
ten saved player-stat records.

Published images are unchanged captures, with per-file hashes in
[sha256.json](images/acceptance/2026-09-30-composites/sha256.json), individual
results in [results.json](images/acceptance/2026-09-30-composites/results.json), and
sanitized counts and source-integrity checks in
[measurements.json](images/acceptance/2026-09-30-composites/measurements.json).
This is a tested development correction, not a claim of perfect conversion for
every model or animation.

---

## Türkçe

### Yeni örnekler ve bileşik dönüşümler — 30 Eylül–1 Ekim 2026

> Bu raporun ekran görüntüleri 4 Ekim 2026'da güncel görüntüler lehine kaldırıldı; aşağıdaki ölçümler
> geçerlidir. Güncel Java/Bedrock görüntüleri [README](../README.md#visual-acceptance-tests) içindedir.

Gerçek kaynaklardan yeni örnekler bir dönüşüm hatasını ortaya çıkardı: durağan bileşik alt modeller
kendi Java görüntü dönüşümlerini kaybediyordu. BetterModel'in `demon_knight` kafası farklı `fixed`
dönüşlerine sahip 27 alt model içerir. Küplerini birleştirip ilk alt modelin pozunu uygulamak boynuzları
ayırıyor ve diğer parçaları eğiyordu. `blue_wizard` de yanlış bir kafa açısı gösteriyordu.

Derleyici artık her alt ağı ve yazarın verdiği bağlam dönüşümlerini korur. Görüntü varlıkları ve
eklentiler ayrı alt kemikler alır; arayüz çizimi her alt modelin arayüz pozunu ve ışığını ortak bir
derinlik tamponuyla uygular. Üretilen sprite alt modelleri küboid alt modellerin yanında korunur. Bu,
model adı istisnası veya kaynak dosya düzenlemesi olmadan kendiliğinden yapılan bir dönüşümdür.

#### Kapsam ve derleme denetimleri

- Önceki iki eşya seçiminde olmayan 36 eşya modeli ve 24 emoji. Seçim sabit bir tohum kullandı ve
  farklı ad alanlarını ve model türlerini tercih etti.
- Dört yeni sağlayıcı modeli: BetterModel `blue_wizard` (211 öğe) ve `demon_knight` (323), ModelEngine
  `bl_earth_small_spider` (49) ve `angel_gm_lancer_one` (24). Mızrakçı ayrıca MythicMobs üzerinden
  bağlandı.
- Sağlayıcı keşfi canlı eşya toplayıcısını kullandı. BetterModel test düzeneği 36 yeni eşya tanımı ve
  önceden bekletilen altı eşya dahil 167/168 eşleme derledi. ModelEngine düzeneği 278/278 eşleme derledi:
  139 tanımlayıcı için eski ve modern eşlemeler, **278 ayrı mob değil**.
- İki tekrarlanan derleme özdeş paketler üretti. Katı mod ModelEngine düzeneğini kabul etti. BetterModel
  düzeneğindeki eksik kaynak dokusunu reddetti ve tanı paketini bayt bayt korudu.
- 18 paketteki 80 test yerelde geçti. Yeni regresyonlar bağımsız bileşik arayüz dönüşümlerini ve ortak
  derinliği, karışık sprite/küboid alt modelleri, ayrı elde tutma pozlarını, sabit dönüşleri ve sol el
  yedeklerini sınar.
- 135 ayrı kopyalanmış dosyayı ve dört özgün model dosyasını kapsayan 163 kaynak manifest girdisinin
  değişmediği karma ile doğrulandı. Üçüncü taraf kaynak paketleri, modeller, sunucu yapılandırmaları ve
  ham günlükler depo dışında tutulur.

Geliştirme [dosyasının](../artifacts/README.md) SHA-256 değeri
`152fd07575e4a2c26b910f5ba9974ebf08c6e501eeb04e43f9c4c8f1010f24c5`. Temel çizgi görüntüleri önceki
`8cb2ceff…` derlemesini kullanır; yayımlanan her çift tam derleme karmasını
[results.json](images/acceptance/2026-09-30-composites/results.json) içinde kaydeder.

#### İstemci gözlemleri

| Java referansı | Bedrock önce | Bedrock sonra |
| --- | --- | --- |
| Java şeytan şövalye | Düzeltmeden önce ayrık kafa parçaları | Düzeltmeden sonra birleşmiş kafa |
| Java mavi büyücü | Düzeltmeden önce yanlış kafa eğimi | Düzeltilmiş alt poz |

| Örnek | Denetimler | Gözlenen sonuç |
| --- | --- | --- |
| blue_wizard | bekleme, yürüme | Yanlış kafa eğimi düzeldi; ağ birleşik kalıyor. |
| demon_knight | bekleme, yürüme, savunma, hammer_attack_1 | Ayrık kafa/boynuz parçaları düzeldi; savunma ve saldırı pozları Bedrock'a ulaşıyor. |
| bl_earth_small_spider | bekleme, yürüme, fangs_attack | Geometri iki istemcide de görünür; poz örnekleri karşılaştırma için saklandı. |
| angel_gm_lancer_one | bekleme, yürüme, attack2 | Geometri ve örneklenen poz değişiklikleri iki istemcide de görünür. |
| MythicMobs mızrakçı | sabit, hasar almayan taşıyıcı ve ModelEngine bağlantısı | Tam model iki istemcide de görünür. |
| 36 eşyalık envanter | silahlar, mobilya, kozmetikler, model parçaları, simgeler | 35 Bedrock önizlemesi çizildi; 34'ünün kullanılabilir Java referansı var. İstisnalar aşağıda. |
| Altı elde tutulan eşya | mızrak, arbalet, hançer, asa, kazma, kalkan | Modeller görünür; birinci şahıs açısı, konumu ve ölçeği hâlâ farklı. |
| 24 emoji | bitişik `Agjp` yazılı dört satır | Glifler aktarılıyor; taban çizgisi, boyut ve alt satır kırpılması farklı kalıyor. |

Son MythicMobs çifti 1 Ekim'de yeniden çekildi. Kaydedilmiş taşıyıcı, yalıtılmış sunucunun yeniden
başlatılmasından sonra ve dönüşümden önce ModelEngine bağlantısını kaybetmişti; çalışan örnek sayılmadı.
Yeni ve sabit bir MythicMobs doğuşu sağlayıcı bağlantısını geri getirdi; çekimden önce ModelEngine API'si
ile doğrulandı. Sağlayıcı bağlantısının yeniden başlatmadan sonra kalıcılığı doğrulanmadı.

Envanter yuvası 26 (`spectra_aurelium_skills:skill_ship`) `#missing` dokusuna başvurur; Java eksik doku
desenini gösterir ve tanı amaçlı Bedrock çıktısı eşlenmemiş kağıt bırakır. Yuva 19
(`iasurvival:item/shields/ruby_shield`) bu düzenekte eksik dokulu bir Java referansına sahipken Bedrock
kırmızı bir kalkan çizer. Java referansının incelenmesi gerekir; görsel eşdeğerlik geçişi **sayılmaz**.
Testi geçirmek için iki kaynak da değiştirilmedi.

| Java envanteri | Bedrock envanteri |
| --- | --- |
| İki eksik dokulu hücre dahil 36 Java referansı | 35 çizilmiş önizleme ve bir eşlenmemiş kağıt eşya |

Dünya kameraları iki istemcide aynı komutla verilen konum ve yönleri kullandı. İstemci en-boy oranları,
arayüz ölçekleri ve çiziciler farklıdır. Düzeltme sonrası BetterModel animasyonları olağan çalıştı ve
sırayla çekildi; görüntüleri eşzamanlı kare zamanlaması kanıtlamaz. Önceki sıfır hızlı BetterModel
örnekleri yalnızca bileşik birleştirme hatasını göstermek için saklandı. İzleyici kameraları Java'da yarı
saydam görünmez bir taşıyıcıyı gösterebilir.

Eşya testleri seçili eşya modeli bileşenine sahip kağıt kullanır. Silah hasarını, arbalet doldurmayı veya
üretim eklentisi oynanışını değil, görünümü doğrular. Envanter bir test kabıdır; özel menü arka
planlarının veya becerilerin aynı davrandığının kanıtı değildir. Önceki [menü hataları](BROAD_CONTENT_2026-09-29.md)
açık kalıyor. Tam animasyonlu doku oynatımı, renk/malzeme eşdeğerliği, birinci şahıs poz eşdeğerliği ve
glif/arayüz yerleşimi bu kontrol noktasıyla kabul edilmez.

#### Kanıt ve test güvenliği

İstemci bağlantısı ve iletişim kutularının işlenmesi yeniden kullanılabilir betikler kullandı. Çekim
otomasyonu artık bir çift çekmeden önce iki test oyuncusunun da bağlı olduğunu denetliyor; bağlantı kesik
menü görüntüleri ve duraklatılmış bir elde tutma görüntüsü hariç tutuldu ve yeniden çekildi. Düzenekler
sabit, hasar almayan taşıyıcılar kullanır. Test oyuncuları yaratıcı/izleyici modundadır, güvenli bir
platforma döner ve kaydedilmiş on oyuncu istatistiğinin hepsinde ölüm sıfırdır.

Yayımlanan görüntüler değiştirilmemiş çekimlerdi; dosya başına karmalar
[sha256.json](images/acceptance/2026-09-30-composites/sha256.json), tek tek sonuçlar
[results.json](images/acceptance/2026-09-30-composites/results.json), arındırılmış sayılar ve kaynak
bütünlüğü denetimleri [measurements.json](images/acceptance/2026-09-30-composites/measurements.json)
içindedir. Bu, her model veya animasyon için kusursuz dönüşüm iddiası değil, test edilmiş bir geliştirme
düzeltmesidir.
