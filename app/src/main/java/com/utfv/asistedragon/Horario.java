package com.utfv.asistedragon;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Activity principal para la gestión del horario académico del profesor.
 *
 * Permite subir el horario en formato PDF o imagen, enviarlo a MistralOCRService
 * para su reconocimiento óptico de caracteres (OCR), y mostrar las materias y
 * sesiones detectadas para su revisión, edición y confirmación por parte del usuario.
 *
 * Una vez confirmadas, las materias se guardan localmente (SharedPreferences) y de
 * forma remota en Firestore, se programan las notificaciones correspondientes
 * mediante NotificacionesManager, y se genera la vista semanal del horario utilizando
 * ViewPager2 y TabLayout.
 */

public class Horario extends AppCompatActivity {

    private static final String TAG = "HorarioActivity";

    // Vistas
    private LinearLayout cardSinHorario;
    private LinearLayout cardProcesando;
    private CardView cardMateriasDetectadas;
    private CardView cardPeriodoVigencia;
    private LinearLayout containerMaterias;
    private TextView textoEstadoOCR;
    private TextView textoTotalMaterias;
    private TextView txtVigencia;
    private TextView txtNombreProfesor;
    private Button btnSubirPDF;
    private Button btnSubirImagen;
    private Button btnAgregarMateria;
    private Button btnConfirmarMaterias;
    private Button btnEscanearNuevo;

    // Datos
    private Uri archivoUri;
    private String tipoArchivo;
    private List<Clase> materiasDetectadas;
    private List<Clase> clasesCompletas;
    private boolean materiasConfirmadas = false;
    private String periodoSeleccionado  = "";
    private String vigenciaDetectada    = "";
    private String nombreProfesor       = "";

    private CardView cardHorarioSemanal;
    private LinearLayout containerHorarioDias;
    private ViewPager2 viewPagerDias;
    private TabLayout tabsDias;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        configurarBarraEstado();
        setContentView(R.layout.activity_horario);

        inicializarVistas();
        cargarNombreProfesor();
        cargarMateriasGuardadas();
        configurarListeners();
        configurarBottomNavigation();
        cargarHorarioDesdeFirestoreSiNecesario();
        // Aviso solo si no hay horario cargado
        if (materiasDetectadas == null || materiasDetectadas.isEmpty()) {
            mostrarAvisoInicial();
        }

