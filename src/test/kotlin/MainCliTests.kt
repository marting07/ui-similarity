import scanner.ExtractionMode
import tests.TestSupport
import tests.TestSupport.assertEquals

object MainCliTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("parseCliConfig defaults to simple mode") {
                val cfg = parseCliConfig(arrayOf("--repos", "/tmp/repos"))
                assertEquals("/tmp/repos", cfg?.reposDir?.path, "Repos path should parse")
                assertEquals(ExtractionMode.SIMPLE, cfg?.mode, "Default mode should be simple")
            },
            TestSupport.test("parseCliConfig parses hybrid mode") {
                val cfg = parseCliConfig(arrayOf("--repos", "/tmp/repos", "--mode", "hybrid"))
                assertEquals(ExtractionMode.HYBRID, cfg?.mode, "Mode should parse as hybrid")
            },
            TestSupport.test("parseCliConfig rejects invalid mode") {
                val cfg = parseCliConfig(arrayOf("--repos", "/tmp/repos", "--mode", "invalid"))
                assertEquals(null, cfg, "Invalid mode should return null config")
            }
        )
    }
}
