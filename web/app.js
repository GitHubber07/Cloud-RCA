// App Logic for RCACopilot Web Dashboard

const API_BASE = 'http://localhost:8080/api';

// UI State
let activeView = 'dashboard';
let incidents = [];
let selectedIncidentId = null;
let currentWorkflows = {};
let currentAlertType = 'ConnectionTimeoutAlert';
let canvasNodes = []; // Nodes currently displayed on builder canvas
let selectedNodeId = null;

// DOM Elements
const btnDashboard = document.getElementById('btn-dashboard');
const btnBuilder = document.getElementById('btn-builder');
const viewDashboard = document.getElementById('view-dashboard');
const viewBuilder = document.getElementById('view-builder');
const btnRunSimulation = document.getElementById('btn-run-simulation');
const incidentList = document.getElementById('incident-list');
const detailsPanel = document.getElementById('details-panel');
const alertSelect = document.getElementById('builder-alert-select');
const btnSaveWorkflow = document.getElementById('btn-save-workflow');
const flowCanvas = document.getElementById('flow-canvas');
const canvasSvg = document.getElementById('canvas-svg');
const inspectorPanel = document.getElementById('inspector-panel');
const inspectorContent = document.getElementById('inspector-content');
const statAccuracy = document.getElementById('stat-accuracy');
const statTotal = document.getElementById('stat-total');
const toastContainer = document.getElementById('toast-container');

// Toolbar buttons
const btnAddQuery = document.getElementById('btn-add-query');
const btnAddScope = document.getElementById('btn-add-scope');
const btnAddMitigation = document.getElementById('btn-add-mitigation');
const btnResetLayout = document.getElementById('btn-reset-layout');

// ----------------------------------------------------
// Navigation & Toast Functions
// ----------------------------------------------------
function switchView(viewName) {
    activeView = viewName;
    if (viewName === 'dashboard') {
        btnDashboard.classList.add('active');
        btnBuilder.classList.remove('active');
        viewDashboard.classList.remove('hidden');
        viewBuilder.classList.add('hidden');
        fetchIncidents();
    } else {
        btnDashboard.classList.remove('active');
        btnBuilder.classList.add('active');
        viewDashboard.classList.add('hidden');
        viewBuilder.classList.remove('hidden');
        loadWorkflowBuilder();
    }
}

btnDashboard.addEventListener('click', () => switchView('dashboard'));
btnBuilder.addEventListener('click', () => switchView('builder'));

function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `<span>${type === 'success' ? '✔' : '✘'}</span> ${message}`;
    toastContainer.appendChild(toast);
    setTimeout(() => {
        toast.remove();
    }, 4000);
}

// ----------------------------------------------------
// API Communication - Incidents Dashboard
// ----------------------------------------------------
async function fetchIncidents() {
    try {
        const response = await fetch(`${API_BASE}/incidents`);
        if (!response.ok) throw new Error('Failed to fetch incidents');
        incidents = await response.json();
        
        // Update stats
        statTotal.textContent = incidents.length;
        calculateGlobalStats();
        
        renderIncidentsList();
        if (selectedIncidentId) {
            showIncidentDetails(selectedIncidentId);
        }
    } catch (err) {
        console.error(err);
        incidentList.innerHTML = `<div class="empty-state text-error">Could not connect to database server. Ensure backend is running.</div>`;
    }
}

function calculateGlobalStats() {
    const testSet = incidents.filter(i => i.id.startsWith('INC-TEST-'));
    if (testSet.length === 0) {
        statAccuracy.textContent = '0.000';
        return;
    }
    const correct = testSet.filter(i => i.predictedCategory && i.predictedCategory.toLowerCase() === i.trueCategory.toLowerCase()).length;
    const accuracy = correct / testSet.length;
    statAccuracy.textContent = accuracy.toFixed(3);
}