        // Animación de entrada — fade in suave
        animarEntradaPantalla();
    }

    // ══════════════════════════════════════════════════════════════════════
    // ANIMACIÓN DE ENTRADA
    // ══════════════════════════════════════════════════════════════════════

    private void animarEntradaPantalla() {
        View rootView = findViewById(android.R.id.content);
        if (rootView == null) return;

        rootView.setAlpha(0f);
        rootView.animate()
                .alpha(1f)
                .setDuration(700)
                .setStartDelay(80)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    // ══════════════════════════════════════════════════════════════════════

    private void configurarBarraEstado() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.green_primary));
    }

    private void inicializarVistas() {
        cardSinHorario          = findViewById(R.id.card_sin_horario);
        cardProcesando          = findViewById(R.id.card_procesando);
        cardMateriasDetectadas  = findViewById(R.id.card_materias_detectadas);
        cardPeriodoVigencia     = findViewById(R.id.card_periodo_vigencia);
        containerMaterias       = findViewById(R.id.container_materias);
        textoEstadoOCR          = findViewById(R.id.texto_estado_ocr);
        textoTotalMaterias      = findViewById(R.id.texto_total_materias);
        txtVigencia             = findViewById(R.id.txt_vigencia);
        txtNombreProfesor       = findViewById(R.id.txt_nombre_profesor);
        btnSubirPDF             = findViewById(R.id.btnSubirPDF);
        btnSubirImagen          = findViewById(R.id.btnSubirImagen);
        btnAgregarMateria       = findViewById(R.id.btn_agregar_materia);
        btnConfirmarMaterias    = findViewById(R.id.btn_confirmar_materias);
        btnEscanearNuevo        = findViewById(R.id.btn_escanear_nuevo);
        cardHorarioSemanal      = findViewById(R.id.card_horario_semanal);
        viewPagerDias           = findViewById(R.id.viewpager_dias);
        tabsDias                = findViewById(R.id.tabs_dias);
    }

    private void configurarListeners() {
        btnSubirPDF.setOnClickListener(v -> abrirPDF());
        btnSubirImagen.setOnClickListener(v -> abrirImagen());
        btnAgregarMateria.setOnClickListener(v -> mostrarDialogoAgregarMateria());
        btnConfirmarMaterias.setOnClickListener(v -> confirmarMaterias());
        btnEscanearNuevo.setOnClickListener(v -> escanearNuevo());

        ImageView btnEliminarHorario = findViewById(R.id.btn_eliminar_horario);
        if (btnEliminarHorario != null) {
            btnEliminarHorario.setOnClickListener(v -> eliminarHorarioCompleto());
        }
    }

    // === Launchers ===
    private final androidx.activity.result.ActivityResultLauncher<Intent> pdfViewerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            String clasesJson = result.getData().getStringExtra("clases_json");
                            boolean confirmadas = result.getData().getBooleanExtra("confirmadas", false);
                            if (clasesJson != null) {
                                com.google.gson.Gson gson = new com.google.gson.Gson();
                                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<Clase>>(){}.getType();
                                List<Clase> clases = gson.fromJson(clasesJson, type);
                                mostrarMateriasDetectadas(clases);
                                if (confirmadas) confirmarMaterias();
                            }
                        }
                    });


    private final ActivityResultLauncher<String> pdfLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            archivoUri = uri;
                            tipoArchivo = "pdf";
                            procesarArchivo();
                        }
                    });

    private final ActivityResultLauncher<String> imageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            archivoUri = uri;
                            tipoArchivo = "image";
                            procesarArchivo();
                        }
                    });

    private void abrirPDF() { pdfLauncher.launch("application/pdf"); }
    private void abrirImagen() { imageLauncher.launch("image/*"); }

    private void procesarArchivo() {
        cardSinHorario.setVisibility(View.GONE);
        cardProcesando.setVisibility(View.VISIBLE);
        textoEstadoOCR.setText("Procesando con Mistral OCR...\nConvirtiendo PDF a imagen...\n\nEsto puede tardar hasta 1 minuto.");

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(archivoUri);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[16384];
                int nRead;
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                byte[] pdfBytes = buffer.toByteArray();
                inputStream.close();

                Log.i(TAG, "PDF leido: " + pdfBytes.length + " bytes");
                runOnUiThread(() -> textoEstadoOCR.setText(
                        "Analizando horario con IA...\n\nSi el servicio está saturado, se reintentará automáticamente."));

                MistralOCRService mistralService = new MistralOCRService(this);
                MistralOCRService.ResultadoOCR resultado = mistralService.procesarHorario(pdfBytes);
                List<Clase> clases       = resultado.clases;
                final String vigenciaOCR = resultado.vigencia;
                final String periodoOCR  = resultado.periodo;

                final String imagenPath = generarImagenPDF(pdfBytes);

                runOnUiThread(() -> {
                    if (clases.isEmpty()) {
                        mostrarError("No se detectaron clases en el PDF", false);
                    } else {
                        vigenciaDetectada = vigenciaOCR;
                        if (!periodoOCR.isEmpty()) periodoSeleccionado = periodoOCR;

                        cardProcesando.setVisibility(View.GONE);
                        String clasesJson = new com.google.gson.Gson().toJson(clases);

                        String nombrePdf = "horario.pdf";
                        try {
                            android.database.Cursor cursor = getContentResolver().query(
                                    archivoUri, null, null, null, null);
                            if (cursor != null && cursor.moveToFirst()) {
                                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                                if (idx >= 0) nombrePdf = cursor.getString(idx);
                                cursor.close();
                            }
                        } catch (Exception ignored) {}

                        Intent intent = new Intent(Horario.this, HorarioPDFViewerActivity.class);
                        intent.putExtra(HorarioPDFViewerActivity.EXTRA_CLASES_JSON, clasesJson);
                        intent.putExtra("nombre_pdf", nombrePdf);
                        if (imagenPath != null) {
                            intent.putExtra(HorarioPDFViewerActivity.EXTRA_PDF_IMAGEN, imagenPath);
                        }
                        pdfViewerLauncher.launch(intent);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error", e);
                final boolean es429 = e.getMessage() != null && e.getMessage().contains("429");
                runOnUiThread(() -> mostrarError(e.getMessage(), es429));
            }
        }).start();
    }

    private String generarImagenPDF(byte[] pdfBytes) {
        try {
            File tempPdf = File.createTempFile("preview_", ".pdf", getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(tempPdf)) { fos.write(pdfBytes); }

            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(pfd);
            PdfRenderer.Page page = renderer.openPage(0);

            int w = page.getWidth() * 2;
            int h = page.getHeight() * 2;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            renderer.close();
            pfd.close();
            tempPdf.delete();

            File imgFile = File.createTempFile("horario_preview_", ".jpg", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(imgFile)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            bmp.recycle();
            return imgFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error generando imagen PDF: " + e.getMessage());
            return null;
        }
    }

    private void mostrarMateriasDetectadas(List<Clase> clases) {
        cardProcesando.setVisibility(View.GONE);

        if (clases.isEmpty()) {
            Toast.makeText(this, "No se detectaron materias", Toast.LENGTH_LONG).show();
            cardSinHorario.setVisibility(View.VISIBLE);
            return;
        }

        this.clasesCompletas = new ArrayList<>(clases);
        this.materiasDetectadas = consolidarMaterias(clases);

        if (cardPeriodoVigencia != null) {
            cardPeriodoVigencia.setVisibility(View.VISIBLE);
            actualizarInfoPeriodo();
        }
        cardMateriasDetectadas.setVisibility(View.VISIBLE);
        actualizarListaMaterias();
    }

    private void cargarHorarioDesdeFirestoreSiNecesario() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        // Si ya hay horario local de este usuario, no hacer nada
        if (prefs.getString("horario_materias", null) != null
                && user.getUid().equals(prefs.getString("horario_owner_uid", ""))) return;

        Log.i(TAG, "Buscando horario en Firestore para uid=" + user.getUid());

        FirebaseFirestore.getInstance()
                .collection("horarios")
                .whereEqualTo("uid", user.getUid())
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        Log.i(TAG, "Sin horario en Firestore para este usuario");
                        return;
                    }

                    com.google.firebase.firestore.DocumentSnapshot doc =
                            query.getDocuments().get(0);

                    List<Clase> clasesRecuperadas = new ArrayList<>();
                    List<Object> materiasRaw = (List<Object>) doc.get("materias");
                    if (materiasRaw == null) return;

                    for (Object matObj : materiasRaw) {
                        java.util.Map<String, Object> mat =
                                (java.util.Map<String, Object>) matObj;
                        String nombreMat = (String) mat.get("materia");
                        String grupoMat  = (String) mat.get("grupo");
                        List<Object> sesiones = (List<Object>) mat.get("sesiones");
                        if (sesiones == null) continue;
                        for (Object sesObj : sesiones) {
                            java.util.Map<String, Object> ses =
                                    (java.util.Map<String, Object>) sesObj;
                            Clase c = new Clase();
                            c.setMateria(nombreMat);
                            c.setGrupo(grupoMat);
                            c.setDia((String) ses.get("dia"));
                            c.setHoraInicio((String) ses.get("horaInicio"));
                            c.setHoraFin((String) ses.get("horaFin"));
                            c.setSalon((String) ses.get("salon"));
                            c.setConfirmada(true);
                            clasesRecuperadas.add(c);
                        }
                    }

                    if (clasesRecuperadas.isEmpty()) return;

                    Gson gson = new Gson();
                    List<Clase> materiasRecuperadas = consolidarMaterias(clasesRecuperadas);
                    String periodo  = doc.getString("periodo")  != null
                            ? doc.getString("periodo")  : "";
                    String vigencia = doc.getString("vigencia") != null
                            ? doc.getString("vigencia") : "";

                    prefs.edit()
                            .putString("horario_materias",
                                    gson.toJson(materiasRecuperadas))
                            .putString("horario_clases_completas",
                                    gson.toJson(clasesRecuperadas))
                            .putInt("horario_total_materias",
                                    materiasRecuperadas.size())
                            .putBoolean("materias_confirmadas",    true)
                            .putString("horario_periodo",          periodo)
                            .putString("horario_vigencia",         vigencia)
                            .putString("horario_owner_uid",        user.getUid())
                            .apply();

                    runOnUiThread(() -> {
                        clasesCompletas     = clasesRecuperadas;
                        materiasDetectadas  = materiasRecuperadas;
                        materiasConfirmadas = true;
                        periodoSeleccionado = periodo;
                        vigenciaDetectada   = vigencia;

                        cardSinHorario.setVisibility(View.GONE);
                        cardMateriasDetectadas.setVisibility(View.VISIBLE);
                        if (cardPeriodoVigencia != null) {
                            cardPeriodoVigencia.setVisibility(View.VISIBLE);
                            actualizarInfoPeriodo();
                        }
                        aplicarEstadoConfirmado();
                        mostrarHorarioSemanal(clasesCompletas);
                        NotificacionesManager.programarTodas(this);
                        Toast.makeText(this,
                                "Horario restaurado ✓", Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error recuperando horario Firestore: "
                                + e.getMessage()));
    }

    private void actualizarInfoPeriodo() {
        if (txtVigencia != null)
            txtVigencia.setText(vigenciaDetectada.isEmpty() ? "No detectada en el PDF" : vigenciaDetectada);
        if (txtNombreProfesor != null)
            txtNombreProfesor.setText(nombreProfesor.isEmpty() ? "No identificado" : nombreProfesor);
        TextView txtPeriodo = findViewById(R.id.txt_periodo);
        if (txtPeriodo != null)
            txtPeriodo.setText(periodoSeleccionado.isEmpty() ? "No detectado" : periodoSeleccionado);
    }

    private void actualizarListaMaterias() {
        textoTotalMaterias.setText(materiasDetectadas.size() +
                (materiasConfirmadas ? " materias confirmadas" : " materias encontradas"));

        if (materiasConfirmadas) {
            textoTotalMaterias.setTextColor(ContextCompat.getColor(this, R.color.success));
        }

        containerMaterias.removeAllViews();

        for (int i = 0; i < materiasDetectadas.size(); i++) {
            final int index = i;
            Clase materia = materiasDetectadas.get(i);
            View materiaView = crearVistaMateria(materia, index, materiasConfirmadas);
            containerMaterias.addView(materiaView);
        }
    }

    private void eliminarHorarioCompleto() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar Horario")
                .setMessage("¿Estás seguro de eliminar todo tu horario?\n\nSe borrarán todas las materias detectadas.")
                .setPositiveButton("Eliminar Todo", (dialog, which) -> {
                    // ── Cancelar notificaciones antes de borrar ─────────
                    NotificacionesManager.cancelarTodas(this);

                    getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                            .edit()
                            .remove("horario_materias")
                            .remove("horario_clases_completas")
                            .remove("horario_total_materias")
                            .remove("materias_confirmadas")
                            .remove("horario_periodo")
                            .remove("horario_vigencia")
                            .apply();

                    materiasDetectadas = null;
                    clasesCompletas = null;
                    materiasConfirmadas = false;
                    archivoUri = null;
                    tipoArchivo = null;
                    vigenciaDetectada = "";
                    periodoSeleccionado = "";

                    cardMateriasDetectadas.setVisibility(View.GONE);
                    cardHorarioSemanal.setVisibility(View.GONE);
                    cardProcesando.setVisibility(View.GONE);
                    if (cardPeriodoVigencia != null) cardPeriodoVigencia.setVisibility(View.GONE);
                    cardSinHorario.setVisibility(View.VISIBLE);

                    btnAgregarMateria.setVisibility(View.VISIBLE);
                    btnConfirmarMaterias.setVisibility(View.VISIBLE);
                    btnEscanearNuevo.setVisibility(View.VISIBLE);
                    btnAgregarMateria.setEnabled(true);
                    btnConfirmarMaterias.setEnabled(true);
                    btnEscanearNuevo.setEnabled(true);
                    btnAgregarMateria.setAlpha(1f);
                    btnConfirmarMaterias.setAlpha(1f);
                    btnEscanearNuevo.setAlpha(1f);
                    btnConfirmarMaterias.setText("Confirmar");
                    btnAgregarMateria.setText("+ Agregar Materia");
                    btnConfirmarMaterias.setBackgroundTintList(
                            ContextCompat.getColorStateList(this, R.color.green_primary));

                    containerMaterias.removeAllViews();
                    Toast.makeText(this, "Horario eliminado. Ya puedes escanear uno nuevo.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private View crearVistaMateria(Clase materia, int index, boolean confirmadas) {
        boolean estaConfirmada = confirmadas || materia.isConfirmada();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        layout.setPadding(16, 14, 16, 14);

        android.graphics.drawable.GradientDrawable itemBg = new android.graphics.drawable.GradientDrawable();
        itemBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        itemBg.setCornerRadius(14f);
        if (estaConfirmada) {
            itemBg.setColor(0xFFF8FFF8);
            itemBg.setStroke(1, 0xFFBEDFBE);
        } else {
            itemBg.setColor(0xFFFEFCF8);
            itemBg.setStroke(1, 0xFFEDE8DF);
        }
        layout.setBackground(itemBg);
        if (materia.isConfirmada() && !confirmadas) layout.setAlpha(0.65f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 8);
        layout.setLayoutParams(params);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView txtMateria = new TextView(this);
        txtMateria.setText(materia.getMateria());
        txtMateria.setTextSize(14);
        txtMateria.setTextColor(estaConfirmada ? 0xFF555555 : 0xFF1A1A1A);
        txtMateria.setTypeface(null, estaConfirmada
                ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        txtMateria.setMaxLines(2);
        txtMateria.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textLayout.addView(txtMateria);

        if (materia.getGrupo() != null && !materia.getGrupo().isEmpty()) {
            TextView chipGrupo = new TextView(this);
            chipGrupo.setText(materia.getGrupo());
            chipGrupo.setTextSize(10);
            chipGrupo.setTextColor(estaConfirmada ? 0xFF888888
                    : ContextCompat.getColor(this, R.color.green_primary));
            chipGrupo.setTypeface(null, android.graphics.Typeface.BOLD);
            android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
            chipBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            chipBg.setCornerRadius(30f);
            chipBg.setColor(estaConfirmada ? 0x10888888 : 0x12228B22);
            chipBg.setStroke(1, estaConfirmada ? 0xFFCCCCCC
                    : ContextCompat.getColor(this, R.color.green_primary));
            chipGrupo.setBackground(chipBg);
            chipGrupo.setPadding(14, 3, 14, 3);
            LinearLayout.LayoutParams cP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cP.setMargins(0, 5, 0, 0);
            chipGrupo.setLayoutParams(cP);
            textLayout.addView(chipGrupo);
        }
        layout.addView(textLayout);

        if (!estaConfirmada) {
            layout.setClickable(true);
            layout.setFocusable(true);
            android.util.TypedValue ripple = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            layout.setForeground(ContextCompat.getDrawable(this, ripple.resourceId));
            layout.setOnClickListener(v -> mostrarDialogoEditarClases(materia));

            LinearLayout rightCol = new LinearLayout(this);
            rightCol.setOrientation(LinearLayout.HORIZONTAL);
            rightCol.setGravity(android.view.Gravity.CENTER_VERTICAL);
            rightCol.setPadding(0, 0, 12, 0);
            rightCol.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

            android.widget.Button btnX = new android.widget.Button(this);
            btnX.setText("✕");
            btnX.setTextColor(0xFFBBBBBB);
            btnX.setTextSize(11);
            btnX.setTypeface(null, android.graphics.Typeface.BOLD);
            btnX.setAllCaps(false);
            android.graphics.drawable.GradientDrawable xBg = new android.graphics.drawable.GradientDrawable();
            xBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            xBg.setColor(0xFFEEEEEE);
            btnX.setBackground(xBg);
            int xSz = (int)(28 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams xP = new LinearLayout.LayoutParams(xSz, xSz);
            btnX.setLayoutParams(xP);
            btnX.setPadding(0, 0, 0, 0);
            btnX.setOnClickListener(v -> {
                String nombreMat = materia.getMateria() != null ? materia.getMateria() : "";
                String grupoMat  = materia.getGrupo()   != null ? materia.getGrupo()   : "";
                new AlertDialog.Builder(this)
                        .setTitle("Eliminar materia")
                        .setMessage("¿Deseas eliminar la materia?\n\n"
                                + "Materia:  " + nombreMat + "\n"
                                + "Grupo:    " + grupoMat)
                        .setPositiveButton("Si, eliminar", (d, w) -> eliminarMateria(index))
                        .setNegativeButton("No", null)
                        .show();
            });
            rightCol.addView(btnX);
            layout.addView(rightCol);
        } else {
            TextView badge = new TextView(this);
            badge.setText("Confirmada");
            badge.setTextSize(9);
            badge.setTextColor(ContextCompat.getColor(this, R.color.success));
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
            android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
            badgeBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(20f);
            badgeBg.setColor(0x1A4CAF50);
            badgeBg.setStroke(1, ContextCompat.getColor(this, R.color.success));
            badge.setBackground(badgeBg);
            badge.setPadding(14, 5, 14, 5);
            layout.addView(badge);
        }
        return layout;
    }

    private void mostrarDialogoEditarClases(Clase materiaConsolidada) {
        if (clasesCompletas == null || clasesCompletas.isEmpty()) {
            Toast.makeText(this, "No hay clases cargadas", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Clase> clasesDeEstaMateria = new ArrayList<>();
        for (Clase clase : clasesCompletas) {
            if (clase.getMateria() != null &&
                    clase.getMateria().equalsIgnoreCase(materiaConsolidada.getMateria()) &&
                    clase.getGrupo() != null &&
                    clase.getGrupo().equals(materiaConsolidada.getGrupo())) {
                clasesDeEstaMateria.add(clase);
            }
        }

        if (clasesDeEstaMateria.isEmpty()) {
            Toast.makeText(this, "No se encontraron clases para esta materia", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFEFCF8);
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(ContextCompat.getColor(this, R.color.green_primary));
        header.setPadding(56, 44, 56, 32);

        TextView txtTitulo = new TextView(this);
        txtTitulo.setText("Sesiones de clase");
        txtTitulo.setTextSize(18);
        txtTitulo.setTextColor(android.graphics.Color.WHITE);
        txtTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(txtTitulo);

        TextView txtNombre = new TextView(this);
        txtNombre.setText(materiaConsolidada.getMateria() != null ? materiaConsolidada.getMateria() : "");
        txtNombre.setTextSize(12);
        txtNombre.setTextColor(0xCCFFFFFF);
        LinearLayout.LayoutParams nmP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nmP.setMargins(0, 5, 0, 0);
        txtNombre.setLayoutParams(nmP);
        header.addView(txtNombre);

        LinearLayout filaBotonesHeader = new LinearLayout(this);
        filaBotonesHeader.setOrientation(LinearLayout.HORIZONTAL);
        filaBotonesHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams fbhP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fbhP.setMargins(0, 18, 0, 0);
        filaBotonesHeader.setLayoutParams(fbhP);

        android.widget.Button btnAgregarDia  = crearBotonHeader("+ Agregar dia", 0x33FFFFFF, android.graphics.Color.WHITE);
        android.widget.Button btnConfirmar   = crearBotonHeader("Confirmar",     0x33FFFFFF, android.graphics.Color.WHITE);
        android.widget.Button btnEliminarDia = crearBotonHeader("Eliminar dia",  0x33FF5252, 0xFFFF5252);

        filaBotonesHeader.addView(btnAgregarDia);
        filaBotonesHeader.addView(btnConfirmar);
        filaBotonesHeader.addView(btnEliminarDia);
        header.addView(filaBotonesHeader);
        root.addView(header);

        LinearLayout listaSesiones = new LinearLayout(this);
        listaSesiones.setOrientation(LinearLayout.VERTICAL);
        listaSesiones.setPadding(40, 20, 40, 8);

        final List<Clase>[] clasesMutable = new List[]{new ArrayList<>(clasesDeEstaMateria)};

        java.lang.Runnable renderLista = new java.lang.Runnable() {
            @Override public void run() {
                listaSesiones.removeAllViews();
                for (Clase clase : clasesMutable[0]) {
                    LinearLayout tarjeta = new LinearLayout(Horario.this);
                    tarjeta.setOrientation(LinearLayout.HORIZONTAL);
                    tarjeta.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    tarjeta.setPadding(24, 20, 24, 20);
                    android.graphics.drawable.GradientDrawable tBg = new android.graphics.drawable.GradientDrawable();
                    tBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    tBg.setCornerRadius(18f);
                    tBg.setColor(0xFFFEFCF8);
                    tBg.setStroke(1, 0xFFEDE8DF);
                    tarjeta.setBackground(tBg);
                    LinearLayout.LayoutParams tP = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    tP.setMargins(0, 0, 0, 12);
                    tarjeta.setLayoutParams(tP);

                    TextView burbuja = new TextView(Horario.this);
                    String ab = clase.getDia() != null && clase.getDia().length() >= 2
                            ? clase.getDia().substring(0, 2).toUpperCase() : "??";
                    burbuja.setText(ab);
                    burbuja.setTextSize(11);
                    burbuja.setTextColor(android.graphics.Color.WHITE);
                    burbuja.setTypeface(null, android.graphics.Typeface.BOLD);
                    burbuja.setGravity(android.view.Gravity.CENTER);
                    android.graphics.drawable.GradientDrawable bFondo = new android.graphics.drawable.GradientDrawable();
                    bFondo.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    bFondo.setColor(ContextCompat.getColor(Horario.this, R.color.green_primary));
                    burbuja.setBackground(bFondo);
                    LinearLayout.LayoutParams bP = new LinearLayout.LayoutParams(72, 72);
                    bP.setMargins(0, 0, 20, 0);
                    burbuja.setLayoutParams(bP);
                    tarjeta.addView(burbuja);

                    LinearLayout info = new LinearLayout(Horario.this);
                    info.setOrientation(LinearLayout.VERTICAL);
                    info.setLayoutParams(new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    TextView tDia = new TextView(Horario.this);
                    tDia.setText(clase.getDia() != null ? clase.getDia() : "Sin día");
                    tDia.setTextSize(14);
                    tDia.setTextColor(ContextCompat.getColor(Horario.this, R.color.black));
                    tDia.setTypeface(null, android.graphics.Typeface.BOLD);
                    info.addView(tDia);
                    TextView tHora = new TextView(Horario.this);
                    String hText = (clase.getHoraInicio() != null ? clase.getHoraInicio() : "??")
                            + " – " + (clase.getHoraFin() != null ? clase.getHoraFin() : "??");
                    if (clase.getSalon() != null && !clase.getSalon().isEmpty())
                        hText += "  •  " + clase.getSalon();
                    tHora.setText(hText);
                    tHora.setTextSize(12);
                    tHora.setTextColor(ContextCompat.getColor(Horario.this, R.color.gray_medium));
                    LinearLayout.LayoutParams hP = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    hP.setMargins(0, 3, 0, 0);
                    tHora.setLayoutParams(hP);
                    info.addView(tHora);
                    tarjeta.addView(info);

                    TextView flecha = new TextView(Horario.this);
                    flecha.setText("›");
                    flecha.setTextSize(24);
                    flecha.setTextColor(ContextCompat.getColor(Horario.this, R.color.green_primary));
                    flecha.setTypeface(null, android.graphics.Typeface.BOLD);
                    tarjeta.addView(flecha);

                    tarjeta.setOnClickListener(v -> {
                        dialog.dismiss();
                        editarClaseIndividual(clase);
                    });

                    listaSesiones.addView(tarjeta);
                }
            }
        };
        renderLista.run();
        root.addView(listaSesiones);

        LinearLayout filaCancelar = new LinearLayout(this);
        filaCancelar.setOrientation(LinearLayout.HORIZONTAL);
        filaCancelar.setGravity(android.view.Gravity.END);
        filaCancelar.setPadding(40, 4, 40, 32);
        android.widget.Button btnCancelar = new android.widget.Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(ContextCompat.getColor(this, R.color.gray_medium));
        btnCancelar.setBackground(null);
        btnCancelar.setTextSize(14);
        btnCancelar.setOnClickListener(v -> dialog.dismiss());
        filaCancelar.addView(btnCancelar);
        root.addView(filaCancelar);

        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            android.graphics.drawable.GradientDrawable dBg = new android.graphics.drawable.GradientDrawable();
            dBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            dBg.setCornerRadius(40f);
            dBg.setColor(0xFFFEFCF8);
            dialog.getWindow().setBackgroundDrawable(dBg);
        }

        btnAgregarDia.setOnClickListener(v -> {
            dialog.dismiss();
            Clase nuevaClase = new Clase();
            nuevaClase.setMateria(materiaConsolidada.getMateria());
            nuevaClase.setGrupo(materiaConsolidada.getGrupo());
            nuevaClase.setDia("Lunes");
            nuevaClase.setHoraInicio("07:00");
            nuevaClase.setHoraFin("08:00");
            nuevaClase.setSalon("");
            editarClaseIndividualConFlag(nuevaClase, true);
        });

        btnConfirmar.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Confirmar materia")
                    .setMessage("¿Confirmas que " + materiaConsolidada.getMateria()
                            + " con todas sus sesiones está correcta? Se bloqueará para edición.")
                    .setPositiveButton("Confirmar", (d2, w) -> {
                        for (Clase c : clasesCompletas) {
                            if (c.getMateria() != null &&
                                    c.getMateria().equalsIgnoreCase(materiaConsolidada.getMateria()) &&
                                    c.getGrupo() != null &&
                                    c.getGrupo().equals(materiaConsolidada.getGrupo())) {
                                c.setConfirmada(true);
                            }
                        }
                        guardarMaterias();
                        actualizarListaMaterias();
                        Toast.makeText(this, materiaConsolidada.getMateria() + " confirmada", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Revisar", null)
                    .show();
        });

        btnEliminarDia.setOnClickListener(v -> {
            List<Clase> lista = clasesMutable[0];
            if (lista.isEmpty()) {
                Toast.makeText(this, "No hay sesiones para eliminar", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] opciones = new String[lista.size()];
            for (int i = 0; i < lista.size(); i++) {
                Clase c = lista.get(i);
                opciones[i] = (c.getDia() != null ? c.getDia() : "??")
                        + "  " + (c.getHoraInicio() != null ? c.getHoraInicio() : "")
                        + "–" + (c.getHoraFin() != null ? c.getHoraFin() : "")
                        + (c.getSalon() != null && !c.getSalon().isEmpty() ? "  " + c.getSalon() : "");
            }
            new AlertDialog.Builder(this)
                    .setTitle("Eliminar sesion")
                    .setItems(opciones, (d2, which) -> {
                        Clase aEliminar = lista.get(which);
                        new AlertDialog.Builder(this)
                                .setTitle("¿Eliminar sesión?")
                                .setMessage("Se eliminará: " + opciones[which])
                                .setPositiveButton("Eliminar", (d3, w) -> {
                                    clasesCompletas.remove(aEliminar);
                                    clasesMutable[0].remove(aEliminar);
                                    materiasDetectadas = consolidarMaterias(clasesCompletas);
                                    actualizarListaMaterias();
                                    renderLista.run();
                                    Toast.makeText(this, "Sesión eliminada", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Cancelar", null)
                                .show();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        dialog.show();
    }

    private android.widget.Button crearBotonHeader(String texto, int bgColor, int textColor) {
        android.widget.Button btn = new android.widget.Button(this);
        btn.setText(texto);
        btn.setTextSize(10);
        btn.setTextColor(textColor);
        btn.setAllCaps(false);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20f);
        bg.setColor(bgColor);
        btn.setBackground(bg);
        btn.setPadding(20, 8, 20, 8);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 10, 0);
        btn.setLayoutParams(p);
        return btn;
    }

    private void editarClaseIndividual(final Clase clase) {
        editarClaseIndividualConFlag(clase, false);
    }

    private void editarClaseIndividualConFlag(final Clase clase, boolean esNueva) {
        final String[] DIAS = {"Lunes","Martes","Miércoles","Jueves","Viernes","Sábado"};
        final String[] HORAS = {
                "07:00","08:00","09:00","10:00","11:00","12:00",
                "13:00","14:00","15:00","16:00","17:00","18:00",
                "19:00","20:00","21:00","22:00"
        };

        int diaIdx = 0;
        for (int i = 0; i < DIAS.length; i++) {
            if (DIAS[i].equalsIgnoreCase(clase.getDia())) { diaIdx = i; break; }
        }
        int inicioIdx = 9;
        for (int i = 0; i < HORAS.length; i++) {
            if (HORAS[i].equals(clase.getHoraInicio())) { inicioIdx = i; break; }
        }
        int finIdx = Math.min(inicioIdx + 2, HORAS.length - 1);
        for (int i = 0; i < HORAS.length; i++) {
            if (HORAS[i].equals(clase.getHoraFin())) { finIdx = i; break; }
        }
        final int[] selDia    = {diaIdx};
        final int[] selInicio = {inicioIdx};
        final int[] selFin    = {finIdx};

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFEFCF8);
        scrollView.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(ContextCompat.getColor(this, R.color.green_primary));
        header.setPadding(56, 48, 56, 36);
        TextView txtTitulo = new TextView(this);
        txtTitulo.setText("Editar Clase");
        txtTitulo.setTextSize(20);
        txtTitulo.setTextColor(android.graphics.Color.WHITE);
        txtTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(txtTitulo);
        TextView txtSub = new TextView(this);
        String nc = clase.getMateria() != null && clase.getMateria().length() > 30
                ? clase.getMateria().substring(0, 30) + "..." : clase.getMateria();
        txtSub.setText(nc);
        txtSub.setTextSize(12);
        txtSub.setTextColor(0xCCFFFFFF);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, 6, 0, 0);
        txtSub.setLayoutParams(sp);
        header.addView(txtSub);
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(56, 32, 56, 16);

        final EditText inputMateria = crearCampoEdicion(body, "Materia", "Ej: Sistemas Operativos", clase.getMateria(), false);
        final EditText inputGrupo   = crearCampoEdicion(body, "Grupo",   "Ej: DSM 204",             clase.getGrupo(),   false);

        final String[] AULAS = {
                "D-100","D-DIR","D-101","D-102","D-103","D-104","D-105","D-106",
                "D-107","D-108","D-109","D-110","D-111",
                "D-201","D-202","D-203","D-204","D-205","D-206","D-207","D-208","D-209","D-210",
                "Otra"
        };

        agregarEtiqueta(body, "Aula");
        android.widget.Spinner spinnerAula = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapterAula = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, AULAS);
        adapterAula.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAula.setAdapter(adapterAula);
        String aulaActual = clase.getSalon() != null ? clase.getSalon().trim() : "";
        int aulaIdx = 0;
        for (int i = 0; i < AULAS.length; i++) {
            if (AULAS[i].equalsIgnoreCase(aulaActual) ||
                    AULAS[i].replace("-","").equalsIgnoreCase(aulaActual.replace("-",""))) {
                aulaIdx = i; break;
            }
        }
        spinnerAula.setSelection(aulaIdx);
        spinnerAula.setBackground(crearFondoCampo());
        spinnerAula.setPadding(24, 4, 24, 4);
        spinnerAula.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        body.addView(spinnerAula);

        agregarEtiqueta(body, "Día");
        android.widget.Spinner spinnerDia = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapterDia = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, DIAS);
        adapterDia.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDia.setAdapter(adapterDia);
        spinnerDia.setSelection(selDia[0]);
        spinnerDia.setBackground(crearFondoCampo());
        spinnerDia.setPadding(24, 4, 24, 4);
        LinearLayout.LayoutParams spinP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spinP.setMargins(0, 0, 0, 4);
        spinnerDia.setLayoutParams(spinP);
        spinnerDia.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) { selDia[0] = pos; }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        body.addView(spinnerDia);

        agregarEtiqueta(body, "Horario");
        final TextView txtErrorHora = new TextView(this);
        txtErrorHora.setTextSize(11);
        txtErrorHora.setTextColor(0xFFE53935);
        txtErrorHora.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams errP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        errP.setMargins(0, 4, 0, 0);
        txtErrorHora.setLayoutParams(errP);

        LinearLayout filaHoras = new LinearLayout(this);
        filaHoras.setOrientation(LinearLayout.HORIZONTAL);
        filaHoras.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaHoras.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.Spinner spinnerInicio = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapterInicio = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, HORAS);
        adapterInicio.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInicio.setAdapter(adapterInicio);
        spinnerInicio.setSelection(selInicio[0]);
        spinnerInicio.setBackground(crearFondoCampo());
        spinnerInicio.setPadding(20, 4, 20, 4);
        LinearLayout.LayoutParams inicioP = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        inicioP.setMargins(0, 0, 8, 0);
        spinnerInicio.setLayoutParams(inicioP);

        TextView sep = new TextView(this);
        sep.setText("→");
        sep.setTextSize(18);
        sep.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        sep.setGravity(android.view.Gravity.CENTER);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        filaHoras.addView(spinnerInicio);
        filaHoras.addView(sep);

        android.widget.Spinner spinnerFin = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapterFin = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, HORAS);
        adapterFin.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFin.setAdapter(adapterFin);
        spinnerFin.setSelection(selFin[0]);
        spinnerFin.setBackground(crearFondoCampo());
        spinnerFin.setPadding(20, 4, 20, 4);
        LinearLayout.LayoutParams finP = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        finP.setMargins(8, 0, 0, 0);
        spinnerFin.setLayoutParams(finP);
        filaHoras.addView(spinnerFin);
        body.addView(filaHoras);
        body.addView(txtErrorHora);

        android.widget.AdapterView.OnItemSelectedListener validarHoras =
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                        selInicio[0] = spinnerInicio.getSelectedItemPosition();
                        selFin[0]    = spinnerFin.getSelectedItemPosition();
                        int diff = selFin[0] - selInicio[0];
                        if (diff <= 0) {
                            txtErrorHora.setText("La hora fin debe ser mayor que la hora inicio");
                            txtErrorHora.setVisibility(android.view.View.VISIBLE);
                        } else if (diff > 3) {
                            txtErrorHora.setText("Maximo 3 horas por sesion");
                            txtErrorHora.setVisibility(android.view.View.VISIBLE);
                        } else {
                            txtErrorHora.setVisibility(android.view.View.GONE);
                        }
                    }
                    public void onNothingSelected(android.widget.AdapterView<?> p) {}
                };
        spinnerInicio.setOnItemSelectedListener(validarHoras);
        spinnerFin.setOnItemSelectedListener(validarHoras);
        root.addView(body);

        LinearLayout filaBotones = new LinearLayout(this);
        filaBotones.setOrientation(LinearLayout.HORIZONTAL);
        filaBotones.setPadding(56, 8, 56, 40);
        filaBotones.setGravity(android.view.Gravity.END);

        android.widget.Button btnCancelar = new android.widget.Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(ContextCompat.getColor(this, R.color.gray_medium));
        btnCancelar.setBackground(null);
        btnCancelar.setTextSize(14);
        filaBotones.addView(btnCancelar);

        android.widget.Button btnGuardar = new android.widget.Button(this);
        btnGuardar.setText("Guardar");
        btnGuardar.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        btnGuardar.setBackground(null);
        btnGuardar.setTextSize(14);
        btnGuardar.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gp.setMargins(16, 0, 0, 0);
        btnGuardar.setLayoutParams(gp);
        filaBotones.addView(btnGuardar);
        root.addView(filaBotones);

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(scrollView);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            android.graphics.drawable.GradientDrawable dBg = new android.graphics.drawable.GradientDrawable();
            dBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            dBg.setCornerRadius(40f);
            dBg.setColor(0xFFFEFCF8);
            dialog.getWindow().setBackgroundDrawable(dBg);
        }

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnGuardar.setOnClickListener(v -> {
            int diff = selFin[0] - selInicio[0];
            if (diff <= 0) {
                txtErrorHora.setText("La hora fin debe ser mayor que la hora inicio");
                txtErrorHora.setVisibility(android.view.View.VISIBLE);
                return;
            }
            if (diff > 3) {
                txtErrorHora.setText("Maximo 3 horas por sesion");
                txtErrorHora.setVisibility(android.view.View.VISIBLE);
                return;
            }

            String nuevaMateria    = inputMateria.getText().toString().trim();
            String nuevoGrupo      = inputGrupo.getText().toString().trim();
            String nuevoDia        = DIAS[selDia[0]];
            String nuevaHoraInicio = HORAS[selInicio[0]];
            String nuevaHoraFin    = HORAS[selFin[0]];
            String nuevaAula       = spinnerAula.getSelectedItem().toString().equals("Otra") ? "" : spinnerAula.getSelectedItem().toString();

            String resumen = nuevaMateria + " "
                    + "  |  " + nuevoGrupo + " "
                    + "  " + nuevoDia + "  " + nuevaHoraInicio + " – " + nuevaHoraFin + " "
                    + "  " + (nuevaAula.isEmpty() ? "Sin aula" : nuevaAula) + " "
                    + "¿Los datos son correctos?";

            new AlertDialog.Builder(this)
                    .setTitle("Confirmar cambios")
                    .setMessage(resumen)
                    .setPositiveButton("Sí, guardar", (d2, w) -> {
                        clase.setMateria(nuevaMateria);
                        clase.setGrupo(nuevoGrupo);
                        clase.setDia(nuevoDia);
                        clase.setHoraInicio(nuevaHoraInicio);
                        clase.setHoraFin(nuevaHoraFin);
                        clase.setSalon(nuevaAula);
                        if (esNueva) clasesCompletas.add(clase);
                        materiasDetectadas = consolidarMaterias(clasesCompletas);
                        actualizarListaMaterias();
                        Toast.makeText(this, esNueva ? "Sesion agregada" : "Clase actualizada", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Revisar", null)
                    .show();
        });

        dialog.show();
    }

    private void agregarEtiqueta(LinearLayout parent, String texto) {
        TextView lbl = new TextView(this);
        lbl.setText(texto);
        lbl.setTextSize(11);
        lbl.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        lbl.setTypeface(null, android.graphics.Typeface.BOLD);
        lbl.setAllCaps(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 20, 0, 6);
        lbl.setLayoutParams(p);
        parent.addView(lbl);
    }

    private EditText crearCampoEdicion(LinearLayout parent, String etiqueta,
                                       String hint, String valor, boolean soloLectura) {
        TextView lbl = new TextView(this);
        lbl.setText(etiqueta);
        lbl.setTextSize(11);
        lbl.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        lbl.setTypeface(null, android.graphics.Typeface.BOLD);
        lbl.setAllCaps(true);
        LinearLayout.LayoutParams lblP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lblP.setMargins(0, 20, 0, 6);
        lbl.setLayoutParams(lblP);
        parent.addView(lbl);

        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(valor);
        et.setTextSize(14);
        et.setTextColor(ContextCompat.getColor(this, R.color.black));
        et.setBackground(crearFondoCampo());
        et.setPadding(24, 20, 24, 20);
        et.setSingleLine(true);
        et.setEnabled(!soloLectura);
        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        parent.addView(et);
        return et;
    }

    private android.graphics.drawable.GradientDrawable crearFondoCampo() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(16f);
        bg.setColor(0xFFF5F0EA);
        bg.setStroke(1, 0xFFDED8CF);
        return bg;
    }

    private void mostrarDialogoAgregarMateria() {
        final String[] DIAS  = {"Lunes","Martes","Miércoles","Jueves","Viernes","Sábado"};
        final String[] HORAS = {
                "07:00","08:00","09:00","10:00","11:00","12:00",
                "13:00","14:00","15:00","16:00","17:00","18:00",
                "19:00","20:00","21:00","22:00"
        };
        final int[] selDia    = {0};
        final int[] selInicio = {9};
        final int[] selFin    = {11};

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFEFCF8);
        scrollView.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(ContextCompat.getColor(this, R.color.green_primary));
        header.setPadding(56, 48, 56, 36);
        TextView txtTitulo = new TextView(this);
        txtTitulo.setText("Nueva Materia");
        txtTitulo.setTextSize(20);
        txtTitulo.setTextColor(android.graphics.Color.WHITE);
        txtTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(txtTitulo);
        TextView txtSub = new TextView(this);
        txtSub.setText("Completa todos los datos de la nueva clase");
        txtSub.setTextSize(12);
        txtSub.setTextColor(0xCCFFFFFF);
        LinearLayout.LayoutParams subP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subP.setMargins(0, 6, 0, 0);
        txtSub.setLayoutParams(subP);
        header.addView(txtSub);
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(56, 32, 56, 16);

        final EditText inputMateria = crearCampoEdicion(body, "Materia", "Ej: Sistemas Operativos", "", false);
        final EditText inputGrupo   = crearCampoEdicion(body, "Grupo",   "Ej: DSM 204",             "", false);

        final String[] AULAS = {
                "D-100","D-DIR","D-101","D-102","D-103","D-104","D-105","D-106",
                "D-107","D-108","D-109","D-110","D-111",
                "D-201","D-202","D-203","D-204","D-205","D-206","D-207","D-208","D-209","D-210",
                "Otra"
        };

        agregarEtiqueta(body, "Aula");
        android.widget.Spinner spinnerAulaNew = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapterAulaNew = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, AULAS);
        adapterAulaNew.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAulaNew.setAdapter(adapterAulaNew);
        spinnerAulaNew.setBackground(crearFondoCampo());
        spinnerAulaNew.setPadding(24, 4, 24, 4);
        spinnerAulaNew.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        body.addView(spinnerAulaNew);

        agregarEtiqueta(body, "Día");
        android.widget.Spinner spinnerDia = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapterDia = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, DIAS);
        adapterDia.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDia.setAdapter(adapterDia);
        spinnerDia.setSelection(0);
        spinnerDia.setBackground(crearFondoCampo());
        spinnerDia.setPadding(24, 4, 24, 4);
        spinnerDia.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        spinnerDia.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) { selDia[0] = pos; }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        body.addView(spinnerDia);

        agregarEtiqueta(body, "Horario");
        final TextView txtError = new TextView(this);
        txtError.setTextSize(11);
        txtError.setTextColor(0xFFE53935);
        txtError.setVisibility(android.view.View.GONE);
        txtError.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout filaHoras = new LinearLayout(this);
        filaHoras.setOrientation(LinearLayout.HORIZONTAL);
        filaHoras.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaHoras.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.Spinner spinnerInicio = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adpInicio = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, HORAS);
        adpInicio.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInicio.setAdapter(adpInicio);
        spinnerInicio.setSelection(9);
        spinnerInicio.setBackground(crearFondoCampo());
        spinnerInicio.setPadding(20, 4, 20, 4);
        LinearLayout.LayoutParams iniP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        iniP.setMargins(0, 0, 8, 0);
        spinnerInicio.setLayoutParams(iniP);

        TextView sep = new TextView(this);
        sep.setText("→");
        sep.setTextSize(18);
        sep.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        sep.setGravity(android.view.Gravity.CENTER);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.Spinner spinnerFin = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adpFin = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, HORAS);
        adpFin.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFin.setAdapter(adpFin);
        spinnerFin.setSelection(11);
        spinnerFin.setBackground(crearFondoCampo());
        spinnerFin.setPadding(20, 4, 20, 4);
        LinearLayout.LayoutParams finP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        finP.setMargins(8, 0, 0, 0);
        spinnerFin.setLayoutParams(finP);

        filaHoras.addView(spinnerInicio);
        filaHoras.addView(sep);
        filaHoras.addView(spinnerFin);
        body.addView(filaHoras);
        body.addView(txtError);

        android.widget.AdapterView.OnItemSelectedListener validar =
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                        selInicio[0] = spinnerInicio.getSelectedItemPosition();
                        selFin[0]    = spinnerFin.getSelectedItemPosition();
                        int diff = selFin[0] - selInicio[0];
                        if (diff <= 0) {
                            txtError.setText("La hora fin debe ser mayor que la hora inicio");
                            txtError.setVisibility(android.view.View.VISIBLE);
                        } else if (diff > 3) {
                            txtError.setText("Maximo 3 horas por sesion");
                            txtError.setVisibility(android.view.View.VISIBLE);
                        } else {
                            txtError.setVisibility(android.view.View.GONE);
                        }
                    }
                    public void onNothingSelected(android.widget.AdapterView<?> p) {}
                };
        spinnerInicio.setOnItemSelectedListener(validar);
        spinnerFin.setOnItemSelectedListener(validar);
        root.addView(body);

        LinearLayout filaBotones = new LinearLayout(this);
        filaBotones.setOrientation(LinearLayout.HORIZONTAL);
        filaBotones.setPadding(56, 8, 56, 40);
        filaBotones.setGravity(android.view.Gravity.END);

        android.widget.Button btnCancelar = new android.widget.Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(ContextCompat.getColor(this, R.color.gray_medium));
        btnCancelar.setBackground(null);
        btnCancelar.setTextSize(14);
        filaBotones.addView(btnCancelar);

        android.widget.Button btnAgregar = new android.widget.Button(this);
        btnAgregar.setText("Agregar");
        btnAgregar.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        btnAgregar.setBackground(null);
        btnAgregar.setTextSize(14);
        btnAgregar.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams agrP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        agrP.setMargins(16, 0, 0, 0);
        btnAgregar.setLayoutParams(agrP);
        filaBotones.addView(btnAgregar);
        root.addView(filaBotones);

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(scrollView);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            android.graphics.drawable.GradientDrawable dBg = new android.graphics.drawable.GradientDrawable();
            dBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            dBg.setCornerRadius(40f);
            dBg.setColor(0xFFFEFCF8);
            dialog.getWindow().setBackgroundDrawable(dBg);
        }

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnAgregar.setOnClickListener(v -> {
            String nombreMateria = inputMateria.getText().toString().trim();
            String grupo         = inputGrupo.getText().toString().trim();
            String aula          = spinnerAulaNew.getSelectedItem().toString().equals("Otra") ? "" : spinnerAulaNew.getSelectedItem().toString();

            if (nombreMateria.isEmpty()) { inputMateria.setError("Requerido"); inputMateria.requestFocus(); return; }
            if (grupo.isEmpty())         { inputGrupo.setError("Requerido");   inputGrupo.requestFocus();   return; }

            int diff = selFin[0] - selInicio[0];
            if (diff <= 0) { txtError.setText("La hora fin debe ser mayor que la hora inicio"); txtError.setVisibility(android.view.View.VISIBLE); return; }
            if (diff > 3)  { txtError.setText("Maximo 3 horas por sesion"); txtError.setVisibility(android.view.View.VISIBLE); return; }

            String diaNuevo = DIAS[selDia[0]];
            int iniNuevoMin = Integer.parseInt(HORAS[selInicio[0]].split(":")[0]) * 60;
            int finNuevoMin = Integer.parseInt(HORAS[selFin[0]].split(":")[0]) * 60;
            if (clasesCompletas != null) {
                for (Clase c : clasesCompletas) {
                    if (c.getDia() == null || !c.getDia().equalsIgnoreCase(diaNuevo)) continue;
                    if (c.getHoraInicio() == null || c.getHoraFin() == null) continue;
                    int iniExist = Integer.parseInt(c.getHoraInicio().split(":")[0]) * 60;
                    int finExist = Integer.parseInt(c.getHoraFin().split(":")[0]) * 60;
                    if (iniNuevoMin < finExist && finNuevoMin > iniExist) {
                        txtError.setText("Choque con " + c.getMateria() + " (" + c.getHoraInicio() + "–" + c.getHoraFin() + " " + diaNuevo + ")");
                        txtError.setVisibility(android.view.View.VISIBLE);
                        return;
                    }
                }
            }

            if (materiasDetectadas == null) materiasDetectadas = new ArrayList<>();
            if (clasesCompletas == null)    clasesCompletas    = new ArrayList<>();

            Clase nuevaClase = new Clase();
            nuevaClase.setMateria(nombreMateria);
            nuevaClase.setGrupo(grupo);
            nuevaClase.setDia(DIAS[selDia[0]]);
            nuevaClase.setHoraInicio(HORAS[selInicio[0]]);
            nuevaClase.setHoraFin(HORAS[selFin[0]]);
            nuevaClase.setSalon(aula);

            clasesCompletas.add(nuevaClase);
            materiasDetectadas = consolidarMaterias(clasesCompletas);
            actualizarListaMaterias();
            Toast.makeText(this, "Materia agregada", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void eliminarMateria(int index) {
        if (materiasDetectadas == null || index < 0 || index >= materiasDetectadas.size()) {
            Toast.makeText(this, "Error: índice inválido", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Eliminar Materia")
                .setMessage("¿Estás seguro de eliminar esta materia?")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    Clase aEliminar = materiasDetectadas.get(index);
                    if (clasesCompletas != null) {
                        clasesCompletas.removeIf(c ->
                                c.getMateria() != null &&
                                        c.getMateria().equalsIgnoreCase(aEliminar.getMateria()) &&
                                        c.getGrupo() != null &&
                                        c.getGrupo().equals(aEliminar.getGrupo()));
                    }
                    materiasDetectadas.remove(index);
                    actualizarListaMaterias();
                    Toast.makeText(this, "Materia eliminada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarMaterias() {
        if (materiasDetectadas == null || materiasDetectadas.isEmpty()) {
            Toast.makeText(this, "No hay materias para confirmar", Toast.LENGTH_SHORT).show();
            return;
        }

        materiasConfirmadas = true;
        guardarMaterias();
        guardarHorarioFirestore();
        getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("materias_confirmadas", true)
                .apply();

        // ── Programar notificaciones de clases ─────────────────────────
        NotificacionesManager.programarTodas(this);

        aplicarEstadoConfirmado();
        mostrarHorarioSemanal(clasesCompletas);
        mostrarAnimacionConfirmado();
    }

    private void mostrarAnimacionConfirmado() {
        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);
        overlay.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        int dp = (int) getResources().getDisplayMetrics().density;
        android.widget.FrameLayout.LayoutParams cardP =
                new android.widget.FrameLayout.LayoutParams(280 * dp, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        cardP.gravity = android.view.Gravity.CENTER;
        card.setLayoutParams(cardP);
        card.setPadding(32 * dp, 36 * dp, 32 * dp, 36 * dp);
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(24 * dp);
        cardBg.setColor(0xFFFFFFFF);
        card.setBackground(cardBg);

        android.widget.FrameLayout circulo = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams circP =
                new android.widget.FrameLayout.LayoutParams(80 * dp, 80 * dp);
        circP.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        circulo.setLayoutParams(circP);
        android.graphics.drawable.GradientDrawable circBg = new android.graphics.drawable.GradientDrawable();
        circBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circBg.setColor(ContextCompat.getColor(this, R.color.success));
        circulo.setBackground(circBg);

        TextView check = new TextView(this);
        check.setText("✓");
        check.setTextSize(36);
        check.setTextColor(android.graphics.Color.WHITE);
        check.setTypeface(null, android.graphics.Typeface.BOLD);
        check.setGravity(android.view.Gravity.CENTER);
        check.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        circulo.addView(check);
        card.addView(circulo);

        TextView titulo = new TextView(this);
        titulo.setText("Horario Confirmado");
        titulo.setTextSize(18);
        titulo.setTextColor(0xFF1A1A1A);
        titulo.setTypeface(null, android.graphics.Typeface.BOLD);
        titulo.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams titP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titP.setMargins(0, 20 * dp, 0, 8 * dp);
        titulo.setLayoutParams(titP);
        card.addView(titulo);

        TextView sub = new TextView(this);
        sub.setText(materiasDetectadas.size() + " materias guardadas correctamente");
        sub.setTextSize(13);
        sub.setTextColor(0xFF888888);
        sub.setGravity(android.view.Gravity.CENTER);
        card.addView(sub);

        overlay.addView(card);
        android.widget.FrameLayout decorView = (android.widget.FrameLayout) getWindow().getDecorView();
        decorView.addView(overlay);

        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(300).start();
        card.setScaleX(0.7f);
        card.setScaleY(0.7f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(350)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();

        overlay.postDelayed(() -> {
            overlay.animate().alpha(0f).setDuration(300).withEndAction(() ->
                    decorView.removeView(overlay)).start();
        }, 2500);
    }

    private void mostrarHorarioSemanal(List<Clase> clases) {
        if (clases == null || clases.isEmpty()) { cardHorarioSemanal.setVisibility(View.GONE); return; }

        List<DiaHorario> diasHorario = agruparPorDia(clases);
        List<DiaHorario> diasConClases = new ArrayList<>();
        for (DiaHorario dia : diasHorario) {
            if (dia.tieneClases()) diasConClases.add(dia);
        }
        if (diasConClases.isEmpty()) { cardHorarioSemanal.setVisibility(View.GONE); return; }

        HorarioPagerAdapter adapter = new HorarioPagerAdapter(diasConClases);
        viewPagerDias.setAdapter(adapter);
        new TabLayoutMediator(tabsDias, viewPagerDias, (tab, position) ->
                tab.setText(obtenerAbreviaturaDia(diasConClases.get(position).getDia()))).attach();
        cardHorarioSemanal.setVisibility(View.VISIBLE);
    }

    private String obtenerAbreviaturaDia(String dia) {
        if (dia == null) return "?";
        switch (dia.toLowerCase()) {
            case "lunes":     return "L";
            case "martes":    return "M";
            case "miércoles": return "Mi";
            case "jueves":    return "J";
            case "viernes":   return "V";
            case "sábado":    return "S";
            case "domingo":   return "D";
            default: return dia.length() > 0 ? dia.substring(0, 1) : "?";
        }
    }

    private List<DiaHorario> agruparPorDia(List<Clase> clases) {
        String[] diasSemana = {"Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"};
        List<DiaHorario> diasHorario = new ArrayList<>();
        for (String dia : diasSemana) {
            DiaHorario diaHorario = new DiaHorario(dia);
            for (Clase clase : clases) {
                if (clase.getDia() != null && clase.getDia().equalsIgnoreCase(dia))
                    diaHorario.agregarClase(clase);
            }
            diasHorario.add(diaHorario);
        }
        return diasHorario;
    }

    private View crearVistaDia(DiaHorario diaHorario) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setBackgroundColor(0xFFFEFCF8);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFFFEFCF8);
        layout.setPadding(20, 20, 20, 28);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout hdrDia = new LinearLayout(this);
        hdrDia.setOrientation(LinearLayout.HORIZONTAL);
        hdrDia.setGravity(android.view.Gravity.CENTER_VERTICAL);
        hdrDia.setBackgroundColor(0xFFFEFCF8);
        LinearLayout.LayoutParams hdrP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hdrP.setMargins(0, 0, 0, 14);
        hdrDia.setLayoutParams(hdrP);

        TextView txtDia = new TextView(this);
        txtDia.setText(diaHorario.getDia().toUpperCase());
        txtDia.setTextSize(12);
        txtDia.setTextColor(0xFF1A1A1A);
        txtDia.setTypeface(null, android.graphics.Typeface.BOLD);
        txtDia.setLetterSpacing(0.1f);
        txtDia.setBackgroundColor(0xFFFEFCF8);
        hdrDia.addView(txtDia);

        int numClases = diaHorario.getClases().size();
        TextView cntClases = new TextView(this);
        cntClases.setText("  ·  " + numClases + (numClases == 1 ? " clase" : " clases"));
        cntClases.setTextSize(11);
        cntClases.setTextColor(0xFFAAAAAA);
        cntClases.setBackgroundColor(0xFFFEFCF8);
        hdrDia.addView(cntClases);
        layout.addView(hdrDia);

        List<Clase> clasesOrdenadas = new ArrayList<>(diaHorario.getClases());
        Collections.sort(clasesOrdenadas, (c1, c2) -> {
            String h1 = c1.getHoraInicio() != null ? c1.getHoraInicio() : "";
            String h2 = c2.getHoraInicio() != null ? c2.getHoraInicio() : "";
            return h1.compareTo(h2);
        });
        for (Clase clase : clasesOrdenadas) layout.addView(crearVistaClaseHorario(clase));

        scroll.addView(layout);
        return scroll;
    }

    private View crearVistaClaseHorario(Clase clase) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18, 16, 18, 16);

        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(20f);
        cardBg.setColor(0xFFFFFFFF);
        cardBg.setStroke(1, 0xFFEDE8DF);
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardP.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardP);

        String horaTexto = (clase.getHoraInicio() != null ? clase.getHoraInicio() : "??")
                + " - " + (clase.getHoraFin() != null ? clase.getHoraFin() : "??");
        TextView txtHora = new TextView(this);
        txtHora.setText(horaTexto);
        txtHora.setTextSize(13);
        txtHora.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        txtHora.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(txtHora);

        TextView txtMateria = new TextView(this);
        txtMateria.setText(clase.getMateria() != null ? clase.getMateria() : "Sin nombre");
        txtMateria.setTextSize(15);
        txtMateria.setTextColor(0xFF1A1A1A);
        txtMateria.setTypeface(null, android.graphics.Typeface.BOLD);
        txtMateria.setMaxLines(2);
        txtMateria.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams matP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        matP.setMargins(0, 6, 0, 0);
        txtMateria.setLayoutParams(matP);
        card.addView(txtMateria);

        if ((clase.getGrupo() != null && !clase.getGrupo().isEmpty())
                || (clase.getSalon() != null && !clase.getSalon().isEmpty())) {
            TextView txtDetalle = new TextView(this);
            StringBuilder det = new StringBuilder();
            if (clase.getGrupo() != null && !clase.getGrupo().isEmpty()) det.append(clase.getGrupo());
            if (clase.getSalon() != null && !clase.getSalon().isEmpty()) {
                if (det.length() > 0) det.append("  •  ");
                det.append(clase.getSalon());
            }
            txtDetalle.setText(det.toString());
            txtDetalle.setTextSize(12);
            txtDetalle.setTextColor(0xFF999999);
            LinearLayout.LayoutParams detP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            detP.setMargins(0, 4, 0, 0);
            txtDetalle.setLayoutParams(detP);
            card.addView(txtDetalle);
        }
        return card;
    }

    private void aplicarEstadoConfirmado() {
        btnAgregarMateria.setVisibility(View.GONE);
        btnConfirmarMaterias.setVisibility(View.GONE);
        btnEscanearNuevo.setVisibility(View.GONE);
        textoTotalMaterias.setText(materiasDetectadas.size() + " materias confirmadas");
        textoTotalMaterias.setTextColor(ContextCompat.getColor(this, R.color.success));
        actualizarListaMaterias();
    }

    private void guardarMaterias() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : "";

        Gson gson = new Gson();
        getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                .edit()
                .putString("horario_materias",         gson.toJson(materiasDetectadas))
                .putString("horario_clases_completas",  gson.toJson(clasesCompletas))
                .putInt("horario_total_materias",       materiasDetectadas.size())
                .putString("horario_periodo",           periodoSeleccionado)
                .putString("horario_vigencia",          vigenciaDetectada)
                .putString("horario_owner_uid",         uid)
                .apply();
        Log.i(TAG, "Guardadas: " + materiasDetectadas.size() + " materias, "
                + clasesCompletas.size() + " clases, owner=" + uid);
    }

    private void cargarNombreProfesor() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String nombre = doc.getString("nombre");
                        if (nombre == null || nombre.isEmpty())
                            nombre = doc.getString("nombreCompleto");
                        nombreProfesor = (nombre != null && !nombre.isEmpty()) ? nombre
                                : (user.getEmail() != null ? user.getEmail() : "");
                    } else {
                        nombreProfesor = user.getEmail() != null ? user.getEmail() : "";
                    }
                    if (cardPeriodoVigencia != null &&
                            cardPeriodoVigencia.getVisibility() == View.VISIBLE) {
                        actualizarInfoPeriodo();
                    }
                })
                .addOnFailureListener(e -> {
                    nombreProfesor = user.getDisplayName() != null ? user.getDisplayName()
                            : (user.getEmail() != null ? user.getEmail() : "");
                    if (cardPeriodoVigencia != null &&
                            cardPeriodoVigencia.getVisibility() == View.VISIBLE) {
                        actualizarInfoPeriodo();
                    }
                });
    }

    private void guardarHorarioFirestore() {
        if (clasesCompletas == null || materiasDetectadas == null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        java.util.List<java.util.Map<String,Object>> materiasData = new java.util.ArrayList<>();
        for (Clase mat : materiasDetectadas) {
            java.util.Map<String,Object> mMap = new java.util.HashMap<>();
            mMap.put("materia", mat.getMateria());
            mMap.put("grupo",   mat.getGrupo());
            java.util.List<java.util.Map<String,Object>> sesiones = new java.util.ArrayList<>();
            for (Clase c : clasesCompletas) {
                if (c.getMateria() != null && c.getMateria().equalsIgnoreCase(mat.getMateria())
                        && c.getGrupo() != null && c.getGrupo().equals(mat.getGrupo())) {
                    java.util.Map<String,Object> s = new java.util.HashMap<>();
                    s.put("dia",        c.getDia());
                    s.put("horaInicio", c.getHoraInicio());
                    s.put("horaFin",    c.getHoraFin());
                    s.put("salon",      c.getSalon());
                    sesiones.add(s);
                }
            }
            mMap.put("sesiones", sesiones);
            materiasData.add(mMap);
        }

        java.util.Map<String,Object> doc = new java.util.HashMap<>();
        doc.put("uid",            user.getUid());
        doc.put("nombreProfesor", nombreProfesor.isEmpty() ? user.getEmail() : nombreProfesor);
        doc.put("email",          user.getEmail());
        doc.put("periodo",        periodoSeleccionado);
        doc.put("vigencia",       vigenciaDetectada);
        doc.put("materias",       materiasData);
        doc.put("totalMaterias",  materiasDetectadas.size());
        doc.put("fechaGuardado",  com.google.firebase.Timestamp.now());

        String docId = user.getUid() + "_" + periodoSeleccionado.replace("-","_");
        FirebaseFirestore.getInstance().collection("horarios").document(docId)
                .set(doc)
                .addOnSuccessListener(v -> Log.i(TAG, "Horario guardado en Firestore: " + docId))
                .addOnFailureListener(e -> Log.e(TAG, "Error Firestore: " + e.getMessage()));
    }

    private void escanearNuevo() {
        if (materiasConfirmadas) {
            Toast.makeText(this, "Las materias ya están confirmadas. Elimina el horario para escanear uno nuevo.", Toast.LENGTH_LONG).show();
            return;
        }
        cardMateriasDetectadas.setVisibility(View.GONE);
        cardSinHorario.setVisibility(View.VISIBLE);
        materiasDetectadas = null;
        clasesCompletas = null;
    }

    private void cargarMateriasGuardadas() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);

        // ── Verificar que el horario pertenece al usuario actual ──────────
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uidActual = user != null ? user.getUid()
                : prefs.getString("user_uid", "");
        String uidOwner  = prefs.getString("horario_owner_uid", "");

        if (!uidOwner.isEmpty() && !uidOwner.equals(uidActual)) {
            Log.i(TAG, "Horario de otro usuario detectado — limpiando y buscando en Firestore");
            prefs.edit()
                    .remove("horario_materias")
                    .remove("horario_clases_completas")
                    .remove("horario_total_materias")
                    .remove("materias_confirmadas")
                    .remove("horario_periodo")
                    .remove("horario_vigencia")
                    .remove("horario_owner_uid")
                    .apply();
            cargarHorarioDesdeFirestoreSiNecesario();
            return;
        }

        String materiasJson = prefs.getString("horario_materias", null);
        String clasesJson   = prefs.getString("horario_clases_completas", null);

        try {
            materiasConfirmadas = prefs.getBoolean("materias_confirmadas", false);
        } catch (ClassCastException e) {
            Log.e(TAG, "SharedPreferences corrupto");
            prefs.edit().remove("materias_confirmadas").apply();
            materiasConfirmadas = false;
        }

        periodoSeleccionado = prefs.getString("horario_periodo",  "");
        vigenciaDetectada   = prefs.getString("horario_vigencia", "");

        if (materiasJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Clase>>(){}.getType();
            try {
                materiasDetectadas = gson.fromJson(materiasJson, type);
                clasesCompletas = clasesJson != null
                        ? gson.fromJson(clasesJson, type)
                        : new ArrayList<>(materiasDetectadas != null
                        ? materiasDetectadas : new ArrayList<>());

                if (materiasDetectadas != null && !materiasDetectadas.isEmpty()) {
                    cardSinHorario.setVisibility(View.GONE);
                    cardMateriasDetectadas.setVisibility(View.VISIBLE);
                    if (cardPeriodoVigencia != null) {
                        cardPeriodoVigencia.setVisibility(View.VISIBLE);
                        actualizarInfoPeriodo();
                    }
                    if (materiasConfirmadas) {
                        aplicarEstadoConfirmado();
                        mostrarHorarioSemanal(clasesCompletas);
                    } else {
                        actualizarListaMaterias();
                    }
                } else {
                    // No hay horario local — buscar en Firestore
                    cargarHorarioDesdeFirestoreSiNecesario();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error cargando datos: " + e.getMessage());
                prefs.edit()
                        .remove("horario_materias")
                        .remove("horario_clases_completas")
                        .remove("materias_confirmadas")
                        .remove("horario_owner_uid")
                        .apply();
                Toast.makeText(this, "Se reiniciaron los datos del horario.", Toast.LENGTH_LONG).show();
                cargarHorarioDesdeFirestoreSiNecesario();
            }
        } else {
            // No hay nada local — buscar en Firestore
            cargarHorarioDesdeFirestoreSiNecesario();
        }
    }

    private List<Clase> consolidarMaterias(List<Clase> clases) {
        List<Clase> consolidadas = new ArrayList<>();
        for (Clase clase : clases) {
            if (clase.getMateria() == null || clase.getMateria().trim().isEmpty()) continue;
            String materia = normalizarNombreMateria(clase.getMateria());
            String grupo   = clase.getGrupo() != null
                    ? clase.getGrupo().trim().toUpperCase().replaceAll("\\s+", " ") : "";
            boolean yaExiste = false;
            for (Clase c : consolidadas) {
                String materiaC = normalizarNombreMateria(c.getMateria());
                String grupoC   = c.getGrupo() != null
                        ? c.getGrupo().trim().toUpperCase().replaceAll("\\s+", " ") : "";
                if (materia.equals(materiaC) && grupo.equals(grupoC)) { yaExiste = true; break; }
            }
            if (!yaExiste) consolidadas.add(clase);
        }
        return consolidadas;
    }

    private String normalizarNombreMateria(String nombre) {
        if (nombre == null) return "";
        return nombre.trim().toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private void mostrarError(String error, boolean esRateLimit) {
        cardProcesando.setVisibility(View.GONE);
        cardSinHorario.setVisibility(View.VISIBLE);

        String titulo, mensaje;
        if (esRateLimit) {
            titulo  = "Servicio ocupado";
            mensaje = "El servicio de análisis está saturado.\n\nEspera 1-2 minutos e intenta de nuevo.";
        } else if (error != null && error.contains("No se pudieron extraer clases")) {
            titulo  = "No se detectaron clases";
            mensaje = "No se pudo leer el horario del PDF.\n\nVerifica que el PDF sea legible.";
        } else if (error != null && error.toLowerCase().contains("timeout")) {
            titulo  = "Tiempo agotado";
            mensaje = "La conexión tardó demasiado. Verifica tu internet.";
        } else {
            titulo  = "Error al procesar";
            mensaje = "Ocurrió un error inesperado:\n\n" + error;
        }

        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton("Reintentar", (d, w) -> {
                    if (archivoUri != null) procesarArchivo();
                    else Toast.makeText(this, "Selecciona un PDF primero", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarAvisoInicial() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFEFCF8);
        sv.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(ContextCompat.getColor(this, R.color.green_primary));
        header.setPadding(52, 40, 52, 32);

        TextView ico = new TextView(this);
        ico.setText("!");
        ico.setTextSize(24);
        ico.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        ico.setTypeface(null, android.graphics.Typeface.BOLD);
        ico.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable icoBg = new android.graphics.drawable.GradientDrawable();
        icoBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        icoBg.setColor(0xFFFFFFFF);
        ico.setBackground(icoBg);
        int icoSz = (int)(44 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams icoP = new LinearLayout.LayoutParams(icoSz, icoSz);
        icoP.setMargins(0, 0, 0, 16);
        ico.setLayoutParams(icoP);
        header.addView(ico);

        TextView titulo = new TextView(this);
        titulo.setText("Importante antes de continuar");
        titulo.setTextSize(18);
        titulo.setTextColor(android.graphics.Color.WHITE);
        titulo.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(titulo);

        TextView subtitulo = new TextView(this);
        subtitulo.setText("Lectura OCR — verifica tu horario");
        subtitulo.setTextSize(12);
        subtitulo.setTextColor(0xCCFFFFFF);
        LinearLayout.LayoutParams subP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subP.setMargins(0, 4, 0, 0);
        subtitulo.setLayoutParams(subP);
        header.addView(subtitulo);
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(48, 28, 48, 8);

        String[] puntos = {
                "Nuestra herramienta OCR puede cometer errores o confusiones al leer tu horario. Verifica siempre con el horario oficial entregado por Direccion Academica.",
                "Esta aplicacion funciona unicamente con el formato oficial de la UTFV. Si usas otro formato, la lectura puede no ser correcta.",
                "Te recomendamos tener tu horario impreso o en computadora cerca para comparar e ir corrigiendo dato por dato.",
                "Verifica cada materia individualmente. Una vez confirmada una materia no sera posible editarla.",
                "Si ves dias de la semana repetidos varias veces, no te preocupes. Simplemente elimina los duplicados usando el boton de eliminar en cada sesion."
        };

        for (int i = 0; i < puntos.length; i++) {
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams filaP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            filaP.setMargins(0, 0, 0, 18);
            fila.setLayoutParams(filaP);

            TextView num = new TextView(this);
            num.setText(String.valueOf(i + 1));
            num.setTextSize(11);
            num.setTextColor(android.graphics.Color.WHITE);
            num.setTypeface(null, android.graphics.Typeface.BOLD);
            num.setGravity(android.view.Gravity.CENTER);
            android.graphics.drawable.GradientDrawable numBg = new android.graphics.drawable.GradientDrawable();
            numBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            numBg.setColor(ContextCompat.getColor(this, R.color.green_primary));
            num.setBackground(numBg);
            int numSz = (int)(26 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams numP = new LinearLayout.LayoutParams(numSz, numSz);
            numP.setMargins(0, 2, 14, 0);
            num.setLayoutParams(numP);
            fila.addView(num);

            TextView txt = new TextView(this);
            txt.setText(puntos[i]);
            txt.setTextSize(13);
            txt.setTextColor(0xFF333333);
            txt.setLineSpacing(4, 1f);
            txt.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            fila.addView(txt);
            body.addView(fila);
        }
        root.addView(body);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(48, 8, 48, 36);

        android.widget.Button btnOk = new android.widget.Button(this);
        btnOk.setText("Entendido, continuar");
        btnOk.setAllCaps(false);
        btnOk.setTextSize(15);
        btnOk.setTextColor(android.graphics.Color.WHITE);
        btnOk.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(40f);
        btnBg.setColor(ContextCompat.getColor(this, R.color.green_primary));
        btnOk.setBackground(btnBg);
        int btnH = (int)(52 * getResources().getDisplayMetrics().density);
        btnOk.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, btnH));
        btnOk.setOnClickListener(v -> dialog.dismiss());
        footer.addView(btnOk);
        root.addView(footer);

        dialog.setContentView(sv);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int)(getResources().getDisplayMetrics().heightPixels * 0.82f));
            android.graphics.drawable.GradientDrawable dBg = new android.graphics.drawable.GradientDrawable();
            dBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            dBg.setCornerRadius(40f);
            dBg.setColor(0xFFFEFCF8);
            dialog.getWindow().setBackgroundDrawable(dBg);
        }
        dialog.show();
    }

    private void configurarBottomNavigation() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_horario);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_horario)  return true;
            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, PanelPrincipalActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish(); return true;
            }
            if (id == R.id.nav_reportes) {
                startActivity(new Intent(this, Reportes.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish(); return true;
            }
            if (id == R.id.nav_perfil) {
                startActivity(new Intent(this, Perfil.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish(); return true;
            }
            if (id == R.id.nav_lista) {
                startActivity(new Intent(this, Lista.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish(); return true;
            }
            return false;
        });
    }
}