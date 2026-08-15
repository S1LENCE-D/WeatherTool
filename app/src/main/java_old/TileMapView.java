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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RainViewer 降雨雷达瓦片视图。
 * 瓦片 URL: {host}{path}/256/{z}/{x}/{y}/{color}/{smooth}_{snow}.png
 * 帧动画 300ms/帧；拖动 + 双指缩放由 PanZoomView 提供。
 */
public class TileMapView extends PanZoomView {

    public interface Listener {
        void onFrameChanged(int index, int total, String timeLabel);
        void onLoadState(boolean ok, String msg);
    }

    private static final int TILE = 256;
    private static final int MAX_CACHE = 60;   // 60 张 256px 瓦片 ≈ 15MB，防低端机 OOM

    private final Paint paint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Map<String, Bitmap> cache =
            new LinkedHashMap<String, Bitmap>(MAX_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    return size() > MAX_CACHE;
                }
            };
    private final Set<String> inflight =
            Collections.synchronizedSet(new HashSet<String>());

    private String host = "";
    private final List<String> paths = new ArrayList<String>();
    private final List<Long> times = new ArrayList<Long>();
    private int frame = 0;
    private boolean playing = true;
    private Listener listener;

    public TileMapView(Context c) {
        super(c);
        setBackgroundColor(0xFF0B1220);
        paint.setFilterBitmap(true);
        gridPaint.setColor(0x12000000);
        setCenter(35.0, 105.0);
        tileZoom = 3;
        scale = 1.7f;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** 传入 RainViewer API 的 host 与帧 path / time 列表（past + nowcast） */
    public void setFrames(String host, List<String> past, List<Long> pastTimes,
                          List<String> nowcast, List<Long> nowTimes) {
        if (pool.isShutdown()) return;
        this.host = host;
        paths.clear();
        times.clear();
        paths.addAll(past);
        times.addAll(pastTimes);
        paths.addAll(nowcast);
        times.addAll(nowTimes);
        frame = past.isEmpty() ? 0 : past.size() - 1;   // 从最新一帧开始
        cache.clear();
        inflight.clear();
        if (paths.isEmpty()) {
            if (listener != null) listener.onLoadState(false, "暂无雷达数据");
            return;
        }
        if (listener != null) listener.onLoadState(true, null);
        notifyFrame();
        invalidate();
        ui.removeCallbacks(ticker);
        ui.postDelayed(ticker, 300);
    }

    public void setPlaying(boolean p) {
        playing = p;
    }

    public boolean isPlaying() {
        return playing;
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (playing && paths.size() > 1) {
                frame = (frame + 1) % paths.size();
                invalidate();
                notifyFrame();
            }
            ui.postDelayed(this, 300);
        }
    };

    private void notifyFrame() {
        if (listener == null || frame >= paths.size()) return;
        long t = frame < times.size() ? times.get(frame) : 0L;
        String label;
        if (t > 0) {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            label = f.format(new Date(t * 1000L));
        } else {
            label = "预报";
        }
        listener.onFrameChanged(frame, paths.size(), label);
    }

    @Override
    protected void onDrawWorld(Canvas canvas) {
        if (paths.isEmpty() || pool.isShutdown()) return;
        int x0 = (int) Math.floor(visibleWorldLeft() / TILE);
        int x1 = (int) Math.ceil(visibleWorldRight() / TILE);
        int y0 = (int) Math.floor(visibleWorldTop() / TILE);
        int y1 = (int) Math.ceil(visibleWorldBottom() / TILE);
        int max = 1 << tileZoom;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (x < 0 || y < 0 || x >= max || y >= max) continue;
                String key = frame + "_" + x + "_" + y;
                Bitmap b = cache.get(key);
                if (b != null) {
                    canvas.drawBitmap(b, x * TILE, y * TILE, paint);
                } else {
                    canvas.drawRect(x * TILE, y * TILE,
                            (x + 1) * TILE, (y + 1) * TILE, gridPaint);
                    requestTile(x, y);
                }
            }
        }
    }

    private void requestTile(final int x, final int y) {
        if (pool.isShutdown()) return;
        final String key = frame + "_" + x + "_" + y;
        if (inflight.contains(key)) return;
        inflight.add(key);
        try {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    Bitmap b = null;
                    try {
                        String url = host + paths.get(frame) + "/256/" + tileZoom
                                + "/" + x + "/" + y + "/2/1_0.png";
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(8000);
                        c.setRequestProperty("User-Agent", "WeatherTool/1.0");
                        InputStream in = c.getInputStream();
                        b = BitmapFactory.decodeStream(in);
                        in.close();
                        c.disconnect();
                    } catch (Exception ignored) {
                    }
                    inflight.remove(key);
                    if (b != null) {
                        final Bitmap fb = b;
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                if (pool.isShutdown()) return;   // 页面已退出，丢弃
                                cache.put(key, fb);
                                invalidate();
                            }
                        });
                    }
                }
            });
        } catch (Exception ignored) {
            inflight.remove(key);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        listener = null;
        ui.removeCallbacks(ticker);
        pool.shutdownNow();
    }
}
