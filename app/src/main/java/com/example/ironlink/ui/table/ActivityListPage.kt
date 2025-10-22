package com.example.ironlink.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ironlink.data.Rate
import com.example.ironlink.data.TrainingPartner
import com.example.ironlink.ui.common.BottomNavigationBar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DecimalFormat

data class TrainingPartnerWithRating(
    val partnerId: String,
    val partner: TrainingPartner,
    val averageRating: Double
)

@Composable
fun ActivityListPage(navController: NavController) {
    val firestore = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()

    var partnersWithRatings by remember { mutableStateOf<List<TrainingPartnerWithRating>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val partnersSnapshot = firestore.collection("training_partners").get().await()
                val partners = partnersSnapshot.documents.mapNotNull { doc ->
                    val partner = doc.toObject(TrainingPartner::class.java) ?: return@mapNotNull null
                    val partnerId = doc.id

                    val ratingsSnapshot = firestore.collection("rates")
                        .whereEqualTo("partnerId", partnerId)
                        .get().await()

                    val ratings = ratingsSnapshot.documents.mapNotNull { it.toObject(Rate::class.java) }
                    val averageRating = if (ratings.isNotEmpty()) {
                        ratings.map { it.value.toDouble() }.average()
                    } else 0.0

                    TrainingPartnerWithRating(partnerId, partner, averageRating)
                }

                partnersWithRatings = partners
            } catch (e: Exception) {
                errorMessage = "Failed to load table!"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController, currentRoute = "table") }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeaderCell(text = "Name", weight = 3f)
                            HeaderCell(text = "Type", weight = 2f)
                            HeaderCell(text = "Phone", weight = 2.5f)
                            HeaderCell(text = "Rating", weight = 1.5f)
                        }
                        Divider()
                    }

                    itemsIndexed(partnersWithRatings.sortedByDescending { it.averageRating }) { index, partnerWithRating ->
                        TableRow(
                            partner = partnerWithRating.partner,
                            averageRating = partnerWithRating.averageRating,
                            index = index,
                            partnerId = partnerWithRating.partnerId,
                            navController = navController
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(8.dp),
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
fun RowScope.DataCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(8.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
fun TableRow(
    partner: TrainingPartner,
    averageRating: Double,
    index: Int,
    partnerId: String,
    navController: NavController
) {
    val decimalFormat = DecimalFormat("#.##")
    val backgroundColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.05f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable {
                navController.navigate("map/$partnerId")
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DataCell(text = partner.name ?: "N/A", weight = 3f)
        DataCell(text = partner.type ?: "N/A", weight = 2f)
        DataCell(text = partner.phone ?: "N/A", weight = 2.5f)
        DataCell(text = decimalFormat.format(averageRating), weight = 1.5f)
    }
}

fun getRatingColor(rating: Double): Color {
    return when {
        rating > 4.5 -> Color.Green
        rating in 4.0..4.5 -> Color(0xFF4CAF50)
        rating in 3.0..3.9 -> Color.Yellow
        rating in 2.0..2.9 -> Color(0xFFFF9800)
        else -> Color.Red
    }
}