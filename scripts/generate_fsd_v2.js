const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType,
  ShadingType, VerticalAlign, PageNumber, PageBreak, LevelFormat,
  ExternalHyperlink, TableOfContents
} = require('docx');
const fs = require('fs');

// ── Colour palette ────────────────────────────────────────────────────────────
const C = {
  brand:    "1A6B3C",   // deep green
  accent:   "2E7D52",   // mid green
  light:    "E8F5EE",   // light green tint
  header:   "F0F7F3",   // table header fill
  border:   "CCCCCC",
  p0:       "1A6B3C",
  p1:       "2471A3",
  p2:       "7D3C98",
  warn:     "C0392B",
  muted:    "666666",
  white:    "FFFFFF",
};

// ── Helpers ───────────────────────────────────────────────────────────────────
const border = { style: BorderStyle.SINGLE, size: 1, color: C.border };
const borders = { top: border, bottom: border, left: border, right: border };
const cellPad = { top: 100, bottom: 100, left: 140, right: 140 };
const CONTENT_W = 9360; // US Letter 8.5" − 2×1" margins in DXA

function h1(text, bookmarkId) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    pageBreakBefore: true,
    children: [new TextRun({ text, bold: true, size: 32, color: C.brand, font: "Arial" })],
    spacing: { before: 0, after: 200 },
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    children: [new TextRun({ text, bold: true, size: 26, color: C.accent, font: "Arial" })],
    spacing: { before: 240, after: 120 },
  });
}
function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    children: [new TextRun({ text, bold: true, size: 22, color: "333333", font: "Arial" })],
    spacing: { before: 180, after: 80 },
  });
}
function body(text, opts = {}) {
  return new Paragraph({
    children: [new TextRun({ text, size: 22, font: "Arial", ...opts })],
    spacing: { before: 60, after: 60 },
  });
}
function lead(text) {
  return new Paragraph({
    children: [new TextRun({ text, size: 24, italics: true, color: "444444", font: "Arial" })],
    spacing: { before: 100, after: 160 },
  });
}
function bullet(text, level = 0) {
  return new Paragraph({
    numbering: { reference: "bullets", level },
    children: [new TextRun({ text, size: 22, font: "Arial" })],
    spacing: { before: 40, after: 40 },
  });
}
function numbered(text, level = 0) {
  return new Paragraph({
    numbering: { reference: "numbers", level },
    children: [new TextRun({ text, size: 22, font: "Arial" })],
    spacing: { before: 40, after: 40 },
  });
}
function gap() {
  return new Paragraph({ children: [new TextRun("")], spacing: { before: 60, after: 60 } });
}
function labelBadge(label, color) {
  return new TextRun({ text: ` ${label} `, bold: true, size: 18, color: C.white,
    shading: { fill: color, type: ShadingType.CLEAR }, font: "Arial" });
}

