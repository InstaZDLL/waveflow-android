# La suggestion du jour

Règles arrêtées **avant le code**, comme celles du déplacement dans un
transcodage. Chaque règle dit ce qu'on doit observer, pour pouvoir s'éprouver
par un test. Une règle qui change se change ici d'abord.

L'algorithme est **porté du bureau** : `waveflow_core::home_suggestion`
(`src-tauri/crates/core/src/home_suggestion.rs`) pour l'arithmétique, ses
requêtes dans `commands/home_suggestion.rs`, l'interface dans
`src/lib/tauri/homeSuggestion.ts` et `useHomeSuggestion.ts`. Ce qui ne se
transpose pas est écrit à la fin, dans « Les écarts avec le bureau ». Issue #95.

## Le problème

L'accueil du bureau pose, sous son canevas, une bande : **un morceau et la
raison pour laquelle on le propose**. Le client Android n'avait pas l'algorithme
qui la nourrit ; l'accueil de la #94 l'avait donc laissée de côté plutôt que d'en
inventer un.

## Ce qu'Android a pour la nourrir

- **L'historique d'écoute** (`play_history`) : **une ligne par piste**, son
  `mediaId`, le nombre d'écoutes et la date de la dernière. Ce n'est pas un
  journal d'événements comme le `play_event` du bureau. Une écoute y est notée
  après 20 secondes écoutées (`DELAI_ECOUTE_MS`) ; un morceau sauté avant n'y
  laisse rien.
- **La bibliothèque de l'appareil** (`LibraryStore`) : chaque morceau, son
  artiste, son album, sa date d'ajout au MediaStore.
- L'historique note **les deux sources** : `local:<id>` pour un fichier de
  l'appareil, `remote:<id>` pour une piste du serveur. La bibliothèque, elle,
  ne connaît que l'appareil.

## Les règles

### Le tirage

