package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.model.CommentRowDO;

/** CommentMetadataWriter 业务能力边界。 */
public interface CommentMetadataWriter {
    void commit(CommentRowDO row);
}