// ── Table helpers ─────────────────────────────────────────────────────────────
function cell(text, opts = {}) {
  const { fill, bold, color, width, italic } = opts;
  return new TableCell({
    borders,
    width: width ? { size: width, type: WidthType.DXA } : undefined,
    shading: fill ? { fill, type: ShadingType.CLEAR } : undefined,
    margins: cellPad,
    verticalAlign: VerticalAlign.CENTER,
    children: [new Paragraph({
      children: [new TextRun({ text: String(text), size: 20, font: "Arial",
        bold: bold || false, color: color || "000000", italics: italic || false })],
    })],
  });
}
function hcell(text, width) {
  return cell(text, { fill: C.header, bold: true, width });
}
function simpleTable(headers, rows, colWidths) {
  const total = colWidths.reduce((a, b) => a + b, 0);
  return new Table({
    width: { size: total, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [
      new TableRow({ children: headers.map((h, i) => hcell(h, colWidths[i])) }),
      ...rows.map(row => new TableRow({
        children: row.map((c, i) => {
          if (typeof c === 'object' && c.text !== undefined) {
            return cell(c.text, { ...c, width: colWidths[i] });
          }
          return cell(c, { width: colWidths[i] });
        })
      }))
    ]
  });
}

// ── Requirement row helper ────────────────────────────────────────────────────
function reqRow(id, req, priority, criterion) {
  const pColor = priority === "Must" ? C.p0 : priority === "Should" ? C.p1 : C.p2;
  return new TableRow({ children: [
    cell(id, { width: 900, bold: true, color: C.brand }),
    cell(req, { width: 4800 }),
    cell(priority, { width: 900, bold: true, color: pColor }),
    cell(criterion, { width: 2760 }),
  ]});
}

// ─────────────────────────────────────────────────────────────────────────────
// DOCUMENT ASSEMBLY
// ─────────────────────────────────────────────────────────────────────────────

const children = [];

// ── Cover page ────────────────────────────────────────────────────────────────
children.push(
  new Paragraph({
    children: [new TextRun({ text: "Family Meal Assistant", bold: true, size: 56,
      color: C.brand, font: "Arial" })],
    alignment: AlignmentType.CENTER,
    spacing: { before: 1440, after: 200 },
  }),
  new Paragraph({
    children: [new TextRun({ text: "Functional Specification Document — Version 2.0", size: 30,
      color: C.accent, font: "Arial" })],
    alignment: AlignmentType.CENTER,
    spacing: { before: 0, after: 800 },
  }),
  new Paragraph({
    children: [new TextRun({ text: "Android Mobile Application  ·  V2 Feature Release", size: 24,
      italics: true, color: "666666", font: "Arial" })],
    alignment: AlignmentType.CENTER,
    spacing: { before: 0, after: 1200 },
  }),
  simpleTable(
    [],
    [
      ["Document title", "Family Meal Assistant — FSD v2.0"],
      ["Version", "2.0"],
      ["Status", "Ready for engineering implementation"],
      ["Platform", "Android (Kotlin · MVVM · Hilt · Compose · Room)"],
      ["Based on", "FSD v1.3.0 (MVP) and V2 Feature Roadmap"],
      ["Prepared date", "April 2026"],
      ["Release horizon", "V2.0 core → V2.1 family → V2.2 planning depth"],
    ],
    [2400, 6960]
  ),
  new Paragraph({ children: [new PageBreak()] }),
);

// ── Table of Contents ─────────────────────────────────────────────────────────
children.push(
  new Paragraph({
    heading: HeadingLevel.HEADING_1,
    children: [new TextRun({ text: "Table of Contents", bold: true, size: 32,
      color: C.brand, font: "Arial" })],
    spacing: { before: 0, after: 300 },
  }),
  new TableOfContents("Contents", { hyperlink: true, headingStyleRange: "1-3" }),
  new Paragraph({ children: [new PageBreak()] }),
);

// ── 1. V2 PRODUCT CONTEXT ────────────────────────────────────────────────────
children.push(
  h1("1. V2 Product Context"),
  lead("Version 2 evolves Family Meal Assistant from a helpful MVP tracker into a trusted household meal planner for everyday decisions — faster to use, more family-aware, and genuinely useful across the full decision loop: recall, decide, act, and learn."),

  h2("1.1 Foundation — What V1 Established"),
  body("The MVP delivered a strategically sound product core: a meal decision assistant (not a recipe app), photo-first logging, soft preference learning through feedback signals, explainable recommendations, and cold-start support via a curated catalog. V2 builds on this foundation without replacing it."),
  gap(),
  body("V1 implementation gaps resolved before V2 begins:", { bold: true }),
  bullet("BUG-01: OnboardingViewModel now calls markOnboardingComplete() — no more infinite loop."),
  bullet("BUG-02: Runtime camera permission requested before launch."),
  bullet("BUG-03: SettingsViewModel apiKey StateFlow is now reactive."),
  bullet("MarkAsCookedSheet wired into HomeScreen via SuggestionCard."),
  bullet("History screen now shows date-grouped entries."),
  bullet("Time-aware meal context default implemented in HomeViewModel."),

  h2("1.2 V2 Product Goal"),
  body("Enable the primary cook or meal planner to answer 'What should I cook next?' in under 30 seconds — with recommendations that feel noticeably tailored to this specific family within the first 1–2 weeks of use, supported by planning tools that reduce stress on busy weekdays and school mornings without becoming a heavy scheduling workflow."),

  h2("1.3 V2 Design Principles"),
  simpleTable(
    ["Principle", "What it means in V2"],
    [
      ["Calm and quick", "Every interaction should be completable with one hand in under 30 seconds."],
      ["Practical over aspirational", "Solve tomorrow's tiffin before offering next-month planning."],
      ["Family-aware, not generic", "Recommendations should feel tuned to this household, not meal content in general."],
      ["Quiet learning", "Personalization improves from normal use — no surveys, no ratings prompts."],
      ["Lightweight enough for every day", "V2 must not expand scope so far that the app feels heavy or demanding."],
      ["Explainable suggestions", "Every recommendation carries a clear human-readable reason."],
      ["Offline-first", "Core logging and history work without network; AI features degrade gracefully."],
    ],
    [2800, 6560]
  ),

  h2("1.4 Release Plan"),
  simpleTable(
    ["Release", "Theme", "Features", "Target"],
    [
      ["V2.0 Core", "Better core loop", "Fast Add · Recently Cooked strip · Recommendation quality upgrade · Meal Memory enhancements · Explanation improvements", "~6 weeks"],
      ["V2.1 Family Wedge", "High-frequency use cases", "Tiffin Planner Lite · Saved Favorites · Effort-aware suggestions", "~4 weeks after 2.0"],
      ["V2.2 Planning Depth", "Forward planning", "Week View · Leftover cues · Household preference profiles", "~6 weeks after 2.1"],
      ["V2.x Future (P2)", "Strategic additions", "Seasonal packs · Learning-to-rank · Shared household mode", "TBD"],
    ],
    [1400, 2000, 4160, 1800]
  ),
  gap(),
);

// ── 2. V2 SCOPE ───────────────────────────────────────────────────────────────
children.push(
  h1("2. V2 Scope"),

  h2("2.1 In Scope — by Release"),
  h3("Release 2.0"),
  bullet("Fast Add with smart defaults (time-based meal type, one-tap recent reuse, post-save feedback chips)."),
  bullet("Recently Cooked + Similar Meals strip on Home screen."),
  bullet("Recommendation quality upgrade (implicit signals, recency windows, slot-aware repetition penalty)."),
  bullet("Meal Memory enhancements (thumbnails via Coil, labels, search, member filter, date grouping improvements)."),
  bullet("Explanation improvements (richer, more specific reason strings)."),
  bullet("Implicit interaction event capture (suggestion viewed, tapped, ignored)."),
  h3("Release 2.1"),
  bullet("Tiffin Planner Lite (dedicated tiffin recommendation view, save-for-tomorrow, kid-friendly labels)."),
  bullet("Saved Favorites shelf and Dependable Meals auto-shelf."),
  bullet("Effort-aware suggestions (effort metadata on catalog, busy-context detection)."),
  h3("Release 2.2"),
  bullet("Week View lightweight planner (pin 1–2 meals per slot, tiffin and dinner focus)."),
  bullet("Leftover / repeat-use cue tagging and planning integration."),
  bullet("Household preference profiles (per-member soft weights, household context tags)."),

  h2("2.2 Explicitly Out of Scope for All V2 Releases"),
  bullet("Full recipe library, recipe creation, or recipe import."),
  bullet("Grocery list, pantry inventory, or nutrition tracking."),
  bullet("Voice input or conversational chat-first workflows."),
  bullet("Collaborative household sharing (multiple app users)."),
  bullet("Heavy ML ranking models (L2R) before sufficient interaction data."),
  bullet("Festival planning, multilingual support, or social sharing."),
  gap(),
);

// ── 3. P0 FEATURE MODULES ────────────────────────────────────────────────────
children.push(
  h1("3. Release 2.0 — P0 Feature Modules"),
  lead("P0 features form the minimum V2 package. All five must ship together in the 2.0 release."),
);

// 3.1 Fast Add
children.push(
  h2("3.1 Fast Add with Smart Defaults"),
  lead("Replace the current linear photo → type → save flow with a near-frictionless experience: pre-filled context, one-tap reuse, and post-save micro-feedback."),
  h3("User pain solved"),
  body("Logging feels too heavy for daily use. Users skip logging, breaking the data flywheel that powers personalization."),
  h3("Functional behavior — shall statements"),
  bullet("The app shall pre-select the meal type based on current local time when the Add Meal screen opens (Breakfast before 10:00, Tiffin 10:00–11:30, Lunch 11:30–15:00, Snack 15:00–18:00, Dinner after 18:00)."),
  bullet("The app shall show a horizontal 'Recently Cooked' quick-add strip at the top of the Add Meal screen, displaying the 5 most recently logged meals as tappable chips."),
  bullet("Tapping a recent meal chip shall pre-fill the meal name and type; the user need only confirm and save."),
  bullet("The photo step shall remain optional; a meal shall be saveable without a photo with a single tap after type confirmation."),
  bullet("After a successful meal save, the app shall show a bottom sheet or inline row of 3–4 feedback chips (Make Again, Kids Liked, Good for Tiffin, Too Much Work) with a 'Skip' option — no forced selection."),
  bullet("The feedback chip selection shall be submitted and the screen dismissed within one tap after save."),
  bullet("The app shall never block save for any combination of missing optional fields (photo, note, AI name)."),
  h3("Screen changes"),
  bullet("Add Meal screen: add quick-reuse strip above the photo capture area."),
  bullet("Add Meal screen: move meal type selector to top; pre-select by time."),
  bullet("New post-save micro-feedback bottom sheet component."),
  h3("Data impact — no schema migration required"),
  bullet("Quick-reuse reads from existing MealEntry order by cookedAt DESC LIMIT 5."),
  bullet("Post-save feedback writes to existing FeedbackSignal table."),
  gap(),
);

// 3.2 Recently Cooked
children.push(
  h2("3.2 Recently Cooked + Similar Meals Strip"),
  lead("A compact anti-repetition strip on the Home screen showing what has been cooked recently and alerting to similar category clustering."),
  h3("User pain solved"),
  body("Users forget recent meal patterns and accidentally repeat dishes, undermining the app's core anti-repetition promise."),
  h3("Functional behavior"),
  bullet("The Home screen shall show a horizontal scrollable strip beneath the meal context switcher, displaying the 5–7 most recently logged meals as compact thumbnail cards."),
  bullet("Each card in the strip shall show the meal thumbnail (or an icon placeholder), meal name, and days-ago label (e.g., '2d ago')."),
  bullet("The strip shall be collapsible; if the user hides it, the preference shall persist across sessions."),
  bullet("If 2 or more meals from the same broad category (e.g., rice-based, bread-based) appear in the strip within the last 5 days, the strip shall display a subtle inline label such as 'Several rice meals recently'."),
  bullet("Tapping a strip card shall open the meal detail sheet."),
  h3("Screen changes"),
  bullet("Home screen: add RecentlyCooked composable between context switcher and suggestion cards."),
  bullet("Strip collapse state persisted in SettingsRepository (non-encrypted prefs)."),
  h3("Data impact"),
  bullet("Reads from existing MealEntry + CatalogMeal join. No schema migration."),
  bullet("Category clustering reads CatalogMeal.category; catalog.json entries need a 'category' field added (e.g., rice, bread, lentil, snack, egg)."),
  gap(),
);

// 3.3 Recommendation Quality Upgrade
children.push(
  h2("3.3 Recommendation Quality Upgrade"),
  lead("Move the ranking engine from recency-only scoring to a fully operational multi-signal engine by wiring feedback counts, member scores, and new implicit interaction signals into the pipeline."),
  h3("User pain solved"),
  body("Recommendations feel generic and do not improve visibly after usage. 60% of the scoring formula's inputs are currently always zero because feedback and member score data are never loaded before ranking."),
  h3("V2 signal additions"),
  simpleTable(
    ["Signal", "Source", "How used in ranking", "Priority"],
    [
      ["Feedback counts per meal", "FeedbackDao.getScoresForCatalogMeal()", "feedbackCounts map populated before rank()", "P0"],
      ["Member-meal scores", "FeedbackDao.getScoresForMember()", "memberScores map populated before rank()", "P0"],
      ["Same-slot repetition", "MealEntry cookedAt + mealType", "Stronger penalty if meal cooked in same slot within 2 days", "P0"],
      ["Suggestion ignored (implicit)", "New RecommendationEvent table", "Mild downrank if shown 3+ times and never selected", "P0"],
      ["Suggestion chosen (implicit)", "New RecommendationEvent table", "Mild positive lift for meals the user selects", "P0"],
      ["Weekday vs weekend context", "LocalDate.dayOfWeek", "Effort-weight adjustment (lighter bias on Mon–Fri mornings)", "P0"],
      ["Category diversity", "Recent meal history categories", "Boost underrepresented categories", "P1"],
    ],
    [2400, 2400, 3000, 960]
  ),
  gap(),
  h3("Implementation tasks"),
  bullet("HomeViewModel.loadSuggestions(): replace emptyMap() with actual DB reads for feedbackCounts and memberScores."),
  bullet("Pass actual ScoreBreakdown from ranked result into reasonGenerator.generate() — currently discarded."),
  bullet("Add RecommendationEvent entity and DAO for implicit interaction logging."),
  bullet("Extend RankingEngine.rank() with same-slot penalty and implicit signal inputs."),
  bullet("Update ranking_config.json with revised default weights."),
  h3("Updated scoring formula"),
  body("Score  =  (recency_weight × recency_bonus)  +  (makeAgain_weight × make_again_count)  −  (notAHit_weight × not_a_hit_count)  −  (tooMuchWork_weight × too_much_work_count × weekday_factor)  +  (tiffin_weight × tiffin_bonus)  +  (memberMatch_weight × member_compatibility)  +  (memberModifier × member_score)  −  (slot_repeat_penalty)  +  (implicit_lift  −  implicit_suppress)  +  (diversity_bonus)", { italics: true }),
  gap(),
  h3("Updated default weights — ranking_config.json"),
  simpleTable(
    ["Signal name", "V1 default", "V2 default", "Notes"],
    [
      ["recency", "0.40", "0.35", "Slightly reduced — more signals share weight"],
      ["makeAgain", "0.30", "0.30", "Unchanged"],
      ["notAHit", "0.25", "0.25", "Unchanged"],
      ["tooMuchWork", "0.20", "0.20", "Unchanged; weekday_factor multiplier added at runtime"],
      ["tiffin", "0.15", "0.20", "Boosted — tiffin wedge is a V2 priority"],
      ["memberMatch", "0.20", "0.25", "Boosted — member scores now actually populated"],
      ["slotRepeat", "—", "0.40", "New: same meal in same slot within 48 h penalty"],
      ["implicitLift", "—", "0.10", "New: chosen suggestion gets mild lift"],
      ["implicitSuppress", "—", "0.15", "New: ignored 3+ times gets mild suppression"],
      ["diversityBonus", "—", "0.08", "New: underrepresented category bonus"],
    ],
    [2400, 1440, 1440, 4080]
  ),
  gap(),
);

// 3.4 Tiffin Planner Lite
children.push(
  h2("3.4 Tiffin Planner Lite"),
  lead("A dedicated tiffin-planning view that lets a parent see tomorrow's tiffin recommendations, save one option, and understand why it was suggested — all in under 30 seconds."),
  h3("User pain solved"),
  body("School and work lunch planning is a daily high-stress moment. The main Home screen is too general for this focused task."),
  h3("Functional behavior"),
  bullet("A 'Tiffin for Tomorrow' entry point shall be accessible from Home screen (chip, card, or FAB secondary action) and from the bottom navigation Tiffin tab."),
  bullet("The Tiffin Planner screen shall show 3–5 tiffin-optimized recommendations filtered for: tiffin-suitable meals, not packed recently (within 3 days), preference for items tagged as Kid Favorite or portable."),
  bullet("Each tiffin suggestion shall show: meal name, thumbnail, days since last packed, and up to 2 context-specific reasons (e.g., 'Not packed in 5 days', 'Kids liked this')."),
  bullet("The user shall be able to save one tiffin suggestion as 'Planned for tomorrow' — stored as a TiffinPlan record."),
  bullet("A saved tiffin plan shall appear as a reminder chip on the Home screen the next morning."),
  bullet("The user shall be able to clear or change the saved plan at any time."),
  bullet("After packing the tiffin, the user shall be prompted (optionally) to log it as a meal entry, pre-filled from the plan."),
  h3("Screen spec — Tiffin Planner"),
  simpleTable(
    ["Area", "Requirement"],
    [
      ["Screen title", "'Tomorrow's Tiffin' with current date shown"],
      ["Suggestion cards", "Compact cards: thumbnail, name, reasons, 'Pack this' button"],
      ["Saved plan banner", "Banner at top when a plan exists: 'Planned: [meal name]' with Change / Log as cooked actions"],
      ["Empty state", "If no tiffin-suitable meals exist in catalog, show 'Add a tiffin-friendly meal to your history' prompt"],
      ["Navigation", "Accessible from Home chip and bottom nav (V2.1 adds Tiffin tab; V2.0 can use Home card entry point)"],
    ],
    [2400, 6960]
  ),
  h3("New data entities"),
  simpleTable(
    ["Entity", "Fields", "Notes"],
    [
      ["TiffinPlan", "id, catalogMealId, plannedDate (Date), savedAt (Long), loggedMealEntryId?", "One active plan at a time; replace on new save"],
    ],
    [2000, 4000, 3360]
  ),
  h3("Room migration"),
  bullet("Migration 1→2: CREATE TABLE tiffin_plans."),
  gap(),
);

// 3.5 Meal Memory
children.push(
  h2("3.5 Meal Memory Enhancements"),
  lead("Transform the History screen from a raw chronological log into a searchable, filterable family meal memory with rich visual thumbnails and meaningful meal labels."),
  h3("User pain solved"),
  body("History feels like data rather than helpful recall. Users cannot quickly find a meal they know they cooked, or see which meals were consistent winners."),
  h3("Functional behavior"),
  bullet("The History screen shall display meal thumbnails using Coil image loading (add coil dependency to build.gradle.kts)."),
  bullet("The History screen shall support text search across meal names — results update as the user types."),
  bullet("The History screen shall support filtering by: meal type (existing), member (new — requires cross-ref JOIN query in MealEntryDao), and meal label (new)."),
  bullet("Each meal entry shall display system-derived labels based on its feedback history: 'Made Again', 'Kid Favorite', 'Tiffin Winner', 'Too Much Work', 'Not a Hit'."),
  bullet("The History screen shall offer a 'Dependable Meals' filter that shows only meals with at least 2 Make Again signals."),
  bullet("Date grouping in History shall use section headers: Today, Yesterday, This Week, Earlier This Month, and explicit month labels for older entries."),
  bullet("A meal detail sheet shall show full photo, all feedback signals, member tags, and a 'Cook again' shortcut."),
  h3("Implementation notes"),
  bullet("Add Coil dependency: implementation(libs.coil.compose) — add coil-compose to libs.versions.toml."),
  bullet("Fix BUG-04: add MealEntryDao.observeMealsByMember() using JOIN on meal_member_cross_refs."),
  bullet("Add label derivation logic in HistoryViewModel based on FeedbackDao counts."),
  bullet("Add MealEntry.notes input field to AddMealScreen (BUG spec gap)."),
  gap(),
);

// ── 4. P1 FEATURE MODULES ────────────────────────────────────────────────────
children.push(
  h1("4. Release 2.1 + 2.2 — P1 Feature Modules"),
  lead("P1 features deepen utility and family-specific differentiation. Release 2.1 ships Tiffin Planner, Saved Favorites, and Effort-awareness. Release 2.2 ships Week View, Leftover cues, and Household profiles."),
);

// 4.1 Saved Favorites
children.push(
  h2("4.1 Saved Favorites and Dependable Meals Shelf"),
  lead("Give families quick access to their most reliable meals as a curated shelf — a fallback for rushed or low-energy moments."),
  h3("Functional behavior"),
  bullet("Users shall be able to manually star/favorite any meal from the History detail sheet or from suggestion cards."),
  bullet("A 'Favorites' shelf shall appear on the Home screen below the suggestion cards, showing up to 6 starred meals as compact chips or mini-cards."),
  bullet("A 'Dependable Meals' shelf shall be system-generated: meals that have been logged 3+ times with at least 2 Make Again signals, shown separately from manually saved favorites."),
  bullet("Users shall be able to tap a favorite to quick-add it to today's plan or log it directly."),
  bullet("Favorites shall persist across sessions; the user may un-favorite from the shelf or detail view."),
  h3("Data changes"),
  bullet("Add isFavorite: Boolean = false column to CatalogMeal (or a separate FavoriteMeal junction table if non-catalog meals need to be favorited too)."),
  bullet("Migration 2→3: ALTER TABLE catalog_meals ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0."),
  gap(),
);

// 4.2 Effort-aware
children.push(
  h2("4.2 Effort-Aware Suggestions"),
  lead("Make the app respect available time and energy — especially during weekday mornings and after-work evenings."),
  h3("Functional behavior"),
  bullet("Each CatalogMeal shall carry an effort level: Quick (under 20 min), Medium (20–45 min), or Involved (45 min+)."),
  bullet("The app shall detect 'busy context' automatically when the selected meal type is Breakfast or Tiffin on a weekday (Monday–Friday)."),
  bullet("In busy context, Quick meals shall receive a +0.15 score boost; Involved meals shall receive a −0.20 penalty."),
  bullet("Users shall be able to set a manual effort cap from the Home screen ('Keep it quick today') that persists for the current session only."),
  bullet("Effort level shall be visible on suggestion cards and meal detail sheets as a compact label (e.g., 'Quick' badge)."),
  h3("Data changes"),
  bullet("Add effortLevel: EffortLevel enum (QUICK, MEDIUM, INVOLVED) to CatalogMeal entity."),
  bullet("Populate effort levels across all existing catalog.json entries."),
  bullet("Migration: ALTER TABLE catalog_meals ADD COLUMN effort_level TEXT NOT NULL DEFAULT 'MEDIUM'."),
  gap(),
);

// 4.3 Week View
children.push(
  h2("4.3 Week View Lightweight Planner"),
  lead("A minimal planning surface where users can pin 1–2 meals up to 3 days ahead without committing to a full weekly schedule."),
  h3("Functional behavior"),
  bullet("A Week View tab or home card shall show a 5-day grid (Mon–Fri or current day +4 days) with Tiffin and Dinner slots per day."),
  bullet("Each empty slot shall show the top recommendation for that slot context as a suggestion chip."),
  bullet("Users shall be able to pin a meal to a slot by tapping the chip or using a 'Pin this' action from any suggestion card."),
  bullet("Pinned meals shall appear as confirmed cards in the grid; users may change or clear a pin at any time."),
  bullet("A pinned meal in a future slot shall appear as a reminder on the Home screen on the relevant day."),
  bullet("The Week View shall not require all slots to be filled; it is a partial planning aid, not a full meal plan enforcer."),
  bullet("Logged meals shall be auto-pinned retrospectively (i.e., if you cooked dinner on Tuesday, Tuesday dinner shows as 'Cooked')."),
  h3("Data changes"),
  bullet("New entity: MealPin(id, catalogMealId, mealName, slotDate: LocalDate as Long, mealType, isPinned, isLogged, createdAt)."),
  bullet("Migration 3→4: CREATE TABLE meal_pins."),
  gap(),
);

// 4.4 Household profiles
children.push(
  h2("4.4 Household Preference Profiles"),
  lead("Move beyond household-level diet filtering to soft per-member preference weights and household context tags that make recommendations feel genuinely tailored."),
  h3("Functional behavior"),
  bullet("Each Member profile shall support a set of household context tags: School-going, Spicy tolerance (None / Mild / Full), Portable preference (for tiffin), and Sunday special preference."),
  bullet("Recommendations shall apply per-member tag modifiers: e.g., a Spicy tolerance: None member in the audience suppresses strongly-spiced meals."),
  bullet("The Settings screen shall expose a 'Family Profiles' section for managing tags per member."),
  bullet("The home screen member audience selector shall show member names with their diet type icon."),
  bullet("Soft preference weights accumulated through feedback shall be attributed per member based on the meal's memberIds at log time."),
  h3("Data changes"),
  bullet("Add to Member entity: spicyTolerance: SpicyTolerance enum, portablePreference: Boolean, isSundaySpecial: Boolean, schoolGoing: Boolean (replaces household-level flag)."),
  bullet("Migration: ALTER TABLE members ADD COLUMN spicy_tolerance TEXT, ADD COLUMN portable_preference INTEGER, ADD COLUMN sunday_special INTEGER."),
  gap(),
);

// 4.5 Explanation improvements
children.push(
  h2("4.5 Explanation Improvements"),
  lead("Make recommendation reasons feel specific, human, and trustworthy rather than generic template strings."),
  h3("V2 reason templates"),
  simpleTable(
    ["Trigger condition", "Reason string (V2)", "V1 equivalent"],
    [
      ["daysSinceLastCooked ≥ 14", "Not had in {N} days — a good time to bring it back", "Not had in {N} days"],
      ["makeAgainCount ≥ 2", "Family voted Make Again {N} times", "Family loved it {N}×"],
      ["memberModifier > 0.3 + single member audience", "{Name} has consistently liked this", "{Name} likes this"],
      ["tiffinBonusActive + kidFavoriteSignal", "Packed before and the kids liked it", "Good for tiffin"],
      ["effortLevel = QUICK + busyContext", "Quick option — ready in under 20 min", "(new)"],
      ["isExploration", "Something different — hasn't come up in a while", "Trying something new"],
      ["diversity bonus active", "Good change from the recent {category} run", "(new)"],
      ["implicitLift active", "You've picked this before when suggested", "(new)"],
    ],
    [3000, 3800, 2560]
  ),
  gap(),
);

// 4.6 Leftover cues
children.push(
  h2("4.6 Leftover / Repeat-Use Cues"),
  lead("Allow families to tag meals as leftover-friendly or next-day reusable, then surface those cues in tiffin and dinner planning."),
  h3("Functional behavior"),
  bullet("Users shall be able to tag a logged meal as 'Good for leftovers' from the post-save feedback sheet or meal detail."),
  bullet("When a leftover-tagged meal was cooked within the last 24 hours, the Tiffin Planner shall surface a 'Use leftovers' suggestion card for that meal with a 'Leftover: {meal name}' label."),
  bullet("The CatalogMeal metadata shall include a leftoverFriendly: Boolean field populated during catalog curation."),
  bullet("Leftover suggestions shall be ranked above cold-start catalog items but below explicit Make Again meals."),
  h3("Data changes"),
  bullet("Add leftoverFriendly: Boolean to CatalogMeal, defaulting false."),
  bullet("Add FeedbackType.LeftoverFriendly to the FeedbackType enum."),
  gap(),
);

// ── 5. P2 FEATURES ───────────────────────────────────────────────────────────
children.push(
  h1("5. Release V2.x — P2 Feature Modules (Future)"),
  lead("P2 features are strategic additions planned after V2.2 ships. They require stronger interaction data and are not part of the near-term build plan."),
);

children.push(
  h2("5.1 Seasonal and Routine Packs"),
  body("Optional theme overlays added to the catalog: Summer Breakfast Ideas, Exam-Week Tiffin, Monsoon Comfort Meals, Festive Light Planning. Presented as a browse section, not a forced flow. Each pack is a tagged subset of the existing catalog with seasonal relevance boosts active for a defined date range."),

  h2("5.2 Learning-to-Rank Personalization"),
  body("Move from hand-tuned weight scoring to a lightweight L2R model (logistic regression or gradient boosted tree) trained on accumulated RecommendationEvent + FeedbackSignal data. Requires minimum 100 logged meals and 50 interaction events per household before activation. Weights from ranking_config.json serve as priors and remain the fallback. Explanation templates are preserved regardless of model."),

  h2("5.3 Shared Household Mode"),
  body("Allow a second device / user to view meal history, save ideas to the same household plan, and pin a meal. Requires backend sync (Firestore recommended). Not in V2 scope — single-user retention must be proven first. Architecture must not block this: HouseholdId is already a first-class entity key."),
  gap(),
);

// ── 6. RECOMMENDATION ENGINE V2 ──────────────────────────────────────────────
children.push(
  h1("6. Recommendation Engine — V2 Specification"),
  lead("The V2 engine extends the V1 weighted scoring approach with fully connected input signals, implicit interaction learning, slot-aware repetition handling, and effort-context awareness. It remains explainable and tunable."),

  h2("6.1 Updated Input Signals"),
  simpleTable(
    ["Signal group", "Source in V2", "Used in V1?"],
    [
      ["Household context", "Member profiles, diet types, tags", "Yes (partial)"],
      ["Meal context", "Selected meal type + time + weekday", "Yes"],
      ["Recency history", "MealEntry.cookedAt, slot-aware", "Yes"],
      ["Feedback (per catalog meal)", "FeedbackDao.getScoresForCatalogMeal()", "No — always empty in V1"],
      ["Member scores (per member × meal)", "FeedbackDao.getScoresForMember()", "No — always empty in V1"],
      ["Implicit interaction", "RecommendationEvent (new)", "No"],
      ["Effort level", "CatalogMeal.effortLevel (new)", "No"],
      ["Leftover availability", "Recent meal with leftoverFriendly tag", "No"],
      ["Diversity (category recency)", "Last 7 days CatalogMeal.category distribution", "No"],
    ],
    [2800, 3800, 2760]
  ),

  h2("6.2 Implicit Interaction Event Capture"),
  body("V2 introduces RecommendationEvent to capture what the user does with suggestions — without requiring explicit ratings."),
  simpleTable(
    ["Event type", "When captured", "Ranking effect"],
    [
      ["SHOWN", "Meal ID appears in suggestion list on Home/Tiffin", "Increments shown_count"],
      ["TAPPED", "User opens suggestion detail or cook sheet", "Mild positive lift (implicitLift weight)"],
      ["COOKED", "Meal is logged via Mark as Cooked from suggestion", "Strong positive — feeds makeAgain path"],
      ["IGNORED", "Shown 3+ times, never tapped, within 14 days", "Mild suppression (implicitSuppress weight)"],
      ["SAVED", "User taps Save for Later (Favorites)", "Moderate positive lift"],
    ],
    [2200, 3600, 3560]
  ),

  h2("6.3 Full Ranking Pipeline V2"),
  numbered("Load candidates: catalog meals matching diet + meal type filter."),
  numbered("Apply hard filters: remove meals with notAHit count ≥ 3 in last 30 days; remove meals with slotRepeat within 24 h."),
  numbered("Score each candidate using updated formula (Section 3.3)."),
  numbered("Apply diversity rerank: if top 5 share >3 meals from same category, inject highest-scoring alternative category item at position 4 or 5."),
  numbered("Split exploitation/exploration: top floor(N × (1 − explorationRatio)) by score; remaining slots filled randomly from exploration pool."),
  numbered("Generate reasons for each result using ScoreBreakdown (pass actual breakdown, not default empty)."),
  numbered("Emit SHOWN events for all returned meal IDs."),

  h2("6.4 Tiffin Planner Ranking Overrides"),
  bullet("Hard filter: tiffinSuitable = true AND not packed as Tiffin in last 3 days."),
  bullet("Kid-friendly boost: +0.25 if audience includes school-going member AND meal has KidsLiked signal."),
  bullet("Leftover cue injection: if leftover-tagged meal cooked within 24 h, inject as position 1 regardless of score with reason 'Use leftovers from last night'."),
  bullet("Exploration ratio reduced to 0.10 for tiffin context (families prefer known safe options for kids)."),

  h2("6.5 Effort Context Detection"),
  bullet("busyContext = true when: mealType is Breakfast or Tiffin AND LocalDate.now().dayOfWeek in [MON, TUE, WED, THU, FRI]."),
  bullet("When busyContext = true: weekday_factor = 1.5 (amplifies tooMuchWork penalty); Quick meals receive +0.15 bonus."),
  bullet("Manual effort cap ('Keep it quick today') sets effortCap = QUICK for the session; Involved meals are excluded from candidates entirely."),
  gap(),
);

// ── 7. DATA MODEL CHANGES ────────────────────────────────────────────────────
children.push(
  h1("7. Data Model — V2 Changes"),
  lead("All changes must be implemented as incremental Room migrations. The baseline is schema version 1 from the MVP. V2 spans schema versions 2 through 5."),

  h2("7.1 New Entities"),
  simpleTable(
    ["Entity", "Table name", "Key fields", "Migration"],
    [
      ["TiffinPlan", "tiffin_plans", "id, catalogMealId, mealName, plannedDate (Long), savedAt, loggedMealEntryId?", "v1→v2"],
      ["RecommendationEvent", "recommendation_events", "id, mealContext, catalogMealId, eventType (SHOWN/TAPPED/COOKED/IGNORED/SAVED), occurredAt, sessionId", "v1→v2"],
      ["MealPin", "meal_pins", "id, catalogMealId, mealName, slotDate (Long), mealType, isPinned, isLogged, createdAt", "v3→v4"],
    ],
    [2000, 2200, 3600, 1560]
  ),

  h2("7.2 Modified Entities"),
  simpleTable(
    ["Entity", "New columns", "Migration", "Notes"],
    [
      ["CatalogMeal", "category TEXT, effortLevel TEXT DEFAULT 'MEDIUM', leftoverFriendly INTEGER DEFAULT 0, isFavorite INTEGER DEFAULT 0", "v1→v2", "Update catalog.json with values for all entries"],
      ["Member", "spicyTolerance TEXT DEFAULT 'FULL', portablePreference INTEGER DEFAULT 0, sundaySpecial INTEGER DEFAULT 0, schoolGoing INTEGER DEFAULT 0", "v2→v3", "schoolGoing migrated from household-level HouseholdProfile"],
      ["FeedbackType (enum)", "+ GoodForLeftovers", "—", "Add enum value; no table migration needed"],
      ["EffortLevel (new enum)", "QUICK, MEDIUM, INVOLVED", "—", "New enum for CatalogMeal.effortLevel"],
    ],
    [2000, 3200, 1560, 2600]
  ),

  h2("7.3 Migration Checklist for Claude Code"),
  simpleTable(
    ["Migration", "Version", "Operations", "Test"],
    [
      ["Migration1to2", "1 → 2", "CREATE tiffin_plans; CREATE recommendation_events; ALTER catalog_meals ADD category, effort_level, leftover_friendly, is_favorite", "Verify tables exist; existing catalog rows default correctly"],
      ["Migration2to3", "2 → 3", "ALTER members ADD spicy_tolerance, portable_preference, sunday_special, school_going", "Existing members get defaults; queries still return correct rows"],
      ["Migration3to4", "3 → 4", "CREATE meal_pins", "Verify empty table on upgrade; existing data unaffected"],
    ],
    [2000, 1200, 4400, 1760]
  ),

  h2("7.4 New DAOs Required"),
  simpleTable(
    ["DAO", "New methods needed"],
    [
      ["TiffinPlanDao", "upsertPlan(), getActivePlan(): Flow<TiffinPlan?>, clearPlan()"],
      ["RecommendationEventDao", "insertEvent(), getImplicitSignals(catalogMealId): ImplicitSignalSummary, getIgnoredMealIds(shownThreshold, withinDays): List<Long>"],
      ["MealPinDao", "upsertPin(), getPinsForWeek(startDate, endDate): Flow<List<MealPin>>, clearPin(id)"],
      ["CatalogMealDao (extend)", "+ updateFavorite(id, isFavorite), getFavorites(): Flow<List<CatalogMeal>>, getDependableMeals(minMakeAgain): Flow<List<CatalogMeal>>"],
    ],
    [2800, 6560]
  ),
  gap(),
);

// ── 8. SCREEN-LEVEL REQUIREMENTS V2 ─────────────────────────────────────────
children.push(
  h1("8. Screen-Level Requirements — V2"),

  h2("8.1 Navigation Model"),
  body("V2.0 keeps the 3-tab bottom nav (Home, Add Meal, History). V2.1 adds a 4th tab: Tiffin (replaces the Home chip entry point with a dedicated tab). V2.2 adds a 5th tab: Planner (Week View). Settings remains accessible via top-right icon."),

  h2("8.2 Home Screen V2"),
  simpleTable(
    ["Area", "V1", "V2 change"],
    [
      ["Context switcher", "Manual meal type chips", "No change; time-based default now working"],
      ["Recently Cooked strip", "Missing (spec gap)", "Add horizontal scrollable strip — 5–7 cards with thumbnail, name, days-ago"],
      ["Suggestion cards", "Name + reason only", "Add effort badge (Quick/Medium), member match indicator"],
      ["Favorites shelf", "Not present", "Add collapsible shelf below suggestions: manually starred + Dependable meals"],
      ["Tiffin tomorrow chip", "Not present", "Add prominent chip/card entry to Tiffin Planner when mealType = Tiffin or time = evening"],
      ["Mark as Cooked", "Sheet exists but unreachable", "Wire MarkAsCookedSheet to SuggestionCard primary action (BUG-01 area fix)"],
    ],
    [2400, 2600, 4360]
  ),

  h2("8.3 Add Meal Screen V2"),
  simpleTable(
    ["Area", "V1", "V2 change"],
    [
      ["Meal type selector", "Manual chip below photo", "Move to top; pre-select by time of day"],
      ["Quick-reuse strip", "Not present", "Add horizontal 'Recently cooked' chip strip at top"],
      ["Notes field", "Missing (spec gap)", "Add optional OutlinedTextField for short notes"],
      ["Post-save feedback", "Navigate away immediately", "Show micro-feedback bottom sheet with 4 chips + Skip"],
      ["Camera permission", "Not requested (BUG-02)", "Add runtime RequestPermission before camera launch"],
    ],
    [2400, 2600, 4360]
  ),

  h2("8.4 History Screen V2 (Meal Memory)"),
  simpleTable(
    ["Area", "V1", "V2 change"],
    [
      ["Thumbnails", "Text-only (spec gap)", "Add Coil image loading; placeholder icon when no photo"],
      ["Date grouping", "Flat list (spec gap)", "Today / Yesterday / This Week / Earlier headers"],
      ["Search", "Not present", "Search bar with real-time name filtering"],
      ["Filter chips", "Meal type only", "Add: Member filter, Label filter (Made Again, Kid Fave, etc.)"],
      ["Meal labels", "Not present", "System-derived labels from FeedbackSignal counts per meal"],
      ["Dependable filter", "Not present", "One-tap 'Dependable Meals' filter shortcut"],
    ],
    [2400, 2600, 4360]
  ),

  h2("8.5 Tiffin Planner Screen (New — V2.1)"),
  simpleTable(
    ["Area", "Requirement"],
    [
      ["Header", "'Tomorrow's Tiffin' + tomorrow's date"],
      ["Saved plan banner", "Appears at top when plan exists; shows meal name with Change / Log actions"],
      ["Suggestion cards", "3–5 tiffin-optimized cards; effort badge; portability indicator; days since last packed"],
      ["Leftover cue card", "If leftover-tagged meal cooked today, inject as first card with special leftover label"],
      ["Empty state", "Friendly prompt to add tiffin-suitable meals; link to catalog browse"],
      ["Navigation", "Tiffin tab in bottom nav (V2.1); Home card entry (V2.0)"],
    ],
    [2400, 6960]
  ),

  h2("8.6 Week View Screen (New — V2.2)"),
  simpleTable(
    ["Area", "Requirement"],
    [
      ["Grid", "5-day grid (today + 4 days); rows = Tiffin, Dinner; columns = days"],
      ["Empty slot", "Shows top recommendation as a suggestion chip with meal type icon"],
      ["Pinned slot", "Shows meal name as confirmed card; tap to change or clear"],
      ["Logged slot", "Shows meal name with 'Cooked' badge; auto-populated from history"],
      ["Pin action", "Swipe up on chip OR tap + long-press to pin"],
      ["Reminder", "Pinned tiffin for tomorrow surfaces as chip on Home screen morning of"],
    ],
    [2400, 6960]
  ),
  gap(),
);

// ── 9. FUNCTIONAL REQUIREMENTS ───────────────────────────────────────────────
children.push(
  h1("9. Functional Requirements — V2"),
  lead("Requirements FR-025 onward extend the MVP requirement set (FR-001 through FR-024). Priority: Must = release gate, Should = strong intent, Could = nice to have."),
  gap(),
  new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: [900, 4800, 900, 2760],
    rows: [
      new TableRow({ children: [hcell("ID", 900), hcell("Requirement", 4800), hcell("Priority", 900), hcell("Acceptance criterion", 2760)] }),
      // Fast Add
      reqRow("FR-025","The app shall pre-select meal type on Add Meal based on current local time.","Must","Breakfast selected before 10:00; Dinner selected after 18:00; verified on a fresh open."),
      reqRow("FR-026","The Add Meal screen shall show a quick-reuse strip of the 5 most recently logged meals.","Must","Strip shows correct meals; tapping one pre-fills name and type."),
      reqRow("FR-027","The app shall show a post-save micro-feedback sheet after every successful meal save.","Must","Sheet appears with ≥3 feedback chips; Skip dismisses without saving; selection saves a FeedbackSignal."),
      reqRow("FR-028","The app shall allow a meal to be logged by tapping a recent meal chip without any additional required input.","Must","Tapping chip + Save completes a valid MealEntry."),
      // Recently Cooked
      reqRow("FR-029","The Home screen shall show a recently cooked strip of the last 5–7 meals.","Must","Strip renders; thumbnail or placeholder shows; days-ago label correct."),
      reqRow("FR-030","The recently cooked strip shall display a category clustering alert when ≥2 meals from the same category appear within 5 days.","Should","Alert label appears in strip when condition is met."),
      reqRow("FR-031","The recently cooked strip shall be collapsible and the state shall persist across sessions.","Should","Collapse/expand persists after app restart."),
      // Recommendation quality
      reqRow("FR-032","The ranking engine shall use feedback counts from the database when scoring meals.","Must","feedbackCounts populated from FeedbackDao; makeAgain signals visibly affect suggestion order."),
      reqRow("FR-033","The ranking engine shall use member-meal scores from the database when scoring meals.","Must","memberScores populated from FeedbackDao; member-specific reasons appear on cards."),
      reqRow("FR-034","The app shall capture SHOWN, TAPPED, COOKED, IGNORED, and SAVED recommendation events.","Must","RecommendationEvent rows created on each interaction type."),
      reqRow("FR-035","Meals shown 3+ times and never tapped within 14 days shall receive a mild suppression modifier.","Should","Repeated-shown meal ranks lower vs. equal-score alternative."),
      reqRow("FR-036","The same meal in the same meal slot within 24 hours shall receive a strong repetition penalty.","Must","Yesterday's dinner does not appear as today's top dinner suggestion."),
      // Meal Memory
      reqRow("FR-037","The History screen shall display meal thumbnails using Coil.","Must","Images load for meals with photoUri; placeholder shows for meals without."),
      reqRow("FR-038","The History screen shall support real-time search across meal names.","Must","Typing filters the list within 300ms."),
      reqRow("FR-039","The History screen shall support member-based filtering.","Should","Selecting a member shows only meals tagged with that member."),
      reqRow("FR-040","The History screen shall display system-derived labels on meal cards.","Should","Labels correct based on FeedbackSignal counts for that meal."),
      reqRow("FR-041","The Add Meal screen shall include an optional notes text field.","Must","User can type a note; it is saved to MealEntry.notes."),
      // Tiffin Planner
      reqRow("FR-042","The app shall provide a Tiffin Planner view showing 3–5 tiffin-optimized suggestions.","Must","View accessible from Home; shows only tiffinSuitable meals not packed in 3 days."),
      reqRow("FR-043","The user shall be able to save one meal as tomorrow's tiffin plan.","Must","TiffinPlan row created; plan visible as banner in Tiffin view the next morning."),
      reqRow("FR-044","A saved tiffin plan shall surface as a reminder chip on the Home screen the next morning.","Must","Chip visible on Home between 06:00–10:00 on planned date."),
      reqRow("FR-045","After packing, the user shall be prompted to log the planned tiffin as a meal entry.","Should","Prompt appears with pre-filled name and type from plan."),
      reqRow("FR-046","If a leftover-tagged meal was cooked within 24 hours, Tiffin Planner shall inject it as the first suggestion.","Should","Leftover card appears at position 1 with correct label."),
      // Saved Favorites
      reqRow("FR-047","Users shall be able to star/favorite any meal from History or suggestion cards.","Must","isFavorite toggled; meal appears/disappears from Favorites shelf."),
      reqRow("FR-048","A Favorites shelf shall appear on Home showing up to 6 saved meals.","Must","Shelf visible; tapping a meal opens it for quick-add."),
      reqRow("FR-049","A Dependable Meals shelf shall auto-populate with meals logged ≥3 times with ≥2 Make Again signals.","Should","Shelf updates after qualifying meals are logged."),
      // Effort-aware
      reqRow("FR-050","CatalogMeal shall carry an effortLevel field (Quick / Medium / Involved).","Must","Field present on all catalog entries after migration."),
      reqRow("FR-051","In busy context (weekday Breakfast or Tiffin), Quick meals shall receive +0.15 score bonus; Involved meals −0.20.","Must","Quick meals rank relatively higher on weekday breakfast vs. weekend."),
      reqRow("FR-052","The user shall be able to set a 'Keep it quick' session effort cap from Home.","Should","Involved meals excluded while cap is active; cap clears on next app open."),
      // Week View
      reqRow("FR-053","The Week View shall show a 5-day grid with Tiffin and Dinner slots.","Must","Grid renders with today + 4 days; slots labeled correctly."),
      reqRow("FR-054","Empty slots shall show top recommendations as suggestion chips.","Must","Chips show meal names; tapping one opens detail."),
      reqRow("FR-055","Users shall be able to pin a meal to a slot.","Must","MealPin row created; slot shows confirmed card."),
      reqRow("FR-056","Logged meals shall auto-populate their corresponding grid slot.","Must","Logging Tuesday dinner shows it as 'Cooked' in Tuesday Dinner slot."),
      // Household profiles
      reqRow("FR-057","Each Member shall support spicy tolerance, portable preference, and school-going tags.","Must","Tags editable from Settings → Family Profiles."),
      reqRow("FR-058","Recommendations for an audience including a Spicy: None member shall suppress strongly-spiced meals.","Should","Spiced meals rank lower when spicy-intolerant member is in audience."),
      // Explanations
      reqRow("FR-059","Explanation strings shall use the V2 templates with specific counts and member names where available.","Must","Reason strings include counts (e.g., 'Family voted Make Again 3 times') not just generic text."),
      reqRow("FR-060","The actual ScoreBreakdown from ranking shall be passed to ReasonGenerator, not the default empty breakdown.","Must","memberModifier-based reasons appear when member scores are non-zero."),
    ]
  }),
  gap(),
);

