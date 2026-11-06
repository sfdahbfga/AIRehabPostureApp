import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// local.properties는 Git에 올라가지 않으므로, 파일이나 키가 없어도 빈 문자열로 빌드되게 한다.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val supabaseUrl = localProperties.getProperty("SUPABASE_URL", "")
val supabasePublishableKey = localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "")

android {
    namespace = "com.example.airehabpostureapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.airehabpostureapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    // 카메라 영상 (CameraX)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    // 관절 위치(Landmark) 추출 전용. 자세 판정은 Java 코드에서 한다.
    implementation(libs.mediapipe.tasks.vision)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
