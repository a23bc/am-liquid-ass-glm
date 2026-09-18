import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull
        ?: releaseSigningProperties.getProperty(propertyName)

val releaseStoreFilePath = releaseSigningValue("AMLIQUIDASS_RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword = releaseSigningValue("AMLIQUIDASS_RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = releaseSigningValue("AMLIQUIDASS_RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = releaseSigningValue("AMLIQUIDASS_RELEASE_KEY_PASSWORD", "keyPassword")
val releaseSigningAvailable = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.example.amliquidass"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.example.amliquidass"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // Fall back to debug signing so the release APK is installable
            // out-of-the-box. Provide a real keystore via env vars or
            // keystore.properties to override.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly("io.github.libxposed:service:102.0.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
