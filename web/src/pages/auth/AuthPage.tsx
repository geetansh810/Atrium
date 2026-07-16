import { useState } from "react";
import { api, ApiError } from "../../shared/api";
import { setAuthSession } from "../../shared/auth";
import { markSignupNeedsOnboarding } from "../../shared/onboarding";
import { PRODUCT_NAME } from "../../shared/theme";
import "./AuthPage.css";

// M3.1: the fresh-browser entry point — signup creates the company + admin
// user atomically, login re-authenticates an existing one. No dashboard
// chrome renders until a session exists (see AuthGate in App.tsx).
export function AuthPage() {
  const [mode, setMode] = useState<"signup" | "login">("signup");
  const [companyName, setCompanyName] = useState("");
  const [companySlug, setCompanySlug] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const slugify = (name: string) =>
    name.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setBusy(true);
    try {
      const auth =
        mode === "signup"
          ? await api.signup({ companyName, companySlug, displayName, email, password })
          : await api.login({ email, password });
      // M3.4: only a fresh signup owes the onboarding wizard — logging back
      // into an existing (possibly still-empty) company must not re-trigger it.
      if (mode === "signup") markSignupNeedsOnboarding(auth.companyId);
      setAuthSession(auth);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
        if (err.fieldErrors) setFieldErrors(err.fieldErrors);
      } else {
        setError("Something went wrong — please try again.");
      }
    } finally {
      setBusy(false);
    }
  };

  const canSubmit =
    mode === "signup"
      ? companyName.trim() && companySlug.trim() && displayName.trim() && email.trim() && password.length >= 8
      : email.trim() && password.length > 0;

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-title">{PRODUCT_NAME}</h1>
        <p className="auth-subtitle">
          {mode === "signup" ? "Create your company" : "Welcome back"}
        </p>

        <div className="auth-mode-toggle">
          <button
            type="button"
            className={mode === "signup" ? "auth-mode-btn active" : "auth-mode-btn"}
            onClick={() => setMode("signup")}
          >
            Sign up
          </button>
          <button
            type="button"
            className={mode === "login" ? "auth-mode-btn active" : "auth-mode-btn"}
            onClick={() => setMode("login")}
          >
            Log in
          </button>
        </div>

        <form onSubmit={submit}>
          {mode === "signup" && (
            <>
              <div className="field">
                <label>Company name</label>
                <input
                  value={companyName}
                  onChange={(e) => {
                    setCompanyName(e.target.value);
                    if (!companySlug || companySlug === slugify(companyName)) {
                      setCompanySlug(slugify(e.target.value));
                    }
                  }}
                  placeholder="Agrawal Namkeen"
                  autoFocus
                />
                {fieldErrors.companyName && <span className="auth-field-error">{fieldErrors.companyName}</span>}
              </div>
              <div className="field">
                <label>Company URL slug</label>
                <input
                  value={companySlug}
                  onChange={(e) => setCompanySlug(e.target.value)}
                  placeholder="agrawal-namkeen"
                />
                {fieldErrors.companySlug && <span className="auth-field-error">{fieldErrors.companySlug}</span>}
              </div>
              <div className="field">
                <label>Your name</label>
                <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="Geetu" />
                {fieldErrors.displayName && <span className="auth-field-error">{fieldErrors.displayName}</span>}
              </div>
            </>
          )}
          <div className="field">
            <label>Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@company.com"
              autoFocus={mode === "login"}
            />
            {fieldErrors.email && <span className="auth-field-error">{fieldErrors.email}</span>}
          </div>
          <div className="field">
            <label>Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={mode === "signup" ? "At least 8 characters" : "••••••••"}
            />
            {fieldErrors.password && <span className="auth-field-error">{fieldErrors.password}</span>}
          </div>

          {error && <div className="auth-error">{error}</div>}

          <button type="submit" className="btn primary auth-submit" disabled={!canSubmit || busy}>
            {busy ? "Please wait…" : mode === "signup" ? "Create company" : "Log in"}
          </button>
        </form>
      </div>
    </div>
  );
}
