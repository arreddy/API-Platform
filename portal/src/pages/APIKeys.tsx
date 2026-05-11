import { useEffect, useState, useCallback } from 'react';
import toast from 'react-hot-toast';
import { keysApi, proxiesApi } from '../api/client';
import { format } from 'date-fns';

interface ApiKey {
  id: string; name: string; keyPrefix: string; proxyId?: string;
  scopes: string[]; rateLimit: number; rateLimitWindow: string;
  status: string; lastUsedAt?: string; expiresAt?: string; createdAt: string;
  rawKey?: string;
}

interface Proxy { id: string; name: string }

export default function APIKeysPage() {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [proxies, setProxies] = useState<Proxy[]>([]);
  const [showCreate, setShowCreate] = useState(false);
  const [newKey, setNewKey] = useState<ApiKey | null>(null);
  const [form, setForm] = useState({ name: '', proxyId: '', rateLimit: 1000, rateLimitWindow: '1h', expiresAt: '' });
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [keysRes, proxiesRes] = await Promise.all([keysApi.list(), proxiesApi.list({ limit: 100 })]);
      setKeys(keysRes.data.data);
      setTotal(keysRes.data.total);
      setProxies(proxiesRes.data.data);
    } catch {
      toast.error('Failed to load');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    try {
      const { data } = await keysApi.create({
        name: form.name,
        proxyId: form.proxyId || undefined,
        rateLimit: form.rateLimit,
        rateLimitWindow: form.rateLimitWindow,
        expiresAt: form.expiresAt || undefined,
      });
      setNewKey(data);
      load();
      setShowCreate(false);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: string } } }).response?.data?.error || 'Failed to create key';
      toast.error(msg);
    } finally {
      setCreating(false);
    }
  };

  const handleRevoke = async (id: string, name: string) => {
    if (!confirm(`Revoke key "${name}"? This cannot be undone.`)) return;
    try {
      await keysApi.revoke(id);
      toast.success('Key revoked');
      load();
    } catch {
      toast.error('Revoke failed');
    }
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">API Keys</h1>
          <p className="text-gray-500 mt-0.5">{total} keys</p>
        </div>
        <button className="btn-primary" onClick={() => setShowCreate(true)}>+ Generate Key</button>
      </div>

      {/* New key reveal modal */}
      {newKey?.rawKey && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-6">
            <div className="text-center mb-4">
              <div className="text-4xl mb-2">🔑</div>
              <h2 className="text-lg font-semibold">Your API Key</h2>
              <p className="text-sm text-gray-500 mt-1">Copy this key now — it will <strong>never</strong> be shown again.</p>
            </div>
            <div className="bg-gray-900 text-green-400 font-mono text-sm p-4 rounded-lg break-all mb-4">
              {newKey.rawKey}
            </div>
            <button
              className="btn-primary w-full justify-center mb-3"
              onClick={() => { navigator.clipboard.writeText(newKey.rawKey!); toast.success('Copied!'); }}
            >
              Copy to clipboard
            </button>
            <button className="btn-secondary w-full justify-center" onClick={() => setNewKey(null)}>
              I've saved it — close
            </button>
          </div>
        </div>
      )}

      {/* Create modal */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
            <div className="p-6 border-b border-gray-200 flex items-center justify-between">
              <h2 className="text-lg font-semibold">Generate API Key</h2>
              <button onClick={() => setShowCreate(false)} className="text-gray-400 hover:text-gray-600 text-xl">×</button>
            </div>
            <form onSubmit={handleCreate} className="p-6 space-y-4">
              <div>
                <label className="label">Name *</label>
                <input className="input" placeholder="Production key" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required />
              </div>
              <div>
                <label className="label">Linked Proxy</label>
                <select className="input" value={form.proxyId} onChange={(e) => setForm((f) => ({ ...f, proxyId: e.target.value }))}>
                  <option value="">— All proxies —</option>
                  {proxies.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label">Rate limit</label>
                  <input type="number" className="input" value={form.rateLimit} onChange={(e) => setForm((f) => ({ ...f, rateLimit: Number(e.target.value) }))} />
                </div>
                <div>
                  <label className="label">Window</label>
                  <select className="input" value={form.rateLimitWindow} onChange={(e) => setForm((f) => ({ ...f, rateLimitWindow: e.target.value }))}>
                    {['1m', '5m', '1h', '24h'].map((w) => <option key={w}>{w}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label className="label">Expires at (optional)</label>
                <input type="datetime-local" className="input" value={form.expiresAt} onChange={(e) => setForm((f) => ({ ...f, expiresAt: e.target.value }))} />
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" className="btn-secondary flex-1 justify-center" onClick={() => setShowCreate(false)}>Cancel</button>
                <button type="submit" className="btn-primary flex-1 justify-center" disabled={creating}>{creating ? 'Generating…' : 'Generate'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              {['Name', 'Key Prefix', 'Proxy', 'Rate Limit', 'Status', 'Last Used', 'Expires', ''].map((h) => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading ? (
              <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">Loading…</td></tr>
            ) : keys.length === 0 ? (
              <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">No API keys yet. Generate one to get started.</td></tr>
            ) : keys.map((k) => (
              <tr key={k.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{k.name}</td>
                <td className="px-4 py-3 font-mono text-xs text-gray-600">{k.keyPrefix}…</td>
                <td className="px-4 py-3 text-gray-500 text-xs">{proxies.find((p) => p.id === k.proxyId)?.name || '—'}</td>
                <td className="px-4 py-3 text-gray-500 text-xs">{k.rateLimit}/{k.rateLimitWindow}</td>
                <td className="px-4 py-3">
                  <span className={k.status === 'active' ? 'badge-green' : 'badge-red'}>{k.status}</span>
                </td>
                <td className="px-4 py-3 text-gray-400 text-xs">{k.lastUsedAt ? format(new Date(k.lastUsedAt), 'MMM d HH:mm') : '—'}</td>
                <td className="px-4 py-3 text-gray-400 text-xs">{k.expiresAt ? format(new Date(k.expiresAt), 'MMM d yyyy') : '∞'}</td>
                <td className="px-4 py-3">
                  {k.status === 'active' && (
                    <button onClick={() => handleRevoke(k.id, k.name)} className="text-red-500 hover:underline text-xs">Revoke</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
