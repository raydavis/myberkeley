package edu.berkeley.myberkeley.notice;

import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

public interface ProfileQueryBuilder {
    
    public ProfileQueryBuilder appendRoot(String str);
    
    public ProfileQueryBuilder appendAnchorNodeParam(Node anchorNode) throws RepositoryException;
    
    public ProfileQueryBuilder appendNestedNodeParams(String[] keys, Set<String> values) throws RepositoryException;
    
    public String toString();
}
