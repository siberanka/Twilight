# Twilight 1.0.0-beta.6

> Türkçe: [aşağıda](#türkçe)

**Worn armour and Java's poses.** Custom armour now shows on Bedrock players, armour stands and mobs: equipment
assets of CraftEngine, Oraxen and 1.21.2+ packs, ItemsAdder's leather-coloured armour and dyed equipment. Hats,
wings and other wearable items can be equipped by Bedrock players too. Asymmetric models no longer show their
east and west faces swapped, items worn on the head face forward, held 3D models keep Java's first person pose,
models with inverted elements (CraftEngine's globe) draw the right faces and furniture in invisible item frames
stands upright.

**Blocks and textures.** CraftEngine and ItemsAdder blocks built on mushroom block states show their own look
instead of a vanilla mushroom, and grayscale textures keep their shade.

**Fewer restarts.** A new build reaches Bedrock players who join afterwards without a restart and without
`geyser reload` (which disconnected every Bedrock player): new textures and models, and the models of furniture
seen for the first time. New custom items, blocks and player heads still need one restart, as Geyser registers
them only when it starts. Oraxen and Nexo servers are no longer asked to restart after every restart.

**Tested** on Leaf 26.3 and Leaf 1.21.4 with ItemsAdder 5.0.0-beta-2, CraftEngine 26.9 and Oraxen 1.220, Geyser
2.12 and Floodgate, comparing Java 1.21.4 and Bedrock captures of items, tools, armour, blocks, ores and furniture
in first and third person, and with 300 item displays and 300 dropped 3D items around the players.

Test server: `oyna.twilightnw.com`.

Update both JARs and restart the server and the proxy once.

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 365 tests
across 63 suites passed. Both JARs carry `LICENSE` and `NOTICE` in `META-INF`. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3 and 2.12.0; other Geyser core versions require validation.

The notes below describe the previous beta.

## Twilight 1.0.0-beta.5

**Geyser 2.12 and CraftEngine.** Twilight now runs on Geyser 2.12 as well as 2.11: item and block displays,
dropped 3D items and the network layer work on both. CraftEngine's content converts like ItemsAdder's: bows,
crossbows, fishing rods and spears whose definitions use properties Geyser cannot read now show their model
instead of the base item, custom block items whose models are named without a namespace show their block, and
trimmed custom armour gets its trim colours. Tested live on Paper 26.3 with Geyser 2.12, Floodgate and
CraftEngine 26.9.2: items, tools, custom blocks and ores, furniture and dropped items, in first and third person.

**Fewer restarts and no overflow.** The same content always builds the same pack, so servers whose provider
lists its items in another order on every start (CraftEngine) are no longer asked to restart again and again.
After a Geyser reload, Twilight's packet translators no longer call each other in a loop.

**TCPShield.** When TCPShield would refuse Geyser's own connection, Twilight says so at start: set
`enable-geyser-support: true` in TCPShield's configuration, otherwise Bedrock players see "Connection failed.
Please try again or contact an administrator". Geyser 2.12.0 builds before b1262 did not show custom items:
update Geyser as well.

Update both JARs and restart the server and the proxy once.

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 352 tests
across 61 suites passed. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3 and 2.12.0; other Geyser core versions require validation.

The notes below describe the previous beta.

## Twilight 1.0.0-beta.4

**Configuration that keeps itself current.** At every start and reload, `config.yml` of Twilight and of
twilight-proxy gets the settings of the new version, with their defaults and explanations, where the
default file has them. Invalid values (a word where a number belongs, a number out of range, an unknown
option) are set back to their defaults one by one with a warning, and a file that is not readable YAML is
restored from the last version that worked. Values that are fine, comments, order, quoting and your own
lists are never changed.

Every change is made safely: a verified copy of the old file goes to `config-backups/` first, the new file
is written atomically and only when it passes every check and stays the same in a second run, and a file
Twilight wrote is not changed again by it, so repairs cannot loop. Links, files that are not UTF-8, larger
than 1 MiB or use YAML anchors are only read.

Update both JARs and restart the server and the proxy once.

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 342 tests
across 58 suites passed. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3; other Geyser core versions require validation.

The notes below describe the previous beta.

### Twilight 1.0.0-beta.3

**Lighter traffic for Bedrock players.** A new network layer runs in every Bedrock connection of the Geyser
Twilight runs on, on a backend or on the proxy. Movement and data of entities a player cannot see (behind
them, or behind walls of at least three solid blocks) are held back, merged and sent as one up-to-date
update when the entity comes into view or every few seconds. Distant entities get fewer movement updates,
every connection has an update budget, and struggling connections get stronger compression. Every limit
is `auto` by default and follows the player's ping and how well the client keeps up; entities close by,
entities with a name tag and display entities are never held by walls, and nothing but entity movement and
data is ever held or reordered. In a test with 1200 moving mobs, 59 percent of the entity updates were
spared at about 1.5 microseconds per packet, and the client ended with exactly the server's state.

Settings are in `bedrock-network` (both configurations; every key defaults to `auto` or on).
`/twilight network` and `/twilightproxy network` show the effect per player. The line-of-sight workers
follow spigot.yml's `netty-threads` on a backend and the processor count on a proxy.

Update both JARs and restart the server and the proxy once.

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 318 tests
across 55 suites passed. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3; other Geyser core versions require validation.

The notes below describe the previous beta.

### Twilight 1.0.0-beta.2

**Holograms, displays and dropped items like Java.** FancyHolograms, DecentHolograms, furniture plugins and
display builds show on Bedrock as on Java, also when plugins send them only as packets: block displays with the
block's model (rotated and tinted like Java), item displays of every item (flat items as Java's 3D sprite,
items without custom data with their own model), billboards that face the camera and brightness overrides.
Dropped custom items with a 3D model lie on the ground with their model, bobbing and spinning. Asymmetric
display models are no longer mirrored.

**Player heads with their skins.** Heads in menus of any plugin, in hands, on armour stands and in holograms show
their skins instead of Steve's, on the server and through the proxy. Twilight learns the heads menus and the
world show, reads the ones plugin configurations name (Base64 textures, skin URLs, user names, HeadDatabase
ids) and keeps the skins of the players who join, so heads of players (`head-%player_name%`, top lists) show
their skins too. Geyser registers heads when it starts: new heads a server shows need a restart; new players'
skins simply come with the next start.

**Menu titles.** Titles without images or offsets are no longer moved: they show at Java's position on desktop
and phones and can no longer drift or disappear. `/twilight menus` analyses the menus players open: plain or
laid-out titles, offset tags a plugin left unreplaced, characters the pack has no image for, heads and custom
items per menu (`reports/menus.json`).

**License.** Twilight is licensed under the [Twilight License](LICENSE): the PolyForm Noncommercial License
1.0.0 with additional terms. Anyone may build and run it on their servers and networks, including ones that
earn money. Selling it under any name is not permitted, and anyone who copies, quotes or adapts any part of it,
even a single code block, must credit Twilight ([NOTICE](NOTICE)).

Update both JARs and restart the server and the proxy once.

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 295 tests
across 52 suites passed. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3; other Geyser core versions require validation.

The notes below describe the previous beta.

### Twilight 1.0.0-beta.1

The first public beta of Twilight: Java custom content for Bedrock players through Geyser, without a client
mod. Twilight reads your server's plugins, datapacks and resource packs (ItemsAdder, CraftEngine, Nexo, Oraxen,
BetterModel, ModelEngine, RealisticSeasons, CustomNameplates), builds the Bedrock pack and Geyser's item
mappings, and shows Bedrock players the same items, blocks, models, menus and text as Java players see.

