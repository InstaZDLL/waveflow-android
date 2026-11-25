# L'audio avancé : ReplayGain et égaliseur

Règles arrêtées **avant le code**, comme celles du déplacement dans un
transcodage. Chaque règle dit ce qu'on doit observer, pour pouvoir s'éprouver
par un test. Une règle qui change se change ici d'abord.

Décisions : le 24/11, on garde Media3 et on fait l'égaliseur et le ReplayGain en
Kotlin, en portant le bureau pour que le son soit le même (issue #82). Le 25/11,
l'utilisateur a décidé que le ReplayGain serait **actif par défaut, en mode
Automatique**, réglable dans *Réglages* avec l'égaliseur. Ce qui est dit de
Media3 a été lu dans les sources de la 1.11.1.

**La référence est le bureau**, `InstaZDLL/WaveFlow` :

- `src-tauri/crates/core/src/playback/replay_gain.rs` pour le calcul du gain ;
- `src-tauri/crates/core/src/scanner/replay_gain.rs` pour la lecture des tags ;
- `src-tauri/crates/app/src/audio/eq.rs` pour l'égaliseur ;
- `src-tauri/crates/app/src/audio/state.rs` (`listening_to`) pour le mode
  Automatique.

Les tests Kotlin reprennent les vecteurs de leurs tests : même entrée, même
sortie.

## Les règles

### Le réglage

**A1. Une section *Son* dans les Réglages, et rien dans le lecteur.** Elle
contient le ReplayGain et l'égaliseur. L'en-tête du lecteur est plein (voir
`docs/PASSATION.md`) : la vitesse s'y règle, pas le volume perçu.

**A2. Le ReplayGain est actif par défaut, en mode Automatique.** Ses réglages
sont ceux du bureau, avec ses défauts sauf le premier :

| Réglage | Défaut | Plage |
|---|---|---|
| ReplayGain | **actif** (le bureau : inactif) | — |
| Gain à appliquer | Automatique | Automatique, Par titre, Par album |
| Pré-amplification | 0 dB | −15 à +15 dB, par 0,5 dB |
| Gain par défaut | 0 dB | −15 à +15 dB, par 0,5 dB |
| Éviter la saturation | actif | — |

Les sous-réglages ne paraissent que ReplayGain actif, comme sur le bureau : ils
n'agiraient pas.

**A3. Les réglages sont bornés à l'écriture et à la relecture**, comme la vitesse
de lecture. Une valeur non finie relit 0 dB. Un mode inconnu (écrit par une
version future) relit Automatique, comme `GainMode::from_setting`. Des bandes
illisibles, ou qui ne sont pas six, relisent l'égaliseur plat ; l'interrupteur
garde sa valeur. Un préréglage inconnu relit un réglage *Personnalisé*, qui
garde ses bandes.

**A4. Un réglage s'applique au tampon suivant**, sans attendre la piste
suivante : le service observe les préférences, comme pour la vitesse, et les
traitements relisent leurs réglages à chaque tampon. Le délai entendu est celui
de la mémoire tampon de la sortie audio, quelques centaines de millisecondes.

### D'où vient le gain

**G1. Une piste locale : les tags de son fichier**, tels que l'extracteur de
Media3 les range dans le `Format.metadata` de la piste audio. Le nom de la clé
ne tient pas compte de la casse :

- MP3 (ID3v2) : un cadre `TXXX` dont la description est `REPLAYGAIN_…` ;
- FLAC, Ogg Vorbis, Opus : un commentaire Vorbis (`VorbisComment`), qui peut
  aussi porter `R128_TRACK_GAIN` et `R128_ALBUM_GAIN` ;
- MP4 / M4A : un atome iTunes `----:com.apple.iTunes:REPLAYGAIN_…`, que Media3
  rend en `InternalFrame`.

Les quatre clés sont `REPLAYGAIN_TRACK_GAIN`, `REPLAYGAIN_TRACK_PEAK`,
`REPLAYGAIN_ALBUM_GAIN` et `REPLAYGAIN_ALBUM_PEAK`.

**G2. Une piste du serveur : les champs du `SongItem`.** Les quatre champs
`replay_gain_track_gain`, `replay_gain_track_peak`, `replay_gain_album_gain` et
`replay_gain_album_peak` (en dB sur l'échelle ReplayGain 2.0, pics linéaires)
voyagent avec la piste dans la file. Le serveur ne met de gain dans aucun flux de
l'API v2 : ni l'original servi par le ticket, ni un transcodage. Seule la façade
Subsonic en applique un, que ce client n'utilise pas. Le gain s'applique donc
aussi bien à l'original qu'à une version transcodée, et une fois seulement.

Un champ absent du serveur est cherché, **champ par champ**, dans les tags du
flux reçu : un original servi tel quel porte les siens. Les champs du serveur
passent par les mêmes bornes qu'un tag (G3) : le serveur ne rejette que les
valeurs non finies, et un tag cassé qu'il aurait recopié — un gain de 89 dB, un
pic écrit en pourcentage — compte pour absent, le tag du flux prenant le relais.

**G3. Lire un tag, comme le scanner du bureau.**

- Un gain textuel est un décimal signé, suivi ou non de `dB` dans n'importe
  quelle casse, entouré ou non d'espaces : `-7.89 dB`, `-7.89dB`, `+3.20 dB`,
  `  0.00 DB  `.
- Un gain dont la valeur absolue dépasse 60 dB, non fini ou illisible est
  écarté : c'est un tag cassé.
- `R128_*` est un entier Q7.8 en LU par rapport à −23 LUFS. Divisé par 256 et
  augmenté de 5, il passe sur l'échelle ReplayGain (−18 LUFS) : `0` donne
  +5 dB, `-1536` donne −1 dB.
- Un nombre s'écrit comme le bureau le lit (`f64::from_str`) : signe, chiffres,
  point décimal, exposant. L'hexadécimal (`0x1p3`) et un suffixe de type
  (`2.5d`), que Kotlin accepterait, sont écartés.
- Quand un fichier porte les deux, `REPLAYGAIN_*` l'emporte sur `R128_*`.
- Un pic est linéaire, strictement positif et au plus 4 : `0`, un négatif,
  `120` ou du texte sont écartés.
- Les quatre valeurs sont indépendantes : un fichier peut avoir un gain sans
  pic, un gain d'album sans gain de piste.

**G4. Le gain suit la piste dont on traite les échantillons**, pas la piste que
le lecteur dit courante. Les deux diffèrent de la mémoire tampon, plusieurs
secondes au plus. Le mécanisme, lu dans les sources de Media3 :

- à chaque nouvelle piste, le renderer audio appelle `AudioSink.configure` avec
  le `Format` de la piste (et ses tags), la `Timeline` et le `MediaPeriodId` ;
- le `DefaultAudioSink` vide ses traitements de l'ancienne piste, puis leur
  envoie `flush(StreamMetadata)` avec le **même** `periodUid`, juste avant le
  premier échantillon de la nouvelle.

L'enveloppe du `DefaultAudioSink` retient donc le gain de chaque piste sous son
`periodUid` au `configure`, et le traitement le reprend au `flush`. Sur un
enchaînement sans blanc, le gain change exactement à la frontière. Un
`periodUid` inconnu donne une piste sans gain connu : le gain par défaut s'y
applique.

### Le calcul du gain

**G5. Le calcul est celui de `effective_gain_db` du bureau.**

1. ReplayGain inactif : 0 dB, quoi que disent les autres réglages.
2. Le mode choisit la paire gain et pic à appliquer (G6).
3. Un gain absent ou non fini prend le **gain par défaut**.
4. On ajoute la **pré-amplification**, puis on borne le total entre −30 et
   +12 dB.
5. Avec **Éviter la saturation** et un pic connu (fini, strictement positif),
   le gain est plafonné à `−20·log10(pic)`, la marge qui amène l'échantillon le
   plus fort à pleine échelle. Le plafond ne fait que baisser : il n'amplifie
   jamais un gain négatif, et une piste déjà saturée (pic > 1) est atténuée.
   Le plafond vient **après** la borne : la borne basse ne doit pas défaire le
   plafond.

Le facteur linéaire est `10^(dB/20)`. Pour 0 dB il vaut **exactement** 1, et le
tampon passe tel quel.

Le bureau marque « non vérifié » un pic venu d'une ancienne analyse et lui
refuse d'autoriser une amplification. Android n'a pas d'analyse, seulement des
tags et les champs du serveur : la règle et ses tests ne sont pas portés.

**G6. Par titre, par album, ou selon l'écoute.**

- *Par titre* : le gain et le pic du titre.
- *Par album* : le gain et le pic de l'album. Sans gain d'album (ou avec un gain
  d'album non fini), ceux du titre. Avec un gain d'album mais sans pic d'album,
  le pic du titre : un plafond inégal d'une piste à l'autre vaut mieux qu'une
  saturation.
- *Automatique* : *par album* quand on écoute un disque en entier (G7), *par
  titre* sinon.

**G7. Quand écoute-t-on un disque en entier ?** Le bureau lit la source de sa
file : `album` et aléatoire coupé, c'est un disque ; aléatoire par titres, ce
n'en est plus un ; une piste ajoutée à la main, une playlist ou la bibliothèque
non plus. Android ne garde pas la source de sa file : **la file elle-même le
dit**. On écoute un disque quand, à la fois :

- l'aléatoire est coupé (Android ne mélange que par titres : c'est le
  `ShuffleMode::Tracks` du bureau) ;
- la file compte **au moins deux** pistes ;
- toutes portent le **même album**, identifié par son identifiant (local ou
  serveur), jamais par son titre.

**C'est une approximation**, et elle dit plus large que le bureau. Rien ne
vérifie que le disque est **entier** ni **dans son ordre** : seule l'unicité de
l'album compte. Il en découle :

- un album lancé depuis sa page, par la voix ou depuis Android Auto : un disque ;
- un morceau lancé seul (une recherche sur le serveur), une playlist ou la liste
  des titres qui mêlent plusieurs albums : pas un disque ;
- mais aussi, **comptés comme un disque** : la page d'un artiste qui n'a qu'un
  album, une recherche locale qui ramène deux titres du même album, un album
  dont on a réordonné la file, une playlist faite d'un seul album. Le gain
  d'album y est appliqué ; il reste juste pour les titres d'un même disque, qui
  n'y perdent que l'égalisation de titre à titre.

Faire mieux demanderait que la file porte sa source, comme le `source_type` du
bureau, depuis chaque écran qui la construit.

L'écoute se réévalue à chaque changement de la file ou de l'aléatoire, et vaut
au tampon suivant, comme sur le bureau qui la relit à chaque tampon : couper
l'aléatoire en plein album change le gain sans attendre la piste suivante.

### L'égaliseur

**E1. Six bandes, celles du bureau.** Filtres en cloche (« peaking ») du
*Audio EQ Cookbook* de RBJ, en série, à 60, 150, 400, 1000, 2400 et 15 000 Hz,
Q = 0,707, ±12 dB par bande, au dixième de dB. Forme directe II transposée, une
mémoire par canal et par bande.

**Écart assumé : une bande qui n'est pas sous la fréquence de Nyquist est
neutre.** Le bureau rééchantillonne tout vers la fréquence de sa sortie
(44,1 kHz au moins) avant l'égaliseur ; Media3 lui passe celle du fichier, qui
peut être 22,05 kHz ou moins. Au-delà de Nyquist, la cloche de 15 kHz aurait un
`alpha` négatif et ses pôles pourraient sortir du cercle unité : le filtre
divergerait. Une bande dont le centre atteint la moitié de la fréquence
d'échantillonnage prend donc les coefficients de l'identité, quel que soit son
gain.

**E2. Inactif par défaut, et alors sans effet.** Inactif, le tampon passe tel
quel. Actif et plat, les filtres sont l'identité (coefficients `1, a1, a2, a1,
a2`) et la pré-amplification vaut 1 : le tampon ressort identique.

**E3. Une marge contre la saturation, calculée sur la cascade.** Un réglage qui
amplifie est précédé d'une atténuation égale à l'inverse du **pic de la réponse
de toute la cascade**, et non du plus grand curseur : des bandes voisines se
chevauchent et leurs gains s'additionnent. Le pic se lit sur 1024 points
espacés logarithmiquement de 20 Hz à 20 kHz (ou juste sous Nyquist), plus le
centre de chaque bande, en double précision. On retire encore 1 dB pour les
transitoires (facteur 0,891 250 9). Un réglage dont le pic ne dépasse pas
1,001 (0,01 dB) n'amplifie pas : sa pré-amplification vaut exactement 1.

**E4. La marge baisse tout de suite et remonte en un tampon.** Une nouvelle
amplification agit dès l'échantillon suivant, l'atténuation qui la protège
aussi. Rendre cette atténuation peut prendre un tampon : un saut de plusieurs dB
entre deux échantillons s'entendrait comme un clic. Le gain glisse alors
linéairement d'une trame à l'autre, tous les canaux d'une trame ensemble.

**E5. La mémoire des filtres survit aux changements de piste.** Comme sur le
bureau, elle n'est remise à zéro qu'au changement de format (fréquence ou
nombre de canaux) et à la libération du lecteur, pas au `flush` qui marque une
nouvelle piste ou un saut : vider la mémoire à la frontière couperait la
résonance de la fin d'une piste dans un enchaînement sans blanc. Les
coefficients se recalculent quand les bandes ou la fréquence d'échantillonnage
changent. **Couper l'égaliseur oublie sa mémoire** : réactivé, il ne rejoue pas
un reste du signal d'avant.

**E6. Les préréglages du bureau**, mêmes valeurs, mêmes noms français : Plat,
Acoustique, Amplification des graves, Réduction des graves, Classique, Dance,
Profond, Électronique, Hip-Hop, Jazz, Latin, Intensité sonore, Lounge, Piano,
Pop, R&B, Rock, Petits haut-parleurs, Voix parlée, Amplification des aigus.
Choisir un préréglage pose ses six valeurs. Bouger un curseur fait passer à
*Personnalisé*, qui garde les valeurs telles quelles. *Réinitialiser* revient à
*Plat*.

**E7. Un curseur s'enregistre quand on le relâche.** Pendant le geste, l'écran
suit le doigt ; l'enregistrement, et donc le son, suivent au relâchement. Écrire
le fichier de préférences à chaque image du geste serait coûteux pour un
réglage qu'on cherche à l'oreille une fois le doigt levé. Le bureau, lui,
applique pendant le geste.

### La chaîne

**C1. Deux `AudioProcessor` dans le `DefaultAudioSink` d'ExoPlayer**, d'abord le
ReplayGain, puis l'égaliseur, l'ordre du bureau (le gain par flux, puis
l'égaliseur sur le mélange). Media3 les place après la coupe du délai et du
remplissage d'encodeur et la conversion en 16 bits, avant le saut des silences
et la vitesse (`SonicAudioProcessor`). Tout ce que Media3 décode en PCM y passe,
piste locale ou flux du serveur, original ou transcodé.

**C2. Entre les deux, le signal reste en flottant.** Le ReplayGain reçoit du PCM
16 bits (ou flottant) et rend du flottant ; l'égaliseur reçoit ce flottant et
rend du 16 bits, arrondi au plus proche et borné à pleine échelle. Le signal ne
repasse en 16 bits qu'une fois, après l'égaliseur, comme le bureau qui ne borne
qu'à la fin. Le 16 bits en sortie est imposé par le saut des silences de
Media3, qui refuse tout autre format.

**C3. Le gapless n'est pas réimplémenté : il se vérifie sur appareil.** Les deux
traitements ne changent ni le nombre de trames ni leur durée. Le gapless reste
celui de Media3 : la coupe du délai et du remplissage d'encodeur (en-tête LAME
du MP3, `iTunSMPB` du MP4, pré-saut de l'Opus), en amont des traitements. Que
l'enchaînement soit vraiment sans blanc ne se voit pas sous Robolectric, qui ne
décode pas l'audio.

**C4. Pas de sortie déchargée, et le passthrough n'est pas traité.** En mode
`offload`, Media3 contourne les traitements : ni gain ni égaliseur. Le lecteur ne
l'active pas ; l'activer un jour ferait disparaître les deux sans bruit. De même,
un flux AC-3, E-AC-3 ou DTS que Media3 enverrait tel quel à un ampli HDMI qui le
décode (passthrough) ne passe par aucun traitement : seul le PCM en reçoit. Le
cas ne se présente pas pour une bibliothèque musicale ordinaire, et il n'est pas
empêché.

## Ce que cette version ne couvre pas

- **Rien n'a été entendu.** Robolectric ne décode pas l'audio : les traitements
  sont éprouvés sur des tampons, hors de la chaîne, et leur branchement l'est
  par ce que Media3 leur transmet. Que le gain et l'égaliseur s'entendent comme
  sur le bureau, et que l'enchaînement reste sans blanc, se valide sur un
  appareil.
- **Pas d'analyse.** Une piste sans tag ni champ serveur prend le gain par
  défaut ; le bureau peut mesurer la sienne (BS.1770), Android non.
- **Le gain de l'en-tête Opus** (`output gain`) est appliqué par le décodeur, et
  `R128_TRACK_GAIN` s'y ajoute : c'est le choix du bureau, qui lit le tag, et la
  convention des outils de marquage, qui laissent l'en-tête à zéro.
- **Pas de normalisation du volume de la sortie**, ni de mélange en mono, ni
  d'image stéréo : le bureau en a, ce ticket ne les demande pas.
