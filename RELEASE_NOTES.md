# Twilight 1.0.0-beta.2

> Türkçe: [aşağıda](#türkçe)

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

## Twilight 1.0.0-beta.1

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

### Twilight 1.0.0-beta.2

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
