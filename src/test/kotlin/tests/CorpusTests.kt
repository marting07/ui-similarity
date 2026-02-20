package tests

import corpus.ComponentCorpus
import corpus.ComponentKey
import corpus.ComponentRecord
import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object CorpusTests {
    private fun sourceRef(repoId: RepoId, path: String, exportName: String): ComponentSourceRef {
        return ComponentSourceRef(
            key = ComponentKey(repoId, path, exportName),
            framework = UiFramework.REACT,
            repoRoot = Fixtures.reactRepo,
            templatePath = java.nio.file.Path.of("src/Button.tsx"),
            stylePaths = emptyList(),
            logicPath = java.nio.file.Path.of("src/Button.tsx")
        )
    }

    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("component record enforces source/signature id consistency") {
                val repoId = RepoId("github.com", "acme", "repo")
                val ref = sourceRef(repoId, "src/A.tsx", "A")
                var threw = false
                try {
                    ComponentRecord(ref, TestData.signature(id = "different-id"))
                } catch (_: IllegalArgumentException) {
                    threw = true
                }
                assertTrue(threw, "ComponentRecord should reject mismatched ids")
            },
            TestSupport.test("component corpus byId and signatures helper methods") {
                val repoId = RepoId("github.com", "acme", "repo")
                val refA = sourceRef(repoId, "src/A.tsx", "A")
                val refB = sourceRef(repoId, "src/B.tsx", "B")
                val recA = ComponentRecord(refA, TestData.signature(refA.key.id))
                val recB = ComponentRecord(refB, TestData.signature(refB.key.id))
                val corpus = ComponentCorpus(listOf(recA, recB))
                assertEquals(2, corpus.byId.size, "byId should index all records")
                assertEquals(2, corpus.signatures().size, "signatures() should include all signatures")
                assertEquals(recA.id, corpus.byId[recA.id]?.id, "byId should resolve exact record")
            },
            TestSupport.test("component corpus forRepo filters correctly") {
                val repoA = RepoId("github.com", "acme", "repo-a")
                val repoB = RepoId("github.com", "acme", "repo-b")
                val recA = ComponentRecord(sourceRef(repoA, "src/A.tsx", "A"), TestData.signature("${repoA}:src/A.tsx#A"))
                val recB = ComponentRecord(sourceRef(repoB, "src/B.tsx", "B"), TestData.signature("${repoB}:src/B.tsx#B"))
                val corpus = ComponentCorpus(listOf(recA, recB))
                val onlyA = corpus.forRepo(repoA)
                assertEquals(1, onlyA.records.size, "forRepo should return only matching repo records")
                assertEquals(recA.id, onlyA.records.first().id, "Filtered record should belong to requested repo")
            }
        )
    }
}
