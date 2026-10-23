import type {
  Task,
  TaskListResponse,
  CreateTaskRequest,
  RecoveryRequest,
  RecoveryProposal,
  ChatResponse,
} from './types';

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.PROD ? 'https://taskpilot-ai-study-recovery-agent.onrender.com' : 'http://localhost:8080');

class ApiClient {
  private async request<T>(endpoint: string, options?: RequestInit): Promise<T> {
    const url = `${API_BASE_URL}${endpoint}`;
    const response = await fetch(url, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
    }

    return response.json();
  }

  async getTasks(filter: string = 'ALL'): Promise<TaskListResponse> {
    return this.request<TaskListResponse>(`/api/tasks?status=${filter}`);
  }

  async createTask(task: CreateTaskRequest): Promise<Task> {
    return this.request<Task>('/api/tasks', {
      method: 'POST',
      body: JSON.stringify(task),
    });
  }

  async completeTask(taskId: string): Promise<Task> {
    return this.request<Task>(`/api/tasks/${taskId}/complete`, {
      method: 'PATCH',
    });
  }

  async generateRecovery(request: RecoveryRequest): Promise<RecoveryProposal> {
    return this.request<RecoveryProposal>('/api/recovery', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  async approveRecovery(taskIds: string[]): Promise<{ completed: string[] }> {
    return this.request<{ completed: string[] }>('/api/recovery/approve', {
      method: 'POST',
      body: JSON.stringify({ taskIds }),
    });
  }

  async chat(message: string): Promise<ChatResponse> {
    return this.request<ChatResponse>('/api/chat', {
      method: 'POST',
      body: JSON.stringify({ message }),
    });
  }
}

export const api = new ApiClient();
