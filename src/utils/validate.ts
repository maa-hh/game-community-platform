/** 常见邮箱格式 */
export const EMAIL_REG = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

/** 密码 6-10 位，仅数字、字母或常见特殊字符 */
export const PASSWORD_REG =
  /^[a-zA-Z0-9!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?`~]{6,10}$/;

export function isValidEmail(email: string): boolean {
  return EMAIL_REG.test(email);
}

export function isValidPassword(password: string): boolean {
  return PASSWORD_REG.test(password);
}
