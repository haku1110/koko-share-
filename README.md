# Koko

Android app built with Jetpack Compose, Firebase, and Google Maps.

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/koko.git
```

### 2. Create `local.properties`

Copy the example file and fill in your values:

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=/path/to/your/Android/Sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

- Get a Maps API key from the [Google Cloud Console](https://console.cloud.google.com/)
- Enable **Maps SDK for Android** for your project

### 3. Set up Firebase

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Add an Android app with package name `package.com.examp.lemyapplication`
3. Download `google-services.json` and place it in the `app/` directory
   - Use `app/google-services.json.template` as a reference for the required structure

### 4. Build & Run

Open the project in Android Studio and run the app.

## Tech Stack

- **UI**: Jetpack Compose, Material3
- **Navigation**: Navigation Compose
- **Maps**: Google Maps Compose
- **Camera**: CameraX
- **Backend**: Firebase (Firestore, Storage, Auth)
- **Local DB**: Room
