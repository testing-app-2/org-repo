import React, { useState, useEffect } from 'react';
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js';
import { Doughnut } from 'react-chartjs-2';
import { FiMenu, FiMoon, FiSun, FiUser, FiLogOut } from 'react-icons/fi';

ChartJS.register(ArcElement, Tooltip, Legend);

const dummyActivities = new Array(20).fill(0).map((_, idx) => ({
  id: idx + 1,
  activity: `User performed action ${idx + 1}`,
  timestamp: new Date().toISOString(),
}));

const Dashboard = () => {
  const [darkMode, setDarkMode] = useState(false);
  const [isSidebarOpen, setSidebarOpen] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(true);

  const toggleTheme = () => setDarkMode((prev) => !prev);
  const toggleSidebar = () => setSidebarOpen((prev) => !prev);

  useEffect(() => {
    setTimeout(() => {
      setActivities(dummyActivities);
      setLoading(false);
    }, 1000);
  }, []);

  const chartData = {
    labels: ['Completed', 'Pending', 'Failed'],
    datasets: [
      {
        label: 'Tasks',
        data: [65, 20, 15],
        backgroundColor: ['#10b981', '#fbbf24', '#ef4444'],
        borderWidth: 1,
      },
    ],
  };

  return (
    <div className={`${darkMode ? 'bg-gray-900 text-white' : 'bg-gray-100 text-gray-800'} min-h-screen flex`}>
      {/* Sidebar */}
      {isSidebarOpen && (
        <aside className="w-64 bg-white dark:bg-gray-800 shadow-lg p-4 space-y-6">
          <div className="text-2xl font-bold">MyApp</div>
          <nav className="flex flex-col space-y-4">
            <button className="text-left hover:text-indigo-600">🏠 Dashboard</button>
            <button className="text-left hover:text-indigo-600">📊 Analytics</button>
            <button className="text-left hover:text-indigo-600">⚙️ Settings</button>
          </nav>
        </aside>
      )}

      {/* Main */}
      <main className="flex-1">
        {/* Header */}
        <header className="flex items-center justify-between p-4 shadow-md bg-white dark:bg-gray-800">
          <div className="flex items-center space-x-4">
            <button onClick={toggleSidebar}>
              <FiMenu className="w-6 h-6" />
            </button>
            <h1 className="text-xl font-semibold">Dashboard</h1>
          </div>
          <div className="flex items-center space-x-4">
            <button onClick={toggleTheme}>
              {darkMode ? <FiSun className="w-5 h-5" /> : <FiMoon className="w-5 h-5" />}
            </button>
            <button onClick={() => setShowModal(true)}>
              <FiUser className="w-5 h-5" />
            </button>
            <button>
              <FiLogOut className="w-5 h-5" />
            </button>
          </div>
        </header>

        {/* Content */}
        <section className="p-6 space-y-10">
          {/* Overview */}
          <div>
            <h2 className="text-2xl font-bold mb-4">Overview</h2>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
              {['Users', 'Sessions', 'Conversions'].map((item, i) => (
                <div key={i} className="bg-white dark:bg-gray-700 p-4 rounded-xl shadow">
                  <p className="text-sm text-gray-500">{item}</p>
                  <p className="text-2xl font-semibold">{Math.floor(Math.random() * 1000)}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Analytics */}
          <div>
            <h2 className="text-2xl font-bold mb-4">Analytics</h2>
            <div className="max-w-sm">
              <Doughnut data={chartData} />
            </div>
          </div>

          {/* Recent Activities */}
          <div>
            <h2 className="text-2xl font-bold mb-4">Recent Activities</h2>
            {loading ? (
              <p>Loading...</p>
            ) : (
              <ul className="space-y-4 max-h-64 overflow-y-auto pr-2">
                {activities.map((a) => (
                  <li key={a.id} className="bg-white dark:bg-gray-700 p-4 rounded shadow text-sm">
                    <div className="font-medium">{a.activity}</div>
                    <div className="text-gray-500">{new Date(a.timestamp).toLocaleString()}</div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      </main>

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-lg w-96 relative">
            <h3 className="text-xl font-bold mb-4">User Profile</h3>
            <p className="mb-4">This is a sample user profile modal.</p>
            <button
              onClick={() => setShowModal(false)}
              className="absolute top-2 right-2 text-gray-500 hover:text-red-500"
            >
              ✖
            </button>
            <button
              onClick={() => alert('Profile updated!')}
              className="mt-4 bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700"
            >
              Save Changes
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default Dashboard;
