export interface Task {
  id: string;
  title: string;
  status: 'PENDING' | 'COMPLETED';
  createdAt: string;
}

export interface TaskListResponse {
  tasks: Task[];
  count: number;
  pendingCount: number;
  completedCount: number;
}

export interface CreateTaskRequest {
  title: string;
}

export interface RecoveryRequest {
  message: string;
}

export interface RecoveryProposal {
  explanation: string;
  recommendedTasks: string[];
  deferredTasks: string[];
  assumptions?: string[];
}

export interface ChatResponse {
  success: boolean;
  reply?: string;
  error?: string;
}

export type TaskFilter = 'ALL' | 'PENDING' | 'COMPLETED';
