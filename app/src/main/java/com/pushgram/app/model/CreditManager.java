package com.pushgram.app.model;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages reel credits earned via push-ups.
 * 1 perfect push-up = 1 reel credit.
 */
public class CreditManager {

    private static final String PREFS_NAME = "pushgram_credits";
    private static final String KEY_CREDITS = "reel_credits";
    private static final String KEY_TOTAL_PUSHUPS = "total_pushups";

    private static CreditManager instance;
    private final SharedPreferences prefs;

    private CreditManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized CreditManager getInstance(Context context) {
        if (instance == null) {
            instance = new CreditManager(context);
        }
        return instance;
    }

    public int getCredits() {
        return prefs.getInt(KEY_CREDITS, 0);
    }

    public void addCredit() {
        int current = getCredits();
        prefs.edit().putInt(KEY_CREDITS, current + 1)
                .putInt(KEY_TOTAL_PUSHUPS, getTotalPushups() + 1)
                .apply();
    }

    /**
     * Spend one credit to watch a reel.
     * @return true if credit was available and spent
     */
    public boolean spendCredit() {
        int current = getCredits();
        if (current > 0) {
            prefs.edit().putInt(KEY_CREDITS, current - 1).apply();
            return true;
        }
        return false;
    }

    public int getTotalPushups() {
        return prefs.getInt(KEY_TOTAL_PUSHUPS, 0);
    }

    public boolean hasCredits() {
        return getCredits() > 0;
    }
}
