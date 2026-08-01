package com.novaempire.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.novaempire.core.domain.models.TerrainType
import com.novaempire.core.domain.theme.HexColor
import com.novaempire.core.domain.theme.MapColors

/**
 * Palette de la carte tactique, convertie en couleurs Compose.
 *
 * Les fonctions de dessin sont des extensions de `DrawScope` : elles ne peuvent pas lire de
 * `CompositionLocal`, la palette leur est donc passée en paramètre — comme l'était déjà
 * `GraphicsConfig` pour `drawPlanet`.
 */
data class MapPalette(
    val void: Color,
    val asteroids: Color,
    val asteroidRock: Color,
    val nebula: Color,
    val nebulaHaze: Color,
    val planet: Color,
    val blackHole: Color,
    val wormhole: Color,
    val plasmaCloud: Color,
    val ionStorm: Color,
    val anomaly: Color,
    val ink: Color,
    val unexplored: Color,
    val explosionCore: Color,
    val explosionMid: Color,
    val explosionEdge: Color,
    val healthBarBackground: Color
) {
    /** Fond d'un hexagone selon son terrain. */
    fun terrainColor(terrain: TerrainType): Color = when (terrain) {
        TerrainType.EMPTY -> void
        TerrainType.ASTEROIDS -> asteroids
        TerrainType.NEBULA -> nebula
        TerrainType.PLANET -> planet
        TerrainType.BLACK_HOLE -> blackHole
        TerrainType.WORMHOLE -> wormhole
        TerrainType.PLASMA_CLOUD -> plasmaCloud
        TerrainType.ION_STORM -> ionStorm
        TerrainType.ANOMALY -> anomaly
    }

    companion object {
        /**
         * Chaque couleur illisible retombe sur sa valeur historique plutôt que de faire échouer le
         * thème entier — même principe que pour les rôles Material.
         */
        fun from(colors: MapColors): MapPalette {
            val fallback = MapColors()
            fun color(value: String, fallbackValue: String): Color =
                Color(HexColor.parse(value) ?: HexColor.parse(fallbackValue)!!)

            return MapPalette(
                void = color(colors.void, fallback.void),
                asteroids = color(colors.asteroids, fallback.asteroids),
                asteroidRock = color(colors.asteroidRock, fallback.asteroidRock),
                nebula = color(colors.nebula, fallback.nebula),
                nebulaHaze = color(colors.nebulaHaze, fallback.nebulaHaze),
                planet = color(colors.planet, fallback.planet),
                blackHole = color(colors.blackHole, fallback.blackHole),
                wormhole = color(colors.wormhole, fallback.wormhole),
                plasmaCloud = color(colors.plasmaCloud, fallback.plasmaCloud),
                ionStorm = color(colors.ionStorm, fallback.ionStorm),
                anomaly = color(colors.anomaly, fallback.anomaly),
                ink = color(colors.ink, fallback.ink),
                unexplored = color(colors.unexplored, fallback.unexplored),
                explosionCore = color(colors.explosionCore, fallback.explosionCore),
                explosionMid = color(colors.explosionMid, fallback.explosionMid),
                explosionEdge = color(colors.explosionEdge, fallback.explosionEdge),
                healthBarBackground = color(colors.healthBarBackground, fallback.healthBarBackground)
            )
        }

        /** Palette historique, utilisée tant qu'aucun thème n'est chargé. */
        val DEFAULT = from(MapColors())
    }
}
