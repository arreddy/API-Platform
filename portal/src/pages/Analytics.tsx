import { useEffect, useState } from 'react';
import {
  AreaChart, Area, BarChart, Bar, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts';
import { analyticsApi, proxiesApi } from '../api/client';
import { format, subHours, subDays } from 'date-fns';

type TimeRange = '1h' | '24h' | '7d' | '30d';

interface Summary {
  totals: { totalRequests: number; successRequests: number; errorRequests: number; avgLatency: number; p99Latency: number };
  timeSeries: Array<{ hour: string; requests: string; avg_latency: string; errors: string }>;
  statusDistribution: Array<{ status_class: number; count: string }>;
  topProxies: Array<{ proxy_id: string; requests: string }>;
}

interface Proxy { id: string; name: string }

const timeRanges: Record<TimeRange, () => Date> = {
  '1h': () => subHours(new Date(), 1),
  '24h': () => subHours(new Date(), 24),
  '7d': () => subDays(new Date(), 7),
  '30d': () => subDays(new Date(), 30),
};

export default function AnalyticsPage() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [proxies, setProxies] = useState<Proxy[]>([]);
  const [range, setRange] = useState<TimeRange>('24h');
  const [proxyId, setProxyId] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    proxiesApi.list({ limit: 100 }).then(({ data }) => setProxies(data.data)).catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    analyticsApi.summary({
      from: timeRanges[range]().toISOString(),
      to: new Date().toISOString(),
      proxyId: proxyId || undefined,
    })
      .then(({ data }) => setSummary(data))
      .catch(() => setSummary(null))
      .finally(() => setLoading(false));
  }, [range, proxyId]);

  const tsData = (summary?.timeSeries || []).map((d) => ({
    time: format(new Date(d.hour), range === '30d' ? 'MMM d' : 'HH:mm'),
    requests: Number(d.requests),
    latency: Number(d.avg_latency),
    errors: Number(d.errors),
  }));

  const statCards = [
    { label: 'Total Requests', value: summary?.totals.totalRequests?.toLocaleString() ?? '—', color: 'text-blue-600', bg: 'bg-blue-50' },
    { label: 'Successful', value: summary?.totals.successRequests?.toLocaleString() ?? '—', color: 'text-green-600', bg: 'bg-green-50' },
    { label: 'Errors', value: summary?.totals.errorRequests?.toLocaleString() ?? '—', color: 'text-red-500', bg: 'bg-red-50' },
    { label: 'Avg Latency', value: summary?.totals.avgLatency ? `${Math.round(summary.totals.avgLatency)}ms` : '—', color: 'text-yellow-600', bg: 'bg-yellow-50' },
    { label: 'P99 Latency', value: summary?.totals.p99Latency ? `${summary.totals.p99Latency}ms` : '—', color: 'text-purple-600', bg: 'bg-purple-50' },
  ];

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Analytics</h1>
          <p className="text-gray-500 mt-0.5">Request traffic and performance metrics</p>
        </div>
        <div className="flex gap-3">
          <select className="input w-40" value={proxyId} onChange={(e) => setProxyId(e.target.value)}>
            <option value="">All proxies</option>
            {proxies.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
          <div className="flex rounded-lg overflow-hidden border border-gray-300">
            {(['1h', '24h', '7d', '30d'] as TimeRange[]).map((r) => (
              <button
                key={r}
                onClick={() => setRange(r)}
                className={`px-3 py-2 text-sm font-medium transition-colors ${
                  range === r ? 'bg-blue-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'
                }`}
              >
                {r}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-5 gap-4 mb-6">
        {statCards.map(({ label, value, color, bg }) => (
          <div key={label} className={`card p-4 ${bg} border-0`}>
            <div className="text-xs text-gray-500">{label}</div>
            <div className={`text-xl font-bold mt-1 ${color}`}>{loading ? '…' : value}</div>
          </div>
        ))}
      </div>

      {/* Charts grid */}
      <div className="grid grid-cols-2 gap-6">
        <div className="card p-5 col-span-2">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">Request Volume & Errors</h3>
          <ResponsiveContainer width="100%" height={220}>
            <AreaChart data={tsData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="time" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              <Legend />
              <Area type="monotone" dataKey="requests" stroke="#3b82f6" fill="#dbeafe" strokeWidth={2} name="Requests" />
              <Area type="monotone" dataKey="errors" stroke="#ef4444" fill="#fee2e2" strokeWidth={2} name="Errors" />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="card p-5">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">Avg Latency (ms)</h3>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={tsData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="time" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              <Line type="monotone" dataKey="latency" stroke="#10b981" strokeWidth={2} dot={false} name="Avg ms" />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="card p-5">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">Requests per Interval</h3>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={tsData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="time" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="requests" fill="#3b82f6" radius={[3, 3, 0, 0]} name="Requests" />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Status distribution */}
        <div className="card p-5 col-span-2">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">HTTP Status Distribution</h3>
          <div className="space-y-3">
            {(summary?.statusDistribution || []).map((d) => {
              const total = summary?.totals.totalRequests || 1;
              const pct = ((Number(d.count) / total) * 100).toFixed(1);
              const colorMap: Record<number, { bar: string; label: string }> = {
                200: { bar: 'bg-green-500', label: 'text-green-700' },
                300: { bar: 'bg-blue-400', label: 'text-blue-700' },
                400: { bar: 'bg-yellow-500', label: 'text-yellow-700' },
                500: { bar: 'bg-red-500', label: 'text-red-700' },
              };
              const colors = colorMap[Number(d.status_class)] || { bar: 'bg-gray-400', label: 'text-gray-700' };
              return (
                <div key={d.status_class} className="flex items-center gap-4">
                  <div className={`text-xs font-bold w-16 ${colors.label}`}>{d.status_class}–{Number(d.status_class) + 99}</div>
                  <div className="flex-1 h-3 bg-gray-100 rounded-full overflow-hidden">
                    <div className={`h-full ${colors.bar} rounded-full transition-all`} style={{ width: `${pct}%` }} />
                  </div>
                  <div className="text-xs text-gray-500 w-24 text-right">
                    {Number(d.count).toLocaleString()} ({pct}%)
                  </div>
                </div>
              );
            })}
            {!loading && !summary?.statusDistribution?.length && (
              <div className="text-center text-gray-400 text-sm py-4">No data for this period</div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
