package core.similarity

import core.model.BehaviorFeatures
import kotlin.math.abs

/**
 * Distance function over [BehaviorFeatures].  Behavioural distance is
 * computed from event handling patterns, interaction types, state management
 * styles, API usage and basic complexity metrics.  Jaccard distances are
 * used for sets and a simple normalised L1 difference for numeric
 * complexity values.
 */
class BehaviorDistance(
    private val maxCyclomatic: Int = 10,
    private val maxHandlers: Int = 10,
    private val maxApiCalls: Int = 10,
    private val maxConditionals: Int = 10
) : Distance<BehaviorFeatures> {
    override fun distance(a: BehaviorFeatures, b: BehaviorFeatures): Double {
        val dEvents = jaccardDistance(a.eventTypes, b.eventTypes)
        val dInteraction = jaccardDistance(a.interactionPatterns, b.interactionPatterns)
        val dState = jaccardDistance(a.statePatterns, b.statePatterns)
        val dApi = jaccardDistance(a.apiSignatures, b.apiSignatures)
        val dComplex = complexityDistance(a, b)
        return 0.25 * dEvents + 0.20 * dInteraction + 0.15 * dState + 0.20 * dApi + 0.20 * dComplex
    }

    private fun complexityDistance(a: BehaviorFeatures, b: BehaviorFeatures): Double {
        val diffs = listOf(
            abs(a.cyclomatic - b.cyclomatic).toDouble() / maxCyclomatic,
            abs(a.handlerCount - b.handlerCount).toDouble() / maxHandlers,
            abs(a.apiCallCount - b.apiCallCount).toDouble() / maxApiCalls,
            abs(a.conditionalCount - b.conditionalCount).toDouble() / maxConditionals
        ).map { it.coerceIn(0.0, 1.0) }
        return diffs.average()
    }
}
