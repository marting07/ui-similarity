package tests

import core.model.UiFramework
import extractor.ComponentSource
import extractor.ast.react.ReactAstCssFeatureExtractor
import tests.TestSupport.assertApproxEquals
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object ReactAstCssExtractorTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("react AST CSS extractor extracts tokens palette spacing and font buckets") {
                val extractor = ReactAstCssFeatureExtractor(command = "node scripts/react-css-ast-extract.mjs")
                val source = ComponentSource(
                    id = "react#ast-css",
                    framework = UiFramework.REACT,
                    templateCode = "",
                    styleCode = """
                        .box {
                          display: flex;
                          flex-direction: row;
                          align-items: center;
                          margin: 12px;
                          padding: 8px;
                          border-radius: 4px;
                          color: #112233;
                          background: #AABBCC;
                          font-size: 14px;
                          font-weight: 700;
                        }
                    """.trimIndent(),
                    logicCode = ""
                )
                val css = extractor.extractCssFeatures(source)
                assertTrue((css.styleTokens["layout:flex"] ?: 0) > 0, "Should detect flex token")
                assertEquals(2, css.palette.size, "Should detect two hex colors")
                assertApproxEquals(10.0, css.spacingMean, 0.01, "Spacing mean should match")
                assertEquals(1, css.fontSizeBuckets["sm"], "14px should map to sm")
            },
            TestSupport.test("react AST CSS extractor falls back when command fails") {
                val failures = mutableListOf<String>()
                val extractor = ReactAstCssFeatureExtractor(
                    command = "exit 1",
                    onFailure = { failures += it.reason }
                )
                val source = ComponentSource(
                    id = "react#css-fallback",
                    framework = UiFramework.REACT,
                    templateCode = "",
                    styleCode = ".x { margin: 5px; }",
                    logicCode = ""
                )
                val css = extractor.extractCssFeatures(source)
                assertTrue((css.styleTokens["margin"] ?: 0) > 0, "Fallback extractor should detect margin")
                assertTrue(failures.isNotEmpty(), "Failure telemetry should capture fallback reason")
            }
        )
    }
}