// ── 10. ARCHITECTURE AND IMPLEMENTATION NOTES ────────────────────────────────
children.push(
  h1("10. Architecture and Implementation Notes"),
  lead("Notes for Claude Code to follow when implementing V2. These complement CLAUDE.md and must not conflict with existing conventions."),

  h2("10.1 Module Additions"),
  simpleTable(
    ["New module", "Package", "Key classes"],
    [
      ["tiffin_planner", "ui/tiffin", "TiffinPlannerScreen, TiffinPlannerViewModel, TiffinPlanCard"],
      ["week_view", "ui/weekview", "WeekViewScreen, WeekViewViewModel, SlotCard, PinMealSheet"],
      ["favorites", "ui/favorites", "FavoritesShelf (composable, embedded in HomeScreen)"],
      ["recommendation_events", "data/repository/RecommendationEventRepository", "RecommendationEventRepositoryImpl, RecommendationEventDao"],
    ],
    [2400, 2800, 4160]
  ),

  h2("10.2 CLAUDE.md Updates Required"),
  bullet("Add tiffin_planner, week_view, and recommendation_events to Core modules list."),
  bullet("Add EffortLevel, TiffinPlan, RecommendationEvent, MealPin, SpicyTolerance to entity type lists."),
  bullet("Add rule: 'RecommendationEvent must be emitted for every suggestion shown or interacted with in HomeViewModel and TiffinPlannerViewModel.'"),
  bullet("Add rule: 'feedbackCounts and memberScores must never be emptyMap() when passed to RankingEngine.rank() — always load from DB.'"),
  bullet("Update: Room schema version = 4 (after all V2 migrations complete)."),

  h2("10.3 Dependency Additions"),
  simpleTable(
    ["Library", "Purpose", "libs.versions.toml entry"],
    [
      ["Coil (coil-compose)", "Thumbnail image loading in History and suggestion cards", "coil = \"2.7.0\" + coil-compose artifact"],
      ["(no new DI libraries)", "Hilt remains; new modules follow existing @HiltViewModel pattern", "—"],
    ],
    [2800, 4160, 2400]
  ),

  h2("10.4 Key Invariants to Preserve"),
  bullet("Meal save must NEVER be blocked by AI classification failure (existing rule — must hold in V2 Fast Add)."),
  bullet("RankingEngine scoring formula must not change without updating ranking_config.json weights."),
  bullet("Every new Room entity must have a corresponding migration and schema export."),
  bullet("All new ViewModels must use sealed UiState (Loading / Success / Error) and collect from StateFlow."),
  bullet("No direct data layer calls from Activity or Composable — repository access only via ViewModel."),
  gap(),
);

