package com.xytgy.teamallbackend.mq.constant;

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

    public static final String GROUP_ORDER_TIMEOUT = "tea-mall-order-timeout-group";
    public static final String GROUP_PAYMENT_NOTIFY = "tea-mall-payment-notify-group";
    public static final String GROUP_TEA_NOTIFICATION = "tea-mall-tea-notification-group";
    public static final String GROUP_CHAT_MESSAGE = "tea-mall-chat-dispatch-group";
    public static final String GROUP_FLASH_ORDER = "tea-mall-flash-order-group";

    public static final int DELAY_LEVEL_30_MINUTES = 6;
}
