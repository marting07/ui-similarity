package tests

import core.model.UiFramework
import extractor.ComponentSignatureExtractor
import extractor.ComponentSource
import extractor.simple.SimpleBehaviorFeatureExtractor
import extractor.simple.SimpleCssFeatureExtractor
import extractor.simple.SimpleDomFeatureExtractor
import tests.TestSupport.assertApproxEquals
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object ExtractorTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("dom extractor finds tags roles and shape") {
                val source = ComponentSource(
                    id = "demo#dom",
                    framework = UiFramework.REACT,
                    templateCode = """
                        <div role="main">
                          <ul>
                            <li>One</li>
                            <li>Two</li>
                          </ul>
                        </div>
                    """.trimIndent(),
                    styleCode = "display: flex; flex-direction: column;",
                    logicCode = ""
                )
                val dom = SimpleDomFeatureExtractor().extractDomFeatures(source)
                assertEquals(1, dom.tagHistogram["div"], "Should count div tag")
                assertEquals(1, dom.tagHistogram["ul"], "Should count ul tag")
                assertEquals(2, dom.tagHistogram["li"], "Should count li tags")
                assertEquals(1, dom.roleHistogram["main"], "Should count role attribute")
                assertTrue(dom.layoutPatterns.contains("list-vertical"), "Should identify list layout hint")
                assertTrue(dom.depth >= 1, "Depth should be positive")
            },
            TestSupport.test("css extractor identifies tokens palette spacing and font buckets") {
                val source = ComponentSource(
                    id = "demo#css",
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
                val css = SimpleCssFeatureExtractor().extractCssFeatures(source)
                assertTrue((css.styleTokens["layout:flex"] ?: 0) > 0, "Should detect flex token")
                assertTrue((css.styleTokens["radius"] ?: 0) > 0, "Should detect radius token")
                assertEquals(2, css.palette.size, "Should extract two hex colors")
                assertApproxEquals(10.0, css.spacingMean, 0.01, "Spacing mean should average margin/padding")
                assertEquals(1, css.fontSizeBuckets["sm"], "14px should map to sm bucket")
            },
            TestSupport.test("behavior extractor identifies events state api and complexity") {
                val source = ComponentSource(
                    id = "demo#behavior",
                    framework = UiFramework.REACT,
                    templateCode = "",
                    styleCode = "",
                    logicCode = """
                        const [open, setOpen] = useState(false)
                        function onClick() {
                          if (open) {
                            axios.get('/x')
                          } else {
                            fetch('/y')
                          }
                          setOpen(!open)
                        }
                    """.trimIndent()
                )
                val behavior = SimpleBehaviorFeatureExtractor().extractBehaviorFeatures(source)
                assertTrue(behavior.eventTypes.contains("click"), "Should detect click event")
                assertTrue(behavior.statePatterns.contains("localState"), "Should detect local state")
                assertTrue(behavior.apiSignatures.any { it.startsWith("axios") }, "Should detect axios API usage")
                assertTrue(behavior.apiSignatures.any { it.startsWith("fetch") }, "Should detect fetch API usage")
                assertTrue(behavior.cyclomatic > 1, "Cyclomatic should increase with conditionals")
            },
            TestSupport.test("component signature extractor composes all layers") {
                val source = ComponentSource(
                    id = "demo#signature",
                    framework = UiFramework.VUE,
                    templateCode = "<div role=\"button\">X</div>",
                    styleCode = "font-size: 16px;",
                    logicCode = "onClick();"
                )
                val extractor = ComponentSignatureExtractor(
                    domExtractor = SimpleDomFeatureExtractor(),
                    cssExtractor = SimpleCssFeatureExtractor(),
                    behaviorExtractor = SimpleBehaviorFeatureExtractor()
                )
                val sig = extractor.extract(source)
                assertEquals("demo#signature", sig.id, "Signature id should be preserved")
                assertEquals(UiFramework.VUE, sig.framework, "Framework should be preserved")
                assertTrue(sig.dom.tagHistogram.isNotEmpty(), "DOM features should exist")
            }
        )
    }
}
