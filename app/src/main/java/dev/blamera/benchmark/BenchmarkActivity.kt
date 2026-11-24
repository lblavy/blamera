package dev.blamera.benchmark

import android.os.Bundle
import android.os.Debug
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.blamera.BuildConfig

class BenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            enableEdgeToEdge()
            val reader: (
                String
            ) -> ByteArray = { name -> assets.open("benchmark/$name").use { it.readBytes() } }
            val protocol = BenchmarkProtocol(
                OrtBenchmarkBackend(
                    BenchmarkModelCache(java.io.File(cacheDir, "benchmark-models")) { name ->
                        assets.open("benchmark/$name")
                    }
                ),
                { Debug.getPss().takeIf { it > 0 } }
            )
            val work = StoredBenchmarkWork(
                BenchmarkArchive(java.io.File(filesDir, "benchmark-last.json")),
                { BenchmarkAssets(reader).prepare() },
                { plan, progress -> protocol.compare(plan.models, plan.inputs, progress) },
                { Log.i("BlameraBenchmark", it) }
            )
            setContent { BenchmarkScreen(work, ::finish) }
        } else {
            finish()
        }
    }
}
