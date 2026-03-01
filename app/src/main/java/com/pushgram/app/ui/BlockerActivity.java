package com.pushgram.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.pushgram.app.databinding.ActivityBlockerBinding;
import com.pushgram.app.model.CreditManager;

public class BlockerActivity extends AppCompatActivity {

    private ActivityBlockerBinding binding;
    private CreditManager creditManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBlockerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        creditManager = CreditManager.getInstance(this);

        binding.btnDoWorkout.setOnClickListener(v ->
                startActivity(new Intent(this, WorkoutActivity.class)));

        binding.btnGoBack.setOnClickListener(v -> {
            moveTaskToBack(true);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        int credits = creditManager.getCredits();
        binding.tvCreditsLeft.setText(String.valueOf(credits));

        if (credits > 0) {
            binding.tvMessage.setText("You have " + credits + " reel credit" +
                    (credits == 1 ? "" : "s") + ". Enjoy!");
            binding.btnDoWorkout.setVisibility(View.GONE);
        } else {
            binding.tvMessage.setText("No credits left. Do push-ups to unlock your next reel.");
            binding.btnDoWorkout.setVisibility(View.VISIBLE);
        }
    }
}
