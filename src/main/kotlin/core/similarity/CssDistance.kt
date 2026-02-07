package core.similarity

import core.model.ColorPoint
import core.model.CssFeatures
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Distance function over [CssFeatures].  The CSS distance is composed of
 * sub‑distances on style tokens (cosine on frequency vectors), colour palette,
 * spacing statistics, font families and font size distributions.  Each
 * component is weighted to reflect its relative importance.
 */
class CssDistance(
    private val maxSpacing: Double = 20.0,
    private val maxColorDist: Double = 100.0
) : Distance<CssFeatures> {
    override fun distance(a: CssFeatures, b: CssFeatures): Double {
        val dTokens = cosineHistDistance(a.styleTokens, b.styleTokens)
        val dPalette = paletteDistance(a.palette, b.palette)
        val dSpacing = spacingDistance(a, b)
        val dFonts = jaccardDistance(a.fontFamilies, b.fontFamilies)
        val dFontSize = cosineHistDistance(a.fontSizeBuckets, b.fontSizeBuckets)
        return 0.45 * dTokens + 0.25 * dPalette + 0.15 * dSpacing + 0.10 * dFonts + 0.05 * dFontSize
    }

    private fun paletteDistance(a: List<ColorPoint>, b: List<ColorPoint>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        if (a.isEmpty() || b.isEmpty()) return 1.0
        // For each colour in A, find the nearest colour in B and average the distances
        val distances = a.map { c -> b.minOf { d -> colorEuclidean(c, d) } }
        val avg = distances.average()
        return (avg / maxColorDist).coerceIn(0.0, 1.0)
    }

    private fun colorEuclidean(c1: ColorPoint, c2: ColorPoint): Double {
        return sqrt((c1.l - c2.l).pow(2) + (c1.a - c2.a).pow(2) + (c1.b - c2.b).pow(2))
    }

    private fun spacingDistance(a: CssFeatures, b: CssFeatures): Double {
        val mDiff = abs(a.spacingMean - b.spacingMean) / maxSpacing
        val sDiff = abs(a.spacingStd - b.spacingStd) / maxSpacing
        val mNorm = mDiff.coerceIn(0.0, 1.0)
        val sNorm = sDiff.coerceIn(0.0, 1.0)
        return 0.7 * mNorm + 0.3 * sNorm
    }
}
