plugins {
    alias(libs.plugins.detekt)
}

repositories {
    mavenCentral()
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
    source.setFrom(subprojects.flatMap { listOf(it.file("src/main"), it.file("src/test")) })
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}
