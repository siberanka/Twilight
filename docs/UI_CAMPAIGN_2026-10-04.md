# UI campaign, provider coverage and custom biomes - 4 October 2026

> Türkçe: [aşağıda](#türkçe)

Twilight development build 7 was checked against every resolvable menu of two real servers,
an original-art style suite that reproduces the menu techniques found there, complete
builds of six real servers and a live custom-biome scene. All checks ran on isolated
copies; production servers were only read. Every screenshot in this repository was
captured on 4 October with the release build; older screenshots were retired.

## Real menus

`ui-campaign.py` collects menu titles from the server's own plugin configurations
(DeluxeMenus and every plugin YAML with a title and size), resolves them like the
running plugins would (legacy, hex and MiniMessage colours, ItemsAdder `:name:` and
`%img_name%` images and `:offset_N:` shifts from the server's own caches,
`{player}`), installs the pack the server delivers to Java players in the Java
client and Twilight's complete build of the same server in Geyser, then opens each
title on both clients. Titles with unresolved placeholders are skipped. Positions are
measured in GUI units on the slot grid (whole window) and on the title band.

| Server | Menus | Font-image titles | Text titles | Window offset 0 | Title band offset 0 |
| --- | --- | --- | --- | --- | --- |
| Survival (CraftEngine, ItemsAdder, CustomNameplates, BetterModel) | 88 | 40 | 48 | 88 | 82 |
| Box PvP (ItemsAdder, ModelEngine) | 16 | 0 | 16 | 16 | 15 |

The remaining title-band differences are Bedrock's own text rendering, not image
placement: Bedrock's font draws some characters with other widths (Turkish `ı`, `ğ`,
bold text, one or two units over a line), and a title containing a character
Bedrock's default font lacks (`❘`) is drawn entirely in Bedrock's smaller unicode
font. One font-image title measured a three-unit vertical band difference in one run
and none in a repeat run; it has semi-transparent art over the chest border.

The campaign found and fixed three defects in this release:

| Defect | Before | After |
| --- | --- | --- |
| Titles without a colour code: Java multiplies glyph images by the default title colour `0x404040`, Bedrock showed them undarkened | mean art difference 78.6 and 74.9 (two menus) | 16.1 and 12.9; brightest art pixel 64 on both clients |
| Bold titles: Bedrock widens bold spacers, so the origin padding moved the line | 4 units right | 0 |
| Pack translations: Java shows the packs' strings (names, hidden inventory label) | Bedrock showed vanilla strings | merged pack strings for every locale |

The real menus use third-party GUI art, so they are published as measurements only:
[Survival](images/acceptance/2026-10-04-ui-campaign/survival/measurements.json),
[Box PvP](images/acceptance/2026-10-04-ui-campaign/boxpvp/measurements.json).

## Original-art style suite

`style-suite.py` draws its own art and builds a resource pack with the techniques the
real servers use; nothing is copied from a server, so all captures are published.

| Style | Technique | Result |
| --- | --- | --- |
| Header, no colour | 76-unit header above the chest after an `-8` space, title without a colour | exact; darkened like Java (art difference 0.2) |
| Header, white | same with `&f` | exact (0.2) |
| Icon row | icons separated by `+4` and `-1` spaces, coloured text | exact |
| Negative-height shifts | eight Jobs-style negative-height bitmaps, then the header | exact |
| Hex text | `&#FFAA00` and legacy colours | exact |
| Bold text | `&b&l` title | exact |
| Layered | banner moved back over the header with a `-121` space, then bold text | not reproduced in development build 7; exact since development build 8 (one label per layer) |

The captures of this run were replaced by the development build 8 captures of the same
suite, which also covers stacked layers; see
[stacked images, nameplates and exact biomes](LAYERS_BIOMES_2026-10-04.md).
The Java client runs in English and the Bedrock client in Turkish, hence
"Inventory" and "Envanter".

### Darkened copies

