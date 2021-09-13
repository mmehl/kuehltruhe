package de.mehlbous.kuehltruhe;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public static final String REQUIRED_PERMISSIONS = Manifest.permission.CAMERA;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private final int REQUEST_CODE_PERMISSIONS = 10;
    private ExecutorService cameraExecutor;
    private File outputDirectory;
    ImageCapture imageCapture;
    private static final String TAG = "kuehlschrank";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{REQUIRED_PERMISSIONS}, REQUEST_CODE_PERMISSIONS);
        }

        imageCapture = new ImageCapture.Builder().build();
        // Set up the listener for take photo button
        View camera_capture_button = findViewById(R.id.camera_capture_button);
        camera_capture_button.setOnClickListener(view -> this.takePhoto());

        outputDirectory = getOutputDirectory();

        cameraExecutor = Executors.newSingleThreadExecutor();

    }

    private File getOutputDirectory() {
        File[] dirs = getExternalMediaDirs();
        if (dirs.length > 0) {
            File mediaDir = new File(dirs[0], getResources().getString(R.string.app_name));
            mediaDir.mkdirs();
            return mediaDir;
        } else {
            return getFilesDir();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (InterruptedException | ExecutionException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS) == PackageManager.PERMISSION_GRANTED;
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        PreviewView previewView = findViewById(R.id.viewFinder);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview);
    }

    private final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    private void takePhoto() {

        Toast.makeText(this,
                "Taking a photo.",
                Toast.LENGTH_SHORT).show();

        final Context theThis = this;
        // Get a stable reference of the modifiable image capture use case
        if (imageCapture == null) return;

        // Create time-stamped output file to hold the image
        File photoFile = new File(
                outputDirectory,
                new SimpleDateFormat(FILENAME_FORMAT, Locale.US
                ).format(System.currentTimeMillis()) + ".jpg");

        // Create output options object which contains file + metadata
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri savedUri = Uri.fromFile(photoFile);
                        String msg = "Photo capture succeeded: $savedUri";
                        Toast.makeText(theThis, msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);

                    }

                    @Override
                    public void onError(ImageCaptureException exc) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc);
                    }
                });
    }


}
