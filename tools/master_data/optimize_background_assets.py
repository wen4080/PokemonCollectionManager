"""將背景靜態圖轉成適合手機 Grid 的 WebP，保留透明度並更新圖片鍵。"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import shutil

try:
    from PIL import Image, ImageChops, ImageStat
except ImportError as error:  # pragma: no cover - only an environment setup failure.
    raise SystemExit("缺少 Pillow；請先執行：python -m pip install Pillow") from error


def backgrounds_from(root: object) -> list[dict]:
    if isinstance(root, list):
        return root
    if isinstance(root, dict):
        return root.get("backgrounds", [])
    raise ValueError("不支援的背景 JSON 格式")


def background_root(root: object) -> dict:
    if isinstance(root, dict):
        return root
    raise ValueError("最佳化主資料必須是物件格式")


def load_previous_backgrounds(path: pathlib.Path | None) -> dict[str, dict]:
    if path is None or not path.is_file():
        return {}
    try:
        root = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return {
        str(item.get("id")): item
        for item in backgrounds_from(root)
        if item.get("id")
    }


def load_verified_preview_index(path: pathlib.Path | None) -> dict[str, dict]:
    """讀取已驗證的完整合成預覽索引。

    這個索引是跨次更新的安全標記。即使上游暫時只回傳底圖，
    只要對應的完整 WebP 仍在快取中，就不能把它降級成底圖預覽。
    """
    if path is None or not path.is_file():
        return {}
    try:
        root = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    items = root.get("previews", []) if isinstance(root, dict) else root
    if not isinstance(items, list):
        return {}
    return {
        str(item.get("backgroundId")): item
        for item in items
        if isinstance(item, dict) and item.get("backgroundId")
    }


def image_difference_score(left: pathlib.Path, right: pathlib.Path) -> float | None:
    """比較預覽與底圖的縮圖差異；數值越接近 0 代表幾乎是同一張圖。"""
    try:
        with Image.open(left) as left_image, Image.open(right) as right_image:
            left_rgb = left_image.convert("RGB")
            right_rgb = right_image.convert("RGB")
            left_rgb.thumbnail((64, 64), Image.Resampling.LANCZOS)
            right_rgb = right_rgb.resize(left_rgb.size, Image.Resampling.LANCZOS)
            difference = ImageChops.difference(left_rgb, right_rgb)
            means = ImageStat.Stat(difference).mean
            return sum(means) / len(means)
    except (OSError, ValueError):
        return None


def asset_path(asset_root: pathlib.Path, image_key: str) -> pathlib.Path:
    relative = image_key.removeprefix("pogo/")
    if not pathlib.PurePosixPath(relative).suffix:
        relative = f"{relative}.png"
    return asset_root / "images" / pathlib.Path(*pathlib.PurePosixPath(relative).parts)


def main() -> None:
    parser = argparse.ArgumentParser(description="最佳化 Android 背景圖片資產")
    parser.add_argument("--manifest", type=pathlib.Path, required=True)
    parser.add_argument("--asset-root", type=pathlib.Path, required=True)
    parser.add_argument(
        "--previous-manifest",
        type=pathlib.Path,
        help="上一版已發布主資料；用來保留已驗證的完整合成預覽",
    )
    parser.add_argument(
        "--verified-preview-index",
        type=pathlib.Path,
        help="跨次更新的完整合成預覽索引；避免上游缺圖時退化成底圖",
    )
    parser.add_argument("--output-manifest", type=pathlib.Path, required=True)
    parser.add_argument("--backgrounds-output", type=pathlib.Path)
    parser.add_argument("--quality", type=int, default=84)
    parser.add_argument("--max-size", type=int, default=512)
    parser.add_argument("--prune-sources", action="store_true")
    args = parser.parse_args()

    root = background_root(json.loads(args.manifest.read_text(encoding="utf-8")))
    backgrounds = backgrounds_from(root)
    previous_manifest = args.previous_manifest
    if previous_manifest is None:
        inferred = args.asset_root / "master" / "master_manifest.json"
        previous_manifest = inferred if inferred.is_file() else None
    previous_backgrounds = load_previous_backgrounds(previous_manifest)
    verified_preview_index = args.verified_preview_index
    if verified_preview_index is None:
        inferred = args.asset_root / "master" / "background_preview_integrity_index.json"
        verified_preview_index = inferred if inferred.is_file() else None
    verified_previews = load_verified_preview_index(verified_preview_index)
    output_dir = args.asset_root / "images" / "background_optimized"
    output_dir.mkdir(parents=True, exist_ok=True)
    existing_index: dict[str, str] = {}
    existing_index_path = args.asset_root / "master" / "background_image_index.json"
    if existing_index_path.is_file():
        try:
            loaded_index = json.loads(existing_index_path.read_text(encoding="utf-8"))
            if isinstance(loaded_index, dict):
                existing_index = {
                    str(key): str(value)
                    for key, value in loaded_index.items()
                    if key and value
                }
        except (OSError, json.JSONDecodeError):
            existing_index = {}
    converted: dict[pathlib.Path, str] = {}
    used_output_paths: set[pathlib.Path] = set()
    source_paths: set[pathlib.Path] = set()
    integrity_audit: dict[str, object] = {
        "checkedPreviewCount": 0,
        "baseEquivalentPreviewCount": 0,
        "baseEquivalentBackgroundKeys": [],
        "preservedPreviousCompletePreviewCount": 0,
        "preservedPreviousBackgroundKeys": [],
        "restoredVerifiedPreviewCount": 0,
        "restoredVerifiedBackgroundKeys": [],
        "previewComparisonThreshold": 1.5,
    }
    base_equivalent_keys: list[str] = []
    preserved_previous_keys: list[str] = []
    untrusted_sources = {"Dittobase", "PoGoMate"}

    for background in backgrounds:
        background_id = str(background.get("id") or "")
        previous = previous_backgrounds.get(background_id)
        cached_key = existing_index.get(background_id)
        cached_path = asset_path(args.asset_root, cached_key) if cached_key else None
        cached_is_available = bool(cached_key and cached_path and cached_path.is_file())
        current_preview_key = background.get("previewImageKey")
        verified = verified_previews.get(background_id)
        verified_key = verified.get("previewImageKey") if verified else None
        verified_path = (
            asset_path(args.asset_root, str(verified_key))
            if verified_key and not str(verified_key).startswith("https://")
            else None
        )
        # 新版主資料可能暫時沒有預覽欄位，但上一版已驗證的完整 WebP
        # 仍然存在。先恢復完整預覽，再進入一般最佳化流程，避免只留下
        # 索財靈底圖、月光底圖或其他多圖層背卡的單層版本。
        if (
            not current_preview_key
            and verified_key
            and verified_path
            and verified_path.is_file()
        ):
            background["previewImageKey"] = verified_key
            background["previewSource"] = verified.get("previewSource") or "已驗證完整快取"
            background["previewStatus"] = "COMPLETE_STATIC_PREVIEW"
            background["sourcePreviewImageKey"] = verified.get(
                "sourcePreviewImageKey",
                background.get("sourcePreviewImageKey"),
            )
            current_preview_key = verified_key
            integrity_audit["restoredVerifiedPreviewCount"] = int(
                integrity_audit["restoredVerifiedPreviewCount"]
            ) + 1
            restored_key = str(background.get("backgroundKey") or background_id)
            integrity_audit["restoredVerifiedBackgroundKeys"].append(restored_key)
        previous_preview_key = previous.get("previewImageKey") if previous else None
        previous_preview_path = (
            asset_path(args.asset_root, str(previous_preview_key))
            if previous_preview_key and not str(previous_preview_key).startswith("https://")
            else None
        )
        # 舊版主資料可能只記錄了原始 background_previews/ 鍵，
        # 但同一張已驗證 WebP 已由穩定索引保留；以索引中的實體檔
        # 作為可沿用的上一版完整預覽。
        if previous and previous_preview_path and not previous_preview_path.is_file():
            cached_previous_key = existing_index.get(background_id)
            cached_previous_path = (
                asset_path(args.asset_root, cached_previous_key)
                if cached_previous_key
                else None
            )
            if cached_previous_key and cached_previous_path and cached_previous_path.is_file():
                previous_preview_key = cached_previous_key
                previous_preview_path = cached_previous_path
        previous_is_complete = bool(
            previous
            and (
                previous.get("previewStatus") == "COMPLETE_STATIC_PREVIEW"
                or previous.get("previewSource")
            )
            and previous_preview_path
            and previous_preview_path.is_file()
        )

        # 外部收藏索引有時只提供遊戲的靜態底圖。若它與本次底圖幾乎
        # 相同，不能把它標成完整圖層；若上一版有已驗證合成圖，則保留
        # 上一版，避免一次更新讓已能辨識的背景退化成無硬幣／無月亮。
        if current_preview_key and background.get("previewSource") in untrusted_sources:
            current_preview_path = asset_path(args.asset_root, str(current_preview_key))
            base_key = background.get("imageKey")
            base_path = asset_path(args.asset_root, str(base_key)) if base_key and not str(base_key).startswith("https://") else None
            score = image_difference_score(current_preview_path, base_path) if base_path and current_preview_path.is_file() and base_path.is_file() else None
            cached_score = image_difference_score(cached_path, current_preview_path) if cached_is_available and current_preview_path.is_file() else None
            cached_protected = False
            if cached_score is not None and cached_score > float(integrity_audit["previewComparisonThreshold"]):
                # 穩定索引中的既有圖比本次外部候選圖明顯不同，代表
                # 既有快取可能包含硬幣、月亮、剪影等合成圖層；外部
                # 候選不具可信度時，寧可保留已收錄的完整版本。
                background["previewImageKey"] = cached_key
                background["previewSource"] = (previous or {}).get("previewSource") or "已驗證完整快取"
                background["previewStatus"] = "COMPLETE_STATIC_PREVIEW"
                preserved_previous_keys.append(str(background.get("backgroundKey") or background_id))
                cached_protected = True
            if score is not None and not cached_protected:
                integrity_audit["checkedPreviewCount"] = int(integrity_audit["checkedPreviewCount"]) + 1
                if score <= float(integrity_audit["previewComparisonThreshold"]):
                    base_equivalent_keys.append(str(background.get("backgroundKey") or background_id))
                    if previous_is_complete:
                        background["previewImageKey"] = previous_preview_key
                        background["previewSource"] = previous.get("previewSource")
                        background["previewStatus"] = "COMPLETE_STATIC_PREVIEW"
                        if previous.get("sourcePreviewImageKey"):
                            background["sourcePreviewImageKey"] = previous["sourcePreviewImageKey"]
                        preserved_previous_keys.append(str(background.get("backgroundKey") or background_id))
                    else:
                        background["previewImageKey"] = None
                        background["previewSource"] = None
                        background["previewStatus"] = "STATIC_BASE_ONLY"

            # --skip-cached-backgrounds 可能讓本次底圖原檔不必重新下載，
            # 此時無法計算差異；對已驗證的上一版仍採保守策略保留合成圖。
            if score is None and previous_is_complete:
                background["previewImageKey"] = previous_preview_key
                background["previewSource"] = previous.get("previewSource")
                background["previewStatus"] = "COMPLETE_STATIC_PREVIEW"
                if previous.get("sourcePreviewImageKey"):
                    background["sourcePreviewImageKey"] = previous["sourcePreviewImageKey"]
                preserved_previous_keys.append(str(background.get("backgroundKey") or background_id))

        # 建置機器可能沒有重新下載到上一版預覽原檔，但上一版最佳化
        # WebP 仍在快取中；此時直接沿用快取，不讓後面的缺檔分支把它
        # 降級成底圖。
        current_preview_path = (
            asset_path(args.asset_root, str(background.get("previewImageKey")))
            if background.get("previewImageKey") and not str(background.get("previewImageKey")).startswith("https://")
            else None
        )
        if background.get("previewImageKey") and previous_is_complete and current_preview_path and not current_preview_path.is_file():
            background["previewImageKey"] = previous_preview_key
            background["previewSource"] = previous.get("previewSource")
            background["previewStatus"] = "COMPLETE_STATIC_PREVIEW"
            if previous.get("sourcePreviewImageKey"):
                background["sourcePreviewImageKey"] = previous["sourcePreviewImageKey"]
            preserved_previous_keys.append(str(background.get("backgroundKey") or background_id))

        # 預覽來源暫時抓不到時，仍可直接沿用上一版已驗證的合成檔。
        if not background.get("previewImageKey") and previous_is_complete:
            background["previewImageKey"] = previous_preview_key
            background["previewSource"] = previous.get("previewSource")
            background["previewStatus"] = "COMPLETE_STATIC_PREVIEW"
            if previous.get("sourcePreviewImageKey"):
                background["sourcePreviewImageKey"] = previous["sourcePreviewImageKey"]
            preserved_previous_keys.append(str(background.get("backgroundKey") or background_id))

        field = "previewImageKey" if background.get("previewImageKey") else "imageKey"
        image_key = background.get(field)
        if not image_key or image_key.startswith("https://"):
            continue
        source = asset_path(args.asset_root, image_key)
        if not source.is_file():
            # 既有版本已將舊背景最佳化並保留索引；新版本不應因上游
            # 暫時 403/逾時而把原本可顯示的城市背卡變成空白。
            # 只有完整預覽檔缺失時才退回原始底圖，不能把舊底圖冒充完整合成預覽。
            if field == "previewImageKey":
                background["previewImageKey"] = None
                background["previewSource"] = None
                field = "imageKey"
                image_key = background.get(field)
                if not image_key or image_key.startswith("https://"):
                    continue
                source = asset_path(args.asset_root, image_key)
            cached_source = cached_path
            if cached_key and cached_source and cached_source.is_file():
                background.setdefault(
                    "sourceImageKey" if field == "imageKey" else "sourcePreviewImageKey",
                    image_key,
                )
                background[field] = cached_key
                used_output_paths.add(cached_source.resolve())
                continue
            raise FileNotFoundError(f"找不到背景圖片：{background.get('backgroundKey')} -> {source}")
        source_paths.add(source.resolve())
        if image_key.startswith("background_optimized/"):
            converted[source.resolve()] = image_key
            used_output_paths.add(source.resolve())
            continue
        optimized_key = converted.get(source.resolve())
        if optimized_key is None:
            digest = hashlib.sha256(source.read_bytes()).hexdigest()[:20]
            destination = output_dir / f"{digest}.webp"
            with Image.open(source) as image:
                image.load()
                image.thumbnail((args.max_size, args.max_size), Image.Resampling.LANCZOS)
                image.save(destination, "WEBP", quality=args.quality, method=6, lossless=False)
            optimized_key = f"background_optimized/{destination.name}"
            converted[source.resolve()] = optimized_key
            used_output_paths.add(destination.resolve())
        source_field = "sourcePreviewImageKey" if field == "previewImageKey" else "sourceImageKey"
        background.setdefault(source_field, image_key)
        background[field] = optimized_key

    for candidate in output_dir.glob("*.webp"):
        if candidate.resolve() not in used_output_paths:
            candidate.unlink()

    args.output_manifest.parent.mkdir(parents=True, exist_ok=True)
    args.output_manifest.write_text(json.dumps(root, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.backgrounds_output:
        args.backgrounds_output.parent.mkdir(parents=True, exist_ok=True)
        args.backgrounds_output.write_text(json.dumps(backgrounds, ensure_ascii=False, indent=2), encoding="utf-8")

    image_index = {
        background["id"]: background.get("previewImageKey") or background.get("imageKey")
        for background in backgrounds
        if background.get("id") and (background.get("previewImageKey") or background.get("imageKey"))
    }
    index_path = args.asset_root / "master" / "background_image_index.json"
    index_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.write_text(json.dumps(image_index, ensure_ascii=False, indent=2), encoding="utf-8")

    if args.prune_sources:
        for source in source_paths:
            if source not in used_output_paths and source.is_file():
                source.unlink()
        for folder in (
            args.asset_root / "images" / "Images" / "LocationCards",
            args.asset_root / "images" / "background_previews",
        ):
            if folder.is_dir():
                shutil.rmtree(folder)
        images_folder = args.asset_root / "images" / "Images"
        if images_folder.is_dir() and not any(images_folder.iterdir()):
            images_folder.rmdir()

    optimized_bytes = sum(path.stat().st_size for path in used_output_paths)
    integrity_audit["baseEquivalentPreviewCount"] = len(base_equivalent_keys)
    integrity_audit["baseEquivalentBackgroundKeys"] = sorted(set(base_equivalent_keys))
    integrity_audit["preservedPreviousCompletePreviewCount"] = len(set(preserved_previous_keys))
    integrity_audit["preservedPreviousBackgroundKeys"] = sorted(set(preserved_previous_keys))
    integrity_audit["restoredVerifiedPreviewCount"] = len(
        set(integrity_audit["restoredVerifiedBackgroundKeys"])
    )
    integrity_audit["restoredVerifiedBackgroundKeys"] = sorted(
        set(integrity_audit["restoredVerifiedBackgroundKeys"])
    )
    root["backgroundPreviewIntegrityAudit"] = integrity_audit
    # 重新寫入一次，讓稽核資訊和最佳化後的背景欄位一起進入主資料。
    args.output_manifest.write_text(json.dumps(root, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Optimized background files: {len(used_output_paths)}")
    print(f"Optimized background bytes: {optimized_bytes}")
    print(f"Background image index entries: {len(image_index)}")
    print(f"Base-equivalent previews: {len(set(base_equivalent_keys))}")
    print(f"Preserved previous complete previews: {len(set(preserved_previous_keys))}")
    print(f"Restored verified complete previews: {integrity_audit['restoredVerifiedPreviewCount']}")


if __name__ == "__main__":
    main()

