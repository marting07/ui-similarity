package index.permutation

import core.model.ComponentSignature
import core.similarity.ComponentDistance

/**
 * Represents an ordering of pivot IDs for a specific component.  The list
 * [orderedPivotIds] contains the IDs of pivots sorted in ascending order
 * of their distance to the component.  This is used as a compact
 * representation of the component’s relative position in metric space.
 */
data class PermutationSignature(
    val componentId: String,
    val orderedPivotIds: List<String>
)

/**
 * A permutation index organises components by comparing their relative
 * distances to a fixed set of pivots.  Given a new component, it
 * approximates nearest neighbours by computing the overlap between the
 * component’s pivot ordering and those of indexed components.  This
 * structure provides fast approximate kNN queries in high‑dimensional or
 * expensive metric spaces.
 */
class PermutationIndex(
    private val pivots: List<Pivot>,
    private val distance: ComponentDistance
) {
    // Map from component ID to its permutation signature
    private val index = mutableMapOf<String, PermutationSignature>()

    /**
     * Build permutation signatures for all components in [dataset] and
     * populate the internal index.  Each signature is created by
     * computing the distances from the component to every pivot and
     * sorting the pivots by increasing distance.
     */
    fun build(dataset: Iterable<ComponentSignature>) {
        index.clear()
        for (comp in dataset) {
            val perm = computePermutation(comp)
            index[comp.id] = perm
        }
    }
    /**
     * Compute the permutation signature of an arbitrary component.  It
     * returns a [PermutationSignature] containing the pivot IDs ordered by
     * their proximity to [component].
     */
    fun computePermutation(component: ComponentSignature): PermutationSignature {
        val distances = pivots.map { pivot ->
            pivot.id to distance.distance(component, pivot.signature)
        }
        val ordered = distances.sortedBy { it.second }.map { it.first }
        return PermutationSignature(component.id, ordered)
    }
    /**
     * Query the index for approximate neighbours of [query].  Returns a
     * list of component IDs paired with a similarity score in descending
     * order of similarity.  The similarity is computed as the fraction
     * of shared pivots in the top [k] positions between the query
     * permutation and each indexed permutation.  Only the top [topN]
     * results are returned.
     */
    fun querySimilar(query: ComponentSignature, k: Int = 10, topN: Int = 20): List<Pair<String, Double>> {
        val effectiveK = minOf(k, pivots.size).coerceAtLeast(0)
        val queryPerm = computePermutation(query).orderedPivotIds.take(effectiveK).toSet()
        val scores = mutableListOf<Pair<String, Double>>()
        for ((id, perm) in index) {
            val candidateTop = perm.orderedPivotIds.take(effectiveK).toSet()
            val inter = queryPerm.intersect(candidateTop).size.toDouble()
            val sim = if (effectiveK > 0) inter / effectiveK.toDouble() else 0.0
            scores += id to sim
        }
        return scores.sortedByDescending { it.second }.take(topN)
    }
}
