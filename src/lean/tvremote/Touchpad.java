package lean.tvremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Trackpad: drag to move, tap to click, drag the right strip to scroll.
 * Movement is batched to one frame per display refresh so the pointer socket isn't flooded.
 */
final class Touchpad extends View {
    interface Pointer {
        void move(int dx, int dy);
        void click();
        void scroll(int dx, int dy);
    }

    /**
     * Pointer units per dp of finger travel at slow speed; faster swipes accelerate up to
     * {@link #MAX_GAIN}. Measured on a webOS 24 TV: a full-width slow swipe crosses about half the screen.
     */
    private static final float BASE_GAIN = 2.4f;
    private static final float MAX_GAIN = 6f;
    /** Finger speed (dp/ms) at which acceleration reaches MAX_GAIN. */
    private static final float FAST = 2.5f;
    /**
     * The TV treats every scroll frame as one wheel notch whatever its dx/dy (measured on webOS 24),
     * so send one notch per this many dp of scroll travel.
     */
    private static final float SCROLL_STEP_DP = 20f;
    /** Finger-sized target, including the margin around the visible scroll track. */
    private static final float SCROLL_AREA_DP = 64f;
    private static final long TAP_MS = 250;

    private final Pointer pointer;
    private final int scrollSign;
    private final float density;
    private final int slop;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint touch = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrollLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrollTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final RectF scrollBounds = new RectF();

    private float lastX, lastY, downX, downY, touchX = -1, touchY;
    private long downTime, lastTime;
    private boolean moved, scrolling, scrollStrip;
    private float pendX, pendY, pendScrollX, pendScrollY;
    private boolean flushPosted;

    private final Runnable flush = this::flush;

    private void flush() {
        flushPosted = false;
        int dx = (int) pendX, dy = (int) pendY;
        if (dx != 0 || dy != 0) {
            pendX -= dx;
            pendY -= dy;
            pointer.move(dx, dy);
        }
        // Natural scrolling moves content with the fingers: upward travel sends wheel-down.
        while (Math.abs(pendScrollY) >= SCROLL_STEP_DP) {
            int notch = pendScrollY > 0 ? 1 : -1;
            pendScrollY -= notch * SCROLL_STEP_DP;
            pointer.scroll(0, notch * scrollSign);
        }
        while (Math.abs(pendScrollX) >= SCROLL_STEP_DP) {
            int notch = pendScrollX > 0 ? 1 : -1;
            pendScrollX -= notch * SCROLL_STEP_DP;
            pointer.scroll(notch * scrollSign, 0);
        }
    }

