package com.utfv.asistedragon;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class Perfil extends AppCompatActivity {

    private static final int REQ_CAMARA = 102;
    private boolean modoEdicion = false;

    // ── Vistas lectura ────────────────────────────────────────────────
    private ImageView         imagenPerfil;
    private TextView          perfilNombre, perfilTipoTurno;
    private TextView          perfilNombreLabel, perfilEmail, perfilEmailNota;
    private TextView          perfilNumeroEmpleado;
    private TextView          perfilTipoDetalle, perfilTurnoDetalle;
    private TextView          perfilTelefono;
    private TextView          botonEditarPerfil, botonGuardarPerfil;

    // ── Vistas edición ────────────────────────────────────────────────
    private TextInputEditText perfilNombreEdit;
    private TextInputEditText perfilNumeroEmpleadoEdit;
    private TextInputEditText perfilTelefonoEdit;
    private Spinner           perfilTipoSpinner, perfilTurnoSpinner;

    // ── Acciones ──────────────────────────────────────────────────────
    private LinearLayout btnCambiarPin, btnCambiarPassword;
    private LinearLayout btnBloquearPin, btnCerrarSesionPerfil;

    // ── Firebase ──────────────────────────────────────────────────────
    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;
    private FirebaseUser      currentUser;

    // ── Datos ─────────────────────────────────────────────────────────
    private String userUid, userName, userEmail, userTipo, userTurno,
            userNumEmp, userTelefono;

    // ── Launchers ─────────────────────────────────────────────────────
    private ActivityResultLauncher<Intent> galeriaLauncher;
    private ActivityResultLauncher<Intent> camaraLauncher;

    private static final String[] TIPOS_PROFESOR = {"Tiempo Completo", "Asignatura"};
    private static final String[] TURNOS         = {"Matutino", "Vespertino"};

    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configurarBarraEstado();
        setContentView(R.layout.activity_perfil);

        mAuth       = FirebaseAuth.getInstance();
        db          = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        inicializarVistas();
        configurarSpinners();
        cargarDatosLocales();
        cargarFotoPerfilCompleta();
        configurarListeners();
        registrarLaunchers();

        // ── Animación de entrada: el styles.xml ya hace el fade global.
        //    No se necesita nada más aquí.
    }

    // ══ CONFIGURACIÓN ═════════════════════════════════════════════════

    private void configurarBarraEstado() {
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(ContextCompat.getColor(this, R.color.green_primary));
    }

    private void inicializarVistas() {
        imagenPerfil             = findViewById(R.id.imagen_perfil);
        perfilNombre             = findViewById(R.id.perfil_nombre);
        perfilTipoTurno          = findViewById(R.id.perfil_tipo_turno);
        perfilNombreLabel        = findViewById(R.id.perfil_nombre_label);
        perfilEmail              = findViewById(R.id.perfil_email);
        perfilEmailNota          = findViewById(R.id.perfil_email_nota);
        perfilNumeroEmpleado     = findViewById(R.id.perfil_numero_empleado);
        perfilTipoDetalle        = findViewById(R.id.perfil_tipo_detalle);
        perfilTurnoDetalle       = findViewById(R.id.perfil_turno_detalle);
        perfilTelefono           = findViewById(R.id.perfil_telefono);
        botonEditarPerfil        = findViewById(R.id.boton_editar_perfil);
        botonGuardarPerfil       = findViewById(R.id.boton_guardar_perfil);
        perfilNombreEdit         = findViewById(R.id.perfil_nombre_edit);
        perfilNumeroEmpleadoEdit = findViewById(R.id.perfil_numero_empleado_edit);
        perfilTelefonoEdit       = findViewById(R.id.perfil_telefono_edit);
        perfilTipoSpinner        = findViewById(R.id.perfil_tipo_spinner);
        perfilTurnoSpinner       = findViewById(R.id.perfil_turno_spinner);
        btnCambiarPin            = findViewById(R.id.btn_cambiar_pin);
        btnCambiarPassword       = findViewById(R.id.btn_cambiar_password);
        btnBloquearPin           = findViewById(R.id.btn_bloquear_pin);
        btnCerrarSesionPerfil    = findViewById(R.id.btn_cerrar_sesion_perfil);
    }

    private void configurarSpinners() {
        ArrayAdapter<String> adTipo = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TIPOS_PROFESOR);
        adTipo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        perfilTipoSpinner.setAdapter(adTipo);

        ArrayAdapter<String> adTurno = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TURNOS);
        adTurno.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        perfilTurnoSpinner.setAdapter(adTurno);
    }

    private void seleccionarEnSpinner(Spinner spinner, String[] opciones, String valor) {
        for (int i = 0; i < opciones.length; i++) {
            if (opciones[i].equalsIgnoreCase(valor)) {
                spinner.setSelection(i);
                return;
            }
        }
        spinner.setSelection(0);
    }

    // ══ CARGA DE DATOS ════════════════════════════════════════════════

    private void cargarDatosLocales() {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        userUid      = prefs.getString("user_uid",             "");
        userName     = prefs.getString("user_nombre",          "Usuario");
        userEmail    = prefs.getString("user_email",           "");
        userTipo     = prefs.getString("user_tipo",            "Asignatura");
        userTurno    = prefs.getString("user_turno",           "Matutino");
        userNumEmp   = prefs.getString("user_numero_empleado", "—");
        userTelefono = prefs.getString("user_telefono",        "—");

        poblarVistasModoVer();

        if (!userUid.isEmpty()) {
            db.collection("usuarios").document(userUid).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) return;
                        String nombre = doc.getString("nombre");
                        String tipo   = doc.getString("tipo");
                        String turno  = doc.getString("turno");
                        String numEmp = doc.getString("numeroEmpleado");
                        String tel    = doc.getString("telefono");

                        if (nombre != null && !nombre.isEmpty()) userName     = nombre;
                        if (tipo   != null && !tipo.isEmpty())   userTipo     = tipo;
                        if (turno  != null && !turno.isEmpty())  userTurno    = turno;
                        if (numEmp != null && !numEmp.isEmpty()) userNumEmp   = numEmp;
                        if (tel    != null && !tel.isEmpty())    userTelefono = tel;

                        prefs.edit()
                                .putString("user_nombre",          userName)
                                .putString("user_tipo",            userTipo)
                                .putString("user_turno",           userTurno)
                                .putString("user_numero_empleado", userNumEmp)
                                .putString("user_telefono",        userTelefono)
                                .apply();

                        poblarVistasModoVer();
                    });
        }
    }

    private void poblarVistasModoVer() {
        perfilNombre.setText(userName);
        perfilTipoTurno.setText(userTipo + " • " + userTurno);
        perfilNombreLabel.setText(userName);
        perfilEmail.setText(userEmail);
        perfilNumeroEmpleado.setText(userNumEmp.isEmpty() ? "—" : userNumEmp);
        perfilTipoDetalle.setText(userTipo);
        perfilTurnoDetalle.setText(userTurno);
        perfilTelefono.setText(userTelefono.isEmpty() ? "—" : userTelefono);
    }

    // ══ FOTO — Base64 en Firestore ════════════════════════════════════

    private void cargarFotoPerfilCompleta() {
        if (imagenPerfil == null) return;
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String base64Local = prefs.getString("user_foto_base64", "");

        if (!base64Local.isEmpty()) {
            mostrarBase64EnImageView(base64Local);
        }

        if (!userUid.isEmpty()) {
            db.collection("usuarios").document(userUid).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) return;
                        String base64Firestore = doc.getString("fotoBase64");
                        if (base64Firestore != null && !base64Firestore.isEmpty()
                                && !base64Firestore.equals(base64Local)) {
                            prefs.edit().putString("user_foto_base64", base64Firestore).apply();
                            mostrarBase64EnImageView(base64Firestore);
                        }
                    });
        }
    }

    private void mostrarBase64EnImageView(String base64) {
        if (base64 == null || base64.isEmpty() || imagenPerfil == null) return;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null) {
                Glide.with(this)
                        .load(bmp)
                        .apply(new RequestOptions().circleCrop())
                        .into(imagenPerfil);
            }
        } catch (Exception ignored) {}
    }

    private void mostrarOpcionesFoto() {
        new AlertDialog.Builder(this)
                .setTitle("Foto de perfil")
                .setItems(new String[]{"Galería", "Cámara", "Eliminar foto"}, (d, which) -> {
                    if (which == 0) abrirGaleria();
                    else if (which == 1) abrirCamara();
                    else eliminarFoto();
                }).show();
    }

    private void abrirGaleria() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galeriaLauncher.launch(intent);
    }

    private void abrirCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMARA);
            return;
        }
        camaraLauncher.launch(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
    }

    private void registrarLaunchers() {
        galeriaLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) procesarYGuardarFoto(uri, null);
                    }
                });
        camaraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bitmap bmp = (Bitmap) result.getData().getExtras().get("data");
                        if (bmp != null) procesarYGuardarFoto(null, bmp);
                    }
                });
    }

    private void procesarYGuardarFoto(Uri uri, Bitmap bmpDirecto) {
        if (userUid == null || userUid.isEmpty()) {
            Toast.makeText(this, "Error: usuario no identificado", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Procesando foto…", Toast.LENGTH_SHORT).show();
        try {
            Bitmap bitmap = bmpDirecto != null
                    ? bmpDirecto
                    : MediaStore.Images.Media.getBitmap(getContentResolver(), uri);

            int maxSz = 512;
            float scale = Math.min(
                    (float) maxSz / bitmap.getWidth(),
                    (float) maxSz / bitmap.getHeight());
            if (scale < 1f) {
                bitmap = Bitmap.createScaledBitmap(bitmap,
                        (int) (bitmap.getWidth() * scale),
                        (int) (bitmap.getHeight() * scale), true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

            mostrarBase64EnImageView(base64);
            getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                    .edit().putString("user_foto_base64", base64).apply();

            Map<String, Object> fotoData = new HashMap<>();
            fotoData.put("fotoBase64", base64);
            db.collection("usuarios").document(userUid)
                    .set(fotoData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(v ->
                            Toast.makeText(this, "Foto actualizada ✓", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            Toast.makeText(this, "Error procesando imagen: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void eliminarFoto() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar foto")
                .setMessage("¿Deseas eliminar tu foto de perfil?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    Map<String, Object> sinFoto = new HashMap<>();
                    sinFoto.put("fotoBase64", "");
                    db.collection("usuarios").document(userUid)
                            .set(sinFoto, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                imagenPerfil.setImageResource(R.drawable.ic_persona_perfil);
                                getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                                        .edit().remove("user_foto_base64").apply();
                                Toast.makeText(this, "Foto eliminada", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancelar", null).show();
    }

    // ══ MODO EDICIÓN ══════════════════════════════════════════════════

    private void activarModoEdicion() {
        modoEdicion = true;
        botonEditarPerfil.setVisibility(View.GONE);
        botonGuardarPerfil.setVisibility(View.VISIBLE);

        perfilNombreEdit.setText(userName);
        perfilNumeroEmpleadoEdit.setText(userNumEmp.equals("—") ? "" : userNumEmp);
        perfilTelefonoEdit.setText(userTelefono.equals("—") ? "" : userTelefono);

        seleccionarEnSpinner(perfilTipoSpinner, TIPOS_PROFESOR, userTipo);
        seleccionarEnSpinner(perfilTurnoSpinner, TURNOS, userTurno);

        perfilNombreLabel.setVisibility(View.GONE);
        perfilNombreEdit.setVisibility(View.VISIBLE);
        perfilNombreEdit.requestFocus();

        perfilNumeroEmpleado.setVisibility(View.GONE);
        perfilNumeroEmpleadoEdit.setVisibility(View.VISIBLE);

        perfilTelefono.setVisibility(View.GONE);
        perfilTelefonoEdit.setVisibility(View.VISIBLE);

        perfilTipoDetalle.setVisibility(View.GONE);
        perfilTipoSpinner.setVisibility(View.VISIBLE);

        perfilTurnoDetalle.setVisibility(View.GONE);
        perfilTurnoSpinner.setVisibility(View.VISIBLE);

        perfilEmailNota.setVisibility(View.VISIBLE);
        perfilEmail.setAlpha(0.5f);
    }

    private void desactivarModoEdicion() {
        modoEdicion = false;
        botonEditarPerfil.setVisibility(View.VISIBLE);
        botonGuardarPerfil.setVisibility(View.GONE);

        poblarVistasModoVer();

        perfilNombreLabel.setVisibility(View.VISIBLE);
        perfilNombreEdit.setVisibility(View.GONE);

        perfilNumeroEmpleado.setVisibility(View.VISIBLE);
        perfilNumeroEmpleadoEdit.setVisibility(View.GONE);

        perfilTelefono.setVisibility(View.VISIBLE);
        perfilTelefonoEdit.setVisibility(View.GONE);

        perfilTipoDetalle.setVisibility(View.VISIBLE);
        perfilTipoSpinner.setVisibility(View.GONE);

        perfilTurnoDetalle.setVisibility(View.VISIBLE);
        perfilTurnoSpinner.setVisibility(View.GONE);

        perfilEmailNota.setVisibility(View.GONE);
        perfilEmail.setAlpha(1f);

        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private void guardarCambios() {
        String nuevoNombre = perfilNombreEdit.getText().toString().trim();
        String nuevoNumEmp = perfilNumeroEmpleadoEdit.getText().toString().trim();
        String nuevoTel    = perfilTelefonoEdit.getText().toString().trim();
        String nuevoTipo   = TIPOS_PROFESOR[perfilTipoSpinner.getSelectedItemPosition()];
        String nuevoTurno  = TURNOS[perfilTurnoSpinner.getSelectedItemPosition()];

        if (nuevoNombre.isEmpty()) {
            perfilNombreEdit.setError("El nombre no puede estar vacío");
            perfilNombreEdit.requestFocus();
            return;
        }

        userName     = nuevoNombre;
        userNumEmp   = nuevoNumEmp.isEmpty() ? "—" : nuevoNumEmp;
        userTelefono = nuevoTel.isEmpty() ? "—" : nuevoTel;
        userTipo     = nuevoTipo;
        userTurno    = nuevoTurno;

        getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE).edit()
                .putString("user_nombre",          userName)
                .putString("user_numero_empleado", userNumEmp)
                .putString("user_telefono",        userTelefono)
                .putString("user_tipo",            userTipo)
                .putString("user_turno",           userTurno)
                .apply();

        Map<String, Object> updates = new HashMap<>();
        updates.put("nombre",         userName);
        updates.put("numeroEmpleado", userNumEmp.equals("—") ? "" : userNumEmp);
        updates.put("telefono",       userTelefono.equals("—") ? "" : userTelefono);
        updates.put("tipo",           userTipo);
        updates.put("turno",          userTurno);

        db.collection("usuarios").document(userUid)
                .update(updates)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Perfil actualizado ✓", Toast.LENGTH_SHORT).show();
                    desactivarModoEdicion();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    desactivarModoEdicion();
                });
    }

    // ══ PIN ══════════════════════════════════════════════════════════════

    private void mostrarCambiarPin() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 8);

        android.widget.EditText etActual = crearEditPIN(layout, "PIN actual (4 dígitos)", false);
        android.widget.EditText etNuevo  = crearEditPIN(layout, "Nuevo PIN (4 dígitos)", true);
        android.widget.EditText etConf   = crearEditPIN(layout, "Confirmar nuevo PIN", true);

        new AlertDialog.Builder(this)
                .setTitle("Cambiar PIN")
                .setView(layout)
                .setPositiveButton("Guardar", (d, w) -> {
                    String actual  = etActual.getText().toString().trim();
                    String nuevo   = etNuevo.getText().toString().trim();
                    String confirm = etConf.getText().toString().trim();
                    if (actual.length() != 4 || nuevo.length() != 4) {
                        Toast.makeText(this, "El PIN debe tener 4 dígitos",
                                Toast.LENGTH_SHORT).show(); return;
                    }
                    if (!nuevo.equals(confirm)) {
                        Toast.makeText(this, "Los PINs no coinciden",
                                Toast.LENGTH_SHORT).show(); return;
                    }
                    validarYCambiarPin(actual, nuevo);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private android.widget.EditText crearEditPIN(LinearLayout parent, String hint,
                                                 boolean conMargen) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(hint);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(4)});
        if (conMargen) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, 12, 0, 0);
            et.setLayoutParams(p);
        }
        parent.addView(et);
        return et;
    }

    private void validarYCambiarPin(String pinActual, String pinNuevo) {
        if (userUid.isEmpty()) return;
        db.collection("usuarios").document(userUid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String hashGuardado = doc.getString("pinHash");
                    if (hashGuardado == null || !hashGuardado.equals(hashPin(pinActual))) {
                        Toast.makeText(this, "PIN actual incorrecto", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> upd = new HashMap<>();
                    upd.put("pinHash", hashPin(pinNuevo));
                    upd.put("pinConfigurado", true);
                    db.collection("usuarios").document(userUid).update(upd)
                            .addOnSuccessListener(v ->
                                    Toast.makeText(this, "PIN actualizado ✓",
                                            Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Error al guardar PIN",
                                            Toast.LENGTH_SHORT).show());
                });
    }

    private String hashPin(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(pin.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return pin; }
    }

    // ══ CONTRASEÑA ════════════════════════════════════════════════════

    private void mostrarCambiarPassword() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 8);

        android.widget.EditText etActual = new android.widget.EditText(this);
        etActual.setHint("Contraseña actual");
        etActual.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etActual);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 12, 0, 0);

        android.widget.EditText etNueva = new android.widget.EditText(this);
        etNueva.setHint("Nueva contraseña (mín. 6 caracteres)");
        etNueva.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etNueva.setLayoutParams(p);
        layout.addView(etNueva);

        android.widget.EditText etConfirm = new android.widget.EditText(this);
        etConfirm.setHint("Confirmar nueva contraseña");
        etConfirm.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etConfirm.setLayoutParams(p);
        layout.addView(etConfirm);

        new AlertDialog.Builder(this)
                .setTitle("Cambiar contraseña")
                .setView(layout)
                .setPositiveButton("Guardar", (d, w) -> {
                    String actual  = etActual.getText().toString().trim();
                    String nueva   = etNueva.getText().toString().trim();
                    String confirm = etConfirm.getText().toString().trim();
                    if (actual.isEmpty() || nueva.isEmpty()) {
                        Toast.makeText(this, "Completa todos los campos",
                                Toast.LENGTH_SHORT).show(); return;
                    }
                    if (nueva.length() < 6) {
                        Toast.makeText(this, "Mínimo 6 caracteres",
                                Toast.LENGTH_SHORT).show(); return;
                    }
                    if (!nueva.equals(confirm)) {
                        Toast.makeText(this, "Las contraseñas no coinciden",
                                Toast.LENGTH_SHORT).show(); return;
                    }
                    reautenticarYCambiarPassword(actual, nueva);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void reautenticarYCambiarPassword(String actual, String nueva) {
        if (currentUser == null || userEmail.isEmpty()) return;
        AuthCredential cred = EmailAuthProvider.getCredential(userEmail, actual);
        currentUser.reauthenticate(cred)
                .addOnSuccessListener(v ->
                        currentUser.updatePassword(nueva)
                                .addOnSuccessListener(v2 ->
                                        Toast.makeText(this, "Contraseña actualizada ✓",
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Error: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Contraseña actual incorrecta",
                                Toast.LENGTH_SHORT).show());
    }

    // ══ SESIÓN ════════════════════════════════════════════════════════

    private void bloquearConPin() {
        mAuth.signOut();
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        String email = prefs.getString("user_email", "");
        prefs.edit().putBoolean("is_logged_in", false).apply();
        Intent i = new Intent(this, VerificarPinActivity.class);
        i.putExtra("email", email);
        i.putExtra("quick_access", true);
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
    // ══ LISTENERS ════════════════════════════════════════════════════

    private void configurarListeners() {
        imagenPerfil.setOnClickListener(v -> mostrarOpcionesFoto());
        View btnEditFoto = findViewById(R.id.boton_editar_foto);
        if (btnEditFoto != null)
            btnEditFoto.setOnClickListener(v -> mostrarOpcionesFoto());

        botonEditarPerfil.setOnClickListener(v -> activarModoEdicion());
        botonGuardarPerfil.setOnClickListener(v -> guardarCambios());

        btnCambiarPin.setOnClickListener(v -> mostrarCambiarPin());
        btnCambiarPassword.setOnClickListener(v -> mostrarCambiarPassword());
        btnBloquearPin.setOnClickListener(v -> bloquearConPin());
        btnCerrarSesionPerfil.setOnClickListener(v -> cerrarSesionCompleta());

        configurarBottomNavigation();
    }

    @Override
    public void onBackPressed() {
        if (modoEdicion) {
            desactivarModoEdicion();
        } else {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    private void configurarBottomNavigation() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setSelectedItemId(R.id.nav_perfil);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_perfil)   return true;
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
            if (id == R.id.nav_horario) {
                startActivity(new Intent(this, Horario.class));
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