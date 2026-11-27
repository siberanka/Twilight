# Migrating to Twilight

> Türkçe: [aşağıda](#türkçe)

Twilight builds the Bedrock pack and the Geyser mappings from your Java content and shows Java item displays
(furniture, ModelEngine and BetterModel models) to Bedrock players. Other tools do parts of the same job. This
guide says what to remove when you come from one of them, what Twilight replaces, what Twilight's automatic
conflict handling does when both are still there, and how to go back.

**The rule: one pack sender, one owner per model.** Do not let two tools send resource packs or custom item
mappings to the same Geyser, and do not let two tools draw the same model. Bedrock players then see duplicate or
missing items, two copies of a model, or one tool's pack replacing the other's. Twilight detects the common cases
at start and resolves them (below), but the cleanest setup removes what Twilight replaces.

## Before you start

1. Stop the server (or proxy) and back up Geyser's folder (`plugins/Geyser-Spigot`, `plugins/Geyser-Velocity`,
   `plugins/Geyser-BungeeCord` or your standalone Geyser): `packs/`, `custom_mappings/`, `extensions/` and
   `config.yml`.
2. Install Twilight (and twilight-proxy when Geyser runs on a proxy); see the [wiki](../WIKI.md#installation).
3. Keep Geyser's `enable-custom-content: true` (the default).
4. After the changes below, start once, run `/twilight diagnose` (`/twilightproxy diagnose` on a proxy) and read
   the findings. They name the setting that changes each decision.

## Coming from

### java2bedrock.sh (or any converter that writes a pack and mappings by hand)

A converter writes a Bedrock pack into Geyser's `packs/` and a mapping file into `custom_mappings/` once, from the
Java pack you gave it. It does not follow your content plugins.

- Remove its pack from `packs/` and its mapping files from `custom_mappings/`. Keep Geyser's own files.
- Twilight replaces them: it reads the content plugins' files (ItemsAdder, CraftEngine, Nexo, Oraxen,
  BetterModel, ModelEngine), datapacks and the resource packs you list, rebuilds when they change, and deploys
  `packs/twilight.zip` and `custom_mappings/twilight_item_mappings.json` itself.
- A converted pack of a plain resource pack that is **not** known to a content plugin: add it as a source
  (`sources.additional` in `config.yml`, see the wiki) instead of keeping the converter's output.
- If you leave the old files in place, Twilight finds mapping files that map the same Java item (the same custom
  model data or item model) and tells you once per file; see "What the automatic handling does".

### Rainbow (Geyser pack converter) and similar pack senders

Remove the plugin (or extension) and the packs and mappings it wrote into Geyser's folders. Twilight replaces its
conversion and its delivery of the pack to Bedrock players. Two pack senders in one Geyser give players duplicate
or conflicting content. Twilight names a few plugins it knows (BedrockBundler, Rainbow) when it sees them.

### Scaffolding and ScaffoldingExtension (Nexo)

The Scaffolding add-on generates Bedrock mappings and a pack for Nexo content and deploys them to Geyser;
ScaffoldingExtension is the Geyser extension that draws Nexo's furniture (item displays) for Bedrock players.

- Remove ScaffoldingExtension from Geyser's `extensions/` and the Scaffolding add-on from Nexo's plugins, plus the
  mappings and packs it deployed. Twilight reads Nexo directly and draws displays itself.
- If you keep ScaffoldingExtension, Geyser allows only one owner of `item_display`: Twilight sees the extension in
  `extensions/` and keeps its own display bridge off (`integrations.conflicts.display-entities: auto`), so
  furniture is drawn by the extension only. Twilight's other layers (items, blocks, text, biomes) keep working.

### GeyserModelEngine (with GeyserUtils)

GeyserModelEngine draws ModelEngine and BetterModel mobs as real Bedrock custom entities through the
GeyserModelEngineExtension and GeyserUtils. Twilight draws the same models as Java item displays.

