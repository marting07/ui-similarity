package index.permutation

import core.model.ComponentSignature
import kotlin.random.Random

/**
 * Utility for selecting a set of pivot components from a dataset.  The
 * simplest strategy picks a random subset of components.  More advanced
 * strategies (e.g. k‑medoids) can be implemented if desired.
 */
object PivotSelector {
    /**
     * Select [count] pivots uniformly at random from [dataset].  Throws if
     * the dataset has fewer elements than the requested number of pivots.
     */
    fun randomPivots(dataset: List<ComponentSignature>, count: Int, random: Random = Random): List<Pivot> {
        require(dataset.size >= count) { "Not enough components to choose $count pivots" }
        return dataset.shuffled(random).take(count).map { comp -> Pivot(comp.id, comp) }
    }
}