package tw.pokemon.collectionmanager.data.local

enum class Gender(val code: String, val label: String) {
    MALE("MALE", "♂ 雄性"),
    FEMALE("FEMALE", "♀ 雌性"),
    GENDERLESS("GENDERLESS", "無性別"),
    UNKNOWN("UNKNOWN", "性別未確認");

    companion object {
        fun fromCode(code: String?): Gender = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

enum class ShadowState(val code: String, val label: String) {
    NORMAL("NORMAL", "一般"),
    SHADOW("SHADOW", "暗影"),
    PURIFIED("PURIFIED", "淨化");

    companion object {
        fun fromCode(code: String?): ShadowState = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

enum class DynamaxState(val code: String, val label: String) {
    NONE("NONE", "一般（非極巨化）"),
    DYNAMAX("DYNAMAX", "極巨化"),
    GIGANTAMAX("GIGANTAMAX", "超極巨化");

    companion object {
        fun fromCode(code: String?): DynamaxState = entries.firstOrNull { it.code == code } ?: NONE
    }
}

enum class SizeType(val code: String, val label: String) {
    NORMAL("NORMAL", "普通"),
    XXL("XXL", "特大"),
    XXS("XXS", "特小");

    companion object {
        fun fromCode(code: String?): SizeType = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

enum class TradeState(val code: String, val label: String) {
    UNTRADED("UNTRADED", "未交換"),
    TRADED("TRADED", "已交換");

    companion object {
        fun fromCode(code: String?): TradeState = entries.firstOrNull { it.code == code } ?: UNTRADED
    }
}

enum class BackgroundType(val code: String, val label: String) {
    LOCATION("LOCATION", "地點"),
    SPECIAL("SPECIAL", "特殊"),
    OTHER("OTHER", "其他");

    companion object {
        fun fromCode(code: String?): BackgroundType = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

enum class ThemeMode(val code: String, val label: String) {
    SYSTEM("SYSTEM", "跟隨系統"),
    LIGHT("LIGHT", "淺色"),
    DARK("DARK", "深色");

    companion object {
        fun fromCode(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}
