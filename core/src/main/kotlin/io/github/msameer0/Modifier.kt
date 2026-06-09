package io.github.msameer0

enum class ModifierType {
    FLIPPED_GRAVITY,
    SLOW_SPEED,
    NORMAL_SPEED,
    FAST_SPEED,
    MIRROR_MODE,
    SMALL_SIZE,
    NORMAL_SIZE,
    BIG_SIZE
}

data class ActiveModifier(
    val type: ModifierType,
    var timeRemaining: Float
)

data class PendingModifier(
    val type: ModifierType,
    var warningTimeRemaining: Float
)
