# WinLog Official Android collector

Install this app on each company Android phone. It reads new calls after the last successful sync and sends them to the WinLog Official dashboard every 15 minutes and shortly after a call ends.

## Build the APK in GitHub

1. Open **Settings → Secrets and variables → Actions**.
2. Add a repository secret named `DEVICE_API_KEY`. Its value must exactly match the private `DEVICE_INGEST_SECRET` stored in WinLog Official.
3. Open **Actions → Build WinLog Official APK → Run workflow**.
4. When the build succeeds, open the run and download `winlog-official-apk`. GitHub packages artifacts as a ZIP; extract `app-debug.apk` before copying it to a phone.

Never put the device key in Kotlin source, a commit, or an issue.

## Set up each phone

1. Install the APK and open **WinLog Official**.
2. Enter the employee ID used in the dashboard, such as `WIN2604`.
3. Leave the receiver address unchanged unless your WinLog domain changes.
4. Grant call-log and phone-state permissions.
5. Allow unrestricted background battery use so Android does not stop syncing.
6. Tap **Bind Device & Sync Now**.

The first sync sends calls currently visible in Android's call log. Later syncs send only newer entries. The dashboard deduplicates repeated batches by device, call time, and normalized phone number.

## Receiver

The default receiver is:

`https://winlogofficial.winstonebd.com/api/public/ingest-call-logs`

The receiver requires the `x-device-api-key` header. The build injects this value from `DEVICE_API_KEY`; it is not stored in this repository.
