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
    
    public static final String PROP_SAKAI_SENDDATE = "sakai:sendDate";

    public static final String PROP_SAKAI_EVENTDATE = "sakai:eventDate";

    public static final String BOX_ARCHIVE = "archive";

    public static final String BOX_DRAFTS = "drafts";

    public static final String BOX_QUEUE = "queue";
    
    public static final String GROUP_CED_ADVISORS = "g-ced-advisors";
    
    public static final String GROUP_CED_STUDENTS = "g-ced-students";
    
    public static final String STATE_SEND_FAILED = "failed";
    
    public static final String DYNAMIC_LISTS_ROOT_NODE_NAME = "dynamic_lists";
    
    public static final String DYNAMIC_LISTS_DATA_NODE_NAME = "lists";
    
    public static final String DYNAMIC_LISTS_LIST_ID = "sakai:listId";
    
    public static final String DYNAMIC_LISTS_QUERY = "query";
    
    public static final String DYNAMIC_LISTS_PREFIX = "dl-";

    public static final String NODE_PATH_PROPERTY = "nodePath";
    
    public static final String QUEUE_NAME = "org/sakaiproject/nakamura/notice/email/outgoing";

    public static final String PROP_SAKAI_REQUIRED = "sakai:required";
}
