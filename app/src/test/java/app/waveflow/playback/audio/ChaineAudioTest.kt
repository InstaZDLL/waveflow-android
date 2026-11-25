package app.waveflow.playback.audio

import android.app.Application
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.test.core.app.ApplicationProvider
import app.waveflow.model.RemoteReplayGain
import app.waveflow.playback.LectureAssemblee
import app.waveflow.playback.assemblerLaLecture
import app.waveflow.playback.toMediaItem
import app.waveflow.testing.remoteSong
import app.waveflow.testing.song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Le branchement de la chaîne sur Media3 : ce que la sortie retient de chaque
 * piste (G1, G2, G4), et l'écoute que le vrai ExoPlayer laisse lire (G7).
 *
 * Robolectric ne décode pas l'audio : aucun échantillon ne traverse ici les
 * traitements. On prouve ce qu'ils recevront, pas ce qu'on entendra.
 */
@RunWith(RobolectricTestRunner::class)
class ChaineAudioTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private var lecture: LectureAssemblee? = null

    @After
    fun tearDown() {
        lecture?.lecteur?.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    // ---------------------------------------------------------------------
    // La sortie retient le gain de chaque piste (G4)
    // ---------------------------------------------------------------------

    private fun formatAvec(metadata: Metadata?): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .setChannelCount(2)
        .setSampleRate(44_100)
        .setMetadata(metadata)
        .build()

    /** Configure la sortie pour une piste seule dans sa timeline, et rend l'identifiant de sa période. */
    private fun configurer(chaine: ChaineAudio, piste: androidx.media3.common.MediaItem, metadata: Metadata?): Any {
        val sortie = SortieQuiRetientLesGains(DefaultAudioSink.Builder(app).build(), chaine.gains)
        val timeline = SinglePeriodTimeline(60_000_000L, true, false, false, null, piste)
        val periode = timeline.getUidOfPeriod(0)
        sortie.configure(
            AudioSink.AudioSinkConfig.Builder(formatAvec(metadata))
                .setTimeline(timeline)
                .setMediaPeriodId(MediaSource.MediaPeriodId(periode))
                .build(),
        )
        sortie.release()
        return periode
    }

    private val tagsDuFichier = Metadata(
        TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", listOf("-3.00 dB")),
        TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_PEAK", listOf("0.9")),
    )

    @Test
    fun `une piste locale prend le gain de ses tags`() {
        val chaine = ChaineAudio()

        val periode = configurer(chaine, song(1).toMediaItem(), tagsDuFichier)

        assertEquals(GainDePiste(gainDb = -3.0, pic = 0.9), chaine.gains.de(periode))
    }

    /** G2 : le serveur d'abord, les tags du flux pour ce qu'il ne dit pas. */
    @Test
    fun `une piste du serveur prend ses champs, et les tags du flux pour le reste`() {
        val chaine = ChaineAudio()
        val piste = remoteSong(id = "s1")
            .copy(replayGain = RemoteReplayGain(trackGainDb = -7.5, albumGainDb = -6.0, albumPeak = 0.99))
            .toMediaItem()

        val periode = configurer(chaine, piste, tagsDuFichier)

        assertEquals(
            GainDePiste(gainDb = -7.5, pic = 0.9, gainAlbumDb = -6.0, picAlbum = 0.99),
            chaine.gains.de(periode),
        )
    }

    @Test
    fun `une piste sans tag ni champ ne porte aucun gain`() {
        val chaine = ChaineAudio()

        val periode = configurer(chaine, remoteSong(id = "s2").toMediaItem(), null)

        assertEquals(GainDePiste(), chaine.gains.de(periode))
    }

    // ---------------------------------------------------------------------
    // L'écoute, sur le vrai ExoPlayer (G7)
    // ---------------------------------------------------------------------

    private fun assembler(): LectureAssemblee =
        assemblerLaLecture(app, DefaultDataSource.Factory(app), { true }, {}).also { lecture = it }

    private fun ecouler() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `un album lance depuis sa page s'ecoute comme un disque`() {
        val l = assembler()

        l.lecteur.setMediaItems(listOf(song(1, albumId = 9), song(2, albumId = 9)).map { it.toMediaItem() })
        ecouler()

        assertEquals(Ecoute.UnDisque, l.chaineAudio.ecoute)
    }

    @Test
    fun `l'aleatoire defait le disque, et le couper le refait`() {
        val l = assembler()
        l.lecteur.setMediaItems(listOf(song(1, albumId = 9), song(2, albumId = 9)).map { it.toMediaItem() })
        ecouler()

        l.lecteur.shuffleModeEnabled = true
        ecouler()
        assertEquals(Ecoute.UnTitre, l.chaineAudio.ecoute)

        l.lecteur.shuffleModeEnabled = false
        ecouler()
        assertEquals(Ecoute.UnDisque, l.chaineAudio.ecoute)
    }

    @Test
    fun `un album du serveur s'ecoute comme un disque, une playlist melee non`() {
        val l = assembler()
        l.lecteur.setMediaItems(
            listOf(remoteSong(id = "a", albumId = "x"), remoteSong(id = "b", albumId = "x")).map { it.toMediaItem() },
        )
        ecouler()
        assertEquals(Ecoute.UnDisque, l.chaineAudio.ecoute)

        l.lecteur.addMediaItem(song(3, albumId = 4).toMediaItem())
        ecouler()
        assertEquals(Ecoute.UnTitre, l.chaineAudio.ecoute)
    }
}