We recommend moving to Twilight: it draws the models without a Blockbench export step, from the plugins' own
packs. Do not run both on the same models. By default (`auto`) Twilight keeps drawing ModelEngine and BetterModel
models even when it finds the GeyserModelEngine extension, because GeyserModelEngine can draw nothing for a model
(one that was not exported for it, or a ModelEngine version it does not support; GeyserModelEngine 1.0.9 created no
entity on ModelEngine R4.1.0 in our test). A missing model is worse than a doubled one, so while both are
installed Bedrock players may see models twice and Twilight logs one warning at every start.

- **Move to Twilight (recommended)**: remove the GeyserModelEngine plugin, GeyserUtils (plugin and Geyser
  extension) and `GeyserModelEngineExtension` with its generated pack folder
  (`extensions/geysermodelengineextension/`), then restart. Twilight's display bridge shows ModelEngine and
  BetterModel models from the plugins' own packs. There is no custom Bedrock entity for the model: the hitbox, name
  tag and damage reaction are those of the Java base entity.
- **Keep GeyserModelEngine**: set `integrations.conflicts.model-bones: other`. With its extension in Geyser's
  `extensions/`, Twilight then leaves model bones to it and builds no bone models for the pack. Twilight still
  handles everything else (furniture, items, blocks, menus). Models you did not export for GeyserModelEngine are not
  shown by either tool then.
- `model-bones: twilight` is the same as `auto` as an explicit choice: Twilight draws, and the warning no longer
  suggests removing GeyserModelEngine.

### Hand-written Geyser mappings and packs

