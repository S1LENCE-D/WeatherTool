package com.simpleweather.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 卫星云图：中央气象台风云二号（FY-2G）中国区域红外云图。
 * 数据源 image.nmc.cn（国内直连、免费、无需 key），UTC 整点一帧，失败自动向前回退。
 * 替换原向日葵 8 号（himawari8.nict.go.jp，国内网络不可达导致云图不显示）。
 */
public class CloudMapView extends PanZoomView {

    public interface Listener {
        void onReady(boolean ok, String msg);
    }

    // 例：https://image.nmc.cn/product/2026/08/09/WXBL/medium/SEVP_NSMC_WXBL_FY2G_ECN_ACHN_LNO_PY_20260809060000000.JPG
    private static final String BASE =
            "https://image.nmc.cn/product/%04d/%02d/%02d/WXBL/medium/"
          + "SEVP_NSMC_WXBL_FY2G_ECN_ACHN_LNO_PY_%04d%02d%02d%02d0000000.JPG";

    private final Paint paint = new Paint();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Bitmap image;
    private Listener listener;
    private boolean loading = false;
    private volatile String frameTime = "";

    public CloudMapView(Context c) {
        super(c);
        setBackgroundColor(0xFF081018);
        paint.setFilterBitmap(true);
        setCenter(35.0, 105.0);
        tileZoom = 3;
        scale = 1.5f;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** 拍摄时间（北京时间），加载成功后可用 */
    public String frameTime() {
        return frameTime;
    }

    public void load(final double lat, final double lng) {
        if (loading) return;
        loading = true;
        setCenter(lat, lng);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap b = null;
                String msg = null;
                try {
                    // 从当前 UTC 整点向前回退找可用帧（产品生成有延迟，最多回退 10 小时）
                    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    String url = null;
                    long ts = 0;
                    for (int i = 0; i < 10; i++) {
                        String u = cloudUrl(c);
                        if (frameExists(u)) {
                            url = u;
                            ts = c.getTimeInMillis();
                            break;
                        }
                        c.add(Calendar.HOUR_OF_DAY, -1);
                    }
                    if (url == null) {
                        msg = "云图暂时不可用";
                    } else {
                        b = fetch(url);
                        if (b == null) {
                            msg = "云图下载失败";
                        } else {
                            frameTime = formatBjt(ts);
                        }
                    }
                } catch (Exception e) {
                    msg = "云图加载失败";
                }
                final Bitmap fb = b;
                final String fmsg = msg;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        if (fb != null) {
                            image = fb;
                            invalidate();
                            if (listener != null) listener.onReady(true, null);
                        } else {
                            if (listener != null) listener.onReady(false, fmsg);
                        }
                    }
                });
            }
        }).start();
    }

    /** 构造 FY-2G 中国区域红外云图 URL（UTC 整点帧） */
    private String cloudUrl(Calendar c) {
        int y = c.get(Calendar.YEAR);
        int mo = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return String.format(Locale.US, BASE, y, mo, d, y, mo, d, h);
    }

    private boolean frameExists(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "WeatherTool/1.0");
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 下载并按需降采样（防低端机 OOM） */
    private Bitmap fetch(String url) {
        HttpURLConnection c = null;
        try {
            // 第一遍只读边界
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "WeatherTool/1.0");
            InputStream in = c.getInputStream();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            c.disconnect();
            c = null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 1600
                    && o.outHeight / (sample * 2) >= 1600) {
                sample *= 2;
            }
            // 第二遍正式解码
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "WeatherTool/1.0");
            InputStream in2 = c.getInputStream();
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeStream(in2, null, o2);
            in2.close();
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String formatBjt(long ms) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
        c.setTimeInMillis(ms);
        return String.format(Locale.US, "%02d-%02d %02d:00",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY));
    }

    @Override
    protected void onDrawWorld(Canvas canvas) {
        if (image != null) {
            canvas.drawBitmap(image, 0, 0, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        listener = null;
    }
}
