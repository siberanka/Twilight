# Pack loading and proxy glyphs - 10 October 2026

> Türkçe: [aşağıda](#türkçe)

Two reports from a network with Geyser on the proxy, both fixed in development build 19:

1. On Survival, Bedrock players were dropped before the resource packs finished loading. It took very
   long, and only on that server.
2. On BoxPVP, an ItemsAdder image used as the prefix of nearly every message showed on Java, while
   Bedrock showed a plain Unicode character in its place.

Both were reproduced locally first. The Survival build folder from the report was measured in a real
Bedrock client (Windows) through Velocity with Geyser and twilight-proxy. BoxPVP was rebuilt from a
read-only copy of its sources.

## 1. Survival: dropped while the packs load

### Cause

The pack was valid, so it was not the client's fault. Most of the client's loading work was content
that a network with Geyser on the proxy never uses.

A Bedrock client builds every model, animation and entity of a pack after it joins. While it does so, it
answers nothing. Survival's pack from the report had 25,238 files: 37 MiB packed, 137 MiB unpacked. 6,829
of these files were a second copy of every 3D item, for **item displays**: one entity with 3,413
variants (`entity/twilight_display.entity.json`) and their geometries and animations. These
exist only for Twilight's display bridge, which shows furniture and model bones on Bedrock, and that
bridge runs only where Geyser runs on the backend. On a proxy network, Geyser on the proxy never uses
them. Every client still built them on each join. Indentation also made the pack's JSON files about
twice as large as needed.

While the client is busy, the Java side keeps waiting. With Geyser's `forward-player-ping: true`, Java
keep-alives wait for the Bedrock client, and Velocity dropped the player after 30 s with "read timed
out". Without that option, the server keeps the player, but a phone that stays frozen for minutes can
still lose its own connection.

### Measurements

These were measured with a desktop Windows client, with the pack sent by Geyser (no pack host). Each row
removes more than the previous one:

| Pack | Size | Download and login | Loading after joining |
|---|---|---|---|
| From the report | 37 MiB, 25,238 files | 1 s (already cached) | 156 s; without protection "read timed out" |
| Glyph pages capped at 4096 pixels | 35 MiB | 115 s | 151 s |
| Without the display entity | 36 MiB | 89 s | 45 s |
| Without the display entity, geometries and animations | 31 MiB | 83 s | 13 s |
| The same with compact JSON | 27 MiB | 72 s | 10 s |
| **Built by development build 19** from a local copy of Survival's sources | 27 MiB, 7,559 files, 41 MiB unpacked | 55 s | under 10 s |

The glyph pages were not the cause. Survival has seven pages of 8192x8192 pixels, each 256 MiB in the
client's memory. That matters on phones with little memory, but capping them barely changed the loading
time.

### Fixes

- **Item display models only where they are used.** The new `geyser.item-display-models` setting
  defaults to `auto`: the models are built only when Geyser runs on that server. Backends of a proxy
  network now build the pack without them. Held and worn items keep their models.
- **Compact JSON.** Every JSON file in the pack is written without indentation.
- **Loading protection, on the backend and on the proxy.** Until the client reports that it is in game,
  Java keep-alives and pings are answered for it, at most `loading-protection-seconds` (default 300,
  0-1800). After that, the normal timeouts apply again. Loads of 10 s or more are logged:
  - "&lt;player&gt; finished loading its resource packs after N s; the connection was kept alive for it
    meanwhile."
  - "&lt;player&gt; left while its client was still loading the resource packs, after N s: &lt;reason&gt;"
- **Glyph pages.** The build names pages of 8192x8192 pixels. `ui.max-glyph-cell: 256` makes them
  4096x4096 by moving images that Java draws far above or below the line, so that they fit.

## 2. BoxPVP: the prefix shows as a Unicode character

### Cause

The prefix is an ItemsAdder font image on U+A840. That code point belongs to a script (Phags-pa) that
Bedrock draws with its own font, and Bedrock cannot replace that font. Twilight therefore draws such
images in the private-use area (here U+F701) and changes the character in each message before the
Bedrock client gets it. The pack itself was correct.

That change, along with the rest of Twilight's text runtime (Java font layout and the packs'
translations), ran only in the Geyser on the backend. On BoxPVP, Geyser runs on the proxy, so the
messages reached Bedrock with U+A840 unchanged. BoxPVP has three such images.

### Fix

twilight-proxy now runs the text runtime in the proxy's Geyser, through the new `bedrock` section:
`text-layout`, `translations` and `loading-protection-seconds`. For each Bedrock player, it uses the
layout of the pack that player loaded. When the player switches servers, the layout switches with the
pack. In the test, BoxPVP's prefix showed as its image in chat.

## After updating

Everything happens on its own:

- The first build after the start (`auto-build-on-startup`) makes the new pack: without display models on
  backends without Geyser, and with compact JSON. twilight-proxy picks it up as before. The item mappings
  do not change, so Geyser does not need a restart.
