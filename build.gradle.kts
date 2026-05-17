plugins {
    alias(libs.plugins.detekt)
}

repositories {
    mavenCentral()
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom(subprojects.map { it.file("src/main") })
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}
