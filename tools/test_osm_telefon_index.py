#!/usr/bin/env python3
"""Az OSM telefonszám-index építőjének tesztjei.

Futtatás: python3 -m unittest tools/test_osm_telefon_index.py
(vagy a tools mappából: python3 test_osm_telefon_index.py)
"""

import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import osm_telefon_index as idx  # noqa: E402


class NormalizeTest(unittest.TestCase):
    def test_hungarian_formats(self):
        cases = {
            "+36 1 234 5678": ["+3612345678"],
            "06-30/123-4567": ["+36301234567"],
            "(06 1) 234-5678": ["+3612345678"],
            "+36-20-123-4567": ["+36201234567"],
            "06 62 123 456": ["+3662123456"],
            "0036 70 123 4567": ["+36701234567"],
            "+36 (0)1 234 5678": ["+3612345678"],
            "30 123 4567": ["+36301234567"],
            "+36 80 123 456": ["+3680123456"],
        }
        for raw, expected in cases.items():
            self.assertEqual(idx.normalize_numbers(raw), expected, raw)

    def test_several_numbers_in_one_value(self):
        self.assertEqual(
            idx.normalize_numbers("+36 1 234 5678; +36 30 123 4567, 06-62/123-456"),
            ["+3612345678", "+36301234567", "+3662123456"],
        )
        # Perjellel elválasztott két teljes szám.
        self.assertEqual(
            idx.normalize_numbers("+36 1 234 5678 / +36 30 123 4567"),
            ["+3612345678", "+36301234567"],
        )
        # Ismétlődő szám csak egyszer.
        self.assertEqual(idx.normalize_numbers("+3612345678;06 1 234 5678"), ["+3612345678"])

    def test_extension_is_dropped(self):
        self.assertEqual(idx.normalize_numbers("+36 1 234 5678 ext. 12"), ["+3612345678"])
        self.assertEqual(idx.normalize_numbers("06 1 234 5678 mellék 3"), ["+3612345678"])

    def test_invalid_is_dropped(self):
        for raw in ["", "   ", "123", "+36 1 234", "06 30 123 45", "+36 30 123 45678", "hívjon", "0123"]:
            self.assertEqual(idx.normalize_numbers(raw), [], raw)

    def test_foreign_numbers_are_kept(self):
        self.assertEqual(idx.normalize_numbers("+43 664 1234567"), ["+436641234567"])
        self.assertEqual(idx.normalize_numbers("+43 (0)1 234 5678"), ["+4312345678"])
        self.assertEqual(idx.normalize_numbers("0043 1 234 5678"), ["+4312345678"])
        # Túl rövid külföldi szám nem kell.
        self.assertEqual(idx.normalize_numbers("+43 123"), [])


class TagTest(unittest.TestCase):
    def test_type_mapping(self):
        self.assertEqual(idx.type_of({"amenity": "pharmacy"}), "gyógyszertár")
        self.assertEqual(idx.type_of({"shop": "car_repair"}), "autószerviz")
        self.assertEqual(idx.type_of({"amenity": "townhall"}), "polgármesteri hivatal")
        # Ismeretlen érték: nyersen, aláhúzás nélkül.
        self.assertEqual(idx.type_of({"shop": "sewing_machine"}), "sewing machine")
        # Az amenity előbbre való, mint a shop.
        self.assertEqual(idx.type_of({"shop": "bakery", "amenity": "cafe"}), "kávézó")
        self.assertEqual(idx.type_of({}), "")

    def test_name_and_place_fallbacks(self):
        self.assertEqual(idx.name_of({"brand": "OMV"}), "OMV")
        self.assertEqual(idx.name_of({"name": "  Kis\tPatika  "}), "Kis Patika")
        self.assertEqual(idx.place_of({"addr:suburb": "Óbuda"}), "Óbuda")
        self.assertEqual(idx.place_of({"addr:city": "Szeged", "addr:place": "X"}), "Szeged")


class BuildTest(unittest.TestCase):
    DATA = {
        "osm3s": {"timestamp_osm_base": "2026-09-30T00:00:00Z"},
        "elements": [
            {"type": "node", "id": 1, "lat": 47.5, "lon": 19.0,
             "tags": {"name": "Arany Patika", "amenity": "pharmacy",
                      "phone": "+36 1 234 5678", "addr:city": "Budapest"}},
            {"type": "way", "id": 2, "center": {"lat": 46.2, "lon": 20.1},
             "tags": {"name": "Szegedi Rendelő", "amenity": "doctors",
                      "contact:phone": "06 62 123 456; 06-30/123-4567"}},
            # Ugyanaz a szám, más név — mindkettő marad.
            {"type": "node", "id": 3, "lat": 47.5, "lon": 19.0,
             "tags": {"name": "Arany Patika Webshop", "shop": "chemist",
                      "phone": "06 1 234 5678"}},
            # Ugyanaz a (szám, név) pár még egyszer — egyszer marad.
            {"type": "node", "id": 4, "lat": 47.5, "lon": 19.0,
             "tags": {"name": "Arany Patika", "amenity": "pharmacy",
                      "mobile": "+3612345678"}},
            # Név nélkül kimarad.
            {"type": "node", "id": 5, "lat": 47.5, "lon": 19.0,
             "tags": {"amenity": "bench", "phone": "+36 1 999 9999"}},
            # Szám nélkül kimarad.
            {"type": "node", "id": 6, "lat": 47.5, "lon": 19.0,
             "tags": {"name": "Csak név"}},
        ],
    }

    def test_build_rows_sorted_and_deduplicated(self):
        rows, stats = idx.build(self.DATA)
        self.assertEqual(rows, [
            ("+3612345678", "Arany Patika", "gyógyszertár", "Budapest"),
            ("+3612345678", "Arany Patika Webshop", "drogéria", ""),
            ("+36301234567", "Szegedi Rendelő", "orvosi rendelő", ""),
            ("+3662123456", "Szegedi Rendelő", "orvosi rendelő", ""),
        ])
        self.assertEqual(stats["objects"], 5)
        self.assertEqual(stats["with_name"], 4)
        self.assertEqual(stats["unique_numbers"], 3)

    def test_main_writes_header_and_lines(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = os.path.join(tmp, "in.json")
            out = os.path.join(tmp, "out.tsv")
            with open(src, "w", encoding="utf-8") as f:
                json.dump(self.DATA, f)
            idx.main([src, "-o", out])
            with open(out, encoding="utf-8") as f:
                lines = f.read().split("\n")
        self.assertEqual(
            lines[0],
            "# forrás: © OpenStreetMap-közreműködők, ODbL 1.0 — letöltve: 2026-09-30T00:00:00Z",
        )
        self.assertEqual(lines[1], "+3612345678\tArany Patika\tgyógyszertár\tBudapest")
        self.assertEqual(lines[-1], "")  # záró sortörés
        self.assertEqual(len(lines), 6)


if __name__ == "__main__":
    unittest.main()
