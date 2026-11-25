# Se déplacer dans un morceau transcodé

Règles arrêtées **avant le code**, à la demande de l'utilisateur (11/09). Chaque
règle dit ce qu'on doit observer, pour pouvoir s'éprouver par un test. Une règle
qui change se change ici d'abord.

Ce qui est dit de Media3 a été lu dans les sources de la 1.11.0.

## Le problème

Un transcodage en direct n'a ni longueur ni plages : le serveur répond
`Accept-Ranges: none` et refuse en 416 toute plage qui ne part pas du premier
octet (`waveflow-server`, `src/media.rs`). ExoPlayer, lui, se déplace par plages.

Ce qui en découle aujourd'hui :

- **Media3 tient la piste pour non déplaçable, et sans durée.** Faute de longueur,
  l'extracteur Ogg pose un `UnseekableOggSeeker`, dont la `SeekMap` est
  `Unseekable(C.TIME_UNSET)` (`StreamReader`).
- **Le curseur est désactivé.** Sans durée, `Media3PlaybackController` publie
  `durationMs = 0`, et le curseur de `NowPlayingScreen` porte
  `enabled = hasDuration`.
- **La notification et la voiture ne peuvent pas se déplacer non plus.**
  `Util.getAvailableCommands` n'accorde `COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM` qu'à
  une piste déplaçable.

Le serveur offre l'autre voie : `offset_ms`, un décalage **temporel**. Il relance
ffmpeg avec `-ss` et sert le reste du morceau à partir de là. Il le refuse sur
`raw` (422) et au-delà de la durée de la piste (422), et ne sert jamais un flux
décalé depuis son cache.

## Le principe : une timeline logique

> Le flux reçu peut commencer à 2:13. Pour le reste d'Android, le morceau commence
> toujours à 0:00 et dure sa durée entière.

Vocabulaire :

- **flux** : ce qu'ExoPlayer lit réellement ;
- **segment** : un flux demandé avec `offset_ms > 0` ;
- **décalage** : l'instant du morceau où le flux commence, `0` hors segment ;
- **position logique** : décalage + position dans le flux ;
- **relance** : remplacer le flux de la piste courante par un autre, à un autre
  décalage.

## Les règles

### Un seul endroit

**R1. La correction vit dans une enveloppe, et tout le service lui parle.** Un
`ForwardingSimpleBasePlayer` enveloppe l'ExoPlayer. La session, et tout ce que
`PlaybackService` branche sur le lecteur — historique, boucle A-B, minuterie,
vitesse — reçoivent l'enveloppe, **jamais l'ExoPlayer**. Seuls l'enveloppe et la
chaîne de lecture connaissent le décalage.

`ForwardingSimpleBasePlayer` plutôt que `ForwardingPlayer` : on corrige un
**état** (`getState()`), et les événements que voient les contrôleurs en sont
déduits par Media3. Avec `ForwardingPlayer`, il faudrait corriger chaque getter
**et** chaque événement, et l'oubli d'un seul ramène 0:00 dans la notification —
le ticket #135 de Resonus.

Mais `ForwardingSimpleBasePlayer` **transmet** beaucoup tel quel, et ce qu'il
transmet raisonne sur l'ExoPlayer. R5 et R13 disent ce qui doit être réécrit.

Et la timeline se corrige **en enveloppant celle de l'ExoPlayer**
(`ForwardingTimeline`), jamais en reposant la liste de lecture sous forme de
liste (`setPlaylist(List)`). Celle-ci fabrique une `PlaylistTimeline`, qui ignore
l'ordre aléatoire — « TODO: Support shuffle order » dans la 1.11 : « suivant »,
« précédent » et l'état des boutons de la notification suivraient alors l'ordre
de la file, lecture aléatoire ou non.

### Ce que voit Android

**R2. Position.** Position de contenu et position tamponnée sont logiques :
décalage + position dans le flux. Deux gardes :

- une position **inconnue** (`C.TIME_UNSET`) reste inconnue — on n'y ajoute pas
  le décalage, ce qui publierait un instant plausible et faux ;
- une position logique reste dans `[0, durée]`. Un flux transcodé peut durer
  quelques millisecondes de plus que la durée du catalogue : la dépasser ferait
  afficher une position au-delà de la fin.

**R3. Durée.** La durée exposée est celle du catalogue quand un segment joue, ou
quand le flux n'en annonce aucune — c'est le cas de tout transcodage en direct.
`RemoteSong.durationMs` existe mais `toMediaItem()` ne la transmet pas : elle
voyagera dans `MediaMetadata.durationMs`.

