package core.model

/**
 * Features extracted from the DOM or template layer of a UI component.  These
 * properties capture structural aspects of the markup, such as the types and
 * frequencies of tags, high‑level layout patterns, semantic roles and
 * approximate tree statistics.  All counts and numeric values should be
 * normalised by extractors before being compared.
 */
data class DomFeatures(
    val tagHistogram: Map<String, Int>,
    val layoutPatterns: Set<String>,
    val depth: Int,
    val avgBranching: Double,
    val roleHistogram: Map<String, Int>
)
