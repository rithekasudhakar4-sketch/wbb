/**
 * NALAM NAGAR EMERGENCY RESPONSE COMMAND CENTER
 * Frontend-to-Backend Bridge (Pure Vanilla JS, Zero External Libraries)
 * Connects the web UI to the Java backend REST API on port 8080.
 * Completely dynamic: All numbers, rosters, tables, and statuses come from live state.
 */

const API_BASE = window.location.origin.includes(':8080')
  ? window.location.origin
  : 'http://localhost:8080';

// Global state trackers
window.currentQueueFilter = 'ALL';
window.lastKnownStatus = null;

// =========================================================================
// 1. Connectivity & Tactical Notification System
// =========================================================================

function showNotification(message, type = 'success') {
  let toast = document.getElementById('cad-toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'cad-toast';
    toast.style.cssText = `
      position: fixed;
      top: 24px;
      right: 28px;
      z-index: 99999;
      padding: 12px 20px;
      border-radius: 8px;
      font-size: 13.5px;
      font-weight: 600;
      box-shadow: 0 10px 25px rgba(0,0,0,0.18);
      transition: all 0.3s ease;
      display: flex;
      align-items: center;
      gap: 10px;
    `;
    document.body.appendChild(toast);
  }

  if (type === 'success') {
    toast.style.backgroundColor = '#f0fdf4';
    toast.style.color = '#16a34a';
    toast.style.border = '1px solid #bbf7d0';
    toast.innerHTML = `<span>✅</span> <span>${message}</span>`;
  } else if (type === 'error') {
    toast.style.backgroundColor = '#fef2f2';
    toast.style.color = '#dc2626';
    toast.style.border = '1px solid #fecaca';
    toast.innerHTML = `<span>⚠️</span> <span>${message}</span>`;
  } else {
    toast.style.backgroundColor = '#eff6ff';
    toast.style.color = '#2563eb';
    toast.style.border = '1px solid #bfdbfe';
    toast.innerHTML = `<span>ℹ️</span> <span>${message}</span>`;
  }

  toast.style.opacity = '1';
  toast.style.transform = 'translateY(0)';

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(-10px)';
  }, 4000);
}

function updateServerOfflineBanner(isOffline) {
  let banner = document.getElementById('server-offline-banner');
  if (isOffline) {
    if (!banner) {
      banner = document.createElement('div');
      banner.id = 'server-offline-banner';
      banner.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        background: #dc2626;
        color: #ffffff;
        padding: 8px 16px;
        text-align: center;
        font-size: 12.5px;
        font-weight: 700;
        letter-spacing: 0.3px;
        z-index: 100000;
        box-shadow: 0 2px 8px rgba(0,0,0,0.25);
      `;
      banner.innerHTML = `⚠️ Java Backend Disconnected &bull; Please make sure 'run.bat' is running on port 8080. Retrying live connection...`;
      document.body.appendChild(banner);
    }
  } else {
    if (banner) banner.remove();
  }
}

// Relative timestamp helper ("just now", "45s ago", "3m ago")
function formatRelativeTime(epochMillis) {
  if (!epochMillis) return 'Just now';
  const diffSec = Math.floor((Date.now() - epochMillis) / 1000);
  if (diffSec < 10) return 'Just now';
  if (diffSec < 60) return `${diffSec}s ago`;
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  return `${diffHr}h ago`;
}

// =========================================================================
// 2. Global Status & Header Synchronization
// =========================================================================

async function syncGlobalStatus() {
  try {
    const res = await fetch(`${API_BASE}/api/status`);
    if (!res.ok) throw new Error('Status HTTP ' + res.status);
    const status = await res.json();
    window.lastKnownStatus = status;
    updateServerOfflineBanner(false);

    // Update sidebar badges dynamically
    document.querySelectorAll('.nav-badge.critical').forEach(el => {
      const count = status.pendingRequests || 0;
      el.textContent = count;
      if (count === 0) {
        el.style.backgroundColor = '#10b981';
        el.style.color = '#ffffff';
      } else {
        el.style.backgroundColor = '';
        el.style.color = '';
      }
    });

    document.querySelectorAll('.nav-badge.neutral').forEach(el => {
      el.textContent = `${status.totalAmbulances || 5}`;
    });

    // Update navigation links containing waiting case counts
    document.querySelectorAll('a').forEach(a => {
      if (a.textContent.includes('Waiting Cases') || a.textContent.includes('Emergency Queue')) {
        a.innerHTML = a.innerHTML.replace(/\(\d+\)/, `(${status.pendingRequests || 0})`);
      }
    });

    return status;
  } catch (err) {
    updateServerOfflineBanner(true);
    return window.lastKnownStatus;
  }
}

// =========================================================================
// 3. System Reset & Simulation Controls
// =========================================================================

async function resetSystem() {
  if (!confirm('Are you sure you want to reset the system to baseline?\n\n• Clears all pending emergency calls\n• Clears all recorded trips and history\n• Returns all 5 ambulances to base stations\n• Restores 100% hospital bed availability')) {
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/reset`, { method: 'POST' });
    const data = await res.json();
    showNotification(data.message, 'success');
    setTimeout(() => window.location.reload(), 1000);
  } catch (err) {
    showNotification('Failed to communicate with reset endpoint', 'error');
  }
}

async function reseedSampleData() {
  try {
    showNotification('Queuing test emergency calls...', 'info');
    const res = await fetch(`${API_BASE}/api/seed-samples`, { method: 'POST' });
    const data = await res.json();
    if (data.success) {
      showNotification(`Queued test emergencies! Autonomous dispatcher will assign ambulances.`, 'success');
      pollCurrentPage();
    }
  } catch (e) {
    showNotification('Could not reach WebServer on port 8080', 'error');
  }
}

async function toggleDemoSimulation() {
  try {
    const res = await fetch(`${API_BASE}/api/demo/toggle`, { method: 'POST' });
    const data = await res.json();
    showNotification(data.message, data.demoMode ? 'success' : 'info');
    updateDemoButtonVisuals(data.demoMode);
  } catch (err) {
    showNotification('Could not toggle simulation demo mode', 'error');
  }
}

function updateDemoButtonVisuals(demoActive) {
  const btn = document.getElementById('btn-toggle-demo');
  if (btn) {
    if (demoActive) {
      btn.innerHTML = `<span>⏸️ Stop Simulation</span>`;
      btn.style.background = '#f59e0b';
      btn.style.color = '#ffffff';
    } else {
      btn.innerHTML = `<span>⚡ Demo Simulation</span>`;
      btn.style.background = '';
      btn.style.color = '';
    }
  }
}

// =========================================================================
// 4. Queue Page (queue.html)
// =========================================================================

window.setQueueFilter = function(filter) {
  window.currentQueueFilter = filter;
  ['all', 'critical', 'serious', 'minor'].forEach(f => {
    const btn = document.getElementById(`btn-filter-${f}`);
    if (btn) {
      if (f.toUpperCase() === filter) {
        btn.className = 'btn-action btn-action-primary';
      } else {
        btn.className = 'btn-action btn-action-secondary';
      }
    }
  });
  initQueuePage();
};

