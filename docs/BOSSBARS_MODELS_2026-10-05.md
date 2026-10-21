# Custom boss bars, CustomNameplates' boss bar and an animated model - 5 October 2026

> Türkçe: [aşağıda](#türkçe)

Twilight development build 10 was checked with three more kinds of server content: boss bars whose sprites a
resource pack redraws, the boss bar of CustomNameplates with the plugin's own default configuration,
and an animated BetterModel mob model. Everything was captured on the isolated test server (Paper 26.2,
Geyser 2.11.3, Java 26.2 and Bedrock 1.26.5203.0 at GUI scale 2). The boss bar sprites and the model
are original art made for this test; the CustomNameplates capture shows the plugin's bundled default
art (CustomNameplates 3.0.42 by XiaoMoMi, Apache-2.0).

## Custom boss bars

Packs can redraw Java's boss bar sprites (`textures/gui/sprites/boss_bar/<colour>_background`,
`<colour>_progress` and the `notched_6/10/12/20` overlays). Bedrock draws every boss bar from one white
texture tinted with the bar's colour, so these bars showed Bedrock's plain bar before. Twilight now
converts the sprites of every redrawn colour (the vanilla ones where a pack redraws only part of a
colour) and draws them the way Java's `BossHealthOverlay` does: background, notches, progress cut at
the bar's value, notch progress. Colours a pack does not touch keep Bedrock's bar; colours a pack makes
transparent (CustomNameplates) stay hidden. Colour and style changes reach Bedrock players live.

<img src="images/acceptance/2026-10-05-showcase/bossbars.png" alt="Java and Bedrock side by side: a red framed health bar, a purple mana bar with ten gold notches and a vanilla green bar" width="100%">

The test pack redraws red at twice the resolution (364 x 10) and purple with a `notched_10` overlay;
green is vanilla. Each bar spans the same 182 units as on Java. With several bars, Bedrock's own grid
stacks them about one unit further apart per bar (second bar 1 unit, third 2.5 units lower).

## CustomNameplates' boss bar

CustomNameplates' default boss bar shows three backgrounds with an icon and text in a vertically
shifted font on each. The run found two defects that this release fixes:

- The line needs four Bedrock labels, and Bedrock shows at most 256 characters of a boss bar name.
  The fixed-size blocks that carry the layers were too small for the text layer and the padding too
  long. Block 0 (the top layer) is now 120 bytes, and four- and eight-unit moves are written as spaces
  (one byte) instead of spacer glyphs (three bytes).
- Names that are not layered were cut into the block labels when they were longer than block 0, so a
  long boss name was split and centred in pieces. Layered names now start with their own zero-width
  marker; every other name is shown whole by one label, however long.
- The shift font's own spaces were lost when the layout fell back to one label; they now stay spaces.

<img src="images/acceptance/2026-10-05-showcase/customnameplates-bossbar.png" alt="Java above, Bedrock below: three dark backgrounds with a clock icon and Time, a compass icon and Your Location, a cloud icon and Weather" width="100%">

Measured from the screen centre, the bar spans -213 to +212 units on both clients. CustomNameplates'
greeting (`Hello 여보세요 你好 こんにちは`) still falls back to one label on Bedrock: Java draws these
characters from its unicode font, whose widths the layout does not know, and Bedrock switches the whole
name to its smaller unicode font when a character is missing from its default font.

## Animated model

A small original golem (body, head, antenna, arms and legs) was made in Blockbench format for
BetterModel 3.4.2 with an `idle` and a `walk` animation, attached to a stationary mob. Twilight converts
BetterModel's bone items and its display bridge plays the pose sequence on Bedrock.

<table>
  <tr><th>walk (1.0 s loop)</th><th>idle (2.0 s loop)</th></tr>
  <tr><td><img src="images/acceptance/2026-10-05-showcase/golem-walk.gif" alt="Java and Bedrock side by side: the golem swings its arms and legs in a walk cycle" width="100%"></td>
      <td><img src="images/acceptance/2026-10-05-showcase/golem-idle.gif" alt="Java and Bedrock side by side: the golem bobs, turns its head and sways its antenna" width="100%"></td></tr>
</table>

Each client restarts the animation before its frames are taken; the two sequences are taken one after
the other, so they start at the same animation time but are not simultaneous. The clients use different
fields of view, so the Bedrock frames are scaled to the same model height.
[Still frame](images/acceptance/2026-10-05-showcase/golem.png),
[measurements](images/acceptance/2026-10-05-showcase/measurements.json),
[hashes](images/acceptance/2026-10-05-showcase/sha256.json).

## Build

200 tests (193 Twilight in 36 suites, 7 proxy) passed with Java 25. The test players stayed alive (no
saved deaths). No hosted CI was used.

---

## Türkçe

### Özel boss çubukları, CustomNameplates boss çubuğu ve animasyonlu bir model - 5 Ekim 2026