function renderIncidentsList() {
    const searchQuery = document.getElementById('incident-search').value.toLowerCase();
    const filtered = incidents.filter(i => {
        return i.id.toLowerCase().includes(searchQuery) ||
               i.alertType.toLowerCase().includes(searchQuery) ||
               i.trueCategory.toLowerCase().includes(searchQuery) ||
               (i.predictedCategory && i.predictedCategory.toLowerCase().includes(searchQuery));
    });

    document.getElementById('incident-count').textContent = `${filtered.length} alerts`;

    if (filtered.length === 0) {
        incidentList.innerHTML = `<div class="empty-state">No matching incidents found.</div>`;
        return;
    }

    incidentList.innerHTML = filtered.map(i => {
        const isSelected = i.id === selectedIncidentId ? 'selected' : '';
        const isCorrect = i.predictedCategory && i.predictedCategory.toLowerCase() === i.trueCategory.toLowerCase();
        
        let statusBadge = '';
        if (i.id.startsWith('INC-TEST-')) {
            if (!i.predictedCategory) {
                statusBadge = `<span class="tag alert-type" style="background: rgba(245,158,11,0.15); color: #f59e0b; border: 1px solid rgba(245,158,11,0.2)">PENDING</span>`;
            } else {
                statusBadge = isCorrect 
                    ? `<span class="tag cat-correct">✔ CORRECT</span>`
                    : `<span class="tag cat-failed">✘ MISCLASSIFIED</span>`;
            }
        } else {
            statusBadge = `<span class="tag" style="background: rgba(148,163,184,0.15); color: #94a3b8; border: 1px solid rgba(148,163,184,0.2)">HISTORICAL</span>`;
        }

        return `
            <div class="incident-item ${isSelected}" onclick="selectIncident('${i.id}')">
                <div class="incident-row-top">
                    <span class="incident-id">${i.id}</span>
                    <span class="incident-time">${new Date(i.timestamp).toLocaleString()}</span>
                </div>
                <div class="incident-title" title="${i.title}">${i.title}</div>
                <div class="incident-tags">
                    <span class="tag alert-type">${i.alertType}</span>
                    ${statusBadge}
                </div>
            </div>
        `;
    }).join('');
}

document.getElementById('incident-search').addEventListener('input', renderIncidentsList);

function selectIncident(id) {
    selectedIncidentId = id;
    renderIncidentsList();
    showIncidentDetails(id);
}

async function showIncidentDetails(id) {
    const incident = incidents.find(i => i.id === id);
    if (!incident) return;

    detailsPanel.innerHTML = `<div class="empty-state">Loading logs and diagnostics...</div>`;

    try {
        const response = await fetch(`${API_BASE}/incidents/${id}/logs`);
        if (!response.ok) throw new Error('Failed to fetch logs');
        const logs = await response.json();
        
        let outcomeClass = 'meta-box';
        let outcomeText = 'N/A';
        if (incident.id.startsWith('INC-TEST-')) {
            if (incident.predictedCategory) {
                const isCorrect = incident.predictedCategory.toLowerCase() === incident.trueCategory.toLowerCase();
                outcomeClass = isCorrect ? 'meta-box success' : 'meta-box fail';
                outcomeText = isCorrect ? '✔ SUCCESS' : '✘ FAILED';
            } else {
                outcomeText = 'PENDING';
            }
        } else {
            outcomeText = 'HISTORICAL';
        }

        let logsTimeline = logs.map(log => {
            let dotType = 'query';
            if (log.source === 'probe') dotType = 'scope';
            else if (log.source === 'exceptions') dotType = 'query';
            else if (log.source === 'mitigation') dotType = 'mitigation';

            return `
                <div class="timeline-item">
                    <div class="timeline-dot ${dotType}"></div>
                    <div class="timeline-content">
                        <div class="timeline-source">
                            <strong>${log.source.toUpperCase()}</strong> [${log.logLevel}]
                            <span>${new Date(log.createdAt).toLocaleTimeString()}</span>
                        </div>
                        <div class="timeline-message">${log.message}</div>
                    </div>
                </div>
            `;
        }).join('');

        if (logs.length === 0) {
            logsTimeline = `<div class="empty-state" style="padding: 10px 0;">No telemetry logs recorded in database.</div>`;
        }

        // Check if there are nearest neighbors (visual similarity matching)
        // Since we load simulation results, let's visualize a mock neighbor grid using category mappings
        let neighborsSection = '';
        if (incident.id.startsWith('INC-TEST-') && incident.predictedCategory) {
            // Find other incidents with same category as neighbors
            const histNeighbors = incidents
                .filter(i => i.id.startsWith('INC-HIST-') && i.trueCategory === incident.trueCategory)
                .slice(0, 3);
                
            const neighborCards = histNeighbors.map((n, idx) => {
                const score = 0.95 - (idx * 0.05); // Simulated decay score
                return `
                    <div class="neighbor-card">
                        <div class="neighbor-left">
                            <h5>${n.id} [${n.alertType}]</h5>
                            <p>${n.title}</p>
                        </div>
                        <div class="neighbor-right">
                            <span class="tag" style="background: rgba(139,92,246,0.15); color: #c084fc; border: 1px solid rgba(139,92,246,0.2)">${n.trueCategory}</span>
                            <div class="similarity-meter">
                                <span class="similarity-val">${(score * 100).toFixed(0)}%</span>
                                <div class="meter-bar">
                                    <div class="meter-fill" style="width: ${score * 100}%"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                `;
            }).join('');

            if (neighborCards) {
                neighborsSection = `
                    <div class="neighbors-section">
                        <h4>Top FastText Match Neighborhood</h4>
                        <div class="neighbors-grid">
                            ${neighborCards}
                        </div>
                    </div>
                `;
            }
        }

        detailsPanel.innerHTML = `
            <div class="details-container">
                <div class="detail-header">
                    <div class="detail-header-top">
                        <h3>${incident.id}</h3>
                        <span class="tag alert-type">${incident.alertType}</span>
                    </div>
                    <div class="detail-desc">${incident.title}</div>
                </div>

                <div class="detail-meta-grid">
                    <div class="meta-box">
                        <span>Alert Timestamp</span>
                        <strong>${new Date(incident.timestamp).toLocaleTimeString()}</strong>
                    </div>
                    <div class="meta-box">
                        <span>True Class</span>
                        <strong style="color: var(--color-accent)">${incident.trueCategory}</strong>
                    </div>
                    <div class="meta-box">
                        <span>LLM Predicted Class</span>
                        <strong style="color: var(--node-query)">${incident.predictedCategory || 'PENDING'}</strong>
                    </div>
                    <div class="${outcomeClass}">
                        <span>Outcome</span>
                        <strong>${outcomeText}</strong>
                    </div>
                </div>

                <div class="timeline-section">
                    <h4>Diagnostic Telemetry Timeline</h4>
                    <div class="timeline">
                        ${logsTimeline}
                    </div>
                </div>

                ${neighborsSection}

                <div class="explanation-section">
                    <h4>Chain-of-Thought Explanation (Llama 3)</h4>
                    <div class="explanation-box">
                        <div class="explanation-text">${incident.explanation || 'No explanation generated yet. Run the evaluation simulation.'}</div>
                    </div>
                </div>
            </div>
        `;
    } catch (err) {
        detailsPanel.innerHTML = `<div class="empty-state text-error">Failed to load details: ${err.message}</div>`;
    }
}

