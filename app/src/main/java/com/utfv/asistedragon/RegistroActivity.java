package com.utfv.asistedragon;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
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
import com.google.firebase.firestore.FirebaseFirestore;

public class RegistroActivity extends AppCompatActivity {

    // Declarar campos
    private TextInputEditText campoNombreCompleto;
    private TextInputEditText campoCorreo;
    private TextInputEditText campoNumeroEmpleado;
    private AutoCompleteTextView campoTipoProfesor;
    private AutoCompleteTextView campoTurno;
    private TextInputEditText campoTelefono;
    private TextInputEditText campoContrasena;
    private TextInputEditText campoConfirmarContrasena;
    private CheckBox checkTerminos;
    private Button botonRegistrarse;
    private TextView textoIrLogin;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Opciones para dropdowns
    private static final String[] TIPOS_PROFESOR = {
            "Tiempo Completo",
            "Asignatura"
    };

    private static final String[] TURNOS = {
            "Matutino",
            "Vespertino"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Barra de estado verde
        configurarBarraEstado();

        setContentView(R.layout.activity_registro);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Inicializar campos
        inicializarCampos();

        // Configurar dropdowns
        configurarDropdowns();

        // Configurar listeners
        configurarListeners();
    }

    private void configurarBarraEstado() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.green_dark));
    }

    private void inicializarCampos() {
        campoNombreCompleto = findViewById(R.id.campo_nombre_completo);
        campoCorreo = findViewById(R.id.campo_correo);
        campoNumeroEmpleado = findViewById(R.id.campo_numero_empleado);
        campoTipoProfesor = findViewById(R.id.campo_tipo_profesor);
        campoTurno = findViewById(R.id.campo_turno);
        campoTelefono = findViewById(R.id.campo_telefono);
        campoContrasena = findViewById(R.id.campo_contrasena);
        campoConfirmarContrasena = findViewById(R.id.campo_confirmar_contrasena);
        checkTerminos = findViewById(R.id.check_terminos);
        botonRegistrarse = findViewById(R.id.boton_registrarse);
        textoIrLogin = findViewById(R.id.texto_ir_login);
    }

    private void configurarDropdowns() {
        ArrayAdapter<String> adapterTipo = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                TIPOS_PROFESOR
        );
        campoTipoProfesor.setAdapter(adapterTipo);

        ArrayAdapter<String> adapterTurno = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                TURNOS
        );
        campoTurno.setAdapter(adapterTurno);
    }

    private void configurarListeners() {
        botonRegistrarse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validarYRegistrar();
            }
        });

        textoIrLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                irALogin();
            }
        });
    }

    private void validarYRegistrar() {
        // Obtener valores
        String nombreCompleto = campoNombreCompleto.getText().toString().trim();
        String correo = campoCorreo.getText().toString().trim();
        String numeroEmpleado = campoNumeroEmpleado.getText().toString().trim();
        String tipoProfesor = campoTipoProfesor.getText().toString().trim();
        String turno = campoTurno.getText().toString().trim();
        String telefono = campoTelefono.getText().toString().trim();
        String contrasena = campoContrasena.getText().toString();
        String confirmarContrasena = campoConfirmarContrasena.getText().toString();

        // Validaciones
        if (nombreCompleto.isEmpty()) {
            campoNombreCompleto.setError("Por favor, ingrese su nombre completo");
            campoNombreCompleto.requestFocus();
            return;
        }

        if (correo.isEmpty()) {
            campoCorreo.setError("Por favor, ingrese su correo institucional");
            campoCorreo.requestFocus();
            return;
        }

        if (!correo.endsWith("@utfv.edu.mx")) {
            campoCorreo.setError("Debe usar su correo institucional (@utfv.edu.mx)");
            campoCorreo.requestFocus();
            return;
        }

        if (numeroEmpleado.isEmpty()) {
            campoNumeroEmpleado.setError("Por favor, ingrese su número de empleado");
            campoNumeroEmpleado.requestFocus();
            return;
        }

        if (tipoProfesor.isEmpty()) {
            campoTipoProfesor.setError("Por favor, seleccione el tipo de profesor");
            campoTipoProfesor.requestFocus();
            return;
        }

        if (turno.isEmpty()) {
            campoTurno.setError("Por favor, seleccione el turno");
            campoTurno.requestFocus();
            return;
        }

        if (telefono.isEmpty()) {
            campoTelefono.setError("Por favor, ingrese su teléfono");
            campoTelefono.requestFocus();
            return;
        }

        if (contrasena.isEmpty()) {
            campoContrasena.setError("Por favor, ingrese una contraseña");
            campoContrasena.requestFocus();
            return;
        }

        if (contrasena.length() < 6) {
            campoContrasena.setError("La contraseña debe tener al menos 6 caracteres");
            campoContrasena.requestFocus();
            return;
        }

        if (!contrasena.equals(confirmarContrasena)) {
            campoConfirmarContrasena.setError("Las contraseñas no coinciden");
            campoConfirmarContrasena.requestFocus();
            return;
        }

        if (!checkTerminos.isChecked()) {
            Toast.makeText(this, "Debe aceptar los términos y condiciones", Toast.LENGTH_SHORT).show();
            return;
        }

        // Deshabilitar botón mientras se registra
        botonRegistrarse.setEnabled(false);
        botonRegistrarse.setText("Registrando...");

        // Registrar en Firebase
        registrarProfesor(nombreCompleto, correo, numeroEmpleado, tipoProfesor, turno, telefono, contrasena);
    }

    private void registrarProfesor(final String nombreCompleto, final String correo,
                                   final String numeroEmpleado, final String tipoProfesor,
                                   final String turno, final String telefono, String contrasena) {

        // Crear usuario en Firebase Authentication
        mAuth.createUserWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Registro exitoso
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                String uid = firebaseUser.getUid();

                                // Crear objeto Usuario
                                Usuario usuario = new Usuario(uid, nombreCompleto, correo,
                                        numeroEmpleado, tipoProfesor, turno, telefono);

                                // Guardar en Firestore
                                guardarEnFirestore(usuario);
                            }
                        } else {
                            // Error en el registro
                            botonRegistrarse.setEnabled(true);
                            botonRegistrarse.setText("REGISTRARSE");

                            String errorMsg = "Error al registrar: ";
                            if (task.getException() != null) {
                                errorMsg += task.getException().getMessage();
                            }
                            Toast.makeText(RegistroActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void guardarEnFirestore(Usuario usuario) {
        db.collection("usuarios")
                .document(usuario.getUid())
                .set(usuario)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Guardado exitoso
                        Toast.makeText(RegistroActivity.this,
                                "Ahora configure su PIN",
                                Toast.LENGTH_SHORT).show();

                        // Guardar email en SharedPreferences
                        getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                                .edit()
                                .putString("user_email", usuario.getCorreo())
                                .apply();

                        // Ir a Configurar PIN
                        Intent intent = new Intent(RegistroActivity.this, ConfigurarPinActivity.class);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        botonRegistrarse.setEnabled(true);
                        botonRegistrarse.setText("REGISTRARSE");
                        Toast.makeText(RegistroActivity.this,
                                "Error al guardar datos: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void irALogin() {
        Intent intent = new Intent(RegistroActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        irALogin();
    }
}