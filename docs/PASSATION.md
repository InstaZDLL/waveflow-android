# Passation

Document vivant : chaque agent qui prend la suite le relit d'abord, et le met à
jour avant de partir. Il dit **où en est le chantier et ce qui vient ensuite** —
pas l'historique, que `git log` raconte mieux.

Dernière mise à jour : **2026-09-11**, sur `main` = `d6ec06d`.

## État du dépôt

- `main` = `d6ec06d`, arbre propre, **aucune PR ouverte**, aucune branche en
  cours.
- **426 tests verts**, CI verte (workflow `Build & test`, ~4 min 45 s).
- **Aucun avertissement de compilation.** C'est une propriété qu'on tient, pas un
  hasard — voir le piège `textReport` plus bas avant d'en supprimer un.
- Gradle 9.7.1, AGP 9.4.0, OkHttp 5.5.0, media3 1.11.0.
- Baseline Detekt : 16 entrées. **Elle ne doit que rétrécir.**

## Le chantier : la refonte v2

Six lots, dans cet ordre :

1. Réglages / DataStore — **fait**
2. Navigation + identité — **fait**
3. Lecteur : file d'attente, minuterie, vitesse, boucle A-B — **fait**
4. Transcodage (remonté du 6ᵉ rang : meilleur rapport travail/effet, le serveur
   est déjà prêt) — **entamé** : le choix de la qualité est fait (#51), reste à
   pouvoir se déplacer dans un morceau transcodé
5. Paroles
6. Audio avancé (EQ, ReplayGain, gapless, sortie)

Le fondu enchaîné **est hors plan** : Media3 ne le fournit pas, il faudrait deux
lecteurs ou une chaîne audio maison.

## Ce que la dernière session a livré

**PR #51 — choisir la qualité de lecture (fusionnée le 11/09).**

Trois profils sous *Réglages ▸ Serveur*, au-dessus du cache : *Qualité
d'origine* (le défaut), *Haute qualité* (Opus 160 kbit/s) et *Économie* (Opus
96 kbit/s). L'original garde le marqueur et la clé de cache d'avant : qui ne
touche à rien ne perd pas son cache.

**Le rendu voyage dans la piste, pas dans les préférences.** `CacheDataSource`
calcule sa clé *avant* que le résolveur ne construise l'URL ; lire le réglage aux
deux bouts laisserait un changement s'intercaler, et une version Opus se
rangerait sous la clé de l'original. `withRendering` réécrit donc **ensemble** le
marqueur (`waveflow://track/<id>?format=opus&bitrate=96`) et la clé, et
`RemoteStreamResolver` relit le marqueur. C'est le motif « vérifier puis agir »,
évité cette fois par construction plutôt que corrigé après coup.

**C'est le service qui pose le rendu**, dans `BrowseCallback.addMediaItems`,
comme il applique déjà la vitesse — une fois par file, pour qu'un album ne se
partage pas entre deux qualités. Au démarrage à froid, la file **attend** la
lecture du réglage plutôt que de prendre le défaut. L'attente vit dans
`firstValueAsFuture`, dont le futur se termine toujours : la revue a relevé qu'un
service détruit pendant l'attente le laissait en suspens, et la réponse que
Media3 attend de la session avec lui.

**Les profils transcodés se grisent sur un serveur sans ffmpeg — cas qui ne se
présente pas.** `waveflow-server` refuse de démarrer sans ffmpeg, si bien que
`GET /api/v2/transcode/status` rend toujours `available: true`. Le grisé suit le
contrat de l'API et porte un état aujourd'hui inatteignable.

Un test de bout en bout pour l'original, suggéré en revue, a été **écarté** : il
passerait la chaîne débranchée, `toMediaItem()` produisant déjà le marqueur et la
clé de l'original. CodeRabbit en a convenu.

