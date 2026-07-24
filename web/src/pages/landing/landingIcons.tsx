// Thin-line icons for the feature grid — sized 1em, stroke inherits currentColor
// so the CSS controls the accent. Kept local to the marketing surface so the
// dashboard's own icon set (shared/icons.tsx) stays untouched.
import type { SVGProps } from "react";

const base: SVGProps<SVGSVGElement> = {
  width: 24,
  height: 24,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.6,
  strokeLinecap: "round",
  strokeLinejoin: "round",
};

export function IconOrg(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <rect x="9" y="3" width="6" height="4.5" rx="1" />
      <rect x="3" y="15.5" width="6" height="4.5" rx="1" />
      <rect x="15" y="15.5" width="6" height="4.5" rx="1" />
      <path d="M12 7.5v4M6 15.5v-2a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v2" />
    </svg>
  );
}

export function IconRoute(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <circle cx="6" cy="6" r="2.2" />
      <circle cx="18" cy="18" r="2.2" />
      <path d="M8.2 6h6.3a3.5 3.5 0 0 1 0 7H9.5a3.5 3.5 0 0 0 0 5h6.3" />
    </svg>
  );
}

export function IconBudget(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="6" width="18" height="12" rx="2" />
      <path d="M3 10h18" />
      <circle cx="16.5" cy="14" r="1.3" />
    </svg>
  );
}

export function IconApproval(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3l7 3v5c0 4.2-2.8 7.5-7 9-4.2-1.5-7-4.8-7-9V6z" />
      <path d="M9 11.8l2 2 4-4.2" />
    </svg>
  );
}

export function IconKnowledge(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M12 6.5A4.2 4.2 0 0 0 4.8 9c-.5 1.7.3 3.2 1.2 4.2-.4 1.4.3 3 1.9 3.6a3.4 3.4 0 0 0 4.1 1.2" />
      <path d="M12 6.5A4.2 4.2 0 0 1 19.2 9c.5 1.7-.3 3.2-1.2 4.2.4 1.4-.3 3-1.9 3.6a3.4 3.4 0 0 1-4.1 1.2" />
      <path d="M12 6.5V19" />
    </svg>
  );
}

export function IconAudit(props: SVGProps<SVGSVGElement>) {
  return (
    <svg {...base} {...props}>
      <path d="M6 3h9l3 3v15H6z" />
      <path d="M9 9h6M9 12.5h6M9 16h4" />
    </svg>
  );
}
