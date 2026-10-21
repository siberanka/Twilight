# Java text on every Bedrock surface and production builds - 4 October 2026

> Türkçe: [aşağıda](#türkçe)

Twilight development build 6 extends the Java font layout from chest titles to the other
places where servers draw custom font content: chat, the action bar, titles and
subtitles, boss bars, scoreboards, entity names and text displays (holograms,
nameplates), and the titles of other containers. The same release fixes several
conversion failures that independent content from four further real servers
exposed. All checks below were run against isolated copies; production servers
were only read.

## Text surfaces

Before this release, only chest titles were rewritten for Bedrock players. Named
fonts and characters Java remaps were sent to Bedrock unchanged, so a CraftEngine
rank image (`七` in its named rank font) appeared in chat as the CJK letter
`七`, and an ItemsAdder HUD in the action bar showed boxes for its offset
characters. Now every supported packet is laid out with the Java metrics already
generated for the pack:

| Surface | Packets | Line model |
| --- | --- | --- |
| Chat | system, player (signed content, sender, target), disguised | left-aligned |
| Action bar | action bar text, overlay system chat | centred |
| Titles | title, subtitle | centred |
| Boss bars | boss bar title | centred |
| Scoreboards | objective title, team display name/prefix/suffix, score display names | centred title, left-aligned lines |
| Entities | custom names, text-display text (multi-line) | centred per line |
| Containers | titles of hoppers, furnaces, dispensers and other non-chest screens | left-aligned |

Left-aligned lines start where their first character needs, so every image keeps
Java's distance to its neighbours. Centred lines are padded with leading or
trailing invisible spacers so Bedrock centres them where Java does; a leading
negative shift therefore moves a HUD image left exactly like on Java. Java
centres at `-width / 2` in integer arithmetic while Bedrock centres exactly, so
lines with an odd Java width receive one more unit of padding; the widths of
ordinary characters for this come from the vanilla client's own font sheets.
Ordinary spaces stay spaces wherever possible, so Bedrock still wraps chat at
them. Text without custom font characters reaches Geyser as the same packet.

`ui.java-text-surfaces` (default `true`) controls the new surfaces;
`ui.java-text-layout` no longer requires the Java container layout.

### Live measurements

An independent fixture was copied read-only from a second server (SkyBlock):
CraftEngine named-font ranks and icons whose code points (`U+4E00`..) collide with
a default-font shift character of the first fixture on purpose, and a
CustomNameplates background set with its off-screen spacing characters. Images
were located in both captures by masked template matching against the authored
art (Java) and the glyph cell actually served to Bedrock. Positions are in GUI
units at two screen pixels per unit; deltas are Bedrock minus Java.

| Scenario | Content | Result |
| --- | --- | --- |
| Action bar | 3 × ItemsAdder `:offset_-8:`, rank, icon | 0, 0 |
| Action bar with text | `Coins: 25 ` + icon + ` left` | 0 |
| Nameplate bar | CustomNameplates left, middle, right (39 units, odd) | 0, 0, 0 |
| Boss bar | rank, ` Boss `, icon | 0, 0 |
| Boss bar, odd width | rank, ` Boss! `, icon | 0, 0 |
| Chat | rank, ` Steve: merhaba `, icon (relative) | 0, 0 |
| Sidebar | rank in the objective title, icon in a score name | both shown |
| Title and subtitle | rank, icon | both shown |

With `ui.java-text-surfaces: false` the same scenarios showed the raw characters
on Bedrock in every surface. The first measurement of the nameplate bar was half a
unit left of Java, which established Java's integer centring; the correction
above removed it. Chest menus were re-measured with the release build: the six
real Survival menus still match Java (title band and window shift 0, 0) and
hopper, furnace and dispenser panels are identical to the vanilla Bedrock UI.

Measurements: [measurements](images/acceptance/2026-10-04-text-surfaces/measurements.json)
([hashes](images/acceptance/2026-10-04-text-surfaces/sha256.json)). The captures
show third-party rank and nameplate art and are therefore not published.

## Production builds of five real servers

A new audit runs the complete build (items, fonts with the layout, sounds, UI)
for each server's discovered sources, as the plugin would on that server. The
same five servers were built with development build 5 and with development build 6:

| Server | Custom items, build 5 | Custom items, build 6 | Problems build 5 → build 6 |
| --- | --- | --- | --- |
| Survival | 125 / 130 | 837 / 841 | 152 → 75 |
| SkyBlock | 0 / 0 | 29 / 52 (21 skin heads left native) | 44 → 43 |
| Box PvP v2 | 772 / 781 | 805 / 811 | 11 → 1 |
| Box PvP (legacy) | 532 / 843 | 539 / 845 | 313 → 301 |
| SMP Lifesteal | no custom content | no custom content | 0 → 0 |

The remaining Survival and SkyBlock problems are 113 vanilla sound events the
packs replace while `vanilla-override` is disabled (a configuration choice), four
item references that no pack provides, and one 10,000-unit full-screen overlay
image that Bedrock's font atlas cannot hold. The legacy Box PvP server lists items
from an older ItemsAdder configuration whose models no longer exist.

Defects found and fixed:

- **Vanilla asset copies shadowed server packs.** ItemsAdder keeps copies of the
  vanilla client assets under `storage/cache/vanilla_assets/<version>`. They were
  discovered as packs above the generated pack, so vanilla item definitions hid
  the custom ones (Survival: 711 custom items missing on Bedrock) and vanilla
  font definitions were merged as custom fonts. Such folders, temporary build
  folders and the stale self-hosted `pack.zip` inside ItemsAdder's working pack
  are no longer sources; a renamed ItemsAdder output (for example
  `generated_1.21.x.zip`) is used when `generated.zip` is absent.
- **Atlas sprite renames.** ItemsAdder's generated packs reference sprites such as
  `ia:2015`, which an atlas definition maps to the real texture. Twilight now
  resolves `single` and `directory` atlas sources like Java.
- **Protected packs.** Pack protection corrupts PNG chunk checksums and the zlib
  checksum, which Java ignores. Textures are now decoded leniently when the strict
  decoder fails (all colour types, bit depths, palettes, transparency, Adam7).
- **Java's own font sheets.** Fonts such as CustomNameplates' vertically shifted
  text reuse `ascii.png` and the unicode pages. Each character was copied as an
  untinted image alias, overflowing Bedrock's private-use pages (more than 50,000
  requests) and failing strict builds. Such characters are now ordinary text.
