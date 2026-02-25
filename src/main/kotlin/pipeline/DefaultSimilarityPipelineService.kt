package pipeline

import core.model.ComponentSignature
import core.model.UiFramework
import core.similarity.ComponentDistance
import corpus.ComponentCorpus
import corpus.ComponentRecord
import corpus.ComponentSourceRef
import corpus.RepoId
import extractor.BehaviorFeatureExtractor
import extractor.ComponentSignatureExtractor
import extractor.CssFeatureExtractor
import extractor.DomFeatureExtractor
import extractor.SourceLoader
import extractor.ast.angular.AngularAstBehaviorFeatureExtractor
import extractor.ast.angular.AngularAstCssFeatureExtractor
import extractor.ast.angular.AngularAstDomFeatureExtractor
import extractor.ast.react.ReactAstBehaviorFeatureExtractor
import extractor.ast.react.ReactAstCssFeatureExtractor
import extractor.ast.react.ReactAstDomFeatureExtractor
import extractor.ast.vue.VueAstBehaviorFeatureExtractor
import extractor.ast.vue.VueAstCssFeatureExtractor
import extractor.ast.vue.VueAstDomFeatureExtractor
import extractor.simple.SimpleBehaviorFeatureExtractor
import extractor.simple.SimpleCssFeatureExtractor
import extractor.simple.SimpleDomFeatureExtractor
import index.permutation.PermutationIndex
import index.permutation.Pivot
import index.permutation.PivotSelector
import persistence.PersistedPermutation
import persistence.PermutationIndexSnapshotV1
import persistence.SignatureSnapshotMapper
import persistence.SnapshotBuildConfig
import persistence.SnapshotMetadata
import scanner.CompositeRepoScanner
import scanner.ExtractionMode
import scanner.createFrameworkScanners
import java.io.File
import java.nio.file.Path
import kotlin.random.Random

class DefaultSimilarityPipelineService : SimilarityPipelineService {
    override fun buildIndex(request: BuildIndexRequest): BuildIndexResult {
        val mode = parseExtractionMode(request.extractionMode)
        val scanners = createFrameworkScanners(mode)
        val compositeScanner = CompositeRepoScanner(scanners)
        val extractor = createSignatureExtractor(
            domAstEnabled = request.domAstEnabled,
            cssAstEnabled = request.cssAstEnabled,
            behaviorAstEnabled = request.behaviorAstEnabled
        )

        val discoveredRepos = discoverRepoCandidates(File(request.reposRoot))
            .filter { frameworkAllowed(it.framework, request.frameworks) }
        val sampledRepos = request.sampling?.let { cfg ->
            val sampledIds = RepoSamplingService.sample(
                discoveredRepos.map { RepoCandidate(it.repoId, it.framework) },
                cfg
            ).map { it.repoId.toString() }.toSet()
            discoveredRepos.filter { it.repoId.toString() in sampledIds }
        } ?: discoveredRepos

        val sourceRefs = mutableListOf<ComponentSourceRef>()
        for (repo in sampledRepos) {
            sourceRefs += compositeScanner.scanRepo(repo.repoId, repo.rootPath)
        }

        val records = mutableListOf<ComponentRecord>()
        for (ref in sourceRefs) {
            val source = SourceLoader.load(ref)
            val signature = extractor.extract(source)
            records += ComponentRecord(ref, signature)
        }

        val corpus = ComponentCorpus(records)
        val signatures = corpus.signatures()
        if (signatures.isEmpty()) {
            return BuildIndexResult(
                scannedComponents = 0,
                corpus = corpus,
                sourceRefs = sourceRefs,
                snapshot = null
            )
        }

        val pivotCount = request.pivotCount.coerceAtLeast(1).coerceAtMost(signatures.size)
        val pivots = PivotSelector.randomPivots(signatures, pivotCount, Random(request.pivotSeed))
        val index = PermutationIndex(pivots, ComponentDistance())
        index.build(signatures)

        val snapshot = PermutationIndexSnapshotV1(
            metadata = SnapshotMetadata(
                createdAtEpochMs = System.currentTimeMillis(),
                componentCount = signatures.size,
                pivotCount = pivots.size,
                frameworkCounts = signatures.groupingBy { it.framework }.eachCount(),
                buildConfig = SnapshotBuildConfig(
                    extractionMode = mode.name.lowercase(),
                    domAstEnabled = request.domAstEnabled,
                    cssAstEnabled = request.cssAstEnabled,
                    behaviorAstEnabled = request.behaviorAstEnabled,
                    samplePercent = request.sampling?.percent,
                    sampleSeed = request.sampling?.seed,
                    sampleMode = request.sampling?.mode?.name?.lowercase()
                )
            ),
            pivotIds = pivots.map { it.id },
            records = signatures.map { sig ->
                val permutation = index.computePermutation(sig)
                PersistedPermutation(componentId = sig.id, orderedPivotIds = permutation.orderedPivotIds)
            },
            signatures = signatures.map { SignatureSnapshotMapper.toPersisted(it) }
        )

        return BuildIndexResult(
            scannedComponents = sourceRefs.size,
            corpus = corpus,
            sourceRefs = sourceRefs,
            snapshot = snapshot
        )
    }

