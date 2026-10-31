
package com.marociptv.app

import android.content.Context
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

data class Film(
    val id: String,
    val titre: String,
    val affiche: String,
    val description: String,
    val type: String
)

data class ExtensionInfo(
    val nom: String,
    val url: String,
    val identifiant: String,
    val version: String
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
                        "Accueil",
                        "Films",
                        "Séries",
                        "Lecteur",
                        "Favoris",
                        "Extensions"
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
                            label = {
                                Text(nom, fontSize = 9.sp)
                            }
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
                            Favoris()
                        }

                        "Extensions" -> {
                            Extensions()
                        }
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
        label = {
            Text("Rechercher un film ou une série")
        },
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
            Text("Erreur : $erreur", color = Color.Red)
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

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = filtres,
                    key = { it.id }
                ) { film ->
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

        if (id.isBlank()) {
            continue
        }

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
            // Continue avec les autres catalogues.
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

    var url by remember {
        mutableStateOf("")
    }

    var message by remember {
        mutableStateOf("")
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
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
        label = {
            Text("Adresse vidéo autorisée")
        },
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = {
            if (
                url.startsWith("https://") ||
                url.startsWith("http://")
            ) {
                exoPlayer.setMediaItem(
                    PlayerMediaItem.fromUri(url)
                )
                exoPlayer.prepare()
                exoPlayer.play()
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
                player = exoPlayer
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    )
}

fun sauvegarderExtensions(
    context: Context,
    extensions: List<ExtensionInfo>
) {
    val tableau = JSONArray()

    extensions.forEach { extension ->
        val objet = JSONObject()

        objet.put("nom", extension.nom)
        objet.put("url", extension.url)
        objet.put("identifiant", extension.identifiant)
        objet.put("version", extension.version)

        tableau.put(objet)
    }

    context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    ).edit()
        .putString(ADDONS_KEY, tableau.toString())
        .apply()
}

fun chargerExtensionsEnregistrees(
    context: Context
): List<ExtensionInfo> {
    val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val texte = preferences.getString(ADDONS_KEY, null)

    if (texte == null) {
        return listOf(
            ExtensionInfo(
                nom = "Netflix Catalog – France",
                url = ADDON_BASE,
                identifiant = "netflix-catalog-fr",
                version = "Extension initiale"
            )
        )
    }

    val tableau = JSONArray(texte)
    val resultat = mutableListOf<ExtensionInfo>()

    for (i in 0 until tableau.length()) {
        val objet = tableau.getJSONObject(i)

        resultat.add(
            ExtensionInfo(
                nom = objet.optString("nom"),
                url = objet.optString("url"),
                identifiant = objet.optString("identifiant"),
                version = objet.optString("version")
            )
        )
    }

    return resultat
}

suspend fun verifierExtension(
    adresse: String
): ExtensionInfo = withContext(Dispatchers.IO) {
    val urlPropre = adresse.trim().removeSuffix("/")

    val urlManifest = if (
        urlPropre.endsWith("/manifest.json")
    ) {
        urlPropre
    } else {
        "$urlPropre/manifest.json"
    }

    val manifest = JSONObject(lireUrl(urlManifest))

    val nom = manifest.optString("name")
    val id = manifest.optString("id")
    val version = manifest.optString("version")

    if (nom.isBlank() || id.isBlank()) {
        throw Exception(
            "Le manifest ne contient pas de nom ou d'identifiant valide."
        )
    }

    ExtensionInfo(
        nom = nom,
        url = urlPropre,
        identifiant = id,
        version = version.ifBlank { "Non précisée" }
    )
}

@Composable
fun Extensions() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var extensions by remember {
        mutableStateOf<List<ExtensionInfo>>(emptyList())
    }

    var adresse by remember {
        mutableStateOf("")
    }

    var message by remember {
        mutableStateOf("")
    }

    var verification by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        extensions = chargerExtensionsEnregistrees(context)
        sauvegarderExtensions(context, extensions)
    }

    Text(
        "Gestionnaire d'extensions",
        color = Blanc,
        fontSize = 21.sp
    )

    Spacer(Modifier.height(8.dp))

    Text(
        "Ajoute des extensions Stremio compatibles avec une adresse manifest.json.",
        color = Blanc
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = adresse,
        onValueChange = { adresse = it },
        label = {
            Text("Adresse de l'extension")
        },
        placeholder = {
            Text("https://exemple.com/manifest.json")
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = {
            if (adresse.isBlank()) {
                message = "Colle d'abord une adresse."
            } else if (
                !adresse.trim().startsWith("https://") &&
                !adresse.trim().startsWith("http://")
            ) {
                message = "L'adresse doit commencer par https:// ou http://"
            } else {
                verification = true
                message = "Vérification de l'extension..."

                scope.launch {
                    try {
                        val nouvelle = verifierExtension(adresse)

                        if (
                            extensions.any {
                                it.identifiant == nouvelle.identifiant
                            }
                        ) {
                            message = "Cette extension est déjà installée."
                        } else {
                            extensions = extensions + nouvelle

                            sauvegarderExtensions(
                                context,
                                extensions
                            )

                            adresse = ""
                            message = "Extension installée : ${nouvelle.nom}"
                        }
                    } catch (e: Exception) {
                        message = "Installation impossible : ${
                            e.message ?: "Erreur inconnue"
                        }"
                    }

                    verification = false
                }
            }
        },
        enabled = !verification,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (verification) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Blanc,
                strokeWidth = 2.dp
            )

            Spacer(Modifier.width(8.dp))
            Text("Vérification...")
        } else {
            Icon(
                Icons.Default.Add,
                contentDescription = null
            )

            Spacer(Modifier.width(8.dp))
            Text("Installer l'extension")
        }
    }

    if (message.isNotBlank()) {
        Text(
            message,
            color = if (
                message.startsWith("Installation impossible")
            ) {
                Color.Red
            } else {
                Violet
            },
            fontSize = 13.sp
        )
    }

    Spacer(Modifier.height(18.dp))

    Text(
        "Extensions installées (${extensions.size})",
        color = Violet,
        fontSize = 18.sp
    )

    Spacer(Modifier.height(8.dp))

    if (extensions.isEmpty()) {
        Text(
            "Aucune extension installée.",
            color = Blanc
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = extensions,
                key = { it.identifiant }
            ) { extension ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .background(
                            Carte,RoundedCornerShape(12.dp)
)
.padding(12.dp),
verticalAlignment = Alignment.CenterVertically
) {
    Icon(
        Icons.Default.Extension,
        contentDescription = null,
        tint = Violet
    )

    Spacer(Modifier.width(10.dp))

    Column(
        modifier = Modifier.weight(1f)
    ) {
        Text(
            extension.nom,
            color = Blanc,
            fontSize = 15.sp
        )

        Text(
            "Version : ${extension.version}",
            color = Violet,
            fontSize = 12.sp
        )
    }

    IconButton(
        onClick = {
            extensions = extensions.filter {
                it.identifiant != extension.identifiant
            }
            sauvegarderExtensions(context, extensions)
            message = "Extension supprimée."
        }
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "Supprimer",
            tint = Color.Red
        )
    }
}
}
}
}
}

@Composable
fun Favoris() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Favorite,
            contentDescription = null,
            tint = Violet,
            modifier = Modifier.size(48.dp)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Mes favoris",
            color = Blanc,
            fontSize = 22.sp
        )

        Text(
            "La sauvegarde des favoris sera ajoutée dans une prochaine étape.",
            color = Blanc
        )
    }
}
                            
