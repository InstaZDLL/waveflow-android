# WaveFlow Pocket — Sonic Editorial

La direction de design mobile de WaveFlow Android, et le plan du prototype qui
l'éprouve. Nom de travail interne, pas un nom de produit : l'application reste
« WaveFlow ». Devise de la direction : *Your music. In motion.*

Le brief de l'utilisateur (27/11) en est le cahier des charges. Son constat :
**l'application mobile n'a pas d'âme** — on a recopié le bureau sur le
téléphone. Ce document dit ce que doit être sa version mobile, écran par écran,
puis comment le prototype la montre **sans toucher à l'application de
production**.

État : **Now Playing et le mini-lecteur** sont prototypés et validés (28/11) ;
**l'accueil, la bibliothèque et la page d'un album** le sont à leur tour, et
attendent la revue de l'utilisateur avant toute intégration.

## Le diagnostic

L'application actuelle porte fidèlement Fluid Editorial et Liquid Aurora Glass.
C'est précisément son défaut : ces deux langues ont été écrites pour une
fenêtre de bureau — un canevas en douze colonnes, un rail, un panneau, une
barre de lecteur — et Android en a hérité les **pièces**, pas l'**intention**.

| Écran        | Ce que l'utilisateur voit                                       | La cause                                                                                    |
| ------------ | --------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Accueil      | trois pochettes et beaucoup de vide                             | le canevas du bureau (salut, compteurs, grand titre) ne contient presque pas de musique      |
| Bibliothèque | des onglets soulignés serrés, une hiérarchie plate              | la rangée d'onglets de `PrimaryTabRow`, l'apparence Material par défaut                     |
| Album        | une pochette petite, une composition rigide                     | `DetailHeader` partagé par cinq pages : une fiche, pas une page d'album                     |
| Artistes     | une liste sans personnalité                                     | une grille de disques sans hiérarchie ni repli quand la photo manque                       |
| Now Playing  | des contrôles teintés par la pochette, du vide mal réparti      | l'accent pris à la pochette rend le bouton violet sur une pochette violette ; deux `weight(1f)` partagent le vide en parts égales au lieu de le donner à la pochette |

Et surtout : **aucun langage commun entre les écrans**. Chaque écran a été
recomposé selon le bureau, mais rien n'est propre au téléphone — ni un geste, ni
une densité, ni une signature que l'on reconnaisse au pouce.

## Principes

1. **La musique d'abord, au pouce.** La pochette porte presque toute la richesse
   visuelle ; les actions fréquentes vivent dans le tiers bas de l'écran, à
   portée d'une main.
2. **Un vert, une onde.** Un seul accent — l'émeraude de WaveFlow — et une seule
   ornementation — l'onde. Les deux disent « WaveFlow » sur une capture, quelle
   que soit la pochette.
3. **La pochette éclaire, elle ne teint pas.** Ses couleurs donnent une
   atmosphère *locale* (derrière elle, en haut de l'écran qui la montre) ; elles
   ne prennent jamais la place de l'accent sur une commande.
4. **Profondeur par le ton, trois niveaux.** Le canevas (plat, ce qui défile),
   les surfaces de contenu (un ton au-dessus), les surfaces flottantes (feuilles,
   menus). Pas de verre, pas de bordure, une seule ombre : sous une grande
   pochette.
5. **Natif d'abord.** Les comportements Material 3 (feuilles, retour prédictif,
   bord à bord, TalkBack, cibles de 48 dp, échelle de police) ; aucune de ses
   apparences par défaut — ni pastille d'indicateur, ni violet, ni cartes dans
   des cartes.
6. **Le vide se mérite.** Un espace vide doit servir la pochette ou le pouce.
   Une page qui manque de musique se remplit d'actions utiles, pas d'air.
7. **Un mouvement qui répond, jamais qui décore.** Le mouvement suit un geste ou
   un changement d'état. Rien ne tourne pour rien ; sous « Supprimer les
   animations », tout tient encore.

