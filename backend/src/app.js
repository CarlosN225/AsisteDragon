const express = require('express');
const { createClient } = require('redis');

const app = express();
const port = process.env.PORT || 3000;
const redisUrl = process.env.REDIS_URL || 'redis://localhost:6379';

const redisClient = createClient({ url: redisUrl });

redisClient.on('error', (err) => {
  if (process.env.NODE_ENV !== 'test') {
    console.log('Aviso Redis:', err.message);
  }
});

if (process.env.NODE_ENV !== 'test') {
  (async () => {
    try {
      await redisClient.connect();
      console.log('Microservicio de Asistencias conectado a Redis');
    } catch (err) {
      console.log('Aviso: Redis no disponible aún');
    }
  })();
}

app.use(express.json());

// Healthcheck (usado por Jest y monitoreo)
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'OK', microservicio: 'asistencias', uptime: process.uptime() });
});

// Contador de visitas
app.get('/api/visitas', async (req, res) => {
  try {
    const total = await redisClient.incr('contador_visitas');
    res.status(200).json({
      mensaje: "Consulta realizada correctamente",
      visitas: total
    });
  } catch (error) {
    res.status(500).json({ error: "Error al actualizar visitas en Redis" });
  }
});

// Registrar asistencia por QR
app.post('/api/asistencias/registrar', async (req, res) => {
  try {
    const asistencia = req.body;
    if (!asistencia || !asistencia.profesorUid || !asistencia.fecha) {
      return res.status(400).json({ error: "Faltan datos obligatorios (profesorUid, fecha)" });
    }

    asistencia.id = 'asist_' + Date.now();
    asistencia.creadoEn = Date.now();

    await redisClient.rPush(`asistencias_db:${asistencia.profesorUid}`, JSON.stringify(asistencia));
    await redisClient.set(`asistencia_hoy:${asistencia.profesorUid}:${asistencia.fecha}`, JSON.stringify(asistencia));

    res.status(201).json({
      mensaje: "Asistencia registrada correctamente en Redis",
      asistencia
    });
  } catch (error) {
    res.status(500).json({ error: "Error al guardar asistencia en Redis" });
  }
});

// Consultar asistencia del día
app.get('/api/asistencias/hoy', async (req, res) => {
  try {
    const { uid, fecha } = req.query;
    if (!uid || !fecha) return res.status(400).json({ error: "Se requiere uid y fecha" });

    const data = await redisClient.get(`asistencia_hoy:${uid}:${fecha}`);
    if (data) {
      res.status(200).json({ registrada: true, asistencia: JSON.parse(data) });
    } else {
      res.status(200).json({ registrada: false });
    }
  } catch (error) {
    res.status(500).json({ error: "Error consultando asistencia de hoy" });
  }
});

// Historial y estadísticas de asistencias
app.get('/api/asistencias/historial', async (req, res) => {
  try {
    const { uid } = req.query;
    if (!uid) return res.status(400).json({ error: "Se requiere uid" });

    const listaRaw = await redisClient.lRange(`asistencias_db:${uid}`, 0, -1);
    const asistencias = listaRaw.map(item => JSON.parse(item));

    let puntuales = 0;
    let retardos = 0;
    asistencias.forEach(a => {
      if (a.estado === 'puntual') puntuales++;
      else retardos++;
    });

    res.status(200).json({
      totalDias: asistencias.length,
      puntuales,
      retardos,
      asistencias
    });
  } catch (error) {
    res.status(500).json({ error: "Error consultando historial" });
  }
});

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    console.log(`Microservicio de Asistencias escuchando en puerto ${port}`);
  });
}

module.exports = app;
