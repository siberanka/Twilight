# Contributing to Twilight

> Türkçe: [aşağıda](#türkçe)

Thank you for helping. Issues and merge or pull requests are welcome on
[GitHub](https://github.com/siberanka/twilight) and [GitLab](https://gitlab.com/siberanka/twilight); both
hosts carry the same history. You can write in English or Turkish.

## Reporting problems

- Use the newest version first; the problem may already be fixed ([changelog](CHANGELOG.md)).
- Pick an issue template (bug, compatibility request or question) and fill in every field.
- One problem per issue. Java and Bedrock screenshots of the same thing help most.
- Never post secrets (forwarding secrets, `secret` / `proxy.secret`, Floodgate `key.pem`), addresses,
  player data, or paid and private assets. Vulnerabilities go to the [security policy](SECURITY.md).

## Changing code

- Twilight targets Java 21 and is built with Java 25 and the Gradle wrapper:

  ```text
  ./gradlew :twilight:build :twilight-proxy:build --offline --no-configuration-cache
  ```

  `twilight/` is the server plugin, `twilight-proxy/` the proxy plugin and `protocol/` the code both
  compile in.
- Add or update tests for every change and run the whole build; there is no hosted CI. Say in the
  request what you tested and on which server, proxy, Geyser and Bedrock versions.
- Keep changes focused and follow the surrounding code. Do not add files from other projects unless their
  licence allows it, and name their source.
- Public Markdown files have an English part and a full Turkish translation under `## Türkçe`; update both.
- Twilight is licensed under the [Twilight License](LICENSE) (the PolyForm Noncommercial License 1.0.0
  with additional terms; summary in [NOTICE](NOTICE)). By
  submitting a contribution you confirm that you wrote it or may submit it, and you allow siberanka to
  publish it under that license and to license it to others, commercially as well.

---

## Türkçe

### Twilight'a katkıda bulunma

Yardımınız için teşekkürler. Issue'lar ve birleştirme (merge/pull) istekleri
[GitHub](https://github.com/siberanka/twilight) ve [GitLab](https://gitlab.com/siberanka/twilight) üzerinde
kabul edilir; iki barındırıcı da aynı geçmişi taşır. İngilizce veya Türkçe yazabilirsiniz.

#### Sorun bildirme

- Önce en yeni sürümü kullanın; sorun düzeltilmiş olabilir ([değişiklik günlüğü](CHANGELOG.md#türkçe)).
- Bir issue şablonu seçin (hata, uyumluluk isteği veya soru) ve her alanı doldurun.
- Issue başına bir sorun. Aynı şeyin Java ve Bedrock ekran görüntüleri en çok yardımcı olur.
- Gizli bilgileri (yönlendirme anahtarları, `secret` / `proxy.secret`, Floodgate `key.pem`), adresleri, oyuncu
  verilerini veya ücretli ve özel varlıkları asla paylaşmayın. Güvenlik açıkları
  [güvenlik politikasına](SECURITY.md#türkçe) gider.

#### Kodu değiştirme

- Twilight Java 21'i hedefler ve Java 25 ile Gradle wrapper kullanılarak derlenir:

  ```text
  ./gradlew :twilight:build :twilight-proxy:build --offline --no-configuration-cache
  ```

  `twilight/` sunucu eklentisi, `twilight-proxy/` proxy eklentisi ve `protocol/` ikisinin de içine derlenen
  koddur.
- Her değişiklik için test ekleyin veya güncelleyin ve tüm derlemeyi çalıştırın; barındırılan CI yoktur.
  İstekte neyi ve hangi sunucu, proxy, Geyser ve Bedrock sürümlerinde test ettiğinizi yazın.
- Değişiklikleri odaklı tutun ve çevredeki koda uyun. Lisansları izin vermedikçe başka projelerden dosya
  eklemeyin ve kaynaklarını belirtin.
- Herkese açık Markdown dosyalarında bir İngilizce bölüm ve `## Türkçe` altında tam bir Türkçe çeviri bulunur;
  ikisini de güncelleyin.
- Twilight, [Twilight License](LICENSE) ile (ek koşullarla birlikte PolyForm Noncommercial License 1.0.0;
  özeti [NOTICE](NOTICE) içinde) lisanslanmıştır. Bir
  katkı göndererek onu sizin yazdığınızı veya gönderme hakkınız olduğunu onaylar; siberanka'nın onu bu lisansla
  yayımlamasına ve başkalarına, ticari olarak da, lisanslamasına izin verirsiniz.
