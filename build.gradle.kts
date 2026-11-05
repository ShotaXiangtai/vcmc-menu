plugins {
    kotlin("jvm") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "jp.vcmc"
version = "1.0.0"

val foliaApiVersion = "26.3.build.6-alpha"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("dev.folia:folia-api:$foliaApiVersion")

    testImplementation(kotlin("test"))
    testImplementation("dev.folia:folia-api:$foliaApiVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        showExceptions = true
        showCauses = true
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    // Kotlin 標準ライブラリを同梱する。Paper のプラグインローダーはプラグインごとに分離されるためリロケートは不要。
    exclude("META-INF/*.kotlin_module")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
