package pipeline

import core.model.ComponentSignature
import core.model.UiFramework
import corpus.ComponentCorpus
import corpus.ComponentSourceRef
import persistence.PermutationIndexSnapshotV1

/**
 * Sampling mode used by shared pipeline APIs. This will back desktop controls
 * and optional CLI sampling flags.
 */
enum class RepoSamplingMode {
    GLOBAL,
    STRATIFIED_FRAMEWORK
}

/**
 * Optional repository-level sampling configuration. When absent, all repos are
 * processed.
 */
data class SamplingConfig(
    val percent: Int,
    val seed: Int,
    val mode: RepoSamplingMode = RepoSamplingMode.GLOBAL
)

/**
 * Request for a full scan+extract+index build.
 */
data class BuildIndexRequest(
    val reposRoot: String,
    val extractionMode: String,
    val domAstEnabled: Boolean,
    val cssAstEnabled: Boolean,
    val behaviorAstEnabled: Boolean,
    val frameworks: Set<UiFramework> = setOf(UiFramework.REACT, UiFramework.ANGULAR, UiFramework.VUE),
    val sampling: SamplingConfig? = null,
    val pivotCount: Int = 16,
    val pivotSeed: Int = 42
)

/**
 * Result for index build operations. `snapshot` is optional because callers
 * may only want in-memory execution in some flows.
 */
data class BuildIndexResult(
    val scannedComponents: Int,
    val corpus: ComponentCorpus,
    val sourceRefs: List<ComponentSourceRef>,
    val snapshot: PermutationIndexSnapshotV1?
)

/**
 * Query request against a loaded snapshot/index.
 */
data class QuerySimilarityRequest(
    val snapshot: PermutationIndexSnapshotV1,
    val componentId: String,
    val topK: Int = 10,
    val topN: Int = 20
)

/**
 * One similarity match shown by CLI and desktop.
 */
data class SimilarityMatch(
    val componentId: String,
    val similarity: Double
)

/**
 * Query response payload.
 */
data class QuerySimilarityResult(
    val queryComponentId: String,
    val matches: List<SimilarityMatch>
)

/**
 * Shared service contract for CLI and desktop integration.
 *
 * Implementation details are intentionally deferred to Phase 1 step 2+.
 */
interface SimilarityPipelineService {
    fun buildIndex(request: BuildIndexRequest): BuildIndexResult
    fun query(request: QuerySimilarityRequest): QuerySimilarityResult
    fun listSignatures(snapshot: PermutationIndexSnapshotV1): List<ComponentSignature>
}

