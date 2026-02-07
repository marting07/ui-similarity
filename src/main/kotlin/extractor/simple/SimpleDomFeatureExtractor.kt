package extractor.simple

import core.model.DomFeatures
import extractor.ComponentSource
import extractor.DomFeatureExtractor

/**
 * A naive DOM feature extractor that operates on plain text.  It counts tag
 * names using a regular expression, estimates depth and branching from
 * indentation and looks for simple layout hints in CSS.  This extractor is
 * provided for demonstration purposes and should be replaced with a
 * framework‑aware parser for real experiments.
 */
class SimpleDomFeatureExtractor : DomFeatureExtractor {
    private val tagRegex = Regex("""<\s*([a-zA-Z0-9]+)""")
    private val roleRegex = Regex("""role\s*=\s*"([^"]+)""")

    override fun extractDomFeatures(source: ComponentSource): DomFeatures {
        val code = source.templateCode
        fun countOccurrences(haystack: String, needle: String): Int {
            var count = 0
            var index = 0
            while (true) {
                index = haystack.indexOf(needle, index)
                if (index == -1) return count
                count++
                index += needle.length
            }
        }
        // Count tags
        val tagCounts = mutableMapOf<String, Int>()
        for (match in tagRegex.findAll(code)) {
            val tag = match.groupValues[1].lowercase()
            tagCounts[tag] = (tagCounts[tag] ?: 0) + 1
        }
        // Count roles
        val roleCounts = mutableMapOf<String, Int>()
        for (match in roleRegex.findAll(code)) {
            val role = match.groupValues[1].lowercase()
            roleCounts[role] = (roleCounts[role] ?: 0) + 1
        }
        // Estimate depth and branching using simple indentation heuristics
        val lines = code.lineSequence()
        var currentDepth = 0
        var maxDepth = 1
        var totalChildren = 0
        var parentCount = 0
        for (line in lines) {
            val open = countOccurrences(line, "<")
            val close = countOccurrences(line, "</")
            currentDepth += open
            if (currentDepth > maxDepth) maxDepth = currentDepth
            if (open > 0) {
                totalChildren += open
                parentCount++
            }
            currentDepth -= close
        }
        val avgBranch = if (parentCount > 0) totalChildren.toDouble() / parentCount else 0.0
        // Layout patterns: gleaned from style tokens in CSS (very naive)
        val patterns = mutableSetOf<String>()
        if (source.styleCode.contains("display: flex")) {
            if (source.styleCode.contains("flex-direction: column")) patterns += "flex-col"
            if (source.styleCode.contains("flex-direction: row") || source.styleCode.contains("align-items: center")) patterns += "flex-row-center"
        }
        if (code.contains("<ul")) patterns += "list-vertical"
        return DomFeatures(
            tagHistogram = tagCounts,
            layoutPatterns = patterns,
            depth = maxDepth,
            avgBranching = avgBranch,
            roleHistogram = roleCounts
        )
    }
}
