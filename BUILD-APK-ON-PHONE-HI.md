# Mobile se APK build karne ka tarika (GitHub Actions)

1. GitHub account banayein / sign in karein: https://github.com
2. New repository banayein. Naam `KharchaDiary-Android` rakh sakte hain. Public ya Private dono theek hain.
3. Is ZIP ke andar ke project files/folders ko repository ke root mein upload karein. `.github` folder bhi upload hona zaroori hai. ZIP file ko as-is upload na karein; pehle phone ke Files app mein Extract karein.
4. GitHub repository kholein → **Actions** tab → **Build KharchaDiary APK** workflow.
5. **Run workflow** dabayein, branch `main` select karein, phir green **Run workflow** button dabayein.
6. Build complete hone par workflow run kholein. Neeche **Artifacts** mein `KharchaDiary-APK` download karein.
7. Downloaded ZIP ko extract karein. `app-debug.apk` par tap karke install karein. Android pooche to browser/Files app ke liye “Install unknown apps” allow karein.

Agar build fail ho, Actions page ka error screenshot bhejein.

Note: APK debug-signed hoti hai, testing/personal installation ke liye. Backup feature WebView ke localStorage ka data backup karta hai; online server par rakhe records ka backup app/API ki server support par depend karta hai.
