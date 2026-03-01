package com.pushgram.app.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.pushgram.app.model.CreditManager;
import com.pushgram.app.ui.BlockerActivity;

/**
 * Monitors Instagram navigation and deducts a credit each time
 * the user enters the Reels section.
 */
public class InstagramMonitorService extends AccessibilityService {

    private static final String TAG = "InstagramMonitor";
    private static final String INSTAGRAM_PKG = "com.instagram.android";

    // Known Reels activity/fragment class name fragments
    private static final String REELS_INDICATOR = "reels";
    private static final String CLIPS_INDICATOR  = "clips";

    private boolean wasInReels = false;
    private long reelStartTime = 0;
    private static final long MIN_REEL_VIEW_MS = 3000; // min 3s counts as "watching a reel"

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null || !pkg.toString().equals(INSTAGRAM_PKG)) return;

        int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            CharSequence className = event.getClassName();
            if (className == null) return;

            String classLower = className.toString().toLowerCase();
            boolean inReels = classLower.contains(REELS_INDICATOR) ||
                    classLower.contains(CLIPS_INDICATOR);

            // Detect entering Reels
            if (inReels && !wasInReels) {
                Log.d(TAG, "User entered Reels");
                handleReelsEntry();
                wasInReels = true;
                reelStartTime = System.currentTimeMillis();
            } else if (!inReels && wasInReels) {
                wasInReels = false;
                long duration = System.currentTimeMillis() - reelStartTime;
                Log.d(TAG, "Left Reels after " + duration + "ms");
            }
        }
    }

    private void handleReelsEntry() {
        CreditManager credits = CreditManager.getInstance(this);

        if (!credits.hasCredits()) {
            Log.d(TAG, "No credits! Showing blocker.");
            showBlocker();
        } else {
            // Spend 1 credit
            credits.spendCredit();
            Log.d(TAG, "Credit spent. Remaining: " + credits.getCredits());
            // Let user watch — no interruption needed
        }
    }

    private void showBlocker() {
        Intent intent = new Intent(this, BlockerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "PushGram accessibility service connected");
    }
}
