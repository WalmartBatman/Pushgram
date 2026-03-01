package com.pushgram.app.camera;

import android.util.Log;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

/**
 * Analyzes ML Kit Pose to detect valid push-up reps.
 *
 * Push-up detection logic:
 * - Tracks elbow angle to determine UP (>150°) and DOWN (<90°) positions
 * - Validates form: body alignment, hands shoulder-width
 * - A valid rep = DOWN position followed by UP position with good form throughout
 */
public class PushUpAnalyzer {

    private static final String TAG = "PushUpAnalyzer";

    // Angle thresholds
    private static final double ANGLE_UP = 150.0;    // Arms extended
    private static final double ANGLE_DOWN = 90.0;   // Chest near ground
    private static final double BODY_ALIGNMENT_THRESHOLD = 20.0; // degrees deviation

    public enum Phase { UNKNOWN, UP, DOWN }

    public interface PushUpListener {
        void onRepCompleted(boolean isPerfectForm);
        void onPhaseChanged(Phase phase, double elbowAngle);
        void onFormFeedback(String feedback);
    }

    private Phase currentPhase = Phase.UNKNOWN;
    private boolean wasDown = false;
    private final PushUpListener listener;

    public PushUpAnalyzer(PushUpListener listener) {
        this.listener = listener;
    }

    public void analyze(Pose pose) {
        if (pose == null) return;

        // Get key landmarks
        PoseLandmark leftShoulder  = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark leftElbow     = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
        PoseLandmark rightElbow    = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
        PoseLandmark leftWrist     = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
        PoseLandmark rightWrist    = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
        PoseLandmark leftHip       = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark rightHip      = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
        PoseLandmark leftAnkle     = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
        PoseLandmark rightAnkle    = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);

        // Need enough landmarks
        if (leftShoulder == null || rightShoulder == null ||
                leftElbow == null || rightElbow == null ||
                leftWrist == null || rightWrist == null) {
            listener.onFormFeedback("Position your full body in frame");
            return;
        }

        // Calculate average elbow angle (left + right)
        double leftAngle = calculateAngle(leftShoulder, leftElbow, leftWrist);
        double rightAngle = calculateAngle(rightShoulder, rightElbow, rightWrist);
        double avgElbowAngle = (leftAngle + rightAngle) / 2.0;

        // Validate body alignment if hips/ankles available
        boolean goodForm = true;
        String formFeedback = "Good form!";

        if (leftHip != null && rightHip != null && leftAnkle != null && rightAnkle != null) {
            boolean aligned = isBodyAligned(leftShoulder, rightShoulder,
                    leftHip, rightHip, leftAnkle, rightAnkle);
            if (!aligned) {
                goodForm = false;
                formFeedback = "Keep your body straight!";
                listener.onFormFeedback(formFeedback);
            }
        }

        // Check elbow flare (elbows shouldn't be too wide out)
        boolean elbowsOk = checkElbowPosition(leftShoulder, rightShoulder, leftElbow, rightElbow);
        if (!elbowsOk) {
            goodForm = false;
            formFeedback = "Keep elbows closer to body";
            listener.onFormFeedback(formFeedback);
        }

        if (goodForm) {
            listener.onFormFeedback("Great form! Keep going!");
        }

        // Phase detection
        Phase newPhase;
        if (avgElbowAngle > ANGLE_UP) {
            newPhase = Phase.UP;
        } else if (avgElbowAngle < ANGLE_DOWN) {
            newPhase = Phase.DOWN;
        } else {
            newPhase = Phase.UNKNOWN; // transition
        }

        listener.onPhaseChanged(newPhase, avgElbowAngle);

        // Rep counting: DOWN -> UP = 1 rep
        if (newPhase == Phase.DOWN && currentPhase != Phase.DOWN) {
            wasDown = true;
            currentPhase = Phase.DOWN;
        } else if (newPhase == Phase.UP && wasDown) {
            wasDown = false;
            currentPhase = Phase.UP;
            Log.d(TAG, "Rep completed! Good form: " + goodForm);
            listener.onRepCompleted(goodForm);
        } else if (newPhase != Phase.UNKNOWN) {
            currentPhase = newPhase;
        }
    }

    /**
     * Calculate angle at the middle point (vertex) of three landmarks.
     */
    private double calculateAngle(PoseLandmark first, PoseLandmark mid, PoseLandmark last) {
        double ax = first.getPosition().x - mid.getPosition().x;
        double ay = first.getPosition().y - mid.getPosition().y;
        double bx = last.getPosition().x - mid.getPosition().x;
        double by = last.getPosition().y - mid.getPosition().y;

        double dotProduct = ax * bx + ay * by;
        double magA = Math.sqrt(ax * ax + ay * ay);
        double magB = Math.sqrt(bx * bx + by * by);

        if (magA == 0 || magB == 0) return 0;

        double cosAngle = dotProduct / (magA * magB);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle)); // clamp
        return Math.toDegrees(Math.acos(cosAngle));
    }

    /**
     * Check if body is roughly straight (shoulder-hip-ankle alignment).
     */
    private boolean isBodyAligned(PoseLandmark leftShoulder, PoseLandmark rightShoulder,
                                   PoseLandmark leftHip, PoseLandmark rightHip,
                                   PoseLandmark leftAnkle, PoseLandmark rightAnkle) {
        // Average the left and right sides
        float shoulderY = (leftShoulder.getPosition().y + rightShoulder.getPosition().y) / 2f;
        float hipY = (leftHip.getPosition().y + rightHip.getPosition().y) / 2f;
        float ankleY = (leftAnkle.getPosition().y + rightAnkle.getPosition().y) / 2f;
        float shoulderX = (leftShoulder.getPosition().x + rightShoulder.getPosition().x) / 2f;
        float hipX = (leftHip.getPosition().x + rightHip.getPosition().x) / 2f;
        float ankleX = (leftAnkle.getPosition().x + rightAnkle.getPosition().x) / 2f;

        // Vector from ankle to shoulder
        double vecX = shoulderX - ankleX;
        double vecY = shoulderY - ankleY;
        // Vector from ankle to hip
        double hipVecX = hipX - ankleX;
        double hipVecY = hipY - ankleY;

        // Check hip deviation from the shoulder-ankle line
        double lineLen = Math.sqrt(vecX * vecX + vecY * vecY);
        if (lineLen == 0) return true;

        // Cross product magnitude gives perpendicular distance proportion
        double cross = Math.abs(hipVecX * vecY - hipVecY * vecX);
        double deviation = cross / lineLen;

        // Allow up to ~20% deviation (relative to body length)
        return deviation < (lineLen * 0.2);
    }

    /**
     * Check that elbows aren't flared excessively outward.
     */
    private boolean checkElbowPosition(PoseLandmark leftShoulder, PoseLandmark rightShoulder,
                                        PoseLandmark leftElbow, PoseLandmark rightElbow) {
        float shoulderWidth = Math.abs(rightShoulder.getPosition().x - leftShoulder.getPosition().x);
        float elbowWidth = Math.abs(rightElbow.getPosition().x - leftElbow.getPosition().x);
        // Elbows should not be more than 1.8x shoulder width apart
        return elbowWidth <= (shoulderWidth * 1.8f);
    }

    public void reset() {
        currentPhase = Phase.UNKNOWN;
        wasDown = false;
    }
}
