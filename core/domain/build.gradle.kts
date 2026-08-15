plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Les JSON de thème restent là où l'application les lit — dans les assets Android — mais sont
// exposés au classpath de test de ce module. `ShippedThemesTest` valide donc les fichiers
// réellement livrés, pas une copie qui pourrait diverger.
sourceSets {
    named("test") {
        resources.srcDir("../../app/src/main/assets")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation(project(":core:hex"))
}
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation("junit:junit:4.13.2")
}
