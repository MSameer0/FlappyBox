package io.github.msameer0

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Matrix4
import ktx.assets.disposeSafely
import kotlin.math.min

class PhoneFrameRenderer(
    private val virtualWidth: Float,
    private val virtualHeight: Float
) {
    private val batch = SpriteBatch()
    private val projection = Matrix4()
    private val whitePixel = createPixelTexture(Color.WHITE)

    fun draw() {
        clearDesktopBackground()
        drawPhoneShadow()
    }

    fun dispose() {
        batch.disposeSafely()
        whitePixel.disposeSafely()
    }

    private fun clearDesktopBackground() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }

    private fun drawPhoneShadow() {
        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        if (screenWidth <= 0f || screenHeight <= 0f) return

        val scale = min(screenWidth / virtualWidth, screenHeight / virtualHeight)
        val phoneWidth = virtualWidth * scale
        val phoneHeight = virtualHeight * scale
        val phoneX = (screenWidth - phoneWidth) * 0.5f
        val phoneY = (screenHeight - phoneHeight) * 0.5f

        projection.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        batch.projectionMatrix = projection
        batch.begin()
        drawShadowLayer(phoneX + 4f, phoneY - 4f, phoneWidth, phoneHeight, 0.06f)
        drawShadowLayer(phoneX + 8f, phoneY - 8f, phoneWidth, phoneHeight, 0.035f)
        drawShadowLayer(phoneX + 12f, phoneY - 12f, phoneWidth, phoneHeight, 0.018f)
        batch.end()
    }

    private fun drawShadowLayer(x: Float, y: Float, width: Float, height: Float, alpha: Float) {
        batch.color = Color(0f, 0f, 0f, alpha)
        batch.draw(whitePixel, x, y, width, height)
        batch.color = Color.WHITE
    }

    private fun createPixelTexture(color: Color): Texture {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()
        return Texture(pixmap).also {
            pixmap.dispose()
        }
    }
}
