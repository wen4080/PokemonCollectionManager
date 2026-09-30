"""驗證主資料 JSON 的唯一性、外鍵與圖片統計。"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def unique(items, field, label, errors):
    values = [item.get(field) for item in items]
    duplicates = sorted({value for value in values if value is not None and values.count(value) > 1})
    if duplicates:
        errors.append(f"{label} 的 {field} 重複：{duplicates[:5]}")


def load_form_name_overrides() -> dict[str, str]:
    path = pathlib.Path(__file__).with_name("form_name_overrides.json")
    if not path.exists():
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    values = raw.get("overrides", raw) if isinstance(raw, dict) else {}
    return {
        str(key).upper(): str(value).strip()
        for key, value in values.items()
        if str(key).strip() and str(value).strip()
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="驗證主資料")
    parser.add_argument("--manifest", type=pathlib.Path, required=True)
    parser.add_argument("--asset-root", type=pathlib.Path)
    parser.add_argument("--require-background-assets", action="store_true", help="每張背景的優先靜態圖都必須已打包")
    args = parser.parse_args()
    errors: list[str] = []
    try:
        root = json.loads(args.manifest.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"JSON 解析失敗：{exc}")
        return 1

    species = root.get("species", [])
    forms = root.get("forms", [])
    costumes = root.get("costumes", [])
    backgrounds = root.get("backgrounds", [])
    unique(species, "id", "Species", errors)
    unique(species, "speciesKey", "Species", errors)
    unique(forms, "id", "Forms", errors)
    form_pairs = [(item.get("speciesId"), item.get("formKey")) for item in forms]
    duplicate_form_pairs = sorted({pair for pair in form_pairs if form_pairs.count(pair) > 1})
    if duplicate_form_pairs:
        errors.append(f"型態組合重複：{duplicate_form_pairs[:5]}")
    unique(costumes, "id", "Costumes", errors)
    unique(costumes, "costumeKey", "Costumes", errors)
    unique(backgrounds, "id", "Backgrounds", errors)
    unique(backgrounds, "backgroundKey", "Backgrounds", errors)
    unique(backgrounds, "displayName", "Backgrounds", errors)
    compatibility = root.get("costumeCompatibility", [])
    unique(compatibility, "id", "Costume compatibility", errors)
    unique(compatibility, "compatibilityKey", "Costume compatibility", errors)
    for background in backgrounds:
        if not background.get("categoryKey") or not background.get("categoryName"):
            errors.append(f"背景缺少分類：{background.get('id')}")
    species_ids = {item.get("id") for item in species}
    species_keys = {item.get("id"): item.get("speciesKey") for item in species}
    form_ids = {item.get("id") for item in forms}
    costume_ids = {item.get("id") for item in costumes}
    background_ids = {item.get("id") for item in backgrounds}
    form_name_overrides = load_form_name_overrides()
    for form in forms:
        if form.get("speciesId") not in species_ids:
            errors.append(f"型態找不到物種：{form.get('id')}")
        species_key = species_keys.get(form.get("speciesId"))
        form_key = form.get("formKey")
        expected_name = form_name_overrides.get(f"{species_key}|{form_key}".upper())
        if expected_name and form.get("displayName") != expected_name:
            errors.append(
                f"型態名稱未套用標準覆寫：{species_key}|{form_key} "
                f"應為「{expected_name}」，目前為「{form.get('displayName')}」"
            )
        if any(token in str(form.get("displayName", "")) for token in ("特殊型態", "未命名型態")):
            errors.append(f"型態名稱仍是未整理的暫名：{form.get('id')}")
        if form.get("nameNeedsReview"):
            errors.append(f"型態名稱需要人工確認：{form.get('id')}")
    for item in compatibility:
        if item.get("speciesId") not in species_ids or item.get("formId") not in form_ids or item.get("costumeId") not in costume_ids:
            errors.append(f"裝扮相容關係外鍵錯誤：{item.get('compatibilityKey', item.get('id'))}")
    form_policy = root.get("formPolicyAudit", {})
    policy_species_without_normal = set(form_policy.get("speciesWithoutStandaloneNormalForm", []))
    for species_item in species:
        species_id = species_item.get("id")
        species_key = species_item.get("speciesKey")
        has_normal = any(item.get("speciesId") == species_id and item.get("formKey") == "NORMAL" for item in forms)
        if species_key in policy_species_without_normal and has_normal:
            errors.append(f"型態政策與資料不一致：{species_key} 被標示為沒有一般型態，但仍有 NORMAL")
        if species_key not in policy_species_without_normal and not has_normal:
            errors.append(f"型態政策缺少沒有一般型態的物種：{species_key}")
    referenced_costume_ids = {item.get("costumeId") for item in compatibility}
    unreferenced_costumes = sorted(costume_ids - {"COSTUME_NONE"} - referenced_costume_ids)
    if unreferenced_costumes:
        errors.append(f"裝扮沒有相容關係：{unreferenced_costumes[:5]}")
    for item in root.get("backgroundCompatibility", []):
        if item.get("backgroundId") not in background_ids:
            errors.append(f"背景相容關係外鍵錯誤：{item.get('id')}")

    image_keys: set[str] = set()
    for item in species + forms + costumes + backgrounds + root.get("costumeCompatibility", []):
        for field in ("defaultImageKey", "shinyImageKey", "imageKey", "previewImageKey"):
            image_key = item.get(field)
            if image_key:
                image_keys.add(image_key)

    background_image_keys = {item.get("imageKey") for item in backgrounds if item.get("imageKey")}
    duplicate_asset_aliases = []
    backgrounds_by_image: dict[str, list[dict]] = {}
    for background in backgrounds:
        if background.get("previewImageKey") and not background.get("previewSource"):
            errors.append(f"完整背景預覽缺少來源：{background.get('id')}")
        aliases = background.get("aliasBackgroundKeys", [])
        if len(aliases) != len(set(aliases)):
            errors.append(f"背景別名重複：{background.get('id')}")
        image_key = background.get("imageKey")
        if image_key:
            backgrounds_by_image.setdefault(image_key, []).append(background)
    for image_key, rows in backgrounds_by_image.items():
        if any(str(row.get("backgroundKey", "")).startswith("ASSET_") for row in rows) and any(
            not str(row.get("backgroundKey", "")).startswith("ASSET_") for row in rows
        ):
            duplicate_asset_aliases.append(image_key)
    if duplicate_asset_aliases:
        errors.append(f"同一背景圖片同時存在正式與 ASSET 暫存資料：{duplicate_asset_aliases[:5]}")

    source_stats = root.get("sourceStats", {})
    if source_stats.get("missingAuthoritativeCostumeForms", 0) != 0:
        errors.append(f"Game Master 裝扮尚未完整匯入：{source_stats.get('missingAuthoritativeCostumeForms')}")
    if source_stats.get("numericCostumeSpeciesWithoutNamedCompatibility", 0) != 0:
        errors.append(
            "純數字裝扮圖片存在沒有具名相容資料的物種："
            f"{source_stats.get('numericCostumeSpeciesWithoutNamedCompatibility')}"
        )
    if source_stats.get("numericCostumeExcessSpecies", 0) != 0:
        errors.append(
            "純數字裝扮圖片款式數多於具名裝扮的物種："
            f"{source_stats.get('numericCostumeExcessSpecies')}"
        )
    source_location_images = source_stats.get("locationCardImages")
    if source_location_images is not None and source_location_images != len(background_image_keys):
        errors.append(
            f"背景圖片覆蓋不完整：來源 {source_location_images}，主資料 {len(background_image_keys)}"
        )

    missing_image_keys: set[str] = set()
    missing_image_count = 0
    if args.asset_root is not None:
        for image_key in image_keys:
            relative_key = image_key.removeprefix("pogo/")
            candidate = args.asset_root / "images" / relative_key
            if not candidate.is_file():
                missing_image_keys.add(image_key)
        missing_image_count = len(missing_image_keys)
        if args.require_background_assets:
            missing_background_assets = []
            for background in backgrounds:
                image_key = background.get("previewImageKey") or background.get("imageKey")
                if not image_key:
                    continue
                relative_key = image_key.removeprefix("pogo/")
                candidate = args.asset_root / "images" / relative_key
                if not candidate.is_file():
                    missing_background_assets.append(f"{background.get('backgroundKey')} -> {relative_key}")
            if missing_background_assets:
                errors.append(
                    f"背景靜態圖未打包 {len(missing_background_assets)} 筆：{missing_background_assets[:5]}"
                )
    else:
        missing_image_count = root.get("missingImageCount", len(root.get("missingImageKeys", [])))

    print(f"Species: {len(species)}")
    print(f"Forms: {len(forms)}")
    print(f"Costumes: {len(costumes)}")
    print(f"Backgrounds: {len(backgrounds)}")
    print(f"Background categories: {len({item.get('categoryKey') for item in backgrounds})}")
    print(f"Costume compatibility: {len(compatibility)}")
    form_name_audit = root.get("formNameAudit", {})
    print(f"Form name sources: {form_name_audit.get('sourceCounts', {})}")
    print(f"Form names requiring review: {form_name_audit.get('reviewRequired', 0)}")
    print(f"Authoritative costume forms missing: {source_stats.get('missingAuthoritativeCostumeForms', 'unknown')}")
    print(f"Numeric costume images audited: {source_stats.get('numericCostumeImages', 'unknown')}")
    print(f"Location card images represented: {len(background_image_keys)}")
    print(f"Complete background previews: {sum(bool(item.get('previewImageKey')) for item in backgrounds)}")
    print(f"Merged internal effect aliases: {sum(len(item.get('aliasBackgroundKeys', [])) for item in backgrounds)}")
    print(f"Images: {len(image_keys)}")
    print(f"Missing images: {missing_image_count}")
    if errors:
        print("驗證失敗：")
        for error in errors:
            print(f"- {error}")
        return 1
    print("驗證通過")
    return 0


if __name__ == "__main__":
    sys.exit(main())

