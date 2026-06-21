import { useState } from "react";
import { resolveAvatarUrl } from "../utils/avatar";

type AvatarImageProps = {
  src?: string | null;
  name?: string | null;
  alt?: string;
  className?: string;
};

export function AvatarImage({ src, name, alt, className = "avatar" }: AvatarImageProps) {
  const [failedSrc, setFailedSrc] = useState("");
  const resolvedSrc = resolveAvatarUrl(src);
  const shouldShowImage = resolvedSrc && resolvedSrc !== failedSrc;
  const fallback = name?.slice(0, 1).toUpperCase() || "玩";

  return (
    <div className={className}>
      {shouldShowImage ? (
        <img src={resolvedSrc} alt={alt ?? name ?? "用户头像"} onError={() => setFailedSrc(resolvedSrc)} />
      ) : (
        fallback
      )}
    </div>
  );
}
