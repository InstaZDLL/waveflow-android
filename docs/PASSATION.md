# Passation

Document vivant : chaque agent qui prend la suite le relit d'abord, et le met à
jour avant de partir. Il dit **où en est le chantier et ce qui vient ensuite** —
pas l'historique, que `git log` raconte mieux.

Dernière mise à jour : **2026-11-27**, fin de la vague 4, sur `main` = `0eab965`.

## État du dépôt

- `main` = `0eab965`. **Les vagues 1 à 4 sont fusionnées en entier** (vague 4 :
  #104, #105, #106) ; aucune PR ouverte. **Les six lots de la refonte v2 sont
  livrés.**
- **917 tests verts** sur `main`, CI verte (workflow `Build & test`, ~4 min).
- **Zéro avertissement de compilation**, la propriété du dépôt, tenue depuis
  #94. **Un avertissement ne se voit pas sur un build tiède** :
  `checks.sh avertissements` de la skill `wa-worker` recompile Kotlin, avec un
  `--rerun` **après chaque tâche** (il ne vaut que pour celle qui le précède).
  Voir aussi le piège `textReport` plus bas.
- Gradle 9.9.2, AGP 9.5.2, Kotlin 2.4.21, OkHttp 5.5.1, media3 1.11.1,
  Robolectric 4.18.1, Compose BOM 2026.11.01. **`compileSdk` 37.1** (exigé par
  Compose 1.13, #76), `targetSdk` 36.
- Baseline Detekt : 15 entrées (`longArgOf` parti avec #105). **Elle ne doit
  que rétrécir.**
- **Tout le reste à faire est suivi en issues** — voir « Le mode
  orchestration ». Cette note dit le pourquoi ; les issues disent le quoi.
  Aucune n'est encore rangée dans une vague 5.

## Le mode orchestration (depuis le 24/11)

Le travail avance désormais **par vagues**, comme sur le bureau : une session
coordinatrice lance trois ouvriers en parallèle, chacun sur une issue et dans son
worktree, sur des fichiers disjoints ; elle relit, intègre les branches de la
vague, l'utilisateur teste l'APK d'intégration, puis viennent les MR et les PR.

- **Les ouvriers chargent la skill `wa-worker`** (`~/.claude/skills/wa-worker/`) :
  worktree, slot, Gradle, émulateur par slot, Fedora, validation par retrait,
  commits en français, rapport de fin. Le relecteur indépendant est l'agent
  **`wa-reviewer`** (`~/.claude/agents/`), qui connaît les pièges ci-dessous.
- **Deux builds Gradle au plus sur le PC** (32 Go), un sur le Fedora
  (`ssh waveflow-fedora`, 15 Go, clone dans `~/Documents/workspace/waveflow-android`,
  plateforme 37.1 installée, build complet vert le 24/11).
- **La revue CodeRabbit passe par GitLab** : le compte pro y relit sans
  l'attente du compte gratuit de GitHub. Branche poussée sur le miroir
  `waveflow-groupe/waveflow-android` (remote `gitlab`), MR, revue jusqu'à
  l'approbation, MR fermée, puis PR GitHub que **l'utilisateur fusionne**.
  Exception : un bump Dependabot ne passe pas par GitLab (CodeRabbit ne relit pas
  les fichiers de versions et de verrous). Vérifier avant une MR que `main` du
  miroir est aligné sur `origin/main`. Une remarque peut encore tomber après
  l'approbation : elle se corrige sur la branche, la PR GitHub restant ouverte.
- **L'utilisateur ne colle plus la revue** : après chaque push, le coordinateur
  lance `~/.claude/skills/wa-worker/scripts/cr-revue.sh <MR> --attendre`, qui
  sort le « Prompt to fix all issues » de la revue du commit de tête (ou
  « AUCUNE REMARQUE »), et le transmet à l'ouvrier, qui vérifie avant de
  corriger. Une remarque écartée se défend dans son fil : CodeRabbit le ferme
  s'il est convaincu, et n'approuve qu'une fois tous ses fils fermés. Pas de
  `@coderabbit17 fix comments` : il appliquerait sans vérifier.
- **Une MR empilée sur une autre branche que `main` n'est pas relue d'office**
  (`base_branches: main` dans `.coderabbit.yaml`) : la déclencher par une note
  `@coderabbit17 review`. Les PR se fusionnent écrasées : la branche empilée se
  rebase (`git rebase --onto origin/main <ancienne tête de la base>`) avant sa
  PR. **Le Fedora n'a pas de clé SSH GitLab** : le coordinateur y pousse ses
  branches depuis le PC.
- **Un push HTTPS vers GitHub peut renvoyer « Internal Server Error »** alors que
  GitHub est vert : `git push git@github.com:InstaZDLL/waveflow-android.git <branche>`
  passe. **Ne jamais démarrer l'émulateur pendant un build Gradle** : sous la
  pression mémoire, `system_server` est tué et l'émulateur boucle au démarrage.

**Les vagues prévues :**

1. ~~Tokens, polices et icône de la DA (#77) · le 429 (#57) · la recherche vocale
   Android Auto (#59) avec l'avertissement #62 et les écarts serveur (#80).~~
   **Faite le 25/11** — voir « Ce que la vague 1 a livré ».
2. ~~Composants de la DA et lecteur (#78) · le profil *Automatique* (#58) · la
   coupure réseau pendant un transcodage (#60), qui réutilise la relance du 429 ·
   l'ordre aléatoire perturbé par une relance (#86).~~ **Faite le 25/11** — voir
   « Ce que la vague 2 a livré ».
3. ~~Recomposition des écrans (#79) · les paroles (#81, lot 5) · ReplayGain et
   égaliseur en Kotlin (#82, lot 6).~~ **Faite le 26/11** — voir « Ce que la
   vague 3 a livré ».

4. ~~La suggestion du jour (#95) · l'onglet allumé, le titre répété et
   « Nouvelle playlist » (#96, #97, #98) · la première écoute non notée (#99).~~
   **Faite le 27/11** — voir « Ce que la vague 4 a livré ».

**Ouvertes, hors vague** (suites de la vague 4, le 27/11) : mesurer l'écoute sur
la position du lecteur, la cause restante de #99 (**#102**) ; la suggestion du
jour pour une bibliothèque du serveur (**#103**). Une vague 5 ne se trace qu'à la
demande de l'utilisateur.

À côté : la validation sur appareil (#61), avec l'utilisateur, et F-Droid (#32).

## Ce qui a changé autour du client le 24/11

**Le serveur est stable.** 2.0.0 le 29/10, 2.3.0 le 17/11 ; le client avait été
écrit contre la 2.0.0-beta.1. **Aucun appel du client ne casse** : aucune route
`/api/v2` n'a été retirée, les champs ajoutés sont ignorés. Le guide a déménagé
dans `docs/guides/api-v2-guide.md` côté serveur. Les petits écarts sont en
**#80** ; le serveur offre aussi beaucoup que le client n'utilise pas encore
(paroles, `replay_gain_*`, favoris, file, mixes, radios, podcasts, profils de
transcodage, AAC, synchronisation par événements).

**Nouvelle direction artistique : Fluid Editorial.** Source de vérité :
`WaveFlow/docs/design/fluid-editorial.md`, qui prévoit sa traduction pour
Android. Sombre uniquement, accent `#34D399`, DM Sans et Playfair italique, un
nouveau logo — le « W en onde », qui remplace les cinq barres. Trois tickets
(#77 à #79). **Tranché depuis** : le thème clair reste, en *Liquid Aurora Glass*
clair (le « skin liquid white » du site, `WaveFlow/docs/design/liquid-aurora-glass.md`),
avec l'accent au palier 600 (`#059669`, encre blanche, 3,77:1 comme le bureau ;
le contraste se règle pour toutes les plateformes avec #78). L'accent tiré de la
pochette vaut **dans le lecteur seulement** (décidé le 25/11, livré par #92).

**Le moteur audio Rust du bureau à la place de Media3 : écarté pour l'instant.**
Seul le remplacement complet du lecteur apporterait ce que Media3 ne fait pas
(fondu enchaîné, APE/WavPack/DSD en local), pour trois à six mois et la logique
du saut dans un transcodage à réécrire ; il faudrait d'abord extraire du bureau
une crate d'orchestration sans Tauri. L'EQ et le ReplayGain se feront en Kotlin
en portant le code du bureau (#82). À reconsidérer si le fondu enchaîné devient
une exigence, après un prototype de quelques jours sur l'émulateur.

## Le chantier : la refonte v2

Six lots, dans cet ordre :

1. Réglages / DataStore — **fait**
2. Navigation + identité — **fait**
3. Lecteur : file d'attente, minuterie, vitesse, boucle A-B — **fait**
4. Transcodage (remonté du 6ᵉ rang : meilleur rapport travail/effet, le serveur
   est déjà prêt) — **fait** : qualité (#51), cache d'un transcodage
   inachevé (#52), retrait du grisé (#53), déplacement dans un morceau
   transcodé (#54), le 429 (#89), le profil *Automatique* (#91), la reprise
   après une coupure et l'ordre aléatoire (#93)
5. Paroles — **fait** (#101)
6. Audio avancé — **fait pour l'EQ et le ReplayGain** (#100) ; le gapless reste
   celui de Media3 (non réimplémenté), le passthrough AC-3/DTS n'est pas traité

Le fondu enchaîné **est hors plan** : Media3 ne le fournit pas, il faudrait deux
lecteurs ou une chaîne audio maison.

## Ce que la vague 4 a livré (27/11)

Trois PR pour cinq issues, mêmes étapes que les vagues précédentes : intégration
locale (916 tests, zéro avertissement), APK validé par l'utilisateur sur
l'émulateur, `wa-reviewer`, CodeRabbit sur GitLab. Seul `MainActivity.kt` était
partagé (une ligne de #95), fusion propre dans les deux ordres. Greptile n'a
rien relevé.

**PR #104 — la suggestion du jour (#95)**, portée de
`waveflow_core::home_suggestion`. Règles S1 à S17 et écarts E1 à E6 dans
`docs/suggestion-du-jour.md`. Paquet `suggestion/` : Oubliée, Dans l'esprit,
Jamais écoutée, tirage `splitmix64` au bit près, deux pistes au plus par artiste.
Les vecteurs exacts ont été tirés du **Rust du bureau compilé à part** sur une
copie — à refaire pour tout portage. Le tirage est épinglé pour la journée dans
un DataStore à part (`TirageStore`), le jour se lit sur une horloge injectable,
avec une attente de minuit bornée à une heure (un changement de fuseau).
**Écarts** : ni journal d'écoutes, ni énergie et tempo, ni artistes similaires
côté Android, donc pas de `vibe_distance` ; **l'appareil seul** (S8), le serveur
suit en #103. Corrigé après CodeRabbit : un flux de lecture qui abandonne après
ses reprises ne se termine plus, il attend la prochaine écriture réussie — sinon
la bande restait figée. Deux leçons : une mémoire de ViewModel se lit et se pose
dans le `combine`, jamais après `flowOn` ; `delay` attend une durée, pas une
échéance.

**PR #105 — navigation et détails (#96, #97, #98).** L'onglet allumé suit la
**pile**, plus la route : chaque page note à son ouverture la racine de sa pile
dans son `SavedStateHandle` (`ui/navigation/OpenTab.kt`), parce que
`NavController.currentBackStack` est réservé au groupe androidx — **seul le lint
le voit, pas le compilateur**. Un `popUpTo(start) { saveState }` non inclusif
range la pile retirée sous le nom de la destination de départ : c'est ce qui
rouvrait l'album en touchant « Accueil ». `popBackStack(route)` s'arrête à
l'entrée la plus haute de cette route. Toucher l'onglet ouvert ramène à sa
racine ; « Bibliothèque » rouvre la dernière vue. Le titre quitte la barre du
haut des pages de détail, comme sur le bureau (`TopBar.tsx`). « Nouvelle
playlist » perd sa boîte ; `SheetOption` gagne icône et seconde ligne.
**Non vérifié** : la mort du processus.

**PR #106 — une écoute traitée après le seuil (#99).** Sur une boucle encombrée,
une pause postée avant les 20 s mais traitée après passait devant le minuteur :
rien n'était noté. Le temps mesuré décide désormais, le minuteur ne fait que
réveiller. **Reste (#102)** : une lecture annoncée en retard au démarrage à froid
perd encore l'écoute, et un enchaînement annoncé en retard peut compter une piste
de moins de 20 s — le compteur mesure entre les livraisons d'événements, pas le
temps joué. Défaut reproduit sur la vraie chaîne (`PlaybackServiceHistoryTest`).
Ce qui ne se devine pas :
- Robolectric en mode PAUSED : l'ExoPlayer n'avance que par messages différés,
  une attente de démarrage doit avancer l'horloge ;
  `ShadowSystemClock.advanceBy` sans écouler les boucles fabrique une boucle
  encombrée ;
- **la position d'un WAV sous Robolectric ne vaut rien** (109 s après 3 s de
  lecture) : #102 se testera sur la JVM avec une fausse position ;
- un test négatif (« rien n'est noté ») attend une **barrière d'écriture** — la
  note d'une piste suivante, rangée dans la même portée — et non un délai.

**Défaut antérieur relevé, non ouvert** : `artworkScope` est annulé par
`onDestroy` et n'a pas de `CoroutineExceptionHandler` ; une écriture d'historique
en cours peut se perdre, une `SQLiteException` ferait planter le service.

**Leçon de machine** : deux émulateurs à la fois saturent le PC (0,9 Go libre,
ANR de l'app) ; garder un émulateur à la fois quand deux builds tournent.

## Ce que la vague 3 a livré (26/11)

Trois PR pour trois issues, mêmes étapes que les vagues 1 et 2 : intégration
locale sans conflit (829 tests, zéro avertissement), APK d'intégration validé par
l'utilisateur sur l'émulateur, `wa-reviewer`, CodeRabbit sur GitLab, puis
Greptile sur GitHub — qui a rendu **trois tours de remarques de sécurité**
successifs sur #100 et #101, tous justes (voir plus bas).

**PR #94 — l'accueil et les écrans selon la DA 2.0 (#79).** `PageParts` (surtitre,
titre souligné de l'onde, pilules) et `Tiles` (tuiles d'album et d'artiste, liseré
et ombre teintée en clair). L'accueil prend le canevas du bureau : salut, compteurs,
dernière écoute en grand titre, mosaïque des ajouts récents. Le salut change de
lui-même à l'heure du palier suivant (`nextGreetingChange`), et pas seulement au
retour au premier plan. `DetailHeader` recomposé sert les cinq pages de détail ;
Albums, Artistes et le catalogue passent en grilles, pagination comprise.
`OutlinedSecureTextField` remplace `PasswordVisualTransformation`. **Laissée de
côté** : la suggestion du jour, faute de l'algorithme (#95). **Deux pièges** : le
`Surface(onClick)` de material3 ne pose pas `Role.Button`, une pilule doit le
poser ; le masquage d'un champ sécurisé se lit sous Robolectric par
`GetTextLayoutResult`, la sémantique portant le texte brut.
`fillMaxWidth(f).widthIn(max)` ne plafonne rien.

**PR #101 — les paroles synchronisées (#81, lot 5).** Un appui sur la pochette
les montre. Piste locale : tags du fichier (USLT et SYLT décodés à la main, Media3
les rendant en cadres bruts ; Vorbis `LYRICS` ; MP4 `©lyr`), puis le `.lrc`
voisin, **sans réseau** (décision du 25/11). Piste du serveur : la première
feuille de `/api/v2/tracks/{id}/lyrics` ; seul un 404 vaut absence. Règles P1 à
P19 dans `docs/paroles.md`. **Plafonds posés après Greptile** : 1 Mio par trame,
1 Mio de paroles décodées par fichier (décodage paresseux, arrêt au premier
synchronisé), `Lrc.lire` sans copie dans la boucle (`Regex.matchAt` à index
mobile), analyse hors du fil principal. Ce qui ne se devine pas :
- **`MetadataRetriever` vit dans `media3-inspector`** en 1.11.1 (dépendance
  ajoutée) ; sous Robolectric, un fichier illisible n'aboutit qu'en poussant
  l'horloge.
- **`MediaController` en pause** n'accepte une position déplacée que datée
  *strictement après* le dernier play ou pause ; sous Robolectric l'horloge figée
  met les deux à la même milliseconde, il faut l'avancer entre les deux.
- **Compose 1.13** : `PageUp`/`PageDown` et l'action `ScrollBy` du lecteur d'écran
  défilent par `NestedScrollScope.scrollBy`, sans `DragInteraction`, et trop vite
  pour qu'un `snapshotFlow` sur `isScrollInProgress` le voie. Le parcours manuel
  se détecte donc dans le défilement imbriqué (P15).
- `advanceUntilIdle()` ne fait pas tourner `backgroundScope` ; `runCurrent()` oui.

**Non vérifié** : le `.lrc` voisin sur Android 11 à 15 (vu sur 16 et 17 avec la
seule permission audio ; un refus le rendrait muet), MP4 et Ogg sur un vrai
fichier, une piste du serveur à l'écran.

**PR #100 — ReplayGain et égaliseur à six bandes (#82, lot 6)**, portés de
`replay_gain.rs` et `eq.rs` avec leurs vecteurs de test. Deux `AudioProcessor`
dans le `DefaultAudioSink`, ReplayGain puis EQ, en flottant entre les deux ;
ReplayGain actif par défaut en mode Automatique (gain d'album quand on écoute un
disque, approximation consignée en G7). EQ par pas de 0,1 dB comme le bureau.
Règles dans `docs/audio-avance.md`. Ce qui ne se devine pas :
- un `AudioProcessor` connaît sa piste par le `periodUid` du
  `flush(StreamMetadata)`, et les tags ne se voient qu'au `configure` de la
  sortie : le gain se retient là, et bascule à la frontière en enchaînement ;
- le saut des silences de Media3 refuse tout sauf le 16 bits : le dernier
  traitement maison rend du 16 bits ;
- un `Slider` material3 1.5 sans crans avance au clavier d'un centième de sa
  course ; cacher les crans, c'est `drawTick = {}` sur le `Track`, pas
  `steps = 0` ;
- une bande au-delà de Nyquist est neutre (le bureau rééchantillonne avant l'EQ,
  Media3 non).
**Plafond posé après Greptile** : une valeur de tag de plus de 64 caractères est
écartée, et la regex décimale est réécrite sans retour arrière quadratique
(`\d+\.?\d*` faisait se disputer les mêmes chiffres par ses deux moitiés).
**Non vérifié** : rien n'a été *entendu* (Robolectric ne décode pas l'audio), le
gapless et la bascule du gain sur appareil, et si les transcodages du serveur
recopient les tags.

**Leçons de méthode.**
- **Toute donnée lue d'un fichier ou du réseau veut un plafond** — longueur d'une
  valeur, taille d'une trame, budget total — et **pas de `substring` ni de
  concaténation dans une boucle**. Greptile l'a trouvé trois fois de suite, chaque
  correctif découvrant le chemin voisin. À demander d'emblée dans les briefs.
- **Une entrée de la baseline Detekt est indexée sur la signature** : ajouter un
  paramètre à une fonction listée la fait sauter, et la règle se rallume.
- **Ne jamais restaurer un retrait par `git checkout`** quand le correctif n'est
  pas encore commité : on efface le correctif avec. Restaurer depuis une copie.
- **Un fil CodeRabbit corrigé peut rester ouvert** et retenir l'approbation : y
  répondre en citant le commit suffit à le faire fermer.
- Sur la Fedora, `emu.sh start` lancé sous `timeout` tue l'émulateur avec lui :
  le lancer en arrière-plan. Une boucle `until ! pgrep -f checks.sh` se trouve
  elle-même et ne finit jamais : attendre un PID.

## Ce que la vague 2 a livré (25/11)

Trois PR pour quatre issues, mêmes étapes que la vague 1 : intégration locale
sans conflit (631 tests), APK d'intégration validé par l'utilisateur sur
l'émulateur, `wa-reviewer` puis CodeRabbit sur GitLab, Greptile sur GitHub.

**PR #91 — le profil *Automatique* (#58).** Un quatrième choix de qualité : un
profil sur un réseau non facturé (affiché Wi-Fi, Original par défaut), un autre
sur un réseau facturé (affiché Données mobiles, Économie par défaut). Au
changement de réseau, seules les pistes **pas encore commencées** prennent le
nouveau rendu ; la courante jamais, et la suivante est épargnée dans les dix
dernières secondes de la courante — protégée une fois, elle est tenue pour
commencée. La lecture du réseau vit dans `Liaison.kt`, partagée avec le repli du
429 ; son flux ne garde que le dernier réseau annoncé (un `callbackFlow` borné
perdait sans bruit le dernier). Règles Q1 à Q9 dans `docs/qualite-automatique.md`.
**Non éprouvé** : rien n'a été entendu, et aucune vraie bascule Wi-Fi → données
n'a été faite sur un appareil.

**PR #93 — l'ordre aléatoire (#86) et la reprise après une coupure (#60).**
- **Piège Media3 payé** : `replaceMediaItem(s)` envoie au fil de lecture
  **deux** messages, ajout puis retrait, chacun avec son ordre aléatoire ; l'ordre
  par défaut range l'ajout à un rang tiré au hasard, et l'étape intermédiaire
  pouvait retirer la suivante déjà préparée en relançant la courante. Reposer
  l'ordre après coup arrive trop tard : `OrdreEnPlace`, posé le temps du
  remplacement, range chaque ajout juste derrière la piste qu'il remplace ; l'ordre
  tiré est rétabli ensuite. Suspendre l'aléatoire n'est plus nécessaire.
- **La coupure d'un transcodage** : un transcodage en direct n'a ni longueur ni
  durée, et Media3, coupé en route, le tient pour un direct et **le recharge du
  début, sans erreur** — l'issue décrivait un autre mécanisme.
  `TranscodeRefusalPolicy` rend la coupure définitive pour une réponse qui n'a pas
  dit accepter les plages ; `TranscodeCutRecovery` relance alors le même rendu à
  la position logique, en silence. Trois reprises au plus sans cinq secondes
  d'écoute. Un transcodage servi entier et l'original se rouvrent toujours à
  l'octet atteint. Règles R25 à R30.
- **Le banc compte** : servi en WAV, dont l'en-tête annonce la durée, le
  transcodage factice prenait un chemin que la production ne prend pas. Il est
  servi en **Matroska PCM sans durée ni index**, que Media3 traite comme un Opus
  en direct, et le défaut s'y reproduit.

**PR #92 — composants signature et lecteur (#78).** `WaveMark`,
`WaveFlowSymbol` (le W qui respire pendant la lecture), `WaveFlowSheet` et
`SheetOption`. Le lecteur sous la lumière de la pochette : accent tiré de la
pochette **dans le lecteur seul** (`PlayerAccent`, 3:1 tenu sur tout le cercle
des teintes, repli sur l'émeraude), nappes de lumière bornées pour que les encres
gardent 4,5:1. En sombre, Android renforce la lumière par rapport à la recette du
bureau (un voile de 1,1 à 1,35:1 devient un halo d'environ 2:1), et les durées et
« EN LECTURE » passent en deuxième encre ; l'écart est noté dans `lightPools` pour
que le bureau puisse s'aligner. `PlayerSeekBar` remplace le `Slider` : onde en
sombre, ligne de lumière en clair, bornes A-B dessinées, gestes refaits à la main
et pilotés sous Robolectric par `performTouchInput` (`PlayerSeekBarTest`). **Un
`pointerInput` ne se relance que si une clé change** : l'appui et le glissement
ont `trackKey` dans leurs clés, sinon un geste commencé sur un morceau finissait
dans le suivant. La barre fait 40 dp, mais Compose étend le toucher d'un
`pointerInput` jusqu'à 48 dp : mesuré, la remarque de Greptile est écartée.
**Non vérifié** : le rendu sur un vrai écran.

## Ce que la vague 1 a livré (25/11)

Six PR, menées par trois ouvriers en parallèle, intégrées sans conflit sur une
branche locale, l'APK d'intégration lancé sur l'émulateur, chaque branche relue
par `wa-reviewer` puis par CodeRabbit sur GitLab.

**PR #87 — fondations de la direction artistique (#77).** Sombre en *Fluid
Editorial*, clair en *Liquid Aurora Glass*, sur Material 3 sans recomposer
d'écran. Un seul accent fixe ; `secondary` et `tertiary` lui sont égaux pour
qu'aucun composant ne trouve de seconde couleur. DM Sans et Playfair italique
embarquées (licences dans `licences/polices/`). Les formes passent par
intention (`WaveFlowShapes`), le mouvement par `WaveFlowMotion` (rien que
l'opacité quand les animations sont supprimées). Le W en onde remplace les cinq
barres. **Ce que material3 1.5 lit vraiment** : `Card` lit
`surfaceContainerHighest`, `DropdownMenu` `surfaceContainer` et la forme
`extraSmall`, la piste de `LinearProgressIndicator` `secondaryContainer` — les
KDoc de `Color.kt` le tiennent à jour. Les calques d'état passent par
`LocalRippleConfiguration`, déprécié sans remplaçant.

**PR #89 — le 429 (#57).** `TranscodeRefusalPolicy` honore `Retry-After` avec
gigue, trois relances au plus ; puis repli sur l'original à la même position,
sur un réseau non facturé seulement, sinon `PlaybackFailure.Busy`. Les sauts
rapprochés dans un transcodage se coalescent (300 ms, R9). Règles R17 à R24 dans
`docs/deplacement-dans-un-transcodage.md`. **Trois pièges Media3 payés** :
`replaceMediaItem` avec un autre URI ajoute la nouvelle source *puis* retire
l'ancienne — en `REPEAT_MODE_ONE` le lecteur finissait, en aléatoire il passait
à la piste tirée suivante ; répétition et aléatoire sont donc suspendus le temps
du remplacement. `stop()` garde `playerError`. `getMinimumLoadableRetryCount`
vaut pour toutes les erreurs, pas seulement le 429. **Reste** : l'ordre
aléatoire peut encore être perturbé par une relance — #86, réglée par #93.

**PR #90 — la recherche vocale Android Auto (#59).** `onPlayFromSearch` arrive
par `onAddMediaItems` en un élément au `mediaId` vide, la phrase dans
`requestMetadata.searchQuery` ; résolu dans la bibliothèque de l'appareil sur
le moteur de l'écran de recherche. Côté téléphone, `MainActivity` déclare
`MEDIA_PLAY_FROM_SEARCH` et lie le service à chaque `onResume` si la permission
audio est accordée. Règles R1 à R12 dans `docs/recherche-vocale.md`. **Non
éprouvé** : la réserve aux contrôleurs de confiance (sous Robolectric, tous le
sont), et rien n'a été vu dans une voiture ni sur le DHU.

**PR #88 — aligner le client sur le serveur 2.3 (#80).** Cache des pochettes
éprouvé face aux nouveaux en-têtes (Coil lit `max-age`, pas `immutable`),
serveur minimal 2.2.2 documenté, et **les autorités installées par
l'utilisateur sont crues** (`<certificates src="user" />`) pour un serveur
derrière Caddy en `tls internal` — compromis assumé et écrit dans la
configuration ; l'épinglage par serveur pourra venir plus tard.

**PR #85 (#62)** retire `Timeline.Period.set` ; **PR #84** renormalise
`gradlew.bat` en LF dans le dépôt (il était commité en CRLF et apparaissait
modifié dans chaque worktree).

## Avant la vague 1

**PR #76 — bump Dependabot débloqué (fusionnée le 24/11).** La CI échouait sur
`checkDebugAarMetadata` : le BOM Compose 2026.11.01 apporte Compose 1.13.0, dont
les AAR refusent une `compileSdk` inférieure à 37.1. Correctif d'une ligne,
`release(37) { minorApiLevel = 1 }` ; AGP installe seul la plateforme manquante
au premier build. Les cinq nouveaux avertissements de compilation viennent de là.

**PR #56 — débloquer la montée de version Gradle (fusionnée le 16/09).**

La CI de ce bump Dependabot échouait sur **tous** les tests Robolectric, pas sur
le code applicatif. Robolectric 4.17 monte l'application en passant par
`ApplicationSharedMemory`, apparu avec Android 16 — le `targetSdk` 36 qu'il
simule. Ce chemin n'existait pas en 4.16.1. Il manipule les entrailles d'un
descripteur de fichier via `jdk.internal.access`, que le JDK n'exporte plus vers
le module anonyme :

> Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?

Le correctif tient en un argument de JVM sur les tâches de test,
`--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`. **À garder en tête :
il vaut pour tout le monde**, JDK 21 en intégration continue comme JDK 25 en
local ; ce n'est pas un contournement de poste de travail.

**Deux enseignements, qui valent plus que le correctif.**

1. **Un avertissement de compilation ne se voit pas sur un build tiède.** Celui
   de la #54 (`Timeline.Period.set` déprécié) dormait depuis quatre jours sur
   `main`, sous une propriété que le dépôt se croyait tenir. J'ai d'abord cru
   qu'il venait du bump ; vérifié plutôt que supposé, en recompilant `main` dans
   une worktree séparée avec `--rerun-tasks` — il y était déjà. Rien dans la CI
   ne fait échouer le build sur un avertissement : la propriété repose sur la
   vigilance, et celle-ci a cédé une fois. Suivi en **#62**, avec la piste
   `allWarningsAsErrors`.
2. **Du code écrit à la main sur une branche Dependabot échappe à CodeRabbit.**
   Il a répondu « Review skipped: ignored keyword in the PR title » — le titre
   de Dependabot le fait passer son tour, et le correctif ci-dessus n'a donc été
   relu par personne. La règle du dépôt est que rien n'échappe à la revue ; ce
   trou-là n'est pas bouché. Demander `@coderabbitai review` explicitement quand
   on pousse du code sur une branche de bump.

**Le reste à faire est passé en issues (le 16/09).** Il vivait jusque-là dans
cette note et dans une fiche de mémoire, c'est-à-dire nulle part où un humain
puisse le voir : **#57** le 429, **#58** le profil *Automatique*, **#59** la
recherche vocale Android Auto, **#60** la coupure réseau pendant un transcodage,
**#61** la validation sur appareil, **#62** l'avertissement ci-dessus. La #32
(F-Droid) était déjà ouverte.

**PR #54 — se déplacer dans un morceau transcodé (fusionnée le 12/09).**

C'était impossible auparavant : un transcodage en direct n'a ni longueur ni
plages, l'extracteur Ogg le tient donc pour non déplaçable, et Media3 retire
alors la commande de saut à tous les contrôleurs. Le curseur restait désactivé,
faute de durée.

**Les règles ont été écrites avant le code**, à la demande de l'utilisateur :
`docs/deplacement-dans-un-transcodage.md`, seize règles numérotées R1 à R16, que
le code et les tests citent. **Le premier commit ne portait que cette note**, et
CodeRabbit l'a relue avant qu'une ligne de code n'existe — deux précisions en
sont sorties. À reprendre pour tout chantier de cette taille.

**Le principe : une timeline logique.** Le flux reçu peut commencer à 2:13 ; pour
tout le reste d'Android, le morceau commence à 0:00 et dure sa durée entière. La
correction vit dans **une seule enveloppe**, `TranscodeSeekingPlayer`, un
`ForwardingSimpleBasePlayer` que la session **et tous les écouteurs du service**
reçoivent à la place de l'ExoPlayer — historique, minuterie, vitesse, boucle A-B.

Ce que la lecture des sources de media3 1.11 a appris, et qui ne se devine pas :

- `BasePlayer.seekToPrevious`, `seekBack` et `seekForward` calculent déjà leur
  cible sur l'état de l'enveloppe, donc sur la position logique — et
  `ForwardingSimpleBasePlayer.handleSeek` la **jette** pour laisser l'ExoPlayer la
  recalculer sur sa position brute. C'est là qu'il faut reprendre la main ;
- **ne jamais corriger la liste de lecture par `setPlaylist(List)`** : cela
  fabrique une `PlaylistTimeline`, qui ignore l'ordre aléatoire (« TODO » dans la
  1.11). On enveloppe la timeline (`LogicalTimeline`, un `ForwardingTimeline`) ;
- `SimpleBasePlayer` déduit les transitions des **identifiants** de fenêtre, et
  `replaceMediaItem` en crée de neufs dès que l'URI change : l'enveloppe rattache
  l'ancien, sans quoi une relance passerait pour un changement de piste et
  `ListeningCounter` compterait deux fois un morceau déjà écouté ;
- `ListenerSet` livre `onEvents` par un **message posté**, jamais dans l'appel :
  rattacher les identifiants juste après le remplacement suffit. Une garde posée
  « au cas où » ne faisait tomber aucun test — elle a été retirée.

**Un segment ne touche pas au cache** (`SegmentCacheBypass`) : le cache précède le
résolveur, et un flux décalé rangé sous la clé du morceau se ferait servir le
morceau depuis 0:00 pendant que l'écran afficherait l'instant demandé.

**Ce qui n'a pas été fait :** le 429 à la relance, la coalescence des sauts
rapprochés (R9, ni écrite ni éprouvée — elle vient avec le 429), le cache des
segments, et la coupure réseau en cours de transcodage, qui se règlera par la
même relance. Répéter un segment laisse entendre un instant son début avant la
relance. **Rien n'a tourné sur un appareil** : personne n'a encore *entendu* un
saut tomber au bon endroit.

**Quatre remarques de revue, toutes justes, et deux leçons.** Deux vrais défauts
— `seekToDefaultPosition()` sans rang qui ramenait au début du flux, un index non
borné dans la surveillance des segments. Et **deux fois une couverture annoncée
plus large qu'elle ne l'était** dans la note. Le réflexe à garder : relire ce
qu'une note ou un message de commit **affirme** avec la même sévérité qu'un test.
Une remarque de revue décrivait d'ailleurs le défaut plus largement qu'il ne
l'était ; repris sans vérification, ce diagnostic a fini dans un message de
commit avant d'être démenti par les sources.

**PR #53 — retirer le grisé « serveur sans ffmpeg » (fusionnée le 11/09).**

`waveflow-server` refuse de démarrer sans ffmpeg : l'état grisé était
inatteignable. `transcodingAvailable` sort de l'API, du dépôt, du ViewModel et de
l'écran, avec les onze tests qui n'éprouvaient que cet état. Un transcodage qui
échoue en pleine lecture relève du 429.

**PR #52 — ne pas garder le début d'un transcodage quitté en route (fusionnée le
11/09).**

Défaut de la #51, trouvé en préparant la #54. Quitté en route, le début d'un
transcodage restait en cache ; à la réécoute, `CacheDataSource` le relisait puis
demandait la suite par une plage, que le serveur refuse en 416 — une erreur que
Media3 ne retente **jamais**. La piste tombait là où le cache s'arrêtait.
`IncompleteTranscodeEviction` retire à la fermeture une entrée **sans longueur
consignée** : c'est la signature d'un transcodage inachevé, l'original annonçant
la sienne. Reproduit d'abord par un test sur la vraie chaîne, face à un
`MockWebServer` qui répond comme le serveur.

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
contrat de l'API et porte un état aujourd'hui inatteignable — **son retrait est
décidé**, voir « La suite ».

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

## Le lot 4, le transcodage : terminé

Le choix de la qualité (#51), le cache d'un transcodage inachevé (#52), le
retrait du grisé (#53), **le déplacement dans un morceau transcodé (#54)**, le
429 (#89), le profil *Automatique* (#91) et la reprise après une coupure (#93)
sont faits. Les décisions avaient été **arrêtées avec l'utilisateur le 11/09**,
après l'avis d'un agent extérieur.

**Les règles du déplacement sont dans `docs/deplacement-dans-un-transcodage.md`**
— R1 à R30, citées par le code et les tests ; celles du profil *Automatique* dans
`docs/qualite-automatique.md` (Q1 à Q9). Y retourner avant d'y toucher : R16 (la
reprise d'une position sauvegardée) est une règle pour plus tard, aucune reprise
n'existant encore. Ce que la note ne couvre pas, ou n'éprouve pas, est listé à sa
fin : le cache des segments, l'annulation d'un saut en attente quand le lecteur
est relâché, un 429 sur la piste suivante pendant son préchargement.

**Côté serveur, l'effet de bord est réglé :** un transcodage abandonné par un
saut n'était jamais mis en cache — `InstaZDLL/waveflow-server#185`, **fermée le
11/09** par la PR serveur #189, déjà dans la 2.0.0-beta.1. Le serveur finit
désormais de remplir le cache en arrière-plan après un saut.

Le profil *Automatique* suit le caractère **facturé ou non** du réseau, pas le
Wi-Fi contre le cellulaire : l'Ethernet suit le Wi-Fi, un Wi-Fi déclaré payant
suit le mobile. Pas de profil « itinérance » pour l'instant.

### L'en-tête du lecteur

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

## Acceptés le 11/09, pour après le lot 4

1. **La recherche vocale Android Auto — acceptée.** « Joue tel album » ne
   fonctionne pas : le lint `MissingIntentFilterForMediaSearch` est
   **rétrogradé en `warning`** dans `app/lint.xml`, avec sa justification.
   Déclarer `MEDIA_PLAY_FROM_SEARCH` sans servir la recherche ouvrirait une porte
   sur une pièce vide. La première version cherche dans la **bibliothèque
   locale** — l'arbre de la #36 ne contient qu'elle, le catalogue serveur en a été
   écarté à dessein — sur le moteur de recherche existant plutôt qu'une seconde
   implémentation : titre, artiste, album, puis le meilleur résultat. D'après la
   documentation de Media3, **à vérifier** : la demande arrive par
   `onAddMediaItems`, avec `requestMetadata.searchQuery` et sans `mediaId`, là où
   `BrowseCallback.addMediaItems` ne résout aujourd'hui que par identifiant.
2. **Issue #32 — publication sur F-Droid — acceptée, implémentée plus tard.** Le
   terrain est favorable : GPLv3, aucune dépendance propriétaire (ni Play
   Services ni Firebase), pas de `signingConfig` release — F-Droid construit et
   signe lui-même. Restent les métadonnées `fdroiddata` et la reproductibilité du
   build. Rien n'a encore été répondu sur l'issue.

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

- **Rien ne se commit sur `main`** : branche + revue CodeRabbit (sur une MR
  GitLab, voir « Le mode orchestration ») + PR. *(Ce document fait exception, à
  la demande explicite de l'utilisateur.)*
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