Files you wrote yourself stay. Twilight only reports mapping files that name the same Java item selector as one of
its own mappings (and a copy of Twilight's own pack under another name). Remove or change those entries. Geyser
keeps the mapping it reads first (files are read in name order, so `geyser_mappings.json` beats `twilight_item_mappings.json`) and logs
"Not registering custom item definition ... both entries have the same predicates" for the other.

## What the automatic handling does

`integrations.conflicts` in `config.yml` (backend) and `plugins/twilight-proxy/config.yml` (proxy). Every
finding is logged once at start with the setting that changes it, and `/twilight diagnose` shows them again.

| Found | Default (`auto`) | Other choices |
|---|---|---|
| GeyserModelEngineExtension in Geyser's `extensions/` | Twilight keeps drawing the models and logs one warning with the fix (players may see models twice) | remove GeyserModelEngine, its extension and GeyserUtils (recommended), or `model-bones: other` (Twilight never draws them; GeyserModelEngine does), `twilight` (explicit, no removal hint) |
| GeyserDisplayEntity or ScaffoldingExtension in `extensions/` | Twilight's display bridge stays off; the extension draws displays | `display-entities: twilight` (Twilight registers; the load order decides) or `other` |
| Mapping files in `custom_mappings/` that map what Twilight maps | Logged once per file (first 10, then one summary line); nothing is changed | `foreign-mappings: retire` moves them to `plugins/Twilight/retired/<time>/`, `off` does not look |
| A pack in `packs/` with Twilight's pack id (a copy under another name) | Logged | remove the copy |
| GeyserModelEngine plugin without its extension or without GeyserUtils | Logged (it draws nothing) | install the missing part or remove the plugin |
| `enable-custom-content: false` in Geyser's config | Logged: Twilight's pack and custom items cannot reach Bedrock | set it to `true` |
| Backend and proxy Geysers both show displays | Logged as information | run Geyser in one place |
| Twilight and twilight-proxy differ in minor version | Logged | update both |

Twilight never edits another tool's files unless you set `foreign-mappings: retire`, and then only moves them
(never deletes) so they can be put back. Nothing here can stop Geyser from starting: unreadable or oversized
files are skipped. Model and display decisions apply on restart (`/twilight reload` re-reads the settings; the
bridge is built at start).

## Rolling back

- Files Twilight moved with `foreign-mappings: retire` are in `plugins/Twilight/retired/<time>/` (proxy:
  `plugins/twilight-proxy/retired/`), under the same relative path. Move them back and set
  `foreign-mappings: report`.
- Go back to the other tool's rendering of one kind with `model-bones: other` or `display-entities: other`
  (Twilight then never renders it), or to Twilight's with `auto` or `twilight`; restart.
- `/twilight rollback [n]` restores an earlier Geyser deployment of Twilight's own pack and mappings.
- To remove Twilight completely: delete `packs/twilight.zip` and `custom_mappings/twilight*.json` from Geyser,
  remove the plugin, restore your backup of the old pack sender's files.

## Türkçe

### Twilight'a geçiş

Twilight, Java içeriğinizden Bedrock paketini ve Geyser eşlemelerini üretir ve Java eşya görüntülerini
(mobilyalar, ModelEngine ve BetterModel modelleri) Bedrock oyuncularına gösterir. Başka araçlar aynı işin
parçalarını yapar. Bu rehber, bunlardan birinden geliyorsanız neyi kaldıracağınızı, Twilight'ın neyin yerini
aldığını, ikisi de duruyorsa otomatik çakışma yönetiminin ne yaptığını ve nasıl geri dönüleceğini anlatır.

**Kural: tek paket gönderici, her model için tek sahip.** Aynı Geyser'a kaynak paketi ya da özel eşya eşlemesi
gönderen iki aracı birlikte çalıştırmayın ve aynı modeli iki aracın çizmesine izin vermeyin. Aksi halde Bedrock
oyuncuları yinelenen ya da eksik eşyalar, bir modelin iki kopyasını ya da bir aracın paketinin ötekini
değiştirmesini görür. Twilight yaygın durumları başlangıçta algılar ve çözer (aşağıda), ama en temiz kurulum
Twilight'ın yerini aldığı şeyleri kaldırmaktır.

#### Başlamadan önce

1. Sunucuyu (ya da proxy'yi) durdurun ve Geyser klasörünü yedekleyin (`plugins/Geyser-Spigot`,
   `plugins/Geyser-Velocity`, `plugins/Geyser-BungeeCord` ya da bağımsız Geyser'ınız): `packs/`,
   `custom_mappings/`, `extensions/` ve `config.yml`.
2. Twilight'ı (Geyser proxy'deyse twilight-proxy'yi de) kurun; [wiki](../WIKI.md#kurulum) sayfasına bakın.
3. Geyser'ın `enable-custom-content: true` ayarını (varsayılan) koruyun.
4. Aşağıdaki değişikliklerden sonra bir kez başlatın, `/twilight diagnose` (proxy'de `/twilightproxy diagnose`)
   çalıştırın ve bulguları okuyun. Her karar için değiştiren ayarı söylerler.

#### Nereden geliyorsunuz

##### java2bedrock.sh (ya da elle paket ve eşleme yazan herhangi bir dönüştürücü)

Dönüştürücü, verdiğiniz Java paketinden bir kez Geyser'ın `packs/` klasörüne Bedrock paketi, `custom_mappings/`
klasörüne eşleme dosyası yazar. İçerik eklentilerinizi izlemez.

- Paketini `packs/` içinden, eşleme dosyalarını `custom_mappings/` içinden kaldırın. Geyser'ın kendi dosyalarını
  tutun.
- Twilight bunların yerini alır: içerik eklentilerinin dosyalarını (ItemsAdder, CraftEngine, Nexo, Oraxen,
  BetterModel, ModelEngine), datapack'leri ve sıraladığınız kaynak paketlerini okur, değiştiklerinde yeniden
  derler ve `packs/twilight.zip` ile `custom_mappings/twilight_item_mappings.json` dosyalarını kendisi dağıtır.
- Bir içerik eklentisinin bilmediği düz bir kaynak paketinin dönüştürülmüş hali için dönüştürücünün çıktısını
  tutmak yerine paketi kaynak olarak ekleyin (`config.yml` içinde `sources.additional`, wiki'ye bakın).
- Eski dosyaları bırakırsanız Twilight aynı Java eşyayı (aynı özel model verisi ya da eşya modeli) eşleyen
  eşleme dosyalarını bulur ve dosya başına bir kez bildirir; "Otomatik yönetim ne yapar" bölümüne bakın.

##### Rainbow (Geyser paket dönüştürücüsü) ve benzer paket göndericiler

Eklentiyi (ya da uzantıyı) ve Geyser klasörlerine yazdığı paketleri ve eşlemeleri kaldırın. Twilight onun
dönüştürmesinin ve paketi Bedrock oyuncularına ulaştırmasının yerini alır. Bir Geyser'da iki paket gönderici
oyunculara yinelenen ya da çakışan içerik verir. Twilight bildiği birkaç eklentiyi (BedrockBundler, Rainbow)
görünce adıyla söyler.

##### Scaffolding ve ScaffoldingExtension (Nexo)

Scaffolding eklentisi Nexo içeriği için Bedrock eşlemeleri ve paketi üretir ve Geyser'a dağıtır;
ScaffoldingExtension, Nexo mobilyalarını (eşya görüntüleri) Bedrock oyuncuları için çizen Geyser uzantısıdır.

- ScaffoldingExtension'ı Geyser'ın `extensions/` klasöründen, Scaffolding eklentisini Nexo'nun eklentilerinden ve
  dağıttığı eşlemeleri ve paketleri kaldırın. Twilight Nexo'yu doğrudan okur ve görüntüleri kendisi çizer.
- ScaffoldingExtension'ı tutarsanız Geyser `item_display` için tek sahibe izin verir: Twilight uzantıyı
  `extensions/` içinde görür ve kendi görüntü köprüsünü kapalı tutar
  (`integrations.conflicts.display-entities: auto`); mobilyaları yalnızca uzantı çizer. Twilight'ın diğer
  katmanları (eşyalar, bloklar, metin, biyomlar) çalışmaya devam eder.

##### GeyserModelEngine (GeyserUtils ile)

GeyserModelEngine, ModelEngine ve BetterModel moblarını GeyserModelEngineExtension ve GeyserUtils ile gerçek
Bedrock özel varlıkları olarak çizer. Twilight aynı modelleri Java eşya görüntüleri olarak çizer.

Twilight'a geçmenizi öneririz: modelleri Blockbench dışa aktarma adımı olmadan, eklentilerin kendi paketlerinden
çizer. Aynı modellerde ikisini birden çalıştırmayın. Varsayılan olarak (`auto`) Twilight, GeyserModelEngine
uzantısını bulsa bile ModelEngine ve BetterModel modellerini çizmeye devam eder; çünkü GeyserModelEngine bir model
için hiçbir şey çizmeyebilir (ona göre dışa aktarılmamış model ya da desteklemediği ModelEngine sürümü;
GeyserModelEngine 1.0.9 testimizde ModelEngine R4.1.0 üzerinde varlık oluşturmadı). Eksik model, iki kat modelden
kötüdür; bu yüzden ikisi de kuruluyken Bedrock oyuncuları modelleri iki kez görebilir ve Twilight her başlangıçta
tek uyarı yazar.

- **Twilight'a geçin (önerilir)**: GeyserModelEngine eklentisini, GeyserUtils'i (eklenti ve Geyser uzantısı) ve
  `GeyserModelEngineExtension` ile üretilmiş paket klasörünü (`extensions/geysermodelengineextension/`) kaldırıp
  yeniden başlatın. Twilight'ın görüntü köprüsü ModelEngine ve BetterModel modellerini eklentilerin kendi
  paketlerinden gösterir. Model için özel bir Bedrock varlığı yoktur: çarpışma kutusu, ad etiketi ve hasar tepkisi
  Java taban varlığınındır.
- **GeyserModelEngine'i tutun**: `integrations.conflicts.model-bones: other` yapın. Uzantısı Geyser'ın
  `extensions/` klasöründeyse Twilight o zaman model kemiklerini ona bırakır ve pakete kemik modeli koymaz.
  Twilight geri kalan her şeyi (mobilyalar, eşyalar, bloklar, menüler) yapmaya devam eder. GeyserModelEngine için
  dışa aktarmadığınız modelleri o zaman iki araç da göstermez.
- `model-bones: twilight`, `auto` ile aynıdır ama açık seçimdir: Twilight çizer ve uyarı artık GeyserModelEngine'i
  kaldırmayı önermez.

##### Elle yazılmış Geyser eşlemeleri ve paketleri

Kendi yazdığınız dosyalar kalır. Twilight yalnızca kendi eşlemelerinden biriyle aynı Java eşya seçicisini adlandıran
eşleme dosyalarını (ve başka adla duran Twilight paketi kopyasını) bildirir. O girdileri kaldırın ya da
değiştirin. Geyser önce okuduğu eşlemeyi tutar (dosyalar ad sırasıyla okunur; `geyser_mappings.json`, `twilight_item_mappings.json` dosyasını geçer) ve ötekini "Not registering custom item definition ... both entries have the same predicates" diye günlüğe yazar.

#### Otomatik yönetim ne yapar

`config.yml` içinde (arka uç) ve `plugins/twilight-proxy/config.yml` içinde (proxy) `integrations.conflicts`.
Her bulgu başlangıçta, değiştiren ayarla birlikte bir kez günlüğe yazılır; `/twilight diagnose` onları yeniden
gösterir.

| Bulunan | Varsayılan (`auto`) | Diğer seçenekler |
|---|---|---|
| Geyser'ın `extensions/` klasöründe GeyserModelEngineExtension | Twilight modelleri çizmeye devam eder ve çözümle tek uyarı yazar (oyuncular modelleri iki kez görebilir) | GeyserModelEngine'i, uzantısını ve GeyserUtils'i kaldırın (önerilir) ya da `model-bones: other` (Twilight hiç çizmez; GeyserModelEngine çizer), `twilight` (açık seçim, kaldırma ipucu yok) |
| `extensions/` içinde GeyserDisplayEntity ya da ScaffoldingExtension | Twilight'ın görüntü köprüsü kapalı kalır; görüntüleri uzantı çizer | `display-entities: twilight` (Twilight kaydeder; yükleme sırası belirler) ya da `other` |
| `custom_mappings/` içinde Twilight'ın eşlediğini eşleyen dosyalar | Dosya başına bir kez günlüğe yazılır (ilk 10, sonra tek özet satırı); hiçbir şey değişmez | `foreign-mappings: retire` onları `plugins/Twilight/retired/<zaman>/` içine taşır, `off` bakmaz |
| `packs/` içinde Twilight'ın paket kimliğini taşıyan paket (başka adla kopya) | Günlüğe yazılır | kopyayı kaldırın |
| Uzantısı ya da GeyserUtils olmayan GeyserModelEngine eklentisi | Günlüğe yazılır (hiçbir şey çizmez) | eksik parçayı kurun ya da eklentiyi kaldırın |
| Geyser ayarında `enable-custom-content: false` | Günlüğe yazılır: Twilight paketi ve özel eşyalar Bedrock'a ulaşamaz | `true` yapın |
| Arka uç ve proxy Geyser'ları ikisi de görüntü gösteriyor | Bilgi olarak yazılır | Geyser'ı tek yerde çalıştırın |
| Twilight ve twilight-proxy küçük sürümde ayrışıyor | Günlüğe yazılır | ikisini de güncelleyin |

Twilight, `foreign-mappings: retire` ayarlamadıkça başka bir aracın dosyalarını asla değiştirmez; ayarlarsanız da
yalnızca taşır (silmez), geri konabilir. Burada hiçbir şey Geyser'ın başlamasını engelleyemez: okunamayan ya da
aşırı büyük dosyalar atlanır. Model ve görüntü kararları yeniden başlatmada uygulanır (`/twilight reload`
ayarları yeniden okur; köprü başlangıçta kurulur).

#### Geri alma

- `foreign-mappings: retire` ile Twilight'ın taşıdığı dosyalar `plugins/Twilight/retired/<zaman>/` içindedir
  (proxy: `plugins/twilight-proxy/retired/`), aynı göreli yolla. Onları geri taşıyın ve
  `foreign-mappings: report` yapın.
- Bir türün çizimini diğer araca geri vermek için `model-bones: other` ya da `display-entities: other` (Twilight o
  zaman hiç çizmez), Twilight'a vermek için `twilight` yazın; yeniden başlatın.
- `/twilight rollback [n]` Twilight'ın kendi paketinin ve eşlemelerinin önceki bir Geyser dağıtımını geri yükler.
- Twilight'ı tamamen kaldırmak için Geyser'dan `packs/twilight.zip` ve `custom_mappings/twilight*.json` dosyalarını
  silin, eklentiyi kaldırın, eski paket göndericinin dosyalarının yedeğini geri koyun.
