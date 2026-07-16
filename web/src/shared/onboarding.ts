// M3.4: tracks whether a company still owes its post-signup onboarding wizard.
// AuthPage marks this true right after a successful *signup* (never login) —
// so an existing company logging back in with an empty roster (M3.1's own
// Done-when) never gets re-routed into the wizard. localStorage (not the
// AuthSession itself) so a mid-wizard hard refresh resumes it, and closing
// the tab without finishing doesn't silently lose the "still owed" state.
const PREFIX = "atrium:onboardingPending:";

export function markSignupNeedsOnboarding(companyId: string) {
  localStorage.setItem(PREFIX + companyId, "1");
}

export function needsOnboarding(companyId: string): boolean {
  return localStorage.getItem(PREFIX + companyId) === "1";
}

export function completeOnboarding(companyId: string) {
  localStorage.removeItem(PREFIX + companyId);
}
