package app.waveflow.playback

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import app.waveflow.model.VoiceFocus
import app.waveflow.model.VoiceRequest
import app.waveflow.testing.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La forme d'une demande vocale entre l'hôte, l'activité et la session (R3 et
 * R10 de `docs/recherche-vocale.md`).
 */
@RunWith(RobolectricTestRunner::class)
class VoiceSearchItemsTest {

    @Test
    fun `l'element que Media3 fabrique pour onPlayFromSearch est reconnu`() {
        // Tel que `MediaSessionLegacyStub.createMediaItemForMediaRequest` le
        // construit : identifiant par défaut, pas d'URI.
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_ALBUM, "Nevermind")
        }
        val item = MediaItem.Builder()
            .setMediaId(MediaItem.DEFAULT_MEDIA_ID)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setSearchQuery("joue Nevermind").setExtras(extras).build(),
            )
            .build()

        assertEquals(
            VoiceRequest("joue Nevermind", VoiceFocus.Album, album = "Nevermind"),
            item.voiceRequestOrNull(),
        )
    }

    @Test
    fun `un element qui porte un identifiant ou une URI n'est pas une demande vocale`() {
        val parIdentifiant = MediaItem.Builder()
            .setMediaId("browse:album/100")
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("x").build())
            .build()

        // `onPlayFromUri` : l'URI voyage dans les métadonnées de la demande.
        val parUri = MediaItem.Builder()
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse("content://media/external/audio/media/1"))
                    .setSearchQuery("x")
                    .build(),
            )
            .build()

        assertNull(parIdentifiant.voiceRequestOrNull())
        assertNull(parUri.voiceRequestOrNull())
        assertNull(song(1L).toMediaItem().voiceRequestOrNull())
        assertNull(MediaItem.Builder().build().voiceRequestOrNull())
    }

    @Test
    fun `une demande fait l'aller-retour par l'element sans rien perdre`() {
        val demande = VoiceRequest("mets du Nirvana", VoiceFocus.Artist, artist = "Nirvana", title = "Lithium")

        assertEquals(demande, demande.toSearchItem().voiceRequestOrNull())
        assertEquals(demande, voiceRequestOf(demande.toExtras()))
    }

    @Test
    fun `l'intention de l'Assistant donne sa phrase et ses complements`() {
        val intention = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, "joue Nevermind")
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE)
            .putExtra(MediaStore.EXTRA_MEDIA_ALBUM, "Nevermind")
            .putExtra(MediaStore.EXTRA_MEDIA_ARTIST, "Nirvana")

        assertEquals(
            VoiceRequest("joue Nevermind", VoiceFocus.Album, album = "Nevermind", artist = "Nirvana"),
            voiceRequestOf(intention),
        )
    }

    @Test
    fun `une intention sans phrase demande toute la bibliotheque`() {
        assertEquals(VoiceRequest(""), voiceRequestOf(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)))
    }

    @Test
    fun `une intention rejouee depuis les recents est ecartee`() {
        // Rouverte depuis les récents, l'activité reçoit l'intention qui l'a
        // lancée : l'ancienne demande remplacerait la file en cours.
        val rejouee = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, "joue Nevermind")
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertNull(voiceRequestOf(rejouee))
    }

    @Test
    fun `une intention aux complements illisibles est ecartee sans planter`() {
        val piegee = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).replaceExtras(bundleIllisible())

        assertNull(voiceRequestOf(piegee))
    }

    @Test
    fun `une autre intention n'est pas une demande vocale`() {
        assertNull(voiceRequestOf(Intent(Intent.ACTION_MAIN)))
        assertNull(voiceRequestOf(null))
    }

    /**
     * Un `Bundle` tel qu'une autre application peut en glisser dans
     * l'intention : bien formé en tête, avec une valeur que rien ne sait lire.
     * Comme il arrive d'un `Parcel`, il n'est désérialisé qu'à la première
     * lecture — `getString` lève alors l'exception.
     */
    private fun bundleIllisible(): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeInt(0) // longueur, réécrite plus bas
            parcel.writeInt(BUNDLE_MAGIC)
            val debut = parcel.dataPosition()
            parcel.writeInt(1) // une entrée
            parcel.writeString(SearchManager.QUERY)
            parcel.writeInt(TYPE_INCONNU)
            val fin = parcel.dataPosition()
            parcel.setDataPosition(0)
            parcel.writeInt(fin - debut)
            parcel.setDataPosition(0)
            return requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    private companion object {
        /** `BaseBundle.BUNDLE_MAGIC` : « BNDL » en petit-boutiste. */
        const val BUNDLE_MAGIC = 0x4C444E42

        /** Aucun des codes de `Parcel.writeValue`. */
        const val TYPE_INCONNU = 9_999
    }
}
