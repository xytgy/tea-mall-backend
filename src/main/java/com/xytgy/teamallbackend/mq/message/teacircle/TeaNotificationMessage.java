package com.xytgy.teamallbackend.mq.message.teacircle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeaNotificationMessage {

    private Long targetUserId;
    private String type;
    private Long sourceId;
    private Long actorId;
}
