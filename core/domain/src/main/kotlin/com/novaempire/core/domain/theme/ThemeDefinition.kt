package com.novaempire.core.domain.theme

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Les douze rôles Material décrits par un JSON de thème, sous forme de chaînes `#AARRGGBB`. */
@Serializable
data class ThemeColors(
    val primary: String,
    val secondary: String,
    val tertiary: String,
    val background: String,
    val surface: String,
    val surfaceVariant: String,
    val onPrimary: String,
    val onSecondary: String,
    val onTertiary: String,
    val onBackground: String,
    val onSurface: String,
    val onSurfaceVariant: String
) {
    /** Les douze rôles sous forme (nom, valeur), pour valider ou convertir sans les réécrire. */
    fun asPairs(): List<Pair<String, String>> = listOf(
        "primary" to primary,
        "secondary" to secondary,
        "tertiary" to tertiary,
        "background" to background,
        "surface" to surface,
        "surfaceVariant" to surfaceVariant,
        "onPrimary" to onPrimary,
        "onSecondary" to onSecondary,
        "onTertiary" to onTertiary,
        "onBackground" to onBackground,
        "onSurface" to onSurface,
        "onSurfaceVariant" to onSurfaceVariant
    )
}

/**
 * Palette de la carte tactique — l'écran que le joueur regarde le plus.
 *
 * Elle était entièrement codée en dur dans `TacticalMapScreen`, si bien que changer de thème
 * transformait l'interface mais laissait les hexagones identiques. Chaque champ a pour valeur par
 * défaut la couleur historique : un thème qui omet la section `terrain` reste donc valide et rend
 * exactement comme avant.
 */
@Serializable
data class MapColors(
    /** Fond d'un hexagone vide. */
    val void: String = "#FF181210",
    val asteroids: String = "#FF241C14",
    /** Silhouette des rochers dessinés par-dessus un hexagone d'astéroïdes. */
    val asteroidRock: String = "#FF2A1C10",
    val nebula: String = "#FF261530",
    /** Nuage de brume dessiné par-dessus un hexagone de nébuleuse. */
    val nebulaHaze: String = "#FF3D2848",
    val planet: String = "#FF162018",
    val blackHole: String = "#FF1A0A00",
    val wormhole: String = "#FF12152A",
    val plasmaCloud: String = "#FF2A1208",
    val ionStorm: String = "#FF20202E",
    val anomaly: String = "#FF142218",
    /** Encre des contours — le trait noir de la direction artistique BD. */
    val ink: String = "#FF130F0A",
    /** Hexagone jamais exploré. */
    val unexplored: String = "#FF0D0A07",
    val explosionCore: String = "#FF8B3A0A",
    val explosionMid: String = "#FF3D1A06",
    val explosionEdge: String = "#FF1A0D04",
    /** Fond de la jauge de points de vie affichée sous les unités. */
    val healthBarBackground: String = "#FF2D2620"
) {
    fun asPairs(): List<Pair<String, String>> = listOf(
        "void" to void,
        "asteroids" to asteroids,
        "asteroidRock" to asteroidRock,
        "nebula" to nebula,
        "nebulaHaze" to nebulaHaze,
        "planet" to planet,
        "blackHole" to blackHole,
        "wormhole" to wormhole,
        "plasmaCloud" to plasmaCloud,
        "ionStorm" to ionStorm,
        "anomaly" to anomaly,
        "ink" to ink,
        "unexplored" to unexplored,
        "explosionCore" to explosionCore,
        "explosionMid" to explosionMid,
        "explosionEdge" to explosionEdge,
        "healthBarBackground" to healthBarBackground
    )
}

