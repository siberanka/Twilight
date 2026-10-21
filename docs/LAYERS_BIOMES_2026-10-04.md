# Stacked images, nameplates and exact biomes - 4 October 2026

> Türkçe: [aşağıda](#türkçe)

Twilight development build 8 closes the gaps left by the [UI campaign](UI_CAMPAIGN_2026-10-04.md):
images and text that Java draws back over earlier images, nameplate plugins, custom
biome colours and seasonal biomes. Every capture below is original art or a vanilla
scene, taken on the isolated test server (Paper 26.2, Geyser 2.11.3, Java 26.2 and
Bedrock 1.26.5203.0 clients at GUI scale 2). Third-party content (CustomNameplates,
RealisticSeasons, server menus) is reported as measurements only.

## Stacked images in menus

Java moves the pen back with negative spaces and draws later images over earlier
ones. A Bedrock label only moves forward, so Twilight splits such a title into up to
four layers, one label each, at runtime for every packet: each layer keeps Java's
positions, the lower layers are drawn first and the top layer last. Text shifted up
or down by a font (CustomNameplates' shift fonts, ascents) gets one-unit line steps;
text Java draws without a shadow goes to its own layer. Nothing is precomputed per
title, so every plugin and every placeholder value works.

| Style | Technique | Window / title offset | Art difference |
| --- | --- | --- | --- |
| Stacked four | panel, banner over it, badge over the banner, bold text over the badge | 0, 0 / 0, 0 | 0.3 |
| Highlight row | translucent bar drawn over the first slot row, then icons and text moved back over the header | 0, 0 / 0, 0 | 0.2 |
| Layered | banner moved back over the header with a `-121` space, then bold text | 0, 0 / 0, 0 | 0.2 |
| Header, no colour | header after an `-8` space, title without a colour (Java darkens it) | 0, 0 / 0, 0 | 0.2 |
| Header, white | the same with `&f` | 0, 0 / 0, 0 | 0.2 |
| Negative-height shifts | eight Jobs-style negative-height bitmaps, then the header | 0, 0 / 0, 0 | 0.4 |
| Icon row, hex text, bold text | icons with `+4`/`-1` spaces, `&#FFAA00`, `&b&l` | 0, 0 / 0, 0 | see note |

Offsets are in GUI units: `0, 0` means the art lands on Java's pixels. The art
difference is the mean absolute luminance difference inside the title panel (0-255).
Titles without a panel image show the world behind the menu there, which each client
dims differently (about 30); their text and icons are at the same positions.

<table>
  <tr><td><img src="images/acceptance/2026-10-04-layers/menus/stacked-four.gif" alt="Animation alternating the Java and Bedrock captures of the four-layer menu; nothing moves" width="100%"></td>
      <td><img src="images/acceptance/2026-10-04-layers/menus/highlight-row.gif" alt="Animation alternating Java and Bedrock: translucent highlight over the slots" width="100%"></td></tr>
</table>

The animations alternate the Java and the Bedrock capture of the same menu every
1.1 seconds; only the label changes. Side-by-side pairs:
[stacked four](images/acceptance/2026-10-04-layers/menus/stacked-four.png),
[highlight row](images/acceptance/2026-10-04-layers/menus/highlight-row.png),
[layered](images/acceptance/2026-10-04-layers/menus/layered-bold.png)
([animation](images/acceptance/2026-10-04-layers/menus/layered-bold.gif)),
[header without colour](images/acceptance/2026-10-04-layers/menus/header-uncoloured.png),
[white header](images/acceptance/2026-10-04-layers/menus/header-white.png),
[icon row](images/acceptance/2026-10-04-layers/menus/icon-row.png),
[negative-height shifts](images/acceptance/2026-10-04-layers/menus/negative-height.png),
[hex text](images/acceptance/2026-10-04-layers/menus/hex-text.png),
[bold text](images/acceptance/2026-10-04-layers/menus/bold-text.png);
[measurements](images/acceptance/2026-10-04-layers/measurements.json),
[hashes](images/acceptance/2026-10-04-layers/sha256.json).

Bedrock spaces label lines one unit apart for the vertical steps and centres each
line in that unit, which raised top-anchored labels (chest titles, boss bar names) by
4.5 units; the label offsets compensate it. A run before the fix measured the title
4 units too high; the runs above are exact.

## Boss bars and the action bar

The same layers serve boss bar names (CustomNameplates backgrounds) and the action
bar. Boss bars whose sprites the Java pack makes transparent stay hidden.

<img src="images/acceptance/2026-10-04-layers/hud.png" alt="Java and Bedrock: gold text over coin icons on the boss bar and bold text over icons on the action bar" width="100%">

The boss bar art occupies screen rows 6-19 on both clients; the action bar text is
72 units above the bottom edge on both.

## Nameplates

Nameplate plugins mount text displays on the player. Java never draws the name of a
living entity that carries passengers (armor stands follow their own rule), so only
the plugin's plate is visible; Bedrock drew the plain name as well. Twilight now
applies Java's rule to the Bedrock name of ridden players and mobs and restores it
when the last passenger leaves. Text displays with a transparent background were
drawn on Bedrock's dark name tag box; `ui.nametag-background` hides that box
(`auto`: when CustomNameplates' name tags are enabled).

<img src="images/acceptance/2026-10-04-layers/nameplate.png" alt="A villager carrying a text display with a badge image and gold text, on Java and Bedrock; the villager's own name is hidden on both" width="100%">

With the real CustomNameplates 3.0.42 and its Survival configuration (measurements
only), Bedrock now shows the plate without the second plain name and without the two
dark boxes behind the image and the text part; the boss bar backgrounds, icons and
shifted text matched Java in an earlier run of the same session.

## Custom biomes

Bedrock refuses biome definitions it does not know, but it takes the look of a
vanilla biome from the resource pack. Twenty-five Bedrock biomes exist only for old
worlds (legacy hills, edges and mutated variants) and no Java biome maps to them. The
pack redefines them with the grass, foliage, water, underwater fog, fog and sky colours
of the server's custom biomes, and Geyser sends their climate (temperature, downfall,
rain or snow). Custom biomes come from the datapacks and from the server registry, so
plugin biomes (RealisticSeasons) are included. With more custom looks than slots, the
looks of the current season go first, then the ones the vanilla biomes approximate
worst; every other custom biome shows as the closest vanilla biome or slot.

| Floor of a datapack biome (mean RGB) | Java | Bedrock |
| --- | --- | --- |
| Development build 7 (closest vanilla biome) | 112, 129, 65 | 102, 123, 54 |
| Development build 8 (exact slot) | 111, 128, 64 | 110, 127, 64 |

<table>
  <tr><td width="60%"><img src="images/acceptance/2026-10-04-layers/biome.png" alt="Grass of a custom datapack biome on Java and Bedrock in the same colour" width="100%"></td>
      <td><img src="images/acceptance/2026-10-04-layers/biome-live.gif" alt="Animation of a 5 by 5 grid of custom biomes changing colours on Bedrock after each fillbiome" width="100%"></td></tr>
</table>

Java changes biomes in place (`/fillbiome`, seasons plugins) with a packet Geyser
does not translate, so Bedrock kept the old biomes until the chunk reloaded. Twilight
now sends such chunks to the Bedrock player again through the normal chunk path; the
animation shows a grid of 25 custom biomes changing several times without a rejoin.
All 25 slots show their own grass and leaf colours ([grid](images/acceptance/2026-10-04-layers/slots.png));
at the borders Bedrock blends neighbouring biomes over a wider radius than Java.

RealisticSeasons 11.12.1 with the Survival configuration (measurements only)
registered 188 seasonal biomes. Twilight read them from the registry, asked
RealisticSeasons' API which ones the current season shows and gave those slots first;
the season change event rebuilds the pack. In winter, a forest floor measured 104,
118, 127 on Java and 104, 118, 128 on Bedrock (98, 115, 111 before the season was
taken into account). RealisticSeasons' own data and packets are not changed.

## Automatic builds on real servers

`ServerBuildAuditMain` ran complete builds of seven production servers read-only with
the default configuration. Content that Java itself tolerates is now converted the way
Java shows it and reported as a notice: textures that exist in no pack become Java's
missing texture, items whose model exists nowhere keep their base item, changed
vanilla sounds keep Bedrock's sound unless `vanilla-override` is enabled, and
screen-sized overlay glyphs are left out while text after them keeps its position.

| Server | Custom items | Glyphs | Problems | Notices |
| --- | --- | --- | --- | --- |
| Survival | 1,915 / 1,932 | 767 | 0 | 175 |
| SkyBlock | 31 / 52 | 31 | 0 | 64 |
| Boxpvp v2 | 884 / 902 | 622 | 0 | 18 |
| BoxPVP | 613 / 928 | 618 | 0 | 319 |
| Kaynak | 2,713 / 3,014 | 762 | 0 | 375 |
| SMP-Lifesteal, Auth | no custom content | 0 | 0 | 0 |

Every build passes strict publication. When a server's first strict build still finds
a problem and no pack is deployed yet, Twilight deploys a first pack without that
content instead of none; later builds stay strict.

## Research

- The [no nametag background](https://www.curseforge.com/minecraft-bedrock/texture-packs/no-nametag-background)
  pack by chromehaert showed that Bedrock's name tag box is drawn with the
  `name_tag` material, whose blending a pack can change. Twilight generates its own
  material file with the same idea; no file of that pack is distributed.
- [Mojang's bedrock-samples](https://github.com/Mojang/bedrock-samples) and the
  [client biome reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/clientbiomesreference/examples/components/client_biome_components)
  document the client biome and fog formats Twilight writes.
- [Chest GUI Generator](https://github.com/wlsgunzz/Chest-GUI-Generator) builds static
  chest overlays per title from a pack. Twilight lays titles out per packet instead,
  so titles with placeholders and stacked layers need no per-title setup.
- The name rules come from the Java 26.2 client's renderers: living entities hide
  their name while carrying passengers, armor stands keep it.

## Packet handling

The changes are made where Geyser translates Java packets for each Bedrock player
(Geyser's own translator registry), not with a separate packet library: Twilight sees
the exact text, entities and chunks Geyser is about to translate, changes nothing for
Java players and needs no extra dependency. This requires Geyser on the same server
as Twilight (Geyser-Spigot); a proxy-only Geyser receives the pack but not these
runtime layers.

## Remaining differences

- A text display riding a mob is drawn about 0.4 blocks lower on Bedrock (Geyser's
  mount offset); on players the plate sits where Java draws it.
- Bedrock's name tag box is hidden for every name tag or none; Java decides per text
  display.
- Hex colours in text are shown in the nearest of Bedrock's 28 text colours.
- Bedrock blends biome colours over a wider radius at biome borders.
- More custom looks than 25 slots share the closest slot or vanilla biome.

---

## Türkçe

### Üst üste görseller, ad etiketleri ve birebir biyomlar - 4 Ekim 2026

Twilight geliştirme derlemesi 8, [arayüz kampanyasının](UI_CAMPAIGN_2026-10-04.md) bıraktığı boşlukları kapatır:
Java'nın önceki görsellerin üzerine geri çizdiği görseller ve yazılar, ad etiketi eklentileri, özel biyom
renkleri ve mevsimsel biyomlar. Aşağıdaki her görüntü özgün görsel veya vanilla bir sahnedir ve yalıtılmış
test sunucusunda çekildi (Paper 26.2, Geyser 2.11.3, arayüz ölçeği 2'de Java 26.2 ve Bedrock 1.26.5203.0
istemcileri). Üçüncü taraf içerik (CustomNameplates, RealisticSeasons, sunucu menüleri) yalnızca ölçüm
olarak raporlanır. Görüntüler ve animasyonlar yukarıdaki İngilizce bölümdedir.

#### Menülerde üst üste görseller

Java negatif boşluklarla kalemi geri alır ve sonraki görselleri öncekilerin üzerine çizer. Bir Bedrock
etiketi yalnızca ileri gider; bu yüzden Twilight böyle bir başlığı çalışma zamanında her paket için en
fazla dört katmana, her biri bir etiket olacak şekilde böler: her katman Java'nın konumlarını korur, alt
katmanlar önce, üst katman en son çizilir. Bir fontla yukarı veya aşağı kaydırılan yazı (CustomNameplates
kaydırma fontları, ascent'ler) bir birimlik satır adımları alır; Java'nın gölgesiz çizdiği yazı kendi
katmanına gider. Başlık başına hiçbir şey önceden hesaplanmaz; bu yüzden her eklenti ve her yer tutucu
değeri çalışır.

| Stil | Teknik | Pencere / başlık kayması | Görsel farkı |
| --- | --- | --- | --- |
| Dört katman | panel, üzerinde afiş, afişin üzerinde rozet, rozetin üzerinde kalın yazı | 0, 0 / 0, 0 | 0,3 |
| Vurgu satırı | ilk yuva satırının üzerine çizilen yarı saydam çubuk, ardından başlık görselinin üzerine geri alınan simgeler ve yazı | 0, 0 / 0, 0 | 0,2 |
| Katmanlı | `-121` boşlukla başlık görselinin üzerine geri alınan afiş, ardından kalın yazı | 0, 0 / 0, 0 | 0,2 |
| Başlık görseli, renksiz | `-8` boşluktan sonra başlık görseli, renksiz başlık (Java onu koyulaştırır) | 0, 0 / 0, 0 | 0,2 |
| Başlık görseli, beyaz | aynısı `&f` ile | 0, 0 / 0, 0 | 0,2 |
| Negatif yükseklik kaydırmaları | Jobs tarzı sekiz negatif yükseklikli bitmap, ardından başlık görseli | 0, 0 / 0, 0 | 0,4 |
| Simge satırı, hex yazı, kalın yazı | `+4`/`-1` boşluklu simgeler, `&#FFAA00`, `&b&l` | 0, 0 / 0, 0 | nota bakın |

Kaymalar arayüz birimidir: `0, 0` görselin Java'nın piksellerine oturduğu anlamına gelir. Görsel farkı,
başlık paneli içindeki ortalama mutlak parlaklık farkıdır (0-255). Panel görseli olmayan başlıklar orada
menünün arkasındaki dünyayı gösterir ve her istemci bunu farklı karartır (yaklaşık 30); yazıları ve
simgeleri aynı konumdadır.

Animasyonlar aynı menünün Java ve Bedrock görüntüsünü 1,1 saniyede bir değiştirir; yalnızca etiket değişir.
Yan yana çiftler ve [ölçümler](images/acceptance/2026-10-04-layers/measurements.json),
[karmalar](images/acceptance/2026-10-04-layers/sha256.json) yukarıdaki İngilizce bölümde bağlantılıdır.

Bedrock dikey adımlar için etiket satırlarını bir birim aralıkla dizer ve her satırı o birim içinde
ortalar; bu, üste çapalı etiketleri (sandık başlıkları, boss çubuğu adları) 4,5 birim yukarı kaldırıyordu;
etiket kaymaları bunu dengeler. Düzeltmeden önceki bir çalıştırma başlığı 4 birim fazla yüksek ölçtü;
yukarıdaki çalıştırmalar birebirdir.

#### Boss çubukları ve aksiyon çubuğu

Aynı katmanlar boss çubuğu adlarına (CustomNameplates arka planları) ve aksiyon çubuğuna da hizmet eder.
Sprite'larını Java paketinin saydam yaptığı boss çubukları gizli kalır.

Boss çubuğu görseli iki istemcide de ekranın 6-19. satırlarını kaplar; aksiyon çubuğu yazısı iki istemcide
de alt kenarın 72 birim üzerindedir.

#### Ad etiketleri

Ad etiketi eklentileri oyuncuya yazı görüntüleri bindirir. Java yolcu taşıyan canlı bir varlığın adını asla
çizmez (zırh askılıkları kendi kurallarını izler); bu yüzden yalnızca eklentinin etiketi görünür; Bedrock
düz adı da çiziyordu. Twilight artık Java'nın kuralını binilen oyuncuların ve mobların Bedrock adına
uyguluyor ve son yolcu indiğinde geri getiriyor. Saydam arka planlı yazı görüntüleri Bedrock'un koyu ad
etiketi kutusunun üzerinde çiziliyordu; `ui.nametag-background` o kutuyu gizler (`auto`: CustomNameplates'in
ad etiketleri açıkken).

Gerçek CustomNameplates 3.0.42 ve Survival yapılandırmasıyla (yalnızca ölçüm), Bedrock artık etiketi ikinci
düz ad olmadan ve görselle yazı kısmının arkasındaki iki koyu kutu olmadan gösteriyor; boss çubuğu arka
planları, simgeler ve kaydırılmış yazı aynı oturumun önceki bir çalıştırmasında Java ile eşleşti.

#### Özel biyomlar

Bedrock bilmediği biyom tanımlarını reddeder, ancak vanilla bir biyomun görünümünü kaynak paketinden alır.
Yirmi beş Bedrock biyomu yalnızca eski dünyalar için vardır (eski tepeler, kenarlar ve mutasyonlu
çeşitler) ve hiçbir Java biyomu onlara eşlenmez. Paket bunları sunucunun özel biyomlarının çimen, yaprak,
su, su altı sisi, sis ve gökyüzü renkleriyle yeniden tanımlar; Geyser de iklimlerini (sıcaklık, yağış
miktarı, yağmur veya kar) gönderir. Özel biyomlar datapack'lerden ve sunucu kayıt defterinden gelir; bu
yüzden eklenti biyomları (RealisticSeasons) dahildir. Yuvadan fazla özel görünüm olduğunda önce geçerli
mevsimin görünümleri, ardından vanilla biyomların en kötü yaklaştığı görünümler gelir; diğer her özel biyom
en yakın vanilla biyom veya yuva olarak görünür.

| Datapack biyomunun zemini (ortalama RGB) | Java | Bedrock |
| --- | --- | --- |
| Geliştirme derlemesi 7 (en yakın vanilla biyom) | 112, 129, 65 | 102, 123, 54 |
| Geliştirme derlemesi 8 (birebir yuva) | 111, 128, 64 | 110, 127, 64 |

Java biyomları yerinde değiştirir (`/fillbiome`, mevsim eklentileri) ve bunu Geyser'ın çevirmediği bir
paketle yapar; bu yüzden Bedrock chunk yeniden yüklenene kadar eski biyomları koruyordu. Twilight artık bu
chunk'ları Bedrock oyuncusuna olağan chunk yolu üzerinden yeniden gönderiyor; animasyon 25 özel biyomluk
bir ızgaranın yeniden katılmadan birkaç kez değiştiğini gösterir. 25 yuvanın hepsi kendi çimen ve yaprak
renklerini gösterir ([ızgara](images/acceptance/2026-10-04-layers/slots.png)); sınırlarda Bedrock komşu
biyomları Java'dan daha geniş bir yarıçapta karıştırır.

Survival yapılandırmasıyla RealisticSeasons 11.12.1 (yalnızca ölçüm) 188 mevsimsel biyom kaydetti.
Twilight bunları kayıt defterinden okudu, RealisticSeasons API'sine geçerli mevsimin hangilerini
gösterdiğini sordu ve yuvaları önce onlara verdi; mevsim değişikliği olayı paketi yeniden derler. Kışın bir
orman zemini Java'da 104, 118, 127, Bedrock'ta 104, 118, 128 ölçüldü (mevsim hesaba katılmadan önce 98,
115, 111). RealisticSeasons'ın kendi verileri ve paketleri değiştirilmez.

#### Gerçek sunucularda otomatik derlemeler

`ServerBuildAuditMain`, yedi üretim sunucusunun tam derlemelerini varsayılan yapılandırmayla salt okunur
çalıştırdı. Java'nın kendisinin tolere ettiği içerik artık Java'nın gösterdiği şekilde dönüştürülüyor ve
bildirim olarak raporlanıyor: hiçbir pakette olmayan dokular Java'nın eksik dokusu olur, modeli hiçbir yerde
olmayan eşyalar temel eşyalarını korur, değiştirilmiş vanilla sesler `vanilla-override` açılmadıkça
Bedrock'un sesini korur ve ekran boyutundaki kaplama glifleri dışarıda bırakılırken ardından gelen yazı
konumunu korur.

| Sunucu | Özel eşya | Glif | Sorun | Bildirim |
| --- | --- | --- | --- | --- |
| Survival | 1.915 / 1.932 | 767 | 0 | 175 |
| SkyBlock | 31 / 52 | 31 | 0 | 64 |
| Boxpvp v2 | 884 / 902 | 622 | 0 | 18 |
| BoxPVP | 613 / 928 | 618 | 0 | 319 |
| Kaynak | 2.713 / 3.014 | 762 | 0 | 375 |
| SMP-Lifesteal, Auth | özel içerik yok | 0 | 0 | 0 |

Her derleme katı yayını geçiyor. Bir sunucunun ilk katı derlemesi hâlâ bir sorun bulursa ve henüz paket
dağıtılmamışsa Twilight hiç paket yerine o içerik olmadan ilk bir paketi dağıtır; sonraki derlemeler katı
kalır.

#### Araştırma

- chromehaert'in [no nametag background](https://www.curseforge.com/minecraft-bedrock/texture-packs/no-nametag-background)
  paketi, Bedrock'un ad etiketi kutusunun karışımı bir paketin değiştirebildiği `name_tag` malzemesiyle
  çizildiğini gösterdi. Twilight aynı fikirle kendi malzeme dosyasını üretir; o paketin hiçbir dosyası
  dağıtılmaz.
- [Mojang'ın bedrock-samples deposu](https://github.com/Mojang/bedrock-samples) ve
  [istemci biyom referansı](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/clientbiomesreference/examples/components/client_biome_components)
  Twilight'ın yazdığı istemci biyom ve sis biçimlerini belgeler.
- [Chest GUI Generator](https://github.com/wlsgunzz/Chest-GUI-Generator) bir paketten başlık başına durağan
  sandık kaplamaları üretir. Twilight ise başlıkları paket başına yerleştirir; bu yüzden yer tutuculu ve üst
  üste katmanlı başlıklar başlık başına kurulum gerektirmez.
- Ad kuralları Java 26.2 istemcisinin çizicilerinden gelir: canlı varlıklar yolcu taşırken adlarını gizler,
  zırh askılıkları korur.

#### Paket işleme

Değişiklikler, Geyser'ın her Bedrock oyuncusu için Java paketlerini çevirdiği yerde (Geyser'ın kendi
çevirmen kayıt defteri) yapılır, ayrı bir paket kütüphanesiyle değil: Twilight Geyser'ın çevirmek üzere
olduğu yazıyı, varlıkları ve chunk'ları birebir görür, Java oyuncuları için hiçbir şeyi değiştirmez ve ek
bağımlılık gerektirmez. Bu, Geyser'ın Twilight ile aynı sunucuda olmasını gerektirir (Geyser-Spigot);
yalnızca proxy'deki bir Geyser paketi alır ama bu çalışma zamanı katmanlarını almaz.

#### Kalan farklar

- Bir moba binen yazı görüntüsü Bedrock'ta yaklaşık 0,4 blok daha aşağıda çizilir (Geyser'ın binek
  kayması); oyuncularda etiket Java'nın çizdiği yerde durur.
- Bedrock'un ad etiketi kutusu ya her ad etiketi için ya hiçbiri için gizlenir; Java her yazı görüntüsü
  için ayrı karar verir.
- Yazıdaki hex renkler Bedrock'un 28 yazı renginden en yakınıyla gösterilir.
- Bedrock biyom sınırlarında biyom renklerini daha geniş bir yarıçapta karıştırır.
- 25 yuvadan fazla özel görünüm en yakın yuvayı veya vanilla biyomu paylaşır.
