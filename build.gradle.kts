// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint)
}

// Lu ici et non dans `allprojects` : l'accesseur du catalogue n'existe que
// dans le contexte du projet racine.
val ktlintToolVersion = libs.versions.ktlint

// Appliqué à la racine autant qu'au module : les scripts `.kts` sont du Kotlin
// eux aussi, et rien ne les vérifierait sinon.
allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // Le plugin embarque sa propre version de l'outil ; la fixer ici évite
        // qu'une montée de version du plugin change le style sans prévenir.
        version.set(ktlintToolVersion)

        // Le style se règle dans `.editorconfig`, pas ici : c'est le seul
        // endroit que l'IDE lit aussi.
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        }
    }
}
