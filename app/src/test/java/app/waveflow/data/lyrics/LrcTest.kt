package app.waveflow.data.lyrics

import app.waveflow.model.Lyrics
import app.waveflow.model.SyncedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La lecture d'un texte de paroles : LRC s'il porte des horodatages, texte simple
 * sinon (`docs/paroles.md`, P9 et P10).
 */
class LrcTest {

    private fun synchronisees(texte: String): List<SyncedLine> =
        (Lrc.parse(texte) as Lyrics.Synced).lines

    @Test
    fun `les horodatages se lisent a toutes les precisions`() {
        val lignes = synchronisees(
            """
            [00:01]une
            [00:02.5]deux
            [00:03.25]trois
            [00:04.125]quatre
            [00:05:50]cinq
            [61:00.00]tard
            """.trimIndent(),
        )

        assertEquals(
            listOf(1_000L, 2_500L, 3_250L, 4_125L, 5_500L, 3_660_000L),
            lignes.map { it.startMs },
        )
        assertEquals(listOf("une", "deux", "trois", "quatre", "cinq", "tard"), lignes.map { it.text })
    }

    @Test
    fun `plusieurs horodatages repetent la ligne, rangee par instant`() {
        val lignes = synchronisees(
            """
            [00:10.00][00:30.00]Refrain
            [00:20.00]Couplet
            """.trimIndent(),
        )

        assertEquals(
            listOf(SyncedLine(10_000, "Refrain"), SyncedLine(20_000, "Couplet"), SyncedLine(30_000, "Refrain")),
            lignes,
        )
    }

    @Test
    fun `a instant egal, l'ordre du texte departage`() {
        val lignes = synchronisees("[00:05.00]premier\n[00:01.00]avant\n[00:05.00]second")

        assertEquals(listOf("avant", "premier", "second"), lignes.map { it.text })
    }

    @Test
    fun `un decalage positif avance les paroles, sans passer sous zero`() {
        val lignes = synchronisees("[offset:+500]\n[00:00.20]tôt\n[00:02.00]ligne")

        assertEquals(listOf(0L, 1_500L), lignes.map { it.startMs })
    }

    @Test
    fun `un decalage negatif les retarde`() {
        val lignes = synchronisees("[offset:-250]\n[00:02.00]ligne")

        assertEquals(2_250L, lignes.single().startMs)
    }

    @Test
    fun `les balises d'en-tete et les instants de mot disparaissent`() {
        val lignes = synchronisees(
            """
            [ar:Bruit de Fond]
            [ti:Résonance]
            [length: 03:20]
            [00:01.00]<00:01.00>Mot <00:01.50>par <00:02.00>mot
            """.trimIndent(),
        )

        assertEquals(listOf(SyncedLine(1_000, "Mot par mot")), lignes)
    }

    @Test
    fun `une ligne horodatee vide est gardee comme pont`() {
        val lignes = synchronisees("[00:01.00]chant\n[00:04.00]\n[00:09.00]reprise")

        assertEquals(listOf("chant", "", "reprise"), lignes.map { it.text })
    }

    @Test
    fun `fins de ligne Windows et marque d'ordre d'octets se lisent`() {
        val lignes = synchronisees(Char(0xFEFF) + "[00:01.00]une\r\n[00:02.00]deux\r\n")

        assertEquals(listOf("une", "deux"), lignes.map { it.text })
    }

    @Test
    fun `sans horodatage, un texte simple, strophes gardees et balises retirees`() {
        val paroles = Lrc.parse(
            """

            [ar:Bruit de Fond]
            [Refrain]
            Première ligne

            Seconde strophe

            """.trimIndent(),
        )

        assertEquals(Lyrics.Plain(listOf("[Refrain]", "Première ligne", "", "Seconde strophe")), paroles)
    }

    @Test
    fun `une ligne entre crochets qui n'est pas une balise du format reste du texte`() {
        val paroles = Lrc.parse("[Chorus: Kanye West]\nUne ligne\n[Intro:Drake]\n[ar:Quelqu'un]")

        assertEquals(Lyrics.Plain(listOf("[Chorus: Kanye West]", "Une ligne", "[Intro:Drake]")), paroles)
    }

    @Test
    fun `rien a lire ne fait pas de paroles`() {
        assertNull(Lrc.parse(""))
        assertNull(Lrc.parse("  \n\n "))
        assertNull(Lrc.parse("[ar:Quelqu'un]\n[ti:Quelque chose]"))
        // Des instants sans un seul mot ne sont pas des paroles.
        assertNull(Lrc.parse("[00:01.00]\n[00:02.00]"))
    }
}
