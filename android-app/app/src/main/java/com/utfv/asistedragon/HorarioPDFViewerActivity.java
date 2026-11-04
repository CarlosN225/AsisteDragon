package com.utfv.asistedragon;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pantalla de revisión de horario:
 * - Mitad superior: imagen del PDF (primera página)
 * - Mitad inferior: Bottom Sheet con materias detectadas para editar/confirmar
 */
public class HorarioPDFViewerActivity extends AppCompatActivity {

    private static final String TAG = "PDFViewer";

    public static final String EXTRA_PDF_IMAGEN   = "pdf_imagen_path";
    public static final String EXTRA_CLASES_JSON  = "clases_json";

    private List<Clase> clasesCompletas;
    private List<Clase> materiasDetectadas;
    private String      nombrePdf = "horario.pdf";

    private LinearLayout containerMaterias;

    // ── Zoom fields ───────────────────────────────────────────────────────
    private ImageView imgPdf;
    private Matrix     matrizImagen    = new Matrix();
    private float      escalaActual    = 1f;
    private float      escalaMin       = 1f;
    private float      escalaMax       = 5f;
    private float      lastTouchX, lastTouchY;
    private boolean    arrastrando     = false;
    private ScaleGestureDetector scaleDetector;
    private TextView txtConteo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Status bar verde
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.green_primary));

        setContentView(R.layout.activity_horario_pdfviewer);

        // ── Cargar datos del Intent ───────────────────────────────────────
        String clasesJson = getIntent().getStringExtra(EXTRA_CLASES_JSON);
        String imagenPath = getIntent().getStringExtra(EXTRA_PDF_IMAGEN);

        if (clasesJson == null) {
            Toast.makeText(this, "Error: no hay datos de clases", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (getIntent().hasExtra("nombre_pdf")) {
            nombrePdf = getIntent().getStringExtra("nombre_pdf");
        }

        Gson gson = new Gson();
        Type type = new TypeToken<List<Clase>>(){}.getType();
        clasesCompletas   = gson.fromJson(clasesJson, type);
        materiasDetectadas = consolidarMaterias(clasesCompletas);

        // ── Imagen del PDF con Zoom ───────────────────────────────────────
        imgPdf = findViewById(R.id.img_pdf_preview);
        imgPdf.setScaleType(ImageView.ScaleType.MATRIX);

        if (imagenPath != null) {
            Bitmap bmp = BitmapFactory.decodeFile(imagenPath);
            if (bmp != null) {
                imgPdf.setImageBitmap(bmp);
                // Centrar imagen al iniciar
                imgPdf.post(() -> centrarImagen(bmp));
            }
        } else {
            imgPdf.setBackgroundColor(android.graphics.Color.WHITE);
            imgPdf.setImageResource(android.R.drawable.ic_menu_report_image);
        }

        // ── Pinch-to-zoom ─────────────────────────────────────────────────
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float factor      = detector.getScaleFactor();
                        float nuevaEscala = Math.max(escalaMin, Math.min(escalaActual * factor, escalaMax));
                        float escalaReal  = nuevaEscala / escalaActual;
                        escalaActual      = nuevaEscala;
                        matrizImagen.postScale(escalaReal, escalaReal,
                                detector.getFocusX(), detector.getFocusY());
                        imgPdf.setImageMatrix(matrizImagen);
                        return true;
                    }
                });

        // ── Detector de doble tap (+10%) y arrastre libre ─────────────────
        final android.view.GestureDetector gestureDetector =
                new android.view.GestureDetector(this,
                        new android.view.GestureDetector.SimpleOnGestureListener() {

                            // Doble tap → zoom +10%
                            @Override
                            public boolean onDoubleTap(MotionEvent e) {
                                float nuevaEscala = Math.min(escalaActual * 1.10f, escalaMax);
                                float escalaReal  = nuevaEscala / escalaActual;
                                escalaActual      = nuevaEscala;
                                matrizImagen.postScale(escalaReal, escalaReal, e.getX(), e.getY());
                                imgPdf.setImageMatrix(matrizImagen);
                             //   Toast.makeText(HorarioPDFViewerActivity.this, "Zoom " + (int)(escalaActual / escalaMin * 100) + "%", Toast.LENGTH_SHORT).show();
                                return true;
                            }

                            // Arrastre libre en todas direcciones (siempre, incluso sin zoom)
                            @Override
                            public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                                    float distanceX, float distanceY) {
                                if (!scaleDetector.isInProgress()) {
                                    matrizImagen.postTranslate(-distanceX, -distanceY);
                                    imgPdf.setImageMatrix(matrizImagen);
                                }
                                return true;
                            }
                        });
        gestureDetector.setIsLongpressEnabled(true);

        // ── Touch unificado ───────────────────────────────────────────────
        imgPdf.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            return true;
        });

        // Long press → reset zoom
        imgPdf.setOnLongClickListener(v -> {
            if (imgPdf.getDrawable() != null) {
                centrarImagen(((android.graphics.drawable.BitmapDrawable)
                        imgPdf.getDrawable()).getBitmap());
            }
            Toast.makeText(this, "🔍 Zoom restablecido", Toast.LENGTH_SHORT).show();
            return true;
        });

        // ── Bottom Sheet ──────────────────────────────────────────────────
        // Panel inferior fijo — sin BottomSheet

        containerMaterias = findViewById(R.id.bs_container_materias);
        txtConteo         = findViewById(R.id.bs_txt_conteo);

        renderMaterias();

        // ── Botón Confirmar — estado visual según materias confirmadas ──
        android.widget.Button btnConfirmarTodo = findViewById(R.id.bs_btn_confirmar);
        btnConfirmarTodo.setOnClickListener(v -> confirmarYRegresar());
        actualizarEstadoBotonConfirmar(btnConfirmarTodo);

        // ── Botón Agregar materia → abre formulario directamente ────────
        findViewById(R.id.bs_btn_agregar).setOnClickListener(v -> mostrarDialogoAgregarMateriaViewer());

        // ── Botón volver — confirmar salida ──────────────────────────────
        findViewById(R.id.bs_btn_volver).setOnClickListener(v -> confirmarSalida());
    }

    // ── Render lista de materias en el Bottom Sheet ───────────────────────

    private void renderMaterias() {
        materiasDetectadas = consolidarMaterias(clasesCompletas);
        containerMaterias.removeAllViews();
        txtConteo.setText(materiasDetectadas.size() + " materias detectadas");

        for (int i = 0; i < materiasDetectadas.size(); i++) {
            final int idx = i;
            Clase materia = materiasDetectadas.get(i);
            View card = crearTarjetaMateria(materia, idx);
            containerMaterias.addView(card);
        }
    }

    private void actualizarListaMaterias() {
        renderMaterias();
        // Refrescar estado del botón Confirmar
        android.widget.Button btn = findViewById(R.id.bs_btn_confirmar);
        if (btn != null) actualizarEstadoBotonConfirmar(btn);
    }

    private void actualizarEstadoBotonConfirmar(android.widget.Button btn) {
        if (materiasDetectadas == null) return;
        long sinConfirmar = materiasDetectadas.stream()
                .filter(c -> !c.isConfirmada()).count();
        if (sinConfirmar == 0) {
            // Todas confirmadas — botón verde activo
            btn.setEnabled(true);
            btn.setAlpha(1f);
            btn.setText("Confirmar");
            btn.setBackgroundTintList(
                    androidx.core.content.ContextCompat.getColorStateList(this, R.color.green_primary));
        } else {
            // Hay sin confirmar — botón gris semitransparente
            btn.setEnabled(true); // sigue clickeable para mostrar el aviso
            btn.setAlpha(0.5f);
            btn.setText("Confirmar (" + sinConfirmar + " pend.)");
            btn.setBackgroundTintList(
                    androidx.core.content.ContextCompat.getColorStateList(this, R.color.gray_medium));
        }
    }

    private View crearTarjetaMateria(Clase materia, int index) {
        boolean confirmada = materia.isConfirmada();

        // ── Card principal ────────────────────────────────────────────────
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(0, 0, 0, 0);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(16f);
        if (confirmada) {
            // Fondo gris muy suave — aspecto "deshabilitado/bloqueado"
            bg.setColor(0xFFF2F2F2);
            bg.setStroke(1, 0xFFDDDDDD);
        } else {
            bg.setColor(0xFFFEFCF8);
            bg.setStroke(1, 0xFFE8E8E8);
        }
        card.setBackground(bg);
        // Opacidad reducida para efecto deshabilitado
        if (confirmada) card.setAlpha(0.72f);

        LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardP.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cardP);

        // ── Barra izquierda: verde si activo, gris si confirmado ──────────
        View barraColor = new View(this);
        barraColor.setBackgroundColor(confirmada
                ? 0xFFBBBBBB
                : ContextCompat.getColor(this, R.color.green_primary));
        LinearLayout.LayoutParams barraP = new LinearLayout.LayoutParams(5,
                LinearLayout.LayoutParams.MATCH_PARENT);
        barraColor.setLayoutParams(barraP);
        card.addView(barraColor);

        // ── Contenido central ─────────────────────────────────────────────
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(20, 14, 12, 14);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Nombre materia — gris si confirmada
        TextView txtNombre = new TextView(this);
        txtNombre.setText(materia.getMateria());
        txtNombre.setTextSize(13);
        txtNombre.setTextColor(confirmada ? 0xFF999999 : 0xFF1A1A1A);
        txtNombre.setTypeface(null, confirmada
                ? android.graphics.Typeface.NORMAL
                : android.graphics.Typeface.BOLD);
        txtNombre.setMaxLines(2);
        txtNombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(txtNombre);

        // Fila: chip grupo + sesiones
        LinearLayout filaInfo = new LinearLayout(this);
        filaInfo.setOrientation(LinearLayout.HORIZONTAL);
        filaInfo.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams filaP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        filaP.setMargins(0, 5, 0, 0);
        filaInfo.setLayoutParams(filaP);

        // Chip grupo
        TextView chipGrupo = new TextView(this);
        chipGrupo.setText(materia.getGrupo());
        chipGrupo.setTextSize(10);
        chipGrupo.setTextColor(confirmada ? 0xFF999999
                : ContextCompat.getColor(this, R.color.green_primary));
        chipGrupo.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
        chipBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        chipBg.setCornerRadius(30f);
        chipBg.setColor(confirmada ? 0x10888888 : 0x15228B22);
        chipBg.setStroke(1, confirmada ? 0xFFCCCCCC
                : ContextCompat.getColor(this, R.color.green_primary));
        chipGrupo.setBackground(chipBg);
        chipGrupo.setPadding(16, 4, 16, 4);
        filaInfo.addView(chipGrupo);

        // Dot
        TextView dot = new TextView(this);
        dot.setText("·");
        dot.setTextSize(14);
        dot.setTextColor(0xFFCCCCCC);
        LinearLayout.LayoutParams dotP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dotP.setMargins(10, 0, 10, 0);
        dot.setLayoutParams(dotP);
        filaInfo.addView(dot);

        // Sesiones
        long sesiones = clasesCompletas.stream().filter(c ->
                c.getMateria() != null &&
                        c.getMateria().equalsIgnoreCase(materia.getMateria()) &&
                        c.getGrupo() != null &&
                        c.getGrupo().equals(materia.getGrupo())).count();
        TextView txtSes = new TextView(this);
        txtSes.setText(sesiones + (sesiones == 1 ? " sesion" : " sesiones"));
        txtSes.setTextSize(10);
        txtSes.setTextColor(confirmada ? 0xFFBBBBBB : 0xFF999999);
        filaInfo.addView(txtSes);
        textCol.addView(filaInfo);

        // Si confirmada: mostrar etiqueta "Bloqueada" pequeña
        if (confirmada) {
            TextView lblBloq = new TextView(this);
            lblBloq.setText("Confirmada · bloqueada");
            lblBloq.setTextSize(9);
            lblBloq.setTextColor(0xFF999999);
            lblBloq.setTypeface(null, android.graphics.Typeface.ITALIC);
            LinearLayout.LayoutParams lblP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lblP.setMargins(0, 4, 0, 0);
            lblBloq.setLayoutParams(lblP);
            textCol.addView(lblBloq);
        }

        card.addView(textCol);

        // ── Indicador derecho ─────────────────────────────────────────────
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(android.view.Gravity.CENTER);
        rightCol.setPadding(0, 0, 16, 0);
        rightCol.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        if (confirmada) {
            // Icono candado visual — círculo con línea
            LinearLayout lockIcon = new LinearLayout(this);
            lockIcon.setOrientation(LinearLayout.VERTICAL);
            lockIcon.setGravity(android.view.Gravity.CENTER);
            android.graphics.drawable.GradientDrawable lockBg = new android.graphics.drawable.GradientDrawable();
            lockBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            lockBg.setCornerRadius(20f);
            lockBg.setColor(0xFFE8E8E8);
            lockIcon.setBackground(lockBg);
            lockIcon.setPadding(10, 8, 10, 8);

            TextView lockTxt = new TextView(this);
            lockTxt.setText("OK");
            lockTxt.setTextSize(9);
            lockTxt.setTextColor(0xFF999999);
            lockTxt.setTypeface(null, android.graphics.Typeface.BOLD);
            lockTxt.setGravity(android.view.Gravity.CENTER);
            lockIcon.addView(lockTxt);
            rightCol.addView(lockIcon);
        } else {
            // Botón ✕ eliminar materia — igual que en sesiones de clase
            android.widget.Button btnElim = new android.widget.Button(this);
            btnElim.setText("✕");
            btnElim.setTextColor(0xFFBBBBBB);
            btnElim.setTextSize(11);
            btnElim.setTypeface(null, android.graphics.Typeface.BOLD);
            btnElim.setAllCaps(false);
            android.graphics.drawable.GradientDrawable elimBg = new android.graphics.drawable.GradientDrawable();
            elimBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            elimBg.setColor(0xFFEEEEEE);
            btnElim.setBackground(elimBg);
            int elimSz = (int)(26 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams elimP2 = new LinearLayout.LayoutParams(elimSz, elimSz);
            elimP2.setMargins(6, 0, 0, 0);
            btnElim.setLayoutParams(elimP2);
            btnElim.setPadding(0, 0, 0, 0);
            btnElim.setOnClickListener(v -> {
                String msgElim = "Materia:  " + materia.getMateria() + "\n"
                        + "Grupo:    " + materia.getGrupo() + "\n\n"
                        + "¿Deseas eliminar esta materia y todas sus sesiones?";
                new AlertDialog.Builder(HorarioPDFViewerActivity.this)
                        .setTitle("Eliminar materia")
                        .setMessage(msgElim)
                        .setPositiveButton("Si, eliminar", (d2, w) -> {
                            // Eliminar de clasesCompletas todas las sesiones
                            clasesCompletas.removeIf(c ->
                                    c.getMateria() != null &&
                                            c.getMateria().equalsIgnoreCase(materia.getMateria()) &&
                                            c.getGrupo() != null &&
                                            c.getGrupo().equals(materia.getGrupo()));
                            actualizarListaMaterias();
                        })
                        .setNegativeButton("No", null)
                        .show();
            });
            rightCol.addView(btnElim);
        }
        card.addView(rightCol);

        // ── Click solo si NO está confirmada ─────────────────────────────
        if (!confirmada) {
            card.setClickable(true);
            card.setFocusable(true);
            android.util.TypedValue ripple = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            card.setForeground(ContextCompat.getDrawable(this, ripple.resourceId));
            card.setOnClickListener(v -> mostrarDialogoEditarClases(materia));
        } else {
            card.setClickable(false);
            card.setFocusable(false);
        }

        return card;
    }

    // ── Agregar materia desde el viewer ──────────────────────────────────

    private void mostrarDialogoAgregarMateriaViewer() {
        final String[] DIAS  = {"Lunes","Martes","Miércoles","Jueves","Viernes","Sábado"};
        final String[] HORAS = {
                "07:00","08:00","09:00","10:00","11:00","12:00",
                "13:00","14:00","15:00","16:00","17:00","18:00",
                "19:00","20:00","21:00","22:00"
        };
        final String[] AULAS = {
                "D-100","D-DIR","D-101","D-102","D-103","D-104","D-105","D-106",
                "D-107","D-108","D-109","D-110","D-111",
                "D-201","D-202","D-203","D-204","D-205",
                "D-206","D-207","D-208","D-209","D-210","Otra"
        };
        final int[] selDia    = {0};
        final int[] selInicio = {9};
        final int[] selFin    = {11};

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.WHITE);
        sv.addView(root);

        // Header
        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.VERTICAL);
        hdr.setBackgroundColor(ContextCompat.getColor(this, R.color.green_primary));
        hdr.setPadding(56, 48, 56, 36);
        TextView hdrTitle = new TextView(this);
        hdrTitle.setText("Nueva Materia");
        hdrTitle.setTextSize(20);
        hdrTitle.setTextColor(android.graphics.Color.WHITE);
        hdrTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        hdr.addView(hdrTitle);
        root.addView(hdr);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(56, 32, 56, 16);

        final android.widget.EditText inputMateria = crearCampoEdicion(body, "Materia", "Ej: Sistemas Operativos", "", false);
        final android.widget.EditText inputGrupo   = crearCampoEdicion(body, "Grupo",   "Ej: DSM 204",             "", false);

        // Spinner Aula
        agregarEtiqueta(body, "Aula");
        android.widget.Spinner spinAula = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adpAula = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, AULAS);
        adpAula.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinAula.setAdapter(adpAula);
        spinAula.setBackground(crearFondoCampo());
        spinAula.setPadding(24, 4, 24, 4);
        spinAula.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        body.addView(spinAula);

        // Spinner Día
        agregarEtiqueta(body, "Día");
        android.widget.Spinner spinDia = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adpDia = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, DIAS);
        adpDia.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinDia.setAdapter(adpDia);
        spinDia.setBackground(crearFondoCampo());
        spinDia.setPadding(24, 4, 24, 4);
        spinDia.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        spinDia.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) { selDia[0] = pos; }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        body.addView(spinDia);

        // Horario
        agregarEtiqueta(body, "Horario");
        final TextView txtErr = new TextView(this);
        txtErr.setTextSize(11);
        txtErr.setTextColor(0xFFE53935);
        txtErr.setVisibility(android.view.View.GONE);
        txtErr.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout filaH = new LinearLayout(this);
        filaH.setOrientation(LinearLayout.HORIZONTAL);
        filaH.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaH.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.Spinner spinIni = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adpIni = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, HORAS);
        adpIni.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinIni.setAdapter(adpIni);
        spinIni.setSelection(9);
        spinIni.setBackground(crearFondoCampo());
        spinIni.setPadding(20, 4, 20, 4);
        LinearLayout.LayoutParams iniP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        iniP.setMargins(0, 0, 8, 0);
        spinIni.setLayoutParams(iniP);

        TextView sep = new TextView(this);
        sep.setText("→");
        sep.setTextSize(18);
        sep.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        sep.setGravity(android.view.Gravity.CENTER);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.Spinner spinFin = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adpFin = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, HORAS);
        adpFin.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinFin.setAdapter(adpFin);
        spinFin.setSelection(11);
        spinFin.setBackground(crearFondoCampo());
        spinFin.setPadding(20, 4, 20, 4);
        LinearLayout.LayoutParams finP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        finP.setMargins(8, 0, 0, 0);
        spinFin.setLayoutParams(finP);

        filaH.addView(spinIni); filaH.addView(sep); filaH.addView(spinFin);
        body.addView(filaH);
        body.addView(txtErr);

        android.widget.AdapterView.OnItemSelectedListener valHoras =
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                        selInicio[0] = spinIni.getSelectedItemPosition();
                        selFin[0]    = spinFin.getSelectedItemPosition();
                        int diff = selFin[0] - selInicio[0];
                        if (diff <= 0) { txtErr.setText("La hora fin debe ser mayor que la hora inicio"); txtErr.setVisibility(android.view.View.VISIBLE); }
                        else if (diff > 3) { txtErr.setText("Máximo 3 horas por sesión"); txtErr.setVisibility(android.view.View.VISIBLE); }
                        else { txtErr.setVisibility(android.view.View.GONE); }
                    }
                    public void onNothingSelected(android.widget.AdapterView<?> p) {}
                };
        spinIni.setOnItemSelectedListener(valHoras);
        spinFin.setOnItemSelectedListener(valHoras);
        root.addView(body);

        LinearLayout filaBtns = new LinearLayout(this);
        filaBtns.setOrientation(LinearLayout.HORIZONTAL);
        filaBtns.setPadding(56, 8, 56, 40);
        filaBtns.setGravity(android.view.Gravity.END);

        android.widget.Button btnCancel = new android.widget.Button(this);
        btnCancel.setText("Cancelar");
        btnCancel.setTextColor(ContextCompat.getColor(this, R.color.gray_medium));
        btnCancel.setBackground(null);
        btnCancel.setTextSize(14);
        filaBtns.addView(btnCancel);

        android.widget.Button btnAdd = new android.widget.Button(this);
        btnAdd.setText("Agregar");
        btnAdd.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        btnAdd.setBackground(null);
        btnAdd.setTextSize(14);
        btnAdd.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams addP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addP.setMargins(16, 0, 0, 0);
        btnAdd.setLayoutParams(addP);
        filaBtns.addView(btnAdd);
        root.addView(filaBtns);

        android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dlg.setContentView(sv);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            android.graphics.drawable.GradientDrawable dlgBg = new android.graphics.drawable.GradientDrawable();
            dlgBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            dlgBg.setCornerRadius(40f);
            dlgBg.setColor(android.graphics.Color.WHITE);
            dlg.getWindow().setBackgroundDrawable(dlgBg);
        }

        btnCancel.setOnClickListener(v -> dlg.dismiss());
        btnAdd.setOnClickListener(v -> {
            String nombre = inputMateria.getText().toString().trim();
            String grupo  = inputGrupo.getText().toString().trim();
            if (nombre.isEmpty()) { inputMateria.setError("Requerido"); inputMateria.requestFocus(); return; }
            if (grupo.isEmpty())  { inputGrupo.setError("Requerido");   inputGrupo.requestFocus();  return; }
            int diff = selFin[0] - selInicio[0];
            if (diff <= 0 || diff > 3) { txtErr.setVisibility(android.view.View.VISIBLE); return; }

            Clase nueva = new Clase();
            nueva.setMateria(nombre);
            nueva.setGrupo(grupo);
            nueva.setDia(DIAS[selDia[0]]);
            nueva.setHoraInicio(HORAS[selInicio[0]]);
            nueva.setHoraFin(HORAS[selFin[0]]);
            nueva.setSalon(spinAula.getSelectedItem().toString().equals("Otra") ? "" : spinAula.getSelectedItem().toString());
            clasesCompletas.add(nueva);
            actualizarListaMaterias();
            dlg.dismiss();
        });
        dlg.show();
    }

    // ── Confirmar todas y regresar a Horario ─────────────────────────────

    private void confirmarYRegresar() {
        // Verificar que todas estén confirmadas
        long sinConfirmar = materiasDetectadas.stream()
                .filter(c -> !c.isConfirmada()).count();

        if (sinConfirmar > 0) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Materias sin confirmar")
                    .setMessage("Tienes " + sinConfirmar + " materia(s) sin confirmar individualmente.\n\n"
                            + "¿Estas seguro que deseas confirmar tu horario?\n\n"
                            + "Las materias sin confirmar se bloquearán todas.")
                    .setPositiveButton("Si, confirmar", (d, w) -> {
                        for (Clase c : clasesCompletas) c.setConfirmada(true);
                        devolverResultado();
                    })
                    .setNeutralButton("Revisar", null)
                    .setNegativeButton("No", null)
                    .show();
        } else {
            devolverResultado();
        }
    }

    private void devolverResultado() {
        Intent result = new Intent();
        result.putExtra("clases_json", new Gson().toJson(clasesCompletas));
        result.putExtra("confirmadas", true);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        confirmarSalida();
    }

    private void confirmarSalida() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Capturando horario")
                .setMessage("Se esta capturando el archivo:\n\n"
                        + nombrePdf +  "\n\n"
                        + "¿Deseas continuar editando o cancelar el proceso?")
                .setPositiveButton("Continuar editando", (d, w) -> {
                    // No hace nada — regresa a seguir editando
                })
                .setNegativeButton("Cancelar proceso", (d, w) -> {
                    // Regresa a Horario sin guardar
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    // ── Aviso de precisión OCR ────────────────────────────────────────────

    private void mostrarAvisoOCR() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false); // no se cierra tocando fuera

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFEFCF8);
        sv.addView(root);

        // ── Header verde ──────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(ContextCompat.getColor(this, R.color.green_primary));
        header.setPadding(52, 40, 52, 32);

        // Icono advertencia
        TextView ico = new TextView(this);
        ico.setText("!");
        ico.setTextSize(28);
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

        // ── Cuerpo con puntos ─────────────────────────────────────────────
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(48, 28, 48, 8);

        String[] puntos = {
                "Nuestra herramienta OCR puede cometer errores o confusiones al leer tu horario. Verifica siempre con el horario oficial entregado por Direccion Academica.",
                "Esta aplicacion funciona unicamente con el formato oficial de la UTFV. Si usas otro formato, la lectura puede no ser correcta.",
                "Te recomendamos tener tu horario impreso o en computadora cerca para comparar e ir corrigiendo dato por dato.",
                "Verifica cada materia individualmente. Una vez confirmada una materia no sera posible editarla.",
                "Si ves dias de la semana repetidos varias veces, no te preocupes. Simplemente elimina los duplicados usando el boton de eliminar en cada sesion.",
        };

        for (int i = 0; i < puntos.length; i++) {
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams filaP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            filaP.setMargins(0, 0, 0, 18);
            fila.setLayoutParams(filaP);

            // Número del punto
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

            // Texto del punto
            TextView txt = new TextView(this);
            txt.setText(puntos[i]);
            txt.setTextSize(13);
            txt.setTextColor(0xFF333333);
            txt.setLineSpacing(4, 1f);
            txt.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            fila.addView(txt);
            body.addView(fila);
        }

        root.addView(body);

        // ── Botón Entendido ───────────────────────────────────────────────
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
        btnOk.setPadding(0, 0, 0, 0);
        int btnH = (int)(52 * getResources().getDisplayMetrics().density);
        btnOk.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, btnH));
        btnOk.setOnClickListener(v -> dialog.dismiss());
        footer.addView(btnOk);
        root.addView(footer);

        // ── Mostrar dialog ────────────────────────────────────────────────
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

    // ── Centrar imagen al inicio ─────────────────────────────────────────

    private void centrarImagen(Bitmap bmp) {
        if (bmp == null || imgPdf.getWidth() == 0) return;
        matrizImagen = new Matrix();
        float viewW  = imgPdf.getWidth();
        float viewH  = imgPdf.getHeight();
        float bmpW   = bmp.getWidth();
        float bmpH   = bmp.getHeight();
        // Ajustar para que ocupe el ancho completo
        float scale  = viewW / bmpW;
        escalaMin    = scale;
        escalaActual = scale;
        // Centrar verticalmente
        float offsetY = (viewH - bmpH * scale) / 2f;
        matrizImagen.postScale(scale, scale);
        matrizImagen.postTranslate(0, offsetY);
        imgPdf.setImageMatrix(matrizImagen);
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

        // ── Dialog ────────────────────────────────────────────────────────
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        // ── Root con scroll ───────────────────────────────────────────────
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.WHITE);
        scroll.addView(root);

        // ── Header ────────────────────────────────────────────────────────
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
        root.addView(header);

        // ── Lista de sesiones ─────────────────────────────────────────────
        LinearLayout listaSesiones = new LinearLayout(this);
        listaSesiones.setOrientation(LinearLayout.VERTICAL);
        listaSesiones.setPadding(40, 20, 40, 8);

        // Referencia mutable a la lista para poder actualizar
        final List<Clase>[] clasesMutable = new List[]{new ArrayList<>(clasesDeEstaMateria)};

        // Array de 1 para poder referenciar renderLista dentro de sí mismo
        final java.lang.Runnable[] renderListaRef = new java.lang.Runnable[1];
        renderListaRef[0] = new java.lang.Runnable() {
            @Override public void run() {
                listaSesiones.removeAllViews();
                for (Clase clase : clasesMutable[0]) {
                    LinearLayout tarjeta = new LinearLayout(HorarioPDFViewerActivity.this);
                    tarjeta.setOrientation(LinearLayout.HORIZONTAL);
                    tarjeta.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    tarjeta.setPadding(24, 20, 24, 20);
                    android.graphics.drawable.GradientDrawable tBg = new android.graphics.drawable.GradientDrawable();
                    tBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    tBg.setCornerRadius(18f);
                    tBg.setColor(0xFFF8F8F8);
                    tBg.setStroke(1, 0xFFEEEEEE);
                    tarjeta.setBackground(tBg);
                    LinearLayout.LayoutParams tP = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    tP.setMargins(0, 0, 0, 12);
                    tarjeta.setLayoutParams(tP);

                    // Burbuja día
                    TextView burbuja = new TextView(HorarioPDFViewerActivity.this);
                    String ab = clase.getDia() != null && clase.getDia().length() >= 2
                            ? clase.getDia().substring(0, 2).toUpperCase() : "??";
                    burbuja.setText(ab);
                    burbuja.setTextSize(11);
                    burbuja.setTextColor(android.graphics.Color.WHITE);
                    burbuja.setTypeface(null, android.graphics.Typeface.BOLD);
                    burbuja.setGravity(android.view.Gravity.CENTER);
                    android.graphics.drawable.GradientDrawable bFondo = new android.graphics.drawable.GradientDrawable();
                    bFondo.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    bFondo.setColor(ContextCompat.getColor(HorarioPDFViewerActivity.this, R.color.green_primary));
                    burbuja.setBackground(bFondo);
                    LinearLayout.LayoutParams bP = new LinearLayout.LayoutParams(72, 72);
                    bP.setMargins(0, 0, 20, 0);
                    burbuja.setLayoutParams(bP);
                    tarjeta.addView(burbuja);

                    // Info
                    LinearLayout info = new LinearLayout(HorarioPDFViewerActivity.this);
                    info.setOrientation(LinearLayout.VERTICAL);
                    info.setLayoutParams(new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    TextView tDia = new TextView(HorarioPDFViewerActivity.this);
                    tDia.setText(clase.getDia() != null ? clase.getDia() : "Sin día");
                    tDia.setTextSize(14);
                    tDia.setTextColor(ContextCompat.getColor(HorarioPDFViewerActivity.this, R.color.black));
                    tDia.setTypeface(null, android.graphics.Typeface.BOLD);
                    info.addView(tDia);
                    TextView tHora = new TextView(HorarioPDFViewerActivity.this);
                    String hText = (clase.getHoraInicio() != null ? clase.getHoraInicio() : "??")
                            + " – " + (clase.getHoraFin() != null ? clase.getHoraFin() : "??");
                    if (clase.getSalon() != null && !clase.getSalon().isEmpty())
                        hText += "  •  " + clase.getSalon();
                    tHora.setText(hText);
                    tHora.setTextSize(12);
                    tHora.setTextColor(ContextCompat.getColor(HorarioPDFViewerActivity.this, R.color.gray_medium));
                    LinearLayout.LayoutParams hP = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    hP.setMargins(0, 3, 0, 0);
                    tHora.setLayoutParams(hP);
                    info.addView(tHora);
                    tarjeta.addView(info);

                    // Botón eliminar directo en la tarjeta
                    android.widget.Button btnDelSesion = new android.widget.Button(HorarioPDFViewerActivity.this);
                    btnDelSesion.setText("✕");
                    btnDelSesion.setTextColor(0xFFBBBBBB);
                    btnDelSesion.setTextSize(10);
                    btnDelSesion.setTypeface(null, android.graphics.Typeface.BOLD);
                    btnDelSesion.setAllCaps(false);
                    android.graphics.drawable.GradientDrawable delBg = new android.graphics.drawable.GradientDrawable();
                    delBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    delBg.setColor(0xFFEEEEEE);
                    btnDelSesion.setBackground(delBg);
                    int delSz = (int)(26 * HorarioPDFViewerActivity.this.getResources().getDisplayMetrics().density);
                    LinearLayout.LayoutParams delP = new LinearLayout.LayoutParams(delSz, delSz);
                    delP.setMargins(8, 0, 0, 0);
                    btnDelSesion.setLayoutParams(delP);
                    btnDelSesion.setPadding(0, 0, 0, 0);
                    tarjeta.addView(btnDelSesion);

                    // Toque en tarjeta → editar
                    tarjeta.setOnClickListener(v -> {
                        dialog.dismiss();
                        editarClaseIndividual(clase);
                    });

                    // Toque en X → confirmar eliminar directamente
                    btnDelSesion.setOnClickListener(v -> {
                        String infoSesion = (clase.getDia() != null ? clase.getDia() : "")
                                + "  " + (clase.getHoraInicio() != null ? clase.getHoraInicio() : "")
                                + " – " + (clase.getHoraFin() != null ? clase.getHoraFin() : "");
                        String diaStr    = clase.getDia()        != null ? clase.getDia()        : "Sin dia";
                        String inicioStr = clase.getHoraInicio() != null ? clase.getHoraInicio() : "??";
                        String finStr    = clase.getHoraFin()    != null ? clase.getHoraFin()    : "??";
                        String aulaStr   = (clase.getSalon() != null && !clase.getSalon().isEmpty())
                                ? clase.getSalon() : "Sin aula";
                        String grupoStr  = clase.getGrupo()      != null ? clase.getGrupo()      : "";
                        String msgElim   = "Materia:  " + clase.getMateria() + "\n"
                                + "Grupo:    " + grupoStr + "\n"
                                + "Dia:      " + diaStr + "\n"
                                + "Horario:  " + inicioStr + " – " + finStr + "\n"
                                + "Aula:     " + aulaStr + "\n\n"
                                + "¿Deseas eliminar esta sesion?";
                        new AlertDialog.Builder(HorarioPDFViewerActivity.this)
                                .setTitle("Eliminar sesion")
                                .setMessage(msgElim)
                                .setPositiveButton("Si, eliminar", (d2, w) -> {
                                    clasesCompletas.remove(clase);
                                    clasesMutable[0].remove(clase);
                                    materiasDetectadas = consolidarMaterias(clasesCompletas);
                                    actualizarListaMaterias();
                                    renderListaRef[0].run();
                                })
                                .setNegativeButton("No", null)
                                .show();
                    });

                    listaSesiones.addView(tarjeta);
                }

                // Botón + Agregar día — siempre al final, sobrevive removeAllViews
                android.widget.Button btnAgr = new android.widget.Button(HorarioPDFViewerActivity.this);
                btnAgr.setText("+ Agregar día");
                btnAgr.setAllCaps(false);
                btnAgr.setTextSize(13);
                btnAgr.setTextColor(ContextCompat.getColor(HorarioPDFViewerActivity.this, R.color.green_primary));
                btnAgr.setTypeface(null, android.graphics.Typeface.BOLD);
                android.graphics.drawable.GradientDrawable agrBg2 = new android.graphics.drawable.GradientDrawable();
                agrBg2.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                agrBg2.setCornerRadius(40f);
                agrBg2.setColor(0xFFE8F5E9);
                agrBg2.setStroke(2, ContextCompat.getColor(HorarioPDFViewerActivity.this, R.color.green_primary));
                btnAgr.setBackground(agrBg2);
                btnAgr.setPadding(0, 0, 0, 0);
                LinearLayout.LayoutParams agrP2 = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (int)(48 * HorarioPDFViewerActivity.this.getResources().getDisplayMetrics().density));
                agrP2.setMargins(0, 12, 0, 4);
                btnAgr.setLayoutParams(agrP2);
                btnAgr.setOnClickListener(v -> {
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
                listaSesiones.addView(btnAgr);
            }
        };
        renderListaRef[0].run();

        root.addView(listaSesiones);

        // ── Fila inferior: Cancelar izq + Confirmar der ─────────────────
        LinearLayout filaFooter = new LinearLayout(this);
        filaFooter.setOrientation(LinearLayout.HORIZONTAL);
        filaFooter.setGravity(android.view.Gravity.CENTER_VERTICAL);
        filaFooter.setPadding(40, 4, 40, 32);

        android.widget.Button btnCancelar = new android.widget.Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(ContextCompat.getColor(this, R.color.gray_medium));
        btnCancelar.setBackground(null);
        btnCancelar.setTextSize(13);
        btnCancelar.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnCancelar.setLayoutParams(cancelP);
        filaFooter.addView(btnCancelar);

        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        filaFooter.addView(spacer);

        android.widget.Button btnConfirmarFooter = new android.widget.Button(this);
        btnConfirmarFooter.setText("Confirmar todo");
        btnConfirmarFooter.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        btnConfirmarFooter.setBackground(null);
        btnConfirmarFooter.setTextSize(13);
        btnConfirmarFooter.setTypeface(null, android.graphics.Typeface.BOLD);
        filaFooter.addView(btnConfirmarFooter);

        btnConfirmarFooter.setOnClickListener(v -> {
            new AlertDialog.Builder(HorarioPDFViewerActivity.this)
                    .setTitle("Confirmar materia")
                    .setMessage("¿Confirmas que " + materiaConsolidada.getMateria()
                            + " (" + materiaConsolidada.getGrupo() + ")"
                            + " con todas sus sesiones está correcta?\n\nSe bloqueará para edición.")
                    .setPositiveButton("Confirmar", (d2, w) -> {
                        for (Clase c : clasesCompletas) {
                            if (c.getMateria() != null &&
                                    c.getMateria().equalsIgnoreCase(materiaConsolidada.getMateria()) &&
                                    c.getGrupo() != null &&
                                    c.getGrupo().equals(materiaConsolidada.getGrupo())) {
                                c.setConfirmada(true);
                            }
                        }
                        actualizarListaMaterias();
                        Toast.makeText(HorarioPDFViewerActivity.this,
                                materiaConsolidada.getMateria() + " confirmada", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Revisar", null)
                    .show();
        });
        root.addView(filaFooter);

        // ── Mostrar dialog ────────────────────────────────────────────────
        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            android.graphics.drawable.GradientDrawable dBg = new android.graphics.drawable.GradientDrawable();
            dBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            dBg.setCornerRadius(40f);
            dBg.setColor(android.graphics.Color.WHITE);
            dialog.getWindow().setBackgroundDrawable(dBg);
        }

        dialog.show();
    }

    /** Crea botón compacto para el header del diálogo */
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

        // ── Datos de listas ───────────────────────────────────────────────
        final String[] DIAS = {"Lunes","Martes","Miércoles","Jueves","Viernes","Sábado"};
        final String[] HORAS = {
                "07:00","08:00","09:00","10:00","11:00","12:00",
                "13:00","14:00","15:00","16:00","17:00","18:00",
                "19:00","20:00","21:00","22:00"
        };

        // Índices actuales
        int diaIdx = 0;
        for (int i = 0; i < DIAS.length; i++) {
            if (DIAS[i].equalsIgnoreCase(clase.getDia())) { diaIdx = i; break; }
        }
        int inicioIdx = 9; // 16:00 por defecto
        for (int i = 0; i < HORAS.length; i++) {
            if (HORAS[i].equals(clase.getHoraInicio())) { inicioIdx = i; break; }
        }
        int finIdx = Math.min(inicioIdx + 2, HORAS.length - 1); // +2h por defecto
        for (int i = 0; i < HORAS.length; i++) {
            if (HORAS[i].equals(clase.getHoraFin())) { finIdx = i; break; }
        }
        final int[] selDia    = {diaIdx};
        final int[] selInicio = {inicioIdx};
        final int[] selFin    = {finIdx};

        // ── Contenedor raíz ───────────────────────────────────────────────
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.WHITE);
        scrollView.addView(root);

        // ── Header verde ──────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(ContextCompat.getColor(this, R.color.green_primary));
        header.setPadding(56, 48, 56, 36);
        TextView txtTitulo = new TextView(this);
        txtTitulo.setText("Editar Clase");
        txtTitulo.setTextSize(22);
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

        // ── Cuerpo ────────────────────────────────────────────────────────
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(56, 32, 56, 16);

        final EditText inputMateria = crearCampoEdicion(body, "Materia", "Ej: Sistemas Operativos", clase.getMateria(), false);
        final EditText inputGrupo   = crearCampoEdicion(body, "Grupo",   "Ej: DSM 204",             clase.getGrupo(),   false);

        final String[] AULAS = {
                "D-100", "D-DIR",
                "D-101", "D-102", "D-103", "D-104", "D-105", "D-106",
                "D-107", "D-108", "D-109", "D-110", "D-111",
                "D-201", "D-202", "D-203", "D-204", "D-205",
                "D-206", "D-207", "D-208", "D-209", "D-210",
                "Otra"
        };

        // Spinner Aula
        agregarEtiqueta(body, "Aula");
        android.widget.Spinner spinnerAula = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapterAula = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, AULAS);
        adapterAula.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAula.setAdapter(adapterAula);
        // Pre-seleccionar el aula actual
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

        // ── Spinner: Día ──────────────────────────────────────────────────
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

        // ── Spinners: Hora inicio → Hora fin ──────────────────────────────
        agregarEtiqueta(body, "Horario");
        // TextView de error de validación
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

        // Listeners con validación máx 3 horas
        android.widget.AdapterView.OnItemSelectedListener validarHoras =
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                        selInicio[0] = spinnerInicio.getSelectedItemPosition();
                        selFin[0]    = spinnerFin.getSelectedItemPosition();
                        int diff = selFin[0] - selInicio[0];
                        if (diff <= 0) {
                            txtErrorHora.setText("⚠️ La hora fin debe ser mayor que la hora inicio");
                            txtErrorHora.setVisibility(android.view.View.VISIBLE);
                        } else if (diff > 3) {
                            txtErrorHora.setText("⚠️ Máximo 3 horas por sesión");
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

        // ── Botones ───────────────────────────────────────────────────────
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

        // ── Dialog ────────────────────────────────────────────────────────
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
            dBg.setColor(android.graphics.Color.WHITE);
            dialog.getWindow().setBackgroundDrawable(dBg);
        }

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnGuardar.setOnClickListener(v -> {
            // Validar horas antes de guardar
            int diff = selFin[0] - selInicio[0];
            if (diff <= 0) {
                txtErrorHora.setText("⚠️ La hora fin debe ser mayor que la hora inicio");
                txtErrorHora.setVisibility(android.view.View.VISIBLE);
                return;
            }
            if (diff > 3) {
                txtErrorHora.setText("⚠️ Máximo 3 horas por sesión");
                txtErrorHora.setVisibility(android.view.View.VISIBLE);
                return;
            }

            String nuevaMateria    = inputMateria.getText().toString().trim();
            String nuevoGrupo      = inputGrupo.getText().toString().trim();
            String nuevoDia        = DIAS[selDia[0]];
            String nuevaHoraInicio = HORAS[selInicio[0]];
            String nuevaHoraFin    = HORAS[selFin[0]];
            String nuevaAula       = spinnerAula.getSelectedItem().toString().equals("Otra") ? "" : spinnerAula.getSelectedItem().toString();

            // ── Confirmación antes de guardar ─────────────────────────────
            String resumen = "Materia:  " + nuevaMateria + "\n"
                    + "Grupo:    " + nuevoGrupo + "\n"
                    + "Dia:      " + nuevoDia + "\n"
                    + "Horario:  " + nuevaHoraInicio + " – " + nuevaHoraFin + "\n"
                    + "Aula:     " + (nuevaAula.isEmpty() ? "Sin aula" : nuevaAula) + "\n\n"
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
                        // ✅ Solo agregar si es sesión nueva (no edición)
                        if (esNueva) clasesCompletas.add(clase);
                        materiasDetectadas = consolidarMaterias(clasesCompletas);
                        actualizarListaMaterias();
                        Toast.makeText(this, esNueva ? "Sesión agregada" : "Clase actualizada", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Revisar", null)
                    .show();
        });

        dialog.show();
    }

    /** Agrega solo la etiqueta encima de un campo */
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

    /** Crea un campo de texto con etiqueta encima y fondo redondeado */
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

    /** Fondo redondeado con borde gris para los campos */
    private android.graphics.drawable.GradientDrawable crearFondoCampo() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(16f);
        bg.setColor(0xFFF5F5F5);
        bg.setStroke(1, 0xFFDDDDDD);
        return bg;
    }

    // ── Consolidar materias ───────────────────────────────────────────────

    private List<Clase> consolidarMaterias(List<Clase> clases) {
        List<Clase> consolidadas = new ArrayList<>();
        for (Clase clase : clases) {
            if (clase.getMateria() == null || clase.getMateria().trim().isEmpty()) continue;
            if (clase.getGrupo()   == null || clase.getGrupo().trim().isEmpty())   continue;
            boolean yaExiste = false;
            for (Clase c : consolidadas) {
                if (c.getMateria().trim().equalsIgnoreCase(clase.getMateria().trim()) &&
                        c.getGrupo().trim().equalsIgnoreCase(clase.getGrupo().trim())) {
                    yaExiste = true; break;
                }
            }
            if (!yaExiste) consolidadas.add(clase);
        }
        return consolidadas;
    }
}