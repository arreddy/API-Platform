import spectralCore from '@stoplight/spectral-core';
import spectralRulesets from '@stoplight/spectral-rulesets';

const { Spectral } = spectralCore;
const { oas } = spectralRulesets;

const SEVERITY_NAMES = ['error', 'warning', 'info', 'hint'];

// Singleton — ruleset loading is expensive
let spectralInstance = null;

function getInstance() {
  if (!spectralInstance) {
    spectralInstance = new Spectral();
    spectralInstance.setRuleset(oas);
  }
  return spectralInstance;
}

export async function analyzeWithSpectral(oasDocument) {
  const spectral = getInstance();
  const issues = await spectral.run(oasDocument);

  const violations = issues.map((issue) => ({
    code: String(issue.code),
    message: issue.message,
    severity: SEVERITY_NAMES[issue.severity] ?? 'hint',
    path: issue.path.join('.'),
    start: issue.range?.start,
  }));

  return {
    violations,
    errorCount: violations.filter((v) => v.severity === 'error').length,
    warningCount: violations.filter((v) => v.severity === 'warning').length,
    infoCount: violations.filter((v) => v.severity === 'info').length,
    hintCount: violations.filter((v) => v.severity === 'hint').length,
  };
}