// Trigger simulation
btnRunSimulation.addEventListener('click', async () => {
    btnRunSimulation.disabled = true;
    btnRunSimulation.textContent = 'Running...';
    try {
        const response = await fetch(`${API_BASE}/simulation/run`, { method: 'POST' });
        if (!response.ok) throw new Error('Simulation failed');
        showToast('Evaluation simulation run triggered in background!');
        
        // Wait a few seconds then refresh
        setTimeout(() => {
            fetchIncidents();
            btnRunSimulation.disabled = false;
            btnRunSimulation.innerHTML = '<span>🚀</span> Trigger Evaluation Run';
        }, 3000);
    } catch (err) {
        showToast('Failed to trigger simulation: ' + err.message, 'error');
        btnRunSimulation.disabled = false;
        btnRunSimulation.innerHTML = '<span>🚀</span> Trigger Evaluation Run';
    }
});

// ----------------------------------------------------
// Workflow Builder Canvas Editor
// ----------------------------------------------------
alertSelect.addEventListener('change', (e) => {
    currentAlertType = e.target.value;
    loadWorkflowBuilder();
});

async function loadWorkflowBuilder() {
    selectedNodeId = null;
    updateInspector();
    
    try {
        const response = await fetch(`${API_BASE}/workflows`);
        if (!response.ok) throw new Error('Failed to fetch workflows');
        const workflows = await response.json();
        
        // Find workflow for alert type
        const wData = workflows.find(w => w.alertType === currentAlertType);
        if (wData) {
            const actions = JSON.parse(wData.actionsJson);
            canvasNodes = actions.map(act => {
                // If it already has x, y coordinates from client editing, keep them. Otherwise set defaults.
                return {
                    id: act.id,
                    type: act.type,
                    x: act.x || 100,
                    y: act.y || 100,
                    // Subclass-specific properties
                    querySource: act.querySource || '',
                    defaultNextActionId: act.defaultNextActionId || null,
                    keywordRoutes: act.keywordRoutes || {},
                    newScope: act.newScope || '',
                    targetResource: act.targetResource || '',
                    nextActionId: act.nextActionId || null,
                    mitigationRecommendation: act.mitigationRecommendation || ''
                };
            });
            
            // Auto layout nodes if they are all centered at defaults
            const isAllDefault = canvasNodes.every(n => n.x === 100 && n.y === 100);
            if (isAllDefault) {
                runAutoLayout(wData.startActionId);
            }
            
            renderCanvas(wData.startActionId);
        } else {
            flowCanvas.innerHTML = `<div class="empty-state">No workflow found for alert type: ${currentAlertType}</div>`;
        }
    } catch (err) {
        console.error(err);
        flowCanvas.innerHTML = `<div class="empty-state text-error">Failed to load workflow builder: ${err.message}</div>`;
    }
}