Bedrock never tints resource-pack glyphs with the text colour: a probe showed
private-use pages untinted in every colour, and non-private pages tinted but always
drawn at half size, whatever their resolution, so they cannot carry menu art. The
pack therefore receives copies of the laid-out glyphs multiplied by `0x404040` on
free private-use pages, and container titles without a colour use them. Other
surfaces default to white and keep the originals. On Survival this adds nine pages
(21 instead of 12, about 330 MiB of uncompressed atlas memory, 1.3 MB of pack size);
`ui.java-glyph-tint: false` removes them. Titles with a colour other than white or
the default are not darkened (none of the 104 real titles uses one before an image).

## Provider coverage and production builds

Discovery now treats each provider's generated pack as what Java players receive:
CraftEngine's `generated/resource_pack.zip` (which also merges CustomNameplates and
BetterModel output), ItemsAdder's output and Nexo/Oraxen packs outrank the
providers' working folders. A provider whose plugin is not installed (a leftover
ItemsAdder folder on a CraftEngine server) or whose settings do not send its pack
while another provider sends a generated pack only fills gaps. CustomNameplates and
BetterHUD packs are discovered; Nexo's vanilla asset cache is ignored.

| Server (sources) | build 6 | build 7 |
| --- | --- | --- |
| Survival (CraftEngine, ItemsAdder folder, CustomNameplates, BetterModel, ModelEngine, world datapack) | 837 / 841 | 1,905 / 1,932 |
| SkyBlock (CraftEngine, ItemsAdder folder, CustomNameplates, BetterModel) | 29 / 52 | 29 / 52 (21 skin-rendered heads noted) |
| Box PvP v2 (ItemsAdder, BetterModel, ModelEngine) | 805 / 811 | 884 / 902 |
| Box PvP (ItemsAdder, ModelEngine) | 539 / 845 | 612 / 928 |
| Kaynak (ItemsAdder, Nexo files, CustomNameplates, ModelEngine) | - | 2,713 / 3,014 |
| SMP Lifesteal (datapack only) | 0 / 0 | 0 / 0 |

Converted / candidate custom items per complete build. The larger candidate counts
come from the delivered packs that were previously outranked by stale folders.
Remaining failures are missing textures and models in the servers' own packs (as on
Java) and the documented limits of 3D provider models. MMO plugins (MMOItems,
MythicMobs items) use the item models of these packs and are converted with them;
MythicMobs entities need a runtime entity bridge (see the
[compatibility contract](COMPATIBILITY.md)). Nexo was tested from files only: no
test server runs the Nexo plugin.

Survival's packs define 23,379 strings in 122 locales. Twilight merges them like
Java and Geyser renders Java text with them; Bedrock's own inventory label follows
the pack (a blank label stays blank).

## Custom biomes

Geyser shows every biome it does not know as the dimension's fallback (ocean in the
overworld), so datapack biomes (Terralith, Incendium) and plugin biomes
(RealisticSeasons) lost their colours on Bedrock. Twilight now registers each custom
biome for Bedrock as the vanilla biome with the closest grass, foliage, water and fog
colours and precipitation, computed from the biome's Java definition and the vanilla
colour maps (`world.bedrock-biome-matching`).

`biome-check.py` installed a datapack biome `twilighttest:blossom_vale` (the cherry
grove definition with its own grass and foliage colours) in the test world, painted a
grass floor with `/fillbiome` and captured both clients. Twilight logged
`blossom_vale=minecraft:cherry_grove`.

| Client | Mean floor colour (RGB) |
| --- | --- |
| Java | 112, 129, 65 |
| Bedrock with the mapping | 102, 123, 54 |
| Bedrock without it (ocean fallback) | 79, 103, 62 |

