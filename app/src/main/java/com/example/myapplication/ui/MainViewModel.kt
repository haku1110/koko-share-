package com.example.myapplication.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PhotoPost(
    val id: String = "",
    val imageUrl: String = "",
    val location: LatLng = LatLng(0.0, 0.0),
    val locationName: String = "",
    val timestamp: Long = 0L,
    val userId: String = "",
    val userName: String = ""
)

data class UserProfile(
    val userId: String = "",
    val userName: String = ""
)

enum class SortOrder(val label: String) {
    CLOSEST("近い順"), 
    NEWEST("新しい順"), 
    OLDEST("古い順")
}

sealed class DeleteResult {
    object Success : DeleteResult()
    data class Failure(val message: String) : DeleteResult()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _photoPosts = mutableStateListOf<PhotoPost>()
    
    // 並べ替え済みのリストを外部に公開
    val photoPosts by derivedStateOf {
        when (currentSortOrder) {
            SortOrder.NEWEST -> _photoPosts.sortedByDescending { it.timestamp }
            SortOrder.OLDEST -> _photoPosts.sortedBy { it.timestamp }
            SortOrder.CLOSEST -> {
                val location = currentUserLocation
                if (location != null) {
                    _photoPosts.sortedBy { calculateDistance(location, it.location) }
                } else {
                    _photoPosts.toList()
                }
            }
        }
    }

    var currentUserLocation by mutableStateOf<LatLng?>(null)
    
    var currentSortOrder by mutableStateOf(SortOrder.NEWEST) // デフォルトは新しい順
    var currentRadius by mutableIntStateOf(50)

    // New search radius for "Nearby" tab (in meters)
    private val _searchRadius = MutableStateFlow(50.0)
    val searchRadius = _searchRadius.asStateFlow()

    fun updateSearchRadius(radius: Double) {
        _searchRadius.value = radius
    }

    // Auth states
    var currentUserProfile by mutableStateOf<UserProfile?>(null)
    var isAuthLoading by mutableStateOf(true)

    // Map focus state
    private val _selectedLocation = MutableStateFlow<LatLng?>(null)
    val selectedLocation = _selectedLocation.asStateFlow()

    // Delete status
    private val _deleteResult = MutableSharedFlow<DeleteResult>()
    val deleteResult = _deleteResult.asSharedFlow()

    private val db = FirebaseFirestore.getInstance()
    // google-services.json の storage_bucket (koko-872e0.firebasestorage.app) を明示的に指定
    private val storage = FirebaseStorage.getInstance("gs://koko-872e0.firebasestorage.app")
    private val auth = FirebaseAuth.getInstance()
    private val geocoder = Geocoder(application, Locale.getDefault())
    private val credentialManager = CredentialManager.create(application)

    init {
        checkAuthStatus()
        observePhotoPosts()
    }

    private fun checkAuthStatus() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            db.collection("users").document(firebaseUser.uid).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("userName")
                    if (name != null) {
                        currentUserProfile = UserProfile(firebaseUser.uid, name)
                    } else {
                        // ユーザー名が未登録の場合はGoogleの表示名を使用
                        val profile = UserProfile(firebaseUser.uid, firebaseUser.displayName ?: "匿名ユーザー")
                        currentUserProfile = profile
                        db.collection("users").document(firebaseUser.uid).set(profile)
                    }
                    isAuthLoading = false
                }
                .addOnFailureListener {
                    isAuthLoading = false
                }
        } else {
            isAuthLoading = false
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    val profile = UserProfile(user.uid, user.displayName ?: "Googleユーザー")
                    db.collection("users").document(user.uid).set(profile)
                        .addOnSuccessListener {
                            currentUserProfile = profile
                            onSuccess()
                        }
                }
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                auth.signOut()
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                currentUserProfile = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error during sign out", e)
            }
        }
    }

    private fun observePhotoPosts() {
        db.collection("photo_posts")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                if (snapshot != null) {
                    val posts = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val lat = data["latitude"] as? Double ?: 0.0
                        val lng = data["longitude"] as? Double ?: 0.0
                        PhotoPost(
                            id = doc.id,
                            imageUrl = data["imageUrl"] as? String ?: "",
                            location = LatLng(lat, lng),
                            locationName = data["locationName"] as? String ?: "",
                            timestamp = data["timestamp"] as? Long ?: 0L,
                            userId = data["userId"] as? String ?: "",
                            userName = data["userName"] as? String ?: "匿名"
                        )
                    }
                    _photoPosts.clear()
                    _photoPosts.addAll(posts)
                }
            }
    }

    private fun compressImageToBytes(uri: Uri, maxSize: Int = 1280): ByteArray {
        val context = getApplication<Application>()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        var sampleSize = 1
        var w = options.outWidth
        var h = options.outHeight
        while (w > maxSize || h > maxSize) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return ByteArray(0)

        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    fun addPhotoPost(uri: Uri, location: LatLng) {
        val userProfile = currentUserProfile ?: return
        // 保存パスの一貫性を保証 (明示的に指定したバケットの photos/ フォルダ)
        val fileName = "${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("photos/$fileName")

        viewModelScope.launch {
            try {
                Log.d("KokoDebug", "Starting upload to bucket: ${storage.reference.bucket}")

                // 1. 圧縮してからアップロード（最大1280px, JPEG 85%）
                val compressedBytes = withContext(Dispatchers.IO) { compressImageToBytes(uri) }
                storageRef.putBytes(compressedBytes).await()
                Log.d("KokoDebug", "Upload Success! Path: ${storageRef.path}")

                // 2. 最新の「トークン付き公開URL」を確実に取得 (404エラー防止)
                val downloadUrlString = storageRef.downloadUrl.await().toString()
                Log.d("KokoDebug", "Generated Tokenized URL: $downloadUrlString")
                
                // 3. 住所情報の取得
                val locationName = getAddressFromLatLng(location)
                val timestamp = System.currentTimeMillis()
                
                val postData = hashMapOf(
                    "imageUrl" to downloadUrlString,
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "locationName" to locationName,
                    "timestamp" to timestamp,
                    "userId" to userProfile.userId,
                    "userName" to userProfile.userName
                )
                
                // 4. Firestoreへの保存完了を待機
                db.collection("photo_posts").add(postData).await()
                Log.d("KokoDebug", "Firestore post added successfully with URL: $downloadUrlString")

            } catch (e: Exception) {
                Log.e("KokoDebug", "CRITICAL: Photo post failed: ${e.message}", e)
            }
        }
    }

    fun deletePhoto(photo: PhotoPost) {
        viewModelScope.launch {
            try {
                // 明示的に初期化したstorageインスタンスからリファレンスを取得
                storage.getReferenceFromUrl(photo.imageUrl).delete().await()
                db.collection("photo_posts").document(photo.id).delete().await()
                _deleteResult.emit(DeleteResult.Success)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error deleting photo", e)
                _deleteResult.emit(DeleteResult.Failure(e.message ?: "削除に失敗しました"))
            }
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private suspend fun getAddressFromLatLng(latLng: LatLng): String = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val admin = addresses[0].adminArea ?: ""
                val locality = addresses[0].locality ?: ""
                return@withContext "$admin$locality".ifEmpty { "不明な場所" }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Reverse geocoding failed", e)
        }
        "不明な場所"
    }

    fun calculateDistance(start: LatLng, end: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude, start.longitude,
            end.latitude, end.longitude,
            results
        )
        return results[0]
    }

    fun focusOnLocation(location: LatLng) {
        _selectedLocation.value = location
    }

    fun clearSelectedLocation() {
        _selectedLocation.value = null
    }
}
