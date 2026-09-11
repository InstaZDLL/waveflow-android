package app.waveflow.playback

import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import app.waveflow.model.StreamRendering
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ConcurrentHashMap

/**
 * Le pont entre [BrowseTree] et ce que Media3 attend d'une bibliothèque.
 *
 * Séparé du service pour être éprouvable sans en démarrer un : tout ce qui suit
 * est de la traduction, et une traduction se vérifie.
 *
 * Les réponses de navigation sont immédiates. L'arbre lit un instantané déjà en
 * mémoire ; rien ici ne part sur le réseau ni sur le disque, et un hôte comme
 * Android Auto n'attend pas. Seul l'ajout à la file peut attendre, et seulement
 * au démarrage à froid — voir [addMediaItems].
 *
 * @param rendering le rendu à demander au serveur pour les pistes distantes,
 *   dès qu'il est connu.
 */
class BrowseCallback(
    private val tree: BrowseTree,
    private val rendering: () -> ListenableFuture<StreamRendering>,
) : MediaLibrarySession.Callback {

    /**
     * Les nœuds qu'un navigateur a demandé à suivre, au moins une fois.
     *
     * Media3 tient la liste des abonnés d'un nœud donné, mais ne sait pas dire
     * quels nœuds ont un abonné : il faut donc les retenir pour savoir qui
     * prévenir quand la bibliothèque change. C'est aussi ce qui borne le coût —
     * notifier tous les albums d'une bibliothèque reviendrait à la parcourir une
     * fois par album, quand un navigateur n'en regarde qu'un.
     *
     * Une liste de candidats, et non l'état des abonnements : c'est la session
     * qui dit lesquels valent encore, et elle seule peut le dire. Deux
     * navigateurs peuvent suivre le même nœud, et compter les abonnements ici
     * reviendrait à tenir en double une comptabilité qu'elle tient déjà — un
     * désabonnement retirerait un nœud que l'autre regarde encore.
     *
     * Concurrent parce que rien ne garantit que les abonnements et les mises à
     * jour de la bibliothèque arrivent du même fil.
     */
    private val watched: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        watched += parentId
        // Le comportement par défaut est conservé : c'est lui qui envoie au
        // navigateur le premier état du nœud, aussitôt après l'abonnement.
        return super.onSubscribe(session, browser, parentId, params)
    }

    /**
     * Prévient les navigateurs que l'arbre a changé sous eux.
     *
     * Sans cet appel, un navigateur garde ce qu'il a lu la première fois. Le cas
     * n'est pas marginal : Android Auto se connecte au démarrage de la voiture,
     * avant que la bibliothèque de l'appareil ne soit lue, et resterait donc
     * devant un arbre vide jusqu'à ce qu'on l'oblige à redemander.
     *
     * Les nœuds que plus personne ne regarde sont oubliés au passage. On le
     * demande à la session plutôt que de le déduire des désabonnements : un
     * navigateur peut disparaître sans se désabonner, et un nœud que deux
     * navigateurs suivent reste suivi quand l'un des deux s'en va.
     */
    fun notifySubscribers(session: MediaLibrarySession) {
        watched.removeAll { session.getSubscribedControllers(it).isEmpty() }
        watched.forEach { parentId ->
            session.notifyChildrenChanged(parentId, tree.children(parentId).size, /* params = */ null)
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(tree.root(), params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val slice = tree.children(parentId).page(page, pageSize)

        return Futures.immediateFuture(
            LibraryResult.ofItemList(ImmutableList.copyOf(slice), params),
        )
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = tree.item(mediaId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))

        return Futures.immediateFuture(LibraryResult.ofItem(item, null))
    }

    /**
     * Donne au lecteur des éléments qu'il sait ouvrir.
     *
     * Ceux que l'hôte renvoie viennent de [BrowseTree.children] : ils portent un
     * `mediaId` et des métadonnées, mais **pas d'URI** — l'hôte les affiche, il
     * ne les lit pas. Sans cette résolution, le lecteur recevrait des éléments
     * sans source et ne jouerait rien.
     *
     * Un élément qui porte déjà une URI est laissé tel quel : il vient alors de
     * l'application elle-même, qui construit ses files complètes.
     */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = addMediaItems(mediaItems)

    /**
     * Ce que [onAddMediaItems] répond, sans la session qu'il ne consulte pas.
     *
     * Les pistes du serveur y reçoivent le rendu choisi. C'est ici, dans le
     * service, et non dans l'application qui bâtit la file : une file arrive
     * aussi d'Android Auto ou d'une reprise de lecture, sans écran ouvert.
     *
     * Le rendu est lu **une fois pour toute la file**. Une file ne doit pas se
     * partager entre deux qualités parce que le réglage a changé pendant qu'on
     * la traduisait.
     *
     * La réponse attend ce rendu s'il n'est pas encore connu — quelques
     * millisecondes au démarrage à froid, le temps de lire le fichier de
     * préférences. Poser le défaut à sa place ferait partir en qualité
     * d'origine une écoute réglée en Économie, précisément sur le forfait
     * qu'on voulait ménager.
     */
    internal fun addMediaItems(mediaItems: List<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
        val resolved = mediaItems.flatMap { item ->
            if (item.localConfiguration != null) listOf(item) else tree.resolve(item.mediaId)
        }

        return Futures.transform(
            rendering(),
            { choisi -> resolved.map { it.withRendering(choisi) }.toMutableList() },
            MoreExecutors.directExecutor(),
        )
    }
}

/**
 * La tranche que l'hôte réclame, bornée à ce qui existe.
 *
 * Une page hors bornes n'est pas une erreur : c'est la fin de la liste. Rendre
 * un échec ferait apparaître un avertissement là où il n'y a simplement rien de
 * plus à voir — et une tranche calculée sans borne lèverait, ce qui couperait
 * la navigation au lieu de la terminer.
 *
 * Une taille de page nulle ou négative ne décrit aucune tranche : rien à rendre.
 */
internal fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    if (pageSize <= 0 || page < 0) return emptyList()

    val from = (page.toLong() * pageSize).coerceAtMost(size.toLong()).toInt()
    val to = (from.toLong() + pageSize).coerceAtMost(size.toLong()).toInt()
    return subList(from, to)
}
