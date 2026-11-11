# Reminder Motor

Aplikasi Android pribadi untuk mengingatkan servis motor berdasarkan kilometer dan waktu. Tampilannya berupa grid ikon sparepart: merah saat waktunya ganti, hijau setelah diganti.

Status: **tahap 1, kerangka.** Aplikasi baru menampilkan layar sambutan.

## Cara mendapatkan APK

1. Buka tab **Actions** di repo ini.
2. Klik proses **Build APK** paling atas yang bertanda centang hijau.
3. Di bagian **Artifacts**, unduh **reminder-motor-apk** (berupa file .zip).
4. Ekstrak zip tersebut, lalu salin `app-debug.apk` ke HP dan instal. Izinkan "instal dari sumber tidak dikenal" kalau diminta.

APK dirakit otomatis setiap ada perubahan di branch `main`. Bisa juga dijalankan manual lewat tombol **Run workflow**.

## Teknologi

Kotlin, Jetpack Compose (Material 3), minimal Android 8.0. Bahasa aplikasi: Indonesia dan English, mengikuti bahasa HP.
