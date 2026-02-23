import scanner.ExtractionMode
import tests.TestSupport
import tests.TestSupport.assertEquals

object MainCliTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("parseCliConfig defaults to hybrid mode") {
                val cfg = parseCliConfig(arrayOf("--repos", "/tmp/repos"))
                assertEquals("/tmp/repos", cfg?.reposDir?.path, "Repos path should parse")
                assertEquals(ExtractionMode.HYBRID, cfg?.mode, "Default mode should be hybrid")
                assertEquals(true, cfg?.domAstEnabled, "DOM AST should default on in hybrid mode")
                assertEquals(true, cfg?.cssAstEnabled, "CSS AST should default on in hybrid mode")
                assertEquals(true, cfg?.behaviorAstEnabled, "Behavior AST should default on in hybrid mode")
            },
            TestSupport.test("parseCliConfig parses hybrid mode") {
                val cfg = parseCliConfig(arrayOf("--repos", "/tmp/repos", "--mode", "hybrid"))
                assertEquals(ExtractionMode.HYBRID, cfg?.mode, "Mode should parse as hybrid")
                assertEquals(true, cfg?.domAstEnabled, "DOM AST should default on in hybrid mode")
                assertEquals(true, cfg?.cssAstEnabled, "CSS AST should default on in hybrid mode")
                assertEquals(true, cfg?.behaviorAstEnabled, "Behavior AST should default on in hybrid mode")
            },
            TestSupport.test("parseCliConfig parses audit output path") {
                val cfg = parseCliConfig(arrayOf("--repos", "/tmp/repos", "--mode", "hybrid", "--audit-out", "out/audit.csv"))
                assertEquals("out/audit.csv", cfg?.auditOut?.path, "Audit output path should parse")
            },
            TestSupport.test("parseCliConfig parses per-layer AST override flags") {
                val cfg = parseCliConfig(
                    arrayOf(
                        "--repos", "/tmp/repos",
                        "--mode", "hybrid",
                        "--dom-ast-enabled", "false",
                        "--css-ast-enabled", "true",
                        "--behavior-ast-enabled", "false"
                    )
                )
                assertEquals(false, cfg?.domAstEnabled, "DOM AST override should parse")
                assertEquals(true, cfg?.cssAstEnabled, "CSS AST override should parse")
                assertEquals(false, cfg?.behaviorAstEnabled, "Behavior AST override should parse")
            },
            TestSupport.test("parseCliConfig rejects invalid per-layer boolean") {
                val cfg = parseCliConfig(
                    arrayOf("--repos", "/tmp/repos", "--dom-ast-enabled", "maybe")
                )
                assertEquals(null, cfg, "Invalid boolean should return null config")
            },
            TestSupport.test("parseCliConfig rejects invalid mode") {
                val cfg = parseCliConfig(arrayOf("--repos", "/tmp/repos", "--mode", "invalid"))
                assertEquals(null, cfg, "Invalid mode should return null config")
            }
        )
    }
}
