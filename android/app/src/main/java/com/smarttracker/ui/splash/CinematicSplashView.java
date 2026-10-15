package com.smarttracker.ui.splash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.smarttracker.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CinematicSplashView
 * A rich video-like cinematic animated intro screen in Pure Java:
 * - Expanding neon energy shockwaves
 * - Floating golden & emerald particle sparks
 * - Spring-zoom 3D app icon badge with glossy shimmer shine sweep
 * - Futuristic neon capsule progress bar with real-time status ticker
 */
public class CinematicSplashView extends View {

    public interface OnIntroFinishListener {
        void onIntroFinished();
    }

    private OnIntroFinishListener finishListener;

    private Paint backgroundPaint;
    private Paint ringPaint;
    private Paint iconBadgePaint;
    private Paint iconShadowPaint;
    private Paint shimmerPaint;
    private Paint titlePaint;
    private Paint subtitlePaint;
    private Paint badgeTextPaint;
    private Paint badgeBgPaint;
    private Paint progressTrackPaint;
    private Paint progressBarPaint;
    private Paint statusTextPaint;
    private Paint particlePaint;

    private Bitmap iconBitmap;
    private RectF rectF = new RectF();

    // Animation progress (0.0 to 1.0)
    private float introProgress = 0f;
    private float pulseTime = 0f;
    private float shimmerOffset = -1f;

    // Status message ticker
    private String currentStatus = "Initializing Encrypted Vault...";

    // Particle field
    private static class SparkParticle {
        float x, y;
        float speedY, speedX;
        float size;
        int color;
        float alpha;
        float maxAlpha;
    }
    private final List<SparkParticle> particles = new ArrayList<>();
    private final Random random = new Random();

    private ValueAnimator introAnimator;
    private ValueAnimator continuousAnimator;

    public CinematicSplashView(Context context) {
        super(context);
        init();
    }

