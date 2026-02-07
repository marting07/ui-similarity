package scanner

import corpus.ComponentKey
import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.io.File
import java.nio.file.Path

/**
 * Scans an Angular project for components.  Detects `.component.ts` files,
 * extracts the associated template and style URLs from the `@Component`
 * decorator and returns a [ComponentSourceRef] for each component class.
 */
class AngularRepoScanner : FrameworkRepoScanner {
    override val framework: UiFramework = UiFramework.ANGULAR
    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef> {
        val root = repoRoot.toFile()
        val result = mutableListOf<ComponentSourceRef>()
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".component.ts") }
            .filter { !it.path.contains("node_modules") && !it.path.contains("dist") && !it.path.contains("build") }
            .forEach { file ->
                val relTs = repoRoot.relativize(file.toPath())
                val config = parseComponentConfig(file)
                for (className in config.classNames) {
                    val key = ComponentKey(repoId, relTs.toString().replace("\\", "/"), className)
                    val templatePath = config.templateUrl?.let { file.parentFile.toPath().resolve(it).normalize() } ?: relTs
                    val stylePaths = if (config.styleUrls.isNotEmpty()) {
                        config.styleUrls.map { file.parentFile.toPath().resolve(it).normalize() }
                    } else {
                        // Default convention: .component.css or .component.scss next to .component.ts
                        val stem = file.nameWithoutExtension
                        val base = File(file.parentFile, "$stem.css")
                        val scss = File(file.parentFile, "$stem.scss")
                        listOf(base, scss).filter { it.exists() }.map { it.toPath() }
                    }
                    result += ComponentSourceRef(
                        key = key,
                        framework = UiFramework.ANGULAR,
                        repoRoot = repoRoot,
                        templatePath = repoRoot.relativize(templatePath),
                        stylePaths = stylePaths.map { repoRoot.relativize(it) },
                        logicPath = relTs
                    )
                }
            }
        return result
    }
    /**
     * Representation of relevant values extracted from a component decorator.
     */
    private data class ComponentConfig(
        val classNames: List<String>,
        val templateUrl: String?,
        val styleUrls: List<String>
    )
    /**
     * Naively parse a `.component.ts` file to extract the component class
     * names and the template/style URLs from the `@Component` decorator.
     */
    private fun parseComponentConfig(file: File): ComponentConfig {
        val text = file.readText()
        // Class names: export class XComponent
        val classRegex = Regex("class\\s+([A-Za-z0-9_]+Component)")
        val classNames = classRegex.findAll(text).map { it.groupValues[1] }.toList()
        // Template URL: templateUrl: './x.component.html'
        val templateRegex = Regex("templateUrl\\s*:\\s*['\"]([^'\"]+)['\"]")
        val templateUrl = templateRegex.find(text)?.groupValues?.get(1)
        // Style URLs: styleUrls: ['x.component.scss','y.scss']
        val stylesRegex = Regex("""styleUrls\s*:\s*\[([^\]]*?)\]""")
        val styleUrls = mutableListOf<String>()
        val styleMatch = stylesRegex.find(text)
        if (styleMatch != null) {
            val arrayContent = styleMatch.groupValues[1]
            val parts = arrayContent.split(',')
            for (p in parts) {
                val cleaned = p.trim().trim('"', '\'', ' ', '\n', '\r')
                if (cleaned.isNotEmpty()) styleUrls += cleaned
            }
        }
        return ComponentConfig(classNames, templateUrl, styleUrls)
    }
}