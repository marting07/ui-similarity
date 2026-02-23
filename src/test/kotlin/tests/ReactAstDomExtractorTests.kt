package tests

import core.model.UiFramework
import extractor.ComponentSource
import extractor.ast.react.ReactAstDomFeatureExtractor
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object ReactAstDomExtractorTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("react AST DOM extractor extracts tags roles and shape") {
                val extractor = ReactAstDomFeatureExtractor(command = "node scripts/react-dom-ast-extract.mjs")
                val source = ComponentSource(
                    id = "react#ast-dom",
                    framework = UiFramework.REACT,
                    templateCode = """
                        export function Card() {
                          return (
                            <div role="main" style={{ display: 'flex', flexDirection: 'column' }}>
                              <ul>
                                <li>One</li>
                                <li>Two</li>
                              </ul>
                            </div>
                          )
                        }
                    """.trimIndent(),
                    styleCode = "",
                    logicCode = ""
                )
                val dom = extractor.extractDomFeatures(source)
                assertEquals(1, dom.tagHistogram["div"], "Should count div")
                assertEquals(1, dom.tagHistogram["ul"], "Should count ul")
                assertEquals(2, dom.tagHistogram["li"], "Should count li")
                assertEquals(1, dom.roleHistogram["main"], "Should count role")
                assertTrue(dom.layoutPatterns.contains("list-vertical"), "Should infer list layout")
                assertTrue(dom.depth >= 2, "Depth should reflect nesting")
            },
            TestSupport.test("react AST DOM extractor falls back when command fails") {
                val failures = mutableListOf<String>()
                val extractor = ReactAstDomFeatureExtractor(command = "exit 1")
                val source = ComponentSource(
                    id = "react#fallback",
                    framework = UiFramework.REACT,
                    templateCode = "<div role=\"dialog\"><span>X</span></div>",
                    styleCode = "",
                    logicCode = ""
                )
                val extractorWithTelemetry = ReactAstDomFeatureExtractor(
                    command = "exit 1",
                    onFailure = { failures += it.reason }
                )
                val dom = extractor.extractDomFeatures(source)
                extractorWithTelemetry.extractDomFeatures(source)
                assertEquals(1, dom.tagHistogram["div"], "Fallback extractor should still produce div tag")
                assertEquals(1, dom.roleHistogram["dialog"], "Fallback extractor should still detect role")
                assertTrue(failures.isNotEmpty(), "Failure telemetry should capture fallback reason")
            }
        )
    }
}
