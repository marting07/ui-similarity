import corpus.ComponentCorpus
import corpus.ComponentRecord
import corpus.ComponentSourceRef
import corpus.CorpusSplit
import corpus.RepoId
import core.model.UiFramework
import core.similarity.ComponentDistance
import extractor.ComponentSignatureExtractor
import extractor.SourceLoader
import extractor.ast.angular.AngularAstBehaviorFeatureExtractor
import extractor.ast.angular.AngularBehaviorAstFailureEvent
import extractor.ast.angular.AngularAstCssFeatureExtractor
import extractor.ast.angular.AngularCssAstFailureEvent
import extractor.ast.angular.AngularAstDomFeatureExtractor
import extractor.ast.angular.AngularDomAstFailureEvent
import extractor.ast.react.ReactAstBehaviorFeatureExtractor
import extractor.ast.react.ReactBehaviorAstFailureEvent
import extractor.ast.react.ReactDomAstFailureEvent
import extractor.ast.react.ReactCssAstFailureEvent
import extractor.ast.react.ReactAstCssFeatureExtractor
import extractor.ast.vue.VueAstCssFeatureExtractor
import extractor.ast.vue.VueAstDomFeatureExtractor
import extractor.ast.vue.VueAstBehaviorFeatureExtractor
import extractor.ast.vue.VueBehaviorAstFailureEvent
import extractor.ast.vue.VueCssAstFailureEvent
import extractor.ast.vue.VueDomAstFailureEvent
import extractor.simple.SimpleBehaviorFeatureExtractor
import extractor.simple.SimpleCssFeatureExtractor
import extractor.simple.SimpleDomFeatureExtractor
import extractor.ast.react.ReactAstDomFeatureExtractor
import index.permutation.PermutationIndex
import index.permutation.PivotSelector
import scanner.CompositeRepoScanner
import scanner.ExtractionMode
import scanner.AstScanFailureEvent
import scanner.createFrameworkScanners
import java.io.File
import java.nio.file.Path
import kotlin.random.Random

/**
 * Demonstration entry point for the UI similarity pipeline.  It expects
 * a directory containing cloned repositories (each in its own subfolder)
 * and runs the full pipeline: scanning, feature extraction, corpus
 * construction, index building and similarity queries.  The results are
 * printed to standard output.
 *
 * Usage: `kt run MainKt --repos /data/repos [--mode simple|ast|hybrid] [--audit-out out/parity.csv]
 *   [--dom-ast-enabled true|false] [--css-ast-enabled true|false] [--behavior-ast-enabled true|false]`
 * (after compiling via Gradle)
 */
