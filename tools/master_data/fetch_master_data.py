"""下載產生主資料所需的公開靜態來源。Windows、Python 標準函式庫即可執行。"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import urllib.request


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")


SOURCES = {
    "game_master_latest.json": "https://raw.githubusercontent.com/PokeMiners/game_masters/master/latest/latest.json",
    "names_en.json": "https://raw.githubusercontent.com/sindresorhus/pokemon/refs/heads/main/data/en.json",
    "names_zh_hant.json": "https://raw.githubusercontent.com/sindresorhus/pokemon/refs/heads/main/data/zh-hant.json",
    "pogo_assets_tree.json": "https://api.github.com/repos/PokeMiners/pogo_assets/git/trees/master?recursive=1",
    "texts_apk_en.txt": "https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Texts/Latest%20APK/English.txt",
    "texts_apk_zh_hant.txt": "https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Texts/Latest%20APK/ChineseTraditional.txt",
    "texts_remote_en.txt": "https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Texts/Latest%20Remote/English.txt",
    "texts_remote_zh_hant.txt": "https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Texts/Latest%20Remote/ChineseTraditional.txt",
}


def download(url: str, target: pathlib.Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "PokemonCollectionManager-master-data"})
    with urllib.request.urlopen(request, timeout=60) as response:
        target.write_bytes(response.read())


def main() -> None:
    parser = argparse.ArgumentParser(description="下載 Pokémon 主資料來源")
    parser.add_argument("--output-dir", type=pathlib.Path, default=pathlib.Path("downloads"))
    parser.add_argument("--force", action="store_true", help="重新下載已有檔案")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    for filename, url in SOURCES.items():
        target = args.output_dir / filename
        if target.exists() and not args.force:
            print(f"已存在：{target}")
            continue
        print(f"下載：{url}")
        download(url, target)
        if filename.endswith(".json"):
            json.loads(target.read_text(encoding="utf-8"))
        print(f"完成：{target}（{target.stat().st_size} bytes）")


if __name__ == "__main__":
    main()
