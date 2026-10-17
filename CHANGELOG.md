# Changelog

> Türkçe: [aşağıda](#türkçe)

All notable changes in Twilight are documented here.

## 1.0.0-beta.5 - 2026-10-17

- Geyser 2.12 support, keeping Geyser 2.11: item and block displays, dropped 3D items, point-cloud adaptation and
  the network layer work on both (Geyser 2.12 dropped two registries and changed a packet field Twilight used).
- Item definitions of CraftEngine and other 1.21.4+ packs: properties Geyser's item mappings cannot read
  (`using_item`, `display_context`, pulling and use duration) become the state players see most instead of making
  Geyser refuse the item (topaz bows, crossbows, fishing rods and spears showed as their base item), and
  `fishing_rod/cast` and `bundle/fullness` get Geyser's names. A model named without a namespace is
  `minecraft:` as on Java (CraftEngine's custom block items showed as their base item).
- Armour trims on custom armour items (`trims/items/helmet_trim_gold` and the like) are recoloured like Java's
  atlas does instead of showing the missing texture.
- The same content always builds the same pack: providers that list their items in another order on every start
  (CraftEngine) no longer make every start ask for another restart.
- No stack overflow on entity data after a Geyser reload: Twilight's translator wrappers no longer wrap each other
  in a loop.
- A warning at start when TCPShield would refuse Geyser's connection (`enable-geyser-support: false`), with the
  setting that fixes it.

## 1.0.0-beta.4 - 2026-10-17

- `config.yml` of Twilight and of twilight-proxy is kept current at every start and reload: settings of a
  newer version are added with their defaults and explanations where the default file has them, invalid
  values (wrong type, out of range, unknown option) are set back to their defaults one by one with a
  warning, and a file that is not readable YAML is restored from the last version that worked (or the
  defaults). A copy of the old file is kept in `config-backups/` first (the newest ten); the new file is
  written atomically and only when it passes every check and stays the same in a second run, and a file
  Twilight wrote is never changed again by it, so repairs cannot loop. Comments, order, quoting, line
  endings, unknown keys (reported) and the administrator's own lists stay as they are; links, files that
  are not UTF-8, larger than 1 MiB or use YAML anchors are only read.

## 1.0.0-beta.3 - 2026-10-16

- Bedrock network layer on every Geyser Twilight runs on (backend or proxy): movement and data of entities a
  Bedrock player cannot see, behind them or behind walls of at least three solid blocks, are held back and
  merged, and sent as one up-to-date update when the entity comes into view or every few seconds. Distant
  entities get fewer movement updates, every connection has an update budget, and struggling connections
  get up to two zlib levels more. Every limit (view angle, distances, wall thickness, refresh, budget)
  defaults to `auto` and follows the player's ping and input rate. Entities with a name tag and display
  entities are never hidden by walls, nearby entities always get every update, and nothing but entity
  movement and data is ever held. `bedrock-network` in both configurations; `/twilight network` and
  `/twilightproxy network` show the effect per player. Line-of-sight workers default to half of
  spigot.yml's `netty-threads` (a quarter of the processors on a proxy), 1-4.

## 1.0.0-beta.2 - 2026-10-16

- Displays and holograms (FancyHolograms, DecentHolograms, furniture plugins, display builds), also when sent
  only as packets: block displays show the block state's model, rotated and tinted like Java; item displays show
  every item, flat ones as Java's one-pixel extrusion and items without custom data with their own model; the
  billboard modes face the camera and the brightness override sets the light as on Java. Each new item or block
  state is learned when a display first shows it and added by the next build.
- Dropped items of custom items with a 3D model lie on the ground with their model, bobbing and spinning like
  Java; other dropped items stay Bedrock's own.
- Display models no longer show a box's east texture on its west side (an item display or block display of a
  crafting table or a furnace looked mirrored).
- Player heads show their skins on Bedrock instead of Steve's, in menus of any plugin, hands, armour stands,
  item frames and holograms, on the server and through the proxy: Twilight learns the heads menus and the world
  show, reads the ones plugin configurations name (Base64 profiles, skin URLs and hashes, user names, HeadDatabase
  ids) and Geyser registers them as custom skulls (`geyser.player-heads`, `bedrock.player-heads`). Heads in item
  displays are drawn with their skin as Java draws them.
- Heads of players show their skins too: the skins of the players who join, Java and Bedrock, are kept
  (`player-skins.txt`, the 1024 most recent by default: `geyser.player-head-skins-of-players`) and registered
  after the heads the server shows, without starting a build or asking for a restart. The configuration scan
  reads TrMenu's `head:` and `skin:` forms, skips libraries, generated packs and per-player data, and limits
  each plugin's folder, so it covers every plugin of a large server in a few seconds.
- Chest titles without custom characters (no image, offset or shift) are no longer moved: they go out as Java
  sent them and show at Java's position in a label of their own, on desktop and on phones. Only titles that need
  it are laid out with spacers, so a plain title can no longer drift or disappear on devices whose GUI scale
  rounds the spacers.
- `/twilight menus` analyses the menus players open, whatever plugin made them: which titles Bedrock gets plain
  and which laid out, offset tags a plugin left unreplaced, characters the pack has no image for, player heads and
  custom items per menu, with the plugin that owns the menu (`reports/menus.json`).

## 1.0.0-beta.1 - 2026-10-15

First public beta of Twilight and twilight-proxy.

- Builds a Bedrock resource pack and Geyser item mappings from the server's plugins, datapacks and resource
  packs (ItemsAdder, CraftEngine, Nexo, Oraxen, BetterModel, ModelEngine, RealisticSeasons, CustomNameplates),
  validates the result and deploys it transactionally; strict builds keep the last good pack.
- Custom items with their models, 3D block items, custom blocks on note blocks, mushroom blocks and tripwire,
  sounds, and datapack and plugin biomes in their exact colours.
- Java text layout for Bedrock players: custom glyphs, negative and positive spaces, offset tags that content
  plugins left unreplaced (`:offset_N:`, `%img_offset_N%`, `%nexo_shift_N%`, `<shift:N>`), named fonts,
  translations, layered menu art, boss bars and Java's chest layout on desktop and on phones. Large glyphs sit
  on pages of their own, so phones load far less texture memory.
- Item displays: furniture and ModelEngine, BetterModel and MythicMobs mobs and NPCs follow Java's pose,
  interpolation and display context, also when plugins send them only as packets. Display models load in
  sheets, and only items the server shows get one.
- twilight-proxy for Velocity and BungeeCord forks (Waterfall, FlameCord): gives each Bedrock player the pack of
  the server they join and runs the text layout, translations, item displays, custom blocks and loading
  protection in the proxy's Geyser. Proxy and backends talk over signed plugin messages.
- Pack hosting over HTTP for fast downloads, an update check against GitHub and GitLab, and a developer API
  with events.
- License: the [Twilight License](LICENSE), the PolyForm Noncommercial License 1.0.0 with additional terms:
  Twilight may be built and run on any server or network, including ones that earn money; selling it, under
  any name, is not permitted; anyone who copies, quotes or adapts any part of it must credit Twilight
  ([NOTICE](NOTICE)).
- 279 tests pass (243 Twilight, 36 proxy).

---

## Türkçe

### Değişiklik günlüğü

Twilight'taki tüm önemli değişiklikler burada belgelenir.

#### 1.0.0-beta.5 - 2026-10-17

- Geyser 2.11'i koruyarak Geyser 2.12 desteği: eşya ve blok görüntüleri, düşen 3B eşyalar, nokta bulutu uyarlaması
  ve ağ katmanı ikisinde de çalışır (Geyser 2.12, Twilight'ın kullandığı iki kaydı kaldırdı ve bir paket alanını
  değiştirdi).
- CraftEngine'in ve diğer 1.21.4+ paketlerin eşya tanımları: Geyser'ın eşya eşlemelerinin okuyamadığı özellikler
  (`using_item`, `display_context`, çekme ve kullanım süresi) Geyser'ın eşyayı reddetmesine yol açmak yerine
  oyuncuların en çok gördüğü durum olur (topaz yaylar, arbaletler, oltalar ve mızraklar temel eşya olarak
  görünüyordu); `fishing_rod/cast` ve `bundle/fullness` Geyser'ın adlarını alır. Ad alanı olmadan adı verilen bir
  model Java'daki gibi `minecraft:` olur (CraftEngine'in özel blok eşyaları temel eşya olarak görünüyordu).
