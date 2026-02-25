package persistence

import core.model.BehaviorFeatures
import core.model.ColorPoint
import core.model.ComponentSignature
import core.model.CssFeatures
import core.model.DomFeatures

object SignatureSnapshotMapper {
    fun toPersisted(signature: ComponentSignature): PersistedComponentSignature {
        return PersistedComponentSignature(
            id = signature.id,
            framework = signature.framework,
            domTagHistogram = signature.dom.tagHistogram,
            domLayoutPatterns = signature.dom.layoutPatterns,
            domDepth = signature.dom.depth,
            domAvgBranching = signature.dom.avgBranching,
            domRoleHistogram = signature.dom.roleHistogram,
            cssStyleTokens = signature.css.styleTokens,
            cssPalette = signature.css.palette.map { Triple(it.l, it.a, it.b) },
            cssSpacingMean = signature.css.spacingMean,
            cssSpacingStd = signature.css.spacingStd,
            cssFontFamilies = signature.css.fontFamilies,
            cssFontSizeBuckets = signature.css.fontSizeBuckets,
            behaviorEventTypes = signature.behavior.eventTypes,
            behaviorInteractionPatterns = signature.behavior.interactionPatterns,
            behaviorStatePatterns = signature.behavior.statePatterns,
            behaviorApiSignatures = signature.behavior.apiSignatures,
            behaviorCyclomatic = signature.behavior.cyclomatic,
            behaviorHandlerCount = signature.behavior.handlerCount,
            behaviorApiCallCount = signature.behavior.apiCallCount,
            behaviorConditionalCount = signature.behavior.conditionalCount
        )
    }

    fun toModel(persisted: PersistedComponentSignature): ComponentSignature {
        return ComponentSignature(
            id = persisted.id,
            framework = persisted.framework,
            dom = DomFeatures(
                tagHistogram = persisted.domTagHistogram,
                layoutPatterns = persisted.domLayoutPatterns,
                depth = persisted.domDepth,
                avgBranching = persisted.domAvgBranching,
                roleHistogram = persisted.domRoleHistogram
            ),
            css = CssFeatures(
                styleTokens = persisted.cssStyleTokens,
                palette = persisted.cssPalette.map { ColorPoint(l = it.first, a = it.second, b = it.third) },
                spacingMean = persisted.cssSpacingMean,
                spacingStd = persisted.cssSpacingStd,
                fontFamilies = persisted.cssFontFamilies,
                fontSizeBuckets = persisted.cssFontSizeBuckets
            ),
            behavior = BehaviorFeatures(
                eventTypes = persisted.behaviorEventTypes,
                interactionPatterns = persisted.behaviorInteractionPatterns,
                statePatterns = persisted.behaviorStatePatterns,
                apiSignatures = persisted.behaviorApiSignatures,
                cyclomatic = persisted.behaviorCyclomatic,
                handlerCount = persisted.behaviorHandlerCount,
                apiCallCount = persisted.behaviorApiCallCount,
                conditionalCount = persisted.behaviorConditionalCount
            )
        )
    }
}
