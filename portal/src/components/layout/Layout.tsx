import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/auth';

const nav = [
  { to: '/dashboard', label: 'Dashboard', icon: '▤' },
  { to: '/apis', label: 'APIs', icon: '⊞' },
  { to: '/deploy', label: 'Deploy', icon: '⬆' },
  { to: '/proxies', label: 'Proxies', icon: '⇄' },
  { to: '/keys', label: 'API Keys', icon: '⚿' },
  { to: '/analytics', label: 'Analytics', icon: '⬡' },
];

export default function Layout() {
  const logout = useAuthStore((s) => s.logout);
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="w-64 bg-gray-900 text-white flex flex-col shadow-xl">
        <div className="px-6 py-5 border-b border-gray-700">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-blue-500 flex items-center justify-center font-bold text-sm">
              AP
            </div>
            <div>
              <div className="font-semibold text-sm">API Platform</div>
              <div className="text-gray-400 text-xs">Developer Portal</div>
            </div>
          </div>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1">
          {nav.map(({ to, label, icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-300 hover:bg-gray-800 hover:text-white'
                }`
              }
            >
              <span className="text-base">{icon}</span>
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="px-3 py-4 border-t border-gray-700">
          <div className="flex items-center gap-3 px-3 py-2">
            <div className="w-7 h-7 rounded-full bg-blue-500 flex items-center justify-center text-xs font-bold">
              {user?.name?.[0]?.toUpperCase() || 'U'}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium truncate">{user?.name || 'Dev User'}</div>
              <div className="text-xs text-gray-400 truncate">{user?.role || 'admin'}</div>
            </div>
            <button
              onClick={handleLogout}
              className="text-gray-400 hover:text-white text-xs px-2 py-1 rounded hover:bg-gray-700"
            >
              Out
            </button>
          </div>
        </div>
      </aside>

      {/* Main */}
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  );
}