function runAutoLayout(startId) {
    let visited = new Set();
    let queue = [{ id: startId, x: 50, y: 150, level: 0 }];
    let nodeMap = {};
    canvasNodes.forEach(n => { nodeMap[n.id] = n; });
    
    let levels = {};
    
    while (queue.length > 0) {
        let curr = queue.shift();
        if (visited.has(curr.id)) continue;
        visited.add(curr.id);
        
        let node = nodeMap[curr.id];
        if (!node) continue;
        
        if (!levels[curr.level]) levels[curr.level] = 0;
        let yOffset = levels[curr.level] * 180;
        levels[curr.level]++;
        
        node.x = curr.x;
        node.y = curr.y + yOffset;
        
        if (node.type === 'QUERY') {
            if (node.defaultNextActionId) {
                queue.push({ id: node.defaultNextActionId, x: curr.x + 280, y: curr.y, level: curr.level + 1 });
            }
            if (node.keywordRoutes) {
                for (let k in node.keywordRoutes) {
                    let nextId = node.keywordRoutes[k];
                    queue.push({ id: nextId, x: curr.x + 280, y: curr.y, level: curr.level + 1 });
                }
            }
        } else if (node.type === 'SCOPE_SWITCH') {
            if (node.nextActionId) {
                queue.push({ id: node.nextActionId, x: curr.x + 280, y: curr.y, level: curr.level + 1 });
            }
        }
    }
    
    // Position unvisited nodes
    let unvisitedY = 400;
    canvasNodes.forEach(n => {
        if (!visited.has(n.id)) {
            n.x = 50;
            n.y = unvisitedY;
            unvisitedY += 160;
        }
    });
}

btnResetLayout.addEventListener('click', () => {
    // Find starting action ID from list. We assume first node or a node with specific ID is start
    // We can guess start ID: it's usually the one that has QUERY in it or we select the first node
    if (canvasNodes.length > 0) {
        const startNode = canvasNodes.find(n => n.id.startsWith('QUERY_')) || canvasNodes[0];
        runAutoLayout(startNode.id);
        renderCanvas(startNode.id);
    }
});

function renderCanvas(startId) {
    // Re-create SVG headers/markers
    canvasSvg.innerHTML = `
        <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                <path d="M 0 0 L 10 5 L 0 10 z" class="arrow-head"></path>
            </marker>
            <marker id="arrow-keyword" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                <path d="M 0 0 L 10 5 L 0 10 z" class="arrow-head-keyword"></path>
            </marker>
        </defs>
    `;

    // Clear old flow node DOM items
    const oldNodes = flowCanvas.querySelectorAll('.flow-node');
    oldNodes.forEach(n => n.remove());

    // Draw connection lines and node DOM elements
    canvasNodes.forEach(node => {
        createNodeDOM(node);
    });

    drawConnections();
}

function createNodeDOM(node) {
    const card = document.createElement('div');
    card.className = `flow-node ${node.type.toLowerCase()}`;
    if (node.id === selectedNodeId) card.classList.add('selected');
    card.style.left = `${node.x}px`;
    card.style.top = `${node.y}px`;
    card.dataset.id = node.id;

    let bodyHTML = '';
    if (node.type === 'QUERY') {
        bodyHTML = `
            <div class="node-id-display">${node.id}</div>
            <div class="node-meta-display">Source: <strong>${node.querySource || 'N/A'}</strong></div>
        `;
    } else if (node.type === 'SCOPE_SWITCH') {
        bodyHTML = `
            <div class="node-id-display">${node.id}</div>
            <div class="node-meta-display">Scope: <strong>${node.newScope || 'N/A'}</strong></div>
        `;
    } else {
        bodyHTML = `
            <div class="node-id-display">${node.id}</div>
            <div class="node-meta-display" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;" title="${node.mitigationRecommendation}">${node.mitigationRecommendation || 'No recommendation'}</div>
        `;
    }

    card.innerHTML = `
        <div class="node-header">
            <span>${node.type}</span>
            <span class="node-type-label">${node.type.substring(0,3)}</span>
        </div>
        <div class="node-body">
            ${bodyHTML}
        </div>
        <div class="node-port port-input"></div>
        <div class="node-port port-output"></div>
    `;

    // Selection
    card.addEventListener('mousedown', (e) => {
        if (e.target.closest('.node-port')) return; // Avoid drag on ports
        selectedNodeId = node.id;
        updateSelectedNodeUI();
        updateInspector();

        // Drag Setup
        let startX = e.clientX;
        let startY = e.clientY;
        let origX = node.x;
        let origY = node.y;

        function onMouseMove(moveEvt) {
            const dx = moveEvt.clientX - startX;
            const dy = moveEvt.clientY - startY;
            node.x = origX + dx;
            node.y = origY + dy;
            card.style.left = `${node.x}px`;
            card.style.top = `${node.y}px`;
            drawConnections();
        }

        function onMouseUp() {
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
        }

        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', onMouseUp);
    });

    flowCanvas.appendChild(card);
}

