import React, { useState, useMemo } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { Role } from '../types';

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: '◆' },
  { path: '/live', label: 'Live Monitor', icon: '▶' },
  { path: '/cameras', label: 'Cameras', icon: '◎' },
  { path: '/roads', label: 'Roads & Zones', icon: '▬' },
  { path: '/violations', label: 'Violations', icon: '⚑' },
  { path: '/incidents', label: 'Incidents', icon: '▲' },
  { path: '/alerts', label: 'Alerts', icon: '●' },
  { path: '/map', label: 'Traffic Map', icon: '◈' },
  { path: '/analytics', label: 'Analytics', icon: '▤' },
  { path: '/users', label: 'Users', icon: '◇', roles: ['SUPER_ADMIN', 'SYSTEM_ADMIN'] as Role[] },
  { path: '/audit-logs', label: 'Audit Logs', icon: '▧', roles: ['SUPER_ADMIN', 'SYSTEM_ADMIN', 'AUDITOR'] as Role[] },
  { path: '/system-health', label: 'System Health', icon: '⚙' },
];

const Layout: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(true);

  const visibleNavItems = useMemo(() => {
    if (!user) return navItems.filter((item) => !item.roles);
    return navItems.filter((item) => !item.roles || item.roles.includes(user.role));
  }, [user]);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="layout">
      <aside className={`sidebar ${sidebarOpen ? 'open' : 'closed'}`}>
        <div className="sidebar-header">
          <h1>RoadEye</h1>
          <span className="sidebar-subtitle">Traffic Surveillance</span>
        </div>
        <nav className="sidebar-nav">
          {visibleNavItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            >
              <span className="nav-icon">{item.icon}</span>
              <span className="nav-label">{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="main-content">
        <header className="top-bar">
          <button className="sidebar-toggle" onClick={() => setSidebarOpen(!sidebarOpen)}>
            ☰
          </button>
          <div className="top-bar-right">
            <span className="user-info">{user?.fullName || user?.username}</span>
            <span className="user-role">{user?.role?.replace('ROLE_', '')}</span>
            <button className="logout-btn" onClick={handleLogout}>Logout</button>
          </div>
        </header>
        <div className="content">
          <Outlet />
        </div>
      </main>
    </div>
  );
};

export default Layout;