- Özel zırh eşyalarındaki zırh süslemeleri (`trims/items/helmet_trim_gold` ve benzerleri) eksik doku göstermek
  yerine Java'nın atlasının yaptığı gibi yeniden renklendirilir.
- Aynı içerik her zaman aynı paketi derler: eşyalarını her açılışta başka sırayla listeleyen sağlayıcılar
  (CraftEngine) artık her açılışta yeni bir yeniden başlatma istetmez.
- Geyser yeniden yüklendikten sonra varlık verilerinde yığın taşması yok: Twilight'ın çevirici sarmalayıcıları
  artık birbirini döngüye sokacak şekilde sarmaz.
- TCPShield Geyser'ın bağlantısını reddedecekse (`enable-geyser-support: false`) açılışta, düzelten ayarla
  birlikte bir uyarı.

#### 1.0.0-beta.4 - 2026-10-17

- Twilight'ın ve twilight-proxy'nin `config.yml` dosyası her açılışta ve yeniden yüklemede güncel tutulur:
  daha yeni bir sürümün ayarları varsayılanları ve açıklamalarıyla varsayılan dosyadaki yerlerine eklenir,
  geçersiz değerler (yanlış tür, aralık dışı, bilinmeyen seçenek) bir uyarıyla tek tek varsayılanlarına
  döndürülür ve okunabilir YAML olmayan bir dosya çalışan son sürümünden (veya varsayılanlardan) geri yüklenir.
  Önce eski dosyanın bir kopyası `config-backups/` içine alınır (en yeni on tane); yeni dosya atomik olarak ve
  yalnızca her denetimi geçip ikinci bir çalıştırmada aynı kaldığında yazılır ve Twilight'ın yazdığı bir dosya
  onun tarafından bir daha değiştirilmez; bu yüzden onarımlar döngüye giremez. Yorumlar, sıra, tırnaklar,
  satır sonları, bilinmeyen anahtarlar (bildirilir) ve yöneticinin kendi listeleri olduğu gibi kalır;
  bağlantılar, UTF-8 olmayan, 1 MiB'tan büyük veya YAML çapaları kullanan dosyalar yalnızca okunur.

