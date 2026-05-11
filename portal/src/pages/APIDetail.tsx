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

type Tab = 'overview' | 'docs' | 'endpoints';

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
          {(['overview', 'docs', 'endpoints'] as Tab[]).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`pb-3 text-sm font-medium border-b-2 capitalize transition-colors ${
                tab === t ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {t === 'docs' ? 'API Docs (Swagger)' : t}
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
    </div>
  );
}
