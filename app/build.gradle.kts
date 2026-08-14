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
    }
    testOptions {
        unitTests {
            // Robolectric a besoin des ressources et du manifest fusionnés.
            isIncludeAndroidResources = true
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