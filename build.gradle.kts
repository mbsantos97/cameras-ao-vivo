plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    // Declarado só para fixar a versão do Kotlin usada pelo Kotlin embutido do AGP 9.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.baselineprofile) apply false
}

// ktlint via CLI oficial (independe de plugin de terceiros e do Kotlin embutido do AGP 9).
val ktlint: Configuration by configurations.creating

dependencies {
    ktlint(libs.ktlint.cli) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}

val ktlintPatterns = listOf("**/src/**/*.kt", "**.kts", "!**/build/**")

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Verifica o estilo do código Kotlin com ktlint."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = ktlintPatterns
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Corrige o estilo do código Kotlin com ktlint."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args = listOf("-F") + ktlintPatterns
}
