package skunkworks.instantprint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import org.opencv.android.OpenCVLoader;

import java.nio.ByteBuffer;

public final class ImgConv {
    private static final int HEIGHT = 384;
    private static final int WIDTH = HEIGHT * 4;

    public static Bitmap shiftCenter(final Bitmap src) {
        final int width = src.getWidth();
        final int height = src.getHeight();

        final Bitmap left = Bitmap.createBitmap(src, 0, 0, width / 2, height);
        final Bitmap right = Bitmap.createBitmap(src, width / 2, 0, width / 2, height);

        src.recycle();

        final Bitmap dst = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(dst);
        c.drawBitmap(right, 0f, 0f, null);
        c.drawBitmap(left, (float) (width / 2), 0f, null);

        left.recycle();
        right.recycle();

        return dst;
    }

    public static byte[] convert(final Bitmap src) {
        // verify input image
        final String imageSize = src.getWidth() + "x" + src.getHeight();
        if (src.getHeight() < HEIGHT * 2) {
            throw new IllegalArgumentException("The height of input image must be larger than " + HEIGHT + ", but got " + imageSize);
        }
        if (src.getWidth() / src.getHeight() != 2) {
            throw new IllegalArgumentException("The aspect ratio of input image must be 2:1, but got " + imageSize);
        }

        final Matrix rotateMatrix = new Matrix();
        rotateMatrix.postRotate(90.0f);

        final Bitmap resized = Bitmap.createScaledBitmap(src, WIDTH, HEIGHT * 2, false);
        final Bitmap cropped = Bitmap.createBitmap(resized, 0, HEIGHT / 2, WIDTH, HEIGHT);
        final Bitmap rotated = Bitmap.createBitmap(cropped, 0, 0, WIDTH, HEIGHT, rotateMatrix, true);
        final byte[] printable = rgba2printable(rotated);

        resized.recycle();
        cropped.recycle();
        rotated.recycle();

        return printable;
    }

    private static byte[] rgba2printable(final Bitmap src) {
        final ByteBuffer srcBytes = ByteBuffer.allocate(src.getByteCount());
        src.copyPixelsToBuffer(srcBytes);
        return rgba2printable(srcBytes.array(), src.getWidth(), src.getHeight());
    }

    private static native byte[] rgba2printable(byte[] src, int width, int height);

    static {
        OpenCVLoader.initDebug();
        System.loadLibrary("imgconv");
    }

    private ImgConv() {
        throw new AssertionError();
    }
}
