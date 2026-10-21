# Validation and acceptance

> Türkçe: [aşağıda](#türkçe)

Twilight uses repeatable machine checks before any visual acceptance pass.

The [additional model matrix](MODEL_MATRIX_2026-09-28.md) records seven real
models, paired client captures, and the defects that prevent visual acceptance.

## Local build

```powershell
$env:JAVA_HOME='<path-to-jdk-25>'
$env:GRADLE_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'
.\gradlew.bat :twilight:build --no-daemon --no-configuration-cache
```

Core tests verify world resolution, archive traversal rejection, content classification, layered legacy items, modern predicate variants, volumetric geometry, model-parent handheld selection, exact authored hand transforms, stable mixed-size glyph pages, named-font collision safety, layered and referenced sounds, deterministic ZIP output, Geyser ownership boundaries, three-snapshot retention, and rollback.

## Source audit

Server roots can be audited read-only with the Gradle verification task:

```powershell
.\gradlew.bat :twilight:auditServerSources `
  -Ptwilight.audit.roots='<server-root>,<second-server-root>' `
  -Ptwilight.audit.output='build/reports/twilight-source-audit.json'
```

The task discovers sources and worlds, parses indexed JSON, expands current item-definition trees, resolves legacy model references, and writes a bounded JSON report. It does not modify the audited servers.

## Server smoke test

Use a disposable local Paper/Folia server with current Geyser:

1. Install `Twilight.jar`.
2. Confirm plugin enable and `vanilla-override=false`.
3. Run `/twilight status`, `/twilight scan`, and `/twilight convert`.
4. Confirm an operation log exists for every command.
5. On strict failure, confirm `build/current` and deployed Geyser files retain their previous hashes.
6. On success, verify `pack.zip`, Geyser mapping v2 JSON, manifest UUIDs, and build report.
7. Deploy four successive known builds and confirm only the newest three snapshots remain; roll back each retained index.

## Visual acceptance

Visual acceptance begins only after structural validation passes. Use a disposable Paper/Geyser server and an automated client run that records commands, perspectives, content logs, and machine-readable results. Required matrices include:

- 2D single/multi-layer icons and animation frames;
- 3D simple and composite models in GUI, first-person hands, third-person hands, ground/frame, and head contexts;
- bow/crossbow pull and charge states, fishing cast/line, shield use, trident/spear, armor, and elytra;
- emoji, glyph, menu, and HUD baselines;
- custom mobs/furniture with idle/move/attack/death animations;
- biome and seasonal transitions in affected worlds.

No private pack, third-party plugin binary, world, credential, or raw server log may enter the public repository or release artifact. Publish screenshots only with explicit operator authorization and after reviewing every image for private data. The [2026-09-27 review](REAL_CONTENT_REVIEW.md) contains authorized test captures, not redistributable source packs.

Local development build 3 passed 65 tests across 16 suites. Regressions cover independent pose bases, left-hand mirroring, Euler singularities, rotated cuboid corners, default/rotated UVs, first animation-frame selection, tall static textures, rejected GUI/spacing providers, pack overlays, display interpolation and visibility, and restart state across repeated deployment, configuration reload, and rollback. Passing these checks does not imply full client visual parity.

---

## Türkçe

### Doğrulama ve kabul

Twilight, her görsel kabul turundan önce tekrarlanabilir makine denetimleri kullanır.

[Ek model matrisi](MODEL_MATRIX_2026-09-28.md) yedi gerçek modeli, eşlenmiş istemci
görüntülerini ve görsel kabulü engelleyen hataları kaydeder.

#### Yerel derleme

```powershell
$env:JAVA_HOME='<jdk-25-yolu>'
$env:GRADLE_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'
.\gradlew.bat :twilight:build --no-daemon --no-configuration-cache
```

Çekirdek testler dünya çözümlemesini, arşivden dışarı çıkan yolların reddini, içerik
sınıflandırmasını, katmanlı eski eşyaları, modern koşul varyantlarını, hacimli geometriyi, model
ebeveynine göre elde tutma seçimini, birebir el dönüşümlerini, karışık boyutlu kararlı glif
sayfalarını, adlandırılmış font çakışma güvenliğini, katmanlı ve başvurulan sesleri, belirlenimci
ZIP çıktısını, Geyser sahiplik sınırlarını, üç anlık görüntünün saklanmasını ve geri almayı doğrular.

#### Kaynak denetimi

Sunucu kökleri Gradle doğrulama göreviyle salt okunur denetlenebilir:

```powershell
.\gradlew.bat :twilight:auditServerSources `
  -Ptwilight.audit.roots='<sunucu-kökü>,<ikinci-sunucu-kökü>' `
  -Ptwilight.audit.output='build/reports/twilight-source-audit.json'
```

Görev kaynakları ve dünyaları bulur, dizinlenen JSON'ları ayrıştırır, güncel eşya tanım ağaçlarını
açar, eski model başvurularını çözer ve sınırlı bir JSON raporu yazar. Denetlenen sunucuları
değiştirmez.

#### Sunucu duman testi

Güncel Geyser'lı, atılabilir bir yerel Paper/Folia sunucusu kullanın:

1. `Twilight.jar` dosyasını kurun.
2. Eklentinin açıldığını ve `vanilla-override=false` olduğunu doğrulayın.
3. `/twilight status`, `/twilight scan` ve `/twilight convert` komutlarını çalıştırın.
4. Her komut için bir işlem günlüğü oluştuğunu doğrulayın.
5. Katı hata durumunda `build/current` ve dağıtılmış Geyser dosyalarının önceki karmalarını
   koruduğunu doğrulayın.
6. Başarıda `pack.zip`, Geyser eşleme v2 JSON'u, manifest UUID'leri ve derleme raporunu doğrulayın.
7. Ardışık dört bilinen derlemeyi dağıtın ve yalnızca en yeni üç anlık görüntünün kaldığını
   doğrulayın; saklanan her sırayı geri alın.

#### Görsel kabul

Görsel kabul ancak yapısal doğrulama geçtikten sonra başlar. Atılabilir bir Paper/Geyser sunucusu
ve komutları, bakış açılarını, içerik günlüklerini ve makinece okunabilir sonuçları kaydeden
otomatik bir istemci çalıştırması kullanın. Gerekli matrisler:

- 2B tek/çok katmanlı simgeler ve animasyon kareleri;
- arayüz, birinci şahıs eller, üçüncü şahıs eller, yer/çerçeve ve kafa bağlamlarında 3B basit ve
  bileşik modeller;
- yay/arbalet germe ve dolum durumları, olta atma/ipi, kalkan kullanımı, trident/mızrak, zırh ve elytra;
- emoji, glif, menü ve HUD taban çizgileri;
- bekleme/yürüme/saldırı/ölüm animasyonlu özel moblar/mobilyalar;
- etkilenen dünyalarda biyom ve mevsim geçişleri.

Hiçbir özel paket, üçüncü taraf eklenti ikilisi, dünya, kimlik bilgisi veya ham sunucu günlüğü
herkese açık depoya veya sürüm dosyasına giremez. Ekran görüntülerini yalnızca açık operatör izniyle
ve her görüntüyü özel veriler için inceledikten sonra yayımlayın. [2026-09-27 incelemesi](REAL_CONTENT_REVIEW.md)
yeniden dağıtılabilir kaynak paketleri değil, izin verilmiş test görüntülerini içerir.

Yerel geliştirme derlemesi 3, 16 paketteki 65 testi geçti. Regresyonlar bağımsız
poz tabanlarını, sol el aynalamasını, Euler tekilliklerini, döndürülmüş küboid köşelerini,
varsayılan/döndürülmüş UV'leri, ilk animasyon karesi seçimini, uzun durağan dokuları, reddedilen
arayüz/boşluk sağlayıcılarını, paket katmanlarını, görüntü ara değerlemesini ve görünürlüğünü ve
tekrarlanan dağıtım, yapılandırma yeniden yükleme ve geri alma boyunca yeniden başlatma durumunu
kapsar. Bu denetimlerin geçmesi tam istemci görsel eşdeğerliği anlamına gelmez.
