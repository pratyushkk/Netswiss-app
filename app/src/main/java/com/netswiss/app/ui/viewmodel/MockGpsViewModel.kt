package com.netswiss.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.netswiss.app.service.MockLocationService
import com.netswiss.app.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.ceil

data class SearchResult(val name: String, val lat: Double, val lon: Double)

enum class SimulationMode { Static, Path }
enum class TravelMode { Walk, Bike, Drive }

class MockGpsViewModel : ViewModel() {

    // --- State Properties ---
    
    // Core parameters
    private val _latitude = MutableStateFlow("28.6139")
    val latitude: StateFlow<String> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow("77.2090")
    val longitude: StateFlow<String> = _longitude.asStateFlow()

    private val _isMocking = MutableStateFlow(MockLocationService.isRunning)
    val isMocking: StateFlow<Boolean> = _isMocking.asStateFlow()

    private val _sheetExpanded = MutableStateFlow(true)
    val sheetExpanded: StateFlow<Boolean> = _sheetExpanded.asStateFlow()

    private val _simulationMode = MutableStateFlow(SimulationMode.Static)
    val simulationMode: StateFlow<SimulationMode> = _simulationMode.asStateFlow()

    private val _travelMode = MutableStateFlow(TravelMode.Walk)
    val travelMode: StateFlow<TravelMode> = _travelMode.asStateFlow()

    private val _routePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val routePoints: StateFlow<List<GeoPoint>> = _routePoints.asStateFlow()

    private val _drawMode = MutableStateFlow(false)
    val drawMode: StateFlow<Boolean> = _drawMode.asStateFlow()

    private val _loopRoute = MutableStateFlow(true)
    val loopRoute: StateFlow<Boolean> = _loopRoute.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _hasInitialCentered = MutableStateFlow(false)
    val hasInitialCentered: StateFlow<Boolean> = _hasInitialCentered.asStateFlow()

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _showResults = MutableStateFlow(false)
    val showResults: StateFlow<Boolean> = _showResults.asStateFlow()
    
    private val _showSetupDialog = MutableStateFlow(false)
    val showSetupDialog: StateFlow<Boolean> = _showSetupDialog.asStateFlow()
    
    // Internal state
    private var simulationJob: Job? = null
    private var searchJob: Job? = null
    private var lastSearchQuery = ""
    private val searchCache = mutableMapOf<String, List<SearchResult>>()


    // --- State Updaters ---

    fun setLatitude(lat: String) { _latitude.value = lat }
    fun setLongitude(lon: String) { _longitude.value = lon }
    fun setIsMocking(mocking: Boolean) { _isMocking.value = mocking }
    fun setSheetExpanded(expanded: Boolean) { _sheetExpanded.value = expanded }
    fun setSimulationMode(mode: SimulationMode) { _simulationMode.value = mode }
    fun setTravelMode(mode: TravelMode) { _travelMode.value = mode }
    fun setDrawMode(draw: Boolean) { _drawMode.value = draw }
    fun setLoopRoute(loop: Boolean) { _loopRoute.value = loop }
    fun setIsPaused(paused: Boolean) { _isPaused.value = paused }
    fun setHasInitialCentered(centered: Boolean) { _hasInitialCentered.value = centered }
    fun setShowResults(show: Boolean) { _showResults.value = show }
    fun setShowSetupDialog(show: Boolean) { _showSetupDialog.value = show }
    fun setSearchResults(results: List<SearchResult>) { _searchResults.value = results }
    
    fun updateRoutePoints(updater: (MutableList<GeoPoint>) -> Unit) {
        val currentList = _routePoints.value.toMutableList()
        updater(currentList)
        _routePoints.value = currentList
    }
    
    fun clearRoutePoints() {
        _routePoints.value = emptyList()
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        val trimmed = query.trim()
        if (trimmed.length < 2 || trimmed == lastSearchQuery) {
            if (trimmed.isEmpty()) {
                _showResults.value = false
                _searchResults.value = emptyList()
            }
            return
        }
    }


    // --- Core Operations ---
    
    fun syncMockingState() {
        _isMocking.value = MockLocationService.isRunning
    }

    fun isMockLocationEnabled(context: Context): Boolean {
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

    fun openDeveloperOptions(context: Context) {
        try {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(context, "Enable Developer Options manually", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendMockUpdate(context: Context, lat: Double, lng: Double): Boolean {
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
            CrashLogger.logException(context, "MockGpsViewModel", "Mock Update Failure", e)
            false
        }
    }

    fun stopMockService(context: Context) {
        simulationJob?.cancel()
        simulationJob = null
        _isSimulating.value = false
        _isPaused.value = false
        try {
            val stopIntent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_STOP
            }
            context.startService(stopIntent)
        } catch (_: Exception) { }

        context.stopService(Intent(context, MockLocationService::class.java))
        _isMocking.value = false
    }

    // --- Search Logic ---
    
    fun submitSearch(context: Context) {
        launchSearch(_searchQuery.value, context, showToastOnEmpty = true)
    }

    fun launchSearch(query: String, context: Context, showToastOnEmpty: Boolean) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _showResults.value = false
            _searchResults.value = emptyList()
            return
        }
        val cacheKey = trimmed.lowercase(Locale.getDefault())
        val cached = searchCache[cacheKey]
        if (cached != null) {
            _searchResults.value = cached
            _showResults.value = cached.isNotEmpty()
            lastSearchQuery = trimmed
            return
        }
        searchJob?.cancel()
        _isSearching.value = true
        _showResults.value = true
        searchJob = viewModelScope.launch {
            try {
                val results = geocodeSearch(context, trimmed)
                    .distinctBy { "${it.lat},${it.lon}" }
                    .take(8)
                _searchResults.value = results
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
                _isSearching.value = false
            }
        }
    }
    
