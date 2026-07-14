// Mock vs API split follows shared/store.tsx's convention: pick the whole
// component once at module load via USE_MOCKS, rather than branching hook
// calls inside a single component body (which would violate rules-of-hooks
// the moment either branch's hook set differs).
import { PRODUCT_NAME } from "../../shared/theme";
import { USE_MOCKS, API_BASE_URL, DEV_COMPANY_ID } from "../../shared/config";
import { useCompany, useModelCatalog } from "../../shared/queries";
import { companyName, currentUser } from "../../shared/mockData";
import { formatTokens } from "../../shared/format";
import { StatusPill } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import { Card } from "../../ui/Card";
import "./SettingsPage.css";

function MockCompanyInfoCard() {
  return (
    <Card title="Company">
      <div className="settings-row">
        <span className="settings-row-label">Name</span>
        <span>{companyName}</span>
      </div>
      <div className="settings-row">
        <span className="settings-row-label">You</span>
        <span>{currentUser.displayName}</span>
      </div>
    </Card>
  );
}

function ApiCompanyInfoCard() {
  const companyQuery = useCompany(DEV_COMPANY_ID);
  return (
    <Card title="Company">
      {companyQuery.isLoading && <p className="about-text">Loading…</p>}
      {companyQuery.isError && <p className="about-text">Couldn't load company info.</p>}
      {companyQuery.data && (
        <>
          <div className="settings-row">
            <span className="settings-row-label">Name</span>
            <span>{companyQuery.data.name}</span>
          </div>
          <div className="settings-row">
            <span className="settings-row-label">Slug</span>
            <span className="mono">{companyQuery.data.slug}</span>
          </div>
          <div className="settings-row">
            <span className="settings-row-label">Plan</span>
            <span>{companyQuery.data.planTier}</span>
          </div>
        </>
      )}
    </Card>
  );
}

const CompanyInfoCard = USE_MOCKS ? MockCompanyInfoCard : ApiCompanyInfoCard;

function MockModelCatalogCard() {
  return (
    <Card title="Model Catalog">
      <EmptyState
        title="Live-API-only feature."
        description="GET /model-catalog is one of core-api's real endpoints with no mock fixture — switch off VITE_USE_MOCKS to see the live catalog."
      />
    </Card>
  );
}

function ApiModelCatalogCard() {
  const catalogQuery = useModelCatalog(DEV_COMPANY_ID);
  const models = catalogQuery.data ?? [];
  return (
    <Card title="Model Catalog">
      {catalogQuery.isLoading && <p className="about-text">Loading…</p>}
      {catalogQuery.isError && <p className="about-text">Couldn't load the model catalog.</p>}
      {!catalogQuery.isLoading && !catalogQuery.isError && models.length === 0 && (
        <EmptyState title="No models seeded yet." />
      )}
      <div className="chip-row">
        {models.map((m) => (
          <span className="chip" key={m.id} title={`${m.provider} · ${formatTokens(m.contextWindow)} context`}>
            {m.displayName} · {m.tier}
          </span>
        ))}
      </div>
    </Card>
  );
}

const ModelCatalogCard = USE_MOCKS ? MockModelCatalogCard : ApiModelCatalogCard;

export function SettingsPage() {
  return (
    <div className="settings-page">
      <header className="settings-page-head">
        <h1>Settings</h1>
        <p>Workspace info, model catalog, and where this app's data is coming from.</p>
      </header>

      <div className="settings-grid">
        <CompanyInfoCard />
        <ModelCatalogCard />

        <Card title="Data Source">
          <div className="settings-row">
            <span className="settings-row-label">Mode</span>
            <StatusPill
              label={USE_MOCKS ? "Mock fixtures" : "Live API"}
              tone={USE_MOCKS ? "neutral" : "success"}
            />
          </div>
          {!USE_MOCKS && (
            <div className="settings-row">
              <span className="settings-row-label">API base URL</span>
              <span className="mono">{API_BASE_URL}</span>
            </div>
          )}
          <p className="about-text" style={{ marginTop: 10, fontSize: 11.5 }}>
            {USE_MOCKS
              ? `${PRODUCT_NAME} is running entirely on local JSON fixtures (VITE_USE_MOCKS=1) — nothing here talks to core-api.`
              : `${PRODUCT_NAME} is reading and writing real data through core-api. Set VITE_USE_MOCKS=1 to switch to fixtures.`}
          </p>
        </Card>
      </div>
    </div>
  );
}
