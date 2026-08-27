export interface TicketDTO {
  id: number
  displayCode: string
  number: number
  priorityType: 'NORMAL' | 'PRIORITY'
  status: 'WAITING' | 'CALLED' | 'IN_SERVICE' | 'FINISHED' | 'ABSENT' | 'CANCELLED'
  queueId: number
  queueName: string
  counterName: string | null
  attendantName: string | null
  createdAt: string
  calledAt: string | null
  serviceStartedAt: string | null
  finishedAt: string | null
}

export interface QueueDTO {
  id: number
  name: string
  prefix: string
  active: boolean
  createdAt: string
}

export interface CounterDTO {
  id: number
  name: string
  active: boolean
  currentAttendantName: string | null
}

export interface UserDTO {
  id: number
  name: string
  email: string
  role: string
  active: boolean
  createdAt: string
}

export const STATUS_LABEL: Record<string, string> = {
  WAITING: 'Aguardando',
  CALLED: 'Chamada',
  IN_SERVICE: 'Em atendimento',
  FINISHED: 'Concluída',
  ABSENT: 'Ausente',
  CANCELLED: 'Cancelada',
}
