import { useAuthSession } from "./auth";
import { useApp } from "./store";

// Resolves an event actor string ("agent:<id>" | "user:<id>" | "system") to a
// display name, "You" when it is the signed-in user.
export function useActorName() {
  const { state } = useApp();
  const session = useAuthSession();
  const agents = new Map(state.agents.map((a) => [a.id, a.name]));
  return (actor: string): string => {
    if (actor.startsWith("agent:")) return agents.get(actor.slice(6)) ?? "An agent";
    if (actor.startsWith("user:")) return session && actor.slice(5) === session.userId ? "You" : "A team member";
    if (actor === "user") return "You";
    return "System";
  };
}

