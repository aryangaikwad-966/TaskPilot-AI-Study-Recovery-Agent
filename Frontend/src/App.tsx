import { useState, useEffect, useCallback } from 'react';
import { api } from './api';
import type { Task, TaskFilter, RecoveryProposal } from './types';
import { CheckSquare, Plus, BookOpen, AlertCircle, Loader2, X } from 'lucide-react';
import Avatar from './components/Avatar';
import './App.css';

function getGreeting() {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

function App() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [filter, setFilter] = useState<TaskFilter>('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [showRecovery, setShowRecovery] = useState(false);
  const [recoveryMessage, setRecoveryMessage] = useState('');
  const [recoveryProposal, setRecoveryProposal] = useState<RecoveryProposal | null>(null);
  const [recoveryLoading, setRecoveryLoading] = useState(false);
  const greeting = getGreeting();
  const [avatarExpression, setAvatarExpression] = useState<'neutral' | 'hyper-focus' | 'cosmic-wonder' | 'glitch-alert' | 'zen-mode'>('neutral');

  const loadTasks = useCallback(async () => {
    try {
      const response = await api.getTasks(filter);
      setTasks(response.tasks);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load tasks');
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    let active = true;
    api.getTasks(filter)
      .then((response) => {
        if (active) setTasks(response.tasks);
      })
      .catch((err: unknown) => {
        if (active) setError(err instanceof Error ? err.message : 'Failed to load tasks');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [filter]);

  const handleAddTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTaskTitle.trim()) return;

    try {
      await api.createTask({ title: newTaskTitle.trim() });
      setNewTaskTitle('');
      await loadTasks();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add task');
    }
  };

  const handleCompleteTask = async (taskId: string) => {
    try {
      await api.completeTask(taskId);
      await loadTasks();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to complete task');
    }
  };

  const handleGenerateRecovery = async () => {
    if (!recoveryMessage.trim()) return;

    try {
      setRecoveryLoading(true);
      setError(null);
      setAvatarExpression('hyper-focus');
      const proposal = await api.generateRecovery({ message: recoveryMessage });
      setRecoveryProposal(proposal);
      setAvatarExpression('cosmic-wonder');
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to generate recovery plan';
      // Provide a more helpful message for API key issues
      if (errorMessage.includes('API key') || errorMessage.includes('503')) {
        setError('Gemini API key not configured. Please set GEMINI_API_KEY environment variable on the backend.');
      } else {
        setError(errorMessage);
      }
      setAvatarExpression('glitch-alert');
    } finally {
      setRecoveryLoading(false);
    }
  };

  const handleApproveRecovery = async () => {
    if (!recoveryProposal) return;

    try {
      const taskIds = tasks
        .filter(t => recoveryProposal.recommendedTasks.includes(t.title))
        .map(t => t.id);

      await api.approveRecovery(taskIds);
      setRecoveryProposal(null);
      setRecoveryMessage('');
      setShowRecovery(false);
      await loadTasks();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to approve recovery plan');
    }
  };

  const handleCancelRecovery = () => {
    setRecoveryProposal(null);
    setRecoveryMessage('');
  };

  const pendingCount = tasks.filter(t => t.status === 'PENDING').length;
  const completedCount = tasks.filter(t => t.status === 'COMPLETED').length;

  return (
    <div className="app">
      <header className="header">
        <div className="header-content">
          <div className="logo">
            <Avatar expression={avatarExpression} size={60} />
            <div className="logo-text">
              <h1>TaskPilot</h1>
              <p className="tagline">Recover your study momentum</p>
            </div>
          </div>
        </div>
      </header>

      <main className="main">
        <div className="greeting-section">
          <h2>{greeting}! Ready to study?</h2>
          <p className="today-focus">Today's focus: {pendingCount > 0 ? `${pendingCount} pending task${pendingCount !== 1 ? 's' : ''}` : 'No pending tasks'}</p>
        </div>

        <div className="stats">
          <div className="stat-card">
            <div className="stat-value">{pendingCount}</div>
            <div className="stat-label">Pending</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{completedCount}</div>
            <div className="stat-label">Completed</div>
          </div>
        </div>

        {error && (
          <div className="error-banner">
            <AlertCircle className="error-icon" />
            <span>{error}</span>
            <button onClick={() => setError(null)} className="error-close">
              <X size={16} />
            </button>
          </div>
        )}

        <div className="actions">
          <button
            className="recovery-button"
            onClick={() => setShowRecovery(!showRecovery)}
          >
            <BookOpen size={20} />
            {showRecovery ? 'Hide Recovery' : 'Recover my study plan'}
          </button>
        </div>

        {showRecovery && (
          <div className="recovery-panel">
            {!recoveryProposal ? (
              <>
                <textarea
                  className="recovery-input"
                  placeholder="Tell TaskPilot what went wrong with your study schedule..."
                  value={recoveryMessage}
                  onChange={(e) => setRecoveryMessage(e.target.value)}
                  rows={4}
                />
                <div className="example-prompts">
                  <p className="examples-label">Example prompts:</p>
                  <button
                    className="example-prompt"
                    onClick={() => setRecoveryMessage('I missed two days of DSA practice.')}
                  >
                    "I missed two days of DSA practice."
                  </button>
                  <button
                    className="example-prompt"
                    onClick={() => setRecoveryMessage('I have too many pending tasks for tomorrow.')}
                  >
                    "I have too many pending tasks for tomorrow."
                  </button>
                  <button
                    className="example-prompt"
                    onClick={() => setRecoveryMessage('Help me prioritize Java revision and binary search.')}
                  >
                    "Help me prioritize Java revision and binary search."
                  </button>
                </div>
                <button
                  className="generate-button"
                  onClick={handleGenerateRecovery}
                  disabled={!recoveryMessage.trim() || recoveryLoading}
                >
                  {recoveryLoading ? (
                    <>
                      <Loader2 className="spinner" size={16} />
                      Generating plan...
                    </>
                  ) : (
                    'Generate recovery plan'
                  )}
                </button>
              </>
            ) : (
              <div className="recovery-proposal">
                <h3>Recovery Proposal</h3>
                <p className="proposal-explanation">{recoveryProposal.explanation}</p>

                {recoveryProposal.recommendedTasks.length > 0 && (
                  <div className="proposal-section">
                    <h4>Recommended for today</h4>
                    <ul className="proposal-list">
                      {recoveryProposal.recommendedTasks.map((task, i) => (
                        <li key={i}>{task}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {recoveryProposal.deferredTasks.length > 0 && (
                  <div className="proposal-section">
                    <h4>Can wait</h4>
                    <ul className="proposal-list">
                      {recoveryProposal.deferredTasks.map((task, i) => (
                        <li key={i}>{task}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {recoveryProposal.assumptions && recoveryProposal.assumptions.length > 0 && (
                  <div className="proposal-section">
                    <h4>Assumptions</h4>
                    <ul className="proposal-list">
                      {recoveryProposal.assumptions.map((assumption, i) => (
                        <li key={i}>{assumption}</li>
                      ))}
                    </ul>
                  </div>
                )}

                <div className="proposal-actions">
                  <button className="approve-button" onClick={handleApproveRecovery} disabled={recoveryProposal.recommendedTasks.length === 0}>
                    Approve plan
                  </button>
                  <button className="revise-button" onClick={handleCancelRecovery}>
                    Revise request
                  </button>
                  <button className="cancel-button" onClick={handleCancelRecovery}>
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        <div className="task-section">
          <div className="task-header">
            <h3>Tasks</h3>
            <div className="task-filters">
              {(['ALL', 'PENDING', 'COMPLETED'] as TaskFilter[]).map((f) => (
                <button
                  key={f}
                  className={`filter-button ${filter === f ? 'active' : ''}`}
                  onClick={() => setFilter(f)}
                >
                  {f}
                </button>
              ))}
            </div>
          </div>

          <form className="add-task-form" onSubmit={handleAddTask}>
            <input
              type="text"
              className="task-input"
              placeholder="Add a new task..."
              value={newTaskTitle}
              onChange={(e) => setNewTaskTitle(e.target.value)}
            />
            <button type="submit" className="add-button" disabled={!newTaskTitle.trim()}>
              <Plus size={18} />
            </button>
          </form>

          {loading ? (
            <div className="loading-state">
              <Loader2 className="spinner" size={24} />
              <p>Loading tasks...</p>
            </div>
          ) : tasks.length === 0 ? (
            <div className="empty-state">
              <CheckSquare size={48} className="empty-icon" />
              <p>No tasks yet. Add your first task above!</p>
            </div>
          ) : (
            <ul className="task-list">
              {tasks.map((task) => (
                <li key={task.id} className={`task-item ${task.status.toLowerCase()}`}>
                  <div className="task-content">
                    <span className="task-title">{task.title}</span>
                    <span className={`task-status ${task.status.toLowerCase()}`}>
                      {task.status}
                    </span>
                  </div>
                  {task.status === 'PENDING' && (
                    <button
                      className="complete-button"
                      onClick={() => handleCompleteTask(task.id)}
                      aria-label="Complete task"
                    >
                      <CheckSquare size={18} />
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </main>
    </div>
  );
}

export default App;