The mapping uses vanilla Bedrock biomes, so exact custom colours were not reproduced
in this run. Since development build 8 custom biomes get free Bedrock biome slots with their
exact colours; the captures were replaced by the
[development build 8 biome captures](LAYERS_BIOMES_2026-10-04.md#custom-biomes).

## Remaining differences

- Layers moved back over an earlier image (a negative space after an image) were not
  reproduced in development build 7; development build 8 draws them with one label per layer.
- Bedrock draws text with its own font: some character widths differ, and a
  character missing from its default font switches the whole title to the unicode
  font.
- Bedrock adds its close button and inventory label in the client's language.
- Item names and lore are not rewritten; high-resolution glyphs are sampled to GUI
  units.

---

## Türkçe

### Arayüz kampanyası, sağlayıcı kapsamı ve özel biyomlar - 4 Ekim 2026

Twilight geliştirme derlemesi 7; iki gerçek sunucunun çözülebilen her menüsüne, orada bulunan menü tekniklerini
yeniden üreten özgün görselli bir stil takımına, altı gerçek sunucunun tam derlemelerine ve canlı bir özel
biyom sahnesine karşı denetlendi. Tüm denetimler yalıtılmış kopyalarda çalıştı; üretim sunucuları yalnızca
okundu. Bu depodaki her ekran görüntüsü 4 Ekim'de sürüm derlemesiyle çekildi; eski görüntüler kaldırıldı.

#### Gerçek menüler

`ui-campaign.py` menü başlıklarını sunucunun kendi eklenti yapılandırmalarından toplar (DeluxeMenus ve
başlığı ve boyutu olan her eklenti YAML'ı), bunları çalışan eklentilerin yapacağı gibi çözer (eski, hex ve
MiniMessage renkleri, sunucunun kendi önbelleklerinden ItemsAdder `:name:` ve `%img_name%` görselleri ile
`:offset_N:` kaydırmaları, `{player}`), sunucunun Java oyuncularına gönderdiği paketi Java istemcisine,
Twilight'ın aynı sunucu için yaptığı tam derlemeyi de Geyser'a kurar, ardından her başlığı iki istemcide
açar. Çözülmemiş yer tutucuları olan başlıklar atlanır. Konumlar yuva ızgarasında (tüm pencere) ve başlık
bandında arayüz birimi olarak ölçülür.

| Sunucu | Menü | Font görselli başlık | Yazı başlığı | Pencere kayması 0 | Başlık bandı kayması 0 |
| --- | --- | --- | --- | --- | --- |
| Survival (CraftEngine, ItemsAdder, CustomNameplates, BetterModel) | 88 | 40 | 48 | 88 | 82 |
| Box PvP (ItemsAdder, ModelEngine) | 16 | 0 | 16 | 16 | 15 |

Kalan başlık bandı farkları görsel yerleşiminden değil, Bedrock'un kendi yazı çiziminden kaynaklanır:
Bedrock'un fontu bazı karakterleri farklı genişliklerle çizer (Türkçe `ı`, `ğ`, kalın yazı; bir satırda bir
iki birim) ve Bedrock'un varsayılan fontunda olmayan bir karakter (`❘`) içeren başlık tamamen Bedrock'un
daha küçük unicode fontuyla çizilir. Font görselli bir başlık bir çalıştırmada üç birimlik dikey bant farkı
ölçtü, tekrarında hiç ölçmedi; sandık kenarının üzerinde yarı saydam görselleri var.

Kampanya bu sürümde üç hata buldu ve düzeltti:

| Hata | Önce | Sonra |
| --- | --- | --- |
| Renk kodu olmayan başlıklar: Java glif görsellerini varsayılan başlık rengi `0x404040` ile çarpar, Bedrock onları koyulaştırmadan gösteriyordu | ortalama görsel farkı 78,6 ve 74,9 (iki menü) | 16,1 ve 12,9; en parlak görsel pikseli iki istemcide de 64 |
| Kalın başlıklar: Bedrock kalın aralık gliflerini genişletir, bu yüzden başlangıç dolgusu satırı kaydırıyordu | 4 birim sağda | 0 |
| Paket çevirileri: Java paketlerin dizelerini gösterir (adlar, gizli envanter etiketi) | Bedrock vanilla dizeleri gösteriyordu | her dil için birleştirilmiş paket dizeleri |

Gerçek menüler üçüncü taraf arayüz görselleri kullandığından yalnızca ölçüm olarak yayımlanır:
[Survival](images/acceptance/2026-10-04-ui-campaign/survival/measurements.json),
[Box PvP](images/acceptance/2026-10-04-ui-campaign/boxpvp/measurements.json).

#### Özgün görselli stil takımı