**Highlights**
- Custom items, 3D block items and custom blocks on note blocks, mushroom blocks and tripwire.
- Menus and text laid out with Java font metrics: negative and positive spaces, offset tags, layered menu art
  and Java's chest layout, also on phones.
- Furniture and ModelEngine, BetterModel and MythicMobs mobs and NPCs, including the ones sent only as packets.
- `TwilightProxy.jar` for Velocity and BungeeCord forks with Geyser on the proxy: every Bedrock player gets the
  pack of the server they join.
- Fast pack downloads through the built-in pack host.

**Installing.** Put `Twilight.jar` on every backend server and, on a network with Geyser on the proxy,
`TwilightProxy.jar` on the proxy, both of the same version. Restart once after the first build: Geyser
registers custom items only when it starts. Servers running an earlier development build do not announce
this version; replace both JARs by hand once. See the [wiki](WIKI.md) for configuration and the
[compatibility notes](docs/COMPATIBILITY.md) for known limits.

**License.** Twilight is licensed under the [Twilight License](LICENSE): the PolyForm Noncommercial License
1.0.0 with additional terms. Anyone may build and run it on their servers and networks, including ones that
earn money. Selling Twilight, a copy, a changed version or a renamed copy of it is not permitted, and anyone
who copies, quotes or adapts any part of it must credit Twilight ([NOTICE](NOTICE)).

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 279 tests
across 49 suites passed. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3; other Geyser core versions require validation.

