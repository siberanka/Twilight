# Twilight compatibility contract

> Türkçe: [aşağıda](#türkçe)

This document separates implemented behavior from planned compatibility. A provider is supported only when its public API or generated standard assets pass the same conversion and validation pipeline; directory recognition alone is discovery support.

The [3 October review](COMPATIBILITY_REVIEW_2026-10-03.md) and
[acceptance catalog](acceptance/catalog.json) define the broader required test
surface. Planned scenarios do not change the support statuses below.

## Runtime platforms

| Platform | Status | Notes |
|---|---|---|
| Paper 1.21.4+ | Implemented | Primary compile and runtime target. |
| Folia 1.21.4+ | Implemented core | Global scheduler adapter; conversion work runs on a dedicated worker. Runtime entity work must remain region-safe. |
| Spigot 1.21.4+ | Implemented core | Bukkit scheduler fallback; no Paper-only hard link at runtime. |
| Fabric client | Unsupported | Retired. Twilight is server-only. |

Java 21 is the minimum bytecode level. Local builds use Java 25.

## Proxies (twilight-proxy)

| Platform | Status |
|---|---|
| Velocity 4.2.0 with Geyser 2.11.3 | Tested live: `auto`, file and switching packs, modern forwarding secret |
| Paper 26.3 with Geyser-Spigot 2.12.0, Floodgate and CraftEngine 26.9.2 | Tested live: items, tools, bows, crossbows, rods, spears, custom blocks and ores, block items, furniture, dropped items, first and third person (Geyser 2.12.0 builds before b1262 did not show custom items) |
| BungeeCord 26.1 with Geyser 2.11.3 | Tested live: `auto`, file and switching packs, explicit secret |
| Waterfall 1.21 (build 615) with Floodgate loaded first | Tested live: Geyser attach, item mappings, stale-file retirement, and the start of the proxy's text runtime |
| FlameCord | Reported from a production network (Geyser attach and item mappings); not tested locally |
| Velocity 3.x, older BungeeCord | Uses only long-standing API (`order` subscriptions, `ServerConnectEvent` with a fallback for proxies without `Reason`); not tested live |
| Download links | Implemented and unit-checked; not tested against a live host |
| Login plugins (AuthMe with AuthMeVelocity/AuthMeBungee, LibreLogin, nLogin, JPremium) | Tested on Velocity with a stand-in that forces a login server and refuses other servers before the login: the reconnected player logs in once and reaches its server; the real plugins were not installed |
| LeaderOS Auth Plus 1.1.1 | Tested live on BungeeCord (with BungeeGuard 1.4, with and without Floodgate 2.2.5) and Velocity (LimboAPI 1.1.27 dev build): one reconnect per server change with and without a session; on Velocity with Minecraft 26.2 the LimboAPI login command was not received from Bedrock (session login worked) |
| BungeeGuard 1.4, Floodgate 2.2.5, Velocity modern forwarding | Tested live; nothing to configure for twilight-proxy |
| Sonar 2.1.52 | Ran on Velocity without effect on reconnects; did not start on the test BungeeCord build |
| Packs up to 152 MiB and 6,000 entries | Tested live through Geyser (172 s) and through the pack host (123 s) |

Per-server packs need a reconnect when the pack changes, because Bedrock loads packs only when it
connects. twilight-proxy runs the text layout (custom glyphs, moved characters,
named fonts, spacing), the packs' translations and the loading protection in the proxy's Geyser, with
each player's pack ([field report](FIELD_REPORT_2026-10-10.md)). It also shows item
displays (furniture, ModelEngine, BetterModel and MythicMobs mobs and NPCs, also when plugins send them
only as packets) and applies the rule that hides the name of ridden entities. Custom biome colours still
need Geyser on the same server as Twilight. See the [proxy test](PROXY_2026-10-05.md).

## Pack hosting

| Case | Status |
|---|---|
| Bedrock for Windows (1.26.5203), Geyser-Spigot on Paper 26.2 | Tested live: Twilight's and Geyser's packs downloaded over `http://` links |
| Bedrock for Windows through Velocity 4.2.0 with Geyser on the proxy | Tested live: the server's `auto` pack (2.2 MiB) downloaded from twilight-proxy's host |
| Failed download (links to a closed port) | Tested live: Bedrock falls back to Geyser's transfer and loads the new pack |
| Other Bedrock platforms (mobile, consoles) | Not tested; if one refuses plain HTTP its players fall back to Geyser's transfer, and an HTTPS `public-address` avoids it |
| Packs another plugin registers without a file | Not hosted; the session then falls back to Geyser's transfer for all packs (Geyser logs "Mixing pack codecs") |
| Antivirus web shields between client and host (tested with one on Windows) | 40 and 152 MiB downloads complete over HTTP since the host keeps the connection open until the client closes it |

## Source discovery

Every provider is read automatically (`sources.providers.<name>: auto`); `generated` limits a provider
to the pack it generates, `contents` to its working folders and `false` turns it off. World datapacks
follow `sources.datapacks`. Each build is also exported to `plugins/Twilight/export` (`Twilight.mcpack`
and Geyser item mappings) for a proxy Geyser or another plugin that sends the pack
(`geyser.send-pack-to-bedrock: false`).

| Source | Discovery | Conversion |
|---|---|---|
| ItemsAdder contents/data/cache/generated packs/API | Implemented | The generated output pack (what Java players download) outranks authored roots and data/cache, which fill gaps. A leftover folder without the plugin, or a pack the settings do not send while another provider sends one, only fills gaps. First-load, load-data, and pack-compressed events are observed. |
| CraftEngine resources/cache/generated packs/API | Implemented | `generated/resource_pack.zip` (which also merges CustomNameplates and BetterModel output) outranks the working folders when CraftEngine sends it. `loadedItems`, reload, and pack-generation events are supported; richer component mapping remains in progress. |
| CustomNameplates and BetterHUD packs | Implemented | Discovered as resource packs; their font images take part in the text layout. Layered boss bar backgrounds and shifted text match Java; ridden players hide their own Bedrock name and the name tag box is hidden (`ui.nametag-background: auto`). Hex gradients use Bedrock's 28 text colours. |
| Nexo/Oraxen packs/API | Implemented | Standard item assets implemented; Nexo's vanilla asset cache is ignored and its delivery settings are read. Oraxen array registries and item-load/pack events are supported, while version-specific API failures fall back to files and are logged. Nexo was verified from files; no test server runs the plugin. Oraxen 1.220 is verified live on Leaf 26.3 and 1.21.4: items, display-entity furniture, custom blocks and worn equipment. |
| BetterModel/ModelEngine generated packs | Partial | Models follow every Java item display (pose, interpolation, display context) on a backend's Geyser and on the proxy's Geyser, including models sent only as packets. Bones get display models without Bedrock items. Verified live with BetterModel and ModelEngine MythicMobs mobs through Velocity; phone performance with many animated models is not measured. |
| Standalone `.bbmodel` sources | Discovered | Source discovery does not imply independent rig/animation conversion; generated Java item assets are currently required. |
| RealisticSeasons pack layers and seasons | Implemented | Pack is indexed as a seasonal layer. Its seasonal biomes are read from the server registry; the current season's looks get exact Bedrock biome slots first, a season change rebuilds the pack, and biome updates reach Bedrock players without a rejoin. RealisticSeasons' data and packets are not changed. |
| World datapacks | Implemented | `server.properties` `level-name`, Bukkit worlds, dimensions, ZIP/folder datapacks. Custom biomes keep their exact colours and climate in 25 redefined Bedrock biomes; further looks use the closest vanilla biome or slot (`world.bedrock-biome-matching`). |
| MythicMobs runtime mobs | Not implemented by Twilight | Provider can spawn Java mobs; cross-client custom entity rendering requires a runtime entity bridge. See the real-content review. |
| Additional configured sources | Implemented | Path containment, link, size, entry, and archive safety checks apply. |

Twilight does not extract protected assets or reproduce paid plugin internals. Integrations use public APIs, documented file formats, and assets available to the server operator.

## Item conversion

Implemented:

- Geyser custom item mapping format v2.
- Modern Java item model roots and Geyser-compatible condition, range, and select predicates.
- Static composites retain separate child geometry and authored display poses
  in item displays, attachables and static inventory previews; generated sprite
  children are retained alongside cuboids. Dynamic composites remain unsupported.
- Legacy numeric custom model data.
- Layered 2D PNG composition and deterministic item atlas entries.
- Java cuboids, default and explicit per-face UVs, face UV rotations, element rotations, texture atlases, Bedrock geometry and attachables.
- First-person right/left, third-person right/left, and head display transforms; authored hand translation, rotation, and scale are preserved without implicit fitting.
- Model-parent-derived handheld presentation, independent of the mapping's vanilla base item.
- Native Bedrock bow/crossbow pose and pull controllers for single-layer texture-only replacements.
- Runtime-selected Java geometry and per-state display transforms for volumetric legacy bow/crossbow pull stages; arrow and rocket charge types remain distinct.
- Generated Bedrock resource paths remain below Geyser's 80-character portability boundary, including runtime state animations.
- Legacy fishing-rod idle/cast variants through Geyser's `fishing_rod_cast` predicate.
- Strict conversion mode and stable pack UUIDs.

Open release gates:

- Full current Java item node/property matrix, including nested dynamic composites and native special renderers.
- Exact GUI rendering for complex 3D models rather than source-texture fallback icons.
- Animated `.mcmeta` to Bedrock flipbook conversion (only the first authored frame currently exports).
- Full visual hand-pose parity, especially first-person framing and complex provider models; see the [real-content review](REAL_CONTENT_REVIEW.md).
- Live acceptance of every provider/version state matrix, including fishing lines, 3D bow/crossbow state transitions, shields, tridents/spears and elytra (worn armour is verified with ItemsAdder, CraftEngine and Oraxen on 26.3 and 1.21.4).
- Component-rich runtime definitions and creative/recipe presentation for every provider version.

## Other custom content

Supported bitmap providers reachable from `minecraft:default` use adaptive 16-to-512-pixel cells with measured Java height/ascent placement. Each page grows to contain its largest glyph while retaining every glyph's authored display dimensions and baseline. No image is shrunk to fit a smaller cell. Visible overflow beyond these bounds and custom space advances fail strict compilation; diagnostic builds report omissions. See the [wide glyph regression](WIDE_GLYPHS_2026-10-02.md) for native calibration, pixel comparisons and memory costs. Representable negative ascents are supported. Unreadable generated layers can fall back to a valid lower-priority source asset. Collision-free BMP private-use glyphs from named fonts can join the global Bedrock atlas.

When `vanilla-override` is disabled, normal Unicode cells from the Java default font never replace Bedrock's vanilla glyphs. With the text layout enabled (default), such characters, differing named-font images that reuse one code point and named glyphs outside the private-use range receive private-use aliases that the runtime layout substitutes per font. Characters drawn from Java's own font sheets (`ascii.png`, `accented.png`, `nonlatin_european.png`, unicode pages), as in vertically shifted text fonts, remain ordinary Bedrock text. Without the layout, contextual characters are rejected by strict publication.

Explicit `minecraft:` texture references absent from the custom pack are resolved from the running server version's Mojang client JAR. Twilight accepts only official HTTPS hosts and verifies version metadata, advertised size, and SHA-1 before caching the archive. `generation.download-vanilla-assets` can disable network retrieval; an existing verified cache remains usable.

Apart from pack links configured in twilight-proxy, the only other outgoing connections are the update check's read-only HTTPS requests for the public release list on `api.github.com` (and `gitlab.com` when GitHub fails), at start and every six hours. They send nothing about the server, download nothing, and are turned off with `update-check.enabled: false`; without internet access the plugins work unchanged.

Layered Java `sounds.json` files are merged with `replace` semantics and converted to `sounds/sound_definitions.json`. File and event references, OGG assets, weight, volume, pitch, streaming, and compatible attenuation distances are preserved. Unqualified Java file references correctly resolve through `minecraft`; emitted paths retain a namespace segment to avoid Bedrock file collisions. Explicit vanilla sound dependencies are fetched through the version's SHA-1-verified Mojang asset index. With `vanilla-override` disabled, definitions identical to vanilla are skipped and changed vanilla events reject strict publication.

Desktop chest screens use a generated Java container layout by default (`ui.java-container-layout`). Titles are unwrapped and unclipped, title and inventory labels use Java's positions and drawing order, and chest, player-inventory and hotbar rows keep Java's spacing for 1 to 6 chest rows. Partial UI definitions are merged into Bedrock's vanilla UI. The pocket UI profile (phones, tablets) uses the same layout (`ui.pocket-container-layout: java`); Bedrock's own pocket screens put the title in a header bar and menu art behind the slots. Measured with the pocket profile on Windows: two real Survival menus land exactly on Java's position, and chat, action bar, boss bars, sidebar, titles and nameplates match as on desktop. Other containers keep the vanilla Bedrock layout. See the [container layout review](CONTAINER_LAYOUT_2026-10-03.md).

Text is laid out for Bedrock players with Java font metrics by default (`ui.java-text-layout`). Font definitions from all packs are combined like Java; space providers, negative-height bitmaps, off-screen spacing bitmaps and empty TrueType glyphs become exact advances through invisible spacer glyphs; glyph bearings are corrected; characters that Java remaps in `minecraft:default` or named fonts receive private-use aliases instead of replacing Bedrock's glyphs. Chest titles use the Java container origin (see the [text layout review](TEXT_LAYOUT_2026-10-03.md)). With `ui.java-text-surfaces` (default), chat, the action bar, titles, boss bars, scoreboards, entity names, text displays and other container titles are laid out too: left-aligned lines keep Java's relative positions, centred lines are padded to Java's integer centring. Measured offsets were zero on every tested surface; see the [text surface review](TEXT_SURFACES_2026-10-04.md). Item names and lore are not rewritten, because Geyser hashes item data and returns complete items in creative mode. Container titles without a colour use pre-darkened glyph copies, because Java draws them at `0x404040` and Bedrock never tints resource-pack glyphs (`ui.java-glyph-tint`). Resource-pack translations are merged like Java and used for Java text and Bedrock's inventory label (`ui.java-translations`). Text and images moved back over earlier ones are drawn with one label per layer (up to four) in chest titles, the action bar and boss bars (`ui.java-text-layers`); see [stacked images, nameplates and exact biomes](LAYERS_BIOMES_2026-10-04.md). A glyph directly after text differs by a unit. See the [UI campaign](UI_CAMPAIGN_2026-10-04.md): 104 real menus, every window and 97 title bands exact. Boss bars whose sprites a pack redraws (`boss_bar/<colour>_background`, `_progress`, `notched_*`) are drawn with those sprites in Java's order; long boss bar names that are not layered are shown whole, and CustomNameplates' default boss bar fits Bedrock's 256-character name limit (see [custom boss bars, CustomNameplates' boss bar and an animated model](BOSSBARS_MODELS_2026-10-05.md)). Several boss bars stack about one unit further apart per bar on Bedrock.