`style-suite.py` kendi görsellerini çizer ve gerçek sunucuların kullandığı tekniklerle bir kaynak paketi
derler; sunucudan hiçbir şey kopyalanmaz, bu yüzden tüm görüntüler yayımlanır.

| Stil | Teknik | Sonuç |
| --- | --- | --- |
| Başlık görseli, renksiz | `-8` boşluktan sonra sandığın üstünde 76 birimlik başlık görseli, renksiz başlık | birebir; Java gibi koyulaşmış (görsel farkı 0,2) |
| Başlık görseli, beyaz | aynısı `&f` ile | birebir (0,2) |
| Simge satırı | `+4` ve `-1` boşluklarla ayrılmış simgeler, renkli yazı | birebir |
| Negatif yükseklik kaydırmaları | Jobs tarzı sekiz negatif yükseklikli bitmap, ardından başlık görseli | birebir |
| Hex yazı | `&#FFAA00` ve eski renkler | birebir |
| Kalın yazı | `&b&l` başlık | birebir |
| Katmanlı | `-121` boşlukla başlık görselinin üzerine geri alınan afiş, ardından kalın yazı | geliştirme derlemesi 7'de yeniden üretilmedi; geliştirme derlemesi 8'den beri birebir (katman başına bir etiket) |

Bu çalıştırmanın görüntüleri, üst üste katmanları da kapsayan aynı takımın geliştirme derlemesi 8 görüntüleriyle
değiştirildi; [üst üste görseller, ad etiketleri ve birebir biyomlar](LAYERS_BIOMES_2026-10-04.md)
belgesine bakın. Java istemcisi İngilizce, Bedrock istemcisi Türkçe çalışıyor; "Inventory" ve "Envanter"
farkı bundandır.

##### Koyulaştırılmış kopyalar

Bedrock kaynak paketi gliflerini asla yazı rengiyle renklendirmez: bir deneme özel kullanım sayfalarının
her renkte renklendirilmediğini, özel olmayan sayfaların ise renklendirildiğini ama çözünürlükleri ne olursa
olsun hep yarım boyutta çizildiğini gösterdi; bu yüzden menü görseli taşıyamazlar. Bu nedenle paket,
yerleştirilmiş gliflerin `0x404040` ile çarpılmış kopyalarını boş özel kullanım sayfalarında alır ve renksiz
konteyner başlıkları bunları kullanır. Diğer yüzeyler varsayılan olarak beyazdır ve özgünleri korur.
Survival'da bu dokuz sayfa ekler (12 yerine 21, yaklaşık 330 MiB sıkıştırılmamış atlas belleği, 1,3 MB
paket boyutu); `ui.java-glyph-tint: false` bunları kaldırır. Beyaz veya varsayılan dışındaki bir renkteki
başlıklar koyulaştırılmaz (104 gerçek başlığın hiçbiri bir görselden önce böyle bir renk kullanmıyor).

#### Sağlayıcı kapsamı ve üretim derlemeleri

Keşif artık her sağlayıcının ürettiği paketi Java oyuncularının aldığı paket olarak kabul ediyor:
CraftEngine'in `generated/resource_pack.zip` dosyası (CustomNameplates ve BetterModel çıktısını da
birleştirir), ItemsAdder'ın çıktısı ve Nexo/Oraxen paketleri sağlayıcıların çalışma klasörlerinin önüne
geçer. Eklentisi kurulu olmayan bir sağlayıcı (CraftEngine sunucusunda kalmış bir ItemsAdder klasörü) veya
başka bir sağlayıcı üretilmiş bir paket gönderirken ayarları kendi paketini göndermeyen bir sağlayıcı
yalnızca boşlukları doldurur. CustomNameplates ve BetterHUD paketleri keşfedilir; Nexo'nun vanilla varlık
önbelleği yok sayılır.