**Ce qui n'a pas été fait :** se déplacer dans un morceau transcodé — voir « La
suite » —, le 429, le profil *Automatique*, et re-rendre la file en cours quand
on change de qualité ; l'écran dit que le réglage s'applique aux pistes lancées
ensuite. **Rien n'a tourné sur un appareil** : Robolectric ne décode pas l'Opus,
le fait qu'une piste transcodée *se joue* n'est pas prouvé.

**PR #50 — la boucle A-B (fusionnée le 11/09).**

Media3 n'a pas de « répéter entre deux points » : `REPEAT_MODE_ONE` reprend la
piste entière. On échantillonne donc la position et on rembobine soi-même, dans
le **service** — on pose une boucle pour repiquer un passage, puis on éteint
l'écran et on prend son instrument.

`AbLoop` porte les bornes sans connaître le lecteur, comme `SleepTimer`.
`AbLoopRunner` **reçoit** la position et le rembobinage au lieu de les prendre
sur un `Player` : c'est ce qui le rend éprouvable sur la JVM, là où Robolectric
ne peut rien montrer faute de codec. Le sommeil se règle sur ce qui reste avant
B, borné des deux côtés.

**Rien n'est échantillonné quand rien ne joue** — ajouté en revue. En pause, la
position reste sous B : la surveillance réveillait le service toutes les 500 ms,
toutes les 50 ms si l'on avait mis en pause juste avant la borne. La revue
proposait d'injecter un `Player` et un `Player.Listener` dans le runner ; c'était
défaire ce qui le rend éprouvable. L'état de lecture entre donc par la même
porte que la position — un `StateFlow<Boolean>` que le service alimente depuis
`onIsPlayingChanged` — et un `combine` le place dans le `collectLatest` existant :
à la pause, la surveillance n'attend pas, elle est **annulée**, puis relancée à la
reprise. La garde relue après chaque sommeil (`enVigueur`) lit les deux
conditions ensemble.

**Le menu de débordement annoncé n'a pas été nécessaire** — voir plus bas, la
note sur l'en-tête a été corrigée.

**Ce qui n'a pas été fait :** les bornes ne sont pas dessinées sur la barre de
progression. Seuls la teinte du bouton et sa description disent qu'une boucle
court. Les marquer demanderait une piste de `Slider` personnalisée ; c'est le
prolongement naturel si l'usage le réclame.

**PR #48 — la vitesse de lecture.**

Elle vit dans les **préférences**, et c'est le **service** qui les observe pour
l'appliquer au lecteur — comme il écoute déjà les expirations de la minuterie.
Ni l'écran ni le `PlaybackController` n'y touchent. Deux raisons, et la seconde
est la vraie : la vitesse se persiste, et une lecture démarrée sans écran ouvert
— Android Auto, la notification — doit partir à la bonne vitesse. Un réglage
posé par l'interface retomberait à ×1 précisément là où on ne peut pas le
corriger.

L'affichage suit le même chemin : `PlayerUiState.playbackSpeed` vient du flux
des préférences, non de `PlaybackState`. C'est ce qui **désamorce le piège n° 1**
plutôt que de le contourner — le flux des préférences émet au moment du choix, y
compris en pause, où l'on règle justement sa vitesse. Le bouton de l'en-tête
*est* son affichage : il porte le chiffre, pas une icône.

Le bornage est double, à l'écriture **et** à la relecture. Le second protège
l'application d'un fichier écrit par une version future aux bornes plus larges ;
le premier protège le fichier lui-même. Ils s'éprouvent séparément — voir plus
bas.

**Ce qui n'a pas été fait :** le mini-player ne dit pas la vitesse. Elle n'est
visible qu'une fois le lecteur déplié. À revoir si quelqu'un s'y perd.

**Trois défauts du magasin de préférences, relevés en revue et antérieurs à
cette PR.** La vitesse les a mis en lumière en ajoutant une seconde clé typée ;
tous trois valaient déjà pour le thème seul.