Offset tags that a content plugin left unreplaced in laid-out text (ItemsAdder's `:offset_N:` and `%img_offset_N%`, Nexo's and Oraxen's `%nexo_shift_N%` / `%oraxen_shift_N%`, the `<shift:N>` of CraftEngine, Nexo and Oraxen) move the layout by N units like the offset characters they stand for. With the layout, glyphs that need cells of 256 pixels or more share pages of their own, so a large menu image no longer makes the page of the small glyphs around it 8192 square (a large survival server's font textures fell from about 1.9 GiB to about 0.7 GiB).

Compiler diagnostics reject supplementary-plane code points without the layout, out-of-cell baseline controls and corrupt source PNG data. Protected packs whose PNG chunk or zlib checksums are broken are decoded like Java. Content Java rejects or shows broken (malformed font files, unreadable TrueType fonts, sound files, textures or models present in no pack, screen-sized overlay glyphs, vanilla sound events changed without `vanilla-override`) is converted the way Java shows it and reported as a notice in `build-report.json`; it does not stop a strict build. Complete builds of seven production servers passed strict publication with the default configuration. Runtime font limitations are separate: Bedrock does not tint bitmap glyphs with the text colour (titles without a colour use darkened copies; other explicit colours keep the image undarkened), and high-resolution or fractionally scaled glyph textures are reduced to GUI units. Item names and lore keep their raw characters. The latest paired tests cover six metric probes, 36 real glyphs and six additional UI/HUD images. Large images rendering in chat do not establish interactive menu or dynamic HUD parity. Atlas pages can reach 8192 square (256 MiB raw RGBA per page); constrained-device acceptance remains unverified.