---

## Türkçe

### Twilight 1.0.0-beta.6

**Giyilen zırhlar ve Java'nın duruşları.** Özel zırhlar artık Bedrock oyuncularında, zırh askılarında ve
moblarda görünür: CraftEngine'in, Oraxen'in ve 1.21.2+ paketlerinin ekipman varlıkları, ItemsAdder'ın deri
renkli zırhları ve boyanmış ekipman. Şapkaları, kanatları ve diğer giyilebilir eşyaları Bedrock oyuncuları da
kuşanabilir. Asimetrik modellerin doğu ve batı yüzleri artık yer değiştirmez, başa giyilen eşyalar öne bakar,
elde tutulan 3B modeller Java'nın birinci şahıs duruşunu korur, ters öğeli modeller (CraftEngine'in küresi) doğru
yüzleri çizer ve görünmez eşya çerçevelerindeki mobilyalar dik durur.

**Bloklar ve dokular.** Mantar bloğu durumları üzerine kurulan CraftEngine ve ItemsAdder blokları vanilla mantar
yerine kendi görünümlerini gösterir; gri tonlu dokular tonlarını korur.

**Daha az yeniden başlatma.** Yeni bir derleme, sonradan giren Bedrock oyuncularına yeniden başlatma ve (tüm
Bedrock oyuncularının bağlantısını koparan) `geyser reload` olmadan ulaşır: yeni dokular ve modeller ile ilk kez
görülen mobilyaların modelleri. Yeni özel eşyalar, bloklar ve oyuncu kafaları, Geyser onları yalnızca açılışta
kaydettiği için hâlâ bir yeniden başlatma gerektirir. Oraxen ve Nexo sunucularından artık her yeniden başlatmadan
sonra yeni bir yeniden başlatma istenmez.

**Test edildi:** Leaf 26.3 ve Leaf 1.21.4 üzerinde ItemsAdder 5.0.0-beta-2, CraftEngine 26.9 ve Oraxen 1.220,
Geyser 2.12 ve Floodgate ile; eşyaların, aletlerin, zırhların, blokların, madenlerin ve mobilyaların Java 1.21.4
ve Bedrock görüntüleri birinci ve üçüncü şahısta karşılaştırıldı, oyuncuların çevresinde 300 eşya görüntüsü ve
300 düşen 3B eşyayla da denendi.

Test sunucusu: `oyna.twilightnw.com`.

İki JAR'ı da güncelleyin ve sunucuyu ve proxy'yi bir kez yeniden başlatın.

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 63 paketteki
365 test geçti. İki JAR da `META-INF` içinde `LICENSE` ve `NOTICE` taşır. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3 ve 2.12.0'ı
hedefler; diğer Geyser çekirdek sürümleri doğrulama gerektirir.

Aşağıdaki notlar önceki betayı anlatır.

#### Twilight 1.0.0-beta.5

