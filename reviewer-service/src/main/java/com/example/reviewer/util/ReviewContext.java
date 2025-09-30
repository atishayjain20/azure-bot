package com.example.reviewer.util;

public final class ReviewContext {
    private static final ThreadLocal<String> USER_ID = new InheritableThreadLocal<>();
    private static final ThreadLocal<String> SESSION_ID = new InheritableThreadLocal<>();

    private ReviewContext() {}

    public static void setUserId(String userId) { USER_ID.set(userId); }
    public static String getUserId() { return USER_ID.get(); }
    public static void setSessionId(String sessionId) { SESSION_ID.set(sessionId); }
    public static String getSessionId() { return SESSION_ID.get(); }
    public static void clear() { USER_ID.remove(); SESSION_ID.remove(); }
}
