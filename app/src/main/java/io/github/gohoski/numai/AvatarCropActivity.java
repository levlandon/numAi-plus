package io.github.gohoski.numai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class AvatarCropActivity extends Activity {
    private Uri sourceUri;
    private Bitmap sourceBitmap;
    private AvatarCropImageView cropView;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_crop);

        String uriValue = getIntent().getStringExtra("source_uri");
        if (uriValue == null || uriValue.length() == 0) {
            finish();
            return;
        }
        sourceUri = Uri.parse(uriValue);
        cropView = (AvatarCropImageView) findViewById(R.id.crop_preview);
        loadPreview();

        findViewById(R.id.crop_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                finish();
            }
        });
        findViewById(R.id.crop_done).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                saveCroppedAvatar();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (sourceBitmap != null) {
            sourceBitmap.recycle();
            sourceBitmap = null;
        }
        super.onDestroy();
    }

    private void loadPreview() {
        try {
            sourceBitmap = MainActivity.decodeSampledBitmap(this, sourceUri, 1024, 1024);
            if (sourceBitmap == null) {
                finish();
                return;
            }
            cropView.setCropBitmap(sourceBitmap);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void saveCroppedAvatar() {
        if (sourceBitmap == null) {
            return;
        }
        Bitmap cropped = cropView.getCroppedBitmap();
        if (cropped == null) {
            Toast.makeText(this, R.string.crop_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap circularAvatar = AvatarBitmapHelper.createCircularBitmap(cropped);
        cropped.recycle();
        if (circularAvatar == null) {
            Toast.makeText(this, R.string.crop_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File file = new File(getFilesDir(), "profile_avatar.png");
            FileOutputStream outputStream = new FileOutputStream(file);
            try {
                circularAvatar.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            } finally {
                outputStream.close();
            }
            Intent result = new Intent();
            result.putExtra("avatar_path", file.getAbsolutePath());
            setResult(RESULT_OK, result);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            circularAvatar.recycle();
        }
    }
}
