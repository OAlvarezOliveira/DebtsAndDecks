package com.debtsdecks.core.model

import com.debtsdecks.core.enemies.EnemyInstance
import kotlinx.serialization.Serializable

@Serializable
data class EnemyState(
    val id: String,
    val defId: String,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val block: Int,
    val strength: Int,
    val weak: Int,
    val vulnerable: Int,
    val poison: Int,
    val intentType: String,
    val intentDamage: Int,
    val intentParam: Int,
    val intentDisplayName: String,
    val intentIconName: String
) {
    companion object {
        fun fromInstance(enemy: EnemyInstance): EnemyState {
            val intent = enemy.currentIntent()
            return EnemyState(
                id = enemy.id,
                defId = enemy.definition.id,
                name = enemy.name,
                hp = enemy.hp,
                maxHp = enemy.maxHp,
                block = enemy.block,
                strength = enemy.strength,
                weak = enemy.weak,
                vulnerable = enemy.vulnerable,
                poison = enemy.poison,
                intentType = intent.type.name,
                intentDamage = intent.damage,
                intentParam = intent.param,
                intentDisplayName = intent.displayName,
                intentIconName = intent.iconName
            )
        }
    }

    val hpPercent: Float
        get() = if (maxHp > 0) hp.toFloat() / maxHp else 0f
}