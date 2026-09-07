# Regole di Ingaggio per Push & Release

Questo documento definisce chi/cosa può fare `commit`, `tag` e `push` su questo repository, ed è complementare (non sostitutivo) alle direttive di versioning già definite in `AGENTS.md`.

## 1. Regola fondamentale: nessun push automatico senza conferma umana esplicita
Qualsiasi agente AI (Warp/Oz o altro) che lavora su questo repository **non deve mai** eseguire `git commit`, `git tag` o `git push` in autonomia, anche se `AGENTS.md` descrive un flusso di release che lo prevede.
- Un agente può preparare tutte le modifiche (codice, test, versione, changelog, sito, APK) e lasciarle pronte, non committate.
- L'agente deve sempre chiedere conferma esplicita all'utente prima di ogni `commit`/`tag`/`push`.
- Se l'utente chiede di "fixare", "aggiornare" o "compilare", questo **non** implica automaticamente il permesso di pubblicare: build e commit/push sono passaggi distinti e vanno confermati separatamente quando non esplicitamente richiesti insieme.

## 2. Flusso standard per una modifica/fix
1. Analisi del problema e ricerca (log, codice, eventuali fonti esterne).
2. Modifica di codice + test corrispondenti.
3. Bump versione (`versionCode`/`versionName` in `app/build.gradle.kts`) secondo Semantic Versioning (vedi `CHANGELOG.md`).
4. Esecuzione `gradle testDebugUnitTest` — nessuna build procede se i test falliscono.
5. `gradle assembleRelease` e sincronizzazione del singolo APK in root e in `docs/`.
6. Sincronizzazione riferimenti versione: `build_apk.bat`, `.github/workflows/deploy.yml`, `docs/index.html`, `docs/404.html`, `docs/preview.html`, `docs/simulator.js`, `README.md`, `CHANGELOG.md`.
7. **Stop.** Presentazione del riepilogo all'utente. Commit/tag/push solo dopo conferma esplicita.

## 3. Chi può autorizzare il push
Solo il proprietario del repository (`FrancescoCastaldi`) può autorizzare `commit`/`tag`/`push` su `main`. L'autorizzazione va data nella stessa conversazione in cui si richiede l'operazione, con una richiesta inequivocabile (es. "fai il commit e il push", "pubblica la release").

## 4. Branch protection
Il branch `main` ha `allow_force_pushes: false` e `allow_deletions: false` lato GitHub. Nessun agente deve tentare di aggirare queste protezioni (es. force-push, modifica delle regole di protezione) senza richiesta esplicita e motivata dell'utente.

## 5. Ambiente di build locale
Il toolchain di riferimento per build/test locali è quello pinnato in `build_apk.bat`:
- `JAVA_HOME=D:\Tools\jdk-21\jdk-21`
- `ANDROID_HOME=D:\Tools\android-sdk`
- Gradle 8.7 in `D:\Tools\gradle\gradle-8.7`

Non generare wrapper Gradle (`gradlew`/`gradlew.bat`) alternativi non tracciati dal repository: causano incoerenze tra ambienti e build fallite silenziosamente diverse da quelle reali del progetto.

## 6. Pipeline CI (GitHub Actions)
`.github/workflows/deploy.yml` compila e pubblica automaticamente su GitHub Pages ad ogni push su `main`. Poiché il push è manuale (vedi regola 1), anche l'attivazione della pipeline CI è indirettamente sotto controllo umano: non va mai forzata con `workflow_dispatch` per pubblicare modifiche non ancora approvate dall'utente.
