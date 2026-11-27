# Twilight 1.0.1

> Türkçe: [aşağıda](#türkçe)

1.0.1 is the first stable release after the 1.0.0 betas. It collects the three 1.0.1 prereleases and two fixes
found while checking them against copies of production servers, with Geyser on the server, on Velocity and on
BungeeCord.

**New since 1.0.1-pre.3**

- **BetterModel 3.3 models show on Bedrock.** BetterModel 3.3 writes every bone into one item definition that
  dispatches on custom model data, and Twilight found no display model for those bones, so the models stayed
  invisible on Bedrock. They now get display models like the bones of BetterModel 3.4 and ModelEngine. Item
  definitions that dispatch on a number (`range_dispatch`) also pick the highest threshold a value reaches, as on
  Java; the first entry used to win.
- **A stray trailing comma in a font file no longer stops the conversion.** Java reads it as a missing provider
  and drops that pack's definition of the font; Twilight now does the same with a notice instead of rejecting the
  whole build in strict mode.

**Since 1.0.0-beta.6** (details in the prerelease notes below and in the [changelog](CHANGELOG.md))

- Geyser 2.12.1 build 1298, which current Bedrock clients need, is supported. Update Twilight before Geyser:
  1.0.0-beta.6 cannot start its display bridge on build 1298 or newer, so furniture models would disappear.
- Invisible item frames are drawn in every facing; ItemsAdder crates and other frame furniture no longer turn
  about 90 degrees when a player comes close.
- Custom datapack biomes show through the proxy (issue #3), per server and across server switches.
- Bedrock players see other players' custom elytras; `geyser.elytra-override` and the experimental
  `geyser.elytra-per-player` (also with Geyser on the proxy) cover a player's own elytra.
- Lit CraftEngine blocks give their light, and CraftEngine blocks built on cactus, sugar cane, sapling and leaves
  states show their own look.
- Large 3D handhelds have Java's first person size; single-model custom bows and crossbows move while drawn.
- Identical content builds a byte-identical pack, so players are not asked to download an unchanged pack again.

**Tested** on copies of production servers (1.21.11 with ItemsAdder 4.0.18, CraftEngine 26.7, BetterModel 3.3.0
and 3.4.2, MythicMobs, FancyHolograms and CrazyCrates) with Geyser 2.12.1 (build 1298) and Floodgate on the
server, on Velocity 4.2.0 and on BungeeCord 26.1. Java 1.21.4 and Bedrock 26.6x captures were compared for items
in hand, blocks, furniture, glyphs in chat and titles, animated model entities, holograms, near and far views
(5 to 96 blocks and back from 400), relogs, server switches with transfers, and server and proxy restarts.

**Known limits**

- A proxy network registers the custom blocks of all its servers once, while each server's pack draws only its
  own states: keep the servers on the same custom block states, or set `bedrock.custom-blocks: false` on the proxy
  (the wiki's Custom blocks section explains it).
- 3D items held in the off hand are not drawn in Bedrock's first person view (a Bedrock client limit).
- Phones and consoles were not tested live; large packs load slowly on phones.

Test server: `oyna.twilightnw.com`.

Update both JARs (Twilight before Geyser) and restart the server and the proxy once after the build; new custom
blocks, items and player heads appear after that restart, as Geyser registers them when it starts.

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 436 tests
across 73 suites passed. Both JARs carry `LICENSE` and `NOTICE` in `META-INF`. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3, 2.12.0 and 2.12.1; other Geyser core versions require validation.

The notes below describe the prereleases that led to 1.0.1.

## Twilight 1.0.1-pre.3 - prerelease

**Light from lit CraftEngine blocks.** Lamps, the lit copper coil and other CraftEngine blocks with a `luminance`
setting now light their surroundings on Bedrock as on Java. CraftEngine keeps the light level in its server
settings, not in the resource pack, so Twilight reads it from CraftEngine when a build starts and the Bedrock block
emits the same level, also with Geyser on the proxy. Restart Geyser once after the build that adds the light, as
for other block changes. The block light of other content plugins is not read yet.

**Custom bows and crossbows drawn in first person.** Custom bows and crossbows with a single model (no pull states)
stayed still on Bedrock while drawn. They now swing to the middle of the view with Java's own drawing pose,
combined with the model's first person transform. Weapons with pull states, which already matched Java, and
vanilla bows and crossbows are unchanged.

**Per-player elytras through the proxy.** The experimental option `geyser.elytra-per-player` now also works with
Geyser on Velocity or BungeeCord: twilight-proxy reads the wings table of the pack each Bedrock player loaded, so
every backend keeps its own elytras. Set the option on the backends; the proxy needs no setting. A player who
switches servers without a transfer keeps the first server's wings table until rejoining.

**Production checks.** ItemsAdder crates and other furniture built from invisible item frames turned about 90
degrees on Bedrock when a player came close in 1.0.0-beta.6; this has been fixed since 1.0.1-pre.2 and was checked
again from every side, after relogs and restarts. Servers still on 1.0.0-beta.6 should update Twilight before
Geyser: beta.6 cannot start its display bridge on Geyser 2.12.1 build 1298 or newer, so furniture models would
disappear. Custom datapack biomes through the proxy (issue #3) were confirmed with a real Bedrock client, including
server switches and reconnects. 3D items held in the off hand are not drawn in Bedrock's first person view, which
is a limit of the Bedrock client (the wiki's troubleshooting section explains it); third person is unaffected.

**Tested** on Leaf 1.21.4 with CraftEngine, Geyser 2.12.1 (build 1298) and Floodgate, comparing Java 1.21.4 and
Bedrock 26.6x captures of lit blocks at night, bows and crossbows while drawing, shields, gliding with custom
elytras (own view and seen by others), furniture seats, item frames in all facings and the new foliage blocks.
Through Velocity with Geyser 2.12.1 (build 1298), custom biomes and per-player elytras were checked with a real
Bedrock client against Geyser on the backend, also across server switches and reconnects.

Test server: `oyna.twilightnw.com`.

Update both JARs and restart the server and the proxy once after the build; new custom blocks, items and player
heads appear after that restart, as Geyser registers them when it starts.

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 429 tests
across 72 suites passed. Both JARs carry `LICENSE` and `NOTICE` in `META-INF`. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3, 2.12.0 and 2.12.1; other Geyser core versions require validation.

## Twilight 1.0.1-pre.2 - prerelease

**Invisible item frames.** Furniture and 3D items in invisible item frames facing north are drawn again, and
frames facing east, west, up and down are no longer about one block out of place. Newer Geyser builds turn the
frame entity by its facing after spawning it, and Twilight's display followed that turn; only south was right
before. Checked live in all six facings against Java; visible frames are unchanged.

**Custom datapack biomes through the proxy** (GitHub issue #3). With Geyser on Velocity or BungeeCord, custom
biomes showed as ocean on Bedrock, without custom grass, foliage, water, fog or sky colours. twilight-proxy now
maps each Bedrock player's custom biomes with the tables of the pack that player loaded, so several backends with
different custom biomes keep their own looks, also after server switches and reconnects; a biome name shared by
two servers with different looks no longer borrows the other server's look. Backends without Twilight, or with
`world.bedrock-biome-matching: false`, are unchanged, and Geyser on a backend behaves as before. Both JARs need
the update. Biome-only changes while a player is online (seasons plugins, `/fillbiome`) are not resent through
the proxy; they show when the chunks are sent again.

**CraftEngine blocks on more vanilla states.** Blocks built on cactus, sugar cane, sapling and oak leaves states
(for example copper coil, reeds, flowers, saplings and palm leaves) now show their own look on Bedrock instead of
the vanilla block, with Java's collision and outline. Leaves and cane-based blocks get Java's plains foliage and
grass colour baked in. Natural cactus, sugar cane, saplings and leaves stay vanilla unless the pack reskins those
exact states on Java too. Block models taller or wider than Bedrock allows (30 pixels per axis) are trimmed by
their outermost pixels with a notice, and parts without volume are skipped, because a single invalid block makes
Bedrock drop every custom block.

**Area effect clouds.** Clouds with a radius of 0 no longer show Geyser's particle swirl on Bedrock, as Java draws
none. Entities riding them (the seats of furniture and model plugins) stay seated.

**Experimental per-player elytras.** The new option `geyser.elytra-per-player` (default `false`) lets a Bedrock
player also see their own custom elytra, standing and gliding, while vanilla elytras stay vanilla. It takes
precedence over `elytra-override`. It works with Geyser on the backend server, not yet with Geyser on the proxy,
and needs a Geyser restart after the first build that contains custom elytras.

**Identical packs across restarts.** The same content now always builds a byte-identical pack: the texture order
of multi-texture block models depended on the JVM run. Bedrock players are no longer asked to download an
unchanged pack again.

Thanks to JamDoggie for the detailed report of issue #3.

**Tested** on Leaf 1.21.4 with CraftEngine, Geyser 2.12.1 (build 1298) and Floodgate, comparing Java 1.21.4 and
Bedrock 26.6x captures of item frames in all facings, the new blocks, clouds, custom elytras including gliding,
furniture, displays, first person 3D handhelds and armour. Packs built from CraftEngine, Oraxen and ItemsAdder
content were compared offline: only the intended changes, and no block outside Bedrock's geometry limits. The
proxy biomes were verified on Velocity with Geyser 2.12.1 (builds 1277, 1298 and 1310) and Leaf 26.3 and 1.21.4
backends, checking the biome ids and definitions on the Bedrock side.

Test server: `oyna.twilightnw.com`.

Update both JARs and restart the server and the proxy once after the build; new custom blocks, items and player
heads appear after that restart, as Geyser registers them when it starts.

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 420 tests
across 70 suites passed. Both JARs carry `LICENSE` and `NOTICE` in `META-INF`. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3, 2.12.0 and 2.12.1; other Geyser core versions require validation.

The notes below describe previous prereleases.

## Twilight 1.0.1-pre.1 - prerelease

**Geyser 2.12.1 (build 1298).** Geyser removed the Java entity type registry that Twilight used, so on that build
item and block displays, furniture, dropped 3D items and point-cloud adaptation stopped working and an error
appeared at start. Twilight now finds the registry on each build, and earlier Geyser 2.11 and 2.12 builds keep
working. The menu and text layout also reads Geyser's client UI profile on the builds that changed it and falls
back to the classic profile.

**Custom elytras on Bedrock.** Bedrock viewers now see other players' custom elytras (CraftEngine, ItemsAdder,
Oraxen and Nexo elytra items) with their own wings, standing and gliding, also when Geyser runs on the proxy
(twilight-proxy). Before, every glider showed as the vanilla elytra. A Bedrock player's own custom elytra still
looks vanilla to that player, because the Bedrock client only glides with the vanilla elytra item. The new option
`geyser.elytra-override` (default `false`) redraws the vanilla elytra itself with the custom wings, for servers
whose only elytra is one custom skin; it changes every worn elytra on Bedrock, vanilla ones included, so leave it
off on servers with vanilla elytras.

**Large 3D handhelds in first person.** Bedrock showed 3D tools and weapons (axes, tridents, pickaxes, shields)
1.3 to 1.8 times smaller than Java in first person. They are now scaled and placed like Java's first person
view: the measured silhouette area went from 0.29-0.50 of Java's to 0.80-0.95. Flat items, bows and crossbows are
unchanged, and so is the third person view.

**Models and blocks.** Models whose faces name their texture under a non-standard key (seen in an ItemsAdder
pack, where the key was the texture path and the value `#0`) are repaired during conversion, so strict mode no
longer rejects the whole conversion. A `model-repaired` notice names the model and says that Java clients may
show it as missing and that the source model should be fixed. Ambiguous faces still fail strict mode, now with
the element and face in the message. Custom blocks keep Java's ambient occlusion and face shading: a block model
with `"ambientocclusion": false` or elements with `"shade": false` turn off Bedrock's `ambient_occlusion` and
`face_dimming` for those materials, so glowing or flat-lit blocks are no longer darkened. Other blocks are
unchanged.

**Resource pack host.** A client that is banned for misbehaving is now recorded as banned before the refusal is
sent, so a reconnect that races the response cannot slip through.

Thanks to yorik100 for the ideas behind the block shading, the pack host ban ordering and the first person scale
observation (pull request #2).

**Known limitations.** A Bedrock player's own custom elytra looks vanilla to that player. A custom item in an
invisible item frame facing north may not render on Bedrock; this is under investigation.

**Tested** on Leaf 1.21.4 with CraftEngine and Oraxen, Geyser 2.12.1 (build 1298) and Floodgate, comparing Java
1.21.4 and Bedrock 26.6x captures of items, 3D handhelds in first person, furniture, displays, custom elytras
standing and gliding, and blocks. Server start was also checked on Geyser 2.12.0 (build 1263). Converting the
CraftEngine and Oraxen test packs produced the same packs as beta.6 apart from the intended changes.

Test server: `oyna.twilightnw.com`.

Update both JARs and restart the server and the proxy once.

The [JARs and SHA-256 files](artifacts/) were built locally under siberanka using Java 25; 400 tests
across 68 suites passed. Both JARs carry `LICENSE` and `NOTICE` in `META-INF`. No hosted CI was run.

Runtime requirements: Java 21+, Paper/Folia/Spigot 1.21.4+, Geyser with custom content enabled;
for twilight-proxy, Velocity or BungeeCord with Geyser on the proxy. The runtime bridges target
Geyser 2.11.3, 2.12.0 and 2.12.1; other Geyser core versions require validation.

## Twilight 1.0.0-beta.6

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

### Twilight 1.0.1

1.0.1, 1.0.0 betalarından sonraki ilk kararlı sürümdür. Üç 1.0.1 ön sürümünü ve onları üretim sunucularının
kopyalarına karşı, Geyser sunucuda, Velocity'de ve BungeeCord'da iken denerken bulunan iki düzeltmeyi bir araya
getirir.

**1.0.1-pre.3'ten bu yana yeni**

- **BetterModel 3.3 modelleri Bedrock'ta görünür.** BetterModel 3.3 her kemiği özel model verisine göre ayrışan tek
  bir eşya tanımına yazar; Twilight bu kemikler için görüntü modeli bulamıyordu ve modeller Bedrock'ta görünmez
  kalıyordu. Artık BetterModel 3.4 ve ModelEngine kemikleri gibi görüntü modeli alırlar. Sayıya göre ayrışan
  (`range_dispatch`) eşya tanımları da Java'daki gibi değerin ulaştığı en yüksek eşiği seçer; önceden ilk kayıt
  kazanıyordu.
- **Yazı tipi dosyasındaki başıboş bir sondaki virgül artık dönüşümü durdurmaz.** Java onu eksik bir sağlayıcı olarak
  okur ve o paketin yazı tipi tanımını atar; Twilight da artık sıkı modda tüm derlemeyi reddetmek yerine bir
  bildirimle aynısını yapar.

**1.0.0-beta.6'dan bu yana** (ayrıntılar aşağıdaki ön sürüm notlarında ve [değişiklik günlüğünde](CHANGELOG.md))

- Güncel Bedrock istemcilerinin gerektirdiği Geyser 2.12.1'in 1298 numaralı derlemesi desteklenir. Twilight'ı
  Geyser'dan önce güncelleyin: 1.0.0-beta.6, 1298 ve sonraki derlemelerde görüntü köprüsünü başlatamaz, bu yüzden
  mobilya modelleri kaybolur.
- Görünmez eşya çerçeveleri her yönde çizilir; ItemsAdder kasaları ve diğer çerçeve mobilyaları bir oyuncu yaklaşınca
  artık yaklaşık 90 derece dönmez.
- Özel datapack biyomları proxy üzerinden, sunucu başına ve sunucu değiştirmelerde de görünür (issue #3).
- Bedrock oyuncuları diğer oyuncuların özel elytralarını görür; oyuncunun kendi elytrası için
  `geyser.elytra-override` ve deneysel `geyser.elytra-per-player` (Geyser proxy'de olduğunda da) vardır.
- Yanan CraftEngine blokları ışıklarını verir; kaktüs, şeker kamışı, fidan ve yaprak durumları üzerine kurulan
  CraftEngine blokları kendi görünümlerini gösterir.
- Büyük 3B el eşyaları Java'nın birinci şahıs boyutundadır; tek modelli özel yaylar ve arbaletler çekilirken hareket
  eder.
- Aynı içerik bayt bayt aynı paketi üretir; böylece oyunculardan değişmemiş bir paketi yeniden indirmeleri istenmez.

**Test edildi:** üretim sunucularının kopyalarında (ItemsAdder 4.0.18, CraftEngine 26.7, BetterModel 3.3.0 ve
3.4.2, MythicMobs, FancyHolograms ve CrazyCrates ile 1.21.11), Geyser 2.12.1 (1298 numaralı derleme) ve Floodgate
sunucuda, Velocity 4.2.0'da ve BungeeCord 26.1'de iken. Eldeki eşyaların, blokların, mobilyaların, sohbet ve
başlıklardaki glifler, animasyonlu model varlıklarının, hologramların, yakın ve uzak görünümlerin (5 ile 96 blok
arası ve 400 bloktan dönüş), yeniden girişlerin, transferle sunucu değiştirmelerin, sunucu ve proxy yeniden
başlatmalarının Java 1.21.4 ve Bedrock 26.6x görüntüleri karşılaştırıldı.

**Bilinen sınırlar**

- Proxy'li bir ağ tüm sunucularının özel bloklarını bir kez kaydeder, her sunucunun paketi ise yalnızca kendi
  durumlarını çizer: sunucuları aynı özel blok durumlarında tutun ya da proxy'de `bedrock.custom-blocks: false`
  yapın (wiki'nin Özel bloklar bölümü açıklar).
- Sol elde tutulan 3B eşyalar Bedrock'un birinci şahıs görünümünde çizilmez (Bedrock istemcisinin bir sınırı).
- Telefonlar ve konsollar canlı test edilmedi; büyük paketler telefonlarda yavaş yüklenir.

Test sunucusu: `oyna.twilightnw.com`.

İki JAR'ı da güncelleyin (Twilight'ı Geyser'dan önce) ve derlemeden sonra sunucuyu ve proxy'yi bir kez yeniden
başlatın; yeni özel bloklar, eşyalar ve oyuncu kafaları, Geyser onları açılışta kaydettiği için bu yeniden
başlatmadan sonra görünür.

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 73 paketteki
436 test geçti. İki JAR da `META-INF` içinde `LICENSE` ve `NOTICE` taşır. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3, 2.12.0 ve
2.12.1'i hedefler; diğer Geyser çekirdek sürümleri doğrulama gerektirir.

Aşağıdaki notlar 1.0.1'e giden ön sürümleri anlatır.

#### Twilight 1.0.1-pre.3 - ön sürüm

**Yanan CraftEngine bloklarının ışığı.** Lambalar, yanan bakır bobin ve `luminance` ayarı olan diğer CraftEngine
blokları artık Bedrock'ta da Java'daki gibi çevrelerini aydınlatır. CraftEngine ışık seviyesini kaynak paketinde
değil sunucu ayarlarında tutar; bu yüzden Twilight bir derleme başlarken onu CraftEngine'den okur ve Bedrock bloğu
aynı seviyede ışık verir, Geyser proxy'de olduğunda da. Işığı ekleyen derlemeden sonra, diğer blok değişikliklerinde
olduğu gibi Geyser'ı bir kez yeniden başlatın. Diğer içerik eklentilerinin blok ışığı henüz okunmaz.

**Birinci şahısta çekilen özel yaylar ve arbaletler.** Tek modelli (germe durumu olmayan) özel yaylar ve arbaletler
Bedrock'ta çekilirken hareketsiz kalıyordu. Artık Java'nın kendi çekme pozuyla, modelin birinci şahıs dönüşümüyle
birleşerek görüşün ortasına doğru döner. Zaten Java ile aynı olan germe durumlu silahlar ile vanilla yay ve
arbaletler değişmez.

**Proxy üzerinden oyuncu başına elytralar.** Deneysel `geyser.elytra-per-player` seçeneği artık Velocity veya
BungeeCord üzerindeki Geyser ile de çalışır: twilight-proxy her Bedrock oyuncusunun yüklediği paketin kanat
tablosunu okur, böylece her arka uç kendi elytralarını korur. Seçeneği arka uçlarda açın; proxy'de ayar gerekmez.
Transfer olmadan sunucu değiştiren oyuncu, yeniden katılana kadar ilk sunucunun kanat tablosunu kullanır.

**Üretim kontrolleri.** 1.0.0-beta.6'da görünmez eşya çerçevelerinden yapılan ItemsAdder kasaları ve diğer
mobilyalar, bir oyuncu yaklaşınca Bedrock'ta yaklaşık 90 derece dönüyordu; bu 1.0.1-pre.2'den beri düzeltilmiştir
ve her yönden, yeniden girişlerden ve yeniden başlatmalardan sonra tekrar kontrol edildi. Hâlâ 1.0.0-beta.6
kullanan sunucular Twilight'ı Geyser'dan önce güncellemelidir: beta.6, Geyser 2.12.1'in 1298 ve sonraki
derlemelerinde görüntü köprüsünü başlatamaz, bu yüzden mobilya modelleri kaybolur. Proxy üzerinden özel datapack
biyomları (issue #3) gerçek bir Bedrock istemcisiyle, sunucu değiştirme ve yeniden bağlanma dahil doğrulandı. Sol
elde tutulan 3B eşyalar Bedrock'un birinci şahıs görünümünde çizilmez; bu Bedrock istemcisinin bir sınırıdır
(wiki'nin sorun giderme bölümü açıklar), üçüncü şahıs etkilenmez.

**Test edildi:** Leaf 1.21.4 üzerinde CraftEngine, Geyser 2.12.1 (1298 numaralı derleme) ve Floodgate ile; gece
yanan blokların, çekilirken yay ve arbaletlerin, kalkanların, özel elytrayla süzülmenin (kendi görünümü ve
başkalarının gördüğü), mobilya oturaklarının, tüm yönlerdeki eşya çerçevelerinin ve yeni bitki bloklarının Java
1.21.4 ve Bedrock 26.6x görüntüleri karşılaştırıldı. Velocity üzerinde Geyser 2.12.1 (1298 numaralı derleme) ile
özel biyomlar ve oyuncu başına elytralar, sunucu değiştirme ve yeniden bağlanma dahil, gerçek bir Bedrock
istemcisiyle arka uçtaki Geyser'a göre kontrol edildi.

Test sunucusu: `oyna.twilightnw.com`.

İki JAR'ı da güncelleyin ve derlemeden sonra sunucuyu ve proxy'yi bir kez yeniden başlatın; yeni özel bloklar,
eşyalar ve oyuncu kafaları, Geyser onları açılışta kaydettiği için bu yeniden başlatmadan sonra görünür.

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 72 paketteki
429 test geçti. İki JAR da `META-INF` içinde `LICENSE` ve `NOTICE` taşır. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3, 2.12.0 ve
2.12.1'i hedefler; diğer Geyser çekirdek sürümleri doğrulama gerektirir.

#### Twilight 1.0.1-pre.2 - ön sürüm

**Görünmez eşya çerçeveleri.** Kuzeye bakan görünmez eşya çerçevelerindeki mobilyalar ve 3B eşyalar yeniden
çizilir; doğuya, batıya, yukarı ve aşağı bakan çerçeveler de artık yaklaşık bir blok yanlış yerde durmaz. Yeni
Geyser derlemeleri çerçeve varlığını oluşturduktan sonra baktığı yöne göre döndürür ve Twilight'ın görüntüsü bu
dönüşü izliyordu; önceden yalnızca güney doğruydu. Altı yönün hepsinde Java ile canlı karşılaştırıldı; görünür
çerçeveler değişmedi.

**Proxy üzerinden özel datapack biyomları** (GitHub issue #3). Geyser Velocity veya BungeeCord üzerinde
çalışırken özel biyomlar Bedrock'ta okyanus olarak görünüyordu; özel çim, yaprak, su, sis veya gökyüzü renkleri
yoktu. twilight-proxy artık her Bedrock oyuncusunun özel biyomlarını o oyuncunun yüklediği paketin tablolarıyla
eşler; böylece farklı özel biyomlara sahip birden çok arka uç, sunucu değiştirmelerden ve yeniden bağlanmalardan
sonra da kendi görünümlerini korur. Farklı görünümlere sahip iki sunucunun paylaştığı bir biyom adı artık diğer
sunucunun görünümünü almaz. Twilight olmayan veya `world.bedrock-biome-matching: false` olan arka uçlar
değişmedi; arka uçtaki Geyser eskisi gibi davranır. İki JAR'ın da güncellenmesi gerekir. Oyuncu çevrimiçiyken
yalnızca biyomu değiştiren işlemler (mevsim eklentileri, `/fillbiome`) proxy üzerinden yeniden gönderilmez;
parçalar yeniden gönderildiğinde görünürler.

**Daha fazla vanilla durumda CraftEngine blokları.** Kaktüs, şeker kamışı, fide ve meşe yaprağı durumları
üzerine kurulan bloklar (örneğin bakır bobin, kamışlar, çiçekler, fideler ve palmiye yaprakları) artık vanilla
blok yerine Bedrock'ta kendi görünümlerini gösterir; çarpışma ve çerçeve Java'nınkiyle aynıdır. Yapraklar ve
kamış tabanlı bloklar Java'nın ova yaprak ve çim rengini hazır alır. Doğal kaktüs, şeker kamışı, fide ve
yapraklar, paket Java'da da tam o durumları yeniden kaplamadıkça vanilla kalır. Bedrock'un izin verdiğinden
(eksen başına 30 piksel) uzun veya geniş blok modelleri en dış piksellerinden kırpılır ve bildirim verilir;
hacmi olmayan parçalar atlanır, çünkü tek bir geçersiz blok Bedrock'un tüm özel blokları düşürmesine yol açar.

**Alan etkisi bulutları.** Yarıçapı 0 olan bulutlar artık Bedrock'ta Geyser'ın parçacık girdabını göstermez;
Java da bir şey çizmez. Üzerlerinde binen varlıklar (mobilya ve model eklentilerinin oturakları) oturmaya devam
eder.

**Deneysel oyuncu başına elytra.** Yeni `geyser.elytra-per-player` seçeneği (varsayılan `false`), bir Bedrock
oyuncusunun kendi özel elytrasını da dururken ve süzülürken görmesini sağlar; vanilla elytralar vanilla kalır.
`elytra-override` üzerinde önceliklidir. Arka uç sunucudaki Geyser ile çalışır, proxy'deki Geyser ile henüz
çalışmaz ve özel elytra içeren ilk derlemeden sonra Geyser'ın yeniden başlatılmasını gerektirir.

**Yeniden başlatmalarda aynı paket.** Aynı içerik artık her zaman bayt bayt aynı paketi derler: çok dokulu blok
modellerinin doku sırası JVM çalıştırmasına bağlıydı. Bedrock oyuncularından değişmemiş bir paketi yeniden
indirmeleri artık istenmez.

#3 numaralı issue'nun ayrıntılı raporu için JamDoggie'ye teşekkürler.

**Test edildi:** Leaf 1.21.4 üzerinde CraftEngine, Geyser 2.12.1 (1298 numaralı derleme) ve Floodgate ile; tüm
yönlerdeki eşya çerçevelerinin, yeni blokların, bulutların, süzülme dahil özel elytraların, mobilyaların,
görüntülerin, birinci şahıs bakışta 3B el eşyalarının ve zırhların Java 1.21.4 ve Bedrock 26.6x görüntüleri
karşılaştırıldı. CraftEngine, Oraxen ve ItemsAdder içeriğinden derlenen paketler çevrimdışı karşılaştırıldı:
yalnızca istenen değişiklikler vardı ve Bedrock'un geometri sınırları dışında blok kalmadı. Proxy biyomları
Velocity üzerinde Geyser 2.12.1 (1277, 1298 ve 1310 numaralı derlemeler) ve Leaf 26.3 ile 1.21.4 arka uçlarıyla
doğrulandı; biyom kimlikleri ve tanımları Bedrock tarafında kontrol edildi.

Test sunucusu: `oyna.twilightnw.com`.

İki JAR'ı da güncelleyin ve derlemeden sonra sunucuyu ve proxy'yi bir kez yeniden başlatın; yeni özel bloklar,
eşyalar ve oyuncu kafaları, Geyser onları açılışta kaydettiği için bu yeniden başlatmadan sonra görünür.

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 70 paketteki
420 test geçti. İki JAR da `META-INF` içinde `LICENSE` ve `NOTICE` taşır. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3, 2.12.0 ve
2.12.1'i hedefler; diğer Geyser çekirdek sürümleri doğrulama gerektirir.

Aşağıdaki notlar önceki ön sürümleri anlatır.

#### Twilight 1.0.1-pre.1 - ön sürüm

**Geyser 2.12.1 (1298 numaralı derleme).** Geyser, Twilight'ın kullandığı Java varlık türü kaydını kaldırdı;
bu yüzden o derlemede eşya ve blok görüntüleri, mobilyalar, düşen 3B eşyalar ve nokta bulutu uyarlaması
çalışmayı bıraktı ve açılışta bir hata görünüyordu. Twilight artık kaydı her derlemede bulur; önceki Geyser 2.11
ve 2.12 derlemeleri çalışmaya devam eder. Menü ve yazı yerleşimi de bunu değiştiren derlemelerde Geyser'ın
istemci arayüz profilini okur, bulamazsa klasik profile döner.

**Bedrock'ta özel elytralar.** Bedrock oyuncuları artık diğer oyuncuların özel elytralarını (CraftEngine,
ItemsAdder, Oraxen ve Nexo elytra eşyaları) kendi kanatlarıyla, dururken ve süzülürken görür; Geyser proxy'de
çalışıyorsa da (twilight-proxy). Önceden her planör vanilla elytra olarak görünüyordu. Bir Bedrock oyuncusunun
kendi özel elytrası, Bedrock istemcisi yalnızca vanilla elytra eşyasıyla süzülebildiği için o oyuncuya hâlâ
vanilla görünür. Yeni `geyser.elytra-override` seçeneği (varsayılan `false`) vanilla elytrayı özel kanatlarla
yeniden çizer; tek elytrası tek bir özel görünüm olan sunucular içindir ve Bedrock'ta giyilen her elytrayı,
vanilla olanlar dahil, değiştirir; vanilla elytrası olan sunucularda kapalı bırakın.

**Birinci şahıs bakışta büyük 3B el eşyaları.** Bedrock, 3B aletleri ve silahları (baltalar, üç dişli mızraklar,
kazmalar, kalkanlar) birinci şahıs bakışta Java'dan 1,3 ila 1,8 kat küçük gösteriyordu. Artık Java'nın birinci
şahıs görünümü gibi ölçeklenir ve yerleştirilir: ölçülen siluet alanı Java'nınkinin 0,29-0,50 katından 0,80-0,95
katına çıktı. Düz eşyalar, yaylar ve arbaletler değişmedi; üçüncü şahıs görünüm de değişmedi.

**Modeller ve bloklar.** Yüzleri dokusunu standart olmayan bir anahtar altında adlandıran modeller (bir
ItemsAdder paketinde görüldü; anahtar doku yoluydu, değer `#0`) dönüştürme sırasında onarılır; böylece katı mod
artık tüm dönüştürmeyi reddetmez. Bir `model-repaired` bildirimi modeli adlandırır ve Java istemcilerinin onu
eksik gösterebileceğini, kaynak modelin düzeltilmesi gerektiğini söyler. Belirsiz yüzler katı modda yine hata
verir; artık iletide öğe ve yüz de bulunur. Özel bloklar Java'nın ortam kapanmasını ve yüz gölgelendirmesini
korur: `"ambientocclusion": false` olan bir blok modeli veya `"shade": false` olan öğeler, o malzemeler için
Bedrock'ın `ambient_occlusion` ve `face_dimming` ayarlarını kapatır; böylece parlayan veya düz aydınlatılmış
bloklar artık kararmaz. Diğer bloklar değişmedi.

**Kaynak paketi sunucusu.** Kötü davranışı nedeniyle yasaklanan bir istemci artık ret gönderilmeden önce
yasaklı olarak kaydedilir; böylece yanıtla yarışan bir yeniden bağlanma içeri sızamaz.

Blok gölgelendirmesi, paket sunucusundaki yasak sıralaması ve birinci şahıs ölçeği gözlemi için fikirleri
veren yorik100'e teşekkürler (pull request #2).

**Bilinen sınırlamalar.** Bir Bedrock oyuncusunun kendi özel elytrası o oyuncuya vanilla görünür. Kuzeye bakan
görünmez bir eşya çerçevesindeki özel bir eşya Bedrock'ta görünmeyebilir; bu inceleniyor.

**Test edildi:** Leaf 1.21.4 üzerinde CraftEngine ve Oraxen, Geyser 2.12.1 (1298 numaralı derleme) ve Floodgate
ile; eşyaların, birinci şahıs bakışta 3B el eşyalarının, mobilyaların, görüntülerin, dururken ve süzülürken özel
elytraların ve blokların Java 1.21.4 ve Bedrock 26.6x görüntüleri karşılaştırıldı. Sunucu açılışı Geyser 2.12.0
(1263 numaralı derleme) üzerinde de denendi. CraftEngine ve Oraxen deneme paketlerinin dönüştürülmesi, istenen
değişiklikler dışında beta.6 ile aynı paketleri üretti.

Test sunucusu: `oyna.twilightnw.com`.

İki JAR'ı da güncelleyin ve sunucuyu ve proxy'yi bir kez yeniden başlatın.

[JAR'lar ve SHA-256 dosyaları](artifacts/) siberanka adına Java 25 ile yerelde derlendi; 68 paketteki
400 test geçti. İki JAR da `META-INF` içinde `LICENSE` ve `NOTICE` taşır. Barındırılan CI çalıştırılmadı.

Çalışma zamanı gereksinimleri: Java 21+, Paper/Folia/Spigot 1.21.4+, özel içeriği açık Geyser; twilight-proxy
için proxy'de Geyser bulunan Velocity veya BungeeCord. Çalışma zamanı köprüleri Geyser 2.11.3, 2.12.0 ve
2.12.1'i hedefler; diğer Geyser çekirdek sürümleri doğrulama gerektirir.

#### Twilight 1.0.0-beta.6

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
