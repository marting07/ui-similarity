package persistence

import core.model.UiFramework

/**
 * Snapshot schema version for persisted similarity index files.
 */
const val INDEX_SNAPSHOT_VERSION_V1: Int = 1

/**
 * Build-time configuration captured inside a persisted snapshot so CLI/UI
 * runs are reproducible.
 */
data class SnapshotBuildConfig(
    val extractionMode: String,
    val domAstEnabled: Boolean,
    val cssAstEnabled: Boolean,
    val behaviorAstEnabled: Boolean,
    val samplePercent: Int? = null,
    val sampleSeed: Int? = null,
    val sampleMode: String? = null
)

/**
 * Stable metadata section shown by CLI `inspect` and desktop index details.
 */
data class SnapshotMetadata(
    val version: Int = INDEX_SNAPSHOT_VERSION_V1,
    val createdAtEpochMs: Long,
    val componentCount: Int,
    val pivotCount: Int,
    val frameworkCounts: Map<UiFramework, Int>,
    val buildConfig: SnapshotBuildConfig
)

/**
 * Minimal persisted representation of one indexed component permutation.
 */
data class PersistedPermutation(
    val componentId: String,
    val orderedPivotIds: List<String>
)

/**
 * Persisted signature payload used for query and inspect flows without
 * re-running extraction.
 */
data class PersistedComponentSignature(
    val id: String,
    val framework: UiFramework,
    val domTagHistogram: Map<String, Int>,
    val domLayoutPatterns: Set<String>,
    val domDepth: Int,
    val domAvgBranching: Double,
    val domRoleHistogram: Map<String, Int>,
    val cssStyleTokens: Map<String, Int>,
    val cssPalette: List<Triple<Double, Double, Double>>,
    val cssSpacingMean: Double,
    val cssSpacingStd: Double,
    val cssFontFamilies: Set<String>,
    val cssFontSizeBuckets: Map<String, Int>,
    val behaviorEventTypes: Set<String>,
    val behaviorInteractionPatterns: Set<String>,
    val behaviorStatePatterns: Set<String>,
    val behaviorApiSignatures: Set<String>,
    val behaviorCyclomatic: Int,
    val behaviorHandlerCount: Int,
    val behaviorApiCallCount: Int,
    val behaviorConditionalCount: Int
)

/**
 * Snapshot payload for permutation index v1.
 *
 * Note: this class models the on-disk contract. IO save/load behavior is
 * intentionally implemented in a separate service (Phase 1, step 2).
 */
data class PermutationIndexSnapshotV1(
    val metadata: SnapshotMetadata,
    val pivotIds: List<String>,
    val records: List<PersistedPermutation>,
    val signatures: List<PersistedComponentSignature> = emptyList()
)