- Existing configuration files keep working. Missing keys use the defaults above.
- Stale Twilight files in Geyser are still moved out (development build 18).

Recommended:

- Enable the [pack host](../WIKI.md#pack-hosting) on the proxy. Geyser sends at most about 1.2 MiB/s, so
  most of the 55 s above is the download.
- If phone players still load slowly, set `ui.max-glyph-cell: 256` on Survival.

## Automated checks

247 tests pass (217 Twilight, 30 proxy). New tests cover:

- a pack without display models, and compact JSON;
- the glyph cell cap, including the image that moves;
- the same Bedrock item on two servers with different display options (logged as information, not as a
  conflict).

The loading protection and the proxy text runtime were checked live, as described above.

---

## Türkçe

### Paket yükleme ve proxy glifleri - 10 Ekim 2026

Geyser'ın proxy'de çalıştığı bir ağdan gelen ve geliştirme derlemesi 19'da düzeltilen iki bildirim:

1. Survival'da Bedrock oyuncuları, kaynak paketleri yüklenmeden atılıyordu. Yükleme çok uzun sürüyordu ve
   yalnızca o sunucuda oluyordu.
2. BoxPVP'de neredeyse her mesajın önekinde kullanılan bir ItemsAdder görseli Java'da görünüyordu; Bedrock
   ise onun yerine düz bir Unicode karakteri gösteriyordu.

İkisi de önce yerelde yeniden üretildi. Rapordaki Survival derleme klasörü, Velocity, Geyser ve twilight-proxy
üzerinden gerçek bir Bedrock istemcisinde (Windows) ölçüldü. BoxPVP, kaynaklarının salt okunur bir
kopyasından yeniden derlendi.

#### 1. Survival: paketler yüklenirken atılma

##### Neden

Paket geçerliydi, yani sorun istemcide değildi. İstemcinin yükleme işinin çoğu, Geyser'ı proxy'de çalışan bir
ağın hiç kullanmadığı içerikti.

Bir Bedrock istemcisi, katıldıktan sonra paketin her modelini, animasyonunu ve varlığını kurar. Bunu yaparken
hiçbir şeye yanıt vermez. Rapordaki Survival paketinde 25.238 dosya vardı: sıkıştırılmış 37 MiB, açılmış
137 MiB. Bu dosyaların 6.829'u, **eşya görüntüleri** için her 3B eşyanın ikinci bir kopyasıydı: 3.413
varyantlı tek bir varlık (`entity/twilight_display.entity.json`) ile bu varyantların geometrileri ve
animasyonları. Bunlar yalnızca Twilight'ın görüntü köprüsü içindir; köprü mobilyaları ve model kemiklerini
Bedrock'ta gösterir ve yalnızca Geyser'ın arka uçta çalıştığı yerde çalışır. Proxy'li bir ağda, proxy'deki
Geyser bunları hiç kullanmaz. Yine de her istemci her katılışta bunları kuruyordu. Girintiler ayrıca paketin
JSON dosyalarını gerekenin yaklaşık iki katı büyüklüğe çıkarıyordu.

İstemci meşgulken Java tarafı beklemeye devam eder. Geyser'da `forward-player-ping: true` ile Java
keep-alive'ları Bedrock istemcisini bekler ve Velocity oyuncuyu 30 saniye sonra "read timed out" ile attı.
Bu seçenek kapalıyken sunucu oyuncuyu tutar; ama dakikalarca donup kalan bir telefon yine kendi bağlantısını
kaybedebilir.

##### Ölçümler

Ölçümler masaüstü Windows istemcisiyle, paket Geyser tarafından gönderilerek (paket sunucusu olmadan) yapıldı.
Her satır, bir öncekinden daha fazlasını çıkarır:

| Paket | Boyut | İndirme ve giriş | Katıldıktan sonra yükleme |
|---|---|---|---|
| Rapordaki | 37 MiB, 25.238 dosya | 1 sn (önbellekte vardı) | 156 sn; koruma olmadan "read timed out" |
| Glif sayfaları 4096 piksele sınırlı | 35 MiB | 115 sn | 151 sn |
| Görüntü varlığı olmadan | 36 MiB | 89 sn | 45 sn |
| Görüntü varlığı, geometrileri ve animasyonları olmadan | 31 MiB | 83 sn | 13 sn |
| Aynısı, sıkışık JSON ile | 27 MiB | 72 sn | 10 sn |
| Survival kaynaklarının yerel bir kopyasından **geliştirme derlemesi 19 ile derlenmiş** | 27 MiB, 7.559 dosya, açılmış 41 MiB | 55 sn | 10 sn'den az |

Sorunun nedeni glif sayfaları değildi. Survival'da 8192x8192 piksellik yedi sayfa var ve her biri istemci
belleğinde 256 MiB tutuyor. Bu, belleği az olan telefonlarda önemlidir; ama sayfaları sınırlamak yükleme
süresini neredeyse hiç değiştirmedi.

##### Düzeltmeler

- **Eşya görüntüsü modelleri yalnızca kullanıldıkları yerde.** Yeni `geyser.item-display-models` ayarının
  varsayılanı `auto`'dur: modeller yalnızca Geyser o sunucuda çalışıyorsa derlenir. Proxy'li bir ağın arka
  uçları artık paketi bunlar olmadan derler. Elde tutulan ve giyilen eşyalar modellerini korur.
- **Sıkışık JSON.** Paketteki her JSON dosyası girintisiz yazılır.
- **Arka uçta ve proxy'de yükleme koruması.** İstemci oyunda olduğunu bildirene kadar Java keep-alive'ları ve
  ping'leri onun yerine yanıtlanır, en fazla `loading-protection-seconds` süresince (varsayılan 300, 0-1800).
  Bu sürenin ardından olağan zaman aşımları yeniden geçerli olur. 10 saniye veya daha uzun süren yüklemeler
  günlüğe yazılır:
  - "&lt;oyuncu&gt; finished loading its resource packs after N s; the connection was kept alive for it
    meanwhile."
  - "&lt;oyuncu&gt; left while its client was still loading the resource packs, after N s: &lt;neden&gt;"
- **Glif sayfaları.** Derleme 8192x8192 piksellik sayfaları adlarıyla bildirir. `ui.max-glyph-cell: 256`,
  Java'nın satırın çok üstüne veya altına çizdiği görselleri sığacak şekilde kaydırarak bu sayfaları
  4096x4096 yapar.

#### 2. BoxPVP: önek Unicode karakteri olarak görünüyor

##### Neden

Önek, U+A840 üzerindeki bir ItemsAdder font görselidir. Bu kod noktası, Bedrock'un kendi fontuyla çizdiği
bir yazı sistemine (Phags-pa) aittir ve Bedrock bu fontun yerine başkasını koyamaz. Bu yüzden Twilight böyle
görselleri özel kullanım alanında (burada U+F701) çizer ve her mesajdaki karakteri, mesaj Bedrock
istemcisine ulaşmadan önce değiştirir. Paketin kendisi doğruydu.

Bu değişiklik, Twilight'ın yazı çalışma zamanının geri kalanıyla (Java font yerleşimi ve paketlerin
çevirileri) birlikte yalnızca arka uçtaki Geyser'da çalışıyordu. BoxPVP'de Geyser proxy'de çalışır; bu yüzden
mesajlar Bedrock'a U+A840 değişmeden ulaştı. BoxPVP'de böyle üç görsel var.

