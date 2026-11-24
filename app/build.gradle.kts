import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.filter.SpdxLicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.JsonReportRenderer
import java.security.MessageDigest
import java.util.Properties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.license.report)
    jacoco
}

val visionSpec = Properties().apply {
    rootProject.file("config/vision-model.properties").inputStream().use(::load)
}
val visionFile = providers.gradleProperty("visionModelPath").map(::file)
    .orElse(
        rootProject.layout.projectDirectory.file(
            "models/m1-01/yolo11n-seg/${visionSpec["filename"]}"
        ).asFile
    )
val visionAssets = layout.buildDirectory.dir("generated/visionAssets")
val prepareVisionModel = tasks.register("prepareVisionModel") {
    inputs.property("modelPath", visionFile.map { it.absolutePath })
    inputs.property("expectedHash", visionSpec.getProperty("sha256"))
    outputs.dir(visionAssets)
    outputs.upToDateWhen { false }
    doLast {
        val model = visionFile.get()
        val destination = visionAssets.get().dir(
            "vision"
        ).file(visionSpec.getProperty("filename")).asFile
        check(model.isFile) { "Vision model absent: $model. See experiments/m1-01/README.md" }
        check(model.length() == visionSpec.getProperty("size").toLong()) {
            "Vision model size mismatch"
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(model.readBytes()).joinToString("") {
            "%02x".format(it)
        }
        check(hash == visionSpec.getProperty("sha256")) { "Vision model SHA-256 mismatch" }
        destination.parentFile.mkdirs()
        model.copyTo(destination, overwrite = true)
    }
}

val benchmarkSpec = Properties().apply {
    rootProject.file("config/benchmark.properties").inputStream().use(::load)
}
val photoSpec = Properties().apply {
    rootProject.file("config/photo-model.properties").inputStream().use(::load)
}
val photoAssets = layout.buildDirectory.dir("generated/photoAssets")
val preparePhotoAssets = tasks.register("preparePhotoAssets") {
    inputs.file(rootProject.file("config/photo-model.properties"))
    outputs.dir(photoAssets)
    outputs.upToDateWhen { false }
    doLast {
        val generated = photoSpec.getProperty("files").split(",")
        val static = photoSpec.getProperty("static.files").split(",")
        for (id in generated + static) {
            val source = rootProject.file(photoSpec.getProperty("$id.file"))
            check(source.isFile && source.length() == photoSpec.getProperty("$id.size").toLong()) {
                "Photo asset missing or wrong length: $id"
            }
            val hash = MessageDigest.getInstance("SHA-256")
            source.inputStream().buffered().use { input ->
                val bytes = ByteArray(65536)
                var count = input.read(bytes)
                while (count != -1) {
                    hash.update(bytes, 0, count)
                    count = input.read(bytes)
                }
            }
            check(
                hash.digest().joinToString("") { "%02x".format(it) } ==
                    photoSpec.getProperty("$id.sha256")
            ) {
                "Photo asset SHA-256 mismatch: $id"
            }
            if (id in static) {
                check(
                    source.canonicalFile == rootProject.file(
                        "app/src/main/assets/${photoSpec.getProperty("$id.asset")}"
                    ).canonicalFile
                ) { "Static photo asset must already be packaged at its declared path: $id" }
            } else {
                val destination = photoAssets.get().file(photoSpec.getProperty("$id.asset")).asFile
                destination.parentFile.mkdirs()
                source.copyTo(destination, overwrite = true)
            }
        }
    }
}
val sharedBenchmarkIds = mapOf("large" to "model", "labels4585" to "labels", "license" to "license")
val faceRuntimeSpec = Properties().apply {
    rootProject.file("config/face-runtime.properties").inputStream().use(::load)
}
val faceAssets = layout.buildDirectory.dir("generated/faceAssets")
val faceGallery = providers.gradleProperty("faceGalleryPath")
val faceModels = providers.gradleProperty("faceModelDirectory").map(::file)
    .orElse(rootProject.layout.projectDirectory.dir("models/m3-02").asFile)
val faceParity = providers.gradleProperty("faceParityDirectory").map(::file)
    .orElse(rootProject.layout.projectDirectory.dir("build/fixtures/m3-05").asFile)
val prepareFaceAssets = tasks.register<Exec>("prepareFaceAssets") {
    inputs.file(rootProject.file("config/face-models.json"))
    inputs.file(rootProject.file("pc/src/blamera_pc/face_mobile_assets.py"))
    inputs.dir(faceModels)
    inputs.dir(faceParity)
    inputs.property("personalGallerySupplied", faceGallery.isPresent)
    if (faceGallery.isPresent) inputs.file(faceGallery.map(::file))
    outputs.dir(faceAssets)
    outputs.upToDateWhen { false }
    workingDir(rootProject.projectDir)
    environment("PYTHONPATH", rootProject.file("pc/src").absolutePath)
    doFirst {
        val pythonName = if (System.getProperty("os.name").startsWith("Windows")) {
            "pc/.venv/Scripts/python.exe"
        } else {
            "pc/.venv/bin/python"
        }
        val python = rootProject.file(pythonName)
        check(python.isFile) { "PC Python absent; follow docs/pc-development.md" }
        commandLine(
            buildList {
                addAll(
                    listOf(
                        python.absolutePath, "-m", "blamera_pc.face_mobile_assets",
                        "--repository", rootProject.projectDir.absolutePath,
                        "--output", faceAssets.get().asFile.absolutePath,
                        "--models", faceModels.get().absolutePath,
                        "--parity", faceParity.get().absolutePath
                    )
                )
                faceGallery.orNull?.let { addAll(listOf("--gallery", file(it).absolutePath)) }
            }
        )
    }
}
val verifyFaceRuntimeDependency = tasks.register("verifyFaceRuntimeDependency") {
    inputs.file(rootProject.file("config/face-runtime.properties"))
    doLast {
        val artifact = configurations.getByName("debugRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts.single {
                it.moduleVersion.id.group == "org.opencv" && it.name == "opencv"
            }
        check(artifact.moduleVersion.id.version == libs.versions.opencv.get())
        check(artifact.file.length() == faceRuntimeSpec.getProperty("opencv.aar.size").toLong())
        val digest = MessageDigest.getInstance("SHA-256")
        artifact.file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            var count = input.read(buffer)
            while (count != -1) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        check(
            digest.digest().joinToString("") { "%02x".format(it) } ==
                faceRuntimeSpec.getProperty("opencv.aar.sha256")
        ) { "OpenCV runtime artifact differs from the reviewed official package" }
    }
}
val benchmarkAssets = layout.buildDirectory.dir("generated/benchmarkAssets")
val prepareBenchmarkAssets = tasks.register("prepareBenchmarkAssets") {
    dependsOn(preparePhotoAssets)
    inputs.file(rootProject.file("config/benchmark.properties"))
    outputs.dir(benchmarkAssets)
    outputs.upToDateWhen { false }
    doLast {
        val destination = benchmarkAssets.get().dir("benchmark").asFile
        destination.mkdirs()
        val allowed = benchmarkSpec.getProperty("files").split(",")
        val leaves = allowed.filter { it !in sharedBenchmarkIds }
            .map { benchmarkSpec.getProperty("$it.asset") }.toSet() + "catalog.properties"
        check(leaves.all { File(it).name == it && it.isNotBlank() })
        destination.listFiles()?.filter { it.name !in leaves }?.forEach { stale ->
            check(stale.isFile && stale.delete()) { "Unable to remove stale benchmark leaf" }
        }
        for (id in allowed) {
            val shared = sharedBenchmarkIds[id]
            if (shared != null) {
                check(
                    photoSpec.getProperty("$shared.asset") ==
                        "benchmark/${benchmarkSpec.getProperty("$id.asset")}"
                )
                for (field in listOf("size", "sha256")) {
                    check(
                        photoSpec.getProperty("$shared.$field") ==
                            benchmarkSpec.getProperty("$id.$field")
                    ) {
                        "Shared photo/benchmark asset mismatch: $id/$field"
                    }
                }
                continue
            }
            val source = rootProject.file(benchmarkSpec.getProperty("$id.file"))
            check(source.isFile) {
                "Benchmark asset absent: $source. See experiments/m1-yoloe/README.md"
            }
            check(source.length() == benchmarkSpec.getProperty("$id.size").toLong()) {
                "Benchmark length mismatch: $id"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(hash == benchmarkSpec.getProperty("$id.sha256")) {
                "Benchmark SHA-256 mismatch: $id"
            }
            source.copyTo(
                destination.resolve(benchmarkSpec.getProperty("$id.asset")),
                overwrite = true
            )
        }
        rootProject.file(
            "config/benchmark.properties"
        ).copyTo(destination.resolve("catalog.properties"), overwrite = true)
    }
}

android {
    namespace = "dev.blamera"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.blamera"
        minSdk = 36
        targetSdk = 36
        versionCode = 32
        versionName = "0.18.0"
        ndk { abiFilters += providers.gradleProperty("faceTargetAbi").orElse("arm64-v8a").get() }
        buildConfigField("String", "VISION_MODEL_SHA256", "\"${visionSpec["sha256"]}\"")
        buildConfigField("String", "VISION_MODEL_ASSET", "\"vision/${visionSpec["filename"]}\"")
        buildConfigField("long", "VISION_MODEL_BYTES", "${visionSpec["size"]}L")
        for (kind in listOf("model", "labels", "french")) {
            val prefix = "PHOTO_${kind.uppercase()}"
            buildConfigField("String", "${prefix}_ASSET", "\"${photoSpec["$kind.asset"]}\"")
            buildConfigField("String", "${prefix}_SHA256", "\"${photoSpec["$kind.sha256"]}\"")
            buildConfigField("long", "${prefix}_BYTES", "${photoSpec["$kind.size"]}L")
        }
        buildConfigField("int", "PHOTO_CATEGORY_COUNT", photoSpec.getProperty("labels.count"))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").assets.srcDir(visionAssets)
    sourceSets.getByName("main").assets.srcDir(photoAssets)
    sourceSets.getByName("main").assets.srcDir(faceAssets)
    sourceSets.getByName("debug").assets.srcDir(benchmarkAssets)
    buildTypes.getByName("debug").isTestCoverageEnabled =
        providers.gradleProperty("faceInstrumentedCoverage").orElse("false").get().toBoolean()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty(
                "robolectric.dependency.repo.url",
                "https://repo.maven.apache.org/maven2"
            )
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(
        prepareVisionModel,
        preparePhotoAssets,
        prepareFaceAssets,
        verifyFaceRuntimeDependency
    )
}
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(prepareBenchmarkAssets) }
configurations.configureEach {
    if (name.contains("UnitTest")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.microsoft.onnxruntime:onnxruntime-android"))
                .using(
                    module(
                        "com.microsoft.onnxruntime:onnxruntime:${libs.versions.onnxruntime.get()}"
                    )
                )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.withType<Test>().configureEach {
    val testJavaHome = providers.gradleProperty(
        "testJavaHome"
    ).orElse(providers.environmentVariable("BLAMERA_TEST_JAVA_HOME"))
        .orElse(
            provider {
                if (System.getProperty("os.name").startsWith("Windows")) {
                    rootProject.file(
                        ".tools/m1-ort-preflight/temurin/jdk-21.0.12.1+1-jre"
                    ).absolutePath
                } else {
                    System.getProperty("java.home")
                }
            }
        )
    testJavaHome.orNull?.let { home ->
        executable =
            file(
                "$home/bin/" +
                    if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            ).absolutePath
    }
    systemProperty("blamera.model", visionFile.get().absolutePath)
    systemProperty(
        "blamera.fixture",
        rootProject.file("build/reports/validation/m1-01/astronaut.png").absolutePath
    )
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        includes = listOf("dev.blamera.*")
    }
}

tasks.register<JacocoReport>("jacocoDebugReport") {
    dependsOn("testDebugUnitTest")
    val unitCoverage = if (providers.gradleProperty("faceInstrumentedCoverage").orNull == "true") {
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
    } else {
        "jacoco/testDebugUnitTest.exec"
    }
    executionData(layout.buildDirectory.file(unitCoverage))
    providers.gradleProperty("faceNativeCoverage").orNull?.let { executionData(file(it)) }
    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            include("dev/blamera/**/*.class")
            exclude("**/R.class", "**/R$*.class", "**/BuildConfig.class")
        }
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

licenseReport {
    configurations = arrayOf("debugRuntimeClasspath")
    excludeOwnGroup = false
    renderers = arrayOf(JsonReportRenderer(), InventoryHtmlReportRenderer())
    filters = arrayOf(
        SpdxLicenseBundleNormalizer(),
        LicenseBundleNormalizer(
            rootProject.file("config/license-normalization.json").absolutePath,
            false
        )
    )
}

tasks.register("runtimeInventory") {
    val report = layout.buildDirectory.file("reports/dependency-license/runtime-inventory.json")
    outputs.file(report)
    outputs.upToDateWhen { false }
    doLast {
        val modules = configurations.getByName("debugRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts
            .map { it.moduleVersion.id }
            .distinctBy { "${it.group}:${it.name}:${it.version}" }
            .sortedBy { "${it.group}:${it.name}" }
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                modules.joinToString(prefix = "[\n", postfix = "\n]\n", separator = ",\n") {
                    "  {\"name\": \"${it.group}:${it.name}\", \"version\": \"${it.version}\", \"ecosystem\": \"Maven\"}"
                }
            )
        }
    }
}

dependencies {
    constraints {
        implementation(libs.guava.android) {
            because("CVE-2026-102554: bound deserialization allocation; retain Android variant")
        }
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.onnxruntime.android)
    implementation(libs.opencv)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.commonmark)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
