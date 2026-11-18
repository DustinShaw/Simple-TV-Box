package win.movie8.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    // Posters are only ~380px wide on a 1920px TV grid. Decoding the source at full
    // resolution blew the heap on the TV box (a single 23MB allocation). Cap the decode
    // to this many pixels on the long edge and keep the cache bounded accordingly.
    private static final int MAX_DECODE_DIMENSION = 640;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024) {
        protected int sizeOf(String key, Bitmap value) { return value.getByteCount() / 1024; }
    };
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(3);
    private ImageLoader() { }
    public static void load(final ImageView view, final String url) {
        view.setTag(url); view.setImageDrawable(null);
        if (url == null || url.isEmpty()) return;
        Bitmap cached = CACHE.get(url); if (cached != null) { view.setImageBitmap(cached); return; }
        WORKERS.execute(new Runnable() { public void run() {
            HttpURLConnection c = null;
            try {
                c = TlsCompat.open(new URL(url));
                c.setConnectTimeout(8000); c.setReadTimeout(12000); c.setRequestProperty("user-agent", "Mozilla/5.0 (Linux; Android 5.0) SimpleTVBox/1.1");
                Bitmap image;
                try (InputStream in = c.getInputStream()) {
                    // First pass: read intrinsic dimensions without allocating the bitmap.
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(in, null, bounds);
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Poster size unavailable");
                    int sample = Math.max(1, (Math.max(bounds.outWidth, bounds.outHeight) + MAX_DECODE_DIMENSION - 1) / MAX_DECODE_DIMENSION);
                    // Second pass: decode a downscaled bitmap. Reopen through the same TLS
                    // compatibility path because decodeStream consumed the first body.
                    c.disconnect();
                    c = TlsCompat.open(new URL(url));
                    c.setConnectTimeout(8000); c.setReadTimeout(12000);
                    c.setRequestProperty("user-agent", "Mozilla/5.0 (Linux; Android 5.0) SimpleTVBox/1.1");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sample;
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    try (InputStream second = c.getInputStream()) {
                        image = BitmapFactory.decodeStream(second, null, options);
                    }
                    if (image == null) throw new IOException("Poster decode returned null");
                }
                CACHE.put(url, image);
                view.post(new Runnable() { public void run() {
                    boolean visible = url.equals(view.getTag());
                    if (visible) view.setImageBitmap(image);
                } });
            } catch (Exception ignored) {
                // Network/decode failures leave the poster background visible.
            } catch (OutOfMemoryError ignored) {
                // Never let a single large image take down the whole app.
            } finally { if (c != null) c.disconnect(); }
        }});
    }
}
