package io.github.msameer0

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Net
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.viewport.FitViewport
import ktx.app.KtxScreen
import ktx.assets.disposeSafely

class LeaderboardScreen(private val game: FlappyBox) : KtxScreen {
    private val viewport = FitViewport(MainMenuScreen.VIRTUAL_WIDTH, MainMenuScreen.VIRTUAL_HEIGHT)
    private val stage = Stage(viewport)
    private val frameRenderer = PhoneFrameRenderer(MainMenuScreen.VIRTUAL_WIDTH, MainMenuScreen.VIRTUAL_HEIGHT)
    private val whitePixel = createPixelTexture(Color.WHITE)
    private val titleFont = createFont(TITLE_FONT_FILE, fallbackSize = 46)
    private val entryFont = createFont(ENTRY_FONT_FILE, fallbackSize = 20)
    private val buttonFont = createFont(BUTTON_FONT_FILE, fallbackSize = 28)
    private val entriesTable = Table()
    private val statusLabel = Label("Loading leaderboard...", Label.LabelStyle(entryFont, Color.BLACK)).apply {
        setAlignment(Align.center)
        wrap = true
    }

    init {
        stage.addActor(background())
        stage.addActor(content())
    }

    override fun show() {
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        Gdx.input.inputProcessor = stage
        loadLeaderboard()
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
        whitePixel.disposeSafely()
        titleFont.disposeSafely()
        entryFont.disposeSafely()
        buttonFont.disposeSafely()
    }

    private fun content(): Table {
        val titleStyle = Label.LabelStyle(titleFont, Color.BLACK)
        val buttonStyle = TextButton.TextButtonStyle(null, null, null, buttonFont).apply {
            fontColor = Color.BLACK
            overFontColor = Color(0.28f, 0.28f, 0.28f, 1f)
            downFontColor = Color(0.1f, 0.1f, 0.1f, 1f)
        }

        val title = Label("Leaderboard", titleStyle).apply {
            setAlignment(Align.center)
        }
        val scrollPane = ScrollPane(entriesTable).apply {
            setScrollingDisabled(true, false)
            setFadeScrollBars(false)
            setOverscroll(false, true)
        }
        val back = menuButton("Back", buttonStyle) {
            game.setScreen<MainMenuScreen>()
        }

        return Table().apply {
            setFillParent(true)
            top()
            padTop(66f)
            padLeft(22f)
            padRight(22f)
            padBottom(26f)

            add(title).width(MainMenuScreen.VIRTUAL_WIDTH - 44f).padBottom(24f).row()
            add(statusLabel).width(MainMenuScreen.VIRTUAL_WIDTH - 44f).padBottom(12f).row()
            add(scrollPane).width(MainMenuScreen.VIRTUAL_WIDTH - 44f).height(380f).padBottom(18f).row()
            add(back).width(220f).height(56f)
        }
    }

    private fun loadLeaderboard() {
        statusLabel.setText("Loading leaderboard...")
        entriesTable.clearChildren()

        val request = Net.HttpRequest(Net.HttpMethods.GET).apply {
            url = "$LEADERBOARD_API_PATH?limit=$LEADERBOARD_ENTRY_LIMIT"
        }

        Gdx.net.sendHttpRequest(request, object : Net.HttpResponseListener {
            override fun handleHttpResponse(response: Net.HttpResponse) {
                if (response.status.statusCode !in 200..299) {
                    showStatus("Leaderboard unavailable")
                    return
                }

                val entries = parseLeaderboard(response.resultAsString)
                if (entries.isEmpty()) {
                    showStatus("No scores yet")
                } else {
                    statusLabel.setText("Top ${entries.size}")
                    showEntries(entries)
                }
            }

            override fun failed(t: Throwable) {
                showStatus("Leaderboard unavailable")
            }

            override fun cancelled() {
                showStatus("Leaderboard unavailable")
            }
        })
    }

    private fun showStatus(status: String) {
        statusLabel.setText(status)
        entriesTable.clearChildren()
    }

    private fun showEntries(entries: List<LeaderboardEntry>) {
        entriesTable.clearChildren()
        entriesTable.top()

        entries.forEachIndexed { index, entry ->
            val rank = "${index + 1}."
            entriesTable.add(entryLabel(rank, Align.left)).width(44f).padBottom(10f)
            entriesTable.add(entryLabel(entry.name, Align.left)).expandX().fillX().padBottom(10f)
            entriesTable.add(entryLabel(entry.score.toString(), Align.right)).width(70f).padBottom(10f).row()
        }
    }

    private fun entryLabel(text: String, alignment: Int): Label =
        Label(text, Label.LabelStyle(entryFont, Color.BLACK)).apply {
            setAlignment(alignment)
            setEllipsis(true)
        }

    private fun parseLeaderboard(json: String): List<LeaderboardEntry> {
        val root = JsonReader().parse(json)
        val entries = root.get("entries") ?: return emptyList()
        val parsedEntries = mutableListOf<LeaderboardEntry>()
        var entry = entries.child

        while (entry != null && parsedEntries.size < LEADERBOARD_ENTRY_LIMIT) {
            parsedEntries += LeaderboardEntry(
                name = entry.getString("name", "Player"),
                score = entry.getInt("score", 0)
            )
            entry = entry.next
        }

        return parsedEntries
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

    private fun background() = object : Actor() {
        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: Float) {
            batch.color = Color.WHITE
            batch.draw(whitePixel, 0f, 0f, MainMenuScreen.VIRTUAL_WIDTH, MainMenuScreen.VIRTUAL_HEIGHT)
            batch.color = Color.WHITE
        }
    }.apply {
        setSize(MainMenuScreen.VIRTUAL_WIDTH, MainMenuScreen.VIRTUAL_HEIGHT)
        touchable = Touchable.disabled
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

    private data class LeaderboardEntry(
        val name: String,
        val score: Int
    )

    companion object {
        private const val DEFAULT_BITMAP_FONT_SIZE = 15f
        private const val TITLE_FONT_FILE = "fonts/carlito-title.fnt"
        private const val ENTRY_FONT_FILE = "fonts/carlito-splash.fnt"
        private const val BUTTON_FONT_FILE = "fonts/carlito-button.fnt"
        private const val FONT_ATLAS_SCALE = 0.5f
        private const val LEADERBOARD_API_PATH = "/api/leaderboard"
        private const val LEADERBOARD_ENTRY_LIMIT = 100
    }
}
