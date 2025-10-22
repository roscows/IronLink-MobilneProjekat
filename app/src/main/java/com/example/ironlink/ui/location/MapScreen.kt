@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ironlink.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ironlink.data.FilterCriteria
import com.example.ironlink.data.TrainingPartner
import com.example.ironlink.ui.common.BottomNavigationBar
import com.example.ironlink.ui.common.getCurrentRoute
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MapScreen(
    viewModel: LocationViewModel = viewModel(),
    navController: NavController,
    focusPartnerId: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val objectsCollection = firestore.collection("training_partners")
    val userId = auth.currentUser?.uid ?: ""

    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var locationErrorMessage by remember { mutableStateOf<String?>(null) }

    var newActivityLocation by remember { mutableStateOf<LatLng?>(null) }
    var tempMarker by remember { mutableStateOf<Marker?>(null) }

    var partnerName by remember { mutableStateOf("") }
    var partnerDescription by remember { mutableStateOf("") }
    var partnerPhone by remember { mutableStateOf("") }
    var partnerType by remember { mutableStateOf("") }

    var showDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var filterCriteria by remember { mutableStateOf<FilterCriteria?>(null) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val mapView = remember { MapView(context) }

    var hasZoomedToPartner by remember { mutableStateOf(false) }

    LaunchedEffect(focusPartnerId) {
        if (focusPartnerId != null && !hasZoomedToPartner) {
            kotlinx.coroutines.delay(500)
            try {
                val doc = firestore.collection("training_partners")
                    .document(focusPartnerId)
                    .get()
                    .await()

                val partner = doc.toObject(TrainingPartner::class.java)
                partner?.let {
                    val lat = it.latitude
                    val lng = it.longitude
                    if (lat != null && lng != null) {
                        val position = LatLng(lat, lng)
                        googleMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(position, 17f)
                        )
                        hasZoomedToPartner = true
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to find location", Toast.LENGTH_SHORT).show()
                hasZoomedToPartner = true
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            currentLocationUpdates(context, fusedLocationClient) { location ->
                currentLocation = location
                location?.let {
                    googleMap?.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(it.latitude, it.longitude), 15f
                        )
                    )
                }
                googleMap?.isMyLocationEnabled = true
                googleMap?.uiSettings?.isMyLocationButtonEnabled = true
            }
        } else {
            locationErrorMessage = "Permission denied!"
        }
    }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            currentLocationUpdates(context, fusedLocationClient) { location ->
                currentLocation = location
                location?.let {
                    googleMap?.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(it.latitude, it.longitude), 15f
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController, getCurrentRoute(navController)) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { mapView.apply { onCreate(null); onResume() } },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                update = { view ->
                    view.getMapAsync { map ->
                        googleMap = map
                        if (hasLocationPermission) {
                            googleMap?.isMyLocationEnabled = true
                            googleMap?.uiSettings?.isMyLocationButtonEnabled = true
                        }
                        currentLocation?.let {
                            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
                        }
                        loadPartnersOnMap(googleMap, objectsCollection, filterCriteria, currentLocation, navController)

                        googleMap?.setOnMapLongClickListener { latLng ->
                            tempMarker?.remove()
                            tempMarker = googleMap?.addMarker(
                                MarkerOptions()
                                    .position(latLng)
                                    .title("New Activity Location")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                            )
                            newActivityLocation = latLng
                            showDialog = true
                        }

                        googleMap?.setOnMarkerClickListener { marker ->
                            if (marker == tempMarker) return@setOnMarkerClickListener true
                            val markerId = marker.tag as? String
                            if (markerId != null) {
                                navController.navigate("details/$markerId")
                            }
                            true
                        }
                    }
                },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        val location = currentLocation
                        if (location != null) {
                            newActivityLocation = LatLng(location.latitude, location.longitude)
                            showDialog = true
                            tempMarker?.remove()
                        } else {
                            Toast.makeText(context, "Current location not available.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create Activity at my location")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                }
            }

            if (showDialog) {
                AddTrainingPartnerDialog(
                    onDismiss = {
                        showDialog = false
                        tempMarker?.remove()
                        tempMarker = null
                    },
                    onSave = { partner ->
                        coroutineScope.launch {
                            val finalPartner = partner.copy(
                                latitude = newActivityLocation?.latitude ?: 0.0,
                                longitude = newActivityLocation?.longitude ?: 0.0
                            )
                            objectsCollection.add(finalPartner).await()
                            if (userId.isNotEmpty()) {
                                firestore.collection("users").document(userId).update("points", FieldValue.increment(10))
                            }
                            loadPartnersOnMap(googleMap, objectsCollection, filterCriteria, currentLocation, navController)
                            showDialog = false
                            tempMarker?.remove()
                            tempMarker = null

                            partnerName = ""
                            partnerDescription = ""
                            partnerPhone = ""
                            partnerType = ""
                        }
                    },
                    name = partnerName,
                    onNameChange = { partnerName = it },
                    description = partnerDescription,
                    onDescriptionChange = { partnerDescription = it },
                    phone = partnerPhone,
                    onPhoneChange = { partnerPhone = it },
                    type = partnerType,
                    onTypeChange = { partnerType = it },
                    auth = auth
                )
            }
            if (showFilterDialog) {
                FilterDialog(onDismiss = { showFilterDialog = false }, onApply = { criteria ->
                    filterCriteria = criteria
                    loadPartnersOnMap(googleMap, objectsCollection, criteria, currentLocation, navController)
                    showFilterDialog = false
                })
            }
        }
    }
}

