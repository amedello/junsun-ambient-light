package com.junsun.ambientlight.views;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Visualizzatore Cockpit di Lusso (Stile Mercedes MBUX / Porsche PCM).
 * Disegna l'abitacolo dell'auto con luci ambientali a multi-strato:
 * - Linea cruscotto panoramica
 * - Profili portiere avvolgenti
 * - Barre a doppio tubo del tunnel centrale
 * - Illuminazione diffusa vano piedi
 * - Animazione fluida di transizione colore e alone di luce neon (bloom).
 */
public class CockpitView extends View {

    private Paint carBodyPaint;
    private Paint carGlassPaint;
    private Paint interiorDetailPaint;
    private Paint seatPaint;

    private Paint glowOuterPaint;
    private Paint glowMidPaint;
    private Paint neonCorePaint;

    private int currentColor = Color.parseColor("#00E5FF");
    private int targetColor = Color.parseColor("#00E5FF");
    private boolean isPowerOn = true;
    private boolean isMusicMode = false;
    private float brightnessFactor = 0.85f;
    private float musicHue = 0f;

    private float breathFactor = 1.0f;
    private ValueAnimator colorAnimator;
    private ValueAnimator breathAnimator;
    private ValueAnimator musicAnimator;

    public CockpitView(Context context) {
        super(context);
        init();
    }

    public CockpitView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CockpitView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        carBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        carBodyPaint.setStyle(Paint.Style.STROKE);
        carBodyPaint.setStrokeWidth(2.8f);
        carBodyPaint.setColor(Color.parseColor("#26354A"));

        carGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        carGlassPaint.setStyle(Paint.Style.FILL);
        carGlassPaint.setColor(Color.parseColor("#0B1018"));

        interiorDetailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        interiorDetailPaint.setStyle(Paint.Style.STROKE);
        interiorDetailPaint.setStrokeWidth(1.6f);
        interiorDetailPaint.setColor(Color.parseColor("#1F2D3E"));

        seatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        seatPaint.setStyle(Paint.Style.STROKE);
        seatPaint.setStrokeWidth(1.8f);
        seatPaint.setColor(Color.parseColor("#223042"));

        glowOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowOuterPaint.setStyle(Paint.Style.STROKE);
        glowOuterPaint.setStrokeCap(Paint.Cap.ROUND);
        glowOuterPaint.setStrokeJoin(Paint.Join.ROUND);

        glowMidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowMidPaint.setStyle(Paint.Style.STROKE);
        glowMidPaint.setStrokeCap(Paint.Cap.ROUND);
        glowMidPaint.setStrokeJoin(Paint.Join.ROUND);

        neonCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        neonCorePaint.setStyle(Paint.Style.STROKE);
        neonCorePaint.setStrokeCap(Paint.Cap.ROUND);
        neonCorePaint.setStrokeJoin(Paint.Join.ROUND);
        neonCorePaint.setStrokeWidth(3.8f);