**Geyser 2.12 ve CraftEngine.** Twilight artık 2.11'in yanında Geyser 2.12'de de çalışır: eşya ve blok
görüntüleri, düşen 3B eşyalar ve ağ katmanı ikisinde de çalışır. CraftEngine'in içeriği ItemsAdder'ınki gibi
dönüştürülür: tanımları Geyser'ın okuyamadığı özellikler kullanan yaylar, arbaletler, oltalar ve mızraklar artık
temel eşya yerine modelleriyle görünür, modelleri ad alanı olmadan adlandırılan özel blok eşyaları bloklarını
gösterir ve süslenmiş özel zırhlar süs renklerini alır. Paper 26.3 üzerinde Geyser 2.12, Floodgate ve CraftEngine
26.9.2 ile canlı test edildi: eşyalar, aletler, özel bloklar ve madenler, mobilyalar ve düşen eşyalar, birinci ve
üçüncü şahısta.

**Daha az yeniden başlatma, taşma yok.** Aynı içerik her zaman aynı paketi derler; böylece sağlayıcısı
eşyalarını her açılışta başka sırayla listeleyen sunuculardan (CraftEngine) artık tekrar tekrar yeniden
başlatma istenmez. Geyser yeniden yüklendikten sonra Twilight'ın paket çeviricileri artık birbirini döngüyle
çağırmaz.

**TCPShield.** TCPShield Geyser'ın kendi bağlantısını reddedecekse Twilight bunu açılışta söyler: TCPShield'in
yapılandırmasında `enable-geyser-support: true` yapın; aksi halde Bedrock oyuncuları "Connection failed. Please
try again or contact an administrator" görür. b1262'den önceki Geyser 2.12.0 derlemeleri özel eşyaları
göstermiyordu: Geyser'ı da güncelleyin.

İki JAR'ı da güncelleyin ve sunucuyu ve proxy'yi bir kez yeniden başlatın.

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 61 paketteki 352
test geçti. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3 ve 2.12.0'ı
hedefler; diğer Geyser çekirdek sürümleri doğrulama gerektirir.

Aşağıdaki notlar önceki betayı anlatır.

#### Twilight 1.0.0-beta.4

**Kendini güncel tutan yapılandırma.** Her açılışta ve yeniden yüklemede Twilight'ın ve twilight-proxy'nin
`config.yml` dosyası yeni sürümün ayarlarını, varsayılanları ve açıklamalarıyla, varsayılan dosyadaki
yerlerinde alır. Geçersiz değerler (sayı olması gereken yerde bir kelime, aralık dışı bir sayı, bilinmeyen
bir seçenek) bir uyarıyla tek tek varsayılanlarına döndürülür ve okunabilir YAML olmayan bir dosya çalışan son
sürümünden geri yüklenir. Geçerli değerler, yorumlar, sıra, tırnaklar ve kendi listeleriniz asla değiştirilmez.

Her değişiklik güvenle yapılır: önce eski dosyanın doğrulanmış bir kopyası `config-backups/` içine alınır, yeni
dosya atomik olarak ve yalnızca her denetimi geçip ikinci bir çalıştırmada aynı kaldığında yazılır ve
Twilight'ın yazdığı bir dosya onun tarafından bir daha değiştirilmez; bu yüzden onarımlar döngüye giremez.
Bağlantılar, UTF-8 olmayan, 1 MiB'tan büyük veya YAML çapaları kullanan dosyalar yalnızca okunur.

İki JAR'ı da güncelleyin ve sunucuyu ve proxy'yi bir kez yeniden başlatın.

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 58 paketteki 342
test geçti. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3'ü hedefler;
diğer Geyser çekirdek sürümleri doğrulama gerektirir.

Aşağıdaki notlar önceki betayı anlatır.

#### Twilight 1.0.0-beta.3

