# Contributing

Thank you for helping. The easiest and most useful ways:

1. **Test on your TV and report.** Open an issue with the brand, model, year, system version and what TV Doctor showed. This is the biggest gap: only one device has been tested for real.
2. **Translate.** TV Doctor and Lume use plain tables of text: `tv-doctor/app_page.html` (the `I18N` object) and `lume/src/org/lumesuite/launcher/It.java`. Copy a language, translate, open a pull request. Say whether the translation is by a native speaker.
3. **Fix or add a TV guide.** Per-year guides live in `tv-doctor/closed_tvs.json` and `tv-doctor/enable_guides.json`. Every menu path must come from the TV itself or from a source you link in the pull request.

Rules that keep the product safe: apps are turned off, never uninstalled; every change must be undoable; anything that could stop a TV from starting needs a health check and an automatic put-back.
