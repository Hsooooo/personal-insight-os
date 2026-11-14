export function getSyncStatusLabel(status: string): string {
  switch (status) {
    case 'COMPLETED':
      return 'Completed';
    case 'RUNNING':
      return 'Running';
    case 'FAILED':
      return 'Failed';
    case 'PARTIAL':
      return 'Partial';
    default:
      return status;
  }
}
