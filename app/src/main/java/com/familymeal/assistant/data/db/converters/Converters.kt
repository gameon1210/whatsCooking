package com.familymeal.assistant.data.db.converters

import androidx.room.TypeConverter
import com.familymeal.assistant.data.db.entity.DietType
import com.familymeal.assistant.data.db.entity.EffortLevel
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.data.db.entity.RecommendationEventType
import com.familymeal.assistant.data.db.entity.SpicyTolerance

class Converters {
    @TypeConverter fun fromDietType(value: DietType): String = value.name
    @TypeConverter fun toDietType(value: String): DietType = DietType.valueOf(value)

    @TypeConverter fun fromMealType(value: MealType): String = value.name
    @TypeConverter fun toMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter fun fromFeedbackType(value: FeedbackType): String = value.name
    @TypeConverter fun toFeedbackType(value: String): FeedbackType = FeedbackType.valueOf(value)

    @TypeConverter fun fromEffortLevel(value: EffortLevel): String = value.name
    @TypeConverter fun toEffortLevel(value: String): EffortLevel = EffortLevel.valueOf(value)

    @TypeConverter fun fromSpicyTolerance(value: SpicyTolerance): String = value.name
    @TypeConverter fun toSpicyTolerance(value: String): SpicyTolerance = SpicyTolerance.valueOf(value)

    @TypeConverter fun fromRecommendationEventType(value: RecommendationEventType): String = value.name
    @TypeConverter fun toRecommendationEventType(value: String): RecommendationEventType =
        RecommendationEventType.valueOf(value)
}