## Ce que les sources ont appris

Les lecteurs du workspace ont été lus pour leurs **solutions**, pas pour leur
apparence. Aucun code ni aucune ressource n'en est repris.

| Source | Ce qui est retenu | Pourquoi | Ce qui est écarté |
| --- | --- | --- | --- |
| **Bureau — Fluid Editorial** | un accent dépensé avec soin ; la profondeur par le ton ; l'onde comme marque *fonctionnelle* (elle mesure, elle souligne) ; la courbe unique `(0.22, 1, 0.36, 1)` ; « la pochette éclaire la page » | c'est la parenté : la même marque, le même goût de la retenue | le canevas en colonnes, le rail, le panneau, Playfair à chaque page : des pièces de fenêtre |
| **Bureau — Liquid Aurora Glass** | un clair qui n'est jamais blanc pur | un clair qui a une matière | le verre, les trois nappes pastel qui dérivent : le brief écarte le verre, et sur un téléphone un flou en défilement coûte des images |
| **waveflow-ios** | laisser les couleurs sémantiques de la plateforme faire le travail ; l'atmosphère de la pochette qui **descend du haut** et s'éteint ; l'en-tête léger « EN LECTURE » | la même marque sur un téléphone : l'iOS n'a jamais copié le bureau | le lecteur forcé en sombre (`preferredColorScheme(.dark)`) : Pocket honore le clair jusque dans le lecteur |
| **CrystalMusic** (Compose, M3 Expressive) | la ligne qui joue se signale **dans la ligne** ; un mini-lecteur qui dit l'avancement d'un regard | ce sont des solutions de téléphone, pas de fenêtre | les couleurs dynamiques violettes, la barre de navigation en capsule flottante, le titre d'écran géant centré sur du vide |
| **sazanami** (Compose, hors ligne) | le **balayage horizontal sur la pochette** pour changer de morceau, avec un seuil ; le geste en complément, jamais seul chemin | le geste le plus naturel d'un lecteur au pouce | la multiplication des habillages (cassette, molette…) : le brief veut **une** identité |
| **Resonus** (React Native) | à l'accueil, les carrousels de grandes pochettes où une troisième dépasse — l'invitation à faire défiler ; des pastilles pour les facettes de la bibliothèque | densité et invitation sans texte | les pastilles grises à icône générique, le cluster d'icônes en haut de l'accueil |
| **Fintunes** (jellyfin-audio-player) | un clair *teinté* par la pochette, à peine ; le titre aligné à gauche sous la pochette ; le temps **restant** en négatif (« −4:19 ») | c'est ce qui rend un clair musical sans verre | les commandes en glyphes noirs pleins, sans accent ni hiérarchie |
| **lokal-mobile** (Flutter) | sa propre note de parité du lecteur : un « pavé de lecture fluorescent » écrase l'écran ; une progression plus fine ; une ligne de métadonnées compacte | l'erreur décrite est celle qui guette un accent vif | le mini-lecteur et la barre en verre flottant au-dessus des pochettes |

## Couleur

Deux apparences d'**une** identité, pas deux matières. Elles partagent l'accent
(à son palier), la teinte des neutres — un gris très légèrement vert-bleu, celui
de l'eau profonde — et les règles d'usage. Les contrastes sont **mesurés**
(WCAG 2, `contrast` de `ui/theme/Oklch.kt`).

### Sombre — la direction principale

