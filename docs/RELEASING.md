# Making a release

Releases are built on the maintainer's computer with the project's own key, not in CI: a CI build without that key would be signed with a throw-away debug key and could not update an installed copy.

1. Run the checks: `python3 tools/privacy_scan.py` and the tests in `tv-doctor/`.
2. Build and sign Lume (`lume/build.sh` with `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASS`, `RELEASE_KEY_ALIAS`), Lume Cast (Gradle, with `CASTBAY_KEYSTORE_FILE`, `CASTBAY_KEYSTORE_PASSWORD`, `CASTBAY_KEY_ALIAS`, `CASTBAY_KEY_PASSWORD`; run `ambient-cast/tools/fetch-openssl.sh` once) and the Gallery (Gradle, then `zipalign` and `apksigner`).
3. Name the files `lume-launcher.apk`, `ambient-cast.apk`, `ambient-gallery.apk`, zip `tv-doctor` as `TV-Doctor.zip`, and write `SHA256SUMS`: TV Doctor downloads exactly these names and refuses any file whose SHA-256 does not match.
4. `gh release create vX.Y.Z <files> --latest`. The key stays outside the repository; keep two copies of it: without it, no installed copy can be updated.
5. Update `docs/latest.json` (version and code of Lume, Lume Cast and Lume Gallery) in the same commit: Lume reads it, only while its About page is open, to say "New version". Without it, nobody is told.
