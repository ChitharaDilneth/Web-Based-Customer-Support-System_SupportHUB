/**
 * SupportHUB Enterprise Platform - Real-time Notifications Engine (FR08)
 * Handles live unread badge count, header dropdown panel, relative time formatting,
 * system event action triggers, role-based routing, and mark-as-read workflows.
 * Brand tokens: Primary Blue #000985, Accent Yellow #F5CC00.
 */
const NotificationsEngine = {
  lastUnreadCount: 0,
  pollInterval: null,
  isInitialized: false,

  async init() {
    if (typeof Auth === 'undefined' || !Auth.isLoggedIn()) return;
    if (this.isInitialized) return;
    this.isInitialized = true;

    await this.fetchUnreadCount(false);
    this.attachEventListeners();
    this.startLivePolling();
  },

  startLivePolling() {
    if (this.pollInterval) clearInterval(this.pollInterval);

    // Standard gentle polling every 30 seconds to minimize unnecessary API requests
    this.pollInterval = setInterval(() => {
      // Avoid querying if tab/window is in background
      if (document.hidden) return;
      this.fetchUnreadCount(true);
    }, 30000);

    // Immediate check when user switches back to this tab
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && typeof Auth !== 'undefined' && Auth.isLoggedIn()) {
        this.fetchUnreadCount(true);
      }
    });
  },

  async fetchUnreadCount(isPoll = false) {
    try {
      if (typeof API === 'undefined') return;
      const res = await API.get('/notifications/unread-count');
      const counterEl = document.getElementById('notif-counter');
      const bellContainer = document.getElementById('notif-bell');
      const count = (res && typeof res.data === 'number') ? res.data : 0;

      if (counterEl) {
        counterEl.textContent = count > 99 ? '99+' : count;
        counterEl.style.display = count > 0 ? 'flex' : 'none';
        if (count > 0) {
          counterEl.setAttribute('aria-label', `${count} unread notifications`);
        }
      }

      if (bellContainer) {
        if (count > 0) {
          bellContainer.classList.add('has-unread');
          bellContainer.setAttribute('aria-label', `Notifications (${count} unread)`);
        } else {
          bellContainer.classList.remove('has-unread');
          bellContainer.setAttribute('aria-label', 'Notifications (No unread)');
        }
      }

      // If poll detected new unread notifications, notify user and refresh open dropdown
      if (isPoll && count > this.lastUnreadCount && this.lastUnreadCount >= 0) {
        if (typeof Utils !== 'undefined' && typeof Utils.showToast === 'function') {
          Utils.showToast('🔔 You have new unread notifications!', 'info');
        }
        const dropdown = document.getElementById('notif-dropdown');
        if (dropdown && dropdown.classList.contains('show')) {
          this.fetchDropdownList();
        }
      }

      this.lastUnreadCount = count;

      // Broadcast event so Notification Center page updates without duplicate API calls
      window.dispatchEvent(new CustomEvent('supportHub:notificationsChanged', {
        detail: { unreadCount: count }
      }));

      return count;
    } catch (err) {
      console.warn('Could not fetch notifications count:', err);
      return 0;
    }
  },

  formatRelativeTime(dateString) {
    if (!dateString) return 'Just now';
    try {
      const date = new Date(dateString);
      if (isNaN(date.getTime())) return 'Recently';

      const now = new Date();
      const diffMs = now - date;
      const diffSec = Math.floor(diffMs / 1000);
      const diffMin = Math.floor(diffSec / 60);
      const diffHour = Math.floor(diffMin / 60);
      const diffDay = Math.floor(diffHour / 24);

      if (diffSec < 45) return 'Just now';
      if (diffMin < 60) return `${diffMin}m ago`;
      if (diffHour === 1) return '1h ago';
      if (diffHour < 24) return `${diffHour}h ago`;
      if (diffDay === 1) return 'Yesterday';
      if (diffDay < 7) return `${diffDay}d ago`;
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    } catch (e) {
      return 'Recently';
    }
  },

  formatFullDateTime(dateString) {
    if (!dateString) return '';
    try {
      const d = new Date(dateString);
      if (isNaN(d.getTime())) return '';
      return d.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch (e) {
      return dateString;
    }
  },

  getTypeDetails(type) {
    const t = (type || '').toUpperCase();
    if (t.includes('CREATE')) {
      return { 
        label: 'Ticket Created',
        icon: 'fa-ticket', 
        bg: 'rgba(0, 9, 133, 0.08)', 
        color: '#000985',
        border: '#000985'
      };
    }
    if (t.includes('ASSIGN')) {
      return { 
        label: 'Assigned',
        icon: 'fa-user-check', 
        bg: '#eff6ff', 
        color: '#2563eb',
        border: '#2563eb'
      };
    }
    if (t.includes('REPLY') || t.includes('COMMENT') || t.includes('MESSAGE')) {
      return { 
        label: 'New Message',
        icon: 'fa-comment-dots', 
        bg: '#f0fdf4', 
        color: '#16a34a',
        border: '#16a34a'
      };
    }
    if (t.includes('STATUS')) {
      return { 
        label: 'Status Changed',
        icon: 'fa-arrows-rotate', 
        bg: '#faf5ff', 
        color: '#9333ea',
        border: '#9333ea'
      };
    }
    if (t.includes('RESOLVED')) {
      return { 
        label: 'Ticket Resolved',
        icon: 'fa-circle-check', 
        bg: '#ecfdf5', 
        color: '#059669',
        border: '#059669'
      };
    }
    if (t.includes('CLOSED')) {
      return { 
        label: 'Ticket Closed',
        icon: 'fa-lock', 
        bg: '#f8fafc', 
        color: '#475569',
        border: '#475569'
      };
    }
    if (t.includes('FEEDBACK') || t.includes('RATING') || t.includes('REVIEW')) {
      return { 
        label: 'Feedback',
        icon: 'fa-star', 
        bg: '#fefce8', 
        color: '#d97706',
        border: '#f59e0b'
      };
    }
    if (t.includes('BREACH') || t.includes('WARNING') || t.includes('URGENT') || t.includes('SLA')) {
      return { 
        label: 'SLA Alert',
        icon: 'fa-triangle-exclamation', 
        bg: '#fef2f2', 
        color: '#dc2626',
        border: '#dc2626'
      };
    }
    return { 
      label: 'System Notification',
      icon: 'fa-bell', 
      bg: '#f1f5f9', 
      color: '#000985',
      border: '#64748b'
    };
  },

  async fetchDropdownList() {
    const dropdown = document.getElementById('notif-dropdown');
    if (!dropdown) return;

    try {
      const res = await API.get('/notifications');
      const notifications = (res && res.data) ? res.data : [];
      const unreadExists = notifications.some(n => !n.read);

      let itemsHtml = '';
      if (!notifications || notifications.length === 0) {
        itemsHtml = `
          <div style="padding: 2.75rem 1rem; text-align: center; color: #64748b;">
            <div style="width: 52px; height: 52px; border-radius: 50%; background: #f8fafc; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 0.85rem; border: 1px dashed #cbd5e1;">
              <i class="fa-regular fa-bell-slash" style="font-size: 1.5rem; color: #94a3b8;"></i>
            </div>
            <div style="font-size: 0.9rem; font-weight: 700; color: #0f172a; margin-bottom: 0.25rem;">All Caught Up!</div>
            <div style="font-size: 0.775rem; color: #64748b;">You have no active notifications right now.</div>
          </div>
        `;
      } else {
        itemsHtml = notifications.slice(0, 6).map(n => {
          const typeDetails = this.getTypeDetails(n.type);
          const isUnread = !n.read;
          const ticketId = n.relatedTicketId || null;
          const timeStr = this.formatRelativeTime(n.createdAt);
          const fullTime = this.formatFullDateTime(n.createdAt);

          return `
            <div class="notif-item ${isUnread ? 'unread' : ''}" 
                 style="${isUnread ? 'background: rgba(0, 9, 133, 0.025); border-left: 3.5px solid #F5CC00;' : ''}"
                 onclick="NotificationsEngine.handleNotificationClick(${n.id}, ${ticketId ? `'${ticketId}'` : 'null'}, '${n.type || ''}')"
                 role="button"
                 tabindex="0"
                 onkeydown="if(event.key === 'Enter' || event.key === ' ') { NotificationsEngine.handleNotificationClick(${n.id}, ${ticketId ? `'${ticketId}'` : 'null'}, '${n.type || ''}'); event.preventDefault(); }">
              <div class="notif-item-icon" style="background: ${typeDetails.bg}; color: ${typeDetails.color};">
                <i class="fa-solid ${typeDetails.icon}"></i>
              </div>
              <div class="notif-item-content">
                <div class="notif-item-header">
                  <span class="notif-item-title" style="color: ${isUnread ? '#000985' : '#0f172a'}; font-weight: ${isUnread ? '700' : '600'};">
                    ${isUnread ? '<span style="width: 7px; height: 7px; border-radius: 50%; background: #F5CC00; display: inline-block; margin-right: 5px; box-shadow: 0 0 6px rgba(245, 204, 0, 0.8);"></span>' : ''}
                    ${Utils.escapeHtml ? Utils.escapeHtml(n.title || 'Notification') : (n.title || 'Notification')}
                  </span>
                  <span class="notif-item-time" title="${fullTime}">${timeStr}</span>
                </div>
                <p class="notif-item-msg">${Utils.escapeHtml ? Utils.escapeHtml(n.message || '') : (n.message || '')}</p>
                ${ticketId ? `<div style="margin-top: 0.35rem;"><span style="display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.68rem; font-weight: 700; background: rgba(0, 9, 133, 0.06); color: #000985; padding: 2px 7px; border-radius: 4px;"><i class="fa-solid fa-ticket"></i> #${ticketId}</span></div>` : ''}
              </div>
            </div>
          `;
        }).join('');
      }

      dropdown.innerHTML = `
        <div style="padding: 0.85rem 1.15rem; border-bottom: 1px solid rgba(0, 9, 133, 0.08); font-weight: 700; display: flex; justify-content: space-between; align-items: center; background: #ffffff;">
          <span style="font-size: 0.88rem; color: #000985; display: flex; align-items: center; gap: 0.5rem;">
            <i class="fa-solid fa-bell" style="color: #F5CC00;"></i> Notifications
          </span>
          ${unreadExists ? `<button onclick="NotificationsEngine.markAllRead(event)" style="background: none; border: none; color: #000985; font-size: 0.75rem; font-weight: 700; cursor: pointer; padding: 0.25rem 0.5rem; border-radius: 6px; transition: background 0.15s ease;" onmouseover="this.style.background='rgba(0, 9, 133, 0.05)'" onmouseout="this.style.background='none'"><i class="fa-solid fa-check-double" style="margin-right: 4px;"></i>Mark all read</button>` : ''}
        </div>
        <div id="notif-dropdown-list" style="max-height: 360px; overflow-y: auto;">
          ${itemsHtml}
        </div>
        <div style="padding: 0.75rem 1.15rem; text-align: center; border-top: 1px solid #f1f5f9; background: #fafbfc;">
          <a href="javascript:void(0)" onclick="NotificationsEngine.navigateToAll(event)" style="font-size: 0.8rem; font-weight: 700; color: #000985; text-decoration: none; display: inline-flex; align-items: center; gap: 0.4rem;">
            View Notification Center <i class="fa-solid fa-arrow-right" style="font-size: 0.75rem; color: #F5CC00;"></i>
          </a>
        </div>
      `;
    } catch (err) {
      console.warn('Could not fetch notifications list:', err);
    }
  },

  async markOneRead(id) {
    if (!id) return;
    try {
      await API.put(`/notifications/${id}/read`).catch(() => API.patch(`/notifications/${id}/read`));
      await this.fetchUnreadCount(false);
      return true;
    } catch (err) {
      console.warn('Could not mark notification as read:', err);
      return false;
    }
  },

  async handleNotificationClick(id, ticketId, type) {
    try {
      // Mark as read immediately on click
      if (id) {
        await this.markOneRead(id);
      }
      
      const dropdown = document.getElementById('notif-dropdown');
      if (dropdown) {
        dropdown.classList.remove('show');
        const bellBtn = document.getElementById('notif-bell');
        if (bellBtn) bellBtn.setAttribute('aria-expanded', 'false');
      }

      // Role-aware context navigation
      const user = (typeof Auth !== 'undefined' && typeof Auth.getUser === 'function') ? Auth.getUser() : null;
      const role = user?.role;
      const basePath = (typeof Auth !== 'undefined' && typeof Auth.getBasePath === 'function') ? Auth.getBasePath() : '';

      if (ticketId) {
        if (role === 'CUSTOMER') {
          window.location.href = `${basePath}/customer/ticket-details.html?id=${ticketId}`;
          return;
        } else if (role === 'CUSTOMER_SERVICE_OFFICER') {
          window.location.href = `${basePath}/officer/ticket-details.html?id=${ticketId}`;
          return;
        } else if (role === 'OPERATIONS_SUPERVISOR') {
          window.location.href = `${basePath}/supervisor/team-tickets.html?id=${ticketId}`;
          return;
        }
      }

      // Default role landing if no specific ticket
      if (type && type.includes('FEEDBACK') && role === 'QA_EXECUTIVE') {
        window.location.href = `${basePath}/qa/feedback-review.html`;
        return;
      }

      // For customer general notification without ticket, navigate to notification center
      if (role === 'CUSTOMER') {
        window.location.href = `${basePath}/customer/notifications.html`;
        return;
      }

      await this.fetchDropdownList();
    } catch (err) {
      console.warn('Error handling notification click:', err);
    }
  },

  async markAllRead(e) {
    if (e) e.stopPropagation();
    try {
      await API.put('/notifications/read-all').catch(() => API.patch('/notifications/read-all'));
      if (typeof Utils !== 'undefined' && typeof Utils.showToast === 'function') {
        Utils.showToast('All notifications marked as read', 'success');
      }
      await this.fetchUnreadCount(false);
      await this.fetchDropdownList();
      
      // Notify active page
      window.dispatchEvent(new CustomEvent('supportHub:allNotificationsMarkedRead'));
    } catch (err) {
      if (typeof Utils !== 'undefined' && typeof Utils.showToast === 'function') {
        Utils.showToast('Failed to mark notifications as read', 'error');
      }
    }
  },

  navigateToAll(e) {
    if (e) e.stopPropagation();
    const dropdown = document.getElementById('notif-dropdown');
    if (dropdown) {
      dropdown.classList.remove('show');
      const bellBtn = document.getElementById('notif-bell');
      if (bellBtn) bellBtn.setAttribute('aria-expanded', 'false');
    }

    const user = (typeof Auth !== 'undefined' && typeof Auth.getUser === 'function') ? Auth.getUser() : null;
    const role = user?.role;
    const basePath = (typeof Auth !== 'undefined' && typeof Auth.getBasePath === 'function') ? Auth.getBasePath() : '';

    if (role === 'CUSTOMER') {
      window.location.href = `${basePath}/customer/notifications.html`;
    } else if (role === 'CUSTOMER_SERVICE_OFFICER') {
      window.location.href = `${basePath}/officer/assigned-tickets.html`;
    } else if (role === 'OPERATIONS_SUPERVISOR') {
      window.location.href = `${basePath}/supervisor/team-tickets.html`;
    } else if (role === 'QA_EXECUTIVE') {
      window.location.href = `${basePath}/qa/qa-dashboard.html`;
    } else if (role === 'CUSTOMER_SUPPORT_MANAGER') {
      window.location.href = `${basePath}/manager/manager-dashboard.html`;
    } else {
      window.location.href = `${basePath}/index.html`;
    }
  },

  attachEventListeners() {
    const bellBtn = document.getElementById('notif-bell');
    const dropdown = document.getElementById('notif-dropdown');

    if (bellBtn && dropdown) {
      // Accessibility setup
      bellBtn.setAttribute('tabindex', '0');
      bellBtn.setAttribute('role', 'button');
      bellBtn.setAttribute('aria-haspopup', 'true');
      bellBtn.setAttribute('aria-expanded', 'false');

      const toggleDropdown = (e) => {
        if (e) e.stopPropagation();
        const isOpen = dropdown.classList.toggle('show');
        bellBtn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        if (isOpen) {
          this.fetchDropdownList();
        }
      };

      // Click trigger
      bellBtn.addEventListener('click', toggleDropdown);

      // Keyboard trigger (Enter or Space)
      bellBtn.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          toggleDropdown(e);
        } else if (e.key === 'Escape' && dropdown.classList.contains('show')) {
          e.preventDefault();
          dropdown.classList.remove('show');
          bellBtn.setAttribute('aria-expanded', 'false');
        }
      });

      // Close when clicking outside
      document.addEventListener('click', (e) => {
        if (!dropdown.contains(e.target) && !bellBtn.contains(e.target)) {
          if (dropdown.classList.contains('show')) {
            dropdown.classList.remove('show');
            bellBtn.setAttribute('aria-expanded', 'false');
          }
        }
      });

      // Close on Escape key anywhere
      document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && dropdown.classList.contains('show')) {
          dropdown.classList.remove('show');
          bellBtn.setAttribute('aria-expanded', 'false');
          bellBtn.focus();
        }
      });
    }
  }
};
