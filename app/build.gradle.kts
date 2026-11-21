import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionName = "1.0.0"
val appVersionCode = 1

// Assinatura de release: keystore.properties local (fora do git) ou variáveis de ambiente no CI.
val keystoreProps =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

fun signingValue(
    key: String,
    env: String,
): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists()

android {
    namespace = "io.github.mbsantos97.camerasaovivo"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.mbsantos97.camerasaovivo"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                if (hasReleaseSigning) {
                    signingConfigs.getByName("release")
                } else {
                    // Sem keystore (ex.: PR no CI) o release sai assinado com a chave de debug, só para teste.
                    signingConfigs.getByName("debug")
                }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    androidResources {
        localeFilters += listOf("pt-rBR")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.kotlin_module", "DebugProbesKt.bin")
        }
        jniLibs {
            // Sem compressão: o Android usa as .so direto do APK (não extrai). Instalado ocupa ~42 MB em vez de ~61 MB
            // (APK 22 MB + 38,6 MB extraídos). Ver docs/DECISOES.md.
            useLegacyPackaging = false
            // Nenhuma TV Box alvo é x86 32-bit.
            excludes += "lib/x86/**"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Checagens que dependem de internet ou de versões futuras não fazem sentido no CI.
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
        lintConfig = file("lint.xml")
    }
}

androidComponents {
    // x86_64 existe só para o emulador: no release, o APK não leva as bibliotecas nativas x86_64.
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.jniLibs.excludes
            .add("lib/x86_64/**")
    }
}

composeCompiler {
    if (project.hasProperty("composeReports")) {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.metrics.performance)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.tv.material)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.libvlc)
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)

    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kxml2)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Copia os APKs de release para dist/ com o nome CamerasAoVivo-v{versao}-{abi}.apk.
tasks.register<Copy>("distApks") {
    group = "distribution"
    description = "Copia os APKs de release para a pasta dist/ na raiz."
    dependsOn("assembleRelease")
    val version = appVersionName
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
        exclude("*x86_64*")
    }
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { name ->
        val abi =
            when {
                name.contains("armeabi-v7a") -> "armeabi-v7a"
                name.contains("arm64-v8a") -> "arm64-v8a"
                else -> "universal"
            }
        "CamerasAoVivo-v$version-$abi.apk"
    }
}

// Screenshots pela JVM (Robolectric, gráficos nativos): ./gradlew :app:testDebugUnitTest -Pscreenshots
tasks.withType<Test>().configureEach {
    systemProperty("screenshots", project.hasProperty("screenshots").toString())
    systemProperty("screenshotsDir", rootProject.layout.projectDirectory.dir("docs/screens").asFile.absolutePath)
    systemProperty("robolectric.graphicsMode", "NATIVE")
    systemProperty("lanProbe", project.hasProperty("lanProbe").toString())
    systemProperty("realCameras", project.findProperty("realCameras")?.toString().orEmpty())
    systemProperty("realOut", project.findProperty("realOut")?.toString().orEmpty())
    listOf("ptzCam", "ptzPass", "ptzX", "ptzMs").forEach { systemProperty(it, project.findProperty(it)?.toString().orEmpty()) }
    systemProperty("mockCamera", project.findProperty("mockCamera")?.toString().orEmpty())
}
