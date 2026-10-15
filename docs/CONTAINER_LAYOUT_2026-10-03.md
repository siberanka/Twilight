# Java container layout on Bedrock chest screens - 3 October 2026

> Türkçe: [aşağıda](#türkçe)

> The screenshots of this report were retired on 4 October 2026 in favour of current captures; the measurements below remain. Current Java/Bedrock captures are in the [README](../README.md#visual-acceptance-tests).

Font-image menus are drawn by Java as part of the container title. Bedrock's
native chest title wrapped such an image at 90% of the panel width,
hyphenated it and clipped it, and its slot rows did not keep Java's spacing.
Twilight development build 4 now generates a Java-compatible desktop chest layout
automatically. A real 176 x 83 menu image from the Survival test content now
appears in full and lines up with its slots in every chest row count from 1 to 6.

This report covers the **desktop (classic) chest family**: chests, large chests,
ender chests, shulker boxes and barrels, as Geyser presents generic 9 x 1 to
9 x 6 menus. It does not claim parity for touch layouts, other container types,
menu actions or tooltips.

## Automatic conversion

`ui.java-container-layout` (default `true`) adds two partial Bedrock UI
definitions to the generated pack. Bedrock merges them into its vanilla
`chest` and `common` namespaces; vanilla elements are not copied or replaced.

| Java behavior | Bedrock adaptation |
| --- | --- |
| The title is drawn unwrapped and unclipped at (8, 6). | The chest title label uses its text size and Java's position relative to the first slot frame. |
| The title is drawn after slot frames and before items. | The title layer is above slot frames and below selection, hover and item renderers. |
| Default title text colour is `0x404040`. | The label's default colour is `0x404040`; explicit colour codes still apply. |
| The player inventory frame starts 13 units below the last chest row; the hotbar starts 4 units below the inventory. | The top half is raised by 2 units and chest screens set a hotbar offset of 4 units through a variable. |
| The `Inventory` label is one unit right of and ten above the player inventory, drawn after the title. | Chest screens set its offset and a layer above the title through variables. |

The shared hotbar template and inventory label read these values from
variables whose defaults equal vanilla Bedrock. Hopper, dispenser and furnace
screens were pixel-identical to the vanilla UI in the captured panel areas.
The inventory label is rebuilt with Bedrock's `modifications` mechanism,
because the vanilla label is an unnamed array entry with literal values.
Disable the option to restore the unmodified Bedrock chest UI.

## Measured results

Both clients ran at two screen pixels per GUI unit. Each empty menu used the
title `H<8 x 8 probe>H<41 x 9 rank>H`. Positions are GUI units relative to the
first chest slot. The scripted comparison measured title glyph runs and rows,
every slot row (chest, player inventory and hotbar), and the first glyph of the
inventory label.

| Chest slots | Vanilla Bedrock layout | Twilight layout |
| --- | --- | --- |
| 9, 18, 27 | Wide title wraps and clips; title text 1 unit left and 2 units lower; inventory 2 and hotbar 3 units higher; inventory label 1 unit left and 1 lower | Title rows, slot rows, hotbar and inventory label identical to Java |
| 36, 45, 54 | As above, except the title text is 1 unit lower | Title rows, slot rows, hotbar and inventory label identical to Java |

The remaining title difference occurs only for custom bitmap glyphs: Bedrock
draws each glyph one unit to the right of Java's position. Their rows match
Java, and so do ordinary text and the character after a glyph without
transparent left columns. The
[layout measurements](images/acceptance/2026-10-03-container-layout/layout-measurements.json)
and [vanilla baseline](images/acceptance/2026-10-03-container-layout/vanilla-measurements.json)
contain the complete per-size data.

### Bitmap glyph behavior measured in the title

- Bedrock trims transparent columns at the left edge of a glyph, draws the
  first opaque column one unit after the pen position and advances by the
  opaque width plus one. Java draws from the pen including transparent
  columns and advances by the width up to the rightmost opaque column plus one.
  A glyph without left transparency therefore appears one unit to the right,
  and left padding narrows the Bedrock advance. A contextual layout adapter is
  needed to correct this; it is not implemented.
- Bedrock does not tint private-use bitmap glyphs. Java multiplies them by the
  text colour, so an image in a default-colour title is darkened by `0x404040`
  on Java and unchanged on Bedrock. White (`&f`) titles match. A red `&c`
  probe was `(255, 0, 85)` on Java and `(255, 0, 255)` on Bedrock.

## Reviewed captures

All captures used development build 4, except the vanilla
baseline, which used the same build with the layout disabled.

| Case | Java | Bedrock |
| --- | --- | --- |
| White-title menu image, 27 slots | Reference | Full image, slots aligned |
| White-title menu image, 54 slots | Reference | Full image, slots aligned |
| Default-colour title | Image darkened by `0x404040` | Image not tinted |
| Probe glyphs | Reference | One-unit glyph offset, no tint |
| Vanilla Bedrock UI, 27 slots | - | Image missing, title hyphenated |

Crops are taken from unchanged client screenshots and identified by
[SHA-256 hashes](images/acceptance/2026-10-03-container-layout/sha256.json).
The Java client language was English and the Bedrock client language Turkish,
so the inventory labels differ in text. Two invalid Java item references remain
visible as missing-texture items in the Java menu.

## Build and environment

- 95 tests in 20 suites passed locally; four new tests cover title placement,
  layering, inventory and hotbar spacing, vanilla defaults and the option switch.
- Release JAR SHA-256: `13d45d42b078211266879d1f86ce57b9e0c6961d2ef8be5fe5b71ac4787d3b16`.
- Tested pack SHA-256: `1723b9024707aed15493a75098e317f3e102b2ade780ed6af570cfe445d504b9`
  (1,165 entries). Two repeated builds were byte-identical, and a rejected
  strict build left the last pack unchanged.
- Java 26.2, Paper 26.2 build 121, Geyser 2.11.3 build 1247 and Bedrock
  1.26.5203.0 on Windows. Local build only; no hosted CI.
- The Bedrock content log reported no UI errors. Test players remained creative
  with health 20, and all saved death counters remained zero.

## Remaining limits

The [text layout review](TEXT_LAYOUT_2026-10-03.md) (development build 5) later removed the
one-unit glyph offset and added Java spacing for chest titles; the items below
describe this development build 4 checkpoint.

- Contextual bitmap tint, the one-unit glyph offset, Java space-provider
  advances (including negative spaces) and multi-layer title composition need a
  runtime text/layout adapter.
- Touch (pocket) layouts, controller focus and other container screens such as
  hoppers, dispensers, furnaces and anvils keep the vanilla Bedrock layout.
- Bedrock's panel frame and close button remain native. Java's image is drawn
  above them where it overlaps.
- Menu interactions, tooltips and live HUD state are outside this change.

---

## Türkçe

### Bedrock sandık ekranlarında Java konteyner yerleşimi - 3 Ekim 2026

> Bu raporun ekran görüntüleri 4 Ekim 2026'da güncel görüntüler lehine kaldırıldı; aşağıdaki ölçümler
> geçerlidir. Güncel Java/Bedrock görüntüleri [README](../README.md#visual-acceptance-tests) içindedir.

Font görselli menüler Java'da konteyner başlığının parçası olarak çizilir. Bedrock'un yerel sandık
başlığı böyle bir görseli panel genişliğinin %90'ında satır kaydırıyor, kısa çizgiyle bölüyor ve
kırpıyordu; yuva satırları da Java'nın aralıklarını korumuyordu. Twilight geliştirme derlemesi 4 artık Java ile
uyumlu bir masaüstü sandık yerleşimini kendiliğinden üretir. Survival test içeriğindeki gerçek bir
176 x 83 menü görseli artık tamamen görünür ve 1'den 6'ya her sandık satır sayısında yuvalarıyla hizalanır.

Bu rapor **masaüstü (klasik) sandık ailesini** kapsar: Geyser'ın genel 9 x 1 ile 9 x 6 menüleri olarak
sunduğu sandıklar, büyük sandıklar, ender sandıkları, shulker kutuları ve variller. Dokunmatik
yerleşimler, diğer konteyner türleri, menü eylemleri veya açıklama kutuları için eşdeğerlik iddia etmez.

#### Otomatik dönüşüm

`ui.java-container-layout` (varsayılan `true`) üretilen pakete iki kısmi Bedrock arayüz tanımı ekler.
Bedrock bunları vanilla `chest` ve `common` ad alanlarıyla birleştirir; vanilla öğeler kopyalanmaz veya
değiştirilmez.

| Java davranışı | Bedrock uyarlaması |
| --- | --- |
| Başlık (8, 6) konumunda satır kaydırmadan ve kırpılmadan çizilir. | Sandık başlık etiketi kendi yazı boyutunu ve Java'nın ilk yuva çerçevesine göre konumunu kullanır. |
| Başlık yuva çerçevelerinden sonra ve eşyalardan önce çizilir. | Başlık katmanı yuva çerçevelerinin üstünde, seçim, üzerine gelme ve eşya çizicilerinin altındadır. |
| Varsayılan başlık rengi `0x404040`'tır. | Etiketin varsayılan rengi `0x404040`'tır; açık renk kodları yine uygulanır. |
| Oyuncu envanteri çerçevesi son sandık satırının 13 birim altında, kısayol çubuğu envanterin 4 birim altında başlar. | Üst yarı 2 birim yükseltilir ve sandık ekranları bir değişkenle 4 birimlik kısayol çubuğu kayması ayarlar. |
| `Inventory` etiketi oyuncu envanterinin bir birim sağında ve on birim üstündedir; başlıktan sonra çizilir. | Sandık ekranları kaymasını ve başlığın üstündeki katmanını değişkenlerle ayarlar. |

Ortak kısayol çubuğu şablonu ve envanter etiketi bu değerleri varsayılanları vanilla Bedrock'a eşit olan
değişkenlerden okur. Huni, fırlatıcı ve fırın ekranları çekilen panel alanlarında vanilla arayüzle
piksel piksel aynıydı. Vanilla etiket sabit değerli adsız bir dizi girdisi olduğundan envanter etiketi
Bedrock'un `modifications` mekanizmasıyla yeniden kurulur. Değiştirilmemiş Bedrock sandık arayüzüne
dönmek için seçeneği kapatın.

#### Ölçülmüş sonuçlar

İki istemci de arayüz birimi başına iki ekran pikseliyle çalıştı. Her boş menü
`H<8 x 8 deneme>H<41 x 9 rütbe>H` başlığını kullandı. Konumlar ilk sandık yuvasına göre arayüz
birimidir. Betikli karşılaştırma başlık glif dizilerini ve satırlarını, her yuva satırını (sandık,
oyuncu envanteri ve kısayol çubuğu) ve envanter etiketinin ilk glifini ölçtü.

| Sandık yuvaları | Vanilla Bedrock yerleşimi | Twilight yerleşimi |
| --- | --- | --- |
| 9, 18, 27 | Geniş başlık kayar ve kırpılır; başlık yazısı 1 birim solda ve 2 birim aşağıda; envanter 2, kısayol çubuğu 3 birim yukarıda; envanter etiketi 1 birim solda ve 1 aşağıda | Başlık satırları, yuva satırları, kısayol çubuğu ve envanter etiketi Java ile aynı |
| 36, 45, 54 | Yukarıdaki gibi, yalnızca başlık yazısı 1 birim aşağıda | Başlık satırları, yuva satırları, kısayol çubuğu ve envanter etiketi Java ile aynı |

Kalan başlık farkı yalnızca özel bitmap gliflerde görülür: Bedrock her glifi Java'nın konumundan bir
birim sağa çizer. Satırları Java ile eşleşir; sıradan yazı ve sol tarafında saydam sütun olmayan bir
glifin ardındaki karakter de eşleşir. [Yerleşim ölçümleri](images/acceptance/2026-10-03-container-layout/layout-measurements.json)
ve [vanilla temel çizgisi](images/acceptance/2026-10-03-container-layout/vanilla-measurements.json)
boyut başına tüm verileri içerir.

##### Başlıkta ölçülen bitmap glif davranışı

- Bedrock bir glifin sol kenarındaki saydam sütunları kırpar, ilk opak sütunu kalem konumundan bir birim
  sonra çizer ve opak genişlik artı bir kadar ilerler. Java saydam sütunlar dahil kalemden çizer ve en
  sağdaki opak sütuna kadarki genişlik artı bir kadar ilerler. Bu yüzden sol saydamlığı olmayan bir glif
  bir birim sağda görünür ve sol dolgu Bedrock ilerlemesini daraltır. Bunu düzeltmek için bağlama duyarlı
  bir yerleşim bağdaştırıcısı gerekir; uygulanmadı.
- Bedrock özel kullanım bitmap gliflerini renklendirmez. Java onları yazı rengiyle çarpar; bu yüzden
  varsayılan renkli bir başlıktaki görsel Java'da `0x404040` ile koyulaşır, Bedrock'ta değişmez. Beyaz
  (`&f`) başlıklar eşleşir. Kırmızı bir `&c` denemesi Java'da `(255, 0, 85)`, Bedrock'ta `(255, 0, 255)`
  oldu.

#### İncelenen görüntüler

Yerleşimin kapatıldığı aynı derlemeyi kullanan vanilla temel çizgisi dışında tüm görüntüler
geliştirme derlemesi 4 ile çekildi.

| Durum | Java | Bedrock |
| --- | --- | --- |
| Beyaz başlıklı menü görseli, 27 yuva | Referans | Tam görsel, yuvalar hizalı |
| Beyaz başlıklı menü görseli, 54 yuva | Referans | Tam görsel, yuvalar hizalı |
| Varsayılan renkli başlık | Görsel `0x404040` ile koyulaşmış | Görsel renklendirilmemiş |
| Deneme glifleri | Referans | Bir birimlik glif kayması, renklendirme yok |
| Vanilla Bedrock arayüzü, 27 yuva | - | Görsel eksik, başlık kısa çizgiyle bölünmüş |

Kırpıntılar değiştirilmemiş istemci görüntülerinden alındı ve
[SHA-256 karmalarıyla](images/acceptance/2026-10-03-container-layout/sha256.json) tanımlanır. Java
istemci dili İngilizce, Bedrock istemci dili Türkçeydi; bu yüzden envanter etiketlerinin yazısı farklı.
İki geçersiz Java eşya referansı Java menüsünde eksik dokulu eşya olarak görünür kalıyor.

#### Derleme ve ortam

- 20 paketteki 95 test yerelde geçti; dört yeni test başlık yerleşimini, katmanları, envanter ve kısayol
  çubuğu aralığını, vanilla varsayılanları ve seçenek anahtarını kapsar.
- Sürüm JAR SHA-256: `13d45d42b078211266879d1f86ce57b9e0c6961d2ef8be5fe5b71ac4787d3b16`.
- Test edilen paket SHA-256: `1723b9024707aed15493a75098e317f3e102b2ade780ed6af570cfe445d504b9`
  (1.165 girdi). İki tekrarlanan derleme bayt bayt aynıydı ve reddedilen bir katı derleme son paketi
  değiştirmedi.
- Java 26.2, Paper 26.2 derleme 121, Geyser 2.11.3 derleme 1247 ve Windows üzerinde Bedrock 1.26.5203.0.
  Yalnızca yerel derleme; barındırılan CI yok.
- Bedrock içerik günlüğü hiçbir arayüz hatası bildirmedi. Test oyuncuları 20 canla yaratıcı modda kaldı
  ve tüm kayıtlı ölüm sayaçları sıfır kaldı.

#### Kalan sınırlar

[Yazı yerleşimi incelemesi](TEXT_LAYOUT_2026-10-03.md) (geliştirme derlemesi 5) daha sonra bir birimlik glif
kaymasını kaldırdı ve sandık başlıklarına Java aralıklarını ekledi; aşağıdaki maddeler bu geliştirme derlemesi 4
kontrol noktasını anlatır.

- Bağlama göre bitmap renklendirmesi, bir birimlik glif kayması, Java boşluk sağlayıcısı ilerlemeleri
  (negatif boşluklar dahil) ve çok katmanlı başlık birleştirmesi bir çalışma zamanı yazı/yerleşim
  bağdaştırıcısı gerektirir.
- Dokunmatik (cep) yerleşimler, kumanda odağı ve huni, fırlatıcı, fırın, örs gibi diğer konteyner
  ekranları vanilla Bedrock yerleşimini korur.
- Bedrock'un panel çerçevesi ve kapatma düğmesi yerel kalır. Java görseli çakıştığı yerde onların
  üstüne çizilir.
- Menü etkileşimleri, açıklama kutuları ve canlı HUD durumu bu değişikliğin kapsamı dışındadır.
