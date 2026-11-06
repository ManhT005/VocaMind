# Unicode 16.0 normalization and casefold provenance

Official pinned inputs (downloaded over HTTPS on 2026-11-06):

- https://www.unicode.org/Public/16.0.0/ucd/CaseFolding.txt
- https://www.unicode.org/Public/16.0.0/ucd/UnicodeData.txt
- https://www.unicode.org/Public/16.0.0/ucd/DerivedNormalizationProps.txt
- https://www.unicode.org/Public/16.0.0/ucd/NormalizationTest.txt
- License: https://www.unicode.org/license.txt (Unicode License V3).

SHA256SUMS records the downloaded bytes. `generate.py` checks hardcoded source
checksums before generating Java. Both generated files include the full license
notice. No download occurs at runtime, during generation, or during tests.

Run `python tools/unicode/generate.py` from the repository root. Commit the
inputs, generated sources and checksums together. CI repeats generation and
rejects differences in generated files.

Casefold includes only C/F mappings (full default folding; S/T excluded).
Supplementary mappings use surrogate pairs in Java literals. NFC uses canonical
decompositions and combining classes from UnicodeData, excludes the full
composition-exclusion set from DerivedNormalizationProps, and implements Hangul
algorithmically. Compatibility decompositions are excluded. Unassigned code
points are identity-mapped, independent of the OS Unicode version.

`UnicodeNfc` implements decomposition, stable canonical ordering and blocked
composition according to [UAX #15, Unicode 16 revision](https://www.unicode.org/reports/tr15/tr15-56.html).
This replaces java.text.Normalizer, whose JDK 17 behavior failed 664 transformations
in the 19,965-row Unicode 16 official corpus. Example: U+105D2 U+0307 must compose
to U+105C9. No failing expectations were relaxed or skipped.

NfcConformanceTest and its AndroidNfcConformanceTest subclass use identical
expectations on the JDK and Robolectric APIs 26/35. Every official row checks all
five NFC invariants; every scalar absent from Part 1 is checked for NFC identity.
The production adapter has a regression for the post-JDK composition above.
These are Robolectric tests, not physical-device execution. Production NFC and
folding depend only on the committed Java tables and code, not platform ICU/NFC.

No library is added to the APK. Raw Unicode inputs remain tooling/test fixtures;
only generated Java tables and the normalization algorithm are packaged.

Reproduce the platform comparison with JDK 17:

```sh
java tools/unicode/PlatformNfcProbe.java tools/unicode/NormalizationTest-16.0.0.txt
```

This diagnostic probes the platform only; it is not the production adapter and
is not an alternative set of conformance expectations.
