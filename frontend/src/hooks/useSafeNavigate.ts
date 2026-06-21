import { useNavigate } from "react-router-dom";
import { useActionLock } from "./useActionLock";

export function useSafeNavigate(cooldownMs = 520) {
  const navigate = useNavigate();
  const { locked, run } = useActionLock(cooldownMs);

  return {
    navigating: locked,
    go: (path: string) => run(() => navigate(path))
  };
}
