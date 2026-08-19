package com.novaempire.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novaempire.app.settings.LocalDisplaySettings
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonGold
import com.novaempire.app.ui.theme.NeonGreen
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.NeonRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** Combien de temps une notification reste lisible. */
private const val BANNER_HOLD_MS = 2600L

/** Durée du glissement d'entrée / de sortie. */
private const val BANNER_SLIDE_MS = 260

/** Support du dernier message affiché — voir l'usage dans [NotificationBanner]. */
private class BannerContent {
    var value: Pair<String, String> = "" to "CYAN"
}

/**
 * Bannière de notification du jeu.
 *
 * Elle remplace le `Snackbar` Material pour les notifications de partie, pour deux raisons :
 *
 * - **La couleur portait une information et était jetée.** Le moteur classe chaque notification
 *   (`"GOLD"` pour la gloire, `"RED"` pour un combat, `"ORANGE"` pour un événement galactique) et
 *   l'appelant la lisait `{ (message, _) -> }`. Le journal de combat, lui, s'en servait déjà — donc
 *   le même message apparaissait coloré en bas et neutre au milieu.
 * - **Un `Snackbar` s'ancre en bas**, là où le joueur pose ses doigts pour déplacer la carte, et
 *   masque la barre d'ordres.
 *
 * Les erreurs restent sur le `Snackbar` : elles appellent une correction du joueur, pas un simple
 * accusé de réception, et les mélanger reviendrait à noyer les unes dans les autres.
 */
@Composable
fun NotificationBanner(
    notifications: Flow<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Le dernier contenu affiché, conservé pendant l'animation de sortie : lire `current` dans le
    // corps ferait disparaître le texte dès le début de la sortie, ne laissant glisser qu'un
    // rectangle vide.
    //
    // Champ ordinaire et non état de composition, comme `MapCameraState.initialized` : l'écrire
    // pendant la composition n'invalide donc rien, et sa valeur est de toute façon relue à chaque
    // fois que `current` change — c'est-à-dire à chaque fois qu'elle peut avoir changé.
    val holder = remember { BannerContent() }
    current?.let { holder.value = it }
    val lastShown = holder.value
    val displaySettings = LocalDisplaySettings.current

    LaunchedEffect(notifications) {
        // `collectLatest` : une notification qui arrive pendant l'affichage de la précédente annule
        // son attente et prend sa place, au lieu de faire la queue derrière elle. Sur une fin de
        // tour qui en émet quatre d'affilée, c'est la différence entre « lire la dernière » et
        // « attendre dix secondes ».
        notifications.collectLatest { entry ->
            current = entry
            // Le maintien ne dépend pas de `reducedMotion` : c'est du temps de lecture, pas du
            // mouvement. Seul le glissement disparaît.
            delay(BANNER_HOLD_MS)
            current = null
        }
    }

    val slide = displaySettings.motionMillis(BANNER_SLIDE_MS)
    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(tween(slide)) { -it } + fadeIn(tween(slide)),
            exit = slideOutVertically(tween(slide)) { -it } + fadeOut(tween(slide)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val accent = notificationColor(lastShown.second)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(vertical = 10.dp)
                    .padding(end = 16.dp)
                    // Annoncé par TalkBack au moment où il apparaît : une bannière qui ne se lit
                    // qu'à l'œil est une information perdue pour qui ne la regarde pas.
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(4.dp).height(20.dp).background(accent))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = lastShown.first,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Couleur d'une notification, depuis l'étiquette que le moteur lui donne.
 *
 * Même table que le journal de combat de la carte tactique : les deux affichent les mêmes messages,
 * et deux tables divergentes donneraient au même événement deux couleurs différentes selon l'endroit
 * où le joueur le lit.
 */
fun notificationColor(label: String): Color = when (label.uppercase()) {
    "RED" -> NeonRed
    "ORANGE" -> NeonOrange
    "GOLD" -> NeonGold
    "GREEN" -> NeonGreen
    else -> NeonCyan
}
