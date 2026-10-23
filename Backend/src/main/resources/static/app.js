/**
 * TaskPilot Frontend – app.js
 * Handles chat, task list, MCP debugger, and particle animation.
 * Talks to the Spring Boot backend at /api/* (REST) and /mcp/message (MCP SSE transport).
 */

'use strict';

// ============================================================
// CONFIG
// ============================================================
const API_BASE = '';  // Same origin (Spring Boot serves this file)

// ============================================================
// STATE
// ============================================================
let currentFilter = 'ALL';
let isLoading = false;

// ============================================================
// PARTICLES (background animation)
// ============================================================
function initParticles() {
    const container = document.getElementById('particles');
    if (!container) return;
    const count = 25;
    for (let i = 0; i < count; i++) {
        const p = document.createElement('div');
        p.className = 'particle';
        p.style.left = Math.random() * 100 + '%';
        p.style.top = Math.random() * 100 + '%';
        p.style.animationDuration = (8 + Math.random() * 12) + 's';
        p.style.animationDelay = (Math.random() * 10) + 's';
        p.style.width = p.style.height = (Math.random() > 0.5 ? 2 : 3) + 'px';
        container.appendChild(p);
    }
}

// ============================================================
// SERVER HEALTH CHECK
// ============================================================
async function checkHealth() {
    const dot = document.getElementById('statusDot');
    try {
        const res = await fetch(`${API_BASE}/api/health`, { signal: AbortSignal.timeout(3000) });
        if (res.ok) {
            dot.className = 'status-dot online';
            dot.title = 'Server online';
        } else {
            dot.className = 'status-dot offline';
        }
    } catch {
        dot.className = 'status-dot offline';
        dot.title = 'Server offline';
    }
}

// ============================================================
// CHAT
// ============================================================
function handleKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
    // Auto-resize textarea
    const ta = event.target;
    ta.style.height = 'auto';
    ta.style.height = Math.min(ta.scrollHeight, 120) + 'px';
}

