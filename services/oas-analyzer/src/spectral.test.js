import { describe, it, expect } from 'vitest';
import { analyzeWithSpectral } from './spectral.js';

const MINIMAL_VALID_OAS = {
  openapi: '3.0.0',
  info: { title: 'Test API', version: '1.0.0' },
  paths: {
    '/pets': {
      get: {
        summary: 'List pets',
        operationId: 'listPets',
        responses: { '200': { description: 'A list of pets' } },
      },
    },
  },
};

describe('analyzeWithSpectral', () => {
  it('returns the expected result shape', async () => {
    const result = await analyzeWithSpectral(MINIMAL_VALID_OAS);

    expect(result).toHaveProperty('violations');
    expect(result).toHaveProperty('errorCount');
    expect(result).toHaveProperty('warningCount');
    expect(result).toHaveProperty('infoCount');
    expect(result).toHaveProperty('hintCount');
    expect(Array.isArray(result.violations)).toBe(true);
  });

  it('violation objects have the required fields', async () => {
    const result = await analyzeWithSpectral(MINIMAL_VALID_OAS);
    for (const v of result.violations) {
      expect(v).toHaveProperty('code');
      expect(v).toHaveProperty('message');
      expect(v).toHaveProperty('severity');
      expect(v).toHaveProperty('path');
      expect(['error', 'warning', 'info', 'hint']).toContain(v.severity);
    }
  });

  it('counts match the violations array', async () => {
    const result = await analyzeWithSpectral(MINIMAL_VALID_OAS);
    const total =
      result.errorCount + result.warningCount + result.infoCount + result.hintCount;
    expect(total).toBe(result.violations.length);
  });

  it('reports errors for a document missing required info fields', async () => {
    const broken = { openapi: '3.0.0', paths: {} };
    const result = await analyzeWithSpectral(broken);
    expect(result.errorCount + result.warningCount).toBeGreaterThan(0);
  });

  it('returns zero counts for a fully described document', async () => {
    const clean = {
      openapi: '3.0.0',
      info: { title: 'Clean API', version: '1.0.0', description: 'A well-described API' },
      paths: {},
    };
    const result = await analyzeWithSpectral(clean);
    expect(result.errorCount).toBe(0);
  });
});
