package edu.berkeley.myberkeley.notice;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

/**
 * Implementation of methods to build an xquery/xpath for finding recipients of a message defined by the dynamic list attributes
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
public class MyBerkeleyProfileQueryBuilder implements ProfileQueryBuilder {

    private StringBuilder sb;
    
    public MyBerkeleyProfileQueryBuilder() {
        this.sb = new StringBuilder();
    }

    /**
     * the deepest node that will be common to all queries
     * @return e.g context
     */
    @Override
    public ProfileQueryBuilder appendRoot(String str) {
        sb.append(str);
        return this;
    }
    
    /**
     * the anchor node is the node that defines the point where multiple queries queries must be constructed and run to 
     * find all target recipients, e.g context where query would include "context[@value='g-ced-students']"
     * @param anchorNode
     * @return
     * @throws RepositoryException
     */
    @Override
    public ProfileQueryBuilder appendAnchorNodeParam(Node anchorNode) throws RepositoryException {
        addQueryParam(anchorNode);
        sb.append("/..");
        return this;
    }

    /**
     * node where multiple queries must be constructed and run. e.g. "standing[@value='grad']" vs "standing[@value='undergrad']"
     * @param keys - all the keys for the nested param - e.g. "[standing, undergrad, major]"
     * @param values - the values for the deepest node e.g. "major[@value='ARCHITECTURE' or @value='DESIGN']"
     * @return
     * @throws RepositoryException
     */
    @Override
    public ProfileQueryBuilder appendNestedNodeParams(String[] keys, Set<String> values) throws RepositoryException {
        sb.append("/").append(keys[0]).append("[@value='").append(keys[1]).append("']/").append("../").append(keys[2]).append("[");
        for (Iterator<String> valuesIter = values.iterator(); valuesIter.hasNext();) {
            String value = valuesIter.next();
            sb.append("@value='").append(value).append("'");
            if (valuesIter.hasNext()) {
                sb.append(" or ");
            }
        }
        sb.append("]");
        return this;
    }

    /**
     * return the fully built query
     * @return
     */
    public String toString() {
        return sb.toString();
    }
    
    protected void addQueryParam(Node paramNode) throws RepositoryException {
        String paramName = paramNode.getName();
        sb.append("/").append(paramName);
        PropertyIterator paramValuePropsIter = paramNode.getProperties();
        Set<String> paramValues = new HashSet<String>();

        // need to copy Properties into array because jcr:primaryType
        // property breaks isLastValue
        while (paramValuePropsIter.hasNext()) {
            Property prop = paramValuePropsIter.nextProperty();
            if (prop.getName().startsWith("__array")) {
                String paramValue = prop.getValue().getString();
                paramValue = new StringBuffer("'").append(paramValue).append("'").toString();
                paramValues.add(paramValue);
            }
        }
        boolean isFirstValue = true;
        boolean isLastValue = false;
        for (Iterator<String> paramValuesIter = paramValues.iterator(); paramValuesIter.hasNext();) {
            String paramValue = paramValuesIter.next();
            if (!paramValuesIter.hasNext())
                isLastValue = true;
            addParamToQuery(paramName, paramValue, isFirstValue, isLastValue);
            isFirstValue = false;
        }
    }
    
    protected void addParamToQuery(String paramName, String paramValue, boolean isFirstValue, boolean isLastValue) {
        if (isFirstValue)
            sb.append("[");
        sb.append("@value=").append(paramValue);
        if (!isLastValue) {
            sb.append(" or ");
        }
        else {
            sb.append("]");
        }
    }

}
