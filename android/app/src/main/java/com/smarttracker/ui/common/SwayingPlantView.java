package com.smarttracker.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SwayingPlantView
 * A custom animated view in Pure Java featuring an organic potted plant with
 * realistic stem flex, fluttering leaves, a blooming flower, drifting pollen particles,
 * and wind gust dynamics with interactive touch spring-back.
 */
public class SwayingPlantView extends View {

    private Paint stemPaint;
    private Paint leafPaint;
    private Paint leafVeinPaint;
    private Paint flowerPetalPaint;
    private Paint flowerCenterPaint;
    private Paint flowerCenterInnerPaint;
    private Paint potPaint;
    private Paint potRimPaint;
    private Paint potShadowPaint;
    private Paint soilPaint;
    private Paint particlePaint;

    private Path stemPath = new Path();
    private Path leafPath = new Path();
    private Path potPath = new Path();
    private RectF rectF = new RectF();

    private float animTime = 0f;
    private ValueAnimator animator;

    // Wind dynamics
    private float windForce = 1.0f;
    private float targetWindForce = 1.0f;
    private float windAngle = 0f;
    private float touchDisplacement = 0f;
    private float touchVelocity = 0f;

    // Floating wind particles
    private static class WindParticle {
        float x, y;
        float speedX, speedY;
        float size;
        float alpha;
        int color;
    }
    private final List<WindParticle> particles = new ArrayList<>();
    private final Random random = new Random();

    public SwayingPlantView(Context context) {
        super(context);
        init();
    }

    public SwayingPlantView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwayingPlantView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        stemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stemPaint.setStyle(Paint.Style.STROKE);
        stemPaint.setStrokeCap(Paint.Cap.ROUND);
        stemPaint.setColor(Color.parseColor("#2E7D32")); // Forest Green

        leafPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        leafPaint.setStyle(Paint.Style.FILL);
        leafPaint.setColor(Color.parseColor("#4CAF50")); // Vibrant Leaf Green

        leafVeinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        leafVeinPaint.setStyle(Paint.Style.STROKE);
        leafVeinPaint.setColor(Color.parseColor("#81C784"));
        leafVeinPaint.setStrokeWidth(2f);

        flowerPetalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flowerPetalPaint.setStyle(Paint.Style.FILL);

        flowerCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flowerCenterPaint.setStyle(Paint.Style.FILL);
        flowerCenterPaint.setColor(Color.parseColor("#FFD54F")); // Warm Gold

        flowerCenterInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flowerCenterInnerPaint.setStyle(Paint.Style.FILL);
        flowerCenterInnerPaint.setColor(Color.parseColor("#FF8F00")); // Deep Amber

        potPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        potPaint.setStyle(Paint.Style.FILL);
        potPaint.setColor(Color.parseColor("#D87D4A")); // Terracotta Ceramic

        potRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        potRimPaint.setStyle(Paint.Style.FILL);
        potRimPaint.setColor(Color.parseColor("#C26533")); // Darker Terracotta Rim

        potShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        potShadowPaint.setStyle(Paint.Style.FILL);
        potShadowPaint.setColor(Color.parseColor("#15000000")); // Soft drop shadow

        soilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        soilPaint.setStyle(Paint.Style.FILL);
        soilPaint.setColor(Color.parseColor("#4E342E")); // Rich Soil

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);

        // Pre-create 12 floating breeze particles
        for (int i = 0; i < 14; i++) {
            WindParticle p = new WindParticle();
            resetParticle(p, true);
            particles.add(p);
        }

        // Animator running at 60 FPS
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            animTime += 0.035f;

            // Smooth wind gust variation
            if (random.nextInt(40) == 0) {
                targetWindForce = 0.6f + random.nextFloat() * 1.4f;
            }
            windForce += (targetWindForce - windForce) * 0.05f;

            // Touch spring relaxation
            if (touchDisplacement != 0f || touchVelocity != 0f) {
                float springK = 0.08f;
                float damping = 0.85f;
                float force = -springK * touchDisplacement;
                touchVelocity = (touchVelocity + force) * damping;
                touchDisplacement += touchVelocity;
                if (Math.abs(touchDisplacement) < 0.1f && Math.abs(touchVelocity) < 0.1f) {
                    touchDisplacement = 0f;
                    touchVelocity = 0f;
                }
            }

            invalidate();
        });
    }

    private void resetParticle(WindParticle p, boolean initial) {
        int w = getWidth() > 0 ? getWidth() : 300;
        int h = getHeight() > 0 ? getHeight() : 300;
        p.x = initial ? random.nextFloat() * w : -20f;
        p.y = random.nextFloat() * (h * 0.85f);
        p.speedX = 1.2f + random.nextFloat() * 2.2f;
        p.speedY = (random.nextFloat() - 0.5f) * 0.6f;
        p.size = 2.5f + random.nextFloat() * 3.5f;
        p.alpha = 80 + random.nextInt(120);
        int[] palette = {
                Color.parseColor("#81C784"), // Pale green leaf fleck
                Color.parseColor("#FFF59D"), // Light yellow pollen
                Color.parseColor("#FFCDD2"), // Petal pink fleck
                Color.parseColor("#A5D6A7")  // Mint
        };
        p.color = palette[random.nextInt(palette.length)];
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float cx = getWidth() / 2f;
                touchDisplacement = (event.getX() - cx) * 0.45f;
                touchVelocity = 0f;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchVelocity = (touchDisplacement > 0 ? 8f : -8f);
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f;
        float potBaseY = h * 0.88f;
        float potTopY = h * 0.68f;
        float potWidthTop = w * 0.38f;
        float potWidthBase = w * 0.28f;
        float plantHeight = h * 0.52f;

        // 1. Draw floating wind/pollen particles
        for (WindParticle p : particles) {
            p.x += p.speedX * (0.8f + windForce * 0.4f);
            p.y += p.speedY + (float) Math.sin(animTime * 2f + p.x * 0.05f) * 0.5f;
            if (p.x > w + 20) {
                resetParticle(p, false);
            }
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int) p.alpha);
            canvas.drawCircle(p.x, p.y, p.size, particlePaint);
        }

        // 2. Pot Ground Shadow
        rectF.set(cx - potWidthTop * 0.7f, potBaseY + 4, cx + potWidthTop * 0.7f, potBaseY + 16);
        canvas.drawOval(rectF, potShadowPaint);

        // 3. Compute Wind Sway Angle
        // Composite multi-frequency organic wind formula
        float primaryWave = (float) Math.sin(animTime * 1.8f);
        float gustWave = (float) Math.sin(animTime * 0.75f + 1.2f) * (float) Math.cos(animTime * 0.4f);
        float microBreeze = (float) Math.sin(animTime * 4.2f) * 0.15f;
        float totalSway = (primaryWave * 16f + gustWave * 24f + microBreeze * 8f) * windForce + touchDisplacement;

        // 4. Draw Organic Stem (Bézier Curve)
        float stemBaseX = cx;
        float stemBaseY = potTopY - 2;

        float ctrl1X = stemBaseX + totalSway * 0.25f;
        float ctrl1Y = stemBaseY - plantHeight * 0.35f;

        float ctrl2X = stemBaseX + totalSway * 0.70f;
        float ctrl2Y = stemBaseY - plantHeight * 0.75f;

        float tipX = stemBaseX + totalSway;
        float tipY = stemBaseY - plantHeight;

        stemPaint.setStrokeWidth(w * 0.024f);
        stemPath.reset();
        stemPath.moveTo(stemBaseX, stemBaseY);
        stemPath.cubicTo(ctrl1X, ctrl1Y, ctrl2X, ctrl2Y, tipX, tipY);
        canvas.drawPath(stemPath, stemPaint);

        // 5. Draw Fluttering Leaves along stem
        // Leaf 1: Lower Left (at ~35% height)
        float t1 = 0.35f;
        float leaf1X = evaluateBezier(stemBaseX, ctrl1X, ctrl2X, tipX, t1);
        float leaf1Y = evaluateBezier(stemBaseY, ctrl1Y, ctrl2Y, tipY, t1);
        float leaf1Angle = -42f + totalSway * 0.4f + (float) Math.sin(animTime * 3f) * 6f;
        drawLeaf(canvas, leaf1X, leaf1Y, leaf1Angle, w * 0.16f, w * 0.075f, Color.parseColor("#388E3C"));

        // Leaf 2: Mid Right (at ~60% height)
        float t2 = 0.60f;
        float leaf2X = evaluateBezier(stemBaseX, ctrl1X, ctrl2X, tipX, t2);
        float leaf2Y = evaluateBezier(stemBaseY, ctrl1Y, ctrl2Y, tipY, t2);
        float leaf2Angle = 38f + totalSway * 0.5f + (float) Math.sin(animTime * 3.2f + 1.5f) * 7f;
        drawLeaf(canvas, leaf2X, leaf2Y, leaf2Angle, w * 0.18f, w * 0.082f, Color.parseColor("#43A047"));

        // Leaf 3: Upper Left (at ~78% height)
        float t3 = 0.78f;
        float leaf3X = evaluateBezier(stemBaseX, ctrl1X, ctrl2X, tipX, t3);
        float leaf3Y = evaluateBezier(stemBaseY, ctrl1Y, ctrl2Y, tipY, t3);
        float leaf3Angle = -32f + totalSway * 0.6f + (float) Math.sin(animTime * 3.5f + 2.5f) * 8f;
        drawLeaf(canvas, leaf3X, leaf3Y, leaf3Angle, w * 0.14f, w * 0.068f, Color.parseColor("#4CAF50"));

        // 6. Draw Flower at Top of Stem
        // Tangent angle at tip of Bézier curve
        float dx = 3 * (1 - 1f) * (1 - 1f) * (ctrl1X - stemBaseX) + 6 * (1 - 1f) * 1f * (ctrl2X - ctrl1X) + 3 * 1f * 1f * (tipX - ctrl2X);
        float dy = 3 * (1 - 1f) * (1 - 1f) * (ctrl1Y - stemBaseY) + 6 * (1 - 1f) * 1f * (ctrl2Y - ctrl1Y) + 3 * 1f * 1f * (tipY - ctrl2Y);
        float flowerRotation = (float) Math.toDegrees(Math.atan2(dy, dx)) + 90f;
        drawFlower(canvas, tipX, tipY, flowerRotation, w * 0.18f);

        // 7. Draw Ceramic Terracotta Pot
        potPath.reset();
        potPath.moveTo(cx - potWidthTop / 2f, potTopY);
        potPath.lineTo(cx + potWidthTop / 2f, potTopY);
        potPath.lineTo(cx + potWidthBase / 2f, potBaseY);
        potPath.lineTo(cx - potWidthBase / 2f, potBaseY);
        potPath.close();

        // Shaded ceramic pot gradient
        potPaint.setShader(new LinearGradient(
                cx - potWidthTop / 2f, potTopY,
                cx + potWidthTop / 2f, potTopY,
                new int[]{Color.parseColor("#E0824E"), Color.parseColor("#C26533"), Color.parseColor("#A84F22")},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawPath(potPath, potPaint);

        // Pot Rim (raised 3D edge)
        rectF.set(cx - potWidthTop * 0.54f, potTopY - h * 0.024f, cx + potWidthTop * 0.54f, potTopY + h * 0.016f);
        canvas.drawRoundRect(rectF, 8f, 8f, potRimPaint);

        // Soil inside pot top
        rectF.set(cx - potWidthTop * 0.44f, potTopY - h * 0.012f, cx + potWidthTop * 0.44f, potTopY + h * 0.008f);
        canvas.drawOval(rectF, soilPaint);
    }

    private void drawLeaf(Canvas canvas, float x, float y, float angleDegrees, float length, float width, int color) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angleDegrees);

        leafPaint.setColor(color);
        leafPath.reset();
        leafPath.moveTo(0, 0);
        // Left curve of leaf
        leafPath.quadTo(length * 0.45f, -width, length, 0);
        // Right curve of leaf
        leafPath.quadTo(length * 0.45f, width, 0, 0);
        leafPath.close();
        canvas.drawPath(leafPath, leafPaint);

        // Leaf center vein
        canvas.drawLine(0, 0, length * 0.85f, 0, leafVeinPaint);

        canvas.restore();
    }

    private void drawFlower(Canvas canvas, float x, float y, float rotation, float diameter) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);

        float radius = diameter / 2f;
        int petalCount = 6;
        float petalRadius = radius * 0.72f;

        // Draw petals in layered radial formation
        for (int i = 0; i < petalCount; i++) {
            float angle = (360f / petalCount) * i;
            canvas.save();
            canvas.rotate(angle);

            // Petal gradient from vibrant coral pink to soft pearl peach
            flowerPetalPaint.setShader(new RadialGradient(
                    0, -petalRadius * 0.6f, petalRadius,
                    new int[]{Color.parseColor("#FF6F61"), Color.parseColor("#FFA07A"), Color.parseColor("#FFD1C1")},
                    new float[]{0f, 0.65f, 1f},
                    Shader.TileMode.CLAMP
            ));

            rectF.set(-petalRadius * 0.48f, -petalRadius * 1.15f, petalRadius * 0.48f, 0f);
            canvas.drawRoundRect(rectF, petalRadius * 0.48f, petalRadius * 0.48f, flowerPetalPaint);

            canvas.restore();
        }

        // Flower golden pistil / core
        canvas.drawCircle(0, 0, radius * 0.38f, flowerCenterPaint);
        canvas.drawCircle(0, 0, radius * 0.22f, flowerCenterInnerPaint);

        // Highlight glint
        Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glint.setColor(Color.WHITE);
        glint.setAlpha(180);
        canvas.drawCircle(-radius * 0.09f, -radius * 0.09f, radius * 0.07f, glint);

        canvas.restore();
    }

    private float evaluateBezier(float p0, float p1, float p2, float p3, float t) {
        float u = 1f - t;
        return (u * u * u * p0) + (3 * u * u * t * p1) + (3 * u * t * t * p2) + (t * t * t * p3);
    }
}