**R4. Déplaçable.** Une piste distante transcodée est annoncée déplaçable, pour
que la commande de saut existe pour tous les contrôleurs — application,
notification, Android Auto.

**R5. La relance n'est pas un changement de piste.** Ni la session ni les
écouteurs du service ne voient de transition.

Deux raisons de l'écrire :

- `ListeningCounter.trackChanged` remet son compte à zéro à **chaque** appel,
  même pour la même piste. Une relance vue comme une transition ferait compter
  deux fois un morceau déjà écouté.
- Relancer, c'est remplacer la piste courante de l'ExoPlayer par un marqueur
  différent. `ExoPlayerImpl.replaceMediaItems` ne sait pas mettre à jour une
  source dont l'URI change : il retire l'ancienne et ajoute la nouvelle, d'où un
  **nouvel identifiant de piste** et un saut de raison `DISCONTINUITY_REASON_REMOVE`.
  `SimpleBasePlayer` déduit les transitions de ces identifiants. L'enveloppe
  garde donc à la piste relancée **l'identifiant qu'elle avait**, et présente le
  saut comme un déplacement (`DISCONTINUITY_REASON_SEEK`) à la position logique.
  Rattacher l'identifiant juste après le remplacement suffit : Media3 livre
  `onEvents` par un message posté, et l'état n'est relu qu'ensuite.

**R6. Pendant la relance, la position est déjà la cible.** Le décalage est posé
avant de relancer : le curseur ne revient pas en arrière le temps que le flux
arrive.

### Quand relancer

**R7. Natif quand ExoPlayer le peut, relance sinon.** Un saut se fait nativement
si la piste courante du lecteur enveloppé est déplaçable — l'original, un
transcodage entier déjà en cache. Sinon, relance avec `offset_ms`. Une fois un
segment en cours, **tout** saut est une relance : le segment n'a pas de longueur
non plus.

**R8. Jamais de `offset_ms` hors transcodage**, et un décalage borné à
`[0, durée[` — le serveur refuse le reste en 422. Un saut à la toute fin se
traite comme la fin du morceau.

**R9. Seul le dernier saut compte.** Chaque relance occupe un créneau de
transcodage côté serveur (`per_user_limit`), et un flux quitté ne le rend pas
sur-le-champ : le serveur ne s'aperçoit du départ qu'au tour de lecture suivant.
Des sauts rapprochés ne doivent donc pas en empiler — c'est précisément ce qui
déclenche le 429 (R17 et suivantes).

Un saut qui demande une relance **attend 300 ms avant de partir**. Un autre saut
dans l'intervalle prend sa place et relance l'attente : une rafale ne produit
qu'**une** demande au serveur, à la cible du dernier saut. Ce qu'on doit
observer :

- pendant l'attente, la position annoncée est déjà la cible (R6), et un
  « avancer » ou « reculer » de plus se calcule depuis elle — cinq pressions sur
  « +10 s » partent ensemble, à +50 s ;
- un saut natif (R7), un changement de piste — la répétition d'un titre en est
  un —, ou une piste qui n'est plus la courante quand l'attente échoit, annulent
  la relance en attente ;
- la cible annoncée est **constante** : l'ancien flux qui joue encore ne la fait
  pas dériver ;
- si le lecteur s'est arrêté **sur une erreur** — le dernier refus d'une liaison
  facturée, avant le geste ou pendant l'attente —, le saut le prépare de
  nouveau, qu'il soit natif ou relancé : sans cela il resterait sans suite, et
  revenir au début, natif, ne ferait pas ce que fait un saut plus loin. Un arrêt
  demandé (`stop`)
  se respecte, même après une panne : le lecteur en garde l'erreur, et
  l'enveloppe retient donc elle-même d'où vient l'arrêt ;
- les relances que l'enveloppe décide seule — la répétition d'un segment (R12),
  le marqueur rendu nu à la piste quittée (R14) — ne sont pas retardées : ce ne
  sont pas des sauts.

300 ms, c'est le délai qu'Android lui-même laisse entre deux touchers d'un même
geste (`ViewConfiguration.getDoubleTapTimeout`). **Le prix est accepté :** tout
saut dans un transcodage part 300 ms plus tard, et pendant ces 300 ms l'ancien
flux continue de s'entendre alors que le curseur montre déjà la cible. Un saut
natif ne paie rien. Chaque tour de la boucle A-B sur un transcodage (R15) paie
ces 300 ms aussi. Le curseur de l'écran ne saute qu'au lâcher
(`onValueChangeFinished`) : un glissement n'est qu'un saut, la rafale vient des
pressions répétées — boutons d'un casque, d'un volant, d'un contrôleur.

