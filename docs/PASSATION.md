# Passation

Document vivant : chaque agent qui prend la suite le relit d'abord, et le met à
jour avant de partir. Il dit **où en est le chantier et ce qui vient ensuite** —
pas l'historique, que `git log` raconte mieux.

Dernière mise à jour : **2026-09-06**, sur `main` = `1c203f6`.

## État du dépôt

- `main` = `1c203f6`, arbre propre, **aucune PR ouverte**, aucune branche en cours.
- **346 tests verts**, CI verte (workflow `Build & test`, ~4 min 45 s).
- **Aucun avertissement de compilation.** C'est une propriété qu'on tient, pas un
  hasard — voir le piège `textReport` plus bas avant d'en supprimer un.
- Gradle 9.7.1, AGP 9.3.2, OkHttp 5.5.0, media3 1.11.0.
- Baseline Detekt : 16 entrées. **Elle ne doit que rétrécir.**

## Le chantier : la refonte v2

Six lots, dans cet ordre :

1. Réglages / DataStore — **fait**
2. Navigation + identité — **fait**
3. Lecteur : file d'attente, minuterie, vitesse, boucle A-B — **aux deux tiers**
4. Transcodage (remonté du 6ᵉ rang : meilleur rapport travail/effet, le serveur
   est déjà prêt)
5. Paroles
6. Audio avancé (EQ, ReplayGain, gapless, sortie)

Le fondu enchaîné **est hors plan** : Media3 ne le fournit pas, il faudrait deux
lecteurs ou une chaîne audio maison.

## Ce que la dernière session a livré

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
était câblé sur l'état figé et ne bougeait plus. La vitesse de lecture et les
bornes A-B liront le même état — il faudra soit les publier sur un flux propre,
soit les relire à l'affichage, comme `SleepTimerSheet` le fait avec un tic local
qui ne vit que le temps où la feuille est ouverte.

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

Rien n'existe encore — ni `setPlaybackSpeed`, ni `PlaybackParameters`, aucune
trace d'A-B dans `PlaybackController`.

### a. La vitesse de lecture — à faire en premier

La plus simple, et de loin. Media3 la fournit d'un appel ; elle se pose dans
`PlaybackController` comme les autres commandes.

Deux points de conception :

- **Elle se persiste.** On ne veut pas retomber à ×1 à chaque relance quand on
  écoute un podcast à ×1,5. Le `PreferencesStore` est là pour ça.
- **Elle s'affiche.** Une vitesse active et invisible est un défaut qu'on cherche
  pendant vingt minutes. Voir le piège n° 1 pour la publier correctement.

### b. La boucle A-B — ensuite, nettement plus retorse

Media3 **n'a pas** de « répéter entre deux points ». Il faut échantillonner la
position et rembobiner au passage de B, ce qui place le mécanisme **dans le
service**, pas dans l'interface. Ce que Robolectric peut en prouver est déjà
borné : `isPlaying` à `true` et l'échantillonnage de position restent hors de
portée faute de codec.

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
- `./gradlew ktlintFormat` **avant chaque commit**.
- **Français** pour l'interface, les messages de commit, la KDoc, les
  commentaires et les noms de tests (`build-and-test.yml` excepté).
- **Toujours rebaser sur `main` avant d'ouvrir la PR.** La CI construit la
  branche, *pas le résultat de fusion* : deux branches vertes chacune de leur
  côté peuvent fusionner en un `main` qui ne compile pas. C'est arrivé le 18/08
  avec media3 1.5.1 → 1.11.0. Après une fusion suivie d'un Dependabot, relancer
  `ktlintCheck detekt testDebugUnitTest` sur `main`.
