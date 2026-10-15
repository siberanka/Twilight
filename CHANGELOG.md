# Changelog

> Türkçe: [aşağıda](#türkçe)

All notable changes in Twilight are documented here.

## 1.0.0-beta.2 - unreleased

- Displays and holograms (FancyHolograms, DecentHolograms, furniture plugins, display builds), also when sent
  only as packets: block displays show the block state's model, rotated and tinted like Java; item displays show
  every item, flat ones as Java's one-pixel extrusion and items without custom data with their own model; the
  billboard modes face the camera and the brightness override sets the light as on Java. Each new item or block
  state is learned when a display first shows it and added by the next build.
- Dropped items of custom items with a 3D model lie on the ground with their model, bobbing and spinning like
  Java; other dropped items stay Bedrock's own.
- Display models no longer show a box's east texture on its west side (an item display or block display of a
  crafting table or a furnace looked mirrored).

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

#### 1.0.0-beta.2 - yayımlanmadı

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
