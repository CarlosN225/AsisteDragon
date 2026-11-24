package com.utfv.asistedragon;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;


public class LoginActivity extends AppCompatActivity {

    private TextInputEditText campoCorreo;
    private TextInputEditText campoContrasena;
    private Button botonIniciarSesion;
    private TextView textoOlvidoContrasena;
    private TextView textoIrRegistro;
    private LinearLayout botonUsarPin;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configurar barra de estado verde
        configurarBarraEstado();

        setContentView(R.layout.activity_login);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        inicializarVistas();
        configurarListeners();
    }

    private void configurarBarraEstado() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.green_dark));
    }

    private void inicializarVistas() {
        campoCorreo = findViewById(R.id.campo_correo);
        campoContrasena = findViewById(R.id.campo_contrasena);
        botonIniciarSesion = findViewById(R.id.boton_iniciar_sesion);
        textoOlvidoContrasena = findViewById(R.id.txt_forgot_password);
        textoIrRegistro = findViewById(R.id.texto_ir_registro);
        botonUsarPin = findViewById(R.id.boton_usar_pin);
    }

    private void configurarListeners() {
        // Botón Iniciar Sesión
        botonIniciarSesion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validarYLogin();
            }
        });

        // Link "¿Olvidó su contraseña?"
        textoOlvidoContrasena.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recuperarContrasena();
            }
        });

        // Link "Registrarse"
        textoIrRegistro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, RegistroActivity.class);
                startActivity(intent);
            }
        });

        // Botón "Usar PIN"
        botonUsarPin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                usarPIN();
            }
        });
    }



    private void validarYLogin() {
        String correo = campoCorreo.getText().toString().trim();
        String contrasena = campoContrasena.getText().toString();

        // Validaciones
        if (correo.isEmpty()) {
            campoCorreo.setError("Por favor, ingrese su correo");
            campoCorreo.requestFocus();
            return;
        }

        if (!correo.endsWith("@utfv.edu.mx")) {
            campoCorreo.setError("Debe usar su correo institucional (@utfv.edu.mx)");
            campoCorreo.requestFocus();
            return;
        }

        if (contrasena.isEmpty()) {
            campoContrasena.setError("Por favor, ingrese su contraseña");
            campoContrasena.requestFocus();
            return;
        }

        if (contrasena.length() < 6) {
            campoContrasena.setError("La contraseña debe tener al menos 6 caracteres");
            campoContrasena.requestFocus();
            return;
        }

        // Deshabilitar botón mientras se procesa
        botonIniciarSesion.setEnabled(false);
        botonIniciarSesion.setText("Iniciando sesión...");

        // Iniciar sesión con Firebase
        iniciarSesionFirebase(correo, contrasena);
    }

    private void iniciarSesionFirebase(final String correo, String contrasena) {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("correo", correo);
            json.put("contrasena", contrasena);
        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                json.toString(),
                okhttp3.MediaType.parse("application/json; charset=utf-8")
        );

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("http://10.0.2.2:3001/api/usuarios/login")
                .post(body)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    botonIniciarSesion.setEnabled(true);
                    botonIniciarSesion.setText("INICIAR SESIÓN");
                    Toast.makeText(LoginActivity.this,
                            "Error al conectar con Microservicio de Usuarios: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                String respuestaJson = response.body() != null ? response.body().string() : "";

                runOnUiThread(() -> {
                    botonIniciarSesion.setEnabled(true);
                    botonIniciarSesion.setText("INICIAR SESIÓN");

                    if (response.isSuccessful()) {
                        try {
                            org.json.JSONObject res = new org.json.JSONObject(respuestaJson);
                            org.json.JSONObject usuario = res.getJSONObject("usuario");

                            SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();

                            editor.putString("user_uid", usuario.optString("id", "usr_docente"));
                            editor.putString("user_email", usuario.optString("correo", correo));
                            editor.putString("user_nombre", usuario.optString("nombreCompleto", "Profesor"));
                            editor.putString("user_rol", usuario.optString("rol", "docente"));
                            editor.putString("user_tipo", usuario.optString("tipoProfesor", "Asignatura"));
                            editor.putString("user_turno", usuario.optString("turno", "Matutino"));
                            editor.putBoolean("is_logged_in", true);
                            editor.putBoolean("session_persistent", true);
                            editor.apply();

                            Toast.makeText(LoginActivity.this, "Sesión iniciada con Microservicio", Toast.LENGTH_SHORT).show();
                            irAPanelPrincipal();

                        } catch (Exception ex) {
                            Toast.makeText(LoginActivity.this, "Error al procesar usuario", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        String errorMsg = "Credenciales incorrectas";
                        try {
                            org.json.JSONObject errJson = new org.json.JSONObject(respuestaJson);
                            if (errJson.has("error")) errorMsg = errJson.getString("error");
                        } catch (Exception ignored) {}
                        Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void cargarDatosUsuario(String uid, final String correo) {
        db.collection("usuarios")
                .document(uid)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            // Guardar datos en SharedPreferences
                            Usuario usuario = documentSnapshot.toObject(Usuario.class);
                            if (usuario != null) {
                                guardarSesion(usuario);
                            }

                            // Login exitoso
                            Toast.makeText(LoginActivity.this, "Bienvenido", Toast.LENGTH_SHORT).show();
                            irAPanelPrincipal();
                        } else {
                            // Usuario autenticado pero sin datos en Firestore
                            Toast.makeText(LoginActivity.this, "Error: Datos de usuario no encontrados", Toast.LENGTH_SHORT).show();
                            botonIniciarSesion.setEnabled(true);
                            botonIniciarSesion.setText("INICIAR SESIÓN");
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(LoginActivity.this, "Error al cargar datos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        botonIniciarSesion.setEnabled(true);
                        botonIniciarSesion.setText("INICIAR SESIÓN");
                    }
                });
    }

    private void guardarSesion(Usuario usuario) {
        SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("user_uid", usuario.getUid());
        editor.putString("user_email", usuario.getCorreo());
        editor.putString("user_nombre", usuario.getNombreCompleto());
        editor.putString("user_rol", usuario.getRol());
        editor.putString("user_tipo", usuario.getTipoProfesor());  // ← AGREGAR
        editor.putString("user_turno", usuario.getTurno());  // ← AGREGAR
        editor.putBoolean("is_logged_in", true);
        editor.putBoolean("session_persistent", true);

        editor.apply();
    }


    private void recuperarContrasena() {
        String correo = campoCorreo.getText().toString().trim();

        if (correo.isEmpty()) {
            Toast.makeText(this, "Por favor, ingrese su correo para recuperar la contraseña", Toast.LENGTH_SHORT).show();
            campoCorreo.requestFocus();
            return;
        }

        if (!correo.endsWith("@utfv.edu.mx")) {
            Toast.makeText(this, "Debe usar su correo institucional (@utfv.edu.mx)", Toast.LENGTH_SHORT).show();
            campoCorreo.requestFocus();
            return;
        }

        // Deshabilitar mientras procesa
        textoOlvidoContrasena.setEnabled(false);

        // Enviar email de recuperación
        enviarEmailRecuperacionFirebase(correo);
    }

    private void enviarEmailRecuperacionFirebase(final String correo) {
        mAuth.sendPasswordResetEmail(correo)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(Task<Void> task) {
                        textoOlvidoContrasena.setEnabled(true);

                        if (task.isSuccessful()) {
                            mostrarDialogoEmailEnviado(correo);
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Error al enviar el correo: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void mostrarDialogoEmailEnviado(String correo) {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_email)
                .setTitle("Correo Enviado")
                .setMessage("Se ha enviado un correo de recuperación a:\n\n" +
                        " " + correo + "\n\n" +
                        "Por favor, revise su bandeja de entrada.\n\n" +
                        "IMPORTANTE: Si no lo encuentra, revise la carpeta de SPAM. " +
                        "Es común que estos correos lleguen ahí.\n\n" +
                        "El enlace es válido por 1 hora.")
                .setPositiveButton("Entendido", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setNeutralButton("Abrir Correo", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(intent);
                        } catch (android.content.ActivityNotFoundException e) {
                            Toast.makeText(LoginActivity.this,
                                    "No se encontró una app de correo",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void usarPIN() {
        // Obtener email
        String emailIngresado = campoCorreo.getText().toString().trim();

        if (emailIngresado.isEmpty()) {
            Toast.makeText(this, "Por favor, ingrese su correo para usar el PIN", Toast.LENGTH_SHORT).show();
            campoCorreo.requestFocus();
            return;
        }

        if (!emailIngresado.endsWith("@utfv.edu.mx")) {
            Toast.makeText(this, "Debe usar su correo institucional (@utfv.edu.mx)", Toast.LENGTH_SHORT).show();
            campoCorreo.requestFocus();
            return;
        }

        // Verificar en Firebase si este usuario tiene PIN configurado
        verificarPinEnFirebase(emailIngresado);
    }

    private void verificarPinEnFirebase(final String email) {
        botonUsarPin.setEnabled(false);

        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("http://10.0.2.2:3001/api/usuarios/perfil?correo=" + android.net.Uri.encode(email))
                .get()
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    botonUsarPin.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Error de conexión: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                runOnUiThread(() -> {
                    botonUsarPin.setEnabled(true);
                    if (response.isSuccessful()) {
                        getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                                .edit()
                                .putString("user_email", email)
                                .putBoolean("pin_configurado", true)
                                .apply();

                        Intent intent = new Intent(LoginActivity.this, VerificarPinActivity.class);
                        intent.putExtra("email", email);
                        startActivity(intent);
                    } else {
                        Toast.makeText(LoginActivity.this, "No existe una cuenta registrada con este correo", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void irAPanelPrincipal() {
        Intent intent = new Intent(LoginActivity.this, PanelPrincipalActivity.class);
        startActivity(intent);
        finish();
    }
}