package scanner

fun createFrameworkScanners(
    mode: ExtractionMode,
    onAstFailure: ((AstScanFailureEvent) -> Unit)? = null
): List<FrameworkRepoScanner> {
    val reactScanner = when (mode) {
        ExtractionMode.SIMPLE -> ReactRepoScanner()
        ExtractionMode.AST -> ReactAstRepoScanner(allowFallback = false, onAstFailure = onAstFailure)
        ExtractionMode.HYBRID -> ReactAstRepoScanner(allowFallback = true, onAstFailure = onAstFailure)
    }
    val angularScanner = when (mode) {
        ExtractionMode.SIMPLE -> AngularRepoScanner()
        ExtractionMode.AST -> AngularAstRepoScanner(allowFallback = false, onAstFailure = onAstFailure)
        ExtractionMode.HYBRID -> AngularAstRepoScanner(allowFallback = true, onAstFailure = onAstFailure)
    }
    val vueScanner = when (mode) {
        ExtractionMode.SIMPLE -> VueRepoScanner()
        ExtractionMode.AST -> VueAstRepoScanner(allowFallback = false, onAstFailure = onAstFailure)
        ExtractionMode.HYBRID -> VueAstRepoScanner(allowFallback = true, onAstFailure = onAstFailure)
    }
    return listOf(
        reactScanner,
        angularScanner,
        vueScanner
    )
}