1. **Les écritures ne retenaient pas leurs erreurs.** `setTheme` comme
   `setPlaybackSpeed` laissaient remonter une `IOException` dans la portée du
   ViewModel, qui n'a pas de gestionnaire : un disque plein emportait
   l'application pour un réglage d'apparence. La retenue est désormais partagée
   par `ecrire`.
2. **Le `catch` ne couvrait pas la conversion**, étant posé en amont du `map`.
   `this[cle]` est un cast non vérifié : une clé portant un autre type que le
   sien y lève une `ClassCastException` que rien ne retenait. Les valeurs se
   lisent maintenant par `asMap()` avec un `as?`.
3. Corollaire du précédent : **déplacer le `catch` en aval aurait suffi à ne
   plus tomber, mais aurait terminé le flux** — ce que la KDoc défend
   explicitement. Un flux terminé fige le partage en aval : on changerait encore
   de thème sans que rien ne bouge. C'est le piège à connaître avant de toucher
   à ce fichier.

**PR #47 — la minuterie de veille.**

`SleepTimer` vit dans `AppContainer`, pas dans le service : on règle une
minuterie puis on quitte l'application. Elle **ne connaît pas le lecteur** — elle
dit *quand*, le service écoute ses `expirations` et met en pause. Elle expose son
**échéance**, pas un décompte : entretenir un compte à rebours à la seconde
coûterait une coroutine pour un affichage que personne ne regarde la plupart du
temps. Le décompte se demande au moment de l'afficher.

Livrée aussi : la correction du `textReport` déprécié (voir les pièges).

**Ce qui n'a pas été fait :** l'option « jusqu'à la fin de la piste ». Elle
demande d'écouter la transition plutôt qu'une échéance — un second mécanisme, à
traiter à part.

## Trois pièges à connaître avant de toucher au lecteur

### 1. `PlayerUiState` ne se reconstruit qu'aux tics de position

**Donc plus du tout en pause.** La minuterie s'y est fait prendre : le décompte
était câblé sur l'état figé et ne bougeait plus. Les bornes A-B liront le même
état — il faudra soit les publier sur un flux propre, soit les relire à
l'affichage, comme `SleepTimerSheet` le fait avec un tic local qui ne vit que le
temps où la feuille est ouverte.

**La vitesse ne s'y est pas laissé prendre**, et c'est la voie à reprendre : elle
ne passe pas par `PlaybackState` du tout, mais par le flux des préférences, qui
émet au moment du choix. Le piège se désamorce mieux qu'il ne se contourne — si
quelque chose d'autre que le lecteur peut porter les bornes A-B, qu'il les
porte.

### 2. « Vérifier puis agir » : la course qui revient

Le même défaut est apparu **trois fois** sur ce dépôt — PR #31 (`CacheViewModel`),
#34 (`ServerSessionRepository`), #47 (`SleepTimer`) — et a été réintroduit après
avoir été corrigé deux fois. La forme est toujours la même : une vérification,
puis une action, sans rien qui tienne entre les deux. Un `Mutex` ou un
`Job.cancel()` **par opération** ne suffit pas : ils protègent chaque geste
isolément, et c'est leur *écartement* qui laisse passer.

Attention à la **demi-correction** : sur la #47, un numéro de génération comparé
au réveil de la coroutine *rétrécissait* la fenêtre sans la fermer. Il a fallu
mettre la reconnaissance, l'effacement et l'émission dans un seul verrou. Une
émission peut y tenir si le `MutableSharedFlow` a un tampon — `tryEmit` ne
suspend pas.

Le réflexe : devant `if (encoreValide) { agir }` traversant deux fils, demander
*qu'est-ce qui empêche l'état de changer entre le `if` et le `agir` ?*