    override fun query(request: QuerySimilarityRequest): QuerySimilarityResult {
        val scores = queryByPermutation(request.snapshot, request.componentId, request.topK, request.topN)
        return QuerySimilarityResult(
            queryComponentId = request.componentId,
            matches = scores.map { (id, score) -> SimilarityMatch(id, score) }
        )
    }

    override fun listSignatures(snapshot: PermutationIndexSnapshotV1): List<ComponentSignature> {
        return snapshot.signatures.map { SignatureSnapshotMapper.toModel(it) }
    }

    fun queryBySignature(
        snapshot: PermutationIndexSnapshotV1,
        querySignature: ComponentSignature,
        topK: Int,
        topN: Int
    ): QuerySimilarityResult {
        val signaturesById = snapshot.signatures
            .map { SignatureSnapshotMapper.toModel(it) }
            .associateBy { it.id }
        val pivots = snapshot.pivotIds.mapNotNull { pivotId ->
            signaturesById[pivotId]?.let { Pivot(id = pivotId, signature = it) }
        }
        require(pivots.isNotEmpty()) { "Snapshot does not contain pivot signatures; cannot query from raw file." }

        val index = PermutationIndex(pivots, ComponentDistance())
        val dataset = signaturesById.values.toList()
        index.build(dataset)
        val matches = index.querySimilar(querySignature, k = topK, topN = topN)
            .map { (id, sim) -> SimilarityMatch(id, sim) }
        return QuerySimilarityResult(queryComponentId = querySignature.id, matches = matches)
    }

    private fun queryByPermutation(
        snapshot: PermutationIndexSnapshotV1,
        componentId: String,
        topK: Int,
        topN: Int
    ): List<Pair<String, Double>> {
        val byId = snapshot.records.associateBy { it.componentId }
        val query = byId[componentId] ?: error("Component not found in index: $componentId")
        val effectiveK = topK.coerceAtLeast(0).coerceAtMost(snapshot.pivotIds.size)
        val querySet = query.orderedPivotIds.take(effectiveK).toSet()
        val scores = snapshot.records.map { rec ->
            val candidateSet = rec.orderedPivotIds.take(effectiveK).toSet()
            val inter = querySet.intersect(candidateSet).size.toDouble()
            val sim = if (effectiveK > 0) inter / effectiveK.toDouble() else 0.0
            rec.componentId to sim
        }
        return scores.sortedByDescending { it.second }.take(topN)
    }

