// Auto-load DemoService if not already in document
(function ensureDemoService() {
  if (typeof window !== 'undefined' && !window.DemoService) {
    try {
      const existing = document.querySelector('script[src*="demo-service.js"]');
      if (!existing && document.head) {
        const s = document.createElement('script');
        const p = window.location.pathname;
        const prefix = (p.includes('/customer/') || p.includes('/supervisor/') || p.includes('/officer/') || p.includes('/manager/') || p.includes('/qa/')) ? '../' : '';
        s.src = `${prefix}js/demo-service.js`;
        s.async = false;
        document.head.appendChild(s);
      }
    } catch (e) {}
  }
})();

/**
 * SupportHUB Enterprise Platform - Centralized API Client
 *
 * Configurable API Base URL resolution:
 * 1. window.__SUPPORT_HUB_API_URL__ (runtime global override injected by hosting environment or config script)
 * 2. localStorage.getItem('supporthub_api_url') (explicit admin/developer manual override via browser storage)
 * 3. Default fallback: 'http://localhost:8080/api' for local development
 */
function resolveApiBaseUrl() {
  if (typeof window !== 'undefined') {
    if (window.__SUPPORT_HUB_API_URL__ && typeof window.__SUPPORT_HUB_API_URL__ === 'string') {
      return window.__SUPPORT_HUB_API_URL__.trim().replace(/\/+$/, '');
    }
    try {
      const stored = localStorage.getItem('supporthub_api_url');
      if (stored && typeof stored === 'string' && stored.trim().length > 0) {
        return stored.trim().replace(/\/+$/, '');
      }
    } catch (e) {
      // Storage access may be restricted in private/sandboxed contexts
    }
  }
  return 'http://localhost:8080/api';
}

const API_BASE_URL = resolveApiBaseUrl();

function getAppBasePath() {
  const path = window.location.pathname;
  if (path.includes('/frontend/')) {
    return path.substring(0, path.indexOf('/frontend/') + 9);
  }
  return '';
}

const API = {
  getBaseUrl() {
    return resolveApiBaseUrl();
  },

  setBackendUrl(url) {
    if (typeof window !== 'undefined' && url) {
      try {
        localStorage.setItem('supporthub_api_url', url.trim().replace(/\/+$/, ''));
      } catch (e) {
        console.warn('Unable to persist API URL to localStorage:', e);
      }
    }
  },

  async request(endpoint, options = {}) {
    const token = localStorage.getItem('jwt_token');
    
    const headers = {
      'Accept': 'application/json',
      ...options.headers
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    if (!(options.body instanceof FormData) && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }

    const config = {
      ...options,
      headers
    };

    // 1. Direct Demo Mode Interception (100% frontend-only mock engine)
    if (typeof DemoService === 'undefined' && typeof window !== 'undefined' && !window.DemoService) {
      const isLikelyDemo = (token && token.startsWith('demo-')) || localStorage.getItem('supporthub_demo_mode') === 'true';
      if (isLikelyDemo) {
        for (let i = 0; i < 5; i++) {
          await new Promise(r => setTimeout(r, 50));
          if (window.DemoService) break;
        }
      }
    }
    const activeDemo = (typeof DemoService !== 'undefined') ? DemoService : (typeof window !== 'undefined' ? window.DemoService : null);
    if (activeDemo && activeDemo.canIntercept(endpoint)) {
      return await activeDemo.handleRequest(endpoint, options);
    }

    try {
      const baseUrl = (this.getBaseUrl ? this.getBaseUrl() : API_BASE_URL);
      const response = await fetch(`${baseUrl}${endpoint}`, config);
      let data = {};
      try {
        data = await response.json();
      } catch (e) {
        data = { message: response.statusText || `HTTP ${response.status}` };
      }

      if (response.status === 401) {
        if (activeDemo && (token?.startsWith('demo-') || localStorage.getItem('supporthub_demo_mode') === 'true')) {
          return await activeDemo.handleRequest(endpoint, options);
        }
        localStorage.removeItem('jwt_token');
        localStorage.removeItem('user_info');
        if (!window.location.pathname.endsWith('login.html') && !window.location.pathname.endsWith('index.html')) {
          window.location.href = `${getAppBasePath()}/login.html?expired=true`;
        }
        throw new Error(data.message || 'Session expired. Please log in again.');
      }

      if (response.status === 403) {
        // Fall back to DemoService if demo session is active
        if (activeDemo && (token?.startsWith('demo-') || localStorage.getItem('supporthub_demo_mode') === 'true')) {
          return await activeDemo.handleRequest(endpoint, options);
        }
        if (!window.location.pathname.endsWith('unauthorized.html')) {
          window.location.href = `${getAppBasePath()}/unauthorized.html`;
        }
        throw new Error(data.message || 'Access Denied');
      }

      if (!response.ok || data.success === false) {
        throw new Error(data.message || `HTTP Error ${response.status}`);
      }

      return data;
    } catch (err) {
      // If live backend server is unreachable (e.g. static Vercel deployment), fall back gracefully to DemoService
      if (activeDemo && (err.name === 'TypeError' || (err.message && (err.message.includes('fetch') || err.message.includes('NetworkError') || err.message.includes('Access Denied'))))) {
        try {
          return await activeDemo.handleRequest(endpoint, options);
        } catch (demoErr) {
          throw demoErr;
        }
      }
      console.error(`API Error [${endpoint}]:`, err);
      throw err;
    }
  },

  get(endpoint) {
    return this.request(endpoint, { method: 'GET' });
  },

  post(endpoint, body) {
    return this.request(endpoint, {
      method: 'POST',
      body: body instanceof FormData ? body : JSON.stringify(body)
    });
  },

  postFormData(endpoint, formData) {
    return this.request(endpoint, {
      method: 'POST',
      body: formData
    });
  },

  put(endpoint, body) {
    return this.request(endpoint, {
      method: 'PUT',
      body: body instanceof FormData ? body : JSON.stringify(body)
    });
  },

  patch(endpoint, body) {
    return this.request(endpoint, {
      method: 'PATCH',
      body: body ? (body instanceof FormData ? body : JSON.stringify(body)) : null
    });
  },

  delete(endpoint) {
    return this.request(endpoint, { method: 'DELETE' });
  },

  getFileUrl(fileUrl) {
    if (!fileUrl) return '#';
    if (fileUrl.startsWith('blob:') || fileUrl.startsWith('data:')) {
      return fileUrl;
    }
    const activeApi = (this.getBaseUrl ? this.getBaseUrl() : API_BASE_URL);
    const baseUrl = activeApi.replace(/\/api\/?$/, '');
    const cleanPath = fileUrl.startsWith('/') ? fileUrl : `/${fileUrl}`;
    const token = (typeof Auth !== 'undefined' && Auth.getToken) ? Auth.getToken() : null;
    const separator = cleanPath.includes('?') ? '&' : '?';
    const authQuery = token ? `${separator}token=${encodeURIComponent(token)}` : '';
    if (fileUrl.startsWith('http://') || fileUrl.startsWith('https://')) {
      return (token && !fileUrl.includes('token=')) ? `${fileUrl}${authQuery}` : fileUrl;
    }
    return `${baseUrl}${cleanPath}${authQuery}`;
  }
};
