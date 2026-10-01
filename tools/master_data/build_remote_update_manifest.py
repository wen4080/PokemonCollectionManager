"""建立可由 Android App 直接匯入的遠端主資料檔。

將背景圖片鍵改寫為使用者自己的 HTTPS 靜態資產根網址，避免手機依賴
建置時使用的多個上游網站。靜態目錄需保留 app assets 的 images/ 結構。
"""

from __future__ import annotations

import argparse
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
    for background in root.get("backgrounds", []):
        # 最佳化流程可能只保留完整預覽的 WebP，而把原始 PokeMiners
        # 底圖移除。遠端主資料的 imageKey 也要指向可公開取得的優先圖，
        # 不能留下更新站上不存在的 Images/LocationCards 原始路徑。
        preview_key = background.get("previewImageKey")
        image_key = background.get("imageKey")
        if preview_key and isinstance(image_key, str) and image_key.startswith("pogo/"):
            background["imageKey"] = preview_key
        for field in ("imageKey", "previewImageKey"):
            image_key = background.get(field)
            if image_key:
                background[field] = remote_asset_url(args.asset_base_url, image_key)
                rewritten += 1
    root["remoteAssetBaseUrl"] = args.asset_base_url.rstrip("/")
    root["remoteBackgroundImageReferences"] = rewritten
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(root, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Remote background image references: {rewritten}")
    print(f"Output: {args.output}")


if __name__ == "__main__":
    main()

