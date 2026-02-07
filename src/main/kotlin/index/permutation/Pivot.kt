package index.permutation

import core.model.ComponentSignature

/**
 * A pivot is a reference component used to build permutation signatures.  It
 * stores the component’s ID and its [ComponentSignature] so that distances
 * can be computed between pivots and other components.  Pivots are
 * selected at index construction time.
 */
data class Pivot(
    val id: String,
    val signature: ComponentSignature
)