package tests

object TestSupport {
    fun assertTrue(condition: Boolean, message: String) {
        if (!condition) throw AssertionError(message)
    }

    fun <T> assertEquals(expected: T, actual: T, message: String) {
        if (expected != actual) {
            throw AssertionError("$message. expected=$expected actual=$actual")
        }
    }

    fun assertContains(haystack: String, needle: String, message: String) {
        if (!haystack.contains(needle)) {
            throw AssertionError("$message. missing='$needle'")
        }
    }

    fun assertApproxEquals(expected: Double, actual: Double, epsilon: Double, message: String) {
        if (kotlin.math.abs(expected - actual) > epsilon) {
            throw AssertionError("$message. expected=$expected actual=$actual epsilon=$epsilon")
        }
    }

    fun test(name: String, block: () -> Unit): Pair<String, Throwable?> {
        return try {
            block()
            name to null
        } catch (t: Throwable) {
            name to t
        }
    }
}
