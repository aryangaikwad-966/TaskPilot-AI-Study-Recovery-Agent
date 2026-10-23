export interface Task {
  id: string;
  title: string;
  status: 'PENDING' | 'COMPLETED';
  createdAt: string;
}

export interface TaskListResponse {
  tasks: Task[];
  count: number;
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

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

export type TaskFilter = 'ALL' | 'PENDING' | 'COMPLETED';
