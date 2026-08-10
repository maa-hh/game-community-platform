import { useState } from 'react';

export function usePostBottomBar(onComment: (content: string) => void) {
  const [draft, setDraft] = useState('');

  const send = () => {
    const text = draft.trim();
    if (!text) return;
    onComment(text);
    setDraft('');
  };

  return { draft, setDraft, send };
}
