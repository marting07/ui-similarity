package scanner

import corpus.ComponentSourceRef
import core.model.UiFramework

sealed class AstScanOutcome {
    data class Success(val refs: List<ComponentSourceRef>) : AstScanOutcome()
    data class Failure(val reason: String) : AstScanOutcome()
}

data class AstScanFailureEvent(
    val framework: UiFramework,
    val repoId: String,
    val reason: String,
    val fallbackUsed: Boolean
)
