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
import android.location.Location
import android.location.LocationManager
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.netswiss.app.service.MockLocationService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.netswiss.app.ui.components.PrimaryButton
import com.netswiss.app.ui.components.SegmentedControl
import com.netswiss.app.ui.components.MockLocationSetupDialog
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
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

import androidx.lifecycle.viewmodel.compose.viewModel
import com.netswiss.app.ui.viewmodel.MockGpsViewModel
import com.netswiss.app.ui.viewmodel.SimulationMode
import com.netswiss.app.ui.viewmodel.TravelMode

@Composable
fun MockGpsScreen(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: MockGpsViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Observe ViewModel State
    val latitude by viewModel.latitude.collectAsState()
    val longitude by viewModel.longitude.collectAsState()
    val isMocking by viewModel.isMocking.collectAsState()
    val sheetExpanded by viewModel.sheetExpanded.collectAsState()
    val simulationMode by viewModel.simulationMode.collectAsState()
    val travelMode by viewModel.travelMode.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val drawMode by viewModel.drawMode.collectAsState()
    val loopRoute by viewModel.loopRoute.collectAsState()
    val isSimulating by viewModel.isSimulating.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val isLocating by viewModel.isLocating.collectAsState()
    val hasInitialCentered by viewModel.hasInitialCentered.collectAsState()

    // Search state
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val showResults by viewModel.showResults.collectAsState()
    val showSetupDialog by viewModel.showSetupDialog.collectAsState()

    val sharedPrefs = context.getSharedPreferences("mock_gps_prefs", Context.MODE_PRIVATE)

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
        viewModel.syncMockingState()
    }

    LaunchedEffect(Unit) {
        val hasShownTutorial = sharedPrefs.getBoolean("has_shown_tutorial", false)
        if (!hasShownTutorial && !viewModel.isMockLocationEnabled(context)) {
            viewModel.setShowSetupDialog(true)
            sharedPrefs.edit().putBoolean("has_shown_tutorial", true).apply()
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
            val modeEmoji = when (viewModel.travelMode.value) {
                TravelMode.Walk -> "🚶"
                TravelMode.Bike -> "🚴"
                TravelMode.Drive -> "🚗"
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
            if (!viewModel.isMockLocationEnabled(context)) {
                viewModel.setShowSetupDialog(true)
                return
            }
            if (viewModel.sendMockUpdate(context, lat, lng)) {
                viewModel.setIsMocking(true)
            } else {
                Toast.makeText(context, "Failed to start mock location service", Toast.LENGTH_SHORT).show()
            }
        } else {
            pendingStart = { startFromInputs() }
            permissionLauncher.launch(manifestPermissions.toTypedArray())
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
        viewModel.updateRoutePoints { points ->
            if (points.isEmpty()) {
                points.add(center)
            } else {
                points[0] = center
            }
        }
        refreshRouteOverlay()
    }

    fun setEndPointFromCenter() {
        val center = currentMapCenterPoint()
        if (center == null) {
            Toast.makeText(context, "Map not ready", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.updateRoutePoints { points ->
            when {
                points.isEmpty() -> {
                    points.add(center)
                    points.add(center)
                }
                points.size == 1 -> points.add(center)
                else -> points[points.lastIndex] = center
            }
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

        viewModel.startPathSimulation(context) { lat, lon ->
            moveMapTo(lat, lon, animate = false)
        }
    }

    fun submitSearch() {
        focusManager.clearFocus()
        viewModel.submitSearch(context)
    }



    fun locateMe(showToast: Boolean = true) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(manifestPermissions.toTypedArray())
            return
        }
        viewModel.locateMe(context, showToast) { lat, lon ->
            moveMapTo(lat, lon)
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
                                    viewModel.updateRoutePoints { it.add(geoPoint) }
                                    refreshRouteOverlay()
                                } else {
                                    viewModel.setLatitude("%.6f".format(geoPoint.latitude))
                                    viewModel.setLongitude("%.6f".format(geoPoint.longitude))
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
                            viewModel.updateRoutePoints { it.add(insertIndex + 1, geoPoint) }
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
                                viewModel.updateRoutePoints { it.removeAt(pointIndex) }
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

        LaunchedEffect(mapViewRef) {
            if (mapViewRef != null && !hasInitialCentered) {
                viewModel.setHasInitialCentered(true)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    locateMe(showToast = false)
                }
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        mapViewRef?.onResume()
                        viewModel.setIsMocking(MockLocationService.isRunning)
                    }
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
                startMarkerRef = null
                endMarkerRef = null
                routeLineRef = null
            }
        }

        val topInset = paddingValues.calculateTopPadding()
        val bottomInset = paddingValues.calculateBottomPadding()
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        val expandedSheetMaxHeight = (screenHeightDp * 0.42f).dp
        val darkGlass = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val glassContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (darkGlass) 0.60f else 0.82f)
        val glassElevatedColor = MaterialTheme.colorScheme.surface.copy(alpha = if (darkGlass) 0.68f else 0.88f)
        val glassBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkGlass) 0.24f else 0.12f)
        val glassGradientTop = MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkGlass) 0.10f else 0.06f)
        val glassGradientBottom = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.01f)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = Spacing.lg + topInset, start = Spacing.lg, end = Spacing.lg)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = glassContainerColor,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, glassBorderColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(glassGradientTop, glassGradientBottom)
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.sm, vertical = 4.dp),
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
                            onValueChange = { newValue: String ->
                                viewModel.setSearchQuery(newValue)
                                if (newValue.isBlank()) {
                                    viewModel.setShowResults(false)
                                    viewModel.setSearchResults(emptyList())
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
                            modifier = Modifier.weight(1f)
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    shape = RoundedCornerShape(18.dp),
                    color = glassContainerColor,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, glassBorderColor)
                ) {
                    Column {
                        searchResults.forEachIndexed { index, result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.handleSearchResultClick(result) { resultLat, resultLon ->
                                            moveMapTo(resultLat, resultLon)
                                            focusManager.clearFocus()
                                        }
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
                                androidx.compose.material3.HorizontalDivider(
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (darkGlass) 0.72f else 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
        val controlYOffset = if (sheetExpanded) (-96).dp else (-56).dp
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(0, controlYOffset.roundToPx()) }
                .padding(end = Spacing.md)
                .shadow(8.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = glassElevatedColor,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, glassBorderColor)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FilledIconButton(
                    onClick = { mapViewRef?.controller?.zoomIn() },
                    colors = controlColors
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }
                FilledIconButton(
                    onClick = { mapViewRef?.controller?.zoomOut() },
                    colors = controlColors
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }
                FilledIconButton(
                    onClick = { locateMe() },
                    colors = controlColors,
                    enabled = !isLocating
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
                }
            }
        }

        if (isMocking || isSimulating) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = if (sheetExpanded) Spacing.xxxl * 3 else Spacing.xxxl * 2),
                shape = RoundedCornerShape(12.dp),
                color = glassContainerColor,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, glassBorderColor)
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
            color = glassContainerColor,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, glassBorderColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(glassGradientTop, glassGradientBottom)
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
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
                        .clickable { viewModel.setSheetExpanded(!sheetExpanded) },
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
                            if (MockLocationService.isRunning) {
                                if (simulationMode == SimulationMode.Path) {
                                    startPathSimulation()
                                } else {
                                    startFromInputs()
                                }
                            } else {
                                viewModel.stopMockService(context)
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
                            viewModel.setSimulationMode(SimulationMode.Static)
                            viewModel.setDrawMode(false)
                            viewModel.setIsPaused(false)
                            viewModel.setSheetExpanded(true)
                        } else {
                            viewModel.setSimulationMode(SimulationMode.Path)
                            viewModel.setDrawMode(true)
                            viewModel.setSheetExpanded(false)
                        }
                    }
                )

                AnimatedVisibility(visible = sheetExpanded) {
                    Column {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = expandedSheetMaxHeight)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        ) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        if (simulationMode == SimulationMode.Static) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = latitude,
                                    onValueChange = { newValue: String -> viewModel.setLatitude(newValue) },
                                    label = { Text("Latitude") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                )
                                OutlinedTextField(
                                    value = longitude,
                                    onValueChange = { newValue: String -> viewModel.setLongitude(newValue) },
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
                                    onClick = { viewModel.setDrawMode(!drawMode) },
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
                                            viewModel.updateRoutePoints { list -> list.removeAt(list.lastIndex) }
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
                                    viewModel.clearRoutePoints()
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
                                    onClick = { viewModel.setTravelMode(TravelMode.Walk) },
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
                                    onClick = { viewModel.setTravelMode(TravelMode.Bike) },
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
                                    onClick = { viewModel.setTravelMode(TravelMode.Drive) },
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
                            val totalDistance = viewModel.routeDistanceMeters(routePoints)
                            Text(
                                text = "Distance: ${viewModel.formatDistance(totalDistance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "ETA (${travelMode.name.lowercase()}): ${viewModel.formatEta(totalDistance)}",
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
                                Switch(checked = loopRoute, onCheckedChange = { viewModel.setLoopRoute(it) })
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
                                            viewModel.setIsPaused(!isPaused)
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
                                    onClick = { viewModel.stopMockService(context) },
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

        if (showSetupDialog) {
            MockLocationSetupDialog(
                onDismiss = { viewModel.setShowSetupDialog(false) },
                onOpenSettings = { 
                    viewModel.setShowSetupDialog(false)
                    viewModel.openDeveloperOptions(context)
                }
            )
        }
    }
}


