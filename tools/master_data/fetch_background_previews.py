"""下載可辨識的特殊背卡完整靜態預覽，供建置階段離線打包。

這支工具只在 Windows 開發電腦執行；Android App 不會在啟動時爬取網站。
PokeMiners 的 LocationCards 仍是主資料身分來源，這裡的預覽只補足動態
特效層未包含在靜態底圖中的情況。
"""

from __future__ import annotations

import argparse
import html
import json
import mimetypes
import pathlib
import re
import sys
import urllib.parse
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
POGOMATE_INDEX_URL = "https://pogomate.com/en/backgrounds"
POGOMATE_BACKGROUND_IMAGE_PATTERN = re.compile(
    r"<img\b(?=[^>]*\bsrc=[\"'](https://cdn\.pogomate\.com/backgrounds/[^\"']+)[\"'])"
    r"(?=[^>]*\balt=[\"']([^\"']+)[\"'])[^>]*>",
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


def normalized_search_text(value: str | None) -> str:
    """把活動名稱轉成可跨中英文、底線與空白比對的穩定字串。"""
    if not value:
        return ""
    return re.sub(r"[\W_]+", "", html.unescape(value).casefold())


def search_tokens(value: str | None) -> set[str]:
    if not value:
        return set()
    ignored = {"and", "background", "go", "in", "of", "pokemon", "the"}
    return {
        token.casefold()
        for token in re.findall(r"[A-Za-z0-9]+|[\u4e00-\u9fff]+", html.unescape(value))
        if token.casefold() not in ignored and not re.fullmatch(r"20\d{2}", token)
    }


def image_stem(image_key: str | None) -> str | None:
    if not image_key:
        return None
    return pathlib.PurePosixPath(image_key.split("?", 1)[0]).stem


def fetch_pogomate_previews() -> list[dict[str, str]]:
    """讀取公開背卡索引，取得每個活動卡面的完整靜態合成預覽。

    PoGoMiners 的 sb/lc 圖片常是遊戲底圖；PoGoMate 的 card image 則是
    經過網站整理的卡面預覽，通常已包含月亮、圖騰、前景等可見圖層。
    這裡只在 Windows/GitHub Actions 建置階段下載，不在手機上爬網站。
    """
    document_bytes, _ = request_bytes(POGOMATE_INDEX_URL)
    document = document_bytes.decode("utf-8", errors="replace")
    rows: list[dict[str, str]] = []
    seen_urls: set[str] = set()
    seen_titles: set[str] = set()

    def add_row(
        *,
        title: str,
        image: str,
        event: str | None = None,
        source_image_stem: str | None = None,
    ) -> None:
        title = html.unescape(re.sub(r"\s+", " ", title).strip())
        if not title:
            return
        image = html.unescape(image).strip()
        if not image.startswith(("http://", "https://")):
            image = "https://assets.dittobase.com/go/backgrounds/" + image.lstrip("/")
        title_key = normalized_search_text(title)
        if image in seen_urls or title_key in seen_titles:
            return
        seen_urls.add(image)
        seen_titles.add(title_key)
        rows.append(
            {
                "title": title,
                "event": event or "",
                "image": image,
                "page": POGOMATE_INDEX_URL,
                "slug": f"pogomate-{pathlib.PurePosixPath(image.split('?', 1)[0]).stem}",
                "sourceImageStem": source_image_stem or image_stem(image),
            }
        )

    for match in POGOMATE_BACKGROUND_IMAGE_PATTERN.finditer(document):
        add_row(title=match.group(2), image=match.group(1))

    # 背卡頁面首屏只輸出 12 組卡面；其餘資料由 Next.js 延遲載入。
    # 從頁面路由的延遲 chunk 找到完整背景目錄，避免新活動永遠只在
    # 使用者按「載入更多」後才看得到，卻沒有被自動更新流程取得。
    script_sources = re.findall(r"<script[^>]+src=[\"']([^\"']+)[\"']", document, re.IGNORECASE)
    route_scripts: list[str] = []
    for source in script_sources:
        try:
            script_url = urllib.parse.urljoin(POGOMATE_INDEX_URL, html.unescape(source))
            script_bytes, _ = request_bytes(script_url)
            script = script_bytes.decode("utf-8", errors="replace")
            if "BackgroundBrowser" in script or "onClick:Y" in script:
                route_scripts.append(script)
        except Exception:
            continue

    lazy_chunk_paths: set[str] = set()
    for script in route_scripts:
        lazy_chunk_paths.update(
            re.findall(r"static/chunks/([A-Za-z0-9_.~-]+[.]js)", script)
        )
    record_pattern = re.compile(
        r'\{id:"([^"]+)",name:"([^"]*)",nameKo:"([^"]*)",type:"([^"]+)",'
        r'event:"([^"]*)",eventKo:"([^"]*)",image:(?:"([^"]*)"|null),date:"([^"]*)"'
    )
    for chunk_path in sorted(lazy_chunk_paths):
        try:
            chunk_url = f"https://pogomate.com/_next/static/chunks/{chunk_path}"
            chunk_bytes, _ = request_bytes(chunk_url)
            chunk = chunk_bytes.decode("utf-8", errors="replace")
        except Exception:
            continue
        for _id, name, _name_ko, _type, event, _event_ko, image, _date in record_pattern.findall(chunk):
            if image:
                add_row(title=name, event=event, image=image)
    if not rows:
        raise RuntimeError(f"PoGoMate 背卡索引沒有找到圖片：{POGOMATE_INDEX_URL}")
    return rows


def match_pogomate_previews(
    provider_rows: list[dict[str, str]],
    backgrounds: list[dict],
) -> tuple[dict[str, dict[str, str]], dict[str, object]]:
    """以背景主資料的活動名稱／來源檔名保守配對完整預覽。

    不用「第一筆看起來像的圖片」硬配。只有唯一高信心配對才會寫入，
    無法判斷的項目會留在 audit，讓未來主資料更新時可見而不會錯配。
    """
    candidates: dict[str, list[tuple[int, dict[str, str]]]] = {}
    event_counts: dict[str, int] = {}
    title_counts: dict[str, int] = {}
    for provider in provider_rows:
        event = normalized_search_text(provider.get("event"))
        if event:
            event_counts[event] = event_counts.get(event, 0) + 1
        title = normalized_search_text(provider.get("title"))
        if title:
            title_counts[title] = title_counts.get(title, 0) + 1
    for background in backgrounds:
        key = str(background.get("backgroundKey") or "")
        if not key or key == "NONE":
            continue
        background_stem = image_stem(str(background.get("imageKey") or ""))
        background_texts = [
            background.get("displayName"),
            background.get("eventName"),
            background.get("eventKey"),
            key,
            background_stem,
        ]
        normalized_display_name = normalized_search_text(background.get("displayName"))
        normalized_event_name = normalized_search_text(background.get("eventName"))
        background_tokens = set().union(*(search_tokens(value) for value in background_texts if value))
        for provider in provider_rows:
            provider_texts = [provider.get("title"), provider.get("event"), provider.get("sourceImageStem")]
            provider_tokens = set().union(*(search_tokens(value) for value in provider_texts if value))
            score = 0
            if background_stem and normalized_search_text(background_stem) == normalized_search_text(provider.get("sourceImageStem")):
                score = 120
            elif normalized_display_name and normalized_display_name == normalized_search_text(provider.get("title")):
                score = 110
            elif (
                normalized_event_name
                and normalized_event_name == normalized_search_text(provider.get("event"))
                and event_counts.get(normalized_event_name, 0) == 1
            ):
                score = 96
            elif (
                normalized_event_name
                and normalized_event_name == normalized_search_text(provider.get("title"))
                and title_counts.get(normalized_event_name, 0) == 1
            ):
                score = 96
            else:
                overlap = background_tokens & provider_tokens
                if len(overlap) >= 2:
                    score = 45 + min(35, len(overlap) * 8)
            if score:
                candidates.setdefault(key, []).append((score, provider))

    matched: dict[str, dict[str, str]] = {}
    ambiguous: list[dict[str, object]] = []
    for key, rows in candidates.items():
        ordered = sorted(rows, key=lambda item: (-item[0], item[1].get("image", "")))
        best_score, best = ordered[0]
        second_score = ordered[1][0] if len(ordered) > 1 else -1
        # 模糊 token 只用來列入 audit，不足以自動下載；否則「GO」「2026」
        # 這類共同字詞會把不同活動的背卡錯誤套用到彼此。
        if best_score >= 96:
            matched[key] = best
        else:
            ambiguous.append(
                {
                    "backgroundKey": key,
                    "candidates": [
                        {"title": item[1].get("title"), "score": item[0]}
                        for item in ordered[:5]
                    ],
                }
            )
    matched_provider_images = {row.get("image") for row in matched.values()}
    audit = {
        "provider": "PoGoMate",
        "providerIndex": POGOMATE_INDEX_URL,
        "providerPreviewCount": len(provider_rows),
        "matchedBackgroundCount": len(matched),
        "matchedProviderImageCount": len(matched_provider_images),
        "ambiguousMatches": ambiguous,
        "unmatchedBackgroundKeys": sorted(
            str(background.get("backgroundKey"))
            for background in backgrounds
            if background.get("backgroundKey") not in matched and background.get("backgroundKey") != "NONE"
        ),
    }
    return matched, audit


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
    parser.add_argument(
        "--catalog",
        type=pathlib.Path,
        help="解析後的 game_master_catalog.json；用於自動配對新的活動完整預覽",
    )
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    slug_cache: dict[str, dict[str, str]] = {}
    rows: list[dict[str, str]] = []
    row_keys: set[tuple[str | None, str | None, str]] = set()
    mapped_source_stems: set[str] = set()
    mapped_background_keys: set[str] = set()

    def materialize(source_stem: str | None, background_key: str | None, spec: dict[str, str]) -> None:
        row_key = (source_stem, background_key, spec["image"])
        if row_key in row_keys:
            return
        image, content_type = request_bytes(spec["image"])
        if not content_type or not content_type.startswith("image/"):
            raise RuntimeError(f"回應不是圖片：{spec['image']}（{content_type}）")
        if len(image) < 1024:
            raise RuntimeError(f"圖片檔案過小：{spec['image']}（{len(image)} bytes）")
        suffix = extension_for(spec["image"], content_type)
        filename = re.sub(r"[^a-z0-9-]+", "-", spec["slug"].lower()).strip("-") + suffix
        destination = args.output_dir / filename
        destination.write_bytes(image)
        row_keys.add(row_key)
        if source_stem:
            mapped_source_stems.add(source_stem)
        if background_key:
            mapped_background_keys.add(background_key)
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

    catalog_backgrounds: list[dict] = []
    if args.catalog and args.catalog.exists():
        catalog_root = json.loads(args.catalog.read_text(encoding="utf-8"))
        catalog_backgrounds = list(catalog_root.get("backgrounds", []))

    provider_audit: dict[str, object] = {
        "provider": "PoGoMate",
        "providerIndex": POGOMATE_INDEX_URL,
        "providerPreviewCount": 0,
        "matchedBackgroundCount": 0,
        "matchedProviderImageCount": 0,
        "ambiguousMatches": [],
        "unmatchedBackgroundKeys": [],
        "catalogBackgroundCount": len(catalog_backgrounds),
    }
    try:
        provider_rows = fetch_pogomate_previews()
        provider_matches, provider_audit = match_pogomate_previews(provider_rows, catalog_backgrounds)
        provider_audit["catalogBackgroundCount"] = len(catalog_backgrounds)
        catalog_by_key = {
            str(item.get("backgroundKey")): item
            for item in catalog_backgrounds
            if item.get("backgroundKey")
        }
        provider_failures: list[str] = []
        provider_skips: list[str] = []
        for background_key, provider in provider_matches.items():
            background = catalog_by_key.get(background_key, {})
            stem = image_stem(str(background.get("imageKey") or ""))
            if background_key in mapped_background_keys or (stem and stem in mapped_source_stems):
                continue
            spec = dict(provider)
            spec["source"] = "PoGoMate"
            provider_host = urllib.parse.urlparse(str(spec.get("image") or "")).netloc.casefold()
            if provider_host in {"assets.dittobase.com", "static.wikia.nocookie.net"}:
                # 這些是第三方的底圖索引，部分時間會拒絕自動下載；而且它們
                # 不一定是含動態圖層的合成預覽。保留在稽核，不把它冒充完整卡面。
                provider_skips.append(
                    f"{background_key}／{provider.get('title')}／{provider_host}"
                )
                continue
            try:
                materialize(stem, background_key, spec)
            except Exception as exc:
                provider_failures.append(
                    f"{background_key}／{provider.get('title')}: {exc}"
                )
        if provider_failures:
            provider_audit["downloadFailures"] = provider_failures
        if provider_skips:
            provider_audit["skippedBaseOnlySources"] = provider_skips
    except Exception as exc:
        failures.append(f"PoGoMate 自動完整預覽：{exc}")
        provider_audit["error"] = str(exc)

    if catalog_backgrounds:
        represented_keys = set(mapped_background_keys)
        represented_stems = set(mapped_source_stems)
        provider_audit["unmatchedBackgroundKeys"] = sorted(
            str(item.get("backgroundKey"))
            for item in catalog_backgrounds
            if item.get("backgroundKey") != "NONE"
            and item.get("backgroundKey") not in represented_keys
            and image_stem(str(item.get("imageKey") or "")) not in represented_stems
        )
        provider_audit["completePreviewMappedCount"] = len(
            set(mapped_background_keys)
            | {
                str(item.get("backgroundKey"))
                for item in catalog_backgrounds
                if image_stem(str(item.get("imageKey") or "")) in represented_stems
            }
        )

    # 相同檔案可能同時被底圖與主鍵規則使用；實體檔只保留一份。
    manifest = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "previews": rows,
        "downloadedFiles": len({row["previewImageKey"] for row in rows}),
        "mappedSourceImages": sum(bool(row.get("sourceImageStem")) for row in rows),
        "mappedBackgroundKeys": sum(bool(row.get("backgroundKey")) for row in rows),
        "failures": failures,
        "audit": provider_audit,
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
    print(f"PoGoMate 自動配對：{provider_audit.get('matchedBackgroundCount', 0)}")
    print(f"尚待完整預覽稽核：{len(provider_audit.get('unmatchedBackgroundKeys', []))}")
    if failures:
        print("下載失敗：")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("完整預覽下載與驗證通過")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

