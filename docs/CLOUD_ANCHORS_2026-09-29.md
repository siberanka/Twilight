# Automatic cloud-anchor adaptation — 29 September 2026

> Türkçe: [aşağıda](#türkçe)

> The screenshots of this report were retired on 4 October 2026 in favour of current captures; the measurements below remain. Current Java/Bedrock captures are in the [README](../README.md#visual-acceptance-tests).

Twilight now removes the unwanted particles around the tested ModelEngine models
automatically. ModelEngine uses invisible Java area-effect clouds with radius zero
as model anchors. Geyser's normal cloud translator clamps the radius to at least
0.5, which made these anchors emit particles on Bedrock.

The display bridge inherits Geyser's cloud translator and selects an invisible
armor-stand actor only when the Java cloud is invisible and its radius is exactly
zero. The Java entity and its metadata remain unchanged. Positive-radius and
visible clouds retain Geyser's normal representation. Live radius/flag changes
switch back automatically. A complete metadata snapshot and passenger links are
restored when replacing the client actor; the server-side mount graph is retained.
There are no model-name exceptions, source edits, operator repair commands, or
resource-pack changes. Another integration's registered cloud translator or custom
spawn definition is left alone.

## Validation

The local Java 25 build passed 67 tests across 17 suites. The isolated Paper 26.2
server used ModelEngine R4.1.1, MythicMobs 5.13.1 and Geyser 2.11.3 build 1247.
It was fully restarted with the new JAR before capturing evidence. Acceptance
used only the plugin implementation; earlier diagnostic packet mutations were
not applied to this process. Read-only runtime inspection recorded the actors
and mount relationships.

Four unchanged models from the previous matrix were sampled in seven paired
Java/Bedrock poses: bear idle/attack, crab idle/hide, archer idle/attack1 and the
static potion basket. Unwanted particles were absent in all seven Bedrock samples;
all four meshes remained visible. An inspection while the models were in range
found 17 adapted anchors and 148 passenger links, with zero broken backreferences.

The separate BetterModel 3.4.2 snapshot test server was also restarted with the
same JAR. Griffon idle/interact, blacksmith idle/wave and owl-crate idle/open
produced six more paired samples. All three meshes remained visible and the
previous facing correction remained intact. These captures sample running
animations sequentially, so differences such as the owl lid being at different
stages are not frame-exact comparisons. Full animation parity remains unaccepted.

A disposable vanilla cloud and invulnerable, stationary pig passenger tested
changes on the same Java entity:

| Java state | Bedrock representation | Result |
|---|---|---|
| Invisible, radius 1.5 | Normal cloud | Particles remain visible |
| Invisible, radius 0 | Invisible armor stand | Particles absent; passenger retained |
| Visible, radius 0 | Normal cloud | Normal Geyser clamping restored |
| Invisible, radius 0 again | Invisible armor stand | Repeat transition passes |
| Invisible, radius 1.5 again | Normal cloud | Prior particle/color metadata retained |
| Reconnect while invisible/radius 0 | Invisible armor stand | Passenger reappears; particles absent |

Each transition retained all 120 in-range cloud passenger links with zero broken
backreferences. Cleanup removed the disposable cloud and passenger, leaving 13
provider anchors and 119 links. Counts differ from the earlier model inspection
because the camera moved and the entity tracker changed the in-range set.
The normal-cloud check establishes preservation of Geyser behavior, not Java/Bedrock
particle-style equivalence.

Both players ended at 20/20 health. All ten saved player death counters across
the two isolated test worlds were zero. Production assets were read-only; all
seven selected source hashes still matched the earlier inventory. No combat
skills were imported. Client launch, connection and known prompt handling used
the private `ai/client-join/join.py` automation documented in
`ai/CLIENT_TEST_AUTOMATION.md`; the new wide pause-menu template was also checked.
The local cloud transition runner changes the disposable Java entity, never
Geyser's live representation.

## Remaining defects

Follow-up: [the mount-height correction](DISPLAY_SEATS_2026-09-29.md) resolves
the basket darkening described below. These captures preserve the earlier build's
results and its original limitations.

The potion basket still becomes nearly black above the stone platform. Some
crab/archer pose differences also remain. This change does not establish full
animation timing, lighting, material, billboard, first-person, glyph or UI parity.
The [earlier matrix](MODEL_MATRIX_2026-09-28.md) retains the failing baseline and
the lighting-isolation evidence. This is a development checkpoint, not an accepted
release. Original artwork remains owned by its creators; screenshots are
compatibility evidence and do not license redistribution of the source assets.

## Evidence

All captures were visually reviewed. [Results](images/acceptance/2026-09-29-clouds/results.json)
and [SHA-256 hashes](images/acceptance/2026-09-29-clouds/sha256.json) accompany the
thirteen paired samples and five transition/reconnect images (31 PNGs).
The [transition assertions](images/acceptance/2026-09-29-clouds/transitions.json)
record metadata and passenger counts. Reconnection starts fresh metadata; the
previous session's unused effect color is not carried into the new flame cloud.

| Java | Bedrock |
|---|---|
| Bear idle Java | Bear idle Bedrock |
| Bear attack Java | Bear attack Bedrock |
| Crab idle Java | Crab idle Bedrock |
| Crab hide Java | Crab hide Bedrock |
| Archer idle Java | Archer idle Bedrock |
| Archer attack Java | Archer attack Bedrock |
| Basket Java | Basket Bedrock |
| Griffon idle Java | Griffon idle Bedrock |
| Griffon interact Java | Griffon interact Bedrock |
| Blacksmith idle Java | Blacksmith idle Bedrock |
| Blacksmith wave Java | Blacksmith wave Bedrock |
| Owl idle Java | Owl idle Bedrock |
| Owl open Java | Owl open Bedrock |

| Normal cloud with passenger | Invisible point anchor with passenger |
|---|---|
| Positive radius | Zero radius invisible |
| Visible zero radius | Reconnect |

Normal cloud restored

---

## Türkçe

### Otomatik bulut çapası uyarlaması — 29 Eylül 2026

> Bu raporun ekran görüntüleri 4 Ekim 2026'da güncel görüntüler lehine kaldırıldı; aşağıdaki ölçümler
> geçerlidir. Güncel Java/Bedrock görüntüleri [README](../README.md#visual-acceptance-tests) içindedir.

Twilight artık test edilen ModelEngine modellerinin çevresindeki istenmeyen parçacıkları kendiliğinden
kaldırıyor. ModelEngine, model çapası olarak yarıçapı sıfır olan görünmez Java alan etkisi bulutları
kullanır. Geyser'ın olağan bulut çevirmeni yarıçapı en az 0,5'e sabitler; bu da bu çapaların
Bedrock'ta parçacık çıkarmasına yol açıyordu.

Görüntü köprüsü Geyser'ın bulut çevirmeninden türer ve yalnızca Java bulutu görünmez ve yarıçapı tam
olarak sıfır olduğunda görünmez bir zırh askılığı aktörü seçer. Java varlığı ve meta verisi değişmez.
Pozitif yarıçaplı ve görünür bulutlar Geyser'ın olağan temsilini korur. Canlı yarıçap/bayrak
değişiklikleri kendiliğinden geri döner. İstemci aktörü değiştirilirken tam bir meta veri anlık
görüntüsü ve yolcu bağlantıları geri yüklenir; sunucu tarafındaki binme çizgesi korunur. Model adı
istisnası, kaynak düzenlemesi, operatör onarım komutu veya kaynak paketi değişikliği yoktur. Başka bir
entegrasyonun kayıtlı bulut çevirmenine veya özel doğuş tanımına dokunulmaz.

#### Doğrulama

Yerel Java 25 derlemesi 17 paketteki 67 testi geçti. Yalıtılmış Paper 26.2 sunucusu ModelEngine R4.1.1,
MythicMobs 5.13.1 ve Geyser 2.11.3 derleme 1247 kullandı. Kanıt toplanmadan önce yeni JAR ile tamamen
yeniden başlatıldı. Kabulde yalnızca eklenti uygulaması kullanıldı; önceki tanı amaçlı paket
değişiklikleri bu sürece uygulanmadı. Salt okunur çalışma zamanı incelemesi aktörleri ve binme
ilişkilerini kaydetti.

Önceki matristen değişmemiş dört model yedi eşlenmiş Java/Bedrock pozunda örneklendi: ayı
bekleme/saldırı, yengeç bekleme/saklanma, okçu bekleme/saldırı1 ve durağan iksir sepeti. Yedi Bedrock
örneğinin hiçbirinde istenmeyen parçacık yoktu; dört ağ da görünür kaldı. Modeller menzildeyken yapılan
incelemede 17 uyarlanmış çapa ve 148 yolcu bağlantısı bulundu, kırık geri başvuru sıfırdı.

Ayrı BetterModel 3.4.2 anlık test sunucusu da aynı JAR ile yeniden başlatıldı. Griffon
bekleme/etkileşim, demirci bekleme/el sallama ve baykuş kasası bekleme/açılma altı eşlenmiş örnek daha
üretti. Üç ağ da görünür kaldı ve önceki yön düzeltmesi sağlam kaldı. Bu görüntüler çalışan
animasyonları sırayla örnekler; baykuş kapağının farklı aşamalarda olması gibi farklar kare kare
birebir karşılaştırma değildir. Tam animasyon eşdeğerliği kabul edilmedi.

Atılabilir bir vanilla bulut ve hasar almayan, sabit bir domuz yolcu aynı Java varlığındaki
değişiklikleri test etti:

| Java durumu | Bedrock temsili | Sonuç |
|---|---|---|
| Görünmez, yarıçap 1,5 | Olağan bulut | Parçacıklar görünür kalır |
| Görünmez, yarıçap 0 | Görünmez zırh askılığı | Parçacık yok; yolcu korunur |
| Görünür, yarıçap 0 | Olağan bulut | Geyser'ın olağan sabitlemesi geri gelir |
| Yeniden görünmez, yarıçap 0 | Görünmez zırh askılığı | Tekrarlanan geçiş geçer |
| Yeniden görünmez, yarıçap 1,5 | Olağan bulut | Önceki parçacık/renk meta verisi korunur |
| Görünmez/yarıçap 0 iken yeniden bağlanma | Görünmez zırh askılığı | Yolcu yeniden görünür; parçacık yok |

Her geçiş menzildeki 120 bulut yolcu bağlantısının tamamını korudu, kırık geri başvuru sıfırdı.
Temizlik atılabilir bulutu ve yolcuyu kaldırdı; 13 sağlayıcı çapası ve 119 bağlantı kaldı. Sayılar
önceki model incelemesinden farklıdır çünkü kamera hareket etti ve varlık izleyicisi menzildeki kümeyi
değiştirdi. Olağan bulut denetimi Geyser davranışının korunduğunu kanıtlar, Java/Bedrock parçacık
biçimi eşdeğerliğini değil.

İki oyuncu da 20/20 canla bitirdi. İki yalıtılmış test dünyasındaki kaydedilmiş on oyuncu ölüm sayacının
hepsi sıfırdı. Üretim dosyaları salt okunurdu; seçilen yedi kaynak karmasının hepsi önceki envanterle
hâlâ eşleşiyor. Hiçbir savaş becerisi içe aktarılmadı. İstemci başlatma, bağlantı ve bilinen uyarıların
işlenmesi `ai/CLIENT_TEST_AUTOMATION.md` içinde belgelenen özel `ai/client-join/join.py` otomasyonunu
kullandı; yeni geniş duraklatma menüsü şablonu da denetlendi. Yerel bulut geçiş çalıştırıcısı Geyser'ın
canlı temsilini değil, atılabilir Java varlığını değiştirir.

#### Kalan hatalar

Devamı: [binme yüksekliği düzeltmesi](DISPLAY_SEATS_2026-09-29.md) aşağıda anlatılan sepet kararmasını
çözer. Bu görüntüler önceki derlemenin sonuçlarını ve özgün sınırlarını saklar.

İksir sepeti taş platformun üzerinde hâlâ neredeyse siyah oluyor. Bazı yengeç/okçu poz farkları da
sürüyor. Bu değişiklik tam animasyon zamanlaması, ışıklandırma, malzeme, billboard, birinci şahıs, glif
veya arayüz eşdeğerliği sağlamaz. [Önceki matris](MODEL_MATRIX_2026-09-28.md) başarısız temel çizgiyi ve
ışık yalıtma kanıtını korur. Bu kabul edilmiş bir sürüm değil, bir geliştirme kontrol noktasıdır. Özgün
görseller yaratıcılarına aittir; ekran görüntüleri uyumluluk kanıtıdır ve kaynak dosyaların yeniden
dağıtımına izin vermez.

#### Kanıt

Tüm görüntüler gözle incelendi. [Sonuçlar](images/acceptance/2026-09-29-clouds/results.json) ve
[SHA-256 karmaları](images/acceptance/2026-09-29-clouds/sha256.json) on üç eşlenmiş örneğe ve beş
geçiş/yeniden bağlanma görüntüsüne (31 PNG) eşlik eder. [Geçiş doğrulamaları](images/acceptance/2026-09-29-clouds/transitions.json)
meta veriyi ve yolcu sayılarını kaydeder. Yeniden bağlanma meta veriyi sıfırdan başlatır; önceki
oturumun kullanılmayan etki rengi yeni alev bulutuna taşınmaz.

Görüntüler kaldırıldığı için tablolar yalnızca örnek adlarını listeler: ayı, yengeç, okçu, sepet,
griffon, demirci ve baykuş (Java ve Bedrock çiftleri); bulut geçişleri için pozitif yarıçap, görünmez
sıfır yarıçap, görünür sıfır yarıçap, yeniden bağlanma ve geri gelen olağan bulut.
