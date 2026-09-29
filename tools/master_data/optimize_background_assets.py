"""將背景靜態圖轉成適合手機 Grid 的 WebP，保留透明度並更新圖片鍵。"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import shutil

try:
    from PIL import Image
except ImportError as error:  # pragma: no cover - only an environment setup failure.
    raise SystemExit("缺少 Pillow；請先執行：python -m pip install Pillow") from error


def asset_path(asset_root: pathlib.Path, image_key: str) -> pathlib.Path:
    relative = image_key.removeprefix("pogo/")
    if not pathlib.PurePosixPath(relative).suffix:
        relative = f"{relative}.png"
    return asset_root / "images" / pathlib.Path(*pathlib.PurePosixPath(relative).parts)


def backgrounds_from(root: object) -> list[dict]:
    if isinstance(root, list):
        return root
    if isinstance(root, dict):
        return root.get("backgrounds", [])
    raise ValueError("不支援的背景 JSON 格式")


def main() -> None:
    parser = argparse.ArgumentParser(description="最佳化 Android 背景圖片資產")
    parser.add_argument("--manifest", type=pathlib.Path, required=True)
    parser.add_argument("--asset-root", type=pathlib.Path, required=True)
    parser.add_argument("--output-manifest", type=pathlib.Path, required=True)
    parser.add_argument("--backgrounds-output", type=pathlib.Path)
    parser.add_argument("--quality", type=int, default=84)
    parser.add_argument("--max-size", type=int, default=512)
    parser.add_argument("--prune-sources", action="store_true")
    args = parser.parse_args()

    root = json.loads(args.manifest.read_text(encoding="utf-8"))
    backgrounds = backgrounds_from(root)
    output_dir = args.asset_root / "images" / "background_optimized"
    output_dir.mkdir(parents=True, exist_ok=True)
    converted: dict[pathlib.Path, str] = {}
    used_output_paths: set[pathlib.Path] = set()
    source_paths: set[pathlib.Path] = set()

    for background in backgrounds:
        field = "previewImageKey" if background.get("previewImageKey") else "imageKey"
        image_key = background.get(field)
        if not image_key or image_key.startswith("https://"):
            continue
        source = asset_path(args.asset_root, image_key)
        if not source.is_file():
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
    print(f"Optimized background files: {len(used_output_paths)}")
    print(f"Optimized background bytes: {optimized_bytes}")
    print(f"Background image index entries: {len(image_index)}")


if __name__ == "__main__":
    main()
