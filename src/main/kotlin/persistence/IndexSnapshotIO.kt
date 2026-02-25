package persistence

import core.model.UiFramework
import java.io.File

/**
 * File IO for permutation index snapshot v1.
 */
object IndexSnapshotIO {
    fun save(file: File, snapshot: PermutationIndexSnapshotV1) {
        file.parentFile?.mkdirs()
        file.writeText(toJson(snapshot) + "\n")
    }

    fun load(file: File): PermutationIndexSnapshotV1 {
        val json = file.readText()
        return loadFromString(json)
    }

    fun toJson(snapshot: PermutationIndexSnapshotV1): String = encode(snapshot)

    fun loadFromString(json: String): PermutationIndexSnapshotV1 = decode(json)

    fun validate(json: String): List<String> {
        val errors = mutableListOf<String>()
        val version = extractInt(json, "version")
        if (version == null) {
            errors += "Missing required field: version"
            return errors
        }
        if (version != INDEX_SNAPSHOT_VERSION_V1) {
            errors += "Unsupported snapshot version: $version"
        }
        if (extractLong(json, "createdAtEpochMs") == null) errors += "Missing required field: createdAtEpochMs"
        if (extractInt(json, "componentCount") == null) errors += "Missing required field: componentCount"
        if (extractInt(json, "pivotCount") == null) errors += "Missing required field: pivotCount"
        if (extractArrayBlock(json, "pivotIds") == null) errors += "Missing required field: pivotIds"
        if (extractArrayBlock(json, "records") == null) errors += "Missing required field: records"
        return errors
    }

    private fun encode(snapshot: PermutationIndexSnapshotV1): String {
        val metadata = snapshot.metadata
        return buildString {
            append("{")
            append("\"version\":").append(metadata.version).append(",")
            append("\"createdAtEpochMs\":").append(metadata.createdAtEpochMs).append(",")
            append("\"componentCount\":").append(metadata.componentCount).append(",")
            append("\"pivotCount\":").append(metadata.pivotCount).append(",")
            append("\"frameworkCounts\":").append(uiFrameworkIntMap(metadata.frameworkCounts)).append(",")
            append("\"buildConfig\":{")
            append("\"extractionMode\":\"").append(escape(metadata.buildConfig.extractionMode)).append("\",")
            append("\"domAstEnabled\":").append(metadata.buildConfig.domAstEnabled).append(",")
            append("\"cssAstEnabled\":").append(metadata.buildConfig.cssAstEnabled).append(",")
            append("\"behaviorAstEnabled\":").append(metadata.buildConfig.behaviorAstEnabled).append(",")
            append("\"samplePercent\":").append(nullableInt(metadata.buildConfig.samplePercent)).append(",")
            append("\"sampleSeed\":").append(nullableInt(metadata.buildConfig.sampleSeed)).append(",")
            append("\"sampleMode\":").append(nullableString(metadata.buildConfig.sampleMode))
            append("},")
            append("\"pivotIds\":").append(stringArray(snapshot.pivotIds)).append(",")
            append("\"records\":[")
            snapshot.records.forEachIndexed { idx, record ->
                if (idx > 0) append(",")
                append("{")
                append("\"componentId\":\"").append(escape(record.componentId)).append("\",")
                append("\"orderedPivotIds\":").append(stringArray(record.orderedPivotIds))
                append("}")
            }
            append("],")
            append("\"signatures\":[")
            snapshot.signatures.forEachIndexed { idx, sig ->
                if (idx > 0) append(",")
                append("{")
                append("\"id\":\"").append(escape(sig.id)).append("\",")
                append("\"framework\":\"").append(sig.framework.name.lowercase()).append("\",")
                append("\"domTagHistogram\":").append(stringIntMap(sig.domTagHistogram)).append(",")
                append("\"domLayoutPatterns\":").append(stringArray(sig.domLayoutPatterns.toList())).append(",")
                append("\"domDepth\":").append(sig.domDepth).append(",")
                append("\"domAvgBranching\":").append(sig.domAvgBranching).append(",")
                append("\"domRoleHistogram\":").append(stringIntMap(sig.domRoleHistogram)).append(",")
                append("\"cssStyleTokens\":").append(stringIntMap(sig.cssStyleTokens)).append(",")
                append("\"cssPalette\":").append(doubleTriplesArray(sig.cssPalette)).append(",")
                append("\"cssSpacingMean\":").append(sig.cssSpacingMean).append(",")
                append("\"cssSpacingStd\":").append(sig.cssSpacingStd).append(",")
                append("\"cssFontFamilies\":").append(stringArray(sig.cssFontFamilies.toList())).append(",")
                append("\"cssFontSizeBuckets\":").append(stringIntMap(sig.cssFontSizeBuckets)).append(",")
                append("\"behaviorEventTypes\":").append(stringArray(sig.behaviorEventTypes.toList())).append(",")
                append("\"behaviorInteractionPatterns\":").append(stringArray(sig.behaviorInteractionPatterns.toList())).append(",")
                append("\"behaviorStatePatterns\":").append(stringArray(sig.behaviorStatePatterns.toList())).append(",")
                append("\"behaviorApiSignatures\":").append(stringArray(sig.behaviorApiSignatures.toList())).append(",")
                append("\"behaviorCyclomatic\":").append(sig.behaviorCyclomatic).append(",")
                append("\"behaviorHandlerCount\":").append(sig.behaviorHandlerCount).append(",")
                append("\"behaviorApiCallCount\":").append(sig.behaviorApiCallCount).append(",")
                append("\"behaviorConditionalCount\":").append(sig.behaviorConditionalCount)
                append("}")
            }
            append("]")
            append("}")
        }
    }

