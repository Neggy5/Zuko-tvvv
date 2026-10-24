@Composable
fun SettingsScreen(onBack: () -> Unit, watchHistory: WatchHistory, onTheme: (String) -> Unit, currentTheme: String) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zuko_settings", Context.MODE_PRIVATE) }
    var quality by remember { mutableStateOf(prefs.getString("quality", "Auto") ?: "Auto") }
    var addon by remember { mutableStateOf("Checking…") }
    var update by remember { mutableStateOf("Not checked") }
    var downloads by remember { mutableStateOf<List<DownloadRow>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        addon = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(BuildConfig.RUNFLIX_ADDON_BASE.trimEnd('/') + "/healthz").build()
                OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build().newCall(request).execute().use { if (it.isSuccessful) "Online" else "Responded: ${it.code}" }
            }.getOrElse { "Unavailable (${it.message ?: "network error"})" }
        }
        downloads = readDownloads(context)
    }
    LazyColumn(Modifier.fillMaxSize().background(Ink).padding(horizontal = 28.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("‹  Back") }; Text("Settings", style = ZukoType.screenTitle, color = TextHi) }
        item { SettingCard("Appearance", "Choose the app colour mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("System", "Dark", "Light").forEach { mode -> FilterChip(selected = currentTheme == mode, onClick = { onTheme(mode) }, label = { Text(mode) }) } }
        } }
        item { SettingCard("Preferred quality", "Used as your playback preference") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Auto", "1080p", "720p", "480p").forEach { q -> FilterChip(selected = quality == q, onClick = { quality = q; prefs.edit().putString("quality", q).apply() }, label = { Text(q) }) } }
        } }
        item { SettingCard("Runflix / Railway addon", "Status: $addon") { Text("Backend is configured for streaming and updates automatically.", style = ZukoType.small, color = TextLo) } }
        item { SettingCard("Watch history", "${watchHistory.items.value.size} saved items") { Button(onClick = { watchHistory.clear(); message = "Watch history cleared" }) { Text("Clear watch history") } } }
        item { SettingCard("Downloads", "Android Download Manager items") {
            if (downloads.isEmpty()) Text("No downloads found", color = TextLo)
            downloads.forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(row.title, color = TextHi); Text(row.status, color = TextLo, style = ZukoType.small) } ; Button(onClick = { cancelDownload(context, row.id) }) { Text("Remove") } } }
            Button(onClick = { downloads = readDownloads(context) }) { Text("Refresh downloads") }
        } }
        item { SettingCard("App updates", "Current version ${BuildConfig.VERSION_NAME}") {
            Button(onClick = { update = "Checking…" }) { Text("Check for update") }
            LaunchedEffect(update) { if (update == "Checking…") update = checkUpdate() }
            Text(update, color = TextLo, style = ZukoType.small)
        } }
        if (message.isNotBlank()) item { Text(message, color = TextHi) }
        item { Text("Release signing is configured through private Gradle/GitHub secrets. Never share your keystore or passwords.", color = TextLo, style = ZukoType.small) }
    }
}