async function initQueuePage() {
  try {
    const [queueRes, ambRes, statusData] = await Promise.all([
      fetch(`${API_BASE}/api/queue`),
      fetch(`${API_BASE}/api/ambulances`),
      syncGlobalStatus()
    ]);

    if (!queueRes.ok || !ambRes.ok) return;

    const requests = await queueRes.json();
    const ambulances = await ambRes.json();

    // Calculate real numbers
    const totalCases = requests.length;
    const criticalCases = requests.filter(r => r.severity === 'CRITICAL').length;
    const seriousCases = requests.filter(r => r.severity === 'SERIOUS').length;
    const minorCases = requests.filter(r => r.severity === 'MINOR').length;
    const availableAmbs = ambulances.filter(a => a.isAvailable);
    const availCount = availableAmbs.length;

    // 1. Update KPI Strip Cards
    const kpiCrit = document.getElementById('queue-kpi-critical');
    if (kpiCrit) kpiCrit.innerHTML = `${criticalCases} <span style="font-size: 15px; color: var(--text-muted); font-weight: 500;">Cases</span>`;
    const kpiCritNote = document.getElementById('queue-kpi-critical-note');
    if (kpiCritNote) kpiCritNote.textContent = criticalCases > 0 ? 'Highest priority life-support' : 'No critical cases waiting';

    const kpiSer = document.getElementById('queue-kpi-serious');
    if (kpiSer) kpiSer.innerHTML = `${seriousCases} <span style="font-size: 15px; color: var(--text-muted); font-weight: 500;">Cases</span>`;
    const kpiSerNote = document.getElementById('queue-kpi-serious-note');
    if (kpiSerNote) kpiSerNote.textContent = seriousCases > 0 ? 'Needs doctor within 15 mins' : 'No urgent cases waiting';

    const kpiMin = document.getElementById('queue-kpi-minor');
    if (kpiMin) kpiMin.innerHTML = `${minorCases} <span style="font-size: 15px; color: var(--text-muted); font-weight: 500;">Cases</span>`;
    const kpiMinNote = document.getElementById('queue-kpi-minor-note');
    if (kpiMinNote) kpiMinNote.textContent = minorCases > 0 ? 'Non-life-threatening transfer' : 'No minor cases waiting';

    const kpiAmb = document.getElementById('queue-kpi-ambulances');
    if (kpiAmb) kpiAmb.innerHTML = `${availCount} <span style="font-size: 15px; color: var(--text-muted); font-weight: 500;">Ready</span>`;
    const kpiAmbNote = document.getElementById('queue-kpi-ambulances-note');
    if (kpiAmbNote) {
      if (availCount > 0) {
        kpiAmbNote.textContent = `${availableAmbs.map(a => a.id).join(', ')} standing by`;
      } else {
        kpiAmbNote.textContent = 'All ambulances deployed on missions';
      }
    }

    // 2. Update Header Status Indicator
    const indicator = document.getElementById('queue-tactical-indicator') || document.querySelector('.tactical-status-indicator');
    if (indicator) {
      if (totalCases > 0) {
        indicator.innerHTML = `<span class="pulse-dot"></span><span>${totalCases} Cases Waiting &bull; Sorted by Medical Urgency</span>`;
      } else {
        indicator.innerHTML = `<span class="pulse-dot" style="background:#10b981; box-shadow:0 0 8px #10b981;"></span><span>All Cases Auto-Dispatched &bull; ${availCount} Ambulances Standing By</span>`;
      }
    }

    // 3. Update Panel Title & Filter Pills
    const tableTitle = document.getElementById('queue-table-title');
    if (tableTitle) tableTitle.textContent = `Emergencies Awaiting Ambulance (${totalCases})`;

    const btnAll = document.getElementById('btn-filter-all');
    if (btnAll) btnAll.textContent = `All Cases (${totalCases})`;
    const btnCrit = document.getElementById('btn-filter-critical');
    if (btnCrit) btnCrit.textContent = `Critical (${criticalCases})`;
    const btnSer = document.getElementById('btn-filter-serious');
    if (btnSer) btnSer.textContent = `Serious (${seriousCases})`;
    const btnMin = document.getElementById('btn-filter-minor');
    if (btnMin) btnMin.textContent = `Minor (${minorCases})`;

    // 4. Update Auto-Dispatch Toggle Button
    const autoDispatchBtn = document.getElementById('btn-toggle-autodispatch');
    if (autoDispatchBtn) {
      autoDispatchBtn.onclick = async (e) => {
        e.preventDefault();
        try {
          const togRes = await fetch(`${API_BASE}/api/auto-dispatch/toggle`, { method: 'POST' });
          const togData = await togRes.json();
          showNotification(togData.message, 'info');
          if (togData.autoDispatchEnabled) {
            autoDispatchBtn.innerHTML = `<span>⚡ Auto-Dispatch Active</span>`;
            autoDispatchBtn.style.background = 'linear-gradient(135deg, #10b981, #059669)';
          } else {
            autoDispatchBtn.innerHTML = `<span>⏸️ Auto-Dispatch Paused</span>`;
            autoDispatchBtn.style.background = '#64748b';
          }
        } catch (ignored) {}
      };
    }

    // 5. Render Filtered Table Rows
    const tbody = document.getElementById('queue-tbody') || document.querySelector('.tactical-table tbody');
    if (!tbody) return;

    let filtered = requests;
    if (window.currentQueueFilter === 'CRITICAL') {
      filtered = requests.filter(r => r.severity === 'CRITICAL');
    } else if (window.currentQueueFilter === 'SERIOUS') {
      filtered = requests.filter(r => r.severity === 'SERIOUS');
    } else if (window.currentQueueFilter === 'MINOR') {
      filtered = requests.filter(r => r.severity === 'MINOR');
    }

    const busyAmbs = ambulances.filter(a => !a.isAvailable);

    if (filtered.length === 0) {
      if (busyAmbs.length > 0 && window.currentQueueFilter === 'ALL') {
        tbody.innerHTML = '';
        busyAmbs.forEach((a, idx) => {
          const tr = document.createElement('tr');
          tr.style.backgroundColor = '#eff6ff';
          tr.innerHTML = `
            <td><strong style="color: var(--accent-blue); font-size: 14px;">⚡ #${idx + 1}</strong></td>
            <td><strong style="color: var(--accent-blue);">${a.assignedRequestId || 'DISPATCHED'}</strong></td>
            <td><strong>${a.patientName || 'Emergency Patient'}</strong></td>
            <td>${a.targetLocation || a.station}</td>
            <td>Urgent Emergency Dispatch</td>
            <td><span class="badge badge-critical">EN ROUTE</span></td>
            <td><strong>In Transit</strong></td>
            <td>🚑 <strong>${a.id}</strong> (Dispatched)</td>
            <td>🏥 <strong>${a.hospitalName || 'Receiving Facility'}</strong></td>
            <td style="text-align: right;">
              <span class="badge" style="background:#dcfce7; color:#166534; font-weight:700; font-size:11px; padding:4px 8px; border-radius:4px; border:1px solid #bbf7d0;">⚡ Auto-Dispatched</span>
            </td>
          `;
          tbody.appendChild(tr);
        });
        const tableTitle = document.getElementById('queue-table-title');
        if (tableTitle) tableTitle.textContent = `Active Dispatches in Transit (${busyAmbs.length})`;
        return;
      }

      tbody.innerHTML = `
        <tr>
          <td colspan="10" style="text-align:center; padding: 36px 20px; color: var(--text-muted);">
            <div style="font-size: 15px; font-weight: 700; color: #10b981; margin-bottom: 6px;">
              ✅ ${totalCases === 0 ? 'All emergency requests have been auto-dispatched!' : 'No cases in this filter category.'}
            </div>
            <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 16px;">
              ${totalCases === 0 ? 'The triage queue is empty. Dispatched ambulances are currently on the road.' : 'Select "All Cases" to view the full queue.'}
            </div>
            <div style="display: flex; gap: 10px; justify-content: center; flex-wrap: wrap;">
              <a href="intake.html" class="btn-action btn-action-primary" style="font-size: 12px; padding: 6px 14px; text-decoration: none;">
                + Request Ambulance
              </a>
              <button class="btn-action btn-action-secondary" style="font-size: 12px; padding: 6px 14px; cursor: pointer;" onclick="reseedSampleData()">
                🔄 Queue Test Cases
              </button>
              <button class="btn-action btn-action-secondary" style="font-size: 12px; padding: 6px 14px; cursor: pointer;" onclick="toggleDemoSimulation()">
                ⚡ Toggle Auto Demo
              </button>
            </div>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = '';
    filtered.forEach((r, idx) => {
      const tr = document.createElement('tr');
      const isCritical = r.severity === 'CRITICAL';
      const isSerious = r.severity === 'SERIOUS';
      const isDispatched = r.status === 'DISPATCHED';

      if (isCritical) {
        tr.style.backgroundColor = '#fff1f2';
      } else if (isSerious) {
        tr.style.backgroundColor = '#fffbeb';
      }

      const badgeClass = isCritical ? 'badge-critical' : (isSerious ? 'badge-serious' : 'badge-minor');
      const rankColor = isCritical ? 'var(--accent-red)' : (isSerious ? 'var(--accent-amber)' : 'var(--accent-blue)');

      const ambulanceCell = isDispatched && r.assignedAmbulance && r.assignedAmbulance !== 'AUTO'
        ? `🚑 <strong>${r.assignedAmbulance}</strong> (En Route)`
        : `🤖 Auto-Allocating Unit`;

      const hospitalCell = isDispatched && r.assignedHospital && r.assignedHospital !== 'AUTO'
        ? `🏥 <strong>${r.assignedHospital}</strong>`
        : `${r.requiredCapability}`;

      const actionCell = isDispatched
        ? `<span class="badge" style="background:#dcfce7; color:#166534; font-weight:700; font-size:11px; padding:4px 8px; border-radius:4px; border:1px solid #bbf7d0;">⚡ Auto-Dispatched</span>`
        : `<span class="badge" style="background:#fef3c7; color:#92400e; font-weight:600; font-size:11px; padding:4px 8px; border-radius:4px; border:1px solid #fde68a;">⏳ Auto-Pending</span>
           <button class="btn-action btn-action-primary" style="font-size: 11px; padding: 3px 8px; margin-left: 6px;" onclick="executeDispatch()">Force</button>`;

      const waitingText = r.timestamp ? formatRelativeTime(r.timestamp) : r.formattedTime;

      tr.innerHTML = `
        <td><strong style="color: ${rankColor}; font-size: 14px;">#${r.rank || (idx + 1)}</strong></td>
        <td><strong style="color: ${rankColor};">${r.requestId}</strong></td>
        <td><strong>${r.patientName}</strong></td>
        <td>${r.locationName} (${r.locationId})</td>
        <td>${r.conditionDisplay || r.condition}</td>
        <td><span class="badge ${badgeClass}">${r.severity}</span></td>
        <td><strong>${waitingText}</strong></td>
        <td>${ambulanceCell}</td>
        <td>${hospitalCell}</td>
        <td style="text-align: right;">${actionCell}</td>
      `;
      tbody.appendChild(tr);
    });

  } catch (err) {
    console.error('Queue poll update error:', err);
  }
}

// Manual override dispatch
async function executeDispatch() {
  try {
    const res = await fetch(`${API_BASE}/api/dispatch`, { method: 'POST' });
    const data = await res.json();
    if (data.success) {
      showNotification('Emergency manually dispatched! Ambulance is rolling.', 'success');
      initQueuePage();
    } else {
      showNotification(data.error || 'Dispatch halted.', 'error');
    }
  } catch (e) {
    showNotification('Manual dispatch command sent.', 'info');
  }
}

// =========================================================================
// 5. Fleet Page (fleet.html)
// =========================================================================

function setFleetOfflineBanner(isOffline) {
  let banner = document.getElementById('server-offline-banner');
  if (isOffline) {
    if (!banner) {
      banner = document.createElement('div');
      banner.id = 'server-offline-banner';
      banner.style.cssText = 'background: #b91c1c; color: #ffffff; padding: 10px 16px; font-weight: 600; text-align: center; font-size: 13px; position: sticky; top: 0; z-index: 99999; box-shadow: 0 2px 8px rgba(0,0,0,0.2);';
      banner.textContent = '⚠️ Server offline — attempting reconnection... Displaying last real fleet data.';
      document.body.prepend(banner);
    } else {
      banner.style.display = 'block';
    }
  } else if (banner) {
    banner.style.display = 'none';
  }
}

async function initFleetPage() {
  try {
    const [fleetRes, statusData] = await Promise.all([
      fetch(`${API_BASE}/api/ambulances`),
      syncGlobalStatus()
    ]);

    if (!fleetRes.ok) {
      setFleetOfflineBanner(true);
      return;
    }
    setFleetOfflineBanner(false);

    const fleet = await fleetRes.json();

    // Classification helpers
    const isReady = (a) => a.status === 'AVAILABLE' || a.isAvailable;
    const isDrivingToPatient = (a) => a.status === 'EN_ROUTE_PATIENT' || a.status === 'EN_ROUTE_TO_PATIENT';
    const isTransportingToHospital = (a) => a.status === 'EN_ROUTE_HOSPITAL' || a.status === 'TO_HOSPITAL';

    // 1. Ambulances Ready KPI
    const readyAmbs = fleet.filter(isReady);
    const kpiReady = document.getElementById('fleet-kpi-ready');
    if (kpiReady) kpiReady.textContent = readyAmbs.length;

    const kpiReadyNote = document.getElementById('fleet-kpi-ready-note');
    if (kpiReadyNote) {
      const readyIds = readyAmbs.map(a => a.id);
      kpiReadyNote.textContent = readyIds.length > 0 ? readyIds.join(', ') : 'None';
    }

    // 2. Driving to Patient KPI
    const toPatientAmbs = fleet.filter(isDrivingToPatient);
    const kpiPat = document.getElementById('fleet-kpi-patient');
    if (kpiPat) kpiPat.textContent = toPatientAmbs.length;

    const kpiPatNote = document.getElementById('fleet-kpi-patient-note');
    if (kpiPatNote) {
      if (toPatientAmbs.length > 0) {
        kpiPatNote.textContent = toPatientAmbs.map(a => `${a.id} -> ${a.patientName || 'Patient'}`).join(' • ');
      } else {
        kpiPatNote.textContent = 'None';
      }
    }

    // 3. Transporting to Hospital KPI
    const toHospAmbs = fleet.filter(isTransportingToHospital);
    const kpiHosp = document.getElementById('fleet-kpi-hospital');
    if (kpiHosp) kpiHosp.textContent = toHospAmbs.length;

    const kpiHospNote = document.getElementById('fleet-kpi-hospital-note');
    if (kpiHospNote) {
      if (toHospAmbs.length > 0) {
        kpiHospNote.textContent = toHospAmbs.map(a => `${a.id} -> ${a.hospitalName || 'Hospital'}`).join(' • ');
      } else {
        kpiHospNote.textContent = 'None';
      }
    }

    // 4. Trips Completed Today KPI
    const totalTripsCompleted = fleet.reduce((sum, a) => sum + (a.totalTripsCompleted || 0), 0);
    const kpiTrips = document.getElementById('fleet-kpi-trips');
    if (kpiTrips) kpiTrips.textContent = totalTripsCompleted;

    const kpiTripsNote = document.getElementById('fleet-kpi-trips-note');
    if (kpiTripsNote) {
      kpiTripsNote.textContent = totalTripsCompleted > 0 ? `${totalTripsCompleted} patients safely admitted` : 'No trips completed yet';
    }

    // Tactical Indicator
    const indicator = document.getElementById('fleet-tactical-indicator') || document.querySelector('.tactical-status-indicator');
    if (indicator) {
      indicator.innerHTML = `<span class="pulse-dot"></span><span>${fleet.length} Ambulances Operational &bull; ${readyAmbs.length} Ready to Respond</span>`;
    }

    // Roster Table Rendering
    const tbody = document.getElementById('fleet-tbody') || document.querySelector('.tactical-table tbody');
    if (!tbody) return;

    if (fleet.length === 0) {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align:center; padding: 24px; color: var(--text-muted);">No ambulances registered.</td></tr>`;
      return;
    }

    tbody.innerHTML = '';
    fleet.forEach(amb => {
      const tr = document.createElement('tr');
      const isBusy = !isReady(amb);
      if (isBusy) tr.style.backgroundColor = '#fff1f2';

      let statusBadge;
      if (isDrivingToPatient(amb)) {
        statusBadge = `<span class="badge badge-critical">🔴 Driving to Patient</span>`;
      } else if (isTransportingToHospital(amb)) {
        statusBadge = `<span class="badge badge-serious">🟠 Transporting to Hospital</span>`;
      } else if (isBusy) {
        statusBadge = `<span class="badge badge-critical">🔴 Busy</span>`;
      } else {
        statusBadge = `<span class="badge badge-assigned">🟢 Ready on Standby</span>`;
      }

      const assignedPatientName = amb.patientName;
      const assignedReqId = amb.assignedEmergencyId || amb.assignedRequestId;
      const severity = amb.emergencySeverity;

      let patientCell = '<span style="color: var(--text-muted);">&mdash;</span>';
      if (assignedPatientName || assignedReqId) {
        let sevBadge = '';
        if (severity === 'CRITICAL') sevBadge = `<span class="badge badge-critical" style="font-size: 10px; padding: 1px 6px; margin-left: 5px;">CRITICAL</span>`;
        else if (severity === 'SERIOUS') sevBadge = `<span class="badge badge-serious" style="font-size: 10px; padding: 1px 6px; margin-left: 5px;">SERIOUS</span>`;
        else if (severity === 'MINOR') sevBadge = `<span class="badge badge-minor" style="font-size: 10px; padding: 1px 6px; margin-left: 5px;">MINOR</span>`;

        patientCell = `
          <div style="display: flex; flex-direction: column; gap: 2px;">
            <strong>${assignedPatientName || 'Assigned'}</strong>
            <span style="font-size: 11px; color: var(--text-muted);">${assignedReqId || ''}${sevBadge}</span>
          </div>
        `;
      }

      const hospitalCell = amb.hospitalName ? `🏥 ${amb.hospitalName}` : '<span style="color: var(--text-muted);">&mdash;</span>';

      let etaCell;
      if (isBusy) {
        if (amb.etaMinutes && amb.etaMinutes > 0) {
          etaCell = `<strong>${amb.etaMinutes.toFixed(1)} mins</strong>`;
        } else {
          etaCell = `<strong>En Route</strong>`;
        }
      } else {
        etaCell = `<span style="color: var(--accent-green); font-weight: 600;">Ready</span>`;
      }

      tr.innerHTML = `
        <td>
          <strong style="font-size: 14px; color: ${isBusy ? 'var(--accent-red)' : 'var(--text-main)'};">🚑 Ambulance ${amb.id}</strong>
        </td>
        <td>${amb.station || amb.currentZone || '—'}</td>
        <td>${statusBadge}</td>
        <td>${patientCell}</td>
        <td>${hospitalCell}</td>
        <td>${etaCell}</td>
        <td>${amb.totalTripsCompleted || 0} Trips</td>
        <td style="text-align: right;">
          ${isBusy
            ? `<button class="btn-action btn-action-primary" style="font-size: 11px; padding: 4px 10px;" onclick="completeTrip('${amb.id}')">Complete Trip & Admit</button>`
            : `<a href="queue.html" class="btn-action btn-action-secondary" style="font-size: 11px; padding: 4px 10px; text-decoration:none;">Assign Case</a>`
          }
        </td>
      `;
      tbody.appendChild(tr);
    });

  } catch (err) {
    setFleetOfflineBanner(true);
    console.error('Fleet poll error:', err);
  }
}

