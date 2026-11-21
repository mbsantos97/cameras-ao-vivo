// Câmera Yoosee simulada (ONVIF na porta 5000 + WS-Discovery), só JDK, para testar o app sem câmera real.
// Uso: ./gradlew :mock-camera:run --args="--variant dual-stacked --rtsp-host 192.168.0.10"
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

application {
    mainClass.set("br.com.aoVivo.mockcamera.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
