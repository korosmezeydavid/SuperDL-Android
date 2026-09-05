# SuperDL 1.63.0

## ÚJ: BESZÉDTÉMA — a telefon mostantól nem csak közöl, hanem szól hozzád

A SuperDL eddig minden eseményt ugyanazzal a gépi hanggal mondott be.
Mostantól hat eseményhez tartozhat FELVETT emberi hang: merülő akkumulátor,
teljes töltöttség, töltő bedugva, töltő kihúzva alacsony töltöttségnél,
reggeli és esti köszönés.

**Elena a beépített alaptéma, és be van kapcsolva.** A hat hang a
programcsomagban utazik, az első indításnál a helyére kerül. Nem kell
letölteni és nem kell beállítani semmit.

Elena egyben a beszédtémák ALAPRÉTEGE is: ha egy letöltött téma nem
tartalmaz hangot valamelyik eseményhez, azt Elena mondja — a telefon nem
vált vissza a gépi hangra a téma közepén.

**A szám mindig elhangzik.** A felvett hang a hangulat, nem az információ:
a töltöttség százaléka a klip után külön kimondásra kerül. Ez akkor is így
van, ha valaki tréfás témát tesz fel.

Beállítások → Hangok → Beszédtéma:

- be- és kikapcsolás, próba, állapot
- téma választása a telepítettek közül
- eseményenkénti ki-be kapcsolás
- reggeli és esti köszönés időpontja
- napi keret: hányszor szólalhat meg naponta (0, 3, 5, 8, 12, 20)
- **az őrség első jelzésének szintje állítható**: 5, 10, 15, 20, 25, 30, 40
  vagy 50 százalék

## ÚJ: SAJÁT BESZÉDTÉMA — a telefonon, számítógép nélkül

Beállítások → Hangok → Beszédtéma → Saját téma felvétele.

A program először a téma NEVÉT kéri, csak utána vesz fel: a korábbi témák
megmaradnak, egyszerre több sajátod is lehet. Eseményenként fel lehet venni,
meghallgatni, újravenni vagy kihagyni.

Aki nem bemondást akar, hanem hanghatást vagy egy előre felvett mondatot:
a felvétel helyett le is tallózhat egy meglévő hangfájlt a telefonról.

Kész témát meg lehet osztani egyetlen fájlban (levélben, üzenetben,
bárhogyan), és a kapott fájlt telepíteni lehet.

## JAVÍTVA: a beállítás varázsló átvizsgálása

Az 1.62.1 után tételesen átnéztük a varázslót. Három hiba került elő:

- **A hibajelentés elvetése kidobott a varázslóból.** Aki a varázslóból
  nyitotta meg a jelentést, és meggondolta magát, a főmenüben találta magát
  — pont abból a listából kiesve, amit még nem fejezett be. Első indításnál
  ez a varázsló kapuját is megkerülte. Mostantól az elvetés oda tér vissza,
  ahonnan a jelentés indult.
- **Sikeres megadás után a lista elejére ugrott vissza.** A megadott tétel
  kikerül a hiánylistából, és a program ilyenkor elvesztette a helyet: aki a
  tizedik tételt adta meg, az elsőnél találta magát. Mostantól ott folytatja,
  ahol abbahagytad.
- **Az asszisztens-szerepkör felismerése két ponton is hibás volt.** Az egyik
  ugyanaz a hiba, ami az értesítés-hozzáférésnél kijött: a fejlesztői
  csomagnév a kiadásival kezdődik, ezért egy részszöveg-keresés a másik
  változat bejegyzésére is igazat adott. A másik fordítva tévedett: a
  rendszer komponensnevet tárol, a program viszont csomagnevet hasonlított
  hozzá, így soha nem talált egyezést.

## LETÖLTÉS

https://github.com/korosmezeydavid/SuperDL-Android/releases/latest/download/SuperDL.apk

HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!