- **Off-screen and transparent spacing images.** Bitmaps placed thousands of units
  away or nearly transparent are spacing tricks; they become advances instead of
  baseline or oversized-image problems.
- **Model details.** Object-form texture entries (`{"sprite": ...}`) are read,
  custom selectors may show plain vanilla models (a barrier as a menu button), and
  faces whose texture variable no model defines use Java's missing texture.
  Player-skin heads, drawn by Java's special renderer, are left to Geyser's native
  rendering and noted.
- **Content Java rejects as well.** Malformed font files, unreadable TrueType
  fonts and sound files that exist in no pack are reported as notices in
  `build-report.json`. Bedrock then matches what Java players get, so they no
  longer stop a strict build; real conversion problems still do.

Measured counts per category are part of the published measurements; server
paths and content names are omitted.

## Remaining limits

- Item names and lore are not rewritten. Geyser hashes item data for inventory
  clicks and sends complete items back in creative mode, so changing them could
  alter the Java item; named-font images in lore therefore still show their raw
  characters on Bedrock.
- A glyph directly after ordinary text without a space is one unit right of Java;
  Bedrock cannot move the pen back. The same applies to overlapping layers such
  as text drawn over a nameplate background with a negative shift.
- Bitmap glyphs drawn from high-resolution textures are reduced to GUI units on
  Bedrock (Java samples them at screen resolution), so small lettering inside rank
  images is less sharp.
- Bold text and Bedrock's touch layout were not measured live on the new surfaces.

## Build and environment

- 138 tests in 25 suites passed locally (Java 25), including Java/Bedrock rendering
  models for left-aligned and centred lines, real protocol packets for every
  surface, lenient PNG decoding against an independent encoder, atlas renames and
  the discovery rules above.
- Java 26.2 client and Paper 26.2 server, Bedrock 1.26.5203.0, Geyser 2.11.3 build
  1247, both clients at two screen pixels per GUI unit.
- Tested fixture pack SHA-256 `651afb1ceb18e0c589da8e0425eb12e307a33defe9b7734b714b14d4cb007936`
  (1,177 entries); a repeated build was byte-identical, and a strict build with an
  injected defect was rejected while the deployed pack stayed unchanged.
- Test players stayed alive (health 20, death counters 0). The Bedrock content log
  showed no UI errors.

---

## Türkçe

### Her Bedrock yüzeyinde Java yazısı ve üretim derlemeleri - 4 Ekim 2026

