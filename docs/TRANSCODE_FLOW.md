# Transcode Flow

> End-to-end runtime flow for: user taps download → audio downloaded to cache temp → transcoded to MP3 → written into the user-selected SAF directory → DB updated → notification shown.
> For module architecture and design rationale, see [ARCHITECTURE.md](./ARCHITECTURE.md).

This document describes the **as-implemented** flow on `master`. The code compiles and the flow is fully wired; the FFmpeg native transcode and SAF write paths still need on-device verification (see [Runtime vs. build](#runtime-vs-build) below).

## Configuration prerequisites

In **Settings → Transmission** (`shared/.../ui/screen/settings/transmission/TransmissionScreen.kt`):

1. **Download root dir** — `rememberDirectoryPickerLauncher` opens the SAF directory picker. On result, `persistSafPermission(dir.path)` calls `ContentResolver.takePersistableUriPermission` (read + write) so the tree URI survives a reboot, then `DownloadRootDirPreference.put(scope, dir.path)` stores the `content://` URI string.
2. **Auto transcode to MP3** — `AutoTranscodeMp3Preference` switch (default on).
3. **Transcode bitrate** — `TranscodeBitratePreference`, one of 64 / 96 / 128 / 192 kbps (default 128).
4. **Naming template** — `DownloadNamingTemplatePreference`, one of `TitleAndShow` / `ShowNumberTitle` / `DateAndTitle` (default `TitleAndShow`).
5. **Keep original after transcode** — `KeepOriginalAfterTranscodePreference` switch (default off).

The transcode path activates only when `AutoTranscodeMp3Preference == true` **and** `DownloadRootDirPreference` is non-empty.

## End-to-end flow

### Step 1 — User taps download on an episode

The UI calls `DownloadStarter.download(url)`.

### Step 2 — `DownloadStarter.download` chooses the destination

`shared/.../download/DownloadStarter.kt`:

1. Reads `AutoTranscodeMp3Preference` and `DownloadRootDirPreference` from `dataStore`.
2. Computes `useTranscodePath = autoTranscode && rootUri.isNotEmpty()`.
3. **If active:** `saveDir = Const.DOWNLOAD_TEMP_DIR` (Android: `cacheDir/downloads`). The article/group/folder computation is skipped — `TranscodeHook` will re-resolve the article from `entity.url` later.
4. **If inactive:** falls back to the base behavior — resolves article → feed → group, then `MediaRepository.getFolder(...)` under `MediaLibLocationPreference` to compute `saveDir`.
5. Calls `IDownloadManager.download(url = url, path = saveDir)` (no `fileName` — the downloader generates one).

### Step 3 — Downloader module downloads to cache temp

The generic `downloader` module downloads the URL into `saveDir` and records a `DownloadEntity` (`path = Const.DOWNLOAD_TEMP_DIR`, `fileName` = downloader-generated). The downloader knows nothing about transcode or SAF.

### Step 4 — Download completes → `Event.Success`

`Downloader.observeEvent()` emits `Event.Success(entity)`.

### Step 5 — `DownloadManager.listenDownloadEvent` dispatches

`shared/.../download/DownloadManager.kt` (companion `listenDownloadEvent`, launched on `Dispatchers.IO`):

1. Re-reads `AutoTranscodeMp3Preference` and `DownloadRootDirPreference`.
2. **If active:** `runCatching { get<TranscodeHook>().onDownloadSuccess(event.entity) }.onFailure { Logger.e(throwable = it) { "TranscodeHook failed" } }`.
3. **If inactive:** base behavior — `EnclosureDao.getMediaArticleId(url)` → `ArticleDao.getArticleWithFeed(articleId).first()` → `MediaRepository.addNewFile(Path(path, fileName), …)` to register the file in the media library.

### Step 6 — `TranscodeHook.onDownloadSuccess`

`shared/.../download/TranscodeHook.kt`. The whole body is wrapped in `runCatching`; on failure it logs and marks `transcodeStatus = "failed"`.

1. **Defensive guard:** if `!autoTranscode || rootUri.isEmpty()` return (`DownloadManager` already gated, but re-checked here in case the hook is invoked directly).
2. **Resolve article:** `EnclosureDao.getMediaArticleId(entity.url)` → `ArticleDao.getArticleWithFeed(articleId).first()`. Null → return.
3. **Map metadata:** `article.toEpisodeMetadata()` → `EpisodeMetadata` (show name, title, episode/season number, pubDate).
4. **Read remaining prefs:** `NamingTemplate.valueOf(DownloadNamingTemplatePreference)`, `TranscodeBitratePreference`, `KeepOriginalAfterTranscodePreference`. Build `TranscodeConfig(bitrateKbps = bitrate)`.
5. **Detect source format:** `AudioFormat.fromUrl(entity.url)`.
6. **Render output name:** `sanitizeFileName(template.render(metadata), extension = "mp3", maxLength = 200)` → e.g. `EP1_ Intro - Daily Tech.mp3`.
7. **Temp input path:** `tempInputPath = Path(entity.path, entity.fileName).toString()`.
8. **Branch on `shouldTranscode(sourceFormat, config)`:**
   - **Transcode branch** (source is not MP3 and not UNKNOWN): `tempOutputPath = Path(entity.path, "$outputFileName.tmp")`; `transcoder.transcode(input = tempInputPath, output = tempOutputPath, config = config).collect { /* progress ignored */ }`; then `writeTranscodedToSaf(tempFile = PlatformFile(tempOutputPath), rootTreeUri = rootUri, showName = metadata.showName, fileName = outputFileName)` → `finalUri`; `finalSize = PlatformFile(tempOutputPath).size()`; delete `tempOutput` (`runCatching { … .delete(mustExist = false) }`).
   - **Direct-copy branch** (source already MP3, or UNKNOWN): `writeTranscodedToSaf(tempFile = PlatformFile(tempInputPath), …)` → `finalUri`; `finalSize = PlatformFile(tempInputPath).size()`.
9. **Update DB:** `DownloadDao.update(entity.copy(outputUri = finalUri, transcodeStatus = "success", finalSize = finalSize))`.
10. **Cleanup:** if `!keepOriginal`, delete `tempInput` (`runCatching { PlatformFile(tempInputPath).delete(mustExist = false) }`).

> Note on "Keep original": it controls the *downloaded source* file (`tempInput`). The transcoded `.tmp` is always deleted once copied to SAF. `finalSize` is read from the temp file (not the SAF target), which equals the bytes streamed into SAF.

#### FFmpegKit transcode (`transcoder` androidMain)

`Transcoder.transcode` builds `-i "$input" -c:a libmp3lame -b:a ${bitrateKbps}k "$output"` and runs it via `FFmpegKit.executeAsync` inside a `callbackFlow`. The statistics callback emits `TranscodeProgress(processedSeconds = stats.time, totalSeconds = null, sizeBytes = stats.size)`; completion (success, cancel, or failure) closes the flow. `awaitClose { FFmpegKit.cancel(session.sessionId) }` cancels the session if the collector is cancelled.

#### SAF write (`SafWriter.android.kt`)

`writeTranscodedToSaf(tempFile, rootTreeUri, showName, fileName)`:

1. `root = PlatformFile(rootTreeUri)` (SAF tree URI → `UriWrapper`).
2. `showDir = root div showName`; `if (!showDir.exists()) showDir.createDirectories()`.
3. **Collision resolution:** if `showDir div fileName` exists, insert ` (2)`, ` (3)`, … before the extension (`episode.mp3` → `episode (2).mp3` → `episode (3).mp3`) until a free name is found. A file with no extension gets the suffix appended to the whole name.
4. **Stream bytes:** `tempFile.source().buffered().use { input -> target.sink(append = false).use { input.transferTo(it) } }`. On a `UriWrapper`, `sink()` creates the document via `DocumentsContract` when it does not yet exist.
5. Return `target.path` (the `content://` document URI string).

### Step 7 — Success notification

`DownloadNotificationManager.sendDownloadSuccessNotification(totalBytes, finalSize = 0L)` displays `finalSize` when `> 0`, otherwise `totalBytes`.

> **Timing caveat:** transcoding runs in `shared` *after* the download-success event fires, and `sendDownloadSuccessNotification` is called from the downloader around the moment of `Event.Success`. At that point `finalSize` is typically still `0`, so the user usually sees `totalBytes` (the source size). Reliably showing the transcoded size would need a follow-up "transcode complete" notification, which is **not** implemented.

## Sequence diagram

```mermaid
sequenceDiagram
    participant U as User / UI
    participant DS as DownloadStarter (shared)
    participant DM as DownloadManager (shared)
    participant DL as Downloader (downloader)
    participant TH as TranscodeHook (shared)
    participant TC as Transcoder (transcoder / FFmpegKit)
    participant SW as SafWriter (shared / FileKit)
    participant DAO as DownloadDao (downloader DB)
    participant Notif as DownloadNotificationManager

    U->>DS: download(url)
    DS->>DS: read AutoTranscodeMp3 + DownloadRootDir
    alt transcode path active
        DS->>DL: download(url, path = Const.DOWNLOAD_TEMP_DIR)
    else inactive (base)
        DS->>DS: resolve article/feed/group -> getFolder
        DS->>DL: download(url, path = mediaLibFolder)
    end
    DL->>DL: download to path/fileName, persist DownloadEntity
    DL->>DM: Event.Success(entity)
    alt transcode path active
        DM->>TH: onDownloadSuccess(entity) [runCatching]
        TH->>TH: EnclosureDao.getMediaArticleId(url) -> ArticleDao.getArticleWithFeed
        TH->>TH: toEpisodeMetadata() + render name
        TH->>TH: AudioFormat.fromUrl(url) + shouldTranscode
        alt shouldTranscode (not MP3, not UNKNOWN)
            TH->>TC: transcode(input, output.tmp, config)
            TC-->>TH: TranscodeProgress flow (collected)
            TH->>SW: writeTranscodedToSaf(tempOutput, rootUri, show, name)
            TH->>TH: finalSize = tempOutput.size(); delete tempOutput
        else already MP3 / UNKNOWN
            TH->>SW: writeTranscodedToSaf(tempInput, rootUri, show, name)
            TH->>TH: finalSize = tempInput.size()
        end
        SW->>SW: root/showName/ createDirectories + collision resolve + sink/transferTo
        SW-->>TH: finalUri (content://)
        TH->>DAO: update(entity: outputUri, transcodeStatus="success", finalSize)
        opt not keepOriginal
            TH->>TH: delete tempInput
        end
    else inactive (base)
        DM->>DM: addNewFile(Path(path,fileName)) to media library
    end
    DL->>Notif: sendDownloadSuccessNotification(totalBytes, finalSize=0)
    Note over Notif: shows finalSize if >0 else totalBytes<br/>(typically totalBytes - transcode not done yet)
```

## Error handling

| Scenario | Behavior |
|---|---|
| **SAF permission lost after reboot** | Fixed by `persistSafPermission` → `takePersistableUriPermission` at picker time. Without it the picker's grant dies with the process and `writeTranscodedToSaf` would throw `SecurityException` on the next launch. |
| **Transcode failure** (FFmpeg error, unsupported codec, etc.) | `TranscodeHook`'s `runCatching` catches it → `Logger.e` → `DownloadDao.update(entity.copy(transcodeStatus = "failed"))`. The original downloaded temp file is **not** deleted (the cleanup step is inside the `runCatching` block, after the failure point). `DownloadManager` also wraps the whole call in its own `runCatching`. |
| **Disk space exhaustion** | FFmpegKit fails to write the `.tmp` output → surfaces as a transcode failure (row above). No explicit pre-check is performed. |
| **Filename collision in SAF** | `SafWriter` appends ` (2)`, ` (3)`, … before the extension until a free name is found. The collision-resolved URI is what gets stored in `DownloadEntity.outputUri`. |
| **Non-transcodable / already-MP3 source** | `shouldTranscode` returns `false` for `AudioFormat.MP3` (already the target) and `AudioFormat.UNKNOWN` (cannot decide — skip to avoid a lossy re-encode of unknown content). The downloaded file is copied straight to SAF; `transcodeStatus` is still set to `"success"`. |
| **Article not found for `entity.url`** | `EnclosureDao.getMediaArticleId` returns null (or `ArticleDao.getArticleWithFeed` returns null) → `TranscodeHook` returns early inside `runCatching`; no DB update, temp file left in cache. |
| **`DownloadRootDirPreference` empty while `AutoTranscodeMp3` on** | Activation condition false → base behavior (download to media-library folder, `addNewFile`). No transcode, no SAF write. |

## Runtime vs. build

- **Compiles and is wired:** the module graph, Koin registrations, hook points, preferences, migrations, and the `TranscodeHook` / `SafWriter` / `Transcoder` implementations are all in place and compile against the project's KMP / Room3 / FileKit / FFmpegKit stack. Unit tests in `transcoder` cover `AudioFormat`, `TranscodeConfig`, `shouldTranscode`, `NamingTemplate`, and `FileNameSanitizer` (pure commonMain logic).
- **Needs device verification:** the parts that exercise native code and Android framework APIs — FFmpegKit native transcode (`-c:a libmp3lame`), SAF `DocumentsContract` write through FileKit's `UriWrapper`, `takePersistableUriPermission` across an actual reboot, and the end-to-end download → transcode → SAF round trip — can only be validated on a device/emulator. Device testing is tracked separately in `docs/DEVICE_TESTING.md`.
