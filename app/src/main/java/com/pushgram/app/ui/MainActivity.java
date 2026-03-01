package com.pushgram.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pushgram.app.R;
import com.pushgram.app.databinding.ActivityMainBinding;
import com.pushgram.app.model.CreditManager;
import com.pushgram.app.service.InstagramMonitorService;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private CreditManager creditManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        creditManager = CreditManager.getInstance(this);
        setupUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
        updateAccessibilityStatus();
    }

    private void setupUI() {
        binding.btnStartWorkout.setOnClickListener(v ->
                startActivity(new Intent(this, WorkoutActivity.class)));

        binding.btnMusic.setOnClickListener(v ->
                startActivity(new Intent(this, com.pushgram.app.music.ui.MusicActivity.class)));

        binding.btnEnableAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Find 'PushGram' and toggle it ON", Toast.LENGTH_LONG).show();
        });
    }

    private void refreshStats() {
        binding.tvCredits.setText(String.valueOf(creditManager.getCredits()));
        binding.tvTotalPushups.setText(String.valueOf(creditManager.getTotalPushups()));
    }

    private void updateAccessibilityStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            binding.tvAccessibilityStatus.setText("● ACTIVE");
            binding.tvAccessibilityStatus.setTextColor(
                    getResources().getColor(R.color.success));
            binding.btnEnableAccessibility.setVisibility(View.GONE);
        } else {
            binding.tvAccessibilityStatus.setText("● OFFLINE");
            binding.tvAccessibilityStatus.setTextColor(
                    getResources().getColor(R.color.text_muted));
            binding.btnEnableAccessibility.setVisibility(View.VISIBLE);
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" +
                InstagramMonitorService.class.getCanonicalName();
        try {
            int enabled = Settings.Secure.getInt(
                    getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled == 0) return false;
            String services = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services == null) return false;
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(services);
            while (splitter.hasNext()) {
                if (splitter.next().equalsIgnoreCase(serviceName)) return true;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }
}