// ── 11. ANALYTICS V2 ─────────────────────────────────────────────────────────
children.push(
  h1("11. Analytics and Success Measurement — V2"),
  simpleTable(
    ["Metric area", "V2 events / measurements", "Why it matters"],
    [
      ["Logging friction", "Time from Add Meal open to save; quick-reuse chip usage rate; post-save feedback completion rate", "Validates Fast Add reduces effort"],
      ["Recommendation quality", "TAPPED / COOKED / IGNORED event ratios; days to first member-specific reason shown", "Validates ranking signal wiring"],
      ["Tiffin engagement", "Tiffin Planner opens per week; plans saved; plans converted to logged meals", "Validates tiffin wedge retention value"],
      ["Favorites usage", "Favorites shelf taps; dependable meals shelf size over time", "Validates fallback shelf utility"],
      ["Week-2 / Week-4 retention", "% of users who open the app and log a meal in week 2 and week 4", "Core retention health signal"],
      ["Explanation trust", "Cards with specific reason strings vs. generic strings; suggestion selection rate by reason type", "Validates V2 explanation upgrade"],
    ],
    [2400, 4200, 2760]
  ),
  gap(),
);

// ── 12. V2 ACCEPTANCE CRITERIA ───────────────────────────────────────────────
children.push(
  h1("12. V2 Acceptance Criteria"),
  lead("The V2.0 release may be submitted for Play Store update when all Must-priority requirements in Section 9 pass, and the following end-to-end scenarios are verified:"),
  numbered("A returning user opens the app, sees the Recently Cooked strip with correct days-ago labels, and gets a suggestion that differs from the top strip item."),
  numbered("A user logs a meal using the quick-reuse chip in under 10 seconds from Add Meal open."),
  numbered("After logging 5+ meals with feedback, the suggestion order differs measurably from the cold-start order (ranked by recency alone)."),
  numbered("The post-save feedback sheet appears after every save and submits a FeedbackSignal on chip tap."),
  numbered("A meal logged as dinner on Monday does not appear as the top dinner suggestion on Tuesday (slot-repeat penalty working)."),
  numbered("Recommendation reasons include specific counts and member names where applicable — no card shows only 'Trying something new' after 2+ weeks of use."),
  numbered("All 12 existing unit tests continue to pass (./gradlew testDebugUnitTest BUILD SUCCESSFUL)."),
  numbered("New unit tests cover: TiffinPlannerViewModel, RecommendationEvent capture, updated RankingEngine with feedbackCounts and memberScores populated."),
  gap(),
);

