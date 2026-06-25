# Változások

## 1.37.1 (versionCode 64) — 2026-06-25

### Sürgős javítás – csengőhangok

- **Néma hiba javítva** – ha a rendszer csengő nem indult el, az ALARM/NOTIFICATION/RINGTONE presetek teljesen némaak maradtak (nincs fallback); most mindig van tartalék hang
- **Ébresztő stream** – csengőhangok `STREAM_ALARM` / `USAGE_ALARM` csatornán, audio focus kéréssel
- **Csengőhang hangerő** – Beállítások → Hangok → 25/50/75/100% léptetés + előnézet
- **Néma mód** – Beállítások → Hangok → ki-be kapcsoló (emlékeztető hangok némítása)

## 1.37.0 (versionCode 63) — 2026-06-25

### Új funkciók

- **Naptár – tiszta diktálás** – program dátuma és kezdési ideje közvetlen hanggal (nem számbillentyűzet-átmenet); offline bevitel swipe lefelével
- **Saját jegyzetek** – listázás, diktálással létrehozás, törlés, felolvasás; menü: Idő és Szervezés
- **Internet kereső → jegyzet** – találatnál swipe lefelé mentés; cikk olvasás közben is menthető jegyzetként
- **Hangos asszisztens** – „saját jegyzetek”, „új jegyzet”, „jegyzet törlése” parancsok

## 1.36.9 (versionCode 62) — 2026-06-25

### Stabilitás – rendbetétel 3. kör

- **Kamera modulok lifecycle** – szövegolvasó, helyszín figyelő/tanító, arc kamera: `postWhenAlive`, Handler törlés bezáráskor
- **Értesítő képernyők** – gyógyszer és naptár emlékeztető: időzített feladatok leállítása bezáráskor
- **Zárolt képernyő** – TTS indítás Handler cleanup
- **Könyv betöltés** – maximum 25 MB fájlméret, konkrét hibaüzenet túl nagy könyvnél
- **Git** – verziókövetés beállítva a projekthez

## 1.36.8 (versionCode 61) — 2026-06-25

### Stabilitás – teljes rendbetétel (2. kör)

- **Összeomlás napló** – `SuperDlApplication` + `crash_log.txt` a telefon belső tárhelyén
- **Adattárolók védelme** – ébresztő, időzítő, könyv, e-mail, bevásárlólista JSON sérülés esetén üresre áll (nem crashel)
- **Főmenü hálózati visszahívások** – időjárás, hírek, GPS kitekintő, navigáció, tömegközlekedés `postWhenAlive`-vel
- **Hívás képernyők** – `TelephonyCallback` (Android 12+), leállított Handler-ek bezáráskor
- **Környezeti kitekintő és pénzfelismerő** – alacsony felbontású előnézet, lifecycle-biztos UI
- **Szín/fény detektor** – OOM védelem képfeldolgozáskor

## 1.36.7 (versionCode 60) — 2026-06-25

### Stabilitás

- **Kamera modulok** – egységes alacsony felbontású előnézet (320×240), biztonságos executor leállítás minden kamera/OCR képernyőn
- **Memóriavédelem** – helyszín tanító és szövegolvasó OOM esetén leáll és hangban jelzi
- **Főmenü háttérszálak** – könyv betöltés, könyvjelző ugrás és TTS hangválasztás nem frissít UI-t bezárt képernyőn (`postWhenAlive`)
- **Új segédosztály** – `CameraStabilityHelper` közös kamera konfiguráció és leállítás

## 1.36.6 (versionCode 59) — 2026-06-25

### Javítások

- **Arc/szelfi kamera** – egyesített „Kamera és szelfi” menüpont; alacsony felbontás, késleltetett arc-felismerés, OOM védelem (nem omlik össze induláskor)
- **Gesztusok** – fel swipe: előlapi/hátoldali váltás, le swipe: kamera bemondás / videó leállítás

