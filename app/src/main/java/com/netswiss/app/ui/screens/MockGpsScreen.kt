package com.netswiss.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.netswiss.app.service.MockLocationService
import com.netswiss.app.ui.components.AppCard
import com.netswiss.app.ui.components.PrimaryButton
import com.netswiss.app.ui.components.SegmentedControl
import com.netswiss.app.ui.theme.GpsGreen
import com.netswiss.app.ui.theme.Spacing
import com.netswiss.app.ui.theme.surfaceColorAtElevation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import kotlin.math.ceil

private data class SearchResult(val name: String, val lat: Double, val lon: Double)

private enum class SimulationMode { Static, Path }
private enum class TravelMode { Walk, Bike, Drive }

@Composable
fun MockGpsScreen(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var latitude by remember { mutableStateOf("28.6139") }
    var longitude by remember { mutableStateOf("77.2090") }
    var isMocking by remember { mutableStateOf(MockLocationService.isRunning) }
    var sheetExpanded by remember { mutableStateOf(true) }
    var simulationMode by remember { mutableStateOf(SimulationMode.Static) }
    var travelMode by remember { mutableStateOf(TravelMode.Walk) }
    val routePoints = remember { mutableStateListOf<GeoPoint>() }
    var drawMode by remember { mutableStateOf(false) }
    var loopRoute by remember { mutableStateOf(true) }
    var isSimulating by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var simulationJob by remember { mutableStateOf<Job?>(null) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var lastSearchQuery by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val searchCache = remember { mutableMapOf<String, List<SearchResult>>() }

    // Map reference for programmatic control
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }
    var startMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var endMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var routeLineRef by remember { mutableStateOf<Polyline?>(null) }

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

    // Update state when service changes
    LaunchedEffect(Unit) {
        isMocking = MockLocationService.isRunning
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

    fun sendMockUpdate(lat: Double, lng: Double): Boolean {
        return try {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = if (MockLocationService.isRunning) {
                    MockLocationService.ACTION_UPDATE
                } else {
                    null
                }
                putExtra(MockLocationService.EXTRA_LATITUDE, lat)
                putExtra(MockLocationService.EXTRA_LONGITUDE, lng)
            }

            if (!MockLocationService.isRunning && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            com.netswiss.app.util.CrashLogger.logException(
                context,
                "MockGpsScreen",
                "Mock Update Failure",
                e
            )
            false
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

        if (sendMockUpdate(lat, lng)) {
            isMocking = true
        } else {
            Toast.makeText(context, "Failed to start mock location service", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopMockService() {
        simulationJob?.cancel()
        simulationJob = null
        isSimulating = false
        isPaused = false
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

    fun speedMetersPerSecond(): Double {
        return when (travelMode) {
            TravelMode.Walk -> 1.4
            TravelMode.Bike -> 4.8
            TravelMode.Drive -> 13.9
        }
    }

    fun travelProfile(): String {
        return when (travelMode) {
            TravelMode.Walk -> "walking"
            TravelMode.Bike -> "cycling"
            TravelMode.Drive -> "driving"
        }
    }

    fun routeDistanceMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.lastIndex) {
            total += points[i].distanceToAsDouble(points[i + 1])
        }
        return total
    }

    fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters >= 1000.0) {
            "%.2f km".format(distanceMeters / 1000.0)
        } else {
            "%.0f m".format(distanceMeters)
        }
    }

    fun formatEta(totalDistanceMeters: Double): String {
        if (totalDistanceMeters <= 0.0) return "--"
        val etaSeconds = (totalDistanceMeters / speedMetersPerSecond()).toInt().coerceAtLeast(1)
        val minutes = etaSeconds / 60
        val seconds = etaSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    fun updateMarker(point: GeoPoint) {
        mapViewRef?.let { map ->
            val marker = markerRef ?: Marker(map).also { created ->
                created.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                created.title = "Mock Location"
                map.overlays.add(created)
                markerRef = created
            }
            val modeEmoji = when (travelMode) {
                TravelMode.Walk -> "\uD83D\uDEB6"
                TravelMode.Bike -> "\uD83D\uDEB4"
                TravelMode.Drive -> "\uD83D\uDE97"
            }
            marker.icon = createEmojiMarkerDrawable(context, modeEmoji)
            marker.position = point
            map.invalidate()
        }
    }

    fun moveMapTo(lat: Double, lon: Double, animate: Boolean = true) {
        mapViewRef?.let { map ->
            val point = GeoPoint(lat, lon)
            try {
                val currentZoom = map.zoomLevelDouble
                if (animate) {
                    map.controller.animateTo(point, currentZoom, 800L)
                } else {
                    map.controller.setCenter(point)
                }
            } catch (_: Exception) {
            }
            updateMarker(point)
        }
    }

    fun refreshRouteOverlay() {
        routeLineRef?.setPoints(routePoints.toList())
        val map = mapViewRef ?: return
        if (routePoints.isNotEmpty()) {
            val start = startMarkerRef ?: Marker(map).also { created ->
                created.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                created.title = "Start"
                created.icon = createEmojiMarkerDrawable(context, "\uD83D\uDEA9")
                map.overlays.add(created)
                startMarkerRef = created
            }
            start.position = routePoints.first()
        } else {
            startMarkerRef?.let { map.overlays.remove(it) }
            startMarkerRef = null
        }

        if (routePoints.size > 1) {
            val end = endMarkerRef ?: Marker(map).also { created ->
                created.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                created.title = "End"
                created.icon = createEmojiMarkerDrawable(context, "\uD83C\uDFC1")
                map.overlays.add(created)
                endMarkerRef = created
            }
            end.position = routePoints.last()
        } else {
            endMarkerRef?.let { map.overlays.remove(it) }
            endMarkerRef = null
        }
        mapViewRef?.invalidate()
    }

    fun currentMapCenterPoint(): GeoPoint? {
        val center = mapViewRef?.mapCenter ?: return null
        return GeoPoint(center.latitude, center.longitude)
    }

    fun setStartPointFromCenter() {
        val center = currentMapCenterPoint()
        if (center == null) {
            Toast.makeText(context, "Map not ready", Toast.LENGTH_SHORT).show()
            return
        }
        if (routePoints.isEmpty()) {
            routePoints.add(center)
        } else {
            routePoints[0] = center
        }
        refreshRouteOverlay()
    }

    fun setEndPointFromCenter() {
        val center = currentMapCenterPoint()
        if (center == null) {
            Toast.makeText(context, "Map not ready", Toast.LENGTH_SHORT).show()
            return
        }
        when {
            routePoints.isEmpty() -> {
                routePoints.add(center)
                routePoints.add(center)
            }
            routePoints.size == 1 -> routePoints.add(center)
            else -> routePoints[routePoints.lastIndex] = center
        }
        refreshRouteOverlay()
    }

    fun startPathSimulation() {
        if (routePoints.size < 2) {
            Toast.makeText(context, "Add at least 2 route points", Toast.LENGTH_SHORT).show()
            return
        }

        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            pendingStart = { startPathSimulation() }
            permissionLauncher.launch(manifestPermissions.toTypedArray())
            return
        }

        if (!isMockLocationEnabled()) {
            Toast.makeText(
                context,
                "Select NetSwiss as Mock Location App in Developer Options",
                Toast.LENGTH_LONG
            ).show()
            openDeveloperOptions()
            return
        }

        simulationJob?.cancel()
        isSimulating = true
        isPaused = false

        simulationJob = scope.launch {
            val requestedPoints = routePoints.toList()
            val routedPoints = withContext(Dispatchers.IO) {
                fetchOsrmRoadRoute(
                    points = requestedPoints,
                    profile = travelProfile()
                )
            }
            val simulationPath = if (routedPoints.size >= 2) {
                routedPoints
            } else {
                if (requestedPoints.size >= 2) {
                    Toast.makeText(
                        context,
                        "Road route unavailable, using straight path",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                requestedPoints
            }

            if (simulationPath.size >= 2) {
                routePoints.clear()
                routePoints.addAll(simulationPath)
                refreshRouteOverlay()
            }

            val speedMps = speedMetersPerSecond()
            val targetTickMs = 700L
            var keepRunning = true
            while (keepRunning && isSimulating) {
                val pointsSnapshot = simulationPath
                if (pointsSnapshot.size < 2) break
                for (i in 0 until pointsSnapshot.lastIndex) {
                    val from = pointsSnapshot[i]
                    val to = pointsSnapshot[i + 1]
                    val segmentDistance = from.distanceToAsDouble(to).coerceAtLeast(1.0)
                    val segmentDurationMs = ((segmentDistance / speedMps) * 1000.0).toLong().coerceAtLeast(targetTickMs)
                    val stepsPerSegment = ceil(segmentDurationMs.toDouble() / targetTickMs.toDouble()).toInt().coerceIn(1, 240)
                    val delayMs = (segmentDurationMs / stepsPerSegment).coerceAtLeast(120L)
                    for (step in 0..stepsPerSegment) {
                        if (!isSimulating) break
                        while (isPaused && isSimulating) {
                            delay(200)
                        }
                        if (!isSimulating) break
                        val fraction = step.toDouble() / stepsPerSegment.toDouble()
                        val lat = from.latitude + ((to.latitude - from.latitude) * fraction)
                        val lon = from.longitude + ((to.longitude - from.longitude) * fraction)
                        latitude = "%.6f".format(lat)
                        longitude = "%.6f".format(lon)
                        moveMapTo(lat, lon, animate = false)
                        sendMockUpdate(lat, lon)
                        isMocking = true
                        delay(delayMs)
                    }
                }
                if (!loopRoute) {
                    keepRunning = false
                }
            }
            isSimulating = false
            isPaused = false
        }
    }

    fun launchSearch(query: String, showToastOnEmpty: Boolean) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            showResults = false
            searchResults = emptyList()
            return
        }
        val cacheKey = trimmed.lowercase(Locale.getDefault())
        val cached = searchCache[cacheKey]
        if (cached != null) {
            searchResults = cached
            showResults = cached.isNotEmpty()
            lastSearchQuery = trimmed
            return
        }
        searchJob?.cancel()
        isSearching = true
        showResults = true
        searchJob = scope.launch {
            try {
                val results = geocodeSearch(context, trimmed)
                    .distinctBy { "${it.lat},${it.lon}" }
                    .take(8)
                searchResults = results
                searchCache[cacheKey] = results
                lastSearchQuery = trimmed
                if (results.isEmpty() && showToastOnEmpty) {
                    Toast.makeText(context, "No locations found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (showToastOnEmpty) {
                    Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isSearching = false
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
                    setUseDataConnection(true)
                    setTilesScaledToDpi(true)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
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

                    val routeLine = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.argb(220, 54, 114, 255)
                        outlinePaint.strokeWidth = 8f
                        setPoints(routePoints.toList())
                    }
                    overlays.add(routeLine)
                    routeLineRef = routeLine

                    overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                        override fun onSingleTapConfirmed(
                            e: android.view.MotionEvent?,
                            mapView: MapView?
                        ): Boolean {
                            if (e != null && mapView != null) {
                                val projection = mapView.projection
                                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                                if (simulationMode == SimulationMode.Path && drawMode) {
                                    routePoints.add(geoPoint)
                                    refreshRouteOverlay()
                                } else {
                                    latitude = "%.6f".format(geoPoint.latitude)
                                    longitude = "%.6f".format(geoPoint.longitude)
                                    updateMarker(geoPoint)
                                }
                            }
                            return true
                        }

                        override fun onDoubleTap(
                            e: android.view.MotionEvent?,
                            mapView: MapView?
                        ): Boolean {
                            if (e == null || mapView == null) return false
                            if (simulationMode != SimulationMode.Path || !drawMode || routePoints.size < 2) return false
                            val projection = mapView.projection
                            val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                            val insertIndex = nearestSegmentInsertIndex(routePoints, geoPoint)
                            routePoints.add(insertIndex + 1, geoPoint)
                            refreshRouteOverlay()
                            return true
                        }

                        override fun onLongPress(
                            e: android.view.MotionEvent?,
                            mapView: MapView?
                        ): Boolean {
                            if (e == null || mapView == null) return false
                            if (simulationMode != SimulationMode.Path || !drawMode || routePoints.isEmpty()) return false
                            val projection = mapView.projection
                            val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                            val pointIndex = nearestPointIndex(routePoints, geoPoint)
                            if (pointIndex >= 0) {
                                routePoints.removeAt(pointIndex)
                                refreshRouteOverlay()
                                return true
                            }
                            return false
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
                    Lifecycle.Event.ON_RESUME -> {
                        mapViewRef?.onResume()
                        isMocking = MockLocationService.isRunning
                    }
                    Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                    else -> {}
                }
            }
            val lifecycle = lifecycleOwner.lifecycle
            lifecycle.addObserver(observer)
            onDispose {
                simulationJob?.cancel()
                lifecycle.removeObserver(observer)
                mapViewRef?.onDetach()
                mapViewRef = null
                markerRef = null
                startMarkerRef = null
                endMarkerRef = null
                routeLineRef = null
            }
        }

        val topInset = paddingValues.calculateTopPadding()
        val bottomInset = paddingValues.calculateBottomPadding()

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = Spacing.lg + topInset, start = Spacing.lg, end = Spacing.lg)
        ) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
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
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                    FilledTonalButton(
                        onClick = { submitSearch() },
                        enabled = searchQuery.trim().isNotEmpty() && !isSearching,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = Spacing.sm),
                        modifier = Modifier
                            .height(36.dp)
                            .padding(start = Spacing.xs)
                    ) {
                        Text(text = "Search", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (isSearching) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Searching...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = showResults && searchResults.isNotEmpty()) {
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
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

        val controlColors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Spacing.md, top = topInset),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            FilledIconButton(onClick = { mapViewRef?.controller?.zoomIn() }, colors = controlColors) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            FilledIconButton(onClick = { mapViewRef?.controller?.zoomOut() }, colors = controlColors) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
            FilledIconButton(onClick = { locateMe() }, colors = controlColors) {
                Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
            }
        }

        if (isMocking || isSimulating) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = if (sheetExpanded) Spacing.xxxl * 3 else Spacing.xxxl * 2),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "SPOOFED LOCATION",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                )
            }
        }

        if (simulationMode == SimulationMode.Path) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Map center",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(22.dp)
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md + bottomInset),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clickable { sheetExpanded = !sheetExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
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
                                if (simulationMode == SimulationMode.Path) {
                                    startPathSimulation()
                                } else {
                                    startFromInputs()
                                }
                            } else {
                                stopMockService()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
                SegmentedControl(
                    options = listOf("Static", "Path"),
                    selectedIndex = if (simulationMode == SimulationMode.Static) 0 else 1,
                    onSelected = { index ->
                        if (index == 0) {
                            simulationMode = SimulationMode.Static
                            drawMode = false
                            isPaused = false
                        } else {
                            simulationMode = SimulationMode.Path
                            drawMode = true
                        }
                    }
                )

                AnimatedVisibility(visible = sheetExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        if (simulationMode == SimulationMode.Static) {
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
                                    shape = MaterialTheme.shapes.medium
                                )
                                OutlinedTextField(
                                    value = longitude,
                                    onValueChange = { longitude = it },
                                    label = { Text("Longitude") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                )
                            }

                            Spacer(modifier = Modifier.height(Spacing.md))

                            PrimaryButton(
                                text = "Update Position",
                                onClick = { startFromInputs() }
                            )
                        } else {
                            Text(
                                text = "Path points: ${routePoints.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                FilledTonalButton(
                                    onClick = { setStartPointFromCenter() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Set Start")
                                }
                                FilledTonalButton(
                                    onClick = { setEndPointFromCenter() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Set End")
                                }
                            }
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            if (routePoints.isNotEmpty()) {
                                Text(
                                    text = "Start: %.5f, %.5f".format(routePoints.first().latitude, routePoints.first().longitude),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (routePoints.size > 1) {
                                Text(
                                    text = "End: %.5f, %.5f".format(routePoints.last().latitude, routePoints.last().longitude),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                FilledTonalButton(
                                    onClick = { drawMode = !drawMode },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(text = if (drawMode) "Drawing On" else "Drawing Off")
                                }
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        if (routePoints.isNotEmpty()) {
                                            routePoints.removeAt(routePoints.lastIndex)
                                            refreshRouteOverlay()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Undo")
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.xs))
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    routePoints.clear()
                                    refreshRouteOverlay()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(text = "Clear Path")
                            }

                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(
                                text = "Travel mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                FilledTonalButton(
                                    onClick = { travelMode = TravelMode.Walk },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Default.DirectionsWalk, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Walk")
                                }
                                FilledTonalButton(
                                    onClick = { travelMode = TravelMode.Bike },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Default.DirectionsBike, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Bike")
                                }
                                FilledTonalButton(
                                    onClick = { travelMode = TravelMode.Drive },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Drive")
                                }
                            }
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            val totalDistance = routeDistanceMeters(routePoints)
                            Text(
                                text = "Distance: ${formatDistance(totalDistance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "ETA (${travelMode.name.lowercase()}): ${formatEta(totalDistance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Loop path",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(checked = loopRoute, onCheckedChange = { loopRoute = it })
                            }

                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        if (!isSimulating) {
                                            startPathSimulation()
                                        } else {
                                            isPaused = !isPaused
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = when {
                                            !isSimulating -> "Start"
                                            isPaused -> "Resume"
                                            else -> "Pause"
                                        }
                                    )
                                }
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { stopMockService() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(text = "Stop")
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Move map, use Set Start/Set End. You can still tap map to add points; double-tap inserts, long-press removes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun nearestPointIndex(points: List<GeoPoint>, target: GeoPoint): Int {
    if (points.isEmpty()) return -1
    var bestIndex = -1
    var bestDistance = Double.MAX_VALUE
    points.forEachIndexed { index, point ->
        val distance = squaredDistance(point, target)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}

private fun nearestSegmentInsertIndex(points: List<GeoPoint>, target: GeoPoint): Int {
    if (points.size < 2) return 0
    var bestIndex = 0
    var bestScore = Double.MAX_VALUE
    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        val score = squaredDistance(a, target) + squaredDistance(b, target)
        if (score < bestScore) {
            bestScore = score
            bestIndex = i
        }
    }
    return bestIndex
}

private fun squaredDistance(a: GeoPoint, b: GeoPoint): Double {
    val latDiff = a.latitude - b.latitude
    val lonDiff = a.longitude - b.longitude
    return latDiff * latDiff + lonDiff * lonDiff
}

private fun createEmojiMarkerDrawable(context: Context, emoji: String): BitmapDrawable {
    val width = 72
    val height = 92
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(240, 38, 114, 255)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, 32f, 28f, circlePaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText(emoji, width / 2f, 43f, textPaint)

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(240, 38, 114, 255)
        style = Paint.Style.FILL
    }
    val path = android.graphics.Path().apply {
        moveTo(width / 2f, 90f)
        lineTo((width / 2f) - 12f, 58f)
        lineTo((width / 2f) + 12f, 58f)
        close()
    }
    canvas.drawPath(path, pinPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private suspend fun geocodeSearch(context: Context, query: String): List<SearchResult> {
    return withContext(Dispatchers.IO) {
        val localResults = if (Geocoder.isPresent()) {
            runCatching {
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
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }

        val remoteResults = if (localResults.size < 6) {
            runCatching { fetchNominatimResults(query) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }

        (localResults + remoteResults)
            .distinctBy { "${it.lat},${it.lon}" }
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

    return try {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "NetSwiss/1.0 (support@netswiss.app)")
        }
        connection.inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
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
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun fetchOsrmRoadRoute(points: List<GeoPoint>, profile: String): List<GeoPoint> {
    if (points.size < 2) return emptyList()

    val routed = ArrayList<GeoPoint>()
    for (i in 0 until points.lastIndex) {
        val segment = fetchOsrmRoadSegment(
            from = points[i],
            to = points[i + 1],
            profile = profile
        )
        if (segment.size < 2) {
            return emptyList()
        }
        if (routed.isEmpty()) {
            routed.addAll(segment)
        } else {
            // Drop first point to avoid duplicating connection vertices.
            routed.addAll(segment.drop(1))
        }
    }
    return routed
}

private fun fetchOsrmRoadSegment(from: GeoPoint, to: GeoPoint, profile: String): List<GeoPoint> {
    val coordinates = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
    val url = URL(
        "https://router.project-osrm.org/route/v1/$profile/$coordinates" +
            "?overview=full&geometries=geojson&steps=false"
    )

    return try {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "NetSwiss/1.0")
        }
        connection.inputStream.bufferedReader().use { reader ->
            val body = reader.readText()
            val root = JSONObject(body)
            if (root.optString("code") != "Ok") return emptyList()
            val routes = root.optJSONArray("routes") ?: return emptyList()
            if (routes.length() == 0) return emptyList()
            val geometry = routes.getJSONObject(0).optJSONObject("geometry") ?: return emptyList()
            val coords = geometry.optJSONArray("coordinates") ?: return emptyList()

            val out = ArrayList<GeoPoint>(coords.length())
            for (i in 0 until coords.length()) {
                val pair = coords.optJSONArray(i) ?: continue
                val lon = pair.optDouble(0, Double.NaN)
                val lat = pair.optDouble(1, Double.NaN)
                if (!lat.isNaN() && !lon.isNaN()) {
                    out.add(GeoPoint(lat, lon))
                }
            }
            out
        }
    } catch (_: Exception) {
        emptyList()
    }
}