// ── 13. IMPLEMENTATION PLAN SUMMARY ─────────────────────────────────────────
children.push(
  h1("13. Implementation Phase Plan — For Claude Code"),
  lead("Recommended implementation sequence to avoid dependency conflicts and enable incremental testing."),
  simpleTable(
    ["Phase", "Work items", "Depends on"],
    [
      ["Phase 1\nDB migrations", "Add Migration1to2 (new tables + CatalogMeal columns); update AppDatabase version; update catalog.json with category + effortLevel; write migration unit tests", "Nothing — do first"],
      ["Phase 2\nRanking engine wiring", "Fix HomeViewModel feedbackCounts + memberScores loading; pass actual ScoreBreakdown to ReasonGenerator; add RecommendationEvent capture; update ranking_config.json weights", "Phase 1 (new entities)"],
      ["Phase 3\nFast Add + Feedback sheet", "Pre-select meal type by time; add quick-reuse strip to AddMealScreen; add notes field; implement post-save micro-feedback sheet; fix camera permission (BUG-02)", "Phase 1"],
      ["Phase 4\nMeal Memory (History V2)", "Add Coil; bind thumbnails in HistoryScreen; add search bar; add label derivation in HistoryViewModel; fix member filter (BUG-04); improve date grouping", "Phase 1"],
      ["Phase 5\nRecently Cooked strip", "Build RecentlyCooked composable; wire to HomeScreen; implement category clustering alert; add collapse preference to SettingsRepository", "Phase 4 (Coil thumbnails)"],
      ["Phase 6\nExplanation improvements", "Update ReasonGenerator with V2 templates; verify ScoreBreakdown passed correctly; add implicitLift / diversity reasons", "Phase 2"],
      ["Phase 7\nTiffin Planner Lite", "TiffinPlannerViewModel + Screen; TiffinPlanDao; plan reminder chip on Home; plan → log flow", "Phases 1–3"],
      ["Phase 8\nSaved Favorites", "Favorite toggle on History + SuggestionCard; FavoritesShelf on HomeScreen; DependableMeals shelf; Migration2to3", "Phases 4–5"],
      ["Phase 9\nEffort-aware suggestions", "EffortLevel enum; busy context detection in RankingEngine; effort cap on HomeScreen; effort badge on cards", "Phase 2"],
      ["Phase 10\nWeek View", "MealPinDao + entity; Migration3to4; WeekViewScreen + ViewModel; reminder chip on Home", "Phases 7–8"],
    ],
    [1440, 5400, 2520]
  ),
  gap(),
);

