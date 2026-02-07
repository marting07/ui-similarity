package corpus

import core.model.UiFramework
import java.nio.file.Path

/**
 * Describes where a UI component’s source files live on disk.  A single
 * component may comprise a template (HTML/JSX), one or more style files
 * (CSS/SCSS) and a logic file (TS/JS).  For frameworks like React the
 * template and logic are often the same file.
 */
data class ComponentSourceRef(
    val key: ComponentKey,
    val framework: UiFramework,
    val repoRoot: Path,
    val templatePath: Path,
    val stylePaths: List<Path>,
    val logicPath: Path
) {
    /**
     * Returns the absolute path to the template file by resolving it
     * against the repository root.
     */
    fun absoluteTemplate(): Path = repoRoot.resolve(templatePath)
    /**
     * Returns the absolute paths to all style files.
     */
    fun absoluteStyles(): List<Path> = stylePaths.map { repoRoot.resolve(it) }
    /**
     * Returns the absolute path to the logic file.
     */
    fun absoluteLogic(): Path = repoRoot.resolve(logicPath)
}