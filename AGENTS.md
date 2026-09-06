# Repository Directives & Continuous Deployment Rules

## 📦 Regola Inviolabile: Singolo APK all'Ultima Versione & Incremento Sempre Unico
Ad ogni modifica, fix, push o rilascio:
1. **Incremento di Versione Obbligatorio e Mai Riciclato**:
   - **MAI** riutilizzare la stessa versione o tag usati in precedenza.
   - Incrementare SEMPRE `versionName` nel formato numerico standard `num.num.num` (es. `2.8.0` -> `2.8.1` -> `2.8.2`...) e incrementare `versionCode` in `app/build.gradle.kts`.
2. **Sempre e Solo 1 Singolo File APK nel Repository e sulla Chiavetta**:
   - Nella root del repository e sulla chiavetta deve essere presente **SOLO 1 file APK**: `YarisHvFanControl-vX.Y.Z.apk` (con versione dinamica aggiornata).
   - **MAI** duplicare l'APK con file rolling (`YarisHvFanControl.apk`) o file multipli.
   - Prima di generare la nuova release, eliminare sempre il file APK della versione precedente.
   - La cartella `docs/` non deve tracciare file `.apk` nel repository (l'APK per il sito web GitHub Pages viene generato e posizionato dinamicamente in `docs/` dalla pipeline CI GitHub Actions durante il deploy).
3. **Aggiornamento di tutti i riferimenti Web & Deploy**:
   - `docs/index.html`, `docs/404.html` e `docs/preview.html` (header, hero badge, download button, changelog e sticky mobile bar con la nuova versione)
   - `build_apk.bat` e `.github/workflows/deploy.yml`
   - `README.md` e `CHANGELOG.md`
4. **Flusso di Verifica & Consegna**:
   - `gradle testDebugUnitTest` prima di ogni build
   - `build_apk.bat` per generare e firmare l'unico APK con certificato RSA
   - Commit, creazione nuovo tag git univoco `vX.Y.Z` e `git push origin main --tags`
