package tests

import core.similarity.BehaviorDistance
import core.similarity.ComponentDistance
import core.similarity.CssDistance
import core.similarity.DomDistance
import core.similarity.cosineHistDistance
import core.similarity.jaccardDistance
import tests.TestSupport.assertApproxEquals
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object SimilarityTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("cosine histogram distance handles empty and identical histograms") {
                assertEquals(0.0, cosineHistDistance(emptyMap(), emptyMap()), "Empty histograms should match")
                assertEquals(1.0, cosineHistDistance(mapOf("a" to 1), emptyMap()), "One empty histogram should be max distance")
                assertApproxEquals(0.0, cosineHistDistance(mapOf("a" to 2), mapOf("a" to 2)), 1e-9, "Identical histograms should have zero distance")
            },
            TestSupport.test("jaccard distance handles boundary cases") {
                assertEquals(0.0, jaccardDistance(emptySet(), emptySet()), "Empty sets should match")
                assertEquals(1.0, jaccardDistance(setOf("x"), emptySet()), "Disjoint with empty should be max distance")
                assertApproxEquals(2.0 / 3.0, jaccardDistance(setOf("a", "b"), setOf("b", "c")), 1e-9, "Expected Jaccard distance")
            },
            TestSupport.test("dom distance is zero for identical and positive for different signatures") {
                val domDistance = DomDistance()
                val a = TestData.signature("a", tags = mapOf("div" to 2, "button" to 1)).dom
                val b = TestData.signature("b", tags = mapOf("div" to 2, "button" to 1)).dom
                val c = TestData.signature("c", tags = mapOf("ul" to 3)).dom
                assertApproxEquals(0.0, domDistance.distance(a, b), 1e-9, "Identical DOM features should yield zero distance")
                assertTrue(domDistance.distance(a, c) > 0.0, "Different DOM features should yield positive distance")
            },
            TestSupport.test("css and behavior distances are bounded") {
                val cssDistance = CssDistance()
                val behaviorDistance = BehaviorDistance()
                val a = TestData.signature("a", styles = mapOf("layout:flex" to 1), events = setOf("click"), cyclomatic = 2)
                val b = TestData.signature("b", styles = mapOf("layout:flex" to 1), events = setOf("click"), cyclomatic = 2)
                val c = TestData.signature("c", styles = mapOf("radius" to 1), events = setOf("submit"), cyclomatic = 7)
                assertApproxEquals(0.0, cssDistance.distance(a.css, b.css), 1e-9, "Identical CSS should be zero")
                assertApproxEquals(0.0, behaviorDistance.distance(a.behavior, b.behavior), 1e-9, "Identical behavior should be zero")
                assertTrue(cssDistance.distance(a.css, c.css) in 0.0..1.0, "CSS distance should stay in [0,1]")
                assertTrue(behaviorDistance.distance(a.behavior, c.behavior) in 0.0..1.0, "Behavior distance should stay in [0,1]")
            },
            TestSupport.test("component distance composes normalized sub-distances") {
                val distance = ComponentDistance()
                val a = TestData.signature("a", tags = mapOf("div" to 1), styles = mapOf("margin" to 1), events = setOf("click"))
                val b = TestData.signature("b", tags = mapOf("div" to 1), styles = mapOf("margin" to 1), events = setOf("click"))
                val c = TestData.signature("c", tags = mapOf("ul" to 1), styles = mapOf("radius" to 1), events = setOf("submit"), cyclomatic = 4)
                assertApproxEquals(0.0, distance.distance(a, b), 1e-9, "Identical component signatures should be zero")
                assertTrue(distance.distance(a, c) in 0.0..1.0, "Component distance should stay in [0,1]")
            }
        )
    }
}
