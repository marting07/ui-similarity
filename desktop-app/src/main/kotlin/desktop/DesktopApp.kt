package desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import core.model.UiFramework
import persistence.IndexSnapshotIO
import persistence.PermutationIndexSnapshotV1
import pipeline.BuildIndexRequest
import pipeline.DefaultSimilarityPipelineService
import pipeline.QuerySimilarityRequest
import pipeline.RepoSamplingMode
import pipeline.SamplingConfig
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "UI Similarity Experimentation Desktop"
    ) {
        MaterialTheme {
            AppContent()
        }
    }
}

@Composable
private fun AppContent() {
    val service = remember { DefaultSimilarityPipelineService() }
    val logs = remember { mutableStateListOf<String>() }

    var reposPath by remember { mutableStateOf("") }
    var indexPath by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("hybrid") }
    var samplePercentText by remember { mutableStateOf("20") }
    var sampleSeedText by remember { mutableStateOf("42") }
    var sampleMode by remember { mutableStateOf("global") }
    var topKText by remember { mutableStateOf("10") }
    var topNText by remember { mutableStateOf("10") }
    var componentId by remember { mutableStateOf("") }
    var frameworkFilter by remember { mutableStateOf("react,angular,vue") }
    var metadataText by remember { mutableStateOf("No index loaded.") }
    var queryResultsText by remember { mutableStateOf("") }
    var loadedSnapshot by remember { mutableStateOf<PermutationIndexSnapshotV1?>(null) }

    val samplePercent = samplePercentText.toIntOrNull() ?: 0
    val estimatedRepos = estimateRepoSampleCount(reposPath, samplePercent)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Workspace / Corpus")
        OutlinedTextField(
            value = reposPath,
            onValueChange = { reposPath = it },
            label = { Text("Repos Root") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                chooseFile(open = false, dirOnly = true)?.let { reposPath = it.absolutePath }
            }) { Text("Browse Repos") }
            Text("Estimated sampled repos: $estimatedRepos")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = samplePercentText, onValueChange = { samplePercentText = it }, label = { Text("Sample %") })
            OutlinedTextField(value = sampleSeedText, onValueChange = { sampleSeedText = it }, label = { Text("Sample Seed") })
            OutlinedTextField(value = sampleMode, onValueChange = { sampleMode = it }, label = { Text("Sample Mode (global|stratified-framework)") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Mode (simple|ast|hybrid)") })
            OutlinedTextField(value = frameworkFilter, onValueChange = { frameworkFilter = it }, label = { Text("Frameworks (csv)") })
        }

        Text("Index")
        OutlinedTextField(
            value = indexPath,
            onValueChange = { indexPath = it },
            label = { Text("Index File") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                chooseFile(open = false, dirOnly = false)?.let { indexPath = it.absolutePath }
            }) { Text("Save As...") }
            Button(onClick = {
                chooseFile(open = true, dirOnly = false)?.let { file ->
                    indexPath = file.absolutePath
                    try {
                        val snapshot = IndexSnapshotIO.load(file)
                        loadedSnapshot = snapshot
                        metadataText = formatMetadata(snapshot)
                        logs += "Loaded index: ${file.absolutePath}"
                    } catch (t: Throwable) {
                        logs += "Load failed: ${t.message}"
                    }
                }
            }) { Text("Open Index...") }
            Button(onClick = {
                try {
                    val percent = samplePercentText.toInt()
                    val seed = sampleSeedText.toInt()
                    val req = BuildIndexRequest(
                        reposRoot = reposPath,
                        extractionMode = mode,
                        domAstEnabled = mode != "simple",
                        cssAstEnabled = mode != "simple",
                        behaviorAstEnabled = mode != "simple",
                        frameworks = parseFrameworks(frameworkFilter),
                        sampling = SamplingConfig(
                            percent = percent,
                            seed = seed,
                            mode = if (sampleMode == "stratified-framework") {
                                RepoSamplingMode.STRATIFIED_FRAMEWORK
                            } else {
                                RepoSamplingMode.GLOBAL
                            }
                        )
                    )
                    val result = service.buildIndex(req)
                    val snapshot = result.snapshot ?: error("No components indexed")
                    if (indexPath.isBlank()) error("Choose an index file path")
                    IndexSnapshotIO.save(File(indexPath), snapshot)
                    loadedSnapshot = snapshot
                    metadataText = formatMetadata(snapshot)
                    logs += "Index built. components=${snapshot.metadata.componentCount} pivots=${snapshot.metadata.pivotCount}"
                    logs += "Index saved to $indexPath"
                } catch (t: Throwable) {
                    logs += "Build failed: ${t.message}"
                }
            }) { Text("Build Index") }
        }
        Text(metadataText)

        Text("Query")
        OutlinedTextField(
            value = componentId,
            onValueChange = { componentId = it },
            label = { Text("Component ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = topKText, onValueChange = { topKText = it }, label = { Text("Top K") })
            OutlinedTextField(value = topNText, onValueChange = { topNText = it }, label = { Text("Top N") })
            Button(onClick = {
                try {
                    val snapshot = loadedSnapshot ?: error("Load or build an index first")
                    val res = service.query(
                        QuerySimilarityRequest(
                            snapshot = snapshot,
                            componentId = componentId,
                            topK = topKText.toInt(),
                            topN = topNText.toInt()
                        )
                    )
                    queryResultsText = buildString {
                        appendLine("Query: ${res.queryComponentId}")
                        res.matches.forEachIndexed { idx, m ->
                            appendLine("${idx + 1}. ${m.componentId} score=${"%.4f".format(m.similarity)}")
                        }
                    }
                } catch (t: Throwable) {
                    logs += "Query failed: ${t.message}"
                }
            }) { Text("Run Query") }
        }
        Text(queryResultsText)

        Text("Run Logs / Telemetry")
        Text(logs.joinToString("\n"))
    }
}

private fun parseFrameworks(raw: String): Set<UiFramework> {
    return raw.split(",").mapNotNull { token ->
        when (token.trim().lowercase()) {
            "react" -> UiFramework.REACT
            "angular" -> UiFramework.ANGULAR
            "vue" -> UiFramework.VUE
            else -> null
        }
    }.toSet().ifEmpty { setOf(UiFramework.REACT, UiFramework.ANGULAR, UiFramework.VUE) }
}

private fun formatMetadata(snapshot: PermutationIndexSnapshotV1): String {
    val frameworks = snapshot.metadata.frameworkCounts.entries.joinToString(", ") {
        "${it.key.name.lowercase()}=${it.value}"
    }
    return "Index metadata: components=${snapshot.metadata.componentCount}, pivots=${snapshot.metadata.pivotCount}, frameworks=$frameworks"
}

private fun estimateRepoSampleCount(reposPath: String, percent: Int): Int {
    if (reposPath.isBlank()) return 0
    val root = File(reposPath)
    if (!root.exists() || !root.isDirectory) return 0
    val repos = root.walkTopDown().count { it.isDirectory && it.name == ".git" }
    val p = percent.coerceIn(1, 100)
    if (repos == 0) return 0
    return kotlin.math.ceil(repos * (p / 100.0)).toInt().coerceAtLeast(1)
}

private fun chooseFile(open: Boolean, dirOnly: Boolean): File? {
    val dialog = FileDialog(null as Frame?, if (open) "Open" else "Save", if (open) FileDialog.LOAD else FileDialog.SAVE)
    dialog.isVisible = true
    val file = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    val selected = File(dir, file)
    if (dirOnly) return if (selected.isDirectory) selected else selected.parentFile
    return selected
}
