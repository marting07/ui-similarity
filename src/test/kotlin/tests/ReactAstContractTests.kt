package tests

import corpus.RepoId
import scanner.CommandReactAstEngine
import scanner.ReactAstContractJson
import scanner.ReactAstScanRequest
import tests.TestSupport.assertContains
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object ReactAstContractTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("react AST contract encodes request as JSON") {
                val json = ReactAstContractJson.encodeRequest(
                    ReactAstScanRequest(
                        repoHost = "github.com",
                        repoOwner = "acme",
                        repoName = "repo",
                        repoRoot = "/tmp/repo"
                    )
                )
                assertContains(json, "\"repoHost\":\"github.com\"", "Request should include repoHost")
                assertContains(json, "\"repoOwner\":\"acme\"", "Request should include repoOwner")
                assertContains(json, "\"repoName\":\"repo\"", "Request should include repoName")
                assertContains(json, "\"repoRoot\":\"/tmp/repo\"", "Request should include repoRoot")
            },
            TestSupport.test("react AST contract decodes component response") {
                val responseJson = """
                    {
                      "status": "ok",
                      "components": [
                        {
                          "relativePath": "src/Card.tsx",
                          "exportName": "Card",
                          "templatePath": "src/Card.tsx",
                          "logicPath": "src/Card.tsx",
                          "stylePaths": ["src/Card.css"],
                          "inlineTemplateCode": "<div role=\\\"main\\\">X</div>",
                          "inlineStyleCodes": [".root { margin: 4px; }"]
                        }
                      ]
                    }
                """.trimIndent()
                val response = ReactAstContractJson.decodeResponse(responseJson)
                assertEquals("ok", response?.status, "Status should decode")
                assertEquals(1, response?.components?.size, "One component should decode")
                val component = response!!.components.first()
                assertEquals("src/Card.tsx", component.relativePath, "Relative path should decode")
                assertEquals("Card", component.exportName, "Export name should decode")
                assertEquals(1, component.stylePaths.size, "Style path array should decode")
                assertTrue(component.inlineTemplateCode?.contains("<div role") == true, "Inline template should decode")
            },
            TestSupport.test("command AST engine maps JSON output to ComponentSourceRef") {
                val command = "cat <<'JSON'\n{\"status\":\"ok\",\"components\":[{\"relativePath\":\"src/Widget.tsx\",\"exportName\":\"Widget\",\"stylePaths\":[\"src/Widget.css\"]}]}\nJSON"
                val engine = CommandReactAstEngine(command)
                val refs = engine.scanRepo(
                    RepoId("github.com", "acme", "repo"),
                    Fixtures.reactRepo
                )
                assertEquals(1, refs?.size, "Engine should return one mapped source ref")
                val ref = refs!!.first()
                assertEquals("Widget", ref.key.exportName, "Export name should map to key")
                assertEquals("src/Widget.tsx", ref.logicPath.toString().replace('\\', '/'), "Logic path should map")
                assertEquals(1, ref.stylePaths.size, "Style path list should map")
            },
            TestSupport.test("real node AST command extracts React exports from fixture repo") {
                val engine = CommandReactAstEngine("node scripts/react-ast-scan.mjs")
                val refs = engine.scanRepo(
                    RepoId("github.com", "acme", "edge-react"),
                    Fixtures.reactEdgeRepo
                )
                assertEquals(1, refs?.size, "Node AST command should extract one component")
                assertEquals("FancyCard", refs?.first()?.key?.exportName, "Default export function name should be parsed")
            }
        )
    }
}
