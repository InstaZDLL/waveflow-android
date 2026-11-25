# La qualité automatique : une qualité par réseau

Règles arrêtées **avant le code**, comme celles du déplacement dans un
transcodage. Chaque règle dit ce qu'on doit observer, pour pouvoir s'éprouver
par un test. Une règle qui change se change ici d'abord.

Décisions de l'utilisateur : le 11/09 pour les deux profils et le critère du
réseau, le 25/11 pour la file en cours (issue #58). Ce qui est dit de Media3 a
été lu dans les sources de la 1.11.1.

## Le problème

La #51 pose le rendu de chaque piste **quand elle entre dans la file** : le
marqueur (`waveflow://track/<id>?format=opus&bitrate=96`) et la clé de cache sont
réécrits ensemble, et le résolveur relit le marqueur. Une file lancée en Wi-Fi
resterait donc en qualité d'origine une fois passé sur les données mobiles — ce
qui viderait le profil de son sens.

## Les règles

### Le réglage

**Q1. Un quatrième choix, *Automatique*, et deux profils sous lui.** Sous
*Réglages ▸ Serveur*, à côté de *Qualité d'origine*, *Haute qualité* et
*Économie*. Choisi, il fait paraître deux lignes, *Wi-Fi* et *Données mobiles*,
qui prennent chacune l'un des trois profils fixes — jamais *Automatique*
lui-même. Par défaut : qualité d'origine en Wi-Fi, Économie en données mobiles.

Choisir un profil fixe quitte *Automatique* ; les deux profils par réseau sont
gardés pour la prochaine fois. Un fichier de préférences écrit par une version
antérieure garde son profil fixe.

**Q2. Le réseau se lit sur sa facturation, pas sur sa nature.**
`NET_CAPABILITY_NOT_METERED` sur le réseau par défaut : non facturé, c'est le
profil *Wi-Fi* ; facturé, *Données mobiles*. L'Ethernet ou un dock USB suivent
donc le Wi-Fi, un Wi-Fi déclaré payant — partage de connexion, forfait limité —
suit les données mobiles. Les mots de l'écran restent *Wi-Fi* et *Données
mobiles* : ce sont ceux que l'utilisateur comprend. Pas de profil
« itinérance ».

La détection est **celle du repli sur l'original** (R22 de
`docs/deplacement-dans-un-transcodage.md`), une seule pour les deux usages.

**Q3. Réseau inconnu au départ : données mobiles. Réseau perdu en route : rien
ne change.** Sans réseau lisible au démarrage, on ne dépense pas le forfait de
l'utilisateur. Une fois un réseau connu, sa perte n'est pas un changement : la
file garde le rendu du dernier réseau connu, et le suivant décide. Un passage
Wi-Fi → rien → Wi-Fi ne réécrit pas la file deux fois.

### La file

**Q4. Une file prend le rendu du réseau du moment.** Comme dans la #51 : lu une
fois pour toute la file quand elle entre dans le lecteur, pour qu'un album ne se
partage pas entre deux qualités. Les pistes locales ne passent pas par le
serveur et ne sont jamais touchées.

**Q5. Au changement de réseau, les pistes pas encore commencées prennent le
rendu du nouveau réseau.** Marqueur et clé de cache réécrits ensemble, comme à
l'entrée dans la file : la clé ne peut pas décrire un autre rendu que celui que
l'URL demande. Une piste qui porte déjà ce rendu est laissée telle quelle.

Seul un changement de réseau réécrit la file en cours, et seulement sous
*Automatique*. Changer le réglage lui-même — un profil fixe, ou l'un des deux
profils par réseau — vaut pour les pistes lancées ensuite, comme l'écran le dit.

**Q6. La piste courante n'est jamais réécrite.** Changer son rendu voudrait dire
la relancer : une coupure audible, pour un bénéfice nul — elle est déjà
téléchargée pour l'essentiel. Le son ne s'interrompt pas, la position ne saute
pas, et rien n'est redemandé au serveur pour elle.

**Q7. Une piste commencée garde son rendu, même quittée.** *Commencée* : elle a
été la piste courante depuis son entrée dans la file. Quittée puis rejouée, elle
retrouve le marqueur et la clé de son propre rendu — y compris après un saut
dans un transcodage (R14) ou un repli sur l'original (R22), que l'enveloppe
défait déjà. La piste se reconnaît à son identité **logique**, celle que
l'enveloppe garde à travers ses relances (R5) : l'identifiant brut de
l'ExoPlayer change à chacune.

**Q8. La piste que le lecteur lit déjà pour l'enchaîner est tenue pour
commencée.** Dans les dernières secondes d'une piste, l'ExoPlayer lit déjà la
suivante pour l'enchaîner sans blanc. La retirer à ce moment
(`MediaPeriodQueue.removeAfter` rend `UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD`)
lui fait relancer la piste courante à sa position (`seekToCurrentPosition`) :
c'est la coupure de Q6. Media3 ne dit pas publiquement quelle piste il lit ; on
protège donc la suivante, dans l'ordre de lecture réel (répétition et aléatoire
compris), quand il reste moins de **10 s** d'écoute à la courante — vitesse de
lecture comprise —, ou quand sa durée est inconnue. Ce qu'il lit d'avance est
de l'ordre de la seconde (le tampon de l'`AudioTrack` en PCM plafonne à 750 ms,
plus ce que tient le décodeur) ; la marge couvre le reste. Le prix : cette piste-là part dans l'ancien rendu.

**Q9. L'ordre aléatoire ne bouge pas.** Remplacer une piste dont l'URI change,
l'ExoPlayer le fait en ajoutant la nouvelle source **puis** en retirant
l'ancienne, et `DefaultShuffleOrder.cloneAndInsert` place la nouvelle à un rang
**tiré au hasard** dans l'ordre aléatoire. Sans précaution, chaque piste
réécrite changerait de place : déjà jouée, elle reviendrait ; à venir, elle
pourrait ne plus venir.

Rétablir l'ordre après coup ne suffit pas : chaque étape — l'ajout, puis le
retrait — part au fil de lecture avec son propre ordre. Un ajout glissé juste
derrière la courante y passerait devant la suivante protégée par Q8, que
l'ExoPlayer retirerait de sa file de lecture : la coupure de Q6. Le temps des
remplacements, un ordre **en place** range donc chaque ajout juste derrière la
piste qu'il remplace, et le retrait qui suit rend l'ordre d'avant, à chaque
étape. L'ordre tiré, relevé avant la réécriture, est rétabli après
(`ExoPlayer.setShuffleOrder`) : les rangs ne changent pas, la longueur non
plus, et il sert de nouveau aux pistes ajoutées ensuite.

## Ce que cette version ne couvre pas

- **La piste déjà lue pour l'enchaînement (Q8)** reste dans l'ancien rendu.
  Media3 n'offre aucun moyen public de savoir quelle piste ses renderers lisent ;
  la marge de 10 s est un compromis, pas une mesure.
- **Un réseau qui bat** — Wi-Fi faible, qui va et vient — réécrit la file à chaque
  bascule. Chaque réécriture abandonne ce qui était déjà chargé de la piste
  suivante. Aucun amortissement n'est posé tant que l'usage ne le réclame pas.
- **Rien n'a tourné sur un appareil** : Robolectric ne décode pas l'audio. Que le
  son ne s'interrompe pas se déduit de ce qui n'est pas fait — ni relance, ni
  saut, ni nouvelle demande pour la piste courante —, éprouvé sur un vrai
  ExoPlayer, mais non entendu.