| Sunucu (kaynaklar) | derleme 6 | derleme 7 |
| --- | --- | --- |
| Survival (CraftEngine, ItemsAdder klasörü, CustomNameplates, BetterModel, ModelEngine, dünya datapack'i) | 837 / 841 | 1.905 / 1.932 |
| SkyBlock (CraftEngine, ItemsAdder klasörü, CustomNameplates, BetterModel) | 29 / 52 | 29 / 52 (21 kaplamayla çizilen kafa not edildi) |
| Box PvP v2 (ItemsAdder, BetterModel, ModelEngine) | 805 / 811 | 884 / 902 |
| Box PvP (ItemsAdder, ModelEngine) | 539 / 845 | 612 / 928 |
| Kaynak (ItemsAdder, Nexo dosyaları, CustomNameplates, ModelEngine) | - | 2.713 / 3.014 |
| SMP Lifesteal (yalnızca datapack) | 0 / 0 | 0 / 0 |

Tam derleme başına dönüştürülen / aday özel eşyalar. Daha büyük aday sayıları, önceden eskimiş klasörlerin
gerisinde kalan teslim edilmiş paketlerden gelir. Kalan başarısızlıklar sunucuların kendi paketlerindeki
eksik dokular ve modeller (Java'daki gibi) ve 3B sağlayıcı modellerinin belgelenmiş sınırlarıdır. MMO
eklentileri (MMOItems, MythicMobs eşyaları) bu paketlerin eşya modellerini kullanır ve onlarla birlikte
dönüştürülür; MythicMobs varlıkları bir çalışma zamanı varlık köprüsü gerektirir
([uyumluluk sözleşmesine](COMPATIBILITY.md) bakın). Nexo yalnızca dosyalardan test edildi: hiçbir test
sunucusu Nexo eklentisini çalıştırmıyor.

Survival'ın paketleri 122 dilde 23.379 dize tanımlar. Twilight bunları Java gibi birleştirir ve Geyser Java
yazısını onlarla çizer; Bedrock'un kendi envanter etiketi paketi izler (boş bir etiket boş kalır).

#### Özel biyomlar

Geyser bilmediği her biyomu boyutun yedeği olarak gösterir (overworld'de okyanus); bu yüzden datapack
biyomları (Terralith, Incendium) ve eklenti biyomları (RealisticSeasons) Bedrock'ta renklerini
kaybediyordu. Twilight artık her özel biyomu Bedrock için; biyomun Java tanımından ve vanilla renk
haritalarından hesaplanan en yakın çimen, yaprak, su ve sis renkleri ile yağışa sahip vanilla biyom olarak
kaydediyor (`world.bedrock-biome-matching`).

`biome-check.py` test dünyasına `twilighttest:blossom_vale` adlı bir datapack biyomu (kendi çimen ve
yaprak renklerine sahip kiraz bahçesi tanımı) kurdu, `/fillbiome` ile bir çimen zemin boyadı ve iki
istemciyi çekti. Twilight `blossom_vale=minecraft:cherry_grove` kaydını düştü.

| İstemci | Ortalama zemin rengi (RGB) |
| --- | --- |
| Java | 112, 129, 65 |
| Eşlemeli Bedrock | 102, 123, 54 |
| Eşlemesiz Bedrock (okyanus yedeği) | 79, 103, 62 |

Eşleme vanilla Bedrock biyomlarını kullandığından bu çalıştırmada birebir özel renkler yeniden üretilmedi.
Geliştirme derlemesi 8'den beri özel biyomlar birebir renkleriyle boş Bedrock biyom yuvaları alıyor; görüntüler
[geliştirme derlemesi 8 biyom görüntüleriyle](LAYERS_BIOMES_2026-10-04.md#custom-biomes) değiştirildi.

#### Kalan farklar

- Önceki bir görselin üzerine geri alınan katmanlar (bir görselden sonra negatif boşluk) geliştirme derlemesi 7'de
  yeniden üretilmedi; geliştirme derlemesi 8 bunları katman başına bir etiketle çizer.
- Bedrock yazıyı kendi fontuyla çizer: bazı karakter genişlikleri farklıdır ve varsayılan fontunda olmayan
  bir karakter tüm başlığı unicode fonta geçirir.
- Bedrock kapatma düğmesini ve envanter etiketini istemcinin dilinde ekler.
- Eşya adları ve açıklamaları yeniden yazılmaz; yüksek çözünürlüklü glifler arayüz birimlerine örneklenir.
