package com.netswiss.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.netswiss.app.ui.components.glass.LiquidGlassCard
import com.netswiss.app.ui.components.glass.LiquidGlassButton
import com.netswiss.app.service.MockLocationService
import com.netswiss.app.ui.theme.GpsGreen
import com.netswiss.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

private data class SearchResult(val name: String, val lat: Double, val lon: Double)

@Composable
fun MockGpsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var latitude by remember { mutableStateOf("28.6139") }
    var longitude by remember { mutableStateOf("77.2090") }
    var isMocking by remember { mutableStateOf(MockLocationService.isRunning) }
    var sheetExpanded by remember { mutableStateOf(true) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var lastSearchQuery by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Map reference for programmatic control
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }

    // Update state when service changes
    LaunchedEffect(Unit) {
        while (true) {
            isMocking = MockLocationService.isRunning
            delay(1000)
        }
    }


    // Check if "Mock Location App" is selected in Developer Options
    fun isMockLocationEnabled(): Boolean {
        return try {
            val opsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            opsManager.checkOp(
                android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                android.os.Process.myUid(),
                context.packageName
            ) == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun openDeveloperOptions() {
        try {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(context, "Enable Developer Options manually", Toast.LENGTH_SHORT).show()
        }
    }

    // Service Start Logic
    fun startMockService(lat: Double, lng: Double) {
        val hasLocPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocPerm) {
            Toast.makeText(context, "Location permission missing", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isMockLocationEnabled()) {
            Toast.makeText(
                context,
                "Please select 'NetSwiss' as Mock Location App in Developer Options",
                Toast.LENGTH_LONG
            ).show()
            openDeveloperOptions()
            return
        }

        try {
            val intent = Intent(context, MockLocationService::class.java).apply {
                putExtra(MockLocationService.EXTRA_LATITUDE, lat)
                putExtra(MockLocationService.EXTRA_LONGITUDE, lng)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    com.netswiss.app.util.CrashLogger.logException(
                        context,
                        "MockGpsScreen",
                        "Service Start Blocked",
                        e
                    )
                    if (e.javaClass.name.contains("ForegroundServiceStartNotAllowedException")) {
                        Toast.makeText(context, "Please open app fully to start service.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Start Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                context.startService(intent)
            }
            isMocking = true
        } catch (e: SecurityException) {
            com.netswiss.app.util.CrashLogger.logException(context, "MockGpsScreen", "Security Exception", e)
            Toast.makeText(context, "Permission Denied: Enable 'Mock Location App'", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            com.netswiss.app.util.CrashLogger.logException(context, "MockGpsScreen", "Unknown Start Error", e)
            Toast.makeText(context, "Unexpected Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopMockService() {
        try {
            val stopIntent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_STOP
            }
            context.startService(stopIntent)
        } catch (_: Exception) {
            // Fallback below still stops service if startService fails.
        }

        context.stopService(Intent(context, MockLocationService::class.java))
        isMocking = false
    }

    // Lambda for permission callback
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Permissions
    val manifestPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        manifestPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fineLoc = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLoc = perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLoc || coarseLoc) {
            pendingStart?.invoke()
            pendingStart = null
        } else {
            Toast.makeText(context, "Location permission required for Mock GPS", Toast.LENGTH_SHORT).show()
        }
    }

    fun startFromInputs() {
        val lat = latitude.toDoubleOrNull()
        val lng = longitude.toDoubleOrNull()
        if (lat == null || lng == null) {
            Toast.makeText(context, "Invalid coordinates", Toast.LENGTH_SHORT).show()
            return
        }
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            Toast.makeText(context, "Coordinates out of range", Toast.LENGTH_SHORT).show()
            return
        }

        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            startMockService(lat, lng)
        } else {
            pendingStart = { startMockService(lat, lng) }
            permissionLauncher.launch(manifestPermissions.toTypedArray())
        }
    }

    fun updateMarker(point: GeoPoint) {
        mapViewRef?.let { map ->
            val marker = markerRef ?: Marker(map).also { created ->
                created.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                created.title = "Mock Location"
                map.overlays.add(created)
                markerRef = created
            }
            marker.position = point
            map.invalidate()
        }
    }

    fun moveMapTo(lat: Double, lon: Double, animate: Boolean = true) {
        mapViewRef?.let { map ->
            val point = GeoPoint(lat, lon)
            try {
                if (animate) {
                    map.controller.animateTo(point, 16.0, 800L)
                } else {
                    map.controller.setCenter(point)
                    map.controller.setZoom(16.0)
                }
            } catch (_: Exception) {
            }
            updateMarker(point)
        }
    }

    fun launchSearch(query: String, showToastOnEmpty: Boolean) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            showResults = false
            searchResults = emptyList()
            return
        }
        searchJob?.cancel()
        isSearching = true
        showResults = true
        searchJob = scope.launch {
            try {
                val results = geocodeSearch(context, trimmed)
                searchResults = results
                isSearching = false
                lastSearchQuery = trimmed
                if (results.isEmpty() && showToastOnEmpty) {
                    Toast.makeText(context, "No locations found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isSearching = false
                if (showToastOnEmpty) {
                    Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun submitSearch() {
        focusManager.clearFocus()
        launchSearch(searchQuery, showToastOnEmpty = true)
    }

    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.length < 2 || trimmed == lastSearchQuery) {
            if (trimmed.isEmpty()) {
                showResults = false
                searchResults = emptyList()
            }
            return@LaunchedEffect
        }
        delay(350)
        if (trimmed != searchQuery.trim()) {
            return@LaunchedEffect
        }
        launchSearch(trimmed, showToastOnEmpty = false)
    }

    fun locateMe() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(manifestPermissions.toTypedArray())
            return
        }
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                latitude = "%.6f".format(lastKnown.latitude)
                longitude = "%.6f".format(lastKnown.longitude)
                moveMapTo(lastKnown.latitude, lastKnown.longitude)
                Toast.makeText(context, "Location found", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Location error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(14.0)
                    minZoomLevel = 4.0
                    maxZoomLevel = 19.0

                    val latStart = latitude.toDoubleOrNull() ?: 28.6139
                    val lngStart = longitude.toDoubleOrNull() ?: 77.2090
                    controller.setCenter(GeoPoint(latStart, lngStart))

                    val initialMarker = Marker(this).apply {
                        position = GeoPoint(latStart, lngStart)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Mock Location"
                    }
                    overlays.add(initialMarker)
                    markerRef = initialMarker

                    overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                        override fun onSingleTapConfirmed(
                            e: android.view.MotionEvent?,
                            mapView: MapView?
                        ): Boolean {
                            if (e != null && mapView != null) {
                                val projection = mapView.projection
                                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                                latitude = "%.6f".format(geoPoint.latitude)
                                longitude = "%.6f".format(geoPoint.longitude)
                                updateMarker(geoPoint)
                            }
                            return true
                        }
                    })

                    mapViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                    else -> {}
                }
            }
            val lifecycle = lifecycleOwner.lifecycle
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                mapViewRef?.onDetach()
                mapViewRef = null
                markerRef = null
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { submitSearch() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    TextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isBlank()) {
                                showResults = false
                                searchResults = emptyList()
                            }
                        },
                        placeholder = { Text("Search for destination...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    LiquidGlassButton(
                        text = "Search",
                        onClick = { submitSearch() },
                        enabled = searchQuery.trim().isNotEmpty() && !isSearching,
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                        modifier = Modifier
                            .height(36.dp)
                            .padding(start = Spacing.xs)
                    )
                }
            }

            AnimatedVisibility(visible = showResults && searchResults.isNotEmpty()) {
                LiquidGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column {
                        searchResults.forEachIndexed { index, result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        latitude = "%.6f".format(result.lat)
                                        longitude = "%.6f".format(result.lon)
                                        showResults = false
                                        searchQuery = result.name.take(40)
                                        focusManager.clearFocus()
                                        moveMapTo(result.lat, result.lon)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = GpsGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = result.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (index < searchResults.size - 1) {
                                Divider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            }
        }

        LiquidGlassCard(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = Spacing.xs)
        ) {
            Column(
                modifier = Modifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = { mapViewRef?.controller?.zoomIn() }) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }
                Divider(modifier = Modifier.width(32.dp))
                IconButton(onClick = { mapViewRef?.controller?.zoomOut() }) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }
                Divider(modifier = Modifier.width(32.dp))
                IconButton(onClick = { locateMe() }) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
                }
            }
        }

        if (isMocking) {
            LiquidGlassCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = if (sheetExpanded) Spacing.xxxl * 4 else Spacing.xxxl * 3),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs)
            ) {
                Text(
                    text = "SPOOFED LOCATION",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                )
            }
        }

        LiquidGlassCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .animateContentSize(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clickable { sheetExpanded = !sheetExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isMocking) GpsGreen else MaterialTheme.colorScheme.outline)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GPS ENGAGED",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "SPOOFING",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isMocking,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                startFromInputs()
                            } else {
                                stopMockService()
                            }
                        }
                    )
                }

                AnimatedVisibility(visible = sheetExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = latitude,
                                onValueChange = { latitude = it },
                                label = { Text("Latitude") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            OutlinedTextField(
                                value = longitude,
                                onValueChange = { longitude = it },
                                label = { Text("Longitude") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LiquidGlassButton(
                            text = "Update Position",
                            onClick = { startFromInputs() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private suspend fun geocodeSearch(context: Context, query: String): List<SearchResult> {
    return withContext(Dispatchers.IO) {
        val localResults = if (Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(query, 6) ?: emptyList()
                addresses.mapNotNull { addr ->
                    val name = buildString {
                        if (!addr.featureName.isNullOrBlank()) append(addr.featureName)
                        if (!addr.locality.isNullOrBlank()) {
                            if (isNotEmpty()) append(", ")
                            append(addr.locality)
                        }
                        if (!addr.adminArea.isNullOrBlank()) {
                            if (isNotEmpty()) append(", ")
                            append(addr.adminArea)
                        }
                        if (!addr.countryName.isNullOrBlank()) {
                            if (isNotEmpty()) append(", ")
                            append(addr.countryName)
                        }
                        if (isEmpty()) append("%.4f, %.4f".format(addr.latitude, addr.longitude))
                    }
                    SearchResult(
                        name = name,
                        lat = addr.latitude,
                        lon = addr.longitude
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        if (localResults.isNotEmpty()) {
            return@withContext localResults
        }

        fetchNominatimResults(query)
    }
}

private fun parseNominatimAddress(displayName: String, address: JSONObject?): String {
    val parts = ArrayList<String>(4)
    val name = address?.optString("name").orEmpty()
    val city = address?.optString("city")
        .orEmpty()
        .ifBlank { address?.optString("town").orEmpty() }
        .ifBlank { address?.optString("village").orEmpty() }
    val state = address?.optString("state").orEmpty()
    val country = address?.optString("country").orEmpty()

    if (name.isNotBlank()) parts.add(name)
    if (city.isNotBlank() && city != name) parts.add(city)
    if (state.isNotBlank()) parts.add(state)
    if (country.isNotBlank()) parts.add(country)

    return if (parts.isNotEmpty()) {
        parts.joinToString(", ")
    } else {
        displayName.split(",").take(3).joinToString(", ").trim()
    }
}

private fun fetchNominatimResults(query: String): List<SearchResult> {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = URL(
        "https://nominatim.openstreetmap.org/search" +
            "?q=$encoded&format=json&addressdetails=1&limit=8&accept-language=en"
    )
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        setRequestProperty("User-Agent", "NetSwiss/1.0 (contact: support@example.com)")
    }

    return try {
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(response)
        val results = ArrayList<SearchResult>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val displayName = item.optString("display_name")
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lon = item.optString("lon").toDoubleOrNull() ?: continue
            val address = item.optJSONObject("address")
            val refinedName = parseNominatimAddress(displayName, address)
            results.add(SearchResult(refinedName, lat, lon))
        }
        results
    } catch (_: Exception) {
        emptyList()
    } finally {
        connection.disconnect()
    }
}
