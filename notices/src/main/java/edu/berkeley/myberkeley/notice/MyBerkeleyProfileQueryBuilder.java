package edu.berkeley.myberkeley.notice;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

public class MyBerkeleyProfileQueryBuilder implements ProfileQueryBuilder {

    private StringBuilder sb;
    
    public MyBerkeleyProfileQueryBuilder() {
        this.sb = new StringBuilder();
    }

    @Override
    public ProfileQueryBuilder appendRoot(String str) {
        sb.append(str);
        return this;
    }
    
    @Override
    public ProfileQueryBuilder appendAnchorNodeParam(Node anchorNode) throws RepositoryException {
        addQueryParam(anchorNode);
        sb.append("/..");
        return this;
    }

    @Override
    public ProfileQueryBuilder appendNestedNodeParams(String[] keys, Set<String> values) throws RepositoryException {
        sb.append("/").append(keys[0]).append("[@value='").append(keys[1]).append("']/").append("../").append(keys[2]).append("[");
        for (Iterator<String> valuesIter = values.iterator(); valuesIter.hasNext();) {
            String major = valuesIter.next();
            sb.append("@value='").append(major).append("'");
            if (valuesIter.hasNext()) {
                sb.append(" or ");
            }
        }
        sb.append("]");
        return this;
    }

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
