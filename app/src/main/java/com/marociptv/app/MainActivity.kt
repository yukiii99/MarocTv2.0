
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect

private val Noir = Color(0xFF100D16)
private val Violet = Color(0xFF8B5CF6)
private val Carte = Color(0xFF211A2D)
private val Blanc = Color(0xFFF7F3FF)

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

    val pages = listOf(
        "Accueil", "Catalogue", "TV en direct",
        "Lecteur", "Favoris", "Extensions"
    )

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
                    pages.forEach { nom ->
                        NavigationBarItem(
                            selected = page == nom,
                            onClick = { page = nom },
                            icon = {
                                Icon(
                                    imageVector = when (nom) {
                                        "Accueil" -> Icons.Default.Home
                                        "Catalogue" -> Icons.Default.Movie
                                        "TV en direct" -> Icons.Default.LiveTv
                                        "Lecteur" -> Icons.Default.PlayArrow
                                        "Favoris" -> Icons.Default.Favorite
                                        else -> Icons.Default.Extension
                                    },
                                    contentDescription = nom
                                )
                            },
                            label = { Text(nom, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    "MarocTv2.0",
                    color = Violet,
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                when (page) {
                    "Accueil" -> Accueil()
                    "Catalogue" -> Catalogue()
                    "TV en direct" -> TvDirect()
                    "Lecteur" -> LecteurVideo()
                    "Favoris" -> Favoris()
                    "Extensions" -> Extensions()
                }
            }
        }
    }
}

@Composable
fun Accueil() {
    Text("Bienvenue sur MarocTv2.0", color = Blanc, fontSize = 22.sp)
    Spacer(modifier = Modifier.height(12.dp))
    Text("Films, séries et télévision en direct.", color = Blanc)
    Spacer(modifier = Modifier.height(20.dp))
    Text("Découvrir", color = Violet, fontSize = 20.sp)
    Text("Ton espace de divertissement.", color = Blanc)
}

@Composable
fun Catalogue() {
    var recherche by remember { mutableStateOf("") }

    val contenus = listOf(
        "Films à découvrir",
        "Séries à découvrir",
        "Nouveautés",
        "Populaires"
    )

    OutlinedTextField(
        value = recherche,
        onValueChange = { recherche = it },
        label = { Text("Rechercher un film ou une série") },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    LazyColumn {
        items(contenus.filter {
            it.contains(recherche, ignoreCase = true)
        }) { titre ->
            CarteMedia(titre)
        }
    }
}

@Composable
fun TvDirect() {
    Text("Télévision en direct", color = Blanc, fontSize = 22.sp)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        "Ajoute une extension M3U pour charger tes chaînes.",
        color = Blanc
    )
}

@Composable
fun LecteurVideo() {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Entre une adresse vidéo.") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Text("Lecteur vidéo", color = Blanc, fontSize = 22.sp)

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Adresse de la vidéo") },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(10.dp))

    Button(
        onClick = {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
                exoPlayer.play()
                message = "Lecture lancée."
            } else {
                message = "Adresse vidéo invalide."
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Violet)
    ) {
        Text("Lire la vidéo")
    }

    Text(message, color = Blanc)

    Spacer(modifier = Modifier.height(12.dp))

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                player = exoPlayer
                useController = true
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

@Composable
fun Favoris() {
    Text("Mes favoris", color = Blanc, fontSize = 22.sp)
    Spacer(modifier = Modifier.height(12.dp))
    Text("Tes films et séries favoris apparaîtront ici.", color = Blanc)
}

@Composable
fun Extensions() {
    var adresse by remember { mutableStateOf("") }
    var extensions by remember {
        mutableStateOf(listOf<String>())
    }

    Text("Extensions", color = Blanc, fontSize = 22.sp)

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = adresse,
        onValueChange = { adresse = it },
        label = { Text("Adresse M3U ou JSON") },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(10.dp))

    Button(
        onClick = {
            if (
                adresse.startsWith("http://") ||
                adresse.startsWith("https://")
            ) {
                extensions = extensions + adresse
                adresse = ""
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Violet)
    ) {
        Text("Ajouter une extension")
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (extensions.isEmpty()) {
        Text("Aucune extension ajoutée.", color = Blanc)
    } else {
        LazyColumn {
            items(extensions) { extension ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        extension,
                        color = Blanc,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            extensions = extensions - extension
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
fun CarteMedia(titre: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Carte, RoundedCornerShape(12.dp))
            .clickable { }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Movie,
            contentDescription = null,
            tint = Violet
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(titre, color = Blanc, fontSize = 16.sp)
    }
}
