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
    private val activeModifiers = mutableListOf<ActiveModifier>()
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
    private var themeBlend = 0f

    init {
        resetGame()
    }

    fun activateModifier(type: ModifierType, duration: Float) {
        if (duration <= 0f) return

        removeConflictingModifiers(type)

        val existingIndex = activeModifiers.indexOfFirst { it.type == type }
        val timeRemaining = if (existingIndex >= 0) {
            max(activeModifiers.removeAt(existingIndex).timeRemaining, duration)
        } else {
            duration
        }

        activeModifiers += ActiveModifier(type, timeRemaining)
    }

    private fun removeConflictingModifiers(type: ModifierType) {
        when (type) {
            ModifierType.SLOW_SPEED,
            ModifierType.NORMAL_SPEED,
            ModifierType.FAST_SPEED -> activeModifiers.removeAll { it.type in SPEED_MODIFIERS }
            else -> Unit
        }
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
        updateModifiers(clampedDelta)

        if (!gameOver) {
            updateGame(clampedDelta)
            updateTheme(clampedDelta)
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
        birdY = (VIRTUAL_HEIGHT - NORMAL_BIRD_SIZE) * 0.5f
        birdVelocity = 0f
        score = 0
        themeBlend = 0f
        gameOver = false
        activeModifiers.clear()
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
        birdVelocity = -modifierState().gravityDirection * FLAP_SPEED
    }

    private fun updateGame(delta: Float) {
        val modifiers = modifierState()

        updateBird(delta, modifiers)
        updatePipes(delta, modifiers)
        recyclePipes()

        if (isBirdOutOfBounds(modifiers) || pipes.any { collidesWithBird(it, modifiers) }) {
            endGame()
        }
    }

    private fun updateModifiers(delta: Float) {
        activeModifiers.forEach { modifier ->
            modifier.timeRemaining -= delta
        }
        activeModifiers.removeAll { it.timeRemaining <= 0f }
    }

    private fun updateBird(delta: Float, modifiers: ModifierState) {
        birdVelocity += GRAVITY_ACCELERATION * modifiers.gravityDirection * delta
        birdY += birdVelocity * delta
    }

    private fun updatePipes(delta: Float, modifiers: ModifierState) {
        pipes.forEach { pipe ->
            pipe.x -= PIPE_SPEED * modifiers.speedMultiplier * delta

            if (!pipe.scored && pipe.x + PIPE_WIDTH < BIRD_BASE_X) {
                pipe.scored = true
                score += 1
                saveHighScoreIfNeeded()
            }
        }
    }

    private fun updateTheme(delta: Float) {
        val targetBlend = targetThemeBlend()
        val step = delta / THEME_TRANSITION_SECONDS

        themeBlend = if (themeBlend < targetBlend) {
            min(targetBlend, themeBlend + step)
        } else {
            max(targetBlend, themeBlend - step)
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

    private fun collidesWithBird(pipe: Pipe, modifiers: ModifierState): Boolean {
        val birdX = BIRD_BASE_X
        val pipeX = pipe.x
        val birdRight = birdX + modifiers.birdSize
        val birdTop = birdY + modifiers.birdSize
        val pipeRight = pipeX + PIPE_WIDTH
        val overlapsPipeX = birdRight > pipeX && birdX < pipeRight
        val outsideGap = birdY < pipe.gapY || birdTop > pipe.gapY + PIPE_GAP

        return overlapsPipeX && outsideGap
    }

    private fun isBirdOutOfBounds(modifiers: ModifierState): Boolean =
        birdY < 0f || birdY + modifiers.birdSize > VIRTUAL_HEIGHT

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
        val palette = gameplayPalette()
        val modifiers = modifierState()

        shapes.projectionMatrix = viewport.camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = palette.background
        shapes.rect(0f, 0f, VIRTUAL_WIDTH, VIRTUAL_HEIGHT)

        shapes.color = palette.foreground

        pipes.forEach { pipe ->
            val pipeX = renderPipeX(pipe, modifiers)
            shapes.rect(pipeX, 0f, PIPE_WIDTH, pipe.gapY)
            shapes.rect(pipeX, pipe.gapY + PIPE_GAP, PIPE_WIDTH, VIRTUAL_HEIGHT - pipe.gapY - PIPE_GAP)
        }
        shapes.rect(renderBirdX(modifiers), birdY, modifiers.birdSize, modifiers.birdSize)

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
        val palette = gameplayPalette()
        val hudLines = listOf(
            HudLine("Score: $score", HUD_PADDING, VIRTUAL_HEIGHT - HUD_TOP_PADDING, hudFont),
            HudLine("High: $highScore", HUD_PADDING, VIRTUAL_HEIGHT - HUD_TOP_PADDING - HUD_LINE_SPACING, hudFont)
        )

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        drawLines(hudLines, palette.foreground)
        drawLinesClippedToForegroundGeometry(hudLines, palette.background)

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

    private fun drawLinesClippedToForegroundGeometry(lines: List<HudLine>, color: Color) {
        lines.forEach { line ->
            line.font.color = color
        }

        batch.flush()
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        foregroundGeometry().forEach { foregroundRect ->
            val scissor = toScreenScissor(foregroundRect)
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

    private fun foregroundGeometry(): List<Rectangle> {
        val modifiers = modifierState()
        val rectangles = mutableListOf<Rectangle>()
        pipes.forEach { pipe ->
            val pipeX = renderPipeX(pipe, modifiers)
            rectangles += Rectangle(pipeX, 0f, PIPE_WIDTH, pipe.gapY)
            rectangles += Rectangle(pipeX, pipe.gapY + PIPE_GAP, PIPE_WIDTH, VIRTUAL_HEIGHT - pipe.gapY - PIPE_GAP)
        }
        rectangles += Rectangle(renderBirdX(modifiers), birdY, modifiers.birdSize, modifiers.birdSize)
        return rectangles
    }

    private fun modifierState(): ModifierState =
        ModifierState(
            gravityDirection = if (hasModifier(ModifierType.FLIPPED_GRAVITY)) 1f else -1f,
            speedMultiplier = speedMultiplier(),
            mirrored = hasModifier(ModifierType.MIRROR_MODE),
            birdSize = birdSize()
        )

    private fun speedMultiplier(): Float =
        when (latestModifier(ModifierType.SLOW_SPEED, ModifierType.NORMAL_SPEED, ModifierType.FAST_SPEED)) {
            ModifierType.SLOW_SPEED -> SLOW_SPEED_MULTIPLIER
            ModifierType.FAST_SPEED -> FAST_SPEED_MULTIPLIER
            else -> NORMAL_SPEED_MULTIPLIER
        }

    private fun birdSize(): Float =
        when (latestModifier(ModifierType.SMALL_SIZE, ModifierType.NORMAL_SIZE, ModifierType.BIG_SIZE)) {
            ModifierType.SMALL_SIZE -> NORMAL_BIRD_SIZE * SMALL_SIZE_MULTIPLIER
            ModifierType.BIG_SIZE -> NORMAL_BIRD_SIZE * BIG_SIZE_MULTIPLIER
            else -> NORMAL_BIRD_SIZE * NORMAL_SIZE_MULTIPLIER
        }

    private fun latestModifier(vararg types: ModifierType): ModifierType? =
        activeModifiers.asReversed().firstOrNull { it.type in types }?.type

    private fun hasModifier(type: ModifierType): Boolean =
        activeModifiers.any { it.type == type }

    private fun renderBirdX(modifiers: ModifierState): Float =
        if (modifiers.mirrored) VIRTUAL_WIDTH - BIRD_BASE_X - modifiers.birdSize else BIRD_BASE_X

    private fun renderPipeX(pipe: Pipe, modifiers: ModifierState): Float =
        if (modifiers.mirrored) VIRTUAL_WIDTH - pipe.x - PIPE_WIDTH else pipe.x

    private fun gameplayPalette(): GameplayPalette {
        val easedBlend = smoothStep(themeBlend)
        val backgroundShade = 1f - easedBlend
        val foregroundShade = easedBlend

        return GameplayPalette(
            background = Color(backgroundShade, backgroundShade, backgroundShade, 1f),
            foreground = Color(foregroundShade, foregroundShade, foregroundShade, 1f)
        )
    }

    private fun targetThemeBlend(): Float =
        if ((score / THEME_SCORE_INTERVAL) % 2 == 1) 1f else 0f

    private fun smoothStep(value: Float): Float =
        value * value * (3f - 2f * value)

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

    private data class GameplayPalette(
        val background: Color,
        val foreground: Color
    )

    private data class ModifierState(
        val gravityDirection: Float,
        val speedMultiplier: Float,
        val mirrored: Boolean,
        val birdSize: Float
    )

    private data class Pipe(
        var x: Float,
        val gapY: Float,
        var scored: Boolean = false
    )

    companion object {
        private const val VIRTUAL_WIDTH = MainMenuScreen.VIRTUAL_WIDTH
        private const val VIRTUAL_HEIGHT = MainMenuScreen.VIRTUAL_HEIGHT
        private const val BIRD_BASE_X = 82f
        private const val NORMAL_BIRD_SIZE = 24f
        private const val SMALL_SIZE_MULTIPLIER = 0.75f
        private const val NORMAL_SIZE_MULTIPLIER = 1f
        private const val BIG_SIZE_MULTIPLIER = 1.4f
        private const val GRAVITY_ACCELERATION = 920f
        private const val FLAP_SPEED = 330f
        private const val NORMAL_SPEED_MULTIPLIER = 1f
        private const val SLOW_SPEED_MULTIPLIER = 0.7f
        private const val FAST_SPEED_MULTIPLIER = 1.35f
        private const val PIPE_WIDTH = NORMAL_BIRD_SIZE
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
        private const val THEME_SCORE_INTERVAL = 50
        private const val THEME_TRANSITION_SECONDS = 1.35f
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

        private val SPEED_MODIFIERS = setOf(
            ModifierType.SLOW_SPEED,
            ModifierType.NORMAL_SPEED,
            ModifierType.FAST_SPEED
        )
    }
}