function updateSelectedNodeUI() {
    const all = flowCanvas.querySelectorAll('.flow-node');
    all.forEach(c => {
        if (c.dataset.id === selectedNodeId) {
            c.classList.add('selected');
        } else {
            c.classList.remove('selected');
        }
    });
}

function drawConnections() {
    // Clear old lines/labels
    const oldPaths = canvasSvg.querySelectorAll('path, text');
    oldPaths.forEach(p => p.remove());

    const nodeWidth = 220;
    const nodeHeight = 85;

    canvasNodes.forEach(node => {
        const outX = node.x + nodeWidth;
        const outY = node.y + (nodeHeight / 2);

        if (node.type === 'QUERY') {
            // Default route
            if (node.defaultNextActionId) {
                const target = canvasNodes.find(n => n.id === node.defaultNextActionId);
                if (target) {
                    drawCurve(outX, outY, target.x, target.y + (nodeHeight / 2), 'default');
                }
            }
            // Keyword routes
            if (node.keywordRoutes) {
                for (let keyword in node.keywordRoutes) {
                    const targetId = node.keywordRoutes[keyword];
                    const target = canvasNodes.find(n => n.id === targetId);
                    if (target) {
                        drawCurve(outX, outY, target.x, target.y + (nodeHeight / 2), 'keyword', keyword);
                    }
                }
            }
        } else if (node.type === 'SCOPE_SWITCH') {
            if (node.nextActionId) {
                const target = canvasNodes.find(n => n.id === node.nextActionId);
                if (target) {
                    drawCurve(outX, outY, target.x, target.y + (nodeHeight / 2), 'default');
                }
            }
        }
    });
}

function drawCurve(x1, y1, x2, y2, style, label = '') {
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    
    // Smooth bezier curve control points
    const cp1x = x1 + 80;
    const cp1y = y1;
    const cp2x = x2 - 80;
    const cp2y = y2;
    
    path.setAttribute('d', `M ${x1} ${y1} C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${x2} ${y2}`);
    
    if (style === 'keyword') {
        path.setAttribute('class', 'keyword-route');
        path.setAttribute('marker-end', 'url(#arrow-keyword)');
    } else {
        path.setAttribute('marker-end', 'url(#arrow)');
    }
    
    canvasSvg.appendChild(path);

    if (label) {
        const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        const mx = (x1 + x2) / 2;
        const my = (y1 + y2) / 2 - 8;
        text.setAttribute('x', mx);
        text.setAttribute('y', my);
        text.setAttribute('fill', 'var(--color-accent)');
        text.setAttribute('font-size', '10px');
        text.setAttribute('font-weight', '600');
        text.setAttribute('font-family', 'var(--font-mono)');
        text.setAttribute('text-anchor', 'middle');
        text.textContent = label;
        canvasSvg.appendChild(text);
    }
}

// ----------------------------------------------------
// Inspector Panel UI Form Configuration
// ----------------------------------------------------
function updateInspector() {
    if (!selectedNodeId) {
        inspectorContent.innerHTML = `<div class="empty-state">Select a node on the canvas to configure it.</div>`;
        return;
    }

    const node = canvasNodes.find(n => n.id === selectedNodeId);
    if (!node) {
        inspectorContent.innerHTML = `<div class="empty-state text-error">Selected node not found.</div>`;
        return;
    }

    let fieldsHTML = '';
    const otherNodesOptions = canvasNodes
        .filter(n => n.id !== node.id)
        .map(n => `<option value="${n.id}">${n.id}</option>`)
        .join('');

    const defaultEmptyOption = '<option value="">(None - Terminal)</option>';

    if (node.type === 'QUERY') {
        // Keyword routes list editor
        let keywordRoutesList = '';
        if (node.keywordRoutes) {
            for (let kw in node.keywordRoutes) {
                const targetId = node.keywordRoutes[kw];
                const routeOptions = canvasNodes
                    .filter(n => n.id !== node.id)
                    .map(n => `<option value="${n.id}" ${n.id === targetId ? 'selected' : ''}>${n.id}</option>`)
                    .join('');

                keywordRoutesList += `
                    <div class="form-row" data-keyword="${kw}" style="margin-bottom: 6px;">
                        <input type="text" value="${kw}" class="input-kw-name" style="width: 80px;" disabled>
                        <select class="select-kw-target">
                            ${routeOptions}
                        </select>
                        <button type="button" class="tool-btn" style="background:rgba(239,68,68,0.1); border-color:rgba(239,68,68,0.2); color:var(--color-error); padding:4px 8px;" onclick="removeKeywordRoute('${kw}')">🗑</button>
                    </div>
                `;
            }
        }

        fieldsHTML = `
            <div class="form-group">
                <label>Query Telemetry Source</label>
                <select id="field-querySource">
                    <option value="socket_metrics" ${node.querySource === 'socket_metrics' ? 'selected' : ''}>socket_metrics</option>
                    <option value="exceptions" ${node.querySource === 'exceptions' ? 'selected' : ''}>exceptions</option>
                    <option value="thread_stacks" ${node.querySource === 'thread_stacks' ? 'selected' : ''}>thread_stacks</option>
                    <option value="probe" ${node.querySource === 'probe' ? 'selected' : ''}>probe</option>
                    <option value="config" ${node.querySource === 'config' ? 'selected' : ''}>config</option>
                </select>
            </div>
            
            <div class="form-group">
                <label>Default Next Step</label>
                <select id="field-defaultNext">
                    ${defaultEmptyOption}
                    ${canvasNodes.filter(n => n.id !== node.id).map(n => `
                        <option value="${n.id}" ${n.id === node.defaultNextActionId ? 'selected' : ''}>${n.id}</option>
                    `).join('')}
                </select>
            </div>

            <div class="form-group">
                <label>Conditional Keyword Routing</label>
                <div style="margin-top: 6px;" id="kw-routes-container">
                    ${keywordRoutesList}
                </div>
                <div class="form-row" style="margin-top: 8px;">
                    <input type="text" id="new-kw-input" placeholder="e.g. timeout" style="font-size:12px; padding:4px 8px;">
                    <button type="button" class="tool-btn" onclick="addKeywordRoute()">Add Route</button>
                </div>
            </div>
        `;
    } else if (node.type === 'SCOPE_SWITCH') {
        fieldsHTML = `
            <div class="form-group">
                <label>New Scope Level</label>
                <select id="field-newScope">
                    <option value="MACHINE" ${node.newScope === 'MACHINE' ? 'selected' : ''}>MACHINE</option>
                    <option value="PROCESS" ${node.newScope === 'PROCESS' ? 'selected' : ''}>PROCESS</option>
                    <option value="VM" ${node.newScope === 'VM' ? 'selected' : ''}>VM</option>
                    <option value="FOREST" ${node.newScope === 'FOREST' ? 'selected' : ''}>FOREST</option>
                </select>
            </div>

            <div class="form-group">
                <label>Target Resource Variable</label>
                <input type="text" id="field-targetResource" value="${node.targetResource || ''}" placeholder="e.g. Machine-123">
            </div>

            <div class="form-group">
                <label>Next Step</label>
                <select id="field-nextActionId">
                    ${defaultEmptyOption}
                    ${canvasNodes.filter(n => n.id !== node.id).map(n => `
                        <option value="${n.id}" ${n.id === node.nextActionId ? 'selected' : ''}>${n.id}</option>
                    `).join('')}
                </select>
            </div>
        `;
    } else if (node.type === 'MITIGATION') {
        fieldsHTML = `
            <div class="form-group">
                <label>Mitigation Recommendation Action</label>
                <textarea id="field-recommendation" placeholder="Describe instructions for the engineer to execute...">${node.mitigationRecommendation || ''}</textarea>
            </div>
        `;
    }

    inspectorContent.innerHTML = `
        <form class="inspector-form" onsubmit="event.preventDefault();">
            <div class="form-group">
                <label>Node ID (Unique Key)</label>
                <input type="text" id="field-id" value="${node.id}" style="font-family: var(--font-mono);">
            </div>
            
            ${fieldsHTML}

            <button type="button" class="delete-node-btn" onclick="deleteSelectedNode()">🗑 Delete Node</button>
        </form>
    `;

    // Hook inputs to save back to model automatically
    const idInput = document.getElementById('field-id');
    if (idInput) {
        idInput.addEventListener('change', (e) => {
            const val = e.target.value.trim().toUpperCase().replace(/\s+/g, '_');
            if (val && val !== node.id) {
                // Ensure unique ID
                if (canvasNodes.some(n => n.id === val)) {
                    showToast('Node ID must be unique!', 'error');
                    e.target.value = node.id;
                    return;
                }
                
                // Update references in other nodes
                canvasNodes.forEach(n => {
                    if (n.defaultNextActionId === node.id) n.defaultNextActionId = val;
                    if (n.nextActionId === node.id) n.nextActionId = val;
                    if (n.keywordRoutes) {
                        for (let k in n.keywordRoutes) {
                            if (n.keywordRoutes[k] === node.id) n.keywordRoutes[k] = val;
                        }
                    }
                });

                node.id = val;
                selectedNodeId = val;
                e.target.value = val;
                
                loadWorkflowBuilder(); // Refresh canvas
            }
        });
    }

    // QUERY Source
    const querySourceSelect = document.getElementById('field-querySource');
    if (querySourceSelect) {
        querySourceSelect.addEventListener('change', (e) => {
            node.querySource = e.target.value;
            loadWorkflowBuilder();
        });
    }

    // QUERY Default Next
    const defaultNextSelect = document.getElementById('field-defaultNext');
    if (defaultNextSelect) {
        defaultNextSelect.addEventListener('change', (e) => {
            node.defaultNextActionId = e.target.value || null;
            drawConnections();
        });
    }

    // SCOPE SWITCH fields
    const newScopeSelect = document.getElementById('field-newScope');
    if (newScopeSelect) {
        newScopeSelect.addEventListener('change', (e) => {
            node.newScope = e.target.value;
            loadWorkflowBuilder();
        });
    }
    const targetResourceInput = document.getElementById('field-targetResource');
    if (targetResourceInput) {
        targetResourceInput.addEventListener('input', (e) => {
            node.targetResource = e.target.value;
        });
    }
    const nextActionSelect = document.getElementById('field-nextActionId');
    if (nextActionSelect) {
        nextActionSelect.addEventListener('change', (e) => {
            node.nextActionId = e.target.value || null;
            drawConnections();
        });
    }

    // MITIGATION fields
    const recommendationTextarea = document.getElementById('field-recommendation');
    if (recommendationTextarea) {
        recommendationTextarea.addEventListener('input', (e) => {
            node.mitigationRecommendation = e.target.value;
            // Update node display title on change
            const dom = flowCanvas.querySelector(`[data-id="${node.id}"] .node-meta-display`);
            if (dom) dom.textContent = e.target.value;
        });
    }

    // Keyword targets change
    const kwTargets = inspectorContent.querySelectorAll('.select-kw-target');
    kwTargets.forEach(select => {
        select.addEventListener('change', (e) => {
            const kw = e.target.closest('.form-row').dataset.keyword;
            node.keywordRoutes[kw] = e.target.value;
            drawConnections();
        });
    });
}

// Inspector global helper operations
window.addKeywordRoute = function() {
    const input = document.getElementById('new-kw-input');
    const kw = input.value.trim().toLowerCase();
    if (!kw) return;
    
    const node = canvasNodes.find(n => n.id === selectedNodeId);
    if (!node || node.type !== 'QUERY') return;

    if (node.keywordRoutes[kw]) {
        showToast('Keyword route already exists!', 'error');
        return;
    }

    // Find any default target that is not this node
    const targetNode = canvasNodes.find(n => n.id !== node.id);
    node.keywordRoutes[kw] = targetNode ? targetNode.id : '';
    
    input.value = '';
    updateInspector();
    drawConnections();
};

window.removeKeywordRoute = function(kw) {
    const node = canvasNodes.find(n => n.id === selectedNodeId);
    if (!node || node.type !== 'QUERY') return;
    
    delete node.keywordRoutes[kw];
    updateInspector();
    drawConnections();
};

window.deleteSelectedNode = function() {
    if (!selectedNodeId) return;
    
    const confirmDelete = confirm(`Are you sure you want to delete node ${selectedNodeId}?`);
    if (!confirmDelete) return;

    // Delete connections referencing this node
    canvasNodes.forEach(n => {
        if (n.defaultNextActionId === selectedNodeId) n.defaultNextActionId = null;
        if (n.nextActionId === selectedNodeId) n.nextActionId = null;
        if (n.keywordRoutes) {
            for (let k in n.keywordRoutes) {
                if (n.keywordRoutes[k] === selectedNodeId) {
                    delete n.keywordRoutes[k];
                }
            }
        }
    });

    canvasNodes = canvasNodes.filter(n => n.id !== selectedNodeId);
    selectedNodeId = null;
    updateInspector();
    
    // Refresh builder
    const startNode = canvasNodes.find(n => n.id.startsWith('QUERY_')) || canvasNodes[0];
    renderCanvas(startNode ? startNode.id : null);
};

