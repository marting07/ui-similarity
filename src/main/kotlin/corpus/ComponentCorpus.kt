package corpus

import core.model.ComponentSignature

/**
 * Associates a component’s source location with its extracted signature.
 * The [id] of the [signature] and the [key] of the [sourceRef] must match.
 */
data class ComponentRecord(
    val sourceRef: ComponentSourceRef,
    val signature: ComponentSignature
) {
    init {
        require(sourceRef.key.id == signature.id) {
            "Signature ID ${signature.id} does not match source key ${sourceRef.key.id}"
        }
    }
    val id: String get() = signature.id
}

/**
 * A collection of component records.  Provides helper methods for looking up
 * records by ID and accessing all signatures.  A corpus is typically
 * constructed after scanning a set of repositories and extracting
 * signatures for all components found.
 */
data class ComponentCorpus(
    val records: List<ComponentRecord>
) {
    val byId: Map<String, ComponentRecord> = records.associateBy { it.id }
    /** Returns all component signatures contained in this corpus. */
    fun signatures(): List<ComponentSignature> = records.map { it.signature }
    /**
     * Returns a sub‑corpus containing only the records belonging to the
     * specified repository.
     */
    fun forRepo(repoId: RepoId): ComponentCorpus =
        ComponentCorpus(records.filter { it.sourceRef.key.repoId == repoId })
}

/**
 * Represents a split of a corpus into a training set and a query set.  The
 * training set is used to build the index, and the query set is used
 * for evaluation or interactive querying.
 */
data class CorpusSplit(
    val train: ComponentCorpus,
    val query: ComponentCorpus
)