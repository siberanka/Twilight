# Pack reconnects, login plugins and large packs - 8 October 2026

> Türkçe: [aşağıda](#türkçe)

twilight-proxy development build 12 was tested on Velocity 4.2.0 and BungeeCord 26.1, each with Geyser 2.11.3 on the
proxy, isolated Paper 26.2 backends (`lobby` with Twilight's 2.2 MiB pack, `survival` with generated load-test
packs of 3 to 152 MiB and up to 6,000 entries, and an `auth` server) and Bedrock 1.26.5203 for Windows. A
stand-in login plugin reproduced what AuthMeVelocity, LibreLogin and similar plugins do: new connections go to
the login server first, no other server is allowed before the login, and the plugin sends the player to the
lobby afterwards. Production servers were not involved.

## Results

| Scenario | development build 11 | development build 12 |
|---|---|---|
| Switch to `survival` (40 MiB) with the login plugin sending reconnected players to `auth` | After the download the proxy set `survival` as the first server, the login plugin refused it, and the player waited without a server until Velocity dropped it after 30 s (`ReadTimeoutException`) | The player joins `auth`, logs in, and the login plugin's move to the lobby becomes a move to `survival`: one login, on `survival` 61 s after the transfer |
| Same with a login plugin that only refuses other servers | The same 30-second hang | The refusal is detected, the player joins the lobby (the login server there), and reaches `survival` after the login, 46 s after the transfer |
| Switch to a 152 MiB pack through Geyser | 172 s, no feedback in the log | 172 s; the log shows the size, the deadline (23 min), the reconnect after 4 s, the arrival and a pack-host hint |
| 40 MiB and 152 MiB packs through the pack host | The download finished, the host closed the idle connection after 5 s, the antivirus web shield in between aborted the hand-over, and Geyser sent the pack again (57 s and 188 s) | The connection stays open until the client closes it: HTTP only, 44 s and 123 s |
| Pack host on an address the client cannot reach | Every join waited about 21 s for the link before Geyser sent the pack | The first join logs why the link failed and the address gets Geyser's transfer for 30 minutes: the next join took 28 s instead of 40 s |
| BungeeCord: `lobby` -> `survival` (10 MiB) -> `lobby` | worked | on `survival` after 22 s, back on `lobby` after 4 s, no repeated reconnects |

A Bedrock client showed "Server not found" in none of these runs. That screen means the client could not reach
the address it was transferred to; the proxy now warns when a player has not come back 60 seconds after a
transfer and names that address (see the [wiki](../WIKI.md#reconnects-login-plugins-and-protections)).

## Changes made during the test

- A reconnected session's first server is only set when no other plugin chose one; a refused first server
  sends the player to the proxy's first server, and the destination is kept until the player's next server
  change, which goes there through a new connection request that every plugin checks.
- A session's first server never causes a reconnect, and `login-servers` lists servers that never do.
- Reconnect deadlines grow with the pack (`transfer-timeout-seconds: auto`); every step is logged.
- Geyser reads an immutable copy of each pack version; new packs are hashed before the first session needs them.
- The pack host keeps connections open after large downloads and remembers addresses whose link did not work.
- Link downloads stop on 60 seconds without data or below 64 KiB/s instead of a fixed timeout.

## Automated checks

214 tests pass (204 Twilight, 10 proxy), among them the reconnect states (login server first, refused first
server, arrival, expiry and the warning for a client that does not come back), deadlines and `login-servers`
parsing, immutable pack versions, the pack host's fallback memory and its connection hand-over times.

---

## Türkçe

### Paket yeniden bağlanmaları, giriş eklentileri ve büyük paketler - 8 Ekim 2026

twilight-proxy geliştirme derlemesi 12; Velocity 4.2.0 ve BungeeCord 26.1 üzerinde, her birinde proxy'de Geyser 2.11.3,
yalıtılmış Paper 26.2 arka uçları (Twilight'ın 2,2 MiB'lik paketiyle `lobby`, 3 ila 152 MiB boyutunda ve 6.000'e
kadar girdili üretilmiş yük testi paketleriyle `survival` ve bir `auth` sunucusu) ve Windows için Bedrock
1.26.5203 ile test edildi. Yerine geçen bir giriş eklentisi AuthMeVelocity, LibreLogin ve benzeri eklentilerin
yaptığını yeniden üretti: yeni bağlantılar önce giriş sunucusuna gider, girişten önce başka sunucuya izin
verilmez ve eklenti oyuncuyu sonrasında lobiye gönderir. Üretim sunucuları kullanılmadı.

#### Sonuçlar

| Senaryo | geliştirme derlemesi 11 | geliştirme derlemesi 12 |
|---|---|---|
| Giriş eklentisi yeniden bağlanan oyuncuları `auth`a gönderirken `survival`a (40 MiB) geçiş | İndirmeden sonra proxy `survival`ı ilk sunucu yaptı, giriş eklentisi bunu reddetti ve oyuncu Velocity onu 30 sn sonra düşürene kadar sunucusuz bekledi (`ReadTimeoutException`) | Oyuncu `auth`a katılır, giriş yapar ve giriş eklentisinin lobiye gönderimi `survival`a gönderime dönüşür: tek giriş, aktarımdan 61 sn sonra `survival`da |
| Yalnızca diğer sunucuları reddeden bir giriş eklentisiyle aynısı | Aynı 30 saniyelik takılma | Ret algılanır, oyuncu lobiye (oradaki giriş sunucusu) katılır ve girişten sonra aktarımdan 46 sn sonra `survival`a ulaşır |
| Geyser üzerinden 152 MiB'lik bir pakete geçiş | 172 sn, günlükte geri bildirim yok | 172 sn; günlük boyutu, süre sınırını (23 dk), 4 sn sonraki yeniden bağlanmayı, varışı ve bir paket sunucusu önerisini gösterir |
| Paket sunucusu üzerinden 40 MiB ve 152 MiB'lik paketler | İndirme bitti, sunucu boştaki bağlantıyı 5 sn sonra kapattı, aradaki antivirüs web kalkanı aktarımı yarıda kesti ve Geyser paketi yeniden gönderdi (57 sn ve 188 sn) | Bağlantı istemci kapatana kadar açık kalır: yalnızca HTTP, 44 sn ve 123 sn |
| İstemcinin ulaşamadığı bir adreste paket sunucusu | Her katılış Geyser paketi gönderene kadar bağlantı için yaklaşık 21 sn bekledi | İlk katılış bağlantının neden çalışmadığını günlüğe yazar ve adres 30 dakika boyunca Geyser'ın aktarımını alır: sonraki katılış 40 sn yerine 28 sn sürdü |
| BungeeCord: `lobby` -> `survival` (10 MiB) -> `lobby` | çalıştı | 22 sn sonra `survival`da, 4 sn sonra yeniden `lobby`de, tekrarlanan yeniden bağlanma yok |

Bu çalıştırmaların hiçbirinde Bedrock istemcisi "Sunucu bulunamadı" göstermedi. Bu ekran istemcinin aktarıldığı
adrese ulaşamadığı anlamına gelir; proxy artık bir oyuncu aktarımdan 60 saniye sonra geri gelmemişse uyarır ve o
adresi yazar ([wiki'ye](../WIKI.md#yeniden-bağlanmalar-giriş-eklentileri-ve-korumalar) bakın).

#### Test sırasında yapılan değişiklikler

- Yeniden bağlanan bir oturumun ilk sunucusu yalnızca başka bir eklenti bir sunucu seçmediyse ayarlanır;
  reddedilen bir ilk sunucu oyuncuyu proxy'nin ilk sunucusuna gönderir ve hedef oyuncunun bir sonraki sunucu
  değişikliğine kadar saklanır; o değişiklik her eklentinin denetlediği yeni bir bağlantı isteğiyle hedefe gider.
- Bir oturumun ilk sunucusu asla yeniden bağlanmaya yol açmaz ve `login-servers` hiç yol açmayan sunucuları listeler.
- Yeniden bağlanma süre sınırları paketle büyür (`transfer-timeout-seconds: auto`); her adım günlüğe yazılır.
- Geyser her paket sürümünün değişmez bir kopyasını okur; yeni paketlerin karması ilk oturum onlara ihtiyaç
  duymadan önce hesaplanır.
- Paket sunucusu büyük indirmelerden sonra bağlantıları açık tutar ve bağlantısı çalışmayan adresleri hatırlar.
- Bağlantı indirmeleri sabit bir zaman aşımı yerine 60 saniye veri gelmeyince veya 64 KiB/s altına düşünce durur.

#### Otomatik denetimler

214 test geçti (204 Twilight, 10 proxy); aralarında yeniden bağlanma durumları (önce giriş sunucusu, reddedilen
ilk sunucu, varış, süre dolması ve geri gelmeyen istemci uyarısı), süre sınırları ve `login-servers`
ayrıştırması, değişmez paket sürümleri, paket sunucusunun geri dönüş hafızası ve bağlantı devir süreleri var.
