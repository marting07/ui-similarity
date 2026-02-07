package core.similarity

import kotlin.math.sqrt

/**
 * Compute the cosine distance between two sparse histograms.  Histograms are
 * represented as maps from keys to integer counts.  An empty histogram is
 * considered identical to another empty histogram (distance 0.0).  If only
 * one histogram is empty, the distance is 1.0.
 */
fun cosineHistDistance(a: Map<String, Int>, b: Map<String, Int>): Double {
    if (a.isEmpty() && b.isEmpty()) return 0.0
    val keys = a.keys + b.keys
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (k in keys) {
        val va = (a[k] ?: 0).toDouble()
        val vb = (b[k] ?: 0).toDouble()
        dot += va * vb
        normA += va * va
        normB += vb * vb
    }
    if (normA == 0.0 || normB == 0.0) return 1.0
    val sim = dot / (sqrt(normA) * sqrt(normB))
    return (1.0 - sim).coerceIn(0.0, 1.0)
}

/**
 * Compute the Jaccard distance between two sets.  An empty set compared to
 * another empty set yields distance 0.0.  Otherwise the distance is
 * 1 minus the ratio of intersection to union sizes.
 */
fun jaccardDistance(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() && b.isEmpty()) return 0.0
    val inter = a.intersect(b).size.toDouble()
    val union = a.union(b).size.toDouble()
    return (1.0 - inter / union).coerceIn(0.0, 1.0)
}
