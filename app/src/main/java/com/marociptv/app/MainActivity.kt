package com.marociptv.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Noir = Color(0xFF080B09)
private val Violet = Color(0xFFE32636)
private val Vert = Color(0xFF168A45)
private val Carte = Color(0xFF171D19)
private val Blanc = Color(0xFFF7F7F2)

private const val ADDON_BASE =
    "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhbXAsYXRwLGhibTo6RlI6MTc5MzQwMzk0MzY4NTowOjA6RlI%3D"
private const val PREFS_NAME = "maroc_tv_preferences"
private const val ADDONS_KEY = "installed_addons"
private const val FAVORITES_KEY = "favorite_items"
private const val HISTORY_KEY = "view_history"
private const val PLAYLIST_KEY = "m3u_playlist"

data class Film(
    val id: String,
    val titre: String,
    val affiche: String,
    val description: String,
    val type: String,
    val addonUrl: String = ADDON_BASE
)

data class ExtensionInfo(
    val nom: String,
    val url: String,
    val identifiant: String,
    val version: String
)

data class VideoStream(val nom: String, val url: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MarocTvApp() }
    }
}

@Composable
fun MarocTvApp() {
    var page by remember { mutableStateOf("Accueil") }
    var selection by remember { mutableStateOf<Film?>(null) }
    var lectureUrl by remember { mutableStateOf("") }
    var lectureTitre by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Noir, surface = Carte, primary = Violet,
            onBackground = Blanc, onSurface = Blanc
        )
    ) {
        Scaffold(
            containerColor = Noir,
            bottomBar = {
                NavigationBar(containerColor = Carte) {
                    listOf("Accueil", "Films", "Séries", "TV", "Lecteur", "Favoris", "Extensions")
                        .forEach { nom ->
                            NavigationBarItem(
                                selected = page == nom,
                                onClick = { page = nom; selection = null },
                                icon = {
                                    Icon(
                                        when (nom) {
                                            "Accueil" -> Icons.Default.Home
                                            "Films" -> Icons.Default.Movie
                                            "Séries" -> Icons.Default.LiveTv
                                            "TV" -> Icons.Default.Tv
                                            "Lecteur" -> Icons.Default.PlayArrow
                                            "Favoris" -> Icons.Default.Favorite
                                            else -> Icons.Default.Extension
                                        }, contentDescription = nom
                                    )
                                },
                                label = { Text(nom, fontSize = 9.sp) }
                            )
                        }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = R.drawable.maroc_logo,
                        contentDescription = "Logo MarocTv2.0",
                        modifier = Modifier.size(65.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("MarocTv2.0", color = Blanc, fontSize = 25.sp)
                }
                Spacer(Modifier.height(12.dp))

                if (lectureUrl.isNotBlank()) {
                    LecteurVideo(
                        urlInitiale = lectureUrl,
                        titreInitial = lectureTitre,
                        onClose = { lectureUrl = ""; lectureTitre = "" }
                    )
                } else if (selection != null) {
                    DetailFilm(
                        film = selection!!,
                        onBack = { selection = null },
                        onPlay = { url, titre ->
                            lectureUrl = url
                            lectureTitre = titre
                            selection = null
                        }
                    )
                } else {
                    when (page) {
                        "Accueil", "Films" -> Catalogue("movie") { selection = it }
                        "Séries" -> Catalogue("series") { selection = it }
                        "TV" -> Television { url, titre ->
                            lectureUrl = url; lectureTitre = titre
                        }
                        "Lecteur" -> LecteurVideo(onClose = null)
                        "Favoris" -> Favoris(
                            onSelect = { selection = it },
                            onPlay = { url, titre -> lectureUrl = url; lectureTitre = titre }
                        )
                        "Extensions" -> Extensions()
                    }
                }
            }
        }
    }
}