**Corollaire pénible :** ces courses ne se testent pas sous `runTest`, dont
l'ordonnanceur est mono-fil. La garde de `SleepTimer` est **posée et documentée
comme non éprouvée** ; un test écrit pour elle passait le retrait de la garde et
a été retiré plutôt que de laisser croire la zone couverte. Voir le commentaire
en fin de `SleepTimerTest.kt`.

**Quatrième occurrence, #50 :** `AbLoopRunner.surveiller` relit l'état après
chaque sommeil. Le même scénario s'est rejoué à l'identique — test écrit, test
creux, test retiré, garde documentée. Sous `runTest` c'est `collectLatest` qui
annule la surveillance avant tout réveil, et le retrait de la garde ne fait
tomber personne. **Ne pas réessayer d'écrire ce test** sans changer d'outil :
c'est un vrai dispatcher qu'il faudrait, pas une horloge virtuelle.

### 3. Le piège `textReport`

`textReport` et `textOutput` sont dépréciés **ensemble** depuis AGP 9. Les
retirer sèchement supprime l'avertissement **mais casse ce qu'ils tenaient** :
les remontées du lint n'arrivent plus au journal, seulement dans un fichier que
personne n'ouvre. Une tâche `afficherRapportLint` liée par `finalizedBy` les
réimprime — `finalizedBy` et **non** `doLast`, parce qu'une action de tâche est
sautée quand la tâche échoue, c'est-à-dire précisément quand le lint a trouvé
une erreur. Ne pas rouvrir.

## La suite : le lot 4, le transcodage

