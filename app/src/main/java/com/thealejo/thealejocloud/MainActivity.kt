package com.thealejo.thealejocloud

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
            // Forzamos un tema oscuro para igualar iOS
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNube()
                }
            }
        }
    }
}

@Composable
fun AppNube() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("AlejoCloudPrefs", Context.MODE_PRIVATE)

    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var tipoConexion by remember { mutableStateOf(prefs.getInt("tipoConexion", 0)) } // 0 = Local, 1 = Externo

    var urlLocal by remember { mutableStateOf(prefs.getString("urlLocal", "http://0.0.0.0:0") ?: "http://0.0.0.0:0") }
    var urlExterna by remember { mutableStateOf(prefs.getString("urlExterna", "https://yourdomain.com") ?: "https://yourdomain.com") }

    var isLoggedIn by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var urlBaseActiva by remember { mutableStateOf("") }

    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (isLoggedIn) {
        VistaNube(token = token, baseURL = urlBaseActiva, currentPath = "/", alSalir = {
            isLoggedIn = false
            token = ""
        })
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Storage, contentDescription = "Logo", modifier = Modifier.size(80.dp), tint = Color(0xFF2196F3))
            Spacer(modifier = Modifier.height(16.dp))
            Text("TheAlejoCloud", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))

            // Custom Segmented Control
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.DarkGray)) {
                Box(modifier = Modifier.weight(1f).clickable { tipoConexion = 0 }.background(if (tipoConexion == 0) Color.Gray else Color.Transparent).padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("Red Local", color = Color.White)
                }
                Box(modifier = Modifier.weight(1f).clickable { tipoConexion = 1 }.background(if (tipoConexion == 1) Color.Gray else Color.Transparent).padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("Externo", color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Settings Disclosure
            Row(modifier = Modifier.fillMaxWidth().clickable { showSettings = !showSettings }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF2196F3))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Configuración de Servidores", color = Color(0xFF2196F3), fontSize = 16.sp)
                Spacer(modifier = Modifier.weight(1f))
                Icon(if (showSettings) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White)
            }

            AnimatedVisibility(visible = showSettings) {
                Column {
                    OutlinedTextField(value = urlLocal, onValueChange = { urlLocal = it }, label = { Text("URL Local") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = urlExterna, onValueChange = { urlExterna = it }, label = { Text("URL Externa") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White))

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
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
                                    // Guardar datos
                                    prefs.edit().putString("username", username).putString("password", password).putInt("tipoConexion", tipoConexion).putString("urlLocal", urlLocal).putString("urlExterna", urlExterna).apply()
                                    withContext(Dispatchers.Main) {
                                        token = jwt
                                        urlBaseActiva = base
                                        isLoggedIn = true
                                    }
                                } else {
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error de credenciales", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Conectar al Servidor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VistaNube(token: String, baseURL: String, currentPath: String, alSalir: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var archivos by remember { mutableStateOf<List<FBItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var subcarpeta by remember { mutableStateOf<String?>(null) }

    // Estados para selección múltiple y descargas
    var modoSeleccionActive by remember { mutableStateOf(false) }
    var itemsSeleccionados by remember { mutableStateOf(setOf<String>()) }
    var descargando by remember { mutableStateOf(false) }

    fun cargarArchivos() {
        isLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val rutaSegura = currentPath.replace(" ", "%20")
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

    // Selector para subir archivos (Replica el fileImporter de iOS)
    val launcherSubida = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                descargando = true // Usamos el mismo overlay visual para indicar que estamos trabajando
                withContext(Dispatchers.IO) {
                    try {
                        val client = OkHttpClient()
                        val inputStream = context.contentResolver.openInputStream(it)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()

                        if (bytes != null) {
                            var nombreArchivo = "upload_file"
                            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (cursor.moveToFirst()) nombreArchivo = cursor.getString(nameIndex)
                            }

                            val pathBase = if (currentPath.endsWith("/")) currentPath else "$currentPath/"
                            val destino = "$pathBase$nombreArchivo".replace(" ", "%20")
                            val requestBody = bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                            val request = Request.Builder().url("$baseURL/api/resources$destino").post(requestBody).header("X-Auth", token).build()

                            val response = client.newCall(request).execute()
                            if (response.isSuccessful) {
                                withContext(Dispatchers.Main) { cargarArchivos() }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    withContext(Dispatchers.Main) { descargando = false }
                }
            }
        }
    }

    // Lógica para descargar y abrir Share Sheet
    fun procesarDescargas(items: List<FBItem>) {
        scope.launch {
            descargando = true
            val urisToShare = ArrayList<Uri>()

            withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                for (item in items) {
                    if (item.isDir) continue
                    val rutaSegura = item.path.replace(" ", "%20")
                    val request = Request.Builder().url("$baseURL/api/raw$rutaSegura").header("X-Auth", token).build()
                    try {
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val inputStream = response.body?.byteStream()
                            val tempFile = File(context.cacheDir, item.name)
                            val outputStream = FileOutputStream(tempFile)
                            inputStream?.copyTo(outputStream)
                            outputStream.close()

                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
                            urisToShare.add(uri)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }

            descargando = false
            if (urisToShare.isNotEmpty()) {
                val intent = Intent().apply {
                    action = if (urisToShare.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                    type = "*/*"
                    if (urisToShare.size == 1) putExtra(Intent.EXTRA_STREAM, urisToShare.first())
                    else putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir archivo(s)"))

                modoSeleccionActive = false
                itemsSeleccionados = emptySet()
            }
        }
    }

    LaunchedEffect(currentPath) { cargarArchivos() }

    if (subcarpeta != null) {
        VistaNube(token, baseURL, subcarpeta!!) { subcarpeta = null }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (currentPath == "/") "Home" else currentPath.substringAfterLast("/"), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (currentPath == "/") {
                            if (!modoSeleccionActive) {
                                TextButton(onClick = alSalir) { Text("Cerrar Sesión", color = Color.Red, fontSize = 16.sp) }
                            }
                        } else {
                            IconButton(onClick = alSalir) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White) }
                        }
                    },
                    actions = {
                        if (archivos.isNotEmpty()) {
                            TextButton(onClick = {
                                modoSeleccionActive = !modoSeleccionActive
                                itemsSeleccionados = emptySet()
                            }) {
                                Text(if (modoSeleccionActive) "Cancelar" else "Seleccionar", color = Color.White, fontSize = 16.sp)
                            }
                        }
                        if (!modoSeleccionActive) {
                            IconButton(onClick = { launcherSubida.launch("*/*") }) {
                                Icon(Icons.Default.Add, contentDescription = "Añadir", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White)
                )
            },
            bottomBar = {
                if (modoSeleccionActive && itemsSeleccionados.isNotEmpty()) {
                    Surface(color = Color.DarkGray) {
                        Button(
                            onClick = {
                                val itemsToDownload = archivos.filter { it.path in itemsSeleccionados }
                                procesarDescargas(itemsToDownload)
                            },
                            modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Descargar seleccionados (${itemsSeleccionados.size})", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF2196F3))
                } else if (archivos.isEmpty()) {
                    Text("Carpeta vacía", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn {
                        items(archivos) { archivo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !modoSeleccionActive || !archivo.isDir) {
                                        if (modoSeleccionActive) {
                                            if (!archivo.isDir) {
                                                itemsSeleccionados = if (itemsSeleccionados.contains(archivo.path)) {
                                                    itemsSeleccionados - archivo.path
                                                } else {
                                                    itemsSeleccionados + archivo.path
                                                }
                                            }
                                        } else {
                                            if (archivo.isDir) subcarpeta = archivo.path
                                            else procesarDescargas(listOf(archivo))
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (modoSeleccionActive && !archivo.isDir) {
                                    Icon(
                                        imageVector = if (itemsSeleccionados.contains(archivo.path)) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (itemsSeleccionados.contains(archivo.path)) Color(0xFF2196F3) else Color.Gray,
                                        modifier = Modifier.padding(end = 12.dp).size(24.dp)
                                    )
                                }

                                Icon(
                                    imageVector = if (archivo.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (archivo.isDir) Color(0xFF2196F3) else Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(archivo.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    if (!archivo.isDir && archivo.size != null) {
                                        Text("${archivo.size / 1024} KB", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                                if (archivo.isDir) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color.DarkGray)
                        }
                    }
                }

                // Overlay de carga general (para descargas o subidas)
                if (descargando) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(Color.DarkGray, RoundedCornerShape(16.dp)).padding(30.dp)) {
                            CircularProgressIndicator(color = Color(0xFF2196F3))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Procesando...", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}