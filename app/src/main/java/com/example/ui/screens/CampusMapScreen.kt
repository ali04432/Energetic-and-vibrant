package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.model.SpotCategory
import com.example.data.model.StudySpot
import com.example.data.model.Subject
import com.example.data.model.TimetableSlot
import com.example.data.model.UiState
import com.example.ui.components.dayLabelShort
import com.example.ui.components.formatMinutesOfDay
import com.example.ui.theme.AmberTertiaryLight
import com.example.ui.theme.ErrorLight
import com.example.ui.theme.IndigoPrimaryLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintSecondaryLight
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.MapsGroundingUiState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private val DEFAULT_CAMPUS_CENTER = LatLng(37.4275, -122.1697)

private const val DARK_MAP_STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#131A2D"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#94A3B8"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#0B1020"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#1E293B"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0F172A"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#112926"}]}
]
"""

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampusMapScreen(
  themeMode: ThemeMode,
  spotsState: UiState<List<StudySpot>>,
  subjectsState: UiState<List<Subject>>,
  slotsState: UiState<List<TimetableSlot>>,
  mapsGroundingState: MapsGroundingUiState,
  onDiscoverNearbyPlaces: (query: String, latitude: Double, longitude: Double) -> Unit,
  onClearMapsGrounding: () -> Unit,
  onAddStudySpot: (
    name: String,
    category: String,
    address: String,
    notes: String,
    subjectId: String,
    latitude: Double,
    longitude: Double,
  ) -> Unit,
  onDeleteStudySpot: (String) -> Unit,
  onFocusHere: (subjectId: String, subjectName: String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val spots = (spotsState as? UiState.Success)?.data.orEmpty()
  val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
  val slots = (slotsState as? UiState.Success)?.data.orEmpty()

  val isDark =
    when (themeMode) {
      ThemeMode.DARK -> true
      ThemeMode.LIGHT -> false
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

  val hasMapsApiKey =
    BuildConfig.MAPS_API_KEY.isNotBlank() && BuildConfig.MAPS_API_KEY != "MY_MAPS_API_KEY"

  var selectedCategory by remember { mutableStateOf<SpotCategory?>(null) }
  var selectedSpot by remember { mutableStateOf<StudySpot?>(null) }
  var searchQuery by remember { mutableStateOf("") }
  var searchStatusMessage by remember { mutableStateOf<String?>(null) }
  var pendingPinLatLng by remember { mutableStateOf<LatLng?>(null) }
  var pendingPinName by remember { mutableStateOf("") }
  var pendingPinAddress by remember { mutableStateOf("") }
  var showPinDialog by remember { mutableStateOf(false) }

  var hasLocationPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val cameraPositionState = rememberCameraPositionState {
    val firstSpot = spots.firstOrNull()
    val startLatLng =
      if (firstSpot != null) LatLng(firstSpot.latitude, firstSpot.longitude)
      else DEFAULT_CAMPUS_CENTER
    position = CameraPosition.fromLatLngZoom(startLatLng, 14.5f)
  }

  @SuppressLint("MissingPermission")
  fun recenterOnUserLocation() {
    if (!hasLocationPermission) return
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    fusedClient.lastLocation.addOnSuccessListener { location ->
      if (location != null) {
        scope.launch {
          cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
              LatLng(location.latitude, location.longitude),
              15.5f,
            )
          )
        }
      } else {
        searchStatusMessage = "Device location unavailable — tap or search on the map."
      }
    }
  }

  val locationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      val granted =
        results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      hasLocationPermission = granted
      if (granted) {
        recenterOnUserLocation()
      } else {
        searchStatusMessage = "Location denied. You can still search or long-press the map to pin spots."
      }
    }

  val filteredSpots =
    remember(spots, selectedCategory) {
      if (selectedCategory == null) spots
      else spots.filter { it.category == selectedCategory?.name }
    }

  fun performGeocodeSearch(queryText: String, prefillName: String = "", openPinDialogOnFallback: Boolean = false) {
    val trimmed = queryText.trim()
    if (trimmed.isEmpty()) return
    scope.launch {
      searchStatusMessage = "Locating \"$trimmed\"..."
      val resolved = geocodeAddress(context, trimmed)
      if (resolved != null) {
        val (latLng, addrLabel) = resolved
        searchStatusMessage = addrLabel
        pendingPinLatLng = latLng
        pendingPinName = prefillName.ifBlank { trimmed }
        pendingPinAddress = addrLabel
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 15.8f))
      } else if (openPinDialogOnFallback) {
        pendingPinLatLng = cameraPositionState.position.target
        pendingPinName = prefillName.ifBlank { trimmed }
        pendingPinAddress = trimmed
        showPinDialog = true
      } else {
        searchStatusMessage = "No exact coordinates found. Long-press or try AI Maps discovery."
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    val mapProperties =
      remember(hasLocationPermission, isDark) {
        MapProperties(
          isMyLocationEnabled = hasLocationPermission,
          mapStyleOptions = if (isDark) MapStyleOptions(DARK_MAP_STYLE_JSON) else null,
        )
      }
    val mapUiSettings =
      remember {
        MapUiSettings(
          zoomControlsEnabled = false,
          myLocationButtonEnabled = false,
          compassEnabled = true,
        )
      }

    GoogleMap(
      modifier = Modifier.fillMaxSize().testTag("campus_google_map"),
      cameraPositionState = cameraPositionState,
      properties = mapProperties,
      uiSettings = mapUiSettings,
      onMapLongClick = { latLng ->
        pendingPinLatLng = latLng
        scope.launch {
          pendingPinAddress = reverseGeocodeAddress(context, latLng)
          showPinDialog = true
        }
      },
      onMapClick = { selectedSpot = null },
    ) {
      filteredSpots.forEach { spot ->
        val hue =
          when (spot.category) {
            SpotCategory.CLASSROOM.name -> BitmapDescriptorFactory.HUE_VIOLET
            SpotCategory.LIBRARY.name -> BitmapDescriptorFactory.HUE_GREEN
            SpotCategory.CAFE.name -> BitmapDescriptorFactory.HUE_ORANGE
            else -> BitmapDescriptorFactory.HUE_ROSE
          }
        Marker(
          state = MarkerState(position = LatLng(spot.latitude, spot.longitude)),
          title = spot.name,
          snippet = spot.address.ifBlank { spot.category },
          icon = BitmapDescriptorFactory.defaultMarker(hue),
          onClick = {
            selectedSpot = spot
            true
          },
        )
      }

      pendingPinLatLng?.let { previewLatLng ->
        Marker(
          state = MarkerState(position = previewLatLng),
          title = "Searched Location",
          snippet = pendingPinAddress,
          icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
          onClick = {
            showPinDialog = true
            true
          },
        )
      }
    }

    // Top Search + Filter Controls Overlay
    Column(
      modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (!hasMapsApiKey) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
          shape = RoundedCornerShape(16.dp),
        ) {
          Text(
            text = "Add MAPS_API_KEY in AI Studio Secrets to render live Google Maps tiles. Saved campus pins below stay synced.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search library, hall, or campus...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
              }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).testTag("map_search_input"),
          )
          Spacer(modifier = Modifier.width(6.dp))
          FilledTonalButton(
            onClick = { performGeocodeSearch(searchQuery) },
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("map_search_button"),
          ) {
            Text("Go")
          }
          Spacer(modifier = Modifier.width(4.dp))
          FilledTonalButton(
            onClick = {
              val target = cameraPositionState.position.target
              val discoveryPrompt = searchQuery.ifBlank { "quiet study libraries and cafes" }
              onDiscoverNearbyPlaces(discoveryPrompt, target.latitude, target.longitude)
            },
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("map_ai_discover_button"),
          ) {
            Icon(Icons.Rounded.TravelExplore, contentDescription = "Discover with Google Maps AI", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("AI")
          }
        }
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          FilterChip(
            selected = selectedCategory == null,
            onClick = { selectedCategory = null },
            label = { Text("All (${spots.size})") },
            modifier = Modifier.testTag("map_filter_all"),
          )
        }
        items(SpotCategory.entries) { cat ->
          val count = spots.count { it.category == cat.name }
          FilterChip(
            selected = selectedCategory == cat,
            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
            label = { Text("${cat.label} ($count)") },
            modifier = Modifier.testTag("map_filter_${cat.name.lowercase()}"),
          )
        }
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val discoveryPresets =
          listOf(
            "Quiet Libraries" to "quiet public and university libraries for studying",
            "Study Cafes" to "quiet cafes with Wi-Fi and power outlets for studying",
            "Campus Study Halls" to "campus study halls and coworking spaces",
          )
        items(discoveryPresets) { (chipLabel, promptText) ->
          AssistChip(
            onClick = {
              searchQuery = chipLabel
              val target = cameraPositionState.position.target
              onDiscoverNearbyPlaces(promptText, target.latitude, target.longitude)
            },
            leadingIcon = {
              Icon(
                Icons.Rounded.TravelExplore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = { Text(chipLabel) },
            modifier = Modifier.testTag("map_ai_chip_${chipLabel.lowercase().replace(" ", "_")}"),
          )
        }
      }

      searchStatusMessage?.let { msg ->
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
          shape = RoundedCornerShape(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = msg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            if (pendingPinLatLng != null) {
              TextButton(onClick = { showPinDialog = true }) {
                Text("Pin Spot")
              }
            }
          }
        }
      }

      val showMapsOverlay =
        mapsGroundingState.isLoading ||
          mapsGroundingState.summaryText.isNotBlank() ||
          mapsGroundingState.places.isNotEmpty() ||
          mapsGroundingState.errorMessage != null

      if (showMapsOverlay) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
          shape = RoundedCornerShape(20.dp),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
          modifier = Modifier.fillMaxWidth().heightIn(max = 270.dp).testTag("map_ai_results_card"),
        ) {
          Column(
            modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  Icons.Rounded.TravelExplore,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "Google Maps Study Picks",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
              }
              IconButton(
                onClick = onClearMapsGrounding,
                modifier = Modifier.size(48.dp).testTag("map_ai_results_close_button"),
              ) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss Maps recommendations")
              }
            }

            if (mapsGroundingState.isLoading) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                  text = "Discovering nearby spots with Google Maps...",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            mapsGroundingState.errorMessage?.let { err ->
              Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }

            if (mapsGroundingState.summaryText.isNotBlank()) {
              Text(
                text = mapsGroundingState.summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }

            mapsGroundingState.places.forEachIndexed { index, place ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Icon(
                      Icons.Rounded.Place,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = place.title,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    OutlinedButton(
                      onClick = {
                        performGeocodeSearch(
                          queryText = place.title,
                          prefillName = place.title,
                          openPinDialogOnFallback = true,
                        )
                      },
                      contentPadding = PaddingValues(horizontal = 10.dp),
                      modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("map_ai_locate_$index"),
                    ) {
                      Icon(Icons.Rounded.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Locate & Pin", maxLines = 1)
                    }
                    if (place.uri.isNotBlank()) {
                      FilledTonalButton(
                        onClick = {
                          runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(place.uri)))
                          }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("map_ai_open_$index"),
                      ) {
                        Icon(
                          Icons.AutoMirrored.Rounded.OpenInNew,
                          contentDescription = "Open ${place.title} in Google Maps",
                          modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Maps")
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

    // Right-side Map Actions (My Location + Pin Current Center)
    Column(
      modifier =
        Modifier.align(Alignment.BottomEnd)
          .padding(end = 16.dp, bottom = if (selectedSpot != null) 216.dp else 132.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SmallFloatingActionButton(
        onClick = {
          if (hasLocationPermission) {
            recenterOnUserLocation()
          } else {
            locationLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
              )
            )
          }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp).testTag("map_my_location_button"),
      ) {
        Icon(Icons.Rounded.MyLocation, contentDescription = "Center on my location")
      }

      SmallFloatingActionButton(
        onClick = {
          val center = cameraPositionState.position.target
          pendingPinLatLng = center
          pendingPinName = ""
          scope.launch {
            pendingPinAddress = reverseGeocodeAddress(context, center)
            showPinDialog = true
          }
        },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(48.dp).testTag("map_pin_center_button"),
      ) {
        Icon(Icons.Rounded.AddLocationAlt, contentDescription = "Pin study spot")
      }
    }

    // Bottom Area: Either Selected Spot Detail Card OR Horizontal Pinned Spots Carousel
    Box(
      modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(14.dp),
    ) {
      val activeSpot = selectedSpot
      if (activeSpot != null) {
        SelectedSpotCard(
          spot = activeSpot,
          subjects = subjects,
          slots = slots,
          onClose = { selectedSpot = null },
          onDirections = {
            val uri =
              Uri.parse(
                "geo:${activeSpot.latitude},${activeSpot.longitude}?q=${Uri.encode("${activeSpot.latitude},${activeSpot.longitude}(${activeSpot.name})")}"
              )
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
          },
          onFocusHere = { subjId, subjName -> onFocusHere(subjId, subjName) },
          onDelete = {
            onDeleteStudySpot(activeSpot.id)
            selectedSpot = null
          },
        )
      } else if (filteredSpots.isNotEmpty()) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(end = 68.dp),
        ) {
          items(filteredSpots, key = { it.id }) { spot ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
              shape = RoundedCornerShape(18.dp),
              elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
              modifier =
                Modifier.width(216.dp)
                  .clip(RoundedCornerShape(18.dp))
                  .clickable {
                    selectedSpot = spot
                    scope.launch {
                      cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(spot.latitude, spot.longitude), 16f)
                      )
                    }
                  }
                  .testTag("map_spot_pill_${spot.id}"),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(
                  color = spotCategoryColor(spot.category).copy(alpha = 0.16f),
                  shape = CircleShape,
                  modifier = Modifier.size(36.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = spotCategoryIcon(spot.category),
                      contentDescription = null,
                      tint = spotCategoryColor(spot.category),
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = spot.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = spot.address.ifBlank { spotCategoryLabel(spot.category) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      } else {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth().padding(end = 68.dp),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "Long-press on the map or tap + to pin classrooms, libraries & quiet cafes.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }

  if (showPinDialog) {
    val targetLatLng = pendingPinLatLng ?: cameraPositionState.position.target
    PinStudySpotDialog(
      initialLat = targetLatLng.latitude,
      initialLng = targetLatLng.longitude,
      initialName = pendingPinName,
      initialAddress = pendingPinAddress,
      subjects = subjects,
      onDismiss = {
        showPinDialog = false
        pendingPinName = ""
      },
      onConfirm = { name, category, address, notes, subjectId, lat, lng ->
        onAddStudySpot(name, category, address, notes, subjectId, lat, lng)
        pendingPinLatLng = null
        pendingPinName = ""
        showPinDialog = false
      },
    )
  }
}

@Composable
private fun SelectedSpotCard(
  spot: StudySpot,
  subjects: List<Subject>,
  slots: List<TimetableSlot>,
  onClose: () -> Unit,
  onDirections: () -> Unit,
  onFocusHere: (subjectId: String, subjectName: String) -> Unit,
  onDelete: () -> Unit,
) {
  val linkedSubject = subjects.firstOrNull { it.id == spot.subjectId }
  val linkedSlots =
    if (spot.subjectId.isBlank()) emptyList()
    else slots.filter { it.subjectId == spot.subjectId }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(22.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = spotCategoryColor(spot.category).copy(alpha = 0.16f),
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = spotCategoryLabel(spot.category),
            style = MaterialTheme.typography.labelMedium,
            color = spotCategoryColor(spot.category),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        Row {
          IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete spot")
          }
          IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Close card")
          }
        }
      }

      Text(spot.name, style = MaterialTheme.typography.titleLarge)
      if (spot.address.isNotBlank()) {
        Text(
          text = spot.address,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (linkedSubject != null) {
        val nextClass = linkedSlots.firstOrNull()
        Text(
          text =
            if (nextClass != null)
              "${linkedSubject.name} • ${dayLabelShort(nextClass.dayOfWeek.toInt())} ${formatMinutesOfDay(nextClass.startMinutes.toInt())}"
            else "Course: ${linkedSubject.name}",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      if (spot.notes.isNotBlank()) {
        Text(spot.notes, style = MaterialTheme.typography.bodySmall)
      }
      Text(
        text = String.format(Locale.US, "%.4f, %.4f", spot.latitude, spot.longitude),
        fontFamily = JetBrainsMonoFontFamily,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = onDirections,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
          Icon(Icons.Rounded.Directions, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Directions")
        }
        Button(
          onClick = {
            onFocusHere(
              linkedSubject?.id ?: "",
              linkedSubject?.name ?: spot.name,
            )
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Focus Here")
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinStudySpotDialog(
  initialLat: Double,
  initialLng: Double,
  initialName: String = "",
  initialAddress: String,
  subjects: List<Subject>,
  onDismiss: () -> Unit,
  onConfirm: (
    name: String,
    category: String,
    address: String,
    notes: String,
    subjectId: String,
    lat: Double,
    lng: Double,
  ) -> Unit,
) {
  var name by remember(initialName) { mutableStateOf(initialName) }
  var category by remember { mutableStateOf(SpotCategory.LIBRARY) }
  var address by remember(initialAddress) { mutableStateOf(initialAddress) }
  var notes by remember { mutableStateOf("") }
  var selectedSubjectId by remember { mutableStateOf("") }
  val lat by remember { mutableDoubleStateOf(initialLat) }
  val lng by remember { mutableDoubleStateOf(initialLng) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pin Campus / Study Spot", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Spot Name *") },
          placeholder = { Text("e.g., Science Library 3F") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("pin_spot_name_input"),
        )
        Text("Category", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          SpotCategory.entries.forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat.label) },
            )
          }
        }
        OutlinedTextField(
          value = address,
          onValueChange = { address = it },
          label = { Text("Building / Address") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (subjects.isNotEmpty()) {
          Text("Link to Course (Optional)", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = selectedSubjectId.isBlank(),
              onClick = { selectedSubjectId = "" },
              label = { Text("None") },
            )
            subjects.forEach { subj ->
              FilterChip(
                selected = selectedSubjectId == subj.id,
                onClick = { selectedSubjectId = subj.id },
                label = { Text(subj.name) },
              )
            }
          }
        }
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (outlets, quiet level, room #)") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = String.format(Locale.US, "Coordinates: %.5f, %.5f", lat, lng),
          fontFamily = JetBrainsMonoFontFamily,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name, category.name, address, notes, selectedSubjectId, lat, lng) },
        enabled = name.isNotBlank(),
        modifier = Modifier.testTag("confirm_pin_spot_button"),
      ) {
        Text("Save Pin")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

private fun spotCategoryColor(category: String): Color =
  when (category) {
    SpotCategory.CLASSROOM.name -> IndigoPrimaryLight
    SpotCategory.LIBRARY.name -> MintSecondaryLight
    SpotCategory.CAFE.name -> AmberTertiaryLight
    else -> ErrorLight
  }

private fun spotCategoryIcon(category: String): ImageVector =
  when (category) {
    SpotCategory.CLASSROOM.name -> Icons.Rounded.School
    SpotCategory.LIBRARY.name -> Icons.AutoMirrored.Rounded.MenuBook
    SpotCategory.CAFE.name -> Icons.Rounded.LocalCafe
    else -> Icons.Rounded.LocationOn
  }

private fun spotCategoryLabel(category: String): String =
  SpotCategory.entries.firstOrNull { it.name == category }?.label ?: "Study Spot"

private suspend fun geocodeAddress(context: android.content.Context, query: String): Pair<LatLng, String>? =
  withContext(Dispatchers.IO) {
    try {
      if (!Geocoder.isPresent()) return@withContext null
      val geocoder = Geocoder(context, Locale.getDefault())
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
          geocoder.getFromLocationName(query, 1) { list ->
            val first = list.firstOrNull()
            val res =
              if (first != null) {
                val label = first.getAddressLine(0) ?: query
                LatLng(first.latitude, first.longitude) to label
              } else null
            if (cont.isActive) cont.resume(res)
          }
        }
      } else {
        @Suppress("DEPRECATION")
        val list = geocoder.getFromLocationName(query, 1)
        val first = list?.firstOrNull()
        if (first != null) {
          LatLng(first.latitude, first.longitude) to (first.getAddressLine(0) ?: query)
        } else null
      }
    } catch (_: Exception) {
      null
    }
  }

private suspend fun reverseGeocodeAddress(context: android.content.Context, latLng: LatLng): String =
  withContext(Dispatchers.IO) {
    try {
      if (!Geocoder.isPresent()) return@withContext ""
      val geocoder = Geocoder(context, Locale.getDefault())
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
          geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { list ->
            val addr = list.firstOrNull()?.getAddressLine(0).orEmpty()
            if (cont.isActive) cont.resume(addr)
          }
        }
      } else {
        @Suppress("DEPRECATION")
        val list = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
        list?.firstOrNull()?.getAddressLine(0).orEmpty()
      }
    } catch (_: Exception) {
      ""
    }
  }
