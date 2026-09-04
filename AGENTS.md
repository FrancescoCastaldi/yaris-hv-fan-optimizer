# Repository Directives & Continuous Deployment Rules

## 📦 Regola Inviolabile: Rilascio APK & Sito Web Sempre Aggiornato
Ad ogni modifica, fix o rilascio:
1. **Sito Web (`docs/`) e Root sempre sincronizzati con l'APK**:
   - `YarisHvFanControl.apk` (link rolling sempre all'ultima versione)
   - `YarisHvFanControl-vX.Y.Z.apk` (file versionato esatto)
   - `docs/YarisHvFanControl.apk`
   - `docs/YarisHvFanControl-vX.Y.Z.apk`
2. **Aggiornamento di tutti i riferimenti Web & Deploy**:
   - `docs/index.html` e `docs/404.html` (header, hero badge, download button, changelog e sticky mobile bar)
   - `docs/vercel.json` (header MIME type per il nuovo APK)
   - `build_apk.bat` e `.github/workflows/deploy.yml`
   - `README.md` e `CHANGELOG.md`
3. **Flusso di Verifica & Consegna**:
   - `gradle testDebugUnitTest` prima di ogni build
   - `build_apk.bat` per generare e firmare l'APK con certificato RSA
   - Commit, creazione tag git `vX.Y.Z` e `git push origin main --tags`