| Jeton | Valeur | Rôle | Contraste mesuré |
| --- | --- | --- | --- |
| `canvas` | `#0B1214` | la page, ce qui défile | — |
| `raised` | `#142023` | la barre de navigation | — |
| `surface` | `#1A282B` | une surface de contenu : le mini-lecteur, une mise en avant, une pastille | — |
| `floating` | `#22333A` | feuilles, menus, dialogues | — |
| `ink` | `#F1F6F4` | titres | 17,3:1 sur `canvas`, 12,0:1 sur `floating` |
| `ink2` | `#A4B3B4` | seconde ligne, corps | 8,7:1 sur `canvas`, 7,0:1 sur `surface`, 6,0:1 sur `floating` |
| `ink3` | `#8A9C9E` | étiquettes, durées, icônes au repos | 6,6:1 sur `canvas`, 5,3:1 sur `surface` |
| `accent` | `#20D39B` | l'action principale, ce qui est allumé | 9,8:1 sur `canvas`, 7,9:1 sur `surface` |
| `onAccent` | `#06231A` | l'encre posée sur l'accent | 8,6:1 sur `accent` |
| `hairline` | blanc 8 % | un séparateur, au besoin | — |

### Clair — un vrai mode, pas une inversion

| Jeton | Valeur | Rôle | Contraste mesuré |
| --- | --- | --- | --- |
| `canvas` | `#F5F6F2` | la page : un blanc cassé neutre, à peine chaud | — |
| `raised` | `#FFFFFF` | la barre de navigation : *plus* blanche que la page | — |
| `surface` | `#E4EEE9` | une surface de contenu — le mini-lecteur, une pastille : la menthe douce | — |
| `floating` | `#FFFFFF` | feuilles, menus — séparés par une ombre teintée | — |
| `ink` | `#142023` | titres — le `raised` du sombre : les deux modes partagent leurs neutres | 15,4:1 sur `canvas` |
| `ink2` | `#46585B` | seconde ligne | 6,9:1 sur `canvas`, 6,3:1 sur `surface` |
| `ink3` | `#586A6D` | étiquettes, durées | 5,2:1 sur `canvas`, 4,8:1 sur `surface` |
| `accent` | `#047857` | l'émeraude au palier 700 | 5,1:1 sur `canvas`, 4,6:1 sur `surface` |
| `onAccent` | `#FFFFFF` | l'encre sur l'accent | **5,5:1** — un libellé de bouton y tient (le palier 600 actuel : 3,77:1) |
| `hairline` | encre 8 % | un séparateur | — |

### Règles d'usage

- **L'accent ne décore jamais** : le bouton de lecture, une bascule allumée, la
  partie jouée de la progression, l'onde sous l'onglet ouvert, la ligne qui
  joue. Rien d'autre.
- **Une bascule allumée** prend l'accent **et** un point sous l'icône : l'état
  ne repose pas sur la couleur seule.
- **`ink3` ne se pose jamais sur `floating` en sombre** (4,0:1) : une feuille
  écrit sa troisième ligne en `ink2`.
- Material 3 reçoit ces jetons sur ses rôles, avec la même règle que la
  production : `secondary` et `tertiary` égaux à l'accent, pour qu'aucun
  composant ne trouve de seconde couleur.

### L'atmosphère de la pochette

La pochette donne **une seule nappe** de lumière, là où elle est montrée :

- **Now Playing** : une nappe elliptique centrée sur la pochette, qui déborde
  vers le haut de l'écran et s'éteint avant les commandes. La nappe prend le ton
  d'ensemble de la pochette (la nuance dominante de Palette), ramené à une
  clarté fixe — sombre en sombre, pâle en clair — pour que deux pochettes ne
  changent pas l'équilibre de l'écran.
- **Album** et **mise en avant de l'accueil** : la même nappe, derrière la
  pochette de l'en-tête (décision du 28/11).
- **Nulle part ailleurs.** Ni la bibliothèque, ni la recherche, ni les listes.

La nappe est **bornée** : son opacité est la plus forte qui garde `ink2` à
4,5:1 au cœur de la nappe (là où le titre peut passer). Pas de flou en temps
réel, pas de dérive : un dégradé radial peint une fois par pochette, fondu en
900 ms au changement de morceau.

## Typographie

