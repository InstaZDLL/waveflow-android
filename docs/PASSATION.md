# Passation

Document vivant : chaque agent qui prend la suite le relit d'abord, et le met à
jour avant de partir. Il dit **où en est le chantier et ce qui vient ensuite** —
pas l'historique, que `git log` raconte mieux.

Dernière mise à jour : **2026-09-06**, sur `main` = `59db1fd`.

## État du dépôt

- `main` = `59db1fd`. Une PR ouverte : **la vitesse de lecture**, branche
  `feat/vitesse-lecture`.
- **364 tests verts**, CI verte (workflow `Build & test`, ~4 min 45 s).
- **Aucun avertissement de compilation.** C'est une propriété qu'on tient, pas un
  hasard — voir le piège `textReport` plus bas avant d'en supprimer un.
- Gradle 9.7.1, AGP 9.3.2, OkHttp 5.5.0, media3 1.11.0.
- Baseline Detekt : 16 entrées. **Elle ne doit que rétrécir.**

## Le chantier : la refonte v2

Six lots, dans cet ordre :

1. Réglages / DataStore — **fait**
2. Navigation + identité — **fait**
3. Lecteur : file d'attente, minuterie, vitesse, boucle A-B — **il ne reste que
   la boucle A-B**
4. Transcodage (remonté du 6ᵉ rang : meilleur rapport travail/effet, le serveur
   est déjà prêt)
5. Paroles
6. Audio avancé (EQ, ReplayGain, gapless, sortie)

Le fondu enchaîné **est hors plan** : Media3 ne le fournit pas, il faudrait deux
lecteurs ou une chaîne audio maison.

## Ce que la dernière session a livré

**La vitesse de lecture** (branche `feat/vitesse-lecture`).

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

### 3. Le piège `textReport`

`textReport` et `textOutput` sont dépréciés **ensemble** depuis AGP 9. Les
retirer sèchement supprime l'avertissement **mais casse ce qu'ils tenaient** :
les remontées du lint n'arrivent plus au journal, seulement dans un fichier que
personne n'ouvre. Une tâche `afficherRapportLint` liée par `finalizedBy` les
réimprime — `finalizedBy` et **non** `doLast`, parce qu'une action de tâche est
sautée quand la tâche échoue, c'est-à-dire précisément quand le lint a trouvé
une erreur. Ne pas rouvrir.

## La suite : solder le lot 3

La vitesse est faite. Reste la boucle A-B — aucune trace dans
`PlaybackController`.

### La boucle A-B — tout ce qui reste, et le plus retorse

Media3 **n'a pas** de « répéter entre deux points ». Il faut échantillonner la
position et rembobiner au passage de B, ce qui place le mécanisme **dans le
service**, pas dans l'interface. Ce que Robolectric peut en prouver est déjà
borné : `isPlaying` à `true` et l'échantillonnage de position restent hors de
portée faute de codec.

**L'en-tête du lecteur est plein.** Quatre boutons y tiennent déjà — réduire,
vitesse, veille, file — et la colonne du titre s'en trouve serrée sur un écran
étroit. L'A-B n'y entrera pas sans un menu de débordement qui regrouperait
veille, vitesse et bornes. C'est le moment de le poser, pas après.

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

## Un compromis à solder dans le lot 3 ou après

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
  qui passe des deux côtés est un test creux, et il y en a six formes connues.
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
