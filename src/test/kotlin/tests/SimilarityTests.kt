package tests

import core.similarity.BehaviorDistance
import core.similarity.ComponentDistance
import core.similarity.CssDistance
import core.similarity.DomDistance
import core.similarity.cosineHistDistance
import core.similarity.jaccardDistance
import core.model.BehaviorFeatures
import core.model.ColorPoint
import core.model.ComponentSignature
import core.model.CssFeatures
import core.model.DomFeatures
import core.model.UiFramework
import index.permutation.PermutationIndex
import index.permutation.Pivot
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
            },
            TestSupport.test("similarity score is 1.0 for same signature and near 0 for very different signatures") {
                val distance = ComponentDistance()
                val same = ComponentSignature(
                    id = "same",
                    framework = UiFramework.REACT,
                    dom = DomFeatures(
                        tagHistogram = mapOf("div" to 4, "button" to 2),
                        layoutPatterns = setOf("flex-col"),
                        depth = 5,
                        avgBranching = 2.8,
                        roleHistogram = mapOf("button" to 2, "navigation" to 1)
                    ),
                    css = CssFeatures(
                        styleTokens = mapOf("layout:flex" to 3, "margin" to 2, "font-size" to 2),
                        palette = listOf(ColorPoint(60.0, 20.0, 10.0), ColorPoint(30.0, -5.0, 8.0)),
                        spacingMean = 12.0,
                        spacingStd = 4.0,
                        fontFamilies = setOf("Inter", "sans-serif"),
                        fontSizeBuckets = mapOf("sm" to 2, "md" to 3)
                    ),
                    behavior = BehaviorFeatures(
                        eventTypes = setOf("click", "change"),
                        interactionPatterns = setOf("form"),
                        statePatterns = setOf("localState"),
                        apiSignatures = setOf("fetchUser"),
                        cyclomatic = 6,
                        handlerCount = 2,
                        apiCallCount = 1,
                        conditionalCount = 2
                    )
                )
                val diff = ComponentSignature(
                    id = "diff",
                    framework = UiFramework.VUE,
                    dom = DomFeatures(
                        tagHistogram = mapOf("table" to 6, "tr" to 12, "td" to 24),
                        layoutPatterns = setOf("grid"),
                        depth = 1,
                        avgBranching = 0.3,
                        roleHistogram = mapOf("grid" to 1)
                    ),
                    css = CssFeatures(
                        styleTokens = mapOf("shadow" to 1, "radius" to 1, "hover" to 1),
                        palette = listOf(ColorPoint(5.0, -40.0, -30.0)),
                        spacingMean = 0.0,
                        spacingStd = 0.0,
                        fontFamilies = setOf("Monaco"),
                        fontSizeBuckets = mapOf("xl" to 2)
                    ),
                    behavior = BehaviorFeatures(
                        eventTypes = setOf("submit", "keydown"),
                        interactionPatterns = setOf("click-toggle"),
                        statePatterns = setOf("vuex"),
                        apiSignatures = setOf("axios.post"),
                        cyclomatic = 1,
                        handlerCount = 6,
                        apiCallCount = 4,
                        conditionalCount = 0
                    )
                )

                val sameSimilarity = 1.0 - distance.distance(same, same)
                val diffSimilarity = 1.0 - distance.distance(same, diff)
                assertApproxEquals(1.0, sameSimilarity, 1e-9, "Same component should have similarity 1.0")
                assertTrue(diffSimilarity < 0.35, "Very different components should be closer to 0.0 than to 1.0")
            },
            TestSupport.test("permutation query returns 1.0 for same component and lower score for different ones") {
                val distance = ComponentDistance()
                val base = TestData.signature("base", tags = mapOf("div" to 2), styles = mapOf("margin" to 1), events = setOf("click"))
                val mid = TestData.signature("mid", tags = mapOf("section" to 2), styles = mapOf("padding" to 1), events = setOf("change"))
                val far = TestData.signature("far", tags = mapOf("table" to 5), styles = mapOf("shadow" to 1), events = setOf("submit"), cyclomatic = 8)
                val pivots = listOf(Pivot(base.id, base), Pivot(mid.id, mid), Pivot(far.id, far))
                val index = PermutationIndex(pivots, distance)
                index.build(listOf(base, mid, far))

                val scores = index.querySimilar(base, k = 1, topN = 3).toMap()
                assertApproxEquals(1.0, scores.getValue("base"), 1e-9, "Querying same component should score 1.0")
                assertTrue(scores.getValue("far") < scores.getValue("base"), "Different component should score lower than identical")
            }
        )
    }
}
