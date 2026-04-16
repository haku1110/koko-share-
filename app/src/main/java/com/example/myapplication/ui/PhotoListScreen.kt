package com.example.myapplication.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoListScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    
    var photoToDelete by remember { mutableStateOf<PhotoPost?>(null) }

    LaunchedEffect(Unit) {
        viewModel.deleteResult.collectLatest { result ->
            if (result is DeleteResult.Failure) {
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            } else if (result is DeleteResult.Success) {
                Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("写真一覧") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // タブ (固定)
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("周りの写真") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("自分の写真") }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> NearbyPhotos(
                        viewModel = viewModel, 
                        onBack = onBack,
                        onDeleteClick = { photoToDelete = it }
                    )
                    1 -> MyPhotos(
                        viewModel = viewModel, 
                        onBack = onBack,
                        onDeleteClick = { photoToDelete = it }
                    )
                }
            }
        }
    }

    // 削除確認ダイアログ
    photoToDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("写真を削除") },
            text = { Text("この写真を削除してもよろしいですか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePhoto(photo)
                        photoToDelete = null
                    }
                ) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyPhotos(
    viewModel: MainViewModel, 
    onBack: () -> Unit,
    onDeleteClick: (PhotoPost) -> Unit
) {
    val userLocation = viewModel.currentUserLocation
    val posts = viewModel.photoPosts
    val currentUser = viewModel.currentUserProfile
    val searchRadius by viewModel.searchRadius.collectAsState()

    val nearbyPosts = remember(posts, userLocation, searchRadius, currentUser) {
        if (userLocation == null || currentUser == null) emptyList()
        else posts.filter { 
            it.userId != currentUser.userId && 
            viewModel.calculateDistance(userLocation, it.location) <= searchRadius 
        }
    }

    if (userLocation == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("位置情報を取得中です...")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // スクロール可能なアイテムとして並べ替えUIを追加
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 並べ替え選択
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("並べ替え:", style = MaterialTheme.typography.bodySmall)
                        SortOrder.entries.forEach { order ->
                            FilterChip(
                                selected = viewModel.currentSortOrder == order,
                                onClick = { viewModel.currentSortOrder = order },
                                label = { Text(order.label) }
                            )
                        }
                    }

                    // 検索範囲選択
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("検索範囲:", style = MaterialTheme.typography.bodySmall)
                        FilterChip(
                            selected = searchRadius == 50.0,
                            onClick = { viewModel.updateSearchRadius(50.0) },
                            label = { Text("50m") }
                        )
                        FilterChip(
                            selected = searchRadius == 100.0,
                            onClick = { viewModel.updateSearchRadius(100.0) },
                            label = { Text("100m") }
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = DividerDefaults.color.copy(alpha = 0.5f)
                    )
                }
            }

            if (nearbyPosts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp), 
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${searchRadius.toInt()}m以内に他人の写真はありません")
                    }
                }
            } else {
                items(nearbyPosts, key = { it.id }) { post ->
                    PhotoCard(
                        modifier = Modifier.animateItem(),
                        post = post,
                        viewModel = viewModel,
                        currentUserId = currentUser?.userId,
                        distance = viewModel.calculateDistance(userLocation, post.location),
                        onDeleteClick = { onDeleteClick(post) },
                        onLocationClick = {
                            viewModel.focusOnLocation(post.location)
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPhotos(
    viewModel: MainViewModel, 
    onBack: () -> Unit,
    onDeleteClick: (PhotoPost) -> Unit
) {
    val posts = viewModel.photoPosts
    val currentUser = viewModel.currentUserProfile
    val userLocation = viewModel.currentUserLocation

    val myPosts = remember(posts, currentUser) {
        if (currentUser == null) emptyList()
        else posts.filter { it.userId == currentUser.userId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // スクロール可能なアイテムとして並べ替えUIを追加
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("並べ替え:", style = MaterialTheme.typography.bodySmall)
                    SortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = viewModel.currentSortOrder == order,
                            onClick = { viewModel.currentSortOrder = order },
                            label = { Text(order.label) }
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = DividerDefaults.color.copy(alpha = 0.5f)
                )
            }
        }

        if (myPosts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("投稿した写真はありません")
                }
            }
        } else {
            items(myPosts, key = { it.id }) { post ->
                PhotoCard(
                    modifier = Modifier.animateItem(),
                    post = post,
                    viewModel = viewModel,
                    currentUserId = currentUser?.userId,
                    distance = userLocation?.let { viewModel.calculateDistance(it, post.location) },
                    onDeleteClick = { onDeleteClick(post) },
                    onLocationClick = {
                        viewModel.focusOnLocation(post.location)
                        onBack()
                    }
                )
            }
        }
    }
}

@Composable
fun PhotoCard(
    modifier: Modifier = Modifier,
    post: PhotoPost,
    viewModel: MainViewModel,
    currentUserId: String?,
    distance: Float?,
    onDeleteClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(post.imageUrl)
                        .size(480)   // リスト表示用（OOM対策）
                        .bitmapConfig(android.graphics.Bitmap.Config.RGB_565) // メモリ半減
                        .crossfade(true)
                        .build(),
                    placeholder = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_gallery),
                    error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_gallery),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                
                if (post.userId == currentUserId) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.small
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "削除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = post.locationName,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = viewModel.formatTimestamp(post.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "投稿者: ${post.userName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        distance?.let {
                            val distanceText = if (it < 1000) {
                                "${it.toInt()}m"
                            } else {
                                String.format("%.1fkm", it / 1000)
                            }
                            Text(
                                text = "距離: $distanceText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TextButton(onClick = onLocationClick) {
                        Text("地図で見る")
                    }
                }
            }
        }
    }
}
