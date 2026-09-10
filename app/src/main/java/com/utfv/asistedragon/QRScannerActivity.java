package com.utfv.asistedragon;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import org.json.JSONException;
import org.json.JSONObject;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QRScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int SEGUNDOS_AUTOCLOSE = 5;

    // Vistas scanner
    private PreviewView previewView;
    private View overlayScanner, frameScanner, headerScanner, footerScanner;
    private TextView textoEstado;
    private Button botonCancelar;

    // Vistas éxito
    private RelativeLayout pantallaExito;
    private TextView textoHoraExito;
    private TextView textoEstadoExito;
    private TextView textoCuentaRegresiva; // "Cerrando en 5..."
    private Button botonContinuar;         // oculto — solo por compatibilidad XML

    // Cámara y ML Kit
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private boolean qrDetectado = false;

    // Firebase
    private FirebaseFirestore db;

    // Datos usuario
    private String userUid, userName, userEmail, userNumeroEmpleado, userTipo, userTurno;

    // Cuenta regresiva
    private Handler autocloseHandler = new Handler(Looper.getMainLooper());
    private int cuentaRegresiva = SEGUNDOS_AUTOCLOSE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configurarPantallaCompleta();
        setContentView(R.layout.activity_qrscanner);

        db = FirebaseFirestore.getInstance();
        inicializarVistas();
        cargarDatosUsuario();

        barcodeScanner = BarcodeScanning.getClient();
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Validar ventana horaria antes de permitir escanear
        if (!validarVentanaHoraria()) return;

        if (verificarPermisosCamara()) iniciarCamara();
        else solicitarPermisosCamara();

        configurarListeners();
    }

    private void configurarPantallaCompleta() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.transparent));
    }

    private void inicializarVistas() {
        previewView          = findViewById(R.id.preview_view);
        overlayScanner       = findViewById(R.id.overlay_scanner);
        frameScanner         = findViewById(R.id.frame_scanner);
        headerScanner        = findViewById(R.id.header_scanner);
        footerScanner        = findViewById(R.id.footer_scanner);
        textoEstado          = findViewById(R.id.texto_estado);
        botonCancelar        = findViewById(R.id.boton_cancelar);
        pantallaExito        = findViewById(R.id.pantalla_exito);
        textoHoraExito       = findViewById(R.id.texto_hora_exito);
        textoEstadoExito     = findViewById(R.id.texto_estado_exito);
        botonContinuar       = findViewById(R.id.boton_continuar);
        // Intentar desde XML; si no existe el ID, crear en código
        try { textoCuentaRegresiva = findViewById(R.id.texto_cuenta_regresiva); } catch (Exception ignored) {}
        if (textoCuentaRegresiva == null) {
            textoCuentaRegresiva = new TextView(this);
        }
    }

    private void cargarDatosUsuario() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        userUid            = prefs.getString("user_uid",              "");
        userName           = prefs.getString("user_nombre",           "");
        userEmail          = prefs.getString("user_email",            "");
        userNumeroEmpleado = prefs.getString("user_numero_empleado",  "");
        userTipo           = prefs.getString("user_tipo",             "");
        userTurno          = prefs.getString("user_turno",            "");
    }

    private void configurarListeners() {
        botonCancelar.setOnClickListener(v -> finish());
        // botonContinuar oculto — auto-cierre reemplaza su función
        if (botonContinuar != null) botonContinuar.setVisibility(View.GONE);
    }

    // ══════════════════════════════════════════════════════════════════════
    // CÁMARA
    // ══════════════════════════════════════════════════════════════════════

    private boolean validarVentanaHoraria() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String clasesJson = prefs.getString("horario_clases_completas", null);
        if (clasesJson == null) return true; // sin horario → permitir

        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type type =
                    new com.google.gson.reflect.TypeToken<java.util.List<Clase>>(){}.getType();
            java.util.List<Clase> todas = gson.fromJson(clasesJson, type);

            // Día de hoy
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("EEEE", new java.util.Locale("es","MX"));
            String diaHoy = sdf.format(new java.util.Date());
            diaHoy = diaHoy.substring(0,1).toUpperCase() + diaHoy.substring(1).toLowerCase();

            java.util.List<Clase> hoy = new java.util.ArrayList<>();
            for (Clase c : todas)
                if (c.getDia() != null && c.getDia().equalsIgnoreCase(diaHoy)) hoy.add(c);

            if (hoy.isEmpty()) return true;

            java.util.Collections.sort(hoy, (a, b) -> {
                String ha = a.getHoraInicio() != null ? a.getHoraInicio() : "";
                String hb = b.getHoraInicio() != null ? b.getHoraInicio() : "";
                return ha.compareTo(hb);
            });

            // Hora actual en minutos
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int ahora = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60
                    + cal.get(java.util.Calendar.MINUTE);

            // Primera clase
            Clase primera = hoy.get(0);
            int iniPrimera = minutosDeHHMM(primera.getHoraInicio());
            int ventana    = iniPrimera - 15;

            if (ahora < ventana) {
                int faltanMin = ventana - ahora;
                int h = faltanMin / 60, m = faltanMin % 60;
                String tiempo = h > 0
                        ? h + " h " + (m > 0 ? m + " min" : "")
                        : m + " min";
                String horaPermitida = String.format(java.util.Locale.getDefault(),
                        "%02d:%02d", ventana/60, ventana%60);

                runOnUiThread(() ->
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("Aún no es momento")
                                .setMessage("Podrás registrar tu asistencia a partir de las "
                                        + horaPermitida
                                        + " (15 min antes de tu primera clase: "
                                        + primera.getHoraInicio() + ")."
                                        + "\n\nFaltan " + tiempo + ".")
                                .setPositiveButton("Entendido", (d,w) -> finish())
                                .setCancelable(false)
                                .show()
                );
                return false;
            }

            // Verificar que no hayan vencido todas las tolerancias
            boolean todasVencidas = true;
            for (Clase c : hoy) {
                int ini = minutosDeHHMM(c.getHoraInicio());
                int fin = minutosDeHHMM(c.getHoraFin());
                int tol = PanelPrincipalActivity.calcularTolerancia(c.getHoraInicio(), c.getHoraFin());
                if (ini >= 0 && ahora <= ini + tol) { todasVencidas = false; break; }
            }
            if (todasVencidas) {
                runOnUiThread(() ->
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("Registro no disponible")
                                .setMessage("El tiempo de registro para todas tus clases de hoy ha concluido.")
                                .setPositiveButton("Entendido", (d,w) -> finish())
                                .setCancelable(false)
                                .show()
                );
                return false;
            }

        } catch (Exception ignored) {}
        return true;
    }

    private boolean verificarPermisosCamara() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void solicitarPermisosCamara() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                iniciarCamara();
            else { Toast.makeText(this, "Se requiere permiso de cámara", Toast.LENGTH_LONG).show(); finish(); }
        }
    }

    private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                vincularCamaraConCicloVida(future.get());
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error cámara: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void vincularCamaraConCicloVida(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysis.setAnalyzer(cameraExecutor, imageProxy -> procesarImagen(imageProxy));

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, selector, preview, analysis);
    }

    private void procesarImagen(ImageProxy imageProxy) {
        if (qrDetectado) { imageProxy.close(); return; }

        @androidx.camera.core.ExperimentalGetImage
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode b : barcodes) {
                        if (b.getValueType() == Barcode.TYPE_TEXT) {
                            String data = b.getRawValue();
                            if (data != null && !qrDetectado) {
                                qrDetectado = true;
                                procesarQRDetectado(data);
                            }
                        }
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> imageProxy.close());
    }

    // ══════════════════════════════════════════════════════════════════════
    // VALIDACIÓN Y REGISTRO
    // ══════════════════════════════════════════════════════════════════════

    private void procesarQRDetectado(String qrData) {
        runOnUiThread(() -> {
            textoEstado.setText("QR Detectado - Validando...");
            if (validarQRUTFV(qrData)) {
                textoEstado.setText("QR Válido - Registrando...");
                registrarAsistencia(qrData);
            } else {
                mostrarErrorQRInvalido(qrData);
            }
        });
    }

    private boolean validarQRUTFV(String qrData) {
        try {
            JSONObject json = new JSONObject(qrData);
            return json.has("tipo") && json.has("codigo")
                    && "asistencia_utfv".equals(json.getString("tipo"));
        } catch (JSONException e) { return false; }
    }

    private void mostrarErrorQRInvalido(String qrData) {
        textoEstado.setTextColor(ContextCompat.getColor(this, R.color.error));
        textoEstado.setText("Código QR No Válido");

        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(200);

        Toast.makeText(this, "Este QR no es válido para registro UTFV", Toast.LENGTH_LONG).show();

        textoEstado.postDelayed(() -> {
            qrDetectado = false;
            textoEstado.setTextColor(ContextCompat.getColor(this, R.color.white));
            textoEstado.setText("Buscando código QR...");
        }, 2000);
    }

    private void registrarAsistencia(String qrData) {
        SimpleDateFormat sdfFecha = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat sdfHora  = new SimpleDateFormat("HH:mm:ss",   Locale.getDefault());
        SimpleDateFormat sdfDia   = new SimpleDateFormat("EEEE", new Locale("es", "MX"));

        Date ahora   = new Date();
        String fecha = sdfFecha.format(ahora);
        String hora  = sdfHora.format(ahora);
        String dia   = sdfDia.format(ahora);
        dia = dia.substring(0, 1).toUpperCase() + dia.substring(1);
        long timestamp = ahora.getTime();

        // ── CALCULAR ESTADO CORRECTO usando el horario guardado ───────────
        String estado              = "puntual";
        String primeraHoraClase   = "";
        int    minutosDiferencia  = 0;

        String horaHHMM = hora.substring(0, 5); // "HH:mm"

        try {
            SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
            String clasesJson = prefs.getString("horario_clases_completas", null);
            if (clasesJson != null) {
                Type type = new TypeToken<List<Clase>>(){}.getType();
                List<Clase> todas = new Gson().fromJson(clasesJson, type);

                // Filtrar clases del día de hoy y ordenar
                List<Clase> clasesHoy = new ArrayList<>();
                for (Clase c : todas)
                    if (c.getDia() != null && c.getDia().equalsIgnoreCase(dia))
                        clasesHoy.add(c);

                Collections.sort(clasesHoy, (a, b) -> {
                    String ha = a.getHoraInicio() != null ? a.getHoraInicio() : "";
                    String hb = b.getHoraInicio() != null ? b.getHoraInicio() : "";
                    return ha.compareTo(hb);
                });

                // Encontrar la clase aplicable (la primera cuyo inicio <= ahora)
                int regMin = minutosDeHHMM(horaHHMM);
                for (Clase c : clasesHoy) {
                    int iniMin = minutosDeHHMM(c.getHoraInicio());
                    if (iniMin < 0) continue;
                    // Esta clase ya comenzó o comienza ahora
                    if (regMin >= iniMin) {
                        primeraHoraClase = c.getHoraInicio();
                        int tol = PanelPrincipalActivity.calcularTolerancia(
                                c.getHoraInicio(), c.getHoraFin());
                        // ¿Llegó después de inicio + tolerancia?
                        if (regMin > iniMin + tol) {
                            estado = "retardo";
                            minutosDiferencia = regMin - iniMin; // minutos desde inicio real
                        } else {
                            estado = "puntual";
                            minutosDiferencia = Math.max(0, regMin - iniMin);
                        }
                        break; // usar la primera clase que aplica
                    }
                }

                // Si no encontró clase activa (llegó antes que todas), usar la primera
                if (primeraHoraClase.isEmpty() && !clasesHoy.isEmpty()) {
                    primeraHoraClase = clasesHoy.get(0).getHoraInicio();
                    estado = "puntual";
                }
            }
        } catch (Exception ignored) {}

        // Extraer código QR
        String codigoQR = "UTFV-ASISTENCIA-2026";
        try {
            codigoQR = new JSONObject(qrData).getString("codigo");
        } catch (JSONException ignored) {}

        final String estadoFinal           = estado;
        final String primeraHoraFinal      = primeraHoraClase;
        final int    minutosDifFinal       = minutosDiferencia;

        Asistencia asistencia = new Asistencia(
                userUid, userName, userEmail, userNumeroEmpleado,
                userTipo, userTurno,
                fecha, hora, timestamp, dia,
                estadoFinal, primeraHoraFinal, minutosDifFinal, codigoQR);

        db.collection("asistencias")
                .add(asistencia)
                .addOnSuccessListener(docRef -> mostrarPantallaExito(hora, estadoFinal))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al registrar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    qrDetectado = false;
                    textoEstado.setText("Buscando código QR...");
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    // PANTALLA ÉXITO CON CUENTA REGRESIVA
    // ══════════════════════════════════════════════════════════════════════

    private void mostrarPantallaExito(String hora, String estado) {
        // Ocultar scanner
        overlayScanner.setVisibility(View.GONE);
        frameScanner.setVisibility(View.GONE);
        headerScanner.setVisibility(View.GONE);
        footerScanner.setVisibility(View.GONE);
        if (botonContinuar != null) botonContinuar.setVisibility(View.GONE);

        // Mostrar éxito
        pantallaExito.setVisibility(View.VISIBLE);
        textoHoraExito.setText(hora);

        if ("puntual".equals(estado)) {
            textoEstadoExito.setText("PUNTUAL");
            textoEstadoExito.setTextColor(ContextCompat.getColor(this, R.color.success));
            if (textoEstadoExito.getBackground() != null)
                textoEstadoExito.setBackgroundResource(R.drawable.badge_puntual);
        } else {
            textoEstadoExito.setText("RETARDO");
            textoEstadoExito.setTextColor(ContextCompat.getColor(this, R.color.warning));
            if (textoEstadoExito.getBackground() != null)
                textoEstadoExito.setBackgroundResource(R.drawable.badge_retardo);
        }

        // Animaciones
        animarIconoExito();
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(500);
        pantallaExito.startAnimation(fadeIn);

        // Cuenta regresiva 5 → 0 → finish()
        cuentaRegresiva = SEGUNDOS_AUTOCLOSE;
        iniciarCuentaRegresiva();
    }

    private void iniciarCuentaRegresiva() {
        actualizarTextoCuenta();
        autocloseHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                cuentaRegresiva--;
                actualizarTextoCuenta();
                if (cuentaRegresiva > 0) {
                    autocloseHandler.postDelayed(this, 1000);
                } else {
                    finish();
                }
            }
        }, 1000);
    }

    private void actualizarTextoCuenta() {
        if (textoCuentaRegresiva != null) {
            textoCuentaRegresiva.setText("Cerrando en " + cuentaRegresiva + "...");
        }
    }

    private void animarIconoExito() {
        View circulo = findViewById(R.id.circulo_exito_fondo);
        View check   = findViewById(R.id.icono_check_exito);

        ScaleAnimation scaleCirculo = new ScaleAnimation(0f, 1f, 0f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleCirculo.setDuration(400);
        scaleCirculo.setInterpolator(new android.view.animation.OvershootInterpolator(1.5f));

        ScaleAnimation scaleCheck = new ScaleAnimation(0f, 1f, 0f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleCheck.setDuration(300);
        scaleCheck.setStartOffset(200);
        scaleCheck.setInterpolator(new android.view.animation.OvershootInterpolator(2f));

        AlphaAnimation fadeCheck = new AlphaAnimation(0f, 1f);
        fadeCheck.setDuration(300);
        fadeCheck.setStartOffset(200);

        if (circulo != null) circulo.startAnimation(scaleCirculo);
        if (check != null) {
            check.startAnimation(scaleCheck);
            check.startAnimation(fadeCheck);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static int minutosDeHHMM(String hora) {
        if (hora == null || !hora.contains(":")) return -1;
        try {
            String[] p = hora.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) { return -1; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autocloseHandler.removeCallbacksAndMessages(null);
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();
    }
}