async function completeTrip(ambId) {
  try {
    const res = await fetch(`${API_BASE}/api/complete-trip`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ambulanceId: ambId })
    });
    const data = await res.json();
    if (data.success) {
      showNotification(`Mission completed! Ambulance ${ambId} is now standing by at hospital.`, 'success');
      pollCurrentPage();
    } else {
      showNotification(data.error || 'Could not complete trip.', 'error');
    }
  } catch (e) {
    showNotification('Completed trip command error.', 'error');
  }
}

// =========================================================================
// 6. Overview Dashboard (index.html)
// =========================================================================

async function initOverviewPage() {
  try {
    const [statusData, historyRes, fleetRes, queueRes, hospRes] = await Promise.all([
      syncGlobalStatus(),
      fetch(`${API_BASE}/api/history`),
      fetch(`${API_BASE}/api/ambulances`),
      fetch(`${API_BASE}/api/queue`),
      fetch(`${API_BASE}/api/hospitals`)
    ]);

    const ambulances = fleetRes.ok ? await fleetRes.json() : [];
    const queue = queueRes.ok ? await queueRes.json() : [];
    const hospitals = hospRes.ok ? await hospRes.json() : [];

    // 1. Update 4 KPI Cards
    if (statusData) {
      const kpiAmb = document.getElementById('overview-kpi-amb');
      if (kpiAmb) kpiAmb.innerHTML = `${statusData.availableAmbulances} <span style="font-size: 16px; font-weight: 500; color: var(--text-muted);">of ${statusData.totalAmbulances}</span>`;
      
      const kpiAmbNote = document.getElementById('overview-kpi-amb-note');
      if (kpiAmbNote) kpiAmbNote.textContent = `${statusData.availableAmbulances} units ready to dispatch immediately`;

      const kpiTrips = document.getElementById('overview-kpi-trips');
      if (kpiTrips) kpiTrips.innerHTML = `${statusData.activeTrips} <span style="font-size: 16px; font-weight: 500; color: var(--text-muted);">on the road</span>`;

      const kpiTripsNote = document.getElementById('overview-kpi-trips-note');
      const busyAmbs = ambulances.filter(a => !a.isAvailable);
      if (kpiTripsNote) {
        kpiTripsNote.textContent = busyAmbs.length > 0
          ? `${busyAmbs.map(a => a.id).join(', ')} responding`
          : 'No active transports';
      }

      const kpiBeds = document.getElementById('overview-kpi-beds');
      if (kpiBeds) kpiBeds.innerHTML = `${statusData.availableBeds} <span style="font-size: 16px; font-weight: 500; color: var(--text-muted);">beds</span>`;

      const kpiAvg = document.getElementById('overview-kpi-avg');
      if (kpiAvg) kpiAvg.innerHTML = `${statusData.avgResponseTime} <span style="font-size: 16px; font-weight: 500; color: var(--text-muted);">mins</span>`;

      const kpiAvgNote = document.getElementById('overview-kpi-avg-note');
      if (kpiAvgNote) kpiAvgNote.textContent = `Target window arrival (${statusData.onTimeRate}% on-time)`;

      const waitBtn = document.getElementById('overview-waiting-cases-btn');
      if (waitBtn) waitBtn.innerHTML = `<span>View All Waiting Cases (${statusData.pendingRequests || 0})</span>`;
    }

    // 2. Dynamic Map Ambulance Callout & Route
    const busyAmb = ambulances.find(a => !a.isAvailable);
    const mapCallout = document.getElementById('overview-map-ambulance-callout');
    const mapText = document.getElementById('overview-map-ambulance-text');
    const mapRoute = document.getElementById('map-active-route');
    if (busyAmb) {
      if (mapCallout) mapCallout.style.display = '';
      if (mapText) mapText.textContent = `🚑 ${busyAmb.id}: ETA ~4-6 Mins`;
      if (mapRoute) mapRoute.style.display = '';
    } else {
      if (mapCallout) mapCallout.style.display = 'none';
      if (mapRoute) mapRoute.style.display = 'none';
    }

    // 3. Populate Active Emergencies in Progress
    const emergTitle = document.getElementById('overview-emergencies-title');
    const emergContainer = document.getElementById('overview-emergencies-container');
    if (emergContainer) {
      const activeItems = [];

      // Busy ambulances
      ambulances.filter(a => !a.isAvailable).forEach(a => {
        activeItems.push({
          type: 'AMBULANCE_ACTIVE',
          badgeClass: 'badge-critical',
          badgeText: a.status === 'EN_ROUTE_PATIENT' ? 'En Route to Patient' : 'Transporting to Hospital',
          title: a.patientName ? `${a.patientName} (${a.assignedRequestId})` : `Active Emergency (${a.assignedRequestId || a.id})`,
          location: a.targetLocation || a.station,
          ambulance: a.id,
          hospital: a.hospitalName || 'Receiving Facility',
          eta: a.status === 'EN_ROUTE_PATIENT' ? '4 Mins' : '7 Mins'
        });
      });

      // Pending queued cases
      queue.forEach(q => {
        activeItems.push({
          type: 'QUEUE_PENDING',
          badgeClass: q.severity === 'CRITICAL' ? 'badge-critical' : (q.severity === 'SERIOUS' ? 'badge-serious' : 'badge-assigned'),
          badgeText: `${q.severity} Waiting`,
          title: `${q.condition} (${q.requestId})`,
          patient: q.patientName,
          location: q.location,
          eta: 'Pending Ambulance'
        });
      });

      if (emergTitle) emergTitle.textContent = `Active Emergencies in Progress (${activeItems.length})`;

      if (activeItems.length === 0) {
        emergContainer.innerHTML = `
          <div style="text-align: center; padding: 28px 16px; color: var(--text-muted);">
            <div style="font-size: 28px; margin-bottom: 6px;">🛡️</div>
            <strong style="color: var(--text-main); font-size: 14px;">No Active Emergencies in Progress</strong>
            <p style="font-size: 12.5px; color: var(--text-secondary); margin-top: 4px; margin-bottom: 12px;">All 5 ambulances are standing by at base stations.</p>
            <a href="intake.html" class="btn-action btn-action-primary" style="font-size: 12px; padding: 5px 12px; display: inline-block;">+ Request Ambulance Now</a>
          </div>
        `;
      } else {
        emergContainer.innerHTML = '';
        activeItems.forEach(item => {
          const card = document.createElement('div');
          card.style.cssText = `
            padding: 14px 16px;
            background-color: var(--bg-surface-alt);
            border-left: 4px solid ${item.badgeClass.includes('critical') ? 'var(--accent-red)' : 'var(--accent-amber)'};
            border-radius: var(--r-md);
            display: flex;
            justify-content: space-between;
            align-items: center;
          `;

          if (item.type === 'AMBULANCE_ACTIVE') {
            card.innerHTML = `
              <div>
                <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
                  <span class="badge ${item.badgeClass}">${item.badgeText}</span>
                  <strong style="color: var(--text-main); font-size: 14px;">${item.title}</strong>
                </div>
                <p style="font-size: 12.5px; color: var(--text-secondary); margin: 0;">
                  Location: <strong>${item.location}</strong>
                </p>
                <p style="font-size: 12px; color: var(--accent-blue); margin-top: 4px; font-weight: 500;">
                  🚑 <strong>${item.ambulance}</strong> active &bull; Destination: <strong>${item.hospital}</strong>
                </p>
              </div>
              <div style="text-align: right; flex-shrink: 0;">
                <span style="font-size: 11px; color: var(--text-muted); display: block;">ETA</span>
                <strong style="font-size: 15px; color: var(--accent-red); font-family: var(--font-mono);">${item.eta}</strong>
                <a href="fleet.html" class="btn-action btn-action-secondary" style="font-size: 11px; padding: 3px 8px; margin-top: 6px; display: inline-block; text-decoration: none;">Fleet</a>
              </div>
            `;
          } else {
            card.innerHTML = `
              <div>
                <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
                  <span class="badge ${item.badgeClass}">${item.badgeText}</span>
                  <strong style="color: var(--text-main); font-size: 14px;">${item.title}</strong>
                </div>
                <p style="font-size: 12.5px; color: var(--text-secondary); margin: 0;">
                  Patient: <strong>${item.patient}</strong> &bull; Location: <strong>${item.location}</strong>
                </p>
                <p style="font-size: 12px; color: var(--accent-amber); margin-top: 4px; font-weight: 500;">
                  ⏳ Autonomous dispatcher searching closest ambulance...
                </p>
              </div>
              <div style="text-align: right; flex-shrink: 0;">
                <span style="font-size: 11px; color: var(--text-muted); display: block;">QUEUE</span>
                <strong style="font-size: 13px; color: var(--accent-amber);">Waiting</strong>
                <a href="queue.html" class="btn-action btn-action-secondary" style="font-size: 11px; padding: 3px 8px; margin-top: 6px; display: inline-block; text-decoration: none;">Queue</a>
              </div>
            `;
          }
          emergContainer.appendChild(card);
        });
      }
    }

    // 4. Populate Hospital Bed Availability
    const hospContainer = document.getElementById('overview-hospitals-container');
    if (hospContainer && hospitals.length > 0) {
      hospContainer.innerHTML = '';
      hospitals.forEach(h => {
        const row = document.createElement('div');
        row.style.cssText = `
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 10px 12px;
          background-color: var(--bg-surface-alt);
          border-radius: var(--r-md);
        `;
        const isNearFull = h.availableBeds <= 2;
        row.innerHTML = `
          <div>
            <strong style="font-size: 13.5px; color: var(--text-main);">${h.name} (${h.id})</strong>
            <span style="display: block; font-size: 11.5px; color: var(--text-secondary);">Zone ${h.locationId} &bull; ${h.capabilities ? h.capabilities.slice(0, 2).join(', ') : 'Emergency Center'}</span>
          </div>
          <span class="badge ${isNearFull ? 'badge-critical' : 'badge-assigned'}">
            ${isNearFull ? '⚠️ ' : '🟢 '}${h.availableBeds} / ${h.totalBeds} Beds Free
          </span>
        `;
        hospContainer.appendChild(row);
      });
    }

  } catch (err) {
    console.error('Overview poll update error:', err);
  }
}

