export interface ApprovalRequest {
  id: string;
  origin: string;
  method: string;
  params: unknown[];
  createdAt: number;
}

export const APPROVAL_PREFIX = "poctraApproval:";

export function approvalKey(id: string): string {
  return `${APPROVAL_PREFIX}${id}`;
}