@Composable
fun Catalogue(type: String, onSelect: (Film) -> Unit) {
    var films by remember(type) { mutableStateOf<List<Film>>(emptyList()) }
    var chargement by remember(type) { mutableStateOf(true) }
    var erreur by remember(type) { mutableStateOf("") }
    var recherche by remember(type) { mutableStateOf("") }
    var actualisation by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(type, actualisation) {
        chargement = true
        erreur = ""
        try {
            films = withContext(Dispatchers.IO) { chargerCatalogue(type, context) }
        } catch (e: Exception) {
            erreur = e.message ?: "Erreur de chargement"
        }
        chargement = false
    }

    OutlinedTextField(
        value = recherche, onValueChange = { recherche = it },
        label = { Text("Rechercher un film ou une série") },
        modifier = Modifier.fillMaxWidth(), singleLine = true
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { actualisation++ }) { Text("Actualiser") }
    }
    when {
        chargement -> CircularProgressIndicator(color = Violet)
        erreur.isNotBlank() -> Text("Erreur : $erreur", color = Color.Red)
        films.isEmpty() -> Text("Aucun contenu trouvé. Vérifie les extensions installées.", color = Blanc)
        else -> {
            val filtres = films.filter { it.titre.contains(recherche, true) }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(filtres, key = { "${it.addonUrl}:${it.id}" }) { film ->
                    CarteFilm(film) { onSelect(film) }
                }
            }
        }
    }
}

suspend fun chargerCatalogue(type: String, context: Context): List<Film> = withContext(Dispatchers.IO) {
    val extensions = chargerExtensionsEnregistrees(context)
    val resultats = mutableListOf<Film>()
    extensions.forEach { ext ->
        try {
            val base = ext.url.trim().removeSuffix("/")
            val manifest = JSONObject(lireUrl("$base/manifest.json"))
            val catalogues = manifest.optJSONArray("catalogs") ?: JSONArray()
            for (i in 0 until catalogues.length()) {
                val cat = catalogues.optJSONObject(i) ?: continue
                if (cat.optString("type") != type) continue
                val id = cat.optString("id")
                if (id.isBlank()) continue
                val url = "$base/catalog/$type/$id.json"
                val metas = JSONObject(lireUrl(url)).optJSONArray("metas") ?: continue
                for (j in 0 until metas.length()) {
                    val item = metas.optJSONObject(j) ?: continue
                    val title = item.optString("name")
                    val itemId = item.optString("id")
                    if (title.isNotBlank() && itemId.isNotBlank()) {
                        resultats.add(
                            Film(itemId, title, item.optString("poster"),
                                item.optString("description"), type, base)
                        )
                    }
                }
            }
        } catch (_: Exception) { /* Une extension en panne n'empêche pas les autres. */ }
    }
    resultats.distinctBy { "${it.addonUrl}:${it.id}" }
}

fun lireUrl(adresse: String): String {
    val connexion = URL(adresse).openConnection() as HttpURLConnection
    try {
        connexion.connectTimeout = 15000
        connexion.readTimeout = 20000
        connexion.requestMethod = "GET"
        connexion.setRequestProperty("User-Agent", "MarocTv2.0 Android")
        if (connexion.responseCode !in 200..299) {
            throw Exception("Réponse du serveur : ${connexion.responseCode}")
        }
        return connexion.inputStream.bufferedReader().use { it.readText() }
    } finally { connexion.disconnect() }
}

/*
 * Les catalogues et les extensions sont stockés dans SharedPreferences.
 * Pour la lecture réseau, on conserve une liste globale actualisée depuis l'écran Extensions.
 */
@Volatile private var cacheExtensions = listOf(
    ExtensionInfo("Catalogue initial", ADDON_BASE, "initial", "1")
)