// --- Top Level Extracted Map Functions ---

suspend fun getBestCurrentLocation(context: Context): Location? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val highAcc = withTimeoutOrNull(7000L) {
        awaitFusedLocation(client, Priority.PRIORITY_HIGH_ACCURACY)
    }
    if (highAcc != null) return highAcc

    val balanced = withTimeoutOrNull(4000L) {
        awaitFusedLocation(client, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
    }
    if (balanced != null) return balanced

    return withTimeoutOrNull(2500L) {
        suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        }
    }
}

suspend fun awaitFusedLocation(
    client: com.google.android.gms.location.FusedLocationProviderClient,
    priority: Int
): Location? = suspendCancellableCoroutine { cont ->
    val tokenSource = CancellationTokenSource()
    cont.invokeOnCancellation { tokenSource.cancel() }
    client.getCurrentLocation(priority, tokenSource.token)
        .addOnSuccessListener { location -> cont.resume(location) }
        .addOnFailureListener { cont.resume(null) }
}

fun nearestPointIndex(points: List<GeoPoint>, target: GeoPoint): Int {
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

fun nearestSegmentInsertIndex(points: List<GeoPoint>, target: GeoPoint): Int {
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

fun squaredDistance(a: GeoPoint, b: GeoPoint): Double {
    val latDiff = a.latitude - b.latitude
    val lonDiff = a.longitude - b.longitude
    return latDiff * latDiff + lonDiff * lonDiff
}

fun createEmojiMarkerDrawable(context: Context, emoji: String): BitmapDrawable {
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
