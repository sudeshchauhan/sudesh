# KharchaDiary Android project

## Internal-storage backup and restore
- Tap **Backup Data** to create a timestamped JSON backup automatically.
- On Android 10 and newer, the file is saved to the phone's shared internal storage at `Internal storage/Download/KharchaDiary/`. It is visible in the Files app and can be copied elsewhere.
- On Android 7-9, it is saved in the app's private internal files folder (`Android/data/com.kharchadiary.app/files` equivalent app-private storage).
- Tap **Restore Data**, choose a saved backup from the list, and confirm. The app restores the website's localStorage and reloads.

## Important scope note
This Android app is a WebView wrapper around a hosted website. The backup includes the website origin's browser `localStorage`. It does not export server-side/database records, remote account data, IndexedDB, or cookies. Those require a backup/export API in the hosted KharchaDiary application.

## Build APK
Open this project in Android Studio and choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The debug APK is normally created at `app/build/outputs/apk/debug/app-debug.apk`.
