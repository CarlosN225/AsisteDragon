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

// Solo conectar a Redis si no estamos en pruebas unitarias
if (process.env.NODE_ENV !== 'test') {
  (async () => {
    try {
      await redisClient.connect();
      console.log('Conectado a Redis exitosamente');
    } catch (err) {
      console.log('Aviso: Redis no disponible aún');
    }
  })();
}

app.use(express.json());

// Endpoint requerido: GET /health
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'OK', uptime: process.uptime() });
});

// Endpoint requerido: GET /api/visitas
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

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    console.log(`Servidor API escuchando en puerto ${port}`);
  });
}

module.exports = app;
