package com.utfv.asistedragon;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class Reportes extends AppCompatActivity {

    private static final String TAG = "Reportes";

    // ── Vistas ────────────────────────────────────────────────────────
    private ScrollView   scrollContenido;
    private LinearLayout contenedorTarjetas;
    private LinearLayout contenedorMaterias;
    private LinearLayout contenedorHistorial;
    private LinearLayout loadingView;
    private LinearLayout layoutSinDatos;
    private TextView     txtMesActual;
    private TextView     txtSinDatos;
    private ImageView    btnCompartir;

    // ── Firebase ──────────────────────────────────────────────────────
    private FirebaseFirestore db;
    private String userUid  = "";
    private String userName = "";

    // ── Periodo ───────────────────────────────────────────────────────
    private String fechaInicioMes = "";
    private String fechaFinMes    = "";

    // ── Datos ─────────────────────────────────────────────────────────
    private int    totalDiasAsistidos     = 0;  // solo hasta ayer (para inasistencias)
    private int    totalDiasAsistidosMes  = 0;  // todos del mes (para mostrar en tarjeta)
    private int    totalClasesCubiertas   = 0;
    private int    totalPuntuales         = 0;
    private int    totalRetardos          = 0;
    private int    totalInasistencias     = 0;
    private int    totalJustificaciones   = 0;
    private String fechaPrimeraAsistencia = "";
    private final List<Map<String, Object>> listaClasesCubiertas = new ArrayList<>();

    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configurarBarraEstado();
        setContentView(R.layout.activity_reportes);

        db = FirebaseFirestore.getInstance();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userUid  = user.getUid();
            userName = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                    .getString("user_nombre", "");
        } else {
            SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
            userUid  = prefs.getString("user_uid",    "");
            userName = prefs.getString("user_nombre", "");
        }

        Log.d(TAG, "UID usado para query: '" + userUid + "'");

        inicializarVistas();
        calcularPeriodoMes();
        configurarBottomNavigation();
        cargarDatos();

        View root = findViewById(android.R.id.content);
        if (root != null) {
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    // ══ CONFIGURACIÓN ════════════════════════════════════════════════

    private void configurarBarraEstado() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(ContextCompat.getColor(this, R.color.green_primary));
    }

    private void inicializarVistas() {
        scrollContenido     = findViewById(R.id.scroll_reportes);
        contenedorTarjetas  = findViewById(R.id.contenedor_tarjetas);
        contenedorMaterias  = findViewById(R.id.contenedor_materias);
        contenedorHistorial = findViewById(R.id.contenedor_historial);
        loadingView         = findViewById(R.id.loading_reportes);
        layoutSinDatos      = findViewById(R.id.layout_sin_datos);
        txtMesActual        = findViewById(R.id.txt_mes_actual);
        txtSinDatos         = findViewById(R.id.txt_sin_datos);
        btnCompartir        = findViewById(R.id.btn_compartir_reporte);
        btnCompartir.setOnClickListener(v -> compartirReporte());
    }

    private void calcularPeriodoMes() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        fechaInicioMes = sdf.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        fechaFinMes = sdf.format(cal.getTime());

        SimpleDateFormat sdfNombre = new SimpleDateFormat("MMMM yyyy", new Locale("es", "MX"));
        String mes = sdfNombre.format(new Date());
        txtMesActual.setText(mes.substring(0, 1).toUpperCase() + mes.substring(1));
    }

    // ══ HELPER: fecha de ayer ════════════════════════════════════════

    private String obtenerFechaAyer() {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    // ══ CARGA DE DATOS ════════════════════════════════════════════════

    private void cargarDatos() {
        if (userUid.isEmpty()) {
            mostrarSinDatos("Usuario no identificado");
            return;
        }

        mostrarLoading(true);

        final boolean[] done = {false, false, false};
        final String ayer = obtenerFechaAyer();

        // ── Asistencias ───────────────────────────────────────────────
        db.collection("asistencias")
                .whereEqualTo("profesorUid", userUid)
                .whereGreaterThanOrEqualTo("fecha", fechaInicioMes)
                .whereLessThanOrEqualTo("fecha", fechaFinMes)
                .get()
                .addOnSuccessListener(query -> {
                    totalDiasAsistidosMes = query.size(); // todos del mes para la tarjeta
                    totalDiasAsistidos    = 0;            // solo hasta ayer para inasistencias
                    totalPuntuales        = 0;
                    totalRetardos         = 0;
                    fechaPrimeraAsistencia = "";

                    for (QueryDocumentSnapshot doc : query) {
                        String fechaDoc = doc.getString("fecha");
                        String estado   = doc.getString("estado");

                        // Puntualidad: contar todos del mes
                        if ("puntual".equals(estado)) totalPuntuales++;
                        else                          totalRetardos++;

                        // Para inasistencias: solo contar asistencias hasta ayer
                        if (fechaDoc != null && fechaDoc.compareTo(ayer) <= 0) {
                            totalDiasAsistidos++;
                        }

                        // Primera asistencia del mes
                        if (fechaDoc != null) {
                            if (fechaPrimeraAsistencia.isEmpty()
                                    || fechaDoc.compareTo(fechaPrimeraAsistencia) < 0) {
                                fechaPrimeraAsistencia = fechaDoc;
                            }
                        }
                    }

                    // Calcular inasistencias
                    if (query.isEmpty() || fechaPrimeraAsistencia.isEmpty()) {
                        totalInasistencias = 0;
                    } else {
                        int diasConClases = calcularDiasConClasesDesde(fechaPrimeraAsistencia);
                        totalInasistencias = diasConClases - totalDiasAsistidos;
                        if (totalInasistencias < 0) totalInasistencias = 0;
                    }

                    Log.d(TAG, "Asistencias hasta ayer: " + totalDiasAsistidos
                            + " | Días con clases: " + calcularDiasConClasesDesde(fechaPrimeraAsistencia)
                            + " | Inasistencias: " + totalInasistencias);

                    done[0] = true;
                    if (done[1] && done[2]) mostrarTodo();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error asistencias: " + e.getMessage());
                    done[0] = true;
                    if (done[1] && done[2]) mostrarTodo();
                });

        // ── Clases cubiertas ──────────────────────────────────────────
        db.collection("clases_cubiertas")
                .whereEqualTo("profesorUid", userUid)
                .whereGreaterThanOrEqualTo("fecha", fechaInicioMes)
                .whereLessThanOrEqualTo("fecha", fechaFinMes)
                .get()
                .addOnSuccessListener(query -> {
                    totalClasesCubiertas = query.size();
                    listaClasesCubiertas.clear();
                    for (QueryDocumentSnapshot doc : query) {
                        listaClasesCubiertas.add(new HashMap<>(doc.getData()));
                    }
                    done[1] = true;
                    if (done[0] && done[2]) mostrarTodo();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error clases: " + e.getMessage());
                    done[1] = true;
                    if (done[0] && done[2]) mostrarTodo();
                });

        // ── Retiros anticipados ───────────────────────────────────────
        db.collection("retiros_anticipados")
                .whereEqualTo("profesorUid", userUid)
                .whereGreaterThanOrEqualTo("fecha", fechaInicioMes)
                .whereLessThanOrEqualTo("fecha", fechaFinMes)
                .get()
                .addOnSuccessListener(query -> {
                    totalJustificaciones = query.size();
                    done[2] = true;
                    if (done[0] && done[1]) mostrarTodo();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error justificaciones: " + e.getMessage());
                    done[2] = true;
                    if (done[0] && done[1]) mostrarTodo();
                });
    }

    // ══ HELPER: días con clases desde primera asistencia hasta ayer ══

    private int calcularDiasConClasesDesde(String fechaDesde) {
        try {
            SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
            String clasesJson = prefs.getString("horario_clases_completas", null);
            if (clasesJson == null || fechaDesde.isEmpty()) return 0;

            Gson gson = new Gson();
            Type type = new TypeToken<List<Clase>>(){}.getType();
            List<Clase> clases = gson.fromJson(clasesJson, type);

            Set<String> diasConClases = new HashSet<>();
            for (Clase c : clases) {
                if (c.getDia() != null) {
                    diasConClases.add(normalizarDia(c.getDia()));
                }
            }
            if (diasConClases.isEmpty()) return 0;

            Log.d(TAG, "Días en horario normalizados: " + diasConClases.toString());

            SimpleDateFormat sdfParse = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdfParse.setTimeZone(TimeZone.getDefault());

            // Inicio = primera asistencia
            Calendar calInicio = Calendar.getInstance(TimeZone.getDefault());
            calInicio.setTime(sdfParse.parse(fechaDesde));
            calInicio.set(Calendar.HOUR_OF_DAY, 0);
            calInicio.set(Calendar.MINUTE, 0);
            calInicio.set(Calendar.SECOND, 0);
            calInicio.set(Calendar.MILLISECOND, 0);

            // Fin = ayer
            Calendar calAyer = Calendar.getInstance(TimeZone.getDefault());
            calAyer.add(Calendar.DAY_OF_MONTH, -1);
            calAyer.set(Calendar.HOUR_OF_DAY, 23);
            calAyer.set(Calendar.MINUTE, 59);
            calAyer.set(Calendar.SECOND, 59);

            if (calInicio.after(calAyer)) return 0;

            SimpleDateFormat sdfDia = new SimpleDateFormat("EEEE", new Locale("es", "MX"));
            sdfDia.setTimeZone(TimeZone.getDefault());

            int count = 0;
            Calendar cal = (Calendar) calInicio.clone();
            while (!cal.after(calAyer)) {
                String nombreDia = normalizarDia(sdfDia.format(cal.getTime()));
                boolean tieneClase = diasConClases.contains(nombreDia);
                Log.d(TAG, "Día " + cal.get(Calendar.DAY_OF_MONTH) + ": '"
                        + nombreDia + "' — en horario: " + tieneClase);
                if (tieneClase) count++;
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return count;

        } catch (Exception e) {
            Log.e(TAG, "Error calcularDias: " + e.getMessage());
            return 0;
        }
    }

    private String normalizarDia(String dia) {
        if (dia == null) return "";
        return dia.toLowerCase()
                .replace("é", "e").replace("á", "a")
                .replace("ó", "o").replace("ú", "u")
                .replace("í", "i").trim();
    }

    // ══ MOSTRAR TODO ══════════════════════════════════════════════════

    private void mostrarTodo() {
        runOnUiThread(() -> {
            mostrarLoading(false);

            if (totalDiasAsistidosMes == 0 && totalClasesCubiertas == 0) {
                mostrarSinDatos("Sin registros este mes");
                return;
            }

            scrollContenido.setVisibility(View.VISIBLE);
            construirTarjetasResumen();
            construirResumenPorMateria();
            construirHistorial();
        });
    }

    // ══ TARJETAS — 3 filas de 2 ══════════════════════════════════════

    private void construirTarjetasResumen() {
        contenedorTarjetas.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;

        // Puntualidad basada en todos los días del mes
        int puntualidad = totalDiasAsistidosMes > 0
                ? (int)((totalPuntuales * 100f) / totalDiasAsistidosMes) : 0;

        // Fila 1: Días asistidos + Puntualidad
        LinearLayout fila1 = crearFila();
        fila1.addView(crearTarjeta(
                String.valueOf(totalDiasAsistidosMes), "Días asistidos",
                R.drawable.ic_calendar_check,
                0xFFE8F5E9, 0xFF1B5E20, dp));
        fila1.addView(crearTarjeta(
                puntualidad + "%", "Puntualidad",
                R.drawable.ic_puntualidad,
                puntualidad >= 80 ? 0xFFE8F5E9 : 0xFFFFF3E0,
                puntualidad >= 80 ? 0xFF1B5E20 : 0xFF7B2D00, dp));
        contenedorTarjetas.addView(fila1);
        contenedorTarjetas.addView(espaciador(dp));

        // Fila 2: Clases cubiertas + Retardos
        LinearLayout fila2 = crearFila();
        fila2.addView(crearTarjeta(
                String.valueOf(totalClasesCubiertas), "Clases cubiertas",
                R.drawable.ic_clases_cubiertas,
                0xFFE3F2FD, 0xFF1565C0, dp));
        fila2.addView(crearTarjeta(
                String.valueOf(totalRetardos), "Retardos",
                R.drawable.ic_retardos,
                totalRetardos == 0 ? 0xFFE8F5E9 : 0xFFFFEBEE,
                totalRetardos == 0 ? 0xFF1B5E20 : 0xFF7B2D00, dp));
        contenedorTarjetas.addView(fila2);
        contenedorTarjetas.addView(espaciador(dp));

        // Fila 3: Inasistencias + Justificaciones
        LinearLayout fila3 = crearFila();
        fila3.addView(crearTarjeta(
                String.valueOf(totalInasistencias), "Inasistencias",
                R.drawable.ic_retardos,
                totalInasistencias == 0 ? 0xFFE8F5E9 : 0xFFFFEBEE,
                totalInasistencias == 0 ? 0xFF1B5E20 : 0xFF7B2D00, dp));
        fila3.addView(crearTarjeta(
                String.valueOf(totalJustificaciones), "Justificaciones",
                R.drawable.ic_puntualidad,
                totalJustificaciones == 0 ? 0xFFE8F5E9 : 0xFFFFF3E0,
                totalJustificaciones == 0 ? 0xFF1B5E20 : 0xFF7B2D00, dp));
        contenedorTarjetas.addView(fila3);
    }

    private LinearLayout crearFila() {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return fila;
    }

    private View espaciador(float dp) {
        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(10 * dp)));
        return sp;
    }

    private View crearTarjeta(String numero, String etiqueta, int iconoRes,
                              int colorFondo, int colorTexto, float dp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding((int)(16*dp), (int)(22*dp), (int)(16*dp), (int)(22*dp));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(0, 0, (int)(8*dp), 0);
        card.setLayoutParams(p);

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20f * dp);
        bg.setColor(colorFondo);
        card.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconoRes);
        icon.setColorFilter(colorTexto, android.graphics.PorterDuff.Mode.SRC_IN);
        int iconSz = (int)(26 * dp);
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(iconSz, iconSz);
        iconP.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(iconP);
        card.addView(icon);

        TextView tvNum = new TextView(this);
        tvNum.setText(numero);
        tvNum.setTextSize(32);
        tvNum.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNum.setTextColor(colorTexto);
        tvNum.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams numP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        numP.setMargins(0, (int)(5*dp), 0, 0);
        tvNum.setLayoutParams(numP);
        card.addView(tvNum);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(etiqueta);
        tvLabel.setTextSize(9);
        tvLabel.setTextColor(colorTexto);
        tvLabel.setAlpha(0.7f);
        tvLabel.setGravity(android.view.Gravity.CENTER);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setAllCaps(true);
        tvLabel.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams lblP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lblP.setMargins(0, (int)(3*dp), 0, 0);
        tvLabel.setLayoutParams(lblP);
        card.addView(tvLabel);

        return card;
    }

    // ══ RESUMEN POR MATERIA ═══════════════════════════════════════════

    private void construirResumenPorMateria() {
        contenedorMaterias.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;

        if (listaClasesCubiertas.isEmpty()) return;

        Map<String, MateriaStats> statsMap = new HashMap<>();
        for (Map<String, Object> clase : listaClasesCubiertas) {
            String materia = (String) clase.get("materia");
            String grupo   = (String) clase.get("grupo");
            if (materia == null) continue;
            String key = materia + "||" + (grupo != null ? grupo : "");
            MateriaStats s = statsMap.containsKey(key)
                    ? statsMap.get(key) : new MateriaStats(materia, grupo);
            s.totalClases++;
            Object retObj = clase.get("retardoMinutos");
            int ret = retObj instanceof Long ? ((Long) retObj).intValue()
                    : retObj instanceof Integer ? (Integer) retObj : 0;
            if (ret > 0) s.retardos++;
            else         s.puntuales++;
            statsMap.put(key, s);
        }

        List<Map.Entry<String, MateriaStats>> entries = new ArrayList<>(statsMap.entrySet());
        Collections.sort(entries, (a, b) ->
                a.getValue().materia.compareTo(b.getValue().materia));

        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                View div = new View(this);
                div.setBackgroundColor(0x10000000);
                div.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                contenedorMaterias.addView(div);
            }
            contenedorMaterias.addView(crearFilaMateria(entries.get(i).getValue(), dp));
        }
    }

    private View crearFilaMateria(MateriaStats stats, float dp) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(android.view.Gravity.CENTER_VERTICAL);
        fila.setPadding((int)(18*dp), (int)(16*dp), (int)(18*dp), (int)(16*dp));
        fila.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView circulo = new TextView(this);
        String inicial = stats.materia != null && !stats.materia.isEmpty()
                ? stats.materia.substring(0, 1).toUpperCase() : "?";
        circulo.setText(inicial);
        circulo.setTextSize(15);
        circulo.setTextColor(0xFFFFFFFF);
        circulo.setTypeface(null, android.graphics.Typeface.BOLD);
        circulo.setGravity(android.view.Gravity.CENTER);
        int sz = (int)(40 * dp);
        LinearLayout.LayoutParams cP = new LinearLayout.LayoutParams(sz, sz);
        cP.setMargins(0, 0, (int)(14*dp), 0);
        circulo.setLayoutParams(cP);
        android.graphics.drawable.GradientDrawable cBg =
                new android.graphics.drawable.GradientDrawable();
        cBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        cBg.setColor(ContextCompat.getColor(this, R.color.green_primary));
        circulo.setBackground(cBg);
        fila.addView(circulo);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvNombre = new TextView(this);
        tvNombre.setText(stats.materia);
        tvNombre.setTextSize(13);
        tvNombre.setTextColor(0xFF1A1A1A);
        tvNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNombre.setMaxLines(1);
        tvNombre.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(tvNombre);

        if (stats.grupo != null && !stats.grupo.isEmpty()) {
            TextView tvGrupo = new TextView(this);
            tvGrupo.setText(stats.grupo);
            tvGrupo.setTextSize(11);
            tvGrupo.setTextColor(0xFF999999);
            LinearLayout.LayoutParams gP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            gP.setMargins(0, (int)(2*dp), 0, 0);
            tvGrupo.setLayoutParams(gP);
            info.addView(tvGrupo);
        }
        fila.addView(info);

        LinearLayout derecha = new LinearLayout(this);
        derecha.setOrientation(LinearLayout.VERTICAL);
        derecha.setGravity(android.view.Gravity.END);

        TextView tvClases = new TextView(this);
        tvClases.setText(stats.totalClases + (stats.totalClases == 1 ? " clase" : " clases"));
        tvClases.setTextSize(13);
        tvClases.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        tvClases.setTypeface(null, android.graphics.Typeface.BOLD);
        tvClases.setGravity(android.view.Gravity.END);
        derecha.addView(tvClases);

        TextView tvEstado = new TextView(this);
        if (stats.retardos > 0) {
            tvEstado.setText(stats.retardos + (stats.retardos == 1 ? " retardo" : " retardos"));
            tvEstado.setTextColor(0xFFB5541A);
        } else {
            tvEstado.setText("Sin retardos ✓");
            tvEstado.setTextColor(0xFF2E7D52);
        }
        tvEstado.setTextSize(11);
        tvEstado.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams eP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        eP.setMargins(0, (int)(2*dp), 0, 0);
        tvEstado.setLayoutParams(eP);
        derecha.addView(tvEstado);

        fila.addView(derecha);
        return fila;
    }

    // ══ HISTORIAL ════════════════════════════════════════════════════

    private void construirHistorial() {
        contenedorHistorial.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;

        if (listaClasesCubiertas.isEmpty()) return;

        List<Map<String, Object>> ordenadas = new ArrayList<>(listaClasesCubiertas);
        Collections.sort(ordenadas, (a, b) -> {
            String fa = (String) a.get("fecha"); if (fa == null) fa = "";
            String fb = (String) b.get("fecha"); if (fb == null) fb = "";
            return fb.compareTo(fa);
        });

        SimpleDateFormat sdfIn  = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat sdfOut = new SimpleDateFormat("dd MMM", new Locale("es", "MX"));
        String fechaAnterior = "";

        for (int i = 0; i < ordenadas.size(); i++) {
            Map<String, Object> clase = ordenadas.get(i);
            String fecha   = (String) clase.get("fecha");
            String materia = (String) clase.get("materia");
            String grupo   = (String) clase.get("grupo");
            String horaIni = (String) clase.get("horaInicio");
            String horaFin = (String) clase.get("horaFin");
            String salon   = (String) clase.get("salon");
            Object retObj  = clase.get("retardoMinutos");
            int retMin = retObj instanceof Long ? ((Long) retObj).intValue()
                    : retObj instanceof Integer ? (Integer) retObj : 0;

            if (fecha != null && !fecha.equals(fechaAnterior)) {
                fechaAnterior = fecha;
                try {
                    Date d = sdfIn.parse(fecha);
                    String fechaStr = d != null ? sdfOut.format(d).toUpperCase() : fecha;
                    TextView tvF = new TextView(this);
                    tvF.setText(fechaStr);
                    tvF.setTextSize(10);
                    tvF.setTextColor(0xFF999999);
                    tvF.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvF.setLetterSpacing(0.08f);
                    LinearLayout.LayoutParams fP = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    fP.setMargins((int)(18*dp), (int)(14*dp), (int)(18*dp), (int)(6*dp));
                    tvF.setLayoutParams(fP);
                    contenedorHistorial.addView(tvF);
                } catch (Exception ignored) {}
            } else if (i > 0) {
                View div = new View(this);
                div.setBackgroundColor(0x08000000);
                div.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                contenedorHistorial.addView(div);
            }

            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(android.view.Gravity.CENTER_VERTICAL);
            fila.setPadding((int)(18*dp), (int)(12*dp), (int)(18*dp), (int)(12*dp));
            fila.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            View punto = new View(this);
            android.graphics.drawable.GradientDrawable pBg =
                    new android.graphics.drawable.GradientDrawable();
            pBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            pBg.setColor(retMin > 0 ? 0xFFB5541A
                    : ContextCompat.getColor(this, R.color.success));
            punto.setBackground(pBg);
            int pSz = (int)(9*dp);
            LinearLayout.LayoutParams pP = new LinearLayout.LayoutParams(pSz, pSz);
            pP.setMargins(0, 0, (int)(14*dp), 0);
            pP.gravity = android.view.Gravity.CENTER_VERTICAL;
            punto.setLayoutParams(pP);
            fila.addView(punto);

            LinearLayout infoH = new LinearLayout(this);
            infoH.setOrientation(LinearLayout.VERTICAL);
            infoH.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvMat = new TextView(this);
            tvMat.setText(materia != null ? materia : "Sin nombre");
            tvMat.setTextSize(13);
            tvMat.setTextColor(0xFF1A1A1A);
            tvMat.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMat.setMaxLines(1);
            tvMat.setEllipsize(android.text.TextUtils.TruncateAt.END);
            infoH.addView(tvMat);

            StringBuilder det = new StringBuilder();
            if (grupo != null && !grupo.isEmpty()) det.append(grupo);
            if (horaIni != null) {
                if (det.length() > 0) det.append("  ·  ");
                det.append(horaIni);
                if (horaFin != null) det.append("–").append(horaFin);
            }
            if (salon != null && !salon.isEmpty()) {
                if (det.length() > 0) det.append("  ·  ");
                det.append(salon);
            }
            if (det.length() > 0) {
                TextView tvDet = new TextView(this);
                tvDet.setText(det.toString());
                tvDet.setTextSize(11);
                tvDet.setTextColor(0xFF888888);
                LinearLayout.LayoutParams dP = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                dP.setMargins(0, (int)(2*dp), 0, 0);
                tvDet.setLayoutParams(dP);
                infoH.addView(tvDet);
            }
            fila.addView(infoH);

            TextView badge = new TextView(this);
            android.graphics.drawable.GradientDrawable bBg =
                    new android.graphics.drawable.GradientDrawable();
            bBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bBg.setCornerRadius(20f);
            if (retMin > 0) {
                badge.setText(retMin + " min");
                badge.setTextColor(0xFFB5541A);
                bBg.setColor(0xFFFFF3E0);
                bBg.setStroke(1, 0xFFB5541A);
            } else {
                badge.setText("Puntual");
                badge.setTextColor(ContextCompat.getColor(this, R.color.success));
                bBg.setColor(0xFFE8F5E9);
                bBg.setStroke(1, ContextCompat.getColor(this, R.color.success));
            }
            badge.setBackground(bBg);
            badge.setTextSize(10);
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
            badge.setPadding((int)(10*dp), (int)(4*dp), (int)(10*dp), (int)(4*dp));
            fila.addView(badge);

            contenedorHistorial.addView(fila);
        }
    }

    // ══ COMPARTIR ════════════════════════════════════════════════════

    private void compartirReporte() {
        Toast.makeText(this, "Generando reporte...", Toast.LENGTH_SHORT).show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                LinearLayout contenido = (LinearLayout) scrollContenido.getChildAt(0);
                int ancho = scrollContenido.getWidth();
                int alto  = contenido != null
                        ? contenido.getHeight() : scrollContenido.getHeight();

                Bitmap bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                canvas.drawColor(0xFFF5F0EA);
                if (contenido != null) contenido.draw(canvas);
                else scrollContenido.draw(canvas);

                File imgFile = new File(getCacheDir(), "reporte_asistencias.png");
                FileOutputStream fos = new FileOutputStream(imgFile);
                bmp.compress(Bitmap.CompressFormat.PNG, 95, fos);
                fos.flush(); fos.close();
                bmp.recycle();

                Uri uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".provider", imgFile);

                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("image/png");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.putExtra(Intent.EXTRA_TEXT,
                        "Mi reporte de asistencias — AsisteDragon\n"
                                + txtMesActual.getText());
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Compartir reporte"));

            } catch (Exception e) {
                Log.e(TAG, "Error compartiendo: " + e.getMessage());
                Toast.makeText(this, "Error al generar reporte", Toast.LENGTH_SHORT).show();
            }
        }, 300);
    }

    // ══ HELPERS ══════════════════════════════════════════════════════

    private void mostrarLoading(boolean show) {
        if (loadingView != null)
            loadingView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (scrollContenido != null && show)
            scrollContenido.setVisibility(View.GONE);
        if (layoutSinDatos != null && show)
            layoutSinDatos.setVisibility(View.GONE);
    }

    private void mostrarSinDatos(String mensaje) {
        mostrarLoading(false);
        if (layoutSinDatos != null) layoutSinDatos.setVisibility(View.VISIBLE);
        if (txtSinDatos != null)    txtSinDatos.setText(mensaje);
        if (scrollContenido != null) scrollContenido.setVisibility(View.GONE);
    }

    private static class MateriaStats {
        String materia, grupo;
        int totalClases = 0, puntuales = 0, retardos = 0;
        MateriaStats(String m, String g) { materia = m; grupo = g; }
    }

    // ══ BOTTOM NAVIGATION ════════════════════════════════════════════

    private void configurarBottomNavigation() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_reportes);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_reportes) return true;
            if (id == R.id.nav_inicio) {
                startActivity(new Intent(this, PanelPrincipalActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish(); return true;
            }
            if (id == R.id.nav_horario) {
                startActivity(new Intent(this, Horario.class));
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

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}