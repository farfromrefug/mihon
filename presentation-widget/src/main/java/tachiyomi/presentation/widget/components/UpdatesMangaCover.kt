package tachiyomi.presentation.widget.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Alignment
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.background
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import tachiyomi.presentation.widget.R
import tachiyomi.presentation.widget.util.appWidgetInnerRadius

val CoverWidth = 58.dp
val CoverHeight = 87.dp

@Composable
fun UpdatesMangaCover(
    cover: Bitmap?,
    cornerRadius: Dp = 12.dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier,
    ) {
        if (cover != null) {
            Image(
                provider = ImageProvider(cover),
                contentDescription = null,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(cornerRadius),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Enjoy placeholder
            Image(
                provider = ImageProvider(R.drawable.appwidget_cover_error),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun UpdatesMangaCoverWithProgress(
    cover: Bitmap?,
    currentPage: Long,
    totalPage: Long,
    cornerRadius: Dp = 12.dp,
    contentColor: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Base cover image
        UpdatesMangaCover(
            cover = cover,
            cornerRadius = cornerRadius,
            modifier = GlanceModifier.fillMaxSize(),
        )

        // Progress overlay at bottom
        if (totalPage > 0) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth().background(ColorProvider(Color(0x80ffffff))),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Page indicator text
                Text(
                    text = "$currentPage / $totalPage",
                    style = TextStyle(color = ColorProvider(Color.Black), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(bottom = 1.dp, start = 4.dp, end = 4.dp),
                )

                // Linear progress bar
                LinearProgressIndicator(
                    progress = (currentPage.toFloat() / totalPage.toFloat()).coerceIn(0f, 1f),
                    color = contentColor,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                )
            }
        }
    }
}
