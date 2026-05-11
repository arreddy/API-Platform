import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  LineChart, Line, AreaChart, Area, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import { analyticsApi, apisApi, proxiesApi, keysApi } from '../api/client';
import { format } from 'date-fns';

interface Summary {
  totals: { totalRequests: number; successRequests: number; errorRequests: number; avgLatency: number; p99Latency: number };
  timeSeries: Array<{ hour: string; requests: number; avg_latency: number; errors: number }>;
  statusDistribution: Array<{ status_class: number; count: number }>;
}

interface Counts { apis: number; proxies: number; keys: number }

export default function Dashboard() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [counts, setCounts] = useState<Counts>({ apis: 0, proxies: 0, keys: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.allSettled([
      analyticsApi.summary({ from: new Date(Date.now() - 24 * 3600_000).toISOString() }),
      apisApi.list({ limit: 1 }),
      proxiesApi.list({ limit: 1 }),
      keysApi.list({ limit: 1 }),
    ]).then(([summaryRes, apisRes, proxiesRes, keysRes]) => {
      if (summaryRes.status === 'fulfilled') setSummary(summaryRes.value.data);
      setCounts({
        apis: apisRes.status === 'fulfilled' ? apisRes.value.data.total : 0,
        proxies: proxiesRes.status === 'fulfilled' ? proxiesRes.value.data.total : 0,
        keys: keysRes.status === 'fulfilled' ? keysRes.value.data.total : 0,
      });
      setLoading(false);
    });
  }, []);

  const stats = [
    { label: 'Total Requests (24h)', value: summary?.totals.totalRequests?.toLocaleString() ?? '—', color: 'text-blue-600' },
    { label: 'Error Rate', value: summary?.totals.totalRequests ? `${((summary.totals.errorRequests / summary.totals.totalRequests) * 100).toFixed(1)}%` : '—', color: 'text-red-500' },
    { label: 'Avg Latency', value: summary?.totals.avgLatency ? `${Math.round(summary.totals.avgLatency)}ms` : '—', color: 'text-green-600' },
    { label: 'P99 Latency', value: summary?.totals.p99Latency ? `${summary.totals.p99Latency}ms` : '—', color: 'text-yellow-600' },
  ];

  const resources = [
    { label: 'Registered APIs', count: counts.apis, to: '/apis', color: 'bg-blue-50 border-blue-200' },
    { label: 'Active Proxies', count: counts.proxies, to: '/proxies', color: 'bg-green-50 border-green-200' },
    { label: 'API Keys', count: counts.keys, to: '/keys', color: 'bg-purple-50 border-purple-200' },
  ];

  const tsData = (summary?.timeSeries || []).map((d) => ({
    ...d,
    hour: format(new Date(d.hour), 'HH:mm'),
    requests: Number(d.requests),
    avg_latency: Number(d.avg_latency),
    errors: Number(d.errors),
  }));

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500 mt-1">Platform overview — last 24 hours</p>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-4 gap-4 mb-8">
        {stats.map(({ label, value, color }) => (
          <div key={label} className="card p-5">
            <div className="text-sm text-gray-500">{label}</div>
            <div className={`text-2xl font-bold mt-1 ${color}`}>{loading ? '…' : value}</div>
          </div>
        ))}
      </div>

      {/* Resource counts */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        {resources.map(({ label, count, to, color }) => (
          <Link key={to} to={to} className={`card p-5 border ${color} hover:shadow-md transition-shadow`}>
            <div className="text-3xl font-bold text-gray-900">{loading ? '…' : count}</div>
            <div className="text-sm text-gray-600 mt-1">{label}</div>
            <div className="text-blue-600 text-xs mt-2 font-medium">View all →</div>
          </Link>
        ))}
      </div>

      {/* Charts */}
      {tsData.length > 0 && (
        <div className="grid grid-cols-2 gap-6">
          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Request Volume</h3>
            <ResponsiveContainer width="100%" height={200}>
              <AreaChart data={tsData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="hour" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Area type="monotone" dataKey="requests" stroke="#3b82f6" fill="#dbeafe" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Avg Latency (ms)</h3>
            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={tsData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="hour" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Line type="monotone" dataKey="avg_latency" stroke="#10b981" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Errors per Hour</h3>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={tsData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="hour" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Bar dataKey="errors" fill="#ef4444" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <div className="card p-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Status Distribution</h3>
            <div className="space-y-3 pt-2">
              {(summary?.statusDistribution || []).map((d) => {
                const total = summary!.totals.totalRequests || 1;
                const pct = ((Number(d.count) / total) * 100).toFixed(1);
                const colorMap: Record<number, string> = { 200: 'bg-green-500', 300: 'bg-blue-400', 400: 'bg-yellow-500', 500: 'bg-red-500' };
                const color = colorMap[d.status_class] || 'bg-gray-400';
                return (
                  <div key={d.status_class}>
                    <div className="flex justify-between text-xs text-gray-600 mb-1">
                      <span>{d.status_class}–{d.status_class + 99}</span>
                      <span>{Number(d.count).toLocaleString()} ({pct}%)</span>
                    </div>
                    <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                      <div className={`h-full ${color} rounded-full`} style={{ width: `${pct}%` }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {tsData.length === 0 && !loading && (
        <div className="card p-12 text-center text-gray-400">
          <div className="text-4xl mb-3">📊</div>
          <p className="text-sm">No traffic data yet. Route some requests through the gateway to see analytics.</p>
        </div>
      )}
    </div>
  );
}
