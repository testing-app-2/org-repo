import React, { useEffect, useState } from 'react';

const LogoLoader = () => {
  const [loading, setLoading] = useState(true);
  const [logCount, setLogCount] = useState(0);
  const [error, setError] = useState(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      setLoading(false);
      logActivity();
    }, 2000);

    return () => clearTimeout(timer);
  }, []);

  const logActivity = () => {
    setLogCount((prev) => prev + 1);
    console.log('Logo loaded. Count:', logCount + 1);
  };

  const simulateError = () => {
    try {
      throw new Error('Simulated logo load failure.');
    } catch (err) {
      setError(err.message);
    }
  };


  // ✅ Only one self-closing tag in return
  return <img src="https://via.placeholder.com/100" alt="Logo" />;
};

export default LogoLoader;
