package core.model

/**
 * A simple colour representation in Lab space.  Extractors should convert
 * colours from their original formats (HEX, RGB, HSL) into a perceptually
 * uniform space like CIELAB.  The [CssDistance] then computes Euclidean
 * distances and normalises them by a maximum possible distance.
 */
data class ColorPoint(
    val l: Double,
    val a: Double,
    val b: Double
)

/**
 * Features extracted from the styling layer of a UI component.  Components
 * typically accumulate tokens from CSS declarations (normalised to
 * semantic categories), summary statistics of spacing values, small sets of
 * representative colours, and typography information such as font families
 * and sizes.  These features should be normalised before comparison.
 */
data class CssFeatures(
    val styleTokens: Map<String, Int>,
    val palette: List<ColorPoint>,
    val spacingMean: Double,
    val spacingStd: Double,
    val fontFamilies: Set<String>,
    val fontSizeBuckets: Map<String, Int>
)
