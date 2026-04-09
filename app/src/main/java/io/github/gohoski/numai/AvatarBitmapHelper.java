package io.github.gohoski.numai;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

public final class AvatarBitmapHelper {
    private AvatarBitmapHelper() {}

    public static Bitmap createCircularBitmap(Bitmap source) {
        if (source == null) {
            return null;
        }
        int size = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - size) / 2;
        int top = (source.getHeight() - size) / 2;
        Bitmap squared = Bitmap.createBitmap(source, left, top, size, size);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawARGB(0, 0, 0, 0);
        float radius = size / 2f;
        canvas.drawCircle(radius, radius, radius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(squared, new Rect(0, 0, size, size), new RectF(0, 0, size, size), paint);
        paint.setXfermode(null);

        if (squared != source) {
            squared.recycle();
        }
        return output;
    }
}
