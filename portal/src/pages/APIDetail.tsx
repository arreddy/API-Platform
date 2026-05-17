import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import SwaggerUI from 'swagger-ui-react';
import 'swagger-ui-react/swagger-ui.css';
import toast from 'react-hot-toast';
import { apisApi } from '../api/client';

interface API {
  id: string;
  title: string;
  name: string;
  version: string;
  description: string;
  oasVersion: string;
  oasDocument: Record<string, unknown>;
  basePath: string;
  servers: Array<{ url: string; description?: string }>;
  endpoints: Array<{ path: string; method: string; summary?: string; tags?: string[] }>;
  tags: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface Violation {
  code: string;
  message: string;
  severity: 'error' | 'warning' | 'info' | 'hint';
  path: string;
}

interface Risk {
  severity: 'critical' | 'high' | 'medium' | 'low';
  category: string;
  description: string;
  recommendation: string;
}

interface Suggestion {
  category: string;
  description: string;
  impact: string;
}

interface Insights {
  apiId: string;
  spectral: {
    violations: Violation[];
    errorCount: number;
    warningCount: number;
    infoCount: number;
    hintCount: number;
  };
  ai: {
    score: number | null;
    summary: string;
    risks: Risk[];
    suggestions: Suggestion[];
  };
  analyzedAt: string;
}

type Tab = 'overview' | 'docs' | 'endpoints' | 'insights';

const SEVERITY_VIOLATION_STYLE: Record<string, string> = {
  error: 'bg-red-100 text-red-700',
  warning: 'bg-yellow-100 text-yellow-700',
  info: 'bg-blue-100 text-blue-700',
  hint: 'bg-gray-100 text-gray-600',
};

const SEVERITY_RISK_STYLE: Record<string, string> = {
  critical: 'bg-red-600 text-white',
  high: 'bg-orange-500 text-white',
  medium: 'bg-yellow-500 text-white',
  low: 'bg-green-500 text-white',
};

function ScoreGauge({ score }: { score: number | null }) {
  if (score === null) return <div className="text-gray-400 text-sm">Score unavailable</div>;
  const color =
    score >= 80 ? 'text-green-600' :
    score >= 60 ? 'text-yellow-600' :
    score >= 40 ? 'text-orange-500' : 'text-red-600';
  const ringColor =
    score >= 80 ? 'stroke-green-500' :
    score >= 60 ? 'stroke-yellow-500' :
    score >= 40 ? 'stroke-orange-500' : 'stroke-red-500';
  const circumference = 2 * Math.PI * 36;
  const offset = circumference - (score / 100) * circumference;

  return (
    <div className="flex flex-col items-center">
      <div className="relative w-24 h-24">
        <svg className="w-full h-full -rotate-90" viewBox="0 0 80 80">
          <circle cx="40" cy="40" r="36" fill="none" strokeWidth="7" className="stroke-gray-200" />
          <circle
            cx="40" cy="40" r="36" fill="none" strokeWidth="7"
            className={ringColor}
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            strokeLinecap="round"
          />
        </svg>
        <div className={`absolute inset-0 flex flex-col items-center justify-center ${color}`}>
          <span className="text-2xl font-bold leading-none">{score}</span>
          <span className="text-xs text-gray-400">/100</span>
        </div>
      </div>
      <div className={`text-xs font-semibold mt-1 ${color}`}>
        {score >= 80 ? 'Excellent' : score >= 60 ? 'Good' : score >= 40 ? 'Needs Work' : 'Poor'}
      </div>
    </div>
  );
}

function InsightsTab({ apiId }: { apiId: string }) {
  const [insights, setInsights] = useState<Insights | null>(null);
  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [severityFilter, setSeverityFilter] = useState<string>('all');

  useEffect(() => {
    apisApi.getInsights(apiId)
      .then(({ data }) => setInsights(data))
      .catch(() => setInsights(null))
      .finally(() => setLoading(false));
  }, [apiId]);

  const runAnalysis = async () => {
    setAnalyzing(true);
    try {
      const { data } = await apisApi.analyze(apiId);
      setInsights(data);
      toast.success('Analysis complete');
    } catch {
      toast.error('Analysis failed — is the OAS Analyzer service running?');
    } finally {
      setAnalyzing(false);
    }
  };

  if (loading) return <div className="text-gray-400 p-4">Loading insights…</div>;

  if (!insights) {
    return (
      <div className="card p-8 text-center">
        <div className="text-4xl mb-3">🔍</div>
        <p className="text-gray-600 mb-4">No analysis available for this API yet.</p>
        <button className="btn-primary" onClick={runAnalysis} disabled={analyzing}>
          {analyzing ? 'Analyzing…' : 'Run Analysis'}
        </button>
      </div>
    );
  }

  const { spectral, ai } = insights;
  const filteredViolations = severityFilter === 'all'
    ? spectral.violations
    : spectral.violations.filter((v) => v.severity === severityFilter);

  return (
    <div className="space-y-6">
      {/* Header row: score + spectral counts + re-analyze */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <div className="card p-5 flex flex-col items-center justify-center">
          <ScoreGauge score={ai.score} />
          <div className="text-xs text-gray-400 mt-2">AI Quality Score</div>
        </div>
        <div className="card p-5">
          <div className="text-xs font-semibold text-gray-400 uppercase mb-3">Spectral Results</div>
          <div className="space-y-1 text-sm">
            <div className="flex justify-between"><span className="text-red-600 font-medium">Errors</span><span className="font-bold">{spectral.errorCount}</span></div>
            <div className="flex justify-between"><span className="text-yellow-600 font-medium">Warnings</span><span className="font-bold">{spectral.warningCount}</span></div>
            <div className="flex justify-between"><span className="text-blue-600 font-medium">Info</span><span className="font-bold">{spectral.infoCount}</span></div>
            <div className="flex justify-between"><span className="text-gray-500 font-medium">Hints</span><span className="font-bold">{spectral.hintCount}</span></div>
          </div>
        </div>
        <div className="card p-5 col-span-2">
          <div className="text-xs font-semibold text-gray-400 uppercase mb-2">AI Summary</div>
          <p className="text-sm text-gray-700 leading-relaxed">{ai.summary || '—'}</p>
          <div className="mt-3 flex justify-between items-center">
            <span className="text-xs text-gray-400">
              Analyzed {new Date(insights.analyzedAt).toLocaleString()}
            </span>
            <button className="btn-secondary text-xs py-1 px-3" onClick={runAnalysis} disabled={analyzing}>
              {analyzing ? 'Re-analyzing…' : 'Re-analyze'}
            </button>
          </div>
        </div>
      </div>

      {/* AI Risks */}
      {ai.risks.length > 0 && (
        <div className="card overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
            <h3 className="text-sm font-semibold text-gray-700">AI-Identified Risks ({ai.risks.length})</h3>
          </div>
          <div className="divide-y divide-gray-100">
            {ai.risks.map((risk, i) => (
              <div key={i} className="p-4 flex gap-3">
                <span className={`text-xs font-semibold px-2 py-0.5 rounded-full h-fit whitespace-nowrap ${SEVERITY_RISK_STYLE[risk.severity] ?? 'bg-gray-200 text-gray-700'}`}>
                  {risk.severity}
                </span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-xs badge-gray">{risk.category}</span>
                  </div>
                  <p className="text-sm text-gray-800">{risk.description}</p>
                  <p className="text-xs text-blue-600 mt-1">Recommendation: {risk.recommendation}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* AI Suggestions */}
      {ai.suggestions.length > 0 && (
        <div className="card overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
            <h3 className="text-sm font-semibold text-gray-700">Improvement Suggestions ({ai.suggestions.length})</h3>
          </div>
          <div className="divide-y divide-gray-100">
            {ai.suggestions.map((s, i) => (
              <div key={i} className="p-4 flex gap-3">
                <span className="text-xs badge-blue h-fit whitespace-nowrap">{s.category}</span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-gray-800">{s.description}</p>
                  <p className="text-xs text-gray-500 mt-1">Impact: {s.impact}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Spectral Violations */}
      <div className="card overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-200 bg-gray-50 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-700">
            Spectral Violations ({filteredViolations.length}{severityFilter !== 'all' ? ` ${severityFilter}` : ` total`})
          </h3>
          <select
            className="text-xs border border-gray-300 rounded-md px-2 py-1 bg-white"
            value={severityFilter}
            onChange={(e) => setSeverityFilter(e.target.value)}
          >
            <option value="all">All severities</option>
            <option value="error">Errors only</option>
            <option value="warning">Warnings only</option>
            <option value="info">Info only</option>
            <option value="hint">Hints only</option>
          </select>
        </div>
        {filteredViolations.length === 0 ? (
          <div className="p-8 text-center text-gray-400 text-sm">
            {spectral.violations.length === 0 ? 'No Spectral violations found.' : 'No violations match the selected filter.'}
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {['Severity', 'Rule', 'Message', 'Path'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filteredViolations.map((v, i) => (
                <tr key={i} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${SEVERITY_VIOLATION_STYLE[v.severity] ?? 'bg-gray-100 text-gray-600'}`}>
                      {v.severity}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-700">{v.code}</td>
                  <td className="px-4 py-3 text-gray-600 max-w-xs truncate" title={v.message}>{v.message}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-400">{v.path || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default function APIDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [api, setApi] = useState<API | null>(null);
  const [tab, setTab] = useState<Tab>('overview');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apisApi.get(id!)
      .then(({ data }) => setApi(data))
      .catch(() => toast.error('Failed to load API'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <div className="p-8 text-gray-400">Loading…</div>;
  if (!api) return <div className="p-8 text-red-500">API not found</div>;

  const methodColor: Record<string, string> = {
    GET: 'method-get', POST: 'method-post', PUT: 'method-put',
    PATCH: 'method-patch', DELETE: 'method-delete',
  };

  return (
    <div className="p-8">
      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center gap-2 text-sm text-gray-400 mb-2">
          <Link to="/apis" className="hover:text-blue-600">APIs</Link>
          <span>/</span>
          <span className="text-gray-700">{api.title}</span>
        </div>
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{api.title}</h1>
            <div className="flex items-center gap-3 mt-2">
              <span className="badge-blue">{api.oasVersion}</span>
              <span className="text-gray-500 text-sm">v{api.version}</span>
              <span className={api.status === 'active' ? 'badge-green' : 'badge-yellow'}>{api.status}</span>
              <span className="text-gray-400 text-xs font-mono">{api.name}</span>
            </div>
            {api.description && <p className="text-gray-500 mt-2 text-sm max-w-2xl">{api.description}</p>}
          </div>
          <Link to={`/proxies?apiId=${api.id}`} className="btn-primary">Create Proxy</Link>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 mb-6">
        <div className="flex gap-6">
          {(['overview', 'docs', 'endpoints', 'insights'] as Tab[]).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`pb-3 text-sm font-medium border-b-2 capitalize transition-colors ${
                tab === t ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {t === 'docs' ? 'API Docs (Swagger)' : t === 'insights' ? 'Insights' : t}
            </button>
          ))}
        </div>
      </div>

      {/* Overview */}
      {tab === 'overview' && (
        <div className="grid grid-cols-3 gap-6">
          <div className="card p-5">
            <div className="text-xs font-semibold text-gray-400 uppercase mb-3">Servers</div>
            {(api.servers || []).map((s, i) => (
              <div key={i} className="text-sm font-mono text-blue-600 truncate">{s.url}</div>
            ))}
          </div>
          <div className="card p-5">
            <div className="text-xs font-semibold text-gray-400 uppercase mb-3">Endpoints</div>
            <div className="text-3xl font-bold text-gray-900">{api.endpoints?.length || 0}</div>
            <div className="text-xs text-gray-400 mt-1">operations defined</div>
          </div>
          <div className="card p-5">
            <div className="text-xs font-semibold text-gray-400 uppercase mb-3">Tags</div>
            <div className="flex flex-wrap gap-1.5">
              {(api.tags || []).map((t) => <span key={t} className="badge-blue text-xs">{t}</span>)}
              {(api.tags || []).length === 0 && <span className="text-gray-400 text-xs">None</span>}
            </div>
          </div>
        </div>
      )}

      {/* Swagger Docs */}
      {tab === 'docs' && api.oasDocument && (
        <div className="card overflow-hidden">
          <SwaggerUI spec={api.oasDocument} />
        </div>
      )}

      {/* Endpoints table */}
      {tab === 'endpoints' && (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {['Method', 'Path', 'Summary', 'Tags'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {(api.endpoints || []).map((ep, i) => (
                <tr key={i} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <span className={methodColor[ep.method] || 'badge-gray'}>{ep.method}</span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-700">{ep.path}</td>
                  <td className="px-4 py-3 text-gray-600">{ep.summary || '—'}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1 flex-wrap">
                      {(ep.tags || []).map((t) => <span key={t} className="badge-gray text-xs">{t}</span>)}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Insights */}
      {tab === 'insights' && <InsightsTab apiId={api.id} />}
    </div>
  );
}
