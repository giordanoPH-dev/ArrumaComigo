@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.thesmallmarket.arrumacomigo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import com.thesmallmarket.arrumacomigo.ui.TaskCardUi
import com.thesmallmarket.arrumacomigo.ui.dueLabel
import com.thesmallmarket.arrumacomigo.ui.isOverdue

/**
 * Cartão de tarefa. Toque alterna concluída/pendente; segurar abre a edição ([onEdit]).
 * [onSkip]/[onPostpone] (opcionais) abrem um menu para pular a ocorrência ou adiá-la para amanhã.
 * [onPersonClick] (opcional) torna o avatar clicável para trocar o responsável.
 */
@Composable
fun TaskCard(
    item: TaskCardUi,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onPostpone: (() -> Unit)? = null,
    onPersonClick: (() -> Unit)? = null,
    showRoom: Boolean = true,
) {
    val task = item.task
    val done = item.done

    // Tarefas concluídas permanecem visíveis, esmaecidas; o restante fica opaco.
    val alpha by animateFloatAsState(
        targetValue = if (done) 0.55f else 1f,
        label = "doneAlpha",
    )

    NeoCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
        onClick = onToggle,
        onLongClick = onEdit,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            NeoCheckbox(
                checked = done,
                onCheckedChange = { onToggle() },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                // Metadados em FlowRow: quebram para a linha de baixo em telas estreitas.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (showRoom) {
                        item.room?.let {
                            Text(
                                "${it.type.emoji} ${it.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val overdue = isOverdue(task.nextDueDate)
                    Text(
                        dueLabel(task.nextDueDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    if (task.recurrence != Recurrence.NONE) {
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = "Recorrente",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (task.reminderEnabled) {
                        Icon(
                            Icons.Rounded.Notifications,
                            contentDescription = "Lembrete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // Coluna fixa à direita: responsável (clicável para trocar) e o menu de pular/adiar.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val avatarModifier = if (onPersonClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Trocar responsável",
                        onClick = onPersonClick,
                    )
                } else Modifier
                item.person?.let {
                    PersonAvatar(emoji = it.emoji, colorHex = it.colorHex, size = 38.dp, modifier = avatarModifier)
                } ?: run {
                    if (onPersonClick != null) {
                        NeoIconButton(
                            icon = Icons.Rounded.Person,
                            onClick = onPersonClick,
                            contentDescription = "Atribuir responsável",
                            size = 38.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if ((onSkip != null || onPostpone != null) && !done) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        NeoIconButton(
                            icon = Icons.Rounded.Block,
                            onClick = { menuOpen = true },
                            contentDescription = "Pular ou adiar",
                            size = 34.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = if (item.person != null || onPersonClick != null) 6.dp else 0.dp),
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (onPostpone != null) {
                                DropdownMenuItem(
                                    text = { Text("Adiar para amanhã") },
                                    onClick = { menuOpen = false; onPostpone() },
                                )
                            }
                            if (onSkip != null) {
                                DropdownMenuItem(
                                    text = { Text("Pular esta ocorrência") },
                                    onClick = { menuOpen = false; onSkip() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