## 1.36.5 (versionCode 58) — 2026-06-24

### Javítások

- **Főmenü gesztusok** – eltávolítva a dupla gesztus-feldolgozás (`onTouchEvent` + `rootLayout` touch listener); swipe műveletek nem futnak kétszer
- **YouTube lejátszó** – stream betöltés közbeni gyors kilépés nem indít lejátszást bezárt képernyőn
- **Helyszín figyelő** – kamera szál biztonságos leállítása bezáráskor (memóriaszivárgás ellen)
- **Hívás DTMF** – balra swipe hangjelzés a billentyűzetből való kilépéskor (konzisztens visszajelzés)
- **Dokumentáció** – README APK útvonal és verziószám frissítve

## 1.36.4 (versionCode 57) — 2026-06-24

### Javítások

- **Pénzfelismerő** – lazább küszöbök, színellenőrzés kikapcsolva automatikus módban, stabilabb frame-feldolgozás
- **Arc/szelfi kamera** – ML Kit sorompó, biztonságosabb kamera újrakötés, lassabb képfeldolgozás (befagyás ellen)
- **Hívás DTMF** – balra swipe kilép a billentyűzetből (nem ragad be), dupla gesztus-feldolgozás megszüntetve
- **Számbevitel** – diktálás először (jobbra swipe), offline billentyűzet le swipe-pal (ébresztő, tárcsázás, időzítő, naptár, számológép, S.O.S., bevásárlólista)

## 1.36.3 (versionCode 56) — 2026-06-24

### Javítások

- **Fordítási hiba javítva** – BanknoteTorchController.kt update() függvény visszatérési értéke helyesen lett kezelve (return when).
- Build pipeline stabilizálva, debug APK generálható.

## 1.34.0 (versionCode 45) — 2026-06-23

### Hibajavítás – Bejövő hívás fogadás és elutasítás

- **Bejövő hívás képernyő** – zárolt képernyőn is megjelenik (`IncomingCallActivity`)
- **Swipe jobbra: fogadás**, **swipe balra: elutasítás** – nem a főmenü jelenik meg
- A hívó neve és száma felolvasásra kerül

### Hívás közbeni vezérlők