**DM Sans seule**, la police variable déjà embarquée (licence OFL dans
`licences/polices/`), avec sa taille optique réglée sur la taille affichée.
Playfair Display italique reste réservée à **une** ligne de l'application : le
salut de l'accueil (décision du 28/11). Une seule famille rend la
hiérarchie par la graisse et la taille, pas par le contraste de deux voix.

| Rôle | Taille | Graisse | Interlettrage | Usage |
| --- | --- | --- | --- | --- |
| Titre d'écran | 32 sp | 640 | −0,03 em | « Bibliothèque », le nom d'un album |
| Mise en avant | 26 sp | 620 | −0,025 em | le titre du lecteur, la reprise de l'accueil |
| Section | 20 sp | 600 | −0,015 em | « Ajouts récents » |
| Titre de piste | 16 sp | 560 | 0 | une ligne de liste, le mini-lecteur (15 sp) |
| Corps / métadonnées | 14 sp | 480 | 0 | l'artiste, la seconde ligne |
| Petite métadonnée | 13 sp | 500 | 0, chiffres tabulaires | durées, compteurs |
| Étiquette | 12 sp | 620 | +0,06 em, capitales | « EN LECTURE » — moins espacée que le bureau : à 12 sp sur un téléphone, 0,1 em disperse le mot |

Rien sous 12 sp. Toutes les tailles suivent l'échelle de police du système ; les
écrans s'adaptent à la hauteur restante plutôt que de figer des hauteurs.

## Formes et espace

Une échelle, une règle : **plus l'objet est grand, plus son rayon l'est, sans
jamais l'arrondir en galet**.

| Forme | Rayon | Où |
| --- | --- | --- |
| Vignette | 6 dp | une pochette de 40 à 56 dp dans une ligne |
| Tuile | 10 dp | une pochette de grille, de carrousel |
| Surface | 14 dp | le mini-lecteur, une mise en avant |
| Grande pochette | 16 dp | le lecteur, l'en-tête d'album |
| Feuille | 24 dp en haut | les feuilles du bas |
| Action | pleine (pilule, disque) | boutons, pastilles, la recherche |
| Artiste | disque | toujours |

- Grille de 4 dp. Gouttière **20 dp** sous 400 dp de large, **24 dp** au-delà.
- Entre deux sections : 32 dp. Dans une section : 12 à 16 dp.
- Cibles : 48 dp minimum, le bouton de lecture du lecteur à 72 dp.

## Mouvement

La courbe du bureau, `cubic-bezier(0.22, 1, 0.36, 1)`, et ses durées : 180 ms
pour un ton, 320 ms pour une forme, 900 ms pour une lumière.

- **La pochette respire avec la lecture** : en pause, elle recule à 92 % ; à la
  lecture, elle revient à 100 %. C'est le seul mouvement de taille de l'écran,
  et il dit l'état d'un regard.
- **Le balayage** sur la pochette la suit sous le doigt (amorti au tiers),
  change de morceau passé 96 dp, revient à sa place sinon.
- **Le sillage** (ci-dessous) ondule pendant la lecture, se pose en pause.
- **Sous « Supprimer les animations »** : la pochette ne recule plus, le sillage
  reste droit, les fondus restent. Rien d'autre ne bouge.

## La signature : le sillage

Le bureau dessine sa progression en onde sur toute la partie jouée. Sur un
téléphone, une onde de 300 dp de long devient une texture. Pocket la resserre
en **un sillage** : la partie jouée est une ligne droite et nette, et seuls les
derniers millimètres avant la tête ondulent — l'onde que laisse le son en
passant. La tête est un point lumineux, entouré d'un halo de l'accent.

- **Au repos** (pause), le sillage se pose : la ligne redevient droite.
- **Sous le doigt**, la tête grossit et le halo s'élargit ; la ligne s'épaissit.
- **Dans le mini-lecteur**, le sillage se réduit à une ligne de 2 dp au pied de
  la surface, avec sa tête lumineuse.
