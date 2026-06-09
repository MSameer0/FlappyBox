package io.github.msameer0

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Net
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.audio.Sound
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
import com.badlogic.gdx.utils.JsonReader
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
    private val warningFont = createFont(WARNING_FONT_FILE, WARNING_FONT_SCALE)
    private val jumpSound = createSound(JUMP_SOUND_FILE)
    private val scoreSound = createSound(SCORE_SOUND_FILE)
    private val deathSound = createSound(DEATH_SOUND_FILE)
    private val warningSound = createSound(WARNING_SOUND_FILE)
    private val layout = GlyphLayout()
    private val touchPoint = Vector2()
    private val retryButton = Rectangle(BUTTON_X, BUTTON_RETRY_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
    private val menuButton = Rectangle(BUTTON_X, BUTTON_MENU_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
    private val submitScoreButton = Rectangle(BUTTON_X, BUTTON_SUBMIT_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
    private val preferences: Preferences = Gdx.app.getPreferences(PREFERENCES_NAME)
    private val pipes = mutableListOf<Pipe>()
    private val activeModifiers = mutableListOf<ActiveModifier>()
    private val modifierDirector = ModifierDirector()
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
                    submitScoreButton.contains(touchPoint) -> promptScoreSubmission()
                    retryButton.contains(touchPoint) -> resetGame()
                    menuButton.contains(touchPoint) -> game.setScreen<MainMenuScreen>()
                }

                return true
            }

            flapOrRestart()
            return true
        }
    }

    private var birdCenterY = 0f
    private var birdVelocity = 0f
    private var score = 0
    private var highScore = preferences.getInteger(HIGH_SCORE_KEY, 0)
    private var gameOver = false
    private var themeBlend = 0f
    private var mirrorBlend = 0f
    private var leaderboardEntries = emptyList<LeaderboardEntry>()
    private var leaderboardStatus = ""
    private var submittingScore = false
    private var scoreSubmitted = false
    private var gravityReturnRelaxedPipesPrepared = false

    init {
        resetGame()
    }

    fun activateModifier(type: ModifierType, duration: Float) {
        if (duration <= 0f) return

        removeConflictingModifiers(type)

        val existingIndex = activeModifiers.indexOfFirst { it.type == type }
        val existingModifier = if (existingIndex >= 0) activeModifiers.removeAt(existingIndex) else null
        val timeRemaining = max(existingModifier?.timeRemaining ?: 0f, duration)
        val totalDuration = max(existingModifier?.duration ?: 0f, duration)

        activeModifiers += ActiveModifier(type, timeRemaining, totalDuration)
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
            updateModifierDirector(clampedDelta)
            updateGame(clampedDelta)
            updateTheme(clampedDelta)
            updateMirrorTransition(clampedDelta)
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
        warningFont.disposeSafely()
        jumpSound?.dispose()
        scoreSound?.dispose()
        deathSound?.dispose()
        warningSound?.dispose()
    }

    private fun resetGame() {
        birdCenterY = VIRTUAL_HEIGHT * 0.5f
        birdVelocity = 0f
        score = 0
        themeBlend = 0f
        mirrorBlend = 0f
        gameOver = false
        leaderboardEntries = emptyList()
        leaderboardStatus = ""
        submittingScore = false
        scoreSubmitted = false
        gravityReturnRelaxedPipesPrepared = false
        activeModifiers.clear()
        modifierDirector.reset(score)
        pipes.clear()

        var gapY = STARTING_GAP_Y
        repeat(PIPE_COUNT) { index ->
            if (index > 0) {
                gapY = nextPipeGapY(gapY)
            }

            pipes += Pipe(
                x = VIRTUAL_WIDTH + PIPE_START_OFFSET + PIPE_SPACING * index,
                gapY = gapY,
                gapHeight = PIPE_GAP
            )
        }
    }

    private fun flapOrRestart() {
        if (gameOver) {
            resetGame()
        }
        birdVelocity = -modifierState().gravityDirection * FLAP_SPEED
        playSound(jumpSound, JUMP_SOUND_VOLUME)
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
        val flippedGravityModifier = activeModifier(ModifierType.FLIPPED_GRAVITY)
        if (
            flippedGravityModifier != null &&
            !gravityReturnRelaxedPipesPrepared &&
            flippedGravityModifier.timeRemaining <= GRAVITY_RETURN_RELAX_SECONDS
        ) {
            relaxFullyOffscreenQueuedPipes()
            gravityReturnRelaxedPipesPrepared = true
        }
        val hadFlippedGravity = flippedGravityModifier != null

        activeModifiers.forEach { modifier ->
            modifier.timeRemaining -= delta
        }
        activeModifiers.removeAll { it.timeRemaining <= 0f }

        if (hadFlippedGravity && !hasModifier(ModifierType.FLIPPED_GRAVITY)) {
            gravityReturnRelaxedPipesPrepared = false
            normalizeFullyOffscreenQueuedPipes()
        }
    }

    private fun updateModifierDirector(delta: Float) {
        val hadPendingModifier = modifierDirector.pendingModifier != null
        val hadPendingGravityWarning = hasPendingGravityWarning()

        modifierDirector.update(
            delta = delta,
            score = score,
            activeModifierTypes = activeModifierTypes(),
            activateModifier = ::activateModifier
        )

        val pendingModifier = modifierDirector.pendingModifier
        if (!hadPendingModifier && pendingModifier != null) {
            playSound(warningSound, WARNING_SOUND_VOLUME)
            if (pendingModifier.type == ModifierType.FLIPPED_GRAVITY) {
                relaxFullyOffscreenQueuedPipes()
            }
        }

        if (hadPendingGravityWarning && !hasPendingGravityWarning() && hasModifier(ModifierType.FLIPPED_GRAVITY)) {
            gravityReturnRelaxedPipesPrepared = false
            normalizeFullyOffscreenQueuedPipes()
        }
    }

    private fun updateBird(delta: Float, modifiers: ModifierState) {
        birdVelocity += GRAVITY_ACCELERATION * modifiers.gravityDirection * delta
        birdCenterY += birdVelocity * delta
    }

    private fun updatePipes(delta: Float, modifiers: ModifierState) {
        pipes.forEach { pipe ->
            pipe.x -= PIPE_SPEED * modifiers.speedMultiplier * delta

            if (!pipe.scored && pipe.x + PIPE_WIDTH < birdLeftX(modifiers)) {
                pipe.scored = true
                score += 1
                playSound(scoreSound, SCORE_SOUND_VOLUME)
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

    private fun updateMirrorTransition(delta: Float) {
        val targetBlend = if (hasModifier(ModifierType.MIRROR_MODE)) 1f else 0f
        val step = delta / MIRROR_TRANSITION_SECONDS

        mirrorBlend = if (mirrorBlend < targetBlend) {
            min(targetBlend, mirrorBlend + step)
        } else {
            max(targetBlend, mirrorBlend - step)
        }
    }

    private fun recyclePipes() {
        val firstPipe = pipes.firstOrNull() ?: return
        if (firstPipe.x + PIPE_WIDTH >= 0f) return

        val farthestPipe = pipes.maxBy { it.x }
        pipes.removeAt(0)
        val gapHeight = nextPipeGapHeight()
        pipes += Pipe(
            x = farthestPipe.x + PIPE_SPACING,
            gapY = nextPipeGapY(farthestPipe.gapY, gapHeight),
            gapHeight = gapHeight
        )
    }

    private fun collidesWithBird(pipe: Pipe, modifiers: ModifierState): Boolean {
        val birdX = birdLeftX(modifiers)
        val birdY = birdBottomY(modifiers)
        val pipeX = pipe.x
        val birdRight = birdX + modifiers.birdSize
        val birdTop = birdY + modifiers.birdSize
        val pipeRight = pipeX + PIPE_WIDTH
        val overlapsPipeX = birdRight > pipeX && birdX < pipeRight
        val outsideGap = birdY < pipe.gapY || birdTop > pipe.gapY + pipe.gapHeight

        return overlapsPipeX && outsideGap
    }

    private fun isBirdOutOfBounds(modifiers: ModifierState): Boolean =
        birdBottomY(modifiers) < 0f || birdTopY(modifiers) > VIRTUAL_HEIGHT

    private fun endGame() {
        if (gameOver) return

        gameOver = true
        playSound(deathSound, DEATH_SOUND_VOLUME)
        saveHighScoreIfNeeded()
        loadLeaderboard()
    }

    private fun promptScoreSubmission() {
        if (submittingScore || scoreSubmitted || score <= 0) return

        Gdx.input.getTextInput(
            object : Input.TextInputListener {
                override fun input(text: String) {
                    submitScore(text)
                }

                override fun canceled() = Unit
            },
            "Submit Score",
            "",
            "Username"
        )
    }

    private fun submitScore(username: String) {
        val trimmedName = username.trim()
        if (trimmedName.isEmpty()) {
            leaderboardStatus = "Enter a name to submit"
            return
        }

        submittingScore = true
        leaderboardStatus = "Submitting score..."

        val request = Net.HttpRequest(Net.HttpMethods.POST).apply {
            url = LEADERBOARD_API_PATH
            setHeader("Content-Type", "application/json")
            content = """{"name":${trimmedName.toJsonString()},"score":$score}"""
        }

        Gdx.net.sendHttpRequest(request, object : Net.HttpResponseListener {
            override fun handleHttpResponse(response: Net.HttpResponse) {
                submittingScore = false

                if (response.status.statusCode !in 200..299) {
                    leaderboardStatus = "Leaderboard unavailable"
                    return
                }

                scoreSubmitted = true
                leaderboardStatus = "Score submitted"
                loadLeaderboard()
            }

            override fun failed(t: Throwable) {
                submittingScore = false
                leaderboardStatus = "Leaderboard unavailable"
            }

            override fun cancelled() {
                submittingScore = false
            }
        })
    }

    private fun loadLeaderboard() {
        leaderboardStatus = "Loading leaderboard..."

        val request = Net.HttpRequest(Net.HttpMethods.GET).apply {
            url = "$LEADERBOARD_API_PATH?limit=$LEADERBOARD_VISIBLE_ENTRIES"
        }

        Gdx.net.sendHttpRequest(request, object : Net.HttpResponseListener {
            override fun handleHttpResponse(response: Net.HttpResponse) {
                if (response.status.statusCode !in 200..299) {
                    leaderboardStatus = "Leaderboard unavailable"
                    return
                }

                leaderboardEntries = parseLeaderboard(response.resultAsString)
                leaderboardStatus = if (leaderboardEntries.isEmpty()) "No scores yet" else ""
            }

            override fun failed(t: Throwable) {
                leaderboardStatus = "Leaderboard unavailable"
            }

            override fun cancelled() = Unit
        })
    }

    private fun parseLeaderboard(json: String): List<LeaderboardEntry> {
        val root = JsonReader().parse(json)
        val entries = root.get("entries") ?: return emptyList()
        val parsedEntries = mutableListOf<LeaderboardEntry>()
        var entry = entries.child

        while (entry != null && parsedEntries.size < LEADERBOARD_VISIBLE_ENTRIES) {
            parsedEntries += LeaderboardEntry(
                name = entry.getString("name", "Player"),
                score = entry.getInt("score", 0)
            )
            entry = entry.next
        }

        return parsedEntries
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
            shapes.rect(pipeX, pipe.gapY + pipe.gapHeight, PIPE_WIDTH, VIRTUAL_HEIGHT - pipe.gapY - pipe.gapHeight)
        }
        shapes.rect(renderBirdX(modifiers), birdBottomY(modifiers), modifiers.birdSize, modifiers.birdSize)

        shapes.end()

        drawActiveModifierDurationBars(palette)
    }

    private fun drawActiveModifierDurationBars(palette: GameplayPalette) {
        if (gameOver) return

        val bars = activeModifierBars()
        if (bars.isEmpty()) return

        shapes.projectionMatrix = viewport.camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = palette.foreground
        drawRectangles(bars)
        shapes.end()

        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
        foregroundGeometry().forEach { foregroundRect ->
            val scissor = toScreenScissor(foregroundRect)
            if (scissor.width > 0f && scissor.height > 0f) {
                Gdx.gl.glScissor(scissor.x.toInt(), scissor.y.toInt(), scissor.width.toInt(), scissor.height.toInt())
                shapes.begin(ShapeRenderer.ShapeType.Filled)
                shapes.color = palette.background
                drawRectangles(bars)
                shapes.end()
            }
        }
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
    }

    private fun activeModifierBars(): List<Rectangle> =
        activeModifiers.mapIndexedNotNull { index, modifier ->
            if (modifier.duration <= 0f) return@mapIndexedNotNull null

            val progress = (modifier.timeRemaining / modifier.duration).coerceIn(0f, 1f)
            Rectangle(
                0f,
                DURATION_BAR_BOTTOM_PADDING + index * (DURATION_BAR_HEIGHT + DURATION_BAR_GAP),
                VIRTUAL_WIDTH * progress,
                DURATION_BAR_HEIGHT
            )
        }

    private fun drawRectangles(rectangles: List<Rectangle>) {
        rectangles.forEach { rectangle ->
            shapes.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height)
        }
    }

    private fun drawGameOverPlate() {
        shapes.projectionMatrix = viewport.camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color.WHITE
        shapes.rect(PLATE_X, PLATE_Y, PLATE_WIDTH, PLATE_HEIGHT)

        shapes.color = Color.BLACK
        drawRectOutline(PLATE_X, PLATE_Y, PLATE_WIDTH, PLATE_HEIGHT, BORDER_SIZE)
        drawRectOutline(submitScoreButton.x, submitScoreButton.y, submitScoreButton.width, submitScoreButton.height, BORDER_SIZE)
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

        if (!gameOver) {
            drawPendingModifierWarning(palette)
        }

        if (gameOver) {
            drawGameOverText()
        }

        batch.end()
    }

    private fun drawGameOverText() {
        drawCentered("Game Over", PLATE_Y + PLATE_HEIGHT - 38f, centerFont)
        drawCentered("Top Scores", PLATE_Y + PLATE_HEIGHT - 78f, hudFont)

        if (leaderboardEntries.isEmpty()) {
            drawCentered(leaderboardStatus.ifEmpty { "No scores yet" }, PLATE_Y + PLATE_HEIGHT - 106f, hudFont)
        } else {
            leaderboardEntries.forEachIndexed { index, entry ->
                drawCentered("${index + 1}. ${entry.name}: ${entry.score}", PLATE_Y + PLATE_HEIGHT - 106f - LEADERBOARD_LINE_SPACING * index, hudFont)
            }
        }

        if (leaderboardStatus.isNotEmpty() && leaderboardEntries.isNotEmpty()) {
            drawCentered(leaderboardStatus, submitScoreButton.y + BUTTON_HEIGHT + 19f, hudFont)
        }

        drawCentered(submitScoreButtonText(), submitScoreButton.y + 31f, hudFont)
        drawCentered("Retry", retryButton.y + 31f, hudFont)
        drawCentered("Main Menu", menuButton.y + 31f, hudFont)
    }

    private fun submitScoreButtonText(): String =
        when {
            submittingScore -> "Submitting..."
            scoreSubmitted -> "Submitted"
            else -> "Submit Score"
        }

    private fun drawPendingModifierWarning(palette: GameplayPalette) {
        val pendingModifier = modifierDirector.pendingModifier ?: return
        val alpha = warningAlpha(pendingModifier)
        if (alpha <= 0f) return

        val text = warningText(pendingModifier.type)
        layout.setText(warningFont, text)

        val warningLine = HudLine(
            text = text,
            x = (VIRTUAL_WIDTH - layout.width) * 0.5f,
            y = WARNING_TEXT_Y,
            font = warningFont
        )

        drawLines(listOf(warningLine), palette.foreground.withAlpha(alpha))
        drawLinesClippedToForegroundGeometry(listOf(warningLine), palette.background.withAlpha(alpha))
    }

    private fun warningAlpha(pendingModifier: PendingModifier): Float {
        val elapsed = ModifierDirector.WARNING_SECONDS - pendingModifier.warningTimeRemaining
        val fadeIn = min(1f, elapsed / WARNING_FADE_SECONDS)
        val fadeOut = min(1f, pendingModifier.warningTimeRemaining / WARNING_FADE_SECONDS)
        return smoothStep(min(fadeIn, fadeOut))
    }

    private fun warningText(type: ModifierType): String =
        when (type) {
            ModifierType.FLIPPED_GRAVITY -> "GRAVITY FLIP INCOMING"
            ModifierType.SLOW_SPEED -> "SLOW MOTION"
            ModifierType.NORMAL_SPEED -> "NORMAL SPEED"
            ModifierType.FAST_SPEED -> "SPEED UP"
            ModifierType.MIRROR_MODE -> "MIRROR MODE"
            ModifierType.SMALL_SIZE -> "SMALL BOX"
            ModifierType.NORMAL_SIZE -> "NORMAL BOX"
            ModifierType.BIG_SIZE -> "BIG BOX"
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
            rectangles += Rectangle(pipeX, pipe.gapY + pipe.gapHeight, PIPE_WIDTH, VIRTUAL_HEIGHT - pipe.gapY - pipe.gapHeight)
        }
        rectangles += Rectangle(renderBirdX(modifiers), birdBottomY(modifiers), modifiers.birdSize, modifiers.birdSize)
        return rectangles
    }

    private fun modifierState(): ModifierState =
        ModifierState(
            gravityDirection = if (hasModifier(ModifierType.FLIPPED_GRAVITY)) 1f else -1f,
            speedMultiplier = speedMultiplier(),
            mirrorBlend = mirrorBlend,
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

    private fun activeModifier(type: ModifierType): ActiveModifier? =
        activeModifiers.firstOrNull { it.type == type }

    private fun hasPendingGravityWarning(): Boolean =
        modifierDirector.pendingModifier?.type == ModifierType.FLIPPED_GRAVITY

    private fun activeModifierTypes(): Set<ModifierType> =
        activeModifiers.mapTo(mutableSetOf()) { it.type }

    private fun birdLeftX(modifiers: ModifierState): Float =
        BIRD_CENTER_X - modifiers.birdSize * 0.5f

    private fun birdBottomY(modifiers: ModifierState): Float =
        birdCenterY - modifiers.birdSize * 0.5f

    private fun birdTopY(modifiers: ModifierState): Float =
        birdCenterY + modifiers.birdSize * 0.5f

    private fun renderBirdX(modifiers: ModifierState): Float {
        val birdX = birdLeftX(modifiers)
        return renderMirroredX(birdX, modifiers.birdSize, modifiers)
    }

    private fun renderPipeX(pipe: Pipe, modifiers: ModifierState): Float =
        renderMirroredX(pipe.x, PIPE_WIDTH, modifiers)

    private fun renderMirroredX(x: Float, width: Float, modifiers: ModifierState): Float {
        val mirroredX = VIRTUAL_WIDTH - x - width
        val progress = easeInOutExpo(modifiers.mirrorBlend)
        return x + (mirroredX - x) * progress
    }

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

    private fun easeInOutExpo(value: Float): Float =
        when {
            value <= 0f -> 0f
            value >= 1f -> 1f
            value < 0.5f -> Math.pow(2.0, (20f * value - 10f).toDouble()).toFloat() * 0.5f
            else -> (2f - Math.pow(2.0, (-20f * value + 10f).toDouble()).toFloat()) * 0.5f
        }

    private fun Color.withAlpha(alpha: Float): Color =
        Color(r, g, b, alpha)

    private fun drawCentered(text: String, y: Float, textFont: BitmapFont) {
        textFont.color = Color.BLACK
        layout.setText(textFont, text)
        textFont.draw(batch, text, (VIRTUAL_WIDTH - layout.width) * 0.5f, y)
    }

    private fun nextPipeGapHeight(): Float {
        if (hasGravityRelaxedPipeWindow()) return RELAXED_PIPE_GAP

        return PIPE_GAP
    }

    private fun hasGravityRelaxedPipeWindow(): Boolean =
        hasPendingGravityWarning() || isGravityReturnRelaxWindow()

    private fun isGravityReturnRelaxWindow(): Boolean =
        activeModifier(ModifierType.FLIPPED_GRAVITY)?.timeRemaining
            ?.let { timeRemaining -> timeRemaining <= GRAVITY_RETURN_RELAX_SECONDS } == true

    private fun relaxFullyOffscreenQueuedPipes(maxCount: Int = Int.MAX_VALUE): Int {
        var relaxedCount = 0
        pipes
            .filter { pipe -> pipe.x >= VIRTUAL_WIDTH && pipe.gapHeight != RELAXED_PIPE_GAP }
            .sortedBy { pipe -> pipe.x }
            .take(maxCount)
            .forEach { pipe ->
                pipe.gapY = centeredGapY(RELAXED_PIPE_GAP)
                pipe.gapHeight = RELAXED_PIPE_GAP
                relaxedCount += 1
            }

        return relaxedCount
    }

    private fun normalizeFullyOffscreenQueuedPipes() {
        pipes
            .filter { pipe -> pipe.x >= VIRTUAL_WIDTH && pipe.gapHeight == RELAXED_PIPE_GAP }
            .forEach { pipe ->
                pipe.gapY = nextPipeGapY(pipe.gapY)
                pipe.gapHeight = PIPE_GAP
            }
    }

    private fun nextPipeGapY(previousGapY: Float, gapHeight: Float = PIPE_GAP): Float {
        if (gapHeight == RELAXED_PIPE_GAP) return centeredGapY(gapHeight)

        val maxStep = pipeGapMaxStep()
        val minGapY = max(MIN_GAP_Y, previousGapY - maxStep)
        val maxGapY = min(maxGapY(gapHeight), previousGapY + maxStep)
        return MathUtils.random(minGapY, maxGapY)
    }

    private fun centeredGapY(gapHeight: Float): Float =
        (VIRTUAL_HEIGHT - gapHeight) * 0.5f

    private fun maxGapY(gapHeight: Float): Float =
        VIRTUAL_HEIGHT - MIN_GAP_Y - gapHeight

    private fun pipeGapMaxStep(): Float =
        when {
            score < 10 -> PIPE_GAP_STEP_SCORE_0
            score < 20 -> PIPE_GAP_STEP_SCORE_10
            score < 35 -> PIPE_GAP_STEP_SCORE_20
            score < 50 -> PIPE_GAP_STEP_SCORE_35
            else -> PIPE_GAP_STEP_SCORE_50
        }

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

    private fun createSound(soundFilePath: String): Sound? {
        val soundFile = Gdx.files.internal(soundFilePath)
        return if (soundFile.exists()) Gdx.audio.newSound(soundFile) else null
    }

    private fun playSound(sound: Sound?, volume: Float) {
        sound?.play(volume)
    }

    private fun String.toJsonString(): String =
        buildString {
            append('"')
            this@toJsonString.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
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
        val mirrorBlend: Float,
        val birdSize: Float
    )

    private data class Pipe(
        var x: Float,
        var gapY: Float,
        var gapHeight: Float,
        var scored: Boolean = false
    )

    private data class LeaderboardEntry(
        val name: String,
        val score: Int
    )

    companion object {
        private const val VIRTUAL_WIDTH = MainMenuScreen.VIRTUAL_WIDTH
        private const val VIRTUAL_HEIGHT = MainMenuScreen.VIRTUAL_HEIGHT
        private const val BIRD_BASE_X = 82f
        private const val NORMAL_BIRD_SIZE = 24f
        private const val BIRD_CENTER_X = BIRD_BASE_X + NORMAL_BIRD_SIZE * 0.5f
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
        private const val RELAXED_PIPE_GAP = 285f
        private const val GRAVITY_RETURN_RELAX_SECONDS = 3.75f
        private const val PIPE_SPEED = 138f
        private const val PIPE_SPACING = 155f
        private const val PIPE_START_OFFSET = 112f
        private const val PIPE_COUNT = 5
        private const val MIN_GAP_Y = 70f
        private const val STARTING_GAP_Y = (VIRTUAL_HEIGHT - PIPE_GAP) * 0.5f
        private const val PIPE_GAP_STEP_SCORE_0 = 34f
        private const val PIPE_GAP_STEP_SCORE_10 = 46f
        private const val PIPE_GAP_STEP_SCORE_20 = 62f
        private const val PIPE_GAP_STEP_SCORE_35 = 80f
        private const val PIPE_GAP_STEP_SCORE_50 = 105f
        private const val HUD_PADDING = 12f
        private const val HUD_TOP_PADDING = 16f
        private const val HUD_LINE_SPACING = 26f
        private const val HUD_FONT_SCALE = 0.48f
        private const val CENTER_FONT_SCALE = 0.78f
        private const val MAX_DELTA = 1f / 30f
        private const val PREFERENCES_NAME = "flappy-box"
        private const val HIGH_SCORE_KEY = "high-score"
        private const val HUD_FONT_FILE = "fonts/carlito-splash.fnt"
        private const val WARNING_FONT_FILE = "fonts/arial-warning.fnt"
        private const val JUMP_SOUND_FILE = "sounds/jump.wav"
        private const val SCORE_SOUND_FILE = "sounds/score.wav"
        private const val DEATH_SOUND_FILE = "sounds/death.wav"
        private const val WARNING_SOUND_FILE = "sounds/warning.wav"
        private const val JUMP_SOUND_VOLUME = 0.35f
        private const val SCORE_SOUND_VOLUME = 0.38f
        private const val DEATH_SOUND_VOLUME = 0.42f
        private const val WARNING_SOUND_VOLUME = 0.34f
        private const val LEADERBOARD_API_PATH = "/api/leaderboard"
        private const val LEADERBOARD_VISIBLE_ENTRIES = 3
        private const val LEADERBOARD_LINE_SPACING = 22f
        private const val THEME_SCORE_INTERVAL = 50
        private const val THEME_TRANSITION_SECONDS = 1.35f
        private const val MIRROR_TRANSITION_SECONDS = 0.7f
        private const val WARNING_FADE_SECONDS = 0.65f
        private const val WARNING_TEXT_Y = VIRTUAL_HEIGHT * 0.62f
        private const val WARNING_FONT_SCALE = 0.44f
        private const val PLATE_WIDTH = 240f
        private const val PLATE_HEIGHT = 326f
        private const val PLATE_X = (VIRTUAL_WIDTH - PLATE_WIDTH) * 0.5f
        private const val PLATE_Y = (VIRTUAL_HEIGHT - PLATE_HEIGHT) * 0.5f
        private const val BUTTON_WIDTH = 170f
        private const val BUTTON_HEIGHT = 44f
        private const val BUTTON_X = (VIRTUAL_WIDTH - BUTTON_WIDTH) * 0.5f
        private const val BUTTON_SUBMIT_Y = PLATE_Y + 128f
        private const val BUTTON_RETRY_Y = PLATE_Y + 76f
        private const val BUTTON_MENU_Y = PLATE_Y + 24f
        private const val BORDER_SIZE = 2f
        private const val DURATION_BAR_HEIGHT = 4f
        private const val DURATION_BAR_GAP = 3f
        private const val DURATION_BAR_BOTTOM_PADDING = 0f

        private val SPEED_MODIFIERS = setOf(
            ModifierType.SLOW_SPEED,
            ModifierType.NORMAL_SPEED,
            ModifierType.FAST_SPEED
        )
    }
}
