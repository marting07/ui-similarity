package extractor

import core.model.ComponentSignature
import core.model.DomFeatures
import core.model.CssFeatures
import core.model.BehaviorFeatures
import core.model.UiFramework

/**
 * Extracts DOM features from raw source code.  Implementations may use
 * framework‑specific parsers; a trivial implementation is provided in
 * [extractor.simple.SimpleDomFeatureExtractor].
 */
interface DomFeatureExtractor {
    fun extractDomFeatures(source: ComponentSource): DomFeatures
}

/**
 * Extracts CSS features from raw source code.  Implementations may parse
 * CSS/SCSS syntax, inline styles or Tailwind classes.  See
 * [extractor.simple.SimpleCssFeatureExtractor].
 */
interface CssFeatureExtractor {
    fun extractCssFeatures(source: ComponentSource): CssFeatures
}

/**
 * Extracts behaviour features from raw source code.  Implementations may
 * inspect JavaScript/TypeScript ASTs to detect event handlers, state
 * patterns, API calls and complexity.  A naive implementation is given in
 * [extractor.simple.SimpleBehaviorFeatureExtractor].
 */
interface BehaviorFeatureExtractor {
    fun extractBehaviorFeatures(source: ComponentSource): BehaviorFeatures
}

/**
 * Primary entry point for feature extraction.  It delegates to the
 * supplied DOM, CSS and behaviour extractors to build a complete
 * [ComponentSignature] from raw code.  Consumers can supply different
 * extractor implementations depending on the framework or desired
 * fidelity.
 */
class ComponentSignatureExtractor(
    private val domExtractor: DomFeatureExtractor,
    private val cssExtractor: CssFeatureExtractor,
    private val behaviorExtractor: BehaviorFeatureExtractor
) {
    fun extract(source: ComponentSource): ComponentSignature {
        val dom = domExtractor.extractDomFeatures(source)
        val css = cssExtractor.extractCssFeatures(source)
        val behavior = behaviorExtractor.extractBehaviorFeatures(source)
        return ComponentSignature(
            id = source.id,
            framework = source.framework,
            dom = dom,
            css = css,
            behavior = behavior
        )
    }
}
