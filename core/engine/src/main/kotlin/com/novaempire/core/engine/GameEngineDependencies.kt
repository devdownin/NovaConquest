package com.novaempire.core.engine

import kotlin.random.Random

data class GameEngineDependencies(
    val combatSystem: CombatSystem = CombatResolver,
    val aiStrategy: AIStrategy = UtilityEvaluator,
    /** Injectable RNG so reducer side-effects (exploration discoveries) stay deterministic/testable. */
    val rng: Random = Random.Default,
)
