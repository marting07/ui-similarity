package tests

import core.model.UiFramework
import extractor.ComponentSource
import extractor.ast.vue.VueAstCssFeatureExtractor
import tests.TestSupport.assertApproxEquals
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object VueAstCssExtractorTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("vue AST CSS extractor extracts tokens palette spacing and font buckets") {
                val extractor = VueAstCssFeatureExtractor(command = "node scripts/vue-css-ast-extract.mjs")
                val source = ComponentSource(
                    id = "vue#ast-css",
                    framework = UiFramework.VUE,
                    templateCode = "",
                    styleCode = """
                        .card {
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
            TestSupport.test("vue AST CSS extractor falls back when command fails") {
                val failures = mutableListOf<String>()
                val extractor = VueAstCssFeatureExtractor(
                    command = "exit 1",
                    onFailure = { failures += it.reason }
                )
                val source = ComponentSource(
                    id = "vue#css-fallback",
                    framework = UiFramework.VUE,
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

