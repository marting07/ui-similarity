package extractor

import core.model.UiFramework
import java.nio.file.Path

/**
 * A container for raw source code associated with a UI component.  The
 * scanner populates this class with file contents for template, style and
 * logic.  Frameworks such as React often store template and logic in the
 * same file.  [templateCode], [styleCode] and [logicCode] contain the raw
 * source text for the component; [framework] declares the originating
 * framework to assist extractors with context.
 */
data class ComponentSource(
    val id: String,
    val framework: UiFramework,
    val templateCode: String,
    val styleCode: String,
    val logicCode: String
)
