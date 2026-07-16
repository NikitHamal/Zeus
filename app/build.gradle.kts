import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("kotlin-parcelize")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
var hasKeystoreProps = false
if (keystorePropsFile.exists()) {
    keystoreProperties.load(keystorePropsFile.inputStream())
    hasKeystoreProps = true
}

// Fallback to rootProject local zeus-release.keystore check
fun getProp(name: String, envFallback: String? = null): String? {
    return (System.getenv(name) ?: keystoreProperties.getProperty(name) ?: envFallback)?.toString()
}

android {
    namespace = "com.zeus.git"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zeus.git"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"" + (System.getenv("GITHUB_OAUTH_CLIENT_ID") ?: (project.findProperty("GITHUB_CLIENT_ID") as String? ?: "")) + "\"")
        buildConfigField("String", "GITHUB_CLIENT_SECRET", "\"" + (System.getenv("GITHUB_OAUTH_CLIENT_SECRET") ?: (project.findProperty("GITHUB_CLIENT_SECRET") as String? ?: "")) + "\"")
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasKeystoreProps) {
                storeFile = file(keystoreProperties["storeFile"] as String? ?: "../zeus-release.keystore")
                storePassword = keystoreProperties["storePassword"] as String?
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
            } else {
                // Fallback: Check for default keystore in project root, else use debug keystore impl
                val defaultKeystore = rootProject.file("zeus-release.keystore")
                if (defaultKeystore.exists()) {
                    storeFile = defaultKeystore
                    storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "zeus123"
                    keyAlias = System.getenv("KEY_ALIAS") ?: "zeus"
                    keyPassword = System.getenv("KEY_PASSWORD") ?: "zeus123"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasKeystoreProps || rootProject.file("zeus-release.keystore").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // For JGit and other legacy libs
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3-window-size-class")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    // Datastore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Ktor - GitHub API
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // JGit - 6.7.0.202309050840-r
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r") {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:6.7.0.202309050840-r")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("com.jcraft:jsch:0.1.55")

    // Accompanist
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    implementation("com.google.accompanist:accompanist-placeholder:0.32.0")

    // Coil - Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil:2.5.0")

    // Security - Crypto for token storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Serialization, Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.datastore:datastore:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform(composeBom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // File handling
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.apache.commons:commons-compress:1.25.0")
}
