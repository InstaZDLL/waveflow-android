# La recherche vocale (« joue tel album »)

Android Auto, et l'Assistant sur le téléphone, transmettent une phrase plutôt
qu'un identifiant : « joue Nevermind », « mets du Nirvana ». Cette note fixe ce
que l'application en fait. Le code et les tests citent ses règles par leur
numéro.

## Par où la demande arrive

Vérifié dans les sources de media3 1.11.1 (`MediaSessionLegacyStub`,
`MediaSession.Callback`) :

- Android Auto passe par la session héritée : `onPlayFromSearch(query, extras)`
  (ou `onPrepareFromSearch`). Media3 en fait **un seul `MediaItem`** dont le
  `mediaId` est **vide** (`MediaItem.DEFAULT_MEDIA_ID`, et non `null`), sans URI,
  avec `requestMetadata.searchQuery` et `requestMetadata.extras`. Il le remet à
  `onSetMediaItems`, dont l'implémentation par défaut appelle
  `onAddMediaItems` — donc `BrowseCallback.addMediaItems`. Si la résolution
  aboutit, il pose la file et la prépare ; il ne la lance que pour
  `onPlayFromSearch`, pas pour `onPrepareFromSearch`.
- Un `MediaController` de Media3 peut poser le même élément par
  `setMediaItem` : il emprunte le même chemin.
- L'Assistant du téléphone, lui, lance l'**activité** qui déclare l'action
  `android.media.action.MEDIA_PLAY_FROM_SEARCH`, avec la phrase dans
  `SearchManager.QUERY` et les mêmes compléments.

Les compléments sont ceux de `MediaStore` : `EXTRA_MEDIA_FOCUS` dit ce que
l'utilisateur a demandé (un album, un artiste, un morceau…),
`EXTRA_MEDIA_ALBUM`, `EXTRA_MEDIA_ARTIST` et `EXTRA_MEDIA_TITLE` les noms qu'il
a prononcés. Ils peuvent manquer : seule la phrase est garantie.

## Les règles

**R1 — La bibliothèque de l'appareil, et elle seule.** La recherche lit
l'instantané qui sert déjà l'arbre de navigation (`BrowseTree`), pour la même
raison que lui : le catalogue du serveur se demande par le réseau, et une
réponse lente ou vide au volant est pire qu'une absence. Les playlists n'y sont
pas cherchées : le moteur existant ne les couvre pas.

**R2 — Le moteur existant, pas un second.** Les comparaisons passent par
`Library.search` (`model/Search.kt`) : sans casse ni accents, les débuts de
texte avant les occurrences au milieu. Un morceau y ressort par son titre, son
artiste ou son album.

**R3 — Une demande vocale se reconnaît à son élément** : pas d'URI, un
`mediaId` vide et un `searchQuery` renseigné. Un élément qui porte un
`mediaId` se résout comme avant, par identifiant ; un élément qui porte une
URI passe tel quel.

**R4 — Une phrase vide joue toute la bibliothèque**, dans l'ordre de l'onglet
Titres. C'est « mets de la musique » : ne rien jouer serait la pièce vide que
le filtre d'intention promet de ne pas ouvrir.

**R5 — Un album demandé joue l'album.** Sous le focus album, on cherche le nom
d'album prononcé (`EXTRA_MEDIA_ALBUM`), à défaut la phrase entière. L'album
dont le titre **est** ce nom est joué, à défaut le premier album du moteur ; ses
pistes dans l'ordre. Le moteur seul ne suffit pas : il compare aussi l'artiste
d'un album, et « Pinkerton » et « Weezer », tous deux de Weezer, y sortent au
même rang — « Pinkerton » d'abord, par l'ordre alphabétique. Les albums
éponymes sont courants.

**R6 — Un artiste demandé joue l'artiste.** Même chose sous le focus artiste,
avec `EXTRA_MEDIA_ARTIST` : l'artiste dont le nom **est** ce nom, à défaut le
premier du moteur ; toutes ses pistes.

**R7 — Un morceau demandé joue les morceaux trouvés**, avec
`EXTRA_MEDIA_TITLE`, à défaut la phrase : ceux dont le titre **est** la phrase
d'abord, puis les autres dans l'ordre du moteur, pour que la voiture ne se
taise pas après une seule piste. Le moteur seul ne suffit pas : il classe
ensemble tout ce qui commence par la phrase, titre, artiste et album
confondus, et « Nevermind » y suivrait « Come As You Are », de l'album du même
nom.

**R8 — Sans focus, le meilleur résultat**, dans cet ordre :

1. un album dont le titre **est** la phrase ;
2. un artiste dont le nom **est** la phrase ;
3. un morceau dont le titre **est** la phrase — les morceaux trouvés, comme
   en R7 ;
