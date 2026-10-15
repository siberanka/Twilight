# Twilight 1.0.0-beta.1

> Türkçe: [aşağıda](#türkçe)

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

### Twilight 1.0.0-beta.1

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
