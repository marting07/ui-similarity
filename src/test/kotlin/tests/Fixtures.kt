package tests

import java.nio.file.Path

object Fixtures {
    private val root: Path = Path.of("src/test/resources/fixtures")

    val reactRepo: Path = root.resolve("repos/react/sample-react")
    val reactEdgeRepo: Path = root.resolve("repos/react/edge-react")
    val angularRepo: Path = root.resolve("repos/angular/sample-angular")
    val angularEdgeRepo: Path = root.resolve("repos/angular/edge-angular")
    val vueRepo: Path = root.resolve("repos/vue/sample-vue")

    val loaderTemplate: Path = root.resolve("source-loader/template.html")
    val loaderStyleA: Path = root.resolve("source-loader/style-a.css")
    val loaderStyleB: Path = root.resolve("source-loader/style-b.css")
    val loaderLogic: Path = root.resolve("source-loader/logic.ts")
    val loaderMissing: Path = root.resolve("source-loader/missing.css")
}