### Le décalage et le cache

**R10. Le décalage voyage dans le marqueur**, comme le rendu depuis la #51 :
`waveflow://track/<id>?format=opus&bitrate=96&offset_ms=133000`.
`RemoteStreamResolver` le relit. Aucun état partagé entre le calcul de la clé de
cache et la construction de l'URL : c'est le motif « vérifier puis agir » que la
#51 a évité par construction.

**R11. Un segment contourne `CacheDataSource`**, avant toute lecture et toute
écriture. Une clé distincte ou une écriture interdite ne suffisent pas : le cache
est posé **avant** le résolveur, et un segment qui s'y présenterait sous la clé du
morceau entier se verrait servir les octets du morceau déjà en cache — l'audio
repartirait de 0:00 pendant que l'enveloppe publierait la position demandée. La
chaîne aiguille donc un marqueur porteur de `offset_ms` directement vers le
résolveur. Mettre les segments en cache, sous une clé qui porte le décalage,
viendra éventuellement plus tard — pas dans cette première version.

### Répéter, revenir, passer

**R12. Répéter un titre repart du début du morceau**, jamais du début du
segment. Laisser `REPEAT_MODE_ONE` à ExoPlayer pendant un segment rejouerait ses
dernières secondes sans fin — le piège payé par Resonus (`applyLoop`).

Toute relance de la piste courante suspend la répétition et l'ordre aléatoire
le temps du remplacement. L'ExoPlayer remplace une source dont l'URI change en
ajoutant la nouvelle juste après l'ancienne, puis en retirant l'ancienne ; et
retirer la piste courante lui fait chercher la suivante, selon la répétition et
l'ordre aléatoire. Sous `REPEAT_MODE_ONE`, c'était la piste elle-même, retirée :
il s'arrêtait (`STATE_ENDED`). En aléatoire, c'était la piste tirée après elle :
il changeait de morceau. Les deux constatés face à un vrai ExoPlayer 1.11.1.
Suspendus, la suivante est la nouvelle source.

**L'ordre aléatoire tiré survit au remplacement (#86).** L'ajout place la
nouvelle source à un rang tiré au hasard dans l'ordre aléatoire — que l'aléatoire
soit actif ou suspendu : ce qui suivait la piste relancée ne la suivait plus, et
ce qui la précédait non plus. L'enveloppe relit donc l'ordre de l'ExoPlayer avant
le remplacement et le repose après (`setShuffleOrder`) : un remplacement ne change
ni le nombre de pistes ni leur index, l'ordre d'avant vaut tel quel. Cela vaut
pour **toute** piste remplacée, courante ou non : la piste quittée qui retrouve
son marqueur nu (R14) ou son rendu (R22) dérangeait l'ordre de la même façon.
Reposer l'ordre ne dispense pas de suspendre l'aléatoire : la suivante se choisit
pendant le remplacement, avant que l'ordre ne puisse être reposé.

**R13. « Précédent », « reculer » et « avancer » jugent sur la position
logique.** `ForwardingSimpleBasePlayer.handleSeek` les transmet tels quels
(`seekToPrevious`, `seekBack`, `seekForward`), et l'ExoPlayer les calcule sur sa
position **brute** : à 2:13 d'un segment commencé à 2:10, il se croirait à 0:03
et reculerait d'une piste au lieu de revenir au début. L'enveloppe les réécrit en
sauts calculés sur la position logique — `maxSeekToPreviousPositionMs` pour
« précédent », les incréments pour « reculer » et « avancer ». Revenir au début,
c'est un décalage `0`.

**R14. Changer de piste remet le décalage à zéro.** Un segment n'est jamais
reposé sur une autre piste, ni sur la même piste rejouée depuis la file.

### Ce qui lit une position

**R15. La boucle A-B lit et rembobine par l'enveloppe.** Sur un transcodage,
chaque tour de boucle est une relance : une latence audible, **acceptée** dans
cette première version.

**R16. Toute reprise de position passera par l'enveloppe.** Il n'en existe aucune
aujourd'hui (pas de `onPlaybackResumption`). Celle qui viendra passera par un
saut de l'enveloppe, et non par une position de départ posée sur l'ExoPlayer.

### Quand le serveur refuse : le 429

