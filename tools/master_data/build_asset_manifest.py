"""建立圖片資產清單，可選擇在 Windows 本機下載 2D 圖片。"""

from __future__ import annotations

import argparse
import json
import pathlib
import tempfile
import urllib.parse
import urllib.request


POGO_RAW_BASE = "https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/"


def remote_url(image_key: str) -> str | None:
    if image_key.startswith("pogo/"):
        return POGO_RAW_BASE + urllib.parse.quote(image_key.removeprefix("pogo/"), safe="/._-")
    if image_key.startswith("http://") or image_key.startswith("https://"):
        return image_key
    return None


GROUPS = ("species", "forms", "costumes", "backgrounds", "costumeCompatibility", "backgroundCompatibility")


def collect_image_keys(manifest: dict, groups: tuple[str, ...] = GROUPS) -> set[str]:
    keys: set[str] = set()
    for group_name in groups:
        for item in manifest.get(group_name, []):
            for field in ("defaultImageKey", "shinyImageKey", "imageKey", "previewImageKey"):
                if item.get(field):
                    keys.add(item[field])
    return keys


def cached_background_source_keys(manifest: dict, asset_dir: pathlib.Path | None) -> set[str]:
    """找出已有最佳化背景可覆蓋的原始鍵，避免每次更新重抓全部舊圖。"""
    if asset_dir is None:
        return set()
    index_path = asset_dir / "master" / "background_image_index.json"
    if not index_path.is_file():
        return set()
    try:
        index = json.loads(index_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return set()
    if not isinstance(index, dict):
        return set()
    cached: set[str] = set()
    for background in manifest.get("backgrounds", []):
        background_id = str(background.get("id") or "")
        optimized_key = index.get(background_id)
        if not background_id or not optimized_key:
            continue
        optimized_path = asset_dir / "images" / pathlib.Path(*pathlib.PurePosixPath(str(optimized_key)).parts)
        if not optimized_path.is_file():
            continue
        for field in ("imageKey", "previewImageKey"):
            image_key = background.get(field)
            if image_key and not str(image_key).startswith(("http://", "https://")):
                cached.add(str(image_key))
    return cached


def relative_asset_path(image_key: str) -> pathlib.PurePosixPath:
    value = image_key.removeprefix("pogo/")
    return pathlib.PurePosixPath(value if pathlib.PurePosixPath(value).suffix else f"{value}.png")


def download(url: str, target: pathlib.Path) -> None:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "PokemonCollectionManager-master-data", "Accept": "image/*"},
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(request, timeout=45) as response:
        content_type = response.headers.get_content_type()
        if not content_type.startswith("image/"):
            raise ValueError(f"不是圖片：{content_type}")
        data = response.read()
    if len(data) < 128:
        raise ValueError("圖片內容過小")
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as temporary:
        temporary.write(data)
        temporary_path = pathlib.Path(temporary.name)
    temporary_path.replace(target)


def main() -> None:
    parser = argparse.ArgumentParser(description="建立圖片資產清單")
    parser.add_argument("--manifest", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--asset-dir", type=pathlib.Path)
    parser.add_argument("--download", action="store_true")
    parser.add_argument(
        "--skip-cached-backgrounds",
        action="store_true",
        help="背景群組若已有 background_image_index.json 可解析，跳過舊原始圖下載",
    )
    parser.add_argument(
        "--groups",
        nargs="+",
        choices=GROUPS,
        default=list(GROUPS),
        help="只建立指定資料群組的圖片清單，例如 --groups backgrounds",
    )
    parser.add_argument("--overwrite", action="store_true", help="重新下載已存在的圖片")
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    records = []
    missing = 0
    downloaded = 0
    existing = 0
    cached_background_keys = cached_background_source_keys(manifest, args.asset_dir) if args.skip_cached_backgrounds else set()
    for image_key in sorted(collect_image_keys(manifest, tuple(args.groups))):
        url = remote_url(image_key)
        relative_path = relative_asset_path(image_key)
        record = {
            "imageKey": image_key,
            "remoteUrl": url,
            "assetPath": f"images/{relative_path.as_posix()}",
        }
        records.append(record)
        if args.download and args.asset_dir is not None and url is not None:
            if image_key in cached_background_keys:
                existing += 1
                continue
            target = args.asset_dir / "images" / pathlib.Path(*relative_path.parts)
            if target.is_file() and not args.overwrite:
                existing += 1
                continue
            try:
                download(url, target)
                downloaded += 1
            except Exception as error:
                missing += 1
                print(f"下載失敗：{image_key}（{error}）")
    output = {
        "manifestVersion": manifest.get("masterVersion", "未指定"),
        "images": records,
        "missingImages": missing,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Images: {len(records)}")
    print(f"Downloaded images: {downloaded}")
    print(f"Existing images: {existing}")
    print(f"Missing images: {missing}")


if __name__ == "__main__":
    main()