##### Düzeltme

twilight-proxy artık yazı çalışma zamanını, yeni `bedrock` bölümü üzerinden proxy'deki Geyser'da çalıştırır:
`text-layout`, `translations` ve `loading-protection-seconds`. Her Bedrock oyuncusu için o oyuncunun yüklediği
paketin yerleşimini kullanır. Oyuncu sunucu değiştirdiğinde yerleşim de paketle birlikte değişir. Testte
BoxPVP'nin öneki sohbette görsel olarak göründü.

#### Güncellemeden sonra

Her şey kendiliğinden olur:

- Açılıştan sonraki ilk derleme (`auto-build-on-startup`) yeni paketi üretir: Geyser olmayan arka uçlarda
  görüntü modelleri olmadan ve sıkışık JSON ile. twilight-proxy onu önceden olduğu gibi alır. Eşya eşlemeleri
  değişmez, bu yüzden Geyser'ın yeniden başlatılması gerekmez.
- Mevcut yapılandırma dosyaları çalışmaya devam eder. Eksik anahtarlar yukarıdaki varsayılanları kullanır.
- Geyser'daki eski Twilight dosyaları yine taşınır (geliştirme derlemesi 18).

Öneriler:

- Proxy'de [paket sunucusunu](../WIKI.md#paket-sunucusu) açın. Geyser en fazla yaklaşık 1,2 MiB/s gönderir;
  yukarıdaki 55 saniyenin çoğu indirmedir.
- Telefon oyuncuları hâlâ yavaş yüklüyorsa Survival'da `ui.max-glyph-cell: 256` ayarlayın.

#### Otomatik denetimler

247 test geçti (217 Twilight, 30 proxy). Yeni testler şunları kapsar:

- görüntü modelleri olmayan bir paket ve sıkışık JSON;
- glif hücresi sınırı ve kaydırılan görsel;
- iki sunucuda farklı görüntü seçenekleriyle aynı Bedrock eşyası (çakışma olarak değil, bilgi olarak
  günlüğe yazılır).

Yükleme koruması ve proxy'deki yazı çalışma zamanı, yukarıda anlatıldığı gibi canlı olarak denetlendi.
