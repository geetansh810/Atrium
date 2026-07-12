import { useState } from "react";
import { PRODUCT_NAME } from "../shared/theme";
import { currentUser } from "../shared/mockData";
import { useApp } from "../shared/store";
import {
  BotIcon,
  FullscreenIcon,
  GridIcon,
  LocateIcon,
  PeopleIcon,
  ScreenIcon,
  SendIcon,
} from "../shared/icons";
import "./BotBar.css";

export function BotBar() {
  const { state, dispatch } = useApp();
  const [message, setMessage] = useState("");
  const [greetingDismissed, setGreetingDismissed] = useState(false);

  const inProgressCount = state.tasks.filter((t) => t.status === "in_progress").length;
  const notice = state.ui.botNotice;
  const showToast = notice !== null || !greetingDismissed;

  const send = () => {
    const text = message.trim();
    if (!text) return;
    setMessage("");
    // "Message Atrium…" = DM to the bot; open the thread so the reply is visible.
    dispatch({ type: "sendMessage", channelId: "dm-bot", sender: `user:${currentUser.id}`, text });
    dispatch({ type: "openPanel", panel: "chat", channelId: "dm-bot" });
    const waiting = state.tasks.filter(
      (t) => t.status === "pending_review" || t.status === "flagged",
    ).length;
    setTimeout(() => {
      dispatch({
        type: "sendMessage",
        channelId: "dm-bot",
        sender: "bot",
        text: `On it! Quick status: ${inProgressCount} tasks in progress, ${waiting} waiting on you.`,
      });
    }, 900);
  };

  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void document.documentElement.requestFullscreen();
  };

  return (
    <div className="botbar">
      {showToast && (
        <div className="botbar-toast">
          <span className="botbar-bot-avatar">
            <BotIcon width={18} height={18} />
          </span>
          <div className="botbar-toast-body">
            <div className="botbar-toast-name">{PRODUCT_NAME} Bot</div>
            <div className="botbar-toast-text">
              {notice ?? (
                <>
                  Welcome back, {currentUser.displayName}! 👋
                  <br />
                  You have {inProgressCount} tasks in progress.
                </>
              )}
            </div>
          </div>
          <button
            className="botbar-toast-close"
            onClick={() =>
              notice ? dispatch({ type: "dismissNotice" }) : setGreetingDismissed(true)
            }
            aria-label="Dismiss"
          >
            ×
          </button>
        </div>
      )}

      <div className="botbar-input-row">
        <input
          className="botbar-input"
          placeholder={`Message ${PRODUCT_NAME}...`}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && send()}
        />
        <button className="botbar-send" aria-label="Send" onClick={send}>
          <SendIcon width={15} height={15} />
        </button>

        <div className="botbar-actions">
          <button
            className="botbar-icon-btn"
            aria-label="Locate me"
            onClick={() =>
              dispatch({ type: "setNotice", text: `You're in the ${state.ui.activeRoom}.` })
            }
          >
            <LocateIcon />
          </button>
          <button
            className="botbar-icon-btn"
            aria-label="People"
            onClick={() => dispatch({ type: "toggleRail" })}
          >
            <PeopleIcon />
          </button>
          <button
            className="botbar-icon-btn"
            aria-label="Workspace"
            onClick={() => dispatch({ type: "openPanel", panel: "workspace" })}
          >
            <ScreenIcon />
          </button>
          <button
            className="botbar-icon-btn"
            aria-label="Analytics"
            onClick={() => dispatch({ type: "openPanel", panel: "analytics" })}
          >
            <GridIcon />
          </button>
          <button className="botbar-icon-btn" aria-label="Fullscreen" onClick={toggleFullscreen}>
            <FullscreenIcon />
          </button>
        </div>
      </div>
    </div>
  );
}
