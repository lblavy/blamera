package dev.blamera.information

internal enum class ImageShareTarget(val appName: String, val packages: List<String>) {
    LENS(
        "Google Lens",
        listOf("com.google.ar.lens", "com.google.android.googlequicksearchbox")
    )
}