    private fun createSignatureExtractor(
        domAstEnabled: Boolean,
        cssAstEnabled: Boolean,
        behaviorAstEnabled: Boolean
    ): ComponentSignatureExtractor {
        val simpleDom = SimpleDomFeatureExtractor()
        val simpleCss = SimpleCssFeatureExtractor()
        val simpleBehavior = SimpleBehaviorFeatureExtractor()

        val reactDom = ReactAstDomFeatureExtractor(fallback = simpleDom)
        val angularDom = AngularAstDomFeatureExtractor(fallback = simpleDom)
        val vueDom = VueAstDomFeatureExtractor(fallback = simpleDom)
        val reactCss = ReactAstCssFeatureExtractor(fallback = simpleCss)
        val angularCss = AngularAstCssFeatureExtractor(fallback = simpleCss)
        val vueCss = VueAstCssFeatureExtractor(fallback = simpleCss)
        val reactBehavior = ReactAstBehaviorFeatureExtractor(fallback = simpleBehavior)
        val angularBehavior = AngularAstBehaviorFeatureExtractor(fallback = simpleBehavior)
        val vueBehavior = VueAstBehaviorFeatureExtractor(fallback = simpleBehavior)

        val domExtractor: DomFeatureExtractor = if (!domAstEnabled) {
            simpleDom
        } else {
            object : DomFeatureExtractor {
                override fun extractDomFeatures(source: extractor.ComponentSource): core.model.DomFeatures {
                    return when (source.framework) {
                        UiFramework.REACT -> reactDom.extractDomFeatures(source)
                        UiFramework.ANGULAR -> angularDom.extractDomFeatures(source)
                        UiFramework.VUE -> vueDom.extractDomFeatures(source)
                        else -> simpleDom.extractDomFeatures(source)
                    }
                }
            }
        }

        val cssExtractor: CssFeatureExtractor = if (!cssAstEnabled) {
            simpleCss
        } else {
            object : CssFeatureExtractor {
                override fun extractCssFeatures(source: extractor.ComponentSource): core.model.CssFeatures {
                    return when (source.framework) {
                        UiFramework.REACT -> reactCss.extractCssFeatures(source)
                        UiFramework.ANGULAR -> angularCss.extractCssFeatures(source)
                        UiFramework.VUE -> vueCss.extractCssFeatures(source)
                        else -> simpleCss.extractCssFeatures(source)
                    }
                }
            }
        }

        val behaviorExtractor: BehaviorFeatureExtractor = if (!behaviorAstEnabled) {
            simpleBehavior
        } else {
            object : BehaviorFeatureExtractor {
                override fun extractBehaviorFeatures(source: extractor.ComponentSource): core.model.BehaviorFeatures {
                    return when (source.framework) {
                        UiFramework.REACT -> reactBehavior.extractBehaviorFeatures(source)
                        UiFramework.ANGULAR -> angularBehavior.extractBehaviorFeatures(source)
                        UiFramework.VUE -> vueBehavior.extractBehaviorFeatures(source)
                        else -> simpleBehavior.extractBehaviorFeatures(source)
                    }
                }
            }
        }

        return ComponentSignatureExtractor(
            domExtractor = domExtractor,
            cssExtractor = cssExtractor,
            behaviorExtractor = behaviorExtractor
        )
    }

    private fun parseExtractionMode(raw: String): ExtractionMode {
        return ExtractionMode.fromCli(raw.lowercase())
    }

    private fun frameworkAllowed(framework: UiFramework, allowed: Set<UiFramework>): Boolean {
        return framework == UiFramework.UNKNOWN || framework in allowed
    }
}

private data class RepoCandidateWithPath(
    val repoId: RepoId,
    val framework: UiFramework,
    val rootPath: Path
)

private fun discoverRepoCandidates(reposDir: File): List<RepoCandidateWithPath> {
    if (!reposDir.exists() || !reposDir.isDirectory) return emptyList()
    val frameworkDirs = setOf("react", "angular", "vue")
    val visitedRoots = mutableSetOf<Path>()
    val out = mutableListOf<RepoCandidateWithPath>()

    reposDir.walkTopDown()
        .filter { it.isDirectory && it.name == ".git" }
        .forEach { gitDir ->
            val repoRoot = gitDir.parentFile ?: return@forEach
            if (!visitedRoots.add(repoRoot.toPath())) return@forEach

            val parts = repoRoot.relativeTo(reposDir).path.split(File.separator).filter { it.isNotEmpty() }
            if (parts.size < 3) return@forEach

            val inferredFramework = when (parts[0]) {
                "react" -> UiFramework.REACT
                "angular" -> UiFramework.ANGULAR
                "vue" -> UiFramework.VUE
                else -> UiFramework.UNKNOWN
            }
            val repoId = if (parts[0] in frameworkDirs) {
                RepoId("github.com", parts[1], parts.drop(2).joinToString("/"))
            } else {
                RepoId(parts[0], parts[1], parts.drop(2).joinToString("/"))
            }
            out += RepoCandidateWithPath(repoId = repoId, framework = inferredFramework, rootPath = repoRoot.toPath())
        }
    return out
}
