package com.byd.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.byd.assistant.service.AssistantForegroundService;
import com.byd.assistant.updater.AppUpdateManager;

public class MainActivity extends Activity {
    private static final int REQ_RECORD_AUDIO = 201;
    private static final int REQ_OVERLAY_PERMISSION = 202;

    private TextView statusTextView;
    private Button toggleServiceButton;
    private TextView versionTextView;
    private Button checkUpdateButton;
    private TextView updateStatusTextView;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.tv_status);
        toggleServiceButton = findViewById(R.id.btn_toggle_service);
        versionTextView = findViewById(R.id.tv_version);
        checkUpdateButton = findViewById(R.id.btn_check_update);
        updateStatusTextView = findViewById(R.id.tv_update_status);

        initUpdateSection();
        checkPermissions();

        toggleServiceButton.setOnClickListener(v -> {
            if (!isServiceRunning) {
                startAssistant();
            } else {
                stopAssistant();
            }
        });

        if (getIntent() != null && getIntent().getBooleanExtra("CHECK_UPDATES", false)) {
            triggerUpdateCheck();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("CHECK_UPDATES", false)) {
            triggerUpdateCheck();
        }
    }

    private void initUpdateSection() {
        String currentVersion = AppUpdateManager.getInstalledVersion(this);
        if (versionTextView != null) {
            versionTextView.setText("الإصدار الحالي: v" + currentVersion);
        }

        if (checkUpdateButton != null) {
            checkUpdateButton.setOnClickListener(v -> triggerUpdateCheck());
        }
    }

    private void triggerUpdateCheck() {
        if (updateStatusTextView != null) {
            updateStatusTextView.setText("جارٍ فحص التحديثات من GitHub...");
        }
        if (checkUpdateButton != null) {
            checkUpdateButton.setEnabled(false);
        }

        AppUpdateManager.checkForUpdates(this, new AppUpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(String latestVersion, String downloadUrl, String releaseNotes) {
                if (checkUpdateButton != null) {
                    checkUpdateButton.setEnabled(true);
                }
                if (updateStatusTextView != null) {
                    updateStatusTextView.setText("يوجد تحديث جديد: " + latestVersion);
                }

                showUpdateDialog(latestVersion, downloadUrl, releaseNotes);
            }

            @Override
            public void onUpToDate(String currentVersion) {
                if (checkUpdateButton != null) {
                    checkUpdateButton.setEnabled(true);
                }
                if (updateStatusTextView != null) {
                    updateStatusTextView.setText("أنت على أحدث إصدار! (" + currentVersion + ")");
                }
                Toast.makeText(MainActivity.this, "أنت على أحدث إصدار!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                if (checkUpdateButton != null) {
                    checkUpdateButton.setEnabled(true);
                }
                if (updateStatusTextView != null) {
                    updateStatusTextView.setText("تعذر فحص التحديثات: " + errorMessage);
                }
                Toast.makeText(MainActivity.this, "تعذر فحص التحديثات", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showUpdateDialog(String latestVersion, String downloadUrl, String releaseNotes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("تحديث جديد متوفر");

        StringBuilder message = new StringBuilder();
        message.append("يتوفر إصدار جديد من التطبيق (").append(latestVersion).append(").\n\n");
        if (releaseNotes != null && !releaseNotes.trim().isEmpty()) {
            message.append("ملاحظات الإصدار:\n").append(releaseNotes.trim()).append("\n\n");
        }
        message.append("هل ترغب في تنزيل التحديث وتثبيته الآن؟");

        builder.setMessage(message.toString());
        builder.setPositiveButton("تحميل وتثبيت الآن", (dialog, which) -> {
            if (updateStatusTextView != null) {
                updateStatusTextView.setText("جارٍ تنزيل ملف التحديث...");
            }
            Toast.makeText(MainActivity.this, "جارٍ بدء تحميل التحديث...", Toast.LENGTH_SHORT).show();
            AppUpdateManager.downloadAndInstall(MainActivity.this, downloadUrl);
        });
        builder.setNegativeButton("لاحقاً", (dialog, which) -> dialog.dismiss());
        builder.setCancelable(true);
        builder.show();
    }

    private void checkPermissions() {
        // 1. Overlay permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
                Toast.makeText(this, "يرجى منح إذن الظهور فوق التطبيقات للشاشة", Toast.LENGTH_LONG).show();
            }
        }

        // 2. Microphone permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            }
        }
    }

    private void startAssistant() {
        Intent serviceIntent = new Intent(this, AssistantForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        isServiceRunning = true;
        updateUiState();
        Toast.makeText(this, "تم تشغيل المساعد الصوتي العائم", Toast.LENGTH_SHORT).show();
    }

    private void stopAssistant() {
        Intent serviceIntent = new Intent(this, AssistantForegroundService.class);
        stopService(serviceIntent);
        isServiceRunning = false;
        updateUiState();
        Toast.makeText(this, "تم إيقاف المساعد الصوتي", Toast.LENGTH_SHORT).show();
    }

    private void updateUiState() {
        if (isServiceRunning) {
            statusTextView.setText("الحالة: المساعد نشط ويعمل كزر عائم");
            toggleServiceButton.setText("إيقاف المساعد الصوتي");
            toggleServiceButton.setBackgroundColor(0xFFE11D48); // Rose red
        } else {
            statusTextView.setText("الحالة: المساعد متوقف");
            toggleServiceButton.setText("تشغيل المساعد الصوتي لسيارة BYD");
            toggleServiceButton.setBackgroundColor(0xFF0284C7); // Sky blue
        }
    }
}
