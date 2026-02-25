import corpus.ComponentCorpus
import corpus.ComponentKey
import corpus.ComponentRecord
import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import tests.Fixtures
import tests.TestData
import tests.TestSupport
import tests.TestSupport.assertEquals

object SplitTests {
    private fun record(idx: Int): ComponentRecord {
        val repoId = RepoId("github.com", "acme", "split-repo")
        val relativePath = "src/C$idx.tsx"
        val exportName = "C$idx"
        val key = ComponentKey(repoId, relativePath, exportName)
        val ref = ComponentSourceRef(
            key = key,
            framework = UiFramework.REACT,
            repoRoot = Fixtures.reactRepo,
            templatePath = java.nio.file.Path.of(relativePath),
            stylePaths = emptyList(),
            logicPath = java.nio.file.Path.of(relativePath)
        )
        return ComponentRecord(ref, TestData.signature(key.id))
    }

    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("createRandomSplit uses deterministic shuffle with seed") {
                val corpus = ComponentCorpus((1..10).map { record(it) })
                val splitA = createRandomSplit(corpus, 0.8, seed = 42)
                val splitB = createRandomSplit(corpus, 0.8, seed = 42)

                assertEquals(8, splitA.train.records.size, "80% of 10 should be in train")
                assertEquals(2, splitA.query.records.size, "Remaining records should be in query")
                assertEquals(
                    splitA.train.records.map { it.id },
                    splitB.train.records.map { it.id },
                    "Same seed should produce same train split"
                )
            },
            TestSupport.test("createRandomSplit handles 0.0 train ratio") {
                val corpus = ComponentCorpus((1..5).map { record(it) })
                val split = createRandomSplit(corpus, 0.0, seed = 1)
                assertEquals(0, split.train.records.size, "Train should be empty for 0 ratio")
                assertEquals(5, split.query.records.size, "All records should be query for 0 ratio")
            }
        )
    }
}
