import tests.ExtractorTests
import tests.CorpusTests
import tests.IndexTests
import tests.PipelineSmokeTests
import tests.ScannerTests
import tests.SimilarityTests
import tests.SourceLoaderTests

fun main() {
    val suites = listOf(
        "ScannerTests" to ScannerTests.run(),
        "SourceLoaderTests" to SourceLoaderTests.run(),
        "ExtractorTests" to ExtractorTests.run(),
        "SimilarityTests" to SimilarityTests.run(),
        "CorpusTests" to CorpusTests.run(),
        "SplitTests" to SplitTests.run(),
        "IndexTests" to IndexTests.run(),
        "PipelineSmokeTests" to PipelineSmokeTests.run()
    )

    var failed = 0
    var total = 0
    for ((suiteName, results) in suites) {
        println("\\n[$suiteName]")
        for ((name, error) in results) {
            total++
            if (error == null) {
                println("  PASS $name")
            } else {
                failed++
                println("  FAIL $name")
                println("    ${error.message}")
            }
        }
    }

    println("\\nSummary: ${total - failed}/$total passed")
    if (failed > 0) {
        throw IllegalStateException("$failed test(s) failed")
    }
}
