package com.utfv.asistedragon;

public class Asistencia {
    private String id;
    private String profesorUid;
    private String profesorNombre;
    private String profesorEmail;
    private String profesorNumeroEmpleado;
    private String profesorTipo;
    private String profesorTurno;

    private String fecha;
    private String horaRegistro;
    private long timestamp;
    private String diaSemana;

    private String estado;  // puntual, retardo, falta
    private String primeraHoraEsperada;
    private int minutosDiferencia;

    private boolean esPrimeraClaseDia;
    private boolean puedeJustificar;
    private String justificacion;

    private String qrCodigo;
    private String appVersion;
    private long creadoEn;

    // Constructor vacío (requerido por Firebase)
    public Asistencia() {
    }

    // Constructor completo
    public Asistencia(String profesorUid, String profesorNombre, String profesorEmail,
                      String profesorNumeroEmpleado, String profesorTipo, String profesorTurno,
                      String fecha, String horaRegistro, long timestamp, String diaSemana,
                      String estado, String primeraHoraEsperada, int minutosDiferencia,
                      String qrCodigo) {
        this.profesorUid = profesorUid;
        this.profesorNombre = profesorNombre;
        this.profesorEmail = profesorEmail;
        this.profesorNumeroEmpleado = profesorNumeroEmpleado;
        this.profesorTipo = profesorTipo;
        this.profesorTurno = profesorTurno;
        this.fecha = fecha;
        this.horaRegistro = horaRegistro;
        this.timestamp = timestamp;
        this.diaSemana = diaSemana;
        this.estado = estado;
        this.primeraHoraEsperada = primeraHoraEsperada;
        this.minutosDiferencia = minutosDiferencia;
        this.esPrimeraClaseDia = true;
        this.puedeJustificar = true;
        this.justificacion = "";
        this.qrCodigo = qrCodigo;
        this.appVersion = "1.0.0";
        this.creadoEn = System.currentTimeMillis();
    }

    // Getters y Setters (genéralos todos con Alt+Insert en Android Studio)

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProfesorUid() { return profesorUid; }
    public void setProfesorUid(String profesorUid) { this.profesorUid = profesorUid; }

    public String getProfesorNombre() { return profesorNombre; }
    public void setProfesorNombre(String profesorNombre) { this.profesorNombre = profesorNombre; }

    public String getProfesorEmail() { return profesorEmail; }
    public void setProfesorEmail(String profesorEmail) { this.profesorEmail = profesorEmail; }

    public String getProfesorNumeroEmpleado() { return profesorNumeroEmpleado; }
    public void setProfesorNumeroEmpleado(String profesorNumeroEmpleado) { this.profesorNumeroEmpleado = profesorNumeroEmpleado; }

    public String getProfesorTipo() { return profesorTipo; }
    public void setProfesorTipo(String profesorTipo) { this.profesorTipo = profesorTipo; }

    public String getProfesorTurno() { return profesorTurno; }
    public void setProfesorTurno(String profesorTurno) { this.profesorTurno = profesorTurno; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public String getHoraRegistro() { return horaRegistro; }
    public void setHoraRegistro(String horaRegistro) { this.horaRegistro = horaRegistro; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getDiaSemana() { return diaSemana; }
    public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getPrimeraHoraEsperada() { return primeraHoraEsperada; }
    public void setPrimeraHoraEsperada(String primeraHoraEsperada) { this.primeraHoraEsperada = primeraHoraEsperada; }

    public int getMinutosDiferencia() { return minutosDiferencia; }
    public void setMinutosDiferencia(int minutosDiferencia) { this.minutosDiferencia = minutosDiferencia; }

    public boolean isEsPrimeraClaseDia() { return esPrimeraClaseDia; }
    public void setEsPrimeraClaseDia(boolean esPrimeraClaseDia) { this.esPrimeraClaseDia = esPrimeraClaseDia; }

    public boolean isPuedeJustificar() { return puedeJustificar; }
    public void setPuedeJustificar(boolean puedeJustificar) { this.puedeJustificar = puedeJustificar; }

    public String getJustificacion() { return justificacion; }
    public void setJustificacion(String justificacion) { this.justificacion = justificacion; }

    public String getQrCodigo() { return qrCodigo; }
    public void setQrCodigo(String qrCodigo) { this.qrCodigo = qrCodigo; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

    public long getCreadoEn() { return creadoEn; }
    public void setCreadoEn(long creadoEn) { this.creadoEn = creadoEn; }
}