package uk.playdrop.cherrytree_idletextrpg;

import android.animation.Animator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Small, animated update panel drawn over the game's own activity. */
final class GameUpdateOverlay {
    private static final int ACCENT = Color.rgb(224, 183, 78);
    private static final int TEXT = Color.rgb(245, 241, 231);
    private static final int MUTED = Color.rgb(183, 175, 158);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Activity activity;
    private final FrameLayout host;
    private final FrameLayout layer;
    private final LinearLayout card;
    private final TextView title;
    private final TextView message;
    private final ProgressBar progress;
    private final LinearLayout actions;
    private boolean removed;
    private int hideGeneration;

    private GameUpdateOverlay(Activity activity, FrameLayout host) {
        this.activity = activity;
        this.host = host;
        layer = new FrameLayout(activity);
        layer.setTag("artemiz_update_overlay");
        layer.setClickable(false);

        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        card.setBackground(background(Color.argb(249, 28, 30, 37),
                withAlpha(ACCENT, 190), dp(12)));
        card.setElevation(dp(12));
        card.setAlpha(0f);
        card.setTranslationY(-dp(22));

        View accentLine = new View(activity);
        accentLine.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.TRANSPARENT, ACCENT, Color.TRANSPARENT }));
        card.addView(accentLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));

        TextView brand = new TextView(activity);
        brand.setText("ARTEMIZ  /  GAME UPDATE");
        brand.setTextColor(ACCENT);
        brand.setTextSize(10f);
        brand.setLetterSpacing(0.1f);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brandParams.topMargin = dp(7);
        card.addView(brand, brandParams);

        title = new TextView(activity);
        title.setTextColor(TEXT);
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(3);
        card.addView(title, titleParams);

        message = new TextView(activity);
        message.setTextColor(MUTED);
        message.setTextSize(13f);
        message.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(3);
        card.addView(message, messageParams);

        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(true);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(90, 224, 183, 78)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
        progressParams.topMargin = dp(12);
        card.addView(progress, progressParams);

        actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setVisibility(View.GONE);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(12);
        card.addView(actions, actionsParams);

        FrameLayout.LayoutParams layerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        host.addView(layer, layerParams);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cardParams.leftMargin = dp(12);
        cardParams.rightMargin = dp(12);
        cardParams.topMargin = dp(12);
        layer.addView(card, cardParams);
        card.animate().alpha(1f).translationY(0f).setDuration(260L).start();
    }

    static GameUpdateOverlay show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout)) return null;
        FrameLayout host = (FrameLayout) root;
        View previous = host.findViewWithTag("artemiz_update_overlay");
        if (previous != null && previous.getParent() == host) host.removeView(previous);
        return new GameUpdateOverlay(activity, host);
    }

    void setState(final String heading, final String detail, final int percent) {
        runOnMain(new StateTask(this, heading, detail, percent));
    }

    void showPermission(final String detail, final Runnable openSettings,
                        final Runnable cancel) {
        runOnMain(new PermissionTask(this, detail, openSettings, cancel));
    }

    void showTransient(String heading, String detail, long durationMs) {
        runOnMain(new TransientTask(this, heading, detail, durationMs));
    }

    void dismiss() {
        runOnMain(new DismissTask(this));
    }

    void dismissImmediately() {
        runOnMain(new RemoveLayerTask(this));
    }

    private TextView actionButton(String label, boolean primary, final Runnable action) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextSize(11f);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.rgb(28, 27, 23) : TEXT);
        button.setBackground(background(primary ? ACCENT : Color.argb(90, 255, 255, 255),
                primary ? ACCENT : withAlpha(ACCENT, 100), dp(8)));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private GradientDrawable background(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void removeLayer() {
        removed = true;
        if (layer.getParent() == host) host.removeView(layer);
    }

    private void setStateOnMain(String heading, String detail, int percent) {
        if (removed) return;
        hideGeneration++;
        actions.removeAllViews();
        actions.setVisibility(View.GONE);
        layer.setBackgroundColor(Color.TRANSPARENT);
        layer.setClickable(false);
        card.setClickable(false);
        title.setText(heading == null ? "Updating Artemiz" : heading);
        message.setText(detail == null ? "" : detail);
        if (percent == -2) {
            progress.setVisibility(View.GONE);
        } else {
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(percent < 0);
            if (percent >= 0) progress.setProgress(Math.max(0, Math.min(100, percent)));
        }
    }

    private void showPermissionOnMain(String detail, Runnable openSettings, Runnable cancel) {
        if (removed) return;
        hideGeneration++;
        layer.setBackgroundColor(Color.argb(150, 0, 0, 0));
        layer.setClickable(true);
        layer.setOnClickListener(v -> cancel.run());
        card.setClickable(true);
        card.setOnClickListener(v -> { });
        title.setText("Allow game updates");
        message.setText(detail);
        progress.setVisibility(View.GONE);
        actions.removeAllViews();
        actions.setVisibility(View.VISIBLE);
        actions.addView(actionButton("NOT NOW", false, cancel),
                new LinearLayout.LayoutParams(0, dp(42), 1f));
        View spacer = new View(activity);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(8), 1));
        actions.addView(actionButton("OPEN SETTINGS", true, openSettings),
                new LinearLayout.LayoutParams(0, dp(42), 1f));
        FrameLayout.LayoutParams cardParams = (FrameLayout.LayoutParams) card.getLayoutParams();
        cardParams.gravity = Gravity.CENTER;
        cardParams.topMargin = 0;
        cardParams.leftMargin = dp(20);
        cardParams.rightMargin = dp(20);
        card.setLayoutParams(cardParams);
        card.setTranslationY(dp(10));
        card.setAlpha(0f);
        card.animate().alpha(1f).translationY(0f).setDuration(220L).start();
    }

    private static final class StateTask implements Runnable {
        private final GameUpdateOverlay overlay;
        private final String heading;
        private final String detail;
        private final int percent;

        StateTask(GameUpdateOverlay overlay, String heading, String detail, int percent) {
            this.overlay = overlay;
            this.heading = heading;
            this.detail = detail;
            this.percent = percent;
        }

        @Override public void run() { overlay.setStateOnMain(heading, detail, percent); }
    }

    private static final class PermissionTask implements Runnable {
        private final GameUpdateOverlay overlay;
        private final String detail;
        private final Runnable openSettings;
        private final Runnable cancel;

        PermissionTask(GameUpdateOverlay overlay, String detail, Runnable openSettings,
                       Runnable cancel) {
            this.overlay = overlay;
            this.detail = detail;
            this.openSettings = openSettings;
            this.cancel = cancel;
        }

        @Override public void run() {
            overlay.showPermissionOnMain(detail, openSettings, cancel);
        }
    }

    private static final class TransientTask implements Runnable {
        private final GameUpdateOverlay overlay;
        private final String heading;
        private final String detail;
        private final long durationMs;

        TransientTask(GameUpdateOverlay overlay, String heading, String detail, long durationMs) {
            this.overlay = overlay;
            this.heading = heading;
            this.detail = detail;
            this.durationMs = durationMs;
        }

        @Override public void run() {
            overlay.setStateOnMain(heading, detail, -2);
            int generation = overlay.hideGeneration;
            MAIN.postDelayed(new AutoDismissTask(overlay, generation), Math.max(700L, durationMs));
        }
    }

    private static final class AutoDismissTask implements Runnable {
        private final GameUpdateOverlay overlay;
        private final int generation;

        AutoDismissTask(GameUpdateOverlay overlay, int generation) {
            this.overlay = overlay;
            this.generation = generation;
        }

        @Override public void run() {
            if (overlay.removed || generation != overlay.hideGeneration) return;
            overlay.dismiss();
        }
    }

    private static final class DismissTask implements Runnable {
        private final GameUpdateOverlay overlay;

        DismissTask(GameUpdateOverlay overlay) { this.overlay = overlay; }

        @Override public void run() {
            if (overlay.removed) return;
            overlay.removed = true;
            overlay.card.animate().alpha(0f).translationY(-overlay.dp(14)).setDuration(300L).start();
            MAIN.postDelayed(new RemoveLayerTask(overlay), 320L);
        }
    }

    private static final class RemoveLayerTask implements Runnable {
        private final GameUpdateOverlay overlay;

        RemoveLayerTask(GameUpdateOverlay overlay) { this.overlay = overlay; }

        @Override public void run() { overlay.removeLayer(); }
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else MAIN.post(action);
    }
}