// ── APPENDIX ─────────────────────────────────────────────────────────────────
children.push(
  h1("Appendix A. Full Feature Priority Summary"),
  simpleTable(
    ["Feature", "Release", "Priority", "Complexity"],
    [
      ["Fast Add with smart defaults", "V2.0", {text:"P0", bold:true, color:C.p0}, "Medium"],
      ["Recently Cooked + Similar Meals strip", "V2.0", {text:"P0", bold:true, color:C.p0}, "Low"],
      ["Recommendation quality upgrade", "V2.0", {text:"P0", bold:true, color:C.p0}, "Medium"],
      ["Meal Memory enhancements (History V2)", "V2.0", {text:"P0", bold:true, color:C.p0}, "Medium"],
      ["Explanation improvements", "V2.0", {text:"P0", bold:true, color:C.p0}, "Low"],
      ["Tiffin Planner Lite", "V2.1", {text:"P1", bold:true, color:C.p1}, "Medium"],
      ["Saved Favorites + Dependable Meals", "V2.1", {text:"P1", bold:true, color:C.p1}, "Low"],
      ["Effort-aware suggestions", "V2.1", {text:"P1", bold:true, color:C.p1}, "Medium"],
      ["Week View planning", "V2.2", {text:"P1", bold:true, color:C.p1}, "Medium"],
      ["Household preference profiles", "V2.2", {text:"P1", bold:true, color:C.p1}, "High"],
      ["Leftover / repeat-use cues", "V2.2", {text:"P1", bold:true, color:C.p1}, "Medium"],
      ["Seasonal and routine packs", "V2.x", {text:"P2", bold:true, color:C.p2}, "Medium"],
      ["Learning-to-rank personalization", "V2.x", {text:"P2", bold:true, color:C.p2}, "High"],
      ["Shared household mode", "V2.x", {text:"P2", bold:true, color:C.p2}, "High"],
    ],
    [3400, 1400, 1200, 1560]
  ),
  gap(),

  h1("Appendix B. Do Not Build in V2"),
  bullet("Full recipe library or recipe creation workflows."),
  bullet("Grocery list, pantry inventory, or shopping integration."),
  bullet("Detailed nutrition goals or health tracking."),
  bullet("Voice assistant or conversational chat interfaces."),
  bullet("Collaborative / shared household login."),
  bullet("Advanced L2R ML model before ≥100 meals and ≥50 interaction events per household."),
  bullet("Festival planning, multilingual UI, or social sharing."),
  gap(),
);

