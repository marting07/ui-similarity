package scanner

import corpus.ComponentKey
import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.io.File
import java.nio.file.Path

/**
 * Scans a Vue project for Single File Components (SFCs) with a `.vue`
 * extension.  Each `.vue` file is treated as a complete component: the
 * template, styles and logic are contained in one file.
 */
class VueRepoScanner : FrameworkRepoScanner {
    override val framework: UiFramework = UiFramework.VUE
    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef> {
        val root = repoRoot.toFile()
        val result = mutableListOf<ComponentSourceRef>()
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".vue") }
            .filter { !it.path.contains("node_modules") && !it.path.contains("dist") && !it.path.contains("build") }
            .forEach { file ->
                val rel = repoRoot.relativize(file.toPath())
                // Use the filename without extension as the export/component name
                val name = file.nameWithoutExtension
                val key = ComponentKey(repoId, rel.toString().replace("\\", "/"), name)
                result += ComponentSourceRef(
                    key = key,
                    framework = UiFramework.VUE,
                    repoRoot = repoRoot,
                    templatePath = rel,
                    stylePaths = listOf(rel), // style is part of .vue file
                    logicPath = rel
                )
            }
        return result
    }
}