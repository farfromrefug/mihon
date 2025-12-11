package eu.kanade.presentation.mangagroup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun MangaGroupCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
    existingGroupNames: ImmutableList<String>,
) {
    var name by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { existingGroupNames.contains(name) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !nameAlreadyExists,
                onClick = {
                    onCreate(name)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.create_group))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(text = stringResource(MR.strings.group_name))
                },
                supportingText = {
                    val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                        MR.strings.error_category_exists // Reuse existing string
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = name.isNotEmpty() && nameAlreadyExists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun MangaGroupEditDialog(
    onDismissRequest: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
    currentName: String,
    existingGroupNames: ImmutableList<String>,
) {
    var name by remember { mutableStateOf(currentName) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { 
        name != currentName && existingGroupNames.contains(name) 
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !nameAlreadyExists && name != currentName,
                onClick = {
                    onEdit(name)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = {
                    onDelete()
                    onDismissRequest()
                }) {
                    Text(text = stringResource(MR.strings.action_delete_group))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            }
        },
        title = {
            Text(text = stringResource(MR.strings.edit_group))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(text = stringResource(MR.strings.group_name))
                },
                supportingText = {
                    val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                        MR.strings.error_category_exists // Reuse existing string
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = name.isNotEmpty() && nameAlreadyExists,
                singleLine = true,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun MangaGroupDeleteDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    groupName: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.delete_group))
        },
        text = {
            Text(text = stringResource(MR.strings.delete_group_confirmation, groupName))
        },
    )
}