- **Sous l'onglet ouvert** de la barre de navigation, l'onde du bureau (le
  `WaveMark`) devient l'indicateur : un trait de 20 dp, pas une pastille.
- **Ailleurs, jamais** : pas d'onde décorative, pas d'onde en fond.

## Composants

Un petit système, nommé par intention.

| Composant | Ce qu'il est |
| --- | --- |
| `PocketTheme` | les jetons ci-dessus sur Material 3, plus ce que Material n'a pas (`ink3`, `raised`, `floating`, `hairline`) |
| `Sillage` | la progression : ligne, sillage, tête ; gestes d'appui, de glissement, clavier, et l'action d'accessibilité |
| `PlayButton` | le disque d'accent, avec l'attente dessinée en anneau autour de lui plutôt qu'à sa place |
| `ToggleIcon` | une bascule : l'accent et le point quand elle est allumée |
| `CoverStage` | la grande pochette : ombre teintée, recul en pause, balayage, appui |
| `Atmosphere` | la nappe de la pochette, bornée |
| `PocketMiniPlayer` | la surface de contenu posée sur la barre de navigation |
| `PocketNavBar` | trois destinations, l'onde sous l'onglet ouvert |
| `Dock` | la rangée d'actions secondaires au pouce (paroles, file) |
| `PocketPill`, `PrimaryPill` | l'action secondaire sur un ton ; l'action principale d'une page, pleine d'accent |
| `SectionTitle` | le titre d'une section (20 sp) et, au besoin, son action à droite |
| `AlbumTile`, `TileCarousel` | la pochette sans conteneur, titre et artiste dessous ; la rangée qui laisse dépasser une troisième tuile |
| `TrackRow` | la ligne compacte : vignette au besoin, titre, seconde ligne, durée — ou l'onde pour celle qui joue —, menu |
| `ArtistPortrait` | le disque d'un artiste, ses initiales sur une teinte tirée de son nom quand la pochette manque |
| `CategorySelector` | les pastilles défilantes de la bibliothèque, l'ouverte pleine d'encre |
| `EmptyState` | ce qui manque, et ce qu'on peut faire |
| `PocketCollection` | la page d'un album — et, dans la même grammaire, d'un artiste ou d'une playlist |

## Les écrans

### Now Playing — l'écran qui définit WaveFlow

**Intention.** Qu'une capture suffise à dire « c'est WaveFlow » : la pochette
en grand, la lumière qu'elle répand, le disque vert et le sillage. Tout le reste
se tait.

**Composition**, de haut en bas :

1. **Une barre légère** : réduire (à gauche) ; au centre, l'étiquette
   « EN LECTURE » et ce qui joue — l'album, ou la source ; « Plus » (à droite),
   qui ouvre une feuille : vitesse, minuterie de veille, boucle A-B. Trois
   éléments, contre cinq aujourd'hui.
