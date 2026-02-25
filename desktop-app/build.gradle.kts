plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.6.11"
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(project(":"))
    implementation(compose.desktop.currentOs)
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "desktop.DesktopAppKt"
    }
}
