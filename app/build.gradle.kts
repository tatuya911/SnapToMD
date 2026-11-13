plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.3.12"
}

android {
    namespace = "io.github.tatuya911.snaptomd"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    val appVersionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
    val appVersionName = (project.findProperty("versionName") as? String) ?: "1.0"

    defaultConfig {
        applicationId = "io.github.tatuya911.snaptomd"
        minSdk = 24
        37.also { targetSdk = it }
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFileOrPath = System.getenv("KEYSTORE_FILE")
                ?: (project.findProperty("KEYSTORE_FILE") as? String)
            if (keystoreFileOrPath != null) {
                val resolved = file(keystoreFileOrPath)
                if (resolved.exists()) {
                    storeFile = resolved
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                        ?: (project.findProperty("KEYSTORE_PASSWORD") as? String)
                    keyAlias = System.getenv("KEY_ALIAS")
                        ?: (project.findProperty("KEY_ALIAS") as? String)
                    keyPassword = System.getenv("KEY_PASSWORD")
                        ?: (project.findProperty("KEY_PASSWORD") as? String)
                }
            }
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
            // R8によるコード・リソースの削減と最適化を有効化（難読化は rules.keep で無効化）
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.compose.material:material-icons-extended")
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // SAFフォルダ操作
    implementation("androidx.documentfile:documentfile:1.0.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // YAML
    implementation("org.snakeyaml:snakeyaml-engine:2.9")
}

