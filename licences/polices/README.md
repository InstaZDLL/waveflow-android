# Polices embarquées

L'application embarque deux polices, dans `app/src/main/res/font/`. Toutes deux
sont sous **SIL Open Font License 1.1**, dont le texte est ici, à côté. Elles
sont livrées telles quelles : seul le nom du fichier change, parce qu'Android
n'accepte dans `res/` que des minuscules, des chiffres et des soulignés.

| Fichier dans `res/font/`      | Fichier d'origine                  | Licence                  |
|-------------------------------|------------------------------------|--------------------------|
| `dm_sans.ttf`                 | `DMSans[opsz,wght].ttf`            | `OFL-DMSans.txt`         |
| `playfair_display_italic.ttf` | `PlayfairDisplay-Italic[wght].ttf` | `OFL-PlayfairDisplay.txt` |

Source : le dépôt [`google/fonts`](https://github.com/google/fonts), dossiers
`ofl/dmsans` (commit `5b35b72`) et `ofl/playfairdisplay` (commit `f4f3b47`),
récupérés le 24/11/2026.

```text
8cd08d97e89c24d0aa92edd2f0f4c8ee6195eee9b7c9f154865a58b02f0c1c0d  dm_sans.ttf
a5e26dc5e2e77fb2803a0bf02fd4f81ee136ec8dea863ccdb0c59a263b21378b  playfair_display_italic.ttf
```

Dans l'APK, chaque police porte dans ses métadonnées (table `name`) son
copyright, la mention de la licence et son adresse (`scripts.sil.org/OFL`) ;
le texte intégral de la licence est ici, pas dans l'APK.

« Playfair Display » est un nom réservé par sa licence : une version modifiée
de la police ne pourrait pas le garder. Pour la sous-ensembler ou la retoucher
un jour, il faudrait la renommer.