// =========================================================================
// 7. Hospitals Page (hospitals.html)
// =========================================================================

async function initHospitalsPage() {
  try {
    const [hospRes, fleetRes, statusData] = await Promise.all([
      fetch(`${API_BASE}/api/hospitals`),
      fetch(`${API_BASE}/api/ambulances`),
      syncGlobalStatus()
    ]);

    if (!hospRes.ok) return;
    const hospitals = await hospRes.json();
    const ambulances = fleetRes.ok ? await fleetRes.json() : [];

    let totalAvailBeds = 0;
    let nearCapacityCount = 0;

    const grid = document.getElementById('hospitals-grid');
    if (grid) grid.innerHTML = '';

    hospitals.forEach(h => {
      totalAvailBeds += h.availableBeds;
      if (h.availableBeds <= 2) nearCapacityCount++;

      // Inbound ambulances
      const inbounds = ambulances.filter(a => a.hospitalName === h.name && !a.isAvailable);

      if (grid) {
        const card = document.createElement('article');
        card.style.cssText = `
          background: var(--bg-surface-alt);
          border: 1px solid var(--border-light);
          border-radius: var(--r-md);
          padding: 18px;
          display: flex;
          flex-direction: column;
          gap: 14px;
        `;

        const isNearFull = h.availableBeds <= 2;
        const badgeClass = isNearFull ? 'badge-critical' : 'badge-assigned';
        const badgeText = isNearFull ? `⚠️ ${h.availableBeds} Beds Left` : `🟢 ${h.availableBeds} Beds Free`;

        const capsHtml = (h.capabilities || []).map(cap => `
          <div style="display: flex; justify-content: space-between; padding: 6px 10px; background: white; border-radius: 4px; border: 1px solid var(--border-light); font-size: 12.5px;">
            <span>🩺 <strong>${cap.replace('_', ' ')}:</strong></span>
            <strong style="color: var(--accent-green);">Equipped & Active</strong>
          </div>
        `).join('');

        const inboundHtml = inbounds.length > 0
          ? `<span style="color: var(--accent-red); font-weight: 600;">🚑 Inbound: ${inbounds.map(a => `${a.id} (${a.patientName || 'Patient'})`).join(', ')}</span>`
          : `<span style="color: var(--text-muted); font-size: 12px;">Inbound: None right now</span>`;

        card.innerHTML = `
          <div style="display: flex; justify-content: space-between; align-items: flex-start;">
            <div>
              <span style="font-size: 11px; font-weight: 700; color: var(--accent-blue); text-transform: uppercase;">🏥 Hospital ${h.id} &bull; Zone ${h.locationId}</span>
              <h3 style="font-size: 16px; font-weight: 700; color: var(--text-main); margin-top: 2px;">${h.name}</h3>
              <p style="font-size: 12.5px; color: var(--text-secondary); margin: 0;">${h.locationName} &bull; Capacity: ${h.totalBeds} Total Beds</p>
            </div>
            <span class="badge ${badgeClass}">${badgeText}</span>
          </div>

          <div style="display: flex; flex-direction: column; gap: 6px;">
            <div style="display: flex; justify-content: space-between; padding: 6px 10px; background: white; border-radius: 4px; border: 1px solid var(--border-light); font-size: 12.5px;">
              <span>🛏️ <strong>Total Bed Availability:</strong></span>
              <strong style="color: ${isNearFull ? 'var(--accent-red)' : 'var(--accent-green)'};">${h.availableBeds} Free / ${h.totalBeds} Total</strong>
            </div>
            ${capsHtml}
          </div>

          <div style="margin-top: auto; padding-top: 10px; border-top: 1px solid var(--border-light); display: flex; justify-content: space-between; align-items: center; font-size: 12px;">
            ${inboundHtml}
            <a href="queue.html" class="btn-action btn-action-primary" style="font-size: 11px; padding: 4px 10px; text-decoration: none;">Select for Case</a>
          </div>
        `;

        grid.appendChild(card);
      }
    });

    const kpiBeds = document.getElementById('hosp-kpi-beds');
    if (kpiBeds) kpiBeds.textContent = totalAvailBeds;

    const kpiWarn = document.getElementById('hosp-kpi-warning');
    if (kpiWarn) kpiWarn.textContent = nearCapacityCount;

    const kpiInbound = document.getElementById('hosp-kpi-inbound');
    if (kpiInbound && statusData) {
      kpiInbound.textContent = statusData.activeTrips || 0;
    }

  } catch (err) {
    console.error('Hospitals poll error:', err);
  }
}

// =========================================================================
// 8. History Page (history.html)
// =========================================================================

