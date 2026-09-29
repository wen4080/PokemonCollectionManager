package tw.pokemon.collectionmanager.data.local

import androidx.room.TypeConverter

class RoomConverters {
    @TypeConverter fun genderToString(value: Gender): String = value.code
    @TypeConverter fun stringToGender(value: String): Gender = Gender.fromCode(value)
    @TypeConverter fun shadowToString(value: ShadowState): String = value.code
    @TypeConverter fun stringToShadow(value: String): ShadowState = ShadowState.fromCode(value)
    @TypeConverter fun dynamaxToString(value: DynamaxState): String = value.code
    @TypeConverter fun stringToDynamax(value: String): DynamaxState = DynamaxState.fromCode(value)
    @TypeConverter fun sizeToString(value: SizeType): String = value.code
    @TypeConverter fun stringToSize(value: String): SizeType = SizeType.fromCode(value)
    @TypeConverter fun tradeToString(value: TradeState): String = value.code
    @TypeConverter fun stringToTrade(value: String): TradeState = TradeState.fromCode(value)
    @TypeConverter fun backgroundTypeToString(value: BackgroundType): String = value.code
    @TypeConverter fun stringToBackgroundType(value: String): BackgroundType = BackgroundType.fromCode(value)
}
