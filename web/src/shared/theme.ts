// Design tokens — source of visual truth for the dashboard.
// Picked from atrium-docs/assets/reference-1-office-main-view.png per 01-product-spec.md §4.
// PRODUCT_NAME is the single rename point per 01 §6.
export const PRODUCT_NAME = "Atrium";
export const PRODUCT_TAGLINE = "Build. Automate. Scale.";

export const theme = {
  color: {
    bg: "#0E1525",
    panel: "#1A2233",
    panelAlt: "#171F30",
    sidebar: "#131A2B",
    border: "#26314A",
    borderSubtle: "#212A40",

    text: "#EAEEF6",
    textMuted: "#8D97AC",
    textFaint: "#5C687F",

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

    surface1: "#131A2B",
    surface2: "#1A2233",
    surface3: "#212A40",

    botBubble: "#1A2233",

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
  layout: {
    sidebarWidth: "240px",
    rightRailWidth: "280px",
    topBarHeight: "56px",
    botBarHeight: "76px",
  },
} as const;

export type StatusKey = keyof typeof theme.color.status;
