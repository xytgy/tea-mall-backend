package com.xytgy.teamallbackend.config.mq;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MqConstants {

    public static final String TOPIC_ORDER_TIMEOUT = "TOPIC_ORDER_TIMEOUT";
    public static final String TAG_TIMEOUT_CANCEL = "TIMEOUT_CANCEL";

    public static final String TOPIC_PAYMENT_NOTIFY = "TOPIC_PAYMENT_NOTIFY";
    public static final String TAG_PAY_SUCCESS = "PAY_SUCCESS";

    public static final String TOPIC_TEA_NOTIFICATION = "TOPIC_TEA_NOTIFICATION";
    public static final String TAG_LIKE = "LIKE";
    public static final String TAG_COMMENT = "COMMENT";

    public static final String TOPIC_CHAT_MESSAGE = "TOPIC_CHAT_MESSAGE";
    public static final String TAG_MSG_DISPATCH = "MSG_DISPATCH";

    public static final String TOPIC_FLASH_ORDER = "TOPIC_FLASH_ORDER";
    public static final String TAG_FLASH_ORDER = "FLASH_ORDER";

    public static final int DELAY_LEVEL_30_MINUTES = 6;
}
