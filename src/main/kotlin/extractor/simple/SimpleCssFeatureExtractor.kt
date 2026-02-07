package extractor.simple

import core.model.ColorPoint
import core.model.CssFeatures
import extractor.ComponentSource
import extractor.CssFeatureExtractor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A naive CSS feature extractor that tokenises common style properties,
 * approximates spacing statistics and builds a small colour palette.
 *
 * This implementation relies on simple regular expressions to find
 * declarations in the provided CSS text.  It does not perform
 * full parsing of CSS and should be replaced with a proper parser
 * (e.g. via PostCSS or a Kotlin CSS parser) for production use.
 */
class SimpleCssFeatureExtractor : CssFeatureExtractor {

    override fun extractCssFeatures(source: ComponentSource): CssFeatures {
        val css = source.styleCode
        // Tokenise style declarations into high‑level tokens.  We look for
        // specific properties that influence layout, spacing, colours and
        // typography.  Each match increments the count for that token.
        val tokens = mutableMapOf<String, Int>()
        fun add(token: String) {
            tokens[token] = (tokens[token] ?: 0) + 1
        }
        // Layout tokens
        if (css.contains("display: flex", ignoreCase = true)) add("layout:flex")
        if (css.contains("flex-direction: column", ignoreCase = true)) add("flex:col")
        if (css.contains("flex-direction: row", ignoreCase = true)) add("flex:row")
        if (css.contains("align-items: center", ignoreCase = true)) add("align:center")
        if (css.contains("justify-content: space-between", ignoreCase = true)) add("justify:space-between")
        // Spacing tokens (we record presence of margin/padding declarations)
        if (css.contains("margin", ignoreCase = true)) add("margin")
        if (css.contains("padding", ignoreCase = true)) add("padding")
        // Shadow and radius tokens
        if (css.contains("box-shadow", ignoreCase = true)) add("shadow")
        if (css.contains("border-radius", ignoreCase = true)) add("radius")
        // Cursor and hover tokens
        if (css.contains("cursor: pointer", ignoreCase = true)) add("cursor:pointer")
        if (css.contains(":hover", ignoreCase = true)) add("hover")
        // Typography tokens
        if (css.contains("font-weight: bold", ignoreCase = true) || css.contains("font-weight: 700", ignoreCase = true)) add("fw:bold")
        if (css.contains("font-weight: 600", ignoreCase = true)) add("fw:semibold")
        if (css.contains("font-size", ignoreCase = true)) add("font-size")
        // Colour palette: extract hex colours and map them to a simple colour space
        val colourRegex = Regex("#[0-9a-fA-F]{6}")
        val colours = colourRegex.findAll(css).map { match ->
            val hex = match.value.drop(1)
            // Convert hex to rough Lab coordinates.  We use a dummy conversion
            // that maps each channel to a 0–100 scale to get values in roughly
            // the right range.  For rigorous colour handling you should
            // convert to CIELAB properly via sRGB.
            val r = Integer.parseInt(hex.substring(0, 2), 16)
            val g = Integer.parseInt(hex.substring(2, 4), 16)
            val b = Integer.parseInt(hex.substring(4, 6), 16)
            val l = (r + g + b) / 7.65  // 0–100 range approximation
            val a = ((r - b) / 2.55)    // approximate a* from R–B
            val bb = ((g - (r + b) / 2.0) / 2.55) // approximate b*
            ColorPoint(l, a, bb)
        }.toList()
        // Spacing statistics: gather pixel values from margin/padding declarations
        val spacingRegex = Regex("(margin|padding)[^;]*?([0-9]+)px", RegexOption.IGNORE_CASE)
        val spacingValues = spacingRegex.findAll(css).map { it.groupValues[2].toDouble() }.toList()
        val mean = if (spacingValues.isNotEmpty()) spacingValues.average() else 0.0
        val std = if (spacingValues.size > 1) {
            val avg = mean
            sqrt(spacingValues.map { (it - avg).pow(2) }.average())
        } else 0.0
        // Font size buckets: classify font sizes into buckets (xs, sm, md, lg, xl)
        val fontBuckets = mutableMapOf<String, Int>()
        val fontSizeRegex = Regex("font-size:\\s*([0-9]+)px", RegexOption.IGNORE_CASE)
        for (match in fontSizeRegex.findAll(css)) {
            val px = match.groupValues[1].toInt()
            val bucket = when {
                px <= 12 -> "xs"
                px <= 14 -> "sm"
                px <= 16 -> "md"
                px <= 20 -> "lg"
                else -> "xl"
            }
            fontBuckets[bucket] = (fontBuckets[bucket] ?: 0) + 1
        }
        return CssFeatures(
            styleTokens = tokens,
            palette = colours,
            spacingMean = mean,
            spacingStd = std,
            fontFamilies = emptySet(),  // no font‑family detection in this simple version
            fontSizeBuckets = fontBuckets
        )
    }
}
