package com.phananh.arenaflow.application.support.chat;

/** Hằng của chat dùng chung — một nơi cho validator và service. */
public final class ConversationRules {

    public static final int MAX_MESSAGE_LENGTH = 1000;
    public static final int DEFAULT_HISTORY_LIMIT = 50;
    public static final int MAX_HISTORY_LIMIT = 100;

    private ConversationRules() {
    }
}
