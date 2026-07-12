import { useEffect, useRef, useState } from "react";
import { Avatar } from "../../shared/Avatar";
import { formatClockTime } from "../../shared/format";
import { BotIcon, EmojiIcon, HashIcon, PaperclipIcon, SendIcon } from "../../shared/icons";
import { currentUser } from "../../shared/mockData";
import { StatusDot } from "../../shared/StatusDot";
import { useApp } from "../../shared/store";
import type { AppState } from "../../shared/store";
import { PanelShell } from "./PanelShell";

function senderName(sender: string, state: AppState): string {
  if (sender === "bot") return "Atrium Bot";
  if (sender.startsWith("user:")) return currentUser.displayName;
  const agent = state.agents.find((a) => `agent:${a.id}` === sender);
  return agent?.name ?? "Unknown";
}

function botStatusReply(state: AppState): string {
  const inProgress = state.tasks.filter((t) => t.status === "in_progress").length;
  const waiting = state.tasks.filter(
    (t) => t.status === "pending_review" || t.status === "flagged",
  ).length;
  return `Right now: ${inProgress} tasks in progress, ${waiting} waiting on you. Open Approvals to clear the queue.`;
}

export function ChatPanel() {
  const { state, dispatch } = useApp();
  const [draft, setDraft] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);

  const channel =
    state.channels.find((c) => c.id === state.ui.activeChannelId) ?? state.channels[0];
  const thread = state.messages
    .filter((m) => m.channelId === channel.id)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));

  const messageCount = thread.length;
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messageCount, channel.id]);

  const send = () => {
    const text = draft.trim();
    if (!text) return;
    setDraft("");
    dispatch({ type: "sendMessage", channelId: channel.id, sender: `user:${currentUser.id}`, text });

    // Simulated replies until office-realtime exists: DMs answer, the bot narrates.
    if (channel.kind === "dm") {
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
    }
  };

  const channels = state.channels.filter((c) => c.kind === "channel");
  const dms = state.channels.filter((c) => c.kind === "dm");

  return (
    <PanelShell title="Chat" subtitle={channel.kind === "channel" ? `# ${channel.name}` : channel.name} width={760} noPad>
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
          <div className="chat-messages">
            {thread.length === 0 && <div className="empty-note">No messages yet. Say hi!</div>}
            {thread.map((msg) => {
              const name = senderName(msg.sender, state);
              return (
                <div className="chat-msg" key={msg.id}>
                  {msg.sender === "bot" ? (
                    <span className="rail-avatar" style={{ width: 30, height: 30 }}>
                      <BotIcon width={15} height={15} />
                    </span>
                  ) : (
                    <Avatar name={name} seed={msg.sender} size={30} />
                  )}
                  <div className="chat-msg-body">
                    <div className="chat-msg-head">
                      <span className="chat-msg-sender">{name}</span>
                      <span className="chat-msg-time">{formatClockTime(msg.createdAt)}</span>
                    </div>
                    <div className="chat-msg-text">{msg.text}</div>
                  </div>
                </div>
              );
            })}
            <div ref={bottomRef} />
          </div>

          <div className="chat-input-row">
            <input
              className="chat-input"
              placeholder={channel.kind === "channel" ? `Message #${channel.name}` : `Message ${channel.name}`}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && send()}
            />
            <button className="chat-icon-btn" aria-label="Emoji"><EmojiIcon /></button>
            <button className="chat-icon-btn" aria-label="Attach"><PaperclipIcon /></button>
            <button className="chat-icon-btn" aria-label="Send" onClick={send}>
              <SendIcon />
            </button>
          </div>
        </div>
      </div>
    </PanelShell>
  );
}
