## Project: Family Meal Assistant (Android MVP)
- Language: Kotlin, minSDK 26, targetSDK 35
- Architecture: MVVM + Repository pattern
- DI: Hilt
- UI: Jetpack Compose + Material3
- Local DB: Room (primary store — offline-first)
- Backend: Firebase or Supabase (TBD — abstract behind Repository)
- Image AI: multimodal API call for meal classification
- Package: com.familymeal.assistant

## Core modules
- onboarding       → HouseholdSetupViewModel, HouseholdProfile entity
- home             → HomeViewModel, RecommendationEngine, SuggestionCard UI
- add_meal         → AddMealViewModel, MealEntry entity, ImageClassifier
- history          → HistoryViewModel, filtered list by meal type + date
- feedback         → FeedbackViewModel, FeedbackSignal entity
- catalog          → CatalogRepository, CatalogMeal entity (cold-start)
- recommendation   → RankingEngine (weighted scoring), ReasonGenerator

## Key conventions
- Repository pattern: all data access via *Repository interfaces
- Never call data layer directly from ViewModel
- Ranking weights must be externally configurable (config file or remote)
- All new features must include unit tests in test/ source set
- Use sealed classes for UI state: Loading, Success, Error
- Photo flow: camera-first, gallery fallback, save always succeeds even if AI fails
- Meal save must NEVER be blocked by AI classification failure

## Do NOT modify
- RankingEngine scoring formula without updating config weights
- MealEntry schema without a Room migration
- Cold-start catalog without reviewing diet/mealtype filters

## Diet types: Veg, Egg, Non-veg, Mixed
## Meal types: Breakfast, Lunch, Dinner, Tiffin, Snack
## Feedback signals: Make again, Good for tiffin, Kids liked, Too much work, Not a hit