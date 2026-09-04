plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// versionCode: total commit count (strictly increasing, works whether or not HEAD is tagged).
// versionName: nearest git tag, e.g. "0.17.1", with "-<n>-g<sha>[-dirty]" appended when HEAD
// isn't exactly on a tag. Requires full history (CI checkout must use fetch-depth: 0).
fun runGit(vararg args: String): String = try {
    val process = ProcessBuilder(listOf("git") + args).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    output
} catch (e: Exception) {
    ""
}

fun gitVersionCode(): Int = runGit("rev-list", "--count", "HEAD").toIntOrNull() ?: 1

fun gitVersionName(): String =
    runGit("describe", "--tags", "--always", "--dirty").removePrefix("v").ifBlank { "0.0.0" }

android {
    namespace = "com.example.calorietracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.calorietracker"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode()
        versionName = gitVersionName()
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: (project.findProperty("calorieTrackerStorePassword") as String?)
            keyAlias = "calorietracker"
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: (project.findProperty("calorieTrackerKeyPassword") as String?)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.glance:glance-appwidget:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
