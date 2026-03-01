package com.pushgram.app.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraProcessor {

    private static final String TAG = "CameraProcessor";

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final PushUpAnalyzer.PushUpListener listener;

    private PoseDetector poseDetector;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    public CameraProcessor(Context context, LifecycleOwner owner,
                            PreviewView previewView, PushUpAnalyzer.PushUpListener listener) {
        this.context = context;
        this.lifecycleOwner = owner;
        this.previewView = previewView;
        this.listener = listener;
    }

    public void start() {
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Use STREAM_MODE for real-time detection
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCamera();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera init failed", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCamera() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        PushUpAnalyzer pushUpAnalyzer = new PushUpAnalyzer(listener);

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            processFrame(imageProxy, pushUpAnalyzer);
        });

        // Use BACK camera for push-up detection (phone placed on floor facing user)
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Camera bind failed", e);
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void processFrame(ImageProxy imageProxy, PushUpAnalyzer analyzer) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        poseDetector.process(image)
                .addOnSuccessListener(analyzer::analyze)
                .addOnFailureListener(e -> Log.w(TAG, "Pose detection failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    public void stop() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (poseDetector != null) {
            try { poseDetector.close(); } catch (Exception e) { Log.w(TAG, e); }
        }
    }
}
