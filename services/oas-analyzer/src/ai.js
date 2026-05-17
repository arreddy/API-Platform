import Anthropic from '@anthropic-ai/sdk';

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
  if (!process.env.ANTHROPIC_API_KEY) {
    return {
      score: null,
      summary: 'AI analysis unavailable: ANTHROPIC_API_KEY not configured.',
      risks: [],
      suggestions: [],
    };
  }

  const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });

  const text = typeof oasContent === 'string' ? oasContent : JSON.stringify(oasContent, null, 2);
  // Stay well within context limits
  const truncated = text.length > 48000 ? text.slice(0, 48000) + '\n\n... (document truncated for analysis)' : text;

  const message = await client.messages.create({
    model: 'claude-sonnet-4-6',
    max_tokens: 2048,
    system: SYSTEM_PROMPT,
    messages: [{ role: 'user', content: `Analyze this OpenAPI Specification:\n\n${truncated}` }],
  });

  const raw = message.content[0]?.text ?? '{}';
  try {
    return JSON.parse(raw);
  } catch {
    // Attempt to extract the first JSON object from free-form text
    const match = raw.match(/\{[\s\S]*\}/);
    if (match) {
      try { return JSON.parse(match[0]); } catch { /* fall through */ }
    }
    return { score: null, summary: raw.slice(0, 500), risks: [], suggestions: [] };
  }
}
