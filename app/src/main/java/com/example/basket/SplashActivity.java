package com.example.basket;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private VideoView videoView;
    private boolean hasVideoCompleted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set content view first
        setContentView(R.layout.splash_screen);

        // Apply fullscreen immediately
        setupFullScreen();

        // Initialize video playback
        initializeVideo();
    }

    /**
     * Setup fullscreen mode with proper notch/cutout support for all Android versions
     */
    private void setupFullScreen() {
        // For Android R (API 30) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }

            // Enable content to be displayed in the cutout area
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(layoutParams);
        }
        // For older versions (Android 9+)
        else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

            // Support cutout mode for Android P (API 28)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                layoutParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(layoutParams);
            }
        }
    }

    /**
     * Initialize video playback with error handling
     */
    private void initializeVideo() {
        try {
            // Inisialisasi VideoView
            videoView = findViewById(R.id.splash_video);

            if (videoView == null) {
                Log.e(TAG, "VideoView not found in layout");
                navigateToMainActivity();
                return;
            }

            // Menonaktifkan fokus agar tidak bisa diklik
            videoView.setFocusable(false);
            videoView.setFocusableInTouchMode(false);

            // Set video path dari folder raw
            Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.introbaru);
            videoView.setVideoURI(videoUri);

            // Listener untuk mendeteksi saat video selesai
            videoView.setOnCompletionListener(mp -> {
                hasVideoCompleted = true;
                animateAndNavigate();
            });

            // Jika terjadi error pada video, langsung pindah ke MainActivity
            videoView.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Error playing video: what=" + what + ", extra=" + extra);
                navigateToMainActivity();
                return true;
            });

            // Mulai putar video
            videoView.start();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing video: " + e.getMessage());
            navigateToMainActivity();
        }
    }

    /**
     * Animate and navigate to the main activity
     */
    private void animateAndNavigate() {
        try {
            Animation zoomin = AnimationUtils.loadAnimation(SplashActivity.this, R.anim.zoom_in);
            videoView.startAnimation(zoomin);

            zoomin.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    // Do nothing
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    navigateToMainActivity();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                    // Do nothing
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error during animation: " + e.getMessage());
            navigateToMainActivity();
        }
    }

    /**
     * Navigate to the main activity
     */
    private void navigateToMainActivity() {
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupFullScreen();
        }
    }

    // Mencegah video dipause ketika layar disentuh
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true; // Mengabaikan semua sentuhan
    }

    // Mencegah tombol back
    @Override
    public void onBackPressed() {
        // No operation - ignore back press during splash
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reapply fullscreen when resuming
        setupFullScreen();

        // Restart video if it was already playing but hasn't completed
        if (videoView != null && !hasVideoCompleted) {
            try {
                if (!videoView.isPlaying()) {
                    videoView.start();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error restarting video: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause video if it's playing and not finished
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    // Jika activity dihentikan sebelum video selesai, langsung ke MainActivity
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
            videoView.suspend();
        }
    }
}