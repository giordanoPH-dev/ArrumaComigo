package com.thesmallmarket.arrumacomigo.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.ui.AppViewModelProvider
import com.thesmallmarket.arrumacomigo.ui.theme.NeumorphicEdgeInset
import com.thesmallmarket.arrumacomigo.ui.components.NeoButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoCard
import com.thesmallmarket.arrumacomigo.ui.components.NeoIconButton
import com.thesmallmarket.arrumacomigo.ui.components.NeoTextField
import com.thesmallmarket.arrumacomigo.ui.components.PersonAvatar
import com.thesmallmarket.arrumacomigo.ui.components.SectionHeader
import com.thesmallmarket.arrumacomigo.ui.theme.PersonColors

private val emojiChoices = listOf("🙂", "😎", "🧑", "👩", "👨", "👧", "👦", "🐱", "🐶", "🌟", "🦄", "🍀")

@Composable
fun PeopleScreen(
    modifier: Modifier = Modifier,
    viewModel: PeopleViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Person?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        SectionHeader(
            title = "Pessoas",
            modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
        ) {
            NeoButton(
                text = "Adicionar",
                icon = Icons.Rounded.Add,
                onClick = { editing = null; showDialog = true },
            )
        }

        if (people.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Adicione as pessoas do lar para dividir as tarefas.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                contentPadding = PaddingValues(vertical = NeumorphicEdgeInset),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(people, key = { it.id }) { person ->
                    PersonRow(
                        person = person,
                        onEdit = { editing = person; showDialog = true },
                        onDelete = { viewModel.delete(person) },
                    )
                }
            }
        }
    }

    if (showDialog) {
        PersonEditorDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = { viewModel.save(it); showDialog = false },
        )
    }
}

@Composable
private fun PersonRow(person: Person, onEdit: () -> Unit, onDelete: () -> Unit) {
    NeoCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(emoji = person.emoji, colorHex = person.colorHex, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                person.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            NeoIconButton(Icons.Rounded.Edit, onClick = onEdit, contentDescription = "Editar", size = 42.dp)
            Spacer(Modifier.width(8.dp))
            NeoIconButton(
                Icons.Rounded.Delete,
                onClick = onDelete,
                contentDescription = "Excluir",
                size = 42.dp,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PersonEditorDialog(
    initial: Person?,
    onDismiss: () -> Unit,
    onSave: (Person) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: emojiChoices.first()) }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: PersonColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova pessoa" else "Editar pessoa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NeoTextField(value = name, onValueChange = { name = it }, label = "Nome", modifier = Modifier.fillMaxWidth())
                Text("Emoji", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojiChoices.take(6).forEach { e -> EmojiOption(e, e == emoji) { emoji = e } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojiChoices.drop(6).forEach { e -> EmojiOption(e, e == emoji) { emoji = e } }
                }
                Text("Cor", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PersonColors.forEach { hex -> ColorOption(hex, hex == colorHex) { colorHex = hex } }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave((initial ?: Person(name = "", colorHex = colorHex)).copy(name = name.trim(), emoji = emoji, colorHex = colorHex))
                    }
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun EmojiOption(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun ColorOption(hex: String, selected: Boolean, onClick: () -> Unit) {
    val color = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
