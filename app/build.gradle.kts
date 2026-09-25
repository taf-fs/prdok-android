import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Build-time configuration lives in secrets.properties at the repo root
// (see secrets.example.properties). Values end up as BuildConfig constants.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String = secrets.getProperty(name)?.trim() ?: ""

// The release workflow passes the git tag without its "v" (-PappVersion=1.2.3), and
// versionCode is derived from it so it grows with every release: 1.2.3 → 10203.
// Local builds without the property fall back to 1.0 / 1.
fun versionCodeOf(version: String): Int {
    val parts = Regex("""(\d+)\.(\d{1,2})\.(\d{1,2})""").matchEntire(version)?.groupValues
        ?: error("appVersion must look like 1.2.3 (minor and patch below 100), got '$version'")
    return parts[1].toInt() * 10_000 + parts[2].toInt() * 100 + parts[3].toInt()
}

val appVersion: String? = providers.gradleProperty("appVersion").orNull
val appVersionName = appVersion ?: "1.0"
val appVersionCode = appVersion?.let(::versionCodeOf) ?: 1

android {
    namespace = "io.tafdev.prdok"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.tafdev.prdok"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"${secret("apiBaseURL")}\"")
        buildConfigField("String", "EMPLOYEE_PORTAL_URL", "\"${secret("employeePortalURL")}\"")
        buildConfigField("String", "PAIRING_INIT_KEY", "\"${secret("pairingInitKey")}\"")
    }

    // Every release must be signed with this same key, or Android refuses to install it
    // over the previous version. Without releaseSigning.* the release APK comes out unsigned.
    signingConfigs {
        val storeFile = secret("releaseSigning.storeFile")
        if (storeFile.isNotEmpty()) {
            create("release") {
                this.storeFile = file(storeFile)
                // PKCS12 keystores (keytool's default) use one password for the store and the key.
                storePassword = secret("releaseSigning.password")
                keyAlias = secret("releaseSigning.keyAlias")
                keyPassword = secret("releaseSigning.password")
            }
        }
    }

    buildTypes {
        debug {
            // Debug-only dev pairing (see DevCredentials.kt in src/debug). Release has no
            // such fields at all, so the release source set can't even reference them.
            buildConfigField("boolean", "DEV_CREDENTIALS_ENABLED", secret("devCredentials.enabled").ifEmpty { "false" })
            buildConfigField("String", "DEV_KLIC", "\"${secret("devCredentials.klic")}\"")
            buildConfigField("String", "DEV_ID", "\"${secret("devCredentials.id")}\"")
            buildConfigField("String", "DEV_IDS", "\"${secret("devCredentials.ids")}\"")
            buildConfigField("String", "DEV_PROVOZ", "\"${secret("devCredentials.provoz")}\"")
            buildConfigField("String", "DEV_SKLADNIK", "\"${secret("devCredentials.skladnik")}\"")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kizitonwose.calendar.compose)
    implementation(libs.wheel.picker.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}