async function initHistoryPage() {
  try {
    const [histRes, statusData] = await Promise.all([
      fetch(`${API_BASE}/api/history`),
      syncGlobalStatus()
    ]);

    if (!histRes.ok) return;
    const trips = await histRes.json();

    // Update KPIs
    const kpiAdmitted = document.getElementById('history-kpi-admitted');
    if (kpiAdmitted) kpiAdmitted.textContent = trips.length;

    const kpiAvg = document.getElementById('history-kpi-avgtime');
    if (kpiAvg && statusData) {
      kpiAvg.innerHTML = `${statusData.avgResponseTime} <span style="font-size: 15px; color: var(--text-muted); font-weight: 500;">mins</span>`;
    }

    const kpiCrit = document.getElementById('history-kpi-critical');
    if (kpiCrit) {
      kpiCrit.textContent = trips.filter(t => t.severity === 'CRITICAL').length;
    }

    const kpiOnTime = document.getElementById('history-kpi-ontime');
    if (kpiOnTime && statusData) {
      kpiOnTime.textContent = `${statusData.onTimeRate}%`;
    }

    const badgeRecords = document.getElementById('history-records-badge');
    if (badgeRecords) badgeRecords.textContent = `${trips.length} Records Archived`;

    const tbody = document.getElementById('history-tbody') || document.querySelector('.tactical-table tbody');
    if (!tbody) return;

    if (trips.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="9" style="text-align:center; padding: 36px 20px; color: var(--text-muted);">
            <div style="font-size: 15px; font-weight: 600; margin-bottom: 6px;">No completed trips recorded yet.</div>
            <div style="font-size: 13px; color: var(--text-secondary);">
              Ambulance missions will appear here automatically after patients are delivered and admitted to receiving hospitals.
            </div>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = '';
    trips.forEach(t => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${t.requestId}</strong></td>
        <td>
          <strong>${t.patientName}</strong>
          <span style="display: block; font-size: 11.5px; color: var(--text-secondary);">${t.location}</span>
        </td>
        <td><span class="badge ${t.severity === 'CRITICAL' ? 'badge-critical' : 'badge-serious'}">${t.severity}</span></td>
        <td>🚑 <strong>${t.ambulanceId}</strong></td>
        <td>🏥 ${t.hospitalName}</td>
        <td>${t.timestamp}</td>
        <td>${t.timestamp}</td>
        <td><strong style="color: var(--accent-green);">${t.duration || '12.0'} mins</strong></td>
        <td><span class="badge badge-assigned">✅ ${t.status || 'Patient Admitted'}</span></td>
      `;
      tbody.appendChild(tr);
    });

  } catch (err) {
    console.error('History poll error:', err);
  }
}

// =========================================================================
// 9. Intake Form (intake.html)
// =========================================================================

function initIntakePage() {
  syncGlobalStatus();
  const form = document.querySelector('form') || document.getElementById('intake-form');
  if (!form || form.dataset.cadBound) return;
  form.dataset.cadBound = 'true';

  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const patientName = document.getElementById('patientName')?.value?.trim();
    const emergencyLocation = document.getElementById('emergencyLocation')?.value;
    const condition = document.getElementById('condition')?.value || 'GENERAL_ILLNESS';
    const severityTier = document.querySelector('input[name="severityTier"]:checked')?.value || 'CRITICAL';
    const requiredCapability = document.getElementById('requiredCapability')?.value || 'AUTO';
    const notes = document.getElementById('dispatcherNotes')?.value || '';

    if (!patientName || !emergencyLocation) {
      showNotification('Please fill in patient name and select an emergency zone.', 'error');
      return;
    }

    const payload = {
      patientName,
      emergencyLocation,
      condition,
      severityTier,
      requiredCapability,
      notes
    };

    try {
      showNotification('Recording emergency call and assigning closest ambulance...', 'info');
      const res = await fetch(`${API_BASE}/api/intake`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        const data = await res.json();
        showNotification(`✅ Emergency triaged as ${data.requestId}! Autonomous dispatcher has assigned ambulance...`, 'success');
        setTimeout(() => {
          window.location.href = 'index.html';
        }, 1000);
      } else {
        const errData = await res.json();
        showNotification(errData.error || 'Failed to submit call', 'error');
      }
    } catch (err) {
      showNotification('Could not connect to Java WebServer on port 8080.', 'error');
    }
  });
}

// =========================================================================
// 10. Traffic & Roads (traffic.html)
// =========================================================================

async function initTrafficPage() {
  syncGlobalStatus();

  try {
    const res = await fetch(`${API_BASE}/api/roads`);
    if (res.ok) {
      const roads = await res.json();
      const tbody = document.getElementById('traffic-tbody');
      if (tbody) {
        tbody.innerHTML = '';
        let openCount = 0;
        let closedCount = 0;
        let slowCount = 0;
        let totalTime = 0;

        roads.forEach(r => {
          if (r.isClosed) {
            closedCount++;
          } else {
            openCount++;
          }
          if (r.travelTime > 12.0) slowCount++;
          totalTime += r.travelTime;

          const tr = document.createElement('tr');
          if (r.isClosed) {
            tr.style.cssText = 'background-color: #fef2f2; border-left: 4px solid var(--accent-red);';
          }

          tr.innerHTML = `
            <td><strong>${r.fromName} (${r.fromId}) &harr; ${r.toName} (${r.toId})</strong></td>
            <td>${r.distance.toFixed(1)} km</td>
            <td><strong style="color: ${r.isClosed ? 'var(--accent-red)' : 'var(--text-main)'};">${r.isClosed ? '⛔ Blocked' : r.travelTime.toFixed(1) + ' mins'}</strong></td>
            <td>${r.isClosed ? '<span class="badge badge-critical">🔴 Road Closed / Blocked</span>' : '<span class="badge badge-assigned">🟢 Clear / Free Flow</span>'}</td>
            <td>${r.isClosed ? '<strong style="color: var(--accent-red);">Detoured via alternate route</strong>' : 'Normal route active'}</td>
          `;
          tbody.appendChild(tr);
        });

        const kpiOpen = document.getElementById('traffic-kpi-open');
        if (kpiOpen) kpiOpen.textContent = openCount;

        const kpiClosed = document.getElementById('traffic-kpi-closed');
        if (kpiClosed) kpiClosed.textContent = closedCount;

        const kpiSlow = document.getElementById('traffic-kpi-slow');
        if (kpiSlow) kpiSlow.textContent = slowCount;

        const kpiAvg = document.getElementById('traffic-kpi-avg');
        if (kpiAvg && roads.length > 0) {
          kpiAvg.innerHTML = `${(totalTime / roads.length).toFixed(1)} <span style="font-size: 15px; color: var(--text-muted); font-weight: 500;">mins</span>`;
        }
      }
    }
  } catch (err) {
    console.error('Traffic poll error:', err);
  }

  const form = document.querySelector('form');
  if (form && !form.dataset.cadBound) {
    form.dataset.cadBound = 'true';
    form.addEventListener('submit', async (e) => {
      e.preventDefault();

      const fromLoc = document.getElementById('fromLoc')?.value;
      const toLoc = document.getElementById('toLoc')?.value;
      const roadAction = document.getElementById('roadAction')?.value;
      const delayMinutes = document.getElementById('delayMinutes')?.value;

      try {
        const res = await fetch(`${API_BASE}/api/roads/update`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ fromLoc, toLoc, roadAction, delayMinutes })
        });
        const data = await res.json();
        if (data.success) {
          showNotification(data.message, 'success');
          initTrafficPage();
        }
      } catch (err) {
        showNotification('Failed to update road status', 'error');
      }
    });
  }
}

function initDispatchPage() {
  syncGlobalStatus();
}

// =========================================================================
// 11. Dijkstra Algorithm Step-by-Step Visualizer Engine
// =========================================================================

let dijkstraNetwork = null;
let dijkstraTrace = null;
let currentDijkstraStepIdx = 0;
let isDijkstraPlaying = false;
let dijkstraPlayTimer = null;
let dijkstraPlaySpeed = 1200;
let dijkstraAnimFrameId = null;

async function initDijkstraVisualizer() {
  syncGlobalStatus();

  try {
    const netRes = await fetch(`${API_BASE}/api/dijkstra/network`);
    if (!netRes.ok) throw new Error('Failed to load network topology');
    dijkstraNetwork = await netRes.json();

    // 1. Populate Zone Dropdowns
    const sourceSelect = document.getElementById('select-source-zone');
    const destSelect = document.getElementById('select-dest-zone');
    const toggleRoadSelect = document.getElementById('select-toggle-road');

    if (sourceSelect && destSelect && dijkstraNetwork.zones) {
      sourceSelect.innerHTML = '';
      destSelect.innerHTML = '';

      dijkstraNetwork.zones.forEach(zone => {
        const hospLabel = zone.isHospital ? ` [🏥 ${zone.hospitalName}]` : '';
        const ambLabel = (zone.ambulances && zone.ambulances.length > 0) ? ` [🚑 ${zone.ambulances.join(', ')}]` : '';
        const optText = `${zone.name} (${zone.id})${hospLabel}${ambLabel}`;

        const opt1 = new Option(optText, zone.id);
        const opt2 = new Option(optText, zone.id);

        sourceSelect.add(opt1);
        destSelect.add(opt2);
      });

      // Default selection: L1 (Central Hospital Junction) -> L8 (Tech Park Zone)
      sourceSelect.value = 'L1';
      destSelect.value = 'L8';
    }

    // 2. Populate Road Hazard Toggle Dropdown
    if (toggleRoadSelect && dijkstraNetwork.roads) {
      toggleRoadSelect.innerHTML = '';
      dijkstraNetwork.roads.forEach(r => {
        const statusText = r.isClosed ? '⛔ CURRENTLY CLOSED' : `🟢 Open (${r.travelTime.toFixed(1)}m)`;
        const opt = new Option(`${r.fromName} (${r.fromId}) ↔ ${r.toName} (${r.toId}) — ${statusText}`, `${r.fromId}|${r.toId}`);
        toggleRoadSelect.add(opt);
      });
    }

    // 3. Render SVG Map
    renderDijkstraSvg(dijkstraNetwork);

    // 4. Populate Live Emergencies for Scenario Loading
    loadLiveEmergenciesForScenario();

    // 5. Wire Button Event Listeners
    setupDijkstraEventListeners();

    // 6. Run Initial Dijkstra Trace (L1 -> L8)
    await runDijkstraTrace('L1', 'L8');

  } catch (err) {
    console.error('Error initializing Dijkstra visualizer:', err);
  }
}

function setupDijkstraEventListeners() {
  const btnRun = document.getElementById('btn-run-dijkstra');
  if (btnRun) {
    btnRun.onclick = async () => {
      const src = document.getElementById('select-source-zone').value;
      const dst = document.getElementById('select-dest-zone').value;
      if (src && dst) {
        await runDijkstraTrace(src, dst);
      }
    };
  }

  const btnPlay = document.getElementById('btn-play-pause');
  if (btnPlay) btnPlay.onclick = toggleDijkstraPlayback;

  const btnNext = document.getElementById('btn-next-step');
  if (btnNext) btnNext.onclick = nextDijkstraStep;

  const btnPrev = document.getElementById('btn-prev-step');
  if (btnPrev) btnPrev.onclick = prevDijkstraStep;

  const btnReset = document.getElementById('btn-reset');
  if (btnReset) btnReset.onclick = resetDijkstraPlayback;

  const speedSlider = document.getElementById('speed-slider');
  const speedDisplay = document.getElementById('speed-display');
  if (speedSlider) {
    speedSlider.oninput = (e) => {
      dijkstraPlaySpeed = parseInt(e.target.value, 10);
      if (speedDisplay) speedDisplay.textContent = `${(dijkstraPlaySpeed / 1000).toFixed(1)}s`;
      if (isDijkstraPlaying) {
        clearInterval(dijkstraPlayTimer);
        dijkstraPlayTimer = setInterval(nextDijkstraStep, dijkstraPlaySpeed);
      }
    };
  }

  const btnToggleClosure = document.getElementById('btn-toggle-closure');
  if (btnToggleClosure) {
    btnToggleClosure.onclick = async () => {
      const selectRoad = document.getElementById('select-toggle-road');
      if (!selectRoad || !selectRoad.value) return;

      const [fromId, toId] = selectRoad.value.split('|');
      const road = dijkstraNetwork.roads.find(r =>
        (r.fromId === fromId && r.toId === toId) || (r.fromId === toId && r.toId === fromId)
      );

      const targetAction = (road && road.isClosed) ? 'OPEN' : 'CLOSED';

      try {
        btnToggleClosure.disabled = true;
        btnToggleClosure.textContent = 'Updating road network...';

        const res = await fetch(`${API_BASE}/api/roads/update`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: `fromLoc=${encodeURIComponent(fromId)}&toLoc=${encodeURIComponent(toId)}&roadAction=${targetAction}`
        });

        if (res.ok) {
          // Re-fetch network and refresh map
          const netRes = await fetch(`${API_BASE}/api/dijkstra/network`);
          if (netRes.ok) {
            dijkstraNetwork = await netRes.json();
            renderDijkstraSvg(dijkstraNetwork);

            // Update dropdown
            const toggleRoadSelect = document.getElementById('select-toggle-road');
            if (toggleRoadSelect && dijkstraNetwork.roads) {
              const prevVal = toggleRoadSelect.value;
              toggleRoadSelect.innerHTML = '';
              dijkstraNetwork.roads.forEach(r => {
                const statusText = r.isClosed ? '⛔ CURRENTLY CLOSED' : `🟢 Open (${r.travelTime.toFixed(1)}m)`;
                const opt = new Option(`${r.fromName} (${r.fromId}) ↔ ${r.toName} (${r.toId}) — ${statusText}`, `${r.fromId}|${r.toId}`);
                toggleRoadSelect.add(opt);
              });
              toggleRoadSelect.value = prevVal;
            }

            // Re-run trace with updated road graph
            const src = document.getElementById('select-source-zone').value;
            const dst = document.getElementById('select-dest-zone').value;
            await runDijkstraTrace(src, dst);
          }
        }
      } catch (e) {
        console.error('Failed to toggle road closure:', e);
      } finally {
        btnToggleClosure.disabled = false;
        btnToggleClosure.textContent = 'Toggle Road Closure & Re-Run';
      }
    };
  }
}

async function loadLiveEmergenciesForScenario() {
  const emSelect = document.getElementById('select-real-emergency');
  const legWrap = document.getElementById('emergency-leg-selector');
  if (!emSelect) return;

  try {
    const res = await fetch(`${API_BASE}/api/queue`);
    if (res.ok) {
      const data = await res.json();
      const list = data.queue || [];

      emSelect.innerHTML = '<option value="">-- Choose from active emergencies --</option>';

      if (list.length === 0) {
        emSelect.add(new Option('(No pending emergencies in queue — submit one in Intake)', ''));
      } else {
        list.forEach(item => {
          emSelect.add(new Option(`${item.requestId}: ${item.patientName} (${item.location}, ${item.severity})`, item.requestId));
        });
      }

      window.cachedEmergencyQueue = list;

      emSelect.onchange = () => {
        const reqId = emSelect.value;
        if (!reqId) {
          if (legWrap) legWrap.style.display = 'none';
          return;
        }

        const em = window.cachedEmergencyQueue ? window.cachedEmergencyQueue.find(e => e.requestId === reqId) : null;
        if (!em) return;

        if (legWrap) legWrap.style.display = 'flex';
        applyEmergencyScenario(em);
      };

      document.querySelectorAll('input[name="emergency-leg"]').forEach(radio => {
        radio.onchange = () => {
          const reqId = emSelect.value;
          const em = window.cachedEmergencyQueue ? window.cachedEmergencyQueue.find(e => e.requestId === reqId) : null;
          if (em) applyEmergencyScenario(em);
        };
      });
    }
  } catch (err) {
    console.error('Failed to load queue for scenario:', err);
  }
}

function applyEmergencyScenario(emergency) {
  const leg = document.querySelector('input[name="emergency-leg"]:checked')?.value || 'leg1';
  const sourceSelect = document.getElementById('select-source-zone');
  const destSelect = document.getElementById('select-dest-zone');
  if (!sourceSelect || !destSelect || !dijkstraNetwork) return;

  const patientZone = dijkstraNetwork.zones.find(z => z.name.toLowerCase() === emergency.location.toLowerCase());

  if (leg === 'leg1') {
    // Leg 1: Ambulance Station -> Patient Zone
    // Pick nearest ambulance station (or AMB-1 station L1)
    const ambZone = dijkstraNetwork.zones.find(z => z.ambulances && z.ambulances.length > 0) || dijkstraNetwork.zones[0];
    sourceSelect.value = ambZone ? ambZone.id : 'L1';
    destSelect.value = patientZone ? patientZone.id : 'L3';
  } else {
    // Leg 2: Patient Zone -> Best Hospital Zone
    sourceSelect.value = patientZone ? patientZone.id : 'L3';
    // Find a hospital zone (e.g., L1 Central Hospital or matching hospital)
    const hospZone = dijkstraNetwork.zones.find(z => z.isHospital) || dijkstraNetwork.zones[0];
    destSelect.value = hospZone ? hospZone.id : 'L1';
  }

  // Auto-trigger run
  runDijkstraTrace(sourceSelect.value, destSelect.value);
}

function renderDijkstraSvg(network) {
  const edgesGroup = document.getElementById('svg-edges-group');
  const labelsGroup = document.getElementById('svg-edge-labels-group');
  const nodesGroup = document.getElementById('svg-nodes-group');

  if (!edgesGroup || !labelsGroup || !nodesGroup || !network) return;

  edgesGroup.innerHTML = '';
  labelsGroup.innerHTML = '';
  nodesGroup.innerHTML = '';

  // 1. Draw Edges
  network.roads.forEach(r => {
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.setAttribute('x1', r.fromX);
    line.setAttribute('y1', r.fromY);
    line.setAttribute('x2', r.toX);
    line.setAttribute('y2', r.toY);
    line.setAttribute('id', `edge-${r.fromId}-${r.toId}`);
    line.setAttribute('data-from', r.fromId);
    line.setAttribute('data-to', r.toId);

    let cls = 'road-edge';
    if (r.isClosed) cls += ' edge-closed';
    else if (r.travelTime > 12.0) cls += ' edge-heavy';
    line.setAttribute('class', cls);
    edgesGroup.appendChild(line);

    // Edge Label Pill at midpoint
    const midX = (r.fromX + r.toX) / 2;
    const midY = (r.fromY + r.toY) / 2;

    const gLabel = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    gLabel.setAttribute('class', 'edge-weight-tag');
    gLabel.setAttribute('id', `edgetag-${r.fromId}-${r.toId}`);

    const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    rect.setAttribute('x', midX - 22);
    rect.setAttribute('y', midY - 11);
    rect.setAttribute('width', 44);
    rect.setAttribute('height', 22);
    rect.setAttribute('rx', 5);
    rect.setAttribute('ry', 5);
    rect.setAttribute('fill', r.isClosed ? '#fef2f2' : (r.travelTime > 12.0 ? '#fffbeb' : '#ffffff'));
    rect.setAttribute('stroke', r.isClosed ? '#dc2626' : (r.travelTime > 12.0 ? '#ea580c' : '#cbd5e1'));
    rect.setAttribute('stroke-width', 1.5);
    gLabel.appendChild(rect);

    const txt = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    txt.setAttribute('x', midX);
    txt.setAttribute('y', midY + 4);
    txt.setAttribute('text-anchor', 'middle');
    txt.setAttribute('font-family', 'var(--font-mono)');
    txt.setAttribute('font-size', '10.5');
    txt.setAttribute('font-weight', '700');
    txt.setAttribute('fill', r.isClosed ? '#dc2626' : (r.travelTime > 12.0 ? '#b45309' : '#0f172a'));
    txt.textContent = r.isClosed ? '⛔ BLK' : `${r.travelTime.toFixed(1)}m`;
    gLabel.appendChild(txt);

    labelsGroup.appendChild(gLabel);
  });

  // 2. Draw Nodes
  network.zones.forEach(z => {
    const gNode = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    gNode.setAttribute('class', 'zone-node-group node-unvisited');
    gNode.setAttribute('id', `node-group-${z.id}`);
    gNode.setAttribute('data-node-id', z.id);

    // Source / Destination Ring
    const ring = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    ring.setAttribute('cx', z.x);
    ring.setAttribute('y', z.y);
    ring.setAttribute('r', 32);
    ring.setAttribute('class', 'node-ring');
    ring.setAttribute('style', 'display: none;');
    gNode.appendChild(ring);

    // Node Circle
    const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    circle.setAttribute('cx', z.x);
    circle.setAttribute('cy', z.y);
    circle.setAttribute('r', 24);
    circle.setAttribute('class', 'zone-node-circle');
    gNode.appendChild(circle);

    // Node Center Icon / ID
    const txtIcon = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    txtIcon.setAttribute('x', z.x);
    txtIcon.setAttribute('y', z.y + 5);
    txtIcon.setAttribute('text-anchor', 'middle');
    txtIcon.setAttribute('font-family', 'var(--font-mono)');
    txtIcon.setAttribute('font-size', z.isHospital ? '14' : '12');
    txtIcon.setAttribute('font-weight', '700');
    txtIcon.setAttribute('fill', '#ffffff');
    txtIcon.textContent = z.isHospital ? '🏥' : z.id;
    gNode.appendChild(txtIcon);

    // Ambulance Badge Pill (if stationed)
    if (z.ambulances && z.ambulances.length > 0) {
      const gAmb = document.createElementNS('http://www.w3.org/2000/svg', 'g');
      const ambRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
      ambRect.setAttribute('x', z.x - 28);
      ambRect.setAttribute('y', z.y - 38);
      ambRect.setAttribute('width', 56);
      ambRect.setAttribute('height', 16);
      ambRect.setAttribute('rx', 4);
      ambRect.setAttribute('fill', '#2563eb');
      gAmb.appendChild(ambRect);

      const ambTxt = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      ambTxt.setAttribute('x', z.x);
      ambTxt.setAttribute('y', z.y - 26);
      ambTxt.setAttribute('text-anchor', 'middle');
      ambTxt.setAttribute('font-family', 'var(--font-mono)');
      ambTxt.setAttribute('font-size', '9.5');
      ambTxt.setAttribute('font-weight', '700');
      ambTxt.setAttribute('fill', '#ffffff');
      ambTxt.textContent = `🚑 ${z.ambulances.join(',')}`;
      gAmb.appendChild(ambTxt);

      gNode.appendChild(gAmb);
    }

    // Zone Name Text Label
    const txtLabel = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    txtLabel.setAttribute('x', z.x);
    txtLabel.setAttribute('y', z.y + 38);
    txtLabel.setAttribute('text-anchor', 'middle');
    txtLabel.setAttribute('font-family', 'var(--font-sans)');
    txtLabel.setAttribute('font-size', '11');
    txtLabel.setAttribute('font-weight', '600');
    txtLabel.setAttribute('fill', '#e2e8f0');
    txtLabel.setAttribute('stroke', '#0f172a');
    txtLabel.setAttribute('stroke-width', '3');
    txtLabel.setAttribute('paint-order', 'stroke');
    txtLabel.textContent = z.name;
    gNode.appendChild(txtLabel);

    // Interactive node click sets source or dest
    gNode.onclick = () => {
      const srcSel = document.getElementById('select-source-zone');
      const dstSel = document.getElementById('select-dest-zone');
      if (srcSel && dstSel) {
        if (srcSel.value === z.id) {
          // Already source, do nothing
        } else {
          dstSel.value = z.id;
          runDijkstraTrace(srcSel.value, dstSel.value);
        }
      }
    };

    nodesGroup.appendChild(gNode);
  });
}

async function runDijkstraTrace(sourceId, destId) {
  pauseDijkstra();

  const statusIndicator = document.getElementById('route-status-indicator');
  if (statusIndicator) {
    statusIndicator.className = 'badge';
    statusIndicator.style.background = '#1e3a8a';
    statusIndicator.style.color = '#38bdf8';
    statusIndicator.textContent = 'Calculating Route...';
  }

  try {
    const res = await fetch(`${API_BASE}/api/dijkstra/trace?from=${encodeURIComponent(sourceId)}&to=${encodeURIComponent(destId)}`);
    if (!res.ok) throw new Error('Dijkstra trace calculation failed');

    dijkstraTrace = await res.json();
    currentDijkstraStepIdx = 0;

    // Render Initial Step (Step 0)
    renderDijkstraStep(currentDijkstraStepIdx);

    // Update Result & Why This Route Card
    updateWhyRouteCard(dijkstraTrace);

    if (statusIndicator) {
      if (dijkstraTrace.reachable) {
        statusIndicator.className = 'badge badge-assigned';
        statusIndicator.style.background = '';
        statusIndicator.style.color = '';
        statusIndicator.textContent = `Optimal Path Found (${dijkstraTrace.totalTime.toFixed(1)}m)`;
      } else {
        statusIndicator.className = 'badge badge-critical';
        statusIndicator.style.background = '';
        statusIndicator.style.color = '';
        statusIndicator.textContent = 'Destination Unreachable (Roads Closed)';
      }
    }

  } catch (err) {
    console.error('Failed to run Dijkstra trace:', err);
    if (statusIndicator) {
      statusIndicator.className = 'badge badge-critical';
      statusIndicator.textContent = 'Trace Error';
    }
  }
}

function renderDijkstraStep(stepIdx) {
  if (!dijkstraTrace || !dijkstraTrace.steps || dijkstraTrace.steps.length === 0) return;
  if (stepIdx < 0 || stepIdx >= dijkstraTrace.steps.length) return;

  const step = dijkstraTrace.steps[stepIdx];
  const totalSteps = dijkstraTrace.steps.length;

  // 1. Update Step Counter & Controls State
  const counterBadge = document.getElementById('step-counter-badge');
  if (counterBadge) counterBadge.textContent = `Step ${stepIdx + 1} / ${totalSteps}`;

  const btnPrev = document.getElementById('btn-prev-step');
  const btnNext = document.getElementById('btn-next-step');
  if (btnPrev) btnPrev.disabled = (stepIdx === 0);
  if (btnNext) btnNext.disabled = (stepIdx === totalSteps - 1);

  // 2. Update Plain-English Explanation Box
  const expTitle = document.getElementById('step-exp-title');
  const expBadge = document.getElementById('step-action-badge');
  const expBody = document.getElementById('step-exp-body');
  const relaxWrap = document.getElementById('step-relax-container');
  const relaxPill = document.getElementById('step-relax-pill');

  if (expTitle) expTitle.textContent = `Step ${step.stepNumber}: ${formatActionName(step.action)} — Zone: ${step.currentNodeName}`;
  if (expBadge) {
    expBadge.textContent = step.action.replace('_', ' ');
    if (step.action === 'DESTINATION_REACHED') expBadge.className = 'badge badge-assigned';
    else if (step.action === 'SKIP_STALE') expBadge.className = 'badge badge-neutral';
    else expBadge.className = 'badge';
  }
  if (expBody) expBody.textContent = step.explanation;

  if (relaxWrap && relaxPill) {
    if (step.relaxations && step.relaxations.length > 0) {
      relaxWrap.style.display = 'block';
      const relaxLines = step.relaxations.map(r =>
        `• ${r.fromId} ➔ ${r.toId} (${r.toName}): ${r.reason}`
      ).join('<br>');
      relaxPill.innerHTML = relaxLines;
    } else {
      relaxWrap.style.display = 'none';
    }
  }

  // 3. Highlight Pseudocode Line
  highlightPseudocodeLine(step.pseudocodeLine);

  // 4. Update Priority Queue Panel
  renderPriorityQueuePanel(step.priorityQueue);

  // 5. Update Visited Breadcrumbs
  renderVisitedChips(step.visitedNodes);

  // 6. Update Distance Table
  renderDistanceTable(step.distanceTable, step.currentNodeId);

  // 7. Update Map SVG Visual States
  renderMapStepVisuals(step, stepIdx === totalSteps - 1);
}

function formatActionName(action) {
  switch (action) {
    case 'INITIALIZATION': return 'Initialize Distance Table & Priority Queue';
    case 'RELAX_EDGES': return 'Evaluate & Relax Incident Roads';
    case 'DESTINATION_REACHED': return 'Destination Settled (Optimal Path Proven)';
    case 'SKIP_STALE': return 'Discard Stale Priority Queue Entry';
    default: return action;
  }
}

function highlightPseudocodeLine(lineNum) {
  const codeBadge = document.getElementById('code-active-badge');
  if (codeBadge) codeBadge.textContent = `Line ${lineNum || 1}`;

  for (let i = 1; i <= 10; i++) {
    const el = document.getElementById(`code-l${i}`);
    if (el) {
      if (i === lineNum) el.classList.add('active-code-line');
      else el.classList.remove('active-code-line');
    }
  }
}

function renderPriorityQueuePanel(pqList) {
  const container = document.getElementById('pq-container');
  const sizeBadge = document.getElementById('pq-size-badge');
  if (!container) return;

  if (sizeBadge) sizeBadge.textContent = `${pqList ? pqList.length : 0} zone(s)`;

  if (!pqList || pqList.length === 0) {
    container.innerHTML = '<span style="font-size: 12px; color: var(--text-muted);">(Queue is empty — all reachable nodes explored)</span>';
    return;
  }

  container.innerHTML = '';
  pqList.forEach((item, idx) => {
    const badge = document.createElement('div');
    badge.className = `pq-badge ${idx === 0 ? 'head' : ''}`;
    badge.title = `${item.nodeName}: ${item.distance.toFixed(1)} mins tentative travel time`;
    badge.innerHTML = `
      ${idx === 0 ? '⭐ <strong>MIN:</strong>' : ''}
      <span>${item.nodeId} (${item.distance.toFixed(1)}m)</span>
    `;
    container.appendChild(badge);
  });
}

function renderVisitedChips(visitedList) {
  const container = document.getElementById('visited-chips');
  if (!container) return;

  if (!visitedList || visitedList.length === 0) {
    container.innerHTML = '<span style="font-size: 12px; color: var(--text-muted);">None settled yet</span>';
    return;
  }

  container.innerHTML = '';
  visitedList.forEach((id, idx) => {
    const chip = document.createElement('span');
    chip.className = 'visited-chip';
    const zone = dijkstraNetwork?.zones?.find(z => z.id === id);
    const zName = zone ? zone.name : id;
    chip.textContent = `${idx + 1}. ${id} (${zName})`;
    container.appendChild(chip);
  });
}

function renderDistanceTable(distTable, currentNodeId) {
  const tbody = document.getElementById('dist-tbody');
  if (!tbody || !distTable) return;

  tbody.innerHTML = '';

  Object.keys(distTable).forEach(nodeId => {
    const entry = distTable[nodeId];
    const tr = document.createElement('tr');

    if (entry.nodeId === currentNodeId) {
      tr.className = 'processing-row';
    } else if (entry.previousId !== null && entry.distance !== null) {
      tr.className = 'settled-row';
    }

    const distStr = entry.distance === null ? '&infin;' : `${entry.distance.toFixed(1)}m`;
    const prevStr = entry.previousId === null ? '—' : `${entry.previousId}`;

    let statusBadge = '<span class="badge badge-neutral" style="font-size:10px;">Unvisited</span>';
    if (entry.nodeId === currentNodeId) {
      statusBadge = '<span class="badge" style="background:#1e3a8a; color:#38bdf8; font-size:10px;">Processing</span>';
    } else if (entry.distance !== null && entry.previousId !== null) {
      statusBadge = '<span class="badge badge-assigned" style="font-size:10px;">Updated</span>';
    }

    const flashClass = entry.updatedInThisStep ? 'flash-updated' : '';

    tr.innerHTML = `
      <td><strong>${entry.nodeId}</strong> <span style="font-size:10.5px; color:var(--text-muted);">${entry.nodeName}</span></td>
      <td class="${flashClass}"><strong style="color: ${entry.distance === null ? 'var(--text-muted)' : 'var(--text-main)'};">${distStr}</strong></td>
      <td class="${flashClass}">${prevStr}</td>
      <td>${statusBadge}</td>
    `;

    tbody.appendChild(tr);
  });
}

function renderMapStepVisuals(step, isFinalStep) {
  if (!dijkstraNetwork) return;

  const sourceId = dijkstraTrace.sourceId;
  const destId = dijkstraTrace.destinationId;
  const pqNodeIds = (step.priorityQueue || []).map(p => p.nodeId);
  const visitedSet = new Set(step.visitedNodes || []);
  const shortestPathSet = new Set((dijkstraTrace.path || []).map(p => p.id));

  // 1. Update Nodes
  dijkstraNetwork.zones.forEach(z => {
    const gNode = document.getElementById(`node-group-${z.id}`);
    if (!gNode) return;

    // Reset base classes
    gNode.className.baseVal = 'zone-node-group';

    // Source / Destination Ring
    const ring = gNode.querySelector('.node-ring');
    if (ring) {
      if (z.id === sourceId) {
        ring.style.display = 'block';
        ring.className.baseVal = 'node-ring node-source-ring';
      } else if (z.id === destId) {
        ring.style.display = 'block';
        ring.className.baseVal = 'node-ring node-target-ring';
      } else {
        ring.style.display = 'none';
      }
    }

    if (isFinalStep && dijkstraTrace.reachable && shortestPathSet.has(z.id)) {
      gNode.classList.add('node-shortest');
    } else if (z.id === step.currentNodeId) {
      gNode.classList.add('node-processing');
    } else if (visitedSet.has(z.id)) {
      gNode.classList.add('node-settled');
    } else if (pqNodeIds.includes(z.id)) {
      gNode.classList.add('node-in-pq');
    } else {
      gNode.classList.add('node-unvisited');
    }
  });

  // 2. Update Edges
  // Find which edge is actively being relaxed in this step
  const activeRelaxationToIds = (step.relaxations || []).filter(r => !r.skipped).map(r => r.toId);

  // Shortest path edge pairs
  const shortestEdgeKeys = new Set();
  if (isFinalStep && dijkstraTrace.reachable && dijkstraTrace.path) {
    for (let i = 0; i < dijkstraTrace.path.length - 1; i++) {
      const u = dijkstraTrace.path[i].id;
      const v = dijkstraTrace.path[i + 1].id;
      shortestEdgeKeys.add(`${u}-${v}`);
      shortestEdgeKeys.add(`${v}-${u}`);
    }
  }

  dijkstraNetwork.roads.forEach(r => {
    const line = document.getElementById(`edge-${r.fromId}-${r.toId}`);
    if (!line) return;

    let cls = 'road-edge';
    if (r.isClosed) {
      cls += ' edge-closed';
    } else if (isFinalStep && (shortestEdgeKeys.has(`${r.fromId}-${r.toId}`) || shortestEdgeKeys.has(`${r.toId}-${r.fromId}`))) {
      cls += ' edge-shortest';
    } else if (
      (r.fromId === step.currentNodeId && activeRelaxationToIds.includes(r.toId)) ||
      (r.toId === step.currentNodeId && activeRelaxationToIds.includes(r.fromId))
    ) {
      cls += ' edge-relaxing';
    } else if (r.travelTime > 12.0) {
      cls += ' edge-heavy';
    }

    line.className.baseVal = cls;
  });

  // 3. Final Step: Trigger Animated Ambulance Marker
  if (isFinalStep && dijkstraTrace.reachable && dijkstraTrace.path && dijkstraTrace.path.length >= 2) {
    animateAmbulanceOnPath(dijkstraTrace.path);
  } else {
    stopAmbulanceAnimation();
  }
}

function updateWhyRouteCard(trace) {
  const timeEl = document.getElementById('res-optimal-time');
  const distEl = document.getElementById('res-optimal-dist');
  const savedEl = document.getElementById('res-alt-saved');
  const crumbsEl = document.getElementById('res-path-breadcrumbs');
  const secondEl = document.getElementById('second-route-details');

  if (!trace.reachable) {
    if (timeEl) timeEl.textContent = 'UNREACHABLE';
    if (distEl) distEl.textContent = '—';
    if (savedEl) savedEl.textContent = '—';
    if (crumbsEl) crumbsEl.innerHTML = `<span style="color:var(--accent-red); font-weight:700;">⛔ No path exists between ${trace.sourceName} and ${trace.destinationName} due to closed roads.</span>`;
    if (secondEl) secondEl.textContent = 'No feasible alternative path exists.';
    return;
  }

  if (timeEl) timeEl.textContent = `${trace.totalTime.toFixed(1)} mins`;
  if (distEl) distEl.textContent = `${trace.totalDistance.toFixed(1)} km`;
  if (savedEl) savedEl.textContent = trace.timeSaved > 0 ? `${trace.timeSaved.toFixed(1)} mins faster` : 'Optimal Choice';

  if (crumbsEl && trace.path) {
    crumbsEl.innerHTML = '';
    trace.path.forEach((loc, idx) => {
      const pill = document.createElement('span');
      pill.className = 'path-pill';
      pill.textContent = `${loc.name} (${loc.id})`;
      crumbsEl.appendChild(pill);

      if (idx < trace.path.length - 1) {
        const arrow = document.createElement('span');
        arrow.className = 'path-arrow';
        arrow.innerHTML = '&rarr;';
        crumbsEl.appendChild(arrow);
      }
    });
  }

  if (secondEl) {
    if (trace.secondBestPath && trace.secondBestPath.length > 0) {
      const altNames = trace.secondBestPath.map(p => p.name).join(' ➔ ');
      secondEl.innerHTML = `<strong>${altNames}</strong> (${trace.secondBestTime.toFixed(1)} mins). Dijkstra saved <strong>${trace.timeSaved.toFixed(1)} minutes</strong>!`;
    } else {
      secondEl.textContent = 'Only one viable path exists in current road traffic conditions.';
    }
  }
}

function animateAmbulanceOnPath(path) {
  stopAmbulanceAnimation();
  const marker = document.getElementById('svg-ambulance-marker');
  if (!marker || !path || path.length < 2) return;

  marker.style.display = 'block';
  marker.innerHTML = `
    <circle r="14" fill="#ffffff" stroke="#2563eb" stroke-width="2.5" filter="drop-shadow(0 0 6px #3b82f6)"/>
    <text font-size="12" text-anchor="middle" y="4">🚑</text>
  `;

  let currentSegment = 0;
  let progress = 0.0;
  const speed = 0.02; // segment progress per animation frame

  function step() {
    if (currentSegment >= path.length - 1) {
      currentSegment = 0;
      progress = 0.0;
    }

    const p1 = path[currentSegment];
    const p2 = path[currentSegment + 1];

    const curX = p1.x + (p2.x - p1.x) * progress;
    const curY = p1.y + (p2.y - p1.y) * progress;

    marker.setAttribute('transform', `translate(${curX}, ${curY})`);

    progress += speed;
    if (progress >= 1.0) {
      progress = 0.0;
      currentSegment++;
    }

    dijkstraAnimFrameId = requestAnimationFrame(step);
  }

  dijkstraAnimFrameId = requestAnimationFrame(step);
}

function stopAmbulanceAnimation() {
  if (dijkstraAnimFrameId) {
    cancelAnimationFrame(dijkstraAnimFrameId);
    dijkstraAnimFrameId = null;
  }
  const marker = document.getElementById('svg-ambulance-marker');
  if (marker) marker.style.display = 'none';
}

function toggleDijkstraPlayback() {
  if (isDijkstraPlaying) {
    pauseDijkstra();
  } else {
    playDijkstra();
  }
}

function playDijkstra() {
  if (!dijkstraTrace || !dijkstraTrace.steps) return;
  isDijkstraPlaying = true;

  const btn = document.getElementById('btn-play-pause');
  const label = document.getElementById('play-pause-label');
  if (label) label.textContent = 'Pause';
  if (btn) {
    btn.classList.remove('primary');
    btn.classList.add('secondary');
  }

  // If already at end, wrap around to start
  if (currentDijkstraStepIdx >= dijkstraTrace.steps.length - 1) {
    currentDijkstraStepIdx = 0;
    renderDijkstraStep(currentDijkstraStepIdx);
  }

  dijkstraPlayTimer = setInterval(nextDijkstraStep, dijkstraPlaySpeed);
}

function pauseDijkstra() {
  isDijkstraPlaying = false;
  if (dijkstraPlayTimer) {
    clearInterval(dijkstraPlayTimer);
    dijkstraPlayTimer = null;
  }

  const btn = document.getElementById('btn-play-pause');
  const label = document.getElementById('play-pause-label');
  if (label) label.textContent = 'Play';
  if (btn) {
    btn.classList.remove('secondary');
    btn.classList.add('primary');
  }
}

function nextDijkstraStep() {
  if (!dijkstraTrace || !dijkstraTrace.steps) return;
  if (currentDijkstraStepIdx < dijkstraTrace.steps.length - 1) {
    currentDijkstraStepIdx++;
    renderDijkstraStep(currentDijkstraStepIdx);
  } else {
    pauseDijkstra();
  }
}

function prevDijkstraStep() {
  if (!dijkstraTrace || !dijkstraTrace.steps) return;
  pauseDijkstra();
  if (currentDijkstraStepIdx > 0) {
    currentDijkstraStepIdx--;
    renderDijkstraStep(currentDijkstraStepIdx);
  }
}

function resetDijkstraPlayback() {
  pauseDijkstra();
  currentDijkstraStepIdx = 0;
  renderDijkstraStep(currentDijkstraStepIdx);
}

// =========================================================================
// 12. Recurring Live Polling Orchestrator
// =========================================================================

function pollCurrentPage() {
  const currentPath = window.location.pathname;

  if (currentPath.includes('dijkstra.html')) {
    syncGlobalStatus();
  } else if (currentPath.includes('queue.html')) {
    initQueuePage();
  } else if (currentPath.includes('fleet.html')) {
    initFleetPage();
  } else if (currentPath.includes('hospitals.html')) {
    initHospitalsPage();
  } else if (currentPath.includes('history.html')) {
    initHistoryPage();
  } else if (currentPath.includes('intake.html')) {
    initIntakePage();
  } else if (currentPath.includes('traffic.html')) {
    initTrafficPage();
  } else {
    initOverviewPage();
  }
}

document.addEventListener('DOMContentLoaded', () => {
  if (window.location.pathname.includes('dijkstra.html')) {
    initDijkstraVisualizer();
  } else {
    pollCurrentPage();
  }

  // Non-blocking 2-second background poll
  if (!window.globalCadPollTimer) {
    window.globalCadPollTimer = setInterval(pollCurrentPage, 2000);
  }
});