async function sendMessage() {
    if (isLoading) return;

    const input = document.getElementById('messageInput');
    const message = input.value.trim();
    if (!message) return;

    input.value = '';
    input.style.height = 'auto';

    // Display user message
    appendMessage('user', message);

    // Show typing indicator
    const typingId = showTyping();
    setLoading(true);

    try {
        const response = await fetch(`${API_BASE}/api/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message }),
            signal: AbortSignal.timeout(45000)
        });

        removeTyping(typingId);

        const data = await response.json();

        if (response.ok && data.success) {
            appendMessage('assistant', data.reply);
        } else {
            appendMessage('assistant', '❌ ' + (data.error || data.reply || 'Something went wrong.'), true);
        }

        // Refresh task list after any chat interaction
        await loadTasks();

    } catch (err) {
        removeTyping(typingId);
        if (err.name === 'TimeoutError') {
            appendMessage('assistant', '⏱️ Request timed out. Please try again.', true);
        } else {
            appendMessage('assistant', '❌ Could not reach the server. Is it running on port 8080?', true);
        }
    } finally {
        setLoading(false);
        input.focus();
    }
}

function appendMessage(role, text, isError = false) {
    const area = document.getElementById('messagesArea');
    const msg = document.createElement('div');
    msg.className = `message ${role}-message`;

    const avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.textContent = role === 'user' ? '👤' : '🤖';

    const bubble = document.createElement('div');
    bubble.className = 'message-bubble' + (isError ? ' error-bubble' : '');
    if (isError) {
        bubble.style.borderLeftColor = 'var(--error)';
        bubble.style.background = 'rgba(239,68,68,0.05)';
    }
    bubble.innerHTML = formatText(text);

    msg.appendChild(avatar);
    msg.appendChild(bubble);
    area.appendChild(msg);
    area.scrollTop = area.scrollHeight;
}

function formatText(text) {
    if (!text) return '';
    // Escape HTML
    let escaped = text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');

    // Bold **text**
    escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');

    // Highlight task IDs
    escaped = escaped.replace(/(task-[a-f0-9]{8})/g,
        '<code style="font-family:JetBrains Mono,monospace;background:rgba(99,102,241,0.15);padding:1px 5px;border-radius:4px;color:var(--primary-light);font-size:0.85em;cursor:pointer;" onclick="copyToClipboard(\'$1\')">$1</code>');

    // Bullet points
    escaped = escaped.replace(/^[•·]\s(.+)$/gm, '<li>$1</li>');
    escaped = escaped.replace(/(<li>.*<\/li>)/s, '<ul style="padding-left:18px;margin-top:6px;">$1</ul>');

    // Line breaks
    escaped = escaped.replace(/\n/g, '<br>');

    return escaped;
}

function showTyping() {
    const area = document.getElementById('messagesArea');
    const id = 'typing-' + Date.now();
    const msg = document.createElement('div');
    msg.className = 'message assistant-message';
    msg.id = id;

    const avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.textContent = '🤖';

    const bubble = document.createElement('div');
    bubble.className = 'message-bubble typing-indicator';
    bubble.innerHTML = '<div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div>';

    msg.appendChild(avatar);
    msg.appendChild(bubble);
    area.appendChild(msg);
    area.scrollTop = area.scrollHeight;
    return id;
}

function removeTyping(id) {
    const el = document.getElementById(id);
    if (el) el.remove();
}

function setLoading(state) {
    isLoading = state;
    const btn = document.getElementById('sendBtn');
    const input = document.getElementById('messageInput');
    btn.disabled = state;
    input.disabled = state;
}

function fillInput(el) {
    const input = document.getElementById('messageInput');
    // Extract text without the emoji prefix
    input.value = el.textContent.replace(/^[📚📋✅]\s/, '');
    input.focus();
}

// ============================================================
// TASK LIST
// ============================================================
async function loadTasks() {
    const btn = document.getElementById('refreshBtn');
    const svg = btn.querySelector('svg');
    if (svg) svg.classList.add('spinning');

    try {
        const url = `${API_BASE}/api/tasks?status=${currentFilter}`;
        const res = await fetch(url, { signal: AbortSignal.timeout(5000) });
        if (!res.ok) throw new Error('Failed to load tasks');
        const data = await res.json();
        renderTasks(data.tasks || []);
    } catch (err) {
        console.warn('Could not load tasks:', err.message);
    } finally {
        if (svg) svg.classList.remove('spinning');
    }
}

function renderTasks(tasks) {
    const list = document.getElementById('taskList');
    const empty = document.getElementById('emptyState');
    const count = document.getElementById('taskCount');

    count.textContent = tasks.length + (tasks.length === 1 ? ' task' : ' tasks');

    if (tasks.length === 0) {
        list.innerHTML = '';
        empty.style.display = 'flex';
        list.appendChild(empty);
        return;
    }

    // Sort: PENDING first, then COMPLETED
    tasks.sort((a, b) => {
        if (a.status === b.status) return 0;
        return a.status === 'PENDING' ? -1 : 1;
    });

    list.innerHTML = '';

    tasks.forEach(task => {
        const card = document.createElement('div');
        card.className = `task-card ${task.status === 'COMPLETED' ? 'completed' : ''}`;
        card.id = 'task-card-' + task.id;

        const icon = task.status === 'COMPLETED' ? '✅' : '⏳';
        const badgeClass = task.status === 'PENDING' ? 'pending' : 'completed';

        card.innerHTML = `
            <div class="task-header">
                <span class="task-status-icon">${icon}</span>
                <span class="task-title">${escapeHtml(task.title)}</span>
                <span class="task-badge ${badgeClass}">${task.status}</span>
            </div>
            <div class="task-footer-row">
                <span class="task-id" onclick="copyTaskId('${escapeHtml(task.id)}')" title="Click to copy ID">${escapeHtml(task.id)}</span>
            </div>
        `;

        list.appendChild(card);
    });
}

function filterTasks(status) {
    currentFilter = status;

    // Update button styles
    ['ALL', 'PENDING', 'COMPLETED'].forEach(s => {
        const btn = document.getElementById('filter-' + s);
        if (btn) btn.classList.toggle('active', s === status);
    });

    loadTasks();
}

// ============================================================
// MCP DEBUGGER
// ============================================================
let mcpPanelOpen = false;

function toggleMcpPanel() {
    mcpPanelOpen = !mcpPanelOpen;
    const body = document.getElementById('mcpPanelBody');
    const toggle = document.getElementById('mcpToggle');
    body.style.display = mcpPanelOpen ? 'block' : 'none';
    toggle.style.transform = mcpPanelOpen ? 'rotate(180deg)' : '';

    // Set default args when opening
    if (mcpPanelOpen) {
        updateMcpDefaultArgs();
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const sel = document.getElementById('mcpTool');
    if (sel) sel.addEventListener('change', updateMcpDefaultArgs);
});

function updateMcpDefaultArgs() {
    const tool = document.getElementById('mcpTool').value;
    const args = document.getElementById('mcpArgs');
    const defaults = {
        'add_task':     '{\n  "title": "Study System Design"\n}',
        'list_tasks':   '{\n  "status": "ALL"\n}',
        'complete_task':'{\n  "task_id": "task-xxxxxxxx"\n}',
        'tools/list':   '{}'
    };
    args.value = defaults[tool] || '{}';
}

async function callMcpDirect() {
    const toolSelect = document.getElementById('mcpTool').value;
    const argsText = document.getElementById('mcpArgs').value.trim();
    const output = document.getElementById('mcpOutput');
    const btn = document.getElementById('mcpCallBtn');

    let args = {};
    try {
        args = argsText ? JSON.parse(argsText) : {};
    } catch (e) {
        output.textContent = '// JSON parse error: ' + e.message;
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Calling...';
    output.textContent = '// Sending MCP request...';

    const isListTools = toolSelect === 'tools/list';
    const method = isListTools ? 'tools/list' : 'tools/call';

    const jsonRpcRequest = {
        jsonrpc: '2.0',
        id: Date.now(),
        method: method,
        params: isListTools ? {} : {
            name: toolSelect,
            arguments: args
        }
    };

    try {
        const res = await fetch(`${API_BASE}/mcp/message`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(jsonRpcRequest),
            signal: AbortSignal.timeout(15000)
        });

        const text = await res.text();

        // Format output
        output.textContent =
            `// REQUEST (POST /mcp/message)\n` +
            JSON.stringify(jsonRpcRequest, null, 2) +
            `\n\n// RESPONSE (HTTP ${res.status})\n` +
            formatMcpResponse(text);

        // Refresh tasks after a tool call
        if (!isListTools) await loadTasks();

    } catch (err) {
        output.textContent = '// ERROR: ' + err.message;
    } finally {
        btn.disabled = false;
        btn.textContent = 'Call MCP Tool';
    }
}

function formatMcpResponse(text) {
    if (!text) return '(empty)';

    // Handle SSE format
    if (text.includes('data:')) {
        const lines = text.split('\n').filter(l => l.trim());
        const jsonLines = lines.filter(l => l.startsWith('data:')).map(l => l.slice(5).trim());
        const combined = jsonLines.join('\n');
        try {
            return JSON.stringify(JSON.parse(combined), null, 2);
        } catch {
            return combined;
        }
    }

    try {
        return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
        return text;
    }
}

// ============================================================
// UTILITIES
// ============================================================
function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function copyTaskId(id) {
    copyToClipboard(id);
}

function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
        showToast('Copied: ' + text);
    }).catch(() => {
        // Fallback
        const el = document.createElement('textarea');
        el.value = text;
        document.body.appendChild(el);
        el.select();
        document.execCommand('copy');
        document.body.removeChild(el);
        showToast('Copied: ' + text);
    });
}

function showToast(msg) {
    let toast = document.querySelector('.copied-toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.className = 'copied-toast';
        document.body.appendChild(toast);
    }
    toast.textContent = msg;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 2000);
}

// ============================================================
// INIT
// ============================================================
document.addEventListener('DOMContentLoaded', () => {
    initParticles();
    checkHealth();
    loadTasks();

    // Poll health every 30s
    setInterval(checkHealth, 30000);

    // Auto-refresh tasks every 15s
    setInterval(loadTasks, 15000);

    // Focus input on load
    const input = document.getElementById('messageInput');
    if (input) input.focus();

    // Set default MCP args
    updateMcpDefaultArgs();
});
