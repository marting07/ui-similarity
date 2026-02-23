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
    mainClass.set("MainKt")
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
    description = "Run MainKt. Pass args with --args='--repos /path --mode hybrid'"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("MainKt")
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ").filter { it.isNotBlank() }
    }
}

tasks.named("check") {
    dependsOn("fastTest")
}
