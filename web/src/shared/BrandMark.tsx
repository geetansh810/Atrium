import { PRODUCT_NAME } from "./theme";
import "./BrandMark.css";

export function BrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <span className={`brand-lockup${compact ? " compact" : ""}`}>
      <img className="brand-horizontal" src="/basecrew-horizontal.jpg" alt={PRODUCT_NAME} />
    </span>
  );
}