fun main(args: Array<String>) {
    val config = parseCliConfig(args)
    if (config == null) {
        println(
            "Usage: MainKt --repos <path-to-repos> [--mode simple|ast|hybrid] [--audit-out <csv-path>] " +
                "[--dom-ast-enabled true|false] [--css-ast-enabled true|false] [--behavior-ast-enabled true|false]"
        )
        return
    }
    val reposDir = config.reposDir
    if (!reposDir.exists() || !reposDir.isDirectory) {
        println("Repositories directory not found: ${reposDir.absolutePath}")
        return
    }

    val astFailures = mutableListOf<AstScanFailureEvent>()
    val angularBehaviorAstFailures = mutableListOf<AngularBehaviorAstFailureEvent>()
    val angularCssAstFailures = mutableListOf<AngularCssAstFailureEvent>()
    val angularDomAstFailures = mutableListOf<AngularDomAstFailureEvent>()
    val vueBehaviorAstFailures = mutableListOf<VueBehaviorAstFailureEvent>()
    val vueCssAstFailures = mutableListOf<VueCssAstFailureEvent>()
    val vueDomAstFailures = mutableListOf<VueDomAstFailureEvent>()
    val reactDomAstFailures = mutableListOf<ReactDomAstFailureEvent>()
    val reactCssAstFailures = mutableListOf<ReactCssAstFailureEvent>()
    val reactBehaviorAstFailures = mutableListOf<ReactBehaviorAstFailureEvent>()
    val scanners = createFrameworkScanners(config.mode) { failure ->
        astFailures += failure
    }
    val compositeScanner = CompositeRepoScanner(scanners)
    println("Extraction mode: ${config.mode.name.lowercase()}")
    println(
        "Extractor AST flags: dom=${config.domAstEnabled}, css=${config.cssAstEnabled}, behavior=${config.behaviorAstEnabled}"
    )

    val simpleScannerForAudit = if (config.auditOut != null) {
        CompositeRepoScanner(createFrameworkScanners(ExtractionMode.SIMPLE))
    } else null
    val astScannerForAudit = if (config.auditOut != null) {
        CompositeRepoScanner(createFrameworkScanners(ExtractionMode.AST))
    } else null

    val sourceRefs = mutableListOf<ComponentSourceRef>()
    val auditRows = mutableListOf<ScannerParityAuditRow>()

    val frameworkDirs = setOf("react", "angular", "vue")
    val visitedRoots = mutableSetOf<Path>()

    reposDir.walkTopDown()
        .filter { it.isDirectory && it.name == ".git" }
        .forEach { gitDir ->
            val repoRoot = gitDir.parentFile ?: return@forEach
            if (!visitedRoots.add(repoRoot.toPath())) return@forEach
            val parts = repoRoot.relativeTo(reposDir).path.split(File.separator).filter { it.isNotEmpty() }
            if (parts.isEmpty()) return@forEach

            val repoId = if (parts[0] in frameworkDirs && parts.size >= 3) {
                RepoId("github.com", parts[1], parts.drop(2).joinToString("/"))
            } else if (parts.size >= 3) {
                RepoId(parts[0], parts[1], parts.drop(2).joinToString("/"))
            } else {
                return@forEach
            }

            val refs = compositeScanner.scanRepo(repoId, repoRoot.toPath())
            println("Scanned ${refs.size} components from ${repoId}")
            sourceRefs += refs

            if (simpleScannerForAudit != null && astScannerForAudit != null) {
                val simpleRefs = simpleScannerForAudit.scanRepo(repoId, repoRoot.toPath())
                val astRefs = astScannerForAudit.scanRepo(repoId, repoRoot.toPath())
                val framework = inferFrameworkForAudit(simpleRefs, astRefs)
                if (framework == UiFramework.REACT || framework == UiFramework.ANGULAR || framework == UiFramework.VUE) {
                    auditRows += buildAuditRow(repoId, framework, simpleRefs, astRefs)
                }
            }
        }

    if (config.auditOut != null) {
        writeAuditReport(config.auditOut, auditRows)
        val mismatched = auditRows.count { it.onlySimple > 0 || it.onlyAst > 0 }
        println("Scanner parity audit written to ${config.auditOut.absolutePath}")
        println("Audit summary: ${auditRows.size} repos compared, $mismatched mismatches")
    }
    printAstFailureSummary(astFailures)

    val simpleDomExtractor = SimpleDomFeatureExtractor()
    val simpleCssExtractor = SimpleCssFeatureExtractor()
    val simpleBehaviorExtractor = SimpleBehaviorFeatureExtractor()
    val reactAstDomExtractor = ReactAstDomFeatureExtractor(
        fallback = simpleDomExtractor,
        onFailure = { reactDomAstFailures += it }
    )
    val angularAstDomExtractor = AngularAstDomFeatureExtractor(
        fallback = simpleDomExtractor,
        onFailure = { angularDomAstFailures += it }
    )
    val vueAstDomExtractor = VueAstDomFeatureExtractor(
        fallback = simpleDomExtractor,
        onFailure = { vueDomAstFailures += it }
    )
    val reactAstCssExtractor = ReactAstCssFeatureExtractor(
        fallback = simpleCssExtractor,
        onFailure = { reactCssAstFailures += it }
    )
    val angularAstCssExtractor = AngularAstCssFeatureExtractor(
        fallback = simpleCssExtractor,
        onFailure = { angularCssAstFailures += it }
    )
    val vueAstCssExtractor = VueAstCssFeatureExtractor(
        fallback = simpleCssExtractor,
        onFailure = { vueCssAstFailures += it }
    )
    val reactAstBehaviorExtractor = ReactAstBehaviorFeatureExtractor(
        fallback = simpleBehaviorExtractor,
        onFailure = { reactBehaviorAstFailures += it }
    )
    val angularAstBehaviorExtractor = AngularAstBehaviorFeatureExtractor(
        fallback = simpleBehaviorExtractor,
        onFailure = { angularBehaviorAstFailures += it }
    )
    val vueAstBehaviorExtractor = VueAstBehaviorFeatureExtractor(
        fallback = simpleBehaviorExtractor,
        onFailure = { vueBehaviorAstFailures += it }
    )
    val domExtractor = if (!config.domAstEnabled) {
        simpleDomExtractor
    } else {
        object : extractor.DomFeatureExtractor {
            override fun extractDomFeatures(source: extractor.ComponentSource): core.model.DomFeatures {
                return when (source.framework) {
                    UiFramework.REACT -> reactAstDomExtractor.extractDomFeatures(source)
                    UiFramework.ANGULAR -> angularAstDomExtractor.extractDomFeatures(source)
                    UiFramework.VUE -> vueAstDomExtractor.extractDomFeatures(source)
                    else -> simpleDomExtractor.extractDomFeatures(source)
                }
            }
        }
    }
    val cssExtractor = if (!config.cssAstEnabled) {
        simpleCssExtractor
    } else {
        object : extractor.CssFeatureExtractor {
            override fun extractCssFeatures(source: extractor.ComponentSource): core.model.CssFeatures {
                return when (source.framework) {
                    UiFramework.REACT -> reactAstCssExtractor.extractCssFeatures(source)
                    UiFramework.ANGULAR -> angularAstCssExtractor.extractCssFeatures(source)
                    UiFramework.VUE -> vueAstCssExtractor.extractCssFeatures(source)
                    else -> simpleCssExtractor.extractCssFeatures(source)
                }
            }
        }
    }
    val behaviorExtractor = if (!config.behaviorAstEnabled) {
        simpleBehaviorExtractor
    } else {
        object : extractor.BehaviorFeatureExtractor {
            override fun extractBehaviorFeatures(source: extractor.ComponentSource): core.model.BehaviorFeatures {
                return when (source.framework) {
                    UiFramework.REACT -> reactAstBehaviorExtractor.extractBehaviorFeatures(source)
                    UiFramework.ANGULAR -> angularAstBehaviorExtractor.extractBehaviorFeatures(source)
                    UiFramework.VUE -> vueAstBehaviorExtractor.extractBehaviorFeatures(source)
                    else -> simpleBehaviorExtractor.extractBehaviorFeatures(source)
                }
            }
        }
    }
    val extractor = ComponentSignatureExtractor(
        domExtractor = domExtractor,
        cssExtractor = cssExtractor,
        behaviorExtractor = behaviorExtractor
    )

    val records = mutableListOf<ComponentRecord>()
    val reactDomParity = mutableListOf<ReactDomParityEvent>()
    val reactCssParity = mutableListOf<ReactCssParityEvent>()
    val reactBehaviorParity = mutableListOf<ReactBehaviorParityEvent>()
    val extractorParityEvents = mutableListOf<ExtractorParityEvent>()
    val extractorParityCompared = mutableMapOf<ExtractorParityKey, Int>()
    val astFrameworks = setOf(UiFramework.REACT, UiFramework.ANGULAR, UiFramework.VUE)
    var totalReactComponents = 0
    for (ref in sourceRefs) {
        try {
            val source = SourceLoader.load(ref)
            val signature = extractor.extract(source)
            records += ComponentRecord(ref, signature)
            if (source.framework == UiFramework.REACT) totalReactComponents++
            if (config.mode != ExtractionMode.SIMPLE && source.framework in astFrameworks) {
                if (config.domAstEnabled) {
                    val simpleDom = simpleDomExtractor.extractDomFeatures(source)
                    val diffs = diffDomFeatures(signature.dom, simpleDom)
                    incrementCompared(extractorParityCompared, source.framework, "dom")
                    if (diffs.isNotEmpty()) {
                        extractorParityEvents += ExtractorParityEvent(source.framework, "dom", source.id, diffs)
                        if (source.framework == UiFramework.REACT) {
                            reactDomParity += ReactDomParityEvent(source.id, diffs)
                        }
                    }
                }
                if (config.cssAstEnabled) {
                    val simpleCss = simpleCssExtractor.extractCssFeatures(source)
                    val cssDiffs = diffCssFeatures(signature.css, simpleCss)
                    incrementCompared(extractorParityCompared, source.framework, "css")
                    if (cssDiffs.isNotEmpty()) {
                        extractorParityEvents += ExtractorParityEvent(source.framework, "css", source.id, cssDiffs)
                        if (source.framework == UiFramework.REACT) {
                            reactCssParity += ReactCssParityEvent(source.id, cssDiffs)
                        }
                    }
                }
                if (config.behaviorAstEnabled) {
                    val simpleBehavior = simpleBehaviorExtractor.extractBehaviorFeatures(source)
                    val behaviorDiffs = diffBehaviorFeatures(signature.behavior, simpleBehavior)
                    incrementCompared(extractorParityCompared, source.framework, "behavior")
                    if (behaviorDiffs.isNotEmpty()) {
                        extractorParityEvents += ExtractorParityEvent(source.framework, "behavior", source.id, behaviorDiffs)
                        if (source.framework == UiFramework.REACT) {
                            reactBehaviorParity += ReactBehaviorParityEvent(source.id, behaviorDiffs)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Failed to extract features for ${ref.key.id}: ${e.message}")
        }
    }
    if (config.mode != ExtractionMode.SIMPLE) {
        println(formatReactDomParitySummary(reactDomParity, totalReactComponents))
        println(formatReactCssParitySummary(reactCssParity, totalReactComponents))
        println(formatReactBehaviorParitySummary(reactBehaviorParity, totalReactComponents))
        val extractorParitySummary = formatExtractorParitySummary(extractorParityCompared, extractorParityEvents)
        println(extractorParitySummary)
        val extractorParityOut = File("out/extractor-parity-summary.txt")
        writeExtractorParityReport(extractorParityOut, extractorParitySummary)
        println("Extractor parity summary written to ${extractorParityOut.absolutePath}")
    }
    println(formatReactDomAstFailureSummary(reactDomAstFailures))
    println(formatAngularDomAstFailureSummary(angularDomAstFailures))
    println(formatVueDomAstFailureSummary(vueDomAstFailures))
    println(formatReactCssAstFailureSummary(reactCssAstFailures))
    println(formatAngularCssAstFailureSummary(angularCssAstFailures))
    println(formatVueCssAstFailureSummary(vueCssAstFailures))
    println(formatReactBehaviorAstFailureSummary(reactBehaviorAstFailures))
    println(formatAngularBehaviorAstFailureSummary(angularBehaviorAstFailures))
    println(formatVueBehaviorAstFailureSummary(vueBehaviorAstFailures))

    val corpus = ComponentCorpus(records)
    println("Total components processed: ${corpus.records.size}")
    if (corpus.records.isEmpty()) {
        println("No components found; check repo path and scanner expectations.")
        return
    }

    val split = createRandomSplit(corpus, 0.8, seed = 42)
    println("Train size: ${split.train.records.size}, Query size: ${split.query.records.size}")

    val distance = ComponentDistance()
    val pivotCount = minOf(16, split.train.records.size)
    val pivots = PivotSelector.randomPivots(split.train.signatures(), pivotCount, Random(42))
    val index = PermutationIndex(pivots, distance)
    index.build(split.train.signatures())

    for (record in split.query.records) {
        val neighbors = index.querySimilar(record.signature, k = 8, topN = 5)
        println("Query: ${record.id}")
        neighbors.forEach { (id, score) -> println("  $id: ${String.format("%.2f", score)}") }
    }
}

data class CliConfig(
    val reposDir: File,
    val mode: ExtractionMode,
    val auditOut: File?,
    val domAstEnabled: Boolean,
    val cssAstEnabled: Boolean,
    val behaviorAstEnabled: Boolean
)

data class ScannerParityAuditRow(
    val repoId: String,
    val framework: String,
    val simpleCount: Int,
    val astCount: Int,
    val onlySimple: Int,
    val onlyAst: Int,
    val onlySimpleSample: String,
    val onlyAstSample: String
)

fun parseCliConfig(args: Array<String>): CliConfig? {
    if (args.isEmpty()) return null
    var reposPath: String? = null
    var mode = ExtractionMode.HYBRID
    var auditOut: String? = null
    var domAstEnabledCli: Boolean? = null
    var cssAstEnabledCli: Boolean? = null
    var behaviorAstEnabledCli: Boolean? = null
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--repos" -> {
                if (i + 1 >= args.size) return null
                reposPath = args[i + 1]
                i += 2
            }
            "--mode" -> {
                if (i + 1 >= args.size) return null
                mode = try {
                    ExtractionMode.fromCli(args[i + 1])
                } catch (e: IllegalArgumentException) {
                    println(e.message)
                    return null
                }
                i += 2
            }
            "--audit-out" -> {
                if (i + 1 >= args.size) return null
                auditOut = args[i + 1]
                i += 2
            }
            "--dom-ast-enabled" -> {
                if (i + 1 >= args.size) return null
                domAstEnabledCli = parseBooleanArg(args[i + 1]) ?: return null
                i += 2
            }
            "--css-ast-enabled" -> {
                if (i + 1 >= args.size) return null
                cssAstEnabledCli = parseBooleanArg(args[i + 1]) ?: return null
                i += 2
            }
            "--behavior-ast-enabled" -> {
                if (i + 1 >= args.size) return null
                behaviorAstEnabledCli = parseBooleanArg(args[i + 1]) ?: return null
                i += 2
            }
            else -> return null
        }
    }
    val path = reposPath ?: return null
    val defaultAstEnabled = mode != ExtractionMode.SIMPLE
    return CliConfig(
        reposDir = File(path),
        mode = mode,
        auditOut = auditOut?.let { File(it) },
        domAstEnabled = domAstEnabledCli ?: defaultAstEnabled,
        cssAstEnabled = cssAstEnabledCli ?: defaultAstEnabled,
        behaviorAstEnabled = behaviorAstEnabledCli ?: defaultAstEnabled
    )
}

private fun parseBooleanArg(raw: String): Boolean? {
    return when (raw.lowercase()) {
        "true" -> true
        "false" -> false
        else -> {
            println("Invalid boolean value '$raw'. Expected true or false.")
            null
        }
    }
}

private fun inferFrameworkForAudit(simpleRefs: List<ComponentSourceRef>, astRefs: List<ComponentSourceRef>): UiFramework {
    return simpleRefs.firstOrNull()?.framework
        ?: astRefs.firstOrNull()?.framework
        ?: UiFramework.UNKNOWN
}

private fun buildAuditRow(
    repoId: RepoId,
    framework: UiFramework,
    simpleRefs: List<ComponentSourceRef>,
    astRefs: List<ComponentSourceRef>
): ScannerParityAuditRow {
    val simpleIds = simpleRefs.map { it.key.id }.toSet()
    val astIds = astRefs.map { it.key.id }.toSet()
    val onlySimple = (simpleIds - astIds)
    val onlyAst = (astIds - simpleIds)

    return ScannerParityAuditRow(
        repoId = repoId.toString(),
        framework = framework.name.lowercase(),
        simpleCount = simpleIds.size,
        astCount = astIds.size,
        onlySimple = onlySimple.size,
        onlyAst = onlyAst.size,
        onlySimpleSample = onlySimple.take(5).joinToString("|"),
        onlyAstSample = onlyAst.take(5).joinToString("|")
    )
}

private fun writeAuditReport(outFile: File, rows: List<ScannerParityAuditRow>) {
    outFile.parentFile?.mkdirs()
    outFile.bufferedWriter().use { writer ->
        writer.write("repo_id,framework,simple_count,ast_count,only_simple,only_ast,only_simple_sample,only_ast_sample\n")
        for (row in rows) {
            writer.write(
                listOf(
                    row.repoId,
                    row.framework,
                    row.simpleCount.toString(),
                    row.astCount.toString(),
                    row.onlySimple.toString(),
                    row.onlyAst.toString(),
                    csvEscape(row.onlySimpleSample),
                    csvEscape(row.onlyAstSample)
                ).joinToString(",") + "\n"
            )
        }
    }
}

private fun writeExtractorParityReport(outFile: File, summary: String) {
    outFile.parentFile?.mkdirs()
    outFile.writeText(summary + "\n")
}

private fun csvEscape(value: String): String {
    if (!value.contains(",") && !value.contains("\"") && !value.contains("\n")) return value
    return "\"" + value.replace("\"", "\"\"") + "\""
}

private fun printAstFailureSummary(events: List<AstScanFailureEvent>) {
    println(formatAstFailureSummary(events))
}

fun formatAstFailureSummary(events: List<AstScanFailureEvent>): String {
    if (events.isEmpty()) return "AST fallback summary: no AST scanner failures."
    val lines = mutableListOf<String>()
    lines += "AST fallback summary:"
    val byFramework = events.groupBy { it.framework.name.lowercase() }
    for (framework in byFramework.keys.sorted()) {
        val fwEvents = byFramework[framework].orEmpty()
        val fallbackCount = fwEvents.count { it.fallbackUsed }
        val strictCount = fwEvents.size - fallbackCount
        val repoCount = fwEvents.map { it.repoId }.toSet().size
        lines += "  $framework: failures=${fwEvents.size}, repos=$repoCount, fallback_used=$fallbackCount, strict_drop=$strictCount"
        val byReason = fwEvents.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
        for ((reason, reasonEvents) in byReason) {
            lines += "    - $reason: ${reasonEvents.size}"
        }
        val byRepo = fwEvents.groupBy { it.repoId }.entries.sortedByDescending { it.value.size }.take(5)
        for ((repoId, repoEvents) in byRepo) {
            val topReason = repoEvents.groupBy { it.reason }.maxByOrNull { it.value.size }?.key ?: "unknown"
            lines += "    * repo=$repoId failures=${repoEvents.size} top_reason=$topReason"
        }
    }
    return lines.joinToString("\n")
}

data class ReactDomParityEvent(
    val componentId: String,
    val differingFields: List<String>
)

data class ReactCssParityEvent(
    val componentId: String,
    val differingFields: List<String>
)

data class ReactBehaviorParityEvent(
    val componentId: String,
    val differingFields: List<String>
)

data class ExtractorParityKey(
    val framework: UiFramework,
    val layer: String
)

data class ExtractorParityEvent(
    val framework: UiFramework,
    val layer: String,
    val componentId: String,
    val differingFields: List<String>
)

private fun incrementCompared(
    compared: MutableMap<ExtractorParityKey, Int>,
    framework: UiFramework,
    layer: String
) {
    val key = ExtractorParityKey(framework, layer)
    compared[key] = (compared[key] ?: 0) + 1
}

private fun diffDomFeatures(ast: core.model.DomFeatures, simple: core.model.DomFeatures): List<String> {
    val diffs = mutableListOf<String>()
    if (ast.tagHistogram != simple.tagHistogram) diffs += "tagHistogram"
    if (ast.roleHistogram != simple.roleHistogram) diffs += "roleHistogram"
    if (ast.layoutPatterns != simple.layoutPatterns) diffs += "layoutPatterns"
    if (ast.depth != simple.depth) diffs += "depth"
    if (kotlin.math.abs(ast.avgBranching - simple.avgBranching) > 1e-9) diffs += "avgBranching"
    return diffs
}

private fun diffBehaviorFeatures(ast: core.model.BehaviorFeatures, simple: core.model.BehaviorFeatures): List<String> {
    val diffs = mutableListOf<String>()
    if (ast.eventTypes != simple.eventTypes) diffs += "eventTypes"
    if (ast.interactionPatterns != simple.interactionPatterns) diffs += "interactionPatterns"
    if (ast.statePatterns != simple.statePatterns) diffs += "statePatterns"
    if (ast.apiSignatures != simple.apiSignatures) diffs += "apiSignatures"
    if (ast.cyclomatic != simple.cyclomatic) diffs += "cyclomatic"
    if (ast.handlerCount != simple.handlerCount) diffs += "handlerCount"
    if (ast.apiCallCount != simple.apiCallCount) diffs += "apiCallCount"
    if (ast.conditionalCount != simple.conditionalCount) diffs += "conditionalCount"
    return diffs
}

private fun diffCssFeatures(ast: core.model.CssFeatures, simple: core.model.CssFeatures): List<String> {
    val diffs = mutableListOf<String>()
    if (ast.styleTokens != simple.styleTokens) diffs += "styleTokens"
    if (ast.palette != simple.palette) diffs += "palette"
    if (kotlin.math.abs(ast.spacingMean - simple.spacingMean) > 1e-9) diffs += "spacingMean"
    if (kotlin.math.abs(ast.spacingStd - simple.spacingStd) > 1e-9) diffs += "spacingStd"
    if (ast.fontFamilies != simple.fontFamilies) diffs += "fontFamilies"
    if (ast.fontSizeBuckets != simple.fontSizeBuckets) diffs += "fontSizeBuckets"
    return diffs
}

fun formatExtractorParitySummary(
    compared: Map<ExtractorParityKey, Int>,
    events: List<ExtractorParityEvent>
): String {
    if (compared.isEmpty()) return "Extractor parity summary: no AST extractor comparisons run."
    val lines = mutableListOf<String>()
    lines += "Extractor parity summary:"
    val layerOrder = mapOf("dom" to 0, "css" to 1, "behavior" to 2)
    val keys = compared.keys.sortedWith(
        compareBy<ExtractorParityKey> { it.framework.name.lowercase() }
            .thenBy { layerOrder[it.layer] ?: 99 }
            .thenBy { it.layer }
    )
    for (key in keys) {
        val comparedCount = compared[key] ?: 0
        val mismatchEvents = events.filter { it.framework == key.framework && it.layer == key.layer }
        lines += "  ${key.framework.name.lowercase()}.${key.layer}: compared=$comparedCount mismatches=${mismatchEvents.size}"
        val byField = mismatchEvents
            .flatMap { it.differingFields }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
        for ((field, count) in byField) {
            lines += "    - $field: $count"
        }
        for (event in mismatchEvents.take(3)) {
            lines += "    * component=${event.componentId} fields=${event.differingFields.joinToString("|")}"
        }
    }
    return lines.joinToString("\n")
}

fun formatReactDomParitySummary(events: List<ReactDomParityEvent>, totalReactComponents: Int): String {
    if (totalReactComponents == 0) return "React DOM AST parity summary: no React components processed."
    if (events.isEmpty()) return "React DOM AST parity summary: no mismatches across $totalReactComponents React components."
    val lines = mutableListOf<String>()
    lines += "React DOM AST parity summary:"
    lines += "  compared=$totalReactComponents mismatches=${events.size}"
    val byField = events.flatMap { it.differingFields }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    for ((field, count) in byField) {
        lines += "  - $field: $count"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} fields=${event.differingFields.joinToString("|")}"
    }
    return lines.joinToString("\n")
}

