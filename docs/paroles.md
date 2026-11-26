# Les paroles : synchronisées dans le lecteur

Règles arrêtées **avant le code**, comme celles du déplacement dans un
transcodage et de la qualité automatique. Chaque règle dit ce qu'on doit
observer, pour pouvoir s'éprouver par un test. Une règle qui change se change
ici d'abord.

Décisions de l'utilisateur : le 30/08 pour le dépôt (aucun appel réseau depuis
un écran), le 25/11 pour les sources et le geste (issue #81). Ce qui est dit de
Media3 a été lu dans les sources de la 1.11.1 ; ce qui est dit du serveur, dans
`docs/guides/api-v2-guide.md` et la RFC-012 de `waveflow-server`.

## Le problème

Le lot 5 de la refonte v2. Le serveur sert les paroles depuis la 2.0.0-beta.1,
et le client n'en montrait aucune — ni celles du serveur, ni celles que les
fichiers de l'appareil portent dans leurs tags ou dans un `.lrc` posé à côté.

## Les règles

### D'où viennent les paroles

**P1. Une piste locale ne prend ses paroles que dans son fichier.** Celles
embarquées dans ses tags d'abord, puis le `.lrc` voisin. Aucun appel réseau,
même quand une session serveur est ouverte — LRCLIB pour les pistes locales
pourra venir plus tard. Des paroles embarquées, même simples, passent devant un
`.lrc` synchronisé : c'est l'ordre décidé.

**P2. Les paroles embarquées sont celles que l'extracteur de Media3 expose.**
Lues par `MetadataRetriever` (module `media3-inspector`, même version que le
reste de Media3) sur l'URI du MediaStore, sans bibliothèque de tags. Dans la
1.11.1 :

| Conteneur | Ce qui porte les paroles | Ce que Media3 en rend |
|---|---|---|
| ID3 (MP3, et tout fichier précédé d'un en-tête ID3) | `SYLT` (synchronisées), `USLT` (simples) | un `BinaryFrame` brut, que le client décode |
| Commentaires Vorbis (FLAC, Ogg Vorbis, Opus) | `LYRICS`, `UNSYNCEDLYRICS` (casse indifférente) | un `VorbisComment` |
| MP4 (M4A, ALAC) | l'atome `©lyr` | un `TextInformationFrame` d'identifiant `USLT` |

Entre plusieurs paroles embarquées, **des synchronisées passent devant des
simples** : un `SYLT`, ou un texte qui se lit comme un LRC (P9), avant un texte
simple. Un tag vide ne compte pas.

D'un `SYLT`, seuls se lisent les instants en millisecondes (format 2) et les
paroles ou la transcription (types 1 et 2) : des accords horodatés ne passent
pas pour des paroles. Un `SYLT` horodaté syllabe par syllabe porte le saut de
ligne en tête du fragment qui ouvre la ligne suivante : ses fragments se
regroupent alors en lignes, chacune au début de son premier fragment. Un
fragment vide y est une ligne vide à son instant, comme en LRC (P9) : il ferme
la ligne en cours, et le fragment qui le suit en ouvre une nouvelle.

**P3. Le `.lrc` voisin : même dossier, même nom, extension remplacée.**
`Chanson.flac` → `Chanson.lrc`, puis `Chanson.LRC`. Le fichier est lu en entier
s'il fait 1 Mio au plus (la limite du serveur), ignoré au-delà. Son encodage :
celui que nomme une marque d'ordre d'octets (UTF-8, UTF-16), sinon UTF-8 s'il en
est, sinon Windows-1252. Un fichier absent ou que le système refuse d'ouvrir vaut
absence, sans erreur affichée.

Le fichier se lit par son chemin, avec la seule permission audio : le MediaStore
range `.lrc` en `application/lrc`, parmi les sous-titres, que le stockage
cloisonné laisse lire. **Vérifié sur l'émulateur Android 17 (API 37) ; non
vérifié sur Android 11 à 16**, où un refus rendrait le `.lrc` muet sans erreur.

**P4. Une piste du serveur prend la première feuille de `GET
/api/v2/tracks/{id}/lyrics`.** Le serveur trie déjà ses feuilles (RFC-012,
décision 6 : synchronisées avant simples, envois de l'utilisateur, fichier
voisin, tags, LRCLIB) et en écarte les vides ; le client montre la première sans
la rejuger. Les débuts de ligne (`start`) sont en millisecondes. Une feuille
annoncée `synced` dont une ligne n'a pas de `start` se montre en paroles
simples. Une liste vide vaut absence ; une piste inconnue (`404`) aussi. Tout
autre refus — un `429` d'abord — dit « pas maintenant », pas « pas de paroles » :
c'est une panne au sens de P7.

**P5. Pas de source prioritaire entre l'appareil et le serveur.** La RFC-003
interdit de dire qu'une piste locale et une piste du serveur sont le même
enregistrement : chaque piste n'a qu'une source de paroles, la sienne.

### Le dépôt

**P6. L'écran ne fait jamais d'appel.** Il lit un état que le ViewModel tient,
et le ViewModel demande au `LyricsRepository`. **Rien n'est cherché tant que les
paroles ne sont pas affichées** : un morceau dont on ne regarde pas les paroles
ne coûte ni requête ni lecture de fichier. Une fois affichées, les paroles du
morceau suivant se cherchent à son arrivée.

**P7. Le dépôt garde ce qu'il a trouvé, et ce qu'il n'a pas trouvé.** Par
`mediaId`, pour les 32 derniers morceaux. Une panne — serveur injoignable,
réponse illisible, session fermée — n'est **pas** gardée : la prochaine demande
réessaie.

**P8. Des paroles appartiennent à leur morceau.** L'état porte le `mediaId` du
morceau pour lequel elles ont été cherchées. Une réponse qui arrive après un
changement de morceau n'est jamais montrée sous le suivant : la recherche
précédente est annulée, et l'écran ne dessine que des paroles du morceau
affiché.

### Le format LRC

**P9. Une ligne synchronisée est un texte précédé d'un ou plusieurs
horodatages.** `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, et `:` au lieu
du point ; les minutes peuvent dépasser 59. Plusieurs horodatages sur une ligne
la répètent à chaque instant. Les lignes sont rangées par instant, l'ordre du
fichier départageant les égalités. `[offset:±n]` décale tout de *n*
millisecondes, un décalage positif avançant les paroles (convention du format).
Les autres balises d'en-tête du format, seules sur leur ligne (`ar`, `ti`, `al`,
`au`, `by`, `re`, `ve`, `la`, `length`, `tool`, `id`, `#`), sont ignorées ; une
ligne comme `[Chorus: Kanye West]` n'en est pas une et reste du texte. Les horodatages de mot du LRC enrichi (`<mm:ss.xx>`) sont retirés du
texte : la ligne seule est suivie. Une ligne horodatée vide est gardée : elle
dit que la ligne précédente est finie.

**P10. Un texte sans aucune ligne horodatée est un texte simple.** Les balises
de P9 en sont retirées, les lignes vides de tête et de queue aussi ; les lignes
vides du milieu, qui séparent les strophes, restent.

### L'affichage

**P11. Un appui sur la pochette la remplace par les paroles ; un appui sur les
paroles, hors d'une ligne synchronisée, ramène la pochette.** Rien de plus dans
l'en-tête, qui garde ses quatre boutons. La file d'attente, ouverte, prend la
place des deux. Le choix tient d'un morceau à l'autre et quand on replie le
lecteur : celui qui lit les paroles veut lire celles du suivant.

**P12. La ligne chantée est la dernière dont le début est atteint.** Avant le
premier début, aucune ligne n'est chantée et les paroles restent en tête.

**P13. Pendant la lecture, la position avance entre deux tics ; en pause, c'est
celle du lecteur.** L'état du lecteur ne se reconstruit qu'aux tics de position,
toutes les 500 ms : une ligne attendrait jusqu'à une demi-seconde. Entre deux
tics, la position se prolonge sur l'horloge d'images, à la vitesse de lecture,
**sans jamais prolonger plus d'un tic** la dernière position reçue — un lecteur qui
cale sans le dire ne fait pas défiler des paroles qu'il ne chante pas. En
pause, plus rien ne se prolonge. **Un déplacement en pause publie sa
position** : la ligne chantée est juste après un déplacement, que la lecture
tourne ou non.

**P14. Un appui sur une ligne synchronisée déplace la lecture à son début.** La
ligne devient la ligne chantée, y compris en pause (P13).

**P15. Les paroles suivent la ligne chantée, sauf quand on les parcourt.** Le
défilement garde la ligne chantée vers le tiers haut de la zone. Un défilement
de l'utilisateur — au doigt, à la molette, au clavier (`PageUp`, `PageDown`) ou
au lecteur d'écran — suspend ce suivi ; le défilement du suivi lui-même ne
compte pas. Un bouton *Revenir à la ligne*, dans
l'accent, le rétablit, et le suivi reprend de lui-même cinq secondes après le
dernier défilement. Toucher une ligne le rétablit aussi.

**P16. Des paroles simples se lisent comme un texte.** Une colonne qui défile,
sans ligne chantée ni déplacement ; un appui n'importe où ramène la pochette.

**P17. Un état vide honnête.** Trois cas qui ne se confondent pas :
- *recherche en cours* : rien n'est affirmé ;
- *pas de paroles* : le morceau n'en a pas — pour une piste locale, ni dans ses
  tags ni dans un `.lrc` voisin ; pour une piste du serveur, le serveur n'en
  connaît pas ;
- *paroles indisponibles* : le serveur n'a pas pu répondre. Un bouton
  *Réessayer* relance la demande, par le dépôt.

**P18. Le dessin suit la matière du thème, et l'accent est celui du lecteur.**
- **Fluid Editorial (sombre)** : les lignes au repos en deuxième encre, estompées
  et floutées avec leur distance à la ligne chantée (voisines à 80 %, deux lignes
  plus loin à 65 %, au-delà à 50 %) ; la ligne chantée nette, en première encre,
  un peu plus grande.
- **Liquid Aurora Glass (clair)** : jamais de flou sur une colonne de texte ;
  l'opacité seule (voisines à 90 %, au-delà à 72 %), et la lumière de la
  pochette sous la ligne chantée.
- **Pendant un parcours (P15)** : toutes les lignes nettes, à 75 %, la ligne
  chantée toujours la plus vive.
- **L'accent** — celui de la pochette (`PlayerAccent`) — va au bouton *Revenir à
  la ligne* et à l'indicateur d'attente : nulle part de décoratif.
- **Mouvement réduit** : ni défilement animé, ni flou ; l'opacité seule.

Le flou n'existe qu'à partir d'Android 12 ; en deçà, l'opacité porte seule la
distance.

**P19. Tout se lit et se règle au lecteur d'écran.** Une ligne synchronisée est
un bouton qui annonce son instant ; la zone des paroles porte l'action *Revenir à
la pochette* ; le texte suit la taille de police du système.

## Hors de cette note

- Les paroles en direct dans la notification (CrystalMusic le fait) : une autre
  carte.
- Le mot chanté du LRC enrichi : les horodatages de mot sont lus et retirés
  (P9), pas suivis.
- LRCLIB pour les pistes locales (P1).
- Envoyer ou modifier des paroles sur le serveur.
