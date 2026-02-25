package extractor.ast.react

import core.model.ColorPoint
import core.model.CssFeatures
import core.model.UiFramework
import extractor.ComponentSource
import extractor.CssFeatureExtractor
import extractor.simple.SimpleCssFeatureExtractor
import java.util.concurrent.TimeUnit

class ReactAstCssFeatureExtractor(
    private val command: String = System.getenv("UI_SIMILARITY_REACT_CSS_AST_CMD")?.takeIf { it.isNotBlank() }
        ?: "node scripts/react-css-ast-extract.mjs",
    private val timeoutSeconds: Long = 10L,
    private val fallback: CssFeatureExtractor = SimpleCssFeatureExtractor(),
    private val onFailure: ((ReactCssAstFailureEvent) -> Unit)? = null
) : CssFeatureExtractor {

    override fun extractCssFeatures(source: ComponentSource): CssFeatures {
        if (source.framework != UiFramework.REACT) {
            return fallback.extractCssFeatures(source)
        }

        val payload = "{\"cssCode\":\"${escape(source.styleCode)}\"}"
        val (output, commandReason) = runCommand(payload)
        if (output == null) {
            onFailure?.invoke(ReactCssAstFailureEvent(source.id, commandReason ?: "command_failure", fallbackUsed = true))
            return fallback.extractCssFeatures(source)
        }
        val (parsed, parseReason) = parseResponse(output)
        if (parsed == null) {
            onFailure?.invoke(ReactCssAstFailureEvent(source.id, parseReason ?: "parse_failure", fallbackUsed = true))
            return fallback.extractCssFeatures(source)
        }

        return CssFeatures(
            styleTokens = parsed.styleTokens,
            palette = parsed.palette,
            spacingMean = parsed.spacingMean,
            spacingStd = parsed.spacingStd,
            fontFamilies = parsed.fontFamilies,
            fontSizeBuckets = parsed.fontSizeBuckets
        )
    }

    private fun runCommand(payload: String): Pair<String?, String?> {
        return try {
            val process = ProcessBuilder("/bin/sh", "-lc", command)
                .redirectErrorStream(true)
                .start()

            process.outputStream.bufferedWriter().use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null to "timeout"
            }
            if (process.exitValue() != 0) return null to "command_exit_nonzero"
            process.inputStream.bufferedReader().readText() to null
        } catch (e: Exception) {
            null to "exception_${e::class.simpleName ?: "unknown"}"
        }
    }

    private data class Parsed(
        val styleTokens: Map<String, Int>,
        val palette: List<ColorPoint>,
        val spacingMean: Double,
        val spacingStd: Double,
        val fontFamilies: Set<String>,
        val fontSizeBuckets: Map<String, Int>
    )

    private fun parseResponse(json: String): Pair<Parsed?, String?> {
        val status = extractString(json, "status") ?: return null to "missing_status"
        if (status != "ok") return null to "status_$status"

        val styleTokens = extractIntMap(json, "styleTokens")
        val fontSizeBuckets = extractIntMap(json, "fontSizeBuckets")
        val fontFamilies = extractStringArray(json, "fontFamilies").toSet()
        val spacingMean = extractNumber(json, "spacingMean") ?: 0.0
        val spacingStd = extractNumber(json, "spacingStd") ?: 0.0
        val palette = extractPalette(json)

        return Parsed(
            styleTokens = styleTokens,
            palette = palette,
            spacingMean = spacingMean,
            spacingStd = spacingStd,
            fontFamilies = fontFamilies,
            fontSizeBuckets = fontSizeBuckets
        ) to null
    }

    private fun extractPalette(json: String): List<ColorPoint> {
        val block = extractArrayBlock(json, "palette") ?: return emptyList()
        val objRegex = Regex("""\{[^{}]*\}""")
        return objRegex.findAll(block).mapNotNull { objMatch ->
            val obj = objMatch.value
            val l = extractNumber(obj, "l") ?: return@mapNotNull null
            val a = extractNumber(obj, "a") ?: return@mapNotNull null
            val b = extractNumber(obj, "b") ?: return@mapNotNull null
            ColorPoint(l, a, b)
        }.toList()
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        val raw = regex.find(json)?.groupValues?.get(1) ?: return null
        return unescape(raw)
    }

    private fun extractNumber(json: String, key: String): Double? {
        val regex = Regex("\"$key\"\\s*:\\s*([-+]?[0-9]+(?:\\.[0-9]+)?)")
        return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractObjectBlock(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return null
        val start = json.indexOf('{', keyIndex)
        if (start == -1) return null
        var depth = 0
        for (i in start until json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return json.substring(start + 1, i)
                }
            }
        }
        return null
    }

    private fun extractArrayBlock(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return null
        val start = json.indexOf('[', keyIndex)
        if (start == -1) return null
        var depth = 0
        for (i in start until json.length) {
            when (json[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return json.substring(start + 1, i)
                }
            }
        }
        return null
    }

    private fun extractIntMap(json: String, key: String): Map<String, Int> {
        val block = extractObjectBlock(json, key) ?: return emptyMap()
        val pairRegex = Regex("\"((?:\\\\.|[^\"])*)\"\\s*:\\s*([0-9]+)")
        return pairRegex.findAll(block).associate {
            unescape(it.groupValues[1]) to it.groupValues[2].toInt()
        }
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val block = extractArrayBlock(json, key) ?: return emptyList()
        val strRegex = Regex("\"((?:\\\\.|[^\"])*)\"")
        return strRegex.findAll(block).map { unescape(it.groupValues[1]) }.toList()
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}

data class ReactCssAstFailureEvent(
    val componentId: String,
    val reason: String,
    val fallbackUsed: Boolean
)