fun formatReactDomAstFailureSummary(events: List<ReactDomAstFailureEvent>): String {
    if (events.isEmpty()) return "React DOM AST extractor summary: no failures."
    val lines = mutableListOf<String>()
    lines += "React DOM AST extractor summary:"
    lines += "  failures=${events.size}, components=${events.map { it.componentId }.toSet().size}"
    val byReason = events.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
    for ((reason, reasonEvents) in byReason) {
        lines += "  - $reason: ${reasonEvents.size}"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} reason=${event.reason} fallback_used=${event.fallbackUsed}"
    }
    return lines.joinToString("\n")
}

fun formatAngularDomAstFailureSummary(events: List<AngularDomAstFailureEvent>): String {
    if (events.isEmpty()) return "Angular DOM AST extractor summary: no failures."
    val lines = mutableListOf<String>()
    lines += "Angular DOM AST extractor summary:"
    lines += "  failures=${events.size}, components=${events.map { it.componentId }.toSet().size}"
    val byReason = events.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
    for ((reason, reasonEvents) in byReason) {
        lines += "  - $reason: ${reasonEvents.size}"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} reason=${event.reason} fallback_used=${event.fallbackUsed}"
    }
    return lines.joinToString("\n")
}

fun formatVueDomAstFailureSummary(events: List<VueDomAstFailureEvent>): String {
    if (events.isEmpty()) return "Vue DOM AST extractor summary: no failures."
    val lines = mutableListOf<String>()
    lines += "Vue DOM AST extractor summary:"
    lines += "  failures=${events.size}, components=${events.map { it.componentId }.toSet().size}"
    val byReason = events.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
    for ((reason, reasonEvents) in byReason) {
        lines += "  - $reason: ${reasonEvents.size}"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} reason=${event.reason} fallback_used=${event.fallbackUsed}"
    }
    return lines.joinToString("\n")
}

