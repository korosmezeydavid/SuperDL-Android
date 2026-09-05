# SuperDL Android 1.62.1

Az 1.62.0-ban került bele a **hibajelentés a beállítás varázslóból**. Az első
éles jelentés néhány órán belül megérkezett — és rögtön **két hibát fogott meg**,
amiket enélkül nem találtunk volna meg. Pontosan ezért készült.

## 1. Az „Alapértelmezett asszisztens" nem volt megadható a varázslóból

A jelentés szerint a tétel **két próbálkozás után is hiányzott**, miközben a
rendszer képernyője elvileg megnyitható lett volna.

Az ok: az Android a szerepköröket egy „kérhető" jelzővel írja le, és amelyiknél
ez nincs bekapcsolva, ott a kérő ablak **meg sem jelenik** — azonnal visszatér,
mintha a felhasználó elutasította volna. Az asszisztens ilyen szerepkör: a
rendszer kizárólag a beállítás-oldalán engedi átállítani.

Mostantól a varázsló egyenesen a **digitális asszisztens beállítás-oldalára**
visz, és kimondja, hogy itt nem lesz felugró kérdés, a listából kell
kiválasztani a Super DL-t.

## 2. A program tévesen hitte megadottnak az értesítés-hozzáférést

Aki a fejlesztői és a kiadási változatot **egyszerre** használja, annál a
kiadási változat úgy látta, hogy megvan az értesítés-hozzáférése — holott az a
fejlesztői változaté volt. Az ok egy túl megengedő szövegkeresés volt: a
fejlesztői csomagnév a kiadási névvel kezdődik. Ugyanez a hiba a Mátrix
billentyűzet kiválasztásának ellenőrzésénél is megvolt.

Mindkettő mostantól pontosan hasonlít.

## 3. A jelentés is okosabb lett

Ha a telefonon **mindkét változat** fent van és be van kapcsolva, a jelentés
ezt külön kiírja. Ez ugyanis valódi ütközés: két képernyőolvasó egymásra
beszél. Ilyenkor az egyiket el kell távolítani.

---

Ha a beállítás varázslóban bármi elakad, a lista legvégén van a
**„Nem megy tovább? Hibajelentés küldése"** tétel. Egy jobbra söprés, és
pontosan látjuk, mi nem engedett.

HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!
