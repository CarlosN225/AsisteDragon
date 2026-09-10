package com.utfv.asistedragon;

import android.content.Intent;
import android.util.Log;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PanelPrincipalActivity extends AppCompatActivity {

    // Vistas
    private TextView       textoNombreUsuario, textoTipoTurno, textoFechaHoy;
    private TextView       textoFechaRegistro, textoHoraRegistro, textoEstadoAsistencia;
    private RelativeLayout estadoSinRegistrar, estadoRegistrado;
    private ImageView      botonRegistrarAsistencia, botonCerrarSesion;
    private LinearLayout   contenedorMensajeRetardo;
    private TextView       textoMensajeRetardo;
    private CardView       cardHorarioBanner;
    private TextView       bannerEstadoLabel, bannerMateria, bannerGrupo;
    private TextView       bannerHorario, bannerAula, bannerTiempoRestante;
    private LinearLayout   bannerContadorLayout, checklistContainer;
    private TextView       btnExpandChecklist;
    private CardView       checklistWrapper;
    private LinearLayout        layoutTerminarDia;
    private ImageView headerFotoPerfil;
    private RelativeLayout      pantallaDiaCompletado;
    private SwipeRefreshLayout  swipeRefresh;

    // Firebase
    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;

    // Datos
    private String   userUid = "", userName = "", userTipo = "", userTurno = "";
    private List<Clase> clasesHoy = new ArrayList<>();
    private String[] estadosChecklist;
    private int[]    minutosRetardo;
    private boolean  asistenciaEscaneada = false;
    private boolean  checklistVisible    = false;
    private String   horaRegistroHoy     = null;

    // Persistencia checklist
    private static final String KEY_CHECKLIST_ESTADOS  = "checklist_estados_hoy";
    private static final String KEY_CHECKLIST_RETARDOS = "checklist_retardos_hoy";
    private static final String KEY_CHECKLIST_FECHA    = "checklist_fecha_guardada";
    private static final String KEY_DIA_CERRADO        = "dia_cerrado_fecha";
    private final Set<Integer> clasesConfirmadasFirestore = new HashSet<>();

    private Handler  timerHandler;
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configurarBarraEstado();
        setContentView(R.layout.activity_panel_principal);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        inicializarVistas();
        cargarDatosUsuario();
        configurarListeners();

        // ── Animación de entrada: el styles.xml ya hace el fade global.
        //    No se necesita nada más aquí.

        if (diaCerradoHoy()) {
            mostrarPantallaDiaCompletadoSilencioso();
        } else if (tieneClasesHoy()) {
            verificarAsistenciaHoy();
        } else {
            mostrarPantallaSinClasesHoy();
        }
    }

    private void configurarBarraEstado() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(ContextCompat.getColor(this, R.color.green_primary));
    }

    private void inicializarVistas() {
        textoNombreUsuario       = findViewById(R.id.texto_nombre_usuario);
        textoTipoTurno           = findViewById(R.id.texto_tipo_turno);
        textoFechaHoy            = findViewById(R.id.texto_fecha_hoy);
        textoFechaRegistro       = findViewById(R.id.texto_fecha_registro);
        textoHoraRegistro        = findViewById(R.id.texto_hora_registro);
        textoEstadoAsistencia    = findViewById(R.id.texto_estado_asistencia);
        contenedorMensajeRetardo = findViewById(R.id.texto_mensaje_retardo);
        textoMensajeRetardo      = findViewById(R.id.texto_retardo_contenido);
        estadoSinRegistrar       = findViewById(R.id.estado_sin_registrar);
        estadoRegistrado         = findViewById(R.id.estado_registrado);
        botonRegistrarAsistencia = findViewById(R.id.boton_registrar_asistencia);
        botonCerrarSesion        = findViewById(R.id.boton_cerrar_sesion);
        cardHorarioBanner        = findViewById(R.id.card_horario_banner);
        bannerEstadoLabel        = findViewById(R.id.banner_estado_label);
        bannerMateria            = findViewById(R.id.banner_materia);
        bannerGrupo              = findViewById(R.id.banner_grupo);
        bannerHorario            = findViewById(R.id.banner_horario);
        bannerAula               = findViewById(R.id.banner_aula);
        bannerTiempoRestante     = findViewById(R.id.banner_tiempo_restante);
        bannerContadorLayout     = findViewById(R.id.banner_contador_layout);
        checklistContainer       = findViewById(R.id.checklist_container);
        btnExpandChecklist       = findViewById(R.id.btn_expand_checklist);
        checklistWrapper         = findViewById(R.id.card_checklist_wrapper);
        layoutTerminarDia        = findViewById(R.id.layout_terminar_dia);
        headerFotoPerfil         = findViewById(R.id.header_foto_perfil);
        pantallaDiaCompletado    = findViewById(R.id.pantalla_dia_completado);
        swipeRefresh             = findViewById(R.id.swipe_refresh);
    }

    // ══ SIN CLASES HOY ══════════════════════════════════════════════════

    private boolean diaCerradoHoy() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String fechaCierre = prefs.getString(KEY_DIA_CERRADO, "");
        String fechaHoy    = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return fechaHoy.equals(fechaCierre);
    }

    private void mostrarPantallaDiaCompletadoSilencioso() {
        if (pantallaDiaCompletado == null) return;
        if (estadoSinRegistrar != null)    estadoSinRegistrar.setVisibility(View.GONE);
        if (estadoRegistrado != null)      estadoRegistrado.setVisibility(View.GONE);
        if (cardHorarioBanner != null)     cardHorarioBanner.setVisibility(View.GONE);
        if (layoutTerminarDia != null)     layoutTerminarDia.setVisibility(View.GONE);
        pantallaDiaCompletado.setVisibility(View.VISIBLE);
        pantallaDiaCompletado.setAlpha(1f);
        if (timerHandler != null && timerRunnable != null)
            timerHandler.removeCallbacks(timerRunnable);
    }

    private android.view.View encontrarSwipeRefresh(android.view.View root) {
        if (root == null) return null;
        if (root instanceof SwipeRefreshLayout) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.view.View found = encontrarSwipeRefresh(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void cargarFotoPerfilHeader() {
        if (headerFotoPerfil == null) return;
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String base64 = prefs.getString("user_foto_base64", "");

        if (!base64.isEmpty()) {
            try {
                byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory
                        .decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    com.bumptech.glide.Glide.with(this)
                            .load(bmp)
                            .apply(new com.bumptech.glide.request.RequestOptions().circleCrop())
                            .into(headerFotoPerfil);
                }
            } catch (Exception ignored) {}
        } else {
            headerFotoPerfil.setImageResource(R.drawable.ic_persona_perfil);
        }
    }

    private boolean tieneClasesHoy() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String clasesJson = prefs.getString("horario_clases_completas", null);
        if (clasesJson == null) return true;
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Clase>>(){}.getType();
            List<Clase> todas = gson.fromJson(clasesJson, type);
            String diaHoy = obtenerDiaHoy();
            for (Clase c : todas)
                if (c.getDia() != null && c.getDia().equalsIgnoreCase(diaHoy)) return true;
            return false;
        } catch (Exception e) { return true; }
    }

    private void mostrarPantallaSinClasesHoy() {
        if (estadoRegistrado != null)         estadoRegistrado.setVisibility(View.GONE);
        if (cardHorarioBanner != null)        cardHorarioBanner.setVisibility(View.GONE);
        if (layoutTerminarDia != null)        layoutTerminarDia.setVisibility(View.GONE);
        if (estadoSinRegistrar == null)       return;
        estadoSinRegistrar.setVisibility(View.VISIBLE);

        String proximoDia = null, proximaHora = null;
        try {
            SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
            String clasesJson = prefs.getString("horario_clases_completas", null);
            if (clasesJson != null) {
                Gson gson = new Gson();
                Type type = new TypeToken<List<Clase>>(){}.getType();
                List<Clase> todas = gson.fromJson(clasesJson, type);
                String diaHoy = obtenerDiaHoy();
                String[] orden = {"Lunes","Martes","Miercoles","Jueves","Viernes","Sabado","Domingo"};
                String dHoyN = diaHoy.replace("\u00e9","e").replace("\u00e1","a")
                        .replace("\u00f3","o").replace("\u00fa","u").replace("\u00ed","i");
                int hoyIdx = 0;
                for (int i = 0; i < orden.length; i++)
                    if (orden[i].equalsIgnoreCase(dHoyN)) { hoyIdx = i; break; }
                for (int offset = 1; offset <= 7 && proximoDia == null; offset++) {
                    String cand = orden[(hoyIdx + offset) % orden.length];
                    for (Clase c : todas) {
                        if (c.getDia() == null) continue;
                        String dN = c.getDia().replace("\u00e9","e").replace("\u00e1","a")
                                .replace("\u00f3","o").replace("\u00fa","u").replace("\u00ed","i");
                        if (dN.equalsIgnoreCase(cand)) {
                            proximoDia = c.getDia(); proximaHora = c.getHoraInicio(); break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        LinearLayout ll = (LinearLayout) estadoSinRegistrar.getChildAt(0);
        if (ll == null) return;
        ll.removeAllViews();

        float dp = getResources().getDisplayMetrics().density;
        ll.setGravity(android.view.Gravity.CENTER);
        ll.setPadding((int)(32*dp), (int)(60*dp), (int)(32*dp), (int)(40*dp));

        TextView txtFecha = new TextView(this);
        txtFecha.setId(R.id.texto_fecha_hoy);
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd 'de' MMMM", new Locale("es", "MX"));
        String fecha = sdf.format(new Date());
        fecha = fecha.substring(0,1).toUpperCase() + fecha.substring(1);
        txtFecha.setText(fecha);
        txtFecha.setTextSize(20);
        txtFecha.setTypeface(null, android.graphics.Typeface.BOLD);
        txtFecha.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        txtFecha.setGravity(android.view.Gravity.CENTER);
        txtFecha.setLetterSpacing(0.01f);
        LinearLayout.LayoutParams fechaP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fechaP.setMargins(0, 0, 0, (int)(32*dp));
        txtFecha.setLayoutParams(fechaP);
        ll.addView(txtFecha);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        int cirSz = (int)(84*dp);
        LinearLayout.LayoutParams frameP = new LinearLayout.LayoutParams(cirSz, cirSz);
        frameP.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        frameP.setMargins(0, 0, 0, (int)(28*dp));
        frame.setLayoutParams(frameP);
        android.graphics.drawable.GradientDrawable cirBg = new android.graphics.drawable.GradientDrawable();
        cirBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        cirBg.setColor(0xFFE8F5EE);
        frame.setBackground(cirBg);
        ImageView ico = new ImageView(this);
        ico.setImageResource(R.drawable.ic_calendar);
        ico.setColorFilter(ContextCompat.getColor(this, R.color.green_primary),
                android.graphics.PorterDuff.Mode.SRC_IN);
        ico.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ico.setPadding((int)(12*dp), (int)(12*dp), (int)(12*dp), (int)(12*dp));
        ico.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        frame.addView(ico);
        ll.addView(frame);

        TextView txtTitulo = new TextView(this);
        txtTitulo.setText("Hoy no tienes clases");
        txtTitulo.setTextSize(22);
        txtTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        txtTitulo.setTextColor(0xFF1C1C1C);
        txtTitulo.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams titP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titP.setMargins(0, 0, 0, (int)(18*dp));
        txtTitulo.setLayoutParams(titP);
        ll.addView(txtTitulo);

        final String dF = proximoDia, hF = proximaHora;
        TextView pill = new TextView(this);
        pill.setText(dF != null
                ? "Nos vemos el " + dF + (hF != null ? "  ·  " + hF : "")
                : "Sin clases esta semana");
        pill.setTextSize(13);
        pill.setTypeface(null, android.graphics.Typeface.BOLD);
        pill.setTextColor(0xFF2E7D52);
        pill.setGravity(android.view.Gravity.CENTER);
        pill.setPadding((int)(22*dp), (int)(10*dp), (int)(22*dp), (int)(10*dp));
        android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
        pillBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(40f);
        pillBg.setColor(0xFFDFF2E8);
        pill.setBackground(pillBg);
        LinearLayout.LayoutParams pillP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pillP.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        pillP.setMargins(0, 0, 0, (int)(20*dp));
        pill.setLayoutParams(pillP);
        ll.addView(pill);

        TextView txtSub = new TextView(this);
        txtSub.setText("¡Disfruta tu día de descanso!");
        txtSub.setTextSize(13);
        txtSub.setTextColor(0xFFAAAAAA);
        txtSub.setGravity(android.view.Gravity.CENTER);
        ll.addView(txtSub);

        textoFechaHoy = txtFecha;
    }

    // ══ ASISTENCIA QR ═══════════════════════════════════════════════════

    private void verificarAsistenciaHoy() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String fechaHoy = sdf.format(new Date());
        db.collection("asistencias")
                .whereEqualTo("profesorUid", userUid)
                .whereEqualTo("fecha", fechaHoy)
                .limit(1)
                .get()
                .addOnSuccessListener(q -> {
                    if (!q.isEmpty()) {
                        Asistencia a = q.getDocuments().get(0).toObject(Asistencia.class);
                        if (a != null) {
                            asistenciaEscaneada = true;
                            horaRegistroHoy = extraerHHMM(a.getHoraRegistro());
                            mostrarAsistenciaRegistrada(a);
                        }
                    } else {
                        asistenciaEscaneada = false;
                        limpiarEstadosChecklist();
                        mostrarSinRegistrar();
                    }
                    cargarYMostrarBannerHorario();
                })
                .addOnFailureListener(e -> {
                    mostrarSinRegistrar();
                    cargarYMostrarBannerHorario();
                });
    }

    private void mostrarAsistenciaRegistrada(Asistencia a) {
        estadoSinRegistrar.setVisibility(View.GONE);
        estadoRegistrado.setVisibility(View.VISIBLE);

        SimpleDateFormat sdf = new SimpleDateFormat("dd 'de' MMMM, yyyy", new Locale("es", "MX"));
        textoFechaRegistro.setText(sdf.format(new Date()));
        textoHoraRegistro.setText(a.getHoraRegistro());

        String estado = a.getEstado();
        if ("puntual".equals(estado)) {
            textoEstadoAsistencia.setText("PUNTUAL");
            textoEstadoAsistencia.setTextColor(ContextCompat.getColor(this, R.color.success));
            textoEstadoAsistencia.setBackgroundResource(R.drawable.badge_puntual);
            if (contenedorMensajeRetardo != null) contenedorMensajeRetardo.setVisibility(View.GONE);
        } else {
            textoEstadoAsistencia.setText("RETARDO");
            textoEstadoAsistencia.setTextColor(ContextCompat.getColor(this, R.color.warning));
            textoEstadoAsistencia.setBackgroundResource(R.drawable.badge_retardo);
            if (textoMensajeRetardo != null && !clasesHoy.isEmpty()) {
                Clase primera = clasesHoy.get(0);
                int regMin = minutosDesde(horaRegistroHoy);
                int iniMin = minutosDesde(primera.getHoraInicio());
                int retMin = (regMin > 0 && iniMin > 0 && regMin > iniMin) ? regMin - iniMin : 0;
                SimpleDateFormat sdfC = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                String tiempoRetardo = "";
                if (retMin > 0) {
                    int rh = retMin / 60, rm = retMin % 60;
                    tiempoRetardo = rh > 0
                            ? rh + " h " + (rm > 0 ? rm + " min" : "")
                            : rm + " min";
                }
                String msg = "Sus clases del " + obtenerDiaHoy() + " " + sdfC.format(new Date())
                        + " iniciaron a las " + primera.getHoraInicio()
                        + (retMin > 0 ? ". Lleva " + tiempoRetardo + " de retardo." : ".");
                textoMensajeRetardo.setText(msg);
                if (contenedorMensajeRetardo != null) contenedorMensajeRetardo.setVisibility(View.VISIBLE);
            }
        }
    }

    private void mostrarSinRegistrar() {
        estadoSinRegistrar.setVisibility(View.VISIBLE);
        estadoRegistrado.setVisibility(View.GONE);
        verificarSiYaPasoElDia();
    }

    private void verificarSiYaPasoElDia() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String clasesJson = prefs.getString("horario_clases_completas", null);
        if (clasesJson == null) return;
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Clase>>(){}.getType();
            List<Clase> todas = gson.fromJson(clasesJson, type);
            String diaHoy = obtenerDiaHoy();
            int ahora = minutosAhora();
            boolean hayClaseHoy = false, todasPasaron = true;
            for (Clase c : todas) {
                if (c.getDia() != null && c.getDia().equalsIgnoreCase(diaHoy)) {
                    hayClaseHoy = true;
                    int fin = minutosDesde(c.getHoraFin());
                    if (fin < 0 || ahora < fin) { todasPasaron = false; break; }
                }
            }
            TextView txtMsj = findViewById(R.id.texto_sin_asistencia_hoy);
            if (txtMsj == null) return;
            if (hayClaseHoy && todasPasaron) {
                txtMsj.setText("Usted no asistio a clases hoy");
                txtMsj.setVisibility(View.VISIBLE);
            } else {
                txtMsj.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {}
    }

    // ══ BANNER + CHECKLIST ══════════════════════════════════════════════

    private void cargarYMostrarBannerHorario() {
        if (!asistenciaEscaneada) {
            if (cardHorarioBanner != null) cardHorarioBanner.setVisibility(View.GONE);
            return;
        }
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String clasesJson = prefs.getString("horario_clases_completas", null);
        if (clasesJson == null || cardHorarioBanner == null) {
            if (cardHorarioBanner != null) cardHorarioBanner.setVisibility(View.GONE);
            return;
        }
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Clase>>(){}.getType();
            List<Clase> todas = gson.fromJson(clasesJson, type);
            String diaHoy = obtenerDiaHoy();
            List<Clase> raw = new ArrayList<>();
            for (Clase c : todas)
                if (c.getDia() != null && c.getDia().equalsIgnoreCase(diaHoy)) raw.add(c);

            clasesHoy = new ArrayList<>();
            for (Clase c : raw) {
                boolean dup = false;
                for (Clase s : clasesHoy)
                    if (s.getMateria() != null && s.getMateria().equalsIgnoreCase(c.getMateria())
                            && s.getGrupo() != null && s.getGrupo().equals(c.getGrupo())
                            && s.getHoraInicio() != null && s.getHoraInicio().equals(c.getHoraInicio())) {
                        dup = true; break;
                    }
                if (!dup) clasesHoy.add(c);
            }
            Collections.sort(clasesHoy, (a, b) -> {
                String ha = a.getHoraInicio() != null ? a.getHoraInicio() : "";
                String hb = b.getHoraInicio() != null ? b.getHoraInicio() : "";
                return ha.compareTo(hb);
            });

            estadosChecklist = new String[clasesHoy.size()];
            minutosRetardo   = new int[clasesHoy.size()];
            clasesConfirmadasFirestore.clear();
            cargarEstadosChecklist();
            calcularEstadosIniciales();
            actualizarBanner();

            if (asistenciaEscaneada && estadoRegistrado.getVisibility() == View.VISIBLE) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                db.collection("asistencias")
                        .whereEqualTo("profesorUid", userUid)
                        .whereEqualTo("fecha", sdf.format(new Date()))
                        .limit(1).get()
                        .addOnSuccessListener(q -> {
                            if (!q.isEmpty()) {
                                Asistencia a = q.getDocuments().get(0).toObject(Asistencia.class);
                                if (a != null) mostrarAsistenciaRegistrada(a);
                            }
                        });
            }

            construirChecklist();
            actualizarVisibilidadBotonTerminar();

            if (timerHandler == null) timerHandler = new Handler(Looper.getMainLooper());
            timerRunnable = () -> {
                calcularEstadosIniciales();
                verificarConfirmacionAutomatica();
                verificarDiaCompletadoAutomatico();
                actualizarBanner();
                construirChecklist();
                actualizarVisibilidadBotonTerminar();
                timerHandler.postDelayed(timerRunnable, 60_000);
            };
            timerHandler.postDelayed(timerRunnable, 60_000);

        } catch (Exception e) {
            if (cardHorarioBanner != null) cardHorarioBanner.setVisibility(View.GONE);
        }
    }

    private void calcularEstadosIniciales() {
        int ahora = minutosAhora();
        for (int i = 0; i < clasesHoy.size(); i++) {
            if ("marcada".equals(estadosChecklist[i])) continue;
            Clase c   = clasesHoy.get(i);
            int ini   = minutosDesde(c.getHoraInicio());
            int tol   = calcularTolerancia(c.getHoraInicio(), c.getHoraFin());
            int limite = ini + tol;
            if (ini < 0) continue;
            if      (ahora < ini)             estadosChecklist[i] = "futura";
            else if (ahora >= ini && ahora <= limite) {
                if (!"espera".equals(estadosChecklist[i])) estadosChecklist[i] = "espera";
            } else estadosChecklist[i] = "bloqueada";
        }
    }

    // ══ BANNER ══════════════════════════════════════════════════════════

    private void actualizarBanner() {
        if (clasesHoy.isEmpty()) { cardHorarioBanner.setVisibility(View.GONE); return; }
        cardHorarioBanner.setVisibility(View.VISIBLE);
        int ahora = minutosAhora();
        Clase enCurso = null, proxima = null;
        for (Clase c : clasesHoy) {
            int ini = minutosDesde(c.getHoraInicio()), fin = minutosDesde(c.getHoraFin());
            if (ini < 0 || fin < 0) continue;
            if (ahora >= ini && ahora < fin) { enCurso = c; break; }
            if (ahora < ini && proxima == null) proxima = c;
        }
        if (enCurso != null)      pintarEnCurso(enCurso, ahora);
        else if (proxima != null) pintarProxima(proxima, ahora);
        else                      pintarTerminada(clasesHoy.get(clasesHoy.size()-1));
        actualizarVisibilidadBotonTerminar();
    }

    private void pintarEnCurso(Clase c, int ahora) {
        int tol    = calcularTolerancia(c.getHoraInicio(), c.getHoraFin());
        int ini    = minutosDesde(c.getHoraInicio());
        int fin    = minutosDesde(c.getHoraFin());
        boolean ok = ahora <= ini + tol;

        cardHorarioBanner.setCardBackgroundColor(0xFFFEFCF8);

        if (ok) {
            bannerEstadoLabel.setText("EN CLASE AHORA");
            bannerEstadoLabel.setTextColor(0xFF2E7D52);
            bannerMateria.setText(c.getMateria() != null ? c.getMateria() : "");
            bannerMateria.setTextColor(0xFF1B4332);
            bannerMateria.setTypeface(null, android.graphics.Typeface.BOLD);
            bannerMateria.setPaintFlags(bannerMateria.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            bannerGrupo.setText(c.getGrupo() != null ? c.getGrupo() : "");
            bannerGrupo.setTextColor(0xFF40916C);
            bannerHorario.setText(c.getHoraInicio() + " – " + c.getHoraFin());
            bannerHorario.setTextColor(0xFF52B788);
            bannerAula.setText(c.getSalon() != null && !c.getSalon().isEmpty() ? c.getSalon() : "Sin aula");
            bannerAula.setTextColor(0xFF52B788);
            bannerContadorLayout.setVisibility(View.VISIBLE);
            bannerTiempoRestante.setText((fin - ahora) + " min restantes");
            bannerTiempoRestante.setTextColor(0xFF2E7D52);
            android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
            chipBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            chipBg.setCornerRadius(30f);
            chipBg.setColor(0xFFD8F3DC);
            bannerContadorLayout.setBackground(chipBg);
        } else {
            bannerEstadoLabel.setText("EN CURSO  •  TOLERANCIA SUPERADA");
            bannerEstadoLabel.setTextColor(0xFFB5541A);
            bannerMateria.setText(c.getMateria() != null ? c.getMateria() : "");
            bannerMateria.setTextColor(0xFF7B2D00);
            bannerMateria.setTypeface(null, android.graphics.Typeface.BOLD);
            bannerMateria.setPaintFlags(bannerMateria.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            bannerGrupo.setText(c.getGrupo() != null ? c.getGrupo() : "");
            bannerGrupo.setTextColor(0xFFAA4E1A);
            bannerHorario.setText(c.getHoraInicio() + " – " + c.getHoraFin());
            bannerHorario.setTextColor(0xFFBF7B45);
            bannerAula.setText(c.getSalon() != null && !c.getSalon().isEmpty() ? c.getSalon() : "Sin aula");
            bannerAula.setTextColor(0xFFBF7B45);
            bannerContadorLayout.setVisibility(View.VISIBLE);
            bannerTiempoRestante.setText((fin - ahora) + " min restantes");
            bannerTiempoRestante.setTextColor(0xFF7B2D00);
            android.graphics.drawable.GradientDrawable chipBg2 = new android.graphics.drawable.GradientDrawable();
            chipBg2.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            chipBg2.setCornerRadius(30f);
            chipBg2.setColor(0xFFFFE8D6);
            bannerContadorLayout.setBackground(chipBg2);
        }

        if (btnExpandChecklist != null) {
            btnExpandChecklist.setTextColor(ok ? 0xFF40916C : 0xFFAA4E1A);
            btnExpandChecklist.setBackgroundColor(ok ? 0x10195D35 : 0x10A33800);
        }
    }

    private void pintarProxima(Clase c, int ahora) {
        int faltan = minutosDesde(c.getHoraInicio()) - ahora;
        cardHorarioBanner.setCardBackgroundColor(0xFFFEFCF8);
        String labelTxt = faltan <= 30 ? "PRÓXIMA CLASE  •  En " + faltan + " min" : "PRÓXIMA CLASE";
        bannerEstadoLabel.setText(labelTxt);
        bannerEstadoLabel.setTextColor(0xFF3A5A8C);
        bannerMateria.setText(c.getMateria() != null ? c.getMateria() : "");
        bannerMateria.setTextColor(0xFF1C2E4A);
        bannerMateria.setTypeface(null, android.graphics.Typeface.BOLD);
        bannerMateria.setPaintFlags(bannerMateria.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        bannerGrupo.setText(c.getGrupo() != null ? c.getGrupo() : "");
        bannerGrupo.setTextColor(0xFF4A6B8C);
        bannerHorario.setText(c.getHoraInicio() + " – " + c.getHoraFin());
        bannerHorario.setTextColor(0xFF6B8FAF);
        bannerAula.setText(c.getSalon() != null && !c.getSalon().isEmpty() ? c.getSalon() : "Sin aula");
        bannerAula.setTextColor(0xFF6B8FAF);
        if (faltan <= 30) {
            bannerContadorLayout.setVisibility(View.VISIBLE);
            bannerTiempoRestante.setText("En " + faltan + " min");
            bannerTiempoRestante.setTextColor(0xFF1C2E4A);
            android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
            chipBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            chipBg.setCornerRadius(30f);
            chipBg.setColor(0xFFD6E4F0);
            bannerContadorLayout.setBackground(chipBg);
        } else {
            bannerContadorLayout.setVisibility(View.GONE);
        }
        if (btnExpandChecklist != null) {
            btnExpandChecklist.setTextColor(0xFF4A6B8C);
            btnExpandChecklist.setBackgroundColor(0x100A2540);
        }
    }

    private void pintarTerminada(Clase c) {
        cardHorarioBanner.setCardBackgroundColor(0xFFFEFCF8);
        bannerEstadoLabel.setText("CLASES DEL DÍA FINALIZADAS");
        bannerEstadoLabel.setTextColor(0xFFAAAAAA);
        bannerMateria.setText(c.getMateria() != null ? c.getMateria() : "");
        bannerMateria.setTextColor(0xFFBBBBBB);
        bannerMateria.setTypeface(null, android.graphics.Typeface.NORMAL);
        bannerMateria.setPaintFlags(bannerMateria.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        bannerGrupo.setText(c.getGrupo() != null ? c.getGrupo() : "");
        bannerGrupo.setTextColor(0xFFCCCCCC);
        bannerHorario.setText(c.getHoraInicio() + " – " + c.getHoraFin());
        bannerHorario.setTextColor(0xFFCCCCCC);
        bannerAula.setText("");
        bannerContadorLayout.setVisibility(View.GONE);
        if (btnExpandChecklist != null) {
            btnExpandChecklist.setTextColor(0xFFBBBBBB);
            btnExpandChecklist.setBackgroundColor(0x08000000);
        }
    }

    private void setTextoBlanco(TextView tv, String texto, boolean tachado) {
        tv.setText(texto != null ? texto : ""); tv.setTextColor(android.graphics.Color.WHITE);
        int f = tv.getPaintFlags();
        tv.setPaintFlags(tachado ? f | Paint.STRIKE_THRU_TEXT_FLAG : f & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }

    // ══ CHECKLIST ═══════════════════════════════════════════════════════

    private void construirChecklist() {
        if (checklistContainer == null || clasesHoy.isEmpty()) return;
        checklistContainer.removeAllViews();
        int ahora = minutosAhora();
        float dp  = getResources().getDisplayMetrics().density;

        for (int i = 0; i < clasesHoy.size(); i++) {
            final int idx = i;
            Clase c   = clasesHoy.get(i);
            String est = estadosChecklist[i] != null ? estadosChecklist[i] : "futura";
            int ini    = minutosDesde(c.getHoraInicio());
            int tol    = calcularTolerancia(c.getHoraInicio(), c.getHoraFin());
            int limite = ini + tol;

            if (i > 0) {
                View div = new View(this); div.setBackgroundColor(0x15000000);
                div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                checklistContainer.addView(div);
            }

            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.VERTICAL);
            fila.setPadding((int)(16*dp),(int)(14*dp),(int)(16*dp),(int)(14*dp));
            fila.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout filaTop = new LinearLayout(this);
            filaTop.setOrientation(LinearLayout.HORIZONTAL);
            filaTop.setGravity(android.view.Gravity.CENTER_VERTICAL);
            filaTop.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView circulo = new TextView(this); circulo.setGravity(android.view.Gravity.CENTER); circulo.setTextSize(13);
            int sz = (int)(30*dp); LinearLayout.LayoutParams cP = new LinearLayout.LayoutParams(sz, sz); cP.setMargins(0,0,(int)(12*dp),0); circulo.setLayoutParams(cP);
            android.graphics.drawable.GradientDrawable cBg = new android.graphics.drawable.GradientDrawable(); cBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            switch (est) {
                case "marcada":  circulo.setText("\u2713"); circulo.setTextColor(android.graphics.Color.WHITE); cBg.setColor(ContextCompat.getColor(this,R.color.success)); break;
                case "bloqueada":circulo.setText("\u2715"); circulo.setTextColor(0xFF999999); cBg.setColor(0xFFEEEEEE); break;
                case "espera":   circulo.setText("\u25CF"); circulo.setTextColor(ContextCompat.getColor(this,R.color.green_primary)); cBg.setColor(0xFFE8F5E9); cBg.setStroke(2,ContextCompat.getColor(this,R.color.green_primary)); break;
                default:         circulo.setText("\u25CB"); circulo.setTextColor(0xFFCCCCCC); cBg.setColor(0xFFF0F0F0);
            }
            circulo.setBackground(cBg); filaTop.addView(circulo);

            LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
            TextView tn = new TextView(this); tn.setText(c.getMateria()!=null?c.getMateria():""); tn.setTextSize(14); tn.setMaxLines(1); tn.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView td = new TextView(this);
            String det = (c.getHoraInicio()!=null?c.getHoraInicio():"")+" \u2013 "+(c.getHoraFin()!=null?c.getHoraFin():"");
            if (c.getSalon()!=null&&!c.getSalon().isEmpty()) det += "  \u2022  "+c.getSalon();
            td.setText(det); td.setTextSize(12);
            LinearLayout.LayoutParams dP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT); dP.setMargins(0,2,0,0); td.setLayoutParams(dP);
            switch (est) {
                case "marcada":   tn.setTextColor(ContextCompat.getColor(this,R.color.success)); tn.setTypeface(null,android.graphics.Typeface.BOLD); td.setTextColor(0xFF888888); break;
                case "bloqueada": tn.setTextColor(0xFFAAAAAA); tn.setPaintFlags(tn.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG); td.setTextColor(0xFFCCCCCC); break;
                case "espera":    tn.setTextColor(0xFF1A1A1A); tn.setTypeface(null,android.graphics.Typeface.BOLD); td.setTextColor(ContextCompat.getColor(this,R.color.green_primary)); break;
                default:          tn.setTextColor(0xFF777777); td.setTextColor(0xFFAAAAAA);
            }
            info.addView(tn); info.addView(td); filaTop.addView(info);

            TextView badge = new TextView(this); badge.setTextSize(10); badge.setTypeface(null,android.graphics.Typeface.BOLD);
            badge.setPadding((int)(14*dp),(int)(5*dp),(int)(14*dp),(int)(5*dp));
            android.graphics.drawable.GradientDrawable bBg = new android.graphics.drawable.GradientDrawable(); bBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE); bBg.setCornerRadius(30f);
            switch (est) {
                case "marcada":
                    int ret = minutosRetardo[i];
                    if (ret>0){badge.setText(ret+" MIN RETARDO");badge.setTextColor(0xFF888888);bBg.setColor(0xFFF5F5F5);bBg.setStroke(1,0xFFCCCCCC);}
                    else{badge.setText("PUNTUAL");badge.setTextColor(ContextCompat.getColor(this,R.color.success));bBg.setColor(0xFFE8F5E9);bBg.setStroke(1,ContextCompat.getColor(this,R.color.success));}
                    break;
                case "espera":   badge.setText("MARCAR");badge.setTextColor(ContextCompat.getColor(this,R.color.green_primary));bBg.setColor(0xFFE8F5E9);bBg.setStroke(1,ContextCompat.getColor(this,R.color.green_primary));break;
                case "bloqueada":badge.setVisibility(View.GONE);break;
                default:         badge.setText(c.getHoraInicio()!=null?c.getHoraInicio():"");badge.setTextColor(0xFFBBBBBB);bBg.setColor(0xFFF5F5F5);
            }
            badge.setBackground(bBg); filaTop.addView(badge); fila.addView(filaTop);

            if ("espera".equals(est)) {
                int restTol = limite - ahora;
                if (restTol > 0) {
                    TextView sub = new TextView(this); sub.setText("En espera \u00b7 "+restTol+" min para cerrar ventana"); sub.setTextSize(11); sub.setTextColor(ContextCompat.getColor(this,R.color.green_primary));
                    LinearLayout.LayoutParams sP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); sP.setMargins((int)(42*dp),(int)(4*dp),0,0); sub.setLayoutParams(sP); fila.addView(sub);
                }
                fila.setClickable(true); fila.setFocusable(true);
                android.util.TypedValue rpl = new android.util.TypedValue(); getTheme().resolveAttribute(android.R.attr.selectableItemBackground,rpl,true);
                fila.setForeground(ContextCompat.getDrawable(this,rpl.resourceId));
                fila.setOnClickListener(v -> {
                    minutosRetardo[idx] = Math.max(0, ahora - ini);
                    estadosChecklist[idx] = "marcada";
                    guardarEstadosChecklist();
                    int finC = minutosDesde(c.getHoraFin());
                    if (finC>0 && (finC-ahora)<=5 && !clasesConfirmadasFirestore.contains(idx)) { clasesConfirmadasFirestore.add(idx); guardarClaseEnFirestore(idx,c); }
                    construirChecklist(); actualizarBanner();
                    Toast.makeText(this, minutosRetardo[idx]>0?"Clase marcada con "+minutosRetardo[idx]+" min de retardo":"Clase marcada \u2014 puntual", Toast.LENGTH_SHORT).show();
                });
            }
            checklistContainer.addView(fila);
        }

        if (btnExpandChecklist != null) {
            btnExpandChecklist.setOnClickListener(v -> {
                checklistVisible = !checklistVisible;
                int vis = checklistVisible ? View.VISIBLE : View.GONE;
                checklistContainer.setVisibility(vis);
                if (checklistWrapper != null) checklistWrapper.setVisibility(vis);
                btnExpandChecklist.setText(checklistVisible ? "Ocultar clases de hoy \u25b2" : "Ver todas las clases de hoy \u25be");
            });
        }
    }

    // ══ TERMINAR DIA ════════════════════════════════════════════════════

    private void actualizarVisibilidadBotonTerminar() {
        if (layoutTerminarDia == null) return;
        layoutTerminarDia.setVisibility(asistenciaEscaneada && !clasesHoy.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void manejarTerminarDia() {
        if (clasesHoy.isEmpty()) return;
        int ahora      = minutosAhora();
        Clase ultClase = clasesHoy.get(clasesHoy.size() - 1);
        int finUltima  = minutosDesde(ultClase.getHoraFin());
        int iniUltima  = minutosDesde(ultClase.getHoraInicio());
        int clasesPendientes = 0, clasesBloqueadas = 0, clasesMarcadas = 0;
        for (int i = 0; i < clasesHoy.size(); i++) {
            String est = estadosChecklist != null && i < estadosChecklist.length ? estadosChecklist[i] : "futura";
            if      ("futura".equals(est) || "espera".equals(est)) clasesPendientes++;
            else if ("bloqueada".equals(est)) clasesBloqueadas++;
            else if ("marcada".equals(est))   clasesMarcadas++;
        }
        boolean jornadaTerminada = finUltima > 0 && ahora >= finUltima;
        boolean casi2Min         = finUltima > 0 && (finUltima - ahora) <= 2 && ahora < finUltima;
        int minRestantes         = finUltima > 0 ? Math.max(0, finUltima - ahora) : 0;

        if (jornadaTerminada) {
            String msg = clasesBloqueadas > 0
                    ? "Su jornada ha concluido. " + clasesBloqueadas + " clase(s) no fueron cubiertas. ¿Desea cerrar su día?"
                    : "Su jornada del día ha concluido correctamente. ¿Desea cerrar su día?";
            new android.app.AlertDialog.Builder(this).setTitle("Jornada completada").setMessage(msg)
                    .setPositiveButton("Cerrar mi día", (d, w) -> { guardarCierreJornada(false, null); mostrarPantallaDiaCompletado(true, null); })
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        if (casi2Min) {
            new android.app.AlertDialog.Builder(this).setTitle("Casi terminando")
                    .setMessage("Faltan " + minRestantes + " min para concluir su última clase. Puede terminar ahora sin necesidad de justificación.")
                    .setPositiveButton("Terminar ahora", (d, w) -> { guardarCierreJornada(false, "Cierre con ≤2 min restantes"); mostrarPantallaDiaCompletado(true, null); })
                    .setNegativeButton("Esperar", null).show();
            return;
        }
        if (clasesPendientes > 0) {
            int h = minRestantes / 60, m = minRestantes % 60;
            String tiempoStr = h > 0 ? h + " h " + (m > 0 ? m + " min" : "") : m + " min";
            new android.app.AlertDialog.Builder(this).setTitle("¿Terminar antes de tiempo?")
                    .setMessage("Aún " + (clasesPendientes == 1 ? "falta 1 clase" : "faltan " + clasesPendientes + " clases") + " y quedan " + tiempoStr + " de jornada. ¿Desea retirarse?")
                    .setPositiveButton("Sí, retirarme", (d, w) -> pedirMotivoRetiro())
                    .setNegativeButton("Continuar", null).show();
            return;
        }
        if (!jornadaTerminada && !casi2Min) {
            int h = minRestantes / 60, m = minRestantes % 60;
            String tiempoStr = h > 0 ? h + " h " + (m > 0 ? m + " min" : "") : m + " min";
            String detalle = clasesBloqueadas > 0 ? clasesMarcadas + " clase(s) cubiertas, " + clasesBloqueadas + " no cubiertas." : "Todas sus clases han sido marcadas.";
            new android.app.AlertDialog.Builder(this).setTitle("Jornada en curso")
                    .setMessage(detalle + " Faltan " + tiempoStr + " para terminar su jornada oficial. ¿Desea retirarse antes de tiempo?")
                    .setPositiveButton("Sí, con motivo", (d, w) -> pedirMotivoRetiro())
                    .setNegativeButton("No, esperar", null).show();
        }
    }

    private void mostrarDialogoTerminarAntes(int pendientes, int minutosRest) {
        int h = minutosRest/60, m = minutosRest%60;
        String t = h>0 ? h+" h "+(m>0?m+" min":"") : m+" min";
        new android.app.AlertDialog.Builder(this).setTitle("¿Terminar dia antes de tiempo?")
                .setMessage("Aun "+(pendientes==1?"falta 1 clase":"faltan "+pendientes+" clases")+" y aproximadamente "+t+" para terminar su jornada de hoy.\n\n¿Esta seguro que desea retirarse?")
                .setPositiveButton("Si, retirarme",(d,w)->pedirMotivoRetiro()).setNegativeButton("Continuar en clase",null).show();
    }

    private void pedirMotivoRetiro() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Ej: Cita medica, emergencia..."); input.setMaxLines(3); input.setPadding(48,24,48,8);
        new android.app.AlertDialog.Builder(this).setTitle("Motivo de retiro anticipado").setMessage("Indique brevemente el motivo:").setView(input)
                .setPositiveButton("Confirmar retiro",(d,w)->{
                    String motivo = input.getText().toString().trim();
                    if (motivo.isEmpty()) motivo = "Sin especificar";
                    guardarRetiroAnticipado(motivo);
                    mostrarPantallaDiaCompletado(false, motivo);
                }).setNegativeButton("Cancelar",null).show();
    }

    private void mostrarDialogoConfirmarCierreNormal() {
        new android.app.AlertDialog.Builder(this).setTitle("Terminar mi dia").setMessage("Ha completado todas sus clases de hoy. ¿Desea cerrar su jornada?")
                .setPositiveButton("Si, terminar",(d,w)->{ guardarCierreJornada(false,null); mostrarPantallaDiaCompletado(true,null); })
                .setNegativeButton("Cancelar",null).show();
    }

    private void mostrarPantallaDiaCompletado(boolean completo, String motivo) {
        if (pantallaDiaCompletado == null) return;
        String fechaHoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE).edit()
                .putString(KEY_DIA_CERRADO, fechaHoy).apply();
        pantallaDiaCompletado.setVisibility(View.VISIBLE);
        pantallaDiaCompletado.setAlpha(0f);
        pantallaDiaCompletado.animate().alpha(1f).setDuration(500)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        if (layoutTerminarDia != null) layoutTerminarDia.setVisibility(View.GONE);
        if (timerHandler != null && timerRunnable != null)
            timerHandler.removeCallbacks(timerRunnable);
    }

    private void verificarDiaCompletadoAutomatico() {
        if (!asistenciaEscaneada || clasesHoy.isEmpty()) return;
        if (pantallaDiaCompletado != null && pantallaDiaCompletado.getVisibility() == View.VISIBLE) return;
        int ahora  = minutosAhora();
        Clase ult  = clasesHoy.get(clasesHoy.size() - 1);
        int finUlt = minutosDesde(ult.getHoraFin());
        if (finUlt < 0 || ahora < finUlt) return;
        boolean hayPendientes = false;
        for (int i = 0; i < clasesHoy.size(); i++) {
            String e = estadosChecklist != null && i < estadosChecklist.length ? estadosChecklist[i] : "futura";
            if ("futura".equals(e) || "espera".equals(e)) { hayPendientes = true; break; }
        }
        if (hayPendientes) return;
        guardarCierreJornada(false, null);
        mostrarPantallaDiaCompletado(true, null);
    }

    private void guardarRetiroAnticipado(String motivo) {
        if (userUid==null||userUid.isEmpty()) return;
        java.util.Map<String,Object> doc = new java.util.HashMap<>();
        doc.put("profesorUid",userUid); doc.put("nombreProfesor",userName);
        doc.put("fecha",new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date()));
        doc.put("tipo","retiro_anticipado"); doc.put("motivo",motivo);
        doc.put("hora",new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        doc.put("timestamp",Timestamp.now());
        int sin=0; if(estadosChecklist!=null) for(String e:estadosChecklist) if("futura".equals(e)||"espera".equals(e)) sin++;
        doc.put("clasesSinCubrir",sin);
        FirebaseFirestore.getInstance().collection("retiros_anticipados").add(doc)
                .addOnSuccessListener(r->Log.i("Panel","Retiro guardado"))
                .addOnFailureListener(e->Log.e("Panel","Error retiro: "+e.getMessage()));
    }

    private void guardarCierreJornada(boolean anticipado, String motivo) {
        if (userUid==null||userUid.isEmpty()) return;
        java.util.Map<String,Object> doc = new java.util.HashMap<>();
        doc.put("profesorUid",userUid); doc.put("nombreProfesor",userName);
        doc.put("fecha",new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date()));
        doc.put("tipo",anticipado?"retiro_anticipado":"cierre_normal");
        doc.put("hora",new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        doc.put("timestamp",Timestamp.now()); if(motivo!=null) doc.put("motivo",motivo);
        FirebaseFirestore.getInstance().collection("jornadas").add(doc)
                .addOnSuccessListener(r->Log.i("Panel","Jornada cerrada"))
                .addOnFailureListener(e->Log.e("Panel","Error jornada: "+e.getMessage()));
    }

    // ══ CONFIRMACION AUTOMATICA ══════════════════════════════════════════

    private void verificarConfirmacionAutomatica() {
        int ahora = minutosAhora();
        for (int i=0;i<clasesHoy.size();i++) {
            if (!"marcada".equals(estadosChecklist[i])) continue;
            if (clasesConfirmadasFirestore.contains(i)) continue;
            int fin = minutosDesde(clasesHoy.get(i).getHoraFin());
            if (fin<0) continue;
            if ((fin-ahora)<=5) { clasesConfirmadasFirestore.add(i); guardarClaseEnFirestore(i,clasesHoy.get(i)); }
        }
    }

    private void guardarClaseEnFirestore(int idx, Clase clase) {
        if (userUid==null||userUid.isEmpty()) return;
        java.util.Map<String,Object> doc = new java.util.HashMap<>();
        doc.put("profesorUid",userUid); doc.put("nombreProfesor",userName);
        doc.put("fecha",new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date()));
        doc.put("dia",obtenerDiaHoy()); doc.put("materia",clase.getMateria());
        doc.put("grupo",clase.getGrupo()); doc.put("horaInicio",clase.getHoraInicio());
        doc.put("horaFin",clase.getHoraFin()); doc.put("salon",clase.getSalon());
        int retMin = (minutosRetardo!=null&&minutosRetardo.length>idx)?minutosRetardo[idx]:0;
        doc.put("retardoMinutos",retMin); doc.put("estado",retMin>0?"retardo":"puntual");
        doc.put("horaRegistro",horaRegistroHoy!=null?horaRegistroHoy:""); doc.put("confirmadaEn",Timestamp.now());
        String safe = clase.getMateria()!=null?clase.getMateria().replaceAll("[^a-zA-Z0-9]","_").substring(0,Math.min(clase.getMateria().replaceAll("[^a-zA-Z0-9]","_").length(),20)):"clase";
        String grp  = clase.getGrupo()!=null?clase.getGrupo().replaceAll("\\s+",""):"";
        String docId = userUid+"_"+new SimpleDateFormat("yyyyMMdd",Locale.getDefault()).format(new Date())+"_"+safe+"_"+grp;
        FirebaseFirestore.getInstance().collection("clases_cubiertas").document(docId).set(doc)
                .addOnSuccessListener(v->{ Log.i("Panel","Clase confirmada: "+docId); Toast.makeText(this,"Clase confirmada: "+clase.getMateria(),Toast.LENGTH_SHORT).show(); })
                .addOnFailureListener(e->{ clasesConfirmadasFirestore.remove(idx); Log.e("Panel","Error: "+e.getMessage()); });
    }

    // ══ PERSISTENCIA CHECKLIST ═══════════════════════════════════════════

    private void guardarEstadosChecklist() {
        if (estadosChecklist==null||clasesHoy.isEmpty()) return;
        String fechaHoy = new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date());
        StringBuilder estados=new StringBuilder(), retardos=new StringBuilder();
        for (int i=0;i<estadosChecklist.length;i++) {
            if(i>0){estados.append("|");retardos.append("|");}
            estados.append(estadosChecklist[i]!=null?estadosChecklist[i]:"futura");
            retardos.append(minutosRetardo!=null&&minutosRetardo.length>i?minutosRetardo[i]:0);
        }
        getSharedPreferences("AsisteDragonPrefs",MODE_PRIVATE).edit()
                .putString(KEY_CHECKLIST_ESTADOS,estados.toString())
                .putString(KEY_CHECKLIST_RETARDOS,retardos.toString())
                .putString(KEY_CHECKLIST_FECHA,fechaHoy).apply();
    }

    private void cargarEstadosChecklist() {
        if (estadosChecklist==null||clasesHoy.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs",MODE_PRIVATE);
        String fechaHoy=new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date());
        if (!fechaHoy.equals(prefs.getString(KEY_CHECKLIST_FECHA,""))) return;
        String eStr=prefs.getString(KEY_CHECKLIST_ESTADOS,""), rStr=prefs.getString(KEY_CHECKLIST_RETARDOS,"");
        if (eStr.isEmpty()) return;
        String[] est=eStr.split("\\|",-1), ret=rStr.split("\\|",-1);
        for (int i=0;i<estadosChecklist.length&&i<est.length;i++) {
            if ("marcada".equals(est[i])) {
                estadosChecklist[i]="marcada";
                if (minutosRetardo!=null&&i<ret.length) try{minutosRetardo[i]=Integer.parseInt(ret[i]);}catch(NumberFormatException ignored){}
            }
        }
    }

    private void limpiarEstadosChecklist() {
        getSharedPreferences("AsisteDragonPrefs",MODE_PRIVATE).edit()
                .remove(KEY_CHECKLIST_ESTADOS).remove(KEY_CHECKLIST_RETARDOS).remove(KEY_CHECKLIST_FECHA).apply();
        if (estadosChecklist!=null) Arrays.fill(estadosChecklist,null);
        if (minutosRetardo!=null)   Arrays.fill(minutosRetardo,0);
        clasesConfirmadasFirestore.clear();
    }

    // ══ HELPERS ══════════════════════════════════════════════════════════

    public static int calcularTolerancia(String horaInicio, String horaFin) {
        int ini=minutosDesde(horaInicio), fin=minutosDesde(horaFin);
        if (ini<0||fin<0) return 15;
        int dur=fin-ini;
        if (dur<=60) return 15; if (dur<=120) return 30; return 45;
    }

    public static String determinarEstado(String horaRegistroHHMM, String horaInicioClase, String horaFinClase) {
        int reg=minutosDesde(horaRegistroHHMM), ini=minutosDesde(horaInicioClase);
        if (reg<0||ini<0) return "puntual";
        return (reg<=ini+calcularTolerancia(horaInicioClase,horaFinClase))?"puntual":"retardo";
    }

    public static int minutosDesde(String hora) {
        if (hora==null||!hora.contains(":")) return -1;
        try { String[] p=hora.split(":"); return Integer.parseInt(p[0])*60+Integer.parseInt(p[1]); }
        catch (Exception e) { return -1; }
    }

    private int minutosAhora() {
        Calendar c=Calendar.getInstance(); return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);
    }

    private String extraerHHMM(String hora) {
        if (hora==null) return null;
        String[] p=hora.split(":"); return p.length>=2?p[0]+":"+p[1]:hora;
    }

    private String obtenerDiaHoy() {
        SimpleDateFormat sdf=new SimpleDateFormat("EEEE",new Locale("es","MX"));
        String dia=sdf.format(new Date()); return dia.substring(0,1).toUpperCase()+dia.substring(1).toLowerCase();
    }

    // ══ NAVEGACION / SESION ══════════════════════════════════════════════

    private void cargarDatosUsuario() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);

        // ── Fallback: si Firebase no tiene sesión usar prefs ──────────────
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null && (prefs.getString("user_uid", "").isEmpty())) {
            prefs.edit().putString("user_uid", firebaseUser.getUid()).apply();
        }

        userUid   = prefs.getString("user_uid",    "");
        userName  = prefs.getString("user_nombre", "Usuario");
        userTipo  = prefs.getString("user_tipo",   "");
        userTurno = prefs.getString("user_turno",  "");

        textoNombreUsuario.setText(userName);
        textoTipoTurno.setText(userTipo + " \u2022 " + userTurno);
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd 'de' MMMM", new Locale("es", "MX"));
        String f = sdf.format(new Date());
        textoFechaHoy.setText(f.substring(0, 1).toUpperCase() + f.substring(1));
    }

    private void configurarListeners() {
        botonRegistrarAsistencia.setOnClickListener(v -> validarYAbrirEscaner());

        cargarFotoPerfilHeader();
        if (headerFotoPerfil != null)
            headerFotoPerfil.setOnClickListener(v -> {
                startActivity(new Intent(this, Perfil.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            });

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeColors(
                    ContextCompat.getColor(this, R.color.green_primary),
                    0xFF52B788, 0xFF2E7D52);
            swipeRefresh.setProgressBackgroundColorSchemeColor(0xFFFEFCF8);
            swipeRefresh.setOnRefreshListener(() -> {
                if (diaCerradoHoy()) {
                    mostrarPantallaDiaCompletadoSilencioso();
                    swipeRefresh.setRefreshing(false);
                } else if (tieneClasesHoy()) {
                    verificarAsistenciaHoy();
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(() -> { if (swipeRefresh != null) swipeRefresh.setRefreshing(false); }, 1200);
                } else {
                    mostrarPantallaSinClasesHoy();
                    swipeRefresh.setRefreshing(false);
                }
            });
        }

        botonCerrarSesion.setOnClickListener(v -> mostrarDialogoCerrarSesion());
        if (layoutTerminarDia!=null) {
            android.widget.Button btn=layoutTerminarDia.findViewById(R.id.boton_terminar_dia);
            if (btn!=null) btn.setOnClickListener(v->manejarTerminarDia());
        }
        configurarBottomNavigation();
        verificarHorarioYConfigurarBoton();
    }

    private void validarYAbrirEscaner() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String clasesJson = prefs.getString("horario_clases_completas", null);
        if (clasesJson == null) {
            startActivity(new Intent(this, QRScannerActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            return;
        }
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Clase>>(){}.getType();
            List<Clase> todas = gson.fromJson(clasesJson, type);
            String diaHoy = obtenerDiaHoy();
            List<Clase> hoy = new ArrayList<>();
            for (Clase c : todas)
                if (c.getDia() != null && c.getDia().equalsIgnoreCase(diaHoy)) hoy.add(c);
            if (hoy.isEmpty()) {
                startActivity(new Intent(this, QRScannerActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                return;
            }
            Collections.sort(hoy, (a, b) -> {
                String ha = a.getHoraInicio() != null ? a.getHoraInicio() : "";
                String hb = b.getHoraInicio() != null ? b.getHoraInicio() : "";
                return ha.compareTo(hb);
            });
            Clase primera = hoy.get(0);
            int iniPrimera = minutosDesde(primera.getHoraInicio());
            int ahora      = minutosAhora();
            int ventana    = iniPrimera - 15;
            if (ahora < ventana) {
                int faltanMin = ventana - ahora;
                int h = faltanMin / 60, m = faltanMin % 60;
                String tiempoStr = h > 0 ? h + " h " + (m > 0 ? m + " min" : "") : m + " min";
                new android.app.AlertDialog.Builder(this).setTitle("Aún no es momento")
                        .setMessage("Podrás registrar tu asistencia a partir de las " + formatearMinutos(ventana)
                                + " (15 min antes de tu primera clase: " + primera.getHoraInicio() + ").\n\nFaltan " + tiempoStr + ".")
                        .setPositiveButton("Entendido", null).show();
                return;
            }
            boolean todasTerminadas = true;
            for (Clase c : hoy) {
                int fin = minutosDesde(c.getHoraFin());
                if (fin < 0 || ahora < fin) { todasTerminadas = false; break; }
            }
            if (todasTerminadas) {
                new android.app.AlertDialog.Builder(this).setTitle("Registro no disponible")
                        .setMessage("Todas tus clases de hoy ya han concluido. No es posible registrar asistencia.")
                        .setPositiveButton("Entendido", null).show();
                return;
            }
            startActivity(new Intent(this, QRScannerActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } catch (Exception e) {
            startActivity(new Intent(this, QRScannerActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    private String formatearMinutos(int totalMinutos) {
        int h = totalMinutos / 60, m = totalMinutos % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    private void configurarBottomNavigation() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_inicio);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_inicio)   return true;
            if (id == R.id.nav_reportes) {
                startActivity(new Intent(this, Reportes.class));
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

    private void mostrarDialogoCerrarSesion() {
        new AlertDialog.Builder(this).setTitle("Cerrar Sesion").setMessage("¿Que desea hacer?")
                .setPositiveButton("Bloquear app (usar PIN)", (d, w) -> cerrarSesionConPIN())
                .setNegativeButton("Cerrar sesion completamente", (d, w) -> cerrarSesionCompleta())
                .setNeutralButton("Cancelar", null).show();
    }

    private void cerrarSesionConPIN() {
        mAuth.signOut();
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String email = prefs.getString("user_email", "");
        prefs.edit().putBoolean("is_logged_in", false).apply();
        Intent i = new Intent(this, VerificarPinActivity.class);
        i.putExtra("email", email); i.putExtra("quick_access", true);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private void cerrarSesionCompleta() {
        new AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Deseas cerrar sesión completamente?")
                .setPositiveButton("Cerrar sesión", (d, w) -> {
                    SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
                    boolean tienePin = prefs.getBoolean("pin_configurado", false);

                    // Solo hacer signOut si NO tiene PIN — si tiene PIN
                    // conservamos la sesión de Firebase para que Firestore funcione
                    if (!tienePin) {
                        mAuth.signOut();
                    }

                    // Limpiar datos de UI/sesión local
                    prefs.edit()
                            .remove("user_nombre")
                            .remove("user_tipo")
                            .remove("user_turno")
                            .remove("user_numero_empleado")
                            .remove("user_telefono")
                            .remove("user_foto_base64")
                            .remove("is_logged_in")
                            .remove("checklist_estados_hoy")
                            .remove("checklist_retardos_hoy")
                            .remove("checklist_fecha_guardada")
                            .remove("dia_cerrado_fecha")
                            .remove("horario_owner_uid")
                            // ── AGREGAR ESTAS 5 ──────────────────────────────────
                            .remove("horario_materias")
                            .remove("horario_clases_completas")
                            .remove("horario_total_materias")
                            .remove("materias_confirmadas")
                            .remove("horario_periodo")
                            .remove("horario_vigencia")
                            .remove("session_persistent")
                            // ─────────────────────────────────────────────────────
                            // NO borrar user_uid, user_email, pin_hash, pin_configurado
                            .apply();

                    Intent i = new Intent(this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void verificarHorarioYConfigurarBoton() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        boolean tieneHorario = prefs.getBoolean("materias_confirmadas", false)
                && prefs.getString("horario_clases_completas", null) != null;

        if (!tieneHorario) {
            // Ocultar cámara
            if (botonRegistrarAsistencia != null)
                botonRegistrarAsistencia.setVisibility(View.GONE);

            // Ocultar textos normales
            if (textoFechaHoy != null)
                textoFechaHoy.setVisibility(View.GONE);

            View txtEscanea = findViewById(R.id.texto_escanea_qr);
            if (txtEscanea != null) txtEscanea.setVisibility(View.GONE);

            View txtAunNo = findViewById(R.id.texto_aun_no_registrado);
            if (txtAunNo != null) txtAunNo.setVisibility(View.GONE);

            View txtSinAsist = findViewById(R.id.texto_sin_asistencia_hoy);
            if (txtSinAsist != null) txtSinAsist.setVisibility(View.GONE);

            // Mostrar estado sin registrar, ocultar el resto
            if (estadoSinRegistrar != null) estadoSinRegistrar.setVisibility(View.VISIBLE);
            if (estadoRegistrado   != null) estadoRegistrado.setVisibility(View.GONE);
            if (cardHorarioBanner  != null) cardHorarioBanner.setVisibility(View.GONE);
            if (layoutTerminarDia  != null) layoutTerminarDia.setVisibility(View.GONE);

            // Inyectar tarjeta dentro del LinearLayout del estado_sin_registrar
            RelativeLayout rl = (RelativeLayout) estadoSinRegistrar;
            LinearLayout ll   = (LinearLayout) rl.getChildAt(0);

            // Evitar duplicar la tarjeta
            if (ll.findViewWithTag("card_sin_horario") != null) return;

            float dp = getResources().getDisplayMetrics().density;

            LinearLayout card = new LinearLayout(this);
            card.setTag("card_sin_horario");
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(android.view.Gravity.CENTER);
            card.setPadding((int)(28*dp), (int)(32*dp), (int)(28*dp), (int)(32*dp));
            // Sin fondo ni contorno
            LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardP.setMargins(0, (int)(12*dp), 0, 0);
            card.setLayoutParams(cardP);

            // Ícono de alerta
            android.widget.ImageView ico = new android.widget.ImageView(this);
            ico.setImageResource(android.R.drawable.ic_dialog_alert);
            ico.setColorFilter(0xFFFFB300, android.graphics.PorterDuff.Mode.SRC_IN);
            int icoSz = (int)(56 * dp);
            LinearLayout.LayoutParams icoP = new LinearLayout.LayoutParams(icoSz, icoSz);
            icoP.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            icoP.setMargins(0, 0, 0, (int)(16*dp));
            ico.setLayoutParams(icoP);
            card.addView(ico);

            // Título
            TextView titulo = new TextView(this);
            titulo.setText("Sin horario registrado");
            titulo.setTextSize(20);
            titulo.setTypeface(null, android.graphics.Typeface.BOLD);
            titulo.setTextColor(0xFF1A1A1A);
            titulo.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams titP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            titP.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            titP.setMargins(0, 0, 0, (int)(10*dp));
            titulo.setLayoutParams(titP);
            card.addView(titulo);

            // Descripción
            TextView desc = new TextView(this);
            desc.setText("Para registrar tu asistencia primero debes cargar tu horario oficial desde la pestaña Horario.");
            desc.setTextSize(13);
            desc.setTextColor(0xFF888888);
            desc.setGravity(android.view.Gravity.CENTER);
            desc.setLineSpacing(4f, 1f);
            LinearLayout.LayoutParams descP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            descP.setMargins(0, 0, 0, (int)(24*dp));
            desc.setLayoutParams(descP);
            card.addView(desc);

            // Botón verde
            TextView btnHorario = new TextView(this);
            btnHorario.setText("Ir a Horario  →");
            btnHorario.setTextSize(14);
            btnHorario.setTypeface(null, android.graphics.Typeface.BOLD);
            btnHorario.setTextColor(0xFFFFFFFF);
            btnHorario.setGravity(android.view.Gravity.CENTER);
            btnHorario.setPadding((int)(32*dp), (int)(14*dp), (int)(32*dp), (int)(14*dp));
            android.graphics.drawable.GradientDrawable btnBg =
                    new android.graphics.drawable.GradientDrawable();
            btnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            btnBg.setCornerRadius(40f);
            btnBg.setColor(ContextCompat.getColor(this, R.color.green_primary));
            btnHorario.setBackground(btnBg);
            LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            btnP.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            btnHorario.setLayoutParams(btnP);
            btnHorario.setOnClickListener(v -> {
                startActivity(new Intent(this, Horario.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            });
            card.addView(btnHorario);

            ll.addView(card);

        } else {
            // Restaurar visibilidad normal
            if (botonRegistrarAsistencia != null)
                botonRegistrarAsistencia.setVisibility(View.VISIBLE);
            if (textoFechaHoy != null)
                textoFechaHoy.setVisibility(View.VISIBLE);
            View txtEscanea = findViewById(R.id.texto_escanea_qr);
            if (txtEscanea != null) txtEscanea.setVisibility(View.VISIBLE);
            View txtAunNo = findViewById(R.id.texto_aun_no_registrado);
            if (txtAunNo != null) txtAunNo.setVisibility(View.VISIBLE);
        }
    }

    // ── onResume: ya NO tiene fade manual, el styles.xml lo maneja ──────
    @Override
    protected void onResume() {
        super.onResume();
        verificarHorarioYConfigurarBoton();
        if (diaCerradoHoy()) {
            mostrarPantallaDiaCompletadoSilencioso();
        } else if (tieneClasesHoy()) {
            verificarAsistenciaHoy();
        } else {
            mostrarPantallaSinClasesHoy();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (timerHandler != null && timerRunnable != null)
            timerHandler.removeCallbacks(timerRunnable);
    }
}