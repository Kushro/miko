plugins {
    id("mihon.library")
    kotlin("android")
}

android {
    namespace = "tachiyomi.source.kotatsu"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        // Required to extend/instantiate the parsers library types (generateUid, AbstractMangaParser, ...)
        freeCompilerArgs.add("-opt-in=org.koitharu.kotatsu.parsers.InternalParsersApi")
    }
}

dependencies {
    implementation(projects.sourceApi)
    implementation(projects.core.common)
    implementation(projects.i18nMiko)

    // Android already ships org.json, bundling the library's copy breaks D8 with a duplicate class.
    implementation(libs.kotatsuParsers) {
        exclude(group = "org.json", module = "json")
    }

    implementation(libs.bundles.okhttp)
    implementation(libs.jsoup)
    implementation(libs.preferencektx)

    implementation(androidx.corektx)
    implementation(androidx.annotation)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
