package com.game.community.utils.email;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 邮箱校验：格式 + DNS MX 记录（比单纯正则更可靠）
 */
public final class EmailValidator {

    private static final Pattern FORMAT_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private EmailValidator() {
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidFormat(String email) {
        String normalized = normalize(email);
        return !normalized.isBlank() && FORMAT_PATTERN.matcher(normalized).matches();
    }

    /**
     * 查询域名 MX 记录，判断邮箱域名是否可收信
     */
    public static boolean hasMxRecord(String email) {
        String normalized = normalize(email);
        int at = normalized.indexOf('@');
        if (at <= 0 || at >= normalized.length() - 1) {
            return false;
        }
        String domain = normalized.substring(at + 1);
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            InitialDirContext context = new InitialDirContext(env);
            //dig MX gmail.com 初步判断域名有效性
            Attributes attrs = context.getAttributes(domain, new String[]{"MX"});
            Attribute mx = attrs.get("MX");
            return mx != null && mx.size() > 0;
        } catch (NamingException e) {
            return false;
        }
    }

    public static void assertDeliverable(String email, boolean mxCheckEnabled) {
        if (!isValidFormat(email)) {
            throw new IllegalArgumentException("请输入正确的邮箱格式");
        }
        if (mxCheckEnabled && !hasMxRecord(email)) {
            throw new IllegalArgumentException("邮箱域名无效或无法接收邮件");
        }
    }
}
