import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useDropzone } from 'react-dropzone';
import toast from 'react-hot-toast';
import { deployApi } from '../api/client';

interface DeployResult {
  api: {
    id: string;
    name: string;
    title: string;
    version: string;
    oasVersion: string;
    status: string;
  };
  proxy: {
    id: string;
    name: string;
    targetUrl: string;
    pathPrefix: string;
    status: string;
    policies: Record<string, unknown>;
  };
  aiRationale: string | null;
  warnings: string[] | null;
}

export default function DeployPage() {
  const [oasText, setOasText] = useState('');
  const [deploying, setDeploying] = useState(false);
  const [result, setResult] = useState<DeployResult | null>(null);
  const [showOverrides, setShowOverrides] = useState(false);
  const [targetUrlOverride, setTargetUrlOverride] = useState('');
  const [pathPrefixOverride, setPathPrefixOverride] = useState('');
  const [authTypeOverride, setAuthTypeOverride] = useState('');
  const [rateLimitRpmOverride, setRateLimitRpmOverride] = useState('');

  const { getRootProps, getInputProps, isDragActive, acceptedFiles } = useDropzone({
    accept: {
      'application/json': ['.json'],
      'application/x-yaml': ['.yaml', '.yml'],
      'text/plain': ['.yaml', '.yml', '.json'],
    },
    maxFiles: 1,
    onDrop: async (files) => {
      if (files[0]) setOasText(await files[0].text());
    },
  });

  const hasOas = acceptedFiles.length > 0 || oasText.trim().length > 0;

  const handleDeploy = async () => {
    if (!hasOas) {
      toast.error('Provide an OAS file or paste OAS content');
      return;
    }
    setDeploying(true);
    setResult(null);
    try {
      const fd = new FormData();
      if (acceptedFiles[0]) {
        fd.append('oas', acceptedFiles[0]);
      } else {
        fd.append('oas', oasText);
      }
      const { data } = await deployApi.deploy(fd, {
        targetUrlOverride: targetUrlOverride || undefined,
        pathPrefixOverride: pathPrefixOverride || undefined,
        authTypeOverride: authTypeOverride || undefined,
        rateLimitRpmOverride: rateLimitRpmOverride ? parseInt(rateLimitRpmOverride) : undefined,
      });
      setResult(data);
      toast.success(`"${data.api.title}" deployed to gateway`);
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { error?: string } } }).response?.data?.error ||
        'Deploy failed';
      toast.error(msg);
    } finally {
      setDeploying(false);
    }
  };

  const reset = () => {
    setResult(null);
    setOasText('');
    setTargetUrlOverride('');
    setPathPrefixOverride('');
    setAuthTypeOverride('');
    setRateLimitRpmOverride('');
  };

  if (result) {
    return <DeployResult result={result} onReset={reset} />;
  }

  return (
    <div className="p-8 max-w-3xl">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Deploy API from OAS</h1>
        <p className="text-gray-500 mt-0.5">
          Upload an OpenAPI Specification — AI generates the proxy configuration and deploys it to
          the gateway in one step.
        </p>
      </div>

      <div className="space-y-5">
        {/* OAS source */}
        <div className="card p-6 space-y-4">
          <h2 className="font-semibold text-gray-700 text-sm uppercase tracking-wide">
            OpenAPI Specification
          </h2>

          <div
            {...getRootProps()}
            className={`border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors ${
              isDragActive
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-300 hover:border-blue-400 hover:bg-gray-50'
            }`}
          >
            <input {...getInputProps()} />
            <div className="text-2xl font-mono text-gray-300 mb-2">OAS</div>
            <p className="text-sm text-gray-600">
              {acceptedFiles[0] ? (
                <span className="font-medium text-blue-600">{acceptedFiles[0].name}</span>
              ) : (
                <>
                  Drop your JSON or YAML file here, or{' '}
                  <span className="text-blue-600 font-medium">click to browse</span>
                </>
              )}
            </p>
            <p className="text-xs text-gray-400 mt-1">OpenAPI 3.0 and 3.1</p>
          </div>

          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-gray-200" />
            </div>
            <div className="relative flex justify-center">
              <span className="text-xs text-gray-400 bg-white px-2">or paste OAS content</span>
            </div>
          </div>

          <textarea
            className="input font-mono text-xs h-36 resize-none"
            placeholder='{ "openapi": "3.0.0", "info": { "title": "...", "version": "1.0.0" }, ... }'
            value={oasText}
            onChange={(e) => setOasText(e.target.value)}
          />
        </div>

        {/* Overrides */}
        <div className="card overflow-hidden">
          <button
            className="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-50 transition-colors"
            onClick={() => setShowOverrides(!showOverrides)}
          >
            <div>
              <span className="font-semibold text-gray-700 text-sm">Advanced Overrides</span>
              <span className="ml-2 text-xs text-gray-400">optional — AI chooses for you</span>
            </div>
            <span className="text-gray-400 text-xs">{showOverrides ? '▲' : '▼'}</span>
          </button>

          {showOverrides && (
            <div className="px-6 pb-6 border-t border-gray-100 pt-4 grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Target URL</label>
                <input
                  className="input text-sm"
                  placeholder="https://api.example.com"
                  value={targetUrlOverride}
                  onChange={(e) => setTargetUrlOverride(e.target.value)}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Path Prefix</label>
                <input
                  className="input text-sm"
                  placeholder="/v1/my-api"
                  value={pathPrefixOverride}
                  onChange={(e) => setPathPrefixOverride(e.target.value)}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Auth Type</label>
                <select
                  className="input text-sm"
                  value={authTypeOverride}
                  onChange={(e) => setAuthTypeOverride(e.target.value)}
                >
                  <option value="">AI auto-detect</option>
                  <option value="api_key">API Key</option>
                  <option value="jwt">JWT</option>
                  <option value="none">None</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">
                  Rate Limit (req/min)
                </label>
                <input
                  className="input text-sm"
                  type="number"
                  min="1"
                  placeholder="60"
                  value={rateLimitRpmOverride}
                  onChange={(e) => setRateLimitRpmOverride(e.target.value)}
                />
              </div>
            </div>
          )}
        </div>

        {/* Deploy button */}
        <button
          className="btn-primary w-full py-3"
          onClick={handleDeploy}
          disabled={deploying || !hasOas}
        >
          {deploying ? 'Deploying to gateway…' : 'Deploy to Gateway'}
        </button>
      </div>
    </div>
  );
}

