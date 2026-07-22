// Design tokens — source of visual truth for the dashboard.
// Picked from atrium-docs/assets/reference-1-office-main-view.png per 01-product-spec.md §4.
// PRODUCT_NAME is the single rename point per 01 §6.
export const PRODUCT_NAME = "Atrium";
export const PRODUCT_TAGLINE = "Build. Automate. Scale.";

export const theme = {
  color: {
    bg: "#0B1120",
    panel: "#161E31",
    panelAlt: "#131A2C",
    sidebar: "#0E1526",
    border: "#283352",
    borderSubtle: "#1E2740",
    borderHi: "#35426B",

    text: "#EDF1F9",
    textMuted: "#939DB3",
    textFaint: "#5D6982",

    accent: "#17B3A3",
    accentHover: "#1ECBB9",
    invite: "#3D6FE0",
    inviteHover: "#4C7DEE",

    status: {
      online: "#3ECF8E",
      working: "#3ECF8E",
      in_meeting: "#F5A623",
      away: "#F5A623",
      in_focus: "#4FA8E0",
      flagged: "#E0574C",
      offline: "#5C687F",
      blocked: "#E0574C",
    },

    node: {
      pending: "#5C687F",
      running: "#4FA8E0",
      completed: "#3ECF8E",
      failed: "#E0574C",
    },

    surface1: "#10182A",
    surface2: "#161E31",
    surface3: "#1E2740",

    botBubble: "#161E31",

    // Reserved for live/primary signals only (brand mark, active-nav rail,
    // tab underline, default progress fill) — mirrors --grad-brand.
    gradBrand: "linear-gradient(120deg, #1ECBB9 0%, #4C7DEE 100%)",

    avatar: ["#3D6FE0", "#17B3A3", "#9B7CE0", "#E0574C", "#F5A623", "#4FA8E0"],
  },
  font: {
    display: "'Space Grotesk', 'IBM Plex Sans', sans-serif",
    body: "'IBM Plex Sans', system-ui, sans-serif",
    mono: "'IBM Plex Mono', monospace",
  },
  radius: {
    sm: "4px",
    md: "8px",
    lg: "12px",
  },
  // Motion vocabulary — mirrors --dur-*/--ease-* in index.css.
  motion: {
    dur1: "120ms",
    dur2: "220ms",
    dur3: "360ms",
    easeOut: "cubic-bezier(0.16, 1, 0.3, 1)",
    easeSpring: "cubic-bezier(0.34, 1.45, 0.64, 1)",
  },
  layout: {
    sidebarWidth: "240px",
    rightRailWidth: "280px",
    topBarHeight: "56px",
    botBarHeight: "76px",
  },
} as const;

export type StatusKey = keyof typeof theme.color.status;
