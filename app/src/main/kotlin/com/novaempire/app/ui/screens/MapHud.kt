package com.novaempire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novaempire.app.ui.map.FactionBadge
import com.novaempire.app.ui.theme.NeonCyan
import com.novaempire.app.ui.theme.NeonGreen
import com.novaempire.app.ui.theme.NeonOrange
import com.novaempire.app.ui.theme.NeonRed
import com.novaempire.app.ui.theme.TextSecondary
import com.novaempire.core.domain.models.Faction
import com.novaempire.core.domain.models.GalacticEvent

@Composable
fun MapHud(
    widthClass: MapWindowClass,
    credits: Int,
    incomePerTurn: Int,
    turn: Int,
    faction: Faction,
    isAiThinking: Boolean,
    event: GalacticEvent,
    isAwakening: Boolean,
    visibleXylar: Int,
    canUndo: Boolean,
    undoClosedByExploration: Boolean,
    onOpenAcademy: () -> Unit,
    onResetView: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compact = widthClass == MapWindowClass.COMPACT
    val expanded = widthClass == MapWindowClass.EXPANDED
    val horizontalPadding = when (widthClass) {
        MapWindowClass.COMPACT -> 6.dp
        MapWindowClass.MEDIUM -> 12.dp
        MapWindowClass.EXPANDED -> 20.dp
    }
    val actions: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenAcademy, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Star, contentDescription = "Hero Academy", tint = NeonCyan)
            }
            IconButton(onClick = onResetView, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset view", tint = NeonCyan)
            }
            IconButton(onClick = onUndo, enabled = canUndo && !isAiThinking,
                modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack,
                    contentDescription = when {
                        canUndo -> "Annuler la dernière action"
                        undoClosedByExploration -> "Annulation impossible : la dernière action a découvert du terrain"
                        else -> "Rien à annuler"
                    },
                    tint = if (canUndo && !isAiThinking) NeonOrange else TextSecondary.copy(alpha = 0.4f))
            }
            if (!compact) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("NOVA CONQUEST", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            }
        }
    }
    val status: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("$credits C", style = MaterialTheme.typography.labelLarge)
                if (!compact) {
                    Text("${if (incomePerTurn >= 0) "+" else ""}$incomePerTurn C/turn",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (incomePerTurn >= 0) NeonGreen else NeonRed)
                }
            }
            Text("T$turn", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FactionBadge(faction, modifier = Modifier.size(20.dp))
                if (!compact) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(faction.name, style = MaterialTheme.typography.labelLarge,
                        color = getFactionColor(faction))
                }
            }
            if (isAiThinking) Text("IA…", style = MaterialTheme.typography.labelSmall, color = NeonOrange)
            if (event != GalacticEvent.NONE && !isAwakening) {
                Text(event.displayName.uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = NeonOrange, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().testTag("map_hud")
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
        .padding(horizontal = horizontalPadding, vertical = 4.dp)) {
        if (expanded) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                actions()
                Spacer(modifier = Modifier.weight(1f))
                status()
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                actions()
                if (compact) Text("NOVA", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween) { status() }
        }
        if (isAwakening) {
            val stormActive = event == GalacticEvent.ION_STORM
            val storm = when {
                stormActive -> "TEMPÊTE IONIQUE · ${if (turn >= 8) "T$turn" else "ACTIVE"}"
                turn < 8 -> "FRONT ORAGEUX · T8"
                else -> "FRONT DISSIPÉ"
            }
            val contacts = if (visibleXylar == 0) "XYLAR : AUCUN CONTACT"
            else "XYLAR : $visibleXylar CONTACT${if (visibleXylar > 1) "S" else ""}"
            Column(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                .background(Color(0xFF263037), RoundedCornerShape(4.dp))
                .testTag("awakening_status").padding(horizontal = 10.dp, vertical = 5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("THE AWAKENING · CORRIDOR DOMINION",
                        style = MaterialTheme.typography.labelSmall, color = NeonCyan,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$turn / 15", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(contacts, style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD9B5D8), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(storm, style = MaterialTheme.typography.labelSmall,
                        color = if (stormActive) NeonOrange else TextSecondary, maxLines = 1)
                }
            }
        }
    }
}
