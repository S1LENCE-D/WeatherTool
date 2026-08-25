package com.simpleweather.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/**
 * v9.104.2：自绘壁纸裁剪界面——单指拖动 / 双指缩放 / 90° 旋转，确认后按裁剪框保存。
 * 读取 filesDir/m3_bg.jpg（选图时已复制），裁剪结果写回同一路径并更新 Theme.m3BgPath。
 */
public class CropActivity extends Activity {

    private CropView cropView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        cropView = new CropView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);
        cropView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(cropView);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(16), dp(12), dp(16), dp(24));
        bar.setBackgroundColor(0xFF101418);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        btnLp.leftMargin = dp(6);
        btnLp.rightMargin = dp(6);

        TextView cancel = makeBtn("取消", 0xFF5A6B7A, new View.OnClickListener() {
            @Override public void onClick(View v) { setResult(RESULT_CANCELED); finish(); }
        });
        bar.addView(cancel, btnLp);

        TextView rotate = makeBtn("旋转 90°", 0xFF3D6B99, new View.OnClickListener() {
            @Override public void onClick(View v) { cropView.rotate(); }
        });
        bar.addView(rotate, btnLp);

        TextView ok = makeBtn("确认", 0xFF2E7D4F, new View.OnClickListener() {
            @Override public void onClick(View v) { doCropAndFinish(); }
        });
        bar.addView(ok, btnLp);

        root.addView(bar);
        setContentView(root);

        File f = new File(getFilesDir(), "m3_bg.jpg");
        if (f.exists()) {
            Bitmap bm = loadSampled(f.getAbsolutePath(),
                    getResources().getDisplayMetrics().widthPixels * 2,
                    getResources().getDisplayMetrics().heightPixels * 2);
            if (bm != null) cropView.setImage(bm);
        }
        if (cropView.getSrc() == null) {
            Toast.makeText(this, "壁纸图片加载失败", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void doCropAndFinish() {
        Bitmap out = cropView.doCrop();
        if (out == null) {
            Toast.makeText(this, "裁剪失败，请调整后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File f = new File(getFilesDir(), "m3_bg.jpg");
            FileOutputStream fos = new FileOutputStream(f);
            out.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            Theme.setM3BgPath(this, f.getAbsolutePath());
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private TextView makeBtn(String t, int color, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(t);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable gd =
                new android.graphics.drawable.GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(12));
        b.setBackground(gd);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Bitmap loadSampled(String path, int maxW, int maxH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int bs = 1;
        while (o.outWidth / bs > maxW || o.outHeight / bs > maxH) bs *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = bs;
        try {
            return BitmapFactory.decodeFile(path, o);
        } catch (Throwable t) {
            return null;
        }
    }
}
