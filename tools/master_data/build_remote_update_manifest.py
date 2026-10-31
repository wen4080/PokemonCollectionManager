"""建立可由 Android App 直接匯入的遠端主資料檔。

將背景圖片鍵改寫為使用者自己的 HTTPS 靜態資產根網址，避免手機依賴
建置時使用的多個上游網站。靜態目錄需保留 app assets 的 images/ 結構。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import urllib.parse


def remote_asset_url(base_url: str, image_key: str) -> str:
    if image_key.startswith("https://"):
        return image_key
    relative = image_key.removeprefix("pogo/")
    if not pathlib.PurePosixPath(relative).suffix:
        relative = f"{relative}.png"
    encoded = urllib.parse.quote(f"images/{relative}", safe="/._-")
    return f"{base_url.rstrip('/')}/{encoded}"


def require_published_asset(asset_root: pathlib.Path, image_key: str, background_key: str) -> None:
    """拒絕發布指向不存在靜態檔的背景資料。

    GitHub Pages 會把 manifest 和 images 一起部署。若建置階段少打包其中
    一張背景，讓流程在發布前失敗，比手機更新後才看到空白縮圖更安全。
    """
    if image_key.startswith("https://"):
        return
    relative = image_key.removeprefix("pogo/")
    if not pathlib.PurePosixPath(relative).suffix:
        relative = f"{relative}.png"
    candidate = asset_root / "images" / pathlib.Path(*pathlib.PurePosixPath(relative).parts)
    if not candidate.is_file():
        raise SystemExit(f"背景靜態資產尚未打包：{background_key} -> {candidate}")


def main() -> None:
    parser = argparse.ArgumentParser(description="建立 App 可直接使用的遠端更新主資料")
    parser.add_argument("--manifest", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--asset-base-url", required=True, help="包含 images/ 目錄的 HTTPS 根網址")
    args = parser.parse_args()
    if not args.asset_base_url.startswith("https://"):
        raise SystemExit("資產根網址必須使用 HTTPS")

    root = json.loads(args.manifest.read_text(encoding="utf-8"))
    rewritten = 0
    asset_root = args.output.parent
    for background in root.get("backgrounds", []):
        for field in ("imageKey", "previewImageKey"):
            image_key = background.get(field)
            if image_key:
                require_published_asset(asset_root, image_key, str(background.get("backgroundKey")))
                background[field] = remote_asset_url(args.asset_base_url, image_key)
                rewritten += 1
    root["remoteAssetBaseUrl"] = args.asset_base_url.rstrip("/")
    root["remoteBackgroundImageReferences"] = rewritten
    root.pop("contentFingerprint", None)
    root["contentFingerprint"] = hashlib.sha256(
        json.dumps(root, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(root, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Remote background image references: {rewritten}")
    print(f"Output: {args.output}")


if __name__ == "__main__":
    main()

