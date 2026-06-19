package com.thealejo.thealejocloud

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

// DTOs
data class LoginPayload(val username: String, val password: String)
data class FilebrowserResponse(val items: List<FBItem>?)
data class FBItem(val name: String, val path: String, val isDir: Boolean, val size: Long?)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNube()
        }
    }
}

// Extensión para Extensiones y Colores
val FBItem.fileExtension: String
    get() = name.substringAfterLast('.', "").lowercase()

val FBItem.hasPreview: Boolean
    get() = !isDir && listOf("jpg", "jpeg", "png", "gif", "heic", "webp", "mp4", "mov", "avi", "mkv", "pdf").contains(fileExtension)

val FBItem.iconVector: ImageVector
    get() = if (isDir) Icons.Default.Folder else when (fileExtension) {
        "jpg", "jpeg", "png", "gif", "heic", "webp" -> Icons.Default.Image
        "mp4", "mov", "avi", "mkv" -> Icons.Default.Movie
        "mp3", "wav", "m4a", "flac" -> Icons.Default.AudioFile
        "pdf" -> Icons.Default.PictureAsPdf
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
        "txt", "md", "csv", "json" -> Icons.Default.Description
        "swift", "py", "js", "html", "css", "cpp", "kt" -> Icons.Default.Code
        else -> Icons.Default.InsertDriveFile
    }

val FBItem.iconColorUI: Color
    get() = if (isDir) Color(0xFF2196F3) else when (fileExtension) {
        "jpg", "jpeg", "png", "gif", "heic", "webp" -> Color(0xFF9C27B0)
        "mp4", "mov", "avi", "mkv", "pdf" -> Color(0xFFF44336)
        "mp3", "wav", "m4a", "flac" -> Color(0xFFFF9800)
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFFFFC107)
        "txt", "md", "csv", "json" -> Color(0xFF4CAF50)
        "swift", "py", "js", "html", "css", "cpp", "kt" -> Color(0xFF009688)
        else -> Color.Gray
    }

