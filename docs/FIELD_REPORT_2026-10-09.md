# Field report fixes - 9 October 2026

> Türkçe: [aşağıda](#türkçe)

A network running Twilight development build 13 (Java 25, Geyser 2.11.3 build 1249, Floodgate build 141,
GeyserReversion 1.0.6) reported failed conversions on its backends, twilight-proxy not seeing the
proxy's Geyser, a possible item registry conflict between backends and an empty `login-servers`
list. Each point was reproduced on isolated local servers before it was fixed in development build 15.
Production servers were not modified.

## 1. Conversions failed with `java.time.Instant#seconds`

**Cause.** The content report (`reports/content-report.json`) was written with Gson's reflection.
The Gson that Paper 1.21 and its forks bundle (2.11) cannot open `java.time` on Java 17 and later,
so every scan stopped before the build and no new Bedrock pack was made. Paper 26 bundles a newer
Gson, which is why the test servers did not show it.

**Reproduced** on a copy of a Leaf 1.21.8 server with Gson 2.11 and Java 25: the same exception at
`ContentReportWriter.java:22`.

**Fix.** The report is built field by field without reflection, and a report that cannot be written
only logs a warning; it never stops a build again. On the same server development build 15 writes the report
and passes scan and build. A unit test writes the report with Gson 2.11 on Java 25.

## 2. "Geyser is not installed on this proxy" while Geyser runs

**Cause.** On Velocity, Geyser creates its API in its own `ProxyInitializeEvent` handler, the same
event in which twilight-proxy attached. When twilight-proxy's handler ran first, Geyser's API did not
exist yet, the failure was silenced and reported as "not installed": no packs per server, no
reconnects and no pack host.

**Reproduced** on Velocity 4.2.0 with Geyser build 1248 by making twilight-proxy load before Geyser:
build 14 logged "Geyser is not installed on this proxy" and `/twilightproxy` "Geyser absent" while
Geyser started two seconds later.

**Fix.**

- twilight-proxy now initialises after every other plugin on Velocity.
- A Geyser that is not ready is tried again every two seconds for two minutes, then whenever a player
  joins.
- Absent, not ready and incompatible are told apart and logged with the reason.
- A failure after the first listener was registered removes it again.
- The pack host starts as soon as Geyser is attached.
- `/twilightproxy` shows "Geyser attached" or "Geyser not attached (reason)".

With the same load order build 15 attached at once. BungeeCord, where Geyser creates its API earlier,
attached as before. Four tests cover a late Geyser, an incompatible one, an absent one, and the
attempt when a player joins.

**Follow-up (development build 16).** The message came from FlameCord, a closed Waterfall fork that could not
be obtained. The tests used Waterfall 1.21 build 615 instead, the open project FlameCord is built on:

- Builds 13 and 16 both found Geyser there.
- On BungeeCord and Waterfall, Geyser creates its API while plugins load, before twilight-proxy
  starts.
- On FlameCord the remaining explanation is that the proxy hides Geyser's classes from other plugins.

Since build 16 twilight-proxy asks the proxy whether a Geyser plugin is installed. It then reports
"Geyser-BungeeCord ... is installed, but its API is not visible to twilight-proxy" with the exact
error and keeps trying, instead of "not installed". The backend's "Geyser unavailable: No local Geyser
plugin data directory was found" in `/twilight status` was misleading too. It now reads "Geyser is not
on this server (normal when it runs on the proxy)", says whether the pack is shared with twilight-proxy,
and is also logged at start.

**Root cause (development build 17).** With build 16 the network's log named the error:

> loader constraint violation when resolving `EventBus.subscribe(...)` returning
> `org.geysermc.event.subscribe.OwnedSubscriber`

Floodgate bundles its own unrelocated copy of Geyser's event library (`org.geysermc.event`). When a proxy
loads Floodgate before Geyser, twilight-proxy's classes resolve that library from Floodgate's jar, while
Geyser's event bus uses its own copy, and the JVM refuses the call.

Reproduced locally in two setups, both with Floodgate 2.2.5 build 141 loaded before Geyser:

- Waterfall 1.21 build 615 with Geyser-BungeeCord made to load after Floodgate;
- Velocity 4.2.0, which loads Floodgate first by itself (alphabetical order, no dependency between them).

Build 16 failed with the same message in both. Every earlier version is affected the same way on such
proxies, and Twilight on a backend could be hit in the same way on a server that loads Floodgate first.

**Fix.** Twilight and twilight-proxy no longer refer to Geyser's event library at all:

- They subscribe through reflection, with the method and `PostOrder` taken from the class loader that
  defined Geyser's event bus.
- A test scans every compiled class and fails if any refers to `org.geysermc.event`. The build 16 classes
  would fail it.
- An attach failure is now logged with its full stack trace and with the jar each side took the
  library from.

**Verified with build 17.**

| Setup | Result |
|---|---|
| Velocity + Floodgate | Attached, wrote 133 item mappings (Geyser registered 134); a Floodgate player (`.SiberAnka`) switched lobby → survival → lobby with one reconnect each (6 s and 5 s) |
| Waterfall with Floodgate first | Attached; pack host started; the session's pack was served from it; lobby → survival → lobby in 6 s and 5 s |
| Backend with Floodgate and Geyser-Spigot | All Twilight bridges registered; the biome mapping ran for a Bedrock session |

The pack host answered unknown paths and random tokens with an empty 404 and blocked the address after
repeated guessing.

**Mapping files from an older sync tool.** A `twilight_network_item_mappings.json` written by a custom
sync tool (identifiers `twilight:n_<32 hex>`) was still in Geyser's `custom_mappings`. twilight-proxy now:

- names such files;
- counts the Java selectors they map to other Bedrock items than the servers' packs;
- shows them in `/twilightproxy` ("remove ...").

Stop that tool (for example its systemd `.path`/`.service` units) and remove its file. twilight-proxy
writes `twilight-proxy_item_mappings.json` itself.

**Login plugin folders left behind.** A leftover `plugins/AuthMeBungee/config.yml`, without the plugin,
made `lobi` a login server. twilight-proxy now reads only the folders of plugins the proxy actually
loaded, and logs "Not reading plugins/AuthMeBungee: that plugin is not installed" for the others.

## 3. Item registry across backends

**Cause.** Geyser registers custom items once per Geyser. On a proxy network that is the proxy's
Geyser, for every backend, but:

- the backends' item mappings never reached it;
- the Bedrock item of a Java selector depended on the model the backend's pack draws.

So custom items showed as their base item unless mappings were copied by hand. Copied mappings could
also conflict: the same custom model data drawn by different models on two backends became two
Bedrock items for one Java selector.

**Fix.**

- The Bedrock item of a Java selector now depends only on what Geyser matches: item, custom model
  data or item model, and predicates. The same selector is therefore the same Bedrock item on every
  backend, and each server's pack decides how it looks there.
- Twilight puts its mappings into the pack it shares.
- twilight-proxy merges the mappings of every `auto` and `packs/` source and writes
  `custom_mappings/twilight-proxy_item_mappings.json` in the proxy's Geyser. It does so before Geyser
  reads that folder at start; a later change asks for a restart.
- Selectors that two servers still map differently are listed. Copies made by hand are named.
- A warning appears when the proxy's locale keeps Geyser from reading the mappings.

**Verified live.**

- Velocity: 133 items from the lobby backend, Geyser "Registered 134 custom items" (one of its own).
  A Bedrock player on the lobby saw the converted icon of an item-model item next to the plain base
  item.
- BungeeCord: two items from a pack file, "Registered 3 custom items".
- A conflicting second pack was reported with both Bedrock items and the kept one.
- Changing a pack while the proxy ran logged the restart request.
- Single-server layout (Geyser on the backend): the rebuilt pack and mappings deployed, Geyser
  registered all 134 items and the Bedrock client showed the converted icon.

**Found during the test.** On this Turkish Windows test machine the proxy's Geyser skipped every item
model mapping ("unknown element in enum ItemDefinitionReaders ... (node was "definition")"), because it
upper-cases with the system locale. With `-Duser.language=en -Duser.country=US` it registered all of
them. twilight-proxy now warns about this; Twilight already warned on backends.

## 4. Empty `login-servers`

**Fix.** twilight-proxy reads the login server from the configuration of login plugins on the proxy:

- LeaderOS Auth (`auth-server`);
- AuthMeVelocity and AuthMeBungee (`auth-servers`, `authServers`);
- LibreLogin (`limbo`);
- JPremium and similar plugins.

Only plugin folders whose name points to a login plugin are read, and only names of servers the proxy
has are taken. Each server found is logged ("Login server auth_lobby (found in
plugins/LeaderOS-Auth/config.yml)") and shown by `/twilightproxy`. Login plugins that pick such a
server at run time were already detected.

Verified on BungeeCord with LeaderOS Auth's configuration, and by tests with LeaderOS, AuthMeVelocity,
AuthMeBungee and LibreLogin files and an unrelated plugin that is not read.

## 5. Security notes

- Item mappings are taken only from signed `auto` packs and from files the administrator put in
  `packs/`, never from download links.
- Only Geyser's documented keys are kept, with strict patterns for Java items, models, Bedrock
  identifiers (`twilight:` only) and icons, and limits on size and count. Clients cannot send or
  change them.
- Login server detection reads a few keys of a few files and writes nothing.
- No change affects forwarding secrets, LeaderOS signatures or backend access.

## 6. Broken item icons after updating (development build 18)

After updating without other changes, custom item icons looked broken. Two things lead there:

- Geyser still had the old sync tool's `twilight_network_item_mappings.json`, with the old Bedrock
  identifiers. Geyser loads every `.json` under `custom_mappings`, recursively, and every pack in `packs`.
- Geyser registers custom items only when it starts, and nothing told the admins that it had to.

Development build 18 handles both on proxies and single servers:

- **Stale files.** Twilight files that the current setup does not own are moved to `retired/<time>/`,
  never deleted, before Geyser loads them: Twilight's load phase on a server, the attach on a proxy.
- **Backends on the same machine.** They are read from their folders: matched by port and Twilight
  installation, their exports are loaded at proxy start and within seconds after every build. No player
  has to join first.
- **Restarts.** A needed restart is shown to admins (console, online admins, admins joining,
  `/twilightproxy`), and `when-empty` restarts once nobody is online.

**Verified live.**

| Setup | Result |
|---|---|
| Velocity | With no backend running and no player, the lobby's export was read from its folder; an old mapping file and an old Twilight pack were moved out; Geyser registered all 134 items |
| Velocity, rebuilt export | Changing the lobby's export while the proxy ran was picked up 20 s later. With `restart: when-empty` and nobody online, the proxy stopped itself a minute later |
| Waterfall with Floodgate first | Found the same backend and moved the stale file |
| Paper with Geyser-Spigot | Moved a stale mapping file and pack in Twilight's load phase, before Geyser registered its items |
| Paper, `restart-for-item-changes: when-empty` | A deployment that changed items was announced; 90 s later, with nobody online, Twilight restarted the server (it stopped, as there was no restart script) |

## Not covered

- GeyserReversion was not installed in the tests. Its documentation says it does not work with servers
  that use custom blocks.
- Real Bedrock logins with two-factor authentication, inventory and crafting, and malicious-pack tests
  remain to be run on the network itself.

## Automated checks

234 tests pass (212 Twilight, 22 proxy), 13 of them new:

- the report written with Gson 2.11;
- the same Bedrock item for one selector on two backends, and the mappings inside the pack;
- the attach cases;
- merging, conflicts, malformed and hostile mappings, and reading mappings from packs;
- writing mappings before and after Geyser registered its items;
- locales that break Geyser;
- login server detection.

---

## Türkçe

### Saha raporu düzeltmeleri - 9 Ekim 2026

Twilight geliştirme derlemesi 13 çalıştıran bir ağ (Java 25, Geyser 2.11.3 build 1249, Floodgate build 141,
GeyserReversion 1.0.6) dört sorun bildirdi:

- arka uçlarında dönüştürmelerin başarısız olduğunu;
- twilight-proxy'nin proxy'deki Geyser'ı görmediğini;
- arka uçlar arasında olası bir eşya kayıt çakışmasını;
- boş bir `login-servers` listesini.

Her madde, geliştirme derlemesi 15'te düzeltilmeden önce yalıtılmış yerel sunucularda yeniden üretildi. Üretim
sunucuları değiştirilmedi.

#### 1. Dönüştürmeler `java.time.Instant#seconds` ile başarısız oluyordu

**Neden.** İçerik raporu (`reports/content-report.json`) Gson'un yansımasıyla yazılıyordu. Paper 1.21 ve
türevlerinin içerdiği Gson (2.11), Java 17 ve sonrasında `java.time` paketini açamaz. Bu yüzden her tarama
derlemeden önce durdu ve yeni Bedrock paketi üretilmedi. Paper 26 daha yeni bir Gson içerir; test
sunucularında görülmemesinin nedeni budur.

**Yeniden üretildi.** Gson 2.11 ve Java 25 ile çalışan Leaf 1.21.8 sunucusunun bir kopyasında
`ContentReportWriter.java:22` konumunda aynı hata görüldü.

**Düzeltme.** Rapor yansıma olmadan alan alan oluşturulur. Yazılamayan bir rapor yalnızca uyarı yazar;
artık bir derlemeyi asla durdurmaz. Aynı sunucuda geliştirme derlemesi 15 raporu yazar, taramayı ve derlemeyi geçer.
Bir birim testi raporu Java 25 üzerinde Gson 2.11 ile yazar.

#### 2. Geyser çalışırken "Geyser is not installed on this proxy"

**Neden.** Velocity'de Geyser API'sini kendi `ProxyInitializeEvent` işleyicisinde oluşturur; twilight-proxy
de aynı olayda bağlanıyordu. twilight-proxy'nin işleyicisi önce çalıştığında Geyser'ın API'si henüz yoktu.
Hata susturuldu ve "kurulu değil" olarak bildirildi. Sonuçta sunucu başına paket, yeniden bağlanma ve paket
sunucusu yoktu.

**Yeniden üretildi.** Velocity 4.2.0 ve Geyser build 1248 ile twilight-proxy, Geyser'dan önce yüklenecek
şekilde ayarlandı. Derleme 14 "Geyser is not installed on this proxy", `/twilightproxy` ise "Geyser absent"
yazdı; oysa Geyser iki saniye sonra başladı.

**Düzeltme.**

- twilight-proxy artık Velocity'de diğer bütün eklentilerden sonra başlar.
- Hazır olmayan bir Geyser iki dakika boyunca iki saniyede bir, ardından her oyuncu katıldığında yeniden
  denenir.
- Kurulu değil, hazır değil ve uyumsuz durumları birbirinden ayrılır ve nedeniyle günlüğe yazılır.
- İlk dinleyici kaydedildikten sonra oluşan bir hata, o dinleyiciyi yeniden kaldırır.
- Paket sunucusu Geyser'a bağlanılır bağlanılmaz başlar.
- `/twilightproxy` "Geyser attached" veya "Geyser not attached (neden)" gösterir.

Aynı yükleme sırasıyla derleme 15 hemen bağlandı. Geyser'ın API'sini daha erken oluşturduğu BungeeCord'da
önceki gibi bağlandı. Dört test geç başlayan, uyumsuz ve hiç olmayan bir Geyser'ı ve oyuncu katıldığındaki
denemeyi kapsar.

**Ek (geliştirme derlemesi 16).** Mesaj, temin edilemeyen kapalı kaynaklı bir Waterfall türevi olan FlameCord'dan
geliyordu. Testlerde bunun yerine FlameCord'un temel aldığı açık proje Waterfall 1.21 build 615 kullanıldı:

- Derleme 13 de derleme 16 da orada Geyser'ı buldu.
- BungeeCord ve Waterfall'da Geyser, API'sini eklentiler yüklenirken, twilight-proxy başlamadan önce oluşturur.
- FlameCord için geriye kalan açıklama, proxy'nin Geyser'ın sınıflarını diğer eklentilerden gizlemesidir.

Derleme 16'dan beri twilight-proxy proxy'ye bir Geyser eklentisi kurulu olup olmadığını sorar. Kuruluysa "not
installed" yerine "Geyser-BungeeCord ... is installed, but its API is not visible to twilight-proxy" ifadesini
tam hatayla birlikte bildirir ve denemeye devam eder. Arka uçta `/twilight status` içindeki "Geyser unavailable:
No local Geyser plugin data directory was found" ifadesi de yanıltıcıydı. Artık "Geyser is not on this server
(normal when it runs on the proxy)" der, paketin twilight-proxy ile paylaşılıp paylaşılmadığını söyler ve bu
satır açılışta da günlüğe yazılır.

**Kök neden (geliştirme derlemesi 17).** Derleme 16 ile ağın günlüğü hatayı adıyla verdi:

> `org.geysermc.event.subscribe.OwnedSubscriber` döndüren `EventBus.subscribe(...)` çözülürken
> loader constraint violation

Floodgate, Geyser'ın olay kütüphanesinin (`org.geysermc.event`) yeniden konumlandırılmamış kendi kopyasını
içerir. Bir proxy Floodgate'i Geyser'dan önce yüklediğinde twilight-proxy'nin sınıfları bu kütüphaneyi
Floodgate'in JAR'ından çözer. Geyser'ın olay veri yolu ise kendi kopyasını kullanır ve JVM çağrıyı reddeder.

Yerelde iki kurulumda yeniden üretildi; ikisinde de Floodgate 2.2.5 build 141, Geyser'dan önce yüklendi:

- Floodgate'ten sonra yüklenecek şekilde ayarlanan Geyser-BungeeCord ile Waterfall 1.21 build 615;
- Floodgate'i kendiliğinden önce yükleyen Velocity 4.2.0 (alfabetik sıra, aralarında bağımlılık yok).

Derleme 16 ikisinde de aynı mesajla başarısız oldu. Önceki bütün sürümler bu tür proxy'lerde aynı şekilde
etkilenir. Arka uçtaki Twilight da Floodgate'i önce yükleyen bir sunucuda aynı şekilde etkilenebilirdi.

**Düzeltme.** Twilight ve twilight-proxy artık Geyser'ın olay kütüphanesine hiç başvurmaz:

- Olaylara yansıma ile abone olurlar; yöntem ve `PostOrder`, Geyser'ın olay veri yolunu tanımlayan sınıf
  yükleyicisinden alınır.
- Bir test derlenen her sınıfı tarar ve herhangi biri `org.geysermc.event` paketine başvurursa başarısız
  olur. derleme 16 sınıfları bu testten geçemezdi.
- Bir bağlanma hatası artık tam yığın iziyle ve her tarafın kütüphaneyi hangi JAR'dan aldığıyla günlüğe
  yazılır.

**Derleme 17 ile doğrulandı.**

| Kurulum | Sonuç |
|---|---|
| Velocity + Floodgate | Bağlandı, 133 eşya eşlemesi yazdı (Geyser 134 kaydetti); bir Floodgate oyuncusu (`.SiberAnka`) lobi → survival → lobi geçişlerini her birinde tek yeniden bağlanmayla yaptı (6 sn ve 5 sn) |
| Floodgate'i önce yükleyen Waterfall | Bağlandı; paket sunucusu başladı; oturumun paketi oradan sunuldu; lobi → survival → lobi 6 sn ve 5 sn |
| Floodgate ve Geyser-Spigot bulunan arka uç | Bütün Twilight köprüleri kaydoldu; bir Bedrock oturumu için biyom eşlemesi çalıştı |

Paket sunucusu bilinmeyen yollara ve rastgele belirteçlere boş bir 404 ile yanıt verdi ve tekrarlanan
tahminlerden sonra adresi engelledi.

**Eski bir eşitleme aracının eşleme dosyaları.** Özel bir eşitleme aracının yazdığı
`twilight_network_item_mappings.json` (kimlikler `twilight:n_<32 onaltılık>`) Geyser'ın `custom_mappings`
klasöründe hâlâ duruyordu. twilight-proxy artık:

- bu tür dosyaların adını verir;
- sunucuların paketlerinden farklı Bedrock eşyalarına eşledikleri Java seçicilerini sayar;
- bunları `/twilightproxy` içinde gösterir ("remove ...").

O aracı durdurun (örneğin systemd `.path`/`.service` birimlerini) ve dosyasını kaldırın.
twilight-proxy `twilight-proxy_item_mappings.json` dosyasını kendisi yazar.

**Geride kalan giriş eklentisi klasörleri.** Eklentinin kendisi olmadan geride kalan
`plugins/AuthMeBungee/config.yml`, `lobi` sunucusunu giriş sunucusu yapıyordu. twilight-proxy artık yalnızca
proxy'nin gerçekten yüklediği eklentilerin klasörlerini okur; diğerleri için "Not reading plugins/AuthMeBungee:
that plugin is not installed" yazar.

#### 3. Arka uçlar arasında eşya kaydı

**Neden.** Geyser özel eşyaları her Geyser için bir kez kaydeder. Proxy'li bir ağda bu, her arka uç için
proxy'deki Geyser'dır. Ancak:

- arka uçların eşya eşlemeleri ona hiç ulaşmıyordu;
- bir Java seçicisinin Bedrock eşyası, arka ucun paketinin çizdiği modele bağlıydı.

Bu yüzden eşlemeler elle kopyalanmadıkça özel eşyalar temel eşya olarak göründü. Kopyalanan eşlemeler de
çakışabiliyordu: iki arka uçta farklı modellerle çizilen aynı custom model data, tek bir Java seçicisi için
iki Bedrock eşyası oluyordu.

**Düzeltme.**

- Bir Java seçicisinin Bedrock eşyası artık yalnızca Geyser'ın eşleştirdiği şeylere bağlıdır: eşya, custom
  model data veya item model ve koşullar. Böylece aynı seçici her arka uçta aynı Bedrock eşyasıdır ve orada
  nasıl görüneceğine her sunucunun kendi paketi karar verir.
- Twilight eşlemelerini paylaştığı pakete koyar.
- twilight-proxy her `auto` ve `packs/` kaynağının eşlemelerini birleştirir ve proxy'deki Geyser'a
  `custom_mappings/twilight-proxy_item_mappings.json` yazar. Bunu, Geyser bu klasörü açılışta okumadan önce
  yapar; sonraki bir değişiklik yeniden başlatma ister.
- İki sunucunun hâlâ farklı eşlediği seçiciler listelenir. Elle yapılmış kopyaların adı verilir.
- Proxy'nin dili Geyser'ın eşlemeleri okumasını engellediğinde uyarı yazılır.

**Canlı doğrulandı.**

- Velocity: lobi arka ucundan 133 eşya; Geyser "Registered 134 custom items" yazdı (biri kendisinin).
  Lobideki bir Bedrock oyuncusu, item model kullanan bir eşyanın dönüştürülmüş simgesini temel eşyanın
  yanında gördü.
- BungeeCord: bir paket dosyasından iki eşya; "Registered 3 custom items".
- Çakışan ikinci bir paket, iki Bedrock eşyası ve tutulan eşyayla birlikte bildirildi.
- Proxy çalışırken bir paketin değiştirilmesi, yeniden başlatma isteğini günlüğe yazdırdı.
- Tek sunuculu düzen (Geyser arka uçta): yeniden derlenen paket ve eşlemeler dağıtıldı, Geyser 134 eşyanın
  tümünü kaydetti ve Bedrock istemcisi dönüştürülmüş simgeyi gösterdi.

**Test sırasında bulundu.** Bu Türkçe Windows test makinesinde proxy'deki Geyser, büyük harfe sistem
diliyle çevirdiği için her item model eşlemesini atladı ("unknown element in enum ItemDefinitionReaders
... (node was "definition")"). `-Duser.language=en -Duser.country=US` ile hepsini kaydetti. twilight-proxy
artık bu konuda uyarır; Twilight arka uçlarda zaten uyarıyordu.

#### 4. Boş `login-servers`

**Düzeltme.** twilight-proxy giriş sunucusunu proxy'deki giriş eklentilerinin yapılandırmasından okur:

- LeaderOS Auth (`auth-server`);
- AuthMeVelocity ve AuthMeBungee (`auth-servers`, `authServers`);
- LibreLogin (`limbo`);
- JPremium ve benzeri eklentiler.

Yalnızca adı bir giriş eklentisine işaret eden eklenti klasörleri okunur ve yalnızca proxy'de bulunan
sunucuların adları alınır. Bulunan her sunucu günlüğe yazılır ("Login server auth_lobby (found in
plugins/LeaderOS-Auth/config.yml)") ve `/twilightproxy` tarafından gösterilir. Çalışırken böyle bir sunucuyu
seçen giriş eklentileri zaten algılanıyordu.

BungeeCord'da LeaderOS Auth yapılandırmasıyla doğrulandı. Testler ayrıca LeaderOS, AuthMeVelocity,
AuthMeBungee ve LibreLogin dosyalarını ve okunmayan ilgisiz bir eklentiyi kapsar.

#### 5. Güvenlik notları

- Eşya eşlemeleri yalnızca imzalı `auto` paketlerden ve yöneticinin `packs/` içine koyduğu dosyalardan
  alınır, indirme bağlantılarından asla.
- Yalnızca Geyser'ın belgelenmiş anahtarları tutulur. Java eşyaları, modeller, Bedrock kimlikleri (yalnızca
  `twilight:`) ve simgeler için sıkı kalıplar, boyut ve sayı için sınırlar vardır. İstemciler bunları ne
  gönderebilir ne değiştirebilir.
- Giriş sunucusu algılama birkaç dosyanın birkaç anahtarını okur ve hiçbir şey yazmaz.
- Hiçbir değişiklik yönlendirme anahtarlarını, LeaderOS imzalarını veya arka uç erişimini etkilemez.

#### 6. Güncellemeden sonra bozuk eşya simgeleri (geliştirme derlemesi 18)

Başka bir değişiklik yapmadan güncelledikten sonra özel eşya simgeleri bozuk göründü. Buna iki şey yol açar:

- Geyser'da eski eşitleme aracının eski Bedrock kimliklerini içeren `twilight_network_item_mappings.json`
  dosyası hâlâ duruyordu. Geyser `custom_mappings` altındaki her `.json` dosyasını alt klasörleriyle birlikte
  ve `packs` içindeki her paketi yükler.
- Geyser özel eşyaları yalnızca açılışta kaydeder ve yöneticilere yeniden başlatmaları gerektiğini hiçbir şey
  söylemiyordu.

Geliştirme derlemesi 18 ikisini de proxy'lerde ve tek sunucularda ele alır:

- **Eski dosyalar.** Mevcut kurulumun sahip olmadığı Twilight dosyaları, Geyser onları yüklemeden önce
  `retired/<zaman>/` klasörüne taşınır, asla silinmez: sunucuda Twilight'ın yükleme aşamasında, proxy'de
  bağlanma sırasında.
- **Aynı makinedeki arka uçlar.** Klasörlerinden okunurlar: port ve Twilight kurulumuyla eşleşen dışa
  aktarımları proxy açılışında ve her derlemeden sonra saniyeler içinde yüklenir. Önce bir oyuncunun
  katılması gerekmez.
- **Yeniden başlatmalar.** Gereken bir yeniden başlatma yöneticilere gösterilir (konsol, çevrimiçi yöneticiler,
  katılan yöneticiler, `/twilightproxy`); `when-empty` kimse çevrimiçi değilken yeniden başlatır.

**Canlı doğrulandı.**

| Kurulum | Sonuç |
|---|---|
| Velocity | Hiçbir arka uç çalışmıyor ve hiç oyuncu yokken lobinin dışa aktarımı klasöründen okundu; eski bir eşleme dosyası ve eski bir Twilight paketi taşındı; Geyser 134 eşyanın tümünü kaydetti |
| Velocity, yeniden derlenen dışa aktarım | Proxy çalışırken lobinin dışa aktarımını değiştirmek 20 sn sonra algılandı. `restart: when-empty` ile ve kimse çevrimiçi değilken proxy bir dakika sonra kendini durdurdu |
| Floodgate'i önce yükleyen Waterfall | Aynı arka ucu buldu ve eski dosyayı taşıdı |
| Geyser-Spigot bulunan Paper | Eski bir eşleme dosyasını ve paketi, Geyser eşyalarını kaydetmeden önce Twilight'ın yükleme aşamasında taşıdı |
| Paper, `restart-for-item-changes: when-empty` | Eşyaları değiştiren bir dağıtım duyuruldu; 90 sn sonra, kimse çevrimiçi değilken Twilight sunucuyu yeniden başlattı (yeniden başlatma betiği olmadığı için durdu) |

#### Kapsam dışında kalanlar

- GeyserReversion testlerde kurulu değildi. Kendi belgelerine göre özel blok kullanan sunucularla çalışmaz.
- İki aşamalı doğrulamalı gerçek Bedrock girişleri, envanter ve üretim, kötü niyetli paket testleri ağın
  kendisinde yapılmalıdır.

#### Otomatik denetimler

234 test geçti (212 Twilight, 22 proxy). Bunların 13'ü yenidir:

- Gson 2.11 ile yazılan rapor;
- iki arka uçta bir seçici için aynı Bedrock eşyası ve paketin içindeki eşlemeler;
- bağlanma durumları;
- birleştirme, çakışmalar, bozuk ve kötü niyetli eşlemeler, paketlerden eşleme okuma;
- Geyser eşyalarını kaydetmeden önce ve sonra eşleme yazma;
- Geyser'ı bozan diller;
- giriş sunucusu algılama.
