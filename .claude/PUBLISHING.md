# DiceFive: Play Store publishing reference

Requirements for getting this app onto the Play Store that aren't produced by the normal build
and aren't covered anywhere else. `.claude/DESIGN.md`'s Phase 12 covers the *GitHub* release
pipeline (signed `assembleRelease` APK attached to a tagged GitHub Release on every push to
`main`) - that is a different thing from a Play Store submission, which this file is about.

Nothing in this file is done yet; it's a checklist for whenever that submission actually happens.

## Store listing assets

- **Hi-res icon.** The Play Console wants its own 512x512 PNG for the store listing page,
  separate from the in-app adaptive icon (`app/src/main/res/drawable/ic_launcher_foreground.xml`
  / `@color/ic_launcher_background`) and not produced by `assembleDebug`/`assembleRelease` at
  all. Export the same artwork (the cup, dice, gold-on-navy brand colours - see `UI.md`'s Colour
  section for the palette) as a flat 512x512 PNG by hand and upload it separately.
- **Feature graphic, screenshots, short/long description** - not yet drafted. Screenshots should
  come from the real app once there's a build worth showing, not mocked up.

## Everything else

Signing, versioning, and store-listing metadata (release notes, content rating, data-safety form)
are not yet worked out. Add sections here as they're figured out, rather than letting this
knowledge live only in a conversation.
