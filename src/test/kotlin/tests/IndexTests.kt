package tests

import core.similarity.ComponentDistance
import index.permutation.PermutationIndex
import index.permutation.PivotSelector
import kotlin.random.Random
import tests.TestSupport.assertApproxEquals
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object IndexTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("pivot selector returns requested count deterministically with seed") {
                val dataset = listOf(
                    TestData.signature("id-1"),
                    TestData.signature("id-2"),
                    TestData.signature("id-3"),
                    TestData.signature("id-4")
                )
                val pivotsA = PivotSelector.randomPivots(dataset, 2, Random(42))
                val pivotsB = PivotSelector.randomPivots(dataset, 2, Random(42))
                assertEquals(2, pivotsA.size, "Should select requested number of pivots")
                assertEquals(pivotsA.map { it.id }, pivotsB.map { it.id }, "Seeded selection should be deterministic")
            },
            TestSupport.test("permutation index builds and returns top neighbors") {
                val dataset = listOf(
                    TestData.signature("id-a", tags = mapOf("div" to 2), styles = mapOf("margin" to 1), events = setOf("click")),
                    TestData.signature("id-b", tags = mapOf("div" to 2), styles = mapOf("margin" to 1), events = setOf("click")),
                    TestData.signature("id-c", tags = mapOf("ul" to 3), styles = mapOf("radius" to 1), events = setOf("submit"), cyclomatic = 4)
                )
                val pivots = PivotSelector.randomPivots(dataset, 2, Random(1))
                val index = PermutationIndex(pivots, ComponentDistance())
                index.build(dataset)

                val neighbors = index.querySimilar(dataset[0], k = 2, topN = 2)
                assertEquals(2, neighbors.size, "Should return requested topN neighbors")
                assertEquals("id-a", neighbors.first().first, "Exact query component should rank first in this tiny dataset")
                assertTrue(neighbors.first().second >= neighbors.last().second, "Scores should be sorted descending")
            },
            TestSupport.test("query similarity is normalized when k exceeds pivot count") {
                val dataset = listOf(
                    TestData.signature("id-a", tags = mapOf("div" to 1)),
                    TestData.signature("id-b", tags = mapOf("ul" to 1))
                )
                val pivots = PivotSelector.randomPivots(dataset, 2, Random(7))
                val index = PermutationIndex(pivots, ComponentDistance())
                index.build(dataset)

                val neighbors = index.querySimilar(dataset[0], k = 10, topN = 1)
                assertEquals("id-a", neighbors[0].first, "Self should remain top result")
                assertApproxEquals(1.0, neighbors[0].second, 1e-9, "Self-similarity should be 1.0 even when k exceeds pivot count")
            }
        )
    }
}