    public CinematicSplashView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CinematicSplashView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnIntroFinishListener(OnIntroFinishListener listener) {
        this.finishListener = listener;
    }

    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);

        iconBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconBadgePaint.setStyle(Paint.Style.FILL);

        iconShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconShadowPaint.setStyle(Paint.Style.FILL);
        iconShadowPaint.setColor(Color.parseColor("#40000000"));

        shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shimmerPaint.setStyle(Paint.Style.FILL);

        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);

        subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setColor(Color.parseColor("#A7F3D0")); // Emerald tint
        subtitlePaint.setTextAlign(Paint.Align.CENTER);

        badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeBgPaint.setColor(Color.parseColor("#10B981")); // Brand Primary Emerald

        badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeTextPaint.setColor(Color.WHITE);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setFakeBoldText(true);

        progressTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressTrackPaint.setStyle(Paint.Style.FILL);
        progressTrackPaint.setColor(Color.parseColor("#1F3B32"));

        progressBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressBarPaint.setStyle(Paint.Style.FILL);

        statusTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        statusTextPaint.setColor(Color.parseColor("#6EE7B7"));
        statusTextPaint.setTextAlign(Paint.Align.CENTER);

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);

        // Pre-create 24 sparkling particles
        for (int i = 0; i < 24; i++) {
            SparkParticle p = new SparkParticle();
            p.x = random.nextFloat() * 1000f;
            p.y = random.nextFloat() * 1800f;
            p.speedY = 1.0f + random.nextFloat() * 2.5f;
            p.speedX = (random.nextFloat() - 0.5f) * 1.2f;
            p.size = 2f + random.nextFloat() * 4f;
            p.maxAlpha = 120 + random.nextInt(120);
            p.alpha = random.nextFloat() * p.maxAlpha;
            p.color = random.nextBoolean() ? Color.parseColor("#10B981") : Color.parseColor("#FBBF24");
            particles.add(p);
        }

        loadIconBitmap();
    }

    private void loadIconBitmap() {
        try {
            Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_wallet);
            if (drawable != null) {
                int size = 180;
                iconBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(iconBitmap);
                drawable.setBounds(0, 0, size, size);
                drawable.setTint(Color.WHITE);
                drawable.draw(c);
            }
        } catch (Exception ignored) {}
    }

    public void startIntroAnimation() {
        // Continuous ambient animation (particles + breathing pulse)
        continuousAnimator = ValueAnimator.ofFloat(0f, 1f);
        continuousAnimator.setDuration(1000);
        continuousAnimator.setRepeatCount(ValueAnimator.INFINITE);
        continuousAnimator.addUpdateListener(anim -> {
            pulseTime += 0.04f;
            invalidate();
        });
        continuousAnimator.start();

        // Main cinematic intro timeline (2.4 seconds total)
        introAnimator = ValueAnimator.ofFloat(0f, 1f);
        introAnimator.setDuration(2400);
        introAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        introAnimator.addUpdateListener(anim -> {
            introProgress = (float) anim.getAnimatedValue();

            // Dynamic status updates
            if (introProgress < 0.28f) {
                currentStatus = "Initializing Encrypted Vault...";
                shimmerOffset = -1f;
            } else if (introProgress < 0.62f) {
                currentStatus = "Connecting Cloud Financial Ledger...";
                shimmerOffset = (introProgress - 0.28f) / 0.34f; // 0.0 to 1.0 sweep
            } else if (introProgress < 0.88f) {
                currentStatus = "Synchronizing Supabase State...";
            } else {
                currentStatus = "System Ready • Welcome Back";
            }

            invalidate();
        });

        introAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (finishListener != null) {
                    finishListener.onIntroFinished();
                }
            }
        });

        introAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (introAnimator != null) introAnimator.cancel();
        if (continuousAnimator != null) continuousAnimator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f;
        float cy = h * 0.40f;

        // 1. Deep Obsidian-Emerald Cinematic Background Gradient
        backgroundPaint.setShader(new RadialGradient(
                cx, cy, h * 0.75f,
                new int[]{Color.parseColor("#092D21"), Color.parseColor("#061A14"), Color.parseColor("#020B08")},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, w, h, backgroundPaint);

        // 2. Ambient Floating Spark Particles
        for (SparkParticle p : particles) {
            p.y -= p.speedY;
            p.x += p.speedX;
            if (p.y < 0) {
                p.y = h + 20f;
                p.x = random.nextFloat() * w;
            }
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int) p.alpha);
            canvas.drawCircle(p.x, p.y, p.size, particlePaint);
        }

        // 3. Concentric Expanding Shockwave Rings
        for (int i = 0; i < 3; i++) {
            float ringPhase = (introProgress * 2.2f + i * 0.33f) % 1.0f;
            float ringRadius = (w * 0.22f) + ringPhase * (w * 0.55f);
            int ringAlpha = (int) ((1f - ringPhase) * 110f * Math.min(introProgress * 2f, 1f));

            ringPaint.setColor(Color.parseColor("#10B981"));
            ringPaint.setAlpha(Math.max(0, ringAlpha));
            ringPaint.setStrokeWidth(3f + (1f - ringPhase) * 4f);
            canvas.drawCircle(cx, cy, ringRadius, ringPaint);
        }

        // 4. App Icon Badge with Spring-Zoom and 3D Drop Shadow
        float iconScale;
        if (introProgress < 0.45f) {
            // Spring zoom in with overshoot
            float t = introProgress / 0.45f;
            iconScale = (float) Math.sin(t * Math.PI / 2f) * 1.08f;
        } else {
            // Settled with gentle breathing pulse
            iconScale = 1.0f + (float) Math.sin(pulseTime * 2f) * 0.025f;
        }

        float badgeSize = w * 0.32f * iconScale;
        float halfBadge = badgeSize / 2f;

        // Badge Shadow
        rectF.set(cx - halfBadge, cy - halfBadge + 16, cx + halfBadge, cy + halfBadge + 16);
        canvas.drawRoundRect(rectF, halfBadge * 0.45f, halfBadge * 0.45f, iconShadowPaint);

        // Badge Background Gradient (Glossy Emerald Glass)
        iconBadgePaint.setShader(new LinearGradient(
                cx - halfBadge, cy - halfBadge,
                cx + halfBadge, cy + halfBadge,
                new int[]{Color.parseColor("#34D399"), Color.parseColor("#10B981"), Color.parseColor("#065F46")},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        ));
        rectF.set(cx - halfBadge, cy - halfBadge, cx + halfBadge, cy + halfBadge);
        canvas.drawRoundRect(rectF, halfBadge * 0.45f, halfBadge * 0.45f, iconBadgePaint);

        // Glowing Badge Border
        ringPaint.setColor(Color.parseColor("#6EE7B7"));
        ringPaint.setAlpha(180);
        ringPaint.setStrokeWidth(3.5f);
        canvas.drawRoundRect(rectF, halfBadge * 0.45f, halfBadge * 0.45f, ringPaint);

        // 5. Draw Icon inside Badge
        if (iconBitmap != null) {
            canvas.save();
            canvas.translate(cx, cy);
            float iconDrawSize = halfBadge * 1.15f;
            Rect src = new Rect(0, 0, iconBitmap.getWidth(), iconBitmap.getHeight());
            RectF dst = new RectF(-iconDrawSize / 2f, -iconDrawSize / 2f, iconDrawSize / 2f, iconDrawSize / 2f);
            canvas.drawBitmap(iconBitmap, src, dst, null);
            canvas.restore();
        }

        // 6. Shimmer Light Streak Sweep across Badge
        if (shimmerOffset >= 0f && shimmerOffset <= 1.0f) {
            canvas.save();
            canvas.clipRect(rectF);
            float sweepX = (cx - badgeSize) + shimmerOffset * (badgeSize * 3f);
            shimmerPaint.setShader(new LinearGradient(
                    sweepX - 40, cy - badgeSize,
                    sweepX + 40, cy + badgeSize,
                    new int[]{Color.TRANSPARENT, Color.parseColor("#90FFFFFF"), Color.TRANSPARENT},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(rectF, shimmerPaint);
            canvas.restore();
        }

        // 7. Typography (Cinematic Fade Up)
        float textAlpha = Math.min(1f, Math.max(0f, (introProgress - 0.25f) / 0.35f));
        int titleAlphaInt = (int) (textAlpha * 255);

        titlePaint.setTextSize(w * 0.078f);
        titlePaint.setAlpha(titleAlphaInt);
        float titleY = cy + halfBadge + h * 0.065f;
        canvas.drawText("SMART EXPENSES", cx, titleY, titlePaint);

        // Subtitle Badge Capsule
        subtitlePaint.setTextSize(w * 0.034f);
        subtitlePaint.setAlpha((int) (textAlpha * 220));
        float subtitleY = titleY + h * 0.038f;
        canvas.drawText("FINANCIAL INTELLIGENCE • PURE JAVA", cx, subtitleY, subtitlePaint);

        // 8. Futuristic Neon Progress Bar (Bottom)
        float progressWidth = w * 0.68f;
        float progressHeight = 8f;
        float progressY = h * 0.82f;
        float progressLeft = cx - progressWidth / 2f;
        float progressRight = cx + progressWidth / 2f;

        // Progress Track
        rectF.set(progressLeft, progressY - progressHeight / 2f, progressRight, progressY + progressHeight / 2f);
        canvas.drawRoundRect(rectF, 4f, 4f, progressTrackPaint);

        // Active Bar
        float currentFill = progressLeft + (progressWidth * introProgress);
        if (currentFill > progressLeft) {
            progressBarPaint.setShader(new LinearGradient(
                    progressLeft, progressY,
                    currentFill, progressY,
                    new int[]{Color.parseColor("#10B981"), Color.parseColor("#34D399"), Color.parseColor("#FBBF24")},
                    new float[]{0f, 0.75f, 1f},
                    Shader.TileMode.CLAMP
            ));
            rectF.set(progressLeft, progressY - progressHeight / 2f, currentFill, progressY + progressHeight / 2f);
            canvas.drawRoundRect(rectF, 4f, 4f, progressBarPaint);

            // Glowing Progress Head
            canvas.drawCircle(currentFill, progressY, 6f, progressBarPaint);
        }

        // 9. Real-Time Status Message Ticker
        statusTextPaint.setTextSize(w * 0.031f);
        statusTextPaint.setAlpha((int) (textAlpha * 200));
        canvas.drawText(currentStatus, cx, progressY + h * 0.042f, statusTextPaint);
    }
}
