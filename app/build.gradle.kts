plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.pickles"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.pickles"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.3.1"
    }

    // Release signing comes from environment variables or Gradle properties (for example in
    // ~/.gradle/gradle.properties or CI secrets), never from files in this repository:
    //   PICKLES_KEYSTORE_PATH, PICKLES_KEYSTORE_PASSWORD, PICKLES_KEY_ALIAS, PICKLES_KEY_PASSWORD
    // When they are absent (e.g. a contributor's local build) the release build falls back to the
    // debug key so it still builds and installs.
    fun signingValue(name: String): String? =
        (System.getenv(name) ?: project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

    val keystorePath = signingValue("PICKLES_KEYSTORE_PATH")
    val keystorePassword = signingValue("PICKLES_KEYSTORE_PASSWORD")
    val keyAliasValue = signingValue("PICKLES_KEY_ALIAS")
    val keyPasswordValue = signingValue("PICKLES_KEY_PASSWORD")
    val hasReleaseSigning =
        keystorePath != null && keystorePassword != null && keyAliasValue != null && keyPasswordValue != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystorePath!!) // absolute, or relative to the repo root
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("Pickles: PICKLES_KEYSTORE_* not set; release build is signed with the debug key.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("robolectric.conscryptMode", "OFF")
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("roborazzi.record.filePathStrategy", "relativePathFromRoborazziContextOutputDirectory")
            }
        }
    }
}

dependencies {
    implementation(project(":engine"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    // UI screenshot smoke tests on the JVM (Robolectric + Roborazzi).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Optional: run JVM unit tests with a specific java binary (e.g. on hosts where Robolectric's
// native graphics runtime is unavailable). Usage: -PtestJavaExecutable=/path/to/java
tasks.withType<Test>().configureEach {
    (project.findProperty("testJavaExecutable") as String?)?.let { executable = it }
    (project.findProperty("testJvmArgs") as String?)?.let { jvmArgs(it.split(" ")) }
}
