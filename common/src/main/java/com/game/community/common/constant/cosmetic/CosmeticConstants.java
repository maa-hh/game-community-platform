package com.game.community.common.constant.cosmetic;

public final class CosmeticConstants {

    public static final long DEF_CACHE_SECONDS = 600;
    public static final String DEF_CACHE_KEY_PREFIX = "user:cosmetic:def:";
    public static final int FIRST_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String FIRST_PAGE_TEXT = "1";
    public static final String DEFAULT_PAGE_SIZE_TEXT = "20";
    public static final int DEFAULT_QUANTITY = 1;
    public static final int INITIAL_VERSION = 0;
    public static final int NON_STACKABLE = 0;
    public static final int STACKABLE = 1;

    public static final class EffectMode {
        public static final String EQUIP = "EQUIP";
        public static final String CONSUMABLE = "CONSUMABLE";

        private EffectMode() {
        }
    }

    public static final class Category {
        public static final String AVATAR_FRAME = "AVATAR_FRAME";
        public static final String COMMENT_CARD = "COMMENT_CARD";
        public static final String COMMENT_FONT = "COMMENT_FONT";
        public static final String POST_CARD = "POST_CARD";
        public static final String PROFILE_BG = "PROFILE_BG";

        private Category() {
        }
    }

    public static final class Slot {
        public static final String AVATAR_FRAME = "AVATAR_FRAME";
        public static final String COMMENT_CARD = "COMMENT_CARD";
        public static final String COMMENT_FONT = "COMMENT_FONT";
        public static final String POST_CARD = "POST_CARD";
        public static final String PROFILE_BG = "PROFILE_BG";

        private Slot() {
        }
    }

    public static final class SourceType {
        public static final String SHOP = "SHOP";
        public static final String EVENT = "EVENT";
        public static final String ADMIN = "ADMIN";

        private SourceType() {
        }
    }

    public static final class State {
        public static final String ACTIVE = "ACTIVE";
        public static final String EXPIRED = "EXPIRED";

        private State() {
        }
    }

    public static final int STATUS_OFF = 0;
    public static final int STATUS_ON = 1;

    private CosmeticConstants() {
    }
}
