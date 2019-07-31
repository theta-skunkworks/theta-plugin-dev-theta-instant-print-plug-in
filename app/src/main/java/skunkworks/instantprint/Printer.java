package skunkworks.instantprint;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import androidx.annotation.NonNull;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Printer implements AutoCloseable {
    private static final String TAG = Printer.class.getSimpleName();

    private static final String ACTION_USB_PERMISSION = "skunkworks.instantprint.ACTION_USB_PERMISSION";

    private static final Charset CHARSET = Charset.forName("UTF-8");

    private static final int BAUDRATE = 38400;

    private static final int BMP_WIDTH = 384;

    private static final int BMP_MAX_HEIGHT = 0xFFFF;

    private static final int BMP_BYTES_PER_LINE = BMP_WIDTH / 8;

    private static final int DEFAULT_TIMEOUT = 10000;

    private static final int MAX_USBFS_BUFFER_SIZE = 16384;

    private UsbSerialPort mPort;

    public Printer(@NonNull final Context context) throws IOException {
        final Context ctx = Objects.requireNonNull(context, "context must not be null.");
        final UsbManager usbManager = context.getSystemService(UsbManager.class);

        final List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        Log.d(TAG, "Available Drivers : " + availableDrivers);

        if (availableDrivers.isEmpty()) {
            throw new IOException("No drivers are available.");
        }

        // Open a connection to the first available driver.
        final UsbSerialDriver driver = availableDrivers.get(0);
        final UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());

        if (connection == null) {
            final PendingIntent intent = PendingIntent.getBroadcast(ctx, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(driver.getDevice(), intent);
            return;
        }

        mPort = driver.getPorts().get(0);
        mPort.open(connection);
        mPort.setParameters(BAUDRATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
    }

    @Override
    public synchronized void close() throws IOException {
        if (mPort != null) {
            mPort.close();
        }
        mPort = null;
    }

    public synchronized void print(@NonNull final byte[] data) throws IOException {
        if (mPort == null) {
            throw new IOException("Printer is not connected.");
        }

        if (data.length % BMP_BYTES_PER_LINE != 0) {
            throw new IllegalArgumentException("The length of the input data must be a multiple of " + BMP_BYTES_PER_LINE);
        }
        final int height = data.length / BMP_BYTES_PER_LINE;
        if (height > BMP_MAX_HEIGHT) {
            throw new IllegalArgumentException("Too large height : " + height);
        }

        final byte m = (byte) 0x65;
        final byte n1 = (byte) ((height & 0xff00) >>> 8);
        final byte n2 = (byte) (height & 0x00ff);

        mPort.write(new byte[]{0x1c, 0x2a, m, n1, n2}, DEFAULT_TIMEOUT);
        for (byte[] chunk : chunks(data)) {
            mPort.write(chunk, DEFAULT_TIMEOUT);
        }
    }

    public synchronized void printQR(@NonNull final QRErrorCorrectionLevel level, @NonNull final String text) throws IOException {
        if (mPort == null) {
            throw new IOException("Printer is not connected.");
        }

        final byte[] data = text.getBytes(CHARSET);
        if (data.length > level.maxLength) {
            throw new IllegalArgumentException("The text is too long to store in the QR code.");
        }

        final byte n1 = level.value;
        final byte n2 = (byte) data.length;

        forward(20);
        mPort.write(new byte[]{0x1d, 0x78, n1, n2}, DEFAULT_TIMEOUT);
        mPort.write(data, DEFAULT_TIMEOUT);
        forward(20);
    }

    public synchronized void forward(final int pixels) throws IOException {
        if (pixels < 0 || 255 < pixels) {
            throw new IllegalArgumentException("Pixels must be in the range 0 to 255, but got " + pixels);
        }

        if (mPort == null) {
            throw new IOException("Printer is not connected.");
        }

        mPort.write(new byte[]{0x1b, 0x4a, (byte) pixels}, DEFAULT_TIMEOUT);
    }

    private static List<byte[]> chunks(byte[] array) {
        final List<byte[]> list = new ArrayList<>();
        for (int from = 0; from < array.length; from += MAX_USBFS_BUFFER_SIZE) {
            final int to = Math.min(array.length, from + MAX_USBFS_BUFFER_SIZE);
            list.add(Arrays.copyOfRange(array, from, to));
        }
        return list;
    }

    public enum QRErrorCorrectionLevel {
        L(0x4c, 154),
        M(0x4d, 122),
        Q(0x51, 86),
        H(0x48, 64);

        final byte value;

        final int maxLength;

        QRErrorCorrectionLevel(int value, int maxLength) {
            this.value = (byte) value;
            this.maxLength = maxLength;
        }
    }
}
