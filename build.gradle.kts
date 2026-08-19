// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.23" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

// Contrôle de style, à cliquet.
//
// Le dépôt a déjà eu deux étapes « Spotless » et « Detekt » dans sa CI, enveloppées dans
// `|| echo "not configured, skipping"` : toujours vertes, elles ne prouvaient rien et ont été
// retirées. Celle-ci échoue pour de bon.
//
// `ratchetFrom` limite le contrôle aux fichiers qui diffèrent de `main`. Sans lui, la toute
// première exécution reprocherait 585 choses réparties sur 135 fichiers et il faudrait soit
// reformater le dépôt entier — en réécrivant l'attribution de chaque ligne — soit désactiver le
// contrôle. Avec lui, la dette se paie fichier par fichier, au moment où quelqu'un y touche de
// toute façon.
//
// Les règles retenues sont réglées dans `.editorconfig`, sur la foi d'un relevé et non d'un goût.
spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    ratchetFrom("origin/main")
}
