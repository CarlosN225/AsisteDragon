package com.utfv.asistedragon;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class VerificarPinActivity extends AppCompatActivity {

    // Vistas
    private TextView textoEmailUsuario;
    private TextView textoUsarContrasena;
    private View punto1, punto2, punto3, punto4;

    // Variables de control
    private StringBuilder pinActual = new StringBuilder();
    private final int PIN_LONGITUD = 4;
    private int intentosFallidos = 0;
    private final int MAX_INTENTOS = 3;

    // Datos del usuario
    private String emailUsuario;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean quickAccess = false; // Indica si viene desde Splash (acceso rápido)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configurar barra de estado
        configurarBarraEstado();

        setContentView(R.layout.activity_verificar_pin);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        // Verificar si es acceso rápido
        quickAccess = getIntent().getBooleanExtra("quick_access", false);  // ← AGREGAR

        inicializarVistas();
        configurarTecladoNumerico();
        cargarEmailUsuario();
    }

    private void configurarBarraEstado() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.green_dark));
    }

    private void inicializarVistas() {
        textoEmailUsuario = findViewById(R.id.texto_email_usuario);
        textoUsarContrasena = findViewById(R.id.texto_usar_contrasena);

        punto1 = findViewById(R.id.punto_1);
        punto2 = findViewById(R.id.punto_2);
        punto3 = findViewById(R.id.punto_3);
        punto4 = findViewById(R.id.punto_4);
    }

    private void configurarTecladoNumerico() {
        // Botones numéricos
        int[] botonesNumericos = {
                R.id.boton_0, R.id.boton_1, R.id.boton_2, R.id.boton_3,
                R.id.boton_4, R.id.boton_5, R.id.boton_6, R.id.boton_7,
                R.id.boton_8, R.id.boton_9
        };

        for (int idBoton : botonesNumericos) {
            Button boton = findViewById(idBoton);
            boton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    agregarDigito(((Button) v).getText().toString());
                }
            });
        }

        // Botón borrar
        Button botonBorrar = findViewById(R.id.boton_borrar);
        botonBorrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                borrarDigito();
            }
        });

        // Link para usar contraseña
        textoUsarContrasena.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                irALogin();
            }
        });
    }

    private void cargarEmailUsuario() {
        // Obtener email del Intent o de SharedPreferences
        emailUsuario = getIntent().getStringExtra("email");

        if (emailUsuario == null || emailUsuario.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
            emailUsuario = prefs.getString("user_email", "usuario@utfv.edu.mx");
        }

        textoEmailUsuario.setText(emailUsuario);
    }

    private void agregarDigito(String digito) {
        if (pinActual.length() < PIN_LONGITUD) {
            pinActual.append(digito);
            actualizarPuntos();

            // Si ya tiene 4 dígitos, verificar automáticamente
            if (pinActual.length() == PIN_LONGITUD) {
                punto1.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        verificarPin();
                    }
                }, 300);
            }
        }
    }

    private void borrarDigito() {
        if (pinActual.length() > 0) {
            pinActual.deleteCharAt(pinActual.length() - 1);
            actualizarPuntos();
        }
    }

    private void actualizarPuntos() {
        int longitud = pinActual.length();

        punto1.setBackgroundResource(longitud >= 1 ? R.drawable.punto_pin_lleno : R.drawable.punto_pin_vacio);
        punto2.setBackgroundResource(longitud >= 2 ? R.drawable.punto_pin_lleno : R.drawable.punto_pin_vacio);
        punto3.setBackgroundResource(longitud >= 3 ? R.drawable.punto_pin_lleno : R.drawable.punto_pin_vacio);
        punto4.setBackgroundResource(longitud >= 4 ? R.drawable.punto_pin_lleno : R.drawable.punto_pin_vacio);
    }

    private void verificarPin() {
        String pinIngresado = pinActual.toString();

        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("correo", emailUsuario);
            json.put("pin", pinIngresado);
        } catch (Exception ignored) {}

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                json.toString(),
                okhttp3.MediaType.parse("application/json; charset=utf-8")
        );

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("http://10.0.2.2:3001/api/usuarios/verificar-pin")
                .post(body)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    // Fallback local si no hay conexión momentánea
                    SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
                    String hashGuardado = prefs.getString("pin_hash", "");
                    if (!hashGuardado.isEmpty() && SecurityUtils.verificarPIN(pinIngresado, hashGuardado)) {
                        Toast.makeText(VerificarPinActivity.this, "Acceso concedido", Toast.LENGTH_SHORT).show();
                        irAPanelPrincipal();
                    } else {
                        Toast.makeText(VerificarPinActivity.this, "Error de red con microservicio", Toast.LENGTH_SHORT).show();
                        reiniciarPin();
                    }
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String resStr = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            org.json.JSONObject res = new org.json.JSONObject(resStr);
                            org.json.JSONObject u = res.getJSONObject("usuario");
                            getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE).edit()
                                    .putString("user_uid", u.optString("id", "usr_docente"))
                                    .putString("user_email", u.optString("correo", emailUsuario))
                                    .putString("user_nombre", u.optString("nombreCompleto", "Docente"))
                                    .putString("user_rol", u.optString("rol", "docente"))
                                    .putString("user_tipo", u.optString("tipoProfesor", "Asignatura"))
                                    .putString("user_turno", u.optString("turno", "Matutino"))
                                    .putString("user_numero_empleado", u.optString("numeroEmpleado", "—"))
                                    .putString("user_telefono", u.optString("telefono", "—"))
                                    .putBoolean("is_logged_in", true)
                                    .putBoolean("session_persistent", true)
                                    .apply();
                        } catch (Exception ignored) {}
                        Toast.makeText(VerificarPinActivity.this, "Acceso concedido ✓", Toast.LENGTH_SHORT).show();
                        irAPanelPrincipal();
                    } else {
                        intentosFallidos++;
                        int intentosRestantes = MAX_INTENTOS - intentosFallidos;
                        if (intentosFallidos >= MAX_INTENTOS) {
                            Toast.makeText(VerificarPinActivity.this,
                                    "Máximo de intentos alcanzado. Use su contraseña.",
                                    Toast.LENGTH_LONG).show();
                            if (quickAccess) {
                                getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                                        .edit().putBoolean("session_persistent", false).apply();
                            }
                            irALogin();
                        } else {
                            Toast.makeText(VerificarPinActivity.this,
                                    "PIN incorrecto. Intentos restantes: " + intentosRestantes,
                                    Toast.LENGTH_SHORT).show();
                            reiniciarPin();
                        }
                    }
                });
            }
        });
    }

    private void cargarDatosUsuarioPorEmail(String email) {
        db.collection("usuarios")
                .whereEqualTo("correo", email)
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        DocumentSnapshot doc = query.getDocuments().get(0);
                        Usuario usuario = doc.toObject(Usuario.class);
                        if (usuario != null) {
                            guardarSesion(usuario);
                        }
                        irAPanelPrincipal();
                    } else {
                        Toast.makeText(this,
                                "No se encontraron datos. Inicie sesión con contraseña.",
                                Toast.LENGTH_LONG).show();
                        irALogin();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Error de conexión. Inicie sesión con contraseña.",
                            Toast.LENGTH_LONG).show();
                    irALogin();
                });
    }


    private void autenticarConPIN() {
        // El PIN es correcto, pero necesitamos autenticar en Firebase
        // Como no tenemos la contraseña, verificamos si ya hay sesión activa

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null && currentUser.getEmail().equals(emailUsuario)) {
            // Ya hay sesión activa con este usuario
            Toast.makeText(this, "Acceso concedido", Toast.LENGTH_SHORT).show();
            cargarDatosUsuarioYContinuar(currentUser.getUid());
        } else {
            // No hay sesión activa, el PIN solo funciona si hay sesión previa
            Toast.makeText(this,
                    "Sesión expirada. Por favor, inicie sesión con su contraseña.",
                    Toast.LENGTH_LONG).show();
            irALogin();
        }
    }

    private void cargarDatosUsuarioYContinuar(String uid) {
        db.collection("usuarios")
                .document(uid)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            Usuario usuario = documentSnapshot.toObject(Usuario.class);
                            if (usuario != null) {
                                guardarSesion(usuario);
                            }
                            irAPanelPrincipal();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(VerificarPinActivity.this,
                                "Error al cargar datos",
                                Toast.LENGTH_SHORT).show();
                        irALogin();
                    }
                });
    }

    private void guardarSesion(Usuario usuario) {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // ── Guardar UID — priorizar el que ya tenemos en prefs si el objeto no lo trae ──
        String uidActual = prefs.getString("user_uid", "");
        String uidUsuario = usuario.getUid();
        String uidFinal = (uidUsuario != null && !uidUsuario.isEmpty())
                ? uidUsuario : uidActual;

        editor.putString("user_uid",    uidFinal);
        editor.putString("user_email",  usuario.getCorreo() != null
                ? usuario.getCorreo() : prefs.getString("user_email", ""));
        editor.putString("user_nombre", usuario.getNombreCompleto() != null
                ? usuario.getNombreCompleto() : prefs.getString("user_nombre", ""));
        editor.putString("user_rol",    usuario.getRol());
        editor.putString("user_tipo",   usuario.getTipoProfesor() != null
                ? usuario.getTipoProfesor() : prefs.getString("user_tipo", ""));
        editor.putString("user_turno",  usuario.getTurno() != null
                ? usuario.getTurno() : prefs.getString("user_turno", ""));
        editor.putBoolean("is_logged_in",       true);
        editor.putBoolean("session_persistent", true);
        editor.apply();
    }

    private void reiniciarPin() {
        pinActual = new StringBuilder();
        actualizarPuntos();
    }

    private void irAPanelPrincipal() {
        Intent intent = new Intent(VerificarPinActivity.this, PanelPrincipalActivity.class);
        startActivity(intent);
        finish();
    }

    private void irALogin() {
        Intent intent = new Intent(VerificarPinActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    @android.annotation.SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        // Ir al login si presiona atrás
        irALogin();
    }
}