Custom blocks on note blocks, mushroom blocks and tripwire (ItemsAdder's `REAL_NOTE` blocks, CraftEngine's
blocks) are converted: each Java state with a custom model becomes a Bedrock block (full cubes
with a texture per face and Java's rotation, other shapes with a block geometry), registered with Geyser at
start, and the items drawn like a block show it in 3D in the inventory and when dropped. Tested live on Paper
26.2 with Geyser-Spigot and Survival's ItemsAdder and CraftEngine content: ores, rotated CraftEngine drawers,
mushroom-block decorations and tripwire plants render, and an ore item drops as a 3D block on both clients. See
the [wiki](../WIKI.md#custom-blocks).

Discovery counters also cover sounds, blockstates, `.bbmodel` files, and datapack biome definitions. Production conversion remains gated until each remaining subsystem has structural tests and real Java/Bedrock acceptance evidence:

- shader menus and dynamic HUD state;
- furniture, mobs, bones, animations, hitboxes, and equipment;
- custom blocks beyond note blocks, mushroom blocks and tripwire (multipart and chorus states);
- live Bedrock playback acceptance for provider-triggered custom sound events;
- more than 25 distinct custom biome looks at once (the rest share the closest look);
- custom skulls and waypoint icons.

## Vanilla preservation

`vanilla-override` defaults to `false`. Twilight maps only detected custom item definitions or legacy override values. It does not emit a mapping for an untouched vanilla stack. Simple custom items may reuse vanilla Bedrock presentation semantics; complex custom shapes keep dedicated geometry and transforms.

## Geyser deployment

Twilight owns only `packs/twilight.zip`, `custom_mappings/twilight_*`, and explicitly generated locale override files recorded in its deployment manifest. Deployment stages and hashes all files, snapshots the previous owned set, publishes with atomic replacement where supported, rolls back on failure, and retains three snapshots by default. Deploy and rollback compare mappings with the startup snapshot: a difference reports a required server restart and suppresses the ineffective Geyser reload. Configuration reload preserves that state. Unchanged mappings permit a controlled resource reload. Unrelated Geyser files are never removed.

Provider reload/pack commands and supported completion events are debounced. Twilight hashes the actual source bytes and live item descriptors before rebuilding, skips duplicate deploys when content is unchanged, and performs a delayed settle check for providers that finish writing after their command returns.

---

## Türkçe

### Twilight uyumluluk sözleşmesi

Bu belge uygulanmış davranışı planlanan uyumluluktan ayırır. Bir sağlayıcı yalnızca genel API'si veya
ürettiği standart varlıklar aynı dönüşüm ve doğrulama hattından geçtiğinde desteklenir; yalnızca dizinin
tanınması keşif desteğidir.

[3 Ekim incelemesi](COMPATIBILITY_REVIEW_2026-10-03.md) ve [kabul katalogu](acceptance/catalog.json) daha
geniş gerekli test yüzeyini tanımlar. Planlanan senaryolar aşağıdaki destek durumlarını değiştirmez.

#### Çalışma zamanı platformları

| Platform | Durum | Notlar |
|---|---|---|
| Paper 1.21.4+ | Uygulandı | Birincil derleme ve çalışma zamanı hedefi. |
| Folia 1.21.4+ | Çekirdek uygulandı | Genel zamanlayıcı bağdaştırıcısı; dönüşüm işi ayrı bir işçide çalışır. Çalışma zamanı varlık işleri bölge güvenli kalmalıdır. |
| Spigot 1.21.4+ | Çekirdek uygulandı | Bukkit zamanlayıcı yedeği; çalışma zamanında Paper'a özgü sabit bağlantı yok. |
| Fabric istemcisi | Desteklenmiyor | Kaldırıldı. Twilight yalnızca sunucu tarafındadır. |

En düşük bytecode düzeyi Java 21'dir. Yerel derlemeler Java 25 kullanır.

#### Proxy'ler (twilight-proxy)

| Platform | Durum |
|---|---|
| Geyser 2.11.3 ile Velocity 4.2.0 | Canlı test edildi: `auto`, dosya ve paket geçişi, modern yönlendirme gizli anahtarı |
| Geyser-Spigot 2.12.0, Floodgate ve CraftEngine 26.9.2 ile Paper 26.3 | Canlı test edildi: eşyalar, aletler, yaylar, arbaletler, oltalar, mızraklar, özel bloklar ve madenler, blok eşyaları, mobilyalar, düşen eşyalar, birinci ve üçüncü şahıs (b1262'den önceki Geyser 2.12.0 derlemeleri özel eşyaları göstermiyordu) |
| Geyser 2.11.3 ile BungeeCord 26.1 | Canlı test edildi: `auto`, dosya ve paket geçişi, açık gizli anahtar |
| Önce Floodgate'i yükleyen Waterfall 1.21 (derleme 615) | Canlı test edildi: Geyser'a bağlanma, eşya eşlemeleri, eski dosyaların taşınması ve proxy'deki yazı çalışma zamanının başlaması |
| FlameCord | Bir üretim ağından bildirildi (Geyser'a bağlanma ve eşya eşlemeleri); yerelde test edilmedi |
| Velocity 3.x, eski BungeeCord | Yalnızca uzun süredir var olan API'yi kullanır (`order` abonelikleri, `Reason` olmayan proxy'ler için yedekli `ServerConnectEvent`); canlı test edilmedi |
| İndirme bağlantıları | Uygulandı ve birim testlerle denetlendi; canlı bir sunucuya karşı test edilmedi |
| Giriş eklentileri (AuthMeVelocity/AuthMeBungee ile AuthMe, LibreLogin, nLogin, JPremium) | Velocity'de bir giriş sunucusunu zorlayan ve girişten önce diğer sunucuları reddeden bir yerine geçen eklentiyle test edildi: yeniden bağlanan oyuncu bir kez giriş yapar ve sunucusuna ulaşır; gerçek eklentiler kurulmadı |
| LeaderOS Auth Plus 1.1.1 | BungeeCord'da (BungeeGuard 1.4 ile, Floodgate 2.2.5 ile ve onsuz) ve Velocity'de (LimboAPI 1.1.27 geliştirme derlemesi) canlı test edildi: oturumla ve oturumsuz sunucu değişikliği başına tek yeniden bağlanma; Velocity'de Minecraft 26.2 ile LimboAPI giriş komutu Bedrock'tan alınmadı (oturumlu giriş çalıştı) |
| BungeeGuard 1.4, Floodgate 2.2.5, Velocity modern yönlendirmesi | Canlı test edildi; twilight-proxy için ayarlanacak bir şey yok |
| Sonar 2.1.52 | Velocity'de yeniden bağlanmaları etkilemeden çalıştı; test BungeeCord derlemesinde başlamadı |
| 152 MiB'e ve 6.000 girdiye kadar paketler | Geyser üzerinden (172 sn) ve paket sunucusu üzerinden (123 sn) canlı test edildi |

Sunucu başına paketler, paket değiştiğinde yeniden bağlanma gerektirir, çünkü Bedrock paketleri yalnızca
bağlanırken yükler. twilight-proxy yazı yerleşimini (özel glifler, taşınan karakterler,
adlandırılmış fontlar, boşluklar), paketlerin çevirilerini ve yükleme korumasını proxy'deki Geyser'da, her
oyuncunun paketiyle çalıştırır ([saha raporu](FIELD_REPORT_2026-10-10.md)). Eşya
görüntülerini de gösterir (mobilyalar, ModelEngine, BetterModel ve MythicMobs mobları ve NPC'leri; eklentiler
onları yalnızca paket olarak gönderse de) ve binilen varlıkların adını gizleyen kuralı uygular. Özel biyom
renkleri hâlâ Geyser'ın Twilight ile aynı sunucuda olmasını gerektirir. [Proxy testine](PROXY_2026-10-05.md) bakın.

#### Paket sunucusu

| Durum | Sonuç |
|---|---|
| Windows için Bedrock (1.26.5203), Paper 26.2 üzerinde Geyser-Spigot | Canlı test edildi: Twilight'ın ve Geyser'ın paketleri `http://` bağlantılarından indirildi |
| Proxy'de Geyser bulunan Velocity 4.2.0 üzerinden Windows için Bedrock | Canlı test edildi: sunucunun `auto` paketi (2,2 MiB) twilight-proxy'nin sunucusundan indirildi |
| Başarısız indirme (kapalı bir porta giden bağlantılar) | Canlı test edildi: Bedrock Geyser'ın aktarımına döner ve yeni paketi yükler |
| Diğer Bedrock platformları (mobil, konsollar) | Test edilmedi; biri düz HTTP'yi reddederse oyuncuları Geyser'ın aktarımına döner, HTTPS bir `public-address` bunu önler |
| Başka bir eklentinin dosyasız kaydettiği paketler | Sunulmaz; oturum o zaman bütün paketler için Geyser'ın aktarımına döner (Geyser "Mixing pack codecs" yazar) |
| İstemciyle sunucu arasındaki antivirüs web kalkanları (Windows'ta biriyle test edildi) | Sunucu bağlantıyı istemci kapatana kadar açık tuttuğundan 40 ve 152 MiB'lik indirmeler HTTP ile tamamlanır |

#### Kaynak keşfi

Her sağlayıcı kendiliğinden okunur (`sources.providers.<ad>: auto`); `generated` sağlayıcıyı ürettiği
pakete, `contents` çalışma klasörlerine sınırlar, `false` ise kapatır. Dünya datapack'leri
`sources.datapacks` ayarını izler. Her derleme ayrıca bir proxy Geyser'ı veya paketi gönderen başka bir
eklenti için (`geyser.send-pack-to-bedrock: false`) `plugins/Twilight/export` dizinine (`Twilight.mcpack` ve
Geyser eşya eşlemeleri) dışa aktarılır.

| Kaynak | Keşif | Dönüşüm |
|---|---|---|
| ItemsAdder contents/data/cache/üretilmiş paketler/API | Uygulandı | Üretilen çıktı paketi (Java oyuncularının indirdiği) yazarın kök dizinlerinin ve data/cache'in önüne geçer; onlar boşlukları doldurur. Eklentisi olmayan kalıntı bir klasör veya başka bir sağlayıcı paket gönderirken ayarların göndermediği bir paket yalnızca boşlukları doldurur. İlk yükleme, veri yükleme ve paket sıkıştırma olayları izlenir. |
| CraftEngine resources/cache/üretilmiş paketler/API | Uygulandı | CraftEngine gönderdiğinde `generated/resource_pack.zip` (CustomNameplates ve BetterModel çıktısını da birleştirir) çalışma klasörlerinin önüne geçer. `loadedItems`, yeniden yükleme ve paket üretim olayları desteklenir; daha zengin bileşen eşlemesi sürüyor. |
| CustomNameplates ve BetterHUD paketleri | Uygulandı | Kaynak paketi olarak keşfedilir; font görselleri yazı yerleşimine katılır. Katmanlı boss çubuğu arka planları ve kaydırılmış yazı Java ile eşleşir; binilen oyuncular kendi Bedrock adlarını gizler ve ad etiketi kutusu gizlenir (`ui.nametag-background: auto`). Hex gradyanlar Bedrock'un 28 yazı rengini kullanır. |
| Nexo/Oraxen paketleri/API | Uygulandı | Standart eşya varlıkları uygulandı; Nexo'nun vanilla varlık önbelleği yok sayılır ve teslim ayarları okunur. Oraxen dizi kayıtları ve eşya yükleme/paket olayları desteklenir; sürüme özgü API hataları dosyalara geri düşer ve günlüğe yazılır. Nexo dosyalardan doğrulandı; hiçbir test sunucusu eklentiyi çalıştırmıyor. Oraxen 1.220, Leaf 26.3 ve 1.21.4 üzerinde canlı doğrulandı: eşyalar, görüntü varlığı mobilyalar, özel bloklar ve giyilen ekipman. |
| BetterModel/ModelEngine üretilmiş paketleri | Kısmi | Modeller her Java eşya görüntüsünü (poz, ara geçiş, görüntü bağlamı) arka ucun Geyser'ında ve proxy'nin Geyser'ında izler; yalnızca paket olarak gönderilen modeller de dahil. Kemikler Bedrock eşyası olmadan görüntü modeli alır. BetterModel ve ModelEngine MythicMobs moblarıyla Velocity üzerinden canlı doğrulandı; çok sayıda animasyonlu modelle telefon başarımı ölçülmedi. |
| Bağımsız `.bbmodel` kaynakları | Keşfedildi | Kaynak keşfi bağımsız iskelet/animasyon dönüşümü anlamına gelmez; şu an üretilmiş Java eşya varlıkları gereklidir. |
| RealisticSeasons paket katmanları ve mevsimler | Uygulandı | Paket mevsimsel katman olarak dizinlenir. Mevsimsel biyomları sunucu kayıt defterinden okunur; geçerli mevsimin görünümleri önce birebir Bedrock biyom yuvaları alır, mevsim değişikliği paketi yeniden derler ve biyom güncellemeleri Bedrock oyuncularına yeniden katılmadan ulaşır. RealisticSeasons'ın verileri ve paketleri değiştirilmez. |
| Dünya datapack'leri | Uygulandı | `server.properties` `level-name`, Bukkit dünyaları, boyutlar, ZIP/klasör datapack'leri. Özel biyomlar birebir renklerini ve iklimlerini yeniden tanımlanmış 25 Bedrock biyomunda korur; fazlası en yakın vanilla biyomu veya yuvayı kullanır (`world.bedrock-biome-matching`). |
| MythicMobs çalışma zamanı mobları | Twilight tarafından uygulanmadı | Sağlayıcı Java mobları doğurabilir; istemciler arası özel varlık çizimi bir çalışma zamanı varlık köprüsü gerektirir. Gerçek içerik incelemesine bakın. |
| Ek yapılandırılmış kaynaklar | Uygulandı | Yol sınırlama, bağlantı, boyut, girdi ve arşiv güvenlik denetimleri uygulanır. |

Twilight korumalı varlıkları çıkarmaz veya ücretli eklentilerin iç yapısını yeniden üretmez. Entegrasyonlar
genel API'leri, belgelenmiş dosya biçimlerini ve sunucu işletmecisinin erişebildiği varlıkları kullanır.

#### Eşya dönüşümü

Uygulananlar:

- Geyser özel eşya eşleme biçimi v2.
- Modern Java eşya modeli kökleri ve Geyser uyumlu koşul, aralık ve seçim belirteçleri.
- Durağan bileşikler eşya görüntülerinde, eklentilerde (attachable) ve durağan envanter önizlemelerinde ayrı
  alt geometriyi ve yazarın verdiği görüntü pozlarını korur; üretilmiş sprite alt modelleri küboidlerle
  birlikte korunur. Dinamik bileşikler desteklenmiyor.
- Eski sayısal custom model data.
- Katmanlı 2B PNG birleştirme ve belirlenimci eşya atlası girdileri.
- Java küboidleri, varsayılan ve açık yüz başına UV'ler, yüz UV dönüşleri, öğe dönüşleri, doku atlasları,
  Bedrock geometrisi ve eklentiler.
- Birinci şahıs sağ/sol, üçüncü şahıs sağ/sol ve kafa görüntü dönüşümleri; yazarın verdiği el ötelemesi,
  dönüşü ve ölçeği örtük sığdırma olmadan korunur.
- Eşlemenin vanilla temel eşyasından bağımsız, model üst öğesinden türetilen elde tutma sunumu.
- Tek katmanlı yalnızca doku değiştirmeleri için yerel Bedrock yay/arbalet pozu ve gerilme denetleyicileri.
- Hacimli eski yay/arbalet gerilme aşamaları için çalışma zamanında seçilen Java geometrisi ve durum başına
  görüntü dönüşümleri; ok ve roket yükleme türleri ayrı kalır.
- Üretilen Bedrock kaynak yolları, çalışma zamanı durum animasyonları dahil Geyser'ın 80 karakterlik
  taşınabilirlik sınırının altında kalır.
- Geyser'ın `fishing_rod_cast` belirteciyle eski olta bekleme/atma çeşitleri.
- Katı dönüşüm modu ve kararlı paket UUID'leri.

Açık sürüm kapıları:

- İç içe dinamik bileşikler ve yerel özel çiziciler dahil tam güncel Java eşya düğümü/özelliği matrisi.
- Karmaşık 3B modeller için kaynak dokusu yedek simgeleri yerine birebir arayüz çizimi.
- Animasyonlu `.mcmeta` dosyalarının Bedrock flipbook'a dönüşümü (şu an yalnızca yazarın verdiği ilk kare
  dışa aktarılır).
- Özellikle birinci şahıs çerçeveleme ve karmaşık sağlayıcı modellerinde tam görsel el pozu eşdeğerliği;
  [gerçek içerik incelemesine](REAL_CONTENT_REVIEW.md) bakın.
- Olta ipleri, 3B yay/arbalet durum geçişleri, kalkanlar, üç dişli mızraklar/mızraklar ve elytra dahil her
  sağlayıcı/sürüm durum matrisinin canlı kabulü (giyilen zırhlar ItemsAdder, CraftEngine ve Oraxen ile 26.3 ve
  1.21.4 üzerinde doğrulandı).
- Her sağlayıcı sürümü için bileşen zengini çalışma zamanı tanımları ve yaratıcı/tarif sunumu.

#### Diğer özel içerik

`minecraft:default` üzerinden erişilen desteklenen bitmap sağlayıcıları, ölçülmüş Java yükseklik/ascent
yerleşimiyle 16 ile 512 piksel arası uyarlanır hücreler kullanır. Her sayfa, her glifin yazarın verdiği
görüntü boyutlarını ve taban çizgisini korurken en büyük glifini içerecek şekilde büyür. Hiçbir görsel daha
küçük bir hücreye sığması için küçültülmez. Bu sınırların ötesindeki görünür taşma ve özel boşluk
ilerlemeleri katı derlemeyi başarısız kılar; tanı derlemeleri atlamaları raporlar. Yerel kalibrasyon,
piksel karşılaştırmaları ve bellek maliyetleri için [geniş glif regresyonuna](WIDE_GLYPHS_2026-10-02.md)
bakın. Temsil edilebilir negatif ascent'ler desteklenir. Okunamayan üretilmiş katmanlar geçerli, daha
düşük öncelikli bir kaynak varlığına geri düşebilir. Adlandırılmış fontlardaki çakışmasız BMP özel kullanım
glifleri genel Bedrock atlasına katılabilir.

`vanilla-override` kapalıyken Java varsayılan fontundaki olağan Unicode hücreleri Bedrock'un vanilla
gliflerinin yerini asla almaz. Yazı yerleşimi açıkken (varsayılan) bu karakterler, bir kod noktasını
yeniden kullanan farklı adlandırılmış font görselleri ve özel kullanım aralığı dışındaki adlandırılmış
glifler, çalışma zamanı yerleşiminin font başına yerine koyduğu özel kullanım takma adları alır. Dikey
kaydırılmış yazı fontlarında olduğu gibi Java'nın kendi font sayfalarından (`ascii.png`, `accented.png`,
`nonlatin_european.png`, unicode sayfaları) çizilen karakterler olağan Bedrock yazısı kalır. Yerleşim
olmadan bağlama bağlı karakterler katı yayında reddedilir.

Özel pakette bulunmayan açık `minecraft:` doku referansları çalışan sunucu sürümünün Mojang istemci
JAR'ından çözülür. Twilight yalnızca resmî HTTPS sunucularını kabul eder ve arşivi önbelleğe almadan önce
sürüm meta verisini, bildirilen boyutu ve SHA-1'i doğrular. `generation.download-vanilla-assets` ağdan
indirmeyi kapatabilir; mevcut doğrulanmış önbellek kullanılabilir kalır.

twilight-proxy'de yapılandırılan paket bağlantıları dışında diğer tek giden bağlantılar, güncelleme denetiminin açılışta ve her altı saatte bir `api.github.com` (GitHub
başarısız olursa `gitlab.com`) üzerindeki herkese açık sürüm listesi için yaptığı salt okunur HTTPS istekleridir.
Sunucu hakkında hiçbir şey göndermez, hiçbir şey indirmez ve `update-check.enabled: false` ile kapatılır; internet
erişimi olmadan eklentiler değişmeden çalışır.

Katmanlı Java `sounds.json` dosyaları `replace` anlamıyla birleştirilir ve `sounds/sound_definitions.json`
dosyasına dönüştürülür. Dosya ve olay referansları, OGG varlıkları, ağırlık, ses düzeyi, perde, akış ve
uyumlu zayıflama mesafeleri korunur. Nitelenmemiş Java dosya referansları `minecraft` üzerinden doğru
çözülür; üretilen yollar Bedrock dosya çakışmalarını önlemek için bir ad alanı parçası korur. Açık vanilla
ses bağımlılıkları sürümün SHA-1 doğrulamalı Mojang varlık dizini üzerinden alınır. `vanilla-override`
kapalıyken vanilla ile özdeş tanımlar atlanır ve değiştirilmiş vanilla olaylar raporlanır.

Masaüstü sandık ekranları varsayılan olarak üretilmiş bir Java konteyner yerleşimi kullanır
(`ui.java-container-layout`). Başlıklar kaydırılmaz ve kırpılmaz, başlık ve envanter etiketleri Java'nın
konumlarını ve çizim sırasını kullanır, sandık, oyuncu envanteri ve kısayol çubuğu satırları 1 ile 6 sandık
satırı için Java'nın aralığını korur. Kısmi arayüz tanımları Bedrock'un vanilla arayüzüyle birleştirilir.
Pocket arayüz profili (telefonlar, tabletler) aynı yerleşimi kullanır
(`ui.pocket-container-layout: java`); Bedrock'un kendi pocket ekranları başlığı bir başlık çubuğuna, menü
görsellerini yuvaların arkasına koyar. Windows'ta pocket profiliyle ölçüldü: iki gerçek Survival menüsü tam Java'nın
konumuna oturur; sohbet, aksiyon çubuğu, boss çubukları, yan panel, başlıklar ve isim plakaları masaüstündeki gibi
eşleşir. Diğer konteynerler vanilla Bedrock yerleşimini korur.
[Konteyner yerleşimi incelemesine](CONTAINER_LAYOUT_2026-10-03.md) bakın.

Yazı, Bedrock oyuncuları için varsayılan olarak Java font ölçüleriyle yerleştirilir (`ui.java-text-layout`).
Tüm paketlerin font tanımları Java gibi birleştirilir; boşluk sağlayıcıları, negatif yükseklikli bitmap'ler,
ekran dışı aralık bitmap'leri ve boş TrueType glifleri görünmez aralık glifleriyle birebir ilerlemelere
dönüşür; glif kenar payları düzeltilir; Java'nın `minecraft:default` veya adlandırılmış fontlarda yeniden
eşlediği karakterler Bedrock'un gliflerinin yerini almak yerine özel kullanım takma adları alır. Sandık
başlıkları Java konteyner başlangıcını kullanır ([yazı yerleşimi incelemesine](TEXT_LAYOUT_2026-10-03.md)
bakın). `ui.java-text-surfaces` (varsayılan) ile sohbet, aksiyon çubuğu, başlıklar, boss çubukları, skor
tabloları, varlık adları, yazı görüntüleri ve diğer konteyner başlıkları da yerleştirilir: sola hizalı
satırlar Java'nın göreli konumlarını korur, ortalı satırlar Java'nın tam sayı ortalamasına göre doldurulur.
Ölçülen kaymalar test edilen her yüzeyde sıfırdı; [yazı yüzeyi incelemesine](TEXT_SURFACES_2026-10-04.md)
bakın. Eşya adları ve açıklamaları yeniden yazılmaz, çünkü Geyser eşya verisinin karmasını alır ve yaratıcı
modda tam eşyaları geri gönderir. Renksiz konteyner başlıkları önceden koyulaştırılmış glif kopyaları
kullanır, çünkü Java onları `0x404040` ile çizer ve Bedrock kaynak paketi gliflerini asla renklendirmez
(`ui.java-glyph-tint`). Kaynak paketi çevirileri Java gibi birleştirilir ve Java yazısı ile Bedrock'un
envanter etiketi için kullanılır (`ui.java-translations`). Öncekilerin üzerine geri alınan yazı ve görseller
sandık başlıklarında, aksiyon çubuğunda ve boss çubuklarında katman başına bir etiketle (en fazla dört)
çizilir (`ui.java-text-layers`); [üst üste görseller, ad etiketleri ve birebir biyomlar](LAYERS_BIOMES_2026-10-04.md)
belgesine bakın. Yazının hemen ardından gelen bir glif bir birim farklıdır. [Arayüz kampanyasına](UI_CAMPAIGN_2026-10-04.md)
bakın: 104 gerçek menü, her pencere ve 97 başlık bandı birebir. Sprite'larını bir paketin yeniden çizdiği boss
çubukları (`boss_bar/<renk>_background`, `_progress`, `notched_*`) bu sprite'larla Java'nın sırasıyla çizilir;
katmanlı olmayan uzun boss çubuğu adları tamamen gösterilir ve CustomNameplates'in varsayılan boss çubuğu
Bedrock'un 256 karakterlik ad sınırına sığar ([özel boss çubukları, CustomNameplates boss çubuğu ve animasyonlu
bir model](BOSSBARS_MODELS_2026-10-05.md) belgesine bakın). Birden çok boss çubuğu Bedrock'ta çubuk başına yaklaşık
bir birim daha aralıklı dizilir.

