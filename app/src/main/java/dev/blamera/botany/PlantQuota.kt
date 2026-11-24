package dev.blamera.botany

internal class PlantQuota(
    private val read: () -> String? = { null },
    private val write: (String) -> Unit = {},
    private val utcDay: () -> Long = { System.currentTimeMillis() / UTC_DAY_MILLIS }
) {
    private var memory: String? = null

    fun claim() = synchronized(plantQuotaLock) {
        val current = record()
        if (current.second >= FREE_DAILY_IDENTIFICATIONS) throw PlantException(PlantFailure.QUOTA)
        save("${current.first}:${current.second + 1}")
    }

    fun exhausted() = synchronized(plantQuotaLock) {
        save("${utcDay()}:$FREE_DAILY_IDENTIFICATIONS")
    }

    private fun save(value: String) {
        write(value)
        memory = value
    }

    private fun record(): Pair<Long, Int> {
        val day = utcDay()
        val value = read() ?: memory ?: return day to 0
        val (savedDay, count) = decodePlantQuota(value)
        // A clock rollback cannot grant another daily allowance.
        if (savedDay > day) throw PlantException(PlantFailure.QUOTA)
        return day to if (savedDay == day) count else 0
    }
}

private fun decodePlantQuota(value: String): Pair<Long, Int> {
    val saved = value.split(':')
    val savedDay = saved.getOrNull(0)?.toLongOrNull() ?: -1
    val count = saved.getOrNull(1)?.toIntOrNull() ?: -1
    val validShape = saved.size == 2 && savedDay >= 0
    if (!validShape || count < 0) throw PlantException(PlantFailure.STORAGE)
    return savedDay to count
}

private val plantQuotaLock = Any()
private const val FREE_DAILY_IDENTIFICATIONS = 500
private const val UTC_DAY_MILLIS = 86_400_000L
