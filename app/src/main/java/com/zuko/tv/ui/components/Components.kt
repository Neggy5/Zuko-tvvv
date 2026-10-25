package com.zuko.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.zuko.tv.data.MovieSummary
import com.zuko.tv.data.Tmdb
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.Panel
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.ZukoBrush
import com.zuko.tv.ui.theme.ZukoType
import java.util.Locale

val PosterWidth = 130.dp
val PosterHeight = 195.dp

/** Poster tile. Focus scales it up and draws the gradient ring. */
@Composable
fun PosterCard(
    movie: MovieSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    progress: Float? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = tween(140),
        label = "posterScale",
    )
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .width(PosterWidth)
            .height(PosterHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(shape)
            .background(Panel)
            .then(if (focused) Modifier.border(3.dp, ZukoBrush, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        val path = movie.posterPath
        if (path != null) {
            AsyncImage(
                model = Tmdb.poster(path),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = movie.title,
                style = ZukoType.meta,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(10.dp),
            )
        }
        if (progress != null && progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x99000000)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(ZukoBrush),
                )
            }
        }
    }
}

/** Small outlined rating pill. Hidden when the title has no votes yet. */
@Composable
fun RatingChip(rating: Double, modifier: Modifier = Modifier) {
    if (rating <= 0.0) return
    Box(
        modifier = modifier
            .border(1.5.dp, ZukoBrush, RoundedCornerShape(6.dp))
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(text = String.format(Locale.US, "%.1f", rating), style = ZukoType.meta)
    }
}

/** Focusable button with a gradient fill when focused. */
@Composable
fun ZukoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .then(
                if (focused) Modifier.background(ZukoBrush)
                else Modifier.background(Panel)
            )
            .clickable(onClick = onClick)
            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = ZukoType.meta)
    }
}

/** Full-screen message used for loading, empty and error states. */
@Composable
fun CenterMessage(
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(start = 96.dp, end = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = ZukoType.screenTitle.copy(color = TextHi), textAlign = TextAlign.Center)
            if (body != null) {
                Text(text = body, style = ZukoType.body, textAlign = TextAlign.Center)
            }
            if (actionLabel != null && onAction != null) {
                ZukoButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