/** Réglages de rendu pilotés par le thème. */
@Serializable
data class GraphicsConfig(
    /** Épaisseur du trait d'encre autour des éléments de carte. */
    val outlineStrokeWidth: Float,
    /** Opacité des hachures d'ombrage des planètes, 0..1. */
    val planetShadowAlpha: Float,
    /** Rayon du flou « verre dépoli » des panneaux. */
    val blurRadius: Float,
    /** Multiplicateur du nombre d'éclats projetés par une explosion. */
    val particleCountMultiplier: Float
)

@Serializable
data class ThemeDefinition(
    val name: String,
    val colors: ThemeColors,
    val graphics: GraphicsConfig,
    /** Optionnelle : un thème qui ne la fournit pas hérite de la palette historique. */
    val terrain: MapColors = MapColors()
) {
    /**
     * Liste les défauts d'un thème, en clair. Vide = thème exploitable tel quel.
     *
     * Renvoyer des problèmes plutôt que lever laisse l'appelant choisir : les tests en font un
     * échec de build, `ThemeManager` s'en sert pour journaliser et repartir sur les couleurs de
     * secours role par role.
     */
    fun problems(): List<String> = buildList {
        colors.asPairs().forEach { (role, value) ->
            if (!HexColor.isValid(value)) add("couleur '$role' invalide: '$value'")
        }
        terrain.asPairs().forEach { (role, value) ->
            if (!HexColor.isValid(value)) add("couleur de terrain '$role' invalide: '$value'")
        }
        if (graphics.outlineStrokeWidth <= 0f) {
            add("outlineStrokeWidth doit être > 0 (reçu ${graphics.outlineStrokeWidth})")
        }
        if (graphics.planetShadowAlpha !in 0f..1f) {
            add("planetShadowAlpha doit être dans 0..1 (reçu ${graphics.planetShadowAlpha})")
        }
        if (graphics.blurRadius < 0f) {
            add("blurRadius doit être >= 0 (reçu ${graphics.blurRadius})")
        }
        if (graphics.particleCountMultiplier < 0f) {
            add("particleCountMultiplier doit être >= 0 (reçu ${graphics.particleCountMultiplier})")
        }
    }
}

/** Lecture d'un JSON de thème. */
object ThemeParser {
    // ignoreUnknownKeys : un JSON de thème peut gagner des sections (palette de terrain, sons…)
    // sans casser les builds plus anciens.
    private val json = Json { ignoreUnknownKeys = true }

    /** @throws SerializationException si le JSON est malformé ou s'il manque une clé. */
    fun parse(raw: String): ThemeDefinition = json.decodeFromString(ThemeDefinition.serializer(), raw)

    /** Variante non levante, pour le chemin de chargement de l'application. */
    fun parseOrNull(raw: String): ThemeDefinition? = try {
        parse(raw)
    } catch (e: SerializationException) {
        null
    }
}

object ThemeDefaults {

    /**
     * Copie compilée du thème par défaut, utilisée si l'asset est introuvable ou illisible.
     *
     * `ShippedThemesTest` vérifie qu'elle est **identique** à `themes/default.json` : c'est ce qui
     * empêche les deux de diverger, comme le faisaient auparavant la palette de secours (0.7) et le
     * JSON (0.6) pour `planetShadowAlpha`.
     */
    val FALLBACK = ThemeDefinition(
        name = "DEFAULT",
        colors = ThemeColors(
            primary = "#FF4A7B9D",
            secondary = "#FF8B2A2A",
            tertiary = "#FFB85C2A",
            background = "#FF130F0A",
            surface = "#FF1C1810",
            surfaceVariant = "#FF2D2620",
            onPrimary = "#FFD4C8B0",
            onSecondary = "#FFD4C8B0",
            onTertiary = "#FFD4C8B0",
            onBackground = "#FFD4C8B0",
            onSurface = "#FFD4C8B0",
            onSurfaceVariant = "#FF7A6E60"
        ),
        graphics = GraphicsConfig(
            outlineStrokeWidth = 3f,
            planetShadowAlpha = 0.6f,
            blurRadius = 12f,
            particleCountMultiplier = 1f
        )
    )
}
