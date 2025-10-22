package com.example.ironlink.ui.ranking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ironlink.data.User
import com.example.ironlink.ui.common.BottomNavigationBar
import com.example.ironlink.ui.common.getCurrentRoute
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserWithPosition(
    val user: User,
    val position: Int
)

@Composable
fun LeaderboardPage(navController: NavController) {
    val firestore = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()

    var usersWithPosition by remember { mutableStateOf<List<UserWithPosition>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val usersSnapshot = firestore.collection("users")
                    .orderBy("points", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get().await()

                val fetchedUsers = usersSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.copy(uid = doc.id)
                }

                val sortedUsers = fetchedUsers.sortedByDescending { it.points }
                val userWithPositionList = mutableListOf<UserWithPosition>()
                var currentPosition = 1
                var lastPoints = -1
                var lastPosition = 0

                for (user in sortedUsers) {
                    if (user.points != lastPoints) {
                        currentPosition = lastPosition + 1
                        lastPosition = currentPosition
                    } else {
                        currentPosition = lastPosition
                    }
                    lastPoints = user.points
                    userWithPositionList.add(UserWithPosition(user = user, position = currentPosition))
                }

                usersWithPosition = userWithPositionList
            } catch (e: Exception) {
                errorMessage = "Failed to load leaderboard!"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController, getCurrentRoute(navController)) }
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Position", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Username", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Points", style = MaterialTheme.typography.titleMedium)
                        }
                        Divider()
                    }
                    items(usersWithPosition) { userWithPos ->
                        LeaderboardItem(user = userWithPos.user, position = userWithPos.position)
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardItem(user: User, position: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
            if (position <= 3) {
                TrophyIcon(position = position)
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }
            Text(
                text = user.fullName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        Text(
            text = user.points.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun TrophyIcon(position: Int) {
    val color = when (position) {
        1 -> Color(0xFFD4AF37)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color.Transparent
    }
    Icon(
        imageVector = Icons.Filled.EmojiEvents,
        contentDescription = "Medal for position $position",
        modifier = Modifier.size(24.dp),
        tint = color
    )
}