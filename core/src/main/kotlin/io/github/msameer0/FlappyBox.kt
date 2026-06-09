package io.github.msameer0

import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.async.KtxAsync

class FlappyBox : KtxGame<KtxScreen>() {
    override fun create() {
        KtxAsync.initiate()

        addScreen(MainMenuScreen(this))
        addScreen(GameScreen(this))
        addScreen(LeaderboardScreen(this))
        setScreen<MainMenuScreen>()
    }
}