@Composable
fun AppNube() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("AlejoCloudPrefs", Context.MODE_PRIVATE)

    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var tipoConexion by remember { mutableStateOf(prefs.getInt("tipoConexion", 0)) }

    var urlLocal by remember { mutableStateOf(prefs.getString("urlLocal", "http://0.0.0.0:0") ?: "") }
    var urlExterna by remember { mutableStateOf(prefs.getString("urlExterna", "https://yourdomain.com") ?: "") }

    var isAutoTheme by remember { mutableStateOf(prefs.getBoolean("isAutoTheme", true)) }
    var isDarkMode by remember { mutableStateOf(prefs.getBoolean("isDarkMode", false)) }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val currentlyDark = if (isAutoTheme) systemDark else isDarkMode

    // Colores dinámicos suavizados equivalentes a iOS
    val bgColor by animateColorAsState(if (currentlyDark) Color(0xFF141414) else Color(0xFFF5F5FA), animationSpec = tween(600), label = "bg")
    val surfaceColor by animateColorAsState(if (currentlyDark) Color(0xFF1C1C1E) else Color.White, animationSpec = tween(600), label = "surface")
    val textColor by animateColorAsState(if (currentlyDark) Color.White else Color.Black, animationSpec = tween(600), label = "text")

    var isLoggedIn by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var urlBaseActiva by remember { mutableStateOf("") }

    var isLoadingLogin by remember { mutableStateOf(false) }
    var errorMensaje by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = if (currentlyDark) darkColorScheme(background = bgColor, surface = surfaceColor) else lightColorScheme(background = bgColor, surface = surfaceColor)) {
        Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
            Crossfade(targetState = isLoggedIn, label = "login_transition") { loggedIn ->
                if (loggedIn) {
                    NubeView(token = token, baseURL = urlBaseActiva, isDark = currentlyDark, surfaceColor = surfaceColor, textColor = textColor) {
                        isLoggedIn = false
                        token = ""
                    }
                } else {
                    // PANTALLA DE LOGIN
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(30.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(40.dp))
                        Icon(Icons.Default.Dns, contentDescription = "Logo", modifier = Modifier.size(80.dp), tint = Color(0xFF2196F3))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("TheAlejoCloud", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(24.dp))

                        // Theme Controls
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                isAutoTheme = !isAutoTheme
                                prefs.edit().putBoolean("isAutoTheme", isAutoTheme).apply()
                            }) {
                                Icon(if (isAutoTheme) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, contentDescription = null, tint = if (isAutoTheme) Color(0xFF2196F3) else Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auto", color = textColor)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text("Claro", fontSize = 12.sp, color = Color.Gray)
                            Switch(
                                checked = currentlyDark,
                                onCheckedChange = {
                                    isAutoTheme = false
                                    isDarkMode = it
                                    prefs.edit().putBoolean("isAutoTheme", false).putBoolean("isDarkMode", it).apply()
                                },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Text("Oscuro", fontSize = 12.sp, color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        // Segmented Control (Red Local / Externo)
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceColor)
                            .padding(4.dp)
                        ) {
                            Box(modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (tipoConexion == 0) Color.Gray.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { tipoConexion = 0 }
                                .padding(10.dp), contentAlignment = Alignment.Center) {
                                Text("Red Local", color = textColor, fontWeight = FontWeight.Medium)
                            }
                            Box(modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (tipoConexion == 1) Color.Gray.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { tipoConexion = 1 }
                                .padding(10.dp), contentAlignment = Alignment.Center) {
                                Text("Externo", color = textColor, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        // Settings Disclosure
                        Column(modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceColor)
                            .padding(16.dp)) {
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSettings = !showSettings }, verticalAlignment = Alignment.CenterVertically) {
                                Text("⚙️ Configuración de Servidores", color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Icon(if (showSettings) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, contentDescription = null, tint = textColor)
                            }
                            AnimatedVisibility(visible = showSettings) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    OutlinedTextField(value = urlLocal, onValueChange = { urlLocal = it }, label = { Text("http://...") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(value = urlExterna, onValueChange = { urlExterna = it }, label = { Text("https://...") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Column(modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceColor)
                            .padding(8.dp)) {
                            TextField(value = username, onValueChange = { username = it }, placeholder = { Text("Usuario") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = textColor, unfocusedTextColor = textColor))
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                            TextField(value = password, onValueChange = { password = it }, placeholder = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = textColor, unfocusedTextColor = textColor))
                        }

                        if (errorMensaje.isNotEmpty()) {
                            Text(errorMensaje, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                isLoadingLogin = true
                                errorMensaje = ""
                                val base = if (tipoConexion == 0) urlLocal.removeSuffix("/") else urlExterna.removeSuffix("/")
                                scope.launch {
                                    try {
                                        val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
                                        val body = Gson().toJson(LoginPayload(username, password)).toRequestBody("application/json".toMediaTypeOrNull())
                                        val request = Request.Builder().url("$base/api/login").post(body).build()
                                        withContext(Dispatchers.IO) {
                                            val response = client.newCall(request).execute()
                                            if (response.isSuccessful) {
                                                val jwt = response.body?.string() ?: ""
                                                prefs.edit().putString("username", username).putString("password", password).putInt("tipoConexion", tipoConexion).putString("urlLocal", urlLocal).putString("urlExterna", urlExterna).apply()
                                                withContext(Dispatchers.Main) {
                                                    token = jwt
                                                    urlBaseActiva = base
                                                    isLoggedIn = true
                                                    isLoadingLogin = false
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    errorMensaje = "Credenciales incorrectas"
                                                    isLoadingLogin = false
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        errorMensaje = "Error de red: ${e.localizedMessage}"
                                        isLoadingLogin = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoadingLogin
                        ) {
                            if (isLoadingLogin) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("Conectar al Servidor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NubeView(token: String, baseURL: String, isDark: Boolean, surfaceColor: Color, textColor: Color, alSalir: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600

    var currentPath by remember { mutableStateOf("/") }
    var archivos by remember { mutableStateOf<List<FBItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var modoSeleccionActive by remember { mutableStateOf(false) }
    var itemsSeleccionados by remember { mutableStateOf(setOf<String>()) }
    var agruparPorTipo by remember { mutableStateOf(false) }

    var estadoOperacion by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    var showMenuCrear by remember { mutableStateOf(false) }
    var showDialogCarpeta by remember { mutableStateOf(false) }
    var nombreNuevaCarpeta by remember { mutableStateOf("") }

    val carpetas = archivos.filter { it.isDir }.sortedBy { it.name.lowercase() }
    val documentos = archivos.filter { !it.isDir }.sortedBy { it.name.lowercase() }

    val currentTitle = if (currentPath == "/") "Home" else currentPath.removeSuffix("/").substringAfterLast("/")

    fun cargarArchivos(path: String) {
        isLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val rutaSegura = path.replace(" ", "%20")
                val request = Request.Builder().url("$baseURL/api/resources$rutaSegura").header("X-Auth", token).build()
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val datos = Gson().fromJson(response.body?.string(), FilebrowserResponse::class.java)
                        withContext(Dispatchers.Main) {
                            archivos = datos.items ?: emptyList()
                            isLoading = false
                        }
                    } else {
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    fun navegarInPlace(nuevoPath: String) {
        archivos = emptyList()
        modoSeleccionActive = false
        itemsSeleccionados = emptySet()
        currentPath = nuevoPath
        cargarArchivos(nuevoPath)
    }

    fun retroceder() {
        if (currentPath == "/") return
        var parentPath = currentPath.removeSuffix("/").substringBeforeLast("/")
        if (parentPath.isEmpty()) parentPath = "/"
        else parentPath += "/"
        navegarInPlace(parentPath)
    }

    fun crearCarpeta() {
        if (nombreNuevaCarpeta.isBlank()) return
        showDialogCarpeta = false
        isProcessing = true
        estadoOperacion = "Creando carpeta..."
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    var rutaDestino = currentPath
                    if (!rutaDestino.endsWith("/")) rutaDestino += "/"
                    rutaDestino += "$nombreNuevaCarpeta/"
                    val rutaSegura = rutaDestino.replace(" ", "%20")
                    val body = "".toRequestBody(null)
                    val request = Request.Builder().url("$baseURL/api/resources$rutaSegura").post(body).header("X-Auth", token).build()
                    client.newCall(request).execute()
                } catch (e: Exception) { e.printStackTrace() }
                withContext(Dispatchers.Main) {
                    nombreNuevaCarpeta = ""
                    isProcessing = false
                    cargarArchivos(currentPath)
                }
            }
        }
    }

    fun ContentResolver.createStreamingRequestBody(uri: Uri): RequestBody {
        return object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
            override fun writeTo(sink: BufferedSink) {
                openInputStream(uri)?.source()?.use { source ->
                    sink.writeAll(source)
                }
            }
        }
    }

    val launcherSubidaMasiva = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isProcessing = true
        estadoOperacion = "Subiendo archivos..."
        scope.launch {
            withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder().writeTimeout(5, TimeUnit.MINUTES).build()
                uris.forEachIndexed { index, uri ->
                    withContext(Dispatchers.Main) { estadoOperacion = "Subiendo ${index + 1} de ${uris.size}..." }
                    try {
                        var nombreArchivo = "upload_file"
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst()) nombreArchivo = cursor.getString(nameIndex)
                        }
                        val pathBase = if (currentPath.endsWith("/")) currentPath else "$currentPath/"
                        val destino = "$pathBase$nombreArchivo".replace(" ", "%20")

                        val requestBody = context.contentResolver.createStreamingRequestBody(uri)
                        val request = Request.Builder().url("$baseURL/api/resources$destino").post(requestBody).header("X-Auth", token).build()
                        client.newCall(request).execute()
                    } catch (e: Exception) { e.printStackTrace() }
                }
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    cargarArchivos(currentPath)
                }
            }
        }
    }

    fun descargarSeleccion() {
        if (itemsSeleccionados.isEmpty()) return
        isProcessing = true
        estadoOperacion = "Preparando descarga..."
        scope.launch {
            val urisToShare = ArrayList<Uri>()
            withContext(Dispatchers.IO) {
                val client = OkHttpClient()

                if (itemsSeleccionados.size == 1) {
                    val path = itemsSeleccionados.first()
                    val item = archivos.firstOrNull { it.path == path }
                    val isZip = item?.isDir == true
                    val rutaSegura = path.replace(" ", "%20")
                    val urlStr = if (isZip) "$baseURL/api/raw$rutaSegura?algo=zip" else "$baseURL/api/raw$rutaSegura"
                    val nombreGuardado = if (isZip) "${item?.name ?: "Carpeta"}.zip" else item?.name ?: "Archivo"

                    val request = Request.Builder().url(urlStr).header("X-Auth", token).build()
                    try {
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val tempFile = File(context.cacheDir, nombreGuardado)
                            val outputStream = FileOutputStream(tempFile)
                            response.body?.byteStream()?.copyTo(outputStream)
                            outputStream.close()
                            urisToShare.add(FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile))
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                } else {
                    val pathsStr = itemsSeleccionados.joinToString(",")
                    val querySegura = pathsStr.replace(" ", "%20")
                    val urlStr = "$baseURL/api/raw/?files=$querySegura&algo=zip"
                    val request = Request.Builder().url(urlStr).header("X-Auth", token).build()
                    try {
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val tempFile = File(context.cacheDir, "Seleccion_Multiple.zip")
                            val outputStream = FileOutputStream(tempFile)
                            response.body?.byteStream()?.copyTo(outputStream)
                            outputStream.close()
                            urisToShare.add(FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile))
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            isProcessing = false
            if (urisToShare.isNotEmpty()) {
                val intent = Intent().apply {
                    action = if (urisToShare.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                    type = "*/*"
                    if (urisToShare.size == 1) putExtra(Intent.EXTRA_STREAM, urisToShare.first())
                    else putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Guardar archivo"))
                modoSeleccionActive = false
                itemsSeleccionados = emptySet()
            }
        }
    }

    LaunchedEffect(Unit) { cargarArchivos(currentPath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isWideScreen) currentTitle else "", fontWeight = FontWeight.Bold, color = textColor) },
                navigationIcon = {
                    if (!modoSeleccionActive && currentPath == "/") {
                        IconButton(onClick = alSalir) { Icon(Icons.Default.Logout, contentDescription = "Salir", tint = Color.Red) }
                    }
                },
                actions = {
                    if (archivos.isNotEmpty()) {
                        TextButton(onClick = {
                            modoSeleccionActive = !modoSeleccionActive
                            itemsSeleccionados = emptySet()
                        }) {
                            Text(if (modoSeleccionActive) "Cancelar" else "Seleccionar", color = textColor, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (!modoSeleccionActive) {
                        Box {
                            IconButton(onClick = { showMenuCrear = true }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Añadir", tint = textColor)
                            }
                            DropdownMenu(expanded = showMenuCrear, onDismissRequest = { showMenuCrear = false }) {
                                DropdownMenuItem(text = { Text("Subir Archivo") }, leadingIcon = { Icon(Icons.Default.UploadFile, null) }, onClick = { showMenuCrear = false; launcherSubidaMasiva.launch("*/*") })
                                DropdownMenuItem(text = { Text("Nueva Carpeta") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { showMenuCrear = false; showDialogCarpeta = true })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = modoSeleccionActive && itemsSeleccionados.isNotEmpty(), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(25.dp)) {
                    Button(
                        onClick = { descargarSeleccion() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp)
                            .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF2196F3).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Descargar (${itemsSeleccionados.size})", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isWideScreen) {
                // DISEÑO SPLIT VIEW (Tablet)
                Row(modifier = Modifier.fillMaxSize()) {
                    // Panel Izquierdo (26%)
                    Column(modifier = Modifier
                        .weight(0.26f)
                        .fillMaxHeight()
                        .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                        .verticalScroll(rememberScrollState())) {
                        if (currentPath != "/") {
                            TextButton(onClick = { retroceder() }, modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Volver", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                        }
                        Text(if (currentPath == "/") "Carpetas Principales" else "Subcarpetas", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor, modifier = Modifier.padding(16.dp))

                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally), color = Color(0xFF2196F3))
                        } else if (carpetas.isEmpty()) {
                            Text("No hay subcarpetas.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        } else {
                            carpetas.forEach { carpeta ->
                                FolderRowView(carpeta, itemsSeleccionados.contains(carpeta.path), modoSeleccionActive, textColor) {
                                    if (modoSeleccionActive) {
                                        itemsSeleccionados = if (itemsSeleccionados.contains(carpeta.path)) itemsSeleccionados - carpeta.path else itemsSeleccionados + carpeta.path
                                    } else {
                                        navegarInPlace(carpeta.path)
                                    }
                                }
                            }
                        }
                    }

                    VerticalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)

                    // Panel Derecho (74%)
                    Column(modifier = Modifier.weight(0.74f).fillMaxHeight()) {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                            .padding(4.dp)) {
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { agruparPorTipo = false }.background(if (!agruparPorTipo) surfaceColor else Color.Transparent).padding(8.dp), contentAlignment = Alignment.Center) { Text("General", color = if(!agruparPorTipo) textColor else Color.Gray, fontWeight = if(!agruparPorTipo) FontWeight.Bold else FontWeight.Normal) }
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { agruparPorTipo = true }.background(if (agruparPorTipo) surfaceColor else Color.Transparent).padding(8.dp), contentAlignment = Alignment.Center) { Text("Por Tipo", color = if(agruparPorTipo) textColor else Color.Gray, fontWeight = if(agruparPorTipo) FontWeight.Bold else FontWeight.Normal) }
                        }

                        if (isLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF2196F3)) }
                        } else if (documentos.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay archivos sueltos aquí.", color = Color.Gray) }
                        } else {
                            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 110.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                                if (agruparPorTipo) {
                                    val agrupados = documentos.groupBy { it.fileExtension }
                                    agrupados.keys.sorted().forEach { ext ->
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Text(if (ext.isEmpty()) "OTROS" else ext.uppercase(), fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                                        }
                                        items(agrupados[ext]!!, key = { it.path }) { doc ->
                                            ItemCardView(doc, itemsSeleccionados.contains(doc.path), modoSeleccionActive, baseURL, token, surfaceColor, textColor) {
                                                if (modoSeleccionActive) itemsSeleccionados = if (itemsSeleccionados.contains(doc.path)) itemsSeleccionados - doc.path else itemsSeleccionados + doc.path
                                                else { itemsSeleccionados = setOf(doc.path); descargarSeleccion() }
                                            }
                                        }
                                    }
                                } else {
                                    items(documentos, key = { it.path }) { doc ->
                                        ItemCardView(doc, itemsSeleccionados.contains(doc.path), modoSeleccionActive, baseURL, token, surfaceColor, textColor) {
                                            if (modoSeleccionActive) itemsSeleccionados = if (itemsSeleccionados.contains(doc.path)) itemsSeleccionados - doc.path else itemsSeleccionados + doc.path
                                            else { itemsSeleccionados = setOf(doc.path); descargarSeleccion() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // DISEÑO MÓVIL (Todo unificado en un solo LazyVerticalGrid infinito sin scroll anidado)
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header fijo
                    if (currentPath != "/") {
                        VStack(alignment = Alignment.Start) {
                            TextButton(onClick = { retroceder() }, modifier = Modifier.padding(start = 8.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Volver", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Text(currentTitle, fontStyle = androidx.compose.ui.text.font.FontStyle.Normal, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }

                    if (!isLoading && archivos.isEmpty()) {
                        // Empty State Móvil Identico a iOS
                        Column(modifier = Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(70.dp), tint = Color(0xFF2196F3).copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Esta carpeta está vacía", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Puedes subir archivos o crear una carpeta en el botón '+'.", fontSize = 14.sp, color = Color.Gray.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                        }
                    } else {
                        // Grid general para todo el contenido
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (carpetas.isNotEmpty() || documentos.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                                        .padding(4.dp)) {
                                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { agruparPorTipo = false }.background(if (!agruparPorTipo) surfaceColor else Color.Transparent).padding(8.dp), contentAlignment = Alignment.Center) { Text("General", color = if(!agruparPorTipo) textColor else Color.Gray, fontWeight = if(!agruparPorTipo) FontWeight.Bold else FontWeight.Normal) }
                                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { agruparPorTipo = true }.background(if (agruparPorTipo) surfaceColor else Color.Transparent).padding(8.dp), contentAlignment = Alignment.Center) { Text("Por Tipo", color = if(agruparPorTipo) textColor else Color.Gray, fontWeight = if(agruparPorTipo) FontWeight.Bold else FontWeight.Normal) }
                                    }
                                }
                            }

                            if (carpetas.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text("Carpetas", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor, modifier = Modifier.padding(top = 8.dp))
                                }
                                items(carpetas, key = { it.path }) { carpeta ->
                                    ItemCardView(carpeta, itemsSeleccionados.contains(carpeta.path), modoSeleccionActive, baseURL, token, surfaceColor, textColor) {
                                        if (modoSeleccionActive) itemsSeleccionados = if (itemsSeleccionados.contains(carpeta.path)) itemsSeleccionados - carpeta.path else itemsSeleccionados + carpeta.path
                                        else navegarInPlace(carpeta.path)
                                    }
                                }
                            }

                            if (documentos.isNotEmpty()) {
                                if (agruparPorTipo) {
                                    val agrupados = documentos.groupBy { it.fileExtension }
                                    agrupados.keys.sorted().forEach { ext ->
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Text(if (ext.isEmpty()) "OTROS" else ext.uppercase(), fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                                        }
                                        items(agrupados[ext]!!, key = { it.path }) { doc ->
                                            ItemCardView(doc, itemsSeleccionados.contains(doc.path), modoSeleccionActive, baseURL, token, surfaceColor, textColor) {
                                                if (modoSeleccionActive) itemsSeleccionados = if (itemsSeleccionados.contains(doc.path)) itemsSeleccionados - doc.path else itemsSeleccionados + doc.path
                                                else { itemsSeleccionados = setOf(doc.path); descargarSeleccion() }
                                            }
                                        }
                                    }
                                } else {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text("Archivos", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                                    }
                                    items(documentos, key = { it.path }) { doc ->
                                        ItemCardView(doc, itemsSeleccionados.contains(doc.path), modoSeleccionActive, baseURL, token, surfaceColor, textColor) {
                                            if (modoSeleccionActive) itemsSeleccionados = if (itemsSeleccionados.contains(doc.path)) itemsSeleccionados - doc.path else itemsSeleccionados + doc.path
                                            else { itemsSeleccionados = setOf(doc.path); descargarSeleccion() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Overlay Cargando
            if (isProcessing) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(surfaceColor, RoundedCornerShape(16.dp)).padding(30.dp)) {
                        CircularProgressIndicator(color = Color(0xFF2196F3))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(estadoOperacion, color = textColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Dialogo Nueva Carpeta
    if (showDialogCarpeta) {
        AlertDialog(
            onDismissRequest = { showDialogCarpeta = false },
            title = { Text("Nueva Carpeta", color = textColor) },
            text = {
                OutlinedTextField(value = nombreNuevaCarpeta, onValueChange = { nombreNuevaCarpeta = it }, placeholder = { Text("Nombre") }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { crearCarpeta() }) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { showDialogCarpeta = false; nombreNuevaCarpeta = "" }) { Text("Cancelar", color = Color.Red) } },
            containerColor = surfaceColor
        )
    }
}

// Componente helper para simplificar vistas verticales
@Composable
fun VStack(alignment: Alignment.Horizontal = Alignment.CenterHorizontally, spacing: Int = 0, content: @Composable ColumnScope.() -> Unit) {
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(spacing.dp), content = content)
}

@Composable
fun FolderRowView(carpeta: FBItem, isSelected: Boolean, isSelectionMode: Boolean, textColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF2196F3).copy(alpha = 0.2f) else Color.Transparent)
            .border(if (isSelected) 1.5.dp else 0.dp, if (isSelected) Color(0xFF2196F3) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = if (isSelected) Color(0xFF2196F3) else Color.Gray.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.width(12.dp))
        }
        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(carpeta.name, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!isSelectionMode) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ItemCardView(archivo: FBItem, isSelected: Boolean, isSelectionMode: Boolean, baseURL: String, token: String, surfaceColor: Color, textColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = if (isSelected) 8.dp else 2.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = if (isSelected) 0.1f else 0.04f))
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(if (isSelected) 2.dp else 0.dp, if (isSelected) Color(0xFF2196F3).copy(alpha = 0.8f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
            if (archivo.hasPreview) {
                val imageRequest = ImageRequest.Builder(LocalContext.current)
                    .data("$baseURL/api/preview/thumb${archivo.path.replace(" ", "%20")}")
                    .addHeader("X-Auth", token)
                    .crossfade(true)
                    .build()
                AsyncImage(model = imageRequest, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
            } else {
                Icon(archivo.iconVector, contentDescription = null, tint = archivo.iconColorUI, modifier = Modifier.size(45.dp))
            }

            if (isSelectionMode) {
                Icon(
                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF2196F3) else Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-10).dp).background(surfaceColor, CircleShape).padding(2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(archivo.name, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (!archivo.isDir && archivo.size != null) {
            Text("${archivo.size / 1024} KB", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}