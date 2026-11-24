plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.license.report) apply false
}

val detektRuntime by configurations.creating
val ktlintRuntime by configurations.creating

dependencies {
    detektRuntime("io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detekt.get()}:all")
    ktlintRuntime("com.pinterest.ktlint:ktlint-cli:${libs.versions.ktlint.get()}:all")
}

val qualityDirectory = layout.buildDirectory.dir("reports/quality")

tasks.register<JavaExec>("detektCheck") {
    group = "verification"
    description = "Kotlin AST analysis in an isolated CLI, without Kotlin type resolution."
    classpath = detektRuntime
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--input", "app/src/main/java",
        "--config", "config/detekt.yml",
        "--build-upon-default-config",
        "--base-path", projectDir.absolutePath,
        "--report", "xml:${qualityDirectory.get().asFile}/detekt.xml",
        "--report", "html:${qualityDirectory.get().asFile}/detekt.html"
    )
}

fun registerKtlintTask(name: String, format: Boolean) = tasks.register<JavaExec>(name) {
    group = "verification"
    classpath = ktlintRuntime
    mainClass.set("com.pinterest.ktlint.Main")
    args(
        "app/src/**/*.kt",
        "*.gradle.kts",
        "app/*.gradle.kts",
        "--reporter=checkstyle,output=${qualityDirectory.get().asFile}/ktlint.xml"
    )
    if (format) args("--format")
}

registerKtlintTask("ktlintCheck", false)
registerKtlintTask("formatKotlin", true)

tasks.register<JavaExec>("detektRuleProof") {
    group = "verification"
    classpath = detektRuntime
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    val fixture = layout.buildDirectory.dir("quality-fixtures/detekt").get().asFile
    val report = qualityDirectory.get().file("self-tests/detekt.xml").asFile
    doFirst {
        fixture.mkdirs()
        fixture.resolve("Complex.kt").writeText(
            buildString {
                appendLine("fun overlyComplex(value: Int): Int {")
                appendLine("    var result = 0")
                repeat(16) { appendLine("    if (value > $it) { result += value }") }
                appendLine("    return result")
                appendLine("}")
            }
        )
    }
    args(
        "--input",
        fixture.absolutePath,
        "--config",
        "config/detekt.yml",
        "--build-upon-default-config",
        "--report",
        "xml:${report.absolutePath}"
    )
    isIgnoreExitValue = true
    doLast {
        check(executionResult.get().exitValue == 2) {
            "Complexity fixture was not rejected by detekt"
        }
        val evidence = report.readText()
        check(
            evidence.contains("detekt.CognitiveComplexMethod") &&
                evidence.contains("detekt.CyclomaticComplexMethod")
        ) {
            "Both configured complexity rules must reject the fixture"
        }
    }
}

tasks.register("qualityReports") {
    group = "verification"
    dependsOn(
        "ktlintCheck",
        "detektCheck",
        "detektRuleProof",
        ":app:testDebugUnitTest",
        ":app:jacocoDebugReport",
        ":app:lintDebug",
        ":app:assembleDebug",
        ":app:generateLicenseReport",
        ":app:runtimeInventory"
    )
}
