import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import "./Markdown.css";

// Agent-produced artifact content (task output, research reports, etc.) is
// plain-text Markdown from the LLM — this is the one place it gets parsed
// instead of dumped raw into a <div>.
export function Markdown({ children }: { children: string }) {
  return (
    <div className="md">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
    </div>
  );
}
