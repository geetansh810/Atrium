// Design tokens — dark-theme mirror of index.css (the CSS file is the source of truth
// and also carries the light theme). BaseCrew "crew pennant" direction.
// PRODUCT_NAME is the single rename point per 01 §6.
export const PRODUCT_NAME = "BaseCrew";
export const PRODUCT_TAGLINE = "Hire the crew. Review the work.";

export const theme = {
  color: {
    bg: "#070707",
    panel: "#111111",
    panelAlt: "#171717",
    sidebar: "#050505",
    border: "#34312B",
    borderSubtle: "#24221E",
    borderHi: "#5B5549",

    text: "#F7F3E8",
    textMuted: "#B9B3A7",
    textFaint: "#847D70",

    accent: "#FFCC00",
    accentHover: "#FFE066",
    invite: "#FFCC00",
    inviteHover: "#FFE066",

    status: {
      online: "#53D887",
      working: "#FFCC00",
      in_meeting: "#D8B84A",
      away: "#918B7F",
      in_focus: "#5CA9FF",
      flagged: "#FF6557",
      offline: "#6D6A64",
      blocked: "#FF6557",
    },

    node: {
      pending: "#6D6A64",
      running: "#FFCC00",
      completed: "#53D887",
      failed: "#FF6557",
    },

    surface1: "#0C0C0C",
    surface2: "#111111",
    surface3: "#1D1B17",

    botBubble: "#111111",

    // Reserved for live/primary signals only (brand mark, active-nav rail,
    // tab underline, default progress fill) — mirrors --grad-brand.
    gradBrand: "linear-gradient(120deg, #FFE066 0%, #FFCC00 100%)",

    avatar: ["#FFCC00", "#53D887", "#5CA9FF", "#B093E8", "#FF6557", "#D0AF46"],
    avatarSkin: ["#F1C7A5", "#C98F65", "#8A5A3B"],
    avatarFeature: "#251A18",
  },
  font: {
    display: "'Space Grotesk', 'Barlow Semi Condensed', system-ui, sans-serif",
    body: "'Barlow Semi Condensed', 'IBM Plex Sans', system-ui, sans-serif",
    mono: "'JetBrains Mono', 'IBM Plex Mono', monospace",
  },
  radius: {
    sm: "2px",
    md: "4px",
    lg: "6px",
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
