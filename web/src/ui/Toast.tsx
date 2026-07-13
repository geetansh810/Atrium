import { useState } from "react";
import { BotIcon } from "../shared/icons";
import { PRODUCT_NAME } from "../shared/theme";
import { currentUser } from "../shared/mockData";
import { useApp } from "../shared/store";
import "./Toast.css";

// Replaces the old BotBar's toast strip: a one-time welcome greeting, then
// whatever ui.botNotice carries (dispatch({type:"setNotice", text}) from
// anywhere — locate-me, budget warnings, etc.).
export function Toast() {
  const { state, dispatch } = useApp();
  const [greetingDismissed, setGreetingDismissed] = useState(false);
  const notice = state.ui.botNotice;
  const inProgressCount = state.tasks.filter((t) => t.status === "in_progress").length;

  if (notice === null && greetingDismissed) return null;

  return (
    <div className="toast">
      <span className="toast-avatar">
        <BotIcon width={18} height={18} />
      </span>
      <div className="toast-body">
        <div className="toast-name">{PRODUCT_NAME} Bot</div>
        <div className="toast-text">
          {notice ?? (
            <>
              Welcome back, {currentUser.displayName}! You have {inProgressCount} tasks in
              progress.
            </>
          )}
        </div>
      </div>
      <button
        className="toast-close"
        onClick={() => (notice ? dispatch({ type: "dismissNotice" }) : setGreetingDismissed(true))}
        aria-label="Dismiss"
      >
        ×
      </button>
    </div>
  );
}
