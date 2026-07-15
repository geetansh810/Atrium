import type { KeyboardEvent } from "react";
import { Avatar } from "../../shared/Avatar";
import { ProgressBar } from "../../shared/ProgressBar";
import { formatTimeAgo } from "../../shared/format";
import { skillColor } from "../../shared/skillColor";
import { useApp } from "../../shared/store";
import { useAppNav } from "../../shared/nav";
import {
  computeKpis,
  liveTeamZones,
  type TeamMember,
  type TeamZoneKey,
} from "../../shared/selectors";
import { PageShell } from "../../ui/PageShell";
import { StatusPill, type PillTone } from "../../ui/StatusPill";
import { EmptyState } from "../../ui/EmptyState";
import "./TeamPage.css";

const ZONE_TONE: Record<TeamZoneKey, PillTone> = {
  working: "running",
  awaiting_review: "warning",
  in_focus: "info",
  idle: "neutral",
  paused: "warning",
  offline: "neutral",
};

// Working/Awaiting review are the page's point — always visible, with a muted
// hint when empty. The status-derived zones only render when occupied.
const ALWAYS_VISIBLE: Set<TeamZoneKey> = new Set(["working", "awaiting_review"]);

const ZONE_EMPTY_HINT: Partial<Record<TeamZoneKey, string>> = {
  working: "Nobody is working on a task right now.",
  awaiting_review: "Nothing waiting on your review.",
};

// The live team board: every agent as a clickable card, grouped by what it's
// doing RIGHT NOW (liveTeamZones — derived from live task assignments over
// the 4s polls, not the stale agent.status field). Replaced the SkyOffice
// office view 2026-07-15.
export function TeamPage() {
  const { state, dispatch } = useApp();

  const zones = liveTeamZones(state.agents, state.tasks);
  const kpis = computeKpis(state.agents, state.tasks);
  const workingCount = zones.find((z) => z.key === "working")?.members.length ?? 0;
  const reviewCount = zones.find((z) => z.key === "awaiting_review")?.members.length ?? 0;
  const subtitle = `${kpis.activeAgents}/${kpis.totalAgents} agents active · ${workingCount} working · ${reviewCount} awaiting review`;

  if (state.agents.length === 0) {
    return (
      <PageShell title="Team" subtitle="No agents on the roster yet.">
        <EmptyState
          title="No agents on the roster yet"
          description="Hire your first agent and it will show up here the moment it starts working."
          action={
            <button className="btn primary" onClick={() => dispatch({ type: "setInviteOpen", open: true })}>
              Hire Agent
            </button>
          }
        />
      </PageShell>
    );
  }

  return (
    <PageShell title="Team" subtitle={subtitle}>
      <div className="team-zones">
        {zones.map((zone) => {
          if (zone.members.length === 0 && !ALWAYS_VISIBLE.has(zone.key)) return null;
          return (
            <section key={zone.key} className="team-zone">
              <header className="team-zone-head">
                <h2>{zone.label}</h2>
                <span className="team-zone-count">{zone.members.length}</span>
              </header>
              {zone.members.length === 0 ? (
                <p className="team-zone-empty">{ZONE_EMPTY_HINT[zone.key]}</p>
              ) : (
                <div className="team-zone-grid">
                  {zone.members.map((member) => (
                    <TeamMemberCard key={member.agent.id} member={member} />
                  ))}
                </div>
              )}
            </section>
          );
        })}
      </div>
    </PageShell>
  );
}

function TeamMemberCard({ member }: { member: TeamMember }) {
  const nav = useAppNav();
  const { agent, zone, activeTask, sinceIso, workload } = member;

  // A div, not a <button>: the card nests a clickable task row and
  // button-in-button is invalid HTML.
  const openAgent = () => nav.openAgent(agent.id);
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      openAgent();
    }
  };

  const sinceLabel =
    sinceIso === null ? null : zone === "working" ? `claimed ${formatTimeAgo(sinceIso)}` : `completed ${formatTimeAgo(sinceIso)}`;

  return (
    <div className="team-card" role="button" tabIndex={0} onClick={openAgent} onKeyDown={onKeyDown}>
      <div className="team-card-top">
        <Avatar name={agent.name} seed={agent.id} size={36} />
        <div className="team-card-info">
          <div className="team-card-name">{agent.name}</div>
          <div className="team-card-role">{agent.roleTitle}</div>
        </div>
        <StatusPill label={zone === "paused" ? "Paused" : zoneLabelShort(zone)} tone={ZONE_TONE[zone]} />
      </div>

      {activeTask && (
        <button
          className="team-card-task"
          onClick={(e) => {
            e.stopPropagation();
            nav.openTask(activeTask.id);
          }}
        >
          <span className="team-card-task-title">{activeTask.title}</span>
          <span className="team-card-task-meta">
            <ProgressBar value={activeTask.progress} height={4} />
            {sinceLabel && <span className="team-card-task-since">{sinceLabel}</span>}
          </span>
        </button>
      )}

      <div className="team-card-skills">
        {agent.skillTags.slice(0, 3).map((skill) => (
          <span key={skill} className="team-card-skill" style={{ background: skillColor(skill) }}>
            {skill}
          </span>
        ))}
        {agent.skillTags.length > 3 && <span className="team-card-skill-more">+{agent.skillTags.length - 3}</span>}
      </div>

      <div className="team-card-foot">
        <span className="team-card-model">
          {agent.modelProvider} · {agent.modelName}
        </span>
        <span className="team-card-workload">{workload} active</span>
      </div>
    </div>
  );
}

function zoneLabelShort(zone: TeamZoneKey): string {
  if (zone === "working") return "Working";
  if (zone === "awaiting_review") return "In review";
  if (zone === "in_focus") return "In focus";
  if (zone === "offline") return "Offline";
  return "Idle";
}