Règles ajoutées le 24/11 pour l'issue #57. Le contrat est celui du serveur
(`waveflow-server`, `docs/guides/api-v2-guide.md`, « When a transcode is
refused ») : honorer `Retry-After`, avec gigue et un nombre d'essais borné ; ne
redescendre vers l'original qu'une fois les essais épuisés, seulement si la
liaison le porte, et le dire à l'écran. Elles valent pour **toute** demande de
transcodage : le début d'une piste comme la relance après un saut.

**R17. Le refus tombe sur le flux, jamais sur le ticket.** `POST
/api/v2/tracks/{id}/stream-ticket` ne transcode rien et n'est pas limité ; c'est
`GET /api/v2/stream/<ticket>` qui répond `429` avec `Retry-After: 1`
(`src/media/mod.rs`), **avant le premier octet** — le serveur prend ses créneaux
(`listener_slots`) avant de lancer ffmpeg. Il n'existe donc pas de 429 en plein
flux, et ce n'est pas le résolveur qui le voit : c'est la source HTTP de Media3.
Il se traite là où Media3 décide déjà de retenter un chargement, dans une
`LoadErrorHandlingPolicy` posée sur la fabrique de sources du lecteur.

Aujourd'hui, la politique par défaut de Media3 retente déjà un 429, mais sans
lire l'en-tête : immédiatement, puis à 1 s et 2 s, et la lecture échoue au
quatrième refus. L'écran dit alors « Serveur injoignable », ce qui est faux.

**R18. Le délai est celui qu'annonce le serveur.** `Retry-After` en secondes. Absent
ou illisible — une date HTTP comprise —, une seconde : la valeur que le serveur
envoie toujours. Borné à 30 s, garde-fou contre un en-tête aberrant : au-delà,
l'application paraîtrait figée.

**R19. Plus une gigue.** Un tirage uniforme entre 0 et la moitié du délai,
**toujours ajouté**, jamais retranché : revenir avant l'heure, c'est se faire
refuser à coup sûr. Sans gigue, dix appareils refusés à la même seconde
reviendraient ensemble et se refuseraient de nouveau.

**R20. Trois relances au plus**, soit quatre demandes : de 3 à 4,5 s d'attente
avec `Retry-After: 1`. Le compte vaut **par demande de transcodage** — chaque
relance après un saut est une source neuve, avec ses propres essais.

Trois, c'est le seuil où Media3 rend une erreur de chargement au lecteur, et il
ne se relève pas pour le seul 429 : le seuil vaut pour toute panne, et le
compte qu'il juge n'est remis à zéro qu'**après** la relance d'un flux qui avait
progressé. Le relever ferait attendre une coupure réseau plus longtemps ; y
renoncer de soi-même à son quatrième échec la rendrait fatale là où Media3
l'aurait relancée. Une erreur qui n'est pas un 429 garde donc **exactement** le
comportement de Media3, délais et seuil.

**R21. Un saut pendant l'attente l'abandonne.** Le flux qu'on attendait n'est plus
celui qu'on veut : la relance remplace la source, et le chargement en attente
part avec elle (R9). On ne retente jamais un flux quitté.

**R22. Les essais épuisés, l'original — seulement sur une liaison non
facturée.** `NET_CAPABILITY_NOT_METERED` sur le réseau actif : le Wi-Fi ou
l'Ethernet ordinaires, pas un Wi-Fi déclaré payant ni les données mobiles.
Liaison facturée, ou impossible à connaître : pas de repli, l'échec remonte.
L'original pèse plusieurs fois le transcodage refusé ; le servir en données
mobiles coûterait plus cher que d'échouer.

Le repli remplace le flux de la **piste courante** par l'original, **à la position
logique** où elle en était, et **sans changement de piste** : même identité,
comme une relance (R5) — sans quoi l'historique compterait deux fois le morceau.
L'original se déplace ensuite nativement (R7). La piste suivante redemande son
propre rendu : la saturation est passagère, la qualité choisie reste la règle.

Le repli ne vaut que **pour l'écoute en cours**. La piste quittée retrouve le
rendu qu'elle avait, comme elle retrouve son marqueur nu (R14) ; répétée (R12),
elle le redemande depuis le début. Sans cela, « précédent » ou la file rejouée
la serviraient en original — en données mobiles aussi.

