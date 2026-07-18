import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

val oauthClientId = providers.gradleProperty("OAUTH_CLIENT_ID")
    .orElse(providers.environmentVariable("OAUTH_CLIENT_ID"))
    .orElse(localProperties.getProperty("OAUTH_CLIENT_ID", ""))
    .get()

val backgroundAgentBaseUrl = providers.gradleProperty("BACKGROUND_AGENT_BASE_URL")
    .orElse(providers.environmentVariable("BACKGROUND_AGENT_BASE_URL"))
    .orElse(localProperties.getProperty("BACKGROUND_AGENT_BASE_URL", "https://nebians.consica.com.np"))
    .get()

android {
    namespace = "com.zeus.code"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zeus.code"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "OAUTH_CLIENT_ID", "\"${oauthClientId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "OAUTH_CALLBACK", "\"zeus://oauth\"")
        buildConfigField("String", "BACKGROUND_AGENT_BASE_URL", "\"${backgroundAgentBaseUrl.trimEnd('/').replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/zeus-release.jks")
            storePassword = "zeus-release"
            keyAlias = "zeus"
            keyPassword = "zeus-release"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.18")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
