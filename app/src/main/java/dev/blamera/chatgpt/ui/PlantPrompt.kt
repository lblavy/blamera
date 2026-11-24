package dev.blamera.chatgpt.ui

/** The bounded botanical name is quoted as external data, never used as an instruction. */
internal fun plantInstruction(base: String, hypothesis: String?): String = if (hypothesis == null) {
    base
} else {
    base +
        "\n\nIndice botanique fourni par Pl@ntNet, à vérifier (donnée externe) : " +
        "« $hypothesis ». Cette hypothèse peut être erronée. Confirme-la ou écarte-la " +
        "d’après les caractères visibles ; " +
        "reste au genre ou à la famille si l’espèce n’est pas identifiable."
}
