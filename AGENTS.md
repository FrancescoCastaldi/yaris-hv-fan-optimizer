# Repository Directives & Continuous Deployment Rules

## 📦 Regola Inviolabile: Singolo APK all'Ultima Versione & Incremento Sempre Unico
Ad ogni modifica, fix, push o rilascio:
1. **Incremento di Versione Obbligatorio e Mai Riciclato**:
   - **MAI** riutilizzare la stessa versione o tag usati in precedenza.
   - Incrementare SEMPRE `versionName` nel formato numerico standard `num.num.num` (es. `2.8.0` -> `2.8.1` -> `2.8.2`...) e incrementare `versionCode` in `app/build.gradle.kts`.
2. **Sempre e Solo 1 Singolo File APK per Versione (Root + Docs)**:
   - Nella root del repository deve essere presente **SOLO 1 file APK**: `YarisHvFanControl-vX.Y.Z.apk` (con versione dinamica aggiornata).
   - **MAI** duplicare l'APK con file rolling (`YarisHvFanControl.apk`) o file multipli.
   - Prima di generare la nuova release, eliminare sempre il file APK della versione precedente.
   - La cartella `docs/` traccia **solo** l'APK della versione corrente (`docs/YarisHvFanControl-vX.Y.Z.apk`): GitHub Pages è configurato con "Deploy from a branch" (`main` + `/docs`) e serve direttamente la cartella `docs/` committata, quindi il download dal sito deve funzionare anche prima che la pipeline CI termini.
3. **Aggiornamento di tutti i riferimenti Web & Deploy**:
   - `docs/index.html`, `docs/404.html` e `docs/preview.html` (header, hero badge, download button, changelog e sticky mobile bar con la nuova versione)
   - `build_apk.bat` e `.github/workflows/deploy.yml`
   - `README.md` e `CHANGELOG.md`
4. **Flusso di Verifica & Consegna**:
   - `gradle testDebugUnitTest` prima di ogni build
   - `build_apk.bat` per generare e firmare l'unico APK con certificato RSA
   - Commit, creazione nuovo tag git univoco `vX.Y.Z` e `git push origin main --tags` **solo dopo conferma umana esplicita** (vedi regola 5)
5. **Regola Inviolabile: Nessun Commit/Tag/Push Automatico**:
   - Qualsiasi agente AI (Warp/Oz o altro) DEVE preparare codice, test, versione, changelog, sito e APK, ma **MAI** eseguire `git commit`, `git tag` o `git push` senza che l'utente lo richieda esplicitamente nella stessa conversazione (es. "fai il commit e il push", "pubblica la release").
   - Richieste generiche come "fixa", "aggiorna", "compila la nuova versione" **non** autorizzano da sole commit/tag/push: sono passaggi distinti che vanno confermati separatamente.
   - Dettagli completi del processo e del toolchain di build in `PUSH_POLICY.md`.