fun formatReactCssParitySummary(events: List<ReactCssParityEvent>, totalReactComponents: Int): String {
    if (totalReactComponents == 0) return "React CSS AST parity summary: no React components processed."
    if (events.isEmpty()) return "React CSS AST parity summary: no mismatches across $totalReactComponents React components."
    val lines = mutableListOf<String>()
    lines += "React CSS AST parity summary:"
    lines += "  compared=$totalReactComponents mismatches=${events.size}"
    val byField = events.flatMap { it.differingFields }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    for ((field, count) in byField) {
        lines += "  - $field: $count"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} fields=${event.differingFields.joinToString("|")}"
    }
    return lines.joinToString("\n")
}

fun formatReactCssAstFailureSummary(events: List<ReactCssAstFailureEvent>): String {
    if (events.isEmpty()) return "React CSS AST extractor summary: no failures."
    val lines = mutableListOf<String>()
    lines += "React CSS AST extractor summary:"
    lines += "  failures=${events.size}, components=${events.map { it.componentId }.toSet().size}"
    val byReason = events.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
    for ((reason, reasonEvents) in byReason) {
        lines += "  - $reason: ${reasonEvents.size}"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} reason=${event.reason} fallback_used=${event.fallbackUsed}"
    }
    return lines.joinToString("\n")
}

