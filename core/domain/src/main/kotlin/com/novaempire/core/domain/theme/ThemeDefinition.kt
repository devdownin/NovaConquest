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
    val graphics: GraphicsConfig
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
