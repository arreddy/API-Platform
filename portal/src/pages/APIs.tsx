import { useEffect, useState, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useDropzone } from 'react-dropzone';
import toast from 'react-hot-toast';
import { apisApi } from '../api/client';
import { format } from 'date-fns';

interface API {
  id: string;
  name: string;
  title: string;
  version: string;
  description: string;
  oasVersion: string;
  tags: string[];
  status: string;
  createdAt: string;
}

interface RegisteredInsights {
  spectral: { errorCount: number; warningCount: number; infoCount: number };
  ai: { score: number | null; summary: string };
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = { active: 'badge-green', deprecated: 'badge-yellow', archived: 'badge-gray' };
  return <span className={map[status] || 'badge-gray'}>{status}</span>;
}

export default function APIsPage() {
  const [apis, setApis] = useState<API[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showUpload, setShowUpload] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [oasText, setOasText] = useState('');
  const [validating, setValidating] = useState(false);
  const [validation, setValidation] = useState<Record<string, unknown> | null>(null);
  const [registeredId, setRegisteredId] = useState<string | null>(null);
  const [registeredInsights, setRegisteredInsights] = useState<RegisteredInsights | null>(null);
  const navigate = useNavigate();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await apisApi.list({ search: search || undefined });
      setApis(data.data);
      setTotal(data.total);
    } catch {
      toast.error('Failed to load APIs');
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => { load(); }, [load]);

  const { getRootProps, getInputProps, isDragActive, acceptedFiles } = useDropzone({
    accept: { 'application/json': ['.json'], 'application/x-yaml': ['.yaml', '.yml'], 'text/plain': ['.yaml', '.yml', '.json'] },
    maxFiles: 1,
    onDrop: async (files) => {
      if (!files[0]) return;
      const text = await files[0].text();
      setOasText(text);
      // Auto-validate on drop
      const fd = new FormData();
      fd.append('oas', files[0]);
      setValidating(true);
      try {
        const { data } = await apisApi.validate(fd);
        setValidation(data.summary);
        toast.success(`Valid OAS ${data.summary.oasVersion} — ${data.summary.endpointCount} endpoints`);
      } catch (e: unknown) {
        const msg = (e as { response?: { data?: { error?: string } } }).response?.data?.error || 'Validation failed';
        toast.error(msg);
        setValidation(null);
      } finally {
        setValidating(false);
      }
    },
  });

  const handleRegister = async () => {
    if (!oasText && acceptedFiles.length === 0) {
      toast.error('Provide an OAS file or paste OAS content');
      return;
    }
    setUploading(true);
    try {
      const fd = new FormData();
      if (acceptedFiles[0]) {
        fd.append('oas', acceptedFiles[0]);
      } else {
        fd.append('oas', oasText);
      }
      const { data } = await apisApi.create(fd);
      toast.success(`API "${data.api.title}" registered!`);
      setRegisteredId(data.api.id);
      if (data.insights) setRegisteredInsights(data.insights);
      load();
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: string } } }).response?.data?.error || 'Registration failed';
      toast.error(msg);
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (id: string, name: string) => {
    if (!confirm(`Delete API "${name}"?`)) return;
    try {
      await apisApi.delete(id);
      toast.success('API deleted');
      load();
    } catch {
      toast.error('Delete failed');
    }
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">APIs</h1>
          <p className="text-gray-500 mt-0.5">{total} registered APIs</p>
        </div>
        <button className="btn-primary" onClick={() => setShowUpload(true)}>
          + Register API
        </button>
      </div>

      {/* Upload modal */}
      {showUpload && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl">
            <div className="p-6 border-b border-gray-200 flex items-center justify-between">
              <h2 className="text-lg font-semibold">Register API from OAS</h2>
              <button onClick={() => { setShowUpload(false); setValidation(null); setOasText(''); setRegisteredId(null); setRegisteredInsights(null); }} className="text-gray-400 hover:text-gray-600 text-xl">×</button>
            </div>

            <div className="p-6 space-y-4">
              {/* Drop zone */}
              <div
                {...getRootProps()}
                className={`border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors ${
                  isDragActive ? 'border-blue-500 bg-blue-50' : 'border-gray-300 hover:border-blue-400 hover:bg-gray-50'
                }`}
              >
                <input {...getInputProps()} />
                <div className="text-3xl mb-2">📄</div>
                <p className="text-sm text-gray-600">
                  {acceptedFiles[0] ? (
                    <span className="font-medium text-blue-600">{acceptedFiles[0].name}</span>
                  ) : (
                    <>Drop your OAS JSON/YAML file here, or <span className="text-blue-600 font-medium">click to browse</span></>
                  )}
                </p>
                <p className="text-xs text-gray-400 mt-1">Supports OpenAPI 3.0 and 3.1</p>
              </div>

              {/* Or paste */}
              <div className="relative">
                <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-gray-200" /></div>
                <div className="relative flex justify-center text-xs text-gray-400 bg-white px-2">or paste OAS content</div>
              </div>
              <textarea
                className="input font-mono text-xs h-32 resize-none"
                placeholder='{ "openapi": "3.0.0", ... }'
                value={oasText}
                onChange={(e) => setOasText(e.target.value)}
              />

              {/* Validation result */}
              {validating && <div className="text-sm text-blue-600 animate-pulse">Validating…</div>}
              {validation && (
                <div className="bg-green-50 border border-green-200 rounded-lg p-4 text-sm">
                  <div className="font-semibold text-green-800 mb-2">✓ Valid OAS {String(validation.oasVersion)}</div>
                  <div className="grid grid-cols-2 gap-2 text-green-700">
                    <div>Title: <span className="font-medium">{String(validation.title)}</span></div>
                    <div>Version: <span className="font-medium">{String(validation.version)}</span></div>
                    <div>Endpoints: <span className="font-medium">{String(validation.endpointCount)}</span></div>
                    <div>Tags: <span className="font-medium">{(validation.tags as string[]).join(', ') || '—'}</span></div>
                  </div>
                </div>
              )}
            </div>

            {/* Post-registration insight summary */}
            {registeredId && (
              <div className="px-6 pb-4">
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                  <div className="font-semibold text-blue-800 mb-2">API registered successfully!</div>
                  {registeredInsights ? (
                    <div className="text-sm text-blue-700 space-y-1">
                      <div className="flex gap-4">
                        <span>AI Score: <strong className={registeredInsights.ai.score !== null && registeredInsights.ai.score >= 70 ? 'text-green-700' : 'text-orange-600'}>{registeredInsights.ai.score ?? '—'}/100</strong></span>
                        <span>Errors: <strong className="text-red-600">{registeredInsights.spectral.errorCount}</strong></span>
                        <span>Warnings: <strong className="text-yellow-600">{registeredInsights.spectral.warningCount}</strong></span>
                      </div>
                      {registeredInsights.ai.summary && (
                        <p className="text-xs text-blue-600 mt-1 line-clamp-2">{registeredInsights.ai.summary}</p>
                      )}
                    </div>
                  ) : (
                    <p className="text-sm text-blue-600">Analysis running in background — check the Insights tab.</p>
                  )}
                </div>
              </div>
            )}

            <div className="p-6 border-t border-gray-200 flex justify-end gap-3">
              {registeredId ? (
                <>
                  <button className="btn-secondary" onClick={() => { setShowUpload(false); setValidation(null); setOasText(''); setRegisteredId(null); setRegisteredInsights(null); }}>
                    Close
                  </button>
                  <button className="btn-primary" onClick={() => navigate(`/apis/${registeredId}`)}>
                    View API
                  </button>
                </>
              ) : (
                <>
                  <button className="btn-secondary" onClick={() => { setShowUpload(false); setValidation(null); setOasText(''); }}>
                    Cancel
                  </button>
                  <button className="btn-primary" onClick={handleRegister} disabled={uploading || (!acceptedFiles[0] && !oasText)}>
                    {uploading ? 'Registering…' : 'Register API'}
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Search */}
      <div className="mb-4">
        <input
          className="input max-w-sm"
          placeholder="Search APIs…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {/* Table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              {['Title', 'Name', 'Version', 'OAS', 'Tags', 'Status', 'Created', ''].map((h) => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loading ? (
              <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">Loading…</td></tr>
            ) : apis.length === 0 ? (
              <tr><td colSpan={8} className="px-4 py-12 text-center text-gray-400">
                No APIs yet. Click <strong>Register API</strong> to get started.
              </td></tr>
            ) : apis.map((api) => (
              <tr key={api.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">
                  <Link to={`/apis/${api.id}`} className="text-blue-600 hover:underline">{api.title}</Link>
                </td>
                <td className="px-4 py-3 text-gray-500 font-mono text-xs">{api.name}</td>
                <td className="px-4 py-3 text-gray-600">{api.version}</td>
                <td className="px-4 py-3"><span className="badge-blue">{api.oasVersion}</span></td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {(api.tags || []).slice(0, 3).map((t) => (
                      <span key={t} className="badge-gray text-xs">{t}</span>
                    ))}
                  </div>
                </td>
                <td className="px-4 py-3"><StatusBadge status={api.status} /></td>
                <td className="px-4 py-3 text-gray-400 text-xs">{format(new Date(api.createdAt), 'MMM d, yyyy')}</td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    <Link to={`/apis/${api.id}`} className="text-blue-600 hover:underline text-xs">View</Link>
                    <button onClick={() => handleDelete(api.id, api.name)} className="text-red-500 hover:underline text-xs">Delete</button>
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
