import { ApiError } from "../api/client";

export function friendlyError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.blockType === "FLOW") {
      return "操作太快啦，网关已经触发限流保护，请稍后再试。";
    }
    if (error.blockType === "DEGRADE") {
      return "服务正在自我保护中，网关已触发熔断，请稍后重试。";
    }
    if (error.status === 401) {
      return "登录状态已失效，请重新登录。";
    }
    return error.message || "请求失败，请稍后再试。";
  }
  return "网络或服务暂时不可用，请稍后再试。";
}
