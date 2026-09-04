# Repository Directives & Continuous Deployment Rules

## 📦 Regola Inviolabile: Rilascio APK & Incremento di Versione Sempre Unico
Ad ogni modifica, fix, push o rilascio:
1. **Incremento di Versione Obbligatorio e Mai Riciclato**:
   - **MAI** riutilizzare la stessa versione o tag usati in precedenza.
   - Incrementare SEMPRE `versionName` nel formato numerico standard `num.num.num` (es. `2.8.0` -> `2.8.1` -> `2.8.2`...) e incrementare `versionCode` in `app/build.gradle.kts`.
2. **Sito Web (`docs/`) e Root sempre sincronizzati con l'APK**:
   - `YarisHvFanControl.apk` (link rolling sempre all'ultima versione)
   - `YarisHvFanControl-vX.Y.Z.apk` (file con nuova versione incrementata)
   - `docs/YarisHvFanControl.apk`
   - `docs/YarisHvFanControl-vX.Y.Z.apk`
3. **Aggiornamento di tutti i riferimenti Web & Deploy**:
   - `docs/index.html` e `docs/404.html` (header, hero badge, download button, changelog e sticky mobile bar con la nuova versione)
   - `docs/vercel.json` (header MIME type per il nuovo APK)
   - `build_apk.bat` e `.github/workflows/deploy.yml`
   - `README.md` e `CHANGELOG.md`
4. **Flusso di Verifica & Consegna**:
   - `gradle testDebugUnitTest` prima di ogni build
   - `build_apk.bat` per generare e firmare l'APK con certificato RSA
   - Commit, creazione nuovo tag git univoco `vX.Y.Z` e `git push origin main --tags`
