# Security policy

> Türkçe: [aşağıda](#türkçe)

## Supported versions

Security fixes are made for the newest version of Twilight and twilight-proxy, currently the 1.0.0 betas.
Each fix is a new version: published JARs are never replaced. Earlier development builds receive no fixes;
update to the newest version.

## Reporting a vulnerability

Please report vulnerabilities privately, never in a public issue:

- **GitHub:** [report a vulnerability](https://github.com/siberanka/twilight/security/advisories/new)
  (Security tab → "Report a vulnerability").
- **GitLab:** open an issue on [GitLab](https://gitlab.com/siberanka/twilight/-/issues/new) and tick
  "This issue is confidential".

Include the affected version, your setup (single server or proxy network), the steps to reproduce and the
impact you expect. Leave out real secrets, addresses and player data; a test value shows the problem just
as well. You will get an answer in the report. Once a fix is released, the changelog names the problem
and, if you wish, credits you.

## Scope

In scope are Twilight's and twilight-proxy's own code and defaults, for example:

- the checks on packs, archives and paths (ZIP traversal, size limits, symbolic links);
- the signed plugin messages between Twilight and twilight-proxy;
- the pack host's download links;
- the update check;
- the files Twilight writes into Geyser and its own folders.

Problems in Geyser, Floodgate, a proxy or a content plugin belong to those projects. If Twilight makes
such a problem worse, report it here as well.

---

## Türkçe

### Güvenlik politikası

#### Desteklenen sürümler

Güvenlik düzeltmeleri Twilight ve twilight-proxy'nin en yeni sürümü için yapılır; şu anda 1.0.0 betaları.
Her düzeltme yeni bir sürümdür: yayımlanmış JAR'lar asla değiştirilmez. Önceki geliştirme derlemeleri düzeltme
almaz; en yeni sürüme güncelleyin.

#### Bir güvenlik açığını bildirme

Güvenlik açıklarını herkese açık bir issue'da değil, lütfen gizli olarak bildirin:

- **GitHub:** [bir güvenlik açığı bildirin](https://github.com/siberanka/twilight/security/advisories/new)
  (Security sekmesi → "Report a vulnerability").
- **GitLab:** [GitLab'da](https://gitlab.com/siberanka/twilight/-/issues/new) bir issue açın ve
  "This issue is confidential" kutusunu işaretleyin.

Etkilenen sürümü, kurulumunuzu (tek sunucu veya proxy'li ağ), yeniden üretme adımlarını ve beklediğiniz
etkiyi ekleyin. Gerçek gizli bilgileri, adresleri ve oyuncu verilerini eklemeyin; bir test değeri sorunu
aynı şekilde gösterir. Yanıtı bildirimin içinde alırsınız. Bir düzeltme yayımlandığında değişiklik günlüğü
sorunu adlandırır ve isterseniz size teşekkür eder.

#### Kapsam

Twilight'ın ve twilight-proxy'nin kendi kodu ve varsayılanları kapsamdadır, örneğin:

- paketler, arşivler ve yollar üzerindeki denetimler (ZIP dışına çıkma, boyut sınırları, sembolik bağlantılar);
- Twilight ile twilight-proxy arasındaki imzalı eklenti mesajları;
- paket sunucusunun indirme bağlantıları;
- güncelleme denetimi;
- Twilight'ın Geyser'a ve kendi klasörlerine yazdığı dosyalar.

Geyser, Floodgate, bir proxy veya bir içerik eklentisindeki sorunlar o projelere aittir. Twilight böyle bir
sorunu kötüleştiriyorsa burada da bildirin.
