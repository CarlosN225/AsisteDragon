package com.utfv.asistedragon;

public class Usuario {
    private String uid;
    private String nombreCompleto;
    private String correo;
    private String numeroEmpleado;
    private String tipoProfesor;
    private String turno;
    private String telefono;
    private String rol;
    private long   fechaRegistro;
    private boolean pinConfigurado;
    private String pinHash;
    private String horarioPrimeraClase;
    private String fotoUrl; // ← NUEVO: URL de foto en Firebase Storage

    // Constructor vacío requerido por Firebase
    public Usuario() {}

    // Constructor completo
    public Usuario(String uid, String nombreCompleto, String correo, String numeroEmpleado,
                   String tipoProfesor, String turno, String telefono) {
        this.uid              = uid;
        this.nombreCompleto   = nombreCompleto;
        this.correo           = correo;
        this.numeroEmpleado   = numeroEmpleado;
        this.tipoProfesor     = tipoProfesor;
        this.turno            = turno;
        this.telefono         = telefono;
        this.rol              = "docente";
        this.fechaRegistro    = System.currentTimeMillis();
        this.pinConfigurado   = false;
        this.pinHash          = "";
        this.fotoUrl          = ""; // vacío al registrarse — se sube desde Perfil
    }

    // Getters y Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String n) { this.nombreCompleto = n; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getNumeroEmpleado() { return numeroEmpleado; }
    public void setNumeroEmpleado(String n) { this.numeroEmpleado = n; }

    public String getTipoProfesor() { return tipoProfesor; }
    public void setTipoProfesor(String t) { this.tipoProfesor = t; }

    public String getTurno() { return turno; }
    public void setTurno(String turno) { this.turno = turno; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public long getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(long fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public boolean isPinConfigurado() { return pinConfigurado; }
    public void setPinConfigurado(boolean pinConfigurado) { this.pinConfigurado = pinConfigurado; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    public String getHorarioPrimeraClase() { return horarioPrimeraClase; }
    public void setHorarioPrimeraClase(String h) { this.horarioPrimeraClase = h; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }
}