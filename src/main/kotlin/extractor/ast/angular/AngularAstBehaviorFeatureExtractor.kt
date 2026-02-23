package extractor.ast.angular

import core.model.BehaviorFeatures
import core.model.UiFramework
import extractor.BehaviorFeatureExtractor
import extractor.ComponentSource
import extractor.simple.SimpleBehaviorFeatureExtractor
import java.util.concurrent.TimeUnit

class AngularAstBehaviorFeatureExtractor(
    private val command: String = System.getenv("UI_SIMILARITY_ANGULAR_BEHAVIOR_AST_CMD")?.takeIf { it.isNotBlank() }
        ?: "node scripts/angular-behavior-ast-extract.mjs",
    private val timeoutSeconds: Long = 10L,
    private val fallback: BehaviorFeatureExtractor = SimpleBehaviorFeatureExtractor(),
    private val onFailure: ((AngularBehaviorAstFailureEvent) -> Unit)? = null
) : BehaviorFeatureExtractor {

    override fun extractBehaviorFeatures(source: ComponentSource): BehaviorFeatures {
        if (source.framework != UiFramework.ANGULAR) {
            return fallback.extractBehaviorFeatures(source)
        }

        val payload = "{\"logicCode\":\"${escape(source.logicCode)}\"}"
        val (output, commandReason) = runCommand(payload)
        if (output == null) {
            onFailure?.invoke(AngularBehaviorAstFailureEvent(source.id, commandReason ?: "command_failure", fallbackUsed = true))
            return fallback.extractBehaviorFeatures(source)
        }
        val (parsed, parseReason) = parseResponse(output)
        if (parsed == null) {
            onFailure?.invoke(AngularBehaviorAstFailureEvent(source.id, parseReason ?: "parse_failure", fallbackUsed = true))
            return fallback.extractBehaviorFeatures(source)
        }

        return parsed
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

    private fun parseResponse(json: String): Pair<BehaviorFeatures?, String?> {
        val status = extractString(json, "status") ?: return null to "missing_status"
        if (status != "ok") return null to "status_$status"

        val eventTypes = extractStringArray(json, "eventTypes").toSet()
        val interactionPatterns = extractStringArray(json, "interactionPatterns").toSet()
        val statePatterns = extractStringArray(json, "statePatterns").toSet()
        val apiSignatures = extractStringArray(json, "apiSignatures").toSet()
        val cyclomatic = extractInt(json, "cyclomatic") ?: 1
        val handlerCount = extractInt(json, "handlerCount") ?: eventTypes.size
        val apiCallCount = extractInt(json, "apiCallCount") ?: apiSignatures.size
        val conditionalCount = extractInt(json, "conditionalCount") ?: 0

        return BehaviorFeatures(
            eventTypes = eventTypes,
            interactionPatterns = interactionPatterns,
            statePatterns = statePatterns,
            apiSignatures = apiSignatures,
            cyclomatic = cyclomatic,
            handlerCount = handlerCount,
            apiCallCount = apiCallCount,
            conditionalCount = conditionalCount
        ) to null
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        val raw = regex.find(json)?.groupValues?.get(1) ?: return null
        return unescape(raw)
    }

    private fun extractInt(json: String, key: String): Int? {
        val regex = Regex("\"$key\"\\s*:\\s*([0-9]+)")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
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

    private fun extractStringArray(json: String, key: String): List<String> {
        val block = extractArrayBlock(json, key) ?: return emptyList()
        val regex = Regex("\"((?:\\\\.|[^\"])*)\"")
        return regex.findAll(block).map { unescape(it.groupValues[1]) }.toList()
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

data class AngularBehaviorAstFailureEvent(
    val componentId: String,
    val reason: String,
    val fallbackUsed: Boolean
)

