package tests

import core.model.UiFramework
import extractor.ComponentSource
import extractor.ast.angular.AngularAstBehaviorFeatureExtractor
import tests.TestSupport.assertTrue

object AngularAstBehaviorExtractorTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("angular AST behavior extractor extracts events state apis and complexity") {
                val extractor = AngularAstBehaviorFeatureExtractor(command = "node scripts/angular-behavior-ast-extract.mjs")
                val source = ComponentSource(
                    id = "angular#ast-behavior",
                    framework = UiFramework.ANGULAR,
                    templateCode = "",
                    styleCode = "",
                    logicCode = """
                        let open = false
                        function onClick() {
                          if (open) {
                            axios.get('/x')
                          } else {
                            fetch('/y')
                          }
                          open = !open
                        }
                    """.trimIndent()
                )
                val behavior = extractor.extractBehaviorFeatures(source)
                assertTrue(behavior.eventTypes.contains("click"), "Should detect click event")
                assertTrue(behavior.apiSignatures.any { it.startsWith("axios") }, "Should detect axios usage")
                assertTrue(behavior.apiSignatures.any { it.startsWith("fetch") }, "Should detect fetch usage")
                assertTrue(behavior.cyclomatic > 1, "Cyclomatic should be > 1")
            },
            TestSupport.test("angular AST behavior extractor falls back when command fails") {
                val failures = mutableListOf<String>()
                val extractor = AngularAstBehaviorFeatureExtractor(
                    command = "exit 1",
                    onFailure = { failures += it.reason }
                )
                val source = ComponentSource(
                    id = "angular#behavior-fallback",
                    framework = UiFramework.ANGULAR,
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

