import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { proxiesApi } from '../api/client';
import { format } from 'date-fns';

interface Proxy {
  id: string; name: string; description: string; targetUrl: string;
  pathPrefix: string; stripPrefix: boolean; version: number;
  policies: Record<string, unknown>; routes: unknown[]; headers: Record<string, string>;
  status: string; createdAt: string; updatedAt: string;
}

interface Version { id: string; version: number; changeNote: string; createdAt: string }

export default function ProxyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [proxy, setProxy] = useState<Proxy | null>(null);
  const [versions, setVersions] = useState<Version[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'config' | 'versions' | 'routes'>('config');

  const load = async () => {
    setLoading(true);
    try {
      const [{ data: p }, { data: v }] = await Promise.all([
        proxiesApi.get(id!),
        proxiesApi.versions(id!),
      ]);
      setProxy(p);
      setVersions(v);
    } catch {
      toast.error('Failed to load proxy');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [id]);

  const handleRollback = async (version: number) => {
    if (!confirm(`Roll back to v${version}?`)) return;
    try {
      await proxiesApi.rollback(id!, version);
      toast.success(`Rolled back to v${version}`);
      load();
    } catch {
      toast.error('Rollback failed');
    }
  };

  if (loading) return <div className="p-8 text-gray-400">Loading…</div>;
  if (!proxy) return <div className="p-8 text-red-500">Proxy not found</div>;

  return (
    <div className="p-8">
      <div className="mb-6">
        <div className="flex items-center gap-2 text-sm text-gray-400 mb-2">
          <Link to="/proxies" className="hover:text-blue-600">Proxies</Link>
          <span>/</span>
          <span className="text-gray-700">{proxy.name}</span>
        </div>
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{proxy.name}</h1>
            <div className="flex gap-3 mt-2 items-center">
              <span className={proxy.status === 'active' ? 'badge-green' : 'badge-gray'}>{proxy.status}</span>
              <span className="text-gray-500 text-sm">v{proxy.version}</span>
              <code className="text-xs bg-gray-100 px-2 py-0.5 rounded">{proxy.pathPrefix} → {proxy.targetUrl}</code>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 mb-6">
        <div className="flex gap-6">
          {(['config', 'routes', 'versions'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`pb-3 text-sm font-medium border-b-2 capitalize transition-colors ${
                tab === t ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {t === 'versions' ? `Versions (${versions.length})` : t}
            </button>
          ))}
        </div>
      </div>

      {tab === 'config' && (
        <div className="grid grid-cols-2 gap-6">
          <div className="card p-5 space-y-3">
            <div className="text-xs font-semibold text-gray-400 uppercase">General</div>
            {[
              ['Target URL', proxy.targetUrl],
              ['Path Prefix', proxy.pathPrefix],
              ['Strip Prefix', proxy.stripPrefix ? 'Yes' : 'No'],
              ['Version', `v${proxy.version}`],
              ['Updated', format(new Date(proxy.updatedAt), 'MMM d, yyyy HH:mm')],
            ].map(([k, v]) => (
              <div key={k} className="flex justify-between text-sm">
                <span className="text-gray-500">{k}</span>
                <span className="font-medium text-gray-800 font-mono text-xs">{v}</span>
              </div>
            ))}
          </div>

          <div className="card p-5">
            <div className="text-xs font-semibold text-gray-400 uppercase mb-3">Policies</div>
            <pre className="text-xs bg-gray-50 rounded p-3 overflow-auto max-h-64">
              {JSON.stringify(proxy.policies, null, 2)}
            </pre>
          </div>

          {Object.keys(proxy.headers).length > 0 && (
            <div className="card p-5">
              <div className="text-xs font-semibold text-gray-400 uppercase mb-3">Custom Headers</div>
              {Object.entries(proxy.headers).map(([k, v]) => (
                <div key={k} className="flex justify-between text-sm py-1 border-b border-gray-50">
                  <span className="font-mono text-gray-600 text-xs">{k}</span>
                  <span className="text-gray-500 text-xs">{v}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {tab === 'routes' && (
        <div className="card overflow-hidden">
          {(proxy.routes as Array<Record<string, unknown>>).length === 0 ? (
            <div className="p-8 text-center text-gray-400 text-sm">No explicit routes — all paths proxied to target</div>
          ) : (
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  {['Method', 'Path', 'Target Path'].map((h) => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {(proxy.routes as Array<Record<string, string>>).map((r, i) => (
                  <tr key={i}>
                    <td className="px-4 py-3"><span className="badge-blue">{r.method}</span></td>
                    <td className="px-4 py-3 font-mono text-xs">{r.path}</td>
                    <td className="px-4 py-3 font-mono text-xs text-gray-400">{r.targetPath || '(same)'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {tab === 'versions' && (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {['Version', 'Note', 'Date', ''].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {versions.map((v) => (
                <tr key={v.id} className={v.version === proxy.version ? 'bg-blue-50' : 'hover:bg-gray-50'}>
                  <td className="px-4 py-3 font-medium">
                    v{v.version}
                    {v.version === proxy.version && <span className="badge-blue ml-2">current</span>}
                  </td>
                  <td className="px-4 py-3 text-gray-500">{v.changeNote || '—'}</td>
                  <td className="px-4 py-3 text-gray-400 text-xs">{format(new Date(v.createdAt), 'MMM d, yyyy HH:mm')}</td>
                  <td className="px-4 py-3">
                    {v.version !== proxy.version && (
                      <button onClick={() => handleRollback(v.version)} className="text-blue-600 hover:underline text-xs">Rollback</button>
                    )}
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