Twilight geliştirme derlemesi 6, Java font yerleşimini sandık başlıklarından sunucuların özel font içeriği çizdiği
diğer yerlere genişletir: sohbet, aksiyon çubuğu, başlıklar ve alt başlıklar, boss çubukları, skor
tabloları, varlık adları ve yazı görüntüleri (hologramlar, ad etiketleri) ile diğer konteynerlerin
başlıkları. Aynı sürüm, dört başka gerçek sunucunun bağımsız içeriğinin ortaya çıkardığı birkaç dönüşüm
hatasını düzeltir. Aşağıdaki tüm denetimler yalıtılmış kopyalarda çalıştı; üretim sunucuları yalnızca
okundu.

#### Yazı yüzeyleri

Bu sürümden önce Bedrock oyuncuları için yalnızca sandık başlıkları yeniden yazılıyordu. Adlandırılmış
fontlar ve Java'nın yeniden eşlediği karakterler Bedrock'a değişmeden gidiyordu; bu yüzden bir CraftEngine
rütbe görseli (adlandırılmış rütbe fontundaki `七`) sohbette CJK harfi `七` olarak görünüyor, aksiyon
çubuğundaki bir ItemsAdder HUD'u kaydırma karakterleri için kutular gösteriyordu. Artık desteklenen her
paket, paket için zaten üretilmiş Java ölçüleriyle yerleştiriliyor:

| Yüzey | Paketler | Satır modeli |
| --- | --- | --- |
| Sohbet | sistem, oyuncu (imzalı içerik, gönderen, hedef), gizlenmiş | sola hizalı |
| Aksiyon çubuğu | aksiyon çubuğu yazısı, kaplama sistem sohbeti | ortalı |
| Başlıklar | başlık, alt başlık | ortalı |
| Boss çubukları | boss çubuğu başlığı | ortalı |
| Skor tabloları | hedef başlığı, takım görünen adı/öneki/soneki, skor görünen adları | ortalı başlık, sola hizalı satırlar |
| Varlıklar | özel adlar, yazı görüntüsü yazısı (çok satırlı) | satır başına ortalı |
| Konteynerler | huni, fırın, fırlatıcı ve diğer sandık dışı ekranların başlıkları | sola hizalı |

Sola hizalı satırlar ilk karakterlerinin gerektirdiği yerden başlar; böylece her görsel komşularına Java'daki
uzaklığını korur. Ortalı satırlar baştaki veya sondaki görünmez aralık glifleriyle doldurulur; böylece
Bedrock onları Java'nın ortaladığı yerde ortalar; baştaki negatif bir kaydırma bu yüzden bir HUD görselini
Java'daki gibi tam sola taşır. Java tam sayı aritmetiğiyle `-genişlik / 2` noktasında ortalarken Bedrock
birebir ortalar; bu yüzden Java genişliği tek olan satırlar bir birim fazla dolgu alır; bunun için sıradan
karakterlerin genişlikleri vanilla istemcinin kendi font sayfalarından gelir. Sıradan boşluklar mümkün olan
her yerde boşluk kalır, böylece Bedrock sohbeti hâlâ onlardan kaydırır. Özel font karakteri olmayan yazı
Geyser'a aynı paket olarak ulaşır.

`ui.java-text-surfaces` (varsayılan `true`) yeni yüzeyleri denetler; `ui.java-text-layout` artık Java
konteyner yerleşimini gerektirmez.

##### Canlı ölçümler

Bağımsız bir düzenek ikinci bir sunucudan (SkyBlock) salt okunur kopyalandı: kod noktaları (`U+4E00`..)
bilerek ilk düzeneğin varsayılan font kaydırma karakteriyle çakışan CraftEngine adlandırılmış font
rütbeleri ve simgeleri ve ekran dışı aralık karakterleriyle bir CustomNameplates arka plan seti. Görseller
iki görüntüde de yazarın verdiği görsele (Java) ve Bedrock'a gerçekten sunulan glif hücresine karşı
maskelenmiş şablon eşleşmesiyle bulundu. Konumlar birim başına iki ekran pikseliyle arayüz birimidir;
farklar Bedrock eksi Java'dır.