Bir içerik eklentisinin yerleştirilen yazıda değiştirmeden bıraktığı kaydırma etiketleri (ItemsAdder'ın
`:offset_N:` ve `%img_offset_N%`, Nexo'nun ve Oraxen'in `%nexo_shift_N%` / `%oraxen_shift_N%` etiketleri,
CraftEngine, Nexo ve Oraxen'in `<shift:N>` etiketi) yerleşimi, temsil ettikleri kaydırma karakterleri gibi N
birim kaydırır. Yerleşimle 256 piksel veya daha büyük hücre gerektiren glifler kendi
sayfalarını paylaşır; böylece büyük bir menü görseli çevresindeki küçük gliflerin sayfasını artık 8192 kare
yapmaz (büyük bir survival sunucusunun font dokuları yaklaşık 1,9 GiB'tan yaklaşık
0,7 GiB'a indi).

Derleyici tanıları yerleşim olmadan tamamlayıcı düzlem kod noktalarını, hücre dışı taban çizgisi
denetimlerini ve bozuk kaynak PNG verisini reddeder. PNG parça veya zlib sağlama toplamları bozuk korumalı
paketler Java gibi çözülür. Java'nın reddettiği veya bozuk gösterdiği içerik (bozuk font dosyaları,
okunamayan TrueType fontlar, hiçbir pakette bulunmayan ses dosyaları, dokular veya modeller, ekran
boyutunda kaplama glifleri, `vanilla-override` olmadan değiştirilmiş vanilla ses olayları) Java'nın
gösterdiği şekilde dönüştürülür ve `build-report.json` içinde bildirim olarak raporlanır; katı derlemeyi
durdurmaz. Yedi üretim sunucusunun tam derlemeleri varsayılan yapılandırmayla katı yayını geçti. Çalışma
zamanı font sınırlamaları ayrıdır: Bedrock bitmap glifleri yazı rengiyle renklendirmez (renksiz başlıklar
koyulaştırılmış kopyalar kullanır; diğer açık renkler görseli koyulaştırmadan korur) ve yüksek çözünürlüklü
veya kesirli ölçekli glif dokuları arayüz birimlerine indirgenir. Eşya adları ve açıklamaları ham
karakterlerini korur. En son eşlenmiş testler altı ölçü denemesini, 36 gerçek glifi ve altı ek arayüz/HUD
görselini kapsar. Sohbette çizilen büyük görseller etkileşimli menü veya dinamik HUD eşdeğerliğini
kanıtlamaz. Atlas sayfaları 8192 kareye (sayfa başına 256 MiB ham RGBA) ulaşabilir; kısıtlı cihaz kabulü
doğrulanmadı.

Nota bloğu, mantar bloğu ve tuzak teli üzerindeki özel bloklar (ItemsAdder'ın `REAL_NOTE` blokları, CraftEngine'in
blokları) dönüştürülür: özel modeli olan her Java durumu bir Bedrock bloğu olur (yüz başına
doku ve Java döndürmesiyle tam küpler, diğer şekiller blok geometrisiyle), açılışta Geyser'a kaydedilir ve bir blok
gibi çizilen eşyalar onu envanterde ve yere düştüğünde 3B gösterir. Paper 26.2 ve Geyser-Spigot üzerinde Survival'ın
ItemsAdder ve CraftEngine içeriğiyle canlı test edildi: madenler, döndürülmüş CraftEngine çekmeceleri, mantar bloğu
süslemeleri ve tuzak teli bitkileri çizilir; bir maden eşyası iki istemcide de 3B blok olarak düşer.
[Wiki'ye](../WIKI.md#özel-bloklar) bakın.

Keşif sayaçları sesleri, blok durumlarını, `.bbmodel` dosyalarını ve datapack biyom tanımlarını da kapsar.
Üretim dönüşümü, kalan her alt sistem yapısal testlere ve gerçek Java/Bedrock kabul kanıtına sahip olana
kadar kapılı kalır:

- shader menüleri ve dinamik HUD durumu;
- mobilya, moblar, kemikler, animasyonlar, çarpışma kutuları ve ekipman;
- nota bloğu, mantar bloğu ve tuzak teli dışındaki özel bloklar (multipart ve chorus durumları);
- sağlayıcının tetiklediği özel ses olayları için canlı Bedrock oynatım kabulü;
- aynı anda 25'ten fazla farklı özel biyom görünümü (geri kalanlar en yakın görünümü paylaşır);
- özel kafatasları ve yol noktası simgeleri.

#### Vanilla koruma

`vanilla-override` varsayılan olarak `false`. Twilight yalnızca algılanan özel eşya tanımlarını veya eski
override değerlerini eşler. Dokunulmamış vanilla bir eşya yığını için eşleme üretmez. Basit özel eşyalar
vanilla Bedrock sunum anlamını yeniden kullanabilir; karmaşık özel şekiller kendi geometrilerini ve
dönüşümlerini korur.

#### Geyser dağıtımı

Twilight yalnızca `packs/twilight.zip`, `custom_mappings/twilight_*` ve dağıtım manifestine kaydedilmiş,
açıkça üretilmiş dil geçersiz kılma dosyalarının sahibidir. Dağıtım tüm dosyaları hazırlar ve karmasını
alır, önceki sahip olunan kümenin anlık görüntüsünü alır, desteklenen yerlerde atomik değiştirmeyle yayımlar,
hata durumunda geri alır ve varsayılan olarak üç anlık görüntü saklar. Dağıtım ve geri alma eşlemeleri
açılış anlık görüntüsüyle karşılaştırır: bir fark gerekli bir sunucu yeniden başlatmasını raporlar ve etkisiz
Geyser yeniden yüklemesini bastırır. Yapılandırma yeniden yüklemesi bu durumu korur. Değişmemiş eşlemeler
denetimli bir kaynak yeniden yüklemesine izin verir. İlgisiz Geyser dosyaları asla kaldırılmaz.

Sağlayıcı yeniden yükleme/paket komutları ve desteklenen tamamlanma olayları geciktirilerek birleştirilir.
Twilight yeniden derlemeden önce gerçek kaynak baytlarının ve canlı eşya tanımlayıcılarının karmasını alır,
içerik değişmediğinde yinelenen dağıtımları atlar ve komutları döndükten sonra yazmayı bitiren sağlayıcılar
için gecikmeli bir yerleşme denetimi yapar.
