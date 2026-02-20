package scanner

fun createFrameworkScanners(mode: ExtractionMode): List<FrameworkRepoScanner> {
    val reactScanner = when (mode) {
        ExtractionMode.SIMPLE -> ReactRepoScanner()
        ExtractionMode.AST -> ReactAstRepoScanner(allowFallback = false)
        ExtractionMode.HYBRID -> ReactAstRepoScanner(allowFallback = true)
    }
    val angularScanner = when (mode) {
        ExtractionMode.SIMPLE -> AngularRepoScanner()
        ExtractionMode.AST -> AngularAstRepoScanner(allowFallback = false)
        ExtractionMode.HYBRID -> AngularAstRepoScanner(allowFallback = true)
    }
    return listOf(
        reactScanner,
        angularScanner,
        VueRepoScanner()
    )
}