**Bedrock oyuncuları için daha hafif trafik.** Yeni bir ağ katmanı, Twilight'ın çalıştığı Geyser'ın (bir arka
uçta veya proxy'de) her Bedrock bağlantısında çalışır. Bir oyuncunun göremediği varlıkların (arkasındakilerin
veya en az üç katı bloktan oluşan duvarların ardındakilerin) hareketi ve verileri bekletilir, birleştirilir ve
varlık görüşe girdiğinde veya birkaç saniyede bir tek bir güncel güncelleme olarak gönderilir. Uzaktaki
varlıklar daha az hareket güncellemesi alır, her bağlantının bir güncelleme bütçesi vardır ve zorlanan
bağlantılar daha güçlü sıkıştırma alır. Her sınır varsayılan olarak `auto` olur ve oyuncunun ping'ini ve
istemcinin ne kadar yetişebildiğini izler; yakındaki varlıklar, ad etiketi olan varlıklar ve görüntü
varlıkları duvarlarla asla bekletilmez ve varlık hareketi ve verileri dışında hiçbir şey bekletilmez veya
yeniden sıralanmaz. Hareket eden 1200 mobluk bir testte varlık güncellemelerinin yüzde 59'u paket başına
yaklaşık 1,5 mikrosaniyeyle düşürüldü ve istemci sonunda tam olarak sunucunun durumuyla kaldı.

Ayarlar `bedrock-network` bölümündedir (iki yapılandırmada da; her anahtar varsayılan olarak `auto` veya
açıktır). `/twilight network` ve `/twilightproxy network` etkisini oyuncu başına gösterir. Görüş çizgisi iş
parçacıkları arka uçta spigot.yml'deki `netty-threads` değerini, proxy'de işlemci sayısını izler.

İki JAR'ı da güncelleyin ve sunucuyu ve proxy'yi bir kez yeniden başlatın.

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 55 paketteki 318
test geçti. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3'ü hedefler;
diğer Geyser çekirdek sürümleri doğrulama gerektirir.

Aşağıdaki notlar önceki betayı anlatır.

#### Twilight 1.0.0-beta.2

**Hologramlar, görüntüler ve düşen eşyalar Java gibi.** FancyHolograms, DecentHolograms, mobilya eklentileri ve
görüntü yapıları, eklentiler onları yalnızca paket olarak gönderdiğinde de Bedrock'ta Java'daki gibi görünür:
blok görüntüleri bloğun modeliyle (Java gibi döndürülmüş ve renklendirilmiş), her eşyanın eşya görüntüsü (düz
eşyalar Java'nın 3B sprite'ı olarak, özel verisi olmayanlar kendi modelleriyle), kameraya dönen billboard'lar ve
parlaklık geçersiz kılmaları. 3B modeli olan düşen özel eşyalar yerde modelleriyle durur, zıplayıp döner. Asimetrik
görüntü modelleri artık ayna görüntüsü gibi görünmez.

