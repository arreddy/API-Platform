import express from 'express';
import yaml from 'js-yaml';
import { analyzeWithSpectral } from './spectral.js';
import { analyzeWithAI } from './ai.js';

const app = express();
app.use(express.json({ limit: '10mb' }));

app.get('/health', (_req, res) => res.json({ status: 'ok' }));

app.post('/analyze', async (req, res) => {
  const { oasContent } = req.body ?? {};
  if (!oasContent) {
    return res.status(400).json({ error: 'oasContent is required' });
  }

  let oasDocument;
  try {
    const raw = typeof oasContent === 'string' ? oasContent.trim() : JSON.stringify(oasContent);
    oasDocument = raw.startsWith('{') || raw.startsWith('[')
      ? JSON.parse(raw)
      : yaml.load(raw);
  } catch (e) {
    return res.status(400).json({ error: 'Cannot parse OAS content: ' + e.message });
  }

  try {
    const [spectral, ai] = await Promise.all([
      analyzeWithSpectral(oasDocument),
      analyzeWithAI(oasContent),
    ]);
    res.json({ spectral, ai });
  } catch (err) {
    console.error('Analysis error:', err);
    res.status(500).json({ error: err.message });
  }
});

const PORT = process.env.PORT || 3004;
app.listen(PORT, () => console.log(`oas-analyzer listening on :${PORT}`));
