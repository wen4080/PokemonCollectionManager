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
