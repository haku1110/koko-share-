package com.example.myapplication.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMapScreen(
    viewModel: MainViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToPhotoList: () -> Unit
) {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState()
    val uiSettings by remember { mutableStateOf(MapUiSettings(zoomControlsEnabled = false)) }

    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val searchRadius by viewModel.searchRadius.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val mapProperties by remember(locationPermissionGranted) {
        mutableStateOf(MapProperties(isMyLocationEnabled = locationPermissionGranted))
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locationPermissionGranted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    viewModel.currentUserLocation = currentLatLng
                    if (selectedLocation == null) {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    viewModel.currentUserLocation = currentLatLng
                    if (selectedLocation == null) {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedLocation) {
        selectedLocation?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 17f))
            viewModel.clearSelectedLocation()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("koko") },
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "ログアウト"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = uiSettings,
                properties = mapProperties
            ) {
                // 現在地を読む（mutableStateOf なので再コンポーズに反応する）
                val userLocation = viewModel.currentUserLocation

                viewModel.photoPosts.forEach { post ->
                    // key() により、このピンのデータが変わった時だけ再描画される（OOM最適化）
                    key(post.id) {
                        val isNearby = userLocation != null && run {
                            val results = FloatArray(1)
                            Location.distanceBetween(
                                userLocation.latitude, userLocation.longitude,
                                post.location.latitude, post.location.longitude,
                                results
                            )
                            results[0] <= searchRadius
                        }

                        // 距離に応じて「ポコッ」と弾むアニメーション
                        val pinSize by animateDpAsState(
                            targetValue = if (isNearby) 64.dp else 40.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "pinSize_${post.id}"
                        )

                        MarkerComposable(
                            state = rememberMarkerState(position = post.location),
                            title = "${post.userName}さんの投稿",
                            snippet = viewModel.formatTimestamp(post.timestamp)
                        ) {
                            PhotoMarkerContent(
                                imageUrl = post.imageUrl,
                                size = pinSize,
                                isNearby = isNearby
                            )
                        }
                    }
                }
            }

            // 半径セレクター（右上）
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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

            // 撮影ボタン（中央下）
            FloatingActionButton(
                onClick = onNavigateToCamera,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "撮影",
                    modifier = Modifier.size(36.dp)
                )
            }

            // 写真一覧ボタン（右下）
            FloatingActionButton(
                onClick = onNavigateToPhotoList,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "写真一覧"
                )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("ログアウト") },
            text = { Text("ログアウトしますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut()
                    }
                ) {
                    Text("ログアウト")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

/**
 * 地図上に表示する写真ピン。
 * Coil の .size() でリサイズ読み込みを強制し、OOM を防ぐ。
 * 範囲内ピンには枠線を付けて視覚的に強調する。
 */
@Composable
private fun PhotoMarkerContent(
    imageUrl: String,
    size: Dp,
    isNearby: Boolean
) {
    val context = LocalContext.current
    // dp → px 変換（Coil の size 指定に使う）
    val sizePx = with(LocalDensity.current) { size.roundToPx() }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (isNearby)
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else
                    Modifier
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(sizePx)          // 表示サイズに合わせてリサイズ（OOM対策の核心）
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}