// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Lu ici et non dans `allprojects` : l'accesseur du catalogue n'existe que
// dans le contexte du projet racine.
val ktlintToolVersion = libs.versions.ktlint
val detektToolVersion = libs.versions.detekt

// Appliqué à la racine autant qu'au module : les scripts `.kts` sont du Kotlin
// eux aussi, et rien ne les vérifierait sinon.
allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

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

// Detekt part de sa configuration livrée et ne surcharge que ce que le dépôt
// fait autrement — `config/detekt/detekt.yml` n'a donc qu'à porter les écarts.
subprojects {
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        toolVersion = detektToolVersion.get()
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        // Ce que l'analyse trouvait le jour où elle a été branchée. Elle ne
        // signale donc plus que ce qui est arrivé depuis — ce qu'on cherche —
        // et la dette d'avant reste lisible dans le fichier plutôt que noyée
        // dans un rapport que personne ne relit.
        baseline = rootProject.file("config/detekt/baseline.xml")
    }
}
