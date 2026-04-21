package com.example.tallerapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.content.ContextCompat

import com.google.accompanist.permissions.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

/*
Aquí se inicializa la interfaz usando Jetpack Compose.
*/
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PantallaPermisos()
        }
    }
}

/*
Modelo de datos que representa una foto junto con su ubicación.
*/
data class FotoUbicacion(
    val uri: String,
    val latLng: LatLng,
    val nombre: String
)

/*
Pantalla encargada de solicitar permisos de cámara y ubicación.
*/
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PantallaPermisos() {

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Solicita permiso de cámara al iniciar
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    // Luego solicita ubicación
    LaunchedEffect(cameraPermission.status.isGranted) {
        if (cameraPermission.status.isGranted && !locationPermission.status.isGranted) {
            locationPermission.launchPermissionRequest()
        }
    }

    // Si ambos permisos están activos se muestra la app
    if (cameraPermission.status.isGranted && locationPermission.status.isGranted) {
        PantallaPrincipal()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("La app no puede funcionar sin permisos")
        }
    }
}

/*
Pantalla principal que divide la interfaz en dos regiones:
cámara y mapa, adaptándose a la orientación.
*/
@Composable
fun PantallaPrincipal() {

    val listaFotos = remember { mutableStateListOf<FotoUbicacion>() }
    var recorridoActivo by remember { mutableStateOf(false) }

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {

        Row(Modifier.fillMaxSize()) {

            Box(Modifier.weight(1f)) {
                MapaView(listaFotos, recorridoActivo) {
                    recorridoActivo = it
                }
            }

            Box(Modifier.weight(1f)) {
                CameraView(listaFotos, recorridoActivo)
            }
        }

    } else {

        Column(Modifier.fillMaxSize()) {

            Box(Modifier.weight(1f)) {
                CameraView(listaFotos, recorridoActivo)
            }

            Box(Modifier.weight(1f)) {
                MapaView(listaFotos, recorridoActivo) {
                    recorridoActivo = it
                }
            }
        }
    }
}

/*
Vista de cámara:
- Muestra preview
- Permite tomar fotos
- Cambiar cámara
- Muestra galería inferior
*/
@Composable
fun CameraView(
    listaFotos: MutableList<FotoUbicacion>,
    recorridoActivo: Boolean
) {

    val context = LocalContext.current
    val lifecycleOwner = context as ComponentActivity
    val previewView = remember { PreviewView(context) }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Column(Modifier.fillMaxSize()) {

        // Vista de cámara
        AndroidView(
            { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Botones de cámara
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            Button(
                onClick = {

                    val fileName = "foto_${System.currentTimeMillis()}.jpg"

                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/TallerApp")
                    }

                    val output = ImageCapture.OutputFileOptions
                        .Builder(
                            context.contentResolver,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                        ).build()

                    imageCapture?.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {

                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {

                                val uri = result.savedUri
                                val locationClient = LocationServices.getFusedLocationProviderClient(context)

                                try {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        locationClient.lastLocation.addOnSuccessListener { loc ->
                                            if (loc != null && uri != null) {
                                                listaFotos.add(
                                                    FotoUbicacion(
                                                        uri.toString(),
                                                        LatLng(loc.latitude, loc.longitude),
                                                        fileName
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } catch (_: SecurityException) {}
                            }

                            override fun onError(exception: ImageCaptureException) {}
                        }
                    )
                },
                enabled = recorridoActivo
            ) {
                Text("Foto")
            }

            Button(onClick = {
                lensFacing =
                    if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT
                    else
                        CameraSelector.LENS_FACING_BACK
            }) {
                Text("Cambiar")
            }
        }

        // Galería de fotos
        Box(
            Modifier.fillMaxWidth().height(100.dp).padding(8.dp)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {

                LazyRow(Modifier.padding(8.dp)) {
                    items(listaFotos) { foto ->

                        val uri = Uri.parse(foto.uri)
                        val input = context.contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(input)

                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp).padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Configuración de la cámara
    LaunchedEffect(lensFacing) {

        val provider = ProcessCameraProvider.getInstance(context).get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val capture = ImageCapture.Builder().build()
        imageCapture = capture

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
    }
}

/*
Vista del mapa:
- Muestra ubicación
- Muestra fotos como marcadores
- Controla recorrido
*/
@SuppressLint("MissingPermission")
@Composable
fun MapaView(
    listaFotos: MutableList<FotoUbicacion>,
    recorridoActivo: Boolean,
    onRecorridoChange: (Boolean) -> Unit
) {

    val context = LocalContext.current
    val locationClient = LocationServices.getFusedLocationProviderClient(context)

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    val cameraState = rememberCameraPositionState()

    // Obtiene ubicación actual
    LaunchedEffect(Unit) {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationClient.lastLocation.addOnSuccessListener {
                    it?.let { loc ->
                        userLocation = LatLng(loc.latitude, loc.longitude)
                    }
                }
            }
        } catch (_: SecurityException) {}
    }

    Column(Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.weight(1f),
            cameraPositionState = cameraState
        ) {

            userLocation?.let {
                Marker(state = MarkerState(it), title = "Mi ubicación")

                LaunchedEffect(it) {
                    cameraState.move(CameraUpdateFactory.newLatLngZoom(it, 15f))
                }
            }

            listaFotos.forEach { foto ->

                val uri = Uri.parse(foto.uri)
                val input = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(input)

                bitmap?.let {
                    val small = android.graphics.Bitmap.createScaledBitmap(it, 100, 100, false)

                    Marker(
                        state = MarkerState(foto.latLng),
                        title = foto.nombre,
                        icon = BitmapDescriptorFactory.fromBitmap(small)
                    )
                }
            }
        }

        // Botones
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(), // 👈 ESTA ES LA CLAVE
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = { onRecorridoChange(true) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Iniciar recorrido")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = {
                    listaFotos.clear()
                    onRecorridoChange(false)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Borrar recorrido")
            }
        }
    }
}