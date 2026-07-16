import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { useCreateTaskMutation, useHireAgentMutation, describeApiError } from "../../shared/queries";
import { STARTER_PACKS } from "../../shared/starterPacks";
import type { StarterPack } from "../../shared/starterPacks";
import { logFunnelEvent } from "../../shared/funnel";
import { PRODUCT_NAME } from "../../shared/theme";
import type { AgentResponse } from "../../shared/api";
import "./OnboardingWizard.css";

type Phase = "pick" | "hiring" | "task" | "done";

// M3.4: the guided post-signup flow — pick a starter pack, hire it, assign a
// suggested first task, land on the Team view. Only ever mounted in API mode
// (mock mode has no signup/auth at all — see App.tsx's AuthGate) and only for
// a fresh signup (shared/onboarding.ts's needsOnboarding flag), so it talks to
// the hire/create-task mutations directly rather than through the generic
// AppState/Action contract mockStore.tsx also has to satisfy.
export function OnboardingWizard({ companyId, onDone }: { companyId: string; onDone: () => void }) {
  const navigate = useNavigate();
  const hireAgentM = useHireAgentMutation(companyId);
  const createTaskM = useCreateTaskMutation(companyId);

  const [phase, setPhase] = useState<Phase>("pick");
  const [pack, setPack] = useState<StarterPack | null>(null);
  const [hired, setHired] = useState<AgentResponse[]>([]);
  const [hireError, setHireError] = useState<string | null>(null);

  const [taskTitle, setTaskTitle] = useState("");
  const [taskDescription, setTaskDescription] = useState("");
  const [taskSkill, setTaskSkill] = useState("");
  const [taskError, setTaskError] = useState<string | null>(null);
  const [creatingTask, setCreatingTask] = useState(false);

  useEffect(() => {
    logFunnelEvent("started", { companyId });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const skip = () => {
    logFunnelEvent("skipped", { atPhase: phase });
    onDone();
  };

  const startPack = async (chosen: StarterPack) => {
    setPack(chosen);
    setHireError(null);
    setPhase("hiring");
    logFunnelEvent("pack_selected", { pack: chosen.key });
    await hireMembers(chosen, []);
  };

  const hireMembers = async (chosen: StarterPack, alreadyHired: AgentResponse[]) => {
    const nextHired = [...alreadyHired];
    let leadId: string | null = nextHired[0]?.id ?? null;
    try {
      for (let i = nextHired.length; i < chosen.members.length; i++) {
        const member = chosen.members[i];
        const agent = await hireAgentM.mutateAsync({
          roleTemplateKey: member.roleTemplateKey,
          name: member.name,
          roleTitle: member.roleTitle,
          skillTags: member.skillTags,
          modelProvider: member.modelProvider,
          modelName: member.modelName,
          managerAgentId: i === 0 ? null : leadId,
          about: `${member.roleTitle} — hired from the ${chosen.name} starter pack.`,
          budgetTokens: member.budgetTokens,
        });
        if (i === 0) leadId = agent.id;
        nextHired.push(agent);
        setHired([...nextHired]);
      }
      logFunnelEvent("agents_hired", { pack: chosen.key, count: nextHired.length });
      setTaskTitle(chosen.sampleTask.title);
      setTaskDescription(chosen.sampleTask.description);
      setTaskSkill(chosen.sampleTask.skillTag);
      setPhase("task");
    } catch (err) {
      setHireError(describeApiError(err));
    }
  };

  const retryHiring = () => {
    if (!pack) return;
    setHireError(null);
    void hireMembers(pack, hired);
  };

  const createFirstTask = async () => {
    if (!taskTitle.trim() || !taskSkill) return;
    setCreatingTask(true);
    setTaskError(null);
    try {
      await createTaskM.mutateAsync({
        title: taskTitle.trim(),
        description: taskDescription.trim(),
        requiredSkill: taskSkill,
        priority: 3,
        etaMinutes: null,
      });
      logFunnelEvent("first_task_created", { pack: pack?.key, skill: taskSkill });
      setPhase("done");
    } catch (err) {
      setTaskError(describeApiError(err));
    } finally {
      setCreatingTask(false);
    }
  };

  const finish = (destination: string) => {
    logFunnelEvent("completed", { pack: pack?.key });
    onDone();
    navigate(destination);
  };

  const skillOptions = Array.from(new Set(hired.flatMap((a) => a.skillTags)));

  return (
    <div className="onboarding-page">
      <div className="onboarding-card">
        <div className="onboarding-title">{PRODUCT_NAME}</div>

        {phase === "pick" && (
          <>
            <h1 className="onboarding-h1">Hire your first team</h1>
            <p className="onboarding-sub">
              Pick a starter roster — every agent below is real, gets hired for you, and starts working the moment
              you assign it a task.
            </p>
            <div className="onboarding-pack-grid">
              {STARTER_PACKS.map((p) => (
                <button key={p.key} className="onboarding-pack-card" onClick={() => void startPack(p)}>
                  <div className="onboarding-pack-name">{p.name}</div>
                  <div className="onboarding-pack-desc">{p.description}</div>
                  <ul className="onboarding-pack-members">
                    {p.members.map((m, i) => (
                      <li key={i}>
                        {m.name} — {m.roleTitle}
                      </li>
                    ))}
                  </ul>
                </button>
              ))}
            </div>
            <button className="onboarding-skip" onClick={skip}>
              Skip for now — I'll hire agents myself
            </button>
          </>
        )}

        {phase === "hiring" && pack && (
          <>
            <h1 className="onboarding-h1">Hiring the {pack.name}…</h1>
            <p className="onboarding-sub">This takes a few seconds per agent.</p>
            <ul className="onboarding-hire-list">
              {pack.members.map((m, i) => (
                <li key={i} className={i < hired.length ? "done" : i === hired.length && !hireError ? "active" : ""}>
                  {m.name} — {m.roleTitle} {i < hired.length ? "✓" : i === hired.length && !hireError ? "…" : ""}
                </li>
              ))}
            </ul>
            {hireError && (
              <div className="onboarding-error">
                {hireError}
                <button className="btn primary" onClick={retryHiring}>
                  Retry
                </button>
              </div>
            )}
          </>
        )}

        {phase === "task" && (
          <>
            <h1 className="onboarding-h1">Assign your first task</h1>
            <p className="onboarding-sub">
              Your team is hired and online. Send them one small task now so you can watch a real agent claim it,
              do the work, and hand it back for your review.
            </p>
            <div className="field">
              <label>Title</label>
              <input value={taskTitle} onChange={(e) => setTaskTitle(e.target.value)} />
            </div>
            <div className="field">
              <label>Description</label>
              <textarea rows={3} value={taskDescription} onChange={(e) => setTaskDescription(e.target.value)} />
            </div>
            <div className="field">
              <label>Required skill</label>
              <select value={taskSkill} onChange={(e) => setTaskSkill(e.target.value)}>
                {skillOptions.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
            {taskError && <div className="onboarding-error">{taskError}</div>}
            <div className="onboarding-actions">
              <button className="onboarding-skip" onClick={() => setPhase("done")}>
                Skip this step
              </button>
              <button
                className="btn primary"
                disabled={!taskTitle.trim() || creatingTask}
                onClick={() => void createFirstTask()}
              >
                {creatingTask ? "Assigning…" : "Assign task"}
              </button>
            </div>
          </>
        )}

        {phase === "done" && (
          <>
            <h1 className="onboarding-h1">You're all set</h1>
            <p className="onboarding-sub">
              Your team is hired and — if a task was assigned — already working. Watch it come together on the
              Team view, or head to Mission Control for the full picture.
            </p>
            <div className="onboarding-actions">
              <button className="btn" onClick={() => finish("/")}>
                Go to Mission Control
              </button>
              <button className="btn primary" onClick={() => finish("/team")}>
                Go to Team view
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
