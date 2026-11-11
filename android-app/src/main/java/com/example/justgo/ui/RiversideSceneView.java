package com.example.justgo.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Decorative, theme-specific illustrations, independent of real vehicle/location data. */
public final class RiversideSceneView extends View {
    public enum Theme {
        ANYWHERE("어디든"), CITY("도시"), MOUNTAIN("산"),
        SEA("바다"), WATER("강/호수"), PARK("공원");

        private final String label;

        Theme(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    private static final int SCENE_WIDTH = 600;
    private static final int SCENE_HEIGHT = 1000;
    private static final long ANIMATION_DURATION_MS = 24_000L;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Rect previewSource = new Rect();
    private final ValueAnimator animator;
    private final LinearGradient carBody = new LinearGradient(0, -43, 0, 28, 0xFFFFFFFF, 0xFF91A8BA, Shader.TileMode.CLAMP);
    private Bitmap background;
    private Theme theme = Theme.ANYWHERE;
    private float phase;
    private boolean motionAllowed = true;
    private boolean previewMode;

    public RiversideSceneView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
    }

    public void setTheme(Theme next) {
        if (next == theme) return;
        theme = next;
        releaseBackground();
        invalidate();
    }

    public void setMotionAllowed(boolean allowed) {
        if (motionAllowed == allowed) return;
        motionAllowed = allowed;
        syncAnimation();
        invalidate();
    }

    public void setPreviewMode() {
        previewMode = true;
        setMotionAllowed(false);
    }

    private void box(Canvas c, float x, float y, float w, float h, int color) {
        paint.setColor(color); c.drawRect(x, y, x + w, y + h, paint);
    }

    private void oval(Canvas c, float x, float y, float w, float h, int color) {
        paint.setColor(color); rect.set(x, y, x + w, y + h); c.drawOval(rect, paint);
    }

    private void hill(Canvas c, float y, int color, float bend) {
        path.reset(); path.moveTo(-50, y);
        path.cubicTo(110, y - bend, 170, y + bend, 320, y - 20);
        path.cubicTo(460, y - bend, 520, y + 30, 650, y - 12);
        path.lineTo(650, 1000); path.lineTo(-50, 1000); path.close();
        paint.setColor(color); c.drawPath(path, paint);
    }

    private void tree(Canvas c, float x, float y, float size, boolean pine) {
        box(c, x - 2, y, 5, size * .66f, 0xFF6C7457);
        if (pine) {
            path.reset(); path.moveTo(x, y - size); path.lineTo(x - size * .4f, y + 5);
            path.lineTo(x + size * .4f, y + 5); path.close();
            paint.setColor(0xFF3F7356); c.drawPath(path, paint);
        } else {
            oval(c, x - size * .55f, y - size, size * 1.1f, size, 0xFF568854);
            oval(c, x - size * .6f, y - size * .65f, size * .7f, size * .65f, 0xFF699B61);
        }
    }

    private void clouds(Canvas c) {
        for (int i = 0; i < 3; i++) {
            c.save(); c.translate(i == 0 ? -40 : i == 1 ? 285 : 520, i == 0 ? 355 : i == 1 ? 410 : 340);
            float scale = i == 1 ? 1 : .75f; c.scale(scale, scale);
            oval(c, 0, 22, 150, 31, 0xF5F6FBFF);
            oval(c, 22, 6, 65, 40, 0xF5F6FBFF);
            oval(c, 66, -5, 58, 52, 0xF5F6FBFF);
            c.restore();
        }
    }

