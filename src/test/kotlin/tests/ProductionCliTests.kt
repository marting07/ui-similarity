package tests

import cli.main as cliMain
import persistence.IndexSnapshotIO
import java.io.File
import java.nio.file.Files

object ProductionCliTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("production CLI scan-index writes snapshot and inspect/validate outputs") {
                val workspace = Files.createTempDirectory("cli-prod-test-").toFile()
                try {
                    val reposRoot = File(workspace, "repos")
                    createSampleReactRepo(reposRoot)

                    val snapshotFile = File(workspace, "out/index.json")
                    val inspectJson = File(workspace, "out/inspect.json")
                    val validateJson = File(workspace, "out/validate.json")

                    cliMain(
                        arrayOf(
                            "scan-index",
                            "--repos", reposRoot.absolutePath,
                            "--out", snapshotFile.absolutePath,
                            "--mode", "simple",
                            "--pivot-count", "2"
                        )
                    )

                    TestSupport.assertTrue(snapshotFile.exists(), "scan-index should create snapshot file")
                    val snapshot = IndexSnapshotIO.load(snapshotFile)
                    TestSupport.assertTrue(snapshot.metadata.componentCount >= 2, "Expected at least 2 indexed components")

                    cliMain(arrayOf("inspect", "--index-file", snapshotFile.absolutePath, "--json-out", inspectJson.absolutePath))
                    TestSupport.assertTrue(inspectJson.exists(), "inspect should write --json-out file")
                    TestSupport.assertContains(inspectJson.readText(), "\"component_count\"", "inspect json should contain component_count")

                    cliMain(arrayOf("validate", "--index-file", snapshotFile.absolutePath, "--json-out", validateJson.absolutePath))
                    TestSupport.assertTrue(validateJson.exists(), "validate should write --json-out file")
                    TestSupport.assertContains(validateJson.readText(), "\"valid\":true", "validate json should report valid=true")
                    TestSupport.assertContains(validateJson.readText(), "\"load_supported\":true", "validate json should include compatibility")
                } finally {
                    workspace.deleteRecursively()
                }
            },
            TestSupport.test("production CLI query works for component-id and query-file") {
                val workspace = Files.createTempDirectory("cli-prod-query-test-").toFile()
                try {
                    val reposRoot = File(workspace, "repos")
                    val componentFile = createSampleReactRepo(reposRoot)
                    val snapshotFile = File(workspace, "out/index.json")
                    cliMain(
                        arrayOf(
                            "scan-index",
                            "--repos", reposRoot.absolutePath,
                            "--out", snapshotFile.absolutePath,
                            "--mode", "simple",
                            "--pivot-count", "2"
                        )
                    )
                    val snapshot = IndexSnapshotIO.load(snapshotFile)
                    val id = snapshot.records.first().componentId

                    val queryByIdJson = File(workspace, "out/query-id.json")
                    cliMain(
                        arrayOf(
                            "query",
                            "--index-file", snapshotFile.absolutePath,
                            "--component-id", id,
                            "--top-k", "2",
                            "--top-n", "2",
                            "--json-out", queryByIdJson.absolutePath
                        )
                    )
                    TestSupport.assertContains(queryByIdJson.readText(), "\"matches\"", "query by id should return matches")

                    val queryByFileJson = File(workspace, "out/query-file.json")
                    cliMain(
                        arrayOf(
                            "query",
                            "--index-file", snapshotFile.absolutePath,
                            "--query-file", componentFile.absolutePath,
                            "--query-framework", "react",
                            "--top-k", "2",
                            "--top-n", "2",
                            "--json-out", queryByFileJson.absolutePath
                        )
                    )
                    TestSupport.assertContains(queryByFileJson.readText(), "\"matches\"", "query by file should return matches")
                } finally {
                    workspace.deleteRecursively()
                }
            }
        )
    }

    private fun createSampleReactRepo(reposRoot: File): File {
        val repo = File(reposRoot, "react/acme/sample-react")
        val git = File(repo, ".git")
        val srcDir = File(repo, "src")
        git.mkdirs()
        srcDir.mkdirs()
        File(repo, "package.json").writeText("""{"dependencies":{"react":"18.0.0"}}""")

        val a = File(srcDir, "Button.tsx")
        a.writeText(
            """
            import React from "react"
            export function Button() {
              return <button className="btn">OK</button>
            }
            """.trimIndent()
        )
        File(srcDir, "Button.css").writeText(".btn { margin: 8px; }")

        val b = File(srcDir, "Card.tsx")
        b.writeText(
            """
            export const Card = () => {
              return <div role="region">Card</div>
            }
            """.trimIndent()
        )
        return a
    }
}
