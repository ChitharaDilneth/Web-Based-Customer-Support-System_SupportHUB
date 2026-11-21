/**
 * SupportHUB Enterprise Platform — Dedicated Frontend Demonstration Service (Demo Mode)
 *
 * Provides a 100% self-contained, in-browser mock data layer and state machine
 * for public Vercel demonstrations without requiring a live backend server or database.
 *
 * Public Demo Credentials:
 *   Email: supervisor@demo.com
 *   Password: Demo@12345
 *   Role: OPERATIONS_SUPERVISOR
 *
 * Persists demo state and changes in browser LocalStorage.
 */

(function (window) {
  'use strict';

  const DEMO_STORAGE_KEY = 'supporthub_demo_store';
  const DEMO_MODE_FLAG = 'supporthub_demo_mode';
  const DEMO_SUPERVISOR_EMAIL = 'supervisor@demo.com';
  const DEMO_SUPERVISOR_PASSWORD = 'Demo@12345';

  // --- Initial Pristine Sample Data Model ---
  function getInitialDemoData() {
    const now = new Date();
    const isoNow = now.toISOString();

    return {
      supervisor: {
        id: 101,
        email: DEMO_SUPERVISOR_EMAIL,
        fullName: 'Sarah Jenkins (Demo Supervisor)',
        role: 'OPERATIONS_SUPERVISOR',
        department: 'Operations & Quality Assurance',
        designation: 'Senior Operations Supervisor',
        phone: '+1 (555) 019-2831',
        createdAt: '2026-01-10T08:00:00Z'
      },

      categories: [
        {
          id: 1,
          name: 'Technical Support',
          description: 'Hardware, system integrations, APIs, and network inquiries',
          icon: 'fa-laptop-code',
          slaHours: 4,
          active: true,
          subcategories: [
            { id: 101, name: 'API & Webhooks', description: 'Webhook drops and API auth failures', active: true },
            { id: 102, name: 'System Outages & Crashes', description: 'Application unresponsiveness', active: true }
          ]
        },
        {
          id: 2,
          name: 'Account & Security',
          description: 'Single sign-on, MFA authentication, permissions, and audit security',
          icon: 'fa-shield-halved',
          slaHours: 8,
          active: true,
          subcategories: [
            { id: 201, name: 'MFA & 2FA Recovery', description: 'Authenticator lockouts and recovery keys', active: true },
            { id: 202, name: 'Role & Permission Requests', description: 'RBAC escalation and privileges', active: true }
          ]
        },
        {
          id: 3,
          name: 'Billing & Invoicing',
          description: 'Invoices, corporate subscriptions, payment gateways, and tax receipts',
          icon: 'fa-credit-card',
          slaHours: 24,
          active: true,
          subcategories: [
            { id: 301, name: 'Corporate Subscriptions', description: 'Annual plan renewals and upgrades', active: true },
            { id: 302, name: 'Payment Reversals', description: 'Wire transfer and duplicate charge issues', active: true }
          ]
        },
        {
          id: 4,
          name: 'Returns & Refunds',
          description: 'RMA tracking, replacement logistics, and store credit management',
          icon: 'fa-box-open',
          slaHours: 24,
          active: true,
          subcategories: [
            { id: 401, name: 'Damaged in Transit', description: 'Courier inspection claims', active: true }
          ]
        },
        {
          id: 5,
          name: 'Order Tracking & Delivery',
          description: 'Shipment tracking, courier status, and package delivery issues',
          icon: 'fa-truck-fast',
          slaHours: 12,
          active: true,
          subcategories: [
            { id: 501, name: 'Courier Tracking Sync', description: 'Live tracking portal updates', active: true }
          ]
        },
        {
          id: 6,
          name: 'General Inquiries',
          description: 'Platform features, partnership programs, and terms of service',
          icon: 'fa-circle-question',
          slaHours: 48,
          active: true,
          subcategories: [
            { id: 601, name: 'Feature Requests & Feedback', description: 'Upcoming roadmap questions', active: true }
          ]
        }
      ],

      agents: [
        {
          id: 201,
          fullName: 'Marcus Vance',
          email: 'marcus.vance@demo.com',
          role: 'CUSTOMER_SERVICE_OFFICER',
          department: 'Technical Tier 1',
          designation: 'Senior Technical Support Officer',
          phone: '+1 (555) 014-4921',
          status: 'AVAILABLE',
          activeTickets: 2,
          resolvedTickets: 48,
          rating: 4.85,
          categoryIds: [1, 2],
          categories: [
            { id: 1, name: 'Technical Support' },
            { id: 2, name: 'Account & Security' }
          ]
        },
        {
          id: 202,
          fullName: 'Elena Rostova',
          email: 'elena.rostova@demo.com',
          role: 'CUSTOMER_SERVICE_OFFICER',
          department: 'Billing & Accounts',
          designation: 'Financial Care Specialist',
          phone: '+1 (555) 017-3849',
          status: 'AVAILABLE',
          activeTickets: 2,
          resolvedTickets: 39,
          rating: 4.92,
          categoryIds: [3, 4],
          categories: [
            { id: 3, name: 'Billing & Invoicing' },
            { id: 4, name: 'Returns & Refunds' }
          ]
        },
        {
          id: 203,
          fullName: 'David Chen',
          email: 'david.chen@demo.com',
          role: 'CUSTOMER_SERVICE_OFFICER',
          department: 'Order Fulfillment',
          designation: 'Logistics Support Officer',
          phone: '+1 (555) 012-7744',
          status: 'BUSY',
          activeTickets: 3,
          resolvedTickets: 56,
          rating: 4.75,
          categoryIds: [5],
          categories: [
            { id: 5, name: 'Order Tracking & Delivery' }
          ]
        },
        {
          id: 204,
          fullName: 'Amara Okafor',
          email: 'amara.okafor@demo.com',
          role: 'CUSTOMER_SERVICE_OFFICER',
          department: 'Customer Success',
          designation: 'Senior Care Officer',
          phone: '+1 (555) 018-9922',
          status: 'AVAILABLE',
          activeTickets: 1,
          resolvedTickets: 64,
          rating: 5.0,
          categoryIds: [1, 6],
          categories: [
            { id: 1, name: 'Technical Support' },
            { id: 6, name: 'General Inquiries' }
          ]
        }
      ],

      customers: [
        {
          id: 301,
          fullName: 'Liam Gallagher',
          email: 'liam.g@demo-client.com',
          phone: '+1 (555) 011-8291',
          address: '742 Evergreen Terr, Springfield, IL',
          status: 'ACTIVE',
          totalTickets: 4,
          openTickets: 1,
          createdAt: '2026-01-15T09:30:00Z'
        },
        {
          id: 302,
          fullName: 'Sophia Rodriguez',
          email: 'sophia.r@demo-client.com',
          phone: '+1 (555) 013-4412',
          address: '1200 Market St, San Francisco, CA',
          status: 'ACTIVE',
          totalTickets: 3,
          openTickets: 1,
          createdAt: '2026-02-01T14:20:00Z'
        },
        {
          id: 303,
          fullName: 'Tariq Al-Mansoor',
          email: 'tariq.m@demo-client.com',
          phone: '+1 (555) 016-9023',
          address: '45 Sheikh Zayed Rd, Dubai, UAE',
          status: 'ACTIVE',
          totalTickets: 2,
          openTickets: 1,
          createdAt: '2026-02-18T11:00:00Z'
        },
        {
          id: 304,
          fullName: 'Chloe Dupont',
          email: 'chloe.d@demo-client.com',
          phone: '+1 (555) 019-5561',
          address: '18 Rue de la Paix, Paris, France',
          status: 'ACTIVE',
          totalTickets: 5,
          openTickets: 1,
          createdAt: '2026-03-04T16:45:00Z'
        },
        {
          id: 305,
          fullName: 'Kenji Sato',
          email: 'kenji.s@demo-client.com',
          phone: '+1 (555) 015-3390',
          address: '3-Chome Ginza, Chuo-ku, Tokyo, Japan',
          status: 'ACTIVE',
          totalTickets: 1,
          openTickets: 1,
          createdAt: '2026-04-12T08:15:00Z'
        },
        {
          id: 306,
          fullName: 'Isabella Rossi',
          email: 'isabella.r@demo-client.com',
          phone: '+1 (555) 017-6621',
          address: 'Via Montenapoleone 8, Milan, Italy',
          status: 'INACTIVE',
          totalTickets: 2,
          openTickets: 0,
          createdAt: '2025-11-20T10:00:00Z'
        },
        {
          id: 307,
          fullName: 'Oliver Hansen',
          email: 'oliver.h@demo-client.com',
          phone: '+1 (555) 018-1144',
          address: 'Ostergade 24, Copenhagen, Denmark',
          status: 'ACTIVE',
          totalTickets: 3,
          openTickets: 0,
          createdAt: '2026-05-02T13:30:00Z'
        },
        {
          id: 308,
          fullName: 'Priya Sharma',
          email: 'priya.s@demo-client.com',
          phone: '+1 (555) 014-8855',
          address: 'Connaught Place, New Delhi, India',
          status: 'ACTIVE',
          totalTickets: 2,
          openTickets: 1,
          createdAt: '2026-06-10T17:10:00Z'
        }
      ],

      tickets: [
        {
          id: 501,
          ticketNumber: 'TKT-2026-000012',
          subject: 'Production Webhook Payload Delivery Failures (HTTP 504)',
          description: 'Our enterprise inventory webhook listener is timing out on payloads larger than 2MB since yesterday morning. Multiple dispatch events dropped.',
          status: 'IN_PROGRESS',
          priority: 'HIGH',
          escalated: false,
          customerId: 301,
          customerName: 'Liam Gallagher',
          customerEmail: 'liam.g@demo-client.com',
          categoryId: 1,
          categoryName: 'Technical Support',
          parentCategoryName: 'Support',
          assignedAgentId: 201,
          assignedAgentName: 'Marcus Vance',
          assignedAgent: { id: 201, fullName: 'Marcus Vance', email: 'marcus.vance@demo.com' },
          orderNumber: '#SRV-WEBHOOK-99',
          createdAt: '2026-11-20T08:00:00Z',
          updatedAt: '2026-11-21T09:15:00Z',
          slaHours: 4,
          slaDueDate: '2026-11-21T18:00:00Z',
          slaBreached: false,
          replies: [
            {
              id: 1,
              senderName: 'Liam Gallagher',
              senderRole: 'CUSTOMER',
              message: 'Attached are the error logs showing recurrent timeouts from your webhook dispatcher.',
              isInternal: false,
              createdAt: '2026-11-20T08:00:00Z'
            },
            {
              id: 2,
              senderName: 'Marcus Vance',
              senderRole: 'CUSTOMER_SERVICE_OFFICER',
              message: 'Hello Liam, we have replicated the payload buffer limit and have adjusted the proxy timeout window. Currently running verification tests.',
              isInternal: false,
              createdAt: '2026-11-21T09:15:00Z'
            }
          ],
          history: [
            { id: 1, action: 'CREATED', details: 'Ticket submitted by customer', changedBy: 'Liam Gallagher', timestamp: '2026-11-20T08:00:00Z' },
            { id: 2, action: 'ASSIGNED', details: 'Assigned to Officer Marcus Vance', changedBy: 'Sarah Jenkins', timestamp: '2026-11-20T08:30:00Z' },
            { id: 3, action: 'STATUS_CHANGED', details: 'Status changed from OPEN to IN_PROGRESS', changedBy: 'Marcus Vance', timestamp: '2026-11-20T09:00:00Z' }
          ]
        },
        {
          id: 502,
          ticketNumber: 'TKT-2026-000014',
          subject: 'Unauthorized Multi-Factor Authentication Lockout on Admin Seat',
          description: 'The primary account administrator phone was replaced and the backup recovery keys are failing validation on the login screen.',
          status: 'ESCALATED',
          priority: 'URGENT',
          escalated: true,
          customerId: 302,
          customerName: 'Sophia Rodriguez',
          customerEmail: 'sophia.r@demo-client.com',
          categoryId: 2,
          categoryName: 'Account & Security',
          parentCategoryName: 'Security',
          assignedAgentId: 201,
          assignedAgentName: 'Marcus Vance',
          assignedAgent: { id: 201, fullName: 'Marcus Vance', email: 'marcus.vance@demo.com' },
          orderNumber: '#SEC-MFA-2026',
          createdAt: '2026-11-19T14:30:00Z',
          updatedAt: '2026-11-21T10:00:00Z',
          slaHours: 8,
          slaDueDate: '2026-11-20T02:30:00Z',
          slaBreached: true,
          replies: [
            {
              id: 1,
              senderName: 'Sophia Rodriguez',
              senderRole: 'CUSTOMER',
              message: 'Our whole dev ops team is locked out from our organization settings console.',
              isInternal: false,
              createdAt: '2026-11-19T14:30:00Z'
            },
            {
              id: 2,
              senderName: 'Sarah Jenkins',
              senderRole: 'OPERATIONS_SUPERVISOR',
              message: '[INTERNAL NOTE] Escalated to Identity Verification Desk for priority hardware token re-issuance.',
              isInternal: true,
              createdAt: '2026-11-21T10:00:00Z'
            }
          ],
          history: [
            { id: 1, action: 'CREATED', details: 'Ticket submitted by customer', changedBy: 'Sophia Rodriguez', timestamp: '2026-11-19T14:30:00Z' },
            { id: 2, action: 'ESCALATED', details: 'Ticket escalated to supervisor due to SLA breach', changedBy: 'System Watchdog', timestamp: '2026-11-20T03:00:00Z' }
          ]
        },
        {
          id: 503,
          ticketNumber: 'TKT-2026-000017',
          subject: 'International Wire Transfer Duplicate Billing On Q4 Invoice',
          description: 'Invoice #INV-2026-9082 was debited twice via our European SEPA account. Please process a reversal or credit note.',
          status: 'OPEN',
          priority: 'HIGH',
          escalated: false,
          customerId: 303,
          customerName: 'Tariq Al-Mansoor',
          customerEmail: 'tariq.m@demo-client.com',
          categoryId: 3,
          categoryName: 'Billing & Invoicing',
          parentCategoryName: 'Billing',
          assignedAgentId: 202,
          assignedAgentName: 'Elena Rostova',
          assignedAgent: { id: 202, fullName: 'Elena Rostova', email: 'elena.rostova@demo.com' },
          orderNumber: '#INV-2026-9082',
          createdAt: '2026-11-21T06:45:00Z',
          updatedAt: '2026-11-21T07:00:00Z',
          slaHours: 24,
          slaDueDate: '2026-11-22T06:45:00Z',
          slaBreached: false,
          replies: [
            {
              id: 1,
              senderName: 'Tariq Al-Mansoor',
              senderRole: 'CUSTOMER',
              message: 'Attached wire confirmation receipt from Emirates NBD showing duplicate reference transactions.',
              isInternal: false,
              createdAt: '2026-11-21T06:45:00Z'
            }
          ],
          history: [
            { id: 1, action: 'CREATED', details: 'Ticket submitted by customer', changedBy: 'Tariq Al-Mansoor', timestamp: '2026-11-21T06:45:00Z' },
            { id: 2, action: 'ASSIGNED', details: 'Assigned to Officer Elena Rostova', changedBy: 'Sarah Jenkins', timestamp: '2026-11-21T07:00:00Z' }
          ]
        },
        {
          id: 504,
          ticketNumber: 'TKT-2026-000019',
          subject: 'Express Courier Shipment Tracking Not Updating in Portal',
          description: 'Package marked as shipped on Nov 18 with courier DHL Express, but the tracking code returns invalid parcel status.',
          status: 'OPEN',
          priority: 'MEDIUM',
          escalated: false,
          customerId: 304,
          customerName: 'Chloe Dupont',
          customerEmail: 'chloe.d@demo-client.com',
          categoryId: 5,
          categoryName: 'Order Tracking & Delivery',
          parentCategoryName: 'Logistics',
          assignedAgentId: null,
          assignedAgentName: 'Unassigned',
          assignedAgent: null,
          orderNumber: '#ORD-DHL-88192',
          createdAt: '2026-11-21T09:20:00Z',
          updatedAt: '2026-11-21T09:20:00Z',
          slaHours: 12,
          slaDueDate: '2026-11-21T21:20:00Z',
          slaBreached: false,
          replies: [],
          history: [
            { id: 1, action: 'CREATED', details: 'Ticket submitted in unassigned queue', changedBy: 'Chloe Dupont', timestamp: '2026-11-21T09:20:00Z' }
          ]
        },
        {
          id: 505,
          ticketNumber: 'TKT-2026-000020',
          subject: 'Damaged Hardware Unit Returned – Awaiting Quality Inspection & RMA',
          description: 'Customer returned defective terminal hardware under RMA-4029. Warehouse confirmation received.',
          status: 'WAITING_FOR_CUSTOMER',
          priority: 'MEDIUM',
          escalated: false,
          customerId: 305,
          customerName: 'Kenji Sato',
          customerEmail: 'kenji.s@demo-client.com',
          categoryId: 4,
          categoryName: 'Returns & Refunds',
          parentCategoryName: 'Fulfillment',
          assignedAgentId: 202,
          assignedAgentName: 'Elena Rostova',
          assignedAgent: { id: 202, fullName: 'Elena Rostova', email: 'elena.rostova@demo.com' },
          orderNumber: '#RMA-4029-JP',
          createdAt: '2026-11-18T10:00:00Z',
          updatedAt: '2026-11-20T16:00:00Z',
          slaHours: 24,
          slaDueDate: '2026-11-22T10:00:00Z',
          slaBreached: false,
          replies: [],
          history: [
            { id: 1, action: 'CREATED', details: 'Ticket created', changedBy: 'Kenji Sato', timestamp: '2026-11-18T10:00:00Z' }
          ]
        },
        {
          id: 506,
          ticketNumber: 'TKT-2026-000021',
          subject: 'Enterprise REST API Rate Limiting Threshold Clarification',
          description: 'Inquiry regarding bulk API burst quotas for upcoming Black Friday customer sync jobs.',
          status: 'RESOLVED',
          priority: 'LOW',
          escalated: false,
          customerId: 307,
          customerName: 'Oliver Hansen',
          customerEmail: 'oliver.h@demo-client.com',
          categoryId: 6,
          categoryName: 'General Inquiries',
          parentCategoryName: 'General',
          assignedAgentId: 204,
          assignedAgentName: 'Amara Okafor',
          assignedAgent: { id: 204, fullName: 'Amara Okafor', email: 'amara.okafor@demo.com' },
          orderNumber: '#API-QUOTA-DK',
          createdAt: '2026-11-17T11:00:00Z',
          updatedAt: '2026-11-19T15:30:00Z',
          slaHours: 48,
          slaDueDate: '2026-11-19T11:00:00Z',
          slaBreached: false,
          replies: [],
          history: [
            { id: 1, action: 'RESOLVED', details: 'Quota guidelines provided', changedBy: 'Amara Okafor', timestamp: '2026-11-19T15:30:00Z' }
          ]
        },
        {
          id: 507,
          ticketNumber: 'TKT-2026-000022',
          subject: 'Critical Database Connection Pool Exhaustion on Dedicated Node',
          description: 'Client application reporting HikariCP connection timeout exceptions. Node cluster unresponsive.',
          status: 'ESCALATED',
          priority: 'URGENT',
          escalated: true,
          customerId: 308,
          customerName: 'Priya Sharma',
          customerEmail: 'priya.s@demo-client.com',
          categoryId: 1,
          categoryName: 'Technical Support',
          parentCategoryName: 'Support',
          assignedAgentId: null,
          assignedAgentName: 'Unassigned',
          assignedAgent: null,
          orderNumber: '#INFRA-NODE-77',
          createdAt: '2026-11-21T05:00:00Z',
          updatedAt: '2026-11-21T05:00:00Z',
          slaHours: 4,
          slaDueDate: '2026-11-21T09:00:00Z',
          slaBreached: true,
          replies: [],
          history: [
            { id: 1, action: 'CREATED', details: 'Automated alarm ticket created', changedBy: 'Monitoring Agent', timestamp: '2026-11-21T05:00:00Z' },
            { id: 2, action: 'ESCALATED', details: 'Overdue high-severity ticket escalated', changedBy: 'System Watchdog', timestamp: '2026-11-21T09:00:00Z' }
          ]
        },
        {
          id: 508,
          ticketNumber: 'TKT-2026-000023',
          subject: 'Custom Domain SSL Certificate Auto-Renewal Failed',
          description: 'Let\'s Encrypt challenge failed during automated TLS handshake. Customer white-label portal showing cert expired.',
          status: 'IN_PROGRESS',
          priority: 'HIGH',
          escalated: false,
          customerId: 301,
          customerName: 'Liam Gallagher',
          customerEmail: 'liam.g@demo-client.com',
          categoryId: 1,
          categoryName: 'Technical Support',
          parentCategoryName: 'Support',
          assignedAgentId: 203,
          assignedAgentName: 'David Chen',
          assignedAgent: { id: 203, fullName: 'David Chen', email: 'david.chen@demo.com' },
          orderNumber: '#SSL-DOM-202',
          createdAt: '2026-11-20T19:00:00Z',
          updatedAt: '2026-11-21T08:30:00Z',
          slaHours: 8,
          slaDueDate: '2026-11-21T15:00:00Z',
          slaBreached: false,
          replies: [],
          history: []
        },
        {
          id: 509,
          ticketNumber: 'TKT-2026-000024',
          subject: 'Annual Enterprise SLA Addendum Signature Routing',
          description: 'Legal procurement team requires counter-signed master service agreement with updated 99.99% uptime guarantee.',
          status: 'CLOSED',
          priority: 'LOW',
          escalated: false,
          customerId: 302,
          customerName: 'Sophia Rodriguez',
          customerEmail: 'sophia.r@demo-client.com',
          categoryId: 6,
          categoryName: 'General Inquiries',
          parentCategoryName: 'General',
          assignedAgentId: 204,
          assignedAgentName: 'Amara Okafor',
          assignedAgent: { id: 204, fullName: 'Amara Okafor', email: 'amara.okafor@demo.com' },
          orderNumber: '#LEG-MSA-992',
          createdAt: '2026-11-15T09:00:00Z',
          updatedAt: '2026-11-18T14:00:00Z',
          slaHours: 48,
          slaDueDate: '2026-11-17T09:00:00Z',
          slaBreached: false,
          replies: [],
          history: []
        },
        {
          id: 510,
          ticketNumber: 'TKT-2026-000025',
          subject: 'Custom CSV Export Column Mappings Discrepancy',
          description: 'Exported transaction files omit invoice tax subtotal fields in the generated comma-separated output.',
          status: 'OPEN',
          priority: 'MEDIUM',
          escalated: false,
          customerId: 303,
          customerName: 'Tariq Al-Mansoor',
          customerEmail: 'tariq.m@demo-client.com',
          categoryId: 3,
          categoryName: 'Billing & Invoicing',
          parentCategoryName: 'Billing',
          assignedAgentId: 203,
          assignedAgentName: 'David Chen',
          assignedAgent: { id: 203, fullName: 'David Chen', email: 'david.chen@demo.com' },
          orderNumber: '#RPT-CSV-301',
          createdAt: '2026-11-21T10:15:00Z',
          updatedAt: '2026-11-21T10:15:00Z',
          slaHours: 24,
          slaDueDate: '2026-11-22T10:15:00Z',
          slaBreached: false,
          replies: [],
          history: []
        }
      ],

      notifications: [
        {
          id: 1,
          title: 'SLA Escalation Alert',
          message: 'Ticket #TKT-2026-000014 (MFA Lockout) has breached target SLA resolution window.',
          type: 'ESCALATION',
          isRead: false,
          read: false,
          createdAt: '2026-11-21T10:00:00Z',
          link: '../supervisor/escalations.html'
        },
        {
          id: 2,
          title: 'Urgent Unassigned Ticket',
          message: 'Critical Ticket #TKT-2026-000022 requires supervisor review and officer assignment.',
          type: 'TICKET_CREATED',
          isRead: false,
          read: false,
          createdAt: '2026-11-21T05:00:00Z',
          link: '../supervisor/team-tickets.html?status=UNASSIGNED'
        },
        {
          id: 3,
          title: 'Positive CSAT Received',
          message: 'Customer Oliver Hansen submitted 5-star feedback for completed resolution.',
          type: 'FEEDBACK',
          isRead: false,
          read: false,
          createdAt: '2026-11-20T17:30:00Z',
          link: '../supervisor/reports.html'
        },
        {
          id: 4,
          title: 'System Health Check',
          message: 'All automated background SLA monitors and queues operating normally.',
          type: 'SYSTEM',
          isRead: true,
          read: true,
          createdAt: '2026-11-20T03:00:00Z',
          link: '../supervisor/supervisor-dashboard.html'
        }
      ],

      feedback: [
        {
          id: 1,
          ticketNumber: 'TKT-2026-000021',
          customerName: 'Oliver Hansen',
          rating: 5,
          comment: 'Amara provided instant, clear clarity on the API quotas. Super professional service!',
          createdAt: '2026-11-19T16:00:00Z'
        },
        {
          id: 2,
          ticketNumber: 'TKT-2026-000018',
          customerName: 'Liam Gallagher',
          rating: 5,
          comment: 'Quick turnaround on our hardware dispatch question.',
          createdAt: '2026-11-18T14:20:00Z'
        },
        {
          id: 3,
          ticketNumber: 'TKT-2026-000015',
          customerName: 'Priya Sharma',
          rating: 4,
          comment: 'Resolution was effective, took slightly longer than anticipated but great communication.',
          createdAt: '2026-11-17T11:45:00Z'
        }
      ]
    };
  }

  // --- LocalStorage Store Accessors ---
  function getStore() {
    try {
      const raw = localStorage.getItem(DEMO_STORAGE_KEY);
      if (raw) {
        return JSON.parse(raw);
      }
    } catch (e) {
      console.warn('DemoStore: failed parsing stored data, resetting:', e);
    }
    const fresh = getInitialDemoData();
    saveStore(fresh);
    return fresh;
  }

  function saveStore(data) {
    try {
      localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(data));
    } catch (e) {
      console.warn('DemoStore: failed saving to localStorage:', e);
    }
  }

  // --- Demo Service Singleton ---
  const DemoService = {
    isDemoActive() {
      if (typeof window === 'undefined') return false;
      const flag = localStorage.getItem(DEMO_MODE_FLAG);
      const userRaw = localStorage.getItem('user_info');
      if (flag === 'true') return true;
      if (userRaw) {
        try {
          const user = JSON.parse(userRaw);
          if (user && user.email === DEMO_SUPERVISOR_EMAIL) {
            return true;
          }
        } catch (e) {}
      }
      return false;
    },

    activateDemo() {
      localStorage.setItem(DEMO_MODE_FLAG, 'true');
      getStore(); // Ensure data initialized
    },

    deactivateDemo() {
      localStorage.removeItem(DEMO_MODE_FLAG);
    },

    resetData() {
      localStorage.removeItem(DEMO_STORAGE_KEY);
      getStore();
      if (typeof Utils !== 'undefined' && Utils.showToast) {
        Utils.showToast('Demo data restored to pristine initial state.', 'success');
      }
      setTimeout(() => {
        window.location.reload();
      }, 350);
    },

    canIntercept(endpoint) {
      if (this.isDemoActive()) return true;
      if (endpoint === '/auth/login' || endpoint.startsWith('/auth/login')) return true;
      // Also intercept if hosted on vercel.app and no backend configured
      if (typeof window !== 'undefined' && window.location.hostname.includes('vercel.app')) {
        const customBackend = localStorage.getItem('supporthub_api_url');
        if (!customBackend && !window.__SUPPORT_HUB_API_URL__) {
          return true;
        }
      }
      return false;
    },

    // --- Main Mock Request Dispatcher ---
    async handleRequest(endpoint, options = {}) {
      // Small simulated latency for natural UI feel (50ms - 150ms)
      await new Promise(r => setTimeout(r, 60));

      const method = (options.method || 'GET').toUpperCase();
      let body = {};
      if (options.body) {
        if (typeof options.body === 'string') {
          try { body = JSON.parse(options.body); } catch (e) { body = {}; }
        } else if (options.body instanceof FormData) {
          options.body.forEach((val, key) => { body[key] = val; });
        } else {
          body = options.body;
        }
      }

      const [pathOnly, queryString] = endpoint.split('?');
      const queryParams = new URLSearchParams(queryString || '');
      const store = getStore();

      // 1. AUTHENTICATION
      if (pathOnly === '/auth/login') {
        const email = (body.email || '').trim().toLowerCase();
        const password = body.password || '';

        const knownDemoEmails = [
          DEMO_SUPERVISOR_EMAIL,
          'agent@demo.com',
          'customer@demo.com',
          'manager@demo.com',
          'qa@demo.com'
        ];

        if (knownDemoEmails.includes(email) && password !== DEMO_SUPERVISOR_PASSWORD) {
          const err = new Error('Password incorrect. Please try again.');
          err.status = 401;
          throw err;
        }

        if (email === DEMO_SUPERVISOR_EMAIL && password === DEMO_SUPERVISOR_PASSWORD) {
          this.activateDemo();
          const user = store.supervisor;
          const token = 'demo-jwt-token-operations-supervisor';
          return {
            success: true,
            message: 'Demo login successful! Welcome to SupportHUB Demonstration Environment.',
            data: {
              token,
              id: user.id,
              email: user.email,
              fullName: user.fullName,
              role: user.role,
              department: user.department,
              designation: user.designation
            }
          };
        } else if (email === 'agent@demo.com' && password === DEMO_SUPERVISOR_PASSWORD) {
          this.activateDemo();
          const user = store.agents[0];
          return {
            success: true,
            message: 'Demo login successful! Welcome Support Officer.',
            data: {
              token: 'demo-jwt-token-agent',
              id: user.id,
              email: user.email,
              fullName: user.fullName,
              role: 'CUSTOMER_SERVICE_OFFICER',
              department: 'Tier 2 Technical Support',
              designation: 'Senior Support Officer'
            }
          };
        } else if (email === 'customer@demo.com' && password === DEMO_SUPERVISOR_PASSWORD) {
          this.activateDemo();
          const user = store.customers[0];
          return {
            success: true,
            message: 'Demo login successful! Welcome Customer.',
            data: {
              token: 'demo-jwt-token-customer',
              id: user.id,
              email: user.email,
              fullName: user.fullName,
              role: 'CUSTOMER',
              department: 'Client',
              designation: 'Customer'
            }
          };
        } else if (email === 'manager@demo.com' && password === DEMO_SUPERVISOR_PASSWORD) {
          this.activateDemo();
          return {
            success: true,
            message: 'Demo login successful! Welcome Support Manager.',
            data: {
              token: 'demo-jwt-token-manager',
              id: 99,
              email: 'manager@demo.com',
              fullName: 'Michael Vance (Demo Manager)',
              role: 'CUSTOMER_SUPPORT_MANAGER',
              department: 'Customer Support Leadership',
              designation: 'VP of Customer Support'
            }
          };
        } else if (email === 'qa@demo.com' && password === DEMO_SUPERVISOR_PASSWORD) {
          this.activateDemo();
          return {
            success: true,
            message: 'Demo login successful! Welcome QA Executive.',
            data: {
              token: 'demo-jwt-token-qa',
              id: 98,
              email: 'qa@demo.com',
              fullName: 'Jessica Chen (Demo QA)',
              role: 'QA_EXECUTIVE',
              department: 'Quality Assurance',
              designation: 'Lead QA Specialist'
            }
          };
        } else {
          const err = new Error('Demo Environment: Please sign in with supervisor@demo.com / Demo@12345 or click Quick Demo Sign In.');
          err.status = 401;
          throw err;
        }
      }

      if (pathOnly === '/auth/me') {
        if (typeof localStorage !== 'undefined') {
          const currentSession = localStorage.getItem('user_info');
          if (currentSession) {
            try {
              return { success: true, data: JSON.parse(currentSession) };
            } catch(e) {}
          }
        }
        return { success: true, data: store.supervisor };
      }

      if (pathOnly === '/auth/profile') {
        store.supervisor.fullName = body.fullName || store.supervisor.fullName;
        store.supervisor.phone = body.phone || store.supervisor.phone;
        store.supervisor.department = body.department || store.supervisor.department;
        store.supervisor.designation = body.designation || store.supervisor.designation;
        saveStore(store);

        // Update session
        const currentSession = localStorage.getItem('user_info');
        if (currentSession) {
          try {
            const parsed = JSON.parse(currentSession);
            Object.assign(parsed, store.supervisor);
            localStorage.setItem('user_info', JSON.stringify(parsed));
          } catch (e) {}
        }
        return { success: true, message: 'Profile updated in demo store', data: store.supervisor };
      }

      if (pathOnly === '/auth/change-password') {
        return { success: true, message: 'Password updated successfully (Demo Mode).' };
      }

      // 2. TICKETS
      if (pathOnly === '/tickets' && method === 'GET') {
        let list = [...store.tickets];
        const status = queryParams.get('status');
        const priority = queryParams.get('priority');
        const catId = queryParams.get('categoryId');
        const agentId = queryParams.get('agentId');
        const search = (queryParams.get('search') || '').toLowerCase().trim();

        if (status) {
          if (status === 'UNASSIGNED') {
            list = list.filter(t => !t.assignedAgentId && t.status !== 'RESOLVED' && t.status !== 'CLOSED');
          } else if (status === 'ESCALATED') {
            list = list.filter(t => t.escalated || t.status === 'ESCALATED');
          } else {
            list = list.filter(t => t.status === status);
          }
        }
        if (priority) {
          list = list.filter(t => t.priority === priority);
        }
        if (catId) {
          list = list.filter(t => String(t.categoryId) === String(catId));
        }
        if (agentId) {
          list = list.filter(t => String(t.assignedAgentId) === String(agentId));
        }
        if (search) {
          list = list.filter(t =>
            (t.ticketNumber && t.ticketNumber.toLowerCase().includes(search)) ||
            (t.subject && t.subject.toLowerCase().includes(search)) ||
            (t.customerName && t.customerName.toLowerCase().includes(search))
          );
        }

        return {
          success: true,
          data: {
            content: list,
            totalElements: list.length,
            totalPages: 1,
            size: list.length,
            number: 0
          }
        };
      }

      if (pathOnly === '/tickets' && method === 'POST') {
        const nextId = store.tickets.length > 0 ? Math.max(...store.tickets.map(t => t.id)) + 1 : 501;
        const seqNum = String(nextId).padStart(6, '0');
        const cat = store.categories.find(c => String(c.id) === String(body.categoryId)) || store.categories[0];
        const newTicket = {
          id: nextId,
          ticketNumber: `TKT-${new Date().getFullYear()}-${seqNum}`,
          subject: body.subject || 'Support Ticket',
          description: body.description || '',
          status: 'OPEN',
          priority: body.priority || 'MEDIUM',
          escalated: false,
          customerId: 301,
          customerName: body.customerName || 'Liam Gallagher',
          customerEmail: body.customerEmail || 'liam.g@demo-client.com',
          categoryId: cat.id,
          categoryName: cat.name,
          parentCategoryName: 'Support',
          assignedAgentId: null,
          assignedAgentName: 'Unassigned',
          assignedAgent: null,
          orderNumber: body.orderNumber || '#ORD-DEMO',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          slaHours: cat.slaHours || 24,
          slaDueDate: new Date(Date.now() + (cat.slaHours || 24) * 3600000).toISOString(),
          slaBreached: false,
          replies: [],
          history: [
            { id: 1, action: 'CREATED', details: 'Ticket submitted in demo environment', changedBy: store.supervisor.fullName, timestamp: new Date().toISOString() }
          ]
        };
        store.tickets.unshift(newTicket);
        saveStore(store);
        return { success: true, message: 'Ticket created successfully', data: newTicket };
      }

      if (pathOnly === '/tickets/escalated') {
        const escalated = store.tickets.filter(t => t.escalated || t.status === 'ESCALATED');
        return { success: true, data: escalated };
      }

      if (pathOnly === '/tickets/check-sla') {
        let count = 0;
        store.tickets.forEach(t => {
          if (t.slaBreached && !t.escalated) {
            t.escalated = true;
            t.status = 'ESCALATED';
            count++;
          }
        });
        saveStore(store);
        return { success: true, message: 'SLA check completed', data: count };
      }

      // Ticket by ID operations: /tickets/{id}
      const ticketIdMatch = pathOnly.match(/^\/tickets\/(\d+)$/);
      if (ticketIdMatch) {
        const tid = parseInt(ticketIdMatch[1], 10);
        const ticket = store.tickets.find(t => t.id === tid);
        if (!ticket) {
          const err = new Error(`Ticket #${tid} not found`);
          err.status = 404;
          throw err;
        }

        if (method === 'GET') {
          return { success: true, data: ticket };
        }
      }

      // Assign Ticket: /tickets/{id}/assign
      const assignMatch = pathOnly.match(/^\/tickets\/(\d+)\/assign$/);
      if (assignMatch && (method === 'PUT' || method === 'POST')) {
        const tid = parseInt(assignMatch[1], 10);
        const ticket = store.tickets.find(t => t.id === tid);
        if (!ticket) throw new Error(`Ticket #${tid} not found`);

        const agId = body.agentId ? parseInt(body.agentId, 10) : null;
        const agent = store.agents.find(a => a.id === agId);

        ticket.assignedAgentId = agId;
        ticket.assignedAgentName = agent ? agent.fullName : 'Unassigned';
        ticket.assignedAgent = agent ? { id: agent.id, fullName: agent.fullName, email: agent.email } : null;
        if (ticket.status === 'OPEN' && agent) {
          ticket.status = 'IN_PROGRESS';
        }
        ticket.updatedAt = new Date().toISOString();

        ticket.history.push({
          id: ticket.history.length + 1,
          action: 'ASSIGNED',
          details: agent ? `Assigned to Officer ${agent.fullName}` : 'Unassigned ticket',
          changedBy: store.supervisor.fullName,
          timestamp: ticket.updatedAt
        });

        // Recalculate agent workloads
        store.agents.forEach(a => {
          a.activeTickets = store.tickets.filter(t => t.assignedAgentId === a.id && t.status !== 'RESOLVED' && t.status !== 'CLOSED').length;
        });

        saveStore(store);
        return { success: true, message: 'Ticket assigned successfully', data: ticket };
      }

      // Update Status: /tickets/{id}/status
      const statusMatch = pathOnly.match(/^\/tickets\/(\d+)\/status$/);
      if (statusMatch && (method === 'PUT' || method === 'PATCH')) {
        const tid = parseInt(statusMatch[1], 10);
        const ticket = store.tickets.find(t => t.id === tid);
        if (!ticket) throw new Error(`Ticket #${tid} not found`);

        const newStatus = body.status || 'OPEN';
        ticket.status = newStatus;
        ticket.updatedAt = new Date().toISOString();
        if (newStatus === 'RESOLVED' || newStatus === 'CLOSED') {
          ticket.escalated = false;
        }

        ticket.history.push({
          id: ticket.history.length + 1,
          action: 'STATUS_CHANGED',
          details: `Status set to ${newStatus}${body.note ? ': ' + body.note : ''}`,
          changedBy: store.supervisor.fullName,
          timestamp: ticket.updatedAt
        });

        saveStore(store);
        return { success: true, message: 'Ticket status updated successfully', data: ticket };
      }

      // Add Reply: /tickets/{id}/replies
      const replyMatch = pathOnly.match(/^\/tickets\/(\d+)\/replies$/);
      if (replyMatch && method === 'POST') {
        const tid = parseInt(replyMatch[1], 10);
        const ticket = store.tickets.find(t => t.id === tid);
        if (!ticket) throw new Error(`Ticket #${tid} not found`);

        const isInternal = body.isInternal === true || body.isInternal === 'true';
        const msg = body.message || '';
        const newReply = {
          id: ticket.replies.length + 1,
          senderName: store.supervisor.fullName,
          senderRole: 'OPERATIONS_SUPERVISOR',
          message: msg,
          isInternal,
          createdAt: new Date().toISOString()
        };
        ticket.replies.push(newReply);
        ticket.updatedAt = newReply.createdAt;

        ticket.history.push({
          id: ticket.history.length + 1,
          action: isInternal ? 'INTERNAL_NOTE' : 'REPLY_SENT',
          details: isInternal ? 'Supervisor added internal note' : 'Supervisor sent message to customer',
          changedBy: store.supervisor.fullName,
          timestamp: newReply.createdAt
        });

        saveStore(store);
        return { success: true, message: 'Reply submitted successfully', data: newReply };
      }

      // 3. AGENTS
      if (pathOnly === '/agents' && method === 'GET') {
        return { success: true, data: store.agents };
      }

      if (pathOnly === '/agents' && method === 'POST') {
        const newAgent = {
          id: store.agents.length > 0 ? Math.max(...store.agents.map(a => a.id)) + 1 : 201,
          fullName: body.fullName || 'New Support Officer',
          email: body.email || 'officer@demo.com',
          role: 'CUSTOMER_SERVICE_OFFICER',
          department: body.department || 'Technical Support',
          designation: body.designation || 'Customer Service Officer',
          phone: body.phone || '+1 (555) 000-0000',
          status: 'AVAILABLE',
          activeTickets: 0,
          resolvedTickets: 0,
          rating: 5.0,
          categoryIds: body.categoryIds || [1],
          categories: (body.categoryIds || [1]).map(cid => {
            const c = store.categories.find(cat => cat.id === cid);
            return { id: cid, name: c ? c.name : 'Category' };
          })
        };
        store.agents.push(newAgent);
        saveStore(store);
        return { success: true, message: 'Support officer added successfully', data: newAgent };
      }

      const agentIdMatch = pathOnly.match(/^\/agents\/(\d+)$/);
      if (agentIdMatch) {
        const aid = parseInt(agentIdMatch[1], 10);
        const idx = store.agents.findIndex(a => a.id === aid);
        if (idx === -1) throw new Error('Agent not found');

        if (method === 'PUT') {
          Object.assign(store.agents[idx], body);
          saveStore(store);
          return { success: true, message: 'Agent updated successfully', data: store.agents[idx] };
        }
        if (method === 'DELETE') {
          store.agents.splice(idx, 1);
          saveStore(store);
          return { success: true, message: 'Agent removed from roster', data: null };
        }
      }

      const agentCatMatch = pathOnly.match(/^\/agents\/(\d+)\/categories$/);
      if (agentCatMatch && method === 'PUT') {
        const aid = parseInt(agentCatMatch[1], 10);
        const agent = store.agents.find(a => a.id === aid);
        if (!agent) throw new Error('Agent not found');

        const catIds = body.categoryIds || [];
        agent.categoryIds = catIds;
        agent.categories = catIds.map(cid => {
          const c = store.categories.find(cat => cat.id === cid);
          return { id: cid, name: c ? c.name : 'Category' };
        });
        saveStore(store);
        return { success: true, message: 'Assigned categories updated successfully', data: agent };
      }

      // 4. CATEGORIES
      if ((pathOnly === '/categories' || pathOnly === '/categories/active') && method === 'GET') {
        return { success: true, data: store.categories };
      }

      if (pathOnly === '/categories' && method === 'POST') {
        const newCat = {
          id: store.categories.length > 0 ? Math.max(...store.categories.map(c => c.id)) + 1 : 1,
          name: body.name || 'New Category',
          description: body.description || '',
          icon: body.icon || 'fa-folder',
          slaHours: parseInt(body.slaHours, 10) || 24,
          active: true,
          subcategories: []
        };
        store.categories.push(newCat);
        saveStore(store);
        return { success: true, message: 'Category created successfully', data: newCat };
      }

      const catIdMatch = pathOnly.match(/^\/categories\/(\d+)$/);
      if (catIdMatch) {
        const cid = parseInt(catIdMatch[1], 10);
        const idx = store.categories.findIndex(c => c.id === cid);
        if (idx === -1) throw new Error('Category not found');

        if (method === 'PUT') {
          Object.assign(store.categories[idx], body);
          saveStore(store);
          return { success: true, message: 'Category updated successfully', data: store.categories[idx] };
        }
        if (method === 'DELETE') {
          store.categories.splice(idx, 1);
          saveStore(store);
          return { success: true, message: 'Category deleted', data: null };
        }
      }

      const catToggleMatch = pathOnly.match(/^\/categories\/(\d+)\/toggle-active$/);
      if (catToggleMatch) {
        const cid = parseInt(catToggleMatch[1], 10);
        const cat = store.categories.find(c => c.id === cid);
        if (cat) {
          cat.active = !cat.active;
          saveStore(store);
          return { success: true, message: 'Category active state toggled', data: cat };
        }
      }

      const subcatMatch = pathOnly.match(/^\/categories\/(\d+)\/subcategories$/);
      if (subcatMatch) {
        const cid = parseInt(subcatMatch[1], 10);
        const cat = store.categories.find(c => c.id === cid);
        if (!cat) throw new Error('Parent category not found');

        if (method === 'GET') {
          return { success: true, data: cat.subcategories || [] };
        }
        if (method === 'POST') {
          const newSub = {
            id: (cat.subcategories && cat.subcategories.length > 0) ? Math.max(...cat.subcategories.map(s => s.id)) + 1 : 1,
            name: body.name || 'New Subcategory',
            description: body.description || '',
            active: true
          };
          cat.subcategories = cat.subcategories || [];
          cat.subcategories.push(newSub);
          saveStore(store);
          return { success: true, message: 'Subcategory created', data: newSub };
        }
      }

      // 5. CUSTOMERS
      if (pathOnly === '/customers' && method === 'GET') {
        return {
          success: true,
          data: {
            content: store.customers,
            totalElements: store.customers.length,
            totalPages: 1
          }
        };
      }

      if (pathOnly === '/customers' && method === 'POST') {
        const newCust = {
          id: store.customers.length > 0 ? Math.max(...store.customers.map(c => c.id)) + 1 : 301,
          fullName: body.fullName || 'New Customer',
          email: body.email || 'customer@demo-client.com',
          phone: body.phone || '+1 (555) 000-0000',
          address: body.address || 'Enterprise HQ',
          status: 'ACTIVE',
          totalTickets: 0,
          openTickets: 0,
          createdAt: new Date().toISOString()
        };
        store.customers.push(newCust);
        saveStore(store);
        return { success: true, message: 'Customer account created', data: newCust };
      }

      const custIdMatch = pathOnly.match(/^\/customers\/(\d+)$/);
      if (custIdMatch) {
        const cid = parseInt(custIdMatch[1], 10);
        const idx = store.customers.findIndex(c => c.id === cid);
        if (idx === -1) throw new Error('Customer not found');

        if (method === 'PUT') {
          Object.assign(store.customers[idx], body);
          saveStore(store);
          return { success: true, message: 'Customer account updated', data: store.customers[idx] };
        }
        if (method === 'DELETE') {
          store.customers.splice(idx, 1);
          saveStore(store);
          return { success: true, message: 'Customer account archived', data: null };
        }
      }

      const custReactivateMatch = pathOnly.match(/^\/customers\/(\d+)\/reactivate$/);
      if (custReactivateMatch) {
        const cid = parseInt(custReactivateMatch[1], 10);
        const cust = store.customers.find(c => c.id === cid);
        if (cust) {
          cust.status = 'ACTIVE';
          saveStore(store);
          return { success: true, message: 'Customer account reactivated', data: cust };
        }
      }

      if (pathOnly === '/customers/check-inactivity') {
        return { success: true, message: 'Inactivity audit completed. Zero accounts flagged.' };
      }

      // 6. NOTIFICATIONS
      if (pathOnly === '/notifications/unread-count') {
        const unread = store.notifications.filter(n => !n.isRead && !n.read).length;
        return { success: true, data: unread };
      }

      if (pathOnly === '/notifications' && method === 'GET') {
        return { success: true, data: store.notifications };
      }

      const notifReadMatch = pathOnly.match(/^\/notifications\/(\d+)\/read$/);
      if (notifReadMatch) {
        const nid = parseInt(notifReadMatch[1], 10);
        const notif = store.notifications.find(n => n.id === nid);
        if (notif) {
          notif.isRead = true;
          notif.read = true;
        }
        saveStore(store);
        return { success: true, message: 'Marked notification as read' };
      }

      if (pathOnly === '/notifications/mark-all-read' || pathOnly === '/notifications/read-all') {
        store.notifications.forEach(n => {
          n.isRead = true;
          n.read = true;
        });
        saveStore(store);
        return { success: true, message: 'All notifications marked as read' };
      }

      // 7. FEEDBACK & ANALYTICS
      if (pathOnly === '/feedback') {
        return { success: true, data: store.feedback };
      }

      if (pathOnly === '/feedback/analytics') {
        return {
          success: true,
          data: {
            totalFeedback: 35,
            averageRating: 4.86,
            csatPercentage: 94.2,
            ratingsDistribution: { 5: 29, 4: 5, 3: 1, 2: 0, 1: 0 },
            averageResponseTimeHours: 2.1,
            averageResolutionTimeHours: 6.5
          }
        };
      }

      // 8. HOME & PUBLIC ENDPOINTS
      if (pathOnly === '/help/stats') {
        return {
          success: true,
          data: {
            activeTickets: store.tickets.filter(t => t.status !== 'RESOLVED' && t.status !== 'CLOSED').length,
            resolvedTickets: 284,
            customerCount: store.customers.length,
            slaCompliancePercent: 98.4
          }
        };
      }

      if (pathOnly === '/faqs/public') {
        return {
          success: true,
          data: [
            { id: 1, question: 'How do I raise an urgent production support ticket?', answer: 'Navigate to Create Ticket, choose your category and select Priority: Urgent.' },
            { id: 2, question: 'What are the response time guarantees for Tier 1 tickets?', answer: 'Tier 1 technical tickets feature a 4-hour initial SLA guarantee.' },
            { id: 3, question: 'How can I track the live status of my shipment?', answer: 'Open your ticket or check Order Tracking using your tracking reference number.' }
          ]
        };
      }

      if (pathOnly === '/knowledge-base/public') {
        return {
          success: true,
          data: [
            { id: 1, title: 'Getting Started with SupportHUB', summary: 'Quick start guide to customer portal navigation and tracking.' },
            { id: 2, title: 'Configuring 2FA and Multi-Factor Devices', summary: 'Step-by-step instructions for setting up mobile authenticators.' },
            { id: 3, title: 'Understanding SLA Response and Resolution Windows', summary: 'Detailed policy documentation for operational tier benchmarks.' }
          ]
        };
      }

      if (pathOnly === '/help/search') {
        return {
          success: true,
          data: {
            faqs: [
              { id: 1, question: 'How do I submit an emergency ticket?', answer: 'Select Urgent priority on the ticket creation form.' }
            ],
            articles: [
              { id: 1, title: 'Knowledge Base Quickstart', summary: 'Explore self-service articles and solutions.' }
            ]
          }
        };
      }

      if (pathOnly === '/chatbot/query') {
        const q = (body.question || '').toLowerCase();
        let reply = 'Thank you for reaching out to SupportHUB Smart Assistant. You can browse our knowledge base or submit a support ticket for specialized assistance.';
        if (q.includes('ticket') || q.includes('create')) {
          reply = 'You can submit a new ticket anytime via the customer dashboard or explore existing tickets in the tickets view.';
        } else if (q.includes('sla') || q.includes('time')) {
          reply = 'Our standard SLA response ranges from 4 hours for Technical Support to 24 hours for Billing inquiries.';
        } else if (q.includes('password') || q.includes('login')) {
          reply = 'For login assistance, use the Forgot Password link on the sign in page or contact your Operations Supervisor.';
        }
        return { success: true, data: { answer: reply } };
      }

      // Default fallback
      return { success: true, data: {} };
    },

    // --- Render Demo Mode Banner in DOM ---
    renderBanner() {
      if (typeof document === 'undefined') return;
      if (!this.isDemoActive()) return;
      if (document.getElementById('demo-mode-banner')) return;

      const banner = document.createElement('div');
      banner.id = 'demo-mode-banner';
      banner.className = 'demo-environment-banner';
      banner.setAttribute('role', 'alert');
      banner.innerHTML = `
        <div class="demo-banner-inner">
          <div class="demo-banner-left">
            <span class="demo-pill"><i class="fa-solid fa-flask"></i> DEMO MODE</span>
            <span class="demo-msg"><strong>DEMO ENVIRONMENT</strong> – Sample data only. Changes are not connected to a live database.</span>
          </div>
          <div class="demo-banner-right">
            <button type="button" class="btn-demo-banner btn-demo-reset" onclick="DemoService.resetData()" title="Reset all demo tickets, customers, and data back to initial sample state">
              <i class="fa-solid fa-rotate-left"></i> Reset Sample Data
            </button>
            <button type="button" class="btn-demo-banner btn-demo-exit" onclick="Auth.logout()" title="Log out and return to sign in">
              <i class="fa-solid fa-right-from-bracket"></i> Exit Demo
            </button>
          </div>
        </div>
      `;

      // Inject banner at top of body
      document.body.prepend(banner);
      document.body.classList.add('has-demo-banner');

      // Inject banner styles dynamically if not in stylesheet
      if (!document.getElementById('demo-banner-styles')) {
        const style = document.createElement('style');
        style.id = 'demo-banner-styles';
        style.textContent = `
          .demo-environment-banner {
            position: sticky;
            top: 0;
            left: 0;
            width: 100%;
            z-index: 99999;
            background: linear-gradient(90deg, #000985 0%, #171f7a 50%, #000766 100%);
            border-bottom: 2px solid #F5CC00;
            color: #ffffff;
            font-family: 'Inter', -apple-system, sans-serif;
            font-size: 0.815rem;
            box-shadow: 0 4px 12px rgba(0, 9, 133, 0.15);
          }
          .demo-banner-inner {
            max-width: 1600px;
            margin: 0 auto;
            padding: 0.5rem 1.25rem;
            display: flex;
            align-items: center;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 0.75rem;
          }
          .demo-banner-left {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            flex-wrap: wrap;
          }
          .demo-pill {
            background: #F5CC00;
            color: #000985;
            font-weight: 800;
            font-size: 0.725rem;
            letter-spacing: 0.04em;
            padding: 0.2rem 0.6rem;
            border-radius: 999px;
            display: inline-flex;
            align-items: center;
            gap: 0.35rem;
          }
          .demo-msg {
            color: #f1f5f9;
            font-weight: 500;
          }
          .demo-msg strong {
            color: #ffffff;
            font-weight: 700;
          }
          .demo-banner-right {
            display: flex;
            align-items: center;
            gap: 0.5rem;
          }
          .btn-demo-banner {
            border: none;
            cursor: pointer;
            font-size: 0.75rem;
            font-weight: 700;
            padding: 0.3rem 0.75rem;
            border-radius: 6px;
            display: inline-flex;
            align-items: center;
            gap: 0.4rem;
            transition: all 0.2s ease;
          }
          .btn-demo-reset {
            background: rgba(255, 255, 255, 0.16);
            color: #ffffff;
            border: 1px solid rgba(255, 255, 255, 0.25);
          }
          .btn-demo-reset:hover {
            background: rgba(255, 255, 255, 0.28);
          }
          .btn-demo-exit {
            background: rgba(239, 68, 68, 0.25);
            color: #fecaca;
            border: 1px solid rgba(239, 68, 68, 0.4);
          }
          .btn-demo-exit:hover {
            background: rgba(239, 68, 68, 0.4);
            color: #ffffff;
          }
          body.has-demo-banner .top-header {
            top: 42px !important;
          }
          @media (max-width: 768px) {
            .demo-banner-inner {
              padding: 0.45rem 0.75rem;
              font-size: 0.75rem;
            }
            .demo-msg {
              font-size: 0.725rem;
            }
            body.has-demo-banner .top-header {
              top: 64px !important;
            }
          }
        `;
        document.head.appendChild(style);
      }
    }
  };

  // Mount DemoService globally
  window.DemoService = DemoService;

  // Auto-mount banner when DOM is ready if demo mode is active
  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => {
        DemoService.renderBanner();
      });
    } else {
      DemoService.renderBanner();
    }
  }

})(typeof window !== 'undefined' ? window : this);