4. un album dont le titre **commence par** la phrase ;
5. un artiste dont le nom **commence par** la phrase ;
6. les morceaux trouvés par le moteur, dans son ordre.

« Est » et « commence par » s'entendent sur la forme comparable de R2. À
égalité, l'album passe avant l'artiste, et l'artiste avant le morceau : une
phrase qui nomme un album le désigne plus sûrement qu'elle ne désigne un titre
qui lui ressemble. Un focus que rien ne satisfait (R5 à R7), ou que la
recherche ne couvre pas (une playlist, un genre), retombe sur cette règle avec
la phrase entière.

**R9 — Rien trouvé : rien ne change.** Rendre une liste vide viderait le
lecteur : Media3 poserait une file sans rien. Le futur échoue, et Media3 ignore
alors la demande (« the session is free to ignore these requests ») : la file
reste, et son état de lecture aussi — une file en pause n'est pas relancée.
Rien n'est dit à l'utilisateur pour l'instant.

**R10 — Le téléphone relaie à la session.** L'activité qui reçoit
`MEDIA_PLAY_FROM_SEARCH` ne cherche rien elle-même : elle transmet la phrase et
ses compléments au service par le contrôleur de l'application, qui les envoie
dans une **commande de session** propre (`app.waveflow.PLAY_FROM_SEARCH`). La
session la résout par le même chemin que la voiture, puis remplace la file, la
prépare et la lance — comme Media3 le fait pour `onPlayFromSearch`, et **seulement
si la résolution a abouti**. Envoyer à la suite `setMediaItem`, `prepare` et
`play` ne le permettrait pas : le premier échoue sans rien trouver (R9), et
`play` relancerait quand même la file en pause. La commande n'est offerte
qu'aux contrôleurs de confiance, dont l'application ; les autres gardent les
commandes par défaut de Media3. La demande attend que la liaison au service
soit établie — elle arrive souvent au démarrage à froid, avant elle.

L'activité ne relaie que les demandes neuves. Recréée (rotation, processus
tué) ou rouverte depuis les récents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`),
elle reçoit encore l'intention qui l'a lancée : la rejouer remplacerait la file
posée depuis. Une intention dont les compléments ne se lisent pas est écartée
aussi : l'activité est exportée, une autre application peut y glisser une
valeur inconnue, et la lire ferait tomber l'activité.

**R11 — Une demande attend la première lecture de la bibliothèque.** Au
démarrage à froid, la liaison qui porte la demande est aussi celle qui crée le
service, et le MediaStore n'a pas encore répondu : chercher tout de suite ne
trouverait rien. La résolution attend donc que l'arbre porte une bibliothèque
lue. L'attente se termine toujours : sans la permission audio, la lecture
échoue, et la bibliothèque se tient alors pour lue — vide.

**R12 — Les compléments départagent les homonymes.** Sous le focus album, un
artiste isolé par l'hôte (`EXTRA_MEDIA_ARTIST`) écarte les albums d'autres
artistes : « Greatest Hits » de Foo Fighters ne joue pas celui de Queen. Sous
le focus morceau, l'artiste et l'album isolés écartent de même les autres
morceaux. Si l'indice n'écarte pas tout, il s'applique ; s'il écarte tout, il
est ignoré — l'hôte découpe parfois mal la phrase, et mieux vaut un homonyme
que rien.

## Ce qui n'est pas fait

- La **liste de résultats** que la voiture affiche quand on tape une recherche
  (`onSearch`, `onGetSearchResult`) : seule la lecture directe est servie.
- **Une phrase sans focus qui mêle album et artiste** — « Nevermind Nirvana » —
  ne trouve rien : le moteur de R2 compare la phrase entière à chaque champ
  séparément. L'hôte isole d'ordinaire ces noms dans ses compléments (R5, R6,
  R12) ; sinon, il faudrait chercher mot à mot, ce qui changerait aussi l'écran
  de recherche, qui partage le moteur.
- **Aucun message** quand rien n'est trouvé (R9).
- Une demande qui attend la première lecture de la bibliothèque (R11) s'applique
  à son terme, même si une autre file a été posée entre-temps — la fenêtre est
  celle du démarrage à froid, le temps d'une requête au MediaStore.
- **Non éprouvé** : la commande réservée aux contrôleurs de confiance (R10).
  Sous Robolectric, tout contrôleur l'est. De même, l'intention illisible n'est
  éprouvée qu'avec un `Bundle` mal formé lu sous l'API 36, pas avec le
  `Serializable` inconnu qui ferait tomber les API 26 à 32.
- Rien de tout cela n'a été vu **dans une vraie voiture** ni sur le DHU : tout
  ce qui est éprouvé l'est sous Robolectric, sur la vraie chaîne Media3.
