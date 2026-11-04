const request = require('supertest');
const app = require('../src/app');

describe('Pruebas unitarias de la API', () => {
  it('GET /health debe responder con status 200 y status OK', async () => {
    const res = await request(app).get('/health');
    expect(res.statusCode).toEqual(200);
    expect(res.body.status).toEqual('OK');
  });
});
