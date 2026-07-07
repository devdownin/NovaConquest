package com.novaempire.app.ui.screens

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GameUnit

interface UnitRenderer {
    fun drawUnit(drawScope: DrawScope, x: Float, y: Float, unit: GameUnit, size: Float, factionColor: androidx.compose.ui.graphics.Color)
}

interface EnvironmentRenderer {
    fun drawPlanet(drawScope: DrawScope, x: Float, y: Float, hexRadius: Float, owner: Faction?)
    fun drawAsteroids(drawScope: DrawScope, x: Float, y: Float, hexRadius: Float)
    fun drawNebula(drawScope: DrawScope, x: Float, y: Float, hexRadius: Float)
    fun drawBlackHole(drawScope: DrawScope, x: Float, y: Float, hexRadius: Float)
    fun drawWormhole(drawScope: DrawScope, x: Float, y: Float, hexRadius: Float)
    fun drawPlasmaCloud(drawScope: DrawScope, x: Float, y: Float, hexRadius: Float)
    fun drawIonStorm(drawScope: DrawScope, x: Float, y: Float, hexRadius: Float)
    fun drawAnomaly(drawScope: DrawScope, x: Float, y: Float, hexRadius: Float)
}