| Senaryo | İçerik | Sonuç |
| --- | --- | --- |
| Aksiyon çubuğu | 3 × ItemsAdder `:offset_-8:`, rütbe, simge | 0, 0 |
| Yazılı aksiyon çubuğu | `Coins: 25 ` + simge + ` left` | 0 |
| Ad etiketi çubuğu | CustomNameplates sol, orta, sağ (39 birim, tek) | 0, 0, 0 |
| Boss çubuğu | rütbe, ` Boss `, simge | 0, 0 |
| Boss çubuğu, tek genişlik | rütbe, ` Boss! `, simge | 0, 0 |
| Sohbet | rütbe, ` Steve: merhaba `, simge (göreli) | 0, 0 |
| Yan panel | hedef başlığında rütbe, skor adında simge | ikisi de görünür |
| Başlık ve alt başlık | rütbe, simge | ikisi de görünür |

`ui.java-text-surfaces: false` ile aynı senaryolar Bedrock'ta her yüzeyde ham karakterleri gösterdi. Ad
etiketi çubuğunun ilk ölçümü Java'nın yarım birim solundaydı; bu, Java'nın tam sayı ortalamasını ortaya
koydu ve yukarıdaki düzeltme farkı kaldırdı. Sandık menüleri sürüm derlemesiyle yeniden ölçüldü: altı
gerçek Survival menüsü hâlâ Java ile eşleşiyor (başlık bandı ve pencere kayması 0, 0); huni, fırın ve
fırlatıcı panelleri vanilla Bedrock arayüzüyle aynı.

Ölçümler: [ölçümler](images/acceptance/2026-10-04-text-surfaces/measurements.json)
([karmalar](images/acceptance/2026-10-04-text-surfaces/sha256.json)). Görüntüler üçüncü taraf rütbe ve ad
etiketi görselleri içerdiğinden yayımlanmaz.

#### Beş gerçek sunucunun üretim derlemeleri

Yeni bir denetim, her sunucunun keşfedilen kaynakları için eklentinin o sunucuda yapacağı gibi tam derlemeyi
(eşyalar, yerleşimli fontlar, sesler, arayüz) çalıştırır. Aynı beş sunucu geliştirme derlemesi 5 ve geliştirme derlemesi 6 ile
derlendi:

| Sunucu | Özel eşya, derleme 5 | Özel eşya, derleme 6 | Sorunlar derleme 5 → derleme 6 |
| --- | --- | --- | --- |
| Survival | 125 / 130 | 837 / 841 | 152 → 75 |
| SkyBlock | 0 / 0 | 29 / 52 (21 kaplama kafası yerel bırakıldı) | 44 → 43 |
| Box PvP v2 | 772 / 781 | 805 / 811 | 11 → 1 |
| Box PvP (eski) | 532 / 843 | 539 / 845 | 313 → 301 |
| SMP Lifesteal | özel içerik yok | özel içerik yok | 0 → 0 |

Kalan Survival ve SkyBlock sorunları; `vanilla-override` kapalıyken (bir yapılandırma seçimi) paketlerin
değiştirdiği 113 vanilla ses olayı, hiçbir paketin sağlamadığı dört eşya referansı ve Bedrock'un font
atlasının taşıyamadığı 10.000 birimlik tam ekran bir kaplama görselidir. Eski Box PvP sunucusu, modelleri
artık bulunmayan eski bir ItemsAdder yapılandırmasındaki eşyaları listeliyor.

Bulunan ve düzeltilen hatalar:

- **Vanilla varlık kopyaları sunucu paketlerini gölgeliyordu.** ItemsAdder vanilla istemci varlıklarının
  kopyalarını `storage/cache/vanilla_assets/<sürüm>` altında tutar. Bunlar üretilen paketin üstünde paket
  olarak keşfediliyordu; bu yüzden vanilla eşya tanımları özel olanları gizliyor (Survival: Bedrock'ta 711
  özel eşya eksikti) ve vanilla font tanımları özel font olarak birleştiriliyordu. Bu klasörler, geçici
  derleme klasörleri ve ItemsAdder'ın çalışma paketindeki eskimiş kendi barındırılan `pack.zip` artık kaynak
  değil; `generated.zip` yoksa yeniden adlandırılmış bir ItemsAdder çıktısı (örneğin
  `generated_1.21.x.zip`) kullanılır.
- **Atlas sprite yeniden adlandırmaları.** ItemsAdder'ın ürettiği paketler `ia:2015` gibi, bir atlas
  tanımının gerçek dokuya eşlediği sprite'lara başvurur. Twilight artık `single` ve `directory` atlas
  kaynaklarını Java gibi çözer.
