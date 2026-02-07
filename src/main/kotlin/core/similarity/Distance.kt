package core.similarity

/**
 * Generic distance interface for objects of type [T].  Distances are expected
 * to be normalised into the [0,1] range, where 0.0 indicates identical
 * objects and 1.0 indicates maximum dissimilarity.  Implementations may
 * coerce values beyond the range into the range.
 */
fun interface Distance<T> {
    fun distance(a: T, b: T): Double
}