fun formatAngularCssAstFailureSummary(events: List<AngularCssAstFailureEvent>): String {
    if (events.isEmpty()) return "Angular CSS AST extractor summary: no failures."
    val lines = mutableListOf<String>()
    lines += "Angular CSS AST extractor summary:"
    lines += "  failures=${events.size}, components=${events.map { it.componentId }.toSet().size}"
    val byReason = events.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
    for ((reason, reasonEvents) in byReason) {
        lines += "  - $reason: ${reasonEvents.size}"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} reason=${event.reason} fallback_used=${event.fallbackUsed}"
    }
    return lines.joinToString("\n")
}

fun formatVueCssAstFailureSummary(events: List<VueCssAstFailureEvent>): String {
    if (events.isEmpty()) return "Vue CSS AST extractor summary: no failures."
    val lines = mutableListOf<String>()
    lines += "Vue CSS AST extractor summary:"
    lines += "  failures=${events.size}, components=${events.map { it.componentId }.toSet().size}"
    val byReason = events.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
    for ((reason, reasonEvents) in byReason) {
        lines += "  - $reason: ${reasonEvents.size}"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} reason=${event.reason} fallback_used=${event.fallbackUsed}"
    }
    return lines.joinToString("\n")
}

fun formatReactBehaviorParitySummary(events: List<ReactBehaviorParityEvent>, totalReactComponents: Int): String {
    if (totalReactComponents == 0) return "React behavior AST parity summary: no React components processed."
    if (events.isEmpty()) return "React behavior AST parity summary: no mismatches across $totalReactComponents React components."
    val lines = mutableListOf<String>()
    lines += "React behavior AST parity summary:"
    lines += "  compared=$totalReactComponents mismatches=${events.size}"
    val byField = events.flatMap { it.differingFields }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    for ((field, count) in byField) {
        lines += "  - $field: $count"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} fields=${event.differingFields.joinToString("|")}"
    }
    return lines.joinToString("\n")
}

