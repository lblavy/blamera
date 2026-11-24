package dev.blamera.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BenchmarkAssetsTest {
    private fun asset(name: String) = RuntimeEnvironment.getApplication()
        .assets.open("benchmark/$name").use { it.readBytes() }

    @Test fun incorrectOrderCadenceVocabularyBooleanDuplicateFixturesAndDimensionsAreRejected() {
        val cases = listOf(
            "models" to "small,baseline,large,xlarge",
            "performance.inputs" to "nasa,synthetic",
            "baseline.classes" to "81",
            "small.agnostic" to "perhaps",
            "inputs" to "nasa,nasa",
            "nasa.width" to "1",
            "nasa.height" to "1"
        )
        for ((key, value) in cases) {
            val spec = java.util.Properties().apply {
                asset("catalog.properties").inputStream().use(::load)
                setProperty(key, value)
            }
            val text = java.io.StringWriter().also { spec.store(it, null) }.toString()
            val loader = BenchmarkAssets {
                if (it ==
                    "catalog.properties"
                ) {
                    text.toByteArray()
                } else {
                    asset(it)
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                loader.prepare().inputs.first().load()
            }
        }
    }

    @Test fun fixedFixturesArePreprocessedOnceAndSourcesAreFailClosed() {
        val plan = BenchmarkAssets(::asset).prepare()
        assertEquals(listOf(80, 4585, 4585, 4585), plan.models.map { it.classes })
        assertEquals(14, plan.inputs.size)
        assertEquals(listOf("nasa", "synthetic"), plan.inputs.take(2).map { it.id })
        assertTrue(
            plan.inputs.all { input ->
                val image = input.load()
                image.values.size == 3 * 640 * 640 &&
                    image.values.all { it.isFinite() && it in 0f..1f }
            }
        )
        assertFalse(plan.inputs[0].load().values.contentEquals(plan.inputs[1].load().values))
        assertThrows(IllegalArgumentException::class.java) {
            BenchmarkAssets {
                if (it ==
                    "nasa.png"
                ) {
                    byteArrayOf(1)
                } else {
                    asset(it)
                }
            }.prepare().inputs.first().load()
        }
        assertThrows(IllegalArgumentException::class.java) {
            BenchmarkAssets {
                if (it ==
                    "catalog.properties"
                ) {
                    asset(it).toString(Charsets.UTF_8)
                        .replace("baseline.classes=80", "baseline.classes=81").toByteArray()
                } else {
                    asset(it)
                }
            }.prepare()
        }
    }
}
