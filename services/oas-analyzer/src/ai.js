import OpenAI from 'openai';

const SYSTEM_PROMPT = `You are an expert API design and security reviewer. Analyze the provided OpenAPI Specification and return ONLY a JSON object (no markdown fences, no explanation) with this exact structure:
{
  "score": <integer 0-100>,
  "summary": "<2-3 sentence overview of API quality>",
  "risks": [
    {
      "severity": "<critical|high|medium|low>",
      "category": "<security|design|performance|documentation>",
      "description": "<specific issue>",
      "recommendation": "<concrete fix>"
    }
  ],
  "suggestions": [
    {
      "category": "<naming|versioning|documentation|security|structure>",
      "description": "<what to improve>",
      "impact": "<why this matters>"
    }
  ]
}

Scoring guide: 90-100 production-ready; 70-89 good with minor issues; 50-69 acceptable but needs work; 30-49 significant problems; 0-29 major rework needed.
Focus on: authentication, authorization, input validation, error responses, versioning, descriptions, rate limiting hints, naming conventions, pagination patterns. Return at most 5 risks and 5 suggestions.`;

export async function analyzeWithAI(oasContent) {
  if (!process.env.OPENAI_API_KEY) {
    return {
      score: null,
      summary: 'AI analysis unavailable: OPENAI_API_KEY not configured.',
      risks: [],
      suggestions: [],
    };
  }

  const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });

  const text = typeof oasContent === 'string' ? oasContent : JSON.stringify(oasContent, null, 2);
  const truncated = text.length > 48000 ? text.slice(0, 48000) + '\n\n... (document truncated for analysis)' : text;

  const response = await client.chat.completions.create({
    model: 'gpt-4o',
    max_tokens: 2048,
    messages: [
      { role: 'system', content: SYSTEM_PROMPT },
      { role: 'user', content: `Analyze this OpenAPI Specification:\n\n${truncated}` },
    ],
  });

  const raw = response.choices[0]?.message?.content ?? '{}';
  try {
    return JSON.parse(raw);
  } catch {
    const match = raw.match(/\{[\s\S]*\}/);
    if (match) {
      try { return JSON.parse(match[0]); } catch { /* fall through */ }
    }
    return { score: null, summary: raw.slice(0, 500), risks: [], suggestions: [] };
  }
}
