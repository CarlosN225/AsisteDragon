package com.utfv.asistedragon;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.OnSuccessListener;

public class ConfigurarPinActivity extends AppCompatActivity {

    // Vistas
    private TextView textoTitulo;
    private TextView textoInstruccion;
    private View punto1, punto2, punto3, punto4;
    private View indicadorPaso1, indicadorPaso2;
    private Button botonBorrar;

    // Variables de control
    private StringBuilder pinActual = new StringBuilder();
    private String primerPin = "";
    private boolean esPrimerPaso = true;
    private final int PIN_LONGITUD = 4; // Solo 4 dígitos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configurar barra de estado
        configurarBarraEstado();

        setContentView(R.layout.activity_configurar_pin);

        inicializarVistas();
        configurarTecladoNumerico();
    }

    private void configurarBarraEstado() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.green_dark));
    }

    private void inicializarVistas() {
        textoTitulo = findViewById(R.id.texto_titulo);
        textoInstruccion = findViewById(R.id.texto_instruccion);

        punto1 = findViewById(R.id.punto_1);
        punto2 = findViewById(R.id.punto_2);
        punto3 = findViewById(R.id.punto_3);
        punto4 = findViewById(R.id.punto_4);

        indicadorPaso1 = findViewById(R.id.indicador_paso1);
        indicadorPaso2 = findViewById(R.id.indicador_paso2);

        botonBorrar = findViewById(R.id.boton_borrar);
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

        // Botón borrar (siempre visible)
        botonBorrar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                borrarDigito();
            }
        });
    }

    private void agregarDigito(String digito) {
        if (pinActual.length() < PIN_LONGITUD) {
            pinActual.append(digito);
            actualizarPuntos();

            // Si ya tiene 4 dígitos, procesar automáticamente
            if (pinActual.length() == PIN_LONGITUD) {
                // Pequeña demora para que el usuario vea el último punto
                punto1.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        procesarPin();
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

    private void procesarPin() {
        if (esPrimerPaso) {
            // Guardar el primer PIN y pedir confirmación
            primerPin = pinActual.toString();
            pinActual = new StringBuilder();
            esPrimerPaso = false;

            textoTitulo.setText("Confirmar PIN");
            textoInstruccion.setText("Ingrese nuevamente su PIN");
            indicadorPaso1.setBackgroundResource(R.drawable.indicador_activo);
            indicadorPaso2.setBackgroundResource(R.drawable.indicador_activo);

            actualizarPuntos();

        } else {
            // Verificar que los PINs coincidan
            String segundoPin = pinActual.toString();

            if (primerPin.equals(segundoPin)) {
                // ¡PINs coinciden! Guardar y continuar
                guardarPin(primerPin);
                irALogin();
            } else {
                // No coinciden, mostrar error con Toast y reiniciar
                Toast.makeText(this, "Los PINs no coinciden. Intente nuevamente", Toast.LENGTH_LONG).show();
                reiniciarProceso();
            }
        }
    }

    private void guardarPin(String pin) {

        // Hashear el PIN con SHA-256
        String pinHasheado = SecurityUtils.hashSHA256(pin);

        // Guardar PIN localmente
        getSharedPreferences("AsisteDragonPrefs", MODE_PRIVATE)
                .edit()
                .putString("pin_hash", pinHasheado)  // ← Guardaba en texto plano
                .putBoolean("pin_configurado", true)
                .apply();

        // Guardar PIN en Firebase también
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("pinConfigurado", true);
            updates.put("pinHash", pinHasheado);  // ← Guardar hash en Firebase

            FirebaseFirestore.getInstance()
                    .collection("usuarios")
                    .document(currentUser.getUid())
                    .update(updates)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Toast.makeText(ConfigurarPinActivity.this,
                                    "PIN configurado exitosamente",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Toast.makeText(ConfigurarPinActivity.this,
                                    "Error al guardar PIN: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void reiniciarProceso() {
        pinActual = new StringBuilder();
        primerPin = "";
        esPrimerPaso = true;

        textoTitulo.setText("Configurar PIN");
        textoInstruccion.setText("Cree un PIN de 4 dígitos");
        indicadorPaso1.setBackgroundResource(R.drawable.indicador_activo);
        indicadorPaso2.setBackgroundResource(R.drawable.indicador_inactivo);

        actualizarPuntos();
    }

    private void irALogin() {
        Toast.makeText(this, "Ahora puede iniciar sesión", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(ConfigurarPinActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Evitar que el usuario pueda volver atrás
        Toast.makeText(this, "Por favor, configure su PIN para continuar", Toast.LENGTH_SHORT).show();
    }
}
 