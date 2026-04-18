package io.github.levlandon.numai_plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class AvatarCropImageView extends ImageView {
    private final Matrix drawMatrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private final RectF cropRect = new RectF();
    private final RectF imageRect = new RectF();
    private final Path overlayPath = new Path();
    private final Paint overlayPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap sourceBitmap;
    private ScaleGestureDetector scaleGestureDetector;
    private float minScale = 1f;
    private float maxScale = 5f;
    private float lastX;
    private float lastY;
    private int activePointerId = -1;
    private boolean dragging;

    public AvatarCropImageView(Context context) {
        super(context);
        init(context);
    }

    public AvatarCropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AvatarCropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        overlayPaint.setColor(0x66000000);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(2));
        borderPaint.setColor(0xFF111111);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    public void setCropBitmap(Bitmap bitmap) {
        sourceBitmap = bitmap;
        setImageBitmap(bitmap);
        post(new Runnable() {
            public void run() {
                resetImageMatrix();
            }
        });
    }

    public Bitmap getCroppedBitmap() {
        if (sourceBitmap == null) {
            return null;
        }
        Matrix inverse = new Matrix();
        if (!drawMatrix.invert(inverse)) {
            return null;
        }
        RectF bitmapCrop = new RectF();
        inverse.mapRect(bitmapCrop, cropRect);

        float left = clamp(bitmapCrop.left, 0f, sourceBitmap.getWidth());
        float top = clamp(bitmapCrop.top, 0f, sourceBitmap.getHeight());
        float right = clamp(bitmapCrop.right, 0f, sourceBitmap.getWidth());
        float bottom = clamp(bitmapCrop.bottom, 0f, sourceBitmap.getHeight());

        int width = Math.max(1, Math.round(right - left));
        int height = Math.max(1, Math.round(bottom - top));
        int size = Math.min(width, height);
        int cropLeft = Math.max(0, Math.round(left + (width - size) / 2f));
        int cropTop = Math.max(0, Math.round(top + (height - size) / 2f));
        if (cropLeft + size > sourceBitmap.getWidth()) {
            cropLeft = sourceBitmap.getWidth() - size;
        }
        if (cropTop + size > sourceBitmap.getHeight()) {
            cropTop = sourceBitmap.getHeight() - size;
        }
        return Bitmap.createBitmap(sourceBitmap, cropLeft, cropTop, size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateCropRect(w, h);
        resetImageMatrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cropRect.isEmpty()) {
            return;
        }
        overlayPath.reset();
        overlayPath.setFillType(Path.FillType.EVEN_ODD);
        overlayPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        overlayPath.addCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width() / 2f, Path.Direction.CCW);
        canvas.drawPath(overlayPath, overlayPaint);
        canvas.drawCircle(cropRect.centerX(), cropRect.centerY(), cropRect.width() / 2f, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (sourceBitmap == null) {
            return super.onTouchEvent(event);
        }

        scaleGestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleGestureDetector.isInProgress() && dragging) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex >= 0) {
                        float x = event.getX(pointerIndex);
                        float y = event.getY(pointerIndex);
                        drawMatrix.postTranslate(x - lastX, y - lastY);
                        constrainImage();
                        setImageMatrix(drawMatrix);
                        invalidate();
                        lastX = x;
                        lastY = y;
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int pointerId = event.getPointerId(event.getActionIndex());
                if (pointerId == activePointerId) {
                    int newIndex = event.getPointerCount() > 1 ? (event.getActionIndex() == 0 ? 1 : 0) : -1;
                    if (newIndex >= 0) {
                        activePointerId = event.getPointerId(newIndex);
                        lastX = event.getX(newIndex);
                        lastY = event.getY(newIndex);
                    } else {
                        activePointerId = -1;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                activePointerId = -1;
                break;
        }
        return true;
    }

    private void updateCropRect(int width, int height) {
        float inset = dpToPx(24);
        float availableWidth = width - inset * 2f;
        float availableHeight = height - inset * 2f;
        float size = Math.min(availableWidth, availableHeight);
        float left = (width - size) / 2f;
        float top = (height - size) / 2f;
        cropRect.set(left, top, left + size, top + size);
    }

    private void resetImageMatrix() {
        if (sourceBitmap == null || getWidth() == 0 || getHeight() == 0 || cropRect.isEmpty()) {
            return;
        }
        drawMatrix.reset();
        float scale = Math.max(cropRect.width() / sourceBitmap.getWidth(), cropRect.height() / sourceBitmap.getHeight());
        minScale = scale;
        maxScale = scale * 5f;
        float scaledWidth = sourceBitmap.getWidth() * scale;
        float scaledHeight = sourceBitmap.getHeight() * scale;
        float dx = cropRect.centerX() - scaledWidth / 2f;
        float dy = cropRect.centerY() - scaledHeight / 2f;
        drawMatrix.postScale(scale, scale);
        drawMatrix.postTranslate(dx, dy);
        setImageMatrix(drawMatrix);
        invalidate();
    }

    private void constrainImage() {
        if (sourceBitmap == null) {
            return;
        }
        imageRect.set(0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight());
        drawMatrix.mapRect(imageRect);

        float dx = 0f;
        float dy = 0f;

        if (imageRect.width() <= cropRect.width()) {
            dx = cropRect.centerX() - imageRect.centerX();
        } else if (imageRect.left > cropRect.left) {
            dx = cropRect.left - imageRect.left;
        } else if (imageRect.right < cropRect.right) {
            dx = cropRect.right - imageRect.right;
        }

        if (imageRect.height() <= cropRect.height()) {
            dy = cropRect.centerY() - imageRect.centerY();
        } else if (imageRect.top > cropRect.top) {
            dy = cropRect.top - imageRect.top;
        } else if (imageRect.bottom < cropRect.bottom) {
            dy = cropRect.bottom - imageRect.bottom;
        }

        if (dx != 0f || dy != 0f) {
            drawMatrix.postTranslate(dx, dy);
        }
    }

    private float getCurrentScale() {
        drawMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float currentScale = getCurrentScale();
            float targetScale = currentScale * detector.getScaleFactor();
            targetScale = clamp(targetScale, minScale, maxScale);
            float factor = targetScale / currentScale;
            drawMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
            constrainImage();
            setImageMatrix(drawMatrix);
            invalidate();
            return true;
        }
    }
}
