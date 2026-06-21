import { useRef, useState } from "react";

export function useActionLock(cooldownMs = 650) {
  const lastRunRef = useRef(0);
  const [locked, setLocked] = useState(false);

  async function run<T>(action: () => Promise<T> | T): Promise<T | undefined> {
    const now = Date.now();
    if (locked || now - lastRunRef.current < cooldownMs) {
      return undefined;
    }
    lastRunRef.current = now;
    setLocked(true);
    try {
      return await action();
    } finally {
      window.setTimeout(() => setLocked(false), cooldownMs);
    }
  }

  return { locked, run };
}
