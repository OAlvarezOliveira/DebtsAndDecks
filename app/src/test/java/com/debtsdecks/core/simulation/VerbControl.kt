package com.debtsdecks.core.simulation

import com.debtsdecks.core.enemies.EnemyDefinition
import com.debtsdecks.core.enemies.IntentStep
import com.debtsdecks.core.enemies.IntentType

/**
 * FV calibration control: the same 8-slot enemy pattern with HEDGE and FORECLOSE switched OFF,
 * each verb slot reverted to the intent it announced before d94b155 landed (FORECLOSE -> ATTACK 9
 * on loan_shark, HEDGE -> MULTI_ATTACK 7x2 on collector, per the pre-verb all.json). Same pattern
 * shape, verbs absent. Shared by the re-metriced E1 gate and the control measurement.
 */
object VerbControl {

    fun verbsOffControl(enemies: List<EnemyDefinition>): List<EnemyDefinition> {
        val predecessorStep = mapOf(
            "loan_shark" to (IntentType.FORECLOSE to IntentStep(IntentType.ATTACK, damage = 9)),
            "collector" to (IntentType.HEDGE to IntentStep(IntentType.MULTI_ATTACK, damage = 7, param = 2)),
        )
        return enemies.map { def ->
            val swap = predecessorStep[def.id] ?: return@map def
            def.copy(
                intentPattern = def.intentPattern.map { step ->
                    if (step.type == swap.first) swap.second else step
                }
            )
        }
    }
}