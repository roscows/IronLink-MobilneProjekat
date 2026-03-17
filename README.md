# IronLink - Mobilna aplikacija

IronLink je Android aplikacija za pronalazenje i deljenje trening aktivnosti/partnera u blizini. Korisnici mogu da se registruju, dodaju treninge na mapi, filtriraju aktivnosti, ocene aktivnosti, prate rang listu i dobijaju notifikacije kada su blizu aktivnosti.

## Sadrzaj

1. Pregled funkcionalnosti
2. Tehnologije
3. Struktura projekta
4. Glavni tokovi u aplikaciji
5. Podaci i kolekcije u Firestore
6. Dozvole (permissions)
7. Podesavanja i kljucevi
8. Pokretanje projekta
9. Firebase Cloud Functions

## 1. Pregled funkcionalnosti

- Registracija i login korisnika (Firebase Auth)
- Profil korisnika sa bodovima i slikom profila
- Upload slike profila na Cloudinary (galerija ili kamera)
- Mapa sa aktivnostima (Google Maps)
- Kreiranje aktivnosti na mapi (long-press ili trenutna lokacija)
- Filtriranje aktivnosti po nazivu, tipu, datumu i radijusu
- Detalji aktivnosti sa ocenjivanjem i prosecnom ocenom
- Lista aktivnosti sortirana po oceni
- Rang lista korisnika po bodovima
- Notifikacije kada je korisnik blizu aktivnosti (foreground service)
- Automatsko brisanje isteklih aktivnosti (Cloud Function)

## 2. Tehnologije

- Kotlin + Jetpack Compose
- Firebase Auth i Firestore
- Google Maps + Play Services Location
- Cloudinary (upload slika)
- Coil (prikaz slika u Compose)
- CameraX (kamera)
- Firebase Cloud Functions (TypeScript)

## 3. Struktura projekta

- `app/` - Android aplikacija
- `functions/` - Firebase Cloud Functions
- `gradle/`, `build.gradle.kts` - Gradle konfiguracija

Glavne klase:

- `MainActivity.kt` - ulazna tacka, navigacija i dozvole
- `AuthViewModel.kt` - login/registracija i pristup Firestore korisnicima
- `HomePage.kt` - profil korisnika
- `MapScreen.kt` - mapa, kreiranje i filtriranje aktivnosti
- `DetailsPage.kt` - detalji aktivnosti i ocenjivanje
- `ActivityListPage.kt` - tabela aktivnosti i ocena
- `LeaderboardPage.kt` - rang lista korisnika
- `NotificationService.kt` - lokacione notifikacije

## 4. Glavni tokovi u aplikaciji

1. Registracija:
   - korisnik unosi ime, email, lozinku
   - bira sliku (kamera/galerija)
   - slika se uploaduje na Cloudinary
   - korisnik se upisuje u Firestore kolekciju `users`

2. Login:
   - Firebase Auth provera
   - nakon uspeha ide na glavnu stranu (profil)

3. Kreiranje aktivnosti:
   - long-press na mapi ili FAB "Add"
   - unosi naziv, opis, tip, telefon i vreme
   - aktivnost se cuva u `training_partners`

4. Ocenjivanje:
   - korisnik moze da oceni aktivnost samo jednom
   - ocena se cuva u `rates`
   - korisnik dobija bodove za aktivnost

5. Notifikacije:
   - foreground servis prati lokaciju
   - ako je korisnik < 100m od aktivnosti, dobija notifikaciju (cooldown 10 minuta)

## 5. Podaci i kolekcije u Firestore

### `users`

- `uid` (string)
- `fullName` (string)
- `email` (string)
- `points` (int)
- `profileImageUrl` (string?)

### `training_partners`

- `name` (string)
- `description` (string)
- `type` (string)
- `phone` (string?)
- `latitude` (double)
- `longitude` (double)
- `userId` (string?)
- `dateCreated` (timestamp)
- `eventTimestamp` (timestamp?)

### `rates`

- `userId` (string)
- `partnerId` (string)
- `value` (int 1-5)

## 6. Dozvole (permissions)

Iz `AndroidManifest.xml`:

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS`
- `CAMERA`
- `READ_EXTERNAL_STORAGE`
- `INTERNET`

## 7. Podesavanja i kljucevi

U projektu su hardkodovani primeri kljuceva:

- Google Maps API key u `AndroidManifest.xml`
- Cloudinary `cloud_name`, `api_key`, `api_secret` u `MainActivity.kt`
- Cloudinary `CLOUDINARY_URL` u `AndroidManifest.xml`

Pre produkcije se preporucuje:

- zameniti kljuceve svojim vrednostima
- sacuvati kljuceve u bezbednim konfiguracijama (npr. gradle secrets ili CI)

Firebase konfiguracija:

- `app/google-services.json` vec postoji u repozitorijumu

## 8. Pokretanje projekta

1. Otvori projekat u Android Studio
2. Sync Gradle
3. Pokreni aplikaciju na emulatoru ili uredjaju

CLI build (opciono):

```
./gradlew assembleDebug
```

## 9. Firebase Cloud Functions

U `functions/` postoji funkcija:

- `deleteExpiredPartners` - radi svaki dan u 03:00 (Europe/Belgrade) i brise aktivnosti kojima je `eventTimestamp` <= sada.

Pokretanje lokalno (primer):

```
cd functions
npm install
firebase emulators:start
```

Deploy:

```
firebase deploy --only functions
```