**Kendi skinleriyle oyuncu kafaları.** Her eklentinin menülerindeki, ellerdeki, zırh standlarındaki ve
hologramlardaki kafalar Steve'inki yerine kendi skinleriyle görünür; sunucuda ve proxy üzerinden. Twilight
menülerin ve dünyanın gösterdiği kafaları öğrenir, eklenti yapılandırmalarının adını verdiklerini okur (Base64
dokular, skin URL'leri, kullanıcı adları, HeadDatabase kimlikleri) ve katılan oyuncuların skinlerini tutar;
böylece oyuncu kafaları (`head-%player_name%`, sıralama listeleri) da kendi skinleriyle görünür. Geyser kafaları
açılırken kaydeder: bir sunucunun gösterdiği yeni kafalar yeniden başlatma gerektirir; yeni oyuncuların skinleri
ise bir sonraki açılışla gelir.

**Menü başlıkları.** Görsel veya offset içermeyen başlıklar artık kaydırılmaz: masaüstünde ve telefonlarda
Java'nın konumunda görünür, artık kaymaz veya kaybolmaz. `/twilight menus` oyuncuların açtığı menüleri analiz eder:
düz veya yerleştirilmiş başlıklar, bir eklentinin değiştirmeden bıraktığı offset etiketleri, paketin görseli
olmayan karakterler, menü başına kafalar ve özel eşyalar (`reports/menus.json`).

**Lisans.** Twilight, [Twilight License](LICENSE) ile, yani ek koşullarla birlikte PolyForm Noncommercial
License 1.0.0 ile lisanslanmıştır. Herkes onu para kazananlar dahil kendi sunucularında ve ağlarında derleyip
çalıştırabilir. Hangi adla olursa olsun satılması yasaktır; herhangi bir parçasını, tek bir kod bloğu bile
olsa, kopyalayan, alıntılayan veya uyarlayan herkes Twilight'ı kaynak olarak göstermelidir ([NOTICE](NOTICE)).

İki JAR'ı da güncelleyin ve sunucuyu ve proxy'yi bir kez yeniden başlatın.

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 52 paketteki 295
test geçti. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3'ü hedefler;
diğer Geyser çekirdek sürümleri doğrulama gerektirir.

Aşağıdaki notlar önceki betayı anlatır.

#### Twilight 1.0.0-beta.1

Twilight'ın ilk herkese açık betası: istemci modu olmadan, Geyser üzerinden Bedrock oyuncularına Java özel
içeriği. Twilight sunucunuzun eklentilerini, datapack'lerini ve kaynak paketlerini (ItemsAdder, CraftEngine,
Nexo, Oraxen, BetterModel, ModelEngine, RealisticSeasons, CustomNameplates) okur, Bedrock paketini ve
Geyser'ın eşya eşlemelerini derler ve Bedrock oyuncularına Java oyuncularının gördüğü eşyaları, blokları,
modelleri, menüleri ve yazıları gösterir.

**Öne çıkanlar**
- Özel eşyalar, 3B blok eşyaları ve nota bloğu, mantar bloğu ve tuzak teli üzerindeki özel bloklar.
- Java font ölçüleriyle yerleştirilen menüler ve yazılar: negatif ve pozitif boşluklar, kaydırma etiketleri,
  katmanlı menü görselleri ve telefonlarda da Java'nın sandık yerleşimi.
- Mobilyalar ve ModelEngine, BetterModel ve MythicMobs mobları ve NPC'leri; yalnızca paket olarak
  gönderilenler de.
- Geyser'ın proxy'de olduğu Velocity ve BungeeCord türevleri için `TwilightProxy.jar`: her Bedrock oyuncusu
  katıldığı sunucunun paketini alır.
- Yerleşik paket barındırıcı üzerinden hızlı paket indirmeleri.

**Kurulum.** `Twilight.jar`'ı her arka uç sunucuya, Geyser'ın proxy'de olduğu bir ağda da `TwilightProxy.jar`'ı
proxy'ye koyun; ikisi de aynı sürümde olsun. İlk derlemeden sonra bir kez yeniden başlatın: Geyser özel
eşyaları yalnızca açılışta kaydeder. Daha önceki bir geliştirme derlemesini çalıştıran sunucular bu sürümü
duyurmaz; iki JAR'ı bir kez elle değiştirin. Yapılandırma için [wiki'ye](WIKI.md), bilinen sınırlar için
[uyumluluk notlarına](docs/COMPATIBILITY.md) bakın.

**Lisans.** Twilight, [Twilight License](LICENSE) ile, yani ek koşullarla birlikte PolyForm Noncommercial
License 1.0.0 ile lisanslanmıştır. Herkes onu para kazananlar dahil kendi sunucularında ve ağlarında derleyip
çalıştırabilir. Twilight'ı, bir kopyasını, değiştirilmiş bir sürümünü veya adı değiştirilmiş bir kopyasını
satmak yasaktır; herhangi bir parçasını kopyalayan, alıntılayan veya uyarlayan herkes Twilight'ı kaynak
olarak göstermelidir ([NOTICE](NOTICE)).

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 49 paketteki 279
test geçti. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3'ü hedefler;
diğer Geyser çekirdek sürümleri doğrulama gerektirir.
