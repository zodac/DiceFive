# DiceFive: the languages (l10n)

The shipped translations, their glossaries, and how to add a language. How strings work is in `I18N.md`. Both
translations are machine-assisted and **not yet checked by a native speaker or on a device**. Each still needs a hand
pass, a TalkBack pass and a Play listing.

## Adding a language

1. **Both strings files.**
   - `app/shared/src/commonMain/composeResources/values-xx/strings.xml` holds the translation. Copy the base; every
     key, `%N$s`/`%N$d` placeholder and markup character (`` ` ``, `*`, `**`) must be present.
   - `app/android/src/main/res/values-xx/strings.xml` holds just `app_name`, so Android lists the language
     (`generateLocaleConfig`).
   - The app's name reaches strings as `%1$s`; never write it out (`StringResourcesTest`).
2. **`common_locale`**: the BCP 47 tag, with any numerals or region (`es-ES`, `ar-u-nu-arab`). It drives numerals,
   grouping, ordinals, lists, dates, plural forms and direction. Reorder `common_timestamp_pattern` too.
3. **Every CLDR plural category** the language has: Russian `few`/`many`; Arabic `zero`/`two`/`few`/`many`. A missing
   one silently falls back to `other` (`PluralFormsTest` catches it).
4. **`achievement_title_max_length`**, if 38 isn't right for the script. Then render the banner in that language to
   check it fits: a character count only stands in for width.
5. **Sora** holds Latin-1 only (no `œ`, `’`, `…`; `é ñ ¿ ¡ « » ×` are fine). A script wholly outside it falls back to
   the system font: add the locale to `StringChecks.localesOutsideSora`, so the Latin-1 guards skip it.
6. **Run the guards**:
   - `StringResourcesTest`, `AchievementTextTest`, `GameModeTextTest` and `StyleNamesTest`;
   - `TextFitTest` with the language's `qualifiers`;
   - a new `<Language>TextTest` like `SpanishTextTest`.
7. **A device pass**, with TalkBack.
8. **The store listing** (`PUBLISHING.md`), under the banned-words rule.

**Process that worked.**

- **Settle the glossary first.**
- **Dump, translate, rebuild, validate.**
    - Dump the base as `key|text` lines and translate a couple of hundred at a time.
    - Build the XML by script, then validate by script: keys, placeholders, markup counts, title caps, and Latin-1 in the
        Sora text.
- **Make bulk changes with a script**, then read the diff.
- **Fit.** A longer language fits by its box growing or its text shrinking (never below 12sp), **never by splitting a
    word**. `TextFitTest` draws each screen at 360dp and fails on a broken word. It sees only what's on screen.
- **Follow the language's own capitalisation**: Spanish headings are sentence case.
- **Where English reuses one word for two things** (Rose / Pink, Opening), pick distinct translations.
- **Initialisms are transliterated** in non-Latin scripts (D20 → *دي ٢٠*), not re-abbreviated.

## Spanish (`values-es`)

- **Locale.** Spain, `es-ES`: "34.521" grouping, and four-digit numbers ungrouped.
- **Style.** Informal *tú*, `¿ ?` / `¡ !`, `×` for the cup, a no-break space before `pts` and the timer's `s`.
    Headings and buttons in sentence case ("Nueva partida").
- **Dates.** `common_timestamp_pattern` is still English's; a native speaker may prefer "7 oct 2026, 14:05".
- **Glossary.**
    - Modes: Standard *Estándar*, Tricolour *Tricolor*, Quickfire *Ráfaga*, 7 Dice Stud *Stud de 7 dados*, Third Wind
        *Tercer aliento*, Hit List *Lista de objetivos*.
    - Actions and objects: hold *conservar*, release *soltar*, roll *tirar*, dice cup *cubilete*, mat *tapete*, scorecard
        *tarjeta*, slot *ranura*.
    - Boxes: *Unos, Doses, Treses, Cuatros, Cincos, Seises*, *Full*, *Escalera pequeña / grande*, *Azar*, *Doble pareja*,
        *Pares / Impares*, *Full de colores*.
    - Colours: Rose *Rosa antiguo* / Pink *Rosa*.
    - The marks (3x, 4x, 5x, Alibi, 2+2) are never translated.
- **Tests.** `SpanishTextTest`: ordinals "1.º puesto", grouping "34.521", `¿`.

## Arabic (`values-ar`, right to left)

- **Locale.** Modern Standard Arabic. `common_locale` is `ar-u-nu-arab`, so numbers are Arabic-Indic ("٣٤٬٥٢١"), and the
    layout is mirrored.
- **Plurals.** All six forms for each of the 26 plurals; `few` is 3-10 and `many` 11-99.
    - `%N$d` stays in every form (`StringChecks`), so a dual that reads better without a number is written "رميتان (٢)".
- **Digits typed into text** are written Western in the source, then converted by script.
    - The script skips `%1$d` and the marks (3x, 4x, 5x, 2+2), which stay Latin and are isolated.
    - Thousands "," becomes "٬", and "%" becomes "٪".
    - If you edit by hand, type Arabic-Indic digits directly.
- **Sora** doesn't hold Arabic, so titles and mode names fall back to the system font. The title cap stays 38.
- **Wording.**
    - Style nouns are indefinite ("نرد", "كأس نرد", "حصيرة"), because they slot into "نمط %2$s «%1$s»" and after ل-.
    - Quotes are «guillemets».
    - Signs come first in the string ("+%1$d", "×%1$d", "=%1$s"), so they land on the right.
- **Glossary.**
    - Boxes: *الواحدات, الاثنيات, الثلاثيات, الرباعيات, الخماسيات, السداسيات*, *فول هاوس*, *تسلسل صغير / كبير*, *الحظ*.
    - Actions and objects: dice cup *كأس النرد*; hold *ثبّت*, release *حرّر*, roll *ارمِ*; scorecard *بطاقة النتائج*;
        mat *حصيرة*.
    - Modes: *القياسي, ثلاثي الألوان, الإطلاق السريع, ستاد 7 أحجار, النفس الثالث, قائمة الأهداف*.
    - "Alibi" and the marks are not translated.
- **Still to check.** Every screen mirrored: about 10 absolute left/right usages remain (offsets and
    `Alignment.*Start/End` mirror; absolute ones don't). The canvas art doesn't depend on direction.
- **Tests.** `ArabicTextFitTest` (re-runs `TextFitTest`, whose base class is `open`) and `ArabicTextTest`.

## Platforms

- **Android.**
    - `androidResources { generateLocaleConfig = true }` gives the per-app language setting on 13+.
    - `res/resources.properties` sets `unqualifiedResLocale=en-GB`.
    - Each language has `res/values-xx/strings.xml` with just `app_name`.
- **Play Store**: a listing per locale (`PUBLISHING.md`).
