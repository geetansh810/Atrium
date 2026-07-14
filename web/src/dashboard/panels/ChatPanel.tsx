import { BotIcon, HashIcon } from "../../shared/icons";
import { StatusDot } from "../../shared/StatusDot";
import { useApp } from "../../shared/store";
import type { AppState } from "../../shared/store";
import { Drawer } from "../../ui/Drawer";
import { ConversationThread } from "../../ui/ConversationThread";
import "./ChatPanel.css";

function botStatusReply(state: AppState): string {
  const inProgress = state.tasks.filter((t) => t.status === "in_progress").length;
  const waiting = state.tasks.filter(
    (t) => t.status === "pending_review" || t.status === "flagged",
  ).length;
  return `Right now: ${inProgress} tasks in progress, ${waiting} waiting on you. Open Approvals to clear the queue.`;
}

export function ChatPanel() {
  const { state, dispatch } = useApp();

  const channel =
    state.channels.find((c) => c.id === state.ui.activeChannelId) ?? state.channels[0];

  // Simulated replies until office-realtime exists: DMs answer, the bot narrates.
  const handleSend = () => {
    if (channel.kind !== "dm") return;
    const agent = state.agents.find((a) => a.id === channel.agentId);
    const reply =
      channel.agentId === "bot"
        ? botStatusReply(state)
        : agent
          ? `${agent.currentActivity} right now — I'll pick this up as soon as I wrap up. 👍`
          : null;
    if (reply) {
      const sender = channel.agentId === "bot" ? "bot" : `agent:${channel.agentId}`;
      setTimeout(() => {
        dispatch({ type: "sendMessage", channelId: channel.id, sender, text: reply });
      }, 900);
    }
  };

  const channels = state.channels.filter((c) => c.kind === "channel");
  const dms = state.channels.filter((c) => c.kind === "dm");

  return (
    <Drawer
      title="Chat"
      subtitle={channel.kind === "channel" ? `# ${channel.name}` : channel.name}
      width={760}
      noPad
      onClose={() => dispatch({ type: "setChatOpen", open: false })}
    >
      <div className="chat-grid">
        <nav className="chat-nav">
          <div className="section-title" style={{ padding: "0 8px" }}>Channels</div>
          {channels.map((c) => (
            <button
              key={c.id}
              className={`chat-nav-item${c.id === channel.id ? " active" : ""}`}
              onClick={() => dispatch({ type: "setChannel", channelId: c.id })}
            >
              <HashIcon width={13} height={13} /> {c.name}
            </button>
          ))}
          <div className="section-title" style={{ padding: "0 8px" }}>Direct Messages</div>
          {dms.map((c) => {
            const agent = state.agents.find((a) => a.id === c.agentId);
            return (
              <button
                key={c.id}
                className={`chat-nav-item${c.id === channel.id ? " active" : ""}`}
                onClick={() => dispatch({ type: "setChannel", channelId: c.id })}
              >
                {agent ? <StatusDot status={agent.status} size={7} /> : <BotIcon width={13} height={13} />}
                {c.name}
              </button>
            );
          })}
        </nav>

        <div className="chat-thread">
          <ConversationThread
            channelId={channel.id}
            placeholder={channel.kind === "channel" ? `Message #${channel.name}` : `Message ${channel.name}`}
            onSend={handleSend}
            fill
          />
        </div>
      </div>
    </Drawer>
  );
}
