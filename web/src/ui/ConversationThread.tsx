import { useEffect, useRef, useState } from "react";
import { Avatar } from "../shared/Avatar";
import { formatClockTime } from "../shared/format";
import { BotIcon, EmojiIcon, PaperclipIcon, SendIcon } from "../shared/icons";
import { currentUser } from "../shared/mockData";
import { useApp } from "../shared/store";
import type { AppState } from "../shared/store";
import { EmptyState } from "./EmptyState";
import "./ConversationThread.css";

function senderName(sender: string, state: AppState): string {
  if (sender === "bot") return "Atrium Bot";
  // "user:<id>" (mock) and bare "user" (API mode, no X-User-Id) both mean the human.
  if (sender === "user" || sender.startsWith("user:")) return currentUser.displayName;
  const agent = state.agents.find((a) => `agent:${a.id}` === sender);
  return agent?.name ?? "Unknown";
}

interface ConversationThreadProps {
  channelId: string;
  placeholder?: string;
  // Fires after a message is dispatched — lets a caller layer in side effects
  // (ChatPanel's simulated DM/bot replies) without ConversationThread itself
  // knowing about them.
  onSend?: (text: string) => void;
  // ChatPanel embeds this in an already-bounded flex column (.chat-thread)
  // and wants it to fill that space; other consumers (TaskDrawer) want a
  // fixed, self-contained height instead.
  fill?: boolean;
}

// The message-list + composer half of ChatPanel, extracted so any surface can
// embed a single-channel conversation (first non-ChatPanel consumer:
// TaskDrawer's Conversation tab, MF-3). Channel nav / multi-channel chrome
// stays in ChatPanel — this only ever renders one channel's thread. Owns its
// own .chat-msg*/.chat-input* classes in ConversationThread.css (MF-6 —
// moved out of the now-deleted dashboard/panels/panels.css).
export function ConversationThread({ channelId, placeholder = "Message…", onSend, fill = false }: ConversationThreadProps) {
  const { state, dispatch } = useApp();
  const [draft, setDraft] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);

  const thread = state.messages
    .filter((m) => m.channelId === channelId)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));

  const messageCount = thread.length;
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messageCount, channelId]);

  const send = () => {
    const text = draft.trim();
    if (!text) return;
    setDraft("");
    dispatch({ type: "sendMessage", channelId, sender: `user:${currentUser.id}`, text });
    onSend?.(text);
  };

  return (
    <div className={`conversation-thread${fill ? " conversation-thread-fill" : ""}`}>
      <div className="chat-messages">
        {thread.length === 0 && <EmptyState title="No messages yet." description="Say hi!" />}
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
          placeholder={placeholder}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && send()}
        />
        <button className="chat-icon-btn" aria-label="Emoji">
          <EmojiIcon />
        </button>
        <button className="chat-icon-btn" aria-label="Attach">
          <PaperclipIcon />
        </button>
        <button className="chat-icon-btn" aria-label="Send" onClick={send}>
          <SendIcon />
        </button>
      </div>
    </div>
  );
}