Twilight geliştirme derlemesi 10 üç tür sunucu içeriğiyle daha denetlendi: sprite'larını bir kaynak paketinin
yeniden çizdiği boss çubukları, eklentinin kendi varsayılan yapılandırmasıyla CustomNameplates'in boss
çubuğu ve animasyonlu bir BetterModel mob modeli. Her şey yalıtılmış test sunucusunda çekildi (Paper 26.2,
Geyser 2.11.3, arayüz ölçeği 2'de Java 26.2 ve Bedrock 1.26.5203.0). Boss çubuğu sprite'ları ve model bu
test için yapılmış özgün görsellerdir; CustomNameplates görüntüsü eklentinin paketle gelen varsayılan
görsellerini gösterir (XiaoMoMi'nin CustomNameplates 3.0.42'si, Apache-2.0).

#### Özel boss çubukları

Paketler Java'nın boss çubuğu sprite'larını yeniden çizebilir (`textures/gui/sprites/boss_bar/<renk>_background`,
`<renk>_progress` ve `notched_6/10/12/20` kaplamaları). Bedrock her boss çubuğunu çubuğun rengiyle
boyanan tek bir beyaz dokudan çizer; bu yüzden bu çubuklar önceden Bedrock'un düz çubuğunu gösteriyordu.
Twilight artık yeniden çizilen her rengin sprite'larını dönüştürüyor (bir paket rengin yalnızca bir kısmını
çizdiğinde eksik olanlar için vanilla sprite'lar) ve onları Java'nın `BossHealthOverlay` sınıfı gibi
çiziyor: arka plan, çentikler, çubuğun değerinde kesilen ilerleme, çentik ilerlemesi. Bir paketin
dokunmadığı renkler Bedrock'un çubuğunu korur; bir paketin saydam yaptığı renkler (CustomNameplates) gizli
kalır. Renk ve stil değişiklikleri Bedrock oyuncularına canlı ulaşır.

Görüntü yukarıdaki İngilizce bölümdedir. Test paketi kırmızıyı iki kat çözünürlükte (364 x 10), moru da
`notched_10` kaplamasıyla yeniden çizer; yeşil vanilladır. Her çubuk Java'daki gibi aynı 182 birimi kaplar.
Birden çok çubukta Bedrock'un kendi ızgarası çubukları çubuk başına yaklaşık bir birim daha aralıklı dizer
(ikinci çubuk 1, üçüncü 2,5 birim aşağıda).

#### CustomNameplates boss çubuğu

CustomNameplates'in varsayılan boss çubuğu, her birinde bir simge ve dikey kaydırılmış bir fontta yazı
bulunan üç arka plan gösterir. Çalıştırma bu sürümün düzelttiği iki hata buldu:

- Satır dört Bedrock etiketi gerektiriyor ve Bedrock bir boss çubuğu adının en fazla 256 karakterini
  gösteriyor. Katmanları taşıyan sabit boyutlu bloklar yazı katmanı için fazla küçük, dolgu da fazla
  uzundu. Blok 0 (üst katman) artık 120 bayt; dört ve sekiz birimlik kaydırmalar aralık glifleri (üç bayt)
  yerine boşluk (bir bayt) olarak yazılıyor.
- Katmanlı olmayan adlar blok 0'dan uzunsa blok etiketlerine bölünüyordu; bu yüzden uzun bir boss adı
  parçalara ayrılıp parça parça ortalanıyordu. Katmanlı adlar artık kendi sıfır genişlikli işaretleriyle
  başlıyor; diğer her ad, ne kadar uzun olursa olsun, tek bir etiketle tamamen gösteriliyor.
- Yerleşim tek etikete geri düştüğünde kaydırma fontunun kendi boşlukları kayboluyordu; artık boşluk
  olarak kalıyor.

Ekranın ortasından ölçüldüğünde çubuk iki istemcide de -213 ile +212 birim arasında. CustomNameplates'in
selamlaması (`Hello 여보세요 你好 こんにちは`) Bedrock'ta hâlâ tek etikete geri düşüyor: Java bu karakterleri
genişliklerini yerleşimin bilmediği unicode fontundan çiziyor, Bedrock da varsayılan fontunda olmayan bir
karakter olduğunda tüm adı daha küçük unicode fontuna geçiriyor.

#### Animasyonlu model

BetterModel 3.4.2 için Blockbench biçiminde `idle` ve `walk` animasyonlu, küçük ve özgün bir golem (gövde,
kafa, anten, kollar ve bacaklar) yapıldı ve sabit bir moba bağlandı. Twilight BetterModel'in kemik
eşyalarını dönüştürür, görüntü köprüsü de poz dizisini Bedrock'ta oynatır.

Her istemci kareleri alınmadan önce animasyonu yeniden başlatır; iki dizi art arda çekilir, bu yüzden aynı
animasyon anında başlarlar ama eşzamanlı değildir. İstemciler farklı görüş alanları kullandığından Bedrock
kareleri aynı model yüksekliğine ölçeklenir. Animasyonlar ve [durağan kare](images/acceptance/2026-10-05-showcase/golem.png)
yukarıdaki İngilizce bölümdedir; [ölçümler](images/acceptance/2026-10-05-showcase/measurements.json),
[karmalar](images/acceptance/2026-10-05-showcase/sha256.json).

#### Derleme

200 test (36 paketteki 193 Twilight testi, 7 proxy testi) Java 25 ile geçti. Test oyuncuları hayatta
kaldı (kayıtlı ölüm yok). Barındırılan CI kullanılmadı.
