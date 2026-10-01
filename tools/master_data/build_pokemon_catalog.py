"""建立 Android 可直接匯入的主資料 JSON。"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
from datetime import datetime, timezone


GENERATION_ENDS = (151, 251, 386, 493, 649, 721, 809, 905, 1025)


def generation_for(dex_number: int) -> int:
    for generation, end in enumerate(GENERATION_ENDS, start=1):
        if dex_number <= end:
            return generation
    return 9


def key(value: str) -> str:
    return re.sub(r"[^A-Z0-9]+", "_", value.upper()).strip("_")


def pogo_image_key(dex_number: int, shiny: bool = False) -> str:
    suffix = ".s" if shiny else ""
    return f"pogo/Images/Pokemon - 256x256/Addressable Assets/pm{dex_number}{suffix}.icon.png"


def form_sort_order(form_key: str) -> int:
    priorities = {
        "NORMAL": 0, "ALOLA": 100, "GALARIAN": 110, "HISUIAN": 120, "PALDEA": 130,
        "TWO": 140, "THREE": 150,
        "FAMILY_OF_THREE": 140, "FAMILY_OF_FOUR": 150,
        "ZERO": 140, "HERO": 150,
        "CROWNED_SWORD": 160, "CROWNED_SHIELD": 160,
        "MEGA": 200, "MEGA_X": 201, "MEGA_Y": 202, "PRIMAL": 210,
        "DYNAMAX": 300, "GIGANTAMAX": 310, "ETERNAMAX": 320,
    }
    return priorities.get(form_key, 500)


def main() -> None:
    parser = argparse.ArgumentParser(description="建立完整 Pokémon 主資料目錄")
    parser.add_argument("--input-dir", type=pathlib.Path, default=pathlib.Path("downloads"))
    parser.add_argument("--parsed-dir", type=pathlib.Path, default=pathlib.Path("generated"))
    parser.add_argument("--output-dir", type=pathlib.Path, default=pathlib.Path("generated"))
    parser.add_argument("--background-preview-manifest", type=pathlib.Path, default=pathlib.Path("generated/background_preview_manifest.json"))
    parser.add_argument("--version", default="2026.09.25")
    args = parser.parse_args()

    english = json.loads((args.input_dir / "names_en.json").read_text(encoding="utf-8"))
    traditional = json.loads((args.input_dir / "names_zh_hant.json").read_text(encoding="utf-8"))
    parsed = json.loads((args.parsed_dir / "game_master_catalog.json").read_text(encoding="utf-8"))
    available_asset_keys = set()
    tree_path = args.input_dir / "pogo_assets_tree.json"
    if tree_path.exists():
        tree = json.loads(tree_path.read_text(encoding="utf-8"))
        available_asset_keys = {f"pogo/{item['path']}" for item in tree.get("tree", []) if item.get("type") == "blob"}
    if len(english) != len(traditional):
        raise ValueError("英文與繁體中文名稱數量不同")

    form_image_candidates: dict[int, list[str]] = {}
    for item in parsed.get("forms", []):
        dex_number = item.get("dexNumber")
        image_key = item.get("imageKey")
        if isinstance(dex_number, int) and isinstance(image_key, str) and image_key in available_asset_keys:
            form_image_candidates.setdefault(dex_number, []).append(image_key)

    def shiny_image_for(normal_image_key: str) -> str | None:
        if normal_image_key.endswith(".icon.png"):
            candidate = normal_image_key.removesuffix(".icon.png") + ".s.icon.png"
        elif normal_image_key.endswith(".png"):
            candidate = normal_image_key.removesuffix(".png") + "_shiny.png"
        else:
            return None
        return candidate if candidate in available_asset_keys else None

    species = []
    forms = []
    species_by_key: dict[str, dict] = {}
    used_species_keys: set[str] = set()
    for dex_number, name_en in enumerate(english, start=1):
        species_key = key(name_en)
        if species_key in used_species_keys:
            species_key = f"{species_key}_{dex_number:04d}"
        used_species_keys.add(species_key)
        default_image_key = pogo_image_key(dex_number)
        shiny_image_key = pogo_image_key(dex_number, shiny=True)
        if default_image_key not in available_asset_keys:
            candidates = sorted(set(form_image_candidates.get(dex_number, [])))
            if candidates:
                default_image_key = next((item for item in candidates if shiny_image_for(item)), candidates[0])
                shiny_image_key = shiny_image_for(default_image_key) or shiny_image_key
        species_item = {
            "id": f"SPECIES_{species_key}",
            "dexNumber": dex_number,
            "speciesKey": species_key,
            "nameZhTw": traditional[dex_number - 1],
            "nameEn": name_en,
            "defaultImageKey": default_image_key,
            "shinyImageKey": shiny_image_key,
            "generation": generation_for(dex_number),
        }
        species.append(species_item)
        species_by_key[species_key] = species_item
    # 不能無條件替每個物種補「一般型態」。部分 Pokémon GO 物種的 Game
    # Master 只有實際存在的特殊型態，例如土龍節節只有二節／三節形態，
    # 一家鼠只有三隻／四隻家庭；若仍補 NORMAL，選擇器就會顯示不存在的選項。
    # 沒有任何型態資料的物種仍保留一般型態，確保主資料不會因上游暫時缺漏
    # 而讓使用者無法建立收藏。
    parsed_form_keys_by_species: dict[str, set[str]] = {}
    for item in parsed.get("forms", []):
        base_key = key(item.get("speciesKey", ""))
        species_item = species_by_key.get(base_key)
        if species_item is None and item.get("dexNumber"):
            species_item = next((candidate for candidate in species if candidate["dexNumber"] == item["dexNumber"]), None)
        if species_item is None:
            continue
        form_key = key(item.get("formKey", "NORMAL")) or "NORMAL"
        parsed_form_keys_by_species.setdefault(species_item["id"], set()).add(form_key)

    species_without_standalone_normal: list[str] = []
    synthetic_default_species: list[str] = []
    for species_item in species:
        known_form_keys = parsed_form_keys_by_species.get(species_item["id"], set())
        if known_form_keys and "NORMAL" not in known_form_keys:
            species_without_standalone_normal.append(species_item["speciesKey"])
            continue
        if not known_form_keys:
            synthetic_default_species.append(species_item["speciesKey"])
        forms.append(
            {
                "id": f"FORM_{species_item['speciesKey']}_NORMAL",
                "speciesId": species_item["id"],
                "formKey": "NORMAL",
                "displayName": "一般型態",
                "displayNameSource": "DEFAULT",
                "nameNeedsReview": False,
                "imageKey": species_item["defaultImageKey"],
                "isDefault": True,
                "sortOrder": 0,
            }
        )

    extra_forms: dict[tuple[str, str], dict] = {}
    for item in parsed.get("forms", []):
        form_key = key(item.get("formKey", "NORMAL")) or "NORMAL"
        base_key = key(item.get("speciesKey", ""))
        species_item = species_by_key.get(base_key)
        if species_item is None and item.get("dexNumber"):
            species_item = next((candidate for candidate in species if candidate["dexNumber"] == item["dexNumber"]), None)
        if species_item is None or form_key == "NORMAL":
            continue
        pair = (species_item["id"], form_key)
        extra_forms[pair] = {
            "id": f"FORM_{species_item['speciesKey']}_{form_key}",
            "speciesId": species_item["id"],
            "formKey": form_key,
            "displayName": item.get("displayName") or "特殊型態",
            "displayNameSource": item.get("displayNameSource", "LEGACY_FALLBACK"),
            "nameNeedsReview": bool(item.get("nameNeedsReview", not item.get("displayName"))),
            "imageKey": item.get("imageKey") or species_item["defaultImageKey"],
            "isDefault": False,
            "sortOrder": form_sort_order(form_key),
        }
    forms.extend(extra_forms.values())
    form_pairs = {(item["speciesId"], item["formKey"]): item for item in forms}

    costumes = [{"id": "COSTUME_NONE", "costumeKey": "NONE", "displayName": "無裝扮", "eventName": None, "imageKey": None, "releaseYear": None, "sortOrder": 0}]
    costume_ids: dict[str, str] = {"NONE": "COSTUME_NONE"}
    for item in parsed.get("costumes", []):
        costume_key = key(item.get("costumeKey", "SPECIAL")) or "SPECIAL"
        costume_id = f"COSTUME_{costume_key}"
        costume_ids[costume_key] = costume_id
        costumes.append(
            {
                "id": costume_id,
                "costumeKey": costume_key,
                "displayName": item.get("displayName") or "特殊裝扮",
                "eventName": item.get("eventName"),
                "imageKey": item.get("imageKey"),
                "releaseYear": item.get("releaseYear"),
                "sortOrder": item.get("sortOrder", 0),
            }
        )

    costume_compatibility = []
    for item in parsed.get("costumeCompatibility", []):
        base_key = key(item.get("speciesKey", ""))
        species_item = species_by_key.get(base_key)
        if species_item is None and item.get("dexNumber"):
            species_item = next((candidate for candidate in species if candidate["dexNumber"] == item["dexNumber"]), None)
        costume_id = costume_ids.get(key(item.get("costumeKey", "")))
        if species_item is None or costume_id is None:
            continue
        form_key = key(item.get("formKey", "NORMAL")) or "NORMAL"
        form_item = form_pairs.get((species_item["id"], form_key))
        # 某些上游裝扮資料只標示物種，沒有可對應的獨立 NORMAL 型態。
        # 這時使用 NULL formId 表示「此物種皆適用」，避免建立不存在的
        # FORM_<species>_NORMAL 外鍵，也讓未來新增的非一般型態仍可選到裝扮。
        if form_item is None and form_key != "NORMAL":
            continue
        costume_compatibility.append(
            {
                # dexNumber-only records do not carry speciesKey, so base_key can be empty.
                # The previous ID consequently collided across species (for example the
                # four FALL_2019 costume rows all had the same primary key) and Room's
                # REPLACE import silently kept only the final row.  Build the primary key
                # from the resolved stable species/form/costume IDs instead.
                "id": f"COMPAT_{species_item['id']}_{form_item['id'] if form_item else 'ANY'}_{costume_id}",
                "compatibilityKey": f"{species_item['id']}|{form_item['id'] if form_item else 'ANY'}|{costume_id}",
                "speciesId": species_item["id"],
                "formId": form_item["id"] if form_item else None,
                "costumeId": costume_id,
                "imageKey": item.get("imageKey"),
                "isVerified": bool(item.get("isVerified", True)),
            }
        )

    backgrounds = [
        {
            "id": "BACKGROUND_NONE",
            "backgroundKey": "NONE",
            "displayName": "無背景",
            "backgroundType": "OTHER",
            "categoryKey": "NONE",
            "categoryName": "無背景",
            "eventKey": "NONE",
            "eventName": None,
            "locationName": None,
            "year": None,
            "availableFrom": None,
            "availableUntil": None,
            "dataSource": "BUNDLED",
            "imageKey": None,
            "vfxKey": None,
            "vfxKeys": None,
            "effectNote": None,
            "previewImageKey": None,
            "previewSource": None,
            "previewStatus": "NOT_APPLICABLE",
            "sortOrder": 0,
        }
    ]
    backgrounds.extend(parsed.get("backgrounds", []))
    preview_manifest = {"previews": []}
    if args.background_preview_manifest.exists():
        preview_manifest = json.loads(args.background_preview_manifest.read_text(encoding="utf-8"))
    previews_by_key = {
        item["backgroundKey"]: item
        for item in preview_manifest.get("previews", [])
        if item.get("backgroundKey")
    }
    previews_by_stem = {
        item["sourceImageStem"]: item
        for item in preview_manifest.get("previews", [])
        if item.get("sourceImageStem")
    }
    # 指定款式的完整預覽即使暫時下載失敗，也不能退回同底圖的通用頁面。
    # 否則會把只有底圖的圖片誤標成完整圖層，並覆蓋上一版正確快取。
    direct_preview_keys = set(preview_manifest.get("directPreviewKeys", []))
    for background in backgrounds:
        image_key = background.get("imageKey")
        stem = pathlib.PurePosixPath(image_key).stem if image_key else None
        preview = previews_by_key.get(background.get("backgroundKey"))
        if preview is None and background.get("backgroundKey") not in direct_preview_keys:
            preview = previews_by_stem.get(stem)
        if preview:
            background["previewImageKey"] = preview["previewImageKey"]
            background["previewSource"] = preview["previewSource"]
            background["previewStatus"] = "COMPLETE_STATIC_PREVIEW"
        else:
            background.setdefault("previewImageKey", None)
            background.setdefault("previewSource", None)
            if not background.get("imageKey"):
                background["previewStatus"] = "NO_IMAGE"
            elif (
                background.get("vfxKey")
                or background.get("vfxKeys")
                or background.get("effectNote")
                or str(background.get("backgroundKey", "")).startswith("ASSET_SB_")
            ):
                background["previewStatus"] = "STATIC_BASE_ONLY"
            else:
                background["previewStatus"] = "STATIC_IMAGE"
    background_compatibility = []

    image_keys = set()
    for item in species + forms + costumes + backgrounds:
        image_keys.update(value for value in (item.get("defaultImageKey"), item.get("shinyImageKey"), item.get("imageKey"), item.get("previewImageKey")) if value)
    image_keys.update(item["imageKey"] for item in costume_compatibility if item.get("imageKey"))
    missing_image_count = sum(1 for image_key in image_keys if image_key.startswith("pogo/") and image_key not in available_asset_keys)

    root = {
        "masterVersion": args.version,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": ["PokeMiners game_masters", "PokeMiners pogo_assets", "sindresorhus/pokemon localized names", "Dittobase/PoGoMate static background previews"],
        "species": species,
        "forms": forms,
        "costumes": costumes,
        "backgrounds": backgrounds,
        "costumeCompatibility": costume_compatibility,
        "backgroundCompatibility": background_compatibility,
        "backgroundPreviewAudit": preview_manifest.get("audit", {}),
        "formNameAudit": parsed.get("formNameAudit", {}),
        "formPolicyAudit": {
            "speciesWithoutStandaloneNormalForm": sorted(species_without_standalone_normal),
            "speciesWithoutParsedFormData": sorted(synthetic_default_species),
            "normalFormRule": "只有上游有 NORMAL 或完全沒有型態資料時才建立一般型態",
        },
        "imageCount": len(image_keys),
        "missingImageCount": missing_image_count,
        "sourceStats": parsed.get("assetStats", {}),
    }
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "master_manifest.json").write_text(json.dumps(root, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output_dir / "pokemon_species.json").write_text(json.dumps(species, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output_dir / "pokemon_forms.json").write_text(json.dumps(forms, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output_dir / "pokemon_costumes.json").write_text(json.dumps(costumes, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output_dir / "pokemon_backgrounds.json").write_text(json.dumps(backgrounds, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Species: {len(species)}")
    print(f"Forms: {len(forms)}")
    print(f"Costumes: {len(costumes)}")
    print(f"Backgrounds: {len(backgrounds)}")
    print(f"Background categories: {len({item.get('categoryKey', 'OTHER_SPECIAL') for item in backgrounds})}")
    print(f"Costume compatibility: {len(costume_compatibility)}")
    print(f"Images: {len(image_keys)}")
    print(f"Missing images: {missing_image_count}")


if __name__ == "__main__":
    main()

