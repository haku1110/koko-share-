package com.example.myapplication.ui // ※ご自身のパッケージ名に合わせてください

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@android.annotation.SuppressLint("MissingPermission")
@Composable
fun MainMapScreen(
    viewModel: MainViewModel, // ★追加: ViewModelを受け取る
    onNavigateToCamera: () -> Unit,
    onNavigateToPhotoList: () -> Unit
) {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState()
    val uiSettings by remember { mutableStateOf(MapUiSettings(zoomControlsEnabled = false)) }

    // ★追加: ViewModelから写真のリストを監視する
    val photoPosts = viewModel.photoPosts

    // 権限状態の管理
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 地図のプロパティ（現在地表示を有効化）
    val mapProperties by remember(locationPermissionGranted) {
        mutableStateOf(MapProperties(isMyLocationEnabled = locationPermissionGranted))
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // 権限リクエスト用ランチャー
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locationPermissionGranted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    // ★追加: 現在地をViewModelに保存（一覧画面の距離計算で使うため）
                    viewModel.currentUserLocation = currentLatLng
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        }
    }

    // 初回起動時の処理
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
                    // ★追加: 現在地をViewModelに保存
                    viewModel.currentUserLocation = currentLatLng
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        }
    }

    Scaffold { innerPadding ->
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
                // ★追加: ここでViewModelに保存された写真の数だけピン（マーカー）をループで描画する
                photoPosts.forEach { post ->
                    Marker(
                        state = MarkerState(position = post.location),
                        title = "写真",
                        snippet = "ここで撮影されました"
                    )
                }
            }

            // 中央下部の撮影ボタン (+)
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

            // 右下部の閲覧ボタン
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
}