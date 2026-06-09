package io.github.msameer0

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class GameScreen(private val game: FlappyBox) : KtxScreen {
    private val viewport = FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT)
    private val shapes = ShapeRenderer()
    private val batch = SpriteBatch()
    private val frameRenderer = PhoneFrameRenderer(VIRTUAL_WIDTH, VIRTUAL_HEIGHT)
    private val hudFont = createFont(HUD_FONT_FILE, HUD_FONT_SCALE)
    private val centerFont = createFont(HUD_FONT_FILE, CENTER_FONT_SCALE)
    private val layout = GlyphLayout()
    private val touchPoint = Vector2()
    private val retryButton = Rectangle(BUTTON_X, BUTTON_RETRY_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
    private val menuButton = Rectangle(BUTTON_X, BUTTON_MENU_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
    private val preferences: Preferences = Gdx.app.getPreferences(PREFERENCES_NAME)
    private val pipes = mutableListOf<Pipe>()
    private val input = object : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            if (gameOver && keycode == Input.Keys.ESCAPE) {
                game.setScreen<MainMenuScreen>()
                return true
            }

            if (keycode == Input.Keys.SPACE || keycode == Input.Keys.UP || keycode == Input.Keys.ENTER) {
                flapOrRestart()
                return true
            }
            return false
        }

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (gameOver) {
                touchPoint.set(screenX.toFloat(), screenY.toFloat())
                viewport.unproject(touchPoint)

                when {
                    retryButton.contains(touchPoint) -> resetGame()
                    menuButton.contains(touchPoint) -> game.setScreen<MainMenuScreen>()
                }

                return true
            }

            flapOrRestart()
            return true
        }
    }

    private var birdY = 0f
    private var birdVelocity = 0f
    private var score = 0
    private var highScore = preferences.getInteger(HIGH_SCORE_KEY, 0)
    private var gameOver = false

    init {
        resetGame()
    }

    override fun show() {
        if (gameOver) {
            resetGame()
        }
        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
        Gdx.input.inputProcessor = input
    }

    override fun render(delta: Float) {
        val clampedDelta = min(delta, MAX_DELTA)
        if (!gameOver) {
            updateGame(clampedDelta)
        }

        frameRenderer.draw()
        viewport.apply()
        drawGame()
        if (gameOver) {
            drawGameOverPlate()
        }
        drawHud()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun hide() {
        if (Gdx.input.inputProcessor == input) {
            Gdx.input.inputProcessor = null
        }
    }

    override fun dispose() {
        shapes.disposeSafely()
        batch.disposeSafely()
        frameRenderer.dispose()
        hudFont.disposeSafely()
        centerFont.disposeSafely()
    }

    private fun resetGame() {
        birdY = (VIRTUAL_HEIGHT - BIRD_SIZE) * 0.5f
        birdVelocity = 0f
        score = 0
        gameOver = false
        pipes.clear()

        repeat(PIPE_COUNT) { index ->
            pipes += Pipe(
                x = VIRTUAL_WIDTH + PIPE_START_OFFSET + PIPE_SPACING * index,
                gapY = randomGapY()
            )
        }
    }

    private fun flapOrRestart() {
        if (gameOver) {
            resetGame()
        }
        birdVelocity = FLAP_VELOCITY
    }

    private fun updateGame(delta: Float) {
        birdVelocity += GRAVITY * delta
        birdY += birdVelocity * delta

        pipes.forEach { pipe ->
            pipe.x -= PIPE_SPEED * delta

            if (!pipe.scored && pipe.x + PIPE_WIDTH < BIRD_X) {
                pipe.scored = true
                score += 1
                saveHighScoreIfNeeded()
            }
        }

        recyclePipes()

        if (birdY < 0f || birdY + BIRD_SIZE > VIRTUAL_HEIGHT || pipes.any(::collidesWithBird)) {
            endGame()
        }
    }

    private fun recyclePipes() {
        val firstPipe = pipes.firstOrNull() ?: return
        if (firstPipe.x + PIPE_WIDTH >= 0f) return

        val farthestX = pipes.maxOf { it.x }
        pipes.removeAt(0)
        pipes += Pipe(
            x = farthestX + PIPE_SPACING,
            gapY = randomGapY()
        )
    }

    private fun collidesWithBird(pipe: Pipe): Boolean {
        val birdRight = BIRD_X + BIRD_SIZE
        val birdTop = birdY + BIRD_SIZE
        val pipeRight = pipe.x + PIPE_WIDTH
        val overlapsPipeX = birdRight > pipe.x && BIRD_X < pipeRight
        val outsideGap = birdY < pipe.gapY || birdTop > pipe.gapY + PIPE_GAP

        return overlapsPipeX && outsideGap
    }

    private fun endGame() {
        gameOver = true
        saveHighScoreIfNeeded()
    }

    private fun saveHighScoreIfNeeded() {
        if (score <= highScore) return

        highScore = score
        preferences.putInteger(HIGH_SCORE_KEY, highScore)
        preferences.flush()
    }

    private fun drawGame() {
        shapes.projectionMatrix = viewport.camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color.WHITE
        shapes.rect(0f, 0f, VIRTUAL_WIDTH, VIRTUAL_HEIGHT)

        shapes.color = Color.BLACK

        pipes.forEach { pipe ->
            shapes.rect(pipe.x, 0f, PIPE_WIDTH, pipe.gapY)
            shapes.rect(pipe.x, pipe.gapY + PIPE_GAP, PIPE_WIDTH, VIRTUAL_HEIGHT - pipe.gapY - PIPE_GAP)
        }
        shapes.rect(BIRD_X, birdY, BIRD_SIZE, BIRD_SIZE)

        shapes.end()
    }

    private fun drawGameOverPlate() {
        shapes.projectionMatrix = viewport.camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color.WHITE
        shapes.rect(PLATE_X, PLATE_Y, PLATE_WIDTH, PLATE_HEIGHT)

        shapes.color = Color.BLACK
        drawRectOutline(PLATE_X, PLATE_Y, PLATE_WIDTH, PLATE_HEIGHT, BORDER_SIZE)
        drawRectOutline(retryButton.x, retryButton.y, retryButton.width, retryButton.height, BORDER_SIZE)
        drawRectOutline(menuButton.x, menuButton.y, menuButton.width, menuButton.height, BORDER_SIZE)
        shapes.end()
    }

    private fun drawRectOutline(x: Float, y: Float, width: Float, height: Float, thickness: Float) {
        shapes.rect(x, y, width, thickness)
        shapes.rect(x, y + height - thickness, width, thickness)
        shapes.rect(x, y, thickness, height)
        shapes.rect(x + width - thickness, y, thickness, height)
    }

    private fun drawHud() {
        val hudLines = listOf(
            HudLine("Score: $score", HUD_PADDING, VIRTUAL_HEIGHT - HUD_TOP_PADDING, hudFont),
            HudLine("High: $highScore", HUD_PADDING, VIRTUAL_HEIGHT - HUD_TOP_PADDING - HUD_LINE_SPACING, hudFont)
        )

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        drawLines(hudLines, Color.BLACK)
        drawLinesClippedToBlackGeometry(hudLines)

        if (gameOver) {
            drawCentered("Game Over", PLATE_Y + PLATE_HEIGHT - 54f, centerFont)
            drawCentered("Retry", retryButton.y + 31f, hudFont)
            drawCentered("Main Menu", menuButton.y + 31f, hudFont)
        }

        batch.end()
    }

    private fun drawLines(lines: List<HudLine>, color: Color) {
        lines.forEach { line ->
            line.font.color = color
            line.font.draw(batch, line.text, line.x, line.y)
        }
    }

    private fun drawLinesClippedToBlackGeometry(lines: List<HudLine>) {
        lines.forEach { line ->
            line.font.color = Color.WHITE
        }

        batch.flush()
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        blackGeometry().forEach { blackRect ->
            val scissor = toScreenScissor(blackRect)
            batch.flush()

            if (scissor.width > 0f && scissor.height > 0f) {
                Gdx.gl.glScissor(scissor.x.toInt(), scissor.y.toInt(), scissor.width.toInt(), scissor.height.toInt())
                lines.forEach { line ->
                    line.font.draw(batch, line.text, line.x, line.y)
                }
                batch.flush()
            }
        }
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
    }

    private fun toScreenScissor(worldRect: Rectangle): Rectangle {
        val scaleX = viewport.screenWidth / VIRTUAL_WIDTH
        val scaleY = viewport.screenHeight / VIRTUAL_HEIGHT
        val x = viewport.screenX + worldRect.x * scaleX
        val y = viewport.screenY + worldRect.y * scaleY
        val width = worldRect.width * scaleX
        val height = worldRect.height * scaleY
        val left = max(viewport.screenX.toFloat(), floor(x))
        val bottom = max(viewport.screenY.toFloat(), floor(y))
        val right = min((viewport.screenX + viewport.screenWidth).toFloat(), ceil(x + width))
        val top = min((viewport.screenY + viewport.screenHeight).toFloat(), ceil(y + height))

        return Rectangle(left, bottom, max(0f, right - left), max(0f, top - bottom))
    }

    private fun blackGeometry(): List<Rectangle> {
        val rectangles = mutableListOf<Rectangle>()
        pipes.forEach { pipe ->
            rectangles += Rectangle(pipe.x, 0f, PIPE_WIDTH, pipe.gapY)
            rectangles += Rectangle(pipe.x, pipe.gapY + PIPE_GAP, PIPE_WIDTH, VIRTUAL_HEIGHT - pipe.gapY - PIPE_GAP)
        }
        rectangles += Rectangle(BIRD_X, birdY, BIRD_SIZE, BIRD_SIZE)
        return rectangles
    }

    private fun drawCentered(text: String, y: Float, textFont: BitmapFont) {
        textFont.color = Color.BLACK
        layout.setText(textFont, text)
        textFont.draw(batch, text, (VIRTUAL_WIDTH - layout.width) * 0.5f, y)
    }

    private fun randomGapY(): Float =
        MathUtils.random(MIN_GAP_Y, VIRTUAL_HEIGHT - MIN_GAP_Y - PIPE_GAP)

    private fun createFont(fontFilePath: String, scale: Float): BitmapFont {
        val fontFile = Gdx.files.internal(fontFilePath)
        val font = if (fontFile.exists()) BitmapFont(fontFile) else BitmapFont()
        font.data.setScale(scale)
        font.regions.forEach { region ->
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
        font.color = Color.BLACK
        return font
    }

    private data class HudLine(
        val text: String,
        val x: Float,
        val y: Float,
        val font: BitmapFont
    )

    private data class Pipe(
        var x: Float,
        val gapY: Float,
        var scored: Boolean = false
    )

    companion object {
        private const val VIRTUAL_WIDTH = MainMenuScreen.VIRTUAL_WIDTH
        private const val VIRTUAL_HEIGHT = MainMenuScreen.VIRTUAL_HEIGHT
        private const val BIRD_X = 82f
        private const val BIRD_SIZE = 24f
        private const val GRAVITY = -920f
        private const val FLAP_VELOCITY = 330f
        private const val PIPE_WIDTH = BIRD_SIZE
        private const val PIPE_GAP = 140f
        private const val PIPE_SPEED = 138f
        private const val PIPE_SPACING = 155f
        private const val PIPE_START_OFFSET = 112f
        private const val PIPE_COUNT = 5
        private const val MIN_GAP_Y = 70f
        private const val HUD_PADDING = 12f
        private const val HUD_TOP_PADDING = 16f
        private const val HUD_LINE_SPACING = 26f
        private const val HUD_FONT_SCALE = 0.48f
        private const val CENTER_FONT_SCALE = 0.78f
        private const val MAX_DELTA = 1f / 30f
        private const val PREFERENCES_NAME = "flappy-box"
        private const val HIGH_SCORE_KEY = "high-score"
        private const val HUD_FONT_FILE = "fonts/carlito-splash.fnt"
        private const val PLATE_WIDTH = 240f
        private const val PLATE_HEIGHT = 214f
        private const val PLATE_X = (VIRTUAL_WIDTH - PLATE_WIDTH) * 0.5f
        private const val PLATE_Y = (VIRTUAL_HEIGHT - PLATE_HEIGHT) * 0.5f
        private const val BUTTON_WIDTH = 170f
        private const val BUTTON_HEIGHT = 44f
        private const val BUTTON_X = (VIRTUAL_WIDTH - BUTTON_WIDTH) * 0.5f
        private const val BUTTON_RETRY_Y = PLATE_Y + 82f
        private const val BUTTON_MENU_Y = PLATE_Y + 30f
        private const val BORDER_SIZE = 2f
    }
}
