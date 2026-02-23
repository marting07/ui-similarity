package scanner

import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.nio.file.Path

/**
 * Adapter scaffold for a future parser/AST-based React scanner.
 */
class ReactAstRepoScanner(
    private val astEngine: ReactAstEngine = createDefaultReactAstEngine(),
    private val fallbackScanner: ReactRepoScanner = ReactRepoScanner(),
    private val allowFallback: Boolean = true,
    private val onAstFailure: ((AstScanFailureEvent) -> Unit)? = null
) : FrameworkRepoScanner {
    override val framework: UiFramework = UiFramework.REACT

    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef> {
        return when (val result = astEngine.scanRepo(repoId, repoRoot)) {
            is AstScanOutcome.Success -> result.refs
            is AstScanOutcome.Failure -> {
                if (!allowFallback) {
                    onAstFailure?.invoke(
                        AstScanFailureEvent(
                            framework = framework,
                            repoId = repoId.toString(),
                            reason = result.reason,
                            fallbackUsed = false
                        )
                    )
                    emptyList()
                } else {
                    onAstFailure?.invoke(
                        AstScanFailureEvent(
                            framework = framework,
                            repoId = repoId.toString(),
                            reason = result.reason,
                            fallbackUsed = true
                        )
                    )
                    fallbackScanner.scanRepo(repoId, repoRoot)
                }
            }
        }
    }
}

fun interface ReactAstEngine {
    fun scanRepo(repoId: RepoId, repoRoot: Path): AstScanOutcome
}

object NoopReactAstEngine : ReactAstEngine {
    override fun scanRepo(repoId: RepoId, repoRoot: Path): AstScanOutcome =
        AstScanOutcome.Failure("engine_unavailable")
}
