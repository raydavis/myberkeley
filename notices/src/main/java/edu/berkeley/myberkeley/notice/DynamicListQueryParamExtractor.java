package edu.berkeley.myberkeley.notice;

import java.util.Set;

import javax.jcr.Node;

public interface DynamicListQueryParamExtractor {
    
    public Node getAnchorNode();
    
    public Set<String> getMultipleQueryKeys();
    
    public String[] getQueryKeyParams(String subKey);
    
    public Set<String> getQueryValues(String paramName);
}
