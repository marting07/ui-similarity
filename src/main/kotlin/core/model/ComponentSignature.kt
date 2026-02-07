package core.model

/**
 * A unified representation of a UI component across DOM, CSS and behavioural
 * dimensions.  Each component in a repository is assigned a globally unique
 * [id] derived from its repository, relative path and export name.  The
 * [framework] field records the originating framework (React, Angular, Vue or
 * unknown), while the three feature objects encapsulate multi‑layer
 * information that can be compared via [core.similarity.ComponentDistance].
 */
data class ComponentSignature(
    val id: String,
    val framework: UiFramework,
    val dom: DomFeatures,
    val css: CssFeatures,
    val behavior: BehaviorFeatures
)