**R23. Le repli se décide avant que l'erreur n'atteigne la session.** Sinon
l'écran dirait « injoignable » puis jouerait. Le service écoute l'erreur sur
l'ExoPlayer, et la répare dans le même appel ; l'enveloppe ne relit l'état
qu'ensuite (`onEvents` arrive par un message posté, voir R5), et n'y trouve plus
d'erreur.

**R24. Tout se dit à l'écran.** Le repli, par le canal des erreurs du lecteur —
le même message bref qu'une panne, sans interrompre la lecture : « Serveur
saturé : lecture en qualité d'origine. » L'échec sans repli se dit pour ce qu'il
est, « serveur occupé », et non « injoignable » : ce n'est pas la même panne, ni
le même geste à suggérer.

## Ce que cette première version ne couvre pas

- **La coupure réseau pendant un transcodage.** Media3 reprend à l'octet atteint,
  et le serveur refuse cette plage. La même relance l'y ramènera, au décalage de
  la position logique courante — dans une PR suivante, une fois l'enveloppe en
  place.
- **Le cache des segments** (R11).

## Comment l'éprouver

Robolectric ne décode pas l'Opus : aucune piste n'y joue, `isPlaying` n'y est
jamais vrai. L'enveloppe s'éprouve donc face à un **lecteur enveloppé factice**
dont on pilote l'état : piste déplaçable ou non, position, durée, identifiants,
fin de flux. Les règles R2 à R8 et R10 à R14 s'y vérifient une à une. **R1 se vérifie en plus sur la
vraie chaîne** service + `MediaController` (`PlaybackServiceSeekTest`) : c'est la
session qui doit annoncer la durée du catalogue et la commande de saut, pas
seulement l'enveloppe. R10 et R11 s'éprouvent sur la chaîne de
lecture réelle face à un `MockWebServer`, comme `RemoteMediaCacheTest`.

**R9 et R17 à R24 s'éprouvent sur la vraie chaîne de lecture**
(`TranscodeRefusalChainTest`) : ExoPlayer, cache, résolveur, enveloppe et repli,
assemblés par la même fonction que le service (`assemblerLeLecteur`), face à un
`MockWebServer` qui refuse comme le serveur. Chaque demande reçue note l'heure de
l'horloge du lecteur, que le test seul fait avancer : c'est elle qui dit si le
lecteur a attendu le délai annoncé, combien de fois il a redemandé, et quand
l'original est venu. Des sauts rapprochés s'y comptent en demandes reçues.
Ce qui suppose une fin de flux — l'original d'un repli qui se répète — ou un
refus tombé à un instant précis — pendant les 300 ms d'un saut en attente —
s'éprouve face au lecteur factice (`TranscodeSeekingPlayerTest`), qui appelle le
repli lui-même.
L'ordre aléatoire après un remplacement (R12, R14) s'éprouve sur la même
chaîne, `TranscodeRelaunchChainTest` : le lecteur factice remplace une piste sur
place et ne peut pas le déranger. L'ExoPlayer tire son ordre sans graine ; une
file de trente pistes et trois remplacements par test ramènent à moins d'un sur
mille la chance qu'un ordre dérangé retombe sur l'ancien.
`TranscodeRefusalPolicyTest` éprouve les bornes de la politique sur des refus
**levés par la vraie source HTTP** — c'est ainsi qu'est apparue la clé `null` que
`HttpURLConnection` donne à la ligne de statut, et qu'un refus construit à la
main aurait cachée.

Ce qui n'est **pas** éprouvé :

- **R21 n'a pas de garde à nous** : c'est Media3 qui abandonne le chargement
  d'une source remplacée. Le test le constate, il ne protège aucun code d'ici.
- **Le saut en attente quand le lecteur est relâché, ou quand le segment finit
  et se répète (R12)** : les deux annulations sont posées, aucun test ne les
  vise.
- **La liaison au moment du refus** : le test injecte la réponse ; seule la
  lecture des capacités du réseau est éprouvée à part (`UnmeteredLinkTest`),
  sur les ombres de Robolectric.
- **Un 429 sur la piste suivante, pendant son préchargement** : le repli vise la
  piste courante, ce qui suppose qu'ExoPlayer ne rende l'erreur d'une piste
  qu'une fois celle-ci devenue la courante. C'est ce que disent ses sources
  (`ExoPlayerImplInternal.handleIoException`, 1.11.1) ; aucun test ne le montre.

Le jeu réel — un saut entendu au bon endroit, la notification et la voiture qui
suivent, l'original qui prend le relais d'un transcodage refusé — reste **à
valider sur appareil**, face à un serveur dont on aura abaissé `per_user_limit`.
