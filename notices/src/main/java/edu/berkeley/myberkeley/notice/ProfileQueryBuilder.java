package edu.berkeley.myberkeley.notice;

import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * methods to build an xquery/xpath for finding recipients of a message defined by the dynamic list attributes
 * an example full query:
 * "/jcr:root//*[@sling:resourceType='sakai/user-profile']/myberkeley/elements/current[@value='true']/../context[@value='g-ced-students']/..
 * /standing[@value='undergrad']/../major[@value='ARCHITECTURE' or @value='LANDSCAPE ARCH']"
 * methods must be run in the following order:
 * 1) appendRoot()
 * 2) appendAnchorNodeParam()
 * 3) appendNestedNodeParams()
 * 4) toString()
 * works in conjunction with the methods in DynamicListQueryParamExtractor
 * @author johnk
 */
public interface ProfileQueryBuilder {
    
    /**
     * the root string is the beginning of the xpath query string
     * e.g. "/jcr:root//*[@sling:resourceType='sakai/user-profile']/myberkeley/elements/current[@value='true']/.."
     * @param str
     * @return
     */
    public ProfileQueryBuilder appendRoot(String str);
    
    /**
     * the anchor node is the node that defines the point where multiple queries queries must be constructed and run to 
     * find all target recipients, e.g context where query would include "context[@value='g-ced-students']"
     * @param anchorNode
     * @return
     * @throws RepositoryException
     */
    public ProfileQueryBuilder appendAnchorNodeParam(Node anchorNode) throws RepositoryException;
    
    /**
     * node where multiple queries must be constructed and run. e.g. "standing[@value='grad']" vs "standing[@value='undergrad']"
     * @param keys - all the keys for the nested param - e.g. "[standing, undergrad, major]"
     * @param values - the values for the deepest node e.g. "major[@value='ARCHITECTURE' or @value='DESIGN']"
     * @return
     * @throws RepositoryException
     */
    public ProfileQueryBuilder appendNestedNodeParams(String[] keys, Set<String> values) throws RepositoryException;
    
    /**
     * return the fully built query
     * @return
     */
    public String toString();
}
