import { useEffect, useState, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { proxiesApi, apisApi } from '../api/client';
import { format } from 'date-fns';

interface Proxy {
  id: string;
  name: string;
  description: string;
  target_url: string;
  path_prefix: string;
  version: number;
  status: string;
  api_id?: string;
  created_at: string;
}

interface ApiOption { id: string; title: string; name: string }

function CreateProxyModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [form, setForm] = useState({
    name: '', description: '', targetUrl: '', pathPrefix: '/',
    stripPrefix: true, apiId: '',
    policies: { rateLimit: { enabled: false, requests: 1000, window: '1h' }, auth: { type: 'api_key' } },
  });
  const [apis, setApis] = useState<ApiOption[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    apisApi.list({ limit: 100 }).then(({ data }) => setApis(data.data)).catch(() => {});
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await proxiesApi.create({
        ...form,
        apiId: form.apiId || undefined,
        policies: { ...form.policies },
      });
      toast.success('Proxy created!');
      onCreated();
      onClose();
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: string } } }).response?.data?.error || 'Failed to create proxy';
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  const f = (key: string, val: unknown) => setForm((prev) => ({ ...prev, [key]: val }));

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-xl max-h-[90vh] overflow-y-auto">
        <div className="p-6 border-b border-gray-200 flex items-center justify-between sticky top-0 bg-white">
          <h2 className="text-lg font-semibold">Create Proxy</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">×</button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div>
            <label className="label">Name *</label>
            <input className="input" placeholder="my-api-proxy" value={form.name} onChange={(e) => f('name', e.target.value)} required />
          </div>
          <div>
            <label className="label">Description</label>
            <input className="input" placeholder="Optional description" value={form.description} onChange={(e) => f('description', e.target.value)} />
          </div>
          <div>
            <label className="label">Target URL *</label>
            <input className="input" type="url" placeholder="https://api.backend.com" value={form.targetUrl} onChange={(e) => f('targetUrl', e.target.value)} required />
          </div>
          <div>
            <label className="label">Path Prefix *</label>
            <input className="input" placeholder="/my-api" value={form.pathPrefix} onChange={(e) => f('pathPrefix', e.target.value)} required />
            <p className="text-xs text-gray-400 mt-1">Gateway routes requests starting with this path</p>
          </div>
          <div>
            <label className="label">Linked API (optional)</label>
            <select className="input" value={form.apiId} onChange={(e) => f('apiId', e.target.value)}>
              <option value="">— none —</option>
              {apis.map((a) => <option key={a.id} value={a.id}>{a.title}</option>)}
            </select>
          </div>

          <div className="border-t border-gray-100 pt-4">
            <div className="text-xs font-semibold text-gray-500 uppercase mb-3">Policies</div>

            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <input
                  type="checkbox"
                  id="rl"
                  checked={form.policies.rateLimit.enabled}
                  onChange={(e) => setForm((p) => ({ ...p, policies: { ...p.policies, rateLimit: { ...p.policies.rateLimit, enabled: e.target.checked } } }))}
                />
                <label htmlFor="rl" className="text-sm">Rate limiting</label>
                {form.policies.rateLimit.enabled && (
                  <div className="flex gap-2 ml-2">
                    <input
                      type="number"
                      className="input w-24 text-xs"
                      value={form.policies.rateLimit.requests}
                      onChange={(e) => setForm((p) => ({ ...p, policies: { ...p.policies, rateLimit: { ...p.policies.rateLimit, requests: Number(e.target.value) } } }))}
                    />
                    <select
                      className="input w-20 text-xs"
                      value={form.policies.rateLimit.window}
                      onChange={(e) => setForm((p) => ({ ...p, policies: { ...p.policies, rateLimit: { ...p.policies.rateLimit, window: e.target.value } } }))}
                    >
                      {['1m', '5m', '1h', '1d'].map((w) => <option key={w}>{w}</option>)}
                    </select>
                  </div>
                )}
              </div>

              <div className="flex items-center gap-3">
                <label className="text-sm font-medium text-gray-600">Auth type:</label>
                <select
                  className="input w-32 text-xs"
                  value={form.policies.auth.type}
                  onChange={(e) => setForm((p) => ({ ...p, policies: { ...p.policies, auth: { type: e.target.value } } }))}
                >
                  {['none', 'api_key', 'jwt', 'oauth2'].map((t) => <option key={t}>{t}</option>)}
                </select>
              </div>
            </div>
          </div>

          <div className="pt-4 flex justify-end gap-3 border-t border-gray-100">
            <button type="button" className="btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={saving}>{saving ? 'Creating…' : 'Create Proxy'}</button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function ProxiesPage() {
  const [proxies, setProxies] = useState<Proxy[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [searchParams] = useSearchParams();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await proxiesApi.list({ apiId: searchParams.get('apiId') || undefined });
      setProxies(data.data);
      setTotal(data.total);
    } catch {
      toast.error('Failed to load proxies');
    } finally {
      setLoading(false);
    }
  }, [searchParams]);

  useEffect(() => { load(); }, [load]);

  const handleDelete = async (id: string, name: string) => {
    if (!confirm(`Deactivate proxy "${name}"?`)) return;
    try {
      await proxiesApi.delete(id);
      toast.success('Proxy deactivated');
      load();
    } catch {
      toast.error('Failed to deactivate');
    }
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Proxies</h1>
          <p className="text-gray-500 mt-0.5">{total} proxy configurations</p>
        </div>
        <button className="btn-primary" onClick={() => setShowCreate(true)}>+ Create Proxy</button>
      </div>

      {showCreate && <CreateProxyModal onClose={() => setShowCreate(false)} onCreated={load} />}

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              {['Name', 'Path Prefix', 'Target URL', 'Version', 'Status', 'Created', ''].map((h) => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading ? (
              <tr><td colSpan={7} className="px-4 py-12 text-center text-gray-400">Loading…</td></tr>
            ) : proxies.length === 0 ? (
              <tr><td colSpan={7} className="px-4 py-12 text-center text-gray-400">
                No proxies yet. Click <strong>Create Proxy</strong> to get started.
              </td></tr>
            ) : proxies.map((p) => (
              <tr key={p.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">
                  <Link to={`/proxies/${p.id}`} className="text-blue-600 hover:underline">{p.name}</Link>
                  {p.description && <div className="text-xs text-gray-400 truncate max-w-xs">{p.description}</div>}
                </td>
                <td className="px-4 py-3 font-mono text-xs text-gray-600">{p.path_prefix}</td>
                <td className="px-4 py-3 text-xs text-gray-500 truncate max-w-xs">{p.target_url}</td>
                <td className="px-4 py-3 text-gray-500">v{p.version}</td>
                <td className="px-4 py-3">
                  <span className={p.status === 'active' ? 'badge-green' : 'badge-gray'}>{p.status}</span>
                </td>
                <td className="px-4 py-3 text-gray-400 text-xs">{format(new Date(p.created_at), 'MMM d, yyyy')}</td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    <Link to={`/proxies/${p.id}`} className="text-blue-600 hover:underline text-xs">Manage</Link>
                    <button onClick={() => handleDelete(p.id, p.name)} className="text-red-500 hover:underline text-xs">Delete</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
