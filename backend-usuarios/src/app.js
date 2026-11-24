const express = require('express');
const crypto = require('crypto');
const cors = require('cors');
const { createClient } = require('redis');

const app = express();
const port = process.env.PORT || 3001;
const redisUrl = process.env.REDIS_URL || 'redis://localhost:6379';

const redisClient = createClient({ url: redisUrl });
redisClient.on('error', (err) => console.log('Aviso Redis Usuarios:', err.message));

app.use(cors());
app.use(express.json());

function hashSHA256(texto) {
  return crypto.createHash('sha256').update(String(texto)).digest('hex');
}

// Conectar a Redis y asegurar usuario base de prueba
(async () => {
  try {
    await redisClient.connect();
    console.log('Microservicio de Usuarios conectado a Redis');

    const existeDefault = await redisClient.hGet('usuarios_db', 'docente@utfv.edu.mx');
    if (!existeDefault) {
      const usuarioBase = {
        id: 'usr_default_01',
        nombreCompleto: 'Docente Prueba UTFV',
        correo: 'docente@utfv.edu.mx',
        numeroEmpleado: '10001',
        tipoProfesor: 'Tiempo Completo',
        turno: 'Matutino',
        telefono: '5512345678',
        passwordHash: hashSHA256('123456'),
        pinHash: hashSHA256('1234'),
        rol: 'docente',
        activo: true,
        fechaRegistro: new Date().toISOString()
      };
      await redisClient.hSet('usuarios_db', 'docente@utfv.edu.mx', JSON.stringify(usuarioBase));
      console.log('Usuario por defecto guardado en Redis: docente@utfv.edu.mx');
    }
  } catch (err) {
    console.log('Error conectando a Redis:', err.message);
  }
})();

// 1. Estado del microservicio
app.get('/health', async (req, res) => {
  try {
    const total = await redisClient.hLen('usuarios_db');
    res.status(200).json({
      microservicio: "usuarios",
      status: "OK",
      almacenamiento: "Redis Persistente",
      usuariosRegistrados: total
    });
  } catch (error) {
    res.status(500).json({ error: "Error consultando estado en Redis" });
  }
});

// 2. Registro de usuarios (Persistente en Redis)
app.post('/api/usuarios/registro', async (req, res) => {
  try {
    const { nombreCompleto, correo, numeroEmpleado, tipoProfesor, turno, telefono, contrasena, pin } = req.body;

    if (!correo || !correo.endsWith('@utfv.edu.mx')) {
      return res.status(400).json({ error: "Debe usar su correo institucional (@utfv.edu.mx)" });
    }

    if (!contrasena || contrasena.length < 6) {
      return res.status(400).json({ error: "La contraseña debe tener al menos 6 caracteres" });
    }

    const correoKey = correo.toLowerCase().trim();
    const yaExiste = await redisClient.hGet('usuarios_db', correoKey);
    if (yaExiste) {
      return res.status(409).json({ error: "El usuario ya se encuentra registrado" });
    }

    const nuevoUsuario = {
      id: "usr_" + Date.now(),
      nombreCompleto,
      correo: correoKey,
      numeroEmpleado: numeroEmpleado || "N/A",
      tipoProfesor: tipoProfesor || "Asignatura",
      turno: turno || "Matutino",
      telefono: telefono || "",
      passwordHash: hashSHA256(contrasena),
      pinHash: pin ? hashSHA256(pin) : hashSHA256("1234"),
      rol: "docente",
      activo: true,
      fechaRegistro: new Date().toISOString()
    };

    await redisClient.hSet('usuarios_db', correoKey, JSON.stringify(nuevoUsuario));

    res.status(201).json({
      mensaje: "Usuario docente registrado exitosamente en Redis",
      usuario: {
        id: nuevoUsuario.id,
        nombreCompleto: nuevoUsuario.nombreCompleto,
        correo: nuevoUsuario.correo,
        rol: nuevoUsuario.rol
      }
    });
  } catch (error) {
    res.status(500).json({ error: "Error interno al guardar usuario" });
  }
});

// 3. Inicio de sesión
app.post('/api/usuarios/login', async (req, res) => {
  try {
    const { correo, contrasena } = req.body;
    const correoKey = (correo || '').toLowerCase().trim();

    const userJson = await redisClient.hGet('usuarios_db', correoKey);
    if (!userJson) {
      return res.status(404).json({ error: "No existe cuenta asociada a este correo" });
    }

    const usuario = JSON.parse(userJson);
    if (usuario.passwordHash !== hashSHA256(contrasena)) {
      return res.status(401).json({ error: "Contraseña incorrecta" });
    }

    res.status(200).json({
      mensaje: "Autenticación exitosa",
      usuario: {
        id: usuario.id,
        nombreCompleto: usuario.nombreCompleto,
        correo: usuario.correo,
        tipoProfesor: usuario.tipoProfesor,
        turno: usuario.turno,
        rol: usuario.rol
      }
    });
  } catch (error) {
    res.status(500).json({ error: "Error interno en inicio de sesión" });
  }
});

// 4. Verificación de PIN
app.post('/api/usuarios/verificar-pin', async (req, res) => {
  try {
    const { correo, pin } = req.body;
    const correoKey = (correo || '').toLowerCase().trim();

    const userJson = await redisClient.hGet('usuarios_db', correoKey);
    if (!userJson) {
      return res.status(404).json({ error: "Usuario no encontrado" });
    }

    const usuario = JSON.parse(userJson);
    if (usuario.pinHash === hashSHA256(pin)) {
      res.status(200).json({ mensaje: "PIN verificado correctamente", acceso: true });
    } else {
      res.status(401).json({ error: "PIN incorrecto", acceso: false });
    }
  } catch (error) {
    res.status(500).json({ error: "Error al verificar PIN" });
  }
});

// 5. Gestión de perfiles
app.get('/api/usuarios/perfil', async (req, res) => {
  try {
    const correoKey = (req.query.correo || '').toLowerCase().trim();
    const userJson = await redisClient.hGet('usuarios_db', correoKey);
    if (!userJson) {
      return res.status(404).json({ error: "Perfil no encontrado" });
    }

    const usuario = JSON.parse(userJson);
    const { passwordHash, pinHash, ...perfilSeguro } = usuario;
    res.status(200).json(perfilSeguro);
  } catch (error) {
    res.status(500).json({ error: "Error consultando perfil" });
  }
});

// 6. Gestión de roles
app.get('/api/usuarios/roles', (req, res) => {
  res.status(200).json({
    rolesDisponibles: ["docente", "administrador", "coordinador"],
    descripcion: "Control de niveles de acceso del sistema AsisteDragon"
  });
});

app.listen(port, () => {
  console.log(`Microservicio de Usuarios activo en puerto ${port}`);
});
