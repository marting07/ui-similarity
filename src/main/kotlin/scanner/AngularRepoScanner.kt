package scanner

import corpus.ComponentKey
import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.io.File
import java.nio.file.Path

/**
 * Scans an Angular project for components.  Detects `.component.ts` files,
 * extracts the associated template and style declarations from the `@Component`
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
                    val repoRootAbs = repoRoot.toAbsolutePath().normalize()
                    val templateRel = if (templatePath.isAbsolute) {
                        repoRootAbs.relativize(templatePath.toAbsolutePath().normalize())
                    } else {
                        templatePath
                    }
                    val styleRelPaths = stylePaths.map { path ->
                        if (path.isAbsolute) {
                            repoRootAbs.relativize(path.toAbsolutePath().normalize())
                        } else {
                            path
                        }
                    }
                    result += ComponentSourceRef(
                        key = key,
                        framework = UiFramework.ANGULAR,
                        repoRoot = repoRoot,
                        templatePath = templateRel,
                        stylePaths = styleRelPaths,
                        logicPath = relTs,
                        inlineTemplateCode = config.inlineTemplate,
                        inlineStyleCodes = config.inlineStyles
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
        val styleUrls: List<String>,
        val inlineTemplate: String?,
        val inlineStyles: List<String>
    )

    /**
     * Naively parse a `.component.ts` file to extract the component class
     * names and the template/style declarations from the `@Component` decorator.
     */
    private fun parseComponentConfig(file: File): ComponentConfig {
        val text = file.readText()
        // Angular does not require class names to end with "Component".
        val classRegex = Regex("""(?:export\s+)?class\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val classNames = classRegex.findAll(text).map { it.groupValues[1] }.toList()

        // Template URL: templateUrl: './x.component.html'
        val templateRegex = Regex("templateUrl\\s*:\\s*['\"]([^'\"]+)['\"]")
        val templateUrl = templateRegex.find(text)?.groupValues?.get(1)
        val inlineTemplate = parseInlineTemplate(text)

        // Style URLs: styleUrls: ['x.component.scss','y.scss']
        val stylesRegex = Regex("""styleUrls\s*:\s*\[([^\]]*?)\]""")
        val styleUrls = mutableListOf<String>()
        stylesRegex.find(text)?.let { match ->
            match.groupValues[1]
                .split(',')
                .map { it.trim().trim('"', '\'', ' ', '\n', '\r') }
                .filter { it.isNotEmpty() }
                .forEach { styleUrls += it }
        }

        return ComponentConfig(
            classNames = classNames,
            templateUrl = templateUrl,
            styleUrls = styleUrls,
            inlineTemplate = inlineTemplate,
            inlineStyles = parseInlineStyles(text)
        )
    }

    private fun parseInlineTemplate(text: String): String? {
        val backtick = Regex("""template\s*:\s*`([\s\S]*?)`""")
        backtick.find(text)?.let { return it.groupValues[1] }
        val single = Regex("""template\s*:\s*'([^']*)'""")
        single.find(text)?.let { return it.groupValues[1] }
        val dbl = Regex("""template\s*:\s*\"([^\"]*)\"""")
        dbl.find(text)?.let { return it.groupValues[1] }
        return null
    }

    private fun parseInlineStyles(text: String): List<String> {
        val stylesRegex = Regex("""styles\s*:\s*\[([\s\S]*?)\]""")
        val stylesMatch = stylesRegex.find(text) ?: return emptyList()
        val arrayContent = stylesMatch.groupValues[1]
        val itemRegex = Regex("""`([\s\S]*?)`|'([^']*)'|\"([^\"]*)\"""")
        return itemRegex.findAll(arrayContent)
            .mapNotNull { match ->
                match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            }
            .toList()
    }
}