    private fun decode(json: String): PermutationIndexSnapshotV1 {
        val errors = validate(json)
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val version = extractInt(json, "version")!!
        val createdAt = extractLong(json, "createdAtEpochMs")!!
        val componentCount = extractInt(json, "componentCount")!!
        val pivotCount = extractInt(json, "pivotCount")!!

        val frameworkCountsRaw = extractIntMap(json, "frameworkCounts")
        val frameworkCounts = frameworkCountsRaw.mapNotNull { (name, count) ->
            val framework = UiFramework.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            framework?.let { it to count }
        }.toMap()

        val cfgBlock = extractObjectBlock(json, "buildConfig")
            ?: error("Missing required field: buildConfig")
        val buildConfig = SnapshotBuildConfig(
            extractionMode = extractString(cfgBlock, "extractionMode") ?: error("Missing buildConfig.extractionMode"),
            domAstEnabled = extractBoolean(cfgBlock, "domAstEnabled")
                ?: error("Missing buildConfig.domAstEnabled"),
            cssAstEnabled = extractBoolean(cfgBlock, "cssAstEnabled")
                ?: error("Missing buildConfig.cssAstEnabled"),
            behaviorAstEnabled = extractBoolean(cfgBlock, "behaviorAstEnabled")
                ?: error("Missing buildConfig.behaviorAstEnabled"),
            samplePercent = extractNullableInt(cfgBlock, "samplePercent"),
            sampleSeed = extractNullableInt(cfgBlock, "sampleSeed"),
            sampleMode = extractNullableString(cfgBlock, "sampleMode")
        )

        val pivotIds = extractStringArray(json, "pivotIds")
        val recordsBody = extractArrayBlock(json, "records") ?: ""
        val records = splitTopLevelObjects(recordsBody).map { obj ->
            PersistedPermutation(
                componentId = extractString(obj, "componentId") ?: error("Missing records[].componentId"),
                orderedPivotIds = extractStringArray(obj, "orderedPivotIds")
            )
        }
        val signaturesBody = extractArrayBlock(json, "signatures")
        val signatures = if (signaturesBody == null) {
            emptyList()
        } else {
            splitTopLevelObjects(signaturesBody).map { obj ->
                PersistedComponentSignature(
                    id = extractString(obj, "id") ?: error("Missing signatures[].id"),
                    framework = parseFramework(extractString(obj, "framework") ?: error("Missing signatures[].framework")),
                    domTagHistogram = extractIntMap(obj, "domTagHistogram"),
                    domLayoutPatterns = extractStringArray(obj, "domLayoutPatterns").toSet(),
                    domDepth = extractInt(obj, "domDepth") ?: 0,
                    domAvgBranching = extractDouble(obj, "domAvgBranching") ?: 0.0,
                    domRoleHistogram = extractIntMap(obj, "domRoleHistogram"),
                    cssStyleTokens = extractIntMap(obj, "cssStyleTokens"),
                    cssPalette = extractDoubleTriplesArray(obj, "cssPalette"),
                    cssSpacingMean = extractDouble(obj, "cssSpacingMean") ?: 0.0,
                    cssSpacingStd = extractDouble(obj, "cssSpacingStd") ?: 0.0,
                    cssFontFamilies = extractStringArray(obj, "cssFontFamilies").toSet(),
                    cssFontSizeBuckets = extractIntMap(obj, "cssFontSizeBuckets"),
                    behaviorEventTypes = extractStringArray(obj, "behaviorEventTypes").toSet(),
                    behaviorInteractionPatterns = extractStringArray(obj, "behaviorInteractionPatterns").toSet(),
                    behaviorStatePatterns = extractStringArray(obj, "behaviorStatePatterns").toSet(),
                    behaviorApiSignatures = extractStringArray(obj, "behaviorApiSignatures").toSet(),
                    behaviorCyclomatic = extractInt(obj, "behaviorCyclomatic") ?: 0,
                    behaviorHandlerCount = extractInt(obj, "behaviorHandlerCount") ?: 0,
                    behaviorApiCallCount = extractInt(obj, "behaviorApiCallCount") ?: 0,
                    behaviorConditionalCount = extractInt(obj, "behaviorConditionalCount") ?: 0
                )
            }
        }

        return PermutationIndexSnapshotV1(
            metadata = SnapshotMetadata(
                version = version,
                createdAtEpochMs = createdAt,
                componentCount = componentCount,
                pivotCount = pivotCount,
                frameworkCounts = frameworkCounts,
                buildConfig = buildConfig
            ),
            pivotIds = pivotIds,
            records = records,
            signatures = signatures
        )
    }

