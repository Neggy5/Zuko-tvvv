package com.zuko.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zuko.tv.data.MangaApi
import com.zuko.tv.data.MangaChapter
import com.zuko.tv.data.MangaItem
import com.zuko.tv.data.MangaPage
import com.zuko.tv.ui.theme.Ink
import com.zuko.tv.ui.theme.Panel
import com.zuko.tv.ui.theme.TextHi
import com.zuko.tv.ui.theme.TextLo
import com.zuko.tv.ui.theme.ZukoType
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Composable
fun MangaScreen(
    client: OkHttpClient,
    onBack: () -> Unit,
) {
    val api = remember { MangaApi(client) }
    var mode by remember { mutableStateOf(0) } // 0 list 1 chapters 2 reader
    var tab by remember { mutableStateOf(0) } // trending / search
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var selected by remember { mutableStateOf<MangaItem?>(null) }
    var chapters by remember { mutableStateOf<List<MangaChapter>>(emptyList()) }
    var pages by remember { mutableStateOf<List<MangaPage>>(emptyList()) }
    var chapterTitle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reloadList() {
        scope.launch {
            loading = true
            error = null
            try {
                items = if (tab == 1) {
                    if (query.isBlank()) emptyList() else api.mangadexSearch(query.trim())
                } else {
                    api.mangadexTrending()
                }
                if (items.isEmpty()) error = if (tab == 1 && query.isBlank()) "Type a search." else "No manga."
            } catch (e: Exception) {
                error = e.message ?: "Failed"
                items = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(tab) { if (mode == 0 && tab == 0) reloadList() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                when (mode) {
                    0 -> onBack()
                    1 -> {
                        mode = 0
                        selected = null
                        chapters = emptyList()
                    }
                    else -> {
                        mode = 1
                        pages = emptyList()
                    }
                }
            }) { Text("← Back", color = TextHi) }
            Text(
                when (mode) {
                    1 -> selected?.title ?: "Chapters"
                    2 -> chapterTitle
                    else -> "📖 Manga"
                },
                style = ZukoType.title,
                color = TextHi,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (mode == 0) TextButton(onClick = { reloadList() }) { Text("Refresh", color = TextHi) }
        }

        if (mode == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MangaChip("Trending", tab == 0) { tab = 0 }
                MangaChip("Search", tab == 1) { tab = 1 }
            }
            if (tab == 1) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search manga…", color = TextLo) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextHi,
                        unfocusedTextColor = TextHi,
                        focusedContainerColor = Panel,
                        unfocusedContainerColor = Panel,
                    ),
                )
                TextButton(onClick = { reloadList() }) { Text("Search", color = TextHi) }
            }
        }

        if (error != null && mode != 2) {
            Text(error!!, color = Color(0xFFFF8A80), style = ZukoType.meta)
            Spacer(Modifier.height(6.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            mode == 2 -> {
                if (pages.isEmpty()) {
                    Text(error ?: "No pages for this chapter.", color = TextLo)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(pages, key = { it.index }) { page ->
                            AsyncImage(
                                model = page.url,
                                contentDescription = "Page ${page.index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black),
                            )
                        }
                    }
                }
            }
            mode == 1 -> {
                if (chapters.isEmpty()) Text(error ?: "No chapters.", color = TextLo)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chapters, key = { it.id }) { ch ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Panel)
                                .clickable {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        try {
                                            pages = api.mangadexPages(ch.id)
                                            chapterTitle = ch.title
                                            if (pages.isEmpty()) {
                                                error = "No readable pages for this chapter."
                                            } else {
                                                mode = 2
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed to load pages"
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(ch.title, color = TextHi, modifier = Modifier.weight(1f))
                            if (ch.language != null) Text(ch.language, color = TextLo, style = ZukoType.meta)
                        }
                    }
                }
            }
            items.isEmpty() -> Text(error ?: "Empty", color = TextLo)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Panel)
                            .clickable {
                                scope.launch {
                                    loading = true
                                    error = null
                                    try {
                                        selected = item
                                        chapters = api.mangadexChapters(item.id)
                                        mode = 1
                                        if (chapters.isEmpty()) error = "No chapters found."
                                    } catch (e: Exception) {
                                        error = e.message ?: "Failed"
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                            .padding(6.dp),
                    ) {
                        AsyncImage(
                            model = item.cover,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1A1A22)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.title,
                            color = TextHi,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            style = ZukoType.meta,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else TextLo,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF6C4CFF) else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
