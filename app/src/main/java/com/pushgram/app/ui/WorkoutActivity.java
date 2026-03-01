package com.pushgram.app.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AnimationUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.pushgram.app.R;
import com.pushgram.app.camera.CameraProcessor;
import com.pushgram.app.camera.PushUpAnalyzer;
import com.pushgram.app.databinding.ActivityWorkoutBinding;
import com.pushgram.app.model.CreditManager;

public class WorkoutActivity extends AppCompatActivity
        implements PushUpAnalyzer.PushUpListener {

    private static final int REQUEST_CAMERA = 100;

    private ActivityWorkoutBinding binding;
    private CameraProcessor cameraProcessor;
    private CreditManager creditManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int sessionReps = 0;
    private int sessionCredits = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWorkoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        creditManager = CreditManager.getInstance(this);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvSessionCredits.setText("0");
        binding.tvSessionReps.setText("0");
        binding.tvTotalCredits.setText(String.valueOf(creditManager.getCredits()));

        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        cameraProcessor = new CameraProcessor(this, this, binding.cameraPreview, this);
        cameraProcessor.start();
        binding.tvStatus.setText("Position yourself for push-ups");
    }

    @Override
    public void onRepCompleted(boolean isPerfectForm) {
        if (isPerfectForm) {
            sessionReps++;
            sessionCredits++;
            creditManager.addCredit();

            mainHandler.post(() -> {
                binding.tvSessionReps.setText(String.valueOf(sessionReps));
                binding.tvSessionCredits.setText(String.valueOf(sessionCredits));
                binding.tvTotalCredits.setText(String.valueOf(creditManager.getCredits()));

                // Pulse animation on rep counter
                binding.tvSessionReps.startAnimation(
                        AnimationUtils.loadAnimation(this, R.anim.pulse_scale));

                showCreditToast();
            });
        } else {
            mainHandler.post(() ->
                    binding.tvStatus.setText("Rep not counted — fix your form!"));
        }
    }

    @Override
    public void onPhaseChanged(PushUpAnalyzer.Phase phase, double elbowAngle) {
        mainHandler.post(() -> {
            String angleStr = String.format("%.0f°", elbowAngle);
            switch (phase) {
                case UP:
                    binding.tvPhase.setText("▲ UP " + angleStr);
                    binding.tvPhase.setTextColor(getResources().getColor(R.color.success));
                    break;
                case DOWN:
                    binding.tvPhase.setText("▼ DOWN " + angleStr);
                    binding.tvPhase.setTextColor(getResources().getColor(R.color.accent_secondary));
                    break;
                default:
                    binding.tvPhase.setText("● " + angleStr);
                    binding.tvPhase.setTextColor(getResources().getColor(R.color.accent_primary));
            }
        });
    }

    @Override
    public void onFormFeedback(String feedback) {
        mainHandler.post(() -> binding.tvStatus.setText(feedback));
    }

    private void showCreditToast() {
        binding.tvCreditToast.setVisibility(View.VISIBLE);
        binding.tvCreditToast.setAlpha(1f);
        binding.tvCreditToast.setScaleX(0.8f);
        binding.tvCreditToast.setScaleY(0.8f);
        binding.tvCreditToast.animate()
                .scaleX(1f).scaleY(1f).setDuration(200)
                .withEndAction(() ->
                        binding.tvCreditToast.animate()
                                .alpha(0f)
                                .setStartDelay(1000)
                                .setDuration(500)
                                .withEndAction(() ->
                                        binding.tvCreditToast.setVisibility(View.GONE))
                                .start())
                .start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA &&
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProcessor != null) cameraProcessor.stop();
    }
}