**S1. Trois sortes, qui tournent.** *Oubliée* (un morceau beaucoup écouté puis
délaissé), *Dans l'esprit* (un morceau d'un artiste écouté ces jours-ci),
*Jamais écoutée* (un morceau jamais lancé). Pour le jour `j` et `e` appuis sur
« Autre suggestion », la sorte essayée d'abord est la `((j + e) mod 3)`ᵉ de
cette liste — modulo euclidien, jamais négatif —, puis les deux suivantes dans
l'ordre circulaire, en repli quand la première n'a rien à offrir (une
bibliothèque neuve n'a pas de favori oublié). C'est `kind_order` du bureau.

**S2. Le jour est celui du calendrier de l'utilisateur.** Le nombre de jours
depuis le 1ᵉʳ janvier 1970 de la **date locale** (`LocalDate.toEpochDay`), pas
de la date UTC — comme `localDayIndex` du bureau. Il se lit sur une **horloge
injectée** ; à minuit, un accueil resté ouvert passe de lui-même à la suggestion
du nouveau jour, sans attendre un retour au premier plan. Le fuseau est relu à
chaque lecture de l'horloge, et l'attente de minuit dure une heure au plus :
après un changement de fuseau ou d'heure, le jour suit dans l'heure.
« Autre suggestion » vaut pour le jour que la bande montre.

**S3. Le tirage est stable.** Parmi les `n` pistes de la courte liste, le rang
tiré est `splitmix64(graine) mod n`, la graine mêlant le jour, le nombre d'appuis
et la sorte, en arithmétique non signée sur 64 bits. C'est `pick_index` du
bureau, au bit près : le même jour, le même nombre d'appuis et la même sorte
donnent toujours la même piste.

**S4. La courte liste.** Les 20 premières pistes de la sorte, les meilleures
d'abord, **deux au plus par artiste** — une seule pour *Jamais écoutée* —, pour
que l'artiste le plus écouté ne soit pas toutes les suggestions. Une piste sans
artiste n'est jamais plafonnée. C'est `shortlist` du bureau.

Les candidats de *Oubliée* et de *Jamais écoutée* sont pris dans les 400
premiers du classement, comme les requêtes du bureau (`POOL_LIMIT`).

### Les sortes

**S5. *Oubliée* : écoutée au moins 3 fois, et pas depuis 60 jours.** Classée
par nombre d'écoutes décroissant, puis la dernière écoute la plus ancienne
d'abord, puis l'identifiant de la piste. La raison dit le nombre d'écoutes et le
mois de la dernière ; sans l'un ou l'autre (un fichier abîmé), pas de raison.

**S6. *Dans l'esprit* : un morceau d'un artiste écouté ces deux dernières
semaines, qu'on n'a pas entendu depuis 30 jours.** Les artistes récents sont les
5 qui comptent le plus de pistes **dont la dernière écoute tombe dans les
14 derniers jours** ; à égalité, l'identifiant d'artiste le plus petit. Les
candidats sont leurs pistes dont la dernière écoute date de plus de 30 jours, ou
qui n'ont jamais été écoutées, classées par identifiant de piste. La raison
nomme l'artiste récent dont le morceau vient.

**S7. *Jamais écoutée* : aucune trace dans l'historique.** Une seule par
artiste, la dernière ajoutée (à égalité, l'identifiant le plus petit). Classées
par nombre d'écoutes de leur artiste décroissant — la somme des écoutes de ses
pistes —, puis la plus récemment ajoutée, puis l'identifiant. La raison nomme
l'artiste quand on l'a déjà écouté, sinon la date d'ajout ; un fichier que le
MediaStore ne date pas n'a pas de raison.

### La source

**S8. Seule la bibliothèque de l'appareil est suggérée.** Une piste est
*disponible* quand elle est dans la bibliothèque chargée. Les lignes
`remote:` de l'historique sont ignorées, comme partout sur l'accueil : il
faudrait redemander chaque piste au catalogue du serveur, et l'accueil ne parle
qu'à l'appareil — il s'affiche hors ligne comme connecté. Pendant un
rechargement du MediaStore, la bibliothèque garde ses morceaux, et la bande
reste.

### L'épinglage

**S9. Une suggestion tirée est gardée pour sa journée.** Le jour, le nombre
d'appuis, la sorte, la piste et ce que dit la raison sont écrits sur disque. Ils
reviennent tels quels, raison comprise, **tant que la piste reste dans la
bibliothèque** — même une fois écoutée, quand elle ne serait plus tirée dans sa
sorte, et même après un redémarrage de l'application. Une piste disparue fait
tirer de nouveau, et le nouveau tirage remplace l'ancien.

**S10. Le tirage d'un autre jour est périmé.** Il compte pour zéro appui et rien
d'épinglé : le jour recommence à sa première suggestion.

**S11. « Autre suggestion » ajoute un appui et efface l'épinglée**, en une
seule écriture faite sur ce que le fichier contient au moment d'écrire, et non
sur l'état affiché : deux appuis rapides valent deux appuis. Un nouveau tirage ne
s'écrit que si le fichier porte encore le jour, le nombre d'appuis et l'épinglée
d'après lesquels il a été fait : un appui arrivé entre le tirage et son écriture
n'est pas écrasé par elle, pas plus qu'un tirage du lendemain — fait juste après
minuit — par celui de la veille. Un tirage daté d'un jour que l'horloge n'a pas
encore atteint, d'un côté comme de l'autre, est tenu pour périmé : enregistré, il
ne bloque rien ; à écrire, il ne s'écrit pas.

**S12. Une écriture en échec ne fait rien tomber.** Elle est notée au journal, et
retentée au calcul suivant. La bande garde ce qu'elle montre : l'accueil retient
aussi en mémoire la suggestion qu'il a tirée pour ce jour et ce nombre d'appuis,
et la préfère tant qu'il vit — sans quoi la piste suggérée, une fois écoutée, ne
serait plus tirée —, sauf quand le fichier porte une épinglée pour ce jour et ce
nombre d'appuis : celle-là passe avant. « Autre suggestion » reste sans effet
tant que le fichier refuse d'écrire.

### L'affichage

**S13. Rien à suggérer, pas de bande.** Ni cadre vide ni message : une
bibliothèque vide n'a rien à proposer, et un cadre vide se lit comme une panne.

**S14. La bande se pose sous le canevas**, avant les ajouts récents, comme sur le
bureau : le surtitre « Suggestion du jour », la sorte dans la voix éditoriale,
le titre, l'artiste et l'album (tu quand il porte le nom du morceau), la raison
en troisième encre, puis « Écouter » et, comme le bureau en français, « Autre
suggestion ».

**S15. Les mots d'une suggestion ne changent pas dans la journée.** Chaque sorte
a trois tournures, chaque raison deux ou trois, celles du bureau en français. La
tournure est la `((((j + e) × 7 + id × 13 + sel) mod n) + 1)`ᵉ — positive même
pour un identifiant négatif —, `sel` valant 0 pour la sorte, 1 pour *Oubliée*,
2 pour *Dans l'esprit*, 3 et 4 pour les deux raisons de *Jamais écoutée*.

**S16. « Écouter » joue la piste, puis la bibliothèque à sa suite**, comme un
appui sur un morceau de l'accueil.

### Les plafonds

**S17. L'historique lu est borné.** Au plus les 20 000 lignes `local:` les plus
récemment écoutées, en une requête. Au-delà, les plus anciennes sont ignorées :
une piste écoutée seulement avant elles pourrait passer pour jamais écoutée.
La bibliothèque est déjà en mémoire et n'a pas d'autre borne que l'appareil :
chaque sorte filtre puis trie ses candidats en entier, hors du fil principal.
Ceux de *Oubliée* et de *Jamais écoutée* se coupent ensuite à 400 (S4) ; *Dans
l'esprit* n'a pas de coupe, le bureau ne coupant les siens (5 000) que pour
borner sa requête, et la courte liste s'arrêtant de toute façon à 20.

## Les vecteurs de test

Ceux de `home_suggestion/tests.rs` sont repris, mêmes entrées et mêmes
sorties : la rotation par jour et par appui, la stabilité du tirage et son
domaine, le plafond par artiste. Le bureau n'y vérifie que des propriétés ; pour
tenir « au bit près » (S3), des **valeurs exactes** de `kind_order` et de
`pick_index` ont été calculées en compilant ses fonctions telles quelles, et le
test Kotlin les attend.

Le vecteur de `vibe_distance` n'est pas repris : voir l'écart E3.

## Les écarts avec le bureau

**E1. Pas de journal d'écoutes.** Le bureau compte les écoutes d'un artiste
*dans* les 14 derniers jours ; Android ne sait que la dernière écoute de chaque
piste. S6 compte donc les pistes de l'artiste écoutées dans la fenêtre — une
écoute au moins chacune, ce qui minore le compte du bureau sans en changer
l'idée. Et seulement les pistes encore sur l'appareil, pour S6 comme pour les
écoutes d'artiste de S7 : d'un fichier effacé, on ne sait plus l'artiste.

**E2. Une écoute compte à 20 secondes, et un morceau sauté n'est pas noté.** Le
bureau compte une écoute à 15 secondes ou au bout du morceau, et note aussi les
sauts : un morceau sauté hier n'y est ni *oublié* ni *jamais écouté*. Sur Android
il peut l'être, l'historique n'en sachant rien.

**E3. Ni énergie, ni tempo, ni artistes similaires.** Le bureau rapproche les
candidats de *Dans l'esprit* de l'énergie et du tempo écoutés ces jours-ci
(`vibe_distance`) et élargit aux artistes similaires de son cache. Android n'a
aucune analyse des pistes ni ce cache : la distance serait la même pour toutes,
et le bureau classe alors par identifiant, ce que fait S6. `vibe_distance` n'est
pas porté — une fonction qu'aucune donnée n'alimente serait du code mort.

**E4. Pas de radio.** « Écouter » lance sur le bureau la piste suivie de titres
proches ; Android n'a pas de radio, il joue la piste puis la bibliothèque (S16).

**E5. Pas de profils.** Le bureau garde un tirage par profil ; Android n'en a
qu'un.

**E6. L'artiste inconnu.** Le MediaStore donne un identifiant même à l'artiste
d'un fichier sans tag (`<unknown>`). Compté comme un artiste, il prendrait deux
places de la courte liste pour tous les fichiers non tagués ; il est donc tenu
pour *sans artiste*, comme le `primary_artist` nul du bureau : jamais plafonné
(S4), jamais parmi les artistes récents (S6), et dans S7 chacun de ses morceaux
fait son propre groupe, à zéro écoute d'artiste.
