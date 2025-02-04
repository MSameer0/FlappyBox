package com.sam.flappybox

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import kotlin.random.Random
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GameView(this))
    }
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val paint = Paint()
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var birdX: Float = 0f
    private var birdY: Float = 0f
    private var birdDy: Float = 0f
    private val birdSize: Float
    private val gravity: Float
    private val jumpVelocity: Float

    private val pipes = mutableListOf<RectF>()
    private val passedPipes = mutableSetOf<RectF>()
    private var playing = false
    private var score = 0
    private val messages = listOf(
        "made in 1 day",
        "flappy bird ripoff smh",
        "do NOT play Fortnite",
        "only 213 lines of code",
        "do NOT report any bugs",
        "Android > iOS",
        "ive played these games before",
        "imagine if there were ads in this",
        "made using no textures",
        "this message has 10% chance to appear"
    )
    private var randomMessage: String = messages.random()

    private val playButton = RectF()
    private val quitButton = RectF()

    init {
        holder.addCallback(this)

        screenWidth = Resources.getSystem().displayMetrics.widthPixels
        screenHeight = Resources.getSystem().displayMetrics.heightPixels

        birdSize = minOf(screenWidth, screenHeight) / 20f
        gravity = screenHeight / 600f
        jumpVelocity = -screenHeight / 75f

        birdX = screenWidth / 4f
        birdY = screenHeight / 2f

        createPipe()
    }

    private fun createPipe() {
        val pipeWidth = screenWidth / 37f
        val pipeHeight = Random.nextInt(screenHeight / 5, screenHeight / 2)
        val gap = screenHeight / 7

        pipes.add(RectF(screenWidth.toFloat(), 0f, screenWidth + pipeWidth, pipeHeight.toFloat()))
        pipes.add(RectF(
            screenWidth.toFloat(),
            (pipeHeight + gap).toFloat(),
            screenWidth + pipeWidth,
            screenHeight.toFloat()
        ))
    }

    private fun resetGame() {
        birdY = screenHeight / 2f
        birdDy = 0f
        pipes.clear()
        passedPipes.clear()
        createPipe()
        score = 0
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val gameThread = Thread {
            while (true) {
                val canvas = holder.lockCanvas()
                canvas?.let {
                    draw(it)
                    holder.unlockCanvasAndPost(it)
                }
                Thread.sleep(16)
            }
        }
        gameThread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!playing) {
                    if (playButton.contains(event.x, event.y)) {
                        playing = true
                        resetGame()
                    } else if (quitButton.contains(event.x, event.y)) {
                        exitProcess(0)
                    }
                } else {
                    birdDy = jumpVelocity
                }
            }
        }
        return true
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        canvas.drawColor(Color.WHITE)
        paint.color = Color.BLACK

        if (playing) {
            birdDy += gravity
            birdY += birdDy

            canvas.drawRect(birdX, birdY, birdX + birdSize, birdY + birdSize, paint)

            for (i in pipes.indices) {
                pipes[i].left -= screenWidth / 120f
                pipes[i].right -= screenWidth / 120f
                canvas.drawRect(pipes[i], paint)
            }

            if (pipes.last().left < screenWidth - screenWidth / 3f) {
                createPipe()
            }

            pipes.removeAll { it.right < 0 }

            val birdRect = RectF(birdX, birdY, birdX + birdSize, birdY + birdSize)
            val collision = pipes.any { pipe ->
                birdRect.intersects(pipe.left, pipe.top, pipe.right, pipe.bottom)
            }

            val outOfBounds = birdY < 0 || birdY + birdSize > screenHeight

            if (collision || outOfBounds) {
                playing = false
                randomMessage = messages.random()
            }

            val pipesToRemove = mutableListOf<RectF>()

            pipes.indices.step(2).forEach { i ->
                val upperPipe = pipes[i]
                val lowerPipe = pipes[i + 1]

                if (birdX > upperPipe.right) {
                    if (!passedPipes.contains(upperPipe)) {
                        score++
                        passedPipes.add(upperPipe)

                        pipesToRemove.add(upperPipe)
                        pipesToRemove.add(lowerPipe)
                    }
                }
            }

            pipes.removeAll(pipesToRemove)

        } else {
            paint.textAlign = Paint.Align.CENTER

            paint.textSize = 115f
            canvas.drawText("FlappyBox", screenWidth / 2f, screenHeight / 3f, paint)

            paint.textSize = 33f
            canvas.drawText(randomMessage, screenWidth / 2f, screenHeight / 2.5f, paint)

            paint.textSize = 55f
            val playX = screenWidth / 2f
            val playY = screenHeight / 2f
            canvas.drawText("Play", playX, playY, paint)
            playButton.set(playX - 100f, playY - 60f, playX + 100f, playY + 20f)

            val quitY = playY + 80f
            canvas.drawText("Quit", playX, quitY, paint)
            quitButton.set(playX - 100f, quitY - 60f, playX + 100f, quitY + 20f)
        }

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 45f
        paint.typeface = ResourcesCompat.getFont(context, R.font.nimbussanbol)
        canvas.drawText("Score: $score", 20f, 50f, paint)
    }
}