package tests

import corpus.ComponentCorpus
import corpus.ComponentRecord
import corpus.RepoId
import core.similarity.ComponentDistance
import extractor.ComponentSignatureExtractor
import extractor.SourceLoader
import extractor.simple.SimpleBehaviorFeatureExtractor
import extractor.simple.SimpleCssFeatureExtractor
import extractor.simple.SimpleDomFeatureExtractor
import index.permutation.PermutationIndex
import index.permutation.PivotSelector
import scanner.AngularRepoScanner
import scanner.CompositeRepoScanner
import scanner.ReactRepoScanner
import scanner.VueRepoScanner
import kotlin.random.Random
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object PipelineSmokeTests {
    private fun splitCorpus(corpus: ComponentCorpus, trainRatio: Double, seed: Int): Pair<ComponentCorpus, ComponentCorpus> {
        val shuffled = corpus.records.shuffled(Random(seed))
        val trainSize = (shuffled.size * trainRatio).toInt()
        return ComponentCorpus(shuffled.take(trainSize)) to ComponentCorpus(shuffled.drop(trainSize))
    }

    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("pipeline smoke test on tiny fixtures") {
                val scanners = listOf(ReactRepoScanner(), AngularRepoScanner(), VueRepoScanner())
                val compositeScanner = CompositeRepoScanner(scanners)

                val refs = mutableListOf<corpus.ComponentSourceRef>()
                refs += compositeScanner.scanRepo(RepoId("github.com", "acme", "sample-react"), Fixtures.reactRepo)
                refs += compositeScanner.scanRepo(RepoId("github.com", "acme", "sample-angular"), Fixtures.angularRepo)
                refs += compositeScanner.scanRepo(RepoId("github.com", "acme", "sample-vue"), Fixtures.vueRepo)

                assertEquals(3, refs.size, "Expected one component from each fixture repo")

                val extractor = ComponentSignatureExtractor(
                    domExtractor = SimpleDomFeatureExtractor(),
                    cssExtractor = SimpleCssFeatureExtractor(),
                    behaviorExtractor = SimpleBehaviorFeatureExtractor()
                )

                val records = refs.map { ref ->
                    val source = SourceLoader.load(ref)
                    val signature = extractor.extract(source)
                    ComponentRecord(ref, signature)
                }

                val corpus = ComponentCorpus(records)
                assertEquals(3, corpus.records.size, "Corpus should contain all extracted components")

                val (train, query) = splitCorpus(corpus, trainRatio = 2.0 / 3.0, seed = 123)
                assertTrue(train.records.isNotEmpty(), "Train split should not be empty")
                assertTrue(query.records.isNotEmpty(), "Query split should not be empty")

                val pivots = PivotSelector.randomPivots(train.signatures(), train.records.size, Random(123))
                val index = PermutationIndex(pivots, ComponentDistance())
                index.build(train.signatures())

                for (queryRecord in query.records) {
                    val neighbors = index.querySimilar(queryRecord.signature, k = 8, topN = 3)
                    assertTrue(neighbors.isNotEmpty(), "Each query should return at least one neighbor")
                    assertTrue(neighbors.all { it.second in 0.0..1.0 }, "Similarity scores should stay in [0,1]")
                }
            }
        )
    }
}
