package scanner

import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.nio.file.Path

class AngularAstRepoScanner(
    private val astEngine: AngularAstEngine = createDefaultAngularAstEngine(),
    private val fallbackScanner: AngularRepoScanner = AngularRepoScanner(),
    private val allowFallback: Boolean = true,
    private val onAstFailure: ((AstScanFailureEvent) -> Unit)? = null
) : FrameworkRepoScanner {
    override val framework: UiFramework = UiFramework.ANGULAR

    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef> {
        return when (val result = astEngine.scanRepo(repoId, repoRoot)) {
            is AstScanOutcome.Success -> result.refs
            is AstScanOutcome.Failure -> {
                if (!allowFallback) {
                    onAstFailure?.invoke(
                        AstScanFailureEvent(framework, repoId.toString(), result.reason, fallbackUsed = false)
                    )
                    emptyList()
                } else {
                    onAstFailure?.invoke(
                        AstScanFailureEvent(framework, repoId.toString(), result.reason, fallbackUsed = true)
                    )
                    fallbackScanner.scanRepo(repoId, repoRoot)
                }
            }
        }
    }
}

fun interface AngularAstEngine {
    fun scanRepo(repoId: RepoId, repoRoot: Path): AstScanOutcome
}

object NoopAngularAstEngine : AngularAstEngine {
    override fun scanRepo(repoId: RepoId, repoRoot: Path): AstScanOutcome =
        AstScanOutcome.Failure("engine_unavailable")
}
