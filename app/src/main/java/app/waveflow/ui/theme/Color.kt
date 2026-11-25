package app.waveflow.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Les valeurs des deux matières de WaveFlow 2.0, recopiées de leurs documents
 * de référence côté bureau, qui font foi :
 *
 * - **sombre = Fluid Editorial** — `WaveFlow/docs/design/fluid-editorial.md`,
 *   `src/styles/skins/fluid.css` ;
 * - **clair = Liquid Aurora Glass, colonne « Light »** —
 *   `WaveFlow/docs/design/liquid-aurora-glass.md`, `src/styles/skins/liquid.css`.
 *
 * Les noms reprennent ceux des documents (`ink`, `hairline`, `tone`…) pour que
 * la correspondance se lise sans table. Leur place sur les rôles Material 3 est
 * décidée dans `Theme.kt`, pas ici.
 *
 * Une valeur « précomposée » est un voile translucide du document (blanc 8 %,
 * verre blanc 74 %…) déjà posé sur le fond de la page et rendu opaque : un rôle
 * Material 3 doit tenir seul, sur n'importe quel conteneur. Les voiles qui
 * doivent rester translucides — les filets — le restent.
 */

// ── Accent : la règle du palier, fixe dans ce ticket ─────────────────────────
// Le palier 400 se lit sur le sombre et prend une encre sombre ; le palier 600
// se lit sur le clair et prend une encre blanche. Le suivi de la pochette
// viendra avec les composants.
//
// L'encre blanche sur le palier 600 ne donne que 3,77:1 : assez pour un grand
// texte ou une icône, pas pour le libellé d'un bouton (4,5:1). C'est su et
// voulu, à l'identique du bureau ; la question se tranche pour toutes les
// plateformes avec le suivi de la pochette (#78).

/** Émeraude, palier 400 : l'accent du sombre. */
val Emerald400 = Color(0xFF34D399)

/** Émeraude, palier 500 : le vert de la marque, la tuile de l'icône. */
val Emerald500 = Color(0xFF10B981)

/** Émeraude, palier 600 : l'accent du clair. */
val Emerald600 = Color(0xFF059669)

// ── Fluid Editorial (sombre) ────────────────────────────────────────────────

/** `bg` — la page. Jamais un noir pur : elle doit porter la lumière de la pochette. */
val FluidBg = Color(0xFF0B0B0E)

/** `surface` — panneaux, cartes au repos. */
val FluidSurface = Color(0xFF121216)

/** `surface-2` — menus, carte survolée : la seconde marche. */
val FluidSurface2 = Color(0xFF17171D)

/** Ton blanc 8 % sur `surface`, précomposé : la marche au-dessus de `surface-2`. */
val FluidTone = Color(0xFF252529)

/** `ink` — titres, ce qui doit se lire d'abord. Chaud, pas un blanc pur. */
val FluidInk = Color(0xFFF3F1EC)

/** `ink-2` — le corps, la seconde ligne. */
val FluidInk2 = Color(0xFFB5B2AA)

/** `ink-3` — étiquettes, compteurs, horodatages, icônes au repos. */
val FluidInk3 = Color(0xFF8E8B83)

/** `hairline` — blanc 7 %, translucide : séparateurs, anneau d'un champ au repos. */
val FluidHairline = Color(0x12FFFFFF)

/** `hairline-strong` — blanc 12 %, translucide : l'anneau d'une puce, d'un champ. */
val FluidHairlineStrong = Color(0x1FFFFFFF)

/** `accent-ink` — l'encre posée sur l'accent. */
val FluidAccentInk = Color(0xFF0B0B0E)

/** `glow` — la lumière de la pochette, par défaut le vert de la marque assombri. */
val FluidGlow = Color(0xFF1F5A48)

/** `glow-deep` — la seconde nappe de lumière. */
val FluidGlowDeep = Color(0xFF0F2B22)

// ── Liquid Aurora Glass, clair ──────────────────────────────────────────────
// Le verre lui-même (flou, bords, reflets) n'est pas un jeton de couleur : ses
// niveaux sont rendus ici opaques, posés sur le fond, comme le document le
// prévoit quand le système demande moins de transparence (« every sheet turns
// solid and the direction still reads »).

/** `ground` — la page, fraîche et légèrement bleutée ; jamais blanche. */
val LiquidGround = Color(0xFFECEEF5)

/**
 * Niveau `canvas` (blanc 90 % sur le fond) : cartes et régions de contenu.
 * L'overlay — menus, dialogues — a la même teinte : seule l'ombre les sépare.
 */
val LiquidCanvas = Color(0xFFFDFDFE)

/** Niveau `elevated` (blanc 74 % sur le fond) : bande de suggestion, carte survolée. */
val LiquidElevated = Color(0xFFFAFBFC)

/** Niveau `structural` (blanc 58 % sur le fond) : barres, plaque du lecteur. */
val LiquidStructural = Color(0xFFF7F8FB)

/** Niveau `tone` (blanc 42 % sur le fond) : une puce, une pilule, un module. */
val LiquidTone = Color(0xFFF4F5F9)

/** Ton d'encre 8 % sur le fond, précomposé : la marche enfoncée. */
val LiquidToneDeep = Color(0xFFDCDEE6)

/** Encre de titre. Presque noire et froide. */
val LiquidInk = Color(0xFF1D1E2B)

/** Encre du corps. */
val LiquidInk2 = Color(0xFF45475D)

/** Encre des étiquettes. */
val LiquidInk3 = Color(0xFF666880)

/** `hairline` — encre 8 %, translucide. */
val LiquidHairline = Color(0x141F2138)

/** `hairline-strong` — encre 14 %, translucide. */
val LiquidHairlineStrong = Color(0x241F2138)

/** `glow` — la lumière de la pochette en clair, par défaut un bleu de glace. */
val LiquidGlow = Color(0xFF9FB8EE)

/** `glow-deep` en clair : une lavande. */
val LiquidGlowDeep = Color(0xFFC9BDF6)

// Les cinq teintes pastel : l'atmosphère et les humeurs, **jamais** le texte
// ni les actions. Rien ne les emploie encore ; elles sont ici pour que les
// composants du verre les prennent au thème plutôt qu'en dur.

/** Lavande — le sommeil ; la première nappe. */
val LiquidLavender = Color(0xFFCFC3FA)

/** Glace — la concentration ; la deuxième nappe. */
val LiquidIce = Color(0xFFC4DCFB)

/** Aqua — le sport ; le départ de la ligne de lumière. */
val LiquidAqua = Color(0xFFB2E6E6)

/** Menthe — la détente ; la troisième nappe. */
val LiquidMint = Color(0xFFC0ECD5)

/** Rose — la fête. */
val LiquidBlush = Color(0xFFF8C9D8)
