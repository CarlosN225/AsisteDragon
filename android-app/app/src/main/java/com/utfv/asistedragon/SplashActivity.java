package com.utfv.asistedragon;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3000;

    private ImageView imagenLogo;
    private View punto1, punto2, punto3;
    private Handler handlerAnimacion = new Handler(Looper.getMainLooper());
    private int puntosActivo = 0;

    private FirebaseAuth mAuth;

    // ── Launcher para pedir permiso de notificaciones ─────────────────
    private final ActivityResultLauncher<String> permisoNotificaciones =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        // El usuario aceptó o rechazó — en cualquier caso continuamos
                        // La app funciona igual, solo sin notificaciones si rechazó
                        esperarYNavegar();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configurarBarraEstado();
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();

        imagenLogo = findViewById(R.id.imagen_logo);
        punto1     = findViewById(R.id.punto_1);
        punto2     = findViewById(R.id.punto_2);
        punto3     = findViewById(R.id.punto_3);

        iniciarAnimacionLogo();
        iniciarAnimacionPuntos();

        // Pedir permiso de notificaciones en Android 13+ (SDK 33+)
        // Se pide una sola vez — si ya fue aceptado o rechazado antes, no vuelve a aparecer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // No tiene permiso → mostrar el diálogo nativo de Android
                // Esperamos 1.2s para que el splash ya sea visible antes del diálogo
                new Handler(Looper.getMainLooper()).postDelayed(() ->
                                permisoNotificaciones.launch(
                                        android.Manifest.permission.POST_NOTIFICATIONS),
                        1200);
                return; // esperarYNavegar() se llama desde el callback del launcher
            }
        }

        // Android 12 o menos, o ya tiene permiso → flujo normal
        esperarYNavegar();
    }

    // ── Espera el tiempo del splash y navega ──────────────────────────
    private void esperarYNavegar() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            detenerAnimacionPuntos();
            verificarSesion();
        }, SPLASH_DURATION);
    }

    // ══ CONFIGURACIÓN ═════════════════════════════════════════════════

    private void configurarBarraEstado() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.white));
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    // ══ ANIMACIONES ═══════════════════════════════════════════════════

    private void iniciarAnimacionLogo() {
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                0.8f, 1.0f, 0.8f, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        scaleAnimation.setDuration(800);
        scaleAnimation.setFillAfter(true);

        AlphaAnimation fadeAnimation = new AlphaAnimation(0.0f, 1.0f);
        fadeAnimation.setDuration(1000);
        fadeAnimation.setFillAfter(true);

        imagenLogo.startAnimation(scaleAnimation);
        imagenLogo.startAnimation(fadeAnimation);
    }

    private void iniciarAnimacionPuntos() {
        final Runnable animacionPuntos = new Runnable() {
            @Override
            public void run() {
                punto1.setAlpha(0.3f);
                punto2.setAlpha(0.3f);
                punto3.setAlpha(0.3f);

                if      (puntosActivo == 0) animarPunto(punto1);
                else if (puntosActivo == 1) animarPunto(punto2);
                else if (puntosActivo == 2) animarPunto(punto3);

                puntosActivo = (puntosActivo + 1) % 3;
                handlerAnimacion.postDelayed(this, 400);
            }
        };
        handlerAnimacion.post(animacionPuntos);
    }

    private void animarPunto(View punto) {
        punto.animate()
                .alpha(1.0f).scaleX(1.2f).scaleY(1.2f).setDuration(200)
                .withEndAction(() ->
                        punto.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start())
                .start();
    }

    private void detenerAnimacionPuntos() {
        handlerAnimacion.removeCallbacksAndMessages(null);
    }

    // ══ NAVEGACIÓN ════════════════════════════════════════════════════

    private void verificarSesion() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);

        boolean sessionPersistent = prefs.getBoolean("session_persistent", false);
        boolean pinConfigurado    = prefs.getBoolean("pin_configurado",    false);
        String  emailGuardado     = prefs.getString("user_email",          "");

        if (sessionPersistent && pinConfigurado && !emailGuardado.isEmpty()) {
            Intent intent = new Intent(this, VerificarPinActivity.class);
            intent.putExtra("email", emailGuardado);
            intent.putExtra("quick_access", true);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        } else if (sessionPersistent && !pinConfigurado && !emailGuardado.isEmpty()) {
        // Solo pedir PIN si hay sesión activa Y hay usuario logueado
        // Si no hay email, es usuario nuevo → ir a login
        irALogin();
         } else {
            irALogin();
        }
    }

    private void irALogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void irAVerificarPin() {
        startActivity(new Intent(this, VerificarPinActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void irAConfigurarPin() {
        startActivity(new Intent(this, ConfigurarPinActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detenerAnimacionPuntos();
    }
}