private fun chargerExtensionsEnregistrees(applicationContextForNetwork: Context?): List<ExtensionInfo> {
    if (applicationContextForNetwork == null) return cacheExtensions
    val prefs = applicationContextForNetwork.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(ADDONS_KEY, null) ?: return listOf(
        ExtensionInfo("Catalogue initial", ADDON_BASE, "initial", "1")
    )
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            ExtensionInfo(obj.optString("nom"), obj.optString("url"),
                obj.optString("identifiant"), obj.optString("version"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun sauvegarderExtensions(context: Context, extensions: List<ExtensionInfo>) {
    val array = JSONArray()
    extensions.forEach {
        array.put(JSONObject().put("nom", it.nom).put("url", it.url)
            .put("identifiant", it.identifiant).put("version", it.version))
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(ADDONS_KEY, array.toString()).apply()
    cacheExtensions = extensions
}

@Composable
fun CarteFilm(film: Film, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background(Carte, RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = film.affiche, contentDescription = film.titre,
            modifier = Modifier.size(90.dp, 125.dp), contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(film.titre, color = Blanc, fontSize = 17.sp)
            Text(if (film.type == "movie") "Film" else "Série", color = Violet)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Détails", tint = Blanc)
    }
}

@Composable
fun DetailFilm(film: Film, onBack: () -> Unit, onPlay: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var streams by remember(film.id, film.addonUrl) { mutableStateOf<List<VideoStream>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(film.id, film.addonUrl) {
        loading = true
        try {
            val pathType = if (film.type == "series") "series" else "movie"
            val json = withContext(Dispatchers.IO) {
                JSONObject(lireUrl("${film.addonUrl}/stream/$pathType/${Uri.encode(film.id)}.json"))
            }
            val array = json.optJSONArray("streams") ?: JSONArray()
            streams = (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val url = obj.optString("url")
                if (url.startsWith("http")) VideoStream(
                    obj.optString("title").ifBlank { obj.optString("name").ifBlank { "Source ${i + 1}" } }, url
                ) else null
            }
        } catch (e: Exception) { error = e.message ?: "Sources indisponibles" }
        loading = false
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Text(film.titre, color = Blanc, fontSize = 23.sp)
            Spacer(Modifier.height(10.dp))
            AsyncImage(film.affiche, film.titre, Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(10.dp))
            Text(film.description.ifBlank { "Aucune description disponible." }, color = Blanc)
            Spacer(Modifier.height(12.dp))
            Text("Sources vidéo", color = Violet, fontSize = 19.sp)
            when {
                loading -> CircularProgressIndicator(color = Violet)
                streams.isEmpty() -> Text(
                    if (error.isBlank()) "Aucune source vidéo fournie par cette extension." else "Sources indisponibles : $error",
                    color = Blanc
                )
                else -> streams.forEach { stream ->
                    Button(onClick = {
                        ajouterHistorique(context, film)
                        onPlay(stream.url, film.titre)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stream.nom)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    ajouterFavori(context, film)
                }) { Icon(Icons.Default.Favorite, null); Text("Ajouter aux favoris") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onBack) { Text("Retour") }
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun LecteurVideo(
    urlInitiale: String = "",
    titreInitial: String = "",
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var url by remember(urlInitiale) { mutableStateOf(urlInitiale) }
    var sousTitre by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) { onDispose { player.release() } }

    Text(titreInitial.ifBlank { "Lecteur vidéo" }, color = Blanc, fontSize = 22.sp)
    OutlinedTextField(url, { url = it }, label = { Text("Adresse vidéo autorisée (MP4, M3U8...)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        sousTitre, { sousTitre = it },
        label = { Text("Adresse du sous-titre (SRT ou VTT, facultatif)") },
        modifier = Modifier.fillMaxWidth()
    )
    Row {
        Button(onClick = {
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                message = "Adresse vidéo invalide."
            } else {
                val builder = PlayerMediaItem.Builder().setUri(url)
                if (sousTitre.startsWith("http://") || sousTitre.startsWith("https://")) {
                    val subtitle = PlayerMediaItem.SubtitleConfiguration.Builder(Uri.parse(sousTitre))
                        .setMimeType(if (sousTitre.lowercase().endsWith(".srt")) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                        .setLanguage("fr").setSelectionFlags(0).build()
                    builder.setSubtitleConfigurations(listOf(subtitle))
                }
                player.setMediaItem(builder.build())
                player.prepare()
                player.play()
                message = "Lecture lancée."
            }
        }) { Text("Lire") }
        if (onClose != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onClose) { Text("Fermer") }
        }
    }
    Text(message, color = Blanc, fontSize = 13.sp)
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; useController = true } },
        update = { it.player = player },
        modifier = Modifier.fillMaxWidth().height(230.dp)
    )
}

private fun ajouterFavori(context: Context, film: Film) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val array = try { JSONArray(prefs.getString(FAVORITES_KEY, "[]")) } catch (_: Exception) { JSONArray() }
    for (i in 0 until array.length()) {
        if (array.optJSONObject(i)?.optString("id") == film.id &&
            array.optJSONObject(i)?.optString("addonUrl") == film.addonUrl) return
    }
    array.put(filmToJson(film))
    prefs.edit().putString(FAVORITES_KEY, array.toString()).apply()
}

private fun retirerFavori(context: Context, film: Film) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val old = try { JSONArray(prefs.getString(FAVORITES_KEY, "[]")) } catch (_: Exception) { JSONArray() }
    val fresh = JSONArray()
    for (i in 0 until old.length()) {
        val obj = old.optJSONObject(i) ?: continue
        if (obj.optString("id") != film.id || obj.optString("addonUrl") != film.addonUrl) fresh.put(obj)
    }
    prefs.edit().putString(FAVORITES_KEY, fresh.toString()).apply()
}

private fun filmToJson(f: Film) = JSONObject().put("id", f.id).put("titre", f.titre)
    .put("affiche", f.affiche).put("description", f.description).put("type", f.type).put("addonUrl", f.addonUrl)

private fun lireFilmsJson(context: Context, key: String): List<Film> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Film(o.optString("id"), o.optString("titre"), o.optString("affiche"),
                o.optString("description"), o.optString("type"), o.optString("addonUrl", ADDON_BASE))
        }
    } catch (_: Exception) { emptyList() }
}