function DeployResult({
  result,
  onReset,
}: {
  result: DeployResult;
  onReset: () => void;
}) {
  const authType = (result.proxy.policies?.auth as Record<string, string> | undefined)?.type;
  const rateLimit = result.proxy.policies?.rateLimit as
    | Record<string, unknown>
    | undefined;

  return (
    <div className="p-8 max-w-3xl space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Deployed</h1>
          <p className="text-gray-500 mt-0.5">
            API registered and proxy is live on the gateway.
          </p>
        </div>
        <button className="btn-secondary text-sm" onClick={onReset}>
          Deploy Another
        </button>
      </div>

      {/* AI rationale */}
      {result.aiRationale && (
        <div className="card p-5 border-l-4 border-blue-500 bg-blue-50/30">
          <div className="text-xs font-semibold text-blue-500 uppercase tracking-wide mb-1">
            AI Rationale
          </div>
          <p className="text-sm text-gray-700">{result.aiRationale}</p>
        </div>
      )}

      {/* Warnings */}
      {result.warnings && result.warnings.length > 0 && (
        <div className="card p-5 border-l-4 border-yellow-400 bg-yellow-50/30">
          <div className="text-xs font-semibold text-yellow-600 uppercase tracking-wide mb-2">
            Warnings
          </div>
          <ul className="text-sm text-yellow-700 space-y-1 list-disc list-inside">
            {result.warnings.map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
        </div>
      )}

      {/* API + Proxy side-by-side */}
      <div className="grid grid-cols-2 gap-4">
        <div className="card p-5 flex flex-col">
          <div className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">
            API Registered
          </div>
          <div className="font-semibold text-gray-900">{result.api.title}</div>
          <div className="text-xs text-gray-400 font-mono mt-0.5">{result.api.name}</div>
          <div className="flex gap-2 mt-3 flex-wrap">
            <span className="badge-blue">{result.api.oasVersion}</span>
            <span className="badge-green">{result.api.status}</span>
          </div>
          <div className="mt-auto pt-4">
            <Link
              to={`/apis/${result.api.id}`}
              className="btn-secondary text-xs w-full block text-center"
            >
              View API
            </Link>
          </div>
        </div>

        <div className="card p-5 flex flex-col">
          <div className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-3">
            Proxy Deployed
          </div>
          <div className="font-semibold text-gray-900">{result.proxy.name}</div>
          <div className="text-xs font-mono text-gray-400 mt-0.5">{result.proxy.pathPrefix}</div>
          <div
            className="text-xs text-gray-400 mt-1 truncate"
            title={result.proxy.targetUrl}
          >
            {result.proxy.targetUrl}
          </div>
          <div className="flex gap-2 mt-3 flex-wrap">
            {authType && <span className="badge-blue">{authType}</span>}
            {Boolean(rateLimit?.enabled) && (
              <span className="badge-gray">{String(rateLimit?.requests)} rpm</span>
            )}
            <span className="badge-green">{result.proxy.status}</span>
          </div>
          <div className="mt-auto pt-4">
            <Link
              to={`/proxies/${result.proxy.id}`}
              className="btn-secondary text-xs w-full block text-center"
            >
              View Proxy
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
