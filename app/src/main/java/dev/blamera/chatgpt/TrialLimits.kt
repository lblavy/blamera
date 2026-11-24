package dev.blamera.chatgpt

internal object TrialLimits {
    const val KIB = 1024
    const val JSON_BYTES = 64 * KIB
    const val JWKS_BYTES = 128 * KIB
    const val MODELS_BYTES = 8 * KIB * KIB
    const val RESPONSE_BYTES = 2 * KIB * KIB
    const val STREAM_EVENT_BYTES = 128 * KIB
    const val REQUEST_BYTES = 3 * KIB * KIB
    const val TOKEN_BYTES = 32 * KIB
    const val NAME_CHARS = 200
    const val ID_CHARS = 256
    const val INSTRUCTION_CHARS = 2000
    const val CATALOG_MODELS = 500
    const val JWT_PARTS = 3
    const val KEY_COUNT = 100
    const val RSA_MIN_BITS = 2048
    const val RSA_MAX_BITS = 8192
    const val EXPONENT_BITS = 64
    const val EXPONENT_MIN = 3L
    const val RANDOM_BYTES = 48
    const val CALLBACK_BYTES = 8192
    const val CODE_CHARS = 4096
    const val HTTP_HEADERS_BYTES = 16 * KIB
    const val HTTP_PARTS = 3
    const val READ_BUFFER_BYTES = 4096
    const val NANOS_PER_MILLISECOND = 1_000_000
    const val MILLIS_PER_SECOND = 1000
    const val AUTH_TIMEOUT_MILLIS = 300_000L
    const val ACCEPT_TIMEOUT_MILLIS = 1000
    const val CALLBACK_READ_MILLIS = 1500
    const val CONNECT_TIMEOUT_MILLIS = 15_000
    const val RESPONSE_READ_MILLIS = 45_000
    const val RESPONSE_TIMEOUT_MILLIS = 120_000L
    const val REFRESH_MARGIN_SECONDS = 30
    const val MAX_GRANT_SECONDS = 86400
    const val TOO_MANY_REQUESTS = 429
}
