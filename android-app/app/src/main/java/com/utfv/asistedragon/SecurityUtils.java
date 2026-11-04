package com.utfv.asistedragon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utilidades de seguridad para la aplicación.
 * Incluye funciones de hash y cifrado.
 *
 * @author Tu Nombre
 * Materia: Seguridad en el Desarrollo de Aplicaciones
 * Institución: UTFV
 */
public class SecurityUtils {

    /**
     * Genera un hash SHA-256 de un texto.
     *
     * @param input Texto a hashear (por ejemplo, un PIN)
     * @return String hexadecimal del hash SHA-256
     *
     * Ejemplo:
     * Input: "1234"
     * Output: "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
     */
    public static String hashSHA256(String input) {
        try {
            // Crear instancia del algoritmo SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Aplicar hash al input
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convertir bytes a hexadecimal
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            // Esto nunca debería pasar ya que SHA-256 es estándar
            throw new RuntimeException("Error: SHA-256 no disponible", e);
        }
    }

    /**
     * Convierte un array de bytes a su representación hexadecimal.
     *
     * @param bytes Array de bytes a convertir
     * @return String hexadecimal
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();

        for (byte b : bytes) {
            // Convertir cada byte a hexadecimal
            String hex = Integer.toHexString(0xff & b);

            // Agregar 0 al inicio si es necesario (para mantener 2 dígitos)
            if (hex.length() == 1) {
                hexString.append('0');
            }

            hexString.append(hex);
        }

        return hexString.toString();
    }

    /**
     * Verifica si un PIN coincide con su hash.
     *
     * @param pin PIN ingresado por el usuario
     * @param hash Hash almacenado en la base de datos
     * @return true si coinciden, false si no
     */
    public static boolean verificarPIN(String pin, String hash) {
        String pinHasheado = hashSHA256(pin);
        return pinHasheado.equals(hash);
    }

    /**
     * Genera un hash con salt (más seguro).
     * El salt es un valor aleatorio que se agrega al PIN antes de hashear.
     * Esto previene ataques de tablas rainbow.
     *
     * @param pin PIN a hashear
     * @param salt Salt único para este usuario
     * @return Hash SHA-256 del PIN + salt
     */
    public static String hashConSalt(String pin, String salt) {
        return hashSHA256(pin + salt);
    }
}