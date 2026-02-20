package scanner

import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.nio.file.Path

/**
 * Adapter scaffold for a future parser/AST-based React scanner.
 *
 * For now, this class attempts an AST engine call and falls back to the
 * existing regex scanner implementation so the pipeline remains operational.
 */
class ReactAstRepoScanner(
    private val astEngine: ReactAstEngine = createDefaultReactAstEngine(),
    private val fallbackScanner: ReactRepoScanner = ReactRepoScanner(),
    private val allowFallback: Boolean = true
) : FrameworkRepoScanner {
    override val framework: UiFramework = UiFramework.REACT

    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef> {
        val astResult = astEngine.scanRepo(repoId, repoRoot)
        if (astResult != null) return astResult

        if (!allowFallback) return emptyList()
        return fallbackScanner.scanRepo(repoId, repoRoot)
    }
}

/**
 * Returns AST scan results when available; returns null to indicate
 * that the adapter should fallback to the existing scanner path.
 */
fun interface ReactAstEngine {
    fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef>?
}

object NoopReactAstEngine : ReactAstEngine {
    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef>? = null
}