fun formatReactBehaviorAstFailureSummary(events: List<ReactBehaviorAstFailureEvent>): String {
    if (events.isEmpty()) return "React behavior AST extractor summary: no failures."
    val lines = mutableListOf<String>()
    lines += "React behavior AST extractor summary:"
    lines += "  failures=${events.size}, components=${events.map { it.componentId }.toSet().size}"
    val byReason = events.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
    for ((reason, reasonEvents) in byReason) {
        lines += "  - $reason: ${reasonEvents.size}"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} reason=${event.reason} fallback_used=${event.fallbackUsed}"
    }
    return lines.joinToString("\n")
}

fun formatAngularBehaviorAstFailureSummary(events: List<AngularBehaviorAstFailureEvent>): String {
    if (events.isEmpty()) return "Angular behavior AST extractor summary: no failures."
    val lines = mutableListOf<String>()
    lines += "Angular behavior AST extractor summary:"
    lines += "  failures=${events.size}, components=${events.map { it.componentId }.toSet().size}"
    val byReason = events.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
    for ((reason, reasonEvents) in byReason) {
        lines += "  - $reason: ${reasonEvents.size}"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} reason=${event.reason} fallback_used=${event.fallbackUsed}"
    }
    return lines.joinToString("\n")
}

fun formatVueBehaviorAstFailureSummary(events: List<VueBehaviorAstFailureEvent>): String {
    if (events.isEmpty()) return "Vue behavior AST extractor summary: no failures."
    val lines = mutableListOf<String>()
    lines += "Vue behavior AST extractor summary:"
    lines += "  failures=${events.size}, components=${events.map { it.componentId }.toSet().size}"
    val byReason = events.groupBy { it.reason }.entries.sortedByDescending { it.value.size }.take(5)
    for ((reason, reasonEvents) in byReason) {
        lines += "  - $reason: ${reasonEvents.size}"
    }
    for (event in events.take(5)) {
        lines += "  * component=${event.componentId} reason=${event.reason} fallback_used=${event.fallbackUsed}"
    }
    return lines.joinToString("\n")
}

/**
 * Create a random train/query split from a corpus.  The [trainRatio]
 * determines the fraction of records placed in the training set.  A
 * [seed] controls the random shuffle for reproducibility.
 */
fun createRandomSplit(corpus: ComponentCorpus, trainRatio: Double, seed: Int): CorpusSplit {
    val shuffled = corpus.records.shuffled(Random(seed))
    val trainSize = (shuffled.size * trainRatio).toInt()
    val train = ComponentCorpus(shuffled.take(trainSize))
    val query = ComponentCorpus(shuffled.drop(trainSize))
    return CorpusSplit(train, query)
}
