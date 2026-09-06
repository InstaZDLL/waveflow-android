plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

ksp {
    // Schémas versionnés dans le dépôt : indispensable pour écrire et relire
    // les migrations quand le schéma bougera.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "app.waveflow"
    // 37 imposé par core-ktx 1.19 et lifecycle 2.11, qui refusent de se lier à
    // une API plus ancienne. Indépendant de `targetSdk`, qui reste à 36 : rien
    // ici n'opte pour les nouveaux comportements d'exécution.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.waveflow"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Pour la version affichée dans Réglages ▸ À propos : c'est la seule
        // source qui suit le `versionName` sans qu'on ait à la recopier.
        buildConfig = true
    }
    lint {
        // Le lint Android voit ce que ktlint et Detekt ne peuvent pas voir :
        // niveaux d'API, ressources, manifeste, accessibilité. Il échoue sur
        // les erreurs — c'est son réglage par défaut — et laisse les
        // avertissements visibles sans bloquer.
        //
        // Les montées de version sont le travail de Dependabot, qui ouvre une
        // demande par dépendance ; les répéter à chaque build ne produirait
        // qu'un bruit dont on apprendrait à ne plus rien lire.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // `targetSdk` reste volontairement en deçà de `compileSdk` : rien ici
        // n'opte pour les nouveaux comportements d'exécution (voir plus haut).
        disable += "OldTargetApi"
    }
    testOptions {
        unitTests {
            // Robolectric a besoin des ressources et du manifest fusionnés.
            isIncludeAndroidResources = true

            // Le test de migration lit les schémas que le compilateur Room
            // exporte. Le chemin est passé en clair plutôt que déduit d'un
            // répertoire de travail dont rien ne garantit lequel il est.
            all { test -> test.systemProperty("waveflow.schemas", "$projectDir/schemas") }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Audio — Media3 / ExoPlayer + MediaSession
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)

    // Pochettes
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette)

    // Room — playlists locales
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Serveur WaveFlow — client HTTP et jetons de session
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    // Tests Compose sur la JVM : `createComposeRule()` tourne sous Robolectric,
    // sans émulateur. Les mêmes artefacts servaient en `androidTest`, dont le
    // source set n'a jamais existé — ils sont ici pour la première fois utiles.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // L'activité vide qui héberge le contenu sous test vient de ce manifest ;
    // les tests unitaires fusionnent celui de la variante debug.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/*
 * Fait remonter les avertissements du lint au journal du build.
 *
 * AGP 9 produit toujours le rapport texte, mais dans un fichier que la CI
 * n'ouvre pas — et les deux propriétés qui l'y amenaient, `textReport` et
 * `textOutput`, sont dépréciées ensemble. Les garder coûterait un avertissement
 * de compilation à chaque build, alors que le dépôt tient à n'en avoir aucun.
 *
 * Le fichier est donc lu et réimprimé. Le chemin est résolu à la configuration,
 * hors du `doLast`, pour rester compatible avec le cache de configuration.
 *
 * Une tâche à part, et non un `doLast` sur `lintDebug` : les actions d'une tâche
 * sont sautées si elle échoue, c'est-à-dire précisément quand le lint a trouvé
 * une erreur et qu'on veut savoir laquelle. `finalizedBy` s'exécute dans les
 * deux cas.
 *
 * `upToDateWhen { false }` parce que cette tâche ne produit rien : son travail
 * est d'imprimer, et une tâche sans sortie serait tenue pour à jour.
 *
 * Le chemin est écrit en clair plutôt que pris à `SingleArtifact.LINT_TEXT_REPORT`.
 * Passer par l'artefact demanderait une classe de tâche et un `onVariants`, pour
 * se prémunir d'un déplacement de fichier que rien n'annonce. À reprendre le
 * jour où le chemin bougera — la tâche se taira alors sans rien casser, le
 * fichier absent étant traité comme tel.
 */
val afficherRapportLint = tasks.register("afficherRapportLint") {
    description = "Réimprime le rapport texte du lint dans le journal du build."
    val rapport = layout.buildDirectory.file("reports/lint-results-debug.txt")
    outputs.upToDateWhen { false }

    doLast {
        val fichier = rapport.get().asFile
        if (!fichier.exists()) return@doLast

        val texte = fichier.readText().trim()
        // « No issues found. » n'apprend rien et noierait le journal d'un build
        // propre : seul ce qui demande une décision est réimprimé.
        if (texte.isNotEmpty() && !texte.startsWith("No issues found")) {
            logger.lifecycle(texte)
        }
    }
}

// Nommée exactement : AGP crée plusieurs tâches qui commencent par `lint` —
// `lintReportDebug`, `lintAnalyzeDebug` — et les prendre toutes imprimait le
// rapport trois fois.
tasks.matching { it.name == "lintDebug" }.configureEach { finalizedBy(afficherRapportLint) }