- **Korumalı paketler.** Paket koruması, Java'nın yok saydığı PNG parça sağlama toplamlarını ve zlib
  sağlama toplamını bozar. Dokular artık katı çözücü başarısız olduğunda esnek biçimde çözülür (tüm renk
  türleri, bit derinlikleri, paletler, saydamlık, Adam7).
- **Java'nın kendi font sayfaları.** CustomNameplates'in dikey kaydırılmış yazısı gibi fontlar `ascii.png`
  ve unicode sayfalarını yeniden kullanır. Her karakter renklendirilmemiş bir görsel takma adı olarak
  kopyalanıyor, Bedrock'un özel kullanım sayfalarını taşırıyor (50.000'den fazla istek) ve katı derlemeleri
  başarısız kılıyordu. Bu karakterler artık sıradan yazıdır.
- **Ekran dışı ve saydam aralık görselleri.** Binlerce birim uzağa konan veya neredeyse saydam bitmap'ler
  aralık hileleridir; taban çizgisi veya aşırı büyük görsel sorunu yerine ilerlemeye dönüşürler.
- **Model ayrıntıları.** Nesne biçimli doku girdileri (`{"sprite": ...}`) okunur, özel seçiciler düz
  vanilla modeller gösterebilir (menü düğmesi olarak bir bariyer) ve doku değişkenini hiçbir modelin
  tanımlamadığı yüzler Java'nın eksik dokusunu kullanır. Java'nın özel çizicisiyle çizilen oyuncu kaplaması
  kafaları Geyser'ın yerel çizimine bırakılır ve not edilir.
- **Java'nın da reddettiği içerik.** Bozuk font dosyaları, okunamayan TrueType fontlar ve hiçbir pakette
  bulunmayan ses dosyaları `build-report.json` içinde bildirim olarak raporlanır. Bedrock böylece Java
  oyuncularının aldığıyla eşleşir; bu yüzden artık katı derlemeyi durdurmazlar; gerçek dönüşüm sorunları
  hâlâ durdurur.

Kategori başına ölçülen sayılar yayımlanan ölçümlerin parçasıdır; sunucu yolları ve içerik adları
çıkarılmıştır.

#### Kalan sınırlar

- Eşya adları ve açıklamaları yeniden yazılmaz. Geyser envanter tıklamaları için eşya verisinin karmasını
  alır ve yaratıcı modda tam eşyaları geri gönderir; bu yüzden onları değiştirmek Java eşyasını
  değiştirebilir; açıklamalardaki adlandırılmış font görselleri bu nedenle Bedrock'ta hâlâ ham
  karakterlerini gösterir.
- Sıradan yazının hemen ardından boşluksuz gelen bir glif Java'nın bir birim sağındadır; Bedrock kalemi geri
  alamaz. Aynısı negatif kaydırmayla bir ad etiketi arka planının üzerine çizilen yazı gibi üst üste binen
  katmanlar için de geçerlidir. (geliştirme derlemesi 8 ile katmanlar çözüldü.)
- Yüksek çözünürlüklü dokulardan çizilen bitmap glifler Bedrock'ta arayüz birimlerine indirgenir (Java
  onları ekran çözünürlüğünde örnekler); bu yüzden rütbe görsellerinin içindeki küçük yazılar daha az
  keskindir.
- Kalın yazı ve Bedrock'un dokunmatik yerleşimi yeni yüzeylerde canlı ölçülmedi.

#### Derleme ve ortam

- Sola hizalı ve ortalı satırlar için Java/Bedrock çizim modelleri, her yüzey için gerçek protokol
  paketleri, bağımsız bir kodlayıcıya karşı esnek PNG çözme, atlas yeniden adlandırmaları ve yukarıdaki
  keşif kuralları dahil 25 paketteki 138 test yerelde geçti (Java 25).
- Java 26.2 istemcisi ve Paper 26.2 sunucusu, Bedrock 1.26.5203.0, Geyser 2.11.3 derleme 1247; iki istemci
  de arayüz birimi başına iki ekran pikseli.
- Test edilen düzenek paketi SHA-256 `651afb1ceb18e0c589da8e0425eb12e307a33defe9b7734b714b14d4cb007936`
  (1.177 girdi); tekrarlanan bir derleme bayt bayt aynıydı ve kasıtlı bir hata eklenmiş katı derleme
  reddedilirken dağıtılmış paket değişmeden kaldı.
- Test oyuncuları hayatta kaldı (can 20, ölüm sayaçları 0). Bedrock içerik günlüğü hiçbir arayüz hatası
  göstermedi.
