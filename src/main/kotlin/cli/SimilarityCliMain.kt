package cli

import core.model.UiFramework
import extractor.ComponentSignatureExtractor
import extractor.ComponentSource
import extractor.simple.SimpleBehaviorFeatureExtractor
import extractor.simple.SimpleCssFeatureExtractor
import extractor.simple.SimpleDomFeatureExtractor
import persistence.IndexSnapshotCompatibility
import persistence.IndexSnapshotIO
import pipeline.BuildIndexRequest
import pipeline.DefaultSimilarityPipelineService
import pipeline.QuerySimilarityRequest
import pipeline.RepoSamplingMode
import pipeline.SamplingConfig
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val command = args.firstOrNull()
        if (command == null) {
            printUsage()
            return
        }
        val cli = SimilarityCli(DefaultSimilarityPipelineService())
        when (command) {
            "scan-index" -> cli.scanIndex(args.drop(1))
            "query" -> cli.query(args.drop(1))
            "inspect" -> cli.inspect(args.drop(1))
            "validate" -> cli.validate(args.drop(1))
            else -> {
                println("Unknown command: $command")
                printUsage()
                exitProcess(2)
            }
        }
    } catch (t: Throwable) {
        println("ERROR: ${t.message}")
        exitProcess(2)
    }
}

private fun printUsage() {
    println(
        """
        Usage:
          scan-index --repos <dir> --out <snapshot.json> [--mode simple|ast|hybrid]
                     [--dom-ast-enabled true|false] [--css-ast-enabled true|false] [--behavior-ast-enabled true|false]
                     [--pivot-count <n>] [--pivot-seed <n>]
                     [--sample-percent <1..100>] [--sample-seed <n>] [--sample-mode global|stratified-framework]
                     [--frameworks react,angular,vue] [--json-out <file>]
          query --index-file <snapshot.json> (--component-id <id> | --query-file <path>)
                [--query-framework react|angular|vue] [--top-k <n>] [--top-n <n>] [--json-out <file>]
          inspect --index-file <snapshot.json> [--json-out <file>]
          validate --index-file <snapshot.json> [--json-out <file>]
        """.trimIndent()
    )
}

