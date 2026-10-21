## v5.61.7 – PackHost : bannissement avant la réponse de refus (2026-10-15)

- `PackHost` comptabilise les requêtes invalides et applique leur éventuel bannissement avant d'envoyer les réponses 404/405. Le client ne peut plus recevoir le 20e refus puis reconnecter avant l'enregistrement du bannissement. Les seuils restent inchangés.
- `blocksAddressesThatKeepGuessing` vérifie les 20 refus et attend la fermeture des 19 premières connexions pour que la limite de connexions simultanées ne fausse pas le comptage. La dernière vérification reste immédiate après le 20e refus, sans temporisation ni nouvelle tentative.
- Les corrections de feu et les mappings offhand de la v5.61.6 sont conservés à l'identique.
- Vérification statique des sources et de l'archive uniquement ; aucune compilation ni exécution des tests pour cette livraison.

## v5.61.6 – Feux natifs et mappings offhand Bedrock (2026-10-15)

- Intégration des corrections de feu v5.61.4 dans le projet v5.61.5 fourni ; la politique offhand, son appel dans le compilateur, ses tests et sa documentation sont conservés à l'identique.
- Les feux colorés exportent une sélection explicitement vide : aucun contour sur les flammes. Le clic sur la face du support utilise le traitement natif du feu de Geyser pour demander l'extinction au serveur.
- Les positions et orientations des flammes, les particules de destruction transparentes, le son d'extinction et la correction de l'API ambientOcclusion sont conservés.
- Sources et assertions mises à jour ; aucune compilation, aucun test exécuté et aucune validation en jeu pour cette livraison.

## v5.61.5 – Offhand Bedrock : mappings client uniquement (2026-10-15)

- Les mappings Geyser émettent `bedrock_options.allow_offhand: false` uniquement pour les matériaux Java de base non autorisés dans l'offhand Bedrock.
- Aucune interception serveur : aucun listener Bukkit/Paper, aucune manipulation d'inventaire ni contrôle différé. Comportement Java inchangé.
- Le rendu, les items et la conversion v5.61.3 sont préservés ; test JUnit du mapping fourni.

# Changelog