#### 1.0.0-beta.3 - 2026-10-16

- Twilight'ın çalıştığı her Geyser'da (arka uç veya proxy) Bedrock ağ katmanı: bir Bedrock oyuncusunun
  göremediği, arkasındaki veya en az üç katı bloktan oluşan duvarların ardındaki varlıkların hareketi ve
  verileri bekletilir ve birleştirilir; varlık görüşe girdiğinde veya birkaç saniyede bir tek bir güncel
  güncelleme olarak gönderilir. Uzaktaki varlıklar daha az hareket güncellemesi alır, her bağlantının bir
  güncelleme bütçesi vardır ve zorlanan bağlantılar en fazla iki zlib seviyesi fazla alır. Her sınır (görüş
  açısı, mesafeler, duvar kalınlığı, yenileme, bütçe) varsayılan olarak `auto` olur ve oyuncunun ping'ini
  ve girdi hızını izler. Ad etiketi olan varlıklar ve görüntü varlıkları duvarlarla asla gizlenmez, yakındaki
  varlıklar her zaman her güncellemeyi alır ve varlık hareketi ve verileri dışında hiçbir şey bekletilmez.
  İki yapılandırmada da `bedrock-network`; `/twilight network` ve `/twilightproxy network` etkisini oyuncu
  başına gösterir. Görüş çizgisi iş parçacıkları varsayılan olarak spigot.yml'deki `netty-threads`
  değerinin yarısıdır (proxy'de işlemcilerin dörtte biri), 1-4.

#### 1.0.0-beta.2 - 2026-10-16

- Görüntüler ve hologramlar (FancyHolograms, DecentHolograms, mobilya eklentileri, görüntü yapıları), yalnızca
  paket olarak gönderildiklerinde de: blok görüntüleri blok durumunun modelini Java gibi döndürülmüş ve
  renklendirilmiş gösterir; eşya görüntüleri her eşyayı gösterir, düz olanları Java'nın bir piksellik kabartması
  olarak, özel verisi olmayanları kendi modelleriyle; billboard modları kameraya döner ve parlaklık geçersiz
  kılma ışığı Java'daki gibi ayarlar. Her yeni eşya veya blok durumu bir görüntü onu ilk gösterdiğinde öğrenilir
  ve sonraki derlemede eklenir.
- 3B modeli olan özel eşyaların düşen eşyaları yerde modelleriyle durur, Java gibi zıplayıp döner; diğer düşen
  eşyalar Bedrock'un kendi eşyaları olarak kalır.
- Görüntü modelleri bir kutunun doğu dokusunu artık batı yüzünde göstermez (bir çalışma masası veya fırın eşya ya
  da blok görüntüsü ayna görüntüsü gibi görünüyordu).
- Oyuncu kafaları Bedrock'ta Steve'inki yerine kendi skinleriyle görünür; her eklentinin menülerinde, ellerde, zırh
  standlarında, eşya çerçevelerinde ve hologramlarda, sunucuda ve proxy üzerinden: Twilight menülerin ve dünyanın
  gösterdiği kafaları öğrenir, eklenti yapılandırmalarının adını verdiklerini okur (Base64 profiller, skin URL'leri
  ve hash'leri, kullanıcı adları, HeadDatabase kimlikleri) ve Geyser onları özel kafa olarak kaydeder
  (`geyser.player-heads`, `bedrock.player-heads`). Eşya görüntülerindeki kafalar Java'nın çizdiği gibi skinleriyle
  çizilir.
- Oyuncuların kafaları da skinleriyle görünür: katılan Java ve Bedrock oyuncularının skinleri tutulur
  (`player-skins.txt`, varsayılan olarak en son 1024 oyuncu: `geyser.player-head-skins-of-players`) ve derleme
  başlatmadan veya yeniden başlatma istemeden sunucunun gösterdiği kafalardan sonra kaydedilir. Yapılandırma
  taraması TrMenu'nün `head:` ve `skin:` biçimlerini okur; kütüphaneleri, üretilmiş paketleri ve oyuncu başına
  verileri atlar ve her eklentinin klasörünü sınırlar; böylece büyük bir sunucunun tüm eklentilerini birkaç
  saniyede kapsar.
- Özel karakter içermeyen (görsel, offset veya kaydırma olmayan) sandık başlıkları artık kaydırılmaz: Java'nın
  gönderdiği gibi gider ve masaüstünde de telefonda da Java'nın konumunda kendi etiketinde görünür. Yalnızca
  gereken başlıklar boşluk glifleriyle yerleştirilir; böylece düz bir başlık, GUI ölçeği boşlukları yuvarlayan
  cihazlarda artık kaymaz veya kaybolmaz.
- `/twilight menus`, oyuncuların açtığı menüleri hangi eklenti yapmış olursa olsun analiz eder: Bedrock'un hangi
  başlıkları düz, hangilerini yerleştirilmiş aldığı, bir eklentinin değiştirmeden bıraktığı offset etiketleri,
  paketin görseli olmayan karakterler, menü başına oyuncu kafaları ve özel eşyalar, menünün sahibi olan eklentiyle
  birlikte (`reports/menus.json`).

#### 1.0.0-beta.1 - 2026-10-15

Twilight ve twilight-proxy'nin ilk herkese açık betası.

- Sunucunun eklentilerinden, datapack'lerinden ve kaynak paketlerinden (ItemsAdder, CraftEngine, Nexo, Oraxen,
  BetterModel, ModelEngine, RealisticSeasons, CustomNameplates) bir Bedrock kaynak paketi ve Geyser eşya
  eşlemeleri derler, sonucu doğrular ve işlem bütünlüğüyle dağıtır; katı derlemeler son iyi paketi korur.
- Modelleriyle özel eşyalar, 3B blok eşyaları, nota bloğu, mantar bloğu ve tuzak teli üzerindeki özel bloklar,
  sesler ve birebir renkleriyle datapack ve eklenti biyomları.
- Bedrock oyuncuları için Java yazı yerleşimi: özel glifler, negatif ve pozitif boşluklar, içerik eklentilerinin
  değiştirmeden bıraktığı kaydırma etiketleri (`:offset_N:`, `%img_offset_N%`, `%nexo_shift_N%`,
  `<shift:N>`), adlandırılmış fontlar, çeviriler, katmanlı menü görselleri, boss çubukları ve masaüstünde ve
  telefonlarda Java'nın sandık yerleşimi. Büyük glifler kendi sayfalarında durur; böylece telefonlar çok daha
  az doku belleği yükler.
- Eşya görüntüleri: mobilyalar ve ModelEngine, BetterModel ve MythicMobs mobları ve NPC'leri, eklentiler
  onları yalnızca paket olarak gönderdiğinde de Java'nın pozunu, ara geçişini ve görüntü bağlamını izler.
  Görüntü modelleri sayfalar hâlinde yüklenir ve yalnızca sunucunun gösterdiği eşyalar bir model alır.
- Velocity ve BungeeCord türevleri (Waterfall, FlameCord) için twilight-proxy: her Bedrock oyuncusuna katıldığı
  sunucunun paketini verir; yazı yerleşimini, çevirileri, eşya görüntülerini, özel blokları ve yükleme
  korumasını proxy'deki Geyser'da çalıştırır. Proxy ve arka uçlar imzalı eklenti mesajlarıyla haberleşir.
- Hızlı indirmeler için HTTP üzerinden paket barındırma, GitHub ve GitLab'a karşı güncelleme denetimi ve
  olaylarıyla bir geliştirici API'si.
- Lisans: [Twilight License](LICENSE), yani ek koşullarla birlikte PolyForm Noncommercial License 1.0.0:
  Twilight para kazananlar dahil her sunucuda veya ağda derlenip çalıştırılabilir; hangi adla olursa olsun
  satılması yasaktır; herhangi bir parçasını kopyalayan, alıntılayan veya uyarlayan herkes Twilight'ı kaynak
  olarak göstermelidir ([NOTICE](NOTICE)).
- 279 test geçer (243 Twilight, 36 proxy).
