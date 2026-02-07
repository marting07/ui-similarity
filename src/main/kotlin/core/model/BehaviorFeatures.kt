package core.model

/**
 * Features extracted from the behavioural logic of a UI component.  These
 * properties capture event handling patterns, high‑level interaction patterns
 * (e.g. lists, forms, modals), state management techniques, abstracted API
 * signatures and simple complexity metrics.  For the purposes of the
 * similarity metric, values are normalised into the [0,1] range.
 */
data class BehaviorFeatures(
    val eventTypes: Set<String>,
    val interactionPatterns: Set<String>,
    val statePatterns: Set<String>,
    val apiSignatures: Set<String>,
    val cyclomatic: Int,
    val handlerCount: Int,
    val apiCallCount: Int,
    val conditionalCount: Int
)
