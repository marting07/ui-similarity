package scanner

import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.nio.file.Path

class AngularAstRepoScanner(
    private val astEngine: AngularAstEngine = createDefaultAngularAstEngine(),
    private val fallbackScanner: AngularRepoScanner = AngularRepoScanner(),
    private val allowFallback: Boolean = true
) : FrameworkRepoScanner {
    override val framework: UiFramework = UiFramework.ANGULAR

    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef> {
        val astResult = astEngine.scanRepo(repoId, repoRoot)
        if (astResult != null) return astResult

        if (!allowFallback) return emptyList()
        return fallbackScanner.scanRepo(repoId, repoRoot)
    }
}

fun interface AngularAstEngine {
    fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef>?
}

object NoopAngularAstEngine : AngularAstEngine {
    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef>? = null
}