private fun ajouterHistorique(context: Context, film: Film) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val current = lireFilmsJson(context, HISTORY_KEY).filterNot { it.id == film.id && it.addonUrl == film.addonUrl }
    val array = JSONArray()
    (listOf(film) + current).take(50).forEach { array.put(filmToJson(it)) }
    prefs.edit().putString(HISTORY_KEY, array.toString()).apply()
}

@Composable
fun Favoris(onSelect: (Film) -> Unit, onPlay: (String, String) -> Unit) {
    val context = LocalContext.current
    var favoris by remember { mutableStateOf(lireFilmsJson(context, FAVORITES_KEY)) }
    var historique by remember { mutableStateOf(lireFilmsJson(context, HISTORY_KEY)) }
    Column(Modifier.fillMaxSize()) {
        Text("Mes favoris", color = Blanc, fontSize = 22.sp)
        if (favoris.isEmpty()) Text("Aucun favori pour le moment. Ajoute un film depuis sa fiche.", color = Blanc)
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(favoris, key = { "${it.addonUrl}:${it.id}" }) { film ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { CarteFilm(film) { onSelect(film) } }
                    IconButton(onClick = {
                        retirerFavori(context, film)
                        favoris = lireFilmsJson(context, FAVORITES_KEY)
                    }) { Icon(Icons.Default.Delete, "Retirer", tint = Violet) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Historique", color = Violet, fontSize = 20.sp)
        if (historique.isEmpty()) Text("Aucune lecture enregistrée.", color = Blanc)
        historique.take(10).forEach { film ->
            TextButton(onClick = { onSelect(film) }) {
                Text(film.titre, color = Blanc)
            }
        }
        TextButton(onClick = { historique = emptyList(); context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(HISTORY_KEY).apply() }) {
            Text("Effacer l'historique")
        }
    }
}

suspend fun verifierExtension(adresse: String): ExtensionInfo = withContext(Dispatchers.IO) {
    val clean = adresse.trim().removeSuffix("/")
    val manifestUrl = if (clean.endsWith("/manifest.json")) clean else "$clean/manifest.json"
    val manifest = JSONObject(lireUrl(manifestUrl))
    val name = manifest.optString("name")
    val id = manifest.optString("id")
    if (name.isBlank() || id.isBlank()) throw Exception("Manifest sans nom ou identifiant.")
    ExtensionInfo(name, clean.removeSuffix("/manifest.json"), id, manifest.optString("version").ifBlank { "Non précisée" })
}

@Composable
fun Extensions() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var extensions by remember { mutableStateOf(chargerExtensionsEnregistrees(context)) }
    var adresse by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var verification by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text("Gestionnaire d'extensions", color = Blanc, fontSize = 21.sp)
        Text("Ajoute des extensions Stremio compatibles avec une adresse manifest.json.", color = Blanc)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            adresse, { adresse = it }, label = { Text("Adresse de l'extension") },
            placeholder = { Text("https://exemple.com/manifest.json") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Button(
            enabled = !verification,
            onClick = {
                if (!adresse.startsWith("https://") && !adresse.startsWith("http://")) {
                    message = "Adresse invalide : utilise http:// ou https://."
                } else {
                    verification = true
                    scope.launch {
                        try {
                            val ext = verifierExtension(adresse)
                            if (extensions.any { it.identifiant == ext.identifiant }) {
                                message = "Cette extension est déjà installée."
                            } else {
                                extensions = extensions + ext
                                sauvegarderExtensions(context, extensions)
                                message = "Extension installée : ${ext.nom}"
                                adresse = ""
                            }
                        } catch (e: Exception) { message = "Installation impossible : ${e.message}" }
                        verification = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (verification) "Vérification..." else "Installer l'extension") }
        if (message.isNotBlank()) Text(message, color = Violet, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Text("Extensions installées (${extensions.size})", color = Violet, fontSize = 18.sp)
        LazyColumn(Modifier.fillMaxWidth()) {
            items(extensions, key = { it.identifiant }) { ext ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Carte, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ext.nom, color = Blanc)
                        Text("Version ${ext.version}", color = Violet, fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        extensions = extensions.filterNot { it.identifiant == ext.identifiant }
                        sauvegarderExtensions(context, extensions)
                    }) { Icon(Icons.Default.Delete, "Désinstaller", tint = Violet) }
                }
            }
        }
    }
}

@Composable
fun Television(onPlay: (String, String) -> Unit) {
    val context = LocalContext.current
    var playlistUrl by remember { mutableStateOf("") }
    var chaines by remember { mutableStateOf<List<VideoStream>>(emptyList()) }
    var recherche by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text("Télévision en direct – M3U", color = Blanc, fontSize = 21.sp)
        OutlinedTextField(
            playlistUrl, { playlistUrl = it },
            label = { Text("URL de playlist M3U") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(enabled = !loading, onClick = {
            if (!playlistUrl.startsWith("http")) message = "Adresse M3U invalide."
            else {
                loading = true
                scope.launch {
                    try {
                        val texte = withContext(Dispatchers.IO) { lireUrl(playlistUrl) }
                        chaines = parserM3u(texte)
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .putString(PLAYLIST_KEY, playlistUrl).apply()
                        message = "${chaines.size} chaîne(s) trouvée(s)."
                    } catch (e: Exception) { message = "Chargement impossible : ${e.message}" }
                    loading = false
                }
            }
        }) { Text(if (loading) "Chargement..." else "Charger la playlist") }
        OutlinedTextField(recherche, { recherche = it }, label = { Text("Rechercher une chaîne") }, modifier = Modifier.fillMaxWidth())
        Text(message, color = Violet)
        LazyColumn {
            items(chaines.filter { it.nom.contains(recherche, true) }) { chaine ->
                TextButton(onClick = { onPlay(chaine.url, chaine.nom) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(chaine.nom, color = Blanc)
                }
            }
        }
    }
}

private fun parserM3u(texte: String): List<VideoStream> {
    val result = mutableListOf<VideoStream>()
    var nom = "Chaîne"
    texte.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("#EXTINF", true)) {
            nom = line.substringAfterLast(",").trim().ifBlank { "Chaîne ${result.size + 1}" }
        } else if (line.startsWith("http://") || line.startsWith("https://")) {
            result.add(VideoStream(nom, line))
            nom = "Chaîne"
        }
    }
    return result
}