Le choix de la qualité est fait (#51). Ce qui reste, dans cet ordre :

### 1. Se déplacer dans un morceau transcodé — la décision à confirmer

**Aujourd'hui, c'est probablement impossible à la première écoute** — lu dans le
serveur, **pas vérifié sur appareil**. Dans `src/media.rs`, un transcodage en
direct répond `Accept-Ranges: none`, refuse en **416** toute plage qui ne part
pas du premier octet, et un client qui s'en va fait tuer ffmpeg et effacer le
fichier partiel. ExoPlayer se déplace par plages. Les plages ne reviennent qu'une
fois le transcodage terminé et mis en cache côté serveur.

**La voie recommandée à l'utilisateur, pas encore arrêtée par lui :** redemander
le flux avec `offset_ms`, que le serveur accepte déjà sur l'URL du ticket, et
compenser la position. En Media3, un `ForwardingPlayer` dans le service, pour que
la session — donc la notification et Android Auto — lise la position compensée.
**Resonus le fait en production** et a payé chaque piège, ticket à l'appui ; voir
« Sources d'inspiration » plus bas. Les plus coûteux :

- la session qui lit la position brute : notification et voiture reviennent à
  0:00 à chaque saut ;
- la durée d'un segment, qui est celle du reste et non du morceau ;
- la répétition d'un titre, qui boucle le segment seul ;
- une fois un segment en cours, tout saut est un nouveau décalage.

Et un piège propre à ce dépôt : **un segment ne doit jamais entrer dans le cache
sous la clé du morceau entier.** La clé devra porter le décalage, ou le segment
contourner le cache.

**Côté serveur, un effet de bord est signalé :** un transcodage abandonné par un
saut n'est jamais mis en cache, si bien qu'une piste déplacée à sa première
écoute se retranscode à chaque écoute — `InstaZDLL/waveflow-server#185`.

### 2. Le 429

Honorer `Retry-After` avec gigue et un nombre borné d'essais ; ne redescendre
vers l'original qu'une fois ceux-ci épuisés, seulement si la liaison le porte,
et le dire à l'écran. C'est le contrat du serveur, `docs/api-v2-guide.md`,
« When a transcode is refused ».

### 3. Le profil *Automatique*

Resonus en donne une forme éprouvée : une qualité pour le Wi-Fi, une autre pour
les données mobiles. Elle se réconcilie avec la décision du 30/08 — des profils,
pas un codec — en choisissant un profil par réseau.

**L'en-tête du lecteur est plein**, et le restera : quatre boutons — réduire,
vitesse, veille, file — et la colonne du titre déjà serrée sur un écran étroit.

La #50 y échappe sans menu de débordement : le bouton A-B vit **entre les deux
durées**, sous la barre de progression, parce que A et B sont des positions et
se posent en regardant celle qui défile. Le réflexe à garder : avant de pousser
un cinquième bouton dans l'en-tête, chercher si le réglage n'a pas une place
plus juste ailleurs.

## Quatre pièges de méthode, payés sur les #48 et #50

Ils ne sont pas dans le code : ils sont dans la façon de le vérifier.

### L'assertion que le défaut satisfait lui-même

Pour prouver qu'une boucle A-B ne travaille plus en pause, le test naturel est
« en pause, aucun rembobinage ». Il passe **avec** le défaut : une position à
l'arrêt reste sous B, et une surveillance qui tourne à vide ne rembobine jamais
non plus. Le symptôme réel était le réveil, pas le geste.

Avant d'écrire une assertion, se demander si le code fautif la satisferait. Si
oui, observer ce que le défaut **coûte** plutôt que ce qu'il **fait** : le faux
`positionMs` de `AbLoopRunnerTest` compte ses appels, et une minute de pause doit
n'en coûter aucun. Puis lui adjoindre son pendant contre la sur-correction — une
garde qui ne se rouvrirait jamais passerait le premier test. Au retrait, ces
deux tests tombent, et eux seuls.

### Le faux doit se comporter comme le vrai

Le faux `seekTo` de `AbLoopRunnerTest` notait le rembobinage **sans déplacer la
position**. La surveillance retrouvait donc la lecture au-delà de B à chaque
réveil et rembobinait sans fin ; trois tests comptaient des tours que le vrai
lecteur ne fait pas. Même famille que le bornage de `FakePreferencesStore`,
relevé en revue sur la #48 : un faux plus permissif — ou plus inerte — que
l'original rend verts des tests qui décrivent une application qui n'existe pas.

### Une assertion « rien n'est levé » ne supporte qu'un seul appel

Deux appels à la suite, et le premier lève pour les deux : le second n'est
jamais éprouvé, et sa protection peut disparaître sans que rien ne tombe.
Rencontré en couvrant `setTheme` et `setPlaybackSpeed` du même filet. Sixième
forme de test creux du dépôt.

### Un script qui applique puis défait un retrait

Deux fois il a abîmé l'arbre, de deux façons :

1. **Un motif de remplacement vide** : `str.replace("", ligne, 1)` réinsère en
   **tête de fichier**, silencieusement. Toujours remplacer par un marqueur non
   vide (`// RETRAIT`).
2. **Un motif de restauration trop générique** : rendre `courant` là où il
   apparaît vingt fois écrase la première occurrence venue. Le motif du retour
   doit être aussi unique que celui de l'aller.

Le contrôle qui les a rattrapés : après une campagne, relancer Gradle et
vérifier qu'il annonce tout **`UP-TO-DATE`**. S'il recompile, un fichier n'est
pas revenu à l'identique.

## En attente d'une décision de l'utilisateur

Ni l'un ni l'autre n'est bloquant. Ils ne sont pas oubliés, ils sont posés.

1. **Issue #32 — publication sur F-Droid**, ouverte le 19/08, `needs triage`,
   jamais discutée. Le terrain est favorable : GPLv3, aucune dépendance
   propriétaire (ni Play Services ni Firebase), pas de `signingConfig` release —
   F-Droid construit et signe lui-même. Restent les métadonnées `fdroiddata` et
   la reproductibilité du build.
2. **La recherche vocale Android Auto.** « Joue tel album » ne fonctionne pas :
   le lint `MissingIntentFilterForMediaSearch` est **rétrogradé en `warning`**
   dans `app/lint.xml`, avec sa justification. Déclarer `MEDIA_PLAY_FROM_SEARCH`
   sans servir la recherche ouvrirait une porte sur une pièce vide. La remontée
   reste visible à chaque build et disparaîtra quand la recherche arrivera.

Reste aussi **la validation sur appareil** de l'arbre Android Auto : il n'a
jamais été vu dans une vraie voiture ni sur le DHU, tout ce qui est consigné
vient de Robolectric.

## Un compromis qui traîne, à solder quand l'occasion viendra

Le catalogue serveur est un **sous-onglet** de la bibliothèque, à côté de
Titres/Albums/Artistes/Playlists. Ce n'est pas le filtre de source décidé le
30/08 — « Serveur » est une *source*, pas un *regard*. Le réunir suppose de
réconcilier deux façons de charger : le local est en mémoire, le distant se
pagine sur le réseau. Le retirer de la barre sans cela l'aurait rendu
**inatteignable**, les réglages menant au compte et non au catalogue.

## Conventions en vigueur

- **Rien ne se commit sur `main`** : branche + PR + revue CodeRabbit. *(Ce
  document fait exception, à la demande explicite de l'utilisateur.)*
- **La fusion demande l'approbation de l'utilisateur.** Le ruleset de `main`
  porte `require_extra_approval_for_unattributed_changes` : l'approbation de
  CodeRabbit ne suffit pas, `gh pr merge` répond « the base branch policy
  prohibits the merge », et **il n'y a pas à passer outre avec `--admin`**.
- **Tout test de régression se valide par retrait** : on enlève le correctif et
  on vérifie que le bon test — et lui seul — tombe, avec `--rerun-tasks`. Un test
  qui passe des deux côtés est un test creux, et il y en a sept formes connues.
  *Sur une machine à court de mémoire, `--rerun-tasks` fait tomber le build ;
  un retrait modifie de toute façon une source, ce qui invalide déjà la tâche de
  test. Le drapeau ne protège que du cas où rien n'a changé.*
- `./gradlew ktlintFormat` **avant chaque commit**.
- **Français** pour l'interface, les messages de commit, la KDoc, les
  commentaires et les noms de tests (`build-and-test.yml` excepté).
- **Toujours rebaser sur `main` avant d'ouvrir la PR.** La CI construit la
  branche, *pas le résultat de fusion* : deux branches vertes chacune de leur
  côté peuvent fusionner en un `main` qui ne compile pas. C'est arrivé le 18/08
  avec media3 1.5.1 → 1.11.0. Après une fusion suivie d'un Dependabot, relancer
  `ktlintCheck detekt testDebugUnitTest` sur `main`.
- **Ce que le client attend du serveur s'ouvre en issue sur `waveflow-server`**,
  directement — autorisé par l'utilisateur le 11/09. Une issue par demande,
  **vérifiée dans le code de `main`** du serveur, après recherche de doublons,
  en anglais et selon les conventions de ce dépôt-là. Ni commit ni PR côté
  serveur.

## Sources d'inspiration

Deux lecteurs désignés par l'utilisateur, à lire pour s'inspirer — leurs choix
se reprennent avec leur raison, pas sans.

- **Resonus** — `E:\Workspace\resonus`, React Native (Expo, `expo-audio`), GPLv3.
  Pour le **transcodage** : se déplacer dans un flux transcodé
  (`src/store/player.ts`, bloc « Seek in transcoded streams »), la qualité par
  réseau (`src/app/settings/playback.tsx`). Ses commentaires citent les tickets
  qui ont motivé chaque choix.
- **CrystalMusic** — `E:\Workspace\CrystalMusic`, Compose, **local seulement**
  sur `MediaPlayer`, Apache-2.0. Pour l'**interface** : Material 3 Expressive,
  formes de pochette, curseur ondulé, et une notification qui porte les paroles
  en direct (`core/Utils.kt`) — utile au lot 5.
