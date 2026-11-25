/**
 * SupportHUB Enterprise Platform - Authentication & RBAC Guard
 */
const Auth = {
  getToken() {
    return localStorage.getItem('jwt_token');
  },

  getUser() {
    const raw = localStorage.getItem('user_info');
    return raw ? JSON.parse(raw) : null;
  },

  isLoggedIn() {
    return !!this.getToken();
  },

  setSession(token, user) {
    localStorage.setItem('jwt_token', token);
    localStorage.setItem('user_info', JSON.stringify(user));
  },

  getBasePath() {
    const path = window.location.pathname;
    if (path.includes('/frontend/')) {
      return path.substring(0, path.indexOf('/frontend/') + 9);
    }
    return '';
  },

  getRoleDashboard(role) {
    switch (role) {
      case 'CUSTOMER':
        return '/customer/customer-dashboard.html';
      case 'CUSTOMER_SERVICE_OFFICER':
        return '/officer/officer-dashboard.html';
      case 'OPERATIONS_SUPERVISOR':
        return '/supervisor/supervisor-dashboard.html';
      case 'CUSTOMER_SUPPORT_MANAGER':
        return '/manager/manager-dashboard.html';
      case 'QA_EXECUTIVE':
      case 'QUALITY_ASSURANCE':
        return '/qa/qa-dashboard.html';
      default:
        return '/index.html';
    }
  },

  logout() {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('user_info');
    localStorage.removeItem('supporthub_demo_mode');
    window.location.href = `${this.getBasePath()}/login.html`;
  },

  requireRole(allowedRoles) {
    if (!this.isLoggedIn()) {
      window.location.href = `${this.getBasePath()}/login.html`;
      return false;
    }

    const user = this.getUser();
    if (!user) {
      window.location.href = `${this.getBasePath()}/login.html`;
      return false;
    }

    const currentPath = window.location.pathname;

    // Specifically block Supervisor from accessing Manager Overview and QA Dashboard as requested
    if (user.role === 'OPERATIONS_SUPERVISOR') {
      if (currentPath.includes('manager-dashboard.html') || currentPath.includes('qa-dashboard.html')) {
        window.location.href = `${this.getBasePath()}/supervisor/supervisor-dashboard.html`;
        return false;
      }
      return true;
    }

    if (allowedRoles) {
      const roles = Array.isArray(allowedRoles) ? [...allowedRoles] : [allowedRoles];
      if (roles.includes('QA_EXECUTIVE') && !roles.includes('QUALITY_ASSURANCE')) roles.push('QUALITY_ASSURANCE');
      if (roles.includes('QUALITY_ASSURANCE') && !roles.includes('QA_EXECUTIVE')) roles.push('QA_EXECUTIVE');
      if (!roles.includes(user.role)) {
        const roleDashboard = this.getRoleDashboard(user.role);
        if (roleDashboard && !currentPath.includes(roleDashboard)) {
          window.location.href = `${this.getBasePath()}${roleDashboard}`;
          return false;
        }
        window.location.href = `${this.getBasePath()}/unauthorized.html`;
        return false;
      }
    }
    return true;
  },

  requireAuth(allowedRoles) {
    if (!this.isLoggedIn()) {
      window.location.href = `${this.getBasePath()}/login.html`;
      return null;
    }

    const user = this.getUser();
    if (!user) {
      window.location.href = `${this.getBasePath()}/login.html`;
      return null;
    }

    if (allowedRoles) {
      const ok = this.requireRole(allowedRoles);
      if (!ok) return null;
    }
    return user;
  },

  renderNav() {
    this.renderSidebar();
    this.renderUserHeader();
  },

  renderUserHeader() {
    const user = this.getUser();
    if (!user) return;

    const nameEl = document.getElementById('header-user-name');
    const roleEl = document.getElementById('header-user-role');
    const avatarEl = document.getElementById('header-user-avatar');

    if (nameEl) nameEl.textContent = user.fullName || user.email;
    if (roleEl) roleEl.textContent = user.role.replaceAll('_', ' ');
    if (avatarEl) {
      const initials = (user.fullName || user.email).substring(0, 2).toUpperCase();
      avatarEl.textContent = initials;
    }
  },

  handleNotificationsClick(e) {
    if (e) e.preventDefault();
    const bell = document.getElementById('notif-bell');
    if (bell) {
      bell.click();
    } else {
      window.location.href = `${this.getBasePath()}/customer/notifications.html`;
    }
  },

  renderSidebar() {
    const user = this.getUser();
    const navContainer = document.getElementById('sidebar-nav');
    if (!navContainer) return;

    const basePath = this.getBasePath();
    const currentPath = window.location.pathname;

    let links = [];

    if (!user) {
      links = [
        { label: 'Home', url: '/index.html', icon: '<i class="fa-solid fa-house"></i>' },
        { label: 'Knowledge Base', url: '/customer/knowledge-base.html', icon: '<i class="fa-solid fa-book-open-reader"></i>' },
        { label: 'Sign In', url: '/login.html', icon: '<i class="fa-solid fa-right-to-bracket"></i>' },
        { label: 'Register', url: '/register.html', icon: '<i class="fa-solid fa-user-plus"></i>' }
      ];
    } else {
      switch (user.role) {
        case 'CUSTOMER':
          links = [
            { label: 'Dashboard', url: '/customer/customer-dashboard.html', icon: '<i class="fa-solid fa-gauge-high"></i>' },
            { label: 'Help Center', url: '/knowledge-base.html', icon: '<i class="fa-solid fa-compass"></i>' },
            { label: 'Knowledge Base', url: '/customer/knowledge-base.html', icon: '<i class="fa-solid fa-book-open-reader"></i>' },
            { label: 'FAQs', url: '/customer/faq.html', icon: '<i class="fa-solid fa-circle-question"></i>' },
            { label: 'My Tickets', url: '/customer/my-tickets.html', icon: '<i class="fa-solid fa-ticket-simple"></i>' },
            { label: 'Notifications', url: '/customer/notifications.html', icon: '<i class="fa-solid fa-bell"></i>' },
            { label: 'My Profile', url: '/customer/customer-profile.html', icon: '<i class="fa-solid fa-user-gear"></i>' },
            { label: 'Sign Out', url: '#signout', icon: '<i class="fa-solid fa-right-from-bracket"></i>', onClick: 'Auth.logout()' }
          ];
          break;

        case 'CUSTOMER_SERVICE_OFFICER':
          links = [
            { section: 'OFFICER WORKSPACE' },
            { label: 'Dashboard', url: '/officer/officer-dashboard.html', icon: '<i class="fa-solid fa-chart-line"></i>' },
            { label: 'Assigned Queue', url: '/officer/assigned-tickets.html', icon: '<i class="fa-solid fa-list-check"></i>' },
            { label: 'Knowledge Base', url: '/customer/knowledge-base.html', icon: '<i class="fa-solid fa-book-bookmark"></i>' },
            { label: 'My Profile', url: '/officer/officer-profile.html', icon: '<i class="fa-solid fa-user-gear"></i>' },
            { label: 'Sign Out', url: '#signout', icon: '<i class="fa-solid fa-right-from-bracket"></i>', onClick: 'Auth.logout()' }
          ];
          break;

        case 'OPERATIONS_SUPERVISOR':
          links = [
            { section: 'OPERATIONS CONTROL' },
            { label: 'Supervisor Dashboard', url: '/supervisor/supervisor-dashboard.html', icon: '<i class="fa-solid fa-gauge-high"></i>' },
            { label: 'Team Tickets Queue', url: '/supervisor/team-tickets.html', icon: '<i class="fa-solid fa-list-check"></i>' },
            { label: 'Escalations Queue', url: '/supervisor/escalations.html', icon: '<i class="fa-solid fa-triangle-exclamation"></i>' },
            { label: 'SLA Monitoring', url: '/supervisor/sla-monitoring.html', icon: '<i class="fa-solid fa-stopwatch"></i>' },

            { section: 'STAFF & CATEGORIES' },
            { label: 'Support Officers', url: '/supervisor/agents.html', icon: '<i class="fa-solid fa-users-gear"></i>' },
            { label: 'Ticket Categories', url: '/supervisor/categories.html', icon: '<i class="fa-solid fa-folder-tree"></i>' },
            { label: 'Customer Accounts', url: '/supervisor/customers.html', icon: '<i class="fa-solid fa-users"></i>' },

            { section: 'ANALYTICS & KNOWLEDGE' },
            { label: 'Operational Reports', url: '/supervisor/reports.html', icon: '<i class="fa-solid fa-chart-pie"></i>' },
            { label: 'Knowledge Base', url: '/customer/knowledge-base.html', icon: '<i class="fa-solid fa-book-bookmark"></i>' },
            { label: 'Sign Out', url: '#signout', icon: '<i class="fa-solid fa-right-from-bracket"></i>', onClick: 'Auth.logout()' }
          ];
          break;

        case 'CUSTOMER_SUPPORT_MANAGER':
          links = [
            { section: 'EXECUTIVE & OPERATIONS' },
            { label: 'Executive Dashboard', url: '/manager/manager-dashboard.html', icon: '<i class="fa-solid fa-gauge-high"></i>' },
            { label: 'Team Performance', url: '/manager/team-performance.html', icon: '<i class="fa-solid fa-users-viewfinder"></i>' },
            { label: 'Team Tickets Queue', url: '/supervisor/team-tickets.html', icon: '<i class="fa-solid fa-list-check"></i>' },
            { label: 'Support Officers', url: '/supervisor/agents.html', icon: '<i class="fa-solid fa-user-tie"></i>' },
            { label: 'Customer Accounts', url: '/supervisor/customers.html', icon: '<i class="fa-solid fa-users"></i>' },

            { section: 'POLICIES & CONFIG' },
            { label: 'SLA & Priority Policies', url: '/manager/sla-management.html', icon: '<i class="fa-solid fa-stopwatch-20"></i>' },
            { label: 'Ticket Categories', url: '/manager/category-management.html', icon: '<i class="fa-solid fa-folder-tree"></i>' },
            { label: 'Service Configuration', url: '/manager/service-configuration.html', icon: '<i class="fa-solid fa-gear"></i>' },

            { section: 'KNOWLEDGE & CONTENT' },
            { label: 'Knowledge Base', url: '/manager/knowledge-base-management.html', icon: '<i class="fa-solid fa-book-bookmark"></i>' },
            { label: 'FAQs Management', url: '/manager/faqs-management.html', icon: '<i class="fa-solid fa-circle-question"></i>' },

            { section: 'ANALYTICS & AUDIT' },
            { label: 'Management Reports', url: '/manager/management-reports.html', icon: '<i class="fa-solid fa-file-invoice"></i>' },
            { label: 'CSAT Analytics', url: '/manager/customer-satisfaction.html', icon: '<i class="fa-solid fa-heart-pulse"></i>' },
            { label: 'Self-Service Analytics', url: '/manager/self-service-analytics.html', icon: '<i class="fa-solid fa-magnifying-glass-chart"></i>' },
            { label: 'Audit Logs', url: '/manager/audit-logs.html', icon: '<i class="fa-solid fa-clipboard-check"></i>' },

            { label: 'Sign Out', url: '#signout', icon: '<i class="fa-solid fa-right-from-bracket"></i>', onClick: 'Auth.logout()' }
          ];
          break;

        case 'QA_EXECUTIVE':
        case 'QUALITY_ASSURANCE':
          links = [
            { section: 'QUALITY OVERSIGHT' },
            { label: 'QA Dashboard', url: '/qa/qa-dashboard.html', icon: '<i class="fa-solid fa-chart-pie"></i>' },
            { label: 'Review Queue', url: '/qa/ticket-reviews.html', icon: '<i class="fa-solid fa-list-check"></i>' },
            { label: 'Evaluation Scorecards', url: '/qa/evaluation-scorecard.html', icon: '<i class="fa-solid fa-clipboard-check"></i>' },

            { section: 'STANDARDS & COACHING' },
            { label: 'Quality Issues', url: '/qa/quality-issues.html', icon: '<i class="fa-solid fa-triangle-exclamation"></i>' },
            { label: 'Officer Performance', url: '/qa/officer-quality.html', icon: '<i class="fa-solid fa-user-shield"></i>' },
            { label: 'Coaching Feedback', url: '/qa/feedback-management.html', icon: '<i class="fa-solid fa-comment-dots"></i>' },

            { section: 'SATISFACTION & REPORTS' },
            { label: 'CSAT vs Quality', url: '/qa/customer-satisfaction.html', icon: '<i class="fa-solid fa-star-half-stroke"></i>' },
            { label: 'Feedback Review Log', url: '/qa/feedback-review.html', icon: '<i class="fa-solid fa-comments"></i>' },
            { label: 'CSAT Analytics', url: '/qa/satisfaction-analysis.html', icon: '<i class="fa-solid fa-chart-column"></i>' },
            { label: 'Quality Reports', url: '/qa/qa-reports.html', icon: '<i class="fa-solid fa-chart-line"></i>' },

            { section: 'GOVERNANCE & ACCOUNT' },
            { label: 'QA Audit History', url: '/qa/qa-audit-history.html', icon: '<i class="fa-solid fa-shield-halved"></i>' },
            { label: 'QA Profile', url: '/qa/qa-profile.html', icon: '<i class="fa-solid fa-id-card"></i>' },

            { label: 'Sign Out', url: '#signout', icon: '<i class="fa-solid fa-right-from-bracket"></i>', onClick: 'Auth.logout()' }
          ];
          break;

        default:
          break;
      }
    }

    // Ensure top sidebar header is cleared and hidden since SupportHUB brand logo is placed in top header
    const sidebarHeader = document.querySelector('.sidebar-header');
    if (sidebarHeader) {
      sidebarHeader.style.display = 'none';
      sidebarHeader.innerHTML = '';
    }

    // Filter out Sign Out from main navigation body to anchor at bottom of capsule
    const mainNavLinks = links.filter(l => l.url !== '#signout' && l.label !== 'Sign Out');
    const hasSignOut = links.some(l => l.url === '#signout' || l.label === 'Sign Out');

    let isFirstSection = true;
    const navItemsHtml = mainNavLinks.map(link => {
      if (link.section) {
        if (isFirstSection) {
          isFirstSection = false;
          return ''; // No divider above very first section
        }
        return `<div class="sidebar-section-divider" title="${link.section}"></div>`;
      }
      isFirstSection = false;
      const isHash = link.url.startsWith('#') || link.url.startsWith('javascript:');
      const fullUrl = isHash ? link.url : `${basePath}${link.url}`;
      const isActive = !isHash && currentPath.endsWith(link.url);
      const clickAttr = link.onClick ? `onclick="${link.onClick}; return false;"` : '';
      return `
        <a href="${fullUrl}" class="nav-item ${isActive ? 'active' : ''}" ${clickAttr} aria-label="${link.label}">
          <span class="nav-icon">${link.icon}</span>
          ${isActive ? '' : `<span class="nav-tooltip">${link.label}</span>`}
          ${isActive ? '<span class="active-indicator-dot"></span>' : ''}
        </a>
      `;
    }).join('');

    // Set navigation HTML without bottom logout in sidebar
    navContainer.innerHTML = navItemsHtml;

    // Remove user profile avatar from sidebar as requested
    const existingSidebarFooter = document.getElementById('sidebar-user-footer');
    if (existingSidebarFooter) {
      existingSidebarFooter.remove();
    }
    const legacySidebarFooter = document.querySelector('.sidebar-footer');
    if (legacySidebarFooter) {
      legacySidebarFooter.remove();
    }

    // Automatically ensure SupportHUB Brand Logo Badge exists in top-header or topbar
    const headerLeft = document.querySelector('.top-header .header-left, .topbar .topbar-left, .header-left, .topbar-left');
    if (headerLeft && !headerLeft.querySelector('.header-brand-pill')) {
      const legacyCapsule = headerLeft.querySelector('.page-title-capsule');
      if (legacyCapsule) legacyCapsule.remove();
      const legacyToggle = headerLeft.querySelector('.btn-menu-toggle');
      if (legacyToggle) legacyToggle.remove();

      const dashboardUrl = this.getDashboardUrl(user);
      const brandPill = document.createElement('a');
      brandPill.href = dashboardUrl;
      brandPill.className = 'header-brand-pill';
      brandPill.title = 'SupportHUB Portal';
      brandPill.innerHTML = `
        <img src="${basePath}/images/supporthub-logo.png" alt="SupportHUB" class="header-logo-img">
      `;
      headerLeft.insertBefore(brandPill, headerLeft.firstChild);
    }

    // Auto initialize SupportHUB Virtual Support Assistant for Customers
    if (user && user.role === 'CUSTOMER') {
      if (typeof ChatbotAssistant !== 'undefined') {
        ChatbotAssistant.init();
      } else {
        const existingScript = document.querySelector('script[src*="chatbot.js"]');
        if (!existingScript) {
          const script = document.createElement('script');
          script.src = `${basePath}/js/chatbot.js`;
          script.onload = () => {
            if (typeof ChatbotAssistant !== 'undefined') {
              ChatbotAssistant.init();
            }
          };
          document.head.appendChild(script);
        }
      }
    }

    // Automatically render header profile capsule if present
    this.renderHeaderProfileCapsule();

    // Automatically bind Logo Navigation across sidebar and top-nav
    this.initLogoNavigation();
  },

  getDashboardUrl(user) {
    const u = user || this.getUser();
    const basePath = this.getBasePath();
    if (!u) return `${basePath}/index.html`;

    switch (u.role) {
      case 'CUSTOMER':
        return `${basePath}/customer/customer-dashboard.html`;
      case 'CUSTOMER_SERVICE_OFFICER':
        return `${basePath}/officer/officer-dashboard.html`;
      case 'OPERATIONS_SUPERVISOR':
        return `${basePath}/supervisor/supervisor-dashboard.html`;
      case 'CUSTOMER_SUPPORT_MANAGER':
        return `${basePath}/manager/manager-dashboard.html`;
      case 'QA_EXECUTIVE':
      case 'QUALITY_ASSURANCE':
        return `${basePath}/qa/qa-dashboard.html`;
      default:
        return `${basePath}/index.html`;
    }
  },

  getProfileUrl(user) {
    const u = user || this.getUser();
    const basePath = this.getBasePath();
    if (!u) return `${basePath}/index.html`;

    switch (u.role) {
      case 'CUSTOMER':
        return `${basePath}/customer/customer-profile.html`;
      case 'CUSTOMER_SERVICE_OFFICER':
        return `${basePath}/officer/officer-profile.html`;
      case 'OPERATIONS_SUPERVISOR':
        return `${basePath}/supervisor/supervisor-profile.html`;
      case 'CUSTOMER_SUPPORT_MANAGER':
        return `${basePath}/manager/manager-profile.html`;
      case 'QA_EXECUTIVE':
      case 'QUALITY_ASSURANCE':
        return `${basePath}/qa/qa-profile.html`;
      default:
        return `${basePath}/index.html`;
    }
  },

  renderHeaderProfileCapsule() {
    const capsule = document.getElementById('header-profile-capsule');
    if (!capsule) return;
    const user = this.getUser();
    if (!user) {
      capsule.innerHTML = '';
      return;
    }
    const profileUrl = this.getProfileUrl(user);
    const roleMap = {
      'CUSTOMER_SUPPORT_MANAGER': 'Support Manager',
      'OPERATIONS_SUPERVISOR': 'Supervisor',
      'CUSTOMER_SERVICE_OFFICER': 'Support Officer',
      'QA_EXECUTIVE': 'QA Executive',
      'QUALITY_ASSURANCE': 'QA Executive',
      'CUSTOMER': 'Customer'
    };
    const displayRole = roleMap[user.role] || user.role.replaceAll('_', ' ');

    capsule.innerHTML = `
      <a href="${profileUrl}" class="header-user-icon-btn" title="${user.fullName || user.email} (${displayRole})" aria-label="User Profile" style="display: inline-flex; align-items: center; justify-content: center; text-decoration: none; cursor: pointer;">
        <div class="header-user-avatar-circle" style="width: 42px; height: 42px; border-radius: 50%; background: linear-gradient(135deg, #000985 0%, #050b2e 100%); border: 2px solid #F5CC00; color: #F5CC00; display: flex; align-items: center; justify-content: center; font-size: 1.15rem; box-shadow: 0 4px 12px rgba(0, 9, 133, 0.25); position: relative; transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);">
          <i class="fa-solid fa-user"></i>
          <span style="position: absolute; bottom: 1px; right: 1px; width: 9px; height: 9px; background: #10b981; border: 2px solid #ffffff; border-radius: 50%; box-shadow: 0 0 4px #10b981;"></span>
        </div>
      </a>
    `;
  },

  handleLogoClick(e) {
    if (e) e.preventDefault();
    const basePath = this.getBasePath();
    const currentPath = window.location.pathname;
    const isHomePage = currentPath.endsWith('/index.html') || currentPath.endsWith('/') || currentPath.endsWith('/frontend') || currentPath.endsWith('/frontend/');
    const isInternalPortal = currentPath.includes('/customer/') || currentPath.includes('/officer/') || currentPath.includes('/supervisor/') || currentPath.includes('/manager/') || currentPath.includes('/qa/');

    if (isHomePage) {
      if (this.isLoggedIn()) {
        window.location.href = this.getDashboardUrl();
      } else {
        window.location.href = `${basePath}/index.html`;
      }
    } else if (isInternalPortal && this.isLoggedIn()) {
      // Inside internal dashboards -> clicking brand navigates to role dashboard root
      window.location.href = this.getDashboardUrl();
    } else {
      // If on public or auth pages -> clicking the logo takes user to Home Page
      window.location.href = `${basePath}/index.html`;
    }
  },

  initLogoNavigation() {
    const basePath = this.getBasePath();
    const currentPath = window.location.pathname;
    const isHomePage = currentPath.endsWith('/index.html') || currentPath.endsWith('/') || currentPath.endsWith('/frontend') || currentPath.endsWith('/frontend/');
    const isInternalPortal = currentPath.includes('/customer/') || currentPath.includes('/officer/') || currentPath.includes('/supervisor/') || currentPath.includes('/manager/') || currentPath.includes('/qa/');
    const user = this.getUser();
    const isLoggedIn = this.isLoggedIn();

    // Ensure all logo anchors render the official SupportHUB logo photo
    const publicLogos = document.querySelectorAll('.nav-logo, .brand-logo, .hc-nav-logo');
    publicLogos.forEach(pl => {
      if (!pl.querySelector('img') && !pl.classList.contains('header-brand-pill') && !pl.closest('.sidebar-header')) {
        pl.innerHTML = `<img src="${basePath}/images/supporthub-logo.png" alt="SupportHUB" class="brand-logo-img">`;
      }
    });

    // Select all logo elements on the page (monograms, full brand logos, and header pills)
    const logos = document.querySelectorAll('.brand-logo, .nav-logo, .hc-nav-logo, .sidebar-header .brand-logo, .sidebar-header, .sidebar-brand-mark, .header-brand-pill');

    logos.forEach(logoEl => {
      let target = logoEl;
      if (logoEl.classList.contains('sidebar-header')) {
        target = logoEl.querySelector('.sidebar-brand-mark') || logoEl.querySelector('.brand-logo') || logoEl;
      }
      target.style.cursor = 'pointer';

      if (isHomePage) {
        if (isLoggedIn) {
          target.setAttribute('title', 'Go to your System Dashboard');
          target.onclick = (e) => {
            this.handleLogoClick(e);
          };
        } else {
          target.setAttribute('title', 'SupportHUB Home');
        }
      } else if (isInternalPortal && isLoggedIn) {
        target.setAttribute('title', 'Go to Dashboard');
        target.onclick = (e) => {
          this.handleLogoClick(e);
        };
      } else {
        target.setAttribute('title', 'Go to Home Page');
        target.onclick = (e) => {
          this.handleLogoClick(e);
        };
      }
    });

    // On Any Public Header Page (Home, Public Knowledge Base, etc.):
    // If user is logged in, replace "Sign In / Register" with role-based Portal button
    if (isLoggedIn && user) {
      const navActions = document.querySelector('.nav-actions');
      if (navActions) {
        const dashboardUrl = this.getDashboardUrl(user);
        const displayName = (user.fullName || user.email || 'Portal').split(' ')[0];
        navActions.innerHTML = `
          <a href="${dashboardUrl}" class="btn btn-accent" style="font-size: 0.875rem; padding: 0.5rem 1.15rem; display: inline-flex; align-items: center; gap: 0.45rem; font-weight: 700; border-radius: 8px;">
            <i class="fa-solid fa-gauge-high"></i> <span>${displayName}'s Portal</span> &rarr;
          </a>
          <button class="hamburger-btn" id="hamburger-toggle" aria-label="Toggle Navigation Menu">☰</button>
        `;
        const hamburgerBtn = document.getElementById('hamburger-toggle');
        const navMenu = document.getElementById('nav-menu');
        if (hamburgerBtn && navMenu) {
          hamburgerBtn.addEventListener('click', () => navMenu.classList.toggle('active'));
        }
      }
    }
  },

  fixUrlSpace() {
    if (window.location.pathname.includes('%20') || window.location.pathname.includes(' ')) {
      const cleanPath = window.location.pathname.replace(/%20|\s+/g, '-');
      window.location.replace(cleanPath + window.location.search + window.location.hash);
    }
  }
};

// Instantly auto-fix URLs with spaces
Auth.fixUrlSpace();

if (typeof window !== 'undefined') {
  window.Auth = Auth;
}

// Auto-bind logo navigation on DOMContentLoaded
if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      Auth.initLogoNavigation();
    });
  } else {
    Auth.initLogoNavigation();
  }
}
