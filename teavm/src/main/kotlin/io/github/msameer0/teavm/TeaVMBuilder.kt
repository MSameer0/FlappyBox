package io.github.msameer0.teavm

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle
import com.github.xpenatan.gdx.teavm.backends.shared.config.compiler.TeaCompiler
import com.github.xpenatan.gdx.teavm.backends.web.config.backend.WebBackend
import org.teavm.tooling.TeaVMSourceFilePolicy
import org.teavm.tooling.sources.DirectorySourceFileProvider
import org.teavm.vm.TeaVMOptimizationLevel
import java.io.File

/** Builds the TeaVM/HTML application. */
object TeaVMBuilder {
    @JvmStatic fun main(arguments: Array<String>) {
        val debug = "debug" in arguments
        val startJetty = "run" in arguments

        val webBackend = WebBackend()
            .setHtmlTitle("FlappyBox")
            .setHtmlWidth(800) /* Change this to fit your game's requirements. */
            .setHtmlHeight(600) /* Change this to fit your game's requirements. */
            .setStartJettyAfterBuild(startJetty)
            .setJettyPort(8080)
//            .setWebAssembly(true) /* Uncomment this line to use WASM output instead of JavaScript output. */

        TeaCompiler(webBackend)
            .addAssets(AssetFileHandle("../assets"))
            .addAssets(AssetFileHandle("com/kotcrab/vis/ui/skin/x1", com.badlogic.gdx.Files.FileType.Classpath))
            .addAssets(AssetFileHandle("com/kotcrab/vis/ui/skin/x2", com.badlogic.gdx.Files.FileType.Classpath))
            .addAssets(AssetFileHandle("com/kotcrab/vis/ui/widget/color/internal", com.badlogic.gdx.Files.FileType.Classpath))
            .addAssets(AssetFileHandle("com/kotcrab/vis/ui/i18n/ButtonBar.properties", com.badlogic.gdx.Files.FileType.Classpath))
            .addAssets(AssetFileHandle("com/kotcrab/vis/ui/i18n/ColorPicker.properties", com.badlogic.gdx.Files.FileType.Classpath))
            .addAssets(AssetFileHandle("com/kotcrab/vis/ui/i18n/Common.properties", com.badlogic.gdx.Files.FileType.Classpath))
            .addAssets(AssetFileHandle("com/kotcrab/vis/ui/i18n/Dialogs.properties", com.badlogic.gdx.Files.FileType.Classpath))
            .addAssets(AssetFileHandle("com/kotcrab/vis/ui/i18n/FileChooser.properties", com.badlogic.gdx.Files.FileType.Classpath))
            .addAssets(AssetFileHandle("com/kotcrab/vis/ui/i18n/TabbedPane.properties", com.badlogic.gdx.Files.FileType.Classpath))
            .setOptimizationLevel(if (debug) TeaVMOptimizationLevel.SIMPLE else TeaVMOptimizationLevel.ADVANCED)
            .setMainClass("io.github.msameer0.teavm.TeaVMLauncher")
            .setObfuscated(!debug)
            .setDebugInformationGenerated(debug)
            .setSourceMapsFileGenerated(debug)
            .setSourceFilePolicy(if (debug) TeaVMSourceFilePolicy.COPY else TeaVMSourceFilePolicy.DO_NOTHING)
            .addSourceFileProvider(DirectorySourceFileProvider(File("../core/src/main/kotlin")))
            // Register any classes or packages that require reflection here.
            .addReflectionClass("com.kotcrab.vis.ui.widget")
            .addReflectionClass("com.kotcrab.vis.ui.widget.spinner")
            .addReflectionClass("com.kotcrab.vis.ui.widget.dialog")
            .addReflectionClass("com.kotcrab.vis.ui.widget.color")
            .addReflectionClass("com.kotcrab.vis.ui.widget.tabbedpane")
            .addReflectionClass("com.kotcrab.vis.ui.widget.toast")
            .addReflectionClass("com.kotcrab.vis.ui.util.form")
            .addReflectionClass("com.kotcrab.vis.ui.Sizes")
            .addReflectionClass("com.kotcrab.vis.ui.util.adapter.SimpleListAdapter")
            .addReflectionClass("com.kotcrab.vis.ui.**Style")
            .build(File("build/dist"))
    }
}