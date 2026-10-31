/**
 * NALAM NAGAR EMERGENCY OPERATIONS - IN-BROWSER SIMULATION ENGINE
 * Provides 100% standalone, serverless client-side simulation for GitHub Pages.
 * Replicates the complete Java backend: Dijkstra route planning, PriorityQueue emergency
 * dispatch, fleet management, hospital bed allocation, and audit traces.
 */

(function(window) {
  'use strict';

  // 1. Master City Network Data (Nalam Nagar)
  const ZONES = [
    { id: 'L1', name: 'Central Hospital Junction', x: 390, y: 290, lat: 12.9716, lng: 77.5946, isHospital: true, hospitalName: 'Nalam General Hospital' },
    { id: 'L2', name: 'Market Circle', x: 580, y: 390, lat: 12.9650, lng: 77.6050, isHospital: true, hospitalName: 'Nalam Neuro & Stroke Care' },
    { id: 'L3', name: 'Residential Colony A', x: 740, y: 270, lat: 12.9800, lng: 77.6100, isHospital: true, hospitalName: 'Grace Children & Multi-Care' },
    { id: 'L4', name: 'Industrial Area', x: 200, y: 490, lat: 12.9550, lng: 77.5800, isHospital: true, hospitalName: 'Apex Heart & Trauma Institute' },
    { id: 'L5', name: 'College Road', x: 750, y: 130, lat: 12.9900, lng: 77.6300, isHospital: true, hospitalName: 'Sanjeevani Burn & Critical Care' },
    { id: 'L6', name: 'Town Bus Terminal', x: 540, y: 190, lat: 12.9750, lng: 77.6000, isHospital: false, hospitalName: null },
    { id: 'L7', name: 'Greenfield Suburb', x: 330, y: 130, lat: 12.9950, lng: 77.5900, isHospital: false, hospitalName: null },
    { id: 'L8', name: 'Tech Park Zone', x: 770, y: 500, lat: 12.9400, lng: 77.6500, isHospital: false, hospitalName: null },
    { id: 'L9', name: 'Old City Bazaar', x: 280, y: 380, lat: 12.9600, lng: 77.5900, isHospital: false, hospitalName: null },
    { id: 'L10', name: 'Highway Bypass Junction', x: 440, y: 530, lat: 12.9300, lng: 77.6000, isHospital: false, hospitalName: null },
    { id: 'L11', name: 'Riverside Gardens', x: 160, y: 230, lat: 12.9850, lng: 77.5750, isHospital: false, hospitalName: null },
    { id: 'L12', name: 'North Gate Hub', x: 530, y: 70, lat: 13.0100, lng: 77.6150, isHospital: false, hospitalName: null }
  ];

  const INITIAL_ROADS = [
    { fromId: 'L1', toId: 'L6', distance: 2.5, travelTime: 6.0, isClosed: false },
    { fromId: 'L6', toId: 'L2', distance: 3.0, travelTime: 8.0, isClosed: false },
    { fromId: 'L1', toId: 'L9', distance: 2.0, travelTime: 5.0, isClosed: false },
    { fromId: 'L9', toId: 'L4', distance: 4.0, travelTime: 10.0, isClosed: false },
    { fromId: 'L2', toId: 'L3', distance: 3.5, travelTime: 7.0, isClosed: false },
    { fromId: 'L3', toId: 'L5', distance: 4.0, travelTime: 9.0, isClosed: false },
    { fromId: 'L1', toId: 'L3', distance: 5.0, travelTime: 12.0, isClosed: false },
    { fromId: 'L6', toId: 'L7', distance: 4.5, travelTime: 11.0, isClosed: false },
    { fromId: 'L7', toId: 'L11', distance: 3.0, travelTime: 7.0, isClosed: false },
    { fromId: 'L11', toId: 'L1', distance: 3.8, travelTime: 9.0, isClosed: false },
    { fromId: 'L5', toId: 'L12', distance: 3.2, travelTime: 6.5, isClosed: false },
    { fromId: 'L7', toId: 'L12', distance: 4.0, travelTime: 8.0, isClosed: false },
    { fromId: 'L2', toId: 'L8', distance: 6.5, travelTime: 15.0, isClosed: false },
    { fromId: 'L4', toId: 'L10', distance: 4.2, travelTime: 9.5, isClosed: false },
    { fromId: 'L10', toId: 'L8', distance: 5.0, travelTime: 11.0, isClosed: false },
    { fromId: 'L4', toId: 'L2', distance: 5.5, travelTime: 14.0, isClosed: false }
  ];

  const INITIAL_HOSPITALS = [
    { hospitalId: 'H1', name: 'Nalam General Hospital', locationId: 'L1', locationName: 'Central Hospital Junction', capabilities: ['ICU', 'GENERAL_WARD', 'TRAUMA_CENTRE'], totalBeds: 25, availableBeds: 12 },
    { hospitalId: 'H2', name: 'Apex Heart & Trauma Institute', locationId: 'L4', locationName: 'Industrial Area', capabilities: ['CARDIAC_CARE', 'TRAUMA_CENTRE', 'ICU'], totalBeds: 15, availableBeds: 4 },
    { hospitalId: 'H3', name: 'Grace Children & Multi-Care', locationId: 'L3', locationName: 'Residential Colony A', capabilities: ['PEDIATRIC_ICU', 'GENERAL_WARD', 'ICU'], totalBeds: 12, availableBeds: 3 },
    { hospitalId: 'H4', name: 'Sanjeevani Burn & Critical Care', locationId: 'L5', locationName: 'College Road', capabilities: ['BURN_UNIT', 'ICU', 'TRAUMA_CENTRE'], totalBeds: 10, availableBeds: 2 },
    { hospitalId: 'H5', name: 'Nalam Neuro & Stroke Care', locationId: 'L2', locationName: 'Market Circle', capabilities: ['STROKE_UNIT', 'ICU', 'CARDIAC_CARE'], totalBeds: 18, availableBeds: 6 }
  ];

  const INITIAL_AMBULANCES = [
    { ambulanceId: 'AMB-1', locationId: 'L1', locationName: 'Central Hospital Junction', isAvailable: true, status: 'AVAILABLE', activeCallId: null, patientName: null, destinationHospital: null },
    { ambulanceId: 'AMB-2', locationId: 'L6', locationName: 'Town Bus Terminal', isAvailable: true, status: 'AVAILABLE', activeCallId: null, patientName: null, destinationHospital: null },
    { ambulanceId: 'AMB-3', locationId: 'L4', locationName: 'Industrial Area', isAvailable: true, status: 'AVAILABLE', activeCallId: null, patientName: null, destinationHospital: null },
    { ambulanceId: 'AMB-4', locationId: 'L12', locationName: 'North Gate Hub', isAvailable: true, status: 'AVAILABLE', activeCallId: null, patientName: null, destinationHospital: null },
    { ambulanceId: 'AMB-5', locationId: 'L2', locationName: 'Market Circle', isAvailable: true, status: 'AVAILABLE', activeCallId: null, patientName: null, destinationHospital: null }
  ];

  const SAMPLE_EMERGENCIES = [
    { requestId: 'REQ-101', patientName: 'Ravi Sharma', locationId: 'L3', locationName: 'Residential Colony A', severity: 'CRITICAL', condition: 'BURN', conditionDisplay: 'Severe Burns', requiredCapability: 'BURN_UNIT', timestamp: Date.now() - 360000, formattedTime: '6 mins ago', status: 'PENDING' },
    { requestId: 'REQ-102', patientName: 'Priya Nair', locationId: 'L5', locationName: 'College Road', severity: 'MINOR', condition: 'GENERAL_ILLNESS', conditionDisplay: 'General Illness / Dehydration', requiredCapability: 'GENERAL_WARD', timestamp: Date.now() - 300000, formattedTime: '5 mins ago', status: 'PENDING' },
    { requestId: 'REQ-103', patientName: 'Anand Patel', locationId: 'L2', locationName: 'Market Circle', severity: 'CRITICAL', condition: 'CARDIAC_ARREST', conditionDisplay: 'Cardiac Arrest / Chest Pain', requiredCapability: 'CARDIAC_CARE', timestamp: Date.now() - 240000, formattedTime: '4 mins ago', status: 'PENDING' },
    { requestId: 'REQ-104', patientName: 'Farooq Abdullah', locationId: 'L4', locationName: 'Industrial Area', severity: 'CRITICAL', condition: 'TRAUMA', conditionDisplay: 'Severe Trauma / Accident', requiredCapability: 'TRAUMA_CENTRE', timestamp: Date.now() - 180000, formattedTime: '3 mins ago', status: 'PENDING' },
    { requestId: 'REQ-105', patientName: 'Meera Sen', locationId: 'L8', locationName: 'Tech Park Zone', severity: 'SERIOUS', condition: 'STROKE', conditionDisplay: 'Stroke Symptoms', requiredCapability: 'STROKE_UNIT', timestamp: Date.now() - 120000, formattedTime: '2 mins ago', status: 'PENDING' }
  ];

  // Storage Key Prefix
  const STORAGE_KEY = 'nalam_sim_state_v1';

  class SimulationEngine {
    constructor() {
      this.loadState();
      this.autoDispatchTimer = null;
      this.demoTimer = null;
      this.isAutoDispatch = true;
      this.isDemoMode = false;
      this.initAutoDispatcher();
    }

    loadState() {
      try {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored);
          this.roads = parsed.roads || JSON.parse(JSON.stringify(INITIAL_ROADS));
          this.hospitals = parsed.hospitals || JSON.parse(JSON.stringify(INITIAL_HOSPITALS));
          this.ambulances = parsed.ambulances || JSON.parse(JSON.stringify(INITIAL_AMBULANCES));
          this.queue = parsed.queue || JSON.parse(JSON.stringify(SAMPLE_EMERGENCIES));
          this.history = parsed.history || [];
          this.requestCounter = parsed.requestCounter || 106;
          return;
        }
      } catch (e) {
        console.warn('Could not read simulation localStorage', e);
      }
      this.resetToDefaults();
    }

    saveState() {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({
          roads: this.roads,
          hospitals: this.hospitals,
          ambulances: this.ambulances,
          queue: this.queue,
          history: this.history,
          requestCounter: this.requestCounter
        }));
      } catch (e) {
        console.warn('Could not save simulation localStorage', e);
      }
    }

    resetToDefaults() {
      this.roads = JSON.parse(JSON.stringify(INITIAL_ROADS));
      this.hospitals = JSON.parse(JSON.stringify(INITIAL_HOSPITALS));
      this.ambulances = JSON.parse(JSON.stringify(INITIAL_AMBULANCES));
      this.queue = JSON.parse(JSON.stringify(SAMPLE_EMERGENCIES));
      this.history = [
        { requestId: 'REQ-098', patientName: 'Karthik Raja', location: 'Riverside Gardens', severity: 'CRITICAL', ambulanceId: 'AMB-1', hospitalName: 'Nalam General Hospital', timestamp: '12m ago', status: 'ADMITTED' },
        { requestId: 'REQ-099', patientName: 'Suresh Varma', location: 'Tech Park Zone', severity: 'SERIOUS', ambulanceId: 'AMB-3', hospitalName: 'Apex Heart & Trauma Institute', timestamp: '24m ago', status: 'ADMITTED' },
        { requestId: 'REQ-100', patientName: 'Divya Krishnan', location: 'Old City Bazaar', severity: 'MINOR', ambulanceId: 'AMB-5', hospitalName: 'Grace Children & Multi-Care', timestamp: '38m ago', status: 'ADMITTED' }
      ];
      this.requestCounter = 106;
      this.saveState();
    }

    getZone(id) {
      return ZONES.find(z => z.id === id || z.name.toLowerCase() === (id || '').toLowerCase());
    }

    // Dijkstra Algorithm in JS (Identical to RoutePlanner.java)
    dijkstra(sourceId, destId) {
      const src = this.getZone(sourceId);
      const dst = this.getZone(destId);
      if (!src || !dst) return { reachable: false, totalTime: 0, totalDistance: 0, path: [] };

      if (src.id === dst.id) {
        return { reachable: true, totalTime: 0, totalDistance: 0, path: [src.id], pathLocations: [src] };
      }

      // Adjacency graph
      const adj = {};
      ZONES.forEach(z => { adj[z.id] = []; });
      this.roads.forEach(r => {
        if (!r.isClosed) {
          adj[r.fromId].push({ to: r.toId, time: r.travelTime, dist: r.distance });
          adj[r.toId].push({ to: r.fromId, time: r.travelTime, dist: r.distance });
        }
      });

      const dist = {};
      const edgeDist = {};
      const prev = {};
      const unvisited = new Set(ZONES.map(z => z.id));

      ZONES.forEach(z => { dist[z.id] = Infinity; });
      dist[src.id] = 0;

      while (unvisited.size > 0) {
        let u = null;
        let minD = Infinity;
        for (const node of unvisited) {
          if (dist[node] < minD) {
            minD = dist[node];
            u = node;
          }
        }

        if (u === null || minD === Infinity) break;
        if (u === dst.id) break;

        unvisited.delete(u);

        for (const edge of (adj[u] || [])) {
          if (unvisited.has(edge.to)) {
            const alt = dist[u] + edge.time;
            if (alt < dist[edge.to]) {
              dist[edge.to] = alt;
              edgeDist[edge.to] = edge.dist;
              prev[edge.to] = u;
            }
          }
        }
      }

      if (dist[dst.id] === Infinity) {
        return { reachable: false, totalTime: 0, totalDistance: 0, path: [] };
      }

      const path = [];
      let curr = dst.id;
      let totalDist = 0;
      while (curr) {
        path.unshift(curr);
        if (curr === src.id) break;
        if (edgeDist[curr]) totalDist += edgeDist[curr];
        curr = prev[curr];
      }

      return {
        reachable: true,
        totalTime: dist[dst.id],
        totalDistance: totalDist,
        path: path,
        pathLocations: path.map(id => this.getZone(id))
      };
    }

    // Step-by-Step Educational Dijkstra Trace Generator (For dijkstra.html)
    generateDijkstraTrace(sourceId, destId) {
      const src = this.getZone(sourceId);
      const dst = this.getZone(destId);
      if (!src || !dst) {
        return { sourceId, destinationId: destId, reachable: false, steps: [], statusMessage: 'Invalid zones' };
      }

      const trace = {
        sourceId: src.id,
        sourceName: src.name,
        destinationId: dst.id,
        destinationName: dst.name,
        reachable: false,
        totalTime: 0,
        totalDistance: 0,
        statusMessage: '',
        path: [],
        steps: []
      };

      if (src.id === dst.id) {
        trace.reachable = true;
        trace.totalTime = 0;
        trace.totalDistance = 0;
        trace.path = [{ id: src.id, name: src.name }];
        trace.statusMessage = `Source and destination are identical (${src.name}). Travel time is 0.0 min.`;
        trace.steps.push({
          stepNumber: 1,
          currentNodeId: src.id,
          currentNodeName: src.name,
          currentDistance: 0,
          action: 'DESTINATION_REACHED',
          explanation: `Source zone is destination (${src.name}). Settled in 0 steps.`,
          pseudocodeLine: 6,
          visitedNodes: [src.id],
          priorityQueue: [],
          distanceTable: { [src.id]: { nodeId: src.id, nodeName: src.name, distance: 0, previousId: src.id, previousName: src.name, updatedInThisStep: true } },
          relaxations: []
        });
        return trace;
      }

      // Adjacency
      const adj = {};
      ZONES.forEach(z => { adj[z.id] = []; });
      this.roads.forEach(r => {
        if (!r.isClosed) {
          adj[r.fromId].push({ to: r.toId, time: r.travelTime, dist: r.distance });
          adj[r.toId].push({ to: r.fromId, time: r.travelTime, dist: r.distance });
        }
      });

      const dist = {};
      const edgeDist = {};
      const prev = {};
      const visited = new Set();
      const pq = [{ id: src.id, time: 0 }];

      ZONES.forEach(z => { dist[z.id] = Infinity; });
      dist[src.id] = 0;

      let stepNum = 1;

      // Helper snapshot functions
      const makeTableSnapshot = (updatedId) => {
        const table = {};
        ZONES.forEach(z => {
          const d = dist[z.id];
          const p = prev[z.id];
          table[z.id] = {
            nodeId: z.id,
            nodeName: z.name,
            distance: d === Infinity ? null : d,
            previousId: p || (z.id === src.id ? src.id : null),
            previousName: p ? this.getZone(p).name : (z.id === src.id ? src.name : null),
            updatedInThisStep: z.id === updatedId
          };
        });
        return table;
      };

      const makePqSnapshot = () => {
        return pq.slice().sort((a,b) => a.time - b.time).map(item => ({
          nodeId: item.id,
          nodeName: this.getZone(item.id).name,
          distance: item.time
        }));
      };

      // Step 1: Initialization
      trace.steps.push({
        stepNumber: stepNum++,
        currentNodeId: src.id,
        currentNodeName: src.name,
        currentDistance: 0,
        action: 'INITIALIZATION',
        explanation: `Initialize Dijkstra: Set distance to source ${src.name} (${src.id}) = 0.0 min, and all other 11 zones to ∞. Insert ${src.id} into Priority Queue.`,
        pseudocodeLine: 3,
        visitedNodes: [],
        priorityQueue: makePqSnapshot(),
        distanceTable: makeTableSnapshot(src.id),
        relaxations: []
      });

      while (pq.length > 0) {
        pq.sort((a,b) => a.time - b.time);
        const current = pq.shift();
        const u = current.id;
        const uZone = this.getZone(u);

        if (visited.has(u)) continue;
        visited.add(u);

        if (u === dst.id) {
          trace.steps.push({
            stepNumber: stepNum++,
            currentNodeId: u,
            currentNodeName: uZone.name,
            currentDistance: dist[u],
            action: 'DESTINATION_REACHED',
            explanation: `Optimal Target Settled! Popped destination zone ${uZone.name} (${u}) with confirmed minimal travel time of ${dist[u].toFixed(1)} minutes. Early termination activated.`,
            pseudocodeLine: 6,
            visitedNodes: Array.from(visited),
            priorityQueue: makePqSnapshot(),
            distanceTable: makeTableSnapshot(u),
            relaxations: []
          });
          break;
        }

        const relaxations = [];
        const edges = adj[u] || [];
        for (const edge of edges) {
          const v = edge.to;
          const vZone = this.getZone(v);
          const oldD = dist[v];
          const newD = dist[u] + edge.time;
          const relaxed = newD < oldD;

          relaxations.push({
            neighborId: v,
            neighborName: vZone.name,
            edgeTime: edge.time,
            oldDistance: oldD === Infinity ? null : oldD,
            newCalculatedDistance: newD,
            relaxed: relaxed,
            explanation: relaxed
              ? `Path through ${uZone.name} (${newD.toFixed(1)}m) is faster than previous best (${oldD === Infinity ? '∞' : oldD.toFixed(1) + 'm'}). Relaxing edge!`
              : `Path through ${uZone.name} (${newD.toFixed(1)}m) is not shorter than current (${oldD.toFixed(1)}m). Keep existing route.`
          });

          if (relaxed) {
            dist[v] = newD;
            edgeDist[v] = edge.dist;
            prev[v] = u;
            pq.push({ id: v, time: newD });
          }
        }

        trace.steps.push({
          stepNumber: stepNum++,
          currentNodeId: u,
          currentNodeName: uZone.name,
          currentDistance: dist[u],
          action: 'EXTRACT_MIN',
          explanation: `Extract-Min popped zone ${uZone.name} (${u}) with lowest distance ${dist[u].toFixed(1)}m. Examining ${edges.length} outgoing road connections.`,
          pseudocodeLine: 8,
          visitedNodes: Array.from(visited),
          priorityQueue: makePqSnapshot(),
          distanceTable: makeTableSnapshot(u),
          relaxations: relaxations
        });
      }

      if (dist[dst.id] !== Infinity) {
        trace.reachable = true;
        trace.totalTime = dist[dst.id];
        let curr = dst.id;
        let totalDist = 0;
        const pathIds = [];
        while (curr) {
          pathIds.unshift(curr);
          if (curr === src.id) break;
          if (edgeDist[curr]) totalDist += edgeDist[curr];
          curr = prev[curr];
        }
        trace.totalDistance = totalDist;
        trace.path = pathIds.map(id => this.getZone(id));
        trace.statusMessage = `Optimal route calculated successfully: ${trace.totalTime.toFixed(1)} mins (${trace.totalDistance.toFixed(1)} km) via ${pathIds.join(' ➔ ')}.`;
      } else {
        trace.reachable = false;
        trace.statusMessage = `Destination unreachable due to active road closures.`;
      }

      return trace;
    }

    // Auto-Dispatcher Core Cycle
    runDispatchCycle() {
      // Find pending emergency
      if (!this.queue || this.queue.length === 0) return false;

      // Sort by Severity (CRITICAL > SERIOUS > MINOR) then timestamp
      const severityWeight = { 'CRITICAL': 3, 'SERIOUS': 2, 'MINOR': 1 };
      this.queue.sort((a,b) => {
        const diff = (severityWeight[b.severity] || 0) - (severityWeight[a.severity] || 0);
        if (diff !== 0) return diff;
        return a.timestamp - b.timestamp;
      });

      // Find available ambulance
      const availAmbs = this.ambulances.filter(a => a.isAvailable);
      if (availAmbs.length === 0) return false;

      const req = this.queue[0];
      const patientLocId = req.locationId;

      // Find closest ambulance
      let bestAmb = null;
      let minAmbTime = Infinity;
      let bestAmbRoute = null;

      availAmbs.forEach(amb => {
        const route = this.dijkstra(amb.locationId, patientLocId);
        if (route.reachable && route.totalTime < minAmbTime) {
          minAmbTime = route.totalTime;
          bestAmb = amb;
          bestAmbRoute = route;
        }
      });

      if (!bestAmb) return false;

      // Find closest suitable hospital with available beds
      let bestHosp = null;
      let minHospTime = Infinity;
      let bestHospRoute = null;

      this.hospitals.forEach(hosp => {
        if (hosp.availableBeds > 0) {
          // Check capability match
          const matches = req.requiredCapability ? hosp.capabilities.includes(req.requiredCapability) || hosp.capabilities.includes('GENERAL_WARD') : true;
          if (matches) {
            const route = this.dijkstra(patientLocId, hosp.locationId);
            if (route.reachable && route.totalTime < minHospTime) {
              minHospTime = route.totalTime;
              bestHosp = hosp;
              bestHospRoute = route;
            }
          }
        }
      });

      if (!bestHosp) {
        // Fallback to any hospital with free beds
        this.hospitals.forEach(hosp => {
          if (hosp.availableBeds > 0) {
            const route = this.dijkstra(patientLocId, hosp.locationId);
            if (route.reachable && route.totalTime < minHospTime) {
              minHospTime = route.totalTime;
              bestHosp = hosp;
              bestHospRoute = route;
            }
          }
        });
      }

      if (!bestHosp) return false;

      // Assign!
      this.queue.shift(); // remove from queue
      bestAmb.isAvailable = false;
      bestAmb.status = 'BUSY / EN ROUTE';
      bestAmb.activeCallId = req.requestId;
      bestAmb.patientName = req.patientName;
      bestAmb.destinationHospital = bestHosp.name;
      bestHosp.availableBeds = Math.max(0, bestHosp.availableBeds - 1);

      // Record to history
      const totalEta = minAmbTime + minHospTime;
      this.history.unshift({
        requestId: req.requestId,
        patientName: req.patientName,
        location: req.locationName,
        severity: req.severity,
        ambulanceId: bestAmb.ambulanceId,
        hospitalName: bestHosp.name,
        timestamp: 'Just now',
        status: 'DISPATCHED'
      });

      this.saveState();
      return true;
    }

    initAutoDispatcher() {
      if (this.autoDispatchTimer) clearInterval(this.autoDispatchTimer);
      this.autoDispatchTimer = setInterval(() => {
        if (this.isAutoDispatch) {
          this.runDispatchCycle();
        }
      }, 3500);
    }

    // HTTP Handler Interceptor
    async handleRequest(path, options = {}) {
      const method = (options.method || 'GET').toUpperCase();
      const url = new URL(path, window.location.origin);
      const pathname = url.pathname.replace(/^\/api/, '');

      // Simulate realistic network delay (50ms)
      await new Promise(r => setTimeout(r, 40));

      const sendJson = (data, status = 200) => {
        return new Response(JSON.stringify(data), {
          status: status,
          headers: { 'Content-Type': 'application/json' }
        });
      };

      // 1. /status
      if (pathname === '/status') {
        const availAmbs = this.ambulances.filter(a => a.isAvailable).length;
        const totalAmbs = this.ambulances.length;
        let availBeds = 0;
        let totalBeds = 0;
        this.hospitals.forEach(h => {
          availBeds += h.availableBeds;
          totalBeds += h.totalBeds;
        });

        return sendJson({
          availableAmbulances: availAmbs,
          totalAmbulances: totalAmbs,
          activeTrips: (totalAmbs - availAmbs),
          pendingRequests: this.queue.length,
          resolvedCases: this.history.length,
          availableBeds: availBeds,
          totalBeds: totalBeds,
          avgResponseTime: 8.4,
          onTimeRate: 94.6
        });
      }

      // 2. /queue
      if (pathname === '/queue') {
        return sendJson(this.queue.map((r, i) => ({
          rank: i + 1,
          requestId: r.requestId,
          patientName: r.patientName,
          locationId: r.locationId,
          locationName: r.locationName,
          severity: r.severity,
          condition: r.condition || 'GENERAL',
          conditionDisplay: r.conditionDisplay || 'General Illness',
          requiredCapability: r.requiredCapability || 'GENERAL_WARD',
          formattedTime: r.formattedTime || 'Just now',
          timestamp: r.timestamp || Date.now(),
          status: r.status || 'PENDING',
          assignedAmbulance: r.assignedAmbulance || 'AUTO',
          assignedHospital: r.assignedHospital || 'AUTO'
        })));
      }

      // 3. /ambulances
      if (pathname === '/ambulances') {
        return sendJson(this.ambulances);
      }

      // 4. /hospitals
      if (pathname === '/hospitals') {
        return sendJson(this.hospitals);
      }

      // 5. /roads
      if (pathname === '/roads') {
        return sendJson(this.roads.map(r => ({
          fromId: r.fromId,
          fromName: this.getZone(r.fromId).name,
          toId: r.toId,
          toName: this.getZone(r.toId).name,
          distance: r.distance,
          travelTime: r.travelTime,
          isClosed: r.isClosed
        })));
      }

      // 6. /roads/update
      if (pathname === '/roads/update' && method === 'POST') {
        let body = {};
        try {
          body = typeof options.body === 'string' ? JSON.parse(options.body) : (options.body || {});
        } catch(e) {
          // Parse urlencoded
          const params = new URLSearchParams(options.body);
          params.forEach((v, k) => { body[k] = v; });
        }

        const from = body.fromLoc;
        const to = body.toLoc;
        const action = body.roadAction;
        const delay = parseFloat(body.delayMinutes || '0');

        const road = this.roads.find(r => (r.fromId === from && r.toId === to) || (r.fromId === to && r.toId === from));
        if (road) {
          if (action) road.isClosed = action.toUpperCase() === 'CLOSED';
          if (delay > 0) road.travelTime = delay;
          this.saveState();
        }
        return sendJson({ success: true, message: 'Road network updated', isClosed: road ? road.isClosed : false });
      }

      // 7. /intake (Submit emergency)
      if (pathname === '/intake' && method === 'POST') {
        let body = {};
        try {
          body = typeof options.body === 'string' ? JSON.parse(options.body) : (options.body || {});
        } catch(e) {
          const params = new URLSearchParams(options.body);
          params.forEach((v, k) => { body[k] = v; });
        }

        const zone = this.getZone(body.location || body.locationId || 'L1');
        const reqId = 'REQ-' + (this.requestCounter++);
        const newReq = {
          requestId: reqId,
          patientName: body.patientName || 'Emergency Patient',
          locationId: zone.id,
          locationName: zone.name,
          severity: body.severity || 'CRITICAL',
          condition: body.condition || 'GENERAL_ILLNESS',
          conditionDisplay: body.condition || 'General Emergency',
          requiredCapability: body.requiredCapability || 'GENERAL_WARD',
          timestamp: Date.now(),
          formattedTime: 'Just now',
          status: 'PENDING'
        };

        this.queue.unshift(newReq);
        this.saveState();

        if (this.isAutoDispatch) {
          setTimeout(() => this.runDispatchCycle(), 500);
        }

        return sendJson({
          success: true,
          requestId: reqId,
          patientName: newReq.patientName,
          location: zone.name,
          severity: newReq.severity,
          condition: newReq.condition,
          capability: newReq.requiredCapability,
          queuePosition: this.queue.length,
          message: 'Emergency recorded in queue and scheduled for dispatch'
        });
      }

      // 8. /dispatch (Manual dispatch)
      if (pathname === '/dispatch' && method === 'POST') {
        const ok = this.runDispatchCycle();
        return sendJson({
          success: ok,
          message: ok ? 'Dispatched fastest ambulance and allocated hospital' : 'No available ambulances or hospital beds'
        });
      }

      // 9. /complete-trip
      if (pathname === '/complete-trip' && method === 'POST') {
        let amb = this.ambulances.find(a => !a.isAvailable);
        if (amb) {
          amb.isAvailable = true;
          amb.status = 'AVAILABLE';
          amb.activeCallId = null;
          amb.patientName = null;
          amb.destinationHospital = null;
          this.saveState();
          return sendJson({ success: true, ambulanceId: amb.ambulanceId, message: 'Trip completed. Patient admitted.' });
        }
        return sendJson({ success: false, error: 'No active busy ambulance found' }, 400);
      }

      // 10. /history
      if (pathname === '/history') {
        return sendJson(this.history);
      }

      // 11. /seed-samples
      if (pathname === '/seed-samples' && method === 'POST') {
        this.queue = JSON.parse(JSON.stringify(SAMPLE_EMERGENCIES));
        this.saveState();
        return sendJson({ success: true, queueSize: this.queue.length });
      }

      // 12. /reset
      if (pathname === '/reset' && method === 'POST') {
        this.resetToDefaults();
        return sendJson({ success: true, message: 'System reset to default state' });
      }

      // 13. /demo/status & /demo/toggle
      if (pathname === '/demo/status') {
        return sendJson({ demoMode: this.isDemoMode });
      }
      if (pathname === '/demo/toggle' && method === 'POST') {
        this.isDemoMode = !this.isDemoMode;
        return sendJson({ success: true, demoMode: this.isDemoMode, message: `Demo mode is now ${this.isDemoMode ? 'ON' : 'OFF'}` });
      }

      // 14. /auto-dispatch/status & /auto-dispatch/toggle
      if (pathname === '/auto-dispatch/status') {
        return sendJson({
          autoDispatchEnabled: this.isAutoDispatch,
          preemptionEnabled: true,
          activeSimulations: this.ambulances.filter(a => !a.isAvailable).length,
          pendingQueue: this.queue.length,
          availableAmbulances: this.ambulances.filter(a => a.isAvailable).length
        });
      }
      if (pathname === '/auto-dispatch/toggle' && method === 'POST') {
        this.isAutoDispatch = !this.isAutoDispatch;
        return sendJson({ success: true, autoDispatchEnabled: this.isAutoDispatch, message: `Auto dispatch is ${this.isAutoDispatch ? 'ON' : 'OFF'}` });
      }

      // 15. /dijkstra/network
      if (pathname === '/dijkstra/network') {
        const ambMap = {};
        this.ambulances.forEach(a => {
          ambMap[a.locationId] = ambMap[a.locationId] || [];
          ambMap[a.locationId].push(a.ambulanceId);
        });

        const hospMap = {};
        this.hospitals.forEach(h => { hospMap[h.locationId] = h; });

        const zonesPayload = ZONES.map(z => {
          const h = hospMap[z.id];
          return {
            id: z.id,
            name: z.name,
            x: z.x,
            y: z.y,
            latitude: z.lat,
            longitude: z.lng,
            isHospital: !!h,
            hospitalName: h ? h.name : null,
            availableBeds: h ? h.availableBeds : 0,
            totalBeds: h ? h.totalBeds : 0,
            ambulances: ambMap[z.id] || []
          };
        });

        const roadsPayload = this.roads.map(r => ({
          fromId: r.fromId,
          fromName: this.getZone(r.fromId).name,
          fromX: this.getZone(r.fromId).x,
          fromY: this.getZone(r.fromId).y,
          toId: r.toId,
          toName: this.getZone(r.toId).name,
          toX: this.getZone(r.toId).x,
          toY: this.getZone(r.toId).y,
          distance: r.distance,
          travelTime: r.travelTime,
          isClosed: r.isClosed
        }));

        return sendJson({ zones: zonesPayload, roads: roadsPayload });
      }

      // 16. /dijkstra/trace
      if (pathname === '/dijkstra/trace') {
        const from = url.searchParams.get('from') || 'L1';
        const to = url.searchParams.get('to') || 'L8';
        const trace = this.generateDijkstraTrace(from, to);
        return sendJson(trace);
      }

      return sendJson({ error: 'Endpoint not found: ' + pathname }, 404);
    }
  }

  // Create global instance
  window.SimulationEngineInstance = new SimulationEngine();

  // Smart Global Fetch Interceptor
  const nativeFetch = window.fetch.bind(window);

  window.fetch = async function(resource, init = {}) {
    const urlStr = typeof resource === 'string' ? resource : (resource.url || '');

    // Only intercept /api/ requests
    if (urlStr.includes('/api/')) {
      const isGitHubPages = window.location.hostname.includes('github.io');

      // If running on GitHub Pages, directly route to client-side engine (bypassing mixed content & failed local network calls)
      if (isGitHubPages) {
        return window.SimulationEngineInstance.handleRequest(urlStr, init);
      }

      // If on localhost / custom domain, try local Java server first. If it fails, fallback gracefully to simulation!
      try {
        const res = await nativeFetch(resource, init);
        return res;
      } catch (networkErr) {
        // Fallback to SimulationEngine
        return window.SimulationEngineInstance.handleRequest(urlStr, init);
      }
    }

    return nativeFetch(resource, init);
  };

  // Banner on GitHub Pages
  if (typeof document !== 'undefined') {
    document.addEventListener('DOMContentLoaded', () => {
      if (window.location.hostname.includes('github.io') && !document.getElementById('gh-pages-demo-banner')) {
        const topBanner = document.createElement('div');
        topBanner.id = 'gh-pages-demo-banner';
        topBanner.style.cssText = `
          position: sticky;
          top: 0;
          left: 0;
          right: 0;
          background: #0f172a;
          color: #94a3b8;
          border-bottom: 1px solid #334155;
          padding: 8px 18px;
          font-size: 12.5px;
          font-weight: 500;
          display: flex;
          align-items: center;
          justify-content: space-between;
          z-index: 99999;
          box-shadow: 0 2px 6px rgba(0,0,0,0.25);
        `;
        topBanner.innerHTML = `
          <div style="display:flex;align-items:center;gap:10px;">
            <span style="display:inline-block;width:9px;height:9px;border-radius:50%;background:#10b981;box-shadow:0 0 8px #10b981;"></span>
            <span style="color:#f8fafc;font-weight:600;">Interactive In-Browser Simulation</span>
            <span style="color:#64748b;">|</span>
            <span>Client-side Dijkstra routing &amp; emergency dispatch active</span>
          </div>
          <div style="display:flex;align-items:center;gap:12px;">
            <a href="https://github.com/rithekasudhakar4-sketch/wbb" target="_blank" style="color:#38bdf8;text-decoration:none;font-weight:600;font-size:12px;">GitHub Repository &rarr;</a>
          </div>
        `;
        document.body.prepend(topBanner);
      }
    });
  }

  console.log('🚑 Nalam Nagar Simulation Engine Loaded (Client-Side Mode Active).');
})(window);

