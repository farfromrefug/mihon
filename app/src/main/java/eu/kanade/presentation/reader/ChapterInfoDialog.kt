package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.text.DateFormat
import java.util.Date

data class ChapterInfo(
    val chapterName: String,
    val chapterNumber: String?,
    val scanlator: String?,
    val sourceName: String,
    val dateUpload: Long?,
    val fileName: String?,
    val filePath: String?,
    val fileSize: Long?,
)

@Composable
fun ChapterInfoDialog(
    chapterInfo: ChapterInfo,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.chapter_info))
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Chapter name
                InfoRow(
                    label = stringResource(MR.strings.title),
                    value = chapterInfo.chapterName,
                )

                // Chapter number
                chapterInfo.chapterNumber?.let {
                    InfoRow(
                        label = stringResource(MR.strings.chapter_number),
                        value = it,
                    )
                }

                // Scanlator
                chapterInfo.scanlator?.let {
                    InfoRow(
                        label = stringResource(MR.strings.scanlator),
                        value = it,
                    )
                }

                // Source
                InfoRow(
                    label = stringResource(MR.strings.label_source),
                    value = chapterInfo.sourceName,
                )

                // Upload date
                chapterInfo.dateUpload?.let { timestamp ->
                    if (timestamp > 0) {
                        InfoRow(
                            label = stringResource(MR.strings.label_date_uploaded),
                            value = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp)),
                        )
                    }
                }

                // File name
                chapterInfo.fileName?.let {
                    InfoRow(
                        label = stringResource(MR.strings.file_name),
                        value = it,
                    )
                }

                // File path
                chapterInfo.filePath?.let {
                    InfoRow(
                        label = stringResource(MR.strings.file_path),
                        value = it,
                    )
                }

                // File size
                chapterInfo.fileSize?.let { size ->
                    InfoRow(
                        label = stringResource(MR.strings.file_size),
                        value = formatFileSize(size),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
