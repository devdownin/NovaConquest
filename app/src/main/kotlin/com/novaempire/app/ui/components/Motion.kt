package com.novaempire.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import com.novaempire.app.settings.AppSettings

// Le vocabulaire du mouvement, en un seul endroit.
//
// Deux raisons de ne pas écrire `tween(300)` à la main dans les écrans :
//
// 1. L'accessibilité doit être impossible à oublier. `reducedMotion` ne peut être respecté que si
//    chaque durée passe par `motionMillis` ; une durée écrite en dur est un réglage silencieusement
//    ignoré, et c'est exactement le genre de manquement qu'on ne voit pas en relecture.
// 2. Une boucle infinie ne se coupe pas en mettant sa durée à zéro : `infiniteRepeatable` avec une
//    durée nulle tourne à vide à chaque frame. `rememberMotionLoop` la remplace par une valeur
//    fixe, donc plus aucune invalidation.

/**
 * Durée d'une animation ponctuelle, ramenée à 0 quand le joueur (ou le système) a demandé des
 * animations réduites.
 *
 * Zéro plutôt qu'un `if` autour de l'appel : l'animation se déroule toujours *logiquement* — mêmes
 * états, mêmes transitions, même code — elle arrive simplement à destination du premier coup. Rien
 * n'est sauté, donc rien ne peut rester bloqué à mi-chemin.
 */
fun AppSettings.motionMillis(millis: Int): Int = if (reducedMotion) 0 else millis

/** Pareil pour les temps d'attente d'un enchaînement (fin d'explosion, maintien d'une bannière). */
fun AppSettings.motionDelay(millis: Long): Long = if (reducedMotion) 0L else millis

/**
 * Boucle d'animation continue (balayage, halo), désactivable.
 *
 * Quand [enabled] est faux, aucune `InfiniteTransition` n'est créée : la valeur rendue est figée à
 * [restValue] et la couche qui la lit cesse de s'invalider.
 */
@Composable
fun rememberMotionLoop(
    enabled: Boolean,
    durationMillis: Int,
    repeatMode: RepeatMode = RepeatMode.Restart,
    restValue: Float = 0f,
    label: String = "MotionLoop"
): State<Float> =
    if (!enabled) {
        remember(restValue) { mutableFloatStateOf(restValue) }
    } else {
        rememberInfiniteTransition(label = label).animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = LinearEasing),
                repeatMode = repeatMode
            ),
            label = label
        )
    }

/**
 * Position à la fraction [t] (0..1) d'une polyligne, répartie uniformément **par segment**.
 *
 * Uniforme par segment et non par distance : sur une grille hexagonale tous les pas font la même
 * longueur, donc les deux reviennent au même, et compter les segments évite de mesurer la
 * polyligne à chaque frame.
 *
 * Renvoie [Offset.Zero] pour une liste vide — un chemin sans point n'a pas de position, et lever
 * ici ferait planter le rendu pour une animation.
 */
fun pointAlongPath(points: List<Offset>, t: Float): Offset {
    if (points.isEmpty()) return Offset.Zero
    if (points.size == 1) return points[0]
    val segments = points.size - 1
    val scaled = (t.coerceIn(0f, 1f) * segments)
    val index = scaled.toInt().coerceAtMost(segments - 1)
    val local = scaled - index
    val a = points[index]
    val b = points[index + 1]
    return Offset(a.x + (b.x - a.x) * local, a.y + (b.y - a.y) * local)
}
