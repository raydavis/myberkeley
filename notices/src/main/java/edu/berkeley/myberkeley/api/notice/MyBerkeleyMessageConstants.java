package edu.berkeley.myberkeley.api.notice;

import org.sakaiproject.nakamura.api.message.MessageConstants;

public interface MyBerkeleyMessageConstants extends MessageConstants {
    /**
     * Identifier for an a notice
     */
    public static final String TYPE_NOTICE = "notice";

    public static final String NOTICE_TRANSPORT = "notice";

    public static final String PROP_SAKAI_CATEGORY = "sakai:category";

    public static final String SAKAI_CATEGORY_MESSAGE = "message";

    public static final String SAKAI_CATEGORY_REMINDER = "reminder";

    public static final String PROP_SAKAI_TASKSTATE = "sakai:taskState";

    public static final String PROP_SAKAI_DUEDATE = "sakai:dueDate";

    public static final String PROP_SAKAI_EVENTDATE = "sakai:eventDate";

    public static final String BOX_ARCHIVE = "sakai:archive";

    public static final String BOX_DRAFTS = "sakai:drafts";

    public static final String BOX_QUEUE = "sakai:queue";

}
