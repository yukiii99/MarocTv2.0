
package com.marociptv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
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
    val type: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarocTvApp()
        }
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
                    val pages = listOf(
                        "Accueil", "Films", "Séries",
                        "Lecteur", "Favoris", "Extensions"
                    )

                    pages.forEach { nom ->
                        NavigationBarItem(
                            selected = page == nom,
                            onClick = {
                                page = nom
                                selection = null
                            },
                            icon = {
                                Icon(
                                    imageVector = when (nom) {
                                        "Accueil" -> Icons.Default.Home
                                        "Films" -> Icons.Default.Movie
                                        "Séries" -> Icons.Default.LiveTv
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp)
            ) {
                Text(
                    text = "MarocTv2.0",
                    color = Violet,
                    fontSize = 27.sp
                )

                Spacer(Modifier.height(12.dp))

                if (selection != null) {
                    DetailFilm(selection!!) {
                        selection = null
                    }
                } else {
                    when (page) {
                        "Accueil" -> Catalogue("movie") {
                            selection = it
                        }
                        "Films" -> Catalogue("movie") {
                            selection = it
                        }
                        "Séries" -> Catalogue("series") {
                            selection = it
                        }
                        "Lecteur" -> LecteurVideo()
                        "Favoris" -> {
                            Text(
                                "Tes favoris apparaîtront ici.",
                                color = Blanc
                            )
                        }
                        "Extensions" -> Extensions()
                    }
                }
            }
        }
    }
}

@Composable
fun Catalogue(
    type: String,
    onSelect: (Film) -> Unit
) {
    var films by remember(type) {
        mutableStateOf<List<Film>>(emptyList())
    }
    var chargement by remember(type) {
        mutableStateOf(true)
    }
    var erreur by remember(type) {
        mutableStateOf("")
    }
    var recherche by remember(type) {
        mutableStateOf("")
    }

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
        label = { Text("Rechercher un film ou une série") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(Modifier.height(12.dp))

    when {
        chargement -> {
            CircularProgressIndicator(color = Violet)
            Text("Chargement du catalogue...", color = Blanc)
        }

        erreur.isNotEmpty() -> {
            Text(
                "Erreur : $erreur",
                color = Color.Red
            )
            Text(
                "Vérifie ta connexion Internet ou l'extension.",
                color = Blanc
            )
        }

        films.isEmpty() -> {
            Text(
                "Aucun contenu trouvé dans ce catalogue.",
                color = Blanc
            )
        }

        else -> {
            val filtres = films.filter {
                it.titre.contains(recherche, ignoreCase = true)
            }

            LazyColumn {
                items(filtres, key = { it.id }) { film ->
                    CarteFilm(film) {
                        onSelect(film)
                    }
                }
            }
        }
    }
}

suspend fun chargerCatalogue(type: String): List<Film> {
    val manifest = JSONObject(
        lireUrl("$ADDON_BASE/manifest.json")
    )

    val catalogues = manifest.optJSONArray("catalogs")
        ?: return emptyList()

    val resultats = mutableListOf<Film>()

    for (i in 0 until catalogues.length()) {
        val catalogue = catalogues.getJSONObject(i)

        if (catalogue.optString("type") != type) {
            continue
        }

        val id = catalogue.optString("id")

        if (id.isBlank()) continue

        val url = "$ADDON_BASE/catalog/$type/$id.json"

        try {
            val reponse = JSONObject(lireUrl(url))
            val metas = reponse.optJSONArray("metas")
                ?: continue

            for (j in 0 until metas.length()) {
                val item = metas.getJSONObject(j)

                val titre = item.optString("name")
                val identifiant = item.optString("id")

                if (titre.isBlank() || identifiant.isBlank()) {
                    continue
                }

                resultats.add(
                    Film(
                        id = identifiant,
                        titre = titre,
                        affiche = item.optString("poster"),
                        description = item.optString("description"),
                        type = type
                    )
                )
            }
        } catch (_: Exception) {
            // Un catalogue indisponible n'empêche pas les autres de charger.
        }
    }

    return resultats.distinctBy { it.id }
}

fun lireUrl(adresse: String): String {
    val connexion = URL(adresse).openConnection()
        as HttpURLConnection

    try {
        connexion.connectTimeout = 15000
        connexion.readTimeout = 15000
        connexion.requestMethod = "GET"
        connexion.setRequestProperty(
            "User-Agent",
            "MarocTv2.0 Android"
        )

        if (connexion.responseCode !in 200..299) {
            throw Exception(
                "Réponse du serveur : ${connexion.responseCode}"
            )
        }

        return connexion.inputStream
            .bufferedReader()
            .use { it.readText() }
    } finally {
        connexion.disconnect()
    }
}

@Composable
fun CarteFilm(
    film: Film,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Carte, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = film.affiche,
            contentDescription = film.titre,
            modifier = Modifier.size(90.dp, 125.dp),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                film.titre,
                color = Blanc,
                fontSize = 17.sp
            )
            Text(
                if (film.type == "movie") "Film" else "Série",
                color = Violet
            )
        }
    }
}

@Composable
fun DetailFilm(
    film: Film,
    retour: () -> Unit
) {
    Text(
        film.titre,
        color = Blanc,
        fontSize = 23.sp
    )

    Spacer(Modifier.height(12.dp))

    AsyncImage(
        model = film.affiche,
        contentDescription = film.titre,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        contentScale = ContentScale.Fit
    )

    Spacer(Modifier.height(12.dp))

    Text(
        film.description.ifBlank {
            "Aucune description disponible."
        },
        color = Blanc
    )

    Spacer(Modifier.height(16.dp))

    Text(
        "La lecture nécessite une source vidéo compatible.",
        color = Violet
    )

    Button(onClick = retour) {
        Text("Retour au catalogue")
    }
}

@Composable
fun LecteurVideo() {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Text(
        "Lecteur vidéo",
        color = Blanc,
        fontSize = 22.sp
    )

    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Adresse vidéo autorisée") },
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = {
            if (url.startsWith("https://") ||
                url.startsWith("http://")
            ) {
                player.setMediaItem(
                    PlayerMediaItem.fromUri(url)
                )
                player.prepare()
                player.play()
                message = "Lecture demandée."
            } else {
                message = "Adresse vidéo invalide."
            }
        }
    ) {
        Text("Lire")
    }

    Text(message, color = Blanc)

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                player = this@LecteurVideo.player
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    )
}

@Composable
fun Extensions() {
    Text(
        "Extension de catalogue",
        color = Blanc,
        fontSize = 21.sp
    )

    Spacer(Modifier.height(12.dp))

    Text(
        "Streaming Catalogs – France",
        color = Violet
    )

    Text(
        "Les catalogues sont chargés depuis l'adresse configurée dans l'application.",
        color = Blanc
    )
}
