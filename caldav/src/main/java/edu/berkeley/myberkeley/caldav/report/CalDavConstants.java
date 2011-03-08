package edu.berkeley.myberkeley.caldav.report;

import org.apache.jackrabbit.webdav.xml.Namespace;

/**
 * Based on code found in "Introducing CalDAV (Part I and II)" at
 * http://blogs.nologin.es/rickyepoderi/index.php?/archives/15-Introducing-CalDAV-Part-II.html
 * @author ricky
 */
public interface CalDavConstants {

    //---< CALDAV Namespace >---------------------------------------------------
    public static final Namespace CALDAV_NAMESPACE =
            Namespace.getNamespace("C", "urn:ietf:params:xml:ns:caldav");

    //---< METHODS >------------------------------------------------------------
    public static final String METHOD_REPORT = "REPORT";

    //---< XML Element, Attribute Names >---------------------------------------
    public static final String CALDAV_XML_CALENDAR_MULTI_GET = "calendar-multiget";
    public static final String CALDAV_XML_CALENDAR_QUERY = "calendar-query";
    public static final String CALDAV_XML_CALENDAR_DATA = "calendar-data";
    public static final String CALDAV_XML_COMP = "comp";
    public static final String CALDAV_XML_ALL_PROP = "allprop";
    public static final String CALDAV_XML_ALL_COMP = "allcomp";
    public static final String CALDAV_XML_PROP = "prop";
    public static final String CALDAV_XML_PROP_NAME = "name";
    public static final String CALDAV_XML_PROP_NOVALUE = "novalue";
    public static final String CALDAV_XML_COMP_NAME = "name";
    public static final String CALDAV_XML_START = "start";
    public static final String CALDAV_XML_END = "end";
    public static final String CALDAV_XML_EXPAND = "expand";
    public static final String CALDAV_XML_LIMIT_RECURRENCE_SET = "limit-recurrence-set";
    public static final String CALDAV_XML_LIMIT_FREEBUSY_SET ="limit-freebusy-set";
    public static final String CALDAV_XML_FILTER = "filter";
    public static final String CALDAV_XML_COMP_FILTER = "comp-filter";
    public static final String CALDAV_XML_COMP_FILTER_NAME = "name";
    public static final String CALDAV_XML_IS_NOT_DEFINED = "is-not-defined";
    public static final String CALDAV_XML_TIME_RANGE = "time-range";
    public static final String CALDAV_XML_PROP_FILTER = "prop-filter";
    public static final String CALDAV_XML_TEXT_MATCH = "text-match";
    public static final String CALDAV_XML_COLLATION = "collation";
    public static final String CALDAV_XML_NEGATE_CONDITION = "negate-condition";
    public static final String CALDAV_XML_PARAM_FILTER = "param-filter";
    public static final String CALDAV_XML_PARAM_FILTER_NAME = "name";
    public static final String CALDAV_XML_TIMEZONE = "timezone";

    //-------------------------------------------------< PropFind Constants >---
    public static final int PROPFIND_NONE = -1;

}
