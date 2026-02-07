import corpus.ComponentCorpus
import corpus.ComponentRecord
import corpus.ComponentSourceRef
import corpus.RepoId
import corpus.CorpusSplit
import core.model.UiFramework
import core.similarity.ComponentDistance
import extractor.ComponentSignatureExtractor
import extractor.SourceLoader
import extractor.simple.SimpleBehaviorFeatureExtractor
import extractor.simple.SimpleCssFeatureExtractor
import extractor.simple.SimpleDomFeatureExtractor
import index.permutation.PivotSelector
import index.permutation.PermutationIndex
import scanner.AngularRepoScanner
import scanner.CompositeRepoScanner
import scanner.ReactRepoScanner
import scanner.VueRepoScanner
import java.io.File
import kotlin.random.Random

/**
 * Demonstration entry point for the UI similarity pipeline.  It expects
 * a directory containing cloned repositories (each in its own subfolder)
 * and runs the full pipeline: scanning, feature extraction, corpus
 * construction, index building and similarity queries.  The results are
 * printed to standard output.
 *
 * Usage: `kt run MainKt --repos /data/repos`  (after compiling via Gradle)
 */
fun main(args: Array<String>) {
    if (args.size < 2 || args[0] != "--repos") {
        println("Usage: MainKt --repos <path-to-repos>")
        return
    }
    val reposDir = File(args[1])
    if (!reposDir.exists() || !reposDir.isDirectory) {
        println("Repositories directory not found: ${reposDir.absolutePath}")
        return
    }
    // Set up scanners for each framework
    val scanners = listOf(ReactRepoScanner(), AngularRepoScanner(), VueRepoScanner())
    val compositeScanner = CompositeRepoScanner(scanners)
    val sourceRefs = mutableListOf<ComponentSourceRef>()
    // Iterate over subdirectories in the repos directory
    for (repoFolder in reposDir.listFiles() ?: emptyArray()) {
        if (repoFolder.isDirectory) {
            // Derive a RepoId from the folder structure: host/owner/name
            val parts = repoFolder.relativeTo(reposDir).path.split(File.separator).filter { it.isNotEmpty() }
            if (parts.size >= 3) {
                val repoId = RepoId(parts[0], parts[1], parts.drop(2).joinToString("/"))
                val refs = compositeScanner.scanRepo(repoId, repoFolder.toPath())
                println("Scanned ${refs.size} components from ${repoId}")
                sourceRefs += refs
            }
        }
    }
    // Feature extractors
    val extractor = ComponentSignatureExtractor(
        domExtractor = SimpleDomFeatureExtractor(),
        cssExtractor = SimpleCssFeatureExtractor(),
        behaviorExtractor = SimpleBehaviorFeatureExtractor()
    )
    // Extract signatures and build records
    val records = mutableListOf<ComponentRecord>()
    for (ref in sourceRefs) {
        try {
            val source = SourceLoader.load(ref)
            val signature = extractor.extract(source)
            records += ComponentRecord(ref, signature)
        } catch (e: Exception) {
            println("Failed to extract features for ${ref.key.id}: ${e.message}")
        }
    }
    val corpus = ComponentCorpus(records)
    println("Total components processed: ${corpus.records.size}")
    // Create train/query split (80/20)
    val split = createRandomSplit(corpus, 0.8, seed = 42)
    println("Train size: ${split.train.records.size}, Query size: ${split.query.records.size}")
    // Build permutation index on training set
    val distance = ComponentDistance()
    val pivotCount = 16
    val pivots = PivotSelector.randomPivots(split.train.signatures(), pivotCount, Random(42))
    val index = PermutationIndex(pivots, distance)
    index.build(split.train.signatures())
    // Query with each component in the query set and print top 5 similar IDs and scores
    for (record in split.query.records) {
        val neighbors = index.querySimilar(record.signature, k = 8, topN = 5)
        println("Query: ${record.id}")
        neighbors.forEach { (id, score) -> println("  $id: ${String.format("%.2f", score)}") }
    }
}

/**
 * Create a random train/query split from a corpus.  The [trainRatio]
 * determines the fraction of records placed in the training set.  A
 * [seed] controls the random shuffle for reproducibility.
 */
fun createRandomSplit(corpus: ComponentCorpus, trainRatio: Double, seed: Int): CorpusSplit {
    val shuffled = corpus.records.shuffled(Random(seed))
    val trainSize = (shuffled.size * trainRatio).toInt()
    val train = ComponentCorpus(shuffled.take(trainSize))
    val query = ComponentCorpus(shuffled.drop(trainSize))
    return CorpusSplit(train, query)
}