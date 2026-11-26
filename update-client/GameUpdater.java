package uk.playdrop.cherrytree_idletextrpg;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONObject;

public final class GameUpdater {
    private static final String TAG = "ArtemizUpdater";
    private static final String PACKAGE_NAME = "uk.playdrop.cherrytree_idletextrpg";
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/itzRealEzra/Test-Game/main/update.json";
    private static final long MAX_MANIFEST_BYTES = 64L * 1024L;
    private static final long MAX_XAPK_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_APK_BYTES = 180L * 1024L * 1024L;
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static boolean checking;
    private static boolean downloading;
    private static boolean lifecycleRegistered;
    private static UpdateInfo pendingPermissionUpdate;

    private GameUpdater() { }

    public static void checkForUpdates(final Activity activity, final boolean manual) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        synchronized (LOCK) {
            if (checking || downloading) {
                if (manual) showToast(activity, "An update check is already running.");
                else finishWelcomeUpdateCheck(activity);
                return;
            }
            checking = true;
        }
        if (manual) showToast(activity, "Checking for Artemiz updates…");
        EXECUTOR.execute(new UpdateCheckTask(activity, manual));
    }

    private static void finishWelcomeUpdateCheck(final Activity activity) {
        if (activity == null) return;
        MAIN.post(new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                try {
                    activity.getClass().getMethod("onUpdateCheckFinished").invoke(activity);
                } catch (NoSuchMethodException ignored) {
                    // Other activities can use the updater without a Welcome continue button.
                } catch (Exception error) {
                    Log.w(TAG, "Could not re-enable Continue after the update check.", error);
                }
            }
        });
    }

    private static UpdateInfo fetchLatestUpdate(Context context) throws Exception {
        HttpURLConnection connection = openHttps(MANIFEST_URL, 15000, 20000);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Update manifest returned HTTP " + status);
            }
            JSONObject manifest = new JSONObject(readLimited(
                    connection.getInputStream(), MAX_MANIFEST_BYTES));
            if (!PACKAGE_NAME.equals(manifest.optString("package_name"))) {
                throw new IOException("Update manifest package does not match Artemiz.");
            }
            long versionCode = manifest.optLong("version_code", -1L);
            String versionName = manifest.optString("version_name", "").trim();
            if (versionCode < 1L || versionName.length() == 0) {
                throw new IOException("Update manifest has no valid version.");
            }

            String abiKey = supportsAbi("arm64-v8a") ? "arm64" : "armv7";
            JSONObject artifact = manifest.optJSONObject(abiKey);
            if (artifact == null) throw new IOException("No update is published for " + abiKey + ".");
            String url = artifact.optString("url", "");
            String sha256 = artifact.optString("sha256", "").toLowerCase(Locale.US);
            if (!url.startsWith("https://") || !sha256.matches("[0-9a-f]{64}")) {
                throw new IOException("Update link or checksum is invalid.");
            }
            return new UpdateInfo(versionCode, versionName, url, sha256,
                    manifest.optString("release_notes", ""));
        } finally {
            connection.disconnect();
        }
    }

    private static boolean supportsAbi(String expected) {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (expected.equalsIgnoreCase(abi)) return true;
        }
        return false;
    }

    private static long installedVersionCode(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static String installedVersionName(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionName == null ? "current build" : info.versionName;
    }

    private static void showUpdatePrompt(final Activity activity, final UpdateInfo update) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        StringBuilder message = new StringBuilder();
        message.append("Artemiz ").append(update.versionName).append(" is available.");
        if (update.releaseNotes.length() > 0) {
            message.append("\n\n").append(update.releaseNotes);
        }
        message.append("\n\nAndroid will update the existing app and keep its local save. "
                + "The Android installer will ask you to confirm.");
        new AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage(message.toString())
                .setPositiveButton("Download & Install", (dialog, which) ->
                        confirmInstallPermission(activity, update))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void confirmInstallPermission(final Activity activity, final UpdateInfo update) {
        if (Build.VERSION.SDK_INT < 26
                || activity.getPackageManager().canRequestPackageInstalls()) {
            downloadAndInstall(activity, update);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Allow game updates")
                .setMessage("Android needs permission for Artemiz to request an update. "
                        + "You will still confirm each installation in Android.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    synchronized (LOCK) {
                        pendingPermissionUpdate = update;
                    }
                    registerLifecycleWatcher(activity);
                    try {
                        Intent settings = new Intent(
                                "android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                    } catch (Exception error) {
                        synchronized (LOCK) {
                            pendingPermissionUpdate = null;
                        }
                        showToast(activity, "Open Android settings and allow Artemiz to install updates.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void registerLifecycleWatcher(Activity activity) {
        synchronized (LOCK) {
            if (lifecycleRegistered) return;
            Application app = activity.getApplication();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, android.os.Bundle state) { }
                @Override public void onActivityStarted(Activity a) { }
                @Override public void onActivityPaused(Activity a) { }
                @Override public void onActivityStopped(Activity a) { }
                @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle state) { }
                @Override public void onActivityDestroyed(Activity a) { }
                @Override public void onActivityResumed(Activity resumed) {
                    if (!PACKAGE_NAME.equals(resumed.getPackageName())
                            || Build.VERSION.SDK_INT < 26
                            || !resumed.getPackageManager().canRequestPackageInstalls()) return;
                    final UpdateInfo pending;
                    synchronized (LOCK) {
                        pending = pendingPermissionUpdate;
                        pendingPermissionUpdate = null;
                    }
                    if (pending != null) downloadAndInstall(resumed, pending);
                }
            });
            lifecycleRegistered = true;
        }
    }

    private static void downloadAndInstall(final Activity activity, final UpdateInfo update) {
        synchronized (LOCK) {
            if (downloading) return;
            downloading = true;
        }
        final ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("Updating Artemiz");
        progress.setMessage("Downloading " + update.versionName + "…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();

        EXECUTOR.execute(new DownloadUpdateTask(activity, update, progress));
    }

    private static File downloadXapk(Context context, UpdateInfo update,
            final ProgressDialog progress) throws Exception {
        HttpURLConnection connection = openHttps(update.url, 20000, 30000);
        File output = new File(context.getCacheDir(),
                "artemiz-update-" + update.versionCode + ".xapk");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Download returned HTTP " + status);
            long expectedLength = connection.getContentLengthLong();
            if (expectedLength > MAX_XAPK_BYTES) throw new IOException("Update bundle is too large.");
            if (expectedLength > 0) {
                MAIN.post(() -> {
                    progress.setIndeterminate(false);
                    progress.setProgress(0);
                });
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = connection.getInputStream();
                    FileOutputStream file = new FileOutputStream(output, false)) {
                byte[] buffer = new byte[32768];
                long total = 0L;
                long lastProgress = 0L;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_XAPK_BYTES) throw new IOException("Update bundle is too large.");
                    digest.update(buffer, 0, count);
                    file.write(buffer, 0, count);
                    if (expectedLength > 0 && total - lastProgress > 512L * 1024L) {
                        final int percent = (int) Math.min(100L, total * 100L / expectedLength);
                        MAIN.post(() -> progress.setProgress(percent));
                        lastProgress = total;
                    }
                }
                file.getFD().sync();
            }
            String actual = hex(digest.digest());
            if (!actual.equals(update.sha256)) throw new IOException("Update checksum did not match.");
            return output;
        } catch (Exception error) {
            output.delete();
            throw error;
        } finally {
            connection.disconnect();
        }
    }

    private static void stageXapk(Context context, File xapk, UpdateInfo update) throws Exception {
        List<ZipEntry> apkEntries = new ArrayList<>();
        long totalApkBytes = 0L;
        boolean hasBase = false;
        try (ZipFile zip = new ZipFile(xapk)) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null) throw new IOException("XAPK is missing its manifest.");
            JSONObject xapkManifest = new JSONObject(readLimited(
                    zip.getInputStream(manifestEntry), MAX_MANIFEST_BYTES));
            if (!PACKAGE_NAME.equals(xapkManifest.optString("package_name"))
                    || xapkManifest.optLong("version_code", -1L) != update.versionCode) {
                throw new IOException("XAPK identity or version does not match the update.");
            }
            JSONArray splits = xapkManifest.optJSONArray("split_apks");
            if (splits == null || splits.length() < 2 || splits.length() > 8) {
                throw new IOException("XAPK split list is invalid.");
            }
            for (int i = 0; i < splits.length(); i++) {
                JSONObject split = splits.getJSONObject(i);
                String name = split.optString("file", "");
                if (!name.matches("[A-Za-z0-9_.-]+\\.apk")) {
                    throw new IOException("XAPK contains an invalid APK path.");
                }
                ZipEntry entry = zip.getEntry(name);
                if (entry == null || entry.isDirectory() || entry.getSize() <= 0L) {
                    throw new IOException("XAPK is missing " + name + ".");
                }
                totalApkBytes += entry.getSize();
                if (totalApkBytes > MAX_APK_BYTES) throw new IOException("APK contents are too large.");
                if ("base".equals(split.optString("id"))) hasBase = true;
                apkEntries.add(entry);
            }
            if (!hasBase) throw new IOException("XAPK is missing its base APK.");

            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(totalApkBytes);
            int sessionId = installer.createSession(params);
            boolean committed = false;
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                for (ZipEntry entry : apkEntries) {
                    try (InputStream input = zip.getInputStream(entry);
                            OutputStream output = session.openWrite(entry.getName(), 0L, entry.getSize())) {
                        byte[] buffer = new byte[32768];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        session.fsync(output);
                    }
                }
                Intent callback = new Intent(context, InstallStatusReceiver.class);
                callback.setAction(context.getPackageName() + ".ARTEMIZ_UPDATE_RESULT");
                callback.setData(Uri.parse("artemiz-update://session/" + sessionId));
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                PendingIntent result = PendingIntent.getBroadcast(context, sessionId, callback, flags);
                IntentSender statusReceiver = result.getIntentSender();
                session.commit(statusReceiver);
                committed = true;
            } finally {
                if (!committed) installer.abandonSession(sessionId);
            }
        }
    }

    private static HttpURLConnection openHttps(String address, int connectTimeout, int readTimeout)
            throws IOException {
        if (address == null || !address.startsWith("https://")) {
            throw new IOException("Update service must use HTTPS.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Artemiz-Game-Updater");
        connection.setRequestProperty("Accept", "application/json, application/octet-stream");
        return connection;
    }

    private static String readLimited(InputStream input, long limit) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if ((long) out.size() + count > limit) throw new IOException("Response is too large.");
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }

    private static String safeMessage(Exception error) {
        String value = error.getMessage();
        if (value == null || value.trim().length() == 0) return "check your connection and try again.";
        return value;
    }

    private static void showToast(Context context, String message) {
        Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private static final class UpdateCheckTask implements Runnable {
        private final Activity activity;
        private final boolean manual;

        UpdateCheckTask(Activity activity, boolean manual) {
            this.activity = activity;
            this.manual = manual;
        }

        @Override public void run() {
            try {
                UpdateInfo latest = fetchLatestUpdate(activity);
                long currentCode = installedVersionCode(activity);
                String currentName = installedVersionName(activity);
                MAIN.post(new UpdateCheckResult(activity, manual, latest, currentCode, currentName));
            } catch (Exception error) {
                Log.w(TAG, "Could not check the update manifest.", error);
                MAIN.post(new UpdateCheckFailure(activity, manual));
            }
        }
    }

    private static final class UpdateCheckResult implements Runnable {
        private final Activity activity;
        private final boolean manual;
        private final UpdateInfo latest;
        private final long currentCode;
        private final String currentName;

        UpdateCheckResult(Activity activity, boolean manual, UpdateInfo latest,
                long currentCode, String currentName) {
            this.activity = activity;
            this.manual = manual;
            this.latest = latest;
            this.currentCode = currentCode;
            this.currentName = currentName;
        }

        @Override public void run() {
            synchronized (LOCK) { checking = false; }
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (latest.versionCode <= currentCode) {
                if (manual) {
                    new AlertDialog.Builder(activity)
                            .setTitle("You're up to date")
                            .setMessage("Artemiz " + currentName
                                    + " is the latest available version.")
                            .setPositiveButton("OK", null)
                            .show();
                }
                finishWelcomeUpdateCheck(activity);
                return;
            }
            showUpdatePrompt(activity, latest);
            finishWelcomeUpdateCheck(activity);
        }
    }

    private static final class UpdateCheckFailure implements Runnable {
        private final Activity activity;
        private final boolean manual;

        UpdateCheckFailure(Activity activity, boolean manual) {
            this.activity = activity;
            this.manual = manual;
        }

        @Override public void run() {
            synchronized (LOCK) { checking = false; }
            if (manual && !activity.isFinishing() && !activity.isDestroyed()) {
                showToast(activity, "Couldn't check updates. Connect to the internet and try again.");
            }
            finishWelcomeUpdateCheck(activity);
        }
    }

    private static final class DownloadUpdateTask implements Runnable {
        private final Activity activity;
        private final UpdateInfo update;
        private final ProgressDialog progress;

        DownloadUpdateTask(Activity activity, UpdateInfo update, ProgressDialog progress) {
            this.activity = activity;
            this.update = update;
            this.progress = progress;
        }

        @Override public void run() {
            File download = null;
            try {
                download = downloadXapk(activity, update, progress);
                stageXapk(activity, download, update);
                download.delete();
                MAIN.post(new InstallLaunchMessage(activity, progress));
            } catch (Exception error) {
                Log.e(TAG, "Could not download or stage the update.", error);
                if (download != null) download.delete();
                MAIN.post(new InstallFailureMessage(activity, progress, error));
            } finally {
                synchronized (LOCK) { downloading = false; }
            }
        }
    }

    private static final class InstallLaunchMessage implements Runnable {
        private final Activity activity;
        private final ProgressDialog progress;

        InstallLaunchMessage(Activity activity, ProgressDialog progress) {
            this.activity = activity;
            this.progress = progress;
        }

        @Override public void run() {
            if (progress.isShowing()) progress.dismiss();
            showToast(activity, "Opening Android's installer…");
        }
    }

    private static final class InstallFailureMessage implements Runnable {
        private final Activity activity;
        private final ProgressDialog progress;
        private final Exception error;

        InstallFailureMessage(Activity activity, ProgressDialog progress, Exception error) {
            this.activity = activity;
            this.progress = progress;
            this.error = error;
        }

        @Override public void run() {
            if (progress.isShowing()) progress.dismiss();
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                showToast(activity, "Update failed: " + safeMessage(error));
            }
        }
    }

    public static final class InstallStatusReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = (Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                } else {
                    showToast(context, "Open Android to confirm the game update.");
                }
            } else if (status == PackageInstaller.STATUS_SUCCESS) {
                showToast(context, "Artemiz updated. Reopen the game if needed.");
            } else {
                String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                showToast(context, "Android could not install the update."
                        + (detail == null ? "" : " " + detail));
            }
        }
    }

    private static final class UpdateInfo {
        final long versionCode;
        final String versionName;
        final String url;
        final String sha256;
        final String releaseNotes;

        UpdateInfo(long versionCode, String versionName, String url, String sha256,
                String releaseNotes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.url = url;
            this.sha256 = sha256;
            this.releaseNotes = releaseNotes;
        }
    }
}
