## SuperDL Android 1.87.0

Az 1.86.0 óta elkészült funkciók és javítások:

- **Internet-teszt:** kapcsolat- és sebességmérés, részletes, felolvasható eredmény és mérési előzmények. Az eredmény a hálózat pillanatnyi állapotát mutatja.
- **Hívásszűrés:** a feketelistás számok kiszűrt hívásai nem jelennek meg a SuperDL hívásnaplójában. A rendszer telefonalkalmazásának saját naplóját és értesítéseit a SuperDL nem tudja minden készüléken befolyásolni.
- **Szám azonosítása:** a hívásnaplóban egy ismeretlen számról a készüléken tárolt, nyilvános OpenStreetMap-adatok alapján kérhető lehetséges név és település. Az adat téves vagy elavult lehet; az eredmény nem bizonyítja a hívó személyazonosságát.
- **Megbízhatóság:** az Akciós újság érthetőbben jelzi, ha egy bolt 403/429 válasszal elutasítja a lekérést. A Beszélő óra és az akcióletöltés több váratlan hibát kezel úgy, hogy a többi funkció tovább működhessen.

A funkciók hangos visszajelzéssel és a SuperDL megszokott gesztusaival használhatók. Ha egy művelet a készülékeden másként viselkedik, a Súgó menüből küldhető hibajelentés segít a javításban.

Letöltés: [SuperDL.apk](https://github.com/korosmezeydavid/SuperDL-Android/releases/latest/download/SuperDL.apk).

Az OpenStreetMap-eredetű telefonindex forrás- és licencadatai a nyilvános tároló `tools/osm_telefon_index_LICENC.txt` fájljában szerepelnek.
