// Design tokens — dark-theme mirror of index.css (the CSS file is the source of truth
// and also carries the light theme). UX1 "Control room" direction, atrium-docs/07 Phase 5.
// PRODUCT_NAME is the single rename point per 01 §6.
export const PRODUCT_NAME = "Atrium";
export const PRODUCT_TAGLINE = "Build. Automate. Scale.";

export const theme = {
  color: {
    bg: "#0F1115",
    panel: "#15181D",
    panelAlt: "#12151A",
    sidebar: "#0B0D10",
    border: "#272B32",
    borderSubtle: "#1D2127",
    borderHi: "#353B45",

    text: "#E7E9EC",
    textMuted: "#8B929D",
    textFaint: "#5F6672",

    accent: "#E8A63A",
    accentHover: "#F2B855",
    invite: "#E8A63A",
    inviteHover: "#F2B855",

    status: {
      online: "#6FCF97",
      working: "#E8A63A",
      in_meeting: "#D9C07A",
      away: "#8F8A78",
      in_focus: "#5AA9E6",
      flagged: "#F0695C",
      offline: "#5F6672",
      blocked: "#F0695C",
    },

    node: {
      pending: "#5F6672",
      running: "#E8A63A",
      completed: "#6FCF97",
      failed: "#F0695C",
    },

    surface1: "#12151A",
    surface2: "#15181D",
    surface3: "#1D2127",

    botBubble: "#15181D",

    // Reserved for live/primary signals only (brand mark, active-nav rail,
    // tab underline, default progress fill) — mirrors --grad-brand.
    gradBrand: "linear-gradient(120deg, #F2B855 0%, #E8A63A 100%)",

    avatar: ["#E8A63A", "#E8A63A", "#9B7CE0", "#E0574C", "#F5A623", "#4FA8E0"],
  },
  font: {
    display: "'Barlow Semi Condensed', 'IBM Plex Sans', system-ui, sans-serif",
    body: "'Barlow Semi Condensed', 'IBM Plex Sans', system-ui, sans-serif",
    mono: "'JetBrains Mono', 'IBM Plex Mono', monospace",
  },
  radius: {
    sm: "3px",
    md: "5px",
    lg: "8px",
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
