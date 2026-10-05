package com.byd.assistant;

import android.Manifest;
import android.app.Activity;
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

public class MainActivity extends Activity {
    private static final int REQ_RECORD_AUDIO = 201;
    private static final int REQ_OVERLAY_PERMISSION = 202;

    private TextView statusTextView;
    private Button toggleServiceButton;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.tv_status);
        toggleServiceButton = findViewById(R.id.btn_toggle_service);

        checkPermissions();

        toggleServiceButton.setOnClickListener(v -> {
            if (!isServiceRunning) {
                startAssistant();
            } else {
                stopAssistant();
            }
        });
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
