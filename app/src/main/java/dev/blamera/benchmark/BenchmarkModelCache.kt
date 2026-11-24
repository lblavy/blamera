package dev.blamera.benchmark

import dev.blamera.vision.ModelAsset
import dev.blamera.vision.ModelFileCache
import java.io.File
import java.io.InputStream

internal data class CachedBenchmarkModel(val file: File, val hit: Boolean)
internal class BenchmarkModelCache(directory: File, source: (String) -> InputStream) {
    private val files = ModelFileCache(directory, source)
    fun prepare(model: BenchmarkModel): CachedBenchmarkModel {
        val cached = files.prepare(ModelAsset(model.asset, model.size, model.hash))
        return CachedBenchmarkModel(cached.file, cached.hit)
    }
}
