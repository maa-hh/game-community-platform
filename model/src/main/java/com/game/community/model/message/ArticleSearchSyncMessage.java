package com.game.community.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSearchSyncMessage implements Serializable {

    public static final String UPSERT = "UPSERT";

    public static final String DELETE = "DELETE";

    private Long articleId;

    private String action;

    /**
     * Event time helps consumers ignore very old duplicate messages if needed later.
     */
    private LocalDateTime eventTime;
}
