"""主資料產生結果的回歸測試。"""

from __future__ import annotations

import json
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parent


class GeneratedCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.catalog = json.loads((ROOT / "generated" / "game_master_catalog.json").read_text(encoding="utf-8"))
        cls.manifest = json.loads((ROOT / "generated" / "master_manifest.json").read_text(encoding="utf-8"))
        cls.app_manifest = json.loads((ROOT.parent.parent / "app" / "src" / "main" / "assets" / "master" / "master_manifest.json").read_text(encoding="utf-8"))
        cls.background_image_index = json.loads(
            (ROOT.parent.parent / "app" / "src" / "main" / "assets" / "master" / "background_image_index.json").read_text(encoding="utf-8"),
        )
        tree = json.loads((ROOT / "downloads" / "pogo_assets_tree.json").read_text(encoding="utf-8"))
        cls.asset_keys = {f"pogo/{item['path']}" for item in tree["tree"] if item.get("type") == "blob"}

    def test_all_authoritative_costumes_are_imported(self) -> None:
        self.assertEqual(0, self.manifest["sourceStats"]["missingAuthoritativeCostumeForms"])
        self.assertEqual(0, self.manifest["sourceStats"]["numericCostumeSpeciesWithoutNamedCompatibility"])
        self.assertEqual(0, self.manifest["sourceStats"]["numericCostumeExcessSpecies"])
        gimmighoul_costumes = {
            item["costumeKey"]
            for item in self.catalog["costumeCompatibility"]
            if item["dexNumber"] == 999
        }
        self.assertTrue({"COIN_A1", "COIN_A2_2026"}.issubset(gimmighoul_costumes))

    def test_reported_costume_examples_are_present(self) -> None:
        pairs = {
            (item["dexNumber"], item["costumeKey"])
            for item in self.catalog["costumeCompatibility"]
        }
        self.assertTrue(
            {
                (1, "SPRING_2020_NOEVOLVE"),
                (4, "SPRING_2020_NOEVOLVE"),
                (7, "SPRING_2020_NOEVOLVE"),
                (25, "MAY_2019_NOEVOLVE"),
                (25, "ADVENTURE_HAT_2020"),
            }.issubset(pairs),
        )

    def test_costume_compatibility_primary_keys_are_unique(self) -> None:
        rows = self.manifest["costumeCompatibility"]
        self.assertEqual(len(rows), len({item["id"] for item in rows}))
        self.assertEqual(len(rows), len({item["compatibilityKey"] for item in rows}))

    def test_costume_titles_are_unambiguous(self) -> None:
        names = [item["displayName"] for item in self.manifest["costumes"]]
        self.assertEqual(len(names), len(set(names)))

    def test_shared_costume_does_not_overwrite_other_species(self) -> None:
        species = {
            item["speciesId"]
            for item in self.manifest["costumeCompatibility"]
            if item["costumeId"] == "COSTUME_SPRING_2020_NOEVOLVE"
        }
        self.assertTrue(
            {"SPECIES_BULBASAUR", "SPECIES_CHARMANDER", "SPECIES_SQUIRTLE"}.issubset(species),
        )

    def test_costume_and_background_images_exist_upstream(self) -> None:
        image_keys = {
            item.get("imageKey")
            for group in ("costumeCompatibility", "backgrounds")
            for item in self.manifest[group]
            if item.get("imageKey")
        }
        self.assertEqual(set(), image_keys - self.asset_keys)

    def test_san_francisco_worlds_is_one_background(self) -> None:
        rows = [
            item
            for item in self.manifest["backgrounds"]
            if "SANFRANCISCO_WCS" in item["backgroundKey"] or "WCS2026_SANFRANCISCO" in item["backgroundKey"]
        ]
        self.assertEqual(1, len(rows))
        self.assertEqual("2026_SANFRANCISCO_WCS_001", rows[0]["backgroundKey"])
        self.assertEqual("pogo/Images/LocationCards/lc_Wcs2026_sanFrancisco.png", rows[0]["imageKey"])
        self.assertEqual("WORLD_CHAMPIONSHIPS", rows[0]["categoryKey"])

    def test_san_francisco_and_worlds_special_are_distinct(self) -> None:
        by_key = {item["backgroundKey"]: item for item in self.manifest["backgrounds"]}
        location = by_key["2026_SANFRANCISCO_WCS_001"]
        special = by_key["SPECIALBACKGROUND_2026_WCS"]
        self.assertNotEqual(location["id"], special["id"])
        self.assertNotEqual(location["imageKey"], special["imageKey"])
        self.assertIn("舊金山", location["displayName"])
        self.assertIn("世界冠軍紀念背卡", special["displayName"])

    def test_known_2026_backgrounds_have_specific_event_titles(self) -> None:
        by_key = {item["backgroundKey"]: item for item in self.manifest["backgrounds"]}
        self.assertIn("GO Fest 全球 2026", by_key["SPECIALBACKGROUND_2026_GLOBAL_GOFEST_001"]["displayName"])
        self.assertIn("GO Tour 全球 2026", by_key["SPECIALBACKGROUND_2026_GLOBAL_DIAMOND_001"]["displayName"])
        self.assertIn("鑽石款", by_key["SPECIALBACKGROUND_2026_GLOBAL_DIAMOND_001"]["displayName"])

    def test_background_event_brands_use_recognizable_official_names(self) -> None:
        categories = {item["categoryKey"]: item["categoryName"] for item in self.manifest["backgrounds"]}
        self.assertEqual("GO Fest 全球背卡", categories["GO_FEST_GLOBAL"])
        self.assertEqual("GO Fest 地區背卡", categories["GO_FEST_REGIONAL"])
        self.assertEqual("GO Tour 背卡", categories["GO_TOUR"])
        self.assertEqual("City Safari 背卡", categories["CITY_SAFARI"])

    def test_all_location_card_images_are_represented(self) -> None:
        image_keys = {item.get("imageKey") for item in self.manifest["backgrounds"] if item.get("imageKey")}
        self.assertEqual(self.manifest["sourceStats"]["locationCardImages"], len(image_keys))

    def test_background_titles_are_unique(self) -> None:
        names = [item["displayName"] for item in self.manifest["backgrounds"]]
        self.assertEqual(len(names), len(set(names)))

    def test_internal_effect_variants_are_one_collectible_background(self) -> None:
        by_key = {item["backgroundKey"]: item for item in self.manifest["backgrounds"]}
        self.assertNotIn("SPECIALBACKGROUND_2026_MEWTWO_002", by_key)
        mewtwo = by_key["SPECIALBACKGROUND_2026_MEWTWO_001"]
        self.assertEqual(2, len(mewtwo["vfxKeys"].split("|")))
        self.assertIn("超夢專屬背卡", mewtwo["displayName"])
        regi = [item for item in self.manifest["backgrounds"] if "GLOBAL_GOFEST_REGI" in item["backgroundKey"]]
        self.assertEqual(1, len(regi))
        self.assertEqual(5, len(regi[0]["aliasBackgroundKeys"]))
        mega = [item for item in self.manifest["backgrounds"] if "2026_GLOBAL_MEGA" in item["backgroundKey"]]
        self.assertEqual(1, len(mega))

    def test_known_dynamic_backgrounds_have_complete_previews(self) -> None:
        by_key = {item["backgroundKey"]: item for item in self.app_manifest["backgrounds"]}
        for key in (
            "SPECIALBACKGROUND_2026_GLOBAL_GOFEST_001",
            "SPECIALBACKGROUND_2026_MEWTWO_001",
            "SPECIALBACKGROUND_2026_WCS",
            "SPECIALBACKGROUND_2026_GLOBAL_MEGA_001",
        ):
            self.assertTrue(by_key[key].get("previewImageKey"), key)
            relative = by_key[key]["previewImageKey"]
            self.assertTrue((ROOT.parent.parent / "app" / "src" / "main" / "assets" / "images" / relative).exists(), relative)
            self.assertTrue(by_key[key].get("sourcePreviewImageKey"), key)

    def test_every_background_image_is_resolvable_by_stable_id(self) -> None:
        backgrounds = [item for item in self.app_manifest["backgrounds"] if item.get("imageKey") or item.get("previewImageKey")]
        self.assertEqual({item["id"] for item in backgrounds}, set(self.background_image_index))
        image_root = ROOT.parent.parent / "app" / "src" / "main" / "assets" / "images"
        for background_id, image_key in self.background_image_index.items():
            self.assertTrue((image_root / image_key).is_file(), f"{background_id} -> {image_key}")

    def test_form_titles_are_specific_and_species_prefixes_are_not_forms(self) -> None:
        forms = self.manifest["forms"]
        self.assertFalse(any("特殊型態" in item["displayName"] for item in forms))
        self.assertFalse(any("未命名型態" in item["displayName"] for item in forms))
        self.assertFalse(any(item["formKey"] == "NIDORAN_NORMAL" for item in forms))
        labels = {item["formKey"]: item["displayName"] for item in forms}
        self.assertEqual("超級進化", labels["MEGA"])
        self.assertEqual("超極巨化", labels["GIGANTAMAX"])
        self.assertEqual("原始回歸", labels["PRIMAL"])

    def test_costumes_have_stable_chronological_metadata(self) -> None:
        costumes = [item for item in self.manifest["costumes"] if item["costumeKey"] != "NONE"]
        self.assertTrue(all("sortOrder" in item for item in costumes))
        self.assertTrue(any(item.get("releaseYear") == 2026 for item in costumes))
        self.assertFalse(any(item.get("eventName") == "活動裝扮" for item in costumes))

    def test_backgrounds_have_year_event_and_source_metadata(self) -> None:
        backgrounds = [item for item in self.manifest["backgrounds"] if item["backgroundKey"] != "NONE"]
        self.assertTrue(all(item.get("eventKey") for item in backgrounds))
        self.assertTrue(all(item.get("eventName") for item in backgrounds))
        self.assertTrue(all(item.get("dataSource") in {"GAME_MASTER", "ASSET_ONLY"} for item in backgrounds))


if __name__ == "__main__":
    unittest.main()
