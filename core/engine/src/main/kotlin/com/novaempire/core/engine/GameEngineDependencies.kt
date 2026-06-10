package com.novaempire.core.engine

data class GameEngineDependencies(
    val combatSystem: CombatSystem = CombatResolver,
    val aiStrategy: AIStrategy = UtilityEvaluator,
)
