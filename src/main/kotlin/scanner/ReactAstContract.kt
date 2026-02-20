package scanner

data class ReactAstScanRequest(
    val repoHost: String,
    val repoOwner: String,
    val repoName: String,
    val repoRoot: String
)

data class ReactAstComponentDescriptor(
    val relativePath: String,
    val exportName: String,
    val templatePath: String? = null,
    val logicPath: String? = null,
    val stylePaths: List<String> = emptyList(),
    val inlineTemplateCode: String? = null,
    val inlineStyleCodes: List<String> = emptyList()
)

data class ReactAstScanResponse(
    val status: String,
    val components: List<ReactAstComponentDescriptor>,
    val error: String? = null
)

object ReactAstContractJson {
    fun encodeRequest(request: ReactAstScanRequest): String {
        return buildString {
            append("{")
            append("\"repoHost\":\"").append(escape(request.repoHost)).append("\",")
            append("\"repoOwner\":\"").append(escape(request.repoOwner)).append("\",")
            append("\"repoName\":\"").append(escape(request.repoName)).append("\",")
            append("\"repoRoot\":\"").append(escape(request.repoRoot)).append("\"")
            append("}")
        }
    }

    fun decodeResponse(json: String): ReactAstScanResponse? {
        val status = extractString(json, "status") ?: return null
        val error = extractString(json, "error")
        val componentsRaw = extractArrayBlock(json, "components")
        val components = if (componentsRaw.isNullOrBlank()) {
            emptyList()
        } else {
            splitTopLevelObjects(componentsRaw).mapNotNull { obj ->
                val relativePath = extractString(obj, "relativePath") ?: return@mapNotNull null
                val exportName = extractString(obj, "exportName") ?: return@mapNotNull null
                ReactAstComponentDescriptor(
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
        return ReactAstScanResponse(status = status, components = components, error = error)
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        val raw = regex.find(json)?.groupValues?.get(1) ?: return null
        return unescape(raw)
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
                if (depth == 0) {
                    return json.substring(start + 1, i)
                }
            }
        }
        return null
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val block = extractArrayBlock(json, key) ?: return emptyList()
        val strRegex = Regex("\"((?:\\\\.|[^\"])*)\"")
        return strRegex.findAll(block).map { unescape(it.groupValues[1]) }.toList()
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
}
