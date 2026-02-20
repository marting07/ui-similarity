package scanner

enum class ExtractionMode {
    SIMPLE,
    AST,
    HYBRID;

    companion object {
        fun fromCli(raw: String): ExtractionMode {
            return when (raw.lowercase()) {
                "simple" -> SIMPLE
                "ast" -> AST
                "hybrid" -> HYBRID
                else -> throw IllegalArgumentException("Unsupported mode '$raw'. Expected one of: simple, ast, hybrid")
            }
        }
    }
}