    Touchpad(Context ctx, int surface, int hintColor, boolean naturalScrolling, Pointer pointer) {
        super(ctx);
        this.pointer = pointer;
        scrollSign = naturalScrolling ? 1 : -1;
        density = getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        fill.setColor(surface);
        hint.setColor(hintColor);
        hint.setTextAlign(Paint.Align.CENTER);
        hint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
        touch.setColor(0x22FFFFFF);
        scrollLine.setColor(hintColor);
        scrollLine.setStrokeWidth(4 * density);
        scrollLine.setStrokeCap(Paint.Cap.ROUND);
        scrollTrack.setColor(hintColor);
        setContentDescription("Touchpad. Drag to move the TV pointer, tap to click. Slide up or down on the right edge to scroll; two-finger scrolling also works.");
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? w : MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas c) {
        bounds.set(0, 0, getWidth(), getHeight());
        float r = 28 * density;
        c.drawRoundRect(bounds, r, r, fill);
        float cy = getHeight() / 2f;
        float centerX = (getWidth() - SCROLL_AREA_DP * density) / 2f;
        c.drawText("Drag to move · Tap to click", centerX, cy - 4 * density, hint);
        c.drawText("Slide right edge to scroll", centerX, cy + 16 * density, hint);
        // A recessed strip and rounded grip distinguish scrolling from the pointer area.
        boolean active = scrollStrip && touchX >= 0;
        float lineX = getWidth() - SCROLL_AREA_DP * density / 2f;
        float top = 24 * density, bottom = getHeight() - 24 * density;
        scrollTrack.setAlpha(active ? 55 : 26);
        scrollBounds.set(lineX - 24 * density, top, lineX + 24 * density, bottom);
        c.drawRoundRect(scrollBounds, 24 * density, 24 * density, scrollTrack);
        scrollLine.setAlpha(active ? 110 : 55);
        c.drawLine(lineX, top + 12 * density, lineX, bottom - 12 * density, scrollLine);
        float halfGrip = 40 * density;
        float gripY = active ? Math.max(top + halfGrip + 8 * density,
                Math.min(bottom - halfGrip - 8 * density, touchY)) : cy;
        scrollLine.setAlpha(active ? 255 : 200);
        scrollBounds.set(lineX - 10 * density, gripY - halfGrip,
                lineX + 10 * density, gripY + halfGrip);
        c.drawRoundRect(scrollBounds, 10 * density, 10 * density, scrollLine);
        if (touchX >= 0 && !scrollStrip) c.drawCircle(touchX, touchY, 28 * density, touch);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        long now = SystemClock.uptimeMillis();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                removeCallbacks(flush);
                flush();
                pendX = pendY = 0;
                pendScrollX = pendScrollY = 0;
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                downTime = lastTime = now;
                moved = false;
                // A generous hit area around the line; keep this mode for the whole gesture.
                scrollStrip = lastX >= getWidth() - SCROLL_AREA_DP * density;
                scrolling = scrollStrip;
                setTouch(lastX, lastY);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                scrolling = true;
                moved = true;
                lastX = centroidX(e);
                lastY = centroidY(e);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                // Re-anchor on the remaining fingers so the pointer doesn't jump.
                lastX = centroidX(e, e.getActionIndex());
                lastY = centroidY(e, e.getActionIndex());
                return true;
            case MotionEvent.ACTION_MOVE: {
                float x = e.getPointerCount() > 1 ? centroidX(e) : e.getX();
                float y = e.getPointerCount() > 1 ? centroidY(e) : e.getY();
                if (!moved && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) moved = true;
                float dx = (x - lastX) / density, dy = (y - lastY) / density;
                long dt = Math.max(1, now - lastTime);
                lastX = x;
                lastY = y;
                lastTime = now;
                setTouch(e.getX(), e.getY());
                if (!moved) return true;
                if (scrolling) {
                    if (!scrollStrip) pendScrollX += dx;
                    pendScrollY += dy;
                } else {
                    float speed = (float) Math.hypot(dx, dy) / dt;
                    float gain = BASE_GAIN + (MAX_GAIN - BASE_GAIN) * Math.min(1f, speed / FAST);
                    pendX += dx * gain;
                    pendY += dy * gain;
                }
                if (!flushPosted) {
                    flushPosted = true;
                    postOnAnimation(flush);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                removeCallbacks(flush);
                flush();
                if (!scrolling && !moved && now - downTime < TAP_MS) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    pointer.click();
                }
                setTouch(-1, 0);
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(flush);
                flushPosted = false;
                pendX = pendY = pendScrollX = pendScrollY = 0;
                setTouch(-1, 0);
                return true;
            default:
                return true;
        }
    }

    private void setTouch(float x, float y) {
        touchX = x;
        touchY = y;
        invalidate();
    }

    private static float centroidX(MotionEvent e) {
        return centroidX(e, -1);
    }

    private static float centroidY(MotionEvent e) {
        return centroidY(e, -1);
    }

    private static float centroidX(MotionEvent e, int skip) {
        float sum = 0;
        int n = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) continue;
            sum += e.getX(i);
            n++;
        }
        return n > 0 ? sum / n : e.getX();
    }

    private static float centroidY(MotionEvent e, int skip) {
        float sum = 0;
        int n = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) continue;
            sum += e.getY(i);
            n++;
        }
        return n > 0 ? sum / n : e.getY();
    }
}