@Composable
fun AddTrainingPartnerDialog(
    onDismiss: () -> Unit,
    onSave: (TrainingPartner) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    type: String,
    onTypeChange: (String) -> Unit,
    auth: FirebaseAuth,
) {
    var isSaving by remember { mutableStateOf(false) }
    val types = listOf("Fitness", "Bodybuilding", "Yoga", "Running", "Team Sport", "Other")
    var expanded by remember { mutableStateOf(false) }
    var eventDate by remember { mutableStateOf<Date?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a New Activity") },
        text = {
            Column {
                TextField(value = name, onValueChange = onNameChange, label = { Text("Activity Name (e.g., 'Running on Ada')") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = description, onValueChange = onDescriptionChange, label = { Text("Description (e.g., 'Light pace, 5km')") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = phone, onValueChange = onPhoneChange, label = { Text("Contact Phone (Optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    TextField(
                        value = type,
                        onValueChange = {},
                        label = { Text("Activity Type") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        types.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    onTypeChange(item)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Time", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                DateTimePicker(
                    label = "Date & Time of Activity",
                    selectedDate = eventDate,
                    onDateChange = { eventDate = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    val partner = TrainingPartner(
                        name = name,
                        description = description,
                        type = type,
                        phone = phone,
                        userId = auth.currentUser?.uid,
                        dateCreated = Timestamp.now(),
                        eventTimestamp = eventDate?.let { Timestamp(it) }
                    )
                    onSave(partner)
                    isSaving = false
                },
                enabled = !isSaving && name.isNotBlank() && type.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun FilterDialog(
    onDismiss: () -> Unit,
    onApply: (FilterCriteria) -> Unit,
) {
    var partnerName by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    var radius by remember { mutableFloatStateOf(100f) }
    var useRadiusFilter by remember { mutableStateOf(false) }

    val types = listOf("Fitness", "Bodybuilding", "Yoga", "Running", "Team Sport", "Other")
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Activities") },
        text = {
            Column {
                TextField(
                    value = partnerName,
                    onValueChange = { partnerName = it },
                    label = { Text("Activity Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = type,
                        onValueChange = {},
                        label = { Text("Type") },
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        types.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    type = item
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useRadiusFilter,
                        onCheckedChange = { useRadiusFilter = it }
                    )
                    Text("Use Radius Filter")
                }

                if (useRadiusFilter) {
                    Text("Radius: ${radius.toInt()} m")
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 0f..1000f,
                        steps = 99,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DatePicker(
                            label = "Select Date",
                            selectedDate = selectedDate,
                            onDateChange = { selectedDate = it }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            // Resetuj polja
                            partnerName = ""
                            type = ""
                            selectedDate = null
                            radius = 100f
                            useRadiusFilter = false
                            // Primeni prazan filter
                            onApply(FilterCriteria())
                            onDismiss()
                        },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text("Clear")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        FilterCriteria(
                            partnerName = partnerName.takeIf { it.isNotBlank() },
                            type = type.takeIf { it.isNotBlank() },
                            selectedDate = selectedDate?.let { Timestamp(it) },
                            radius = if (useRadiusFilter) radius else null,
                        ),
                    )
                    onDismiss()
                },
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun loadPartnersOnMap(
    googleMap: GoogleMap?,
    objectsCollection: com.google.firebase.firestore.CollectionReference,
    filterCriteria: FilterCriteria?,
    currentLocation: Location?,
    navController: NavController,
) {
    objectsCollection.get().addOnSuccessListener { snapshot ->
        googleMap?.clear()
        for (doc in snapshot.documents) {
            val partner = doc.toObject(TrainingPartner::class.java) ?: continue
            val lat = partner.latitude ?: continue
            val lng = partner.longitude ?: continue

            val matches = filterMatches(partner, filterCriteria, currentLocation)
            if (matches) {
                val position = LatLng(lat, lng)
                val marker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(partner.name ?: "Partner")
                        .snippet("Type: ${partner.type}\nClick for details")
                )
                marker?.tag = doc.id
            }
        }
    }
}

private fun filterMatches(
    partner: TrainingPartner,
    criteria: FilterCriteria?,
    currentLocation: Location?,
): Boolean {
    if (criteria == null) return true

    val matchesType = criteria.type?.let { it == partner.type } ?: true

    val matchesDate = criteria.selectedDate?.let { selectedTimestamp ->
        val partnerDate = partner.eventTimestamp?.toDate() ?: partner.dateCreated?.toDate()
        if (partnerDate == null) return@let false

        val calendar = Calendar.getInstance()
        calendar.time = selectedTimestamp.toDate()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val filterDateStart = calendar.time

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val filterDateEnd = calendar.time

        partnerDate.after(filterDateStart) && partnerDate.before(filterDateEnd) ||
                partnerDate == filterDateStart
    } ?: true

    val matchesRadius = criteria.radius?.let { radius ->
        currentLocation?.let { loc ->
            val distance = calculateDistance(
                loc.latitude,
                loc.longitude,
                partner.latitude ?: 0.0,
                partner.longitude ?: 0.0
            )
            distance <= radius
        } ?: true
    } ?: true

    val matchesName = criteria.partnerName?.let { filterName ->
        partner.name?.contains(filterName, ignoreCase = true) ?: false
    } ?: true

    return matchesType && matchesDate && matchesRadius && matchesName
}

private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c * 1000
}

@Composable
fun DateTimePicker(
    label: String,
    selectedDate: Date?,
    onDateChange: (Date?) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    selectedDate?.let {
        calendar.time = it
    }
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            onDateChange(calendar.time)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    )
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            timePickerDialog.show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    OutlinedButton(onClick = { datePickerDialog.show() }) {
        Text(text = if (selectedDate != null) dateFormat.format(selectedDate) else label)
    }
}

@Composable
fun DatePicker(
    label: String,
    selectedDate: Date?,
    onDateChange: (Date?) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateChange(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    OutlinedButton(onClick = { datePickerDialog.show() }) {
        Text(text = if (selectedDate != null) dateFormat.format(selectedDate) else label)
    }
}

private fun currentLocationUpdates(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    onLocationReceived: (Location?) -> Unit,
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            onLocationReceived(location)
        }
    } else {
        onLocationReceived(null)
    }
}