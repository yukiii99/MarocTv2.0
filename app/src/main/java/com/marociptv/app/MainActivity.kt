
package com.marociptv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

private val Noir = Color(0xFF100D16)
private val Violet = Color(0xFF8B5CF6)
private val Carte = Color(0xFF211A2D)
private val Blanc = Color(0xFFF7F3FF)

private const val ADDON_BASE =
    "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhbXAsYXRwLGhibTo6RlI6MTc5MzQwMzk0MzY4NTowOjA6RlI%3D"

data class Film(
    val id: String,
    val titre: String,
    val affiche: String,
    val description: String,
    val type: String,
    val source: String
)

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

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Noir,
            surface = Carte,
            primary = Violet,
            onBackground = Blanc,
            onSurface = Blanc
        )
    ) {
        Scaffold(
            containerColor = Noir,
            bottomBar = {
                NavigationBar(containerColor = Carte) {
                    listOf("Accueil", "Films", "Séries", "Lecteur", "Favoris", "Extensions").forEach { nom ->
                        NavigationBarItem(
                            selected = page == nom,
                            onClick = {
                                page = nom
                                selection = null
                            },
                            icon = {
                                Icon(
                                    when (nom) {
                                        "Accueil" -> Icons.Default.Home
                                        "Films" -> Icons.Default.Movie
                                        "Séries" -> Icons.Default.Tv
                                        "Lecteur" -> Icons.Default.PlayArrow
                                        "Favoris" -> Icons.Default.Favorite
                                        else -> Icons.Default.Extension
                                    },
                                    contentDescription = nom
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
                Text("MarocTv2.0", color = Violet, fontSize = 27.sp)
                Spacer(Modifier.height(12.dp))

                if (selection != null) {
                    DetailFilm(selection!!) { selection = null }
                } else {
                    when (page) {
                        "Accueil" -> AccueilCatalogue { selection = it }
                        "Films" -> Catalogue("movie") { selection = it }
                        "Séries" -> Catalogue("series") { selection = it }
                        "Lecteur" -> LecteurVideo()
                        "Favoris" -> Text("Tes favoris apparaîtront ici.", color = Blanc)
                        "Extensions" -> Extensions()
                    }
                }
            }
        }
    }
}

@Composable
fun AccueilCatalogue(onSelect: (Film) -> Unit) {
    Text("Découvrir", color = Blanc, fontSize = 21.sp)
    Catalogue("movie", onSelect)
}

@Composable
fun Catalogue(type: String, onSelect: (Film) -> Unit) {
    var films by remember(type) { mutableStateOf<List<Film>>(emptyList()) }
    var chargement by remember(type) { mutableStateOf(true) }
    var erreur by remember(type) { mutableStateOf("") }
    var recherche by remember { mutableStateOf("") }

    LaunchedEffect(type) {
        chargement = true
        erreur = ""
        try {
            films = withContext(Dispatchers.IO) {
                chargerCatalogue(type)
            }
        } catch (e: Exception) {
            erreur = e.message ?: "Erreur de chargement"
        }
        chargement = false
    }

    OutlinedTextField(
        value = recherche,
        onValueChange = { recherche = it },
        label = { Text("Rechercher") },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(12.dp))

    when {
        chargement -> CircularProgressIndicator(color = Violet)
        erreur.isNotEmpty() -> Text("Impossible de charger : $erreur", color = Color.Red)
        films.isEmpty() -> Text("Aucun contenu trouvé.", color = Blanc)
        else -> LazyColumn {
            items(films.filter {
                it.titre.contains(recherche, ignoreCase = true)
            }) { film ->
                CarteFilm(film) { onSelect(film) }
            }
        }
    }
}

suspend fun chargerCatalogue(type: String): List<Film> {
    val manifestUrl = "$ADDON_BASE/manifest.json"
    val manifest = JSONObject(
        URL(manifestUrl).readText()
    )

    val catalogues = manifest.getJSONArray("catalogs")
    val resultats = mutableListOf<Film>()

    for (i in 0 until catalogues.length()) {
        val catalogue = catalogues.getJSONObject(i)
        if (catalogue.optString("type") != type) continue

        val id = catalogue.optString("id")
        val nom = catalogue.optString("name", "Catalogue")
        val url = "$ADDON_BASE/catalog/$type/$id.json"

        try {
            val reponse = JSONObject(URL(url).readText())
            val metas = reponse.optJSONArray("metas") ?: continue

            for (j in 0 until metas.length()) {
                val item = metas.getJSONObject(j)
                resultats.add(
                    Film(
                        id = item.optString("id"),
                        titre = item.optString("name", nom),
                        affiche = item.optString("poster"),
                        description = item.optString("description"),
                        type = type,
                        source = ADDON_BASE
                    )
                )
            }
        } catch (_: Exception) {
        }
    }
    return resultats.distinctBy { it.id }
}

@Composable
fun CarteFilm(film: Film, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Carte, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = film.affiche,
            contentDescription = film.titre,
            modifier = Modifier.size(width = 85.dp, height = 120.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(film.titre, color = Blanc, fontSize = 17.sp)
            Text(film.type, color = Violet)
        }
    }
}

@Composable
fun DetailFilm(film: Film, retour: () -> Unit) {
    Text(film.titre, color = Blanc, fontSize = 23.sp)
    Spacer(Modifier.height(12.dp))
    AsyncImage(
        model = film.affiche,
        contentDescription = film.titre,
        modifier = Modifier.fillMaxWidth().height(260.dp),
        contentScale = ContentScale.Fit
    )
    Spacer(Modifier.height(12.dp))
    Text(film.description, color = Blanc)
    Spacer(Modifier.height(16.dp))
    Text(
        "La source vidéo doit être fournie par une extension de lecture compatible.",
        color = Violet
    )
    Button(onClick = retour) { Text("Retour au catalogue") }
}

@Composable
fun LecteurVideo() {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Text("Lecteur vidéo", color = Blanc, fontSize = 22.sp)
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Adresse vidéo autorisée") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(onClick = {
        if (url.startsWith("http")) {
            player.setMediaItem(PlayerMediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
    }) {
        Text("Lire")
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxWidth().height(220.dp)
    )
}

@Composable
fun Extensions() {
    Text("Extension Stremio française", color = Blanc, fontSize = 20.sp)
    Text(ADDON_BASE + "/manifest.json", color = Violet)
    Text("Le catalogue est chargé automatiquement dans Films et Séries.", color = Blanc)
}