private class SimilarityCli(
    private val service: DefaultSimilarityPipelineService
) {
    fun scanIndex(args: List<String>) {
        val kv = parseArgs(args)
        val repos = kv["repos"] ?: errorWithUsage("Missing --repos")
        val out = kv["out"] ?: errorWithUsage("Missing --out")
        val mode = kv["mode"] ?: "hybrid"
        val domAst = parseBoolOrDefault(kv["dom-ast-enabled"], mode != "simple")
        val cssAst = parseBoolOrDefault(kv["css-ast-enabled"], mode != "simple")
        val behaviorAst = parseBoolOrDefault(kv["behavior-ast-enabled"], mode != "simple")
        val pivotCount = kv["pivot-count"]?.toIntOrNull() ?: 16
        val pivotSeed = kv["pivot-seed"]?.toIntOrNull() ?: 42
        val frameworks = parseFrameworkSet(kv["frameworks"])
        val sampling = parseSampling(kv)
        val jsonOut = kv["json-out"]?.let { File(it) }

        val result = service.buildIndex(
            BuildIndexRequest(
                reposRoot = repos,
                extractionMode = mode,
                domAstEnabled = domAst,
                cssAstEnabled = cssAst,
                behaviorAstEnabled = behaviorAst,
                frameworks = frameworks,
                sampling = sampling,
                pivotCount = pivotCount,
                pivotSeed = pivotSeed
            )
        )
        val snapshot = result.snapshot ?: error("No components found to index.")
        val outFile = File(out)
        IndexSnapshotIO.save(outFile, snapshot)

        val summary = mapOf(
            "command" to "scan-index",
            "snapshot_file" to outFile.absolutePath,
            "scanned_components" to result.scannedComponents.toString(),
            "indexed_components" to snapshot.metadata.componentCount.toString(),
            "pivot_count" to snapshot.metadata.pivotCount.toString(),
            "mode" to snapshot.metadata.buildConfig.extractionMode
        )
        println("Index written: ${outFile.absolutePath}")
        println("Scanned=${result.scannedComponents} Indexed=${snapshot.metadata.componentCount} Pivots=${snapshot.metadata.pivotCount}")
        jsonOut?.let { writeJson(it, summary) }
    }

    fun query(args: List<String>) {
        val kv = parseArgs(args)
        val indexFile = kv["index-file"] ?: errorWithUsage("Missing --index-file")
        val snapshot = IndexSnapshotIO.load(File(indexFile))
        val topK = kv["top-k"]?.toIntOrNull() ?: 10
        val topN = kv["top-n"]?.toIntOrNull() ?: 20
        val jsonOut = kv["json-out"]?.let { File(it) }

        val componentId = kv["component-id"]
        val queryFile = kv["query-file"]
        require((componentId != null) xor (queryFile != null)) {
            "Provide exactly one of --component-id or --query-file"
        }

        val result = if (componentId != null) {
            service.query(
                QuerySimilarityRequest(
                    snapshot = snapshot,
                    componentId = componentId,
                    topK = topK,
                    topN = topN
                )
            )
        } else {
            val file = File(queryFile!!)
            require(file.exists() && file.isFile) { "Query file not found: ${file.absolutePath}" }
            val framework = parseQueryFramework(kv["query-framework"], file)
            val querySignature = extractQuerySignature(file, framework)
            service.queryBySignature(snapshot, querySignature, topK = topK, topN = topN)
        }

        println("Query component: ${result.queryComponentId}")
        for ((idx, match) in result.matches.withIndex()) {
            println("${idx + 1}. ${match.componentId}  score=${"%.4f".format(match.similarity)}")
        }
        jsonOut?.let {
            val body = buildString {
                append("{")
                append("\"query_component_id\":\"").append(escapeJson(result.queryComponentId)).append("\",")
                append("\"matches\":[")
                result.matches.forEachIndexed { idx, match ->
                    if (idx > 0) append(",")
                    append("{")
                    append("\"component_id\":\"").append(escapeJson(match.componentId)).append("\",")
                    append("\"similarity\":").append(match.similarity)
                    append("}")
                }
                append("]")
                append("}")
            }
            writeJsonRaw(it, body)
        }
    }

    fun inspect(args: List<String>) {
        val kv = parseArgs(args)
        val indexFile = kv["index-file"] ?: errorWithUsage("Missing --index-file")
        val snapshot = IndexSnapshotIO.load(File(indexFile))
        val metadata = snapshot.metadata
        val frameworks = metadata.frameworkCounts.entries.joinToString(", ") { "${it.key.name.lowercase()}=${it.value}" }
        val jsonOut = kv["json-out"]?.let { File(it) }

        println("Snapshot: $indexFile")
        println("Version: ${metadata.version}")
        println("CreatedAtEpochMs: ${metadata.createdAtEpochMs}")
        println("Components: ${metadata.componentCount}")
        println("Pivots: ${metadata.pivotCount}")
        println("Frameworks: $frameworks")
        println("Build mode: ${metadata.buildConfig.extractionMode}")

        val body = mapOf(
            "version" to metadata.version.toString(),
            "created_at_epoch_ms" to metadata.createdAtEpochMs.toString(),
            "component_count" to metadata.componentCount.toString(),
            "pivot_count" to metadata.pivotCount.toString(),
            "framework_counts" to frameworks,
            "extraction_mode" to metadata.buildConfig.extractionMode
        )
        jsonOut?.let { writeJson(it, body) }
    }

    fun validate(args: List<String>) {
        val kv = parseArgs(args)
        val indexFile = kv["index-file"] ?: errorWithUsage("Missing --index-file")
        val file = File(indexFile)
        val jsonOut = kv["json-out"]?.let { File(it) }
        require(file.exists() && file.isFile) { "Index file not found: ${file.absolutePath}" }

        val errors = IndexSnapshotIO.validate(file.readText())
        val compatibility = IndexSnapshotCompatibility.assess(file.readText())
        if (errors.isEmpty()) {
            println("VALID: ${file.absolutePath}")
        } else {
            println("INVALID: ${file.absolutePath}")
            errors.forEach { println(" - $it") }
        }
        println("Compatibility: ${compatibility.message}")
        jsonOut?.let { out ->
            val body = buildString {
                append("{")
                append("\"valid\":").append(errors.isEmpty()).append(",")
                append("\"detected_version\":").append(compatibility.detectedVersion ?: "null").append(",")
                append("\"load_supported\":").append(compatibility.loadSupported).append(",")
                append("\"migration_required\":").append(compatibility.migrationRequired).append(",")
                append("\"compatibility_message\":\"").append(escapeJson(compatibility.message)).append("\",")
                append("\"errors\":[")
                errors.forEachIndexed { idx, err ->
                    if (idx > 0) append(",")
                    append("\"").append(escapeJson(err)).append("\"")
                }
                append("]")
                append("}")
            }
            writeJsonRaw(out, body)
        }
    }

    private fun parseArgs(args: List<String>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        var i = 0
        while (i < args.size) {
            val token = args[i]
            require(token.startsWith("--")) { "Invalid argument: $token" }
            val key = token.removePrefix("--")
            require(i + 1 < args.size) { "Missing value for $token" }
            out[key] = args[i + 1]
            i += 2
        }
        return out
    }

    private fun parseFrameworkSet(raw: String?): Set<UiFramework> {
        if (raw.isNullOrBlank()) return setOf(UiFramework.REACT, UiFramework.ANGULAR, UiFramework.VUE)
        return raw.split(",")
            .map { parseFramework(it.trim()) }
            .toSet()
    }

    private fun parseSampling(kv: Map<String, String>): SamplingConfig? {
        val percent = kv["sample-percent"]?.toIntOrNull() ?: return null
        val seed = kv["sample-seed"]?.toIntOrNull() ?: 42
        val mode = when (kv["sample-mode"]?.lowercase() ?: "global") {
            "global" -> RepoSamplingMode.GLOBAL
            "stratified-framework" -> RepoSamplingMode.STRATIFIED_FRAMEWORK
            else -> error("Unsupported sample mode: ${kv["sample-mode"]}")
        }
        return SamplingConfig(percent = percent, seed = seed, mode = mode)
    }

    private fun parseBoolOrDefault(raw: String?, fallback: Boolean): Boolean {
        if (raw == null) return fallback
        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> error("Invalid boolean value: $raw")
        }
    }

    private fun parseQueryFramework(raw: String?, queryFile: File): UiFramework {
        if (!raw.isNullOrBlank()) return parseFramework(raw)
        val name = queryFile.name.lowercase()
        return when {
            name.endsWith(".vue") -> UiFramework.VUE
            name.endsWith(".tsx") || name.endsWith(".jsx") -> UiFramework.REACT
            name.endsWith(".html") -> UiFramework.ANGULAR
            else -> UiFramework.REACT
        }
    }

    private fun parseFramework(raw: String): UiFramework {
        return when (raw.lowercase()) {
            "react" -> UiFramework.REACT
            "angular" -> UiFramework.ANGULAR
            "vue" -> UiFramework.VUE
            else -> error("Unsupported framework: $raw")
        }
    }

    private fun extractQuerySignature(file: File, framework: UiFramework): core.model.ComponentSignature {
        val source = ComponentSource(
            id = "query::${file.absolutePath}",
            framework = framework,
            templateCode = file.readText(),
            styleCode = "",
            logicCode = file.readText()
        )
        val extractor = ComponentSignatureExtractor(
            domExtractor = SimpleDomFeatureExtractor(),
            cssExtractor = SimpleCssFeatureExtractor(),
            behaviorExtractor = SimpleBehaviorFeatureExtractor()
        )
        return extractor.extract(source)
    }
}

private fun writeJson(file: File, values: Map<String, String>) {
    val body = buildString {
        append("{")
        values.entries.forEachIndexed { idx, entry ->
            if (idx > 0) append(",")
            append("\"").append(escapeJson(entry.key)).append("\":")
            append("\"").append(escapeJson(entry.value)).append("\"")
        }
        append("}")
    }
    writeJsonRaw(file, body)
}

private fun writeJsonRaw(file: File, body: String) {
    file.parentFile?.mkdirs()
    file.writeText(body + "\n")
}

private fun escapeJson(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}

private fun errorWithUsage(msg: String): Nothing {
    throw IllegalArgumentException("$msg. See usage by running without arguments.")
}
