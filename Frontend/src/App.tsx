import { useState, useEffect, useCallback } from 'react';
import { api } from './api';
import type { Task, TaskFilter, RecoveryProposal } from './types';
import { AlertCircle, Check, Loader2, Plus, X, LayoutDashboard, ListTodo, Sparkles, Terminal } from 'lucide-react';
import './App.css';

type View = 'overview' | 'tasks' | 'recovery' | 'inspector';

function App() {
  const [view, setView] = useState<View>('overview');
  const [tasks, setTasks] = useState<Task[]>([]);
  const [pendingCount, setPendingCount] = useState(0);
  const [completedCount, setCompletedCount] = useState(0);
  const [filter, setFilter] = useState<TaskFilter>('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [recoveryMessage, setRecoveryMessage] = useState('');
  const [recoveryProposal, setRecoveryProposal] = useState<RecoveryProposal | null>(null);
  const [recoveryLoading, setRecoveryLoading] = useState(false);
  const [approving, setApproving] = useState(false);
  const [chatMessage, setChatMessage] = useState('');
  const [chatReply, setChatReply] = useState<string | null>(null);
  const [chatLoading, setChatLoading] = useState(false);

  const applyList = (response: { tasks: Task[]; pendingCount?: number; completedCount?: number }) => {
    setTasks(response.tasks);
    setPendingCount(response.pendingCount ?? response.tasks.filter((t) => t.status === 'PENDING').length);
    setCompletedCount(response.completedCount ?? response.tasks.filter((t) => t.status === 'COMPLETED').length);
  };

  const loadTasks = useCallback(async () => {
    const response = await api.getTasks(filter);
    applyList(response);
  }, [filter]);

  useEffect(() => {
    let active = true;
    api.getTasks(filter)
      .then((response) => { if (active) applyList(response); })
      .catch((err: unknown) => { if (active) setError(err instanceof Error ? err.message : 'Failed to load tasks'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filter]);

  const handleAddTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTaskTitle.trim()) return;
    try {
      setError(null);
      await api.createTask({ title: newTaskTitle.trim() });
      setNewTaskTitle('');
      await loadTasks();
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to add task'); }
  };

  const handleCompleteTask = async (taskId: string) => {
    try {
      setError(null);
      await api.completeTask(taskId);
      await loadTasks();
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to complete task'); }
  };

  const handleGenerateRecovery = async () => {
    if (!recoveryMessage.trim()) return;
    try {
      setRecoveryLoading(true);
      setError(null);
      setNotice(null);
      const proposal = await api.generateRecovery({ message: recoveryMessage });
      setRecoveryProposal(proposal);
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to generate recovery plan'); }
    finally { setRecoveryLoading(false); }
  };

  const handleApproveRecovery = async () => {
    if (!recoveryProposal) return;
    try {
      setApproving(true);
      setError(null);
      const allTasks = await api.getTasks('ALL');
      const recommended = new Set(recoveryProposal.recommendedTasks);
      const taskIds = allTasks.tasks
        .filter((task) => task.status === 'PENDING' && recommended.has(task.title))
        .map((task) => task.id);
      if (taskIds.length === 0) { setError('No matching pending tasks remain for this proposal.'); return; }
      const result = await api.approveRecovery(taskIds);
      setRecoveryProposal(null);
      setRecoveryMessage('');
      setNotice(`Completed ${result.completed.length} recommended task${result.completed.length === 1 ? '' : 's'}.`);
      await loadTasks();
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed to approve recovery plan'); }
    finally { setApproving(false); }
  };

  const handleCancelRecovery = () => { setRecoveryProposal(null); setRecoveryMessage(''); };
  const handleReviseRecovery = () => { setRecoveryProposal(null); };

  const handleChat = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatMessage.trim()) return;
    try {
      setChatLoading(true);
      setError(null);
      const response = await api.chat(chatMessage.trim());
      if (!response.success) { setError(response.error || 'Chat request failed'); setChatReply(null); return; }
      setChatReply(response.reply ?? '');
      setChatMessage('');
      await loadTasks();
    } catch (err) { setError(err instanceof Error ? err.message : 'Chat request failed'); }
    finally { setChatLoading(false); }
  };

  const nav = [
    { id: 'overview' as View, label: 'Overview', icon: LayoutDashboard },
    { id: 'tasks' as View, label: 'Tasks', icon: ListTodo },
    { id: 'recovery' as View, label: 'AI Recovery', icon: Sparkles },
    { id: 'inspector' as View, label: 'MCP Inspector', icon: Terminal },
  ];

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-mark" />
          <div><h1>TaskPilot</h1><p>AI Study Recovery</p></div>
        </div>
        <nav className="sidebar-nav" aria-label="Primary">
          {nav.map((n) => {
            const Icon = n.icon;
            return (
              <button key={n.id} type="button" className={`nav-item ${view === n.id ? 'active' : ''}`} onClick={() => setView(n.id)}>
                <Icon size={16} /><span>{n.label}</span>
              </button>
            );
          })}
        </nav>
        <div className="sidebar-footer">
          <span className="status-dot" /> In-memory workspace
        </div>
      </aside>

      <div className="main-area">
        <header className="topbar">
          <h2>{nav.find((n) => n.id === view)?.label}</h2>
          <p className="topbar-meta">REST and MCP share the same task store.</p>
        </header>

        <main className="content">
          {error && (
            <div className="banner error" role="alert">
              <AlertCircle size={16} /><span>{error}</span>
              <button type="button" onClick={() => setError(null)} aria-label="Dismiss error"><X size={14} /></button>
            </div>
          )}
          {notice && (
            <div className="banner success" role="status">
              <Check size={16} /><span>{notice}</span>
              <button type="button" onClick={() => setNotice(null)} aria-label="Dismiss notice"><X size={14} /></button>
            </div>
          )}

          {view === 'overview' && (
            <section className="stats" aria-label="Task counts">
              <article className="stat-card"><p className="stat-label">Pending</p><p className="stat-value">{pendingCount}</p></article>
              <article className="stat-card"><p className="stat-label">Completed</p><p className="stat-value">{completedCount}</p></article>
              <article className="stat-card"><p className="stat-label">Total</p><p className="stat-value">{pendingCount + completedCount}</p></article>
            </section>
          )}

          {(view === 'overview' || view === 'tasks') && (
            <section className="panel">
              <div className="panel-header">
                <h2>Task workspace</h2>
                <div className="task-filters" role="tablist" aria-label="Task filters">
                  {(['ALL', 'PENDING', 'COMPLETED'] as TaskFilter[]).map((item) => (
                    <button key={item} type="button" className={`filter-button ${filter === item ? 'active' : ''}`}
                      onClick={() => { setLoading(true); setFilter(item); }}>
                      {item}
                    </button>
                  ))}
                </div>
              </div>
              <form className="add-task-form" onSubmit={handleAddTask}>
                <input type="text" className="task-input" placeholder="Add a task" value={newTaskTitle}
                  onChange={(e) => setNewTaskTitle(e.target.value)} maxLength={300} aria-label="New task title" />
                <button type="submit" className="primary-button" disabled={!newTaskTitle.trim()}><Plus size={16} /> Add</button>
              </form>
              {loading ? (
                <div className="state"><Loader2 className="spinner" size={18} /> Loading tasks</div>
              ) : tasks.length === 0 ? (
                <div className="state">No tasks in this view. Add a task to populate the recovery workflow.</div>
              ) : (
                <ul className="task-list">
                  {tasks.map((task) => (
                    <li key={task.id} className="task-item">
                      <div className="task-content">
                        <span className="task-title">{task.title}</span>
                        <span className={`task-status ${task.status.toLowerCase()}`}>{task.status}</span>
                      </div>
                      {task.status === 'PENDING' && (
                        <button type="button" className="ghost-button" onClick={() => handleCompleteTask(task.id)}>Complete</button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}

          {(view === 'overview' || view === 'recovery') && (
            <section className="panel">
              <div className="panel-header"><h2>AI recovery</h2></div>
              <p className="panel-copy">Generates a proposal from current pending tasks. Approval completes only the recommended pending tasks. Cancel leaves the store unchanged.</p>
              {!recoveryProposal ? (
                <>
                  <textarea className="recovery-input" placeholder="Describe the missed work. Example: I missed two days of DSA practice."
                    value={recoveryMessage} onChange={(e) => setRecoveryMessage(e.target.value)} rows={4} aria-label="Recovery request" />
                  <button type="button" className="primary-button" onClick={handleGenerateRecovery}
                    disabled={!recoveryMessage.trim() || recoveryLoading}>
                    {recoveryLoading ? (<><Loader2 className="spinner" size={16} /> Generating</>) : 'Generate proposal'}
                  </button>
                </>
              ) : (
                <div className="recovery-proposal">
                  <p className="proposal-explanation">{recoveryProposal.explanation}</p>
                  {recoveryProposal.assumptions && recoveryProposal.assumptions.length > 0 && (
                    <div className="proposal-section">
                      <h3>Assumptions</h3>
                      <ul>{recoveryProposal.assumptions.map((a) => <li key={a}>{a}</li>)}</ul>
                    </div>
                  )}
                  {recoveryProposal.recommendedTasks.length > 0 && (
                    <div className="proposal-section">
                      <h3>Prioritize</h3>
                      <ul>{recoveryProposal.recommendedTasks.map((t) => <li key={t}>{t}</li>)}</ul>
                    </div>
                  )}
                  {recoveryProposal.deferredTasks.length > 0 && (
                    <div className="proposal-section">
                      <h3>Defer</h3>
                      <ul>{recoveryProposal.deferredTasks.map((t) => <li key={t}>{t}</li>)}</ul>
                    </div>
                  )}
                  <div className="proposal-actions">
                    <button type="button" className="primary-button" onClick={handleApproveRecovery}
                      disabled={recoveryProposal.recommendedTasks.length === 0 || approving}>
                      {approving ? 'Approving' : 'Approve'}
                    </button>
                    <button type="button" className="ghost-button" onClick={handleReviseRecovery}>Revise</button>
                    <button type="button" className="ghost-button" onClick={handleCancelRecovery}>Cancel</button>
                  </div>
                </div>
              )}
            </section>
          )}

          {(view === 'overview' || view === 'inspector') && (
            <section className="panel">
              <div className="panel-header"><h2>Task inspector</h2></div>
              <p className="panel-copy">Sends the prompt to Gemini. The assistant may call <code>list_tasks</code> over MCP. It cannot add or complete tasks from this panel.</p>
              <form className="add-task-form" onSubmit={handleChat}>
                <input type="text" className="task-input" placeholder="Ask for a read-only task summary" value={chatMessage}
                  onChange={(e) => setChatMessage(e.target.value)} aria-label="Inspector prompt" />
                <button type="submit" className="primary-button" disabled={!chatMessage.trim() || chatLoading}>
                  {chatLoading ? <Loader2 className="spinner" size={16} /> : 'Ask'}
                </button>
              </form>
              {chatReply && <pre className="chat-reply">{chatReply}</pre>}
            </section>
          )}
        </main>
      </div>
    </div>
  );
}

export default App;
