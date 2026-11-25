import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

android {
    namespace = "com.w2018.procmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.w2018.procmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.1.5"
        // 无原生库，保持全 ABI 以兼容 arm/x86 模拟器
    }

    // 签名：本地 release keystore 存在则用，否则回退 debug（便于无人值守出包）
    val storePath = localProps.getProperty("release.storeFile")
    val storePass = localProps.getProperty("release.storePassword")
    val keyAliasV = localProps.getProperty("release.keyAlias")
    val keyPass = localProps.getProperty("release.keyPassword")
    val releaseKeystore = storePath?.let { File(it.replace("\\\\", "\\")) }

    signingConfigs {
        if (releaseKeystore != null && releaseKeystore.isFile && !storePass.isNullOrBlank()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = storePass
                keyAlias = keyAliasV
                keyPassword = keyPass ?: storePass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint { abortOnError = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 纯 JVM 的 ADB 客户端（无线 ADB 直连执行 shell）
    implementation("dev.mobile:dadb:1.2.10")

    // Shizuku 官方 API / Provider（从 Maven Central 解析）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
