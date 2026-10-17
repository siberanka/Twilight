# Twilight 1.0.0-beta.5 build

> Türkçe: [aşağıda](#türkçe)

[Download Twilight.jar](Twilight.jar?raw=true) | [SHA-256](Twilight.jar.sha256) (backend servers)

[Download TwilightProxy.jar](TwilightProxy.jar?raw=true) | [SHA-256](TwilightProxy.jar.sha256) (Velocity and BungeeCord proxies, optional)

Built locally by siberanka using Java 25.0.2 and Gradle 9.6.0, with
`:twilight:build :twilight-proxy:build --offline --no-daemon --no-configuration-cache`.
352 tests across 61 suites passed. No hosted CI was used.

The corresponding source is in this commit under `twilight/`, `twilight-proxy/` and `protocol/`, with build files
at the repository root. Licensed under the [Twilight License](../LICENSE) (the PolyForm Noncommercial
License 1.0.0 with additional terms): it may run on any server or network, including ones that earn money;
selling it is not permitted, and copied or adapted parts must credit Twilight ([NOTICE](../NOTICE)).

Read the [release notes](../RELEASE_NOTES.md), the [wiki](../WIKI.md) and the
[beta status and compatibility](../README.md#beta-status-and-compatibility) summary before deployment.
Install the same version of both JARs and restart once after the first build.

---

## Türkçe

### Twilight 1.0.0-beta.5 derlemesi

[Twilight.jar indir](Twilight.jar?raw=true) | [SHA-256](Twilight.jar.sha256) (arka uç sunucuları)

[TwilightProxy.jar indir](TwilightProxy.jar?raw=true) | [SHA-256](TwilightProxy.jar.sha256) (Velocity ve BungeeCord proxy'leri, isteğe bağlı)

siberanka tarafından Java 25.0.2 ve Gradle 9.6.0 ile yerel olarak
`:twilight:build :twilight-proxy:build --offline --no-daemon --no-configuration-cache`
komutuyla derlendi. 61 paketteki 352 test geçti. Barındırılan CI kullanılmadı.

İlgili kaynak kod bu commit içinde `twilight/`, `twilight-proxy/` ve `protocol/` altında, derleme
dosyaları depo kökündedir. [Twilight License](../LICENSE) ile (ek koşullarla birlikte PolyForm
Noncommercial License 1.0.0) lisanslanmıştır: para kazananlar dahil her sunucuda veya ağda çalışabilir;
satılması yasaktır ve kopyalanan veya uyarlanan parçalar Twilight'ı kaynak olarak göstermelidir
([NOTICE](../NOTICE)).

Dağıtımdan önce [sürüm notlarını](../RELEASE_NOTES.md), [wiki'yi](../WIKI.md) ve
[beta durumu ve uyumluluk](../README.md#beta-durumu-ve-uyumluluk) özetini okuyun. İki JAR'ın aynı sürümünü
kurun ve ilk derlemeden sonra bir kez yeniden başlatın.
