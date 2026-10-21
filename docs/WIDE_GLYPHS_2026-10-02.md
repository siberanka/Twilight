# Wide bitmap glyphs and UI images - 2 October 2026

> Türkçe: [aşağıda](#türkçe)

> The screenshots of this report were retired on 4 October 2026 in favour of current captures; the measurements below remain. Current Java/Bedrock captures are in the [README](../README.md#visual-acceptance-tests).

Twilight now enlarges a Unicode atlas cell when a Java bitmap needs more space.
It preserves the authored display dimensions instead of shrinking a wide rank
or menu image into a 16-pixel cell. The formerly omitted 41-by-9 rank and six
additional UI/HUD images render in the tested Bedrock chat. **Complete menu and
pixel parity is still not achieved.** A real inventory test exposes native title
clipping, and fractional scaling and horizontal padding still differ.

## Automatic conversion

Each glyph selects the smallest power-of-two cell from 16 through 512 pixels
that contains its width and visible vertical bounds. A page uses the largest
required cell among its glyphs. Each glyph retains its own Java display height
and ascent; a larger neighbor does not resize or vertically displace it.

Independent native calibration used pages from 256 through 8192 pixels wide.
In the tested Bedrock client, one texture pixel remains one GUI unit. The cell
origin relative to ordinary text is `4 - cellSize / 2`; placing the bitmap at
`cellSize / 2 + 3 - ascent` therefore reproduces Java's `7 - ascent` top offset.
Six equal 9-by-9 probes on different page sizes confirmed this relationship.
The native calibration capture
is diagnostic evidence, separate from the compiler-output screenshots below.
The diagnostic pages were removed before acceptance of actual compiler output.

Visible overflow beyond the supported bound remains a diagnostic and fails
strict compilation. Transparent padding cannot overwrite neighboring cells.
No source asset names, provider-specific exceptions or manual output corrections
are used by the implementation.

## Real-source coverage

The new fixture retains 36 real glyphs and six metric probes and adds six unused
Survival images with their original PNGs, code points, heights and ascents.
All 42 original PNG entries and isolated copies were verified unchanged.
Both clients used two screen pixels per GUI unit for these captures.

| Sample | Declared width x height | Ascent | Observed result |
| --- | --- | --- | --- |
| Player rank | 41 x 9 | 8 | All opaque pixels match both clients exactly; previously omitted. |
| Barrel menu image | 176 x 83 | 14 | Opaque pixels match both chat captures exactly. Native inventory title clips the image. |
| Fisherman portrait | 72 x 72 | -1 | Original 36 x 36 image uses the authored 2x enlargement; opaque pixels match both clients exactly. |
| Mana HUD image | 20.83 x 50 | -15 | Appears below the text as authored; Java fractional sampling differs from the integer Bedrock bitmap. |
| Lands menu image | 236 x 245 | 81 | Opaque pixels match; Bedrock removes 12 GUI units of transparent left padding. |
| Leaderboard image | 256 x 256 | 48 | Opaque pixels match both chat captures exactly, including the complete visible lower inventory area. |
| Exit banner | 198.36 x 74 | 10 | Image transfers, but fractional sampling differs. Not a pixel-parity pass. |

The five exact comparisons above concern opaque pixels after alignment, not
whole-screen equality, text advance, transparent margins or menu behavior.
For the fractional exit banner, the atlas also differs at 219 pixels from the
independent Pillow nearest-neighbor reference because sampling conventions
differ. This measurement is retained as a failure, not rounded into a pass.
The [measurements](images/acceptance/2026-10-02-wide-fonts/measurements.json)
include coordinates, display sizes and masked mean channel errors. Java's chat
culls offscreen line anchors even when a tall glyph could extend into view;
the capture helper keeps anchors visible without changing the source metrics.

| Compilation audit | Sources | Item mappings | Glyphs | Pages |
| --- | --- | --- | --- | --- |
| Box content | 30 | 772 / 781 | 622, previously 573 | 4 |
| Survival content | 138 | 125 / 130 | 751, previously 598 | 6 |
| Live mixed fixture | 4 | 167 / 168 | 48 / 48 | 5 |

All three repeated packs were byte-identical. Strict builds rejected remaining
problems and preserved the last diagnostic pack. The mixed fixture has one
known item with a missing face texture and no font omissions. Whole-source
audits still report malformed/missing assets, contextual font remapping,
unsupported spacing and extreme dimensions/baselines. Counts represent compiler
coverage, not hundreds of individually verified visual passes.

## Reviewed client evidence

| Test | Java | Bedrock |
| --- | --- | --- |
| Six metric probes | Reference | Converted |
| 36 real glyphs, including wide rank | Reference | All visible |
| Barrel image in chat | Reference | Converted |
| Portrait | Reference | Converted |
| Negative-ascent HUD image | Reference | Sampling difference |
| Lands image | Reference | Left padding differs |
| Leaderboard image | Reference | Converted |
| Exit banner | Reference | Sampling difference |
| Actual 36-item inventory with barrel title | Darkened image overlays slots | Title image clipped by native label |

The inventory pair is a **recorded failure**, not successful custom-menu
adaptation. The glyph works in chat but exceeds the native title label's space.
It needs a runtime/UI adapter that also accounts for Java spacing and tint.
The existing two invalid Java item references remain visible in this inventory.

Captures are unchanged client screenshots. [SHA-256 hashes](images/acceptance/2026-10-02-wide-fonts/sha256.json)
identify the evidence. Original third-party packs, server data and test helpers
are not distributed. Test players remained creative; both had health 20 at the
final save and all ten saved player death counters remained zero.

## Build and remaining limits

The locally built development build 4 JAR passed 87 tests in 19 suites.
Three new regressions cover enlarged ascenders/descenders, every pixel of a
wide label plus its small neighbor, and a large menu bitmap. Existing model,
animation, mapping and deployment tests remain in the full build. This round
does not claim a fresh live pose/animation matrix for every provider.

- JAR SHA-256: `37a4195d2d4e36c23d7afe61d8c46741879601927fd76ff8358b9c427f6d4e24`
- Tested compiler pack SHA-256: `28b132858cdb19a45379bc2a039001af6a4a1325c00aee10dbeb5ac2b6e73ed3`
- Java 26.2, Bedrock 1.26.5203.0, Geyser 2.11.3 build 1247; local build only.

An 8192-square RGBA atlas occupies 256 MiB before compression. This fixture's
five pages total 384.5 MiB of raw pixels; peak compiler/client memory can be
higher. Acceptance on this Windows client does not certify low-memory devices.

Preserving authored dimensions does not preserve every high-resolution source
texel: native font pixels still use integer GUI units. Fractional resampling,
horizontal bearings/advances, title clipping and tint, contextual named fonts,
dynamic HUD placement and full interactive menu layout remain open. First-person
model poses and complete animated-texture playback also remain open. A successful
compiler build is not a guarantee of fidelity in every usage context.

The native glyph-page structure was checked against Microsoft's
[resource-pack contents reference](https://learn.microsoft.com/en-us/minecraft/creator/documents/comprehensivepackcontents?view=minecraft-bedrock-stable)
and [NhanAZ/glyph](https://github.com/NhanAZ/glyph) (GPL-3.0).
No external code was imported. The placement formula and runtime limits above
come from Twilight's own client measurements.

---

## Türkçe

### Geniş bitmap glifler ve arayüz görselleri - 2 Ekim 2026

> Bu raporun ekran görüntüleri 4 Ekim 2026'da güncel görüntüler lehine kaldırıldı; aşağıdaki ölçümler
> geçerlidir. Güncel Java/Bedrock görüntüleri [README](../README.md#visual-acceptance-tests) içindedir.

Twilight artık bir Java bitmap'i daha fazla alana ihtiyaç duyduğunda Unicode atlas hücresini büyütüyor.
Geniş bir rütbeyi veya menü görselini 16 piksellik hücreye küçültmek yerine yazarın verdiği görüntü
boyutlarını korur. Önceden dışarıda bırakılan 41'e 9 rütbe ve altı ek arayüz/HUD görseli test edilen
Bedrock sohbetinde çiziliyor. **Tam menü ve piksel eşdeğerliğine hâlâ ulaşılmadı.** Gerçek bir envanter
testi yerel başlık kırpılmasını ortaya çıkarıyor; kesirli ölçekleme ve yatay dolgu hâlâ farklı.

#### Otomatik dönüşüm

Her glif, genişliğini ve görünür dikey sınırlarını kapsayan 16 ile 512 piksel arasındaki en küçük ikinin
kuvveti hücreyi seçer. Bir sayfa, gliflerinin gerektirdiği en büyük hücreyi kullanır. Her glif kendi Java
görüntü yüksekliğini ve ascent değerini korur; daha büyük bir komşu onu yeniden boyutlandırmaz veya
dikeyde kaydırmaz.

Bağımsız yerel kalibrasyon 256 ile 8192 piksel genişliğinde sayfalar kullandı. Test edilen Bedrock
istemcisinde bir doku pikseli bir arayüz birimi olarak kalır. Hücrenin sıradan yazıya göre başlangıcı
`4 - hücreBoyutu / 2`'dir; bu yüzden bitmap'i `hücreBoyutu / 2 + 3 - ascent` konumuna koymak Java'nın
`7 - ascent` üst kaymasını yeniden üretir. Farklı sayfa boyutlarındaki altı eşit 9'a 9 deneme bu ilişkiyi
doğruladı. Yerel kalibrasyon görüntüsü aşağıdaki derleyici çıktısı görüntülerinden ayrı, tanı amaçlı
kanıttır. Tanı sayfaları gerçek derleyici çıktısının kabulünden önce kaldırıldı.

Desteklenen sınırın ötesindeki görünür taşma tanı olarak kalır ve katı derlemeyi başarısız kılar. Saydam
dolgu komşu hücrelerin üzerine yazamaz. Uygulamada kaynak varlık adları, sağlayıcıya özgü istisnalar
veya elle çıktı düzeltmeleri kullanılmaz.

#### Gerçek kaynak kapsamı

Yeni düzenek 36 gerçek glifi ve altı ölçü denemesini korur, özgün PNG'leri, kod noktaları, yükseklikleri
ve ascent'leriyle kullanılmamış altı Survival görseli ekler. 42 özgün PNG girdisinin ve yalıtılmış
kopyalarının değişmediği doğrulandı. İki istemci de bu görüntülerde arayüz birimi başına iki ekran
pikseli kullandı.

| Örnek | Bildirilen genişlik x yükseklik | Ascent | Gözlenen sonuç |
| --- | --- | --- | --- |
| Oyuncu rütbesi | 41 x 9 | 8 | Tüm opak pikseller iki istemcide birebir eşleşiyor; önceden dışarıda bırakılıyordu. |
| Varil menü görseli | 176 x 83 | 14 | Opak pikseller iki sohbet görüntüsünde birebir eşleşiyor. Yerel envanter başlığı görseli kırpıyor. |
| Balıkçı portresi | 72 x 72 | -1 | Özgün 36 x 36 görsel yazarın verdiği 2 kat büyütmeyi kullanıyor; opak pikseller iki istemcide birebir eşleşiyor. |
| Mana HUD görseli | 20.83 x 50 | -15 | Yazarın verdiği gibi yazının altında görünür; Java'nın kesirli örneklemesi tam sayılı Bedrock bitmap'inden farklı. |
| Lands menü görseli | 236 x 245 | 81 | Opak pikseller eşleşiyor; Bedrock 12 arayüz birimi saydam sol dolguyu kaldırıyor. |
| Lider tablosu görseli | 256 x 256 | 48 | Görünür alt envanter alanının tamamı dahil opak pikseller iki sohbet görüntüsünde birebir eşleşiyor. |
| Çıkış afişi | 198.36 x 74 | 10 | Görsel aktarılıyor ancak kesirli örnekleme farklı. Piksel eşdeğerliği geçişi değil. |

Yukarıdaki beş birebir karşılaştırma hizalamadan sonraki opak pikselleri ilgilendirir; tüm ekran
eşitliğini, yazı ilerlemesini, saydam kenar paylarını veya menü davranışını değil. Kesirli çıkış afişi için
atlas, örnekleme kuralları farklı olduğundan bağımsız Pillow en yakın komşu referansından da 219 pikselde
farklı. Bu ölçüm bir geçişe yuvarlanmadan başarısızlık olarak saklanır.
[Ölçümler](images/acceptance/2026-10-02-wide-fonts/measurements.json) koordinatları, görüntü boyutlarını ve
maskelenmiş ortalama kanal hatalarını içerir. Java'nın sohbeti, uzun bir glif görüş alanına uzanabilecek
olsa bile ekran dışı satır çapalarını atlar; çekim yardımcısı kaynak ölçülerini değiştirmeden çapaları
görünür tutar.

| Derleme denetimi | Kaynaklar | Eşya eşlemeleri | Glifler | Sayfalar |
| --- | --- | --- | --- | --- |
| Box içeriği | 30 | 772 / 781 | 622, önceden 573 | 4 |
| Survival içeriği | 138 | 125 / 130 | 751, önceden 598 | 6 |
| Canlı karışık düzenek | 4 | 167 / 168 | 48 / 48 | 5 |

Tekrarlanan üç paketin hepsi bayt bayt aynıydı. Katı derlemeler kalan sorunları reddetti ve son tanı
paketini korudu. Karışık düzenekte yüz dokusu eksik bilinen bir eşya var ve font atlaması yok. Tüm kaynak
denetimleri hâlâ bozuk/eksik varlıkları, bağlama göre font yeniden eşlemeyi, desteklenmeyen aralıkları
ve aşırı boyutları/taban çizgilerini raporluyor. Sayılar tek tek doğrulanmış yüzlerce görsel geçişi
değil, derleyici kapsamını temsil eder.

#### İncelenen istemci kanıtı

| Test | Java | Bedrock |
| --- | --- | --- |
| Altı ölçü denemesi | Referans | Dönüştürüldü |
| Geniş rütbe dahil 36 gerçek glif | Referans | Hepsi görünür |
| Sohbette varil görseli | Referans | Dönüştürüldü |
| Portre | Referans | Dönüştürüldü |
| Negatif ascent'li HUD görseli | Referans | Örnekleme farkı |
| Lands görseli | Referans | Sol dolgu farklı |
| Lider tablosu görseli | Referans | Dönüştürüldü |
| Çıkış afişi | Referans | Örnekleme farkı |
| Varil başlıklı gerçek 36 eşyalık envanter | Koyulaşmış görsel yuvaların üstünde | Başlık görseli yerel etiket tarafından kırpılmış |

Envanter çifti başarılı bir özel menü uyarlaması değil, **kaydedilmiş bir başarısızlıktır**. Glif sohbette
çalışıyor ama yerel başlık etiketinin alanını aşıyor. Java aralığını ve renklendirmesini de hesaba katan
bir çalışma zamanı/arayüz bağdaştırıcısı gerekir. Mevcut iki geçersiz Java eşya referansı bu envanterde
görünür kalıyor.

Görüntüler değiştirilmemiş istemci ekran görüntüleridir. [SHA-256 karmaları](images/acceptance/2026-10-02-wide-fonts/sha256.json)
kanıtı tanımlar. Özgün üçüncü taraf paketleri, sunucu verileri ve test yardımcıları dağıtılmaz. Test
oyuncuları yaratıcı modda kaldı; son kayıtta ikisinin de canı 20'ydi ve kaydedilmiş on oyuncu ölüm
sayacının hepsi sıfır kaldı.

#### Derleme ve kalan sınırlar

Yerelde derlenen geliştirme derlemesi 4 JAR'ı 19 paketteki 87 testi geçti. Üç yeni regresyon büyütülmüş
yükselen/alçalan kısımları, geniş bir etiketin ve küçük komşusunun her pikselini ve büyük bir menü
bitmap'ini kapsar. Mevcut model, animasyon, eşleme ve dağıtım testleri tam derlemede kalır. Bu tur her
sağlayıcı için yeni bir canlı poz/animasyon matrisi iddia etmez.

- JAR SHA-256: `37a4195d2d4e36c23d7afe61d8c46741879601927fd76ff8358b9c427f6d4e24`
- Test edilen derleyici paketi SHA-256: `28b132858cdb19a45379bc2a039001af6a4a1325c00aee10dbeb5ac2b6e73ed3`
- Java 26.2, Bedrock 1.26.5203.0, Geyser 2.11.3 derleme 1247; yalnızca yerel derleme.

8192 kare bir RGBA atlası sıkıştırmadan önce 256 MiB yer kaplar. Bu düzeneğin beş sayfası toplam
384,5 MiB ham piksel tutar; derleyici/istemci bellek tepe noktası daha yüksek olabilir. Bu Windows
istemcisindeki kabul düşük bellekli cihazları onaylamaz.

Yazarın verdiği boyutları korumak her yüksek çözünürlüklü kaynak texel'ini korumaz: yerel font pikselleri
hâlâ tam sayılı arayüz birimleri kullanır. Kesirli yeniden örnekleme, yatay kenar payları/ilerlemeler,
başlık kırpılması ve renklendirmesi, bağlama göre adlandırılmış fontlar, dinamik HUD yerleşimi ve tam
etkileşimli menü yerleşimi açık kalıyor. Birinci şahıs model pozları ve tam animasyonlu doku oynatımı da
açık. Başarılı bir derleyici çıktısı her kullanım bağlamında aslına uygunluğu garanti etmez.

Yerel glif sayfası yapısı Microsoft'un
[kaynak paketi içerikleri referansı](https://learn.microsoft.com/en-us/minecraft/creator/documents/comprehensivepackcontents?view=minecraft-bedrock-stable)
ve [NhanAZ/glyph](https://github.com/NhanAZ/glyph) (GPL-3.0) ile karşılaştırıldı. Harici kod alınmadı.
Yukarıdaki yerleşim formülü ve çalışma zamanı sınırları Twilight'ın kendi istemci ölçümlerinden gelir.
