package extractor

import corpus.ComponentSourceRef
import java.nio.file.Files
import java.nio.file.Path

/**
 * Utility for loading the raw source code strings (template, style and logic)
 * for a given [ComponentSourceRef].  This abstracts away file I/O and
 * concatenation of multiple style files.  The loaded code can then be
 * passed to feature extractors.
 */
object SourceLoader {
    /**
     * Load the template, style and logic code for the given component.
     * If a referenced file does not exist it contributes an empty string.
     */
    fun load(sourceRef: ComponentSourceRef): ComponentSource {
        val template = readFileSafe(sourceRef.absoluteTemplate())
        // Concatenate all style files into a single string
        val style = sourceRef.absoluteStyles().joinToString("\n") { readFileSafe(it) }
        val logic = readFileSafe(sourceRef.absoluteLogic())
        return ComponentSource(
            id = sourceRef.key.id,
            framework = sourceRef.framework,
            templateCode = template,
            styleCode = style,
            logicCode = logic
        )
    }
    private fun readFileSafe(path: Path): String {
        return try {
            Files.readString(path)
        } catch (e: Exception) {
            ""
        }
    }
}