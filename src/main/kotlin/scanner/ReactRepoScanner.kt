package scanner

import corpus.ComponentKey
import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.io.File
import java.nio.file.Path

/**
 * Scans a React project for components by searching for TSX/JSX files in
 * conventional component directories and extracting exported names.
 */
class ReactRepoScanner : FrameworkRepoScanner {
    override val framework: UiFramework = UiFramework.REACT
    private val componentExtensions = setOf("tsx", "jsx")

    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef> {
        val rootFile = repoRoot.toFile()
        val result = mutableListOf<ComponentSourceRef>()
        rootFile.walkTopDown()
            .filter { it.isFile && it.extension in componentExtensions }
            .filter { !it.path.contains("node_modules") && !it.path.contains("dist") && !it.path.contains("build") }
            .forEach { file ->
                val rel = repoRoot.relativize(file.toPath())
                val exports = detectExports(file)
                val stylePaths = detectStyleFiles(file)
                for (exportName in exports) {
                    val key = ComponentKey(repoId, rel.toString().replace("\\", "/"), exportName)
                    val sourceRef = ComponentSourceRef(
                        key = key,
                        framework = UiFramework.REACT,
                        repoRoot = repoRoot,
                        templatePath = rel,
                        stylePaths = stylePaths.map { repoRoot.relativize(it.toPath()) },
                        logicPath = rel
                    )
                    result += sourceRef
                }
            }
        return result
    }

    /**
     * Detect exported component names in a TSX/JSX file.  Supports patterns
     * such as `export const Name`, `export function Name` and
     * `export default function Name`.  Anonymous default exports are ignored.
     */
    private fun detectExports(file: File): List<String> {
        val code = file.readText()
        val names = mutableSetOf<String>()
        // export const Name = (...)
        val constRegex = Regex("export\\s+const\\s+([A-Za-z0-9_]+)")
        constRegex.findAll(code).forEach { names += it.groupValues[1] }
        // export function Name(...)
        val funcRegex = Regex("export\\s+function\\s+([A-Za-z0-9_]+)")
        funcRegex.findAll(code).forEach { names += it.groupValues[1] }
        // export default function Name(...)
        val defaultFuncRegex = Regex("export\\s+default\\s+function\\s+([A-Za-z0-9_]+)")
        defaultFuncRegex.findAll(code).forEach { names += it.groupValues[1] }
        // export default Name;
        val defaultAssignRegex = Regex("export\\s+default\\s+(?!function\\b|class\\b)([A-Za-z0-9_]+)\\b")
        defaultAssignRegex.findAll(code).forEach { names += it.groupValues[1] }
        return names.toList()
    }

    /**
     * Detect style files associated with a component by looking for files
     * with the same basename and common CSS/SCSS extensions.  For
     * example, `Card.tsx` → `Card.css`, `Card.module.scss` in the same
     * directory.
     */
    private fun detectStyleFiles(componentFile: File): List<File> {
        val dir = componentFile.parentFile ?: return emptyList()
        val stem = componentFile.nameWithoutExtension
        val candidates = listOf(
            File(dir, "$stem.css"),
            File(dir, "$stem.module.css"),
            File(dir, "$stem.scss"),
            File(dir, "$stem.module.scss")
        )
        return candidates.filter { it.exists() }
    }
}
