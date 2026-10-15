# Live display bridge (development)

> Türkçe: [aşağıda](#türkçe)

This implementation is under visual validation. It is not a claim of complete
BetterModel, ModelEngine, MythicMobs, or arbitrary Java entity support.

Twilight emits an independent centered geometry for each converted custom mesh.
The Java item geometry and the hand attachable remain separate. A shared Bedrock
client entity selects its mesh and texture using the generated display index.
The bridge reads that index from the **deployed** pack at server startup.
Changing or removing this index requires a full server restart, even if item
mappings are unchanged. Texture-only changes retain the ordinary reload path.

Geyser currently has no public item-display translator registration API. The
adapter therefore compiles against the pinned Geyser core build
`2.11.3-20260925.135253-13`. It registers a Java `item_display` translator only
when no other translator owns that type. Geyser remains responsible for each
connection's spawn, movement, metadata, removal, and dimension lifecycle.
No Java server entities or provider animation states are modified.

Each update carries both translation/scale/quaternion endpoints. The resource
pack uses shortest-arc quaternion SLERP at render time, with a normalized linear
fallback for nearly identical rotations. Left and right rotations remain
separate around nonuniform scale. A packed item/context property keeps the
protocol within Bedrock's 32-property limit, including timing and revision.
The rig includes Java's extra Y=180 item frame after the display transformation.
Entity yaw is applied once by the Bedrock actor. Spawn and movement packets keep
head yaw equal to body yaw because Java displays have no independent head.
This prevents Bedrock's living-body rotation from turning mounted meshes toward
a default head heading. The generated rig does not reapply body yaw.
Explicit and fallback left-hand item contexts both receive Java mirroring.
The display mesh ignores the base entity invisibility flag, as Java does.
Zero view range hides a display without losing its selected item or transform;
restoring the range restores its current appearance. Positive-distance culling
still needs separate client render-distance coverage.

Only start-delay metadata resets the interpolation clock. Duration-only updates
retain both endpoints; pose updates without a start delta retain the existing
clock. Negative delay and interrupted interpolation have dedicated tests.
Quaternion-to-Euler extraction handles both gimbal poles, checked by evaluating
the emitted numeric Molang against independently composed quaternions.

The local real-model probe now assembles the original salamander in both clients;
its attack pose is visible in the reviewed captures. Three stationary carriers
produced 105 per-session displays. This is visual evidence for one real model,
not full provider or arbitrary-model certification.

A later ModelEngine R4.1.1 probe verified two direct API models and one new
MythicMobs model in both clients. A frozen attack pose matched the Java reference
after correcting entity invisibility and zero-view-range handling. See the
[real-content review](REAL_CONTENT_REVIEW.md) for paired images and limits.

Further validation must include mounted-display offsets,
per-viewer changes, negative scale, near-singular rotations, item replacement,
reload/reconnect, and removal. Font/UI adaptation, animated textures, armor-stand
model providers, tinting, billboard constraints and lighting require their own
coverage; they are not implied by successful item-display registration.

## Upstream credits and licensing

- [Geyser](https://github.com/GeyserMC/Geyser), by GeyserMC and contributors,
  copyright 2019–2026, MIT license. Geyser is a compile-only dependency and a
  separately installed runtime plugin. Twilight does not bundle its classes.
- [BetterModel](https://github.com/toxicity188/BetterModel), by toxicity188 and
  contributors, copyright 2024–2026, MIT license. Its public API and protocol
  implementation were inspected to understand display behavior. BetterModel's
  code, JAR, and user model assets are not distributed in Twilight.
- ModelEngine and MythicMobs test installations remain private. Their proprietary
  binaries and source content must not enter public artifacts.

The adapter and compiler code are authored by siberanka under Twilight's
license ([Twilight License](../LICENSE): PolyForm Noncommercial 1.0.0 with additional terms, summary in [NOTICE](../NOTICE)). See the upstream projects for their complete license
texts and contributor histories.

---

## Türkçe

### Canlı görüntü köprüsü (geliştirme)

Bu uygulama görsel doğrulama aşamasındadır. BetterModel, ModelEngine, MythicMobs veya rastgele Java
varlıkları için tam destek iddiası değildir.

Twilight dönüştürülen her özel ağ için bağımsız, ortalanmış bir geometri üretir. Java eşya
geometrisi ile el eklentisi ayrı kalır. Ortak bir Bedrock istemci varlığı, üretilen görüntü dizinini
kullanarak ağını ve dokusunu seçer. Köprü bu dizini sunucu açılışında **dağıtılmış** paketten okur.
Bu dizini değiştirmek veya kaldırmak, eşya eşlemeleri değişmese bile sunucunun tamamen yeniden
başlatılmasını gerektirir. Yalnızca dokuyu etkileyen değişiklikler olağan yeniden yükleme yolunu korur.

Geyser'ın şu anda eşya görüntüsü çevirmeni kaydı için herkese açık bir API'si yoktur. Bu nedenle
bağdaştırıcı sabitlenmiş Geyser çekirdek derlemesi `2.11.3-20260925.135253-13` ile derlenir. Java
`item_display` çevirmenini yalnızca bu türün başka bir çevirmeni yoksa kaydeder. Her bağlantının
doğuş, hareket, meta veri, kaldırma ve boyut yaşam döngüsünden Geyser sorumlu kalır. Hiçbir Java
sunucu varlığı veya sağlayıcı animasyon durumu değiştirilmez.

Her güncelleme hem öteleme/ölçek/dördey uç noktalarını taşır. Kaynak paketi çizim anında en kısa yay
dördey SLERP'i kullanır; neredeyse aynı dönüşler için normalleştirilmiş doğrusal bir yedeği vardır.
Düzgün olmayan ölçek etrafında sol ve sağ dönüşler ayrı kalır. Paketlenmiş bir eşya/bağlam özelliği,
zamanlama ve sürüm dahil, protokolü Bedrock'un 32 özellik sınırı içinde tutar. Düzen, görüntü
dönüşümünden sonra Java'nın ek Y=180 eşya çerçevesini içerir. Varlık sapması Bedrock aktörü
tarafından bir kez uygulanır. Doğuş ve hareket paketleri kafa sapmasını gövde sapmasına eşit tutar,
çünkü Java görüntülerinin bağımsız bir kafası yoktur. Bu, Bedrock'un canlı gövde dönüşünün binilen
ağları varsayılan bir kafa yönüne çevirmesini önler. Üretilen düzen gövde sapmasını yeniden uygulamaz.
Açık ve yedek sol el eşya bağlamlarının ikisi de Java aynalamasını alır. Görüntü ağı, Java'daki gibi
temel varlığın görünmezlik bayrağını yok sayar. Sıfır görüş menzili bir görüntüyü seçili eşyasını
veya dönüşümünü kaybetmeden gizler; menzil geri gelince güncel görünümü de geri gelir. Pozitif
mesafeli kırpma hâlâ ayrı istemci çizim mesafesi denetimi gerektirir.

Ara değerleme saatini yalnızca başlangıç gecikmesi meta verisi sıfırlar. Yalnızca süre içeren
güncellemeler iki uç noktayı da korur; başlangıç farkı olmayan poz güncellemeleri mevcut saati korur.
Negatif gecikme ve yarıda kesilen ara değerlemenin ayrı testleri vardır. Dördeyden Euler'e çıkarım iki
gimbal kutbunu da işler; üretilen sayısal Molang, bağımsız olarak oluşturulan dördeylere karşı
değerlendirilerek denetlenir.

Yerel gerçek model denemesi artık özgün semenderi iki istemcide de birleştiriyor; saldırı pozu
incelenen görüntülerde görünür. Üç sabit taşıyıcı oturum başına 105 görüntü üretti. Bu bir gerçek
model için görsel kanıttır, tam sağlayıcı veya rastgele model sertifikası değildir.

Daha sonraki bir ModelEngine R4.1.1 denemesi iki doğrudan API modelini ve bir yeni MythicMobs modelini
iki istemcide doğruladı. Varlık görünmezliği ve sıfır görüş menzili işlenişi düzeltildikten sonra
dondurulmuş bir saldırı pozu Java referansıyla eşleşti. Eşlenmiş görüntüler ve sınırlar için
[gerçek içerik incelemesine](REAL_CONTENT_REVIEW.md) bakın.

Sonraki doğrulamalar binilen görüntü kaymalarını, izleyiciye özel değişiklikleri, negatif ölçeği,
tekilliğe yakın dönüşleri, eşya değişimini, yeniden yükleme/yeniden bağlanmayı ve kaldırmayı
içermelidir. Font/arayüz uyarlaması, animasyonlu dokular, zırh askılığı model sağlayıcıları,
renklendirme, billboard kısıtları ve ışıklandırma kendi kapsamlarını gerektirir; başarılı eşya
görüntüsü kaydı bunları kapsamaz.

#### Kaynak projeler ve lisanslar

- [Geyser](https://github.com/GeyserMC/Geyser), GeyserMC ve katkıda bulunanlar, telif 2019–2026,
  MIT lisansı. Geyser yalnızca derleme bağımlılığı ve ayrıca kurulan bir çalışma zamanı eklentisidir.
  Twilight onun sınıflarını içermez.
- [BetterModel](https://github.com/toxicity188/BetterModel), toxicity188 ve katkıda bulunanlar, telif
  2024–2026, MIT lisansı. Görüntü davranışını anlamak için herkese açık API'si ve protokol uygulaması
  incelendi. BetterModel'in kodu, JAR'ı ve kullanıcı model dosyaları Twilight ile dağıtılmaz.
- ModelEngine ve MythicMobs test kurulumları özel kalır. Tescilli ikilileri ve kaynak içerikleri
  herkese açık dosyalara giremez.

Bağdaştırıcı ve derleyici kodu siberanka tarafından Twilight'ın lisansıyla ([Twilight License](../LICENSE): ek koşullarla
birlikte PolyForm Noncommercial 1.0.0, özeti [NOTICE](../NOTICE) içinde) yazılmıştır. Tam lisans metinleri ve katkıda bulunan geçmişleri için kaynak projelere bakın.
