import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
providers.gradleProperty("capriBuildDir").orNull?.let { layout.buildDirectory.set(file(it)) }
val manualCapture = providers.gradleProperty("manualCapture").orNull != "false"

android {
    namespace = "com.capri.companion"
    compileSdk = 36
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.capri.companion"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"
        buildConfigField("boolean", "MANUAL_CAPTURE", manualCapture.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake {
            arguments += listOf("-DANDROID_STL=c++_shared", "-DGGML_NATIVE=OFF", "-DBUILD_SHARED_LIBS=OFF")
            cppFlags += listOf("-O3", "-DNDEBUG")
            cFlags += listOf("-O3", "-DNDEBUG")
        } }
    }
    buildFeatures { compose = true; buildConfig = true }
    androidResources { noCompress += "gguf" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
if (manualCapture) {
    android.sourceSets["debug"].manifest.srcFile("src/manual/AndroidManifest.xml")
    android.sourceSets["release"].manifest.srcFile("src/manual/AndroidManifest.xml")
}

val capriLocal = Properties().apply { rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) } }
val executableSuffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
val cmakeHome = file(capriLocal.getProperty("cmake.dir") ?: "${android.sdkDirectory}/cmake/3.22.1")
val ndkHome = file(capriLocal.getProperty("ndk.dir") ?: "${android.sdkDirectory}/ndk/27.2.12479018")
val fastBuild = layout.buildDirectory.dir("native-fast-arm64")
val fastJni = layout.buildDirectory.dir("generated/fast-jni")
val configureFastArm = tasks.register<Exec>("configureFastArm") {
    inputs.file("src/main/cpp/CMakeLists.txt")
    outputs.file(fastBuild.map { it.file("CMakeCache.txt") })
    commandLine("${cmakeHome}/bin/cmake$executableSuffix", "-S", file("src/main/cpp"), "-B", fastBuild.get().asFile,
        "-G", "Ninja", "-DCMAKE_MAKE_PROGRAM=${cmakeHome}/bin/ninja$executableSuffix",
        "-DCMAKE_TOOLCHAIN_FILE=${ndkHome}/build/cmake/android.toolchain.cmake", "-DANDROID_ABI=arm64-v8a",
        "-DANDROID_PLATFORM=android-26", "-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release",
        "-DBUILD_SHARED_LIBS=OFF", "-DQWEN3_ANDROID_FAST_ARM=ON", "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${fastBuild.get().asFile}/lib")
}
val buildFastArm = tasks.register<Exec>("buildFastArm") {
    dependsOn(configureFastArm)
    inputs.files(fileTree("src/main/cpp"), fileTree("../third_party/qwen3-tts.cpp/src"), fileTree("../third_party/qwen3-tts.cpp/ggml/src"), fileTree("../third_party/qwen3-tts.cpp/ggml/include"))
    outputs.file(fastBuild.map { it.file("lib/libqwen3_tts_jni_fast.so") })
    commandLine("${cmakeHome}/bin/cmake$executableSuffix", "--build", fastBuild.get().asFile, "--target", "qwen3_tts_jni_fast", "-j", "4")
}
val packageFastArm = tasks.register<Copy>("packageFastArm") {
    dependsOn(buildFastArm)
    from(fastBuild.map { it.file("lib/libqwen3_tts_jni_fast.so") })
    into(fastJni.map { it.dir("arm64-v8a") })
}
android.sourceSets["main"].jniLibs.srcDir(fastJni)
tasks.named("preBuild") { dependsOn(packageFastArm) }

if (providers.gradleProperty("bundleQwen").orNull == "true") {
    val modelDir = file(providers.gradleProperty("qwenModelsDir").orNull ?: rootProject.file("models/qwen").absolutePath)
    val bundled = layout.buildDirectory.dir("generated/bundled-voice")
    val includeQwen = tasks.register<Copy>("includeQwenModels") {
        from(modelDir) { include("qwen-talker-0.6b-base-Q4_K_M.gguf", "qwen-tokenizer-12hz-Q4_K_M.gguf"); into("qwen-bundle") }
        into(bundled)
        doFirst { require(modelDir.resolve("qwen-talker-0.6b-base-Q4_K_M.gguf").isFile && modelDir.resolve("qwen-tokenizer-12hz-Q4_K_M.gguf").isFile) { "Run scripts/fetch-qwen.py or set -PqwenModelsDir before bundling Qwen." } }
    }
    android.sourceSets["main"].assets.srcDir(bundled)
    tasks.named("preBuild") { dependsOn(includeQwen) }
}

val signingFile = rootProject.file("signing.properties")
if (signingFile.isFile) {
    val signingValues = Properties().apply { signingFile.inputStream().use { load(it) } }
    android.signingConfigs.create("distribution") {
        storeFile = rootProject.file(signingValues.getProperty("storeFile"))
        storePassword = signingValues.getProperty("storePassword")
        keyAlias = signingValues.getProperty("keyAlias")
        keyPassword = signingValues.getProperty("keyPassword")
    }
    android.buildTypes.getByName("release").signingConfig = android.signingConfigs.getByName("distribution")
}
if (providers.gradleProperty("previewSigning").orNull == "true") {
    android.buildTypes.getByName("release").signingConfig = android.signingConfigs.getByName("debug")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

