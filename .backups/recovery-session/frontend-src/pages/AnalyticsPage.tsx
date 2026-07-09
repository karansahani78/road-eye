import React from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import api from '../services/api';

const AnalyticsPage: React.FC = () => {
  const { data: volumeData } = useQuery({
    queryKey: ['analytics-volume'],
    queryFn: () => api.get('/analytics/traffic-volume?period=7d'),
  });

  const { data: categoryData } = useQuery({
    queryKey: ['analytics-category'],
    queryFn: () => api.get('/analytics/vehicle-categories'),
  });

  const { data: violationTrends } = useQuery({
    queryKey: ['analytics-violations'],
    queryFn: () => api.get('/analytics/violation-trends?period=30d'),
  });

  const chartData = volumeData?.data?.data || [];
  const categoryChartData = categoryData?.data?.data || [];
  const violationChartData = violationTrends?.data?.data || [];

  const COLORS = ['#3b82f6', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899'];

  return (
    <div className="analytics-page">
      <h2>Traffic Analytics</h2>
      <div className="analytics-grid">
        <div className="chart-card">
          <h3>Traffic Volume (7 days)</h3>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="count" stroke="#3b82f6" name="Vehicles" />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="chart-card">
          <h3>Vehicle Categories</h3>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={categoryChartData}
                dataKey="count"
                nameKey="category"
                cx="50%"
                cy="50%"
                outerRadius={100}
                label
              >
                {categoryChartData.map((_: any, index: number) => (
                  <Cell key={index} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="chart-card full-width">
          <h3>Violation Trends (30 days)</h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={violationChartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Bar dataKey="overspeeding" fill="#ef4444" name="Overspeeding" />
              <Bar dataKey="other" fill="#f59e0b" name="Other" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
};

export default AnalyticsPage;