> Türkçe: [aşağıda](#türkçe)

All notable changes in Twilight are documented here.

## 1.0.1-pre.5 - 2026-10-10

- twilight-proxy on FlameCord and other BungeeCord forks: the item mappings (and the retirement of stale Twilight
  files) are now prepared in the plugin load phase, before Geyser starts and reads them. Before, Geyser read
  the file of the previous run: items that changed while the proxy was down needed a second restart, and stale
  Twilight files were moved away only after Geyser had loaded them. The server list is read from the proxy's
  `config.yml`, which the proxy has not loaded yet at that point.
- ModelEngine: when its model registry cannot be read through its API (a changed API between ModelEngine
  versions, models still loading), the bone items are read from the resource pack ModelEngine generates
  (`resource pack/assets/modelengine/items`, base item from its `Item-Model` setting).
- Builds without item display models (Geyser on the proxy) leave out ModelEngine's and BetterModel's bone items:
  only item displays show them, so they only made the pack larger.
- Auto discovery: when ItemsAdder or CraftEngine has not generated its pack yet, the folders and ZIPs it is set
  to merge (`merge_other_plugins_resourcepacks_folders`, `merge-external-folders`, `merge-external-zip-files`)
  are read as well, so their assets are not missing. Once the generated pack exists it already holds them.
- 263 tests pass (231 Twilight, 32 proxy).

## 1.0.1-pre.4 - 2026-10-10

- twilight-proxy on FlameCord and other BungeeCord forks: Geyser defined its custom blocks before the plugin
  enabled, so no custom block was registered and ores stayed note blocks (and their items flat). The blocks are
  now subscribed in the proxy's plugin load phase, before Geyser starts, from the packs the last run cached;
  once the plugin runs they follow the servers' current packs. Verified on Waterfall: 195 blocks and 29 block
  items registered from the load phase.
- twilight-proxy: a server pack without a text layout table made every text of the players on that server
  fail the layout once ("Java text layout failed for LayoutSource"); such packs are now remembered as having
  no layout.
- 258 tests pass (227 Twilight, 31 proxy).

## 1.0.1-pre.3 - 2026-10-10

- Custom blocks. ItemsAdder's and CraftEngine's blocks (ores, decorations, plants) are note block, mushroom
  block or tripwire states whose model the pack replaces; Bedrock showed the vanilla note block. Every such
  state with a custom model now becomes a Bedrock block: full cubes use Bedrock's block cube with a texture per
  face and Java's rotation, other shapes a block geometry. The packs of all content plugins are merged, the
  effective pack wins where two define a state, as on Java. Twilight and twilight-proxy register the blocks with
  Geyser when it starts (`geyser.custom-blocks`, `bedrock.custom-blocks`); a build that changes them asks for a
  restart like item changes.
- Items drawn like a custom block (ores, placeable blocks) show the block in 3D in the inventory and when
  dropped, as on Java, instead of a flat icon.
- Verified live on Paper 26.2 with Geyser-Spigot and Survival's content (195 blocks, 29 block items): ores,
  rotated CraftEngine drawers, mushroom-block decorations and tripwire plants render, and ore items drop in 3D.
- 257 tests pass (227 Twilight, 30 proxy).

## 1.0.1-pre.2 - 2026-10-10

- Phones and tablets get Java's chest layout. Bedrock's pocket UI profile (the default there) shows chests as
  two scrolling columns with the title in a header bar, so menu art drawn from the title sat in that bar and
  behind the slot panel. Chest, large chest, ender chest, shulker box and barrel screens now use the same Java
  layout as on desktop for both profiles, and the runtime lays their titles out like desktop ones
  (`ui.pocket-container-layout: java`, `bedrock` keeps Bedrock's screens). Measured live with the pocket
  profile on Windows: two real Survival menus land on Java's position (shift 0, 0), and chat, action bar, boss
  bars, sidebar, titles and nameplates pass the same checks as on desktop.
- 252 tests pass (222 Twilight, 30 proxy).

## 1.0.1-pre.1 - 2026-10-10

- Fix: on a server with Geyser-Spigot (or Geyser on a proxy) without `forward-player-ping`, the loading
  protection of 1.0.0-pre.19 and 1.0.0-beta.1 answered Java keep-alives a second time while the client was
  loading its packs. Paper dropped every Bedrock player right after joining ("keepalive response without
  matching challenge", shown as "Timed out"). The protection now answers only when Geyser forwards pings to
  the client; otherwise Geyser already answers and only loading times are logged. Verified live on Paper
  26.2 with Geyser-Spigot.
- 250 tests pass (220 Twilight, 30 proxy).

## 1.0.0-beta.1 - 2026-10-10

First beta. It contains everything up to 1.0.0-pre.19 and these changes:

- The update check orders release stages as alpha, pre, beta, rc and release, so servers on a beta hear
  about newer betas and release candidates. Versions 1.0.0-pre.14 to pre.19 order them alphabetically and do
  not announce this beta: replace both JARs by hand once.
- README: beta status and a compatibility summary at the top. The proxy notes in
  [compatibility](docs/COMPATIBILITY.md) now list what runs on a proxy's Geyser and what still needs Geyser
  on the backend.
- Issue templates (bug, compatibility request, question) on GitHub and GitLab, merge and pull request
  templates, a [security policy](SECURITY.md) and [contribution notes](CONTRIBUTING.md).
- 248 tests pass (218 Twilight, 30 proxy).

## 1.0.0-pre.19 - 2026-10-10

Fixes for two reports from a proxy network ([field report](docs/FIELD_REPORT_2026-10-10.md)).

- Leave item display models out of packs for servers without Geyser (`geyser.item-display-models: auto`).
  They were a second copy of every 3D item (Survival: one entity with 3,413 variants) that every client
  built while loading, although only the display bridge of a server's own Geyser uses them. Survival's
  loading time after joining fell from 156 s to under 10 s. Pack JSON is written without indentation.
- Loading protection on the backend (`geyser.loading-protection-seconds`) and on the proxy
  (`bedrock.loading-protection-seconds`), 300 s by default: until the client is in game, Java keep-alives
  and pings are answered for it, so a client that loads packs for minutes is no longer dropped ("read
  timed out"). Loads of 10 s or more are logged.
- twilight-proxy runs Twilight's text layout and translations in the proxy's Geyser, with the pack each
  player loaded. Images on characters Twilight moves for Bedrock (BoxPVP's ItemsAdder prefix on U+A840)
  showed as plain Unicode characters on proxy networks.
- `ui.max-glyph-cell` caps glyph pages (256: 4096x4096 instead of 8192x8192), and the build names pages of
  8192x8192 pixels.
- The same Bedrock item with other display options on two servers is logged as information, not as a
  conflict.
- 247 tests pass (217 Twilight, 30 proxy).

## 1.0.0-pre.18 - 2026-10-09

- Move stale Twilight files out of Geyser automatically, before Geyser loads them: item mappings
  (`twilight*.json` under `custom_mappings`) and packs (`twilight*` or a manifest named "Twilight") left
  by older versions, hand copies or sync tools. They registered the same items with other Bedrock
  identifiers or sent a second Twilight pack, which showed as broken icons after updating. Files go to
  `retired/<time>/` in Twilight's or twilight-proxy's folder, never deleted (`retire-stale-files`).
- twilight-proxy reads backends on the same machine from their folders (`local-backends: auto`):
  matched by port and Twilight installation, it loads each `export/Twilight.mcpack` at start (so
  Geyser starts with every server's current items, no player needed) and within seconds after each
  build.
- Pending Geyser restarts for changed items are shown to admins when they join and to online admins
  at once, and in `/twilightproxy`; `item-mappings.restart: when-empty` (proxy) and
  `geyser.restart-for-item-changes: when-empty` (server) restart once nobody is online.
- 244 tests pass (215 Twilight, 29 proxy).

## 1.0.0-pre.17 - 2026-10-09

- Attach to Geyser on proxies and servers that load Floodgate before Geyser. Floodgate bundles its
  own copy of Geyser's event library, and linking against it failed with "loader constraint violation"
  on FlameCord, Waterfall and Velocity with Floodgate (every earlier version). Twilight and
  twilight-proxy now subscribe to Geyser's events through reflection with Geyser's own class loader,
  and a test fails if any class refers to that library. Verified on Velocity and Waterfall with
  Floodgate loaded first: attach, item mappings, pack host and server switches of a Floodgate player.
- Log an attach failure with its full stack trace and with the jars each side took Geyser's event
  library from.
- Read login servers only from plugins the proxy loaded; a folder left behind by a removed login
  plugin is reported instead of making its server a login server.
- Name other Twilight mapping files in the proxy's Geyser (copied by hand or written by an older sync
  tool), count the selectors they map differently, and show them in `/twilightproxy`.
- 239 tests pass (213 Twilight, 26 proxy).

## 1.0.0-pre.16 - 2026-10-09

- twilight-proxy: when the proxy lists a Geyser plugin but its classes are not visible to
  twilight-proxy (proxy forks that isolate plugins, such as FlameCord), report "... is installed,
  but its API is not visible to twilight-proxy" with the error and keep trying, instead of "Geyser is
  not installed". Tested on Waterfall 1.21 build 615.
- Twilight on a backend without Geyser: `/twilight status` and the start-up log say that Geyser is
  not on this server (normal on a proxy network) and whether the pack is shared with twilight-proxy,
  instead of "Geyser unavailable: No local Geyser plugin data directory was found".
- 235 tests pass (212 Twilight, 23 proxy).

## 1.0.0-pre.15 - 2026-10-09

Fixes from a field report on a proxy network; each point was reproduced locally first
([field report](docs/FIELD_REPORT_2026-10-09.md)).

- Write the content report without Gson reflection. On servers whose Gson cannot open `java.time`
  (Paper 1.21 and forks with Gson 2.11 on Java 17+) every scan failed with
  "Failed making field 'java.time.Instant#seconds' accessible" and no pack was built. A report that
  cannot be written no longer stops a build.
- Attach twilight-proxy to Geyser reliably: initialise after every other plugin on Velocity, retry a
  Geyser that is still loading (two minutes, then when players join), tell absent, not ready and
  incompatible apart with the reason, remove half-registered listeners, start the pack host once
  attached, and show the state in `/twilightproxy`. Before, a Geyser that loaded after twilight-proxy
  was reported as "not installed".
- Deliver custom items on proxy networks: Twilight puts its Geyser item mappings into the shared
  pack, and twilight-proxy merges those of every `auto` and `packs/` source into
  `custom_mappings/twilight-proxy_item_mappings.json` of the proxy's Geyser before Geyser reads it,
  asks for a restart when they change later, lists selectors two servers map differently, names
  hand-made copies and warns when the proxy's locale (Turkish, Azerbaijani) keeps Geyser from reading
  them.
- Derive the Bedrock item of a Java selector only from what Geyser matches (item, custom model data or
  item model, predicates), so the same selector is the same Bedrock item on every backend; a second
  model for an already used selector is reported and skipped. Item identifiers change once: restart
  Geyser after updating.
- Find login servers in the configuration of LeaderOS Auth, AuthMeVelocity, AuthMeBungee, LibreLogin,
  JPremium and similar plugins on the proxy and treat them like `login-servers`.
- 234 tests pass (212 Twilight, 22 proxy).

## 1.0.0-pre.14 - 2026-10-09

- Add an update check to Twilight and twilight-proxy (`update-check`, on by default): about 20 seconds
  after start and every six hours the public release list is read from GitHub, or from the GitLab mirror
  when GitHub fails or refuses; a newer version is logged once and shown with a clickable link to players
  with `twilight.update` (default: operators) or `twilight.proxy.update`/`twilight.proxy.admin` when they
  join. Prereleases are offered only to servers running a prerelease. Only version tags are read from
  the answer (at most 2 MiB, 5 s to connect, 10 s per request); nothing about the server is sent and
  nothing is downloaded. Unreachable hosts are logged once with a short reason (no connection, timeout,
  untrusted certificate). `/twilight status` and `/twilightproxy` show the last result.
- Publish every change as a new version instead of rebuilding a released one.
- Tested live: an operator joining a Paper server running an older version got the notice with a working
  link, a non-operator did not, `/twilight reload` turned the check off and on; Velocity found the newer
  release on GitHub, and BungeeCord without a trusted certificate logged one line and kept running.
  221 tests pass (210 Twilight, 11 proxy).

## 1.0.0-pre.13 - 2026-10-09

- Count a pack reconnect as arrived only once the player is connected to its server. Login plugins that
  change the target after every other plugin (LeaderOS Auth on BungeeCord, priority 127) used to make
  twilight-proxy believe the player had arrived, so the login plugin's later move to the lobby caused a
  second reconnect.
- Reconnect a fresh session once, a few seconds after it is in game, when its first server is not the one
  its pack was chosen for (BungeeCord returning players to their last server, forced hosts).
- Match a Bedrock session by name only before Geyser linked it to a Java player and only from the address
  it plays from, so on offline-mode networks a Java player who takes a Bedrock player's name cannot steer or
  trigger its reconnects; use a client's join address for a transfer only when it is a plain host name or
  IP address.
- Tested with LeaderOS Auth Plus 1.1.1 on BungeeCord (BungeeGuard, with and without Floodgate) and Velocity
  (LimboAPI, Sonar): one reconnect per server change with and without a login session; see the
  [login plugin test](docs/PROXY_AUTH_2026-10-09.md). 215 tests pass (204 Twilight, 11 proxy).

## 1.0.0-pre.12 - 2026-10-08

- Make twilight-proxy's pack reconnects work with login plugins (AuthMe with AuthMeVelocity or
  AuthMeBungee, LibreLogin, nLogin, JPremium): a login server chosen for the reconnected session is
  kept, a refused first server sends the player to the proxy's first server instead of leaving it
  without a server until Velocity's read timeout, and after the login the player goes on to the
  server it reconnected for through a new connection request that every plugin checks.
- Never reconnect a session for its first server, so a plugin that sends players elsewhere first no
  longer causes a second reconnect; add `login-servers` for servers that never cause one.
- Scale reconnect deadlines with the pack (`transfer-timeout-seconds: auto`: three minutes plus the
  pack at 128 KiB/s, at most an hour) and log every step: transfer with size and deadline, reconnect,
  arrival with timings, refused or redirected first servers, clients that do not come back within 60
  seconds (with the address they were sent to), abandoned reconnects, transfer limits and a pack-host
  hint for packs of 32 MiB or more. Warn when Velocity's `login-ratelimit` is longer than a reconnect.
- Give Geyser an immutable copy of each pack version (`cache/versions/`), so a pack rebuilt during a
  long download cannot corrupt it, and hash new packs before the first session needs them.
- Pack host: keep the connection open until the client closes it after a download (antivirus web
  shields aborted 40 and 152 MiB downloads when the host closed after 5 s idle), and remember addresses
  whose link did not work for 30 minutes with the reason in the log (unreachable port, changed address,
  unfinished download) plus a warning when the host looks unreachable from outside.
- Stop link downloads on the proxy after `download-timeout-seconds` without data or below 64 KiB/s,
  instead of a fixed timeout that large packs could not meet; log large and stalled `auto` transfers.
- Add the [reconnect and large pack test](docs/PROXY_RECONNECT_2026-10-08.md) and document login
  plugins, forwarding and protections in the [wiki](WIKI.md#reconnects-login-plugins-and-protections).
  214 tests pass (204 Twilight, 10 proxy).

## 1.0.0-pre.11 - 2026-10-07

- Add the pack host (`pack-host`, off by default) to Twilight and twilight-proxy: Bedrock players
  download the packs over HTTP from the server that runs Geyser instead of Geyser's in-game transfer,
  like ItemsAdder's or CraftEngine's self-host on a port of your choice. Every pack of the session is
  announced with a link minted for that Bedrock session: a random 256-bit token, valid for
  `link-minutes` and `downloads-per-link`, and by default only from the IP address the player
  connects to Geyser from. Any other request gets the same empty 404.
- Harden the host against abuse: strict request parsing (GET/HEAD, 8 KiB heads, five-second
  timeout), connection limits in total and per address (IPv6 per /64), 60 requests a minute per
  address, a 15-minute block after 20 refused requests, a minimum transfer rate, immutable pack
  copies checked against Geyser's SHA-256, and `X-Forwarded-For` only from `trusted-proxies`.
- Keep joining independent of the host: a failed download falls back to Geyser's transfer (tested
  with links to a closed port). The host subscribes after every other listener and hosts all file
  packs together (Twilight's, Geyser's integrated pack, other plugins'), keeping pack options and
  content keys, because Bedrock does not mix links with in-game packs.
- Support `public-address` (`auto`, a host, or `http(s)://` behind a reverse proxy), `public-port`
  for NAT and `bind-address`; twilight-proxy's configuration accepts one-line lists such as
  `trusted-proxies: [127.0.0.1]`.
- Document every key, network layouts and the security model in the [wiki](WIKI.md#pack-hosting).
  209 tests pass (202 Twilight, 7 proxy).

## 1.0.0-pre.10 - 2026-10-05

- Draw boss bars whose sprites a pack redraws (`boss_bar/<colour>_background`, `_progress` and the
  `notched_*` overlays) with those sprites on Bedrock, in Java's order and cut at the bar's value;
  colour and style changes are sent again live. Untouched colours keep Bedrock's bar.
- Show long boss bar names whole: names that are not layered were cut into the layer labels when
  they were longer than the first block. Layered names now carry their own marker.
- Fit CustomNameplates' default boss bar (three backgrounds with text) into Bedrock's 256 characters:
  a larger first block for the top layer and spaces instead of spacer glyphs for four- and eight-unit
  moves. Named-font spaces (shift fonts) stay spaces when the line falls back to one label.
- Add the [boss bar and model review](docs/BOSSBARS_MODELS_2026-10-05.md) with new captures: redrawn
  boss bars, CustomNameplates' default boss bar and an original BetterModel mob in walk and idle
  animations (GIFs). 200 tests pass (193 Twilight, 7 proxy).

## 1.0.0-pre.9 - 2026-10-05

- Add twilight-proxy (`TwilightProxy.jar`) for Velocity and BungeeCord: every backend server gets
  its own Bedrock pack through Geyser on the proxy, from Twilight on that backend (`auto`), a file
  in `packs/` or a download link. Bedrock players who move to a server with another pack are
  reconnected to load it and sent on. Tested on Velocity 4.2.0 and BungeeCord 26.1.
- Share the exported pack with twilight-proxy over the `twilight:proxy` plugin-message channel:
  HMAC-SHA256 signed with the secret the proxy already shares with its servers (Velocity
  forwarding, BungeeGuard) or `proxy.secret`, with timestamps, single-use request nonces, one
  bounded transfer at a time, and nothing announced without a secret.
- Refuse to lay out text longer than 16384 characters or moves wider than 2^20 units (sent
  unchanged instead), so crafted text cannot grow the layout without bound.
- Add the [wiki](WIKI.md): installation, commands, every configuration key, files, pack entries,
  API, protocol, security model and troubleshooting. 191 tests pass (184 Twilight, 7 proxy).

## 1.0.0-pre.8 - 2026-10-04

- Draw text and images that Java moves back over earlier ones (stacked menu art,
  CustomNameplates backgrounds) with one Bedrock label per layer in chest titles,
  the action bar and boss bars, including font shifts and shadowless text
  (`ui.java-text-layers`). Boss bars that the Java pack makes transparent stay
  hidden.
- Show custom biomes in their exact grass, foliage, water, fog and sky colours and
  climate: the pack redefines 25 Bedrock biomes that only old worlds use. Biomes come
  from datapacks and the server registry; the current RealisticSeasons season gets
  slots first and a season change rebuilds the pack. Biome-only updates
  (`/fillbiome`, seasons) now reach Bedrock players without a rejoin.
- Hide the Bedrock name of ridden players and mobs like Java, so nameplate plugins
  show only their plate, and hide Bedrock's name tag box when CustomNameplates draws
  its own backgrounds (`ui.nametag-background`).
- Choose per provider what is read (`sources.providers`: auto, generated, contents
  or off) and whether world datapacks are read (`sources.datapacks`); `auto` follows
  each provider's own delivery settings.
- Export every build to `plugins/Twilight/export` (`Twilight.mcpack` and Geyser item
  mappings) and allow another plugin or a proxy to send the pack
  (`geyser.send-pack-to-bedrock: false`). Servers without a local Geyser only export.
- Convert content Java tolerates the way Java shows it instead of failing strict
  builds: missing textures and models, screen-sized overlay glyphs and vanilla sounds
  changed without `vanilla-override` are notices. Complete builds of seven
  production servers pass with the default configuration; a first build still
  deploys when nothing is deployed yet.
- Keep layered chest titles and boss bar names at Java's height (Bedrock raised them
  by 4.5 units with one-unit line spacing).
- Replace the published captures with current ones, including animated Java/Bedrock
  comparisons; see [stacked images, nameplates and exact biomes](docs/LAYERS_BIOMES_2026-10-04.md).
  183 tests pass.

## 1.0.0-pre.7 - 2026-10-04

- Treat each provider's generated pack (CraftEngine `resource_pack.zip`,
  ItemsAdder output, Nexo/Oraxen packs) as what Java players receive: it now
  outranks the provider's working folders. Packs of providers that are not
  installed, or whose settings do not send them while another provider sends a
  generated pack, only fill gaps. Discover CustomNameplates and BetterHUD packs
  and ignore Nexo's vanilla asset cache.
- Show datapack and plugin biomes on Bedrock as the vanilla biome with the
  closest grass, foliage, water and fog colours and precipitation, instead of
  Geyser's ocean fallback (`world.bedrock-biome-matching`).
- Merge the resource packs' `lang` files like Java and give them to Geyser, so
  Bedrock players see the names of datapack and plugin content and translation
  overrides such as an image as the ender chest title or a hidden inventory label
  (`ui.java-translations`).
- Darken the images of container titles without a colour code as Java does
  (Java multiplies glyphs by the default title colour 0x404040; Bedrock never
  tints resource-pack glyphs), using pre-darkened copies (`ui.java-glyph-tint`).
- Keep the origin spacers of bold titles unstyled; Bedrock drew them wider and
  moved bold titles about four units to the right. Legacy colour codes inside
  laid-out text no longer take space.
- Keep a blank inventory label blank on Bedrock (Bedrock treated an empty
  translation as missing).
- Verify the UI conversion with 104 menus of two real servers and an original-art
  style suite: every window and 97 of 104 title bands match Java; layers moved back
  over an earlier image remain unsupported. Complete builds of six real servers and
  a live custom-biome scene are in the [UI campaign](docs/UI_CAMPAIGN_2026-10-04.md).
  Older screenshots were retired in favour of current captures. 156 tests pass.

## 1.0.0-pre.6 - 2026-10-04

- Lay out Java text on every Bedrock text surface: chat, action bar, titles,
  boss bars, scoreboards, entity names, text displays and other container titles.
  Named fonts, remapped characters and space shifts now show the right images at
  Java's positions; centred lines follow Java's integer centring. Controlled by
  `ui.java-text-surfaces`; `ui.java-text-layout` no longer requires the container
  layout.
- Stop treating ItemsAdder's vanilla asset copies, temporary build folders and
  stale nested packs as sources; they hid hundreds of custom items. Use a renamed
  ItemsAdder output pack when `generated.zip` is absent.
- Resolve texture atlas sprite renames, decode protected PNGs like Java, read
  object-form model textures, allow vanilla models behind custom selectors and use
  Java's missing texture for undefined face textures.
- Keep characters from Java's own font sheets as Bedrock text and treat
  off-screen or transparent spacing images as advances, removing alias-page
  overflow on real servers.
- Report content Java rejects as well (malformed fonts, unreadable TrueType files,
  absent sound files, skin-rendered heads) as notices instead of failing strict
  builds.
- Verify every surface live with independent SkyBlock content and run complete
  production builds for five real servers; see the
  [text surface review](docs/TEXT_SURFACES_2026-10-04.md). 138 tests pass.

## 1.0.0-pre.5 - 2026-10-03

- Lay out chest titles for Bedrock players with Java font metrics. Negative and
  custom spacing (space providers, negative-height bitmaps), glyph bearings and
  characters Java remaps in the default or named fonts are reproduced with
  invisible spacer glyphs and private-use aliases. Real menu images now land on
  Java's exact GUI pixels, including the former one-unit offset. Controlled by
  `ui.java-text-layout`; the touch layout receives glyph substitution only.
- Combine font definitions from every resource pack like Java, and ignore
  TrueType fonts Java cannot load.
- Verify six real Survival menus, every chest size and unchanged hopper, furnace
  and dispenser screens in paired captures; see the
  [text layout review](docs/TEXT_LAYOUT_2026-10-03.md). 109 tests pass.

## 1.0.0-pre.4 - 2026-10-03

- Generate a Java container layout for Bedrock desktop chest screens. Wide
  font-image titles are no longer wrapped, hyphenated or clipped. Title and
  inventory labels use Java's positions, colour and drawing order, and chest,
  inventory and hotbar rows keep Java's spacing for 1 to 6 chest rows. The
  partial UI is merged into Bedrock's vanilla UI and can be disabled with
  `ui.java-container-layout`.
- Verify all six chest sizes and hopper, dispenser and furnace screens in paired
  Java/Bedrock captures with scripted measurements; record Bedrock's untinted
  bitmap glyphs and one-unit glyph offset in the
  [container layout review](docs/CONTAINER_LAYOUT_2026-10-03.md). 95 tests pass.

- Automatically enlarge bitmap-font atlas cells while retaining each glyph's
  Java display dimensions and ascent. Wide rank labels and larger UI/HUD images
  no longer need to be shrunk or omitted solely because they exceed 16 pixels.
- Add pixel-preservation and neighboring-glyph regressions; all 87 tests pass.
  Repeat whole-source audits and compare six further real UI/HUD images in both
  clients. Record remaining title clipping, horizontal padding and fractional
  sampling differences in the [wide glyph review](docs/WIDE_GLYPHS_2026-10-02.md).
- Expand the acceptance contract to 40 areas and 289 required scenarios after
  reviewing external feature documentation and the available menu corpus.
  Add namespace-collision and multi-row glyph-sheet regressions; the local
  suite now passes 89 tests. This expands validation, not runtime UI support.

## 1.0.0-pre.3 - 2026-10-02

- Preserve Java bitmap glyph heights and ascents with measured Bedrock cell
  coordinates. Support representable negative ascents and reject visible
  overflow without silently shrinking or clipping; isolate transparent padding
  from neighboring characters.

- Preserve each static composite child's display transforms in display entities,
  attachables and inventory previews. This fixes detached BetterModel head parts
  and unintended head/body tilt without changing provider assets. Generated
  sprite children remain visible beside cuboid children.

- Render custom inventory icons from Java model geometry and inherited GUI
  transforms instead of the first texture. Preserve face UVs, UV quarter turns,
  element rotation/rescale, depth and transparency in static previews.

- Accept Java-compatible dotted and uppercase pack overlay directory names;
  retain traversal protection. Extended real-source testing exposed versioned
  directories that previously stopped Survival content discovery.
- Match Java's full-height passenger attachment for cloud-mounted item displays.
  This corrects their vertical position and the potion basket's dark lighting
  above solid blocks, without changing textures, shaders or brightness overrides.
- Automatically represent invisible, zero-radius Java cloud anchors with inert
  Bedrock actors. Preserve cloud metadata and mounted passengers across changes;
  ordinary clouds retain Geyser translation. This removes the unwanted particles
  observed around ModelEngine models without editing provider assets.
- Keep item-display head yaw aligned with body yaw and apply entity yaw once,
  correcting mounted BetterModel models that faced away from their Java reference.
- Add a seven-model Java/Bedrock comparison with captured failures and follow-up
  evidence; lighting and full pose parity remain acceptance gaps.
- Match Java display visibility: ignore base entity invisibility and hide
  zero-view-range displays while retaining their current item and transform.
- Added a development Geyser item-display bridge with separate quaternion
  interpolation, Java item-frame rotation, and correct start/duration semantics.
- Added live BetterModel and ModelEngine bone-item registry discovery. Strict
  conversion rejects incomplete provider discovery.
- Select pack overlays by the target Minecraft format and declaration order;
  inactive overlays are no longer imported as independent packs.
- Resolve explicit vanilla model textures and their frame metadata from the
  verified client cache while preserving authored texture overrides.
- Require a restart for display-index changes, and validate pack ZIP data before
  replacing deployed files.
- Corrected custom third-person item rotation axes, signs, and model-frame
  conversion to preserve Java display orientation, including compound angles.
- Applied Java's left-hand mirroring to explicitly authored left-hand poses.
- Fixed quaternion-to-Euler conversion at the positive 90-degree singularity,
  which could reverse an item's orientation.
- Added regressions for yaw, all three orientation axes, explicit left-hand
  transforms, and both Euler singularities. Full in-game visual parity remains
  unverified.
- Corrected rotated cuboid X/Y signs, preserved face UV rotations, and derived
  omitted face UV rectangles from Java element bounds.
- Sampled the first authored animation frame for static texture export instead
  of stretching a whole animation sheet; tall static PNGs are no longer cropped.
- Rejected oversized GUI bitmap glyphs and custom spacing in strict mode instead
  of silently shrinking menus into emoji-sized cells. Diagnostic builds report
  these omissions; full Bedrock GUI adaptation remains unsupported.
- Reported a required server restart when custom item mappings differ from the
  running Geyser registry. Texture-only reloads remain available; configuration
  reloads no longer clear the pending restart requirement.
- Added a JVM-locale diagnostic for Geyser's Turkish/Azeri enum-parsing issue.
- Skipped optional event registration for disabled providers, avoiding closed
  classloader errors when an incompatible ModelEngine version fails to enable.
- Disabled automatic GitHub and GitLab pipeline triggers. Builds are run and
  tested locally under siberanka; pushes do not run hosted builds.

## 1.0.0-pre.2 - 2026-09-22

### Java-to-Bedrock presentation

- Fixed custom tool and weapon pose selection by deriving Geyser's handheld
  presentation from the resolved Java model parent chain instead of the base
  Minecraft item identifier.
- Preserved authored Java first- and third-person translation, rotation, and
  scale without automatic geometry fitting that could shrink or reposition a
  model into an incorrect held pose.
- Fixed chat emoji height by composing every Bedrock Unicode page on a stable
  16-pixel grid and bottom-aligning each glyph independently, including pages
  that also contain oversized GUI glyphs.

### Validation

- Added regressions for model-parent pose selection, exact hand transforms,
  and mixed-size bitmap glyphs sharing one Unicode page.
- Removed pre-fix pose and emoji captures from the public acceptance gallery.

## 1.0.0-pre.1 - 2026-09-21

### Server platform

- Added one server plugin for Paper, Folia, and Spigot with a Bukkit service
  API, operation events, commands, structured logs, and controlled Geyser
  reloads.
- Added deterministic provider discovery, lifecycle hooks, command
  synchronization, source fingerprints, and delayed settle checks for
  ItemsAdder, CraftEngine, Nexo, Oraxen, ModelEngine, and BetterModel.
- Added authored `contents` and `resources`, provider `data` and `cache`,
  datapack, configured source, and generated-pack fallback layers.

### Java-to-Bedrock conversion

- Added current and legacy item-definition handling, Geyser custom mappings,
  layered textures, Java cuboids, display transforms, equipment attachables,
  block states, creative metadata, and animated textures.
- Preserved Bedrock's native pose, pull, charge, cast, and line behavior for
  exact texture-only bow, crossbow, and fishing-rod recolours.
- Added runtime-selected Java state geometry for layered, transformed,
  animated, and volumetric weapons, including distinct crossbow arrow and
  rocket states.
- Added bitmap-font conversion with Unicode collision protection and layered
  custom-sound conversion with recursive references and OGG validation.
- Added hash-verified, version-matched Mojang model, texture, font, and sound
  fallback for explicitly referenced vanilla assets.

### Reliability

- Added strict publication validation, bounded resource names, safe path and
  archive handling, deterministic output, transactional deployment,
  last-known-good restoration, and bounded backups.
- Added focused source, font, and sound audits plus 28 automated tests for the
  current server compiler and deployment path.
- Added GitHub Actions and GitLab CI pipelines that build and test the same
  tagged source before publishing `Twilight.jar` and its SHA-256 checksum.

---

## Türkçe

### Değişiklik günlüğü

Twilight'taki tüm önemli değişiklikler burada belgelenir.

#### 1.0.1-pre.5 - 2026-10-10

- FlameCord ve diğer BungeeCord türevlerinde twilight-proxy: eşya eşlemeleri (ve eski Twilight dosyalarının
  emekliye ayrılması) artık eklenti yükleme aşamasında, Geyser başlayıp onları okumadan önce hazırlanır.
  Önceden Geyser önceki çalışmanın dosyasını okuyordu: proxy kapalıyken değişen eşyalar ikinci bir yeniden
  başlatma gerektiriyor, eski Twilight dosyaları ise ancak Geyser onları yükledikten sonra kaldırılıyordu. Sunucu
  listesi, proxy'nin o anda henüz yüklemediği `config.yml` dosyasından okunur.
- ModelEngine: model kaydı API'si üzerinden okunamadığında (ModelEngine sürümleri arasında değişen API, hâlâ
  yüklenen modeller) kemik eşyaları ModelEngine'in ürettiği kaynak paketinden okunur
  (`resource pack/assets/modelengine/items`, temel eşya `Item-Model` ayarından).
- Eşya görüntüsü modelleri olmayan derlemeler (Geyser proxy'de) ModelEngine'in ve BetterModel'in kemik
  eşyalarını dışarıda bırakır: onları yalnızca eşya görüntüleri gösterir, bu yüzden yalnızca paketi
  büyütüyorlardı.
- Otomatik keşif: ItemsAdder veya CraftEngine paketini henüz üretmediyse, birleştirmek üzere ayarlandığı klasörler
  ve ZIP'ler (`merge_other_plugins_resourcepacks_folders`, `merge-external-folders`, `merge-external-zip-files`)
  da okunur; böylece onların varlıkları eksik kalmaz. Üretilmiş paket oluşunca bunları zaten içerir.
- 263 test geçti (231 Twilight, 32 proxy).

#### 1.0.1-pre.4 - 2026-10-10

- FlameCord ve diğer BungeeCord türevlerinde twilight-proxy: Geyser özel blokları eklenti etkinleşmeden önce
  tanımlıyordu; bu yüzden hiçbir özel blok kaydedilmiyor, madenler nota bloğu (eşyaları düz) kalıyordu. Bloklar
  artık proxy'nin eklenti yükleme aşamasında, Geyser başlamadan önce, son çalışmanın önbelleğe aldığı paketlerden
  abone edilir; eklenti çalışınca sunucuların güncel paketlerini izler. Waterfall'da doğrulandı: yükleme
  aşamasından 195 blok ve 29 blok eşyası kaydedildi.
- twilight-proxy: yazı yerleşim tablosu olmayan bir sunucu paketi, o sunucudaki oyuncuların her yazısında
  yerleşimin bir kez başarısız olmasına yol açıyordu ("Java text layout failed for LayoutSource"); böyle paketler
  artık yerleşimi olmayan paket olarak hatırlanır.
- 258 test geçti (227 Twilight, 31 proxy).

#### 1.0.1-pre.3 - 2026-10-10

- Özel bloklar. ItemsAdder'ın ve CraftEngine'in blokları (madenler, süslemeler, bitkiler), modelini paketin
  değiştirdiği nota bloğu, mantar bloğu veya tuzak teli durumlarıdır; Bedrock vanilla nota bloğunu gösteriyordu.
  Özel modeli olan her böyle durum artık bir Bedrock bloğu olur: tam küpler yüz başına doku ve Java'nın döndürmesiyle
  Bedrock'un blok küpünü, diğer şekiller bir blok geometrisini kullanır. Tüm içerik eklentilerinin paketleri
  birleştirilir; iki paket bir durumu tanımladığında Java'daki gibi etkin paket kazanır. Twilight ve twilight-proxy
  blokları Geyser açılırken kaydeder (`geyser.custom-blocks`, `bedrock.custom-blocks`); blokları değiştiren bir
  derleme, eşya değişiklikleri gibi yeniden başlatma ister.
- Özel bir blok gibi çizilen eşyalar (madenler, yerleştirilebilir bloklar) bloğu düz bir simge yerine Java'daki gibi
  envanterde ve yere düştüğünde 3B gösterir.
- Paper 26.2 ve Geyser-Spigot üzerinde Survival'ın içeriğiyle (195 blok, 29 blok eşyası) canlı doğrulandı: madenler,
  döndürülmüş CraftEngine çekmeceleri, mantar bloğu süslemeleri ve tuzak teli bitkileri çizilir, maden eşyaları 3B
  düşer.
- 257 test geçti (227 Twilight, 30 proxy).

#### 1.0.1-pre.2 - 2026-10-10

- Telefonlar ve tabletler Java'nın sandık yerleşimini alır. Bedrock'un pocket arayüz profili (orada varsayılan)
  sandıkları başlığı bir başlık çubuğunda duran iki kaydırılabilir sütun olarak gösterir; bu yüzden başlıktan
  çizilen menü görselleri o çubukta ve yuva panelinin arkasında kalıyordu. Sandık, büyük sandık, ender sandığı,
  shulker kutusu ve fıçı ekranları artık iki profilde de masaüstündekiyle aynı Java yerleşimini kullanır ve çalışma
  zamanı başlıklarını masaüstündeki gibi yerleştirir (`ui.pocket-container-layout: java`; `bedrock` Bedrock'un
  ekranlarını korur). Windows'ta pocket profiliyle canlı ölçüldü: iki gerçek Survival menüsü Java'nın konumuna oturur
  (kayma 0, 0); sohbet, aksiyon çubuğu, boss çubukları, yan panel, başlıklar ve isim plakaları masaüstündeki aynı
  denetimleri geçer.
- 252 test geçti (222 Twilight, 30 proxy).

#### 1.0.1-pre.1 - 2026-10-10

- Düzeltme: Geyser-Spigot'lu (veya Geyser'ı proxy'de olan) ve `forward-player-ping` kapalı bir sunucuda,
  1.0.0-pre.19 ve 1.0.0-beta.1'in yükleme koruması istemci paketlerini yüklerken Java keep-alive'larını ikinci
  kez yanıtlıyordu. Paper her Bedrock oyuncusunu katıldıktan hemen sonra atıyordu ("keepalive response without
  matching challenge", "Zaman aşımı" olarak görünür). Koruma artık yalnızca Geyser ping'leri istemciye
  yönlendirdiğinde yanıtlar; aksi hâlde Geyser zaten yanıtlar ve yalnızca yükleme süreleri günlüğe yazılır.
  Paper 26.2 ve Geyser-Spigot üzerinde canlı doğrulandı.
- 250 test geçti (220 Twilight, 30 proxy).

#### 1.0.0-beta.1 - 2026-10-10

İlk beta. 1.0.0-pre.19'a kadar olan her şeyi ve şu değişiklikleri içerir:

- Güncelleme denetimi sürüm aşamalarını alpha, pre, beta, rc ve kararlı sürüm olarak sıralar; böylece beta
  çalıştıran sunucular daha yeni betaları ve sürüm adaylarını duyar. 1.0.0-pre.14 ile pre.19 arasındaki sürümler
  bunları alfabetik sıralar ve bu betayı duyurmaz: iki JAR'ı bir kez elle değiştirin.
- README: en üstte beta durumu ve uyumluluk özeti. [Uyumluluk](docs/COMPATIBILITY.md#türkçe) belgesindeki
  proxy notları artık proxy'deki Geyser'da nelerin çalıştığını ve nelerin hâlâ arka uçta Geyser gerektirdiğini
  listeler.
- GitHub ve GitLab'da issue şablonları (hata, uyumluluk isteği, soru), birleştirme isteği şablonları, bir
  [güvenlik politikası](SECURITY.md#türkçe) ve [katkı notları](CONTRIBUTING.md#türkçe).
- 248 test geçti (218 Twilight, 30 proxy).

#### 1.0.0-pre.19 - 2026-10-10

Proxy'li bir ağdan gelen iki bildirimin düzeltmeleri ([saha raporu](docs/FIELD_REPORT_2026-10-10.md)).

- Geyser'ı olmayan sunucuların paketlerinde eşya görüntüsü modelleri yer almaz (`geyser.item-display-models:
  auto`). Bunlar her 3B eşyanın ikinci bir kopyasıydı (Survival: 3.413 varyantlı tek bir varlık) ve her
  istemci yüklerken bunları kuruyordu; oysa yalnızca sunucunun kendi Geyser'ındaki görüntü köprüsü onları
  kullanır. Survival'ın katıldıktan sonraki yükleme süresi 156 saniyeden 10 saniyenin altına indi. Paket
  JSON'u girintisiz yazılır.
- Arka uçta (`geyser.loading-protection-seconds`) ve proxy'de (`bedrock.loading-protection-seconds`) yükleme
  koruması, varsayılan 300 saniye: istemci oyuna girene kadar Java keep-alive'ları ve ping'leri onun yerine
  yanıtlanır; paketleri dakikalarca yükleyen bir istemci artık atılmaz ("read timed out"). 10 saniye veya
  daha uzun yüklemeler günlüğe yazılır.
- twilight-proxy, Twilight'ın yazı yerleşimini ve çevirilerini proxy'deki Geyser'da, her oyuncunun yüklediği
  paketle çalıştırır. Twilight'ın Bedrock için taşıdığı karakterlerdeki görseller (BoxPVP'nin U+A840
  üzerindeki ItemsAdder öneki) proxy'li ağlarda düz Unicode karakterleri olarak görünüyordu.
- `ui.max-glyph-cell` glif sayfalarını sınırlar (256: 8192x8192 yerine 4096x4096) ve derleme 8192x8192
  piksellik sayfaları adlarıyla bildirir.
- İki sunucuda başka görüntü seçenekleriyle aynı Bedrock eşyası çakışma olarak değil, bilgi olarak günlüğe
  yazılır.
- 247 test geçti (217 Twilight, 30 proxy).

#### 1.0.0-pre.18 - 2026-10-09

- Eski Twilight dosyaları, Geyser yüklemeden önce Geyser'dan kendiliğinden taşınır: eski sürümlerin, elle
  yapılmış kopyaların veya eşitleme araçlarının bıraktığı eşya eşlemeleri (`custom_mappings` altındaki
  `twilight*.json`) ve paketler (`twilight*` veya manifest adı "Twilight"). Bunlar aynı eşyaları başka Bedrock
  kimlikleriyle kaydediyor veya ikinci bir Twilight paketi gönderiyordu; güncellemeden sonra bozuk simgeler
  olarak görünüyordu. Dosyalar Twilight'ın veya twilight-proxy'nin klasöründe `retired/<zaman>/` altına taşınır,
  asla silinmez (`retire-stale-files`).
- twilight-proxy aynı makinedeki arka uçları klasörlerinden okur (`local-backends: auto`): port ve Twilight
  kurulumuyla eşleşen her `export/Twilight.mcpack` dosyasını açılışta (böylece Geyser her sunucunun güncel
  eşyalarıyla başlar, oyuncu gerekmez) ve her derlemeden sonra saniyeler içinde yükler.
- Değişen eşyalar için bekleyen Geyser yeniden başlatmaları yöneticilere katıldıklarında, çevrimiçi
  yöneticilere hemen ve `/twilightproxy` içinde gösterilir; `item-mappings.restart: when-empty` (proxy) ve
  `geyser.restart-for-item-changes: when-empty` (sunucu) kimse çevrimiçi değilken yeniden başlatır.
- 244 test geçti (215 Twilight, 29 proxy).

#### 1.0.0-pre.17 - 2026-10-09

- Floodgate'i Geyser'dan önce yükleyen proxy'lerde ve sunucularda Geyser'a bağlanılır. Floodgate, Geyser'ın olay
  kütüphanesinin kendi kopyasını içerir. Ona bağlanmak FlameCord'da, Waterfall'da ve Floodgate bulunan Velocity'de
  "loader constraint violation" ile başarısız oluyordu (önceki bütün sürümler). Twilight ve twilight-proxy artık
  Geyser'ın olaylarına Geyser'ın kendi sınıf yükleyicisiyle yansıma üzerinden abone olur. Herhangi bir sınıf bu
  kütüphaneye başvurursa bir test başarısız olur. Floodgate'in önce yüklendiği Velocity ve Waterfall'da
  doğrulandı: bağlanma, eşya eşlemeleri, paket sunucusu ve bir Floodgate oyuncusunun sunucu geçişleri.
- Bir bağlanma hatası tam yığın iziyle ve her tarafın Geyser'ın olay kütüphanesini hangi JAR'lardan aldığıyla
  günlüğe yazılır.
- Giriş sunucuları yalnızca proxy'nin yüklediği eklentilerden okunur. Kaldırılmış bir giriş eklentisinin geride
  kalan klasörü, sunucusunu giriş sunucusu yapmak yerine bildirilir.
- Proxy'deki Geyser'da bulunan diğer Twilight eşleme dosyalarının (elle kopyalanmış veya eski bir eşitleme
  aracının yazdığı) adı verilir, farklı eşledikleri seçiciler sayılır ve bunlar `/twilightproxy` içinde gösterilir.
- 239 test geçti (213 Twilight, 26 proxy).

#### 1.0.0-pre.16 - 2026-10-09

- twilight-proxy: proxy bir Geyser eklentisi listelediği hâlde sınıfları twilight-proxy'ye görünmüyorsa
  (FlameCord gibi eklentileri yalıtan proxy türevleri) "Geyser is not installed" yerine "... is installed, but
  its API is not visible to twilight-proxy" ifadesini hatayla birlikte bildirir ve denemeye devam eder.
  Waterfall 1.21 build 615 üzerinde test edildi.
- Geyser'sız bir arka uçta Twilight: `/twilight status` ve açılış günlüğü, "Geyser unavailable: No local Geyser
  plugin data directory was found" yerine Geyser'ın bu sunucuda olmadığını (proxy'li ağda normaldir) ve
  paketin twilight-proxy ile paylaşılıp paylaşılmadığını söyler.
- 235 test geçti (212 Twilight, 23 proxy).

#### 1.0.0-pre.15 - 2026-10-09

Proxy'li bir ağdan gelen saha raporunun düzeltmeleri; her madde önce yerelde yeniden üretildi
([saha raporu](docs/FIELD_REPORT_2026-10-09.md)).

- İçerik raporu Gson yansıması olmadan yazılır. Gson'u `java.time` paketini açamayan sunucularda (Java 17+
  üzerinde Gson 2.11 kullanan Paper 1.21 ve türevleri) her tarama "Failed making field
  'java.time.Instant#seconds' accessible" ile başarısız oluyor ve paket derlenmiyordu. Yazılamayan bir rapor
  artık derlemeyi durdurmaz.
- twilight-proxy Geyser'a güvenilir biçimde bağlanır: Velocity'de diğer bütün eklentilerden sonra başlar,
  hâlâ yüklenen bir Geyser'ı yeniden dener (iki dakika, ardından oyuncular katıldığında), kurulu değil, hazır
  değil ve uyumsuz durumlarını nedeniyle ayırır, yarım kalan dinleyicileri kaldırır, bağlanınca paket
  sunucusunu başlatır ve durumu `/twilightproxy` içinde gösterir. Önceden twilight-proxy'den sonra yüklenen
  bir Geyser "kurulu değil" olarak bildiriliyordu.
- Proxy'li ağlarda özel eşyalar sağlanır: Twilight Geyser eşya eşlemelerini paylaşılan pakete koyar,
  twilight-proxy her `auto` ve `packs/` kaynağınınkileri Geyser okumadan önce proxy'deki Geyser'ın
  `custom_mappings/twilight-proxy_item_mappings.json` dosyasında birleştirir, sonradan değiştiklerinde
  yeniden başlatma ister, iki sunucunun farklı eşlediği seçicileri listeler, elle yapılmış kopyaların adını
  verir ve proxy'nin dili (Türkçe, Azerice) Geyser'ın bunları okumasını engellediğinde uyarır.
- Bir Java seçicisinin Bedrock eşyası yalnızca Geyser'ın eşleştirdiğinden (eşya, custom model data veya item
  model, koşullar) türetilir; böylece aynı seçici her arka uçta aynı Bedrock eşyasıdır. Zaten kullanılan bir
  seçici için ikinci bir model bildirilir ve atlanır. Eşya kimlikleri bir kez değişir: güncellemeden sonra
  Geyser'ı yeniden başlatın.
- Giriş sunucuları proxy'deki LeaderOS Auth, AuthMeVelocity, AuthMeBungee, LibreLogin, JPremium ve benzeri
  eklentilerin yapılandırmasında bulunur ve `login-servers` gibi ele alınır.
- 234 test geçti (212 Twilight, 22 proxy).

#### 1.0.0-pre.14 - 2026-10-09

- Twilight ve twilight-proxy'ye bir güncelleme denetimi eklendi (`update-check`, varsayılan olarak açık):
  açılıştan yaklaşık 20 saniye sonra ve her altı saatte bir herkese açık sürüm listesi GitHub'dan, GitHub
  başarısız olduğunda veya reddettiğinde GitLab yansısından okunur; daha yeni bir sürüm bir kez günlüğe
  yazılır ve `twilight.update` (varsayılan: operatörler) veya `twilight.proxy.update`/`twilight.proxy.admin`
  iznine sahip oyunculara katıldıklarında tıklanabilir bir bağlantıyla gösterilir. Ön sürümler yalnızca ön
  sürüm çalıştıran sunuculara önerilir. Yanıttan yalnızca sürüm etiketleri okunur (en fazla 2 MiB, bağlantı
  için 5 sn, istek başına 10 sn); sunucu hakkında hiçbir şey gönderilmez ve hiçbir şey indirilmez. Ulaşılamayan
  sunucular kısa bir nedenle (bağlantı yok, zaman aşımı, güvenilmeyen sertifika) bir kez günlüğe yazılır.
  `/twilight status` ve `/twilightproxy` son sonucu gösterir.
- Her değişiklik, yayımlanmış bir sürümü yeniden derlemek yerine yeni bir sürüm olarak yayımlanır.
- Canlı test edildi: eski bir sürüm çalıştıran Paper sunucusuna katılan bir operatör bildirimi çalışan bir
  bağlantıyla aldı, operatör olmayan almadı, `/twilight reload` denetimi kapatıp açtı; Velocity daha yeni
  sürümü GitHub'da buldu, güvenilir sertifikası olmayan BungeeCord tek bir satır yazıp çalışmaya devam etti.
  221 test geçti (210 Twilight, 11 proxy).

#### 1.0.0-pre.13 - 2026-10-09

- Bir paket yeniden bağlanması artık yalnızca oyuncu sunucusuna bağlandığında varmış sayılır. Hedefi diğer
  bütün eklentilerden sonra değiştiren giriş eklentileri (BungeeCord'da LeaderOS Auth, öncelik 127)
  twilight-proxy'nin oyuncunun vardığını sanmasına yol açıyordu; bu yüzden giriş eklentisinin sonradan lobiye
  taşıması ikinci bir yeniden bağlanmaya neden oluyordu.
- İlk sunucusu paketinin seçildiği sunucu olmayan yeni bir oturum (BungeeCord'un oyuncuları son sunucularına
  geri göndermesi, zorunlu sunucular) oyuna girdikten birkaç saniye sonra bir kez yeniden bağlanır.
- Bir Bedrock oturumu adla yalnızca Geyser onu bir Java oyuncusuna bağlamadan önce ve yalnızca oynadığı
  adresten eşleştirilir; böylece çevrimdışı ağlarda bir Bedrock oyuncusunun adını alan bir Java oyuncusu onun
  yeniden bağlanmalarını yönlendiremez veya tetikleyemez; bir istemcinin katılma adresi aktarımda yalnızca düz
  bir alan adı veya IP adresiyse kullanılır.
- BungeeCord'da (BungeeGuard, Floodgate ile ve onsuz) ve Velocity'de (LimboAPI, Sonar) LeaderOS Auth Plus 1.1.1
  ile test edildi: giriş oturumuyla ve oturumsuz, sunucu değişikliği başına tek yeniden bağlanma;
  [giriş eklentisi testine](docs/PROXY_AUTH_2026-10-09.md) bakın. 215 test geçti (204 Twilight, 11 proxy).

#### 1.0.0-pre.12 - 2026-10-08

- twilight-proxy'nin paket yeniden bağlanmaları giriş eklentileriyle (AuthMeVelocity veya AuthMeBungee ile
  AuthMe, LibreLogin, nLogin, JPremium) çalışır hâle getirildi: yeniden bağlanan oturum için seçilen bir giriş
  sunucusu korunur, reddedilen bir ilk sunucu oyuncuyu Velocity'nin okuma zaman aşımına kadar sunucusuz
  bırakmak yerine proxy'nin ilk sunucusuna gönderir ve girişten sonra oyuncu, her eklentinin denetlediği yeni
  bir bağlantı isteğiyle yeniden bağlandığı sunucuya gider.
- Bir oturum ilk sunucusu için asla yeniden bağlanmaz; böylece oyuncuları önce başka yere gönderen bir eklenti
  artık ikinci bir yeniden bağlanmaya yol açmaz; hiç yol açmayan sunucular için `login-servers` eklendi.
- Yeniden bağlanma süre sınırları paketle ölçeklenir (`transfer-timeout-seconds: auto`: üç dakika artı paketin
  128 KiB/s ile süresi, en fazla bir saat) ve her adım günlüğe yazılır: boyut ve süre sınırıyla aktarım,
  yeniden bağlanma, sürelerle varış, reddedilen veya yönlendirilen ilk sunucular, 60 saniye içinde geri
  gelmeyen istemciler (gönderildikleri adresle), bırakılan yeniden bağlanmalar, aktarım sınırları ve 32 MiB veya
  daha büyük paketler için paket sunucusu önerisi. Velocity'nin `login-ratelimit` değeri bir yeniden
  bağlanmadan uzunsa uyarı verilir.
- Geyser'a her paket sürümünün değişmez bir kopyası verilir (`cache/versions/`); böylece uzun bir indirme
  sırasında yeniden derlenen bir paket onu bozamaz ve yeni paketlerin karması ilk oturum onlara ihtiyaç
  duymadan önce hesaplanır.
- Paket sunucusu: bir indirmeden sonra bağlantı istemci kapatana kadar açık tutulur (sunucu 5 sn boşta kalınca
  kapattığında antivirüs web kalkanları 40 ve 152 MiB'lik indirmeleri yarıda kesti) ve bağlantısı çalışmayan
  adresler nedeniyle birlikte (ulaşılamayan port, değişen adres, bitmeyen indirme) 30 dakika hatırlanır;
  sunucu dışarıdan ulaşılamaz görünürse uyarı verilir.
- Proxy'deki bağlantı indirmeleri büyük paketlerin karşılayamadığı sabit bir zaman aşımı yerine
  `download-timeout-seconds` boyunca veri gelmezse veya 64 KiB/s altına düşünce durur; büyük ve duran `auto`
  aktarımları günlüğe yazılır.
- [Yeniden bağlanma ve büyük paket testi](docs/PROXY_RECONNECT_2026-10-08.md) eklendi; giriş eklentileri,
  yönlendirme ve korumalar [wiki'de](WIKI.md#yeniden-bağlanmalar-giriş-eklentileri-ve-korumalar) belgelendi.
  214 test geçti (204 Twilight, 10 proxy).

#### 1.0.0-pre.11 - 2026-10-07

- Twilight ve twilight-proxy'ye paket sunucusu (`pack-host`, varsayılan olarak kapalı) eklendi: Bedrock
  oyuncuları paketleri Geyser'ın oyun içi aktarımı yerine Geyser'ı çalıştıran sunucudan HTTP ile indirir;
  ItemsAdder'ın veya CraftEngine'in self-host özelliği gibi seçtiğiniz bir portta. Oturumun her paketi o
  Bedrock oturumu için üretilmiş bir bağlantıyla duyurulur: 256 bitlik rastgele bir belirteç,
  `link-minutes` ve `downloads-per-link` boyunca geçerli ve varsayılan olarak yalnızca oyuncunun Geyser'a
  bağlandığı IP adresinden. Diğer her istek aynı boş 404 yanıtını alır.
- Sunucu kötüye kullanıma karşı sağlamlaştırıldı: katı istek ayrıştırma (GET/HEAD, 8 KiB başlık, beş
  saniyelik zaman aşımı), toplamda ve adres başına bağlantı sınırları (IPv6 için /64 başına), adres başına
  dakikada 60 istek, reddedilen 20 istekten sonra 15 dakikalık engel, en düşük aktarım hızı, Geyser'ın
  SHA-256 değeriyle denetlenen değişmez paket kopyaları ve yalnızca `trusted-proxies` adreslerinden
  `X-Forwarded-For`.
- Katılmak sunucudan bağımsız kalır: başarısız bir indirme Geyser'ın aktarımına döner (kapalı bir porta
  giden bağlantılarla test edildi). Sunucu diğer bütün dinleyicilerden sonra abone olur ve bütün dosya
  paketlerini (Twilight'ınki, Geyser'ın tümleşik paketi, diğer eklentilerinkiler) paket seçeneklerini ve
  içerik anahtarlarını koruyarak birlikte sunar, çünkü Bedrock bağlantıları oyun içi paketlerle karıştırmaz.
- `public-address` (`auto`, bir sunucu adı veya ters proxy arkasında `http(s)://`), NAT için `public-port` ve
  `bind-address` desteklenir; twilight-proxy yapılandırması `trusted-proxies: [127.0.0.1]` gibi tek satırlık
  listeleri kabul eder.
- Her anahtar, ağ düzenleri ve güvenlik modeli [wiki'de](WIKI.md#paket-sunucusu) belgelendi. 209 test geçti
  (202 Twilight, 7 proxy).

#### 1.0.0-pre.10 - 2026-10-05

- Sprite'larını bir paketin yeniden çizdiği boss çubukları (`boss_bar/<renk>_background`, `_progress` ve
  `notched_*` kaplamaları) Bedrock'ta bu sprite'larla, Java'nın sırasıyla ve çubuğun değerinde kesilerek
  çiziliyor; renk ve stil değişiklikleri canlı olarak yeniden gönderiliyor. Dokunulmayan renkler Bedrock'un
  çubuğunu korur.
- Uzun boss çubuğu adları tamamen gösteriliyor: katmanlı olmayan adlar ilk bloktan uzunsa katman
  etiketlerine bölünüyordu. Katmanlı adlar artık kendi işaretlerini taşıyor.
- CustomNameplates'in varsayılan boss çubuğu (yazılı üç arka plan) Bedrock'un 256 karakterine sığdırıldı:
  üst katman için daha büyük bir ilk blok ve dört ile sekiz birimlik kaydırmalarda aralık glifleri yerine
  boşluklar. Satır tek etikete geri düştüğünde adlandırılmış font boşlukları (kaydırma fontları) boşluk
  olarak kalıyor.
- Yeni görüntülerle [boss çubuğu ve model incelemesi](docs/BOSSBARS_MODELS_2026-10-05.md) eklendi: yeniden
  çizilmiş boss çubukları, CustomNameplates'in varsayılan boss çubuğu ve yürüme ile bekleme animasyonlarında
  özgün bir BetterModel mobu (GIF'ler). 200 test geçiyor (193 Twilight, 7 proxy).

#### 1.0.0-pre.9 - 2026-10-05

- Velocity ve BungeeCord için twilight-proxy (`TwilightProxy.jar`) eklendi: her arka uç sunucu proxy'deki
  Geyser üzerinden kendi Bedrock paketini alır; paket o arka uçtaki Twilight'tan (`auto`), `packs/`
  içindeki bir dosyadan veya bir indirme bağlantısından gelir. Başka paketli bir sunucuya geçen Bedrock
  oyuncuları paketi yüklemek için yeniden bağlanır ve yönlendirilir. Velocity 4.2.0 ve BungeeCord 26.1
  üzerinde test edildi.
- Dışa aktarılan paket `twilight:proxy` eklenti mesajı kanalı üzerinden twilight-proxy ile paylaşılır:
  proxy'nin sunucularıyla zaten paylaştığı gizli anahtarla (Velocity yönlendirmesi, BungeeGuard) veya
  `proxy.secret` ile HMAC-SHA256 imzalı; zaman damgaları, tek kullanımlık istek nonce'ları, aynı anda tek
  bir sınırlı aktarım ve gizli anahtar yoksa hiçbir duyuru olmadan.
- 16384 karakterden uzun yazıların veya 2^20 birimden geniş kaydırmaların yerleştirilmesi reddedilir (bunun
  yerine değişmeden gönderilir); böylece özel hazırlanmış yazı yerleşimi sınırsız büyütemez.
- [Wiki](WIKI.md) eklendi: kurulum, komutlar, her yapılandırma anahtarı, dosyalar, paket girdileri, API,
  protokol, güvenlik modeli ve sorun giderme. 191 test geçiyor (184 Twilight, 7 proxy).

#### 1.0.0-pre.8 - 2026-10-04

- Java'nın öncekilerin üzerine geri aldığı yazı ve görseller (üst üste menü görselleri, CustomNameplates
  arka planları) sandık başlıklarında, aksiyon çubuğunda ve boss çubuklarında font kaydırmaları ve gölgesiz
  yazı dahil katman başına bir Bedrock etiketiyle çizilir (`ui.java-text-layers`). Java paketinin saydam
  yaptığı boss çubukları gizli kalır.
- Özel biyomlar birebir çimen, yaprak, su, sis ve gökyüzü renkleri ve iklimleriyle gösterilir: paket
  yalnızca eski dünyaların kullandığı 25 Bedrock biyomunu yeniden tanımlar. Biyomlar datapack'lerden ve
  sunucu kayıt defterinden gelir; geçerli RealisticSeasons mevsimi yuvaları önce alır ve mevsim değişikliği
  paketi yeniden derler. Yalnızca biyom güncellemeleri (`/fillbiome`, mevsimler) artık Bedrock oyuncularına
  yeniden katılmadan ulaşır.
- Binilen oyuncuların ve mobların Bedrock adı Java gibi gizlenir; böylece ad etiketi eklentileri yalnızca
  kendi etiketlerini gösterir; CustomNameplates kendi arka planlarını çizdiğinde Bedrock'un ad etiketi kutusu
  gizlenir (`ui.nametag-background`).
- Sağlayıcı başına neyin okunacağı (`sources.providers`: auto, generated, contents veya kapalı) ve dünya
  datapack'lerinin okunup okunmayacağı (`sources.datapacks`) seçilebilir; `auto` her sağlayıcının kendi
  teslim ayarlarını izler.
- Her derleme `plugins/Twilight/export` dizinine (`Twilight.mcpack` ve Geyser eşya eşlemeleri) dışa
  aktarılır ve paketi başka bir eklentinin veya proxy'nin göndermesine izin verilir
  (`geyser.send-pack-to-bedrock: false`). Yerel Geyser'ı olmayan sunucular yalnızca dışa aktarır.
- Java'nın tolere ettiği içerik katı derlemeleri başarısız kılmak yerine Java'nın gösterdiği şekilde
  dönüştürülür: eksik dokular ve modeller, ekran boyutunda kaplama glifleri ve `vanilla-override` olmadan
  değiştirilmiş vanilla sesler bildirimdir. Yedi üretim sunucusunun tam derlemeleri varsayılan
  yapılandırmayla geçiyor; henüz hiçbir şey dağıtılmamışsa ilk derleme yine dağıtılır.
- Katmanlı sandık başlıkları ve boss çubuğu adları Java'nın yüksekliğinde tutulur (Bedrock bir birimlik
  satır aralığıyla onları 4,5 birim yükseltiyordu).
- Yayımlanan görüntüler animasyonlu Java/Bedrock karşılaştırmaları dahil güncel olanlarla değiştirildi;
  [üst üste görseller, ad etiketleri ve birebir biyomlar](docs/LAYERS_BIOMES_2026-10-04.md) belgesine bakın.
  183 test geçiyor.

#### 1.0.0-pre.7 - 2026-10-04

- Her sağlayıcının ürettiği paket (CraftEngine `resource_pack.zip`, ItemsAdder çıktısı, Nexo/Oraxen
  paketleri) Java oyuncularının aldığı paket olarak kabul edilir: artık sağlayıcının çalışma klasörlerinin
  önüne geçer. Kurulu olmayan sağlayıcıların veya başka bir sağlayıcı üretilmiş paket gönderirken ayarları
  kendi paketini göndermeyen sağlayıcıların paketleri yalnızca boşlukları doldurur. CustomNameplates ve
  BetterHUD paketleri keşfedilir, Nexo'nun vanilla varlık önbelleği yok sayılır.
- Datapack ve eklenti biyomları Bedrock'ta Geyser'ın okyanus yedeği yerine en yakın çimen, yaprak, su ve sis
  renklerine ve yağışa sahip vanilla biyom olarak gösterilir (`world.bedrock-biome-matching`).
- Kaynak paketlerinin `lang` dosyaları Java gibi birleştirilir ve Geyser'a verilir; böylece Bedrock
  oyuncuları datapack ve eklenti içeriğinin adlarını ve ender sandığı başlığı olarak bir görsel veya gizli
  envanter etiketi gibi çeviri geçersiz kılmalarını görür (`ui.java-translations`).
- Renk kodu olmayan konteyner başlıklarının görselleri Java'daki gibi koyulaştırılır (Java glifleri
  varsayılan başlık rengi 0x404040 ile çarpar; Bedrock kaynak paketi gliflerini asla renklendirmez); önceden
  koyulaştırılmış kopyalar kullanılır (`ui.java-glyph-tint`).
- Kalın başlıkların başlangıç aralık glifleri biçimsiz tutulur; Bedrock bunları daha geniş çiziyor ve kalın
  başlıkları yaklaşık dört birim sağa kaydırıyordu. Yerleştirilmiş yazıdaki eski renk kodları artık yer
  kaplamaz.
- Boş bir envanter etiketi Bedrock'ta boş kalır (Bedrock boş bir çeviriyi eksik sayıyordu).
- Arayüz dönüşümü iki gerçek sunucunun 104 menüsü ve özgün görselli bir stil takımıyla doğrulandı: her
  pencere ve 104 başlık bandından 97'si Java ile eşleşiyor; önceki bir görselin üzerine geri alınan katmanlar
  desteklenmiyor. Altı gerçek sunucunun tam derlemeleri ve canlı bir özel biyom sahnesi
  [arayüz kampanyasındadır](docs/UI_CAMPAIGN_2026-10-04.md). Eski ekran görüntüleri güncel görüntüler
  lehine kaldırıldı. 156 test geçiyor.

#### 1.0.0-pre.6 - 2026-10-04

- Java yazısı her Bedrock yazı yüzeyinde yerleştirilir: sohbet, aksiyon çubuğu, başlıklar, boss
  çubukları, skor tabloları, varlık adları, yazı görüntüleri ve diğer konteyner başlıkları. Adlandırılmış
  fontlar, yeniden eşlenmiş karakterler ve boşluk kaydırmaları artık doğru görselleri Java'nın konumlarında
  gösterir; ortalı satırlar Java'nın tam sayı ortalamasını izler. `ui.java-text-surfaces` ile denetlenir;
  `ui.java-text-layout` artık konteyner yerleşimini gerektirmez.
- ItemsAdder'ın vanilla varlık kopyaları, geçici derleme klasörleri ve eskimiş iç içe paketler artık kaynak
  sayılmaz; yüzlerce özel eşyayı gizliyorlardı. `generated.zip` yoksa yeniden adlandırılmış bir ItemsAdder
  çıktı paketi kullanılır.
- Doku atlası sprite yeniden adlandırmaları çözülür, korumalı PNG'ler Java gibi çözülür, nesne biçimli model
  dokuları okunur, özel seçicilerin arkasında vanilla modellere izin verilir ve tanımsız yüz dokuları için
  Java'nın eksik dokusu kullanılır.
- Java'nın kendi font sayfalarındaki karakterler Bedrock yazısı olarak tutulur ve ekran dışı veya saydam
  aralık görselleri ilerleme sayılır; bu, gerçek sunuculardaki takma ad sayfası taşmasını giderir.
- Java'nın da reddettiği içerik (bozuk fontlar, okunamayan TrueType dosyaları, bulunmayan ses dosyaları,
  kaplamayla çizilen kafalar) katı derlemeleri başarısız kılmak yerine bildirim olarak raporlanır.
- Her yüzey bağımsız SkyBlock içeriğiyle canlı doğrulandı ve beş gerçek sunucu için tam üretim derlemeleri
  çalıştırıldı; [yazı yüzeyi incelemesine](docs/TEXT_SURFACES_2026-10-04.md) bakın. 138 test geçiyor.

#### 1.0.0-pre.5 - 2026-10-03

- Sandık başlıkları Bedrock oyuncuları için Java font ölçüleriyle yerleştirilir. Negatif ve özel aralık
  (boşluk sağlayıcıları, negatif yükseklikli bitmap'ler), glif kenar payları ve Java'nın varsayılan veya
  adlandırılmış fontlarda yeniden eşlediği karakterler görünmez aralık glifleri ve özel kullanım takma
  adlarıyla yeniden üretilir. Gerçek menü görselleri, önceki bir birimlik kayma dahil artık Java'nın birebir
  arayüz piksellerine oturuyor. `ui.java-text-layout` ile denetlenir; dokunmatik yerleşim yalnızca glif
  değiştirme alır.
- Her kaynak paketinin font tanımları Java gibi birleştirilir ve Java'nın yükleyemediği TrueType fontlar yok
  sayılır.
- Altı gerçek Survival menüsü, her sandık boyutu ve değişmemiş huni, fırın ve fırlatıcı ekranları eşlenmiş
  görüntülerle doğrulandı; [yazı yerleşimi incelemesine](docs/TEXT_LAYOUT_2026-10-03.md) bakın. 109 test
  geçiyor.

#### 1.0.0-pre.4 - 2026-10-03

- Bedrock masaüstü sandık ekranları için bir Java konteyner yerleşimi üretilir. Geniş font görselli
  başlıklar artık kaydırılmaz, kısa çizgiyle bölünmez veya kırpılmaz. Başlık ve envanter etiketleri Java'nın
  konumlarını, rengini ve çizim sırasını kullanır; sandık, envanter ve kısayol çubuğu satırları 1 ile 6
  sandık satırı için Java'nın aralığını korur. Kısmi arayüz Bedrock'un vanilla arayüzüyle birleştirilir ve
  `ui.java-container-layout` ile kapatılabilir.
- Altı sandık boyutunun hepsi ile huni, fırlatıcı ve fırın ekranları betikli ölçümlerle eşlenmiş
  Java/Bedrock görüntülerinde doğrulandı; Bedrock'un renklendirilmemiş bitmap glifleri ve bir birimlik glif
  kayması [konteyner yerleşimi incelemesine](docs/CONTAINER_LAYOUT_2026-10-03.md) kaydedildi. 95 test
  geçiyor.

- Bitmap font atlas hücreleri her glifin Java görüntü boyutlarını ve ascent değerini koruyarak
  kendiliğinden büyütülür. Geniş rütbe etiketleri ve daha büyük arayüz/HUD görselleri yalnızca 16 pikseli
  aştıkları için artık küçültülmek veya atlanmak zorunda değil.
- Piksel koruma ve komşu glif regresyonları eklendi; 87 testin hepsi geçiyor. Tüm kaynak denetimleri
  tekrarlandı ve altı ek gerçek arayüz/HUD görseli iki istemcide karşılaştırıldı. Kalan başlık kırpılması,
  yatay dolgu ve kesirli örnekleme farkları [geniş glif incelemesine](docs/WIDE_GLYPHS_2026-10-02.md)
  kaydedildi.
- Harici özellik belgeleri ve mevcut menü derlemi incelendikten sonra kabul sözleşmesi 40 alana ve 289
  gerekli senaryoya genişletildi. Ad alanı çakışması ve çok satırlı glif sayfası regresyonları eklendi;
  yerel takım artık 89 testi geçiyor. Bu, çalışma zamanı arayüz desteğini değil, doğrulamayı genişletir.

#### 1.0.0-pre.3 - 2026-10-02

- Java bitmap glif yükseklikleri ve ascent değerleri ölçülmüş Bedrock hücre koordinatlarıyla korunur.
  Temsil edilebilir negatif ascent'ler desteklenir, görünür taşma sessizce küçültülmeden veya kırpılmadan
  reddedilir; saydam dolgu komşu karakterlerden yalıtılır.

- Her durağan bileşik alt modelin görüntü dönüşümleri görüntü varlıklarında, eklentilerde ve envanter
  önizlemelerinde korunur. Bu, sağlayıcı varlıklarını değiştirmeden ayrık BetterModel kafa parçalarını ve
  istenmeyen kafa/gövde eğimini düzeltir. Üretilmiş sprite alt modelleri küboid alt modellerin yanında
  görünür kalır.

- Özel envanter simgeleri ilk doku yerine Java model geometrisinden ve devralınan arayüz dönüşümlerinden
  çizilir. Durağan önizlemelerde yüz UV'leri, UV çeyrek dönüşleri, öğe dönüşü/yeniden ölçekleme, derinlik ve
  saydamlık korunur.

- Java uyumlu noktalı ve büyük harfli paket kaplama dizin adları kabul edilir; dizin aşımı koruması korunur.
  Genişletilmiş gerçek kaynak testleri, önceden Survival içerik keşfini durduran sürümlü dizinleri ortaya
  çıkardı.
- Buluta binen eşya görüntüleri için Java'nın tam yükseklikli yolcu bağlantısı izlenir. Bu, dokuları,
  shader'ları veya parlaklık geçersiz kılmalarını değiştirmeden dikey konumlarını ve iksir sepetinin katı
  blokların üzerindeki karanlık ışıklandırmasını düzeltir.
- Görünmez, sıfır yarıçaplı Java bulut çapaları kendiliğinden etkisiz Bedrock aktörleriyle temsil edilir.
  Değişiklikler boyunca bulut meta verisi ve bağlı yolcular korunur; olağan bulutlar Geyser çevirisini
  korur. Bu, sağlayıcı varlıklarını düzenlemeden ModelEngine modellerinin çevresinde gözlenen istenmeyen
  parçacıkları kaldırır.
- Eşya görüntüsü kafa yaw değeri gövde yaw değeriyle hizalı tutulur ve varlık yaw değeri bir kez uygulanır;
  bu, Java referansından ters yöne bakan bağlı BetterModel modellerini düzeltir.
- Yakalanan başarısızlıklar ve devam kanıtıyla yedi modellik bir Java/Bedrock karşılaştırması eklendi;
  ışıklandırma ve tam poz eşdeğerliği kabul boşlukları olarak kalıyor.
- Java görüntü görünürlüğü izlenir: temel varlık görünmezliği yok sayılır ve sıfır görüş menzilli
  görüntüler geçerli eşya ve dönüşümleri korunarak gizlenir.
- Ayrı kuaterniyon ara değerlemesi, Java eşya çerçevesi dönüşü ve doğru başlangıç/süre anlamıyla geliştirme
  aşamasında bir Geyser eşya görüntüsü köprüsü eklendi.
- Canlı BetterModel ve ModelEngine kemik eşyası kayıt keşfi eklendi. Katı dönüşüm eksik sağlayıcı keşfini
  reddeder.
- Paket kaplamaları hedef Minecraft biçimine ve bildirim sırasına göre seçilir; etkin olmayan kaplamalar
  artık bağımsız paket olarak içe aktarılmaz.
- Açık vanilla model dokuları ve kare meta verileri, yazarın verdiği doku geçersiz kılmaları korunarak
  doğrulanmış istemci önbelleğinden çözülür.
- Görüntü dizini değişiklikleri için yeniden başlatma gerekir ve dağıtılmış dosyaları değiştirmeden önce paket
  ZIP verisi doğrulanır.
- Özel üçüncü şahıs eşya dönüş eksenleri, işaretleri ve model çerçevesi dönüşümü, bileşik açılar dahil Java
  görüntü yönünü korumak için düzeltildi.
- Açıkça tanımlanmış sol el pozlarına Java'nın sol el aynalaması uygulandı.
- Bir eşyanın yönünü tersine çevirebilen pozitif 90 derece tekilliğindeki kuaterniyondan Euler'e dönüşüm
  düzeltildi.
- Yaw, üç yön ekseninin hepsi, açık sol el dönüşümleri ve iki Euler tekilliği için regresyonlar eklendi.
  Tam oyun içi görsel eşdeğerlik doğrulanmadı.
- Döndürülmüş küboid X/Y işaretleri düzeltildi, yüz UV dönüşleri korundu ve atlanan yüz UV dikdörtgenleri
  Java öğe sınırlarından türetildi.
- Durağan doku dışa aktarımı için tüm animasyon sayfasını germek yerine yazarın verdiği ilk animasyon karesi
  örneklenir; uzun durağan PNG'ler artık kırpılmaz.
- Menüleri sessizce emoji boyutunda hücrelere küçültmek yerine aşırı büyük arayüz bitmap glifleri ve özel
  aralık katı modda reddedildi. Tanı derlemeleri bu atlamaları raporlar; tam Bedrock arayüz uyarlaması
  desteklenmiyor.
- Özel eşya eşlemeleri çalışan Geyser kayıt defterinden farklı olduğunda gerekli bir sunucu yeniden
  başlatması raporlanır. Yalnızca doku yeniden yüklemeleri kullanılabilir kalır; yapılandırma yeniden
  yüklemeleri bekleyen yeniden başlatma gereksinimini artık temizlemez.
- Geyser'ın Türkçe/Azerice enum ayrıştırma sorunu için bir JVM yerel ayar tanısı eklendi.
- Devre dışı sağlayıcılar için isteğe bağlı olay kaydı atlanır; uyumsuz bir ModelEngine sürümü
  etkinleşemediğinde kapalı sınıf yükleyici hataları önlenir.
- Otomatik GitHub ve GitLab hat tetikleyicileri kapatıldı. Derlemeler siberanka adına yerelde çalıştırılır ve
  test edilir; push'lar barındırılan derleme çalıştırmaz.

#### 1.0.0-pre.2 - 2026-09-22

##### Java'dan Bedrock'a sunum

- Geyser'ın elde tutma sunumu temel Minecraft eşya kimliği yerine çözülmüş Java model üst öğe zincirinden
  türetilerek özel alet ve silah poz seçimi düzeltildi.
- Bir modeli küçültüp yanlış bir tutuş pozuna taşıyabilecek otomatik geometri sığdırma olmadan, yazarın
  verdiği Java birinci ve üçüncü şahıs ötelemesi, dönüşü ve ölçeği korundu.
- Her Bedrock Unicode sayfası kararlı 16 piksellik bir ızgarada birleştirilip her glif bağımsız olarak alta
  hizalanarak, aşırı büyük arayüz glifleri de içeren sayfalar dahil sohbet emoji yüksekliği düzeltildi.

##### Doğrulama

- Model üst öğesi poz seçimi, birebir el dönüşümleri ve tek bir Unicode sayfasını paylaşan karışık boyutlu
  bitmap glifler için regresyonlar eklendi.
- Düzeltme öncesi poz ve emoji görüntüleri genel kabul galerisinden kaldırıldı.

#### 1.0.0-pre.1 - 2026-09-21

##### Sunucu platformu

- Paper, Folia ve Spigot için Bukkit hizmet API'si, işlem olayları, komutlar, yapılandırılmış günlükler ve
  denetimli Geyser yeniden yüklemeleri içeren tek bir sunucu eklentisi eklendi.
- ItemsAdder, CraftEngine, Nexo, Oraxen, ModelEngine ve BetterModel için belirlenimci sağlayıcı keşfi, yaşam
  döngüsü kancaları, komut eşzamanlaması, kaynak parmak izleri ve gecikmeli yerleşme denetimleri eklendi.
- Yazarın verdiği `contents` ve `resources`, sağlayıcı `data` ve `cache`, datapack, yapılandırılmış kaynak
  ve üretilmiş paket yedek katmanları eklendi.

##### Java'dan Bedrock'a dönüşüm

- Güncel ve eski eşya tanımı işleme, Geyser özel eşlemeleri, katmanlı dokular, Java küboidleri, görüntü
  dönüşümleri, ekipman eklentileri, blok durumları, yaratıcı meta verisi ve animasyonlu dokular eklendi.
- Birebir yalnızca doku içeren yay, arbalet ve olta yeniden renklendirmeleri için Bedrock'un yerel poz,
  gerilme, yükleme, atma ve ip davranışı korundu.
- Ayrı arbalet ok ve roket durumları dahil katmanlı, dönüştürülmüş, animasyonlu ve hacimli silahlar için
  çalışma zamanında seçilen Java durum geometrisi eklendi.
- Unicode çakışma korumalı bitmap font dönüşümü ile özyinelemeli referanslar ve OGG doğrulamasıyla katmanlı
  özel ses dönüşümü eklendi.
- Açıkça başvurulan vanilla varlıklar için karma doğrulamalı, sürümle eşleşen Mojang model, doku, font ve ses
  yedeği eklendi.

##### Güvenilirlik

- Katı yayın doğrulaması, sınırlı kaynak adları, güvenli yol ve arşiv işleme, belirlenimci çıktı, işlemsel
  dağıtım, bilinen son iyiye geri dönme ve sınırlı yedekler eklendi.
- Geçerli sunucu derleyicisi ve dağıtım yolu için odaklı kaynak, font ve ses denetimleri ile 28 otomatik test
  eklendi.
- `Twilight.jar` ve SHA-256 sağlama toplamı yayımlanmadan önce aynı etiketli kaynağı derleyip test eden
  GitHub Actions ve GitLab CI hatları eklendi.