2. **La pochette**, qui prend toute la hauteur qui reste, plafonnée à la
   largeur. Un appui montre les paroles (comme aujourd'hui), un balayage change
   de morceau.
3. **Le titre** en mise en avant (26 sp, deux lignes au plus), l'artiste et
   l'album sur une ligne en `ink2`. Aligné à gauche, collé à la pochette.
4. **Le sillage**, le temps écoulé à gauche, le temps **restant** à droite, et
   entre les deux **des pastilles d'état**, *seulement* quand un réglage
   s'écarte de l'ordinaire : « ×1,5 », la lune de la minuterie, « A–B ». Une
   vitesse active et invisible est un défaut qu'on cherche longtemps. Leur
   rangée a une hauteur constante : une pastille qui paraît ne déplace rien
   (posée d'abord sous le titre, elle faisait remonter la pochette).
5. **Les commandes** : aléatoire · précédent · **lecture (72 dp, l'accent)** ·
   suivant · répétition.
6. **Le dock** au pouce : « Paroles » et « File · 12 », deux actions à
   étiquette. La file s'ouvre en **feuille** (geste natif, glisser pour fermer),
   au lieu de remplacer la pochette.

**Adaptation à la hauteur.** La pochette n'a pas de taille : elle reçoit ce que
le reste lui laisse, plafonné à la largeur (et à 520 dp sur une tablette). Sous
120 dp — une fenêtre basse, une police doublée —, l'écran passe en
**disposition compacte** : une pochette de 96 dp à gauche du titre, et les
commandes gardent leur taille. Le seuil est bas à dessein : essayé à 200 dp, il
faisait passer en vignette un petit téléphone (360 × 640 dp) à police normale,
sous un grand vide, là où une pochette de 160 dp au-dessus des commandes se
lisait mieux. En paysage large, deux volets : la pochette à gauche, le reste à
droite.

**Ce qui change par rapport à l'actuel.**

- Le bouton de lecture est **toujours vert** : l'accent ne vient plus de la
  pochette (décision du 28/11). C'était la cause des « contrôles violets ».
- Le vide n'est plus partagé à parts égales au-dessus et au-dessous de la
  pochette : il va **à la pochette**.
- L'en-tête passe de cinq éléments à trois ; vitesse, minuterie et boucle A-B
  vont dans « Plus », et se signalent par leurs pastilles quand elles agissent.
- La file s'ouvre en feuille ; paroles et file se touchent au pouce.
- Le temps restant remplace la durée totale.
- La lumière : une nappe au lieu de deux (sombre) ou trois (clair), et le clair
  n'est plus du verre.

**Ce qui n'est pas affiché, faute d'exister** : aucun favori (le client n'en a
pas), aucune sortie audio (aucun sélecteur n'est branché), aucune information
de qualité de flux par piste. Le prototype ne les montre pas.

### Mini-lecteur et barre de navigation

**Intention.** Le lecteur ne disparaît pas quand on le réduit : il se range, au
même endroit, avec les mêmes signes — la pochette, le vert, le sillage.

**Composition.** Une surface de contenu (`surface`) de 64 dp, rayon 14 dp, à 8 dp des bords,
posée **sur** la barre de navigation (pas flottante au-dessus du contenu) :
vignette 44 dp, titre et artiste, le disque de lecture (40 dp, l'accent), le
morceau suivant. Le sillage court au pied de la surface. Un appui l'ouvre ; un
balayage vers le haut aussi ; un balayage horizontal change de morceau.

La barre de navigation garde ses trois destinations — Accueil, Bibliothèque,
Recherche —, à 64 dp plus l'encart du système. L'onglet ouvert : son icône
pleine en `ink`, son étiquette, et l'onde de 20 dp dessous, dans l'accent. Les
autres : icône au trait en `ink3`. Pas de pastille. À police agrandie,
l'étiquette rétrécit d'un cran plutôt que d'être coupée.

**Ce qui change.** La plaque à liseré de verre et sa lueur en dégradé
disparaissent ; la progression quitte le bord haut pour le pied (là où l'œil
finit la lecture de la ligne) ; l'indicateur de navigation devient l'onde.

### Accueil

**Intention.** Dire tout de suite quoi écouter, même avec trois albums.

**Composition**, chaque bloc seulement s'il a de vraies données :
1. **Le salut** en Playfair italique (« Bonsoir. ») — la seule ligne
   éditoriale de l'application —, et dessous, en troisième encre, ce que
   contient l'appareil. Il change de lui-même au palier suivant.
2. **Reprendre** : la dernière écoute réelle (l'historique de l'application),
   sa pochette sur sa nappe, le titre, l'artiste et l'album, le disque de
   lecture. Le disque relance ; le reste de la carte ouvre l'album. **Sans
   historique**, sur la page et non sur une carte : « Votre musique est là. »
   et une seule action, *Au hasard* — les catégories sont juste en dessous.
3. **Les accès rapides** : Albums, Artistes, Titres, Playlists, en pastilles
   qui ouvrent la catégorie de la bibliothèque. Pas de cartes de statistiques.
4. **La suggestion du jour** (#104) : sa sorte et sa raison, le morceau, et
   *Autre*.
5. **Ajouts récents** : un carrousel où deux pochettes et le bord d'une
   troisième tiennent sur la largeur (tuiles bornées de 132 à 200 dp), *Tout
   voir*.
6. **Écoutés récemment** : les albums de l'historique, chacun une fois, sans
   celui de la reprise.

Sur une tablette, l'accueil garde une colonne de 840 dp, centrée. À police
agrandie, la pochette de la reprise passe de 104 à 72 dp : le titre se coupait
au milieu d'un mot.

**Ce qui change.** Les compteurs et le grand titre du bureau partent ; la
musique arrive dans le premier écran ; une bibliothèque de trois albums remplit
l'écran de sa reprise, de ses accès, de sa suggestion et de ses pochettes.

**Ce qui n'y est pas.** L'icône des réglages proposée par le brief : le
prototype n'a pas d'écran de réglages, et une icône qui n'ouvre rien serait un
bouton mort. Elle reviendra à l'intégration, vers les réglages de
l'application.

### Bibliothèque

**Intention.** Un vrai navigateur de musique, efficace sur des milliers de
titres.

**Composition.** Le titre d'écran et ce que contient l'appareil, puis **le
sélecteur de catégorie** : Albums · Artistes · Titres · Playlists, des
pastilles qui défilent jusqu'au bord de l'écran, l'ouverte pleine d'encre —
encre claire sur le sombre, encre sombre sur le clair. Une seule surface qui
défile, des clés stables :

- **Albums** en grille : deux colonnes sur un téléphone, quatre sur une
  tablette (tuiles de 150 dp au moins), la pochette sans conteneur ;
- **Artistes** en lignes : le disque de 56 dp, le nom, « 2 albums · 9 titres » ;
  sans pochette, ses initiales sur une teinte tirée de son nom (« ? » pour
  l'artiste inconnu) ;
- **Titres** en lignes compactes : vignette 48 dp, titre, artiste, durée, menu
  (*Ajouter à une playlist*, *Voir l'album*, *Voir l'artiste*) ; celle qui
  joue prend l'accent et l'onde à la place de sa durée ;
- **Playlists** en lignes : la pochette du premier titre, le nom, le compte.

Dès 680 dp, les lignes passent sur deux colonnes. Un artiste et une playlist
s'ouvrent sur la page d'un album, dans la même grammaire.

**Ce qui change.** La rangée d'onglets soulignés part ; les artistes gagnent un
visage qui n'est pas une note de musique.

**Ce qui n'y est pas.** Le catalogue du serveur, sous-onglet de
l'application : le prototype ne lit que l'appareil. Il reviendra à
l'intégration comme cinquième pastille.

### Album

**Intention.** Une page d'album, pas une fiche.

**Composition.** La pochette en grand sur sa nappe — la largeur moins les
gouttières, bornée par la moitié de la hauteur et par 400 dp —, le nom en
titre d'écran, l'artiste, « Album · 9 titres · 42 min », **Lire** (pilule
d'accent) et **Aléatoire** (pilule tonale), puis les pistes sans pochette
répétée : le titre, l'artiste seulement s'il n'est pas celui de l'album, la
durée ou l'onde, le menu. En défilant, la pochette recule et pâlit avec le
geste, vers son bord bas pour ne pas mordre sur le titre, et le nom passe dans
la barre du haut ; la flèche de retour garde un disque de la page tant qu'elle
passe sur la pochette. Sous « Supprimer les animations », seul le nom paraît,
en fondu. À partir de 600 dp, la pochette et le texte sont côte à côte.

**Ce qui change.** `DetailHeader` (commun à cinq pages) laisse la place à un
en-tête propre ; la pochette passe de ~140 dp à toute la largeur.

**Ce qui n'y est pas.** L'année et le numéro de piste : l'application ne les
lit pas dans le MediaStore, et les pistes sont dans l'ordre de la bibliothèque
(par titre). Le prototype ne les invente pas ; les lire est un changement du
dépôt MediaStore, hors de son périmètre.

## Le prototype

**Isolé dans la variante `debug`.** Tout vit dans
`app/src/debug/java/app/waveflow/prototype/` avec son propre manifeste
(`app/src/debug/AndroidManifest.xml`) : une activité `PocketActivity`, une
seconde icône de lanceur **« WaveFlow Pocket »**, nommée par
`app/src/debug/res/values/strings.xml`. Le prototype emprunte à la production ;
la production n'importe rien du prototype, et une build `release` ne contient
ni son code ni son entrée de lanceur. Le retirer, c'est supprimer ces quatre
chemins : le dossier du prototype, son manifeste, sa ressource de nom et ses
tests (`app/src/testDebug/java/app/waveflow/prototype/`), qui ne compileraient
plus sans lui.

**Il lit les vraies données et pilote le vrai lecteur.** Le prototype prend les
ViewModels de la production — `PlayerViewModel` (sa fabrique, donc son
`MediaController`, sa minuterie, sa boucle A-B, ses paroles), `HomeViewModel`
(la reprise et la suggestion du jour), `PlaylistsViewModel` — et le
`LibraryStore` d'`AppContainer`.
Il ne réécrit rien de Media3, du MediaStore, du service ni d'Android Auto. Il
réemploie les feuilles et volets existants qui portent une logique (`QueueScreen`,
`LyricsPane`, `SleepTimerSheet`, `PlaybackSpeedSheet`, `AddToPlaylistSheet`)
en les habillant de ses jetons.

**Pour l'ouvrir** : installer l'APK debug, puis l'icône « WaveFlow Pocket », ou

```bash
adb shell am start -n app.waveflow/app.waveflow.prototype.PocketActivity
```

Le thème suit celui du système : `adb shell cmd uimode night yes` pour le
sombre, `adb shell cmd uimode night no` pour le clair.

**Ce qu'il montre** : les quatre écrans — l'accueil, la bibliothèque, la page
d'un album (et d'un artiste, d'une playlist), Now Playing —, avec le
mini-lecteur et la barre de navigation. L'onglet Recherche dit qu'il n'est
pas l'un des quatre. Les tests de ses composants à logique sont dans
`app/src/testDebug/`, pour que la variante `release` n'ait rien à compiler.

## Décisions arrêtées le 28/11

L'utilisateur a validé la direction de Now Playing et du mini-lecteur le
28/11, et tranché les cinq points que ce document rouvrait. **Elles ne valent
que dans le prototype** (variante `debug`) : l'application de production ne
bouge pas tant que les quatre écrans n'ont pas été revus.

1. **L'accent** : `#20D39B` en sombre (9,8:1 sur la page), `#047857` en clair
   (encre blanche à 5,5:1 : un libellé de bouton y tient). Il remplace
   `#34D399` et `#059669`.
2. **L'accent tiré de la pochette est abandonné.** Le bouton de lecture reste
   toujours vert ; la pochette ne donne que la lumière. Cela revient sur la
   décision du 25/11, cause directe des « contrôles violets ».
3. **L'atmosphère de la pochette** s'étend du lecteur à l'en-tête de la page
   d'album et à la reprise de l'accueil — une seule nappe chaque fois, jamais
   un écran entier teinté.
4. **Le clair passe en tons pleins**, sans verre (`#F5F6F2`, menthe `#E4EEE9`,
   blanc pour ce qui se lève), à la place de *Liquid Aurora Glass*.
5. **La police** : DM Sans seule ; Playfair Display italique ne reste que pour
   la ligne du salut de l'accueil, dans les deux apparences.
