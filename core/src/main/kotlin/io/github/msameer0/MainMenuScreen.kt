package io.github.msameer0

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import ktx.app.KtxScreen
import ktx.assets.disposeSafely

class MainMenuScreen(private val game: FlappyBox) : KtxScreen {
    private val viewport = FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT)
    private val stage = Stage(viewport)
    private val frameRenderer = PhoneFrameRenderer(VIRTUAL_WIDTH, VIRTUAL_HEIGHT)
    private val grayPixel = createPixelTexture(BACKGROUND_GRAY)
    private val titleFont = createFont(TITLE_FONT_FILE, fallbackSize = 58)
    private val splashFont = createFont(SPLASH_FONT_FILE, fallbackSize = 18)
    private val buttonFont = createFont(BUTTON_FONT_FILE, fallbackSize = 28)
    private val splashTexts = loadSplashTexts()

    init {
        stage.addActor(background())
        stage.addActor(menu())
    }

    override fun show() {
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        Gdx.input.inputProcessor = stage
    }

    override fun render(delta: Float) {
        frameRenderer.draw()

        viewport.apply()
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun hide() {
        if (Gdx.input.inputProcessor == stage) {
            Gdx.input.inputProcessor = null
        }
    }

    override fun dispose() {
        stage.disposeSafely()
        frameRenderer.dispose()
        grayPixel.disposeSafely()
        titleFont.disposeSafely()
        splashFont.disposeSafely()
        buttonFont.disposeSafely()
    }

    private fun background() = object : Actor() {
        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
            batch.color = BACKGROUND_GRAY
            batch.draw(grayPixel, 0f, 0f, VIRTUAL_WIDTH, VIRTUAL_HEIGHT)
            batch.color = Color.WHITE
        }
    }.apply {
        setSize(VIRTUAL_WIDTH, VIRTUAL_HEIGHT)
        touchable = Touchable.disabled
    }

    private fun menu(): Table {
        val titleStyle = Label.LabelStyle(titleFont, Color.BLACK)
        val splashStyle = Label.LabelStyle(splashFont, Color.BLACK)
        val buttonStyle = TextButton.TextButtonStyle(null, null, null, buttonFont).apply {
            fontColor = Color.BLACK
            overFontColor = Color(0.28f, 0.28f, 0.28f, 1f)
            downFontColor = Color(0.1f, 0.1f, 0.1f, 1f)
        }

        val title = Label("FlappyBox", titleStyle).apply {
            setAlignment(Align.center)
        }
        val splash = Label(randomSplashText(), splashStyle).apply {
            setAlignment(Align.center)
            wrap = true
        }
        val start = menuButton("Start", buttonStyle) {
            game.setScreen<GameScreen>()
        }
        val leaderboard = menuButton("Leaderboard", buttonStyle) {
            // TODO: Open leaderboard once the leaderboard screen exists.
        }

        return Table().apply {
            setFillParent(true)
            center()

            add(title).width(VIRTUAL_WIDTH).padBottom(8f).row()
            add(splash).width(300f).padBottom(88f).row()
            add(start).width(220f).height(56f).padBottom(10f).row()
            add(leaderboard).width(220f).height(56f)
        }
    }

    private fun menuButton(text: String, style: TextButton.TextButtonStyle, onClick: () -> Unit): TextButton =
        TextButton(text, style).apply {
            label.setAlignment(Align.center)
            touchable = Touchable.enabled
            addListener(object : ClickListener(Input.Buttons.LEFT) {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onClick()
                }
            })
        }

    private fun createPixelTexture(color: Color): Texture {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()
        return Texture(pixmap).also {
            pixmap.dispose()
        }
    }

    private fun createFont(fontFilePath: String, fallbackSize: Int): BitmapFont {
        val fontFile = Gdx.files.internal(fontFilePath)
        if (!fontFile.exists()) return createFallbackFont(fallbackSize)

        return BitmapFont(fontFile).apply {
            data.setScale(FONT_ATLAS_SCALE)
            regions.forEach { region ->
                region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            }
            color = Color.BLACK
        }
    }

    private fun createFallbackFont(size: Int): BitmapFont =
        BitmapFont().apply {
            data.setScale(size / DEFAULT_BITMAP_FONT_SIZE)
            color = Color.BLACK
        }

    private fun loadSplashTexts(): List<String> {
        val splashesFile = Gdx.files.internal(SPLASHES_FILE)
        if (!splashesFile.exists()) return DEFAULT_SPLASH_TEXTS

        val splashes = mutableListOf<String>()
        splashesFile.readString().split('\n').forEach { line ->
            val splash = line.trim()
            if (splash.isNotEmpty() && !splash.startsWith("#")) {
                splashes += splash
            }
        }

        return if (splashes.isEmpty()) DEFAULT_SPLASH_TEXTS else splashes
    }

    private fun randomSplashText(): String {
        if (splashTexts.size == 1) return splashTexts.first()
        return splashTexts[MathUtils.random(splashTexts.size - 1)]
    }

    companion object {
        const val VIRTUAL_WIDTH = 360f
        const val VIRTUAL_HEIGHT = 640f
        private const val DEFAULT_BITMAP_FONT_SIZE = 15f
        private const val SPLASHES_FILE = "splashes.txt"
        private const val TITLE_FONT_FILE = "fonts/carlito-title.fnt"
        private const val SPLASH_FONT_FILE = "fonts/carlito-splash.fnt"
        private const val BUTTON_FONT_FILE = "fonts/carlito-button.fnt"
        private const val FONT_ATLAS_SCALE = 0.5f

        private val BACKGROUND_GRAY = Color.WHITE
        private val DEFAULT_SPLASH_TEXTS = listOf(
            "splash text."
        )
    }
}
