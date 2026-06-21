import { ButtonHTMLAttributes } from "react";
import { useActionLock } from "../hooks/useActionLock";

type ActionButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  busy?: boolean;
  cooldownMs?: number;
  variant?: "primary" | "ghost" | "danger" | "soft";
};

export function ActionButton({
  busy,
  cooldownMs = 650,
  variant = "primary",
  className = "",
  children,
  onClick,
  disabled,
  ...props
}: ActionButtonProps) {
  const { locked, run } = useActionLock(cooldownMs);
  const isDisabled = disabled || busy || locked;

  return (
    <button
      {...props}
      className={`action-button action-button--${variant} ${className}`}
      disabled={isDisabled}
      onClick={(event) => {
        if (!onClick) {
          return;
        }
        void run(() => onClick(event));
      }}
    >
      <span>{busy ? "处理中..." : children}</span>
    </button>
  );
}