    fun handleSearchResultClick(result: SearchResult, onLocationSelected: (Double, Double) -> Unit) {
        searchJob?.cancel()
        _isSearching.value = false
        _searchResults.value = emptyList()
        _latitude.value = "%.6f".format(result.lat)
        _longitude.value = "%.6f".format(result.lon)
        _searchQuery.value = ""
        lastSearchQuery = ""
        _showResults.value = false
        onLocationSelected(result.lat, result.lon)
    }

    // --- Utilities & Simulations ---
    
    fun speedMetersPerSecond(): Double {
        return when (_travelMode.value) {
            TravelMode.Walk -> 1.4
            TravelMode.Bike -> 4.8
            TravelMode.Drive -> 13.9
        }
    }

    fun travelProfile(): String {
        return when (_travelMode.value) {
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

    fun locateMe(context: Context, showToast: Boolean = true, onLocationFound: (Double, Double) -> Unit) {
        if (_isLocating.value) return
        _isLocating.value = true
        viewModelScope.launch {
            try {
                val current = getBestCurrentLocation(context)
                if (current != null) {
                    _latitude.value = "%.6f".format(current.latitude)
                    _longitude.value = "%.6f".format(current.longitude)
                    onLocationFound(current.latitude, current.longitude)
                    if (showToast) {
                        Toast.makeText(context, "Current location updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (showToast) {
                        Toast.makeText(context, "Unable to get current location", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (showToast) {
                    Toast.makeText(context, "Location error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isLocating.value = false
            }
        }
    }

    fun startPathSimulation(context: Context, moveMapTo: (Double, Double) -> Unit) {
        val points = _routePoints.value
        if (points.size < 2) {
            Toast.makeText(context, "Add at least 2 route points", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isMockLocationEnabled(context)) {
            Toast.makeText(context, "Select NetSwiss as Mock Location App in Developer Options", Toast.LENGTH_LONG).show()
            openDeveloperOptions(context)
            return
        }

        simulationJob?.cancel()
        _isSimulating.value = true
        _isPaused.value = false

        simulationJob = viewModelScope.launch {
            val requestedPoints = points.toList()
            val routedPoints = withContext(Dispatchers.IO) {
                fetchOsrmRoadRoute(points = requestedPoints, profile = travelProfile())
            }
            val simulationPath = if (routedPoints.size >= 2) {
                routedPoints
            } else {
                if (requestedPoints.size >= 2) {
                    Toast.makeText(context, "Road route unavailable, using straight path", Toast.LENGTH_SHORT).show()
                }
                requestedPoints
            }

            if (simulationPath.size >= 2) {
                _routePoints.value = simulationPath
            }

            val speedMps = speedMetersPerSecond()
            val targetTickMs = 700L
            var lastServicePushMs = 0L
            var keepRunning = true
            
            while (keepRunning && _isSimulating.value) {
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
                        if (!_isSimulating.value) break
                        while (_isPaused.value && _isSimulating.value) {
                            delay(200)
                        }
                        if (!_isSimulating.value) break
                        
                        val fraction = step.toDouble() / stepsPerSegment.toDouble()
                        val lat = from.latitude + ((to.latitude - from.latitude) * fraction)
                        val lon = from.longitude + ((to.longitude - from.longitude) * fraction)
                        
                        _latitude.value = "%.6f".format(lat)
                        _longitude.value = "%.6f".format(lon)
                        moveMapTo(lat, lon)
                        
                        val now = System.currentTimeMillis()
                        val isSegmentEnd = step == stepsPerSegment
                        if (isSegmentEnd || now - lastServicePushMs >= 1000L) {
                            sendMockUpdate(context, lat, lon)
                            lastServicePushMs = now
                        }
                        _isMocking.value = true
                        delay(delayMs)
                    }
                }
                if (!_loopRoute.value) {
                    keepRunning = false
                }
            }
            _isSimulating.value = false
            _isPaused.value = false
        }
    }

    // --- Private Helper Functions ---
    
    private suspend fun getBestCurrentLocation(context: Context): Location? {
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

    private suspend fun awaitFusedLocation(
        client: com.google.android.gms.location.FusedLocationProviderClient,
        priority: Int
    ): Location? = suspendCancellableCoroutine { cont ->
        val tokenSource = CancellationTokenSource()
        cont.invokeOnCancellation { tokenSource.cancel() }
        client.getCurrentLocation(priority, tokenSource.token)
            .addOnSuccessListener { location -> cont.resume(location) }
            .addOnFailureListener { cont.resume(null) }
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
}
