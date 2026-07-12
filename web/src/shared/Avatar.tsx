// Initials avatar tinted per-agent (deterministic) until pixel sprites arrive
// with the SkyOffice fork. Palette lives in index.css as --avatar-N vars.
const AVATAR_VAR_COUNT = 6;

function hashString(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = (hash * 31 + value.charCodeAt(i)) | 0;
  }
  return Math.abs(hash);
}

interface AvatarProps {
  name: string;
  seed?: string;
  size?: number;
  square?: boolean;
}

export function Avatar({ name, seed, size = 28, square = false }: AvatarProps) {
  const idx = (hashString(seed ?? name) % AVATAR_VAR_COUNT) + 1;
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: square ? "8px" : "50%",
        background: `var(--avatar-${idx})`,
        color: "var(--text)",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        fontWeight: 600,
        fontSize: Math.max(10, Math.round(size * 0.42)),
        flexShrink: 0,
        userSelect: "none",
      }}
      aria-hidden
    >
      {name.slice(0, 1).toUpperCase()}
    </span>
  );
}
