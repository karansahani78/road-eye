import React, { useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: '📊' },
  { path: '/cameras', label: 'Cameras', icon: '📷' },
  { path: '/roads', label: 'Roads & Zones', icon: '🛣️' },
  { path: '/violations', label: 'Violations', icon: '🚨' },
  { path: '/alerts', label: 'Alerts', icon: '🔔' },
  { path: '/map', label: 'Traffic Map', icon: '🗺️' },
  { path: '/analytics', label: 'Analytics', icon: '📈' },
  { path: '/users', label: 'Users', icon: '👥' },
  { path: '/audit-logs', label: 'Audit Logs', icon: '📋' },
  { path: '/system-health', label: 'System Health', icon: '⚙️' },
];

const Layout: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(true);

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
          {navItems.map((item) => (
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
            <span className="user-role">{user?.role}</span>
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
