import { HttpErrorResponse } from '@angular/common/http';

/**
 * Maps an HttpErrorResponse to a user-facing message.
 * Centralized so every service hits the same wording and we never leak
 * stack traces or internal hostnames into the UI.
 */
export function mapHttpError(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Cannot reach the server — is the backend running?';
  }
  if (error.status === 404) {
    return 'No data found for the requested resource.';
  }
  if (error.status === 400) {
    return 'Invalid request — please check your filters and try again.';
  }
  if (error.status >= 500) {
    return `Server error (${error.status}) — please try again later.`;
  }
  return `Request failed (${error.status}).`;
}
