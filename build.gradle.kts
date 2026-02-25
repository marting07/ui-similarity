plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("cli.SimilarityCliMainKt")
}

tasks.register<JavaExec>("fastTest") {
    group = "verification"
    description = "Run lightweight fixture-based tests via RunAllTestsKt"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("RunAllTestsKt")
}

tasks.register<JavaExec>("runPipeline") {
    group = "application"
    description = "Run legacy MainKt demo pipeline. Pass args with --args='--repos /path --mode hybrid'"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("MainKt")
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ").filter { it.isNotBlank() }
    }
}

tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Run production CLI entrypoint. Pass args with --args='scan-index --repos ... --out ...'"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("cli.SimilarityCliMainKt")
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ").filter { it.isNotBlank() }
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "cli.SimilarityCliMainKt"
    }
}

tasks.named("check") {
    dependsOn("fastTest")
}

tasks.register("desktopRun") {
    group = "application"
    description = "Run Compose Desktop experimentation app"
    dependsOn(":desktop-app:run")
}
