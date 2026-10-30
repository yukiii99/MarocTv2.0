
package com.marociptv.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Violet = Color(0xFF8B5CF6)
private val Fond = Color(0xFF100D16)
private val Carte = Color(0xFF211A2D)

data class ExtensionSource(
    val name: String,
    val url: String,
    val type: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarocTvApp(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarocTvApp(context: Context) {
    val preferences = remember {
        context.getSharedPreferences("maroctv", Context.MODE_PRIVATE)
    }

    var page by remember { mutableStateOf("Accueil") }
    var recherche by remember { mutableStateOf("") }
    var nom by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("M3U") }
    var message by remember { mutableStateOf("") }

    var extensions by remember {
        mutableStateOf(loadExtensions(preferences))
    }

    var favoris by remember { mutableStateOf(setOf<String>()) }

    fun enregistrer(nouvelles: List<ExtensionSource>) {
        extensions = nouvelles
        saveExtensions(preferences, nouvelles)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            background = Fond,
            surface = Carte
        )
    ) {
        Scaffold(
            containerColor = Fond,
            topBar = {
                TopAppBar(
                    title = {
                        Text("MarocTv2.0", color = Color.White)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Fond
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Carte) {
                    NavigationBarItem(
                        selected = page == "Accueil",
                        onClick = { page = "Accueil" },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Accueil") }
                    )
                    NavigationBarItem(
                        selected = page == "Catalogue",
                        onClick = { page = "Catalogue" },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Catalogue") }
                    )
                    NavigationBarItem(
                        selected = page == "TV",
                        onClick = { page = "TV" },
                        icon = { Icon(Icons.Default.LiveTv, null) },
                        label = { Text("TV") }
                    )
                    NavigationBarItem(
                        selected = page == "Favoris",
                        onClick = { page = "Favoris" },
                        icon = { Icon(Icons.Default.Favorite, null) },
                        label = { Text("Favoris") }
                    )
                    NavigationBarItem(
                        selected = page == "Extensions",
                        onClick = { page = "Extensions" },
                        icon = { Icon(Icons.Default.Extension, null) },
                        label = { Text("Extensions") }
                    )
                }
            }
        ) { padding ->

            if (page == "Extensions") {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    Text(
                        "Mes extensions",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nom,
                        onValueChange = { nom = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nom de l'extension") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Adresse URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("Type d'extension", color = Color.White)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = type == "M3U",
                            onClick = { type = "M3U" }
                        )
                        Text("TV M3U", color = Color.White)

                        Spacer(Modifier.width(16.dp))

                        RadioButton(
                            selected = type == "JSON",
                            onClick = { type = "JSON" }
                        )
                        Text("Catalogue JSON", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (
                                nom.isNotBlank() &&
                                (url.startsWith("https://") ||
                                 url.startsWith("http://"))
                            ) {
                                if (extensions.any { it.url == url.trim() }) {
                                    message = "Cette extension existe déjà."
                                } else {
                                    enregistrer(
                                        extensions + ExtensionSource(
                                            nom.trim(),
                                            url.trim(),
                                            type
                                        )
                                    )
                                    message = "Extension enregistrée !"
                                    nom = ""
                                    url = ""
                                }
                            } else {
                                message = "Vérifie le nom et l'adresse URL."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ajouter l'extension")
                    }

                    if (message.isNotBlank()) {
                        Text(message, color = Violet)
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Extensions installées : ${extensions.size}",
                        color = Color.White
                    )

                    LazyColumn {
                        items(extensions) { extension ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Carte
                                )
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            extension.name,
                                            color = Color.White
                                        )
                                        Text(
                                            extension.type,
                                            color = Violet
                                        )
                                        Text(
                                            extension.url,
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            enregistrer(
                                                extensions - extension
                                            )
                                            message = "Extension supprimée."
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Supprimer",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = recherche,
                        onValueChange = { recherche = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Rechercher") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(20.dp))

                    Text(
                        page,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(Modifier.height(12.dp))

                    val elements = when (page) {
                        "Accueil" -> listOf(
                            "Bienvenue sur MarocTv2.0",
                            "Films",
                            "Séries",
                            "TV en direct",
                            "Mes extensions"
                        )
                        "Catalogue" -> listOf("Films", "Séries")
                        "TV" -> listOf("Chaînes TV en direct")
                        else -> favoris.toList()
                    }.filter {
                        it.contains(recherche, ignoreCase = true)
                    }

                    LazyColumn {
                        items(elements) { element ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Carte
                                )
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        element,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            favoris =
                                                if (element in favoris) {
                                                    favoris - element
                                                } else {
                                                    favoris + element
                                                }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Favorite,
                                            contentDescription = "Favori",
                                            tint = if (element in favoris)
                                                Violet else Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadExtensions(
    preferences: android.content.SharedPreferences
): List<ExtensionSource> {
    val count = preferences.getInt("count", 0)
    return (0 until count).mapNotNull { index ->
        val name = preferences.getString("name_$index", null)
        val url = preferences.getString("url_$index", null)
        val type = preferences.getString("type_$index", null)

        if (name != null && url != null && type != null) {
            ExtensionSource(name, url, type)
        } else {
            null
        }
    }
}

private fun saveExtensions(
    preferences: android.content.SharedPreferences,
    extensions: List<ExtensionSource>
) {
    val editor = preferences.edit()
    editor.clear()
    editor.putInt("count", extensions.size)

    extensions.forEachIndexed { index, extension ->
        editor.putString("name_$index", extension.name)
        editor.putString("url_$index", extension.url)
        editor.putString("type_$index", extension.type)
    }

    editor.apply()
}
