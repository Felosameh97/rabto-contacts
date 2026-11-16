package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.domain.model.ContactRecord
import com.example.util.EgyptGeoLookup
import com.example.util.SystemIntents
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    geoContacts: List<ContactRecord>,
    onOpenContact: (ContactRecord) -> Unit,
) {
    val context = LocalContext.current
    var centerLat by remember { mutableDoubleStateOf(30.0844) }
    var centerLng by remember { mutableDoubleStateOf(31.2957) }
    var selectedDistrict by remember { mutableStateOf<String?>(null) }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(11.5)
            controller.setCenter(GeoPoint(centerLat, centerLng))
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        centerLat = loc.latitude
                        centerLng = loc.longitude
                        mapView.controller.animateTo(GeoPoint(centerLat, centerLng))
                        mapView.controller.setZoom(14.0)
                    }
                }
        }
    }

    val sortedNearby = remember(geoContacts, centerLat, centerLng, selectedDistrict) {
        geoContacts
            .filter { selectedDistrict == null || it.district == selectedDistrict || it.address?.contains(selectedDistrict.orEmpty(), true) == true }
            .map { contact ->
                val distKm = haversineKm(centerLat, centerLng, contact.latitude ?: centerLat, contact.longitude ?: centerLng)
                contact to distKm
            }
            .sortedBy { it.second }
    }

    val markersSignature = remember(sortedNearby, selectedDistrict) {
        "${selectedDistrict.orEmpty()}_${sortedNearby.size}_${sortedNearby.take(15).joinToString(",") { it.first.id }}"
    }

    val availableDistricts = remember(geoContacts) {
        val counts = geoContacts.mapNotNull { it.district }.groupingBy { it }.eachCount()
        EgyptGeoLookup.knownDistricts.filter { (counts[it.key] ?: 0) > 0 }
            .sortedByDescending { counts[it.key] ?: 0 }
            .ifEmpty { EgyptGeoLookup.knownDistricts.take(12) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = {
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (hasFine || hasCoarse) {
                        LocationServices.getFusedLocationProviderClient(context).lastLocation
                            .addOnSuccessListener { loc ->
                                if (loc != null) {
                                    centerLat = loc.latitude
                                    centerLng = loc.longitude
                                    mapView.controller.animateTo(GeoPoint(centerLat, centerLng))
                                    mapView.controller.setZoom(14.0)
                                }
                            }
                    } else {
                        locationLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                        )
                    }
                },
                modifier = Modifier.testTag("map_my_location_btn"),
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("موقعي الحالي")
            }

            FilterChip(
                selected = selectedDistrict == null,
                onClick = { selectedDistrict = null },
                label = { Text("الكل (${geoContacts.size})") },
            )
            availableDistricts.take(18).forEach { dist ->
                FilterChip(
                    selected = selectedDistrict == dist.key,
                    onClick = {
                        selectedDistrict = if (selectedDistrict == dist.key) null else dist.key
                        centerLat = dist.lat
                        centerLng = dist.lng
                        mapView.controller.animateTo(GeoPoint(dist.lat, dist.lng))
                        mapView.controller.setZoom(13.2)
                    },
                    label = { Text(dist.key) },
                )
            }
        }

        // Interactive OSMDroid Map guarded against redundant marker rebuilds
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            AndroidView(
                factory = { mapView },
                update = { view ->
                    if (view.tag != markersSignature) {
                        view.tag = markersSignature
                        view.overlays.clear()
                        sortedNearby.take(250).forEach { (contact, distKm) ->
                            val lat = contact.latitude ?: return@forEach
                            val lng = contact.longitude ?: return@forEach
                            // Subtle deterministic jitter so pins in the same district don't overlap on 1 dot
                            val hash = contact.id.hashCode()
                            val latOffset = ((hash and 0xF) - 7) * 0.00055
                            val lngOffset = (((hash ushr 4) and 0xF) - 7) * 0.00055
                            val marker = Marker(view).apply {
                                position = GeoPoint(lat + latOffset, lng + lngOffset)
                                title = contact.displayName
                                snippet = "${contact.primaryPhone} • ${String.format("%.1f كم", distKm)}"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                setOnMarkerClickListener { _, _ ->
                                    onOpenContact(contact)
                                    true
                                }
                            }
                            view.overlays.add(marker)
                        }
                        view.invalidate()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Nearby Contacts List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "جهات اتصال في هذا النطاق (${sortedNearby.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AssistChip(
                        onClick = {
                            SystemIntents.openGoogleMaps(context, centerLat, centerLng, selectedDistrict ?: "الموقع المحدد")
                        },
                        label = { Text("Google Maps") },
                        leadingIcon = { Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }

            if (sortedNearby.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("لا توجد جهات اتصال بإحداثيات في هذا النطاق", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "اضغط 'تنظيم ذكي' في الفئات لاستنتاج المواقع تلقائياً من المحافظات والمناطق وأكواد الخطوط الأرضية، أو أضف المنطقة يدوياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(sortedNearby.take(80), key = { it.first.id }) { (contact, distKm) ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenContact(contact) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${contact.district ?: contact.address ?: "موقع محدد"} • ${String.format("%.1f كم", distKm)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            if (contact.primaryPhone.isNotBlank()) {
                                FilledTonalIconButton(
                                    onClick = { SystemIntents.dialPhone(context, contact.primaryPhone) },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = "اتصال", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}