// ----------------------------------------------------
// Toolbar Action Node Insertion
// ----------------------------------------------------
function insertNode(type) {
    // Generate unique ID
    let count = 1;
    let id = `${type}_STEP_${count}`;
    while (canvasNodes.some(n => n.id === id)) {
        count++;
        id = `${type}_STEP_${count}`;
    }

    const newNode = {
        id: id,
        type: type,
        x: 150 + Math.random() * 50,
        y: 150 + Math.random() * 50,
        querySource: type === 'QUERY' ? 'exceptions' : '',
        defaultNextActionId: null,
        keywordRoutes: {},
        newScope: type === 'SCOPE_SWITCH' ? 'MACHINE' : '',
        targetResource: '',
        nextActionId: null,
        mitigationRecommendation: type === 'MITIGATION' ? 'Clear temp dump directory.' : ''
    };

    canvasNodes.push(newNode);
    selectedNodeId = id;
    
    const startNode = canvasNodes.find(n => n.id.startsWith('QUERY_')) || canvasNodes[0];
    renderCanvas(startNode ? startNode.id : null);
    updateInspector();
    
    showToast(`Created new ${type} node: ${id}`);
}

btnAddQuery.addEventListener('click', () => insertNode('QUERY'));
btnAddScope.addEventListener('click', () => insertNode('SCOPE_SWITCH'));
btnAddMitigation.addEventListener('click', () => insertNode('MITIGATION'));

// ----------------------------------------------------
// Save Workflow changes to API
// ----------------------------------------------------
btnSaveWorkflow.addEventListener('click', async () => {
    if (canvasNodes.length === 0) {
        showToast('Cannot save an empty workflow graph!', 'error');
        return;
    }

    // Guess start action ID: it's the root node that does not have any other node pointing to it, 
    // or simply the first node in the layout that starts with QUERY_ or has no input references.
    // In our case, to be exact, we can find a node whose ID contains "START" or is "QUERY_SOCKETS" / "QUERY_EXCEPTIONS", 
    // or just the first QUERY node.
    let startActionId = '';
    const queryNodes = canvasNodes.filter(n => n.type === 'QUERY');
    const startNode = queryNodes.find(n => n.id.includes('START') || n.id.includes('SOCKET') || n.id.includes('EXCEP')) || queryNodes[0] || canvasNodes[0];
    if (startNode) {
        startActionId = startNode.id;
    }

    // Compile minimal format payload for Database
    const payload = {
        alertType: currentAlertType,
        startActionId: startActionId,
        actions: canvasNodes.map(n => {
            const item = {
                id: n.id,
                type: n.type,
                x: n.x,
                y: n.y
            };
            if (n.type === 'QUERY') {
                item.querySource = n.querySource;
                item.defaultNextActionId = n.defaultNextActionId;
                item.keywordRoutes = n.keywordRoutes;
            } else if (n.type === 'SCOPE_SWITCH') {
                item.newScope = n.newScope;
                item.targetResource = n.targetResource;
                item.nextActionId = n.nextActionId;
            } else if (n.type === 'MITIGATION') {
                item.mitigationRecommendation = n.mitigationRecommendation;
            }
            return item;
        })
    };

    try {
        const response = await fetch(`${API_BASE}/workflows`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) throw new Error('Failed to save');
        showToast(`Workflow for ${currentAlertType} saved successfully!`);
    } catch (err) {
        showToast('Error saving workflow: ' + err.message, 'error');
    }
});

// ----------------------------------------------------
// Init Application
// ----------------------------------------------------
fetchIncidents();
// Periodically poll for incident changes in case a background run completes
setInterval(() => {
    if (activeView === 'dashboard') {
        fetchIncidents();
    }
}, 5000);
