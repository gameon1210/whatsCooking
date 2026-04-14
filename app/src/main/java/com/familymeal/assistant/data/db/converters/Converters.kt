package com.familymeal.assistant.data.db.converters

import androidx.room.TypeConverter
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MealType

class Converters {
    @TypeConverter fun fromDietType(value: DietType): String = value.name
    @TypeConverter fun toDietType(value: String): DietType = DietType.valueOf(value)

    @TypeConverter fun fromMealType(value: MealType): String = value.name
    @TypeConverter fun toMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter fun fromFeedbackType(value: FeedbackType): String = value.name
    @TypeConverter fun toFeedbackType(value: String): FeedbackType = FeedbackType.valueOf(value)
}