// ─────────────────────────────────────────────────────────────────────────────
// BUILD THE DOCUMENT
// ─────────────────────────────────────────────────────────────────────────────

const doc = new Document({
  numbering: {
    config: [
      {
        reference: "bullets",
        levels: [{
          level: 0, format: LevelFormat.BULLET, text: "\u2022",
          alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } }
        }, {
          level: 1, format: LevelFormat.BULLET, text: "\u25E6",
          alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 1080, hanging: 360 } } }
        }]
      },
      {
        reference: "numbers",
        levels: [{
          level: 0, format: LevelFormat.DECIMAL, text: "%1.",
          alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } }
        }]
      },
    ]
  },
  styles: {
    default: {
      document: { run: { font: "Arial", size: 22 } }
    },
    paragraphStyles: [
      {
        id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 32, bold: true, font: "Arial", color: C.brand },
        paragraph: { spacing: { before: 480, after: 200 }, outlineLevel: 0 }
      },
      {
        id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 26, bold: true, font: "Arial", color: C.accent },
        paragraph: { spacing: { before: 300, after: 120 }, outlineLevel: 1 }
      },
      {
        id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 22, bold: true, font: "Arial", color: "333333" },
        paragraph: { spacing: { before: 200, after: 80 }, outlineLevel: 2 }
      },
    ]
  },
  sections: [{
    properties: {
      page: {
        size: { width: 12240, height: 15840 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 }
      }
    },
    headers: {
      default: new Header({
        children: [new Paragraph({
          children: [
            new TextRun({ text: "Family Meal Assistant — FSD v2.0", size: 18, color: "888888", font: "Arial" }),
            new TextRun({ text: "\t", size: 18 }),
            new TextRun({ text: "CONFIDENTIAL · April 2026", size: 18, color: "888888", font: "Arial" }),
          ],
          tabStops: [{ type: "right", position: 8640 }],
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: C.brand, space: 1 } }
        })]
      })
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          children: [
            new TextRun({ text: "Page ", size: 18, color: "888888", font: "Arial" }),
            new TextRun({ children: [PageNumber.CURRENT], size: 18, color: "888888", font: "Arial" }),
            new TextRun({ text: " of ", size: 18, color: "888888", font: "Arial" }),
            new TextRun({ children: [PageNumber.TOTAL_PAGES], size: 18, color: "888888", font: "Arial" }),
          ],
          alignment: AlignmentType.CENTER,
          border: { top: { style: BorderStyle.SINGLE, size: 4, color: C.border, space: 1 } }
        })]
      })
    },
    children,
  }]
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync("D:\\dev\\code\\whatsCooking\\family_meal_assistant_fsd_v2.docx", buf);
  console.log("Done: family_meal_assistant_fsd_v2.docx written.");
});
