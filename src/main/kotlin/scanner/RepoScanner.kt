package scanner

import corpus.RepoId
import corpus.ComponentSourceRef
import core.model.UiFramework
import java.nio.file.Path

/**
 * A scanner produces a list of [ComponentSourceRef] objects for a given
 * cloned repository.  The [scanRepo] method should traverse the file
 * structure under [repoRoot] and detect components based on the
 * conventions of the specific framework.
 */
interface RepoScanner {
    fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef>
}

/**
 * A scanner that operates on a specific UI framework.  It declares the
 * framework it handles so that a composite scanner can delegate to the
 * appropriate implementation.
 */
interface FrameworkRepoScanner : RepoScanner {
    val framework: UiFramework
}

/**
 * A scanner that delegates to a list of [FrameworkRepoScanner]s based on
 * heuristics for detecting the framework of a repository.  If no
 * framework can be detected, it returns an empty list.
 */
class CompositeRepoScanner(private val scanners: List<FrameworkRepoScanner>) : RepoScanner {
    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef> {
        val framework = detectFramework(repoRoot)
        val scanner = scanners.firstOrNull { it.framework == framework }
        return scanner?.scanRepo(repoId, repoRoot) ?: emptyList()
    }
    /**
     * Naively detect the framework by inspecting files in the repository root.
     * This can be overridden with more robust logic or configured manually.
     */
    private fun detectFramework(repoRoot: Path): UiFramework {
        // Angular has angular.json or *.module.ts
        val angularJson = repoRoot.resolve("angular.json").toFile()
        if (angularJson.exists()) return UiFramework.ANGULAR
        // Check package.json for dependencies
        val packageJson = repoRoot.resolve("package.json").toFile()
        if (packageJson.exists()) {
            val text = packageJson.readText()
            if (text.contains("\"@angular/core\"")) return UiFramework.ANGULAR
            if (text.contains("\"react\"")) return UiFramework.REACT
            if (text.contains("\"vue\"")) return UiFramework.VUE
        }
        // Fallback
        return UiFramework.UNKNOWN
    }
}