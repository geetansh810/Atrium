import "./Avatar.css";

// Deterministic illustrated crew portrait. The same agent keeps the same
// palette, skin tone, and hair silhouette everywhere without storing an image.
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
      className={`crew-avatar crew-avatar-${idx}${square ? " square" : ""}`}
      style={{
        width: size,
        height: size,
      }}
      title={name}
      aria-hidden
    >
      <svg viewBox="0 0 64 64" focusable="false">
        <circle className="crew-avatar-backdrop" cx="32" cy="32" r="32" />
        <path className="crew-avatar-shirt" d="M9 64c1-15 9-22 23-22s22 7 23 22H9Z" />
        <path className="crew-avatar-neck" d="M26 37h12v12H26z" />
        <circle className="crew-avatar-ear" cx="20" cy="28" r="4" />
        <circle className="crew-avatar-ear" cx="44" cy="28" r="4" />
        <ellipse className="crew-avatar-face" cx="32" cy="27" rx="14" ry="17" />
        {idx % 3 === 0 ? (
          <path className="crew-avatar-hair" d="M18 28c-2-15 6-22 17-21 10 1 14 8 12 19-4-1-7-5-8-10-5 6-11 9-21 12Z" />
        ) : idx % 2 === 0 ? (
          <path className="crew-avatar-hair" d="M18 25C18 12 25 7 34 8c9 1 14 7 13 18-5-5-9-9-11-13-5 7-10 10-18 12Z" />
        ) : (
          <path className="crew-avatar-hair" d="M18 26C17 12 24 7 34 8c8 1 13 7 13 17-6-2-10-5-14-10-3 5-8 9-15 11Z" />
        )}
        <circle className="crew-avatar-eye" cx="27" cy="28" r="1.25" />
        <circle className="crew-avatar-eye" cx="37" cy="28" r="1.25" />
        <path className="crew-avatar-smile" d="M28 34c2.5 2 5.5 2 8 0" />
      </svg>
    </span>
  );
}
