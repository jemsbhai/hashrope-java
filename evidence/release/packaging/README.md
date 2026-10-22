# Release packaging TDD

The tests were written and executed before release build changes. `01-red` preserves the original POM and tests: three archive subtests failed for missing MIT notices and the release-profile test failed for absence of the profile. The metadata and ordinary-build isolation tests passed.

The first implementation added the Portal/GPG profile and license resources. The attempted green in `02-green` still failed for the Javadoc license. Enabling doc-files copying also failed (`doc-resource-test.log`); these outcomes are retained without relabeling them as successes. The minimal passing fix explicitly copies the same MIT resource into the Javadoc output during prepare-package. `03-green` and `final-green.log` record all four tests passing.

The POM was then reformatted for maintainability without behavioral changes. `refactor.log` records all four packaging tests passing again, and `../bundle-build-refactor.log` records all 10,608 JUnit tests plus creation of the signed bundle with upload disabled. `../local-consumer.log` records two independent consumer tests against the locally installed release build; a separate clean Central-only run is required after publication.

Reproduce the original red in a temporary checkout of source commit `79dbec41da04a096ed9c87fca19cfb55df23b024`: copy `01-red/test_release_packaging.py` to `tests/test_release_packaging.py`, run the README Maven package command, then `python -B tests/test_release_packaging.py`. Expected: exit 1 with the four failures described above. Reproduce green/refactor from the delivered release source: run Maven package with empty settings, then the same Python command; expected exit 0. No original Python checkout or private signing material is needed for these packaging replay checks.

The first batch signing attempt failed because the agent had no usable cached unlock. Interactive signing subsequently succeeded, and later batch signing succeeded with the unlocked agent. Signing failures are operational evidence, distinct from the intentionally failing packaging tests.
