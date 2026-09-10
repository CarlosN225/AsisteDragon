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
        mAuth.signInWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Login exitoso
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                // Cargar datos del usuario desde Firestore
                                cargarDatosUsuario(user.getUid(), correo);
                            }
                        } else {
                            // Error en el login
                            botonIniciarSesion.setEnabled(true);
                            botonIniciarSesion.setText("INICIAR SESIÓN");

                            String errorMsg = "Credenciales incorrectas";
                            if (task.getException() != null) {
                                String exceptionMsg = task.getException().getMessage();
                                if (exceptionMsg != null) {
                                    if (exceptionMsg.contains("no user record")) {
                                        errorMsg = "No existe una cuenta con este correo";
                                    } else if (exceptionMsg.contains("password is invalid")) {
                                        errorMsg = "Contraseña incorrecta";
                                    } else if (exceptionMsg.contains("network")) {
                                        errorMsg = "Error de conexión. Verifique su internet";
                                    }
                                }
                            }
                            Toast.makeText(LoginActivity.this, "  " + errorMsg, Toast.LENGTH_LONG).show();
                        }
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
        // Mostrar loading
        botonUsarPin.setEnabled(false);

        db.collection("usuarios")
                .whereEqualTo("correo", email)
                .limit(1)
                .get()
                .addOnSuccessListener(new OnSuccessListener<com.google.firebase.firestore.QuerySnapshot>() {
                    @Override
                    public void onSuccess(com.google.firebase.firestore.QuerySnapshot queryDocumentSnapshots) {
                        botonUsarPin.setEnabled(true);

                        if (!queryDocumentSnapshots.isEmpty()) {
                            // Usuario encontrado
                            DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                            Usuario usuario = document.toObject(Usuario.class);

                            if (usuario != null && usuario.isPinConfigurado()) {
                                // SÍ tiene PIN configurado

                                // Guardar datos localmente (incluido el PIN)
                                SharedPreferences prefs = getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();

                                editor.putString("user_email", email);
                                editor.putString("user_uid", usuario.getUid());
                                editor.putString("pin_hash", usuario.getPinHash());  // ← Guardar PIN localmente
                                editor.putBoolean("pin_configurado", true);
                                editor.apply();

                                // Ir a verificar PIN
                                Intent intent = new Intent(LoginActivity.this, VerificarPinActivity.class);
                                intent.putExtra("email", email);
                                startActivity(intent);
                            } else {
                                // NO tiene PIN configurado
                                Toast.makeText(LoginActivity.this,
                                        "Este usuario aún no ha configurado un PIN.\nPor favor, inicie sesión con su contraseña primero.",
                                        Toast.LENGTH_LONG).show();
                            }
                        } else {
                            // Usuario no encontrado
                            Toast.makeText(LoginActivity.this,
                                    "No existe una cuenta con este correo",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        botonUsarPin.setEnabled(true);
                        Toast.makeText(LoginActivity.this,
                                "Error de conexión: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void irAPanelPrincipal() {
        Intent intent = new Intent(LoginActivity.this, PanelPrincipalActivity.class);
        startActivity(intent);
        finish();
    }
}