package com.simpleweather.app;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃捕获：把未捕获异常的堆栈写入应用外部目录 crash.log，
 * 并额外复制一份到公共「下载」目录（Android 11+ 也能直接取到），
 * 便于真机排查（不影响正常崩溃流程）。
 */
public final class CrashCatcher {
    private static boolean installed = false;

    private CrashCatcher() { }

    public static void install(final Context ctx) {
        if (installed) return;
        installed = true;
        final Thread.UncaughtExceptionHandler def =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable ex) {
                        try {
                            String stamp = "=== " + new SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss", Locale.US)
                                    .format(new Date()) + " ===";
                            StringBuilder sb = new StringBuilder(stamp)
                                    .append((char) 10).append("Device: ").append(Build.MANUFACTURER)
                                    .append(" ").append(Build.MODEL)
                                    .append(" SDK ").append(Build.VERSION.SDK_INT)
                                    .append((char) 10).append("Thread: ").append(thread.getName())
                                    .append((char) 10);
                            java.io.StringWriter sw = new java.io.StringWriter();
                            ex.printStackTrace(new PrintWriter(sw));
                            sb.append(sw.toString()).append((char) 10);

                            // ① 应用专属目录（Android/data 下）
                            File dir = ctx.getExternalFilesDir(null);
                            if (dir != null) {
                                writeLog(new File(dir, "crash.log"), sb.toString());
                            }
                            // ② 公共下载目录（用户方便直接取）
                            try {
                                File dl = Environment
                                        .getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS);
                                writeLog(new File(dl, "weathertool_crash.log"),
                                        sb.toString());
                            } catch (Exception ignored) { }
                        } catch (Exception ignored) { }
                        try {
                            Toast.makeText(ctx,
                                    "出了点问题，日志已写入下载目录 weathertool_crash.log",
                                    Toast.LENGTH_LONG).show();
                        } catch (Exception ignored) { }
                        if (def != null) def.uncaughtException(thread, ex);
                    }
                });
    }

    /** 日志文件上限：10MB（v9.81，与 WeatherCache 清理口径一致） */
    static final long LOG_MAX_BYTES = 10L * 1024 * 1024;

    private static void writeLog(File f, String content) {
        try {
            // v9.73/v9.81：每次写入前检查，超过 10MB 直接重写只留最新一条
            //（不依赖 6 小时节流，避免节流窗口内多次崩溃把日志撑大）
            if (f.exists() && f.length() > LOG_MAX_BYTES) {
                f.delete();
            }
            FileWriter fw = new FileWriter(f, true);
            fw.write(content);
            fw.close();
        } catch (Exception ignored) { }
    }
}
