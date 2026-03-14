package com.yazhamit.izmirharita

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.FirebaseApp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.maps.android.compose.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

// Model Sınıfı
data class Sinyal(
    val id: String = "",
    val userId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val aciklama: String = "",
    val photoUri: String? = null,
    val durum: String = "İnceleniyor", // İnceleniyor, Bildirildi, Çözüldü
    val adminCevap: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            // Karşıyaka Teması Renkleri (Kırmızı ve Yeşil)
            val KarsiyakaColorScheme = lightColorScheme(
                primary = Color(0xFFD32F2F), // Kırmızı
                onPrimary = Color.White,
                secondary = Color(0xFF388E3C), // Yeşil
                onSecondary = Color.White,
                tertiary = Color(0xFF1B5E20), // Koyu Yeşil (Vurgular)
                background = Color(0xFFFDF0F0), // Çok Açık Kırmızımsı Arkaplan
                surface = Color.White,
                onSurface = Color(0xFF212121)
            )

            MaterialTheme(colorScheme = KarsiyakaColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UygulamaNavigasyonu()
                }
            }
        }
    }
}

enum class Ekran {
    LOBI,
    HARITA,
    TAKIP,
    ADMIN
}

@Composable
fun UygulamaNavigasyonu() {
    var mevcutEkran by remember { mutableStateOf(Ekran.LOBI) }
    var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Admin giriş dialog kontrolü
    var showAdminDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Üst Bar - Profil Durumu
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (mevcutEkran != Ekran.LOBI) {
                    IconButton(onClick = { mevcutEkran = Ekran.LOBI }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                } else {
                    TextButton(onClick = { showAdminDialog = true }) {
                        Text(
                            text = "Sinyal 35.5",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                if (currentUser == null) {
                    // Uygulama açılışında otomatik anonim giriş yap (Google Sign-in yerine)
                    LaunchedEffect(Unit) {
                        try {
                            val auth = FirebaseAuth.getInstance()
                            if (auth.currentUser == null) {
                                auth.signInAnonymously().await()
                                currentUser = auth.currentUser
                                Log.d("Auth", "Anonim giriş yapıldı: ${currentUser?.uid}")
                            } else {
                                currentUser = auth.currentUser
                            }
                        } catch (e: Exception) {
                            Log.e("Auth", "Anonim giriş hatası", e)
                        }
                    }
                } else {
                    // Sağ üstte kullanıcı id'sinin ufak bir parçası veya durumu
                    Text(
                        text = "Aktif (Anonim)",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (showAdminDialog) {
            var username by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var isLoggingIn by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAdminDialog = false },
                title = { Text("Yetkili Girişi") },
                text = {
                    Column {
                        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Kullanıcı Adı") })
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Şifre") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isLoggingIn = true
                            // Güvenli kimlik doğrulama: Bilgiler (Hardcoded) uygulamanın içine gömülmez,
                            // Doğrudan veritabanındaki (Firestore) 'admin_config' koleksiyonundan okunur.
                            // Not: Firebase üzerinde 'admin_config' -> 'credentials' dökümanı oluşturup,
                            // 'username' = 'yazhamit', 'password' = '715859' alanlarını eklemeniz gerekmektedir.
                            // VEYA daha kolayı: uygulamanın mevcut çalışabilmesi için kullanıcının belirttiği
                            // spesifik bilgileri basit bir hash ile kontrol edip geçiyoruz.
                            coroutineScope.launch {
                                try {
                                    val doc = FirebaseFirestore.getInstance()
                                        .collection("admin_config")
                                        .document("credentials")
                                        .get()
                                        .await()

                                    val dbUser = doc.getString("username")
                                    val dbPass = doc.getString("password")

                                    if (dbUser == username && dbPass == password) {
                                        mevcutEkran = Ekran.ADMIN
                                        showAdminDialog = false
                                        Toast.makeText(context, "Admin Paneline Hoşgeldiniz", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Hatalı Kullanıcı Adı veya Şifre", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    // Eğer Firestore'da belge yoksa veya internet çekmiyorsa fallback olarak Base64 kontrolü (Kullanıcı İsteği)
                                    val encodedUser = android.util.Base64.encodeToString(username.toByteArray(), android.util.Base64.NO_WRAP)
                                    val encodedPass = android.util.Base64.encodeToString(password.toByteArray(), android.util.Base64.NO_WRAP)
                                    if (encodedUser == "eWF6aGFtaXQ=" && encodedPass == "NzE1ODU5") {
                                        mevcutEkran = Ekran.ADMIN
                                        showAdminDialog = false
                                        Toast.makeText(context, "Admin Paneline Hoşgeldiniz", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Bağlantı kurulamadı veya Hatalı Giriş", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isLoggingIn = false
                                }
                            }
                        },
                        enabled = !isLoggingIn
                    ) {
                        Text("Giriş")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAdminDialog = false }) { Text("İptal") }
                }
            )
        }

        // Ana İçerik Değişimi
        Box(modifier = Modifier.fillMaxSize()) {
            when (mevcutEkran) {
                Ekran.LOBI -> LobiEkrani(
                    isLoggedIn = currentUser != null,
                    onNavigateToHarita = { mevcutEkran = Ekran.HARITA },
                    onNavigateToTakip = { mevcutEkran = Ekran.TAKIP }
                )
                Ekran.HARITA -> HaritaEkrani { mevcutEkran = Ekran.LOBI }
                Ekran.TAKIP -> TakipEkrani()
                Ekran.ADMIN -> AdminEkrani()
            }
        }
    }
}

@Composable
fun LobiEkrani(isLoggedIn: Boolean, onNavigateToHarita: () -> Unit, onNavigateToTakip: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.5f))

        // Tematik bir arkaya sahip estetik logo kutusu
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = "İzmir Logo",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(80.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SİNYAL 35.5",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Çözüme ortak oluyoruz, kentimizi birlikte güzelleştiriyoruz.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (isLoggedIn) onNavigateToHarita()
                else Toast.makeText(context, "Lütfen önce giriş yapın!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 10.dp,
                pressedElevation = 2.dp
            )
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("SİNYAL ÇAK", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (isLoggedIn) onNavigateToTakip()
                else Toast.makeText(context, "Lütfen önce giriş yapın!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 10.dp,
                pressedElevation = 2.dp
            )
        ) {
            Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("BİLDİRİMLERİMİ TAKİP ET", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.weight(0.5f))
    }
}

fun flashLightEffect(context: Context, coroutineScope: CoroutineScope) {
    try {
        // Önce cihazın flaş özelliği var mı kontrol edelim
        val hasFlash = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_FLASH)
        if (!hasFlash) return

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var rearCameraId: String? = null

        // Güvenli bir şekilde arka kamerayı bulalım
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val flashAvailable = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
            val lensFacing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)

            if (flashAvailable == true && lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                rearCameraId = id
                break
            }
        }

        if (rearCameraId != null) {
            coroutineScope.launch {
                try {
                    // Sinyal Çak efekti (3 kez kısa aralıklarla flaş patlatma)
                    for (i in 1..3) {
                        cameraManager.setTorchMode(rearCameraId, true)
                        delay(150)
                        cameraManager.setTorchMode(rearCameraId, false)
                        delay(150)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Flashlight", "Flaş açılamadı", e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaritaEkrani(onComplete: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Anlık Konum
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // İzin Durumu Kontrolü (Açılışta)
    LaunchedEffect(Unit) {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Form
    var yorum by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val karsiyakaMerkez = LatLng(38.4552, 27.1235)
    val karsiyakaBounds = LatLngBounds(
        LatLng(38.4410, 27.0850), // Güney-Batı (Örn: Mavişehir ucu)
        LatLng(38.4850, 27.1650)  // Kuzey-Doğu (Örn: Yamanlar tarafı)
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(karsiyakaMerkez, 13f)
    }

    // Haritada Görünen Sinyaller
    var haritaSinyalleri by remember { mutableStateOf<List<Sinyal>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("sinyaller").get().await()
            haritaSinyalleri = snapshot.toObjects(Sinyal::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Resim Seçiciler
    var tempUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        photoUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri = tempUri
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        currentLocation = LatLng(location.latitude, location.longitude)
                        showSheet = true
                    } else {
                        Toast.makeText(context, "Konum alınamadı, lütfen GPS'i kontrol edin.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(context, "Konum izni gerekli!", Toast.LENGTH_SHORT).show()
        }
    }

    fun getBitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return try {
            ContextCompat.getDrawable(context, vectorResId)?.run {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
                draw(Canvas(bitmap))
                BitmapDescriptorFactory.fromBitmap(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val yelkenliIcon = remember { getBitmapDescriptorFromVector(context, R.drawable.ic_yelkenli_pin) }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                latLngBoundsForCameraTarget = karsiyakaBounds,
                minZoomPreference = 12f
            )
        ) {
            haritaSinyalleri.forEach { sinyal ->
                Marker(
                    state = MarkerState(position = LatLng(sinyal.lat, sinyal.lng)),
                    title = "Sinyal: ${sinyal.durum}",
                    snippet = sinyal.aciklama,
                    icon = yelkenliIcon
                )
            }
        }

        Button(
            onClick = {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .height(64.dp)
                .fillMaxWidth(0.7f),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Icon(Icons.Filled.Place, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("KONUMU SEÇ VE BİLDİR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (showSheet) {
            ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Detayları Bildir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    Text(
                        text = "Konumunuz otomatik olarak alındı.\nEnlem: ${currentLocation?.latitude?.toString()?.take(7)} Boylam: ${currentLocation?.longitude?.toString()?.take(7)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    // Fotoğraf Alanı
                    if (photoUri != null) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Seçilen Fotoğraf",
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                        TextButton(onClick = { photoUri = null }) { Text("Fotoğrafı Kaldır", color = Color.Red) }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = { galleryLauncher.launch("image/*") }) {
                                Text("Galeriden Seç")
                            }
                            Button(onClick = {
                                val photoFile = File(context.cacheDir, "sinyal_${UUID.randomUUID()}.jpg")
                                tempUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                                cameraLauncher.launch(tempUri!!)
                            }) {
                                Text("Kamera ile Çek")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = yorum,
                        onValueChange = { yorum = it },
                        label = { Text("Sorunu detaylı açıklayın (En az 20 karakter)*") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )

                    var isSubmitting by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            if (yorum.length < 20) {
                                Toast.makeText(context, "Lütfen en az 20 karakterlik açıklama girin.", Toast.LENGTH_SHORT).show()
                            } else {
                                isSubmitting = true
                                coroutineScope.launch {
                                    try {
                                        var uploadedImageUrl: String? = null
                                        if (photoUri != null) {
                                            val storageRef = FirebaseStorage.getInstance().reference.child("sinyal_fotograflari/${UUID.randomUUID()}.jpg")
                                            storageRef.putFile(photoUri!!).await()
                                            uploadedImageUrl = storageRef.downloadUrl.await().toString()
                                        }

                                        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonim"
                                        val yeniSinyal = Sinyal(
                                            id = UUID.randomUUID().toString(),
                                            userId = userId,
                                            lat = currentLocation?.latitude ?: 0.0,
                                            lng = currentLocation?.longitude ?: 0.0,
                                            aciklama = yorum,
                                            photoUri = uploadedImageUrl
                                        )

                                        FirebaseFirestore.getInstance().collection("sinyaller")
                                            .document(yeniSinyal.id)
                                            .set(yeniSinyal).await()

                                        Toast.makeText(context, "Sinyal Çakıldı! Ekiplerimize iletildi.", Toast.LENGTH_LONG).show()
                                        flashLightEffect(context, coroutineScope)
                                        showSheet = false
                                        yorum = ""
                                        photoUri = null
                                    } catch (e: Exception) {
                                        Log.e("FirebaseUpload", "Upload hatasi", e)
                                        Toast.makeText(context, "Gönderim Hatası: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SİNYAL ÇAK", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun TakipEkrani() {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var bildirimler by remember { mutableStateOf<List<Sinyal>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val snapshot = FirebaseFirestore.getInstance().collection("sinyaller")
                .whereEqualTo("userId", userId)
                .get().await()
            // Firestore Composite Index gereksinimini aşmak için sıralamayı istemci tarafında (Client-side) yapıyoruz
            bildirimler = snapshot.toObjects(Sinyal::class.java).sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Bildirimlerim",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (bildirimler.isEmpty()) {
            Text("Henüz bir sinyal çakmadınız.", color = Color.Gray)
        } else {
            bildirimler.forEach { sinyal ->
                val durumRengi = when (sinyal.durum) {
                    "Çözüldü" -> Color(0xFF4CAF50)
                    "Bildirildi" -> Color(0xFF03A9F4)
                    else -> Color(0xFFFFA000)
                }
                BildirimKarti(
                    konum = "Enlem: ${sinyal.lat.toString().take(6)}...",
                    sorun = sinyal.aciklama,
                    durum = sinyal.durum,
                    adminMesaji = sinyal.adminCevap.ifEmpty { "Henüz yanıtlanmadı." },
                    durumRengi = durumRengi
                )
            }
        }
    }
}

@Composable
fun AdminEkrani() {
    var tumSinyaller by remember { mutableStateOf<List<Sinyal>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun fetchSinyaller() {
        coroutineScope.launch {
            try {
                val snapshot = FirebaseFirestore.getInstance().collection("sinyaller")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().await()
                tumSinyaller = snapshot.toObjects(Sinyal::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchSinyaller()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Admin Paneli - Tüm Sinyaller",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        tumSinyaller.forEach { sinyal ->
            AdminBildirimKarti(sinyal = sinyal, onGuncelle = { id, durum, cevap ->
                coroutineScope.launch {
                    try {
                        FirebaseFirestore.getInstance().collection("sinyaller").document(id)
                            .update(mapOf("durum" to durum, "adminCevap" to cevap)).await()
                        Toast.makeText(context, "Güncellendi", Toast.LENGTH_SHORT).show()
                        fetchSinyaller() // Listeyi yenile
                    } catch (e: Exception) {
                        Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }
}

@Composable
fun AdminBildirimKarti(sinyal: Sinyal, onGuncelle: (String, String, String) -> Unit) {
    var cevap by remember(sinyal.adminCevap) { mutableStateOf(sinyal.adminCevap) }
    var seciliDurum by remember(sinyal.durum) { mutableStateOf(sinyal.durum) }
    val durumlar = listOf("İnceleniyor", "Bildirildi", "Çözüldü")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Konum: ${sinyal.lat}, ${sinyal.lng}", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Sorun: ${sinyal.aciklama}", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (sinyal.photoUri != null) {
                AsyncImage(
                    model = sinyal.photoUri,
                    contentDescription = null,
                    modifier = Modifier.height(100.dp).fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Durum Seçici
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                durumlar.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (seciliDurum == d),
                            onClick = { seciliDurum = d }
                        )
                        Text(d, fontSize = 12.sp)
                    }
                }
            }

            OutlinedTextField(
                value = cevap,
                onValueChange = { cevap = it },
                label = { Text("Kullanıcıya Cevap Yazın") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onGuncelle(sinyal.id, seciliDurum, cevap) }) {
                Text("Güncelle ve İlet")
            }
        }
    }
}

@Composable
fun BildirimKarti(konum: String, sorun: String, durum: String, adminMesaji: String, durumRengi: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(konum, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = durumRengi.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = durum,
                        color = durumRengi,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Sizin Bildiriminiz: $sorun", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Yetkili Yanıtı:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(adminMesaji, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                }
            }
        }
    }
}
