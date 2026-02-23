package scanner

data class VueAstScanRequest(
    val repoHost: String,
    val repoOwner: String,
    val repoName: String,
    val repoRoot: String
)

data class VueAstComponentDescriptor(
    val relativePath: String,
    val exportName: String,
    val templatePath: String? = null,
    val logicPath: String? = null,
    val stylePaths: List<String> = emptyList(),
    val inlineTemplateCode: String? = null,
    val inlineStyleCodes: List<String> = emptyList()
)

data class VueAstScanResponse(
    val status: String,
    val components: List<VueAstComponentDescriptor>,
    val error: String? = null
)

object VueAstContractJson {
    fun encodeRequest(request: VueAstScanRequest): String {
        return buildString {
            append("{")
            append("\"repoHost\":\"").append(escape(request.repoHost)).append("\",")
            append("\"repoOwner\":\"").append(escape(request.repoOwner)).append("\",")
            append("\"repoName\":\"").append(escape(request.repoName)).append("\",")
            append("\"repoRoot\":\"").append(escape(request.repoRoot)).append("\"")
            append("}")
        }
    }

    fun decodeResponse(json: String): VueAstScanResponse? {
        val status = extractString(json, "status") ?: return null
        val error = extractString(json, "error")
        val componentsRaw = extractArrayBlock(json, "components")
        val components = if (componentsRaw.isNullOrBlank()) {
            emptyList()
        } else {
            splitTopLevelObjects(componentsRaw).mapNotNull { obj ->
                val relativePath = extractString(obj, "relativePath") ?: return@mapNotNull null
                val exportName = extractString(obj, "exportName") ?: return@mapNotNull null
                VueAstComponentDescriptor(
                    relativePath = relativePath,
                    exportName = exportName,
                    templatePath = extractString(obj, "templatePath"),
                    logicPath = extractString(obj, "logicPath"),
                    stylePaths = extractStringArray(obj, "stylePaths"),
                    inlineTemplateCode = extractString(obj, "inlineTemplateCode"),
                    inlineStyleCodes = extractStringArray(obj, "inlineStyleCodes")
                )
            }
        }
        return VueAstScanResponse(status = status, components = components, error = error)
    }

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
}
