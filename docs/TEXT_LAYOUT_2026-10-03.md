# Java title layout for Bedrock chest screens - 3 October 2026

> Türkçe: [aşağıda](#türkçe)

> The screenshots of this report were retired on 4 October 2026 in favour of current captures; the measurements below remain. Current Java/Bedrock captures are in the [README](../README.md#visual-acceptance-tests).

> Later change (development build 6): the layout now also covers chat, the action bar,
> titles, boss bars, scoreboards, entity names and other container titles, and no
> longer requires the container layout. See the
> [text surface review](TEXT_SURFACES_2026-10-04.md).

Twilight development build 5 lays out container titles for Bedrock players with Java font
metrics. Real Java menu titles combine negative-space characters with large GUI
images. On Bedrock these titles previously failed in three ways: characters that
Java remaps in `minecraft:default` (for example `七` or `ἃ`) showed Bedrock's own
letters, negative and custom spacing was lost, and every bitmap glyph was drawn
one GUI unit right of Java. Six real Survival menus now land on the same GUI
pixels as on Java in the title area, and on all measured slot rows.

## Measured Bedrock glyph rules

Diagnostic glyph pages were measured in the Bedrock 1.26.5203 chest title:

- A glyph is drawn from its first column with alpha above zero, placed one unit
  after the pen. Its advance is its inked width plus one. Java draws column zero
  at the pen and advances by its width up to the rightmost inked column, plus one.
- Pixels with alpha 1 count as ink while remaining invisible. A fully transparent
  glyph advances four units.
- Vertical placement relative to text is identical to Java for atlas cells from
  16 to 512 pixels.
- Bedrock cannot advance by exactly one unit or move left within a line.

## Automatic conversion

`ui.java-text-layout` (default `true`, requires `ui.java-container-layout`) adds:

| Part | Behaviour |
| --- | --- |
| Java font model | Font definitions from every resource pack are combined like Java (higher-priority packs first). Bitmap advances use Java's formula, including negative heights (`(int)(0.5 + width × height / cellHeight) + 1`); space providers and empty TrueType glyphs become advances. TrueType files Java cannot load (no `maxp` table) are ignored, as on Java. |
| Private-use aliases | Characters Java remaps in the default font or a named font, and colliding or supplementary named-font glyphs, receive private-use code points on unused pages, so Bedrock's own glyphs stay intact. |
| Spacers | An invisible page of alpha-1 glyphs provides advances of 2 to 33 units. Small glyphs receive a variant with one invisible trailing column to absorb one-unit gaps. |
| Layout table | `twilight/text-layout.json` stores each character's Java advance, Bedrock code point and inked columns. |
| Chest UI | The chest title label starts 256 units left of Java's title origin. |
| Runtime | A Geyser translator wrapper rewrites chest titles for Bedrock players with leading spacers, Java-exact gaps and aliases. Styles, colours and the component tree are preserved; translated components are preceded by spacers. It follows Geyser start-up and reloads. |

Bedrock's touch (pocket) layout keeps its centred native title. There, glyphs are
substituted without positioning and Java-only spacing characters are removed.
Titles of hoppers, dispensers, furnaces and other containers are not changed.

## Real-menu results

Menu titles from the Survival DeluxeMenus definitions were reproduced with their
original font providers and images in an isolated fixture. Both clients ran at
two screen pixels per GUI unit. Shifts are the best alignment of the Bedrock
capture to Java, in GUI units: title band only, and the whole menu window.

| Menu (slots) | Title mechanism | Title shift | Menu shift |
| --- | --- | --- | --- |
| Jobs farmer (54) | 8 × negative-height bitmap `七`, image remapped from `ἃ` | 0, 0 | 0, 0 |
| Rules (36) | ItemsAdder `:offset_-8:` space provider, image `Ⱘ` | 0, 0 | 0, 0 |
| Minion panel (54) | 8 × negative-height bitmap `ꯈ`, image `ꯄ` | 0, 0 | 0, 0 |
| Wallet (27) | 12 × negative-height bitmap `㈁`, 512-cell image `㈆` | 0, 0 | 0, 0 |
| Server info (54) | 8 × `ꯈ`, image `ᭅ` | 0, 0 | 0, 0 |
| Barrel (27) | private-use image `` | 0, 0 (pixel-identical band) | 0, 0 |

Before this layout, the same barrel menu was one unit to the right
(Bedrock before).
Now: Java and
Bedrock.
The other five menus use third-party GUI art and are published as measurements
only ([measurements](images/acceptance/2026-10-03-text-layout/measurements.json),
[hashes](images/acceptance/2026-10-03-text-layout/sha256.json)).

The chest-size suite (9 to 54 slots) kept Java's title rows, slot rows, hotbar and
inventory label. Hopper, furnace and dispenser panels remained pixel-identical to
the vanilla Bedrock UI. The Bedrock content log reported no UI errors.

A live check also found that Java 26.2 does not load the ItemsAdder
`negative_spaces.ttf` file (no `maxp` table): its characters appear as fallback
glyphs on Java. Twilight now matches this instead of applying the font's advances.

## Build and environment

- 109 tests in 22 suites passed locally, including Java/Bedrock rendering models
  for spacing, glyph bearings, negative heights, TrueType advances, cross-pack font
  merging, aliasing, spacer invisibility and the reflective component rewrite.
- Release JAR SHA-256: `1824ec2b3f6ee5d91ad778479f47c98623dd4a9427a1258c9bc01c310fd5d78b`.
- Tested fixture pack SHA-256: `2075b2ac0280bb1d55308e9ca90a386fb79fd4ab1e49a91cce7bffafc9902b6c`
  (1,170 entries); two repeated builds were byte-identical, and a rejected strict
  build left the last pack unchanged.
- Java 26.2, Paper 26.2 build 121, Geyser 2.11.3 build 1247, Bedrock 1.26.5203.0
  on Windows; local build only, no hosted CI. Test players stayed alive (health 20,
  all saved death counters zero).

## Remaining limits

- A glyph directly after ordinary text (no space between) is still one unit right,
  because Bedrock cannot move the pen left. With a space between, it is exact.
- Overlapping layers (a title that moves backwards to draw text over an image) are
  not reproduced; such moves are counted as approximations.
- Ordinary characters are assumed to have the same widths on both clients;
  non-ASCII text before a glyph can shift it if the widths differ.
- Bitmap tint, bold glyphs, chat, item names, lore, scoreboards and boss bars are
  not laid out yet; aliased characters appear correctly only in chest titles.
- The touch layout was covered by unit tests, not by a live touch-client capture.

---

## Türkçe

### Bedrock sandık ekranları için Java başlık yerleşimi - 3 Ekim 2026

> Bu raporun ekran görüntüleri 4 Ekim 2026'da güncel görüntüler lehine kaldırıldı; aşağıdaki ölçümler
> geçerlidir. Güncel Java/Bedrock görüntüleri [README](../README.md#visual-acceptance-tests) içindedir.

> Sonraki değişiklik (geliştirme derlemesi 6): yerleşim artık sohbeti, aksiyon çubuğunu, başlıkları, boss
> çubuklarını, skor tablolarını, varlık adlarını ve diğer konteyner başlıklarını da kapsar ve konteyner
> yerleşimini gerektirmez. [Yazı yüzeyi incelemesine](TEXT_SURFACES_2026-10-04.md) bakın.

Twilight geliştirme derlemesi 5, konteyner başlıklarını Bedrock oyuncuları için Java font ölçüleriyle yerleştirir.
Gerçek Java menü başlıkları negatif boşluk karakterlerini büyük arayüz görselleriyle birleştirir.
Bedrock'ta bu başlıklar önceden üç şekilde bozuluyordu: Java'nın `minecraft:default` içinde yeniden
eşlediği karakterler (örneğin `七` veya `ἃ`) Bedrock'un kendi harflerini gösteriyordu, negatif ve özel
boşluklar kayboluyordu ve her bitmap glif Java'dan bir arayüz birimi sağda çiziliyordu. Altı gerçek
Survival menüsü artık başlık alanında ve ölçülen tüm yuva satırlarında Java ile aynı arayüz
piksellerine oturuyor.

#### Ölçülmüş Bedrock glif kuralları

Tanı amaçlı glif sayfaları Bedrock 1.26.5203 sandık başlığında ölçüldü:

- Bir glif, alfası sıfırdan büyük ilk sütunundan başlayarak kalemin bir birim ardına çizilir. İlerlemesi
  mürekkepli genişliği artı birdir. Java sıfırıncı sütunu kalemde çizer ve en sağdaki mürekkepli sütuna
  kadarki genişliği artı bir kadar ilerler.
- Alfası 1 olan pikseller görünmez kalsa da mürekkep sayılır. Tamamen saydam bir glif dört birim ilerler.
- Yazıya göre dikey yerleşim 16 ile 512 piksel arası atlas hücreleri için Java ile aynıdır.
- Bedrock tam olarak bir birim ilerleyemez veya bir satır içinde sola gidemez.

#### Otomatik dönüşüm

`ui.java-text-layout` (varsayılan `true`, `ui.java-container-layout` gerektirir) şunları ekler:

| Parça | Davranış |
| --- | --- |
| Java font modeli | Tüm kaynak paketlerinin font tanımları Java'daki gibi birleştirilir (öncelikli paketler önce). Bitmap ilerlemeleri negatif yükseklikler dahil Java'nın formülünü kullanır (`(int)(0.5 + genişlik × yükseklik / hücreYüksekliği) + 1`); boşluk sağlayıcıları ve boş TrueType glifleri ilerlemeye dönüşür. Java'nın yükleyemediği TrueType dosyaları (`maxp` tablosu olmayan) Java'daki gibi yok sayılır. |
| Özel kullanım takma adları | Java'nın varsayılan veya adlandırılmış bir fontta yeniden eşlediği karakterler ve çakışan ya da tamamlayıcı düzlemdeki adlandırılmış font glifleri kullanılmayan sayfalarda özel kullanım kod noktaları alır; böylece Bedrock'un kendi glifleri bozulmaz. |
| Aralık glifleri | Alfası 1 olan görünmez bir glif sayfası 2 ile 33 birim arası ilerlemeler sağlar. Küçük glifler bir birimlik boşlukları emmek için görünmez bir son sütunlu varyant alır. |
| Yerleşim tablosu | `twilight/text-layout.json` her karakterin Java ilerlemesini, Bedrock kod noktasını ve mürekkepli sütunlarını saklar. |
| Sandık arayüzü | Sandık başlık etiketi Java'nın başlık başlangıcının 256 birim solundan başlar. |
| Çalışma zamanı | Bir Geyser çevirmen sarmalayıcısı Bedrock oyuncuları için sandık başlıklarını baştaki aralık glifleri, Java ile birebir boşluklar ve takma adlarla yeniden yazar. Biçimler, renkler ve bileşen ağacı korunur; çevrilen bileşenlerin önüne aralık glifleri konur. Geyser açılışını ve yeniden yüklemelerini izler. |

Bedrock'un dokunmatik (cep) yerleşimi kendi ortalanmış başlığını korur. Orada glifler konumlandırılmadan
değiştirilir ve yalnızca Java'ya özgü boşluk karakterleri kaldırılır. Huni, fırlatıcı, fırın ve diğer
konteynerlerin başlıkları değişmez.

#### Gerçek menü sonuçları

Survival DeluxeMenus tanımlarındaki menü başlıkları, özgün font sağlayıcıları ve görselleriyle yalıtılmış
bir düzenekte yeniden üretildi. İki istemci de arayüz birimi başına iki ekran pikseliyle çalıştı.
Kaymalar Bedrock görüntüsünün Java'ya en iyi hizalanmasıdır (arayüz birimi): yalnızca başlık bandı ve
tüm menü penceresi.

| Menü (yuva) | Başlık mekanizması | Başlık kayması | Menü kayması |
| --- | --- | --- | --- |
| Jobs çiftçi (54) | 8 × negatif yükseklikli bitmap `七`, `ἃ`'dan yeniden eşlenmiş görsel | 0, 0 | 0, 0 |
| Kurallar (36) | ItemsAdder `:offset_-8:` boşluk sağlayıcısı, görsel `Ⱘ` | 0, 0 | 0, 0 |
| Minion paneli (54) | 8 × negatif yükseklikli bitmap `ꯈ`, görsel `ꯄ` | 0, 0 | 0, 0 |
| Cüzdan (27) | 12 × negatif yükseklikli bitmap `㈁`, 512 hücreli görsel `㈆` | 0, 0 | 0, 0 |
| Sunucu bilgisi (54) | 8 × `ꯈ`, görsel `ᭅ` | 0, 0 | 0, 0 |
| Varil (27) | özel kullanım görseli | 0, 0 (piksel piksel aynı bant) | 0, 0 |

Bu yerleşimden önce aynı varil menüsü bir birim sağdaydı. Diğer beş menü üçüncü taraf arayüz görselleri
kullanır ve yalnızca ölçüm olarak yayımlanır ([ölçümler](images/acceptance/2026-10-03-text-layout/measurements.json),
[karmalar](images/acceptance/2026-10-03-text-layout/sha256.json)).

Sandık boyutu takımı (9 ile 54 yuva) Java'nın başlık satırlarını, yuva satırlarını, kısayol çubuğunu ve
envanter etiketini korudu. Huni, fırın ve fırlatıcı panelleri vanilla Bedrock arayüzüyle piksel piksel
aynı kaldı. Bedrock içerik günlüğü hiçbir arayüz hatası bildirmedi.

Canlı bir denetim Java 26.2'nin ItemsAdder `negative_spaces.ttf` dosyasını (`maxp` tablosu yok)
yüklemediğini de buldu: karakterleri Java'da yedek glif olarak görünür. Twilight artık fontun
ilerlemelerini uygulamak yerine buna uyar.

#### Derleme ve ortam

- Boşluk, glif kenar payları, negatif yükseklikler, TrueType ilerlemeleri, paketler arası font
  birleştirme, takma adlar, aralık görünmezliği ve yansıtmalı bileşen yeniden yazımı için Java/Bedrock
  çizim modelleri dahil 22 paketteki 109 test yerelde geçti.
- Sürüm JAR SHA-256: `1824ec2b3f6ee5d91ad778479f47c98623dd4a9427a1258c9bc01c310fd5d78b`.
- Test edilen düzenek paketi SHA-256: `2075b2ac0280bb1d55308e9ca90a386fb79fd4ab1e49a91cce7bffafc9902b6c`
  (1.170 girdi); iki tekrarlanan derleme bayt bayt aynıydı ve reddedilen bir katı derleme son paketi
  değiştirmedi.
- Java 26.2, Paper 26.2 derleme 121, Geyser 2.11.3 derleme 1247, Windows üzerinde Bedrock 1.26.5203.0;
  yalnızca yerel derleme, barındırılan CI yok. Test oyuncuları hayatta kaldı (can 20, tüm kayıtlı ölüm
  sayaçları sıfır).

#### Kalan sınırlar

- Sıradan yazının hemen ardından (arada boşluk olmadan) gelen bir glif hâlâ bir birim sağdadır, çünkü
  Bedrock kalemi sola taşıyamaz. Arada boşluk varsa birebirdir.
- Üst üste binen katmanlar (bir görselin üzerine yazı çizmek için geri giden başlık) yeniden
  üretilmiyor; bu tür geri gitmeler yaklaşık olarak sayılır. (geliştirme derlemesi 8 ile çözüldü.)
- Sıradan karakterlerin iki istemcide aynı genişlikte olduğu varsayılır; bir glifin önündeki ASCII
  olmayan yazı genişlikler farklıysa onu kaydırabilir.
- Bitmap renklendirmesi, kalın glifler, sohbet, eşya adları, açıklamalar, skor tabloları ve boss
  çubukları henüz yerleştirilmiyor; takma adlı karakterler yalnızca sandık başlıklarında doğru görünür.
- Dokunmatik yerleşim canlı bir dokunmatik istemci görüntüsüyle değil, birim testleriyle kapsandı.
