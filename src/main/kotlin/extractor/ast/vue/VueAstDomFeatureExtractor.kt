package extractor.ast.vue

import core.model.DomFeatures
import core.model.UiFramework
import extractor.ComponentSource
import extractor.DomFeatureExtractor
import extractor.simple.SimpleDomFeatureExtractor
import java.util.concurrent.TimeUnit

class VueAstDomFeatureExtractor(
    private val command: String = System.getenv("UI_SIMILARITY_VUE_DOM_AST_CMD")?.takeIf { it.isNotBlank() }
        ?: "node scripts/vue-dom-ast-extract.mjs",
    private val timeoutSeconds: Long = 10L,
    private val fallback: DomFeatureExtractor = SimpleDomFeatureExtractor(),
    private val onFailure: ((VueDomAstFailureEvent) -> Unit)? = null
) : DomFeatureExtractor {

    override fun extractDomFeatures(source: ComponentSource): DomFeatures {
        if (source.framework != UiFramework.VUE) {
            return fallback.extractDomFeatures(source)
        }

        val payload = "{\"templateCode\":\"${escape(source.templateCode)}\",\"styleCode\":\"${escape(source.styleCode)}\"}"
        val (output, commandReason) = runCommand(payload)
        if (output == null) {
            onFailure?.invoke(VueDomAstFailureEvent(source.id, commandReason ?: "command_failure", fallbackUsed = true))
            return fallback.extractDomFeatures(source)
        }

        val (parsed, parseReason) = parseResponse(output)
        if (parsed == null) {
            onFailure?.invoke(VueDomAstFailureEvent(source.id, parseReason ?: "parse_failure", fallbackUsed = true))
            return fallback.extractDomFeatures(source)
        }

        return DomFeatures(
            tagHistogram = parsed.tagHistogram,
            layoutPatterns = parsed.layoutPatterns,
            depth = parsed.depth,
            avgBranching = parsed.avgBranching,
            roleHistogram = parsed.roleHistogram
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
        val tagHistogram: Map<String, Int>,
        val roleHistogram: Map<String, Int>,
        val layoutPatterns: Set<String>,
        val depth: Int,
        val avgBranching: Double
    )

    private fun parseResponse(json: String): Pair<Parsed?, String?> {
        val status = extractString(json, "status") ?: return null to "missing_status"
        if (status != "ok") return null to "status_$status"

        val tags = extractIntMap(json, "tagHistogram")
        val roles = extractIntMap(json, "roleHistogram")
        val patterns = extractStringArray(json, "layoutPatterns").toSet()
        val depth = extractNumber(json, "depth")?.toInt() ?: 1
        val avgBranch = extractNumber(json, "avgBranching") ?: 0.0
        return Parsed(tags, roles, patterns, depth, avgBranch) to null
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        val raw = regex.find(json)?.groupValues?.get(1) ?: return null
        return unescape(raw)
    }

    private fun extractNumber(json: String, key: String): Double? {
        val regex = Regex("\"$key\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
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

data class VueDomAstFailureEvent(
    val componentId: String,
    val reason: String,
    val fallbackUsed: Boolean
)
