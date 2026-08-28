package com.debtsdecks.core.model

import kotlinx.serialization.Serializable

/**
 * F2 districts: a named stretch of the run. Purely descriptive — a district carries no combat,
 * economy or reward value, so adding one cannot move the balance gate. [EncounterSlot] points at a
 * district by id; the district owns only its identity and its player-facing text.
 *
 * [name] and [description] hold i18n bundle keys, never prose, mirroring `enemies/all.json` and
 * `cards/all.json` (see [com.debtsdecks.core.i18n.Localizer]). Catalog: `assets/districts/all.json`.
 */
@Serializable
data class District(
    val id: String,
    val name: String,
    val description: String
)
