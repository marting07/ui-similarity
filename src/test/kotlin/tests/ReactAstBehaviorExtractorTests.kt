package tests

import core.model.UiFramework
import extractor.ComponentSource
import extractor.ast.react.ReactAstBehaviorFeatureExtractor
import tests.TestSupport.assertTrue

object ReactAstBehaviorExtractorTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("react AST behavior extractor extracts events state apis and complexity") {
                val extractor = ReactAstBehaviorFeatureExtractor(command = "node scripts/react-behavior-ast-extract.mjs")
                val source = ComponentSource(
                    id = "react#ast-behavior",
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
                val behavior = extractor.extractBehaviorFeatures(source)
                assertTrue(behavior.eventTypes.contains("click"), "Should detect click event")
                assertTrue(behavior.statePatterns.contains("localState"), "Should detect local state")
                assertTrue(behavior.apiSignatures.any { it.startsWith("axios") }, "Should detect axios usage")
                assertTrue(behavior.apiSignatures.any { it.startsWith("fetch") }, "Should detect fetch usage")
                assertTrue(behavior.cyclomatic > 1, "Cyclomatic should be > 1")
            },
            TestSupport.test("react AST behavior extractor falls back when command fails") {
                val failures = mutableListOf<String>()
                val extractor = ReactAstBehaviorFeatureExtractor(
                    command = "exit 1",
                    onFailure = { failures += it.reason }
                )
                val source = ComponentSource(
                    id = "react#behavior-fallback",
                    framework = UiFramework.REACT,
                    templateCode = "",
                    styleCode = "",
                    logicCode = "function onClick() { fetch('/x') }"
                )
                val behavior = extractor.extractBehaviorFeatures(source)
                assertTrue(behavior.eventTypes.contains("click"), "Fallback extractor should still detect click")
                assertTrue(failures.isNotEmpty(), "Failure telemetry should capture fallback reason")
            }
        )
    }
}