    private fun stringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { "\"${escape(it)}\"" }

    private fun uiFrameworkIntMap(values: Map<UiFramework, Int>): String =
        values.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key.name.lowercase()}\":${it.value}" }

    private fun stringIntMap(values: Map<String, Int>): String =
        values.entries.joinToString(prefix = "{", postfix = "}") { "\"${escape(it.key)}\":${it.value}" }

    private fun doubleTriplesArray(values: List<Triple<Double, Double, Double>>): String =
        values.joinToString(prefix = "[", postfix = "]") { triple ->
            "{\"l\":${triple.first},\"a\":${triple.second},\"b\":${triple.third}}"
        }

    private fun nullableString(value: String?): String = value?.let { "\"${escape(it)}\"" } ?: "null"

    private fun nullableInt(value: Int?): String = value?.toString() ?: "null"

    private fun extractString(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return null
        val colon = json.indexOf(':', keyIndex + key.length + 2)
        if (colon == -1) return null
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return null
        val parsed = parseJsonString(json, i) ?: return null
        return unescape(parsed.first)
    }

    private fun extractNullableString(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return null
        val colon = json.indexOf(':', keyIndex + key.length + 2)
        if (colon == -1) return null
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i + 3 < json.length && json.substring(i, i + 4) == "null") return null
        if (i >= json.length || json[i] != '"') return null
        val parsed = parseJsonString(json, i) ?: return null
        return unescape(parsed.first)
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return null
        val colon = json.indexOf(':', keyIndex + key.length + 2)
        if (colon == -1) return null
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        return when {
            i + 3 < json.length && json.substring(i, i + 4) == "true" -> true
            i + 4 < json.length && json.substring(i, i + 5) == "false" -> false
            else -> null
        }
    }

    private fun extractInt(json: String, key: String): Int? {
        val regex = Regex("\"$key\"\\s*:\\s*([0-9]+)")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractLong(json: String, key: String): Long? {
        val regex = Regex("\"$key\"\\s*:\\s*([0-9]+)")
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractDouble(json: String, key: String): Double? {
        val regex = Regex("\"$key\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
        return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractNullableInt(json: String, key: String): Int? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return null
        val colon = json.indexOf(':', keyIndex + key.length + 2)
        if (colon == -1) return null
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i + 3 < json.length && json.substring(i, i + 4) == "null") return null
        var j = i
        while (j < json.length && json[j].isDigit()) j++
        return json.substring(i, j).toIntOrNull()
    }

    private fun extractArrayBlock(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return null
        val start = json.indexOf('[', keyIndex)
        if (start == -1) return null
        var depth = 0
        for (i in start until json.length) {
            val ch = json[i]
            if (ch == '[') depth++
            if (ch == ']') {
                depth--
                if (depth == 0) return json.substring(start + 1, i)
            }
        }
        return null
    }

    private fun extractObjectBlock(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return null
        val start = json.indexOf('{', keyIndex)
        if (start == -1) return null
        var depth = 0
        for (i in start until json.length) {
            val ch = json[i]
            if (ch == '{') depth++
            if (ch == '}') {
                depth--
                if (depth == 0) return json.substring(start + 1, i)
            }
        }
        return null
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val block = extractArrayBlock(json, key) ?: return emptyList()
        val values = mutableListOf<String>()
        var i = 0
        while (i < block.length) {
            if (block[i] == '"') {
                val parsed = parseJsonString(block, i) ?: break
                values += unescape(parsed.first)
                i = parsed.second
            } else {
                i++
            }
        }
        return values
    }

    private fun extractIntMap(json: String, key: String): Map<String, Int> {
        val block = extractObjectBlock(json, key) ?: return emptyMap()
        val pairRegex = Regex("\"((?:\\\\.|[^\"])*)\"\\s*:\\s*([0-9]+)")
        return pairRegex.findAll(block).associate {
            unescape(it.groupValues[1]) to it.groupValues[2].toInt()
        }
    }

    private fun extractDoubleTriplesArray(json: String, key: String): List<Triple<Double, Double, Double>> {
        val body = extractArrayBlock(json, key) ?: return emptyList()
        return splitTopLevelObjects(body).mapNotNull { obj ->
            val l = extractDouble(obj, "l") ?: return@mapNotNull null
            val a = extractDouble(obj, "a") ?: return@mapNotNull null
            val b = extractDouble(obj, "b") ?: return@mapNotNull null
            Triple(l, a, b)
        }
    }

    private fun splitTopLevelObjects(arrayBody: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        for ((idx, ch) in arrayBody.withIndex()) {
            if (ch == '{') {
                if (depth == 0) start = idx
                depth++
            } else if (ch == '}') {
                depth--
                if (depth == 0 && start >= 0) {
                    objects += arrayBody.substring(start, idx + 1)
                    start = -1
                }
            }
        }
        return objects
    }

    private fun parseJsonString(text: String, startQuoteIndex: Int): Pair<String, Int>? {
        if (startQuoteIndex < 0 || startQuoteIndex >= text.length || text[startQuoteIndex] != '"') return null
        val out = StringBuilder()
        var i = startQuoteIndex + 1
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\') {
                if (i + 1 >= text.length) return null
                out.append('\\')
                out.append(text[i + 1])
                i += 2
                continue
            }
            if (ch == '"') return out.toString() to (i + 1)
            out.append(ch)
            i++
        }
        return null
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

    private fun parseFramework(raw: String): UiFramework {
        return UiFramework.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UiFramework.UNKNOWN
    }
}
