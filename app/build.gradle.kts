import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 签名信息从 keystore.properties 读取，该文件不入库（模板见 keystore.properties.example）。
// 缺失时 release 变体将产出未签名 APK，但不影响 debug 构建。
val keystorePropsFile = rootProject.file("keystore.properties")
val hasSigningConfig = keystorePropsFile.exists()
val keystoreProps = Properties().apply {
    if (hasSigningConfig) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.baodan.keeper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.baodan.keeper"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.7"

        // App 内「检查更新」指向的 GitHub 仓库。
        // 通常无需改这里：在 gradle.properties 里覆盖即可（见该文件注释）。
        val ghOwner = findProperty("policykeeper.github.owner")?.toString() ?: "YOUR_GITHUB_USERNAME"
        val ghRepo = findProperty("policykeeper.github.repo")?.toString() ?: "PolicyKeeper"
        buildConfigField("String", "GITHUB_OWNER", "\"$ghOwner\"")
        buildConfigField("String", "GITHUB_REPO", "\"$ghRepo\"")
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = keystoreProps.getProperty("storeFile")
                    ?.let { rootProject.file(it) }
                    ?: rootProject.file("keystore/policykeeper.jks")
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        // 更新功能需要读取 BuildConfig 里的仓库地址
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
