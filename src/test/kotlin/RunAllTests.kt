import tests.AngularAstContractTests
import tests.AngularAstBehaviorExtractorTests
import tests.AngularAstCssExtractorTests
import tests.AngularAstDomExtractorTests
import tests.AstModeSmokeTests
import tests.ExtractorTests
import tests.CorpusTests
import tests.HybridModeSmokeTests
import tests.IndexTests
import tests.IndexSnapshotIOTests
import tests.IndexSnapshotCompatibilityTests
import tests.PipelineSmokeTests
import tests.ProductionCliTests
import tests.ReactAstContractTests
import tests.ReactAstBehaviorExtractorTests
import tests.ReactAstCssExtractorTests
import tests.ReactAstDomExtractorTests
import tests.RepoSamplingServiceTests
import tests.ScannerAuditSummaryTests
import tests.ScannerTests
import tests.SimilarityTests
import tests.SourceLoaderTests
import tests.VueAstContractTests
import tests.VueAstBehaviorExtractorTests
import tests.VueAstCssExtractorTests
import tests.VueAstDomExtractorTests

fun main() {
    val suites = listOf(
        "MainCliTests" to MainCliTests.run(),
        "AstFailureSummaryTests" to AstFailureSummaryTests.run(),
        "ReactAstContractTests" to ReactAstContractTests.run(),
        "AngularAstContractTests" to AngularAstContractTests.run(),
        "VueAstContractTests" to VueAstContractTests.run(),
        "AngularAstBehaviorExtractorTests" to AngularAstBehaviorExtractorTests.run(),
        "AngularAstDomExtractorTests" to AngularAstDomExtractorTests.run(),
        "AngularAstCssExtractorTests" to AngularAstCssExtractorTests.run(),
        "VueAstBehaviorExtractorTests" to VueAstBehaviorExtractorTests.run(),
        "VueAstDomExtractorTests" to VueAstDomExtractorTests.run(),
        "VueAstCssExtractorTests" to VueAstCssExtractorTests.run(),
        "ReactAstDomExtractorTests" to ReactAstDomExtractorTests.run(),
        "ReactAstCssExtractorTests" to ReactAstCssExtractorTests.run(),
        "ReactAstBehaviorExtractorTests" to ReactAstBehaviorExtractorTests.run(),
        "AstModeSmokeTests" to AstModeSmokeTests.run(),
        "HybridModeSmokeTests" to HybridModeSmokeTests.run(),
        "ScannerAuditSummaryTests" to ScannerAuditSummaryTests.run(),
        "ScannerTests" to ScannerTests.run(),
        "SourceLoaderTests" to SourceLoaderTests.run(),
        "ExtractorTests" to ExtractorTests.run(),
        "SimilarityTests" to SimilarityTests.run(),
        "RepoSamplingServiceTests" to RepoSamplingServiceTests.run(),
        "CorpusTests" to CorpusTests.run(),
        "SplitTests" to SplitTests.run(),
        "IndexTests" to IndexTests.run(),
        "IndexSnapshotIOTests" to IndexSnapshotIOTests.run(),
        "IndexSnapshotCompatibilityTests" to IndexSnapshotCompatibilityTests.run(),
        "ProductionCliTests" to ProductionCliTests.run(),
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
