"""將 Game Master 與 PokeMiners 2D 資產索引整理成應用程式主資料片段。"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
from collections import defaultdict


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


FORM_LABELS = {
    "NORMAL": "一般型態",
    "ALOLA": "阿羅拉型態",
    "ALOLAN": "阿羅拉型態",
    "GALARIAN": "伽勒爾型態",
    "HISUIAN": "洗翠型態",
    "PALDEAN": "帕底亞型態",
    "ORIGIN": "起源型態",
    "ALTERED": "改變型態",
    "THERIAN": "靈獸型態",
    "INCARNATE": "化身型態",
    "ATTACK": "攻擊型態",
    "DEFENSE": "防禦型態",
    "SPEED": "速度型態",
    "DUSK": "黃昏型態",
    "DAWN": "黎明型態",
    "MIDDAY": "白晝型態",
    "MIDNIGHT": "黑夜型態",
    "SKY": "天空型態",
    "LAND": "陸地型態",
    "BLADE": "刀劍型態",
    "SHIELD": "盾牌型態",
    "SCHOOL": "魚群型態",
    "SOLO": "單獨型態",
    "BUSTED": "破裂型態",
    "RESOLUTE": "覺悟型態",
    "ASH": "小智型態",
    "MEGA": "超級進化",
    "MEGA_X": "超級進化Ｘ",
    "MEGA_Y": "超級進化Ｙ",
    "DYNAMAX": "極巨化",
    "GIGANTAMAX": "超極巨化",
    "PRIMAL": "原始回歸",
    "ETERNAMAX": "無極巨化",
    "ULTRA": "究極爆發",
    "CROWNED_SWORD": "劍之王",
    "CROWNED_SHIELD": "盾之王",
    "DAWN_WINGS": "拂曉之翼",
    "DUSK_MANE": "黃昏之鬃",
    "CONFINED": "懲戒型態",
    "UNBOUND": "解放型態",
    "COMPLETE": "完全體型態",
    "TEN_PERCENT": "百分之十型態",
    "FIFTY_PERCENT": "百分之五十型態",
    "COMPLETE_TEN_PERCENT": "百分之十完全體型態",
    "COMPLETE_FIFTY_PERCENT": "百分之五十完全體型態",
    "GALARIAN_STANDARD": "伽勒爾一般模式",
    "GALARIAN_ZEN": "伽勒爾達摩模式",
    "STANDARD": "一般模式",
    "ZEN": "達摩模式",
    "AMPED": "高調型態",
    "LOW_KEY": "低調型態",
    "ARIA": "歌聲型態",
    "PIROUETTE": "舞步型態",
    "BAILE": "熱辣熱辣風格",
    "POMPOM": "啪滋啪滋風格",
    "PAU": "呼拉呼拉風格",
    "SENSU": "輕盈輕盈風格",
    "SINGLE_STRIKE": "一擊流",
    "RAPID_STRIKE": "連擊流",
    "HERO": "全能型態",
    "HANGRY": "空腹花紋",
    "FULL_BELLY": "滿腹花紋",
    "DISGUISED": "化形的樣子",
    "BUSTED": "現形的樣子",
    "BREAD_DOUGH_MODE": "發酵型態",
    "BREAD_DOUGH_MODE_2": "烘焙型態",
    "FAMILY_OF_THREE": "三隻家庭",
    "FAMILY_OF_FOUR": "四隻家庭",
    "THREE": "三隻家庭",
    "TWO": "二節形態",
    "SMALL": "小型",
    "AVERAGE": "中型",
    "LARGE": "大型",
    "SUPER": "特大型",
    "BLUE_STRIPED": "藍條紋型態",
    "RED_STRIPED": "紅條紋型態",
    "WHITE_STRIPED": "白條紋型態",
    "EAST_SEA": "東海型態",
    "WEST_SEA": "西海型態",
    "BURMY_PLANT": "草木蓑衣",
    "BURMY_SANDY": "砂土蓑衣",
    "BURMY_TRASH": "垃圾蓑衣",
    "WORMADAM_PLANT": "草木蓑衣",
    "WORMADAM_SANDY": "砂土蓑衣",
    "WORMADAM_TRASH": "垃圾蓑衣",
    "OVERCAST": "陰天型態",
    "SUNNY": "太陽型態",
    "RAINY": "雨水型態",
    "SNOWY": "雪雲型態",
    "HEAT": "加熱洛托姆",
    "WASH": "清洗洛托姆",
    "FROST": "結冰洛托姆",
    "FAN": "旋轉洛托姆",
    "MOW": "切割洛托姆",
    "SPRING": "春天的樣子",
    "SUMMER": "夏天的樣子",
    "AUTUMN": "秋天的樣子",
    "WINTER": "冬天的樣子",
    "NATURAL": "自然型態",
    "PHONY": "贗品型態",
    "ANTIQUE": "真品型態",
    "COUNTERFEIT": "仿製品型態",
    "ARTISAN": "高檔品型態",
    "ORDINARY": "凡作型態",
    "UNREMARKABLE": "凡庸型態",
    "MASTERPIECE": "傑作型態",
    "POKEBALL": "精靈球花紋",
    "FANCY": "幻彩花紋",
    "ICY_SNOW": "冰雪花紋",
    "POLAR": "雪國花紋",
    "TUNDRA": "雪原花紋",
    "CONTINENTAL": "大陸花紋",
    "GARDEN": "庭園花紋",
    "ELEGANT": "雅致花紋",
    "MEADOW": "花園花紋",
    "MODERN": "摩登花紋",
    "MARINE": "大海花紋",
    "ARCHIPELAGO": "群島花紋",
    "HIGH_PLAINS": "荒野花紋",
    "SANDSTORM": "沙塵花紋",
    "RIVER": "大河花紋",
    "MONSOON": "驟雨花紋",
    "SAVANNA": "熱帶草原花紋",
    "SUN": "太陽花紋",
    "OCEAN": "大洋花紋",
    "JUNGLE": "叢林花紋",
    "MALE": "雄性型態",
    "FEMALE": "雌性型態",
    "S": "暗影專用型態",
    "APEX": "衝馳型態",
    "ULTIMATE": "驅馳型態",
    "BURN": "火焰卡帶",
    "CHILL": "冰凍卡帶",
    "DOUSE": "水流卡帶",
    "SHOCK": "閃電卡帶",
    "CURLY": "上弓姿勢",
    "DROOPY": "下垂姿勢",
    "STRETCHY": "平挺姿勢",
    "GORGING": "大口吞型態",
    "GULPING": "一口吞型態",
    "NOICE": "解凍頭型態",
    "ICE_RIDER": "騎白馬的樣子",
    "SHADOW_RIDER": "騎黑馬的樣子",
    "ZERO": "平凡型態",
    "NEUTRAL": "放鬆模式",
    "ORIGINAL_COLOR": "初始顏色",
    "PALDEA": "帕底亞型態",
    "PALDEA_COMBAT": "帕底亞肯泰羅・鬥戰種",
    "PALDEA_BLAZE": "帕底亞肯泰羅・火熾種",
    "PALDEA_AQUA": "帕底亞肯泰羅・水瀾種",
    "PLANT": "草木蓑衣",
    "SANDY": "砂土蓑衣",
    "TRASH": "垃圾蓑衣",
    "DANDY": "紳士造型",
    "DEBUTANTE": "淑女造型",
    "DIAMOND": "鑽石造型",
    "HEART": "愛心造型",
    "KABUKI": "歌舞伎造型",
    "LA_REINE": "皇后造型",
    "MATRON": "貴婦造型",
    "PHARAOH": "法老造型",
    "STAR": "明星造型",
    "INDIGO": "靛藍色核心",
    "VIOLET": "紫羅蘭色核心",
}

TYPE_FORM_LABELS = {
    "NORMAL": "一般屬性型態", "FIRE": "火屬性型態", "WATER": "水屬性型態",
    "ELECTRIC": "電屬性型態", "GRASS": "草屬性型態", "ICE": "冰屬性型態",
    "FIGHTING": "格鬥屬性型態", "POISON": "毒屬性型態", "GROUND": "地面屬性型態",
    "FLYING": "飛行屬性型態", "PSYCHIC": "超能力屬性型態", "BUG": "蟲屬性型態",
    "ROCK": "岩石屬性型態", "GHOST": "幽靈屬性型態", "DRAGON": "龍屬性型態",
    "DARK": "惡屬性型態", "STEEL": "鋼屬性型態", "FAIRY": "妖精屬性型態",
}

COLOR_FORM_LABELS = {
    "RED": "紅色型態", "ORANGE": "橙色型態", "YELLOW": "黃色型態",
    "GREEN": "綠色型態", "BLUE": "藍色型態", "WHITE": "白色型態", "BLACK": "黑色型態",
}

CITY_LABELS = {
    "AMSTERDAM": "阿姆斯特丹",
    "ANAHEIM": "安納罕",
    "ARIZONA": "亞利桑那",
    "BANGKOK": "曼谷",
    "BOSTON": "波士頓",
    "BRISBANE": "布里斯本",
    "BUENOSAIRES": "布宜諾斯艾利斯",
    "CANCUN": "坎昆",
    "CHICAGO": "芝加哥",
    "CLEVELAND": "克里夫蘭",
    "COLOGNE": "科隆",
    "COPENHAGEN": "哥本哈根",
    "HAGUE": "海牙",
    "JERSEYCITY": "澤西城",
    "LISBON": "里斯本",
    "LOSANGELES": "洛杉磯",
    "MALAGA": "馬拉加",
    "MANCHESTER": "曼徹斯特",
    "MARSEILLE": "馬賽",
    "MIAMI": "邁阿密",
    "MILAN": "米蘭",
    "MUNICH": "慕尼黑",
    "NEWYORK": "紐約",
    "PARIS": "巴黎",
    "RIO": "里約熱內盧",
    "SANFRANCISCO": "舊金山",
    "SANTIAGO": "聖地牙哥",
    "SAOPAULO": "聖保羅",
    "SINGAPORE": "新加坡",
    "SYDNEY": "雪梨",
    "VANCOUVER": "溫哥華",
    "VALENCIA": "瓦倫西亞",
    "WASHINGTON": "華盛頓",
    "BALI": "峇里島",
    "BARCELONA": "巴塞隆納",
    "HONGKONG": "香港",
    "INCHEON": "仁川",
    "JAKARTA": "雅加達",
    "FUKUOKA": "福岡",
    "HIROSHIMA": "廣島",
    "HOKKAIDO": "北海道",
    "KAGAWA": "香川",
    "KANAZAWA": "金澤",
    "KYOTO": "京都",
    "MEXICOCITY": "墨西哥城",
    "NAGOYA": "名古屋",
    "NAGASAKI": "長崎",
    "OKINAWA": "沖繩",
    "OSAKA": "大阪",
    "SAPPORO": "札幌",
    "SEOUL": "首爾",
    "SURABAYA": "泗水",
    "TAIPEI": "台北",
    "TOHOKU": "東北",
    "TOKYO": "東京",
    "YOKOHAMA": "橫濱",
    "YOGYAKARTA": "日惹",
    "AICHI": "愛知",
    "AKITA": "秋田",
    "AOMORI": "青森",
    "CHIBA": "千葉",
    "EHIME": "愛媛",
    "FUKUI": "福井",
    "FUKUSHIMA": "福島",
    "GIFU": "岐阜",
    "HYOGO": "兵庫",
    "IBARAKI": "茨城",
    "ISHIKAWA": "石川",
    "IWATE": "岩手",
    "KAGOSHIMA": "鹿兒島",
    "KOCHI": "高知",
    "MIE": "三重",
    "MIYAGI": "宮城",
    "MIYAZAKI": "宮崎",
    "NARA": "奈良",
    "NIIGATA": "新潟",
    "OKAYAMA": "岡山",
    "SAGA": "佐賀",
    "SAITAMA": "埼玉",
    "SHIGA": "滋賀",
    "SHIMANE": "島根",
    "SHIZUOKA": "靜岡",
    "TOCHIGI": "栃木",
    "TOKUSHIMA": "德島",
    "TOTTORI": "鳥取",
    "TOYAMA": "富山",
    "WAKAYAMA": "和歌山",
    "YAMAGATA": "山形",
    "YAMAGUCHI": "山口",
}

COSTUME_LABELS = {
    "FASHION": "時尚",
    "HOLIDAY": "節慶",
    "HALLOWEEN": "萬聖節",
    "SPRING": "春季",
    "FALL": "秋季",
    "SUMMER": "夏季",
    "WINTER": "冬季",
    "PARTY": "派對",
    "GOFEST": "Pokémon 大型慶典",
    "GOTOUR": "Pokémon 巡迴活動",
    "WILDAREA": "Pokémon 荒野地區活動",
    "SWIM": "泳裝",
    "WORLDS": "世界賽",
    "SANTA": "聖誕",
    "NEWYEAR": "新年",
    "VALENTINE": "情人節",
    "ADVENTURE": "冒險",
    "ANNIVERSARY": "週年紀念",
    "FLYING": "飛行氣球",
    "TRAIN": "列車",
    "CONDUCTOR": "車掌",
    "MALAYSIA": "馬來西亞",
    "PHILIPPINE": "菲律賓",
    "SINGAPORE": "新加坡",
    "TAIWAN": "台灣",
    "VALOR": "勇氣隊",
    "INSTINCT": "直覺隊",
    "MYSTIC": "神秘隊",
    "TCG": "集換式卡牌活動",
    "COIN": "金幣款",
}

COSTUME_SPECIAL_LABELS = {
    "FALL_2019": "2019 萬聖節裝扮",
    "JAN_2020_NOEVOLVE": "派對帽（2020）",
    "SPRING_2020_NOEVOLVE": "皮卡丘遮陽帽（2020）",
    "MAY_2019_NOEVOLVE": "名偵探帽（2019）",
    "FALL_2023_NOEVOLVE": "2023 秋季活動裝扮",
    "MOVIE_2020": "探險帽（2020）",
    "COPY_2019": "複製外觀（2019）",
    "ADVENTURE_HAT_2020": "冒險帽（2020）",
    "KARIYUSHI": "沖繩嘉利衫",
    "POP_STAR": "流行歌手裝扮",
    "ROCK_STAR": "搖滾歌手裝扮",
    "NIGHTCAP": "睡帽",
    "4THANNIVERSARY": "4 週年紀念裝扮",
    "5THANNIVERSARY": "5 週年紀念裝扮",
    "ANNIVERSARY": "週年紀念裝扮",
    "POPSTAR": "流行歌手裝扮",
    "ROCKSTAR": "搖滾歌手裝扮",
    "PI_NOEVOLVE": "派對裝扮（不可進化）",
    "ONE_YEAR_ANNIVERSARY": "一週年紀念裝扮",
    "COSTUME_2020": "4 週年飛行氣球裝扮",
    "FLYING_5TH_ANNIV": "5 週年飛行氣球裝扮",
    "FLYING_OKINAWA": "沖繩飛行氣球裝扮",
    "KANTO_2020_NOEVOLVE": "關都主題帽（2020，不可進化）",
    "JOHTO_2020_NOEVOLVE": "城都主題帽（2020，不可進化）",
    "HOENN_2020_NOEVOLVE": "豐緣主題帽（2020，不可進化）",
    "SINNOH_2020_NOEVOLVE": "神奧主題帽（2020，不可進化）",
    "SAFARI_2020_NOEVOLVE": "探險帽（2020，不可進化）",
}

SPORT_TEAM_LABELS = {
    "ARIZONADIAMONDBACKS": "亞利桑那響尾蛇",
    "BALTIMOREORIOLES": "巴爾的摩金鶯",
    "BOSTONREDSOX": "波士頓紅襪",
    "CHICAGOWHITESOX": "芝加哥白襪",
    "CLEVELANDGUARDIANS": "克里夫蘭守護者",
    "MARINERS": "西雅圖水手",
    "MARLINS": "邁阿密馬林魚",
    "MILWAUKEEBREWERS": "密爾瓦基釀酒人",
    "MINNESOTATWINS": "明尼蘇達雙城",
    "NEWYORKMETS": "紐約大都會",
    "SANFRANCISCOGIANTS": "舊金山巨人",
    "TAMPABAYRAYS": "坦帕灣光芒",
    "TEXASRANGERS": "德州遊騎兵",
    "WASHINGTONNATIONALS": "華盛頓國民",
    "LOTTEGIANTS": "樂天巨人",
    "CARDINALS": "亞利桑那紅雀",
    "BELLUNA": "貝爾納球場",
    "CHUNICHI DRAGONS": "中日龍",
    "CHUNICHIDRAGONS": "中日龍",
    "HIROSHIMACARP": "廣島鯉魚",
    "HOKKAIDOFIGHTERS": "北海道日本火腿鬥士",
    "KOSHIENHANSHINTIGERS": "阪神虎甲子園球場",
    "KYOCERA": "京瓷巨蛋",
    "RAKUTENEAGLES": "東北樂天金鷲",
    "SOFTBANKHAWKS": "福岡軟銀鷹",
    "TOKYOSWALLOWS": "東京養樂多燕子",
    "TOYAMAALLSTARS": "富山全明星",
    "YOKOHAMASTADIUM": "橫濱球場",
    "YOMIURIGIANTS": "讀賣巨人",
    "ZOZOMARINE": "佐佐海洋球場",
}


def background_category(background_key: str, background_type: str, city: str | None) -> tuple[str, str]:
    """依背景主鍵的活動線索分組；未知活動保留在可辨識的其他分類。"""
    key = normalize(background_key)
    if key == "NONE":
        return "NONE", "無背景"
    if "GOFEST" in key:
        if "GLOBAL" in key:
            return "GO_FEST_GLOBAL", "GO Fest 全球背卡"
        return "GO_FEST_REGIONAL", "GO Fest 地區背卡"
    if any(token in key for token in ("MLB", "NPB", "KBO", "BASEBALL", "BALLPARK")):
        return "BASEBALL_STADIUM", "棒球場背卡"
    if "CITYSAFARI" in key:
        return "CITY_SAFARI", "City Safari 背卡"
    if "SAFARIZONE" in key or "SAFARI_ZONE" in key:
        return "SAFARI_ZONE", "Safari Zone 背卡"
    if "GOTOUR" in key:
        return "GO_TOUR", "GO Tour 背卡"
    if "WORLDCHAMPIONSHIP" in key or "WORLDS" in key or "WCS" in key:
        return "WORLD_CHAMPIONSHIPS", "Pokémon World Championships 背卡"
    if "ROADTRIP" in key:
        return "ROAD_TRIP", "公路巡迴背卡"
    if "AIRADVENTURE" in key or "AIRADV" in key:
        return "AIR_ADVENTURES", "飛行冒險背卡"
    if "COMICCON" in key:
        return "COMIC_CON", "漫畫展活動背卡"
    if any(token in key for token in ("CARNIVAL", "FESTIVAL", "CHERRYBLOSSOM", "SUMMERFEST", "WINTERFEST")):
        return "FESTIVAL", "節慶活動背卡"
    if any(token in key for token in ("NFL", "NBA", "NHL", "MLS", "SOCCER", "CRICKET", "RUGBY", "SPORT")):
        return "SPORTS", "其他運動賽事背卡"
    if "GOWA" in key or "WILDAREA" in key:
        return "GO_EVENT_AREA", "GO Wild Area 背卡"
    if "SPECIAL" in key and "GLOBAL" in key:
        return "SPECIAL_GLOBAL", "全球特殊活動背卡"
    if city or background_type == "LOCATION":
        return "OTHER_LOCATION", "其他地點背卡"
    return "OTHER_SPECIAL", "其他特殊背卡"


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def normalize(value: str) -> str:
    return re.sub(r"[^A-Z0-9]+", "_", value.upper()).strip("_")


def form_display(form_key: str, seen: dict[str, int], species_key: str | None = None) -> str:
    if species_key == "UNOWN" and (re.fullmatch(r"[A-Z]", form_key) or form_key in {"EXCLAMATION_POINT", "QUESTION_MARK"}):
        symbol = form_key.replace("EXCLAMATION_POINT", "驚嘆號").replace("QUESTION_MARK", "問號")
        return f"未知圖騰「{symbol}」"
    if form_key in FORM_LABELS:
        return FORM_LABELS[form_key]
    if form_key in TYPE_FORM_LABELS:
        return TYPE_FORM_LABELS[form_key]
    if form_key in COLOR_FORM_LABELS:
        return COLOR_FORM_LABELS[form_key]
    if form_key.startswith("UNOWN_"):
        symbol = form_key.removeprefix("UNOWN_").replace("EXCLAMATION_POINT", "驚嘆號").replace("QUESTION_MARK", "問號")
        return f"未知圖騰「{symbol}」"
    if re.fullmatch(r"\d{2}", form_key):
        return f"花紋型態 {int(form_key) + 1}"
    # 上游只給縮寫而沒有正式名稱時，不再把多個不同外觀都顯示成
    # 「特殊型態」。保留穩定、全中文且可區分的款式序號。
    seen[form_key] += 1
    return f"未命名型態（款式 {sorted(seen).index(form_key) + 1}）"


def asset_image_key(path: str) -> str:
    return f"pogo/{path}"


def parse_icon_filename(path: str) -> dict | None:
    name = pathlib.PurePosixPath(path).name
    if not name.endswith(".icon.png"):
        return None
    stem = name[: -len(".icon.png")]
    shiny = stem.endswith(".s")
    if shiny:
        stem = stem[:-2]
    stem = re.sub(r"\.g\d+$", "", stem, flags=re.IGNORECASE)
    match = re.match(r"^pm(\d+)(.*)$", stem, flags=re.IGNORECASE)
    if match is None:
        return None
    rest = match.group(2)
    form_match = re.search(r"\.f([^.]+)", rest, flags=re.IGNORECASE)
    costume_match = re.search(r"\.c([^.]+)", rest, flags=re.IGNORECASE)
    return {
        "dexNumber": int(match.group(1)),
        "formKey": normalize(form_match.group(1)) if form_match else "NORMAL",
        "costumeKey": normalize(costume_match.group(1)) if costume_match else None,
        "shiny": shiny,
        "imageKey": asset_image_key(path),
        "format": "addressable",
        "slot": 0,
    }


def parse_modern_icon_filename(path: str) -> dict | None:
    """解析新版 pokemon_icon_pmXXXX_00_pgo_*.png 圖片。

    PokeMiners 同時保留舊的 Addressable Assets 命名與新版圖片命名。
    新版檔名才包含部分早期裝扮（例如 FALL_2019、MOVIE_2020），
    因此不能只解析 pmXXXX.cCOSTUME.icon.png。
    """
    prefix = "Images/Pokemon - 256x256/"
    if not path.startswith(prefix):
        return None
    name = pathlib.PurePosixPath(path).name
    if not name.lower().endswith(".png"):
        return None
    stem = name[:-4]
    shiny = stem.lower().endswith("_shiny")
    if shiny:
        stem = stem[:-6]
    match = re.match(r"^pokemon_icon_pm(\d{4})_(\d{2})(?:_(.+))?$", stem, flags=re.IGNORECASE)
    if match is None:
        return None
    descriptor = match.group(3)
    if not descriptor:
        return None
    costume_key = costume_key_from_asset_suffix(descriptor)
    if not costume_key or costume_key in {"NORMAL", "DEFAULT"}:
        return None
    return {
        "dexNumber": int(match.group(1)),
        "formKey": "NORMAL",
        "costumeKey": costume_key,
        "shiny": shiny,
        "imageKey": asset_image_key(path),
        "format": "modern",
        "slot": int(match.group(2)),
    }


def costume_key_from_asset_suffix(value: str) -> str | None:
    """將圖片／Game Master assetBundleSuffix 正規化成可比對的裝扮鍵。"""
    descriptor = value.lower()
    descriptor = re.sub(r"^pm\d+_\d+_", "", descriptor)
    # 圖檔前綴可能是 pgo_fall2019，也可能是 pikachu_pgo_kariyushi。
    descriptor = re.sub(r"^pgo_", "", descriptor)
    descriptor = re.sub(r"^[a-z0-9]+_pgo_", "", descriptor)
    if descriptor in {"", "pgo", "normal", "default"}:
        return None
    descriptor = re.sub(r"(?<=[a-z])(?=\d)", "_", descriptor)
    result = normalize(descriptor)
    return result or None


def is_form_like_costume(costume_key: str) -> bool:
    """排除其實是型態而不是裝扮的新版圖片。"""
    form_tokens = {
        "ALOLA", "ALOLAN", "GALARIAN", "HISUIAN", "PALDEAN", "ORIGIN",
        "ALTERED", "THERIAN", "INCARNATE", "ATTACK", "DEFENSE", "SPEED",
        "DUSK", "DAWN", "MIDDAY", "MIDNIGHT", "SKY", "LAND", "BLADE",
        "SHIELD", "SCHOOL", "SOLO", "RESOLUTE", "ASH", "MEGA", "PRIMAL",
        "DYNAMAX", "GIGANTAMAX", "SHADOW", "PURIFIED",
    }
    return any(token in costume_key.split("_") for token in form_tokens)


COSTUME_FORM_MARKERS = {
    "ANNIVERSARY", "APRIL", "BB", "BANDANA", "CAP", "CARNIVAL", "COSTUME", "COPY",
    "DIWALI", "DOCTOR", "FALL", "FESTIVAL", "FOSSIL", "FLYING", "GEMS", "GOFEST",
    "GOTOUR", "GOGGLES", "HALLOWEEN", "HAT", "HORIZONS", "HOLIDAY", "INDONESIA",
    "JEJU", "KARIYUSHI", "KURTA", "MAY", "MONOCLE", "MOVIE", "NIGHTCAP", "PI",
    "POP", "PXP", "ROCK", "SAFARI", "SPRING", "SUMMER", "SWIM", "TCG", "TSHIRT", "VALOR",
    "VISOR", "VS", "WCS", "WILDAREA", "WINTER", "CHRISTMAS", "PARTY", "ROYAL",
}


def is_costume_form_key(form_key: str) -> bool:
    """辨識 Game Master 的活動裝扮型態，並把它放回裝扮選擇器。"""
    tokens = set(form_key.split("_"))
    if not tokens or tokens == {"NORMAL"}:
        return False
    # 這些單字本身也會是季節型態或屬性型態（例如 四季鹿、銀伴戰獸），
    # 只有帶年份、活動或服飾細節時才視為裝扮。
    if form_key in {"SPRING", "SUMMER", "FALL", "WINTER", "FLYING", "ROCK"}:
        return False
    if re.fullmatch(r"20\d{2}", form_key) or re.match(r"K_20\d{2}(?:_|$)", form_key):
        return True
    # 地區、戰鬥、融合與永久型態仍然留在 Form，不應被誤當成裝扮。
    if is_form_like_costume(form_key) or tokens.intersection({"MALE", "FEMALE", "GENDERLESS"}):
        return False
    return bool(tokens.intersection(COSTUME_FORM_MARKERS))


def identifier_tokens(value: str) -> set[str]:
    """把 camelCase、年份與活動名稱拆成可比對的語意 token。"""
    text = re.sub(r"(?<=[a-z])(?=[A-Z])", "_", value)
    text = text.upper().replace("SPECIALBACKGROUND", "SPECIAL_BACKGROUND")
    text = text.replace("LOCATIONCARD", "LOCATION_CARD")
    parts = re.findall(r"[A-Z]+|\d+", text)
    combined: list[str] = []
    combine_pairs = {
        ("GO", "FEST"): "GOFEST",
        ("GO", "TOUR"): "GOTOUR",
        ("CITY", "SAFARI"): "CITYSAFARI",
        ("AIR", "ADV"): "AIRADV",
        ("WORLD", "CHAMPIONSHIPS"): "WORLDCHAMPIONSHIPS",
        ("WORLD", "CHAMPIONSHIP"): "WORLDCHAMPIONSHIP",
        ("SPECIAL", "BACKGROUND"): "SPECIAL_BACKGROUND",
        ("NATIONAL", "TRUST"): "NATIONALTRUST",
        ("MID", "AUTUMN"): "MIDAUTUMN",
        ("TEAM", "LEADER"): "TEAMLEADER",
        ("WORLD", "SPECIAL"): "WORLDSPECIAL",
        ("CAR", "FREE"): "CARFREE",
    }
    index = 0
    while index < len(parts):
        if index + 1 < len(parts) and (parts[index], parts[index + 1]) in combine_pairs:
            combined.append(combine_pairs[(parts[index], parts[index + 1])])
            index += 2
        else:
            combined.append(parts[index])
            index += 1
    return {token for token in combined if token not in {"LC", "SB", "LOCATION", "CARD", "SPECIAL", "BACKGROUND"}}


def background_detail_label(tokens: set[str]) -> str | None:
    """將同一活動下的款式補成可辨識的中文名稱。"""
    detail_rules = [
        ({"BLACK", "WHITE"}, "黑白款"),
        ({"WORMHOLE", "MOON"}, "月亮蟲洞款"),
        ({"WORMHOLE", "SUN"}, "太陽蟲洞款"),
        ({"WORMHOLE"}, "蟲洞款"),
        ({"SHIELD", "CROWNED"}, "王者之盾款"),
        ({"SWORD", "CROWNED"}, "王者之劍款"),
        ({"SHIELD"}, "盾牌款"),
        ({"SWORD"}, "劍款"),
        ({"ETERNATUS"}, "無極汰那款"),
        ({"MEWTWO"}, "超夢款"),
        ({"DIAMOND"}, "鑽石款"),
        ({"GOLD"}, "金色款"),
        ({"MEGA"}, "超級進化款"),
        ({"PEARL"}, "珍珠款"),
        ({"RUBY"}, "紅寶石款"),
        ({"SAPPHIRE"}, "藍寶石款"),
        ({"SILVER"}, "銀色款"),
        ({"ENIGMA"}, "謎團款"),
        ({"RADIANCE"}, "光輝款"),
        ({"UMBRA"}, "暗影款"),
        ({"REGI"}, "雷吉系列款"),
        ({"X"}, "Ｘ款"),
        ({"Y"}, "Ｙ款"),
        ({"BLUE"}, "藍隊款"),
        ({"RED"}, "紅隊款"),
        ({"YELLOW"}, "黃隊款"),
        ({"DUELDESTINY"}, "決鬥命運"),
        ({"MIGHTANDMASTERY"}, "力量與精通"),
        ({"DELIGHTFULDAYS"}, "歡樂時光"),
        ({"TALESOFTRANSFORMATION"}, "變化故事"),
        ({"DUEL", "DESTINY"}, "決鬥命運"),
        ({"MIGHT", "AND", "MASTERY"}, "力量與精通"),
        ({"DELIGHTFUL", "DAYS"}, "歡樂時光"),
        ({"TALES", "OF", "TRANSFORMATION"}, "變化故事"),
        ({"FESTIVAL", "COLORS"}, "色彩節"),
    ]
    for required, label in detail_rules:
        if required.issubset(tokens):
            return label
    return None


def background_asset_label(path: str, background_key: str | None = None) -> str:
    """將圖片檔名轉成使用者可辨識的名稱，不把代號當成標題。"""
    stem = pathlib.PurePosixPath(path).stem
    stem = re.sub(r"^(?:lc|sb)_", "", stem, flags=re.IGNORECASE)
    tokens = identifier_tokens(stem)
    if background_key:
        tokens |= identifier_tokens(background_key)
    compact = re.sub(r"[^A-Z0-9]", "", normalize(f"{stem}_{background_key or ''}"))
    year = next((token for token in tokens if re.fullmatch(r"20\d{2}", token)), None)
    city = next((label for marker, label in CITY_LABELS.items() if marker in compact), None)
    team = next((label for marker, label in SPORT_TEAM_LABELS.items() if marker in compact), None)
    detail = background_detail_label(tokens)
    event_labels = [
        ("GOFEST", "GO Fest"),
        ("GOTOUR", "GO Tour"),
        ("CITYSAFARI", "City Safari"),
        ("SAFARIZONE", "Safari Zone"),
        ("MLB", "美國職棒"),
        ("NPB", "日本職棒"),
        ("KBO", "韓國職棒"),
        ("WCS", "Pokémon World Championships"),
        ("WORLDCHAMPIONSHIP", "Pokémon World Championships"),
        ("ROADTRIP", "公路巡迴"),
        ("AIRADV", "飛行冒險"),
        ("COMICCON", "漫畫展"),
        ("FESTIVAL", "節慶活動"),
        ("CARNIVAL", "嘉年華"),
        ("POKECENTER", "寶可夢中心"),
        ("POKELID", "寶可夢人孔蓋"),
        ("NATIONALTRUST", "英國國民信託景點"),
        ("ESA", "太空景點"),
        ("GOWA", "GO Wild Area"),
        ("TPC30TH", "寶可夢 30 週年活動"),
        ("TOKMUN", "東京灣活動"),
        ("CARFREE", "無車日活動"),
        ("ANNIVERSARY", "週年活動"),
        ("COMMUNITY", "社群日活動"),
        ("CONCIERGE", "活動服務背卡"),
        ("LEGO", "積木合作活動"),
        ("MIDAUTUMN", "中秋活動"),
        ("PATTERNWILD", "野外花紋活動"),
        ("SEASON", "賽季活動"),
        ("TEAMLEADER", "隊長活動"),
        ("WORLDSPECIAL", "世界特殊活動"),
    ]
    event = next((label for marker, label in event_labels if marker in tokens), None)
    if team:
        return f"{event or '運動賽事'}｜{team}"
    if event and year and "GLOBAL" in tokens:
        return f"{event} 全球 {year}" + (f"｜{detail}" if detail else "")
    if event and year and city:
        return f"{event} {year}｜{city}" + (f"｜{detail}" if detail else "")
    if event and year:
        return f"{event} {year}" + (f"｜{detail}" if detail else "")
    if event and city:
        return f"{event}｜{city}" + (f"｜{detail}" if detail else "")
    if event:
        return f"{event}背卡" + (f"｜{detail}" if detail else "")
    if city and year:
        return f"{city}活動背卡 {year}" + (f"｜{detail}" if detail else "")
    if city:
        return f"{city}活動背卡" + (f"｜{detail}" if detail else "")
    if year:
        return f"未確認活動背卡 {year}" + (f"｜{detail}" if detail else "")
    return "未確認活動背卡"


def background_key_label(background_key: str, index: int) -> str:
    """為沒有圖片的 Game Master 背卡建立可讀的暫時名稱。"""
    tokens = identifier_tokens(background_key)
    year = next((token for token in tokens if re.fullmatch(r"20\d{2}", token)), None)
    city = next((CITY_LABELS[token] for token in tokens if token in CITY_LABELS), None)
    detail = background_detail_label(tokens)
    event_labels = [
        ("GOFEST", "GO Fest"),
        ("GOTOUR", "GO Tour"),
        ("CITYSAFARI", "City Safari"),
        ("SAFARIZONE", "Safari Zone"),
        ("MLB", "美國職棒球場"),
        ("NPB", "日本職棒球場"),
        ("KBO", "韓國職棒球場"),
        ("WCS", "Pokémon World Championships"),
        ("WORLDCHAMPIONSHIP", "Pokémon World Championships"),
        ("WORLDCHAMPIONSHIPS", "Pokémon World Championships"),
        ("ROADTRIP", "公路巡迴"),
        ("AIRADV", "飛行冒險"),
        ("COMICCON", "漫畫展"),
        ("FESTIVAL", "節慶活動"),
        ("CARNIVAL", "嘉年華"),
        ("WILDAREA", "GO Wild Area"),
        ("ESA", "太空景點"),
        ("GOWA", "GO Wild Area"),
        ("TPC30TH", "寶可夢 30 週年活動"),
        ("TOKMUN", "東京灣活動"),
        ("ANNIVERSARY", "週年活動"),
        ("COMMUNITY", "社群日活動"),
        ("NATIONALTRUST", "英國國民信託景點"),
        ("MIDAUTUMN", "中秋活動"),
        ("CARFREE", "無車日活動"),
        ("SEASON", "賽季活動"),
        ("TEAMLEADER", "隊長活動"),
        ("WORLDSPECIAL", "世界特殊活動"),
        ("GLOBAL", "全球特殊活動"),
    ]
    event = next((label for marker, label in event_labels if marker in tokens), None)
    if event and year and "GLOBAL" in tokens:
        return f"{event} 全球 {year}" + (f"｜{detail}" if detail else "")
    if event and year and city:
        return f"{city}{event} {year}" + (f"｜{detail}" if detail else "")
    if event and year:
        return f"{event} {year}" + (f"｜{detail}" if detail else "")
    if event and city:
        return f"{city}{event}" + (f"｜{detail}" if detail else "")
    if event:
        return f"{event}背卡" + (f"｜{detail}" if detail else "")
    if city and year:
        return f"{city}活動背卡 {year}" + (f"｜{detail}" if detail else "")
    if year:
        return f"未確認活動背卡 {year}" + (f"｜{detail}" if detail else "")
    return f"未確認活動背卡｜款式 {index:03d}"


def match_background_asset(background_key: str, asset_path: str) -> int:
    """回傳背景主鍵與圖片檔名的相似度；低分不自動配對。"""
    key_tokens = identifier_tokens(background_key)
    asset_tokens = identifier_tokens(pathlib.PurePosixPath(asset_path).stem)
    common = key_tokens & asset_tokens
    if not common:
        return 0
    score = len(common) * 10
    if any(re.fullmatch(r"20\d{2}", token) for token in common):
        score += 8
    if any(token in common for token in {"GOFEST", "GOTOUR", "CITYSAFARI", "MLB", "NPB", "KBO", "WCS", "ROADTRIP", "AIRADV"}):
        score += 8
    if background_key.startswith("ASSET_"):
        score -= 5
    key_variants = background_variant_tokens(key_tokens)
    asset_variants = background_variant_tokens(asset_tokens)
    if key_variants == asset_variants:
        score += 12
    elif asset_variants and asset_variants.issubset(key_variants):
        score += 4
    shared_core = background_core_variant_tokens(key_tokens) & background_core_variant_tokens(asset_tokens)
    if shared_core:
        score += 12
    return score


def background_variant_tokens(tokens: set[str]) -> set[str]:
    """移除年份、流水號與通用前綴，保留能區分背卡款式的 token。"""
    return {
        token
        for token in tokens
        if token not in {"GLOBAL", "LOCAL", "REGIONAL", "SPECIAL_BACKGROUND"}
        and not re.fullmatch(r"(?:20\d{2}|\d{1,3})", token)
    }


def background_core_variant_tokens(tokens: set[str]) -> set[str]:
    return background_variant_tokens(tokens) - {
        "GOFEST",
        "GOTOUR",
        "CITYSAFARI",
        "AIRADV",
        "AIRADVENTURES",
        "GOWA",
        "WCS",
        "WORLDCHAMPIONSHIP",
        "WORLDCHAMPIONSHIPS",
        "ROADTRIP",
        "MLB",
        "NPB",
        "KBO",
        "BASEBALL",
        "BALLPARK",
    }


def background_asset_has_variant_conflict(background_key: str, asset_path: str) -> bool:
    """避免只共享年份／活動代碼時，把不同款式誤配在一起。"""
    key_tokens = identifier_tokens(background_key)
    asset_tokens = identifier_tokens(pathlib.PurePosixPath(asset_path).stem)
    colors = {"RED", "GREEN", "BLUE", "YELLOW", "BLACK", "WHITE", "GOLD", "SILVER"}
    key_colors = key_tokens & colors
    asset_colors = asset_tokens & colors
    return bool(key_colors and asset_colors and key_colors != asset_colors)


def location_card(
    background_key: str,
    index: int,
    image_path: str | None = None,
    display_name_override: str | None = None,
    vfx_key: str | None = None,
) -> dict:
    raw = background_key
    upper = normalize(raw)
    year_match = re.search(r"20\d{2}", raw)
    year = int(year_match.group(0)) if year_match else None
    city = None
    for token, label in CITY_LABELS.items():
        if token in upper:
            city = label
            break
    kind = "SPECIAL" if "SPECIAL" in upper or "GLOBAL" in upper else "LOCATION"
    category_key, category_name = background_category(upper, kind, city)
    if display_name_override:
        display_name = display_name_override
    elif image_path:
        display_name = background_asset_label(image_path, upper)
    else:
        display_name = background_key_label(upper, index)
    event_name = display_name.split("｜", 1)[0].removesuffix("背卡").strip()
    if event_name.startswith("未確認活動"):
        event_name = category_name.removesuffix("背卡")
    event_key = normalize(f"{category_key}_{year or 'UNKNOWN'}_{event_name}")
    result = {
        "id": f"BACKGROUND_{upper}",
        "backgroundKey": upper,
        "displayName": display_name,
        "backgroundType": kind,
        "categoryKey": category_key,
        "categoryName": category_name,
        "eventKey": event_key,
        "eventName": event_name,
        "locationName": city,
        "year": year,
        "availableFrom": None,
        "availableUntil": None,
        "dataSource": "ASSET_ONLY" if upper.startswith("ASSET_") else "GAME_MASTER",
        "vfxKey": vfx_key,
        "vfxKeys": vfx_key,
        "effectNote": None,
        "previewImageKey": None,
        "previewSource": None,
        "sortOrder": index * 10,
    }
    if image_path:
        result["imageKey"] = asset_image_key(image_path)
    return result


def collectible_background_identity(background_key: str) -> str:
    """將同一收藏背卡的內部特效代號歸到同一穩定身分。

    流水號通常是依 Pokémon／型態切換的 VFX，不是玩家需要分開選擇的
    Collection Variant。劍／盾的一般與王者型態也是同一張收藏背卡。
    """
    if background_key == "SPECIALBACKGROUND_10ANI2026":
        return "SPECIALBACKGROUND_2026_10TH_ANNIVERSARY"
    identity = re.sub(r"_\d{3}$", "", background_key)
    identity = identity.replace("_SHIELD_CROWNED", "_SHIELD")
    identity = identity.replace("_SWORD_CROWNED", "_SWORD")
    return identity


def merge_background_effect_variants(backgrounds: list[dict]) -> list[dict]:
    groups: dict[tuple[str, str | None], list[dict]] = defaultdict(list)
    for background in backgrounds:
        identity = collectible_background_identity(background["backgroundKey"])
        image_group = None if identity == "SPECIALBACKGROUND_2026_10TH_ANNIVERSARY" else background.get("imageKey")
        groups[(identity, image_group)].append(background)

    merged: list[dict] = []
    for (identity, _), rows in groups.items():
        ordered = sorted(
            rows,
            key=lambda item: (
                0 if item["backgroundKey"] == "SPECIALBACKGROUND_2026_10TH_ANNIVERSARY_001" else 1,
                item["backgroundKey"],
            ),
        )
        canonical = dict(ordered[0])
        vfx_keys = sorted({item.get("vfxKey") for item in ordered if item.get("vfxKey")})
        canonical["vfxKey"] = vfx_keys[0] if vfx_keys else None
        canonical["vfxKeys"] = "|".join(vfx_keys) if vfx_keys else None
        canonical["aliasBackgroundKeys"] = [item["backgroundKey"] for item in ordered[1:]]
        if len(ordered) > 1:
            canonical["displayName"] = re.sub(r"｜款式 \d+$", "", canonical["displayName"])
            if "MEWTWO" in identity:
                canonical["displayName"] = "GO Fest 全球 2026｜超夢專屬背卡"
                canonical["categoryKey"] = "GO_FEST_GLOBAL"
                canonical["categoryName"] = "GO Fest 全球背卡"
                canonical["eventKey"] = "GO_FEST_GLOBAL_2026_GO_FEST_2026"
                canonical["eventName"] = "GO Fest 全球 2026"
                canonical["effectNote"] = "同一張收藏背卡；超級超夢Ｘ／Ｙ會顯示不同附加特效"
            elif "REGI" in identity:
                canonical["displayName"] = "GO Fest 全球 2025｜傳說巨人背卡"
                canonical["effectNote"] = "同一張收藏背卡；不同傳說巨人會顯示對應圖騰特效"
            elif "GLOBAL_MEGA" in identity:
                canonical["displayName"] = "GO Tour 全球 2026｜超級進化背卡"
                canonical["effectNote"] = "同一張收藏背卡；遊戲會依一般或強化效果切換附加動畫"
            elif "SHIELD" in identity:
                canonical["displayName"] = "GO Fest 全球 2025｜藏瑪然特背卡"
                canonical["effectNote"] = "同一張收藏背卡；一般與王者之盾型態共用收藏身分"
            elif "SWORD" in identity:
                canonical["displayName"] = "GO Fest 全球 2025｜蒼響背卡"
                canonical["effectNote"] = "同一張收藏背卡；一般與劍之王型態共用收藏身分"
            elif "10TH_ANNIVERSARY" in identity:
                canonical["displayName"] = "Pokémon GO 十週年慶典｜超夢背卡"
                canonical["categoryKey"] = "ANNIVERSARY"
                canonical["categoryName"] = "週年活動背卡"
                canonical["eventKey"] = "ANNIVERSARY_2026_10TH_ANNIVERSARY_CELEBRATION"
                canonical["eventName"] = "Pokémon GO 十週年慶典"
                canonical["availableFrom"] = "2026-08-12"
                canonical["availableUntil"] = "2026-09-06"
                canonical["effectNote"] = "同一張已發放的超夢收藏背卡；舊拆包暫存代號已合併"
            else:
                canonical["effectNote"] = f"同一張收藏背卡包含 {len(vfx_keys)} 組遊戲內特效"
        merged.append(canonical)
    return merged


def apply_known_background_metadata(backgrounds: list[dict]) -> None:
    """公開活動已確認後覆寫拆包暫名與日期；沒有可靠來源的項目維持待確認。"""
    overrides = {
        "SPECIALBACKGROUND_2026_GLOBAL_GOFEST_001": {
            "displayName": "GO Fest 全球 2026｜烈角犀獸圖騰背卡",
            "availableFrom": "2026-07-06",
            "availableUntil": "2026-07-12",
            "effectNote": "完整預覽包含烈角犀獸圖騰；遊戲內另有粒子動畫",
        },
        "SPECIALBACKGROUND_2026_MEWTWO_001": {
            "availableFrom": "2026-07-11",
            "availableUntil": "2026-07-12",
            "effectNote": "同一張收藏背卡；縮圖採超級進化完整預覽，超級超夢Ｘ／Ｙ的遊戲內動畫會不同",
        },
        "SPECIALBACKGROUND_2026_GLOBAL_MEGA_001": {
            "displayName": "GO Fest 2026：Mega Finale｜超級進化背卡",
            "categoryKey": "GO_FEST_GLOBAL",
            "categoryName": "GO Fest 全球背卡",
            "eventKey": "GO_FEST_GLOBAL_2026_MEGA_FINALE",
            "eventName": "GO Fest 2026：Mega Finale",
            "availableFrom": "2026-08-31",
            "availableUntil": "2026-09-06",
        },
        "SPECIALBACKGROUND_2026_WCS": {
            "displayName": "Pokémon World Championships 2026｜世界冠軍紀念背卡",
            "availableFrom": "2026-08-28",
            "availableUntil": "2026-08-30",
            "effectNote": "世界冠軍 2025 紀念研究獎勵；完整預覽包含藍色世界賽紋樣",
        },
        "SPECIALBACKGROUND_GG2026": {
            "displayName": "Pokémon GO 十週年｜索財靈背卡",
            "categoryKey": "ANNIVERSARY",
            "categoryName": "週年活動背卡",
            "eventKey": "ANNIVERSARY_2026_10TH_ANNIVERSARY",
            "eventName": "Pokémon GO 十週年",
        },
        "SPECIALBACKGROUND_2025_GLOBAL_ENIGMA_001": {"displayName": "GO Tour 全球 2025｜N 謎團背卡"},
        "SPECIALBACKGROUND_2025_GLOBAL_GOTOUR_BLACK_001": {"displayName": "GO Tour 全球 2025｜黑版背卡"},
        "SPECIALBACKGROUND_2025_GLOBAL_GOTOUR_BLACK_WHITE_001": {"displayName": "GO Tour 全球 2025｜黑白融合背卡"},
        "SPECIALBACKGROUND_2025_GLOBAL_GOTOUR_WHITE_001": {"displayName": "GO Tour 全球 2025｜白版背卡"},
    }
    for background in backgrounds:
        background.update(overrides.get(background["backgroundKey"], {}))

    # 部分 LocationCards 只有圖片檔，沒有在當期 Game Master 留下可解析的
    # locationCard 設定，因此原本會被錯誤放進「年份待確認」。這些活動已有
    # 公開活動名稱／年份，集中在這裡補上可讀資料；不會建立不存在的卡片。
    def apply_event(
        background: dict,
        *,
        display_name: str,
        category_key: str,
        category_name: str,
        event_key: str,
        event_name: str,
        year: int,
        available_from: str | None = None,
        available_until: str | None = None,
        effect_note: str | None = None,
    ) -> None:
        background.update(
            {
                "displayName": display_name,
                "categoryKey": category_key,
                "categoryName": category_name,
                "eventKey": event_key,
                "eventName": event_name,
                "year": year,
                "availableFrom": available_from,
                "availableUntil": available_until,
            }
        )
        if effect_note:
            background["effectNote"] = effect_note

    for background in backgrounds:
        key = background["backgroundKey"]
        if key == "ID_CAR_FREE_DAY":
            apply_event(
                background,
                display_name="Car Free Day Indonesia 2026｜無車日活動背卡",
                category_key="SEASONAL_EVENT",
                category_name="節慶活動背卡",
                event_key="CAR_FREE_DAY_2026_INDONESIA",
                event_name="Car Free Day Indonesia 2026",
                year=2026,
            )
        elif key.startswith("JAPAN_STAMP_RALLY_"):
            location = background.get("locationName") or background["displayName"].split("｜")[-1]
            apply_event(
                background,
                display_name=f"Pokémon Center 2026｜{location}",
                category_key="POKEMON_CENTER",
                category_name="Pokémon Center 背卡",
                event_key="POKEMON_CENTER_2026",
                event_name="Pokémon Center 2026",
                year=2026,
            )
        elif key == "JEJU_STAMP_RALLY":
            apply_event(
                background,
                display_name="Stamp Rally 2025｜濟州島",
                category_key="STAMP_RALLY",
                category_name="Stamp Rally 背卡",
                event_key="STAMP_RALLY_2025_JEJU",
                event_name="Stamp Rally 2025",
                year=2025,
            )
        elif key.startswith("NT_"):
            location = background.get("locationName") or background["displayName"].split("｜")[-1]
            apply_event(
                background,
                display_name=f"National Trust 2026｜{location}",
                category_key="NATIONAL_TRUST",
                category_name="National Trust 背卡",
                event_key="NATIONAL_TRUST_2026",
                event_name="National Trust 2026",
                year=2026,
            )
        elif key.startswith("POKELID_"):
            location = background.get("locationName") or background["displayName"].split("｜")[-1]
            old_2025_locations = {"FUKUOKA", "KAGOSHIMA", "MIYAZAKI", "NAGASAKI", "OKINAWA", "SAGA"}
            location_key = key.removeprefix("POKELID_")
            year = 2025 if location_key in old_2025_locations else 2026
            apply_event(
                background,
                display_name=f"Poké Lid {year}｜{location}",
                category_key="POKELID",
                category_name="Poké Lid 背卡",
                event_key=f"POKELID_{year}",
                event_name=f"Poké Lid {year}",
                year=year,
            )
        elif key == "SPECIALBACKGROUND_OBSERVATORY_EXHIBITION_TOUR":
            apply_event(
                background,
                display_name="Observatory Exhibition Tour 2025",
                category_key="OTHER_SPECIAL",
                category_name="特殊活動背卡",
                event_key="OBSERVATORY_EXHIBITION_TOUR_2025",
                event_name="Observatory Exhibition Tour 2025",
                year=2025,
            )
        elif key.startswith("SPECIALBACKGROUND_TEAM_"):
            team = key.removeprefix("SPECIALBACKGROUND_TEAM_")
            team_name = {"BLUE": "藍隊", "RED": "紅隊", "YELLOW": "黃隊"}.get(team, team)
            apply_event(
                background,
                display_name=f"隊長活動 2024｜{team_name}款",
                category_key="TEAM_LEADER",
                category_name="隊長活動背卡",
                event_key="TEAM_LEADER_2024",
                event_name="隊長活動 2024",
                year=2024,
            )
        elif key.startswith("TOKMUN_STAMP_"):
            location = background.get("locationName") or background["displayName"].split("｜")[-1]
            apply_event(
                background,
                display_name=f"Stamp Rally Tokyo Bay 2026｜{location}",
                category_key="STAMP_RALLY",
                category_name="Stamp Rally 背卡",
                event_key="STAMP_RALLY_2026_TOKYO_BAY",
                event_name="Stamp Rally Tokyo Bay 2026",
                year=2026,
            )
        elif key.startswith("ASSET_LC_ESA_"):
            location = background["displayName"].split("｜")[-1]
            apply_event(
                background,
                display_name=f"Pokémon GO × ESA 2026｜{location}",
                category_key="SPACE_EVENT",
                category_name="太空景點背卡",
                event_key="ESA_2026",
                event_name="Pokémon GO × ESA 2026",
                year=2026,
            )
        elif key.startswith("ASSET_LC_TPC30TH_"):
            location = {
                "ASSET_LC_TPC30TH_MALAYSIA": "馬來西亞",
                "ASSET_LC_TPC30TH_PHILIPPINES": "菲律賓",
                "ASSET_LC_TPC30TH_SINGAPORE": "新加坡",
                "ASSET_LC_TPC30TH_TAIWAN": "台灣",
            }.get(key, "地區款")
            apply_event(
                background,
                display_name=f"Pokémon 30 週年活動 2026｜{location}",
                category_key="ANNIVERSARY",
                category_name="週年活動背卡",
                event_key="TPC_30TH_ANNIVERSARY_2026",
                event_name="Pokémon 30 週年活動 2026",
                year=2026,
            )
        elif key == "ASSET_SB_MIDAUTUMN":
            apply_event(
                background,
                display_name="Dancing in the Moonlight 2026｜Clefairy 月光特殊背卡",
                category_key="SEASONAL_EVENT",
                category_name="節慶活動背卡",
                event_key="DANCING_IN_THE_MOONLIGHT_2026",
                event_name="Dancing in the Moonlight 2026",
                year=2026,
                available_from="2026-09-23",
                available_until="2026-09-28",
                effect_note="目前公開資產只提供一張靜態底圖；遊戲內月光效果不另拆成可收藏背卡。",
            )
        elif key == "ASSET_SB_PATTERNWILD":
            apply_event(
                background,
                display_name="Patterns of the Wild 2026｜Pikachu 蠟染服裝背卡",
                category_key="SEASONAL_EVENT",
                category_name="節慶活動背卡",
                event_key="PATTERNS_OF_THE_WILD_2026",
                event_name="Patterns of the Wild 2026",
                year=2026,
                available_from="2026-10-02",
                effect_note="已在公開資產中發現；活動尚未開始時標示為拆包資訊。",
            )


def disambiguate_background_names(backgrounds: list[dict]) -> None:
    """同名但不同 ID 的背卡以穩定款式編號區分，避免使用者只能猜資料代號。"""
    groups: dict[str, list[dict]] = defaultdict(list)
    for background in backgrounds:
        groups[background["displayName"]].append(background)
    for display_name, rows in groups.items():
        if len(rows) <= 1:
            continue
        for index, background in enumerate(sorted(rows, key=lambda item: item["backgroundKey"]), start=1):
            background["displayName"] = f"{display_name}｜款式 {index}"


def costume_display(costume_key: str, index: int) -> str:
    if costume_key in COSTUME_SPECIAL_LABELS:
        return COSTUME_SPECIAL_LABELS[costume_key]
    parts = []
    for token in costume_key.split("_"):
        if token in {"NOEVOLVE", "NOSTAGE"}:
            parts.append("不可進化")
            continue
        if token == "G2":
            continue
        if token in COSTUME_LABELS:
            parts.append(COSTUME_LABELS[token])
        elif token.isdigit() or (len(token) == 4 and token.startswith("20")):
            parts.append(token)
        elif token in {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"}:
            parts.append(f"{int({'JAN': '1', 'FEB': '2', 'MAR': '3', 'APR': '4', 'MAY': '5', 'JUN': '6', 'JUL': '7', 'AUG': '8', 'SEP': '9', 'OCT': '10', 'NOV': '11', 'DEC': '12'}[token])}月")
        elif token in {"HAT", "CAP"}:
            parts.append("帽子")
        elif token == "GOGGLES":
            parts.append("護目鏡")
        elif token == "MONOCLE":
            parts.append("單片眼鏡")
        elif token == "BANDANA":
            parts.append("頭巾")
        elif token == "VISOR":
            parts.append("遮陽帽")
        elif token == "TSHIRT":
            parts.append("短袖上衣")
        elif token == "DOCTOR":
            parts.append("醫生")
        elif token == "KURTA":
            parts.append("庫爾塔服飾")
        elif token == "FOSSIL":
            parts.append("化石")
        elif token == "BB":
            parts.append("棒球")
        elif token == "PI":
            parts.append("派對")
        elif token == "WCS":
            parts.append("世界賽")
        elif token == "BLUE":
            parts.append("藍色")
        elif token == "RED":
            parts.append("紅色")
        elif token == "YELLOW":
            parts.append("黃色")
        elif token == "GREEN":
            parts.append("綠色")
        elif token in {"A", "B", "C", "D", "E"}:
            parts.append(f"款式 {dict(A='甲', B='乙', C='丙', D='丁', E='戊')[token]}")
        elif token.startswith("0") and token.isdigit():
            parts.append(f"款式 {token.lstrip('0') or '0'}")
        elif token == "DIWALI":
            parts.append("排燈節")
        elif token == "HORIZONS":
            parts.append("寶可夢 地平線")
        elif token == "MTIARA":
            parts.append("月亮頭冠")
        elif token == "STIARA":
            parts.append("太陽頭冠")
        elif token == "MSCARF":
            parts.append("月亮圍巾")
        elif token == "SSCARF":
            parts.append("太陽圍巾")
    detail = " · ".join(parts)
    return f"{detail}裝扮" if detail else f"活動裝扮｜款式 {index:03d}"


def costume_release_year(costume_key: str) -> int | None:
    match = re.search(r"(?:19|20)\d{2}", costume_key)
    return int(match.group(0)) if match else None


def costume_event_name(costume_key: str, display_name: str) -> str:
    year = costume_release_year(costume_key)
    event_markers = [
        ("GOFEST", "Pokémon 大型慶典"), ("GOTOUR", "Pokémon 巡迴活動"),
        ("WORLDS", "Pokémon 世界錦標賽"), ("WCS", "Pokémon 世界錦標賽"),
        ("HALLOWEEN", "萬聖節活動"), ("HOLIDAY", "冬季節慶活動"),
        ("NEWYEAR", "新年活動"), ("SPRING", "春季活動"),
        ("SUMMER", "夏季活動"), ("FASHION", "時尚週活動"),
        ("ANNIVERSARY", "週年紀念活動"), ("ADVENTURE", "冒險活動"),
    ]
    event = next((label for marker, label in event_markers if marker in costume_key), None)
    if event:
        return f"{event} {year}" if year else event
    return f"{year} 年活動" if year else display_name


def main() -> None:
    parser = argparse.ArgumentParser(description="解析 Game Master 與 Pokémon GO 2D 資產")
    parser.add_argument("--input-dir", type=pathlib.Path, default=pathlib.Path("downloads"))
    parser.add_argument("--output-dir", type=pathlib.Path, default=pathlib.Path("generated"))
    args = parser.parse_args()
    game_master = json.loads((args.input_dir / "game_master_latest.json").read_text(encoding="utf-8"))
    asset_tree = json.loads((args.input_dir / "pogo_assets_tree.json").read_text(encoding="utf-8"))
    asset_paths = [item["path"] for item in asset_tree.get("tree", []) if item.get("type") == "blob"]

    settings: dict[str, list[dict]] = defaultdict(list)
    for item in walk(game_master):
        pokemon = item.get("pokemonSettings")
        if isinstance(pokemon, dict) and isinstance(pokemon.get("pokemonId"), str):
            settings[pokemon["pokemonId"]].append(pokemon)

    # Game Master 的 formSettings.isCostume 是判斷活動裝扮的權威欄位。
    # 檔名規則只能作為補充，否則像索財靈 COIN_A1／COIN_A2_2026
    # 這種沒有 HAT、COSTUME 等關鍵字的裝扮會被誤留在型態清單。
    dex_by_species_key: dict[str, int] = {}
    for template in game_master:
        data = template.get("data") if isinstance(template, dict) else None
        pokemon = data.get("pokemonSettings") if isinstance(data, dict) else None
        template_id = template.get("templateId", "") if isinstance(template, dict) else ""
        dex_match = re.match(r"V(\d{4})_POKEMON_", template_id)
        if isinstance(pokemon, dict) and isinstance(pokemon.get("pokemonId"), str) and dex_match:
            dex_by_species_key.setdefault(normalize(pokemon["pokemonId"]), int(dex_match.group(1)))
    species_key_by_dex = {dex_number: species_key for species_key, dex_number in dex_by_species_key.items()}

    authoritative_costume_forms: set[tuple[int, str]] = set()
    authoritative_regular_forms: set[tuple[int, str, str]] = set()
    costume_aliases_by_dex: dict[tuple[int, str], str] = {}
    for template in game_master:
        data = template.get("data") if isinstance(template, dict) else None
        form_settings = data.get("formSettings") if isinstance(data, dict) else None
        if not isinstance(form_settings, dict):
            continue
        species_key = normalize(str(form_settings.get("pokemon", "")))
        dex_number = dex_by_species_key.get(species_key)
        if dex_number is None:
            continue
        for form in form_settings.get("forms", []):
            if not isinstance(form, dict):
                continue
            full_form_key = normalize(str(form.get("form", "")))
            prefix = f"{species_key}_"
            form_key = full_form_key[len(prefix):] if full_form_key.startswith(prefix) else full_form_key
            if full_form_key.endswith("_NORMAL"):
                form_key = "NORMAL"
            is_costume = bool(form.get("isCostume")) or is_costume_form_key(form_key)
            if form_key and form.get("isCostume"):
                authoritative_costume_forms.add((dex_number, form_key))
            if form_key and not is_costume:
                authoritative_regular_forms.add((dex_number, species_key, form_key))
            asset_suffix = form.get("assetBundleSuffix")
            if is_costume and isinstance(asset_suffix, str):
                alias_key = costume_key_from_asset_suffix(asset_suffix)
                if alias_key:
                    costume_aliases_by_dex[(dex_number, alias_key)] = form_key

    form_seen: dict[str, int] = defaultdict(int)
    # Pokémon 名稱本身可能包含底線（例如 PORYGON_Z、MR_MIME）。舊作法直接
    # 用第一個底線切割，會把物種名稱誤當成型態，甚至掛到另一隻 Pokémon。
    # 改以 formSettings 的 pokemon 與 form 欄位建立權威關係。
    parsed_forms: list[dict] = []
    for dex_number, species_key, form_key in sorted(authoritative_regular_forms):
        parsed_forms.append(
            {
                "dexNumber": dex_number,
                "speciesKey": species_key,
                "formKey": form_key,
                "displayName": form_display(form_key, form_seen, species_key),
                "isDefault": form_key == "NORMAL",
            }
        )

    legacy_icon_records = [parse_icon_filename(path) | {"path": path} for path in asset_paths if parse_icon_filename(path)]
    modern_icon_records = [parse_modern_icon_filename(path) | {"path": path} for path in asset_paths if parse_modern_icon_filename(path)]
    for record in modern_icon_records:
        record["costumeKey"] = costume_aliases_by_dex.get(
            (record["dexNumber"], record["costumeKey"]),
            record["costumeKey"],
        )
    icon_records = legacy_icon_records + modern_icon_records
    normal_costume_assets: dict[tuple[int, str, str], dict] = {}
    normal_form_assets: dict[tuple[int, str], dict] = {}

    def costume_priority(record: dict) -> tuple[int, int, int]:
        # 新版圖片通常是較完整的活動裝扮；同一裝扮若有 g2 與一般圖，優先一般圖。
        return (
            0 if record.get("format") == "modern" else 1,
            int(record.get("slot", 0)),
            1 if ".g2." in record["path"].lower() else 0,
        )

    for record in icon_records:
        form_key = record["formKey"]
        dex_number = record["dexNumber"]
        if form_key != "NORMAL" and not record["shiny"]:
            current = normal_form_assets.get((dex_number, form_key))
            if current is None or (current["path"].endswith(".g2.icon.png") and not record["path"].endswith(".g2.icon.png")):
                normal_form_assets[(dex_number, form_key)] = record
        costume_key = record["costumeKey"]
        if costume_key is not None and not record["shiny"] and not is_form_like_costume(costume_key):
            identity = (dex_number, form_key, costume_key)
            current = normal_costume_assets.get(identity)
            if current is None or costume_priority(record) < costume_priority(current):
                normal_costume_assets[identity] = record

    # 另一批上游裝扮不是 .cCOSTUME.icon.png，而是 .fFORM_KEY.icon.png。
    # 例如活動帽、偵探裝扮、GO Fest 眼鏡等都會落在這裡；若不轉換，
    # 使用者只會在型態清單看到「特殊型態」，裝扮選擇器就會漏掉它們。
    costume_form_pairs: list[tuple[int, str]] = []
    for (dex_number, form_key), record in list(normal_form_assets.items()):
        if (dex_number, form_key) not in authoritative_costume_forms and not is_costume_form_key(form_key):
            continue
        costume_form_pairs.append((dex_number, form_key))
        costume_record = dict(record)
        costume_record["costumeKey"] = form_key
        costume_record["formKey"] = "NORMAL"
        costume_record["format"] = "form_costume"
        identity = (dex_number, "NORMAL", form_key)
        current = normal_costume_assets.get(identity)
        if current is None or costume_priority(costume_record) < costume_priority(current):
            normal_costume_assets[identity] = costume_record
        del normal_form_assets[(dex_number, form_key)]

    costumes_by_key: dict[str, dict] = {}
    compatibility: dict[tuple[int, str, str], dict] = {}
    sorted_costume_keys = sorted({item[2] for item in normal_costume_assets})
    costume_index = {costume_key: index for index, costume_key in enumerate(sorted_costume_keys, start=1)}
    for (dex_number, form_key, costume_key), record in sorted(normal_costume_assets.items()):
        costume_name = costume_display(costume_key, costume_index[costume_key])
        costumes_by_key.setdefault(
            costume_key,
            {
                "costumeKey": costume_key,
                "displayName": costume_name,
                "eventName": costume_event_name(costume_key, costume_name),
                "imageKey": record["imageKey"],
                "releaseYear": costume_release_year(costume_key),
                "sortOrder": costume_index[costume_key] * 10,
            },
        )
        compatibility[(dex_number, form_key, costume_key)] = {
            "dexNumber": dex_number,
            "formKey": form_key,
            "costumeKey": costume_key,
            "imageKey": record["imageKey"],
            "isVerified": True,
        }

    # 舊版純數字檔名沒有可安全還原的裝扮名稱，而且上游也有已知錯圖。
    # 它們只作覆蓋率稽核，不直接建立第二套重複裝扮。只要同物種的
    # 具名 Addressable/Game Master 裝扮數不少於數字圖示款式數，就沒有
    # 因忽略純數字命名而少掉可選款式。
    numeric_costumes_by_dex: dict[int, set[str]] = defaultdict(set)
    for path in asset_paths:
        numeric_match = re.match(
            r"^Images/Pokemon - 256x256/pokemon_icon_(\d{3})_(\d{2})_(\d{2})\.png$",
            path,
        )
        if numeric_match:
            numeric_costumes_by_dex[int(numeric_match.group(1))].add(numeric_match.group(3))
    named_costumes_by_dex: dict[int, set[str]] = defaultdict(set)
    for dex_number, _form_key, costume_key in compatibility:
        named_costumes_by_dex[dex_number].add(costume_key)
    numeric_excess_species = {
        dex_number
        for dex_number, numeric_keys in numeric_costumes_by_dex.items()
        if len(numeric_keys) > len(named_costumes_by_dex[dex_number])
    }

    location_paths = sorted(
        path
        for path in asset_paths
        if path.startswith("Images/LocationCards/")
        and pathlib.PurePosixPath(path).name.lower().startswith(("lc_", "sb_"))
        and path.lower().endswith(".png")
    )
    location_paths_by_stem: dict[str, list[str]] = defaultdict(list)
    for path in location_paths:
        location_paths_by_stem[normalize(pathlib.PurePosixPath(path).stem)].append(path)

    known_location_cards: set[str] = set()
    direct_image_matches: dict[str, str] = {}
    direct_vfx_matches: dict[str, str] = {}
    location_card_fields = {
        "locationCard",
        "existingLocationCard",
        "replacementLocationCard",
        "basePokemonLocationCard",
        "componentPokemonLocationCard",
        "fusionPokemonLocationCard",
    }
    for item in walk(game_master):
        if not isinstance(item, dict):
            continue
        for field in location_card_fields:
            value = item.get(field)
            if not isinstance(value, str):
                continue
            upper_value = value.upper()
            if upper_value.startswith(("LC_", "SB_")):
                known_location_cards.add(normalize(value[3:]))
            elif "BACKGROUND" in upper_value or "GOTOUR" in upper_value or "GOFEST" in upper_value:
                # 部分特殊背卡在 Game Master 內沒有 LC_/SB_ 前綴。
                known_location_cards.add(normalize(value))

        # 同一筆 Game Master 已直接提供 locationCard 與 imageUrl 時，
        # 必須優先使用這個一對一關係。若只靠 token 相似度，
        # LC_2026_SANFRANCISCO_WCS_001 與 lc_Wcs2026_sanFrancisco
        # 會被錯誤拆成「無圖背卡」與「圖片資產背卡」兩筆。
        location_value = item.get("locationCard")
        image_url = item.get("imageUrl")
        vfx_address = item.get("vfxAddress")
        if isinstance(location_value, str) and isinstance(vfx_address, str):
            normalized_location = normalize(location_value)
            background_key = (
                normalized_location[3:]
                if normalized_location.startswith(("LC_", "SB_"))
                else normalized_location
            )
            direct_vfx_matches[background_key] = vfx_address
        if isinstance(location_value, str) and isinstance(image_url, str):
            normalized_location = normalize(location_value)
            background_key = (
                normalized_location[3:]
                if normalized_location.startswith(("LC_", "SB_"))
                else normalized_location
            )
            image_stem = normalize(pathlib.PurePosixPath(image_url).stem)
            candidates = list(location_paths_by_stem.get(image_stem, []))
            if not candidates:
                candidates = [
                    path
                    for stem, paths in location_paths_by_stem.items()
                    if stem.startswith(image_stem) or image_stem.startswith(stem)
                    for path in paths
                ]
            if len(candidates) == 1:
                direct_image_matches[background_key] = candidates[0]

    # 先用年份、活動與地點 token 做高信心配對；代號不完整或同組多筆時，
    # 不猜錯對應，稍後建立「圖片資產背卡」讓使用者仍能依縮圖辨識。
    image_matches: dict[str, str] = dict(direct_image_matches)
    used_location_paths = set(image_matches.values())
    unmatched_location_paths: list[str] = []
    for path in location_paths:
        if path in used_location_paths:
            continue
        candidates = sorted(
            (
                (match_background_asset(background_key, path), background_key)
                for background_key in known_location_cards
                if background_key not in image_matches
            ),
            reverse=True,
        )
        if (
            candidates
            and candidates[0][0] >= 28
            and (len(candidates) == 1 or candidates[0][0] > candidates[1][0])
            and not background_asset_has_variant_conflict(candidates[0][1], path)
        ):
            score, background_key = candidates[0]
            if background_key not in image_matches:
                image_matches[background_key] = path
                continue
        unmatched_location_paths.append(path)

    backgrounds = [
        location_card(
            background_key,
            index,
            image_matches.get(background_key),
            vfx_key=direct_vfx_matches.get(background_key),
        )
        for index, background_key in enumerate(sorted(known_location_cards), start=1)
    ]
    # 所有沒有可靠 Game Master 對應的 lc_/sb_ 圖片仍要進主資料，
    # 以穩定 ASSET_ key 保存，不讓圖片因為命名差異消失。
    asset_only_backgrounds = []
    for offset, path in enumerate(unmatched_location_paths, start=1):
        asset_key = normalize(pathlib.PurePosixPath(path).stem)
        if not asset_key:
            continue
        asset_background_key = f"ASSET_{asset_key}"
        asset_only_backgrounds.append(
            location_card(
                asset_background_key,
                len(known_location_cards) + offset,
                path,
                background_asset_label(path),
            ),
        )
    backgrounds.extend(asset_only_backgrounds)
    backgrounds = merge_background_effect_variants(backgrounds)
    apply_known_background_metadata(backgrounds)
    disambiguate_background_names(backgrounds)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = {
        "forms": parsed_forms + [
            {
                "dexNumber": dex_number,
                "formKey": form_key,
                "displayName": form_display(form_key, form_seen, species_key_by_dex.get(dex_number)),
                "isDefault": False,
                "imageKey": record["imageKey"],
            }
            for (dex_number, form_key), record in sorted(normal_form_assets.items())
        ],
        "costumes": list(costumes_by_key.values()),
        "costumeCompatibility": list(compatibility.values()),
        "backgrounds": backgrounds,
        "assetStats": {
            "pokemon2dFiles": len(icon_records),
            "costumeVisualVariants": len(normal_costume_assets),
            "costumeKeys": len(costumes_by_key),
            "costumeSpecies": len({item[0] for item in normal_costume_assets}),
            "costumeFormVisualVariants": len(costume_form_pairs),
            "authoritativeCostumeForms": len(authoritative_costume_forms),
            "missingAuthoritativeCostumeForms": len(authoritative_costume_forms - set(costume_form_pairs)),
            "numericCostumeImages": sum(len(items) for items in numeric_costumes_by_dex.values()),
            "numericCostumeSpecies": len(numeric_costumes_by_dex),
            "numericCostumeSpeciesWithoutNamedCompatibility": len(
                set(numeric_costumes_by_dex) - set(named_costumes_by_dex)
            ),
            "numericCostumeExcessSpecies": len(numeric_excess_species),
            "formVisualVariants": len(normal_form_assets),
            "locationCardImages": len(location_paths),
            "directLocationCardImages": len(direct_image_matches),
            "matchedLocationCardImages": len(image_matches),
            "assetOnlyLocationCards": len(asset_only_backgrounds),
            "knownLocationCards": len(backgrounds),
        },
        "source": "PokeMiners game_masters and pogo_assets",
    }
    (args.output_dir / "game_master_catalog.json").write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"型態片段：{len(output['forms'])}")
    print(f"裝扮主鍵：{len(costumes_by_key)}")
    print(f"裝扮視覺版本：{len(normal_costume_assets)}")
    print(f"裝扮相容關係：{len(compatibility)}")
    print(f"背景主資料：{len(backgrounds)}")
    print(f"背景圖片：{len(location_paths)}（已配對 {len(image_matches)}，新增圖片資產 {len(asset_only_backgrounds)}）")
    print(f"背景分類：{len({item['categoryKey'] for item in backgrounds})}")
    print(f"Pokémon 2D 圖片檔：{len(icon_records)}")


if __name__ == "__main__":
    main()
