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

### Ce que voit Android

**R2. Position.** Position de contenu et position tamponnée sont logiques :
décalage + position dans le flux.

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
transcodage côté serveur (`per_user_limit`). Des sauts rapprochés ne doivent pas
en empiler ; le 429 qui en résulterait relève du chantier 429.

### Le décalage et le cache

**R10. Le décalage voyage dans le marqueur**, comme le rendu depuis la #51 :
`waveflow://track/<id>?format=opus&bitrate=96&offset_ms=133000`.
`RemoteStreamResolver` le relit. Aucun état partagé entre le calcul de la clé de
cache et la construction de l'URL : c'est le motif « vérifier puis agir » que la
#51 a évité par construction.

**R11. Un segment ne touche pas au cache**, ni en lecture ni en écriture. Il ne
s'y range **jamais** sous la clé du morceau entier. Plus tard, éventuellement,
sous une clé qui porte le décalage — pas dans cette première version.

### Répéter, revenir, passer

**R12. Répéter un titre repart du début du morceau**, jamais du début du
segment. Laisser `REPEAT_MODE_ONE` à ExoPlayer pendant un segment rejouerait ses
dernières secondes sans fin — le piège payé par Resonus (`applyLoop`).

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

## Ce que cette première version ne couvre pas

- **La coupure réseau pendant un transcodage.** Media3 reprend à l'octet atteint,
  et le serveur refuse cette plage. La même relance l'y ramènera, au décalage de
  la position logique courante — dans une PR suivante, une fois l'enveloppe en
  place.
- **Le cache des segments** (R11).
- **Le 429 à la relance** (R9) : chantier suivant.

## Comment l'éprouver

Robolectric ne décode pas l'Opus : aucune piste n'y joue, `isPlaying` n'y est
jamais vrai. L'enveloppe s'éprouve donc face à un **lecteur enveloppé factice**
dont on pilote l'état : piste déplaçable ou non, position, durée, identifiants,
fin de flux. Les règles R2 à R14 s'y vérifient une à une. R1 et R5 se vérifient
en plus sur la vraie chaîne service + `MediaController` (voir
`PlaybackServiceQualityTest`) : c'est la session qui doit voir la position
logique, pas seulement l'enveloppe. R10 et R11 s'éprouvent sur la chaîne de
lecture réelle face à un `MockWebServer`, comme `RemoteMediaCacheTest`.

Le jeu réel — un saut entendu au bon endroit, la notification et la voiture qui
suivent — reste **à valider sur appareil**.
