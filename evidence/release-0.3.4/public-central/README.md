# Java 0.3.4 publication preparation — pending

The coordinated release gate passed, Java source PR #2 merged at `aa0708905ee629654a4d38654146cf3a8b1d7b42`, and Java 17/24 CI succeeded. The frozen signed bundle is 210,486 bytes, SHA-256 `9749fc47d485abe39a553cfaa44987c997ba88b87fe5cbba2374ae2dbd54713e`.

**No Java upload or publication has been submitted.** `upload-preparation.json` records the supported browser file-picker timeouts, the user-confirmed file-access setting, and the fresh authenticated Portal session. The user has been asked to select the exact frozen ZIP in the ready upload form. These failures concern the browser tool, not the validated package.

`verify_public.py` is prepared and independently reviewed; it has not yet run for 0.3.4. Its location assumes execution from ignored `target/public-central-0.3.4/` (two parent levels below the checkout root). Copy it there for reproduction, retain the reviewed bundle at `target/release-0.3.4-frozen/central-bundle.zip`, and choose a new output directory:

```text
python target/public-central-0.3.4/verify_public.py --output target/public-central-0.3.4/run-0.3.4
```

The driver requires the exact frozen bundle hash, verifies all 24 canonical public files and four signatures, then creates an empty Maven cache and a separate consumer using credentials-free Central-only settings. It requires all three consumer tests and exact POM/main/source/Javadoc bytes. Maven repository origins must be either the configured Central-only ID or that ID plus the SHA-1 of the configured Central URL, as used by Maven 3.10. No library install or staging repository can satisfy resolution. Existing output directories are rejected.

Source, runtime, fixtures, signed artifacts and the `v0.3.4` tag remain unchanged by this documentation update. Public availability will be recorded only after upload, publication and the independent checks finish.
