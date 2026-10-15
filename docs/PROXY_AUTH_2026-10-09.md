# Login plugins, forwarding and protections - 9 October 2026

> Türkçe: [aşağıda](#türkçe)

twilight-proxy development build 13 was tested with LeaderOS Auth Plus 1.1.1-siberanka (release build, hashes
checked) against a local stand-in for the LeaderOS web panel, on BungeeCord 26.1 and Velocity 4.2.0, each
with Geyser 2.11.3 on the proxy, three isolated Paper 26.2 backends (`lobby` with Twilight's pack, `survival`
with a 10 MiB load-test pack, `auth_lobby` with LeaderOS Auth on Bukkit) and Bedrock 1.26.5203 for Windows.
Production servers were not involved.

## Stack

| Part | BungeeCord run | Velocity run |
|---|---|---|
| Forwarding | `ip_forward` + BungeeGuard 1.4 on the proxy and every backend | modern forwarding |
| Login | LeaderOS Auth on the proxy and on `auth_lobby` (signed messages, proxy session check, return to the requested server) | LeaderOS Auth on the proxy (LimboAPI 1.1.27 dev build) |
| Bedrock identity | Geyser `auth-type: offline`, and `floodgate` with Floodgate 2.2.5 on the proxy and backends | Geyser `auth-type: offline` |
| Anti-bot | Sonar 2.1.52 did not start on this BungeeCord build (a library error inside Sonar) | Sonar 2.1.52 |

## Results

| Scenario | Result |
|---|---|
| Server change with a valid panel session (BungeeCord) | One reconnect; LeaderOS skipped the auth server, the player reached `survival` 25 s after the transfer |
| Server change without a session, LeaderOS sends the player to the server it asked for after the login | One reconnect and one login: held on `auth_lobby`, then on `survival` 24 s after the transfer; no later bounce to the lobby |
| The same, destination is the default server (send-after-auth) | On `lobby` 26 s after the transfer |
| The same with LeaderOS's return disabled, so it sends the player to the lobby | twilight-proxy sent the player on to `survival` with a new connection request: one reconnect, 27 s |
| Floodgate player (`.SiberAnka`) with BungeeGuard | One reconnect, on `survival` 24 s after the transfer |
| BungeeCord sends a returning player to its last server instead of the expected one | Reconnected once, five seconds after it was in game, for that server's pack; before, the client ignored a transfer sent while it was still loading |
| Velocity with LeaderOS (LimboAPI login with a session) and Sonar | `survival` 23 s and `lobby` 6 s after the transfers |
| Stand-in login plugin forcing a login server / refusing other servers (Velocity) | One login, then on the requested server (see the [8 October test](PROXY_RECONNECT_2026-10-08.md)) |

## Defects found and fixed

- On BungeeCord, LeaderOS Auth changes the target of every unauthenticated connection after all other
  plugins (priority 127). twilight-proxy counted the reconnect as arrived when it set the destination, so
  after the login the move to the lobby caused a second reconnect. Arrival now counts only once the player
  is connected to its server; any other server holds the reconnect until the next server change.
- A fresh session whose first server differed from the expected one kept the wrong pack. It is now
  reconnected once for that server's pack, after the client is in game.
- Lookups of a Bedrock session by name (needed while BungeeCord chooses the first server) now also require
  the address the session plays from, and a join address is only used for a transfer when it is a plain host
  name or IP address.

## Observations about other plugins

- LeaderOS Auth on Velocity with the LimboAPI dev build and Minecraft 26.2: a Bedrock player's `/login`
  in the limbo was not received, so only the session path could be tested there; Bukkit login worked.
- Sonar 2.1.52 failed to start on the test BungeeCord build; on Velocity it ran and did not interfere.
- SafeNET could not be obtained; like other anti-bot tools it must let a client reconnect a few seconds
  after leaving, which the proxy log reports when it does not happen.

## Automated checks

215 tests pass (204 Twilight, 11 proxy), among them arrival on connection, a login plugin that changes
the target after everyone, refused redirects, name lookups from another address, and transfer host checks.

---

## Türkçe

### Giriş eklentileri, yönlendirme ve korumalar - 9 Ekim 2026

twilight-proxy geliştirme derlemesi 13; LeaderOS Auth Plus 1.1.1-siberanka (karmaları denetlenmiş sürüm derlemesi) ile,
LeaderOS web panelinin yerel bir yerine geçeniyle, BungeeCord 26.1 ve Velocity 4.2.0 üzerinde test edildi.
Her birinde proxy'de Geyser 2.11.3, yalıtılmış üç Paper 26.2 arka ucu (Twilight paketiyle `lobby`, 10 MiB'lik
yük testi paketiyle `survival`, Bukkit'te LeaderOS Auth bulunan `auth_lobby`) ve Windows için Bedrock 1.26.5203
vardı. Üretim sunucuları kullanılmadı.

#### Yapı

| Parça | BungeeCord çalıştırması | Velocity çalıştırması |
|---|---|---|
| Yönlendirme | proxy'de ve her arka uçta `ip_forward` + BungeeGuard 1.4 | modern yönlendirme |
| Giriş | proxy'de ve `auth_lobby`de LeaderOS Auth (imzalı mesajlar, proxy oturum denetimi, istenen sunucuya dönüş) | proxy'de LeaderOS Auth (LimboAPI 1.1.27 geliştirme derlemesi) |
| Bedrock kimliği | Geyser `auth-type: offline` ve proxy ile arka uçlarda Floodgate 2.2.5 ile `floodgate` | Geyser `auth-type: offline` |
| Anti-bot | Sonar 2.1.52 bu BungeeCord derlemesinde başlamadı (Sonar içindeki bir kütüphane hatası) | Sonar 2.1.52 |

#### Sonuçlar

| Senaryo | Sonuç |
|---|---|
| Geçerli panel oturumuyla sunucu değişikliği (BungeeCord) | Tek yeniden bağlanma; LeaderOS auth sunucusunu atladı, oyuncu aktarımdan 25 sn sonra `survival`a ulaştı |
| Oturumsuz sunucu değişikliği, LeaderOS girişten sonra oyuncuyu istediği sunucuya gönderir | Tek yeniden bağlanma ve tek giriş: `auth_lobby`de bekletildi, sonra aktarımdan 24 sn sonra `survival`da; ardından lobiye geri sekme yok |
| Aynısı, hedef varsayılan sunucu (send-after-auth) | Aktarımdan 26 sn sonra `lobby`de |
| LeaderOS'un geri dönüşü kapalıyken aynısı, oyuncuyu lobiye gönderir | twilight-proxy oyuncuyu yeni bir bağlantı isteğiyle `survival`a gönderdi: tek yeniden bağlanma, 27 sn |
| BungeeGuard ile Floodgate oyuncusu (`.SiberAnka`) | Tek yeniden bağlanma, aktarımdan 24 sn sonra `survival`da |
| BungeeCord geri dönen bir oyuncuyu beklenen yerine son sunucusuna gönderir | Oyuna girdikten beş saniye sonra o sunucunun paketi için bir kez yeniden bağlandı; önceden istemci hâlâ yüklenirken gönderilen aktarımı yok sayıyordu |
| LeaderOS (oturumlu LimboAPI girişi) ve Sonar ile Velocity | Aktarımlardan 23 sn sonra `survival`, 6 sn sonra `lobby` |
| Giriş sunucusunu zorlayan / diğer sunucuları reddeden yerine geçen giriş eklentisi (Velocity) | Tek giriş, ardından istenen sunucuda ([8 Ekim testine](PROXY_RECONNECT_2026-10-08.md) bakın) |

#### Bulunan ve düzeltilen hatalar

- BungeeCord'da LeaderOS Auth, giriş yapmamış her bağlantının hedefini diğer bütün eklentilerden sonra
  değiştirir (öncelik 127). twilight-proxy yeniden bağlanmayı hedefi ayarladığı anda varmış sayıyordu; bu
  yüzden girişten sonra lobiye taşınma ikinci bir yeniden bağlanmaya yol açtı. Varış artık yalnızca oyuncu
  sunucusuna bağlandığında sayılır; başka herhangi bir sunucu yeniden bağlanmayı bir sonraki sunucu
  değişikliğine kadar bekletir.
- İlk sunucusu beklenenden farklı olan yeni bir oturum yanlış paketi tutuyordu. Artık istemci oyuna girdikten
  sonra o sunucunun paketi için bir kez yeniden bağlanır.
- Bir Bedrock oturumunun adla aranması (BungeeCord ilk sunucuyu seçerken gerekir) artık oturumun oynadığı
  adresi de gerektirir; bir katılma adresi yalnızca düz bir alan adı veya IP adresiyse aktarımda kullanılır.

#### Diğer eklentilerle ilgili gözlemler

- Velocity'de LimboAPI geliştirme derlemesi ve Minecraft 26.2 ile LeaderOS Auth: limbodaki bir Bedrock
  oyuncusunun `/login` komutu alınmadı; bu yüzden orada yalnızca oturum yolu test edilebildi; Bukkit girişi
  çalıştı.
- Sonar 2.1.52 test BungeeCord derlemesinde başlamadı; Velocity'de çalıştı ve araya girmedi.
- SafeNET temin edilemedi; diğer anti-bot araçları gibi bir istemcinin ayrıldıktan birkaç saniye sonra
  yeniden bağlanmasına izin vermelidir; bu olmadığında proxy günlüğü bunu bildirir.

#### Otomatik denetimler

215 test geçti (204 Twilight, 11 proxy); aralarında bağlantıda varış, hedefi herkesten sonra değiştiren bir
giriş eklentisi, reddedilen yönlendirmeler, başka adresten adla aramalar ve aktarım adresi denetimleri var.
