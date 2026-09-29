"""下載可辨識的特殊背卡完整靜態預覽，供建置階段離線打包。

這支工具只在 Windows 開發電腦執行；Android App 不會在啟動時爬取網站。
PokeMiners 的 LocationCards 仍是主資料身分來源，這裡的預覽只補足動態
特效層未包含在靜態底圖中的情況。
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import pathlib
import re
import sys
import urllib.request
from datetime import datetime, timezone


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


DITTOBASE_ROOT = "https://www.dittobase.com/pokemon-go/backgrounds/"
DITTOBASE_ASSET_PATTERN = re.compile(
    r"https://assets\.dittobase\.com/go/backgrounds/[A-Za-z0-9._-]+"
)
DITTOBASE_CURRENT_BACKGROUND_PATTERN = re.compile(
    r'alt=["\']Background["\'][^>]*src=["\'](https://assets\.dittobase\.com/go/backgrounds/[A-Za-z0-9._-]+)["\']',
    re.IGNORECASE,
)

# 上游底圖檔名與 Dittobase 收藏背卡頁面的穩定對照。未列入者不猜測。
SOURCE_PREVIEWS = {
    "sb_2024_decemberCdRecap": "sb-2024-december-cd-recap",
    "sb_9thAnniversary": "sb-9th-anniversary",
    "sb_Community_2026": "sb-community-days-2026",
    "sb_Concierge2025": "sb-concierge",
    "sb_FestivalofColors_2026": "sb-festivalofcolors-2026",
    "sb_GOWA2025_Global": "sb-gowildarea-2025-global",
    "sb_GoFest2024_radiance": "sb-go-fest-2024-radiance",
    "sb_GoFest2024_umbra": "sb-go-fest-2024-umbra",
    "sb_GoFest2024_wormhole": "sb-go-fest-2024-wormhole",
    "sb_GoFest2024_wormhole_moon": "sb-go-fest-2024-wormhole-moon",
    "sb_GoFest2024_wormhole_sun": "sb-go-fest-2024-wormhole-sun",
    "sb_GoFest2025": "sb-go-fest-2025",
    "sb_GoFest2025_Eternatus": "sb-go-fest-2025-dark-skies",
    "sb_GoFest2026_global": "sb-gofest2026-global",
    "sb_GoFest2026_mewtwo": "sb-gofest2026-mewtwo",
    "sb_GoTour2025_black": "sb-go-tour-2025-black",
    "sb_GoTour2025_black_white": "sb-go-tour-2025-black-white",
    "sb_GoTour2025_enigma": "sb-go-tour-2025-enigma",
    "sb_GoTour2025_white": "sb-go-tour-2025-white",
    "sb_GoTour2026_diamond": "sb-go-tour-2026-diamond",
    "sb_GoTour2026_gold": "sb-go-tour-2026-gold",
    "sb_GoTour2026_mega": "sb-go-tour-2026-mega",
    "sb_GoTour2026_pearl": "sb-go-tour-2026-pearl",
    "sb_GoTour2026_ruby": "sb-go-tour-2026-ruby",
    "sb_GoTour2026_sapphire": "sb-sapphire",
    "sb_GoTour2026_silver": "sb-go-tour-2026-silver",
    "sb_GoTour2026_x": "sb-go-tour-2026-x",
    "sb_GoTour2026_y": "sb-go-tour-2026-y",
    "sb_ObservatoryExhibitionTour": "sb-observatory-exhibition-tour",
    "sb_Season17_DuelDestiny": "sb-season-17-duel-destiny",
    "sb_Season18_MightAndMastery": "sb-season-18-might-and-mastery",
    "sb_Season19_DelightfulDays": "sb-season-19-delightful-days",
    "sb_Season20_TalesOfTransformation": "sb-season-20-tales-of-transformation",
    "sb_TeamLeader_blue": "sb-team-leader-blue",
    "sb_TeamLeader_red": "sb-team-leader-red",
    "sb_TeamLeader_yellow": "sb-team-leader-yellow",
    "sb_arraia_2026": "sb-arraia-2026",
    "sb_lego_2026": "sb-lego-2026",
    "sb_s24_sep_2026": "sb-s24-sep-2026",
}

# 同一底圖承載多張不同收藏背卡時，必須以背景主鍵覆寫，不能只靠底圖檔名。
BACKGROUND_KEY_PREVIEWS = {
    "SPECIALBACKGROUND_2025_GLOBAL_ENIGMA_001": "sb-go-tour-2025-enigma",
    "SPECIALBACKGROUND_2025_GLOBAL_GOFEST_SHIELD_001": "sb-zamazenta-go-fest-2025",
    "SPECIALBACKGROUND_2025_GLOBAL_GOFEST_SHIELD_CROWNED_001": "sb-zamazenta-go-fest-2025",
    "SPECIALBACKGROUND_2025_GLOBAL_GOFEST_SWORD_001": "sb-zacian-go-fest-2025",
    "SPECIALBACKGROUND_2025_GLOBAL_GOFEST_SWORD_CROWNED_001": "sb-zacian-go-fest-2025",
    "SPECIALBACKGROUND_2025_GLOBAL_GOTOUR_BLACK_001": "sb-go-tour-2025-black",
    "SPECIALBACKGROUND_2025_GLOBAL_GOTOUR_BLACK_WHITE_001": "sb-go-tour-2025-black-white",
    "SPECIALBACKGROUND_2025_GLOBAL_GOTOUR_WHITE_001": "sb-go-tour-2025-white",
}

# Dittobase 尚未收錄的完整預覽，使用另一個公開收藏索引的穩定靜態圖片。
DIRECT_PREVIEWS = {
    "SPECIALBACKGROUND_2026_MEWTWO_001": {
        "slug": "go-fest-2026-mega-mewtwo",
        "page": "https://archives.bulbagarden.net/wiki/File:GO_Fest_2026_Mega_Mewtwo_background.png",
        "image": "https://archives.bulbagarden.net/wiki/Special:Redirect/file/GO_Fest_2026_Mega_Mewtwo_background.png",
        "source": "Bulbagarden Archives",
    },
    "SPECIALBACKGROUND_2026_MEWTWO_002": {
        "slug": "go-fest-2026-mega-mewtwo",
        "page": "https://archives.bulbagarden.net/wiki/File:GO_Fest_2026_Mega_Mewtwo_background.png",
        "image": "https://archives.bulbagarden.net/wiki/Special:Redirect/file/GO_Fest_2026_Mega_Mewtwo_background.png",
        "source": "Bulbagarden Archives",
    },
    "SPECIALBACKGROUND_2026_WCS": {
        "slug": "worlds-special-blue-2026",
        "page": "https://archives.bulbagarden.net/wiki/File:GO_2026_Worlds_background.png",
        "image": "https://archives.bulbagarden.net/wiki/Special:Redirect/file/GO_2026_Worlds_background.png",
        "source": "Bulbagarden Archives",
    },
    "SPECIALBACKGROUND_2026_10TH_ANNIVERSARY_001": {
        "slug": "10th-anniversary-celebration-mewtwo-2026",
        "page": "https://archives.bulbagarden.net/wiki/File:GO_10th_Anniversary_Celebration_Mewtwo_background.png",
        "image": "https://archives.bulbagarden.net/wiki/Special:Redirect/file/GO_10th_Anniversary_Celebration_Mewtwo_background.png",
        "source": "Bulbagarden Archives",
    },
    "SPECIALBACKGROUND_GG2026": {
        "slug": "10th-anniversary-gimmighoul-2026",
        "page": "https://archives.bulbagarden.net/wiki/File:GO_10th_Anniversary_background.png",
        "image": "https://archives.bulbagarden.net/wiki/Special:Redirect/file/GO_10th_Anniversary_background.png",
        "source": "Bulbagarden Archives",
    },
}


def request_bytes(url: str) -> tuple[bytes, str | None]:
    request = urllib.request.Request(url, headers={"User-Agent": "PokemonCollectionManager/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read(), response.headers.get_content_type()


def dittobase_preview(slug: str) -> dict[str, str]:
    page = DITTOBASE_ROOT + slug
    html, _ = request_bytes(page)
    document = html.decode("utf-8", errors="replace")
    current = DITTOBASE_CURRENT_BACKGROUND_PATTERN.search(document)
    urls = [current.group(1)] if current else DITTOBASE_ASSET_PATTERN.findall(document)
    if not urls:
        raise RuntimeError(f"找不到完整預覽：{page}")
    # 背景頁第一張 /go/backgrounds/ 圖片就是該收藏背卡，不取 Pokémon 圖。
    return {"slug": slug, "page": page, "image": urls[0], "source": "Dittobase"}


def extension_for(url: str, content_type: str | None) -> str:
    suffix = pathlib.PurePosixPath(url.split("?", 1)[0]).suffix.lower()
    if suffix in {".png", ".jpg", ".jpeg", ".webp"}:
        return ".jpg" if suffix == ".jpeg" else suffix
    guessed = mimetypes.guess_extension(content_type or "")
    return ".jpg" if guessed == ".jpe" else (guessed or ".img")


def main() -> int:
    parser = argparse.ArgumentParser(description="下載特殊背卡完整靜態預覽")
    parser.add_argument("--output-dir", type=pathlib.Path, required=True)
    parser.add_argument("--manifest", type=pathlib.Path, required=True)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    slug_cache: dict[str, dict[str, str]] = {}
    rows: list[dict[str, str]] = []

    def materialize(source_stem: str | None, background_key: str | None, spec: dict[str, str]) -> None:
        image, content_type = request_bytes(spec["image"])
        if not content_type or not content_type.startswith("image/"):
            raise RuntimeError(f"回應不是圖片：{spec['image']}（{content_type}）")
        if len(image) < 1024:
            raise RuntimeError(f"圖片檔案過小：{spec['image']}（{len(image)} bytes）")
        suffix = extension_for(spec["image"], content_type)
        filename = re.sub(r"[^a-z0-9-]+", "-", spec["slug"].lower()).strip("-") + suffix
        destination = args.output_dir / filename
        destination.write_bytes(image)
        rows.append(
            {
                "sourceImageStem": source_stem,
                "backgroundKey": background_key,
                "previewImageKey": f"background_previews/{filename}",
                "previewSource": spec["source"],
                "sourcePage": spec["page"],
                "sourceImage": spec["image"],
            }
        )

    failures: list[str] = []
    for stem, slug in SOURCE_PREVIEWS.items():
        try:
            spec = slug_cache.setdefault(slug, dittobase_preview(slug))
            materialize(stem, None, spec)
        except Exception as exc:  # 保留其他成功項目並在結尾以失敗狀態回報。
            failures.append(f"{stem}: {exc}")
    for background_key, slug in BACKGROUND_KEY_PREVIEWS.items():
        try:
            spec = slug_cache.setdefault(slug, dittobase_preview(slug))
            materialize(None, background_key, spec)
        except Exception as exc:
            failures.append(f"{background_key}: {exc}")
    for background_key, spec in DIRECT_PREVIEWS.items():
        try:
            materialize(None, background_key, spec)
        except Exception as exc:
            failures.append(f"{background_key}: {exc}")

    # 相同檔案可能同時被底圖與主鍵規則使用；實體檔只保留一份。
    manifest = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "previews": rows,
        "downloadedFiles": len({row["previewImageKey"] for row in rows}),
        "mappedSourceImages": sum(bool(row.get("sourceImageStem")) for row in rows),
        "mappedBackgroundKeys": sum(bool(row.get("backgroundKey")) for row in rows),
        "failures": failures,
    }
    referenced_files = {pathlib.PurePosixPath(row["previewImageKey"]).name for row in rows}
    for candidate in args.output_dir.iterdir():
        if candidate.is_file() and candidate.name not in referenced_files:
            candidate.unlink()
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"完整預覽檔案：{manifest['downloadedFiles']}")
    print(f"底圖對照：{manifest['mappedSourceImages']}")
    print(f"背景主鍵覆寫：{manifest['mappedBackgroundKeys']}")
    if failures:
        print("下載失敗：")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("完整預覽下載與驗證通過")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
