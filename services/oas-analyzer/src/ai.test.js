import { describe, it, expect, vi, afterEach } from 'vitest';

const mockCreate = vi.fn();

vi.mock('openai', () => ({
  default: class OpenAI {
    constructor() {
      this.chat = { completions: { create: mockCreate } };
    }
  },
}));

const FALLBACK = {
  score: null,
  summary: 'AI analysis unavailable: OPENAI_API_KEY not configured.',
  risks: [],
  suggestions: [],
};

const VALID_RESULT = {
  score: 75,
  summary: 'Decent API.',
  risks: [{ severity: 'high', category: 'security', description: 'No auth', recommendation: 'Add auth' }],
  suggestions: [{ category: 'versioning', description: 'Add version', impact: 'Stability' }],
};

// Import once — vi.mock hoists so the mock is already in place
const { analyzeWithAI } = await import('./ai.js');

afterEach(() => {
  delete process.env.OPENAI_API_KEY;
  mockCreate.mockReset();
});

describe('analyzeWithAI', () => {
  it('returns fallback when OPENAI_API_KEY is not set', async () => {
    delete process.env.OPENAI_API_KEY;
    const result = await analyzeWithAI('openapi: 3.0.0');
    expect(result).toEqual(FALLBACK);
    expect(mockCreate).not.toHaveBeenCalled();
  });

  it('calls OpenAI with correct model and messages, parses JSON response', async () => {
    process.env.OPENAI_API_KEY = 'test-key';
    mockCreate.mockResolvedValue({
      choices: [{ message: { content: JSON.stringify(VALID_RESULT) } }],
    });

    const result = await analyzeWithAI('openapi: 3.0.0');

    expect(mockCreate).toHaveBeenCalledOnce();
    const call = mockCreate.mock.calls[0][0];
    expect(call.model).toBe('gpt-4o');
    expect(call.messages[0].role).toBe('system');
    expect(call.messages[1].role).toBe('user');
    expect(result).toEqual(VALID_RESULT);
  });

  it('extracts JSON when response contains surrounding text', async () => {
    process.env.OPENAI_API_KEY = 'test-key';
    mockCreate.mockResolvedValue({
      choices: [{ message: { content: 'Here is the result: ' + JSON.stringify(VALID_RESULT) } }],
    });

    const result = await analyzeWithAI('openapi: 3.0.0');
    expect(result.score).toBe(75);
  });

  it('returns degraded object when response cannot be parsed as JSON', async () => {
    process.env.OPENAI_API_KEY = 'test-key';
    mockCreate.mockResolvedValue({
      choices: [{ message: { content: 'Sorry, cannot analyze this.' } }],
    });

    const result = await analyzeWithAI('openapi: 3.0.0');
    expect(result.score).toBeNull();
    expect(result.summary).toBe('Sorry, cannot analyze this.');
    expect(result.risks).toEqual([]);
    expect(result.suggestions).toEqual([]);
  });

  it('truncates content longer than 48000 characters', async () => {
    process.env.OPENAI_API_KEY = 'test-key';
    mockCreate.mockResolvedValue({
      choices: [{ message: { content: JSON.stringify(VALID_RESULT) } }],
    });

    await analyzeWithAI('x'.repeat(50000));

    const userMessage = mockCreate.mock.calls[0][0].messages[1].content;
    expect(userMessage).toContain('document truncated for analysis');
    expect(userMessage.length).toBeLessThan(50000);
  });

  it('accepts an object as oasContent and serialises it to JSON', async () => {
    process.env.OPENAI_API_KEY = 'test-key';
    mockCreate.mockResolvedValue({
      choices: [{ message: { content: JSON.stringify(VALID_RESULT) } }],
    });

    await analyzeWithAI({ openapi: '3.0.0', info: { title: 'Test', version: '1.0' } });

    const userMessage = mockCreate.mock.calls[0][0].messages[1].content;
    expect(userMessage).toContain('"openapi"');
  });
});