    private void buildBackground() {
        background = Bitmap.createBitmap(SCENE_WIDTH, SCENE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(background);
        paint.setShader(new LinearGradient(0, 0, 0, 650, 0xFF76ADE0, 0xFFEDF6F8, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 600, 1000, paint); paint.setShader(null);
        paint.setShader(new RadialGradient(450, 255, 65, new int[]{0xFFFFF9DB, 0xCCFFF9DB, 0x00FFF9DB}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(450, 255, 65, paint); paint.setShader(null);
        switch (theme) {
            case CITY: drawCity(c); break;
            case MOUNTAIN: drawMountains(c); break;
            case SEA: drawSea(c); break;
            case WATER: drawWater(c); break;
            case PARK: drawPark(c); break;
            default: drawMeadow(c);
        }
        box(c, 0, 698, 600, 12, theme == Theme.SEA ? 0xFFA9B3A1 : theme == Theme.CITY ? 0xFF9BA9BB : 0xFF9AAF8B);
        box(c, 0, 710, 600, 86, theme == Theme.SEA ? 0xFF2D485F : theme == Theme.CITY ? 0xFF34465F : 0xFF3E5A54);
        box(c, 0, 713, 600, 3, 0xFFA9BCB6);
        if (theme == Theme.SEA) drawCoastalRail(c);
        box(c, 0, 796, 600, 204, theme == Theme.SEA ? 0xFF708C9C : theme == Theme.CITY ? 0xFF42516B : 0xFF3D684D);
        if (theme != Theme.CITY) {
            path.reset(); path.moveTo(0, 810);
            path.cubicTo(95, 794, 145, 837, 242, 818);
            path.cubicTo(350, 790, 432, 838, 600, 809);
            path.lineTo(600, 1000); path.lineTo(0, 1000); path.close();
            paint.setColor(theme == Theme.SEA ? 0xFF78939B : 0xFF64856C); c.drawPath(path, paint);
            path.reset(); path.moveTo(0, 843);
            path.cubicTo(138, 815, 216, 867, 345, 839);
            path.cubicTo(457, 815, 511, 857, 600, 833);
            path.lineTo(600, 1000); path.lineTo(0, 1000); path.close();
            paint.setColor(theme == Theme.SEA ? 0xFF5D7E89 : 0xFF426B52); c.drawPath(path, paint);
            paint.setColor(theme == Theme.SEA ? 0xFF426976 : 0xFF355E47);
            paint.setStrokeWidth(2.2f);
            for (int i = 0; i < 10; i++) {
                float x = 28 + i * 63, y = 813 + i * 17 % 30;
                c.drawLine(x, y + 9, x - 5, y - 4, paint);
                c.drawLine(x, y + 9, x + 3, y - 10, paint);
                c.drawLine(x, y + 9, x + 9, y - 2, paint);
            }
        }
        paint.setShader(new LinearGradient(0, 762, 0, 1000, 0x00203B32,
            theme == Theme.CITY || theme == Theme.SEA ? 0xF01F2C42 : 0xF01B392E, Shader.TileMode.CLAMP));
        c.drawRect(0, 762, 600, 1000, paint); paint.setShader(null);
    }

    private void drawCoastalRail(Canvas c) {
        // Low sandy verge and a continuous guardrail directly above the asphalt.
        path.reset(); path.moveTo(0, 696);
        path.cubicTo(80, 682, 118, 705, 204, 697);
        path.cubicTo(302, 687, 360, 706, 439, 696);
        path.cubicTo(515, 684, 554, 704, 600, 693);
        path.lineTo(600, 710); path.lineTo(0, 710); path.close();
        paint.setColor(0xFFB2B7A4); c.drawPath(path, paint);
        box(c, 0, 698, 600, 4, 0xFFCAD2CF);
        for (int i = 0; i < 17; i++) box(c, i * 40, 698, 4, 15, 0xFFAEBDBE);
        box(c, 0, 709, 600, 2, 0xFF839BA3);
    }

    private void drawMeadow(Canvas c) {
        hill(c, 540, 0xFFABBFD1, 90);
        hill(c, 640, 0xFF90B27E, 85);
        hill(c, 706, 0xFF71945E, 45);
        tree(c, 44, 698, 54, true); tree(c, 430, 694, 24, false); tree(c, 556, 710, 38, false);
    }

    private void drawCity(Canvas c) {
        for (int i = 0; i < 13; i++) {
            float h = 165 + i * 79 % 210;
            box(c, i * 51 - 15, 748 - h, 44, h, 0xFFB2C3D8);
        }
        for (int i = 0; i < 9; i++) {
            float h = 125 + i * 71 % 160, x = i * 79 - 30;
            box(c, x, 748 - h, 66, h, i % 2 == 0 ? 0xFF788FAA : 0xFF607A99);
            for (int row = 0; row < (int)(h / 19) - 1; row++)
                for (int col = 0; col < 4; col++) box(c, x + 9 + col * 13, 758 - h + row * 19, 5, 8, 0xFFB8CFE1);
        }
        for (int i = 0; i < 3; i++) {
            float x = 40 + i * 250;
            box(c, x, 650, 5, 96, 0xFF465D78); box(c, x, 646, 42, 5, 0xFF465D78);
            oval(c, x + 32, 646, 16, 8, 0xFFFFF4D2);
        }
    }

    private void drawMountains(Canvas c) {
        for (int i = 0; i < 4; i++) {
            float x = i * 190 - 80, y = 365 + i % 2 * 48;
            path.reset(); path.moveTo(x - 100, 650); path.lineTo(x + 100, y); path.lineTo(x + 300, 650); path.close();
            paint.setColor(i % 2 == 0 ? 0xFF8DA8A1 : 0xFFA2B9B0); c.drawPath(path, paint);
        }
        hill(c, 650, 0xFF6D9282, 100);
        hill(c, 721, 0xFF507A65, 35);
        for (int i = 0; i < 38; i++) tree(c, i * 18 - 15, 730, 38 + i * 11 % 43, true);
    }

    private void drawSea(Canvas c) {
        hill(c, 547, 0xFF7F9FB5, 60);
        paint.setShader(new LinearGradient(0, 545, 0, 734, 0xFF54A5CD, 0xFF2A6E9B, Shader.TileMode.CLAMP));
        c.drawRect(0, 545, 600, 734, paint); paint.setShader(null);
        ripples(c, 556, 150);
    }

    private void drawWater(Canvas c) {
        hill(c, 574, 0xFFADC3CE, 75);
        for (int i = 0; i < 28; i++) tree(c, i * 24, 622 + i % 3 * 5, 21 + i % 4 * 4, false);
        paint.setShader(new LinearGradient(0, 636, 0, 734, 0xFF8EBBC9, 0xFFB0CCCC, Shader.TileMode.CLAMP));
        c.drawRect(0, 636, 600, 734, paint); paint.setShader(null);
        ripples(c, 643, 75);
    }

    private void ripples(Canvas c, int top, int height) {
        paint.setStrokeWidth(2);
        for (int i = 0; i < 35; i++) {
            paint.setColor(i % 2 == 0 ? 0x66FFFFFF : 0x3380A6B6);
            float x = i * 83 % 600, y = top + i * 23 % height;
            c.drawLine(x, y, x + 15 + i % 4 * 8, y, paint);
        }
    }

    private void drawPark(Canvas c) {
        hill(c, 640, 0xFFA3C49A, 70); hill(c, 697, 0xFF729C64, 35);
        for (int i = 0; i < 8; i++) tree(c, i * 87 - 8, 700 + i % 3 * 12, 34 + i % 3 * 15, false);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (background == null) buildBackground();
        rect.set(0, 0, getWidth(), getHeight());
        if (previewMode) {
            float aspect = getWidth() / (float) Math.max(1, getHeight());
            int height = Math.max(1, Math.min(500, Math.round(SCENE_WIDTH / aspect)));
            int width = Math.max(1, Math.min(SCENE_WIDTH, Math.round(height * aspect)));
            int left = (SCENE_WIDTH - width) / 2;
            int top = 615 - height / 2;
            previewSource.set(left, top, left + width, top + height);
            c.drawBitmap(background, previewSource, rect, paint);
            return;
        }
        c.drawBitmap(background, null, rect, paint);
        c.save(); c.scale(getWidth() / (float) SCENE_WIDTH, getHeight() / (float) SCENE_HEIGHT);
        c.save();
        c.translate((float) Math.sin(phase * Math.PI * 2) * 18, 0);
        clouds(c);
        c.restore();
        // Near objects travel faster than the clouds, giving a quiet sense of depth.
        paint.setColor(0x5588A5B7);
        for (int i = 0; i < 7; i++) {
            float x = i * 110 - (phase * 1320 % 110);
            rect.set(x, 789, x + 25, 791); c.drawRoundRect(rect, 1, 1, paint);
        }
        float offset = phase * 720 % 60;
        for (int i = 0; i < 12; i++) box(c, i * 60 - offset, 753, 29, 3, 0xFFAFBFB7);
        drawCar(c);
        c.restore();
    }

    private void drawCar(Canvas c) {
        c.save();
        // Modern fastback silhouette: rear at left, long hood and grille at right.
        c.translate(185 + (float)Math.sin(phase * Math.PI * 2) * 12,
            728 + (float)Math.sin(phase * Math.PI * 16) * .65f);
        oval(c, -8, 23, 224, 12, 0x22304036);
        oval(c, 12, 25, 184, 7, 0x44304036);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(carBody);
        path.reset(); path.moveTo(8, -7); path.quadTo(12, -13, 32, -15);
        path.cubicTo(50, -27, 57, -42, 79, -43); path.lineTo(106, -42);
        path.cubicTo(121, -40, 132, -24, 145, -16); path.lineTo(184, -12);
        path.quadTo(201, -10, 204, 0); path.lineTo(203, 15); path.quadTo(198, 22, 185, 22);
        path.lineTo(19, 22); path.quadTo(3, 21, 2, 10); path.lineTo(3, -1);
        path.quadTo(3, -5, 8, -7); path.close(); c.drawPath(path, paint);
        paint.setShader(null);
        paint.setColor(0xFF37556D);
        path.reset(); path.moveTo(43, -15); path.lineTo(65, -36); path.lineTo(85, -36);
        path.lineTo(85, -15); path.close(); c.drawPath(path, paint);
        path.reset(); path.moveTo(92, -36); path.lineTo(106, -36); path.lineTo(132, -15);
        path.lineTo(92, -15); path.close(); c.drawPath(path, paint);
        paint.setColor(0xFF9EB9CA); paint.setStrokeWidth(1.4f);
        c.drawLine(48, -17, 70, -33, paint); c.drawLine(96, -33, 119, -20, paint);
        paint.setColor(0xFF8EA4B4); paint.setStrokeWidth(1.2f);
        c.drawLine(28, 1, 178, -1, paint); c.drawLine(28, 13, 184, 13, paint);
        c.drawLine(91, -10, 89, 12, paint); c.drawLine(139, -8, 139, 13, paint);
        paint.setColor(0xDDEAF5FF); paint.setStrokeWidth(2);
        c.drawLine(30, -11, 54, -26, paint); c.drawLine(148, -11, 184, -8, paint);
        box(c, 28, 17, 155, 3, 0xFF718C9E);
        box(c, 101, -6, 13, 2, 0xFF708A9D); box(c, 45, -6, 12, 2, 0xFF708A9D);
        rect.set(131, -19, 146, -12); paint.setColor(0xFF36576D); c.drawRoundRect(rect, 3, 3, paint);
        // Distinctive front running light, lower dark grille and red rear light.
        box(c, 177, -8, 24, 4, 0xFFF7FCFF);
        box(c, 197, -1, 5, 14, 0xFF273F53);
        box(c, 184, 13, 17, 4, 0xFF52697A);
        box(c, 5, -7, 5, 11, 0xFFD86A69);
        box(c, 9, -8, 15, 3, 0xFFBD5F62);
        for (int wheel = 0; wheel < 2; wheel++) {
            int x = wheel == 0 ? 42 : 158;
            oval(c, x - 16, 4, 32, 32, 0xFF253945);
            oval(c, x - 11, 9, 22, 22, 0xFFA5B7C3);
            c.save(); c.rotate(phase * 4320, x, 20);
            paint.setColor(0xFF516E83); paint.setStrokeWidth(2);
            for (int spoke = 0; spoke < 5; spoke++) {
                c.drawLine(x, 20, x, 10, paint); c.rotate(72, x, 20);
            }
            c.restore();
            oval(c, x - 3, 17, 6, 6, 0xFFD5E0E6);
        }
        c.restore();
    }

    private void releaseBackground() {
        // Let the renderer release the previous frame before the bitmap is collected.
        background = null;
    }
    private void syncAnimation() {
        if (animator == null) return;
        if (motionAllowed && isAttachedToWindow() && isShown()
                && getWindowVisibility() == VISIBLE && ValueAnimator.areAnimatorsEnabled()) {
            if (!animator.isStarted()) animator.start();
        } else animator.cancel();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); syncAnimation(); }
    @Override protected void onVisibilityChanged(View changedView, int visibility) { super.onVisibilityChanged(changedView, visibility); syncAnimation(); }
    @Override protected void onDetachedFromWindow() { animator.cancel(); releaseBackground(); super.onDetachedFromWindow(); }
}
