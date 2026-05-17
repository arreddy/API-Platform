import { describe, it, expect, vi, beforeEach } from 'vitest';
import request from 'supertest';

vi.mock('./spectral.js', () => ({
  analyzeWithSpectral: vi.fn(),
}));
vi.mock('./ai.js', () => ({
  analyzeWithAI: vi.fn(),
}));

const SPECTRAL_RESULT = {
  violations: [],
  errorCount: 0,
  warningCount: 0,
  infoCount: 0,
  hintCount: 0,
};

const AI_RESULT = {
  score: 80,
  summary: 'Good API.',
  risks: [],
  suggestions: [],
};

const VALID_OAS_JSON = JSON.stringify({
  openapi: '3.0.0',
  info: { title: 'Test', version: '1.0.0' },
  paths: {},
});

const VALID_OAS_YAML = `openapi: "3.0.0"\ninfo:\n  title: Test\n  version: "1.0.0"\npaths: {}`;

async function getApp() {
  const { default: app } = await import('./app.js');
  return app;
}

describe('GET /health', () => {
  it('returns 200 with status ok', async () => {
    const app = await getApp();
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'ok' });
  });
});

describe('POST /analyze', () => {
  let spectralMock;
  let aiMock;

  beforeEach(async () => {
    vi.resetModules();
    const spectral = await import('./spectral.js');
    const ai = await import('./ai.js');
    spectralMock = spectral.analyzeWithSpectral;
    aiMock = ai.analyzeWithAI;
    spectralMock.mockResolvedValue(SPECTRAL_RESULT);
    aiMock.mockResolvedValue(AI_RESULT);
  });

  it('returns 400 when oasContent is missing', async () => {
    const app = await getApp();
    const res = await request(app).post('/analyze').send({});
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('oasContent is required');
  });

  it('returns 400 for unparseable content', async () => {
    const app = await getApp();
    const res = await request(app)
      .post('/analyze')
      .send({ oasContent: '{ this is: [not valid json or yaml' });
    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/Cannot parse OAS content/);
  });

  it('returns 200 with spectral and ai for valid JSON OAS string', async () => {
    const app = await getApp();
    const res = await request(app)
      .post('/analyze')
      .send({ oasContent: VALID_OAS_JSON });
    expect(res.status).toBe(200);
    expect(res.body).toHaveProperty('spectral');
    expect(res.body).toHaveProperty('ai');
    expect(res.body.spectral).toEqual(SPECTRAL_RESULT);
    expect(res.body.ai).toEqual(AI_RESULT);
  });

  it('returns 200 for valid YAML OAS string', async () => {
    const app = await getApp();
    const res = await request(app)
      .post('/analyze')
      .send({ oasContent: VALID_OAS_YAML });
    expect(res.status).toBe(200);
    expect(res.body).toHaveProperty('spectral');
    expect(res.body).toHaveProperty('ai');
  });

  it('accepts an OAS object directly (not a string)', async () => {
    const app = await getApp();
    const res = await request(app)
      .post('/analyze')
      .send({ oasContent: { openapi: '3.0.0', info: { title: 'T', version: '1' }, paths: {} } });
    expect(res.status).toBe(200);
  });

  it('returns 500 when analysis throws', async () => {
    spectralMock.mockRejectedValue(new Error('Spectral exploded'));
    const app = await getApp();
    const res = await request(app)
      .post('/analyze')
      .send({ oasContent: VALID_OAS_JSON });
    expect(res.status).toBe(500);
    expect(res.body.error).toBe('Spectral exploded');
  });

  it('passes the parsed document to analyzeWithSpectral and raw content to analyzeWithAI', async () => {
    const app = await getApp();
    await request(app).post('/analyze').send({ oasContent: VALID_OAS_JSON });
    expect(spectralMock).toHaveBeenCalledWith(expect.objectContaining({ openapi: '3.0.0' }));
    expect(aiMock).toHaveBeenCalledWith(VALID_OAS_JSON);
  });
});