- **DTMF billentyűzet** – ügyfélszolgálati menük kezelése (1, 2, 3…, *, #)
- **Kihangosítás** és **mikrofon némítás** váltása hívás közben
- Swipe le: billentyűzet, swipe jobbra: kihangosítás/vezérlők

### WiFi és Bluetooth – közvetlen kapcsolás

- **WiFi és Bluetooth** közvetlenül kapcsolódnak ki/be – nem nyílik a rendszer beállítások panel
- `CHANGE_WIFI_STATE` engedély visszaállítva minden Android verzióra

### Bevásárlólista bővítés

- **Létrehozás után zárolt** – meglévő listák és tételek csak megjelölhetőek, nem szerkeszthetőek/törölhetőek
- **Árazás** – tétel létrehozásakor opcionális forint ár megadása offline számbillentyűzettel
- **Árösszesítő** – a lista végén összesítő sor a kalkulálható végösszeggel

### Offline számbillentyűzet bővítés

- **Ébresztő** időbeállítás számbillentyűzettel (nem csak diktálás)
- **Időzítő** időtartam számbillentyűzettel
- **Naptár** dátum és időpont számbillentyűzettel (ééééhhnn formátum)

---

## 1.33.0 (versionCode 44) — 2026-06-23

### C3 – Folyamatos OCR

- **Új menüpont:** Eszközök → „Folyamatos szövegolvasó”
- **Automatikus szövegváltozás-felismerés** – ha a kamera más részre mutat, felolvassa az új szöveget (nem ismétli feleslegesen ugyanazt)
- **Rész-alapú navigáció** – hosszú szövegnél le swipe: következő rész, fel swipe: ismétlés
- **Szünet / folytatás** – jobbra swipe: folyamatos figyelés ki-be
- **TTS-sor** – beszéd közben várakozik, utána folytatja az új szöveget
- **Hangos asszisztens:** „folyamatos szövegolvasó”, „folyamatos OCR”

---

## 1.30.3 (versionCode 40) — 2026-06-22

### Audit javítások – stabilitás és hibatűrés

- **PatrolAnnouncer üzenetsor** – GPS, értesítés és időzítő bemondások nem esnek ki egymásból (max. 12 sorban várakoznak)
- **Kamera memória védelem** – pénzfelismerő és környezeti kitekintő OutOfMemory esetén leállítja a beolvasást és hangban jelzi
- **Gyógyszer tároló JSON séma** – sérült adat automatikus helyreállítás, sémaverzió és migráció
- **Diktafon hibajelzés** – felvétel-szál és mentési hibák konkrét TTS üzenettel érkeznek
- **Release minify** – R8/ProGuard bekapcsolva release buildben (keep szabályok bővítve)

---

## 1.30.2 (versionCode 39) — 2026-06-22

### Javítás – Befagyás ellen: beep-beep, aztán menü

- **Azonnali beep-beep** érkezik először (könnyű, ébresztő csatornán) – még lassú rendszeren is hallható
- **~1,5 mp múlva** indul a teljes riasztó (hang + swipe menü + TTS) – nem egyszerre terheli a telefont
- Program és gyógyszer emlékeztetőnél is érvényes

---

## 1.30.1 (versionCode 38) — 2026-06-22

### Gyógyszer emlékeztető – 1 órás halasztás

- **Swipe menü** gyógyszer riasztáskor: „Emlékeztetés 1 óra múlva” / „Bevétel megerősítése”
- ⬆⬇ választás, ➡ végrehajtás, ⬅ bezárás (hang leáll, bevétel nem rögzítve)
- Halasztás után pontosan 1 órával újra jelez

---

## 1.30.0 (versionCode 37) — 2026-06-22

### Javítás – Program és emlékeztető értesítések

- **Program emlékeztető most tényleg megszólal** – dedikált `CalendarAlertActivity` zárolt képernyőn is (csengőhang + rezgés + TTS)
- **Swipe menü program idején** – fel-le választás: „Emlékeztetés 1 óra múlva” / „Megjelölés teljesítettként”, jobbra megerősítés, balra bezárás
- **Gyógyszer emlékeztető megbízhatóbb** – wake lock, háttérből is induló riasztó ablak és hang
- **Pontos ébresztő fallback** – engedély hiányában is ütemez (kevésbé pontos), figyelmeztetés mentéskor
- **PIN zárolás nem nyeli el** a program emlékeztetőt

### Új – Hangbeállítások (Beállítások → Hangok)

- Program emlékeztető, gyógyszer, ébresztő, SMS, e-mail és egyéb értesítés hangja külön választható
- 6 előre definiált hang (ébresztő, csengő, értesítés, csengő-hármas, lágy csengő, dupla síp)
- Jobbra swipe: beállítás + azonnali előnézet

---

## 1.29.1 (versionCode 36) — 2026-06-21

### Javítás – WiFi menüpont és launcher váltás

- **WiFi navigáció** – fel-le lapozáskor külön, biztonságos állapotfelolvasás (nem omlik össze, nem ugrik launcher váltásra)
- **WiFi kapcsoló** – Android 10+ eszközön a rendszer gyorsbeállítások panel nyílik (nem tiltott API hívás)
- **Null-biztos WiFi kezelés** – nincs összeomlás hiányzó WiFi szolgáltatás esetén
- **Launcher váltás védelem** – menünavigáció törli a félkész kilépés-megerősítést
- **ACCESS_WIFI_STATE** engedély hozzáadva

---

## 1.29.0 (versionCode 35) — 2026-06-21

### Javítás – Beállítások menü navigáció