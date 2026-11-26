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
import android.content.pm.ApplicationInfo;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
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
    private static final long MAX_PATCH_BYTES = 120L * 1024L * 1024L;
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

            long sourceVersion = installedVersionCode(context);
            String releaseNotes = manifest.optString("release_notes", "");
            if (versionCode <= sourceVersion) {
                return new UpdateInfo(sourceVersion, versionCode, versionName, "", "", releaseNotes);
            }

            String abiKey = supportsAbi("arm64-v8a") ? "arm64" : "armv7";
            JSONObject artifact = manifest.optJSONObject(abiKey);
            if (artifact == null) throw new IOException("No update is published for " + abiKey + ".");
            JSONObject deltas = artifact.optJSONObject("deltas");
            JSONObject delta = deltas == null ? null : deltas.optJSONObject(Long.toString(sourceVersion));
            if (delta == null || delta.optLong("from_version_code", -1L) != sourceVersion
                    || delta.optLong("to_version_code", -1L) != versionCode) {
                throw new IOException("No patch is published for this installed version.");
            }
            String url = delta.optString("url", "");
            String sha256 = delta.optString("sha256", "").toLowerCase(Locale.US);
            if (!url.startsWith("https://") || !sha256.matches("[0-9a-f]{64}")) {
                throw new IOException("Patch link or checksum is invalid.");
            }
            return new UpdateInfo(sourceVersion, versionCode, versionName, url, sha256, releaseNotes);
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

    private static void confirmInstallPermission(final Activity activity, final UpdateInfo update) {
        if (Build.VERSION.SDK_INT < 26
                || activity.getPackageManager().canRequestPackageInstalls()) {
            downloadAndInstall(activity, update);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Allow game updates")
                .setMessage("Android needs permission for Artemiz to request updates. "
                        + "Artemiz will apply the patch automatically; Android may still ask "
                        + "you to confirm the installation.")
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
                        showToast(activity, "Open Android settings and allow Artemiz to request updates.");
                        finishWelcomeUpdateCheck(activity);
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> finishWelcomeUpdateCheck(activity))
                .setOnCancelListener(dialog -> finishWelcomeUpdateCheck(activity))
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
                    if (!PACKAGE_NAME.equals(resumed.getPackageName()) || Build.VERSION.SDK_INT < 26) return;
                    final UpdateInfo pending;
                    synchronized (LOCK) {
                        pending = pendingPermissionUpdate;
                        if (pending != null && resumed.getPackageManager().canRequestPackageInstalls()) {
                            pendingPermissionUpdate = null;
                        } else if (pending != null) {
                            pendingPermissionUpdate = null;
                        }
                    }
                    if (pending == null) return;
                    if (resumed.getPackageManager().canRequestPackageInstalls()) {
                        downloadAndInstall(resumed, pending);
                    } else {
                        showToast(resumed, "Install permission was not enabled. The patch will retry next launch.");
                        finishWelcomeUpdateCheck(resumed);
                    }
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
        progress.setMessage("Downloading and applying patch " + update.versionName + "…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();

        EXECUTOR.execute(new DownloadUpdateTask(activity, update, progress));
    }

    private static File downloadPatch(Context context, UpdateInfo update,
            final ProgressDialog progress) throws Exception {
        HttpURLConnection connection = openHttps(update.url, 20000, 30000);
        File output = new File(context.getCacheDir(),
                "artemiz-update-" + update.fromVersionCode + "-to-" + update.versionCode + ".patch.zip");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Patch download returned HTTP " + status);
            long expectedLength = connection.getContentLengthLong();
            if (expectedLength > MAX_PATCH_BYTES) throw new IOException("Update patch is too large.");
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
                    if (total > MAX_PATCH_BYTES) throw new IOException("Update patch is too large.");
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
            if (!actual.equals(update.sha256)) throw new IOException("Patch checksum did not match.");
            return output;
        } catch (Exception error) {
            output.delete();
            throw error;
        } finally {
            connection.disconnect();
        }
    }

    private static void applyAndStagePatch(Context context, File patchBundle, UpdateInfo update)
            throws Exception {
        if (installedVersionCode(context) != update.fromVersionCode) {
            throw new IOException("Installed version changed before patching.");
        }

        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo application = packageManager.getApplicationInfo(PACKAGE_NAME, 0);
        PackageInfo packageInfo = packageManager.getPackageInfo(PACKAGE_NAME, 0);
        List<TargetApk> targets = new ArrayList<>();
        List<File> temporaryFiles = new ArrayList<>();
        long totalApkBytes = 0L;
        try (ZipFile zip = new ZipFile(patchBundle)) {
            ZipEntry manifestEntry = zip.getEntry("patch.json");
            if (manifestEntry == null || manifestEntry.isDirectory()) {
                throw new IOException("Patch is missing patch.json.");
            }
            JSONObject patchManifest = new JSONObject(readLimited(
                    zip.getInputStream(manifestEntry), MAX_MANIFEST_BYTES));
            if (!"artemiz-bsdiff-v1".equals(patchManifest.optString("format"))
                    || !PACKAGE_NAME.equals(patchManifest.optString("package_name"))
                    || patchManifest.optLong("from_version_code", -1L) != update.fromVersionCode
                    || patchManifest.optLong("to_version_code", -1L) != update.versionCode) {
                throw new IOException("Patch package, format, or version is incorrect.");
            }

            List<InstalledApk> installedApks = installedApks(application, packageInfo);
            JSONArray parts = patchManifest.optJSONArray("split_apks");
            if (parts == null || parts.length() != installedApks.size()
                    || parts.length() < 2 || parts.length() > 8) {
                throw new IOException("Patch split list does not match this installation.");
            }

            java.util.HashSet<String> seenIds = new java.util.HashSet<>();
            java.util.HashSet<String> seenFiles = new java.util.HashSet<>();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                String id = part.optString("id", "");
                String name = part.optString("file", "");
                String patchName = part.optString("patch", "");
                String oldSha256 = part.optString("old_sha256", "").toLowerCase(Locale.US);
                String newSha256 = part.optString("new_sha256", "").toLowerCase(Locale.US);
                if (!id.matches("[A-Za-z0-9_.-]+")
                        || !name.matches("[A-Za-z0-9_.-]+\\.apk")
                        || !patchName.matches("patches/[A-Za-z0-9_.-]+\\.bsdiff")
                        || !oldSha256.matches("[0-9a-f]{64}")
                        || !newSha256.matches("[0-9a-f]{64}")
                        || !seenIds.add(id) || !seenFiles.add(name)) {
                    throw new IOException("Patch contains invalid split metadata.");
                }

                File oldApk = findInstalledApk(installedApks, id);
                if (oldApk == null || !oldApk.isFile()
                        || !oldSha256.equals(sha256File(oldApk))) {
                    throw new IOException("Installed " + id + " APK does not match this patch.");
                }
                ZipEntry patchEntry = zip.getEntry(patchName);
                if (patchEntry == null || patchEntry.isDirectory()
                        || patchEntry.getSize() <= 0L || patchEntry.getSize() > MAX_PATCH_BYTES) {
                    throw new IOException("Patch data is missing for " + id + ".");
                }

                File patchPart = new File(context.getCacheDir(),
                        "artemiz-part-" + update.versionCode + "-" + id + ".bsdiff");
                File newApk = new File(context.getCacheDir(),
                        "artemiz-rebuilt-" + update.versionCode + "-" + id + ".apk");
                temporaryFiles.add(patchPart);
                temporaryFiles.add(newApk);
                try (InputStream input = zip.getInputStream(patchEntry);
                        FileOutputStream output = new FileOutputStream(patchPart, false)) {
                    copyLimited(input, output, MAX_PATCH_BYTES);
                    output.getFD().sync();
                }
                BsdiffPatch.apply(oldApk, patchPart, newApk);
                String actualSha256 = sha256File(newApk);
                if (!newSha256.equals(actualSha256)) {
                    throw new IOException("Rebuilt " + id + " APK failed its hash check.");
                }
                totalApkBytes += newApk.length();
                if (totalApkBytes > MAX_APK_BYTES) throw new IOException("Rebuilt APK set is too large.");
                targets.add(new TargetApk(name, newApk));
            }

            if (!seenIds.contains("base")) throw new IOException("Patch is missing the base APK.");
            PackageInstaller installer = packageManager.getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(PACKAGE_NAME);
            params.setSize(totalApkBytes);
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
            int sessionId = installer.createSession(params);
            boolean committed = false;
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                for (TargetApk target : targets) {
                    try (FileInputStream input = new FileInputStream(target.file);
                            OutputStream output = session.openWrite(target.name, 0L, target.file.length())) {
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
                session.commit(result.getIntentSender());
                committed = true;
            } finally {
                if (!committed) installer.abandonSession(sessionId);
            }
        } finally {
            for (File file : temporaryFiles) file.delete();
        }
    }

    private static List<InstalledApk> installedApks(ApplicationInfo application,
            PackageInfo packageInfo) throws IOException {
        List<InstalledApk> result = new ArrayList<>();
        if (application.sourceDir == null) throw new IOException("Installed base APK was not found.");
        result.add(new InstalledApk("base", new File(application.sourceDir)));
        String[] names = packageInfo.splitNames;
        String[] paths = application.splitSourceDirs;
        if (names == null) names = new String[0];
        if (paths == null) paths = new String[0];
        if (names.length != paths.length) throw new IOException("Installed splits are incomplete.");
        for (int i = 0; i < names.length; i++) {
            result.add(new InstalledApk(names[i], new File(paths[i])));
        }
        return result;
    }

    private static File findInstalledApk(List<InstalledApk> installed, String splitId) {
        for (InstalledApk apk : installed) {
            if (splitId.equals(apk.id)) return apk.file;
        }
        return null;
    }

    private static String sha256File(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static void copyLimited(InputStream input, OutputStream output, long limit)
            throws IOException {
        byte[] buffer = new byte[32768];
        long total = 0L;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IOException("Patch component is too large.");
            output.write(buffer, 0, count);
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
            confirmInstallPermission(activity, latest);
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
                download = downloadPatch(activity, update, progress);
                applyAndStagePatch(activity, download, update);
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
            showToast(activity, "Patch applied. Finishing the update…");
            finishWelcomeUpdateCheck(activity);
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
            finishWelcomeUpdateCheck(activity);
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
        final long fromVersionCode;
        final long versionCode;
        final String versionName;
        final String url;
        final String sha256;
        final String releaseNotes;

        UpdateInfo(long fromVersionCode, long versionCode, String versionName, String url, String sha256,
                String releaseNotes) {
            this.fromVersionCode = fromVersionCode;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.url = url;
            this.sha256 = sha256;
            this.releaseNotes = releaseNotes;
        }
    }

    private static final class InstalledApk {
        final String id;
        final File file;

        InstalledApk(String id, File file) {
            this.id = id;
            this.file = file;
        }
    }

    private static final class TargetApk {
        final String name;
        final File file;

        TargetApk(String name, File file) {
            this.name = name;
            this.file = file;
        }
    }
}
