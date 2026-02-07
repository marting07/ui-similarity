package core.similarity

import core.model.DomFeatures
import kotlin.math.abs

/**
 * Distance function over [DomFeatures].  The distance is a weighted sum of
 * sub‑distances over tag histograms, layout patterns, shape (depth/branching)
 * and semantic roles.  Weights can be tuned empirically.  Depth and
 * branching differences are normalised by maximum caps.
 */
class DomDistance(
    private val maxDepthCap: Int = 10,
    private val maxBranchCap: Double = 5.0
) : Distance<DomFeatures> {
    override fun distance(a: DomFeatures, b: DomFeatures): Double {
        val dTags = cosineHistDistance(a.tagHistogram, b.tagHistogram)
        val dLayout = jaccardDistance(a.layoutPatterns, b.layoutPatterns)
        val dShape = shapeDistance(a, b)
        val dRoles = cosineHistDistance(a.roleHistogram, b.roleHistogram)
        return 0.45 * dTags + 0.25 * dLayout + 0.20 * dShape + 0.10 * dRoles
    }

    private fun shapeDistance(a: DomFeatures, b: DomFeatures): Double {
        val depthDiff = abs(a.depth - b.depth).toDouble() / maxDepthCap
        val branchDiff = abs(a.avgBranching - b.avgBranching) / maxBranchCap
        val depthNorm = depthDiff.coerceIn(0.0, 1.0)
        val branchNorm = branchDiff.coerceIn(0.0, 1.0)
        return 0.7 * depthNorm + 0.3 * branchNorm
    }
}