        // Animazione pulsazione impercettibile di respiro (respiro ambient light)
        breathAnimator = ValueAnimator.ofFloat(0.92f, 1.06f);
        breathAnimator.setDuration(2400);
        breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathAnimator.setRepeatMode(ValueAnimator.REVERSE);
        breathAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        breathAnimator.addUpdateListener(anim -> {
            breathFactor = (float) anim.getAnimatedValue();
            invalidate();
        });
        breathAnimator.start();
    }

    public void setMusicMode(boolean active) {
        this.isMusicMode = active;
        if (active) {
            if (musicAnimator == null) {
                musicAnimator = ValueAnimator.ofFloat(0f, 360f);
                musicAnimator.setDuration(3500);
                musicAnimator.setRepeatCount(ValueAnimator.INFINITE);
                musicAnimator.addUpdateListener(anim -> {
                    musicHue = (float) anim.getAnimatedValue();
                    invalidate();
                });
            }
            if (!musicAnimator.isRunning()) {
                musicAnimator.start();
            }
        } else {
            if (musicAnimator != null && musicAnimator.isRunning()) {
                musicAnimator.cancel();
            }
        }
        invalidate();
    }

    public void setAmbientColor(int newColor) {
        this.isMusicMode = false;
        if (musicAnimator != null && musicAnimator.isRunning()) {
            musicAnimator.cancel();
        }
        if (this.targetColor == newColor) return;
        this.targetColor = newColor;

        if (colorAnimator != null && colorAnimator.isRunning()) {
            colorAnimator.cancel();
        }

        colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, targetColor);
        colorAnimator.setDuration(350);
        colorAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        colorAnimator.addUpdateListener(animation -> {
            currentColor = (int) animation.getAnimatedValue();
            invalidate();
        });
        colorAnimator.start();
    }

    public void setPowerState(boolean powerOn) {
        this.isPowerOn = powerOn;
        invalidate();
    }

    public void setBrightness(int percent) {
        this.brightnessFactor = Math.max(0.12f, percent / 100.0f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2.0f;
        float cy = h / 2.0f;
        float scale = Math.min(w / 380.0f, h / 380.0f);

        // 1. Sfondo alone diffuso (Aura Cockpit)
        if (isPowerOn) {
            int activeRenderColor = isMusicMode ? Color.HSVToColor(new float[]{musicHue, 1.0f, 1.0f}) : currentColor;
            float effBrightness = brightnessFactor * (isMusicMode ? 1.0f : breathFactor);
            int r = Color.red(activeRenderColor);
            int g = Color.green(activeRenderColor);
            int b = Color.blue(activeRenderColor);

            // Luce diffusa al centro
            int centralAlpha = (int) (48 * effBrightness);
            Paint radialFloorGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
            radialFloorGlow.setShader(new RadialGradient(cx, cy - (15 * scale), 160 * scale,
                    Color.argb(centralAlpha, r, g, b), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy - (15 * scale), 160 * scale, radialFloorGlow);

            // Luce vano piedi SX e DX
            int footwellAlpha = (int) (60 * effBrightness);
            Paint footwellGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
            footwellGlow.setShader(new RadialGradient(cx - (55 * scale), cy - (25 * scale), 48 * scale,
                    Color.argb(footwellAlpha, r, g, b), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx - (55 * scale), cy - (25 * scale), 48 * scale, footwellGlow);

            footwellGlow.setShader(new RadialGradient(cx + (55 * scale), cy - (25 * scale), 48 * scale,
                    Color.argb(footwellAlpha, r, g, b), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx + (55 * scale), cy - (25 * scale), 48 * scale, footwellGlow);
        }

        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        // 2. Scocca Auto Sportiva (Silhouette Aerodinamica)
        Path carSilhouette = new Path();
        carSilhouette.moveTo(-95, -145);
        carSilhouette.cubicTo(-60, -165, 60, -165, 95, -145); // Muso/Cofano
        carSilhouette.cubicTo(115, -90, 118, 90, 102, 135);   // Fiancata DX
        carSilhouette.cubicTo(65, 158, -65, 158, -102, 135);  // Coda posteriore
        carSilhouette.cubicTo(-118, 90, -115, -90, -95, -145);// Fiancata SX
        carSilhouette.close();

        // Riempimento cabina scura
        canvas.drawPath(carSilhouette, carGlassPaint);
        canvas.drawPath(carSilhouette, carBodyPaint);

        // Parabrezza e montanti
        Path windshield = new Path();
        windshield.moveTo(-76, -125);
        windshield.cubicTo(-40, -142, 40, -142, 76, -125);
        windshield.lineTo(84, -58);
        windshield.cubicTo(45, -68, -45, -68, -84, -58);
        windshield.close();
        canvas.drawPath(windshield, interiorDetailPaint);

        // Sedili Sportivi a Guscio
        // Guida
        canvas.drawRoundRect(-72, -32, -22, 54, 14, 14, seatPaint);
        canvas.drawRoundRect(-64, -24, -30, -8, 6, 6, interiorDetailPaint); // Poggiatesta
        // Passeggero
        canvas.drawRoundRect(22, -32, 72, 54, 14, 14, seatPaint);
        canvas.drawRoundRect(30, -24, 64, -8, 6, 6, interiorDetailPaint); // Poggiatesta

        // Tunnel centrale di lusso con selettore
        canvas.drawRoundRect(-14, -48, 14, 65, 10, 10, interiorDetailPaint);
        canvas.drawCircle(0, -15, 6, interiorDetailPaint); // Manopola selettore

        // Volante a 3 razze sportivo piatto in basso
        canvas.drawCircle(-47, -62, 18, interiorDetailPaint);
        canvas.drawLine(-47, -62, -47, -44, interiorDetailPaint);
        canvas.drawLine(-47, -62, -62, -62, interiorDetailPaint);
        canvas.drawLine(-47, -62, -32, -62, interiorDetailPaint);

        // 3. Strisce LED Ambient Light (Vettoriali ad Alta Definizione)
        Path ambientStrips = new Path();

        // A. Arco Panoramico Cruscotto (Curva continua da portiera a portiera)
        ambientStrips.moveTo(-82, -50);
        ambientStrips.cubicTo(-40, -66, 40, -66, 82, -50);

        // B. Portiera Sinistra (Profilo dinamico ad ala)
        ambientStrips.moveTo(-90, -35);
        ambientStrips.cubicTo(-94, 20, -92, 60, -88, 85);

        // C. Portiera Destra
        ambientStrips.moveTo(90, -35);
        ambientStrips.cubicTo(94, 20, 92, 60, 88, 85);

        // D. Binari illuminati Tunnel Centrale (Twin Rails)
        ambientStrips.moveTo(-16, -42);
        ambientStrips.lineTo(-16, 55);
        ambientStrips.moveTo(16, -42);
        ambientStrips.lineTo(16, 55);

        // E. Illuminazione posteriore (Halo dei sedili)
        ambientStrips.moveTo(-70, 75);
        ambientStrips.cubicTo(-30, 82, 30, 82, 70, 75);

        if (isPowerOn) {
            int activeRenderColor = isMusicMode ? Color.HSVToColor(new float[]{musicHue, 1.0f, 1.0f}) : currentColor;
            float effBrightness = brightnessFactor * (isMusicMode ? 1.0f : breathFactor);
            int r = Color.red(activeRenderColor);
            int g = Color.green(activeRenderColor);
            int b = Color.blue(activeRenderColor);

            // Layer 1: Bloom esterno ultra-morbido (Deep Glow)
            glowOuterPaint.setColor(Color.argb((int) (85 * effBrightness), r, g, b));
            glowOuterPaint.setStrokeWidth(17f);
            canvas.drawPath(ambientStrips, glowOuterPaint);

            // Layer 2: Bloom intermedio brillante
            glowMidPaint.setColor(Color.argb((int) (175 * effBrightness), r, g, b));
            glowMidPaint.setStrokeWidth(9f);
            canvas.drawPath(ambientStrips, glowMidPaint);

            // Layer 3: Anima al neon satura
            neonCorePaint.setColor(activeRenderColor);
            neonCorePaint.setStrokeWidth(3.8f);
            canvas.drawPath(ambientStrips, neonCorePaint);

            // Layer 4: Filo bianco centrale iper-luminoso (Highlight di vetro ottico)
            Paint whiteCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            whiteCorePaint.setStyle(Paint.Style.STROKE);
            whiteCorePaint.setStrokeCap(Paint.Cap.ROUND);
            whiteCorePaint.setStrokeJoin(Paint.Join.ROUND);
            whiteCorePaint.setStrokeWidth(1.4f);
            whiteCorePaint.setColor(Color.argb((int) (210 * effBrightness), 255, 255, 255));
            canvas.drawPath(ambientStrips, whiteCorePaint);
        } else {
            // Stato Standby / Spento
            neonCorePaint.setColor(Color.parseColor("#263546"));
            neonCorePaint.setStrokeWidth(2.5f);
            canvas.drawPath(ambientStrips, neonCorePaint);
        }

        canvas.restore();
    }
}
