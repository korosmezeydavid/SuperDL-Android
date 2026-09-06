# SuperDL 1.63.4

Kis kiadás, egyetlen hibával — de az a hiba hazudott nekem, és majdnem
elhitte velem, hogy egy tesztelő telefonján nincs beszédmotor.

## „BESZÉDMOTOROK A RENDSZERBEN: EGY SEM TALÁLHATÓ"

Ezt írta az 1.63.2 új diagnosztikája egy Xiaomi Redmi Note 10 Pro-ról.
Ugyanarról a telefonról, amin fut a TalkBack — vagyis amin **biztosan van**
beszédmotor, különben a TalkBack sem szólalna meg.

Az ok egy elgépelés, aminek két helye volt:

```
rossz:  "android.speech.tts.engine.INTENT_ACTION_TTS_SERVICE"   ← a konstans NEVE
helyes: "android.intent.action.TTS_SERVICE"                      ← a konstans ÉRTÉKE
```

A `TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE` konstans **neve** és
**értéke** nem ugyanaz. Két helyen a nevét írtuk be szövegként:

- **`SetupDiagnostics`** — ezért a hibajelentés MINDEN készüléken azt írta,
  hogy nincs beszédmotor. Nem csak Gézáén; mindenkién, mindig.
- **`AndroidManifest` `<queries>` blokkja** — és ez a súlyosabb. Android 11
  óta itt kell megadni, mely alkalmazásokat láthatja a program. Nem létező
  szándékra hivatkoztunk, tehát ez a bejegyzés **semmire nem adott
  láthatóságot**. A beszédmotor-választó eddig csak azért működött, mert egy
  másik, tágabb engedély kisegítette.

A többi hívási hely (`TtsEngineHelper`, `BookTtsPrefs`) végig helyesen a
konstanst használta — ezért nem tűnt fel.

## AMI EBBŐL TANULSÁG

Ez a mai nap negyedik olyan hibája, ami ugyanabból állt: **rossz kérdést
tettünk fel, és az üres választ tényként olvastuk.**

| a rossz kérdés | mit hittünk | az igazság |
|---|---|---|
| `dumpsys device_policy` → `Password quality` | nincs képernyőzár | van PIN kód |
| `adb shell ls` a program mappájában | nincs ott fájl | nincs jogunk megnézni |
| `run-as ls files/zarhang` | nincsenek klipek | rossz tárolót néztünk |
| `Intent("…INTENT_ACTION_TTS_SERVICE")` | nincs beszédmotor | nincs ilyen szándék |

Mind a négy ugyanaz: **„nem látom" nem azonos azzal, hogy „nincs ott".**

## LETÖLTÉS

https://github.com/korosmezeydavid/SuperDL-Android/releases/latest/download/SuperDL.apk

Az 1.63.3 minden javítása benne van: a Direct Boot összeomlás, a varázsló
néma kijárata, a telefon-szerepkör felismerése.

HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!
