// Deterministic tint per skill, drawn from the avatar palette CSS vars.
const VAR_COUNT = 6;

export function skillColor(skill: string): string {
  let hash = 0;
  for (let i = 0; i < skill.length; i++) {
    hash = (hash * 31 + skill.charCodeAt(i)) | 0;
  }
  return `var(--avatar-${(Math.abs(hash) % VAR_COUNT) + 1})`;
}
