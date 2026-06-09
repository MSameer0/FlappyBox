package io.github.msameer0

import kotlin.random.Random

class ModifierDirector(private val seed: Int = DEFAULT_SEED) {
    private var random = Random(seed)
    private var timeUntilNextModifier = 0f
    private var pendingDuration = 0f
    var pendingModifier: PendingModifier? = null
        private set

    init {
        reset()
    }

    fun reset(score: Int = 0) {
        random = Random(seed)
        timeUntilNextModifier = randomIn(difficultyFor(score).gapRange)
        pendingDuration = 0f
        pendingModifier = null
    }

    fun update(
        delta: Float,
        score: Int,
        activeModifierTypes: Set<ModifierType>,
        activateModifier: (ModifierType, Float) -> Unit
    ) {
        pendingModifier?.let { pending ->
            pending.warningTimeRemaining -= delta
            if (pending.warningTimeRemaining <= 0f) {
                val difficulty = difficultyFor(score)
                if (pending.type !in activeModifierTypes && activeModifierTypes.size < difficulty.maxActiveModifiers) {
                    activateModifier(pending.type, pendingDuration)
                }
                pendingModifier = null
                pendingDuration = 0f
                timeUntilNextModifier = randomIn(difficultyFor(score).gapRange)
            }
            return
        }

        val difficulty = difficultyFor(score)
        if (activeModifierTypes.size >= difficulty.maxActiveModifiers) return

        val candidates = difficulty.allowedModifiers.filter { modifier ->
            modifier !in activeModifierTypes && !modifier.conflictsWith(activeModifierTypes)
        }
        if (candidates.isEmpty()) return

        timeUntilNextModifier -= delta
        if (timeUntilNextModifier > 0f) return

        pendingModifier = PendingModifier(candidates.random(), WARNING_SECONDS)
        pendingDuration = randomIn(difficulty.durationRange)
    }

    private fun List<ModifierType>.random(): ModifierType =
        this[random.nextInt(size)]

    private fun randomIn(range: SecondsRange): Float =
        random.nextFloat() * (range.max - range.min) + range.min

    private fun ModifierType.conflictsWith(activeModifierTypes: Set<ModifierType>): Boolean =
        this in SPEED_MODIFIERS && activeModifierTypes.any { it in SPEED_MODIFIERS }

    private fun difficultyFor(score: Int): ModifierDifficulty =
        when {
            score < 10 -> ModifierDifficulty(
                allowedModifiers = listOf(
                    ModifierType.SLOW_SPEED,
                    ModifierType.SMALL_SIZE
                ),
                durationRange = SecondsRange(4f, 6f),
                gapRange = SecondsRange(10f, 15f),
                maxActiveModifiers = 1
            )
            score < 25 -> ModifierDifficulty(
                allowedModifiers = listOf(
                    ModifierType.SLOW_SPEED,
                    ModifierType.SMALL_SIZE,
                    ModifierType.FAST_SPEED,
                    ModifierType.BIG_SIZE
                ),
                durationRange = SecondsRange(5f, 8f),
                gapRange = SecondsRange(8f, 12f),
                maxActiveModifiers = 1
            )
            score < 50 -> ModifierDifficulty(
                allowedModifiers = listOf(
                    ModifierType.SLOW_SPEED,
                    ModifierType.SMALL_SIZE,
                    ModifierType.FAST_SPEED,
                    ModifierType.BIG_SIZE,
                    ModifierType.FLIPPED_GRAVITY
                ),
                durationRange = SecondsRange(6f, 9f),
                gapRange = SecondsRange(6f, 10f),
                maxActiveModifiers = 2
            )
            else -> ModifierDifficulty(
                allowedModifiers = listOf(
                    ModifierType.SLOW_SPEED,
                    ModifierType.SMALL_SIZE,
                    ModifierType.FAST_SPEED,
                    ModifierType.BIG_SIZE,
                    ModifierType.FLIPPED_GRAVITY,
                    ModifierType.MIRROR_MODE
                ),
                durationRange = SecondsRange(7f, 12f),
                gapRange = SecondsRange(4f, 8f),
                maxActiveModifiers = 2
            )
        }

    private data class ModifierDifficulty(
        val allowedModifiers: List<ModifierType>,
        val durationRange: SecondsRange,
        val gapRange: SecondsRange,
        val maxActiveModifiers: Int
    )

    private data class SecondsRange(
        val min: Float,
        val max: Float
    )

    companion object {
        private const val DEFAULT_SEED = 0xF1A99B0
        const val WARNING_SECONDS = 3f

        private val SPEED_MODIFIERS = setOf(
            ModifierType.SLOW_SPEED,
            ModifierType.NORMAL_SPEED,
            ModifierType.FAST_SPEED
        )
    }
}
