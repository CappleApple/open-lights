# Open Lights 2.0.0 split verification

Release validation for 2.0.0 completed 2026-10-28. Targets Forge 1.20.1 / Java 17 and NeoForge 1.21.1 / Java 21.

## Build and artifact checks

- Forge and NeoForge each pass 101 unit tests with zero failures, errors, or skips, including inert-extension behavior and RF arithmetic/affordability boundaries.
- TaCZ integration passes 13 tests and builds against the library. Its dependency range now accepts Open Lights 2.x. The existing production QA helper compiles with migrated addon imports.
- `verifySplitJars` checks metadata, disjoint classes and Java packages, absence of content/data in the library, absence of bytecode dependencies from the library to addons, foreign refmaps, and the Forge runtime shadow-field mapping.
- All runtime JSON and TOML resources parse. New documentation links and configuration examples were checked against source.

Final build commands, with the matching JDK configured:

```powershell
# Parent TaCZ checkout, including Forge subproject and QA compilation
.\gradlew.bat --offline test build -PproductionQa productionQaJar :open-lights:splitQaJar

# open-lights/neoforge
.\gradlew.bat --offline test build splitQaJar
```

The ordinary build command for either standalone loader directory remains `.\gradlew.bat test build`.

## Packaged runtime coverage

The isolated `splitQa` helper compiles against the library alone. Client runs use actual packaged JARs without development class/resource directories. Each loader is checked with:

| Installed mods | Client | Dedicated server |
| --- | --- | --- |
| Library only | Both passed | Both passed fresh/restart/reload |
| Library + Flashlight | Both passed | Both passed fresh/restart/reload |
| Library + GI | Both passed | Both passed fresh/restart/reload |
| Library + both addons | Both passed | Both passed fresh/restart/reload |

Checks cover zero unsolicited lights and zero world/GI cache activity without GI; class absence for the GI implementation; populated world probes and replacement lighting with GI; API light submission/rendering/removal; flashlight registration only with its addon; and real item energy-capability charge simulation, receive-only behavior, stack serialization, server inventory drain and exhausted-item toggle refusal.

Clients run hidden, muted and without mouse capture. Dedicated servers bind loopback, perform `/reload`, save, stop, and restart. No user game instance or world was modified. Runtime Forge is 47.4.23; the build baseline remains 47.4.10. NeoForge is 21.1.248. Neither runtime includes Distant Horizons or a shader loader.

The Forge flashlight-only before/on/depleted captures were inspected. The on frame changed 119,412 static receiver pixels by more than two RGB levels (maximum 40); the depleted frame matched the off frame exactly. The renderer selected one spot light and produced one shadow pass before reusing it. NeoForge changed 119,414 receiver pixels under the same check and also returned exactly to the off image when depleted. This verifies visible output and shutoff under the default profile, not a brightness preference or performance target.

## Artifacts

| Runtime JAR | Bytes | SHA-256 |
| --- | ---: | --- |
| `ol-global-illumination-forge-1.20.1-2.0.0.jar` | 159741 | `bf8866db35bef91255392e4977953ab1e22caa9ff6718b728ac74d5f765d3564` |
| `open-lights-flashlight-forge-1.20.1-2.0.0.jar` | 24907 | `e76bcc692a3a2abe836a21167f8bf76bc385682607818cbfde3f2aa6f1e70547` |
| `openlights-1.20.1-2.0.0.jar` | 166990 | `850e354336197962450fce6fcb42db2e542936e3a3eaf4079f36bd6188e91f21` |
| `ol-global-illumination-neoforge-1.21.1-2.0.0.jar` | 155511 | `2a2bd08f9b88756a8ec23239ca8a1e4fe118e8d21f6bef5e02e832420d161e14` |
| `open-lights-flashlight-neoforge-1.21.1-2.0.0.jar` | 20365 | `42687d87258c2d8afc485f6e86f545d001b447153a40e343019d3c1af3325d16` |
| `openlights-neoforge-1.21.1-2.0.0.jar` | 167670 | `8d0696b2f3572cc7f356707386a694a235ced9da14954787713f61b5e03e3419` |

## Evidence and limits

Raw build logs, launcher, disposable runtime directories, per-run receipts and hashes remain under the parent checkout's ignored `.research/`. Successful dedicated summaries: `split-forge-20261028T050756593232.json` and `split-neoforge-20261028T050345337784.json`. Successful client summaries: `split-forge-20261028T050646207065.json` (core), `split-forge-20261028T050807884206.json` (other combinations), `split-forge-20261028T051131612428.json` (stronger flashlight visual check), and `split-neoforge-20261028T051222012736.json` (all combinations). Aggregate: `split-2.0.0-final-runtime-audit.json`. Nine packaged clients and sixteen dedicated launches passed; every dedicated launch performed a reload and clean shutdown.

Failed attempts are preserved separately: sandbox native startup failure, a Forge artifact overwritten by an overlapping parent development build, and early QA menu stalls. The corrected QA helper includes resource-pack metadata and startup diagnostics; the launcher bypasses first-run onboarding. Production JAR mapping checks were strengthened, and final Forge packaging ran sequentially. These failed attempts are not passing runtime evidence.

This validates the split package boundaries and the standard energy-capability contract. It does not establish compatibility with every RF charger, remote multiplayer combination, shader pack, Distant Horizons version, or modpack. Existing 1.8.x reports remain historical. No whole-pipeline performance benchmark or production acceptance is claimed.
