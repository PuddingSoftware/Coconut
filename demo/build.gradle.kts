import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.File

plugins {
    id("minestom.java-binary")
    id("com.gradleup.shadow") version "9.4.2"
}

dependencies {
    implementation(project(":"))

    runtimeOnly(libs.bundles.logback)
}

application {
    mainClass.set("net.minestom.demo.Main")
    mainModule.set("net.minestom.demo")

    applicationDefaultJvmArgs += "-ea"
}

val deployJar by tasks.registering(Copy::class) {
    val shadowJarTask = tasks.named<ShadowJar>("shadowJar")

    dependsOn(shadowJarTask)

    from(shadowJarTask.flatMap { it.archiveFile })
    //into(file("${System.getProperty("user.home")}${File.separator}Desktop${File.separator}MinestomServer"))
    into(file("${System.getProperty("user.home")}${File.separator}OneDrive${File.separator}Desktop${File.separator}TestingServer"))
}