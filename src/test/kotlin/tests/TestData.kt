package tests

import core.model.BehaviorFeatures
import core.model.ComponentSignature
import core.model.CssFeatures
import core.model.DomFeatures
import core.model.UiFramework

object TestData {
    fun signature(
        id: String,
        framework: UiFramework = UiFramework.REACT,
        tags: Map<String, Int> = mapOf("div" to 1),
        styles: Map<String, Int> = emptyMap(),
        events: Set<String> = emptySet(),
        cyclomatic: Int = 1
    ): ComponentSignature {
        return ComponentSignature(
            id = id,
            framework = framework,
            dom = DomFeatures(
                tagHistogram = tags,
                layoutPatterns = emptySet(),
                depth = 1,
                avgBranching = 1.0,
                roleHistogram = emptyMap()
            ),
            css = CssFeatures(
                styleTokens = styles,
                palette = emptyList(),
                spacingMean = 0.0,
                spacingStd = 0.0,
                fontFamilies = emptySet(),
                fontSizeBuckets = emptyMap()
            ),
            behavior = BehaviorFeatures(
                eventTypes = events,
                interactionPatterns = emptySet(),
                statePatterns = emptySet(),
                apiSignatures = emptySet(),
                cyclomatic = cyclomatic,
                handlerCount = events.size,
                apiCallCount = 0,
                conditionalCount = if (cyclomatic > 1) 1 else 0
            )
        )
    }
}
