package edu.berkeley.myberkeley.notice;

import java.util.Set;

import javax.jcr.Node;

/**
 * Methods to enable constructing a new nested Dynamic List query into 
 * a "flat" xquery string.  To enable calling methods in ProfileQueryBuilder
 * @author johnk
 *
 */
public interface DynamicListQueryParamExtractor {
    
    /**
     * the deepest node that will be common to all queries
     * @return e.g context
     */
    public Node getAnchorNode();
    
    /**
     * the values that will require separate queries to be built, one per value, e.g. [grad, undergrad]
     * where one query will have ../standing[@value='grad']../ and ../standing[@value='undergrad']../
     * @return e.g. ['grad', 'undergrad']
     */
    public Set<String> getMultipleQueryValues();
    
    /**
     * the keys necessary to build the query for nested nodes
     * @param selectorValue e.g. grad or undergrad
     * @return e.g. [standing, undergrad, major]
     */
    public String[] getQueryKeyParams(String selectorValue);
    
    /**
     * get the multiple values associated with the paramName in the dynamic list
     * @param paramName e.g. major
     * @return e.g. ["ARCHITECTURE", "DESIGN"]
     */
    public Set<String> getQueryValues(String paramName);
}
