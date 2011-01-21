package edu.berkeley.myberkeley.notice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

/**
 * Implementation of methods to enable constructing a new nested Dynamic List query into 
 * a "flat" xquery string.  Works in conjunction with MyBerkeleyProfileQueryBuilder
 * @author johnk
 *
 */
public class MyBerkeleyDynamicListQueryParamExtractor implements DynamicListQueryParamExtractor {
    private Node anchorNode;
    private Set<String> nestedQueryParams;
    private Map<String, List<Map<String, Map<String, Set<String>>>>> nestedNodesData; // {standing=[{undergrad={major=[LANDSCAPE ARCH, ARCHITECTURE]}}, {grad={major=[DESIGN]}}]}
    
    /**
     * made private because values in public constructor are required
     */
    private MyBerkeleyDynamicListQueryParamExtractor() {}
    
    /**
     * 
     * @param queryNode - from the persisied dynamic list
     * @param anchorNodeName - the deepest node that will be common to all queries - e.g. context
     * @param nestedQueryParams - the param that has nested values requiring this processing - e.g. [standing]
     * @throws RepositoryException
     */
    public MyBerkeleyDynamicListQueryParamExtractor(Node queryNode, String anchorNodeName, Set<String> nestedQueryParams) throws RepositoryException {
        this.anchorNode = queryNode.getNode(anchorNodeName);
        this.nestedQueryParams = Collections.unmodifiableSet(nestedQueryParams);
        this.nestedNodesData = Collections.unmodifiableMap(extractNestedNodes(queryNode));
    }
    
    /**
     * the depest node that will be common to all queries - e.g. context
     * @return
     */
    @Override
    public Node getAnchorNode() {
        return this.anchorNode;
    }

    /**
     * the values that will require separate queries to be built, one per value, e.g. [grad, undergrad]
     * where one query will have ../standing[@value='grad']../ and ../standing[@value='undergrad']../
     * @return e.g. ['grad', 'undergrad']
     */
    @Override
    public Set<String> getMultipleQueryValues() {
        Set<String> multipleQueryKeys = new HashSet<String>();
        Set<String> topKeys = this.nestedNodesData.keySet();
        Iterator<String> topKeysIter = topKeys.iterator();
        String topKey = topKeysIter.next();
        List<Map<String, Map<String, Set<String>>>> multipleQueryList = this.nestedNodesData.get(topKey);
        for (Iterator<Map<String, Map<String, Set<String>>>> iterator = multipleQueryList.iterator(); iterator.hasNext();) {
            Map<String, Map<String, Set<String>>> queryParamMap = iterator.next();
            Set<String> queryParamKeySet = queryParamMap.keySet();
            multipleQueryKeys.addAll(queryParamKeySet);
        }
        return multipleQueryKeys;
    }

    /**
     * the keys necessary to build the query for nested nodes
     * @param selectorValue e.g. grad or undergrad
     * @return e.g. [standing, undergrad, major]
     */
    @Override
    public String[] getQueryKeyParams(String selectorValue) { // grad or undergrad
        String subSubKey = null;
        Set<String> topKeys = this.nestedNodesData.keySet();  // {standing=[{undergrad={major=[LANDSCAPE ARCH, ARCHITECTURE]}}, {grad={major=[DESIGN]}}]}
        Iterator<String> topKeysIter = topKeys.iterator();
        String topKey = topKeysIter.next();
        List<Map<String, Map<String, Set<String>>>> multipleQueryList = this.nestedNodesData.get(topKey);
        for (Iterator<Map<String, Map<String, Set<String>>>> iterator = multipleQueryList.iterator(); iterator.hasNext();) {
            Map<String, Map<String, Set<String>>> queryParamMap = iterator.next();
            Set<String> queryParamKeySet = queryParamMap.keySet();
            for (Iterator<String> iterator2 = queryParamKeySet.iterator(); iterator2.hasNext();) {
                String queryKey = iterator2.next();
                if (selectorValue.equals(queryKey)) {
                    Map<String, Set<String>> subMap = queryParamMap.get(queryKey);
                    subSubKey = subMap.keySet().iterator().next();
                }
            }
        }
        String[] keys = new String[]{topKey, selectorValue, subSubKey};  // [standing, undergrad, major]
        return keys;
    }

    /**
     * get the multiple values associated with the paramName in the dynamic list
     * @param paramName e.g. major
     * @return e.g. ["ARCHITECTURE", "DESIGN"]
     */
    @Override
    public Set<String> getQueryValues(String paramName) { // e.g. major
        String subSubKey = null;
        Set<String> queryValues = null;
        Set<String> topKeys = this.nestedNodesData.keySet();  // {standing=[{undergrad={major=[LANDSCAPE ARCH, ARCHITECTURE]}}, {grad={major=[DESIGN]}}]}
        Iterator<String> topKeysIter = topKeys.iterator();
        String topKey = topKeysIter.next();
        List<Map<String, Map<String, Set<String>>>> multipleQueryList = this.nestedNodesData.get(topKey);
        for (Iterator<Map<String, Map<String, Set<String>>>> iterator = multipleQueryList.iterator(); iterator.hasNext();) {
            Map<String, Map<String, Set<String>>> queryParamMap = iterator.next();
            Set<String> queryParamKeySet = queryParamMap.keySet();
            for (Iterator<String> iterator2 = queryParamKeySet.iterator(); iterator2.hasNext();) {
                String queryKey = iterator2.next();
                if (paramName.equals(queryKey)) {
                    Map<String, Set<String>> subMap = queryParamMap.get(queryKey);
                    subSubKey = subMap.keySet().iterator().next();
                    queryValues = subMap.get(subSubKey);
                }
            }
        }
        return queryValues; // e.g. ['ARCHTITECTURE', 'DESIGN']
    }
    
    /*
     * method to build the nested hashmap that reflects the nested nodes in the persisted dynamic list to enable 
     * building the query for dynamic list recipients
     * e.g. {standing=[{undergrad={major=[LANDSCAPE ARCH, ARCHITECTURE]}}, {grad={major=[DESIGN]}}]}
     */
    private Map<String, List<Map<String, Map<String, Set<String>>>>> extractNestedNodes(Node queryNode) throws RepositoryException {
        Map<String, Set<String>> subSubNodeData = null;
        Map<String, Map<String, Set<String>>> subNodeData = null;
        List<Map<String, Map<String, Set<String>>>> subNodesData = null;
        Map<String, List<Map<String, Map<String, Set<String>>>>> nestedNodesData = new HashMap<String, List<Map<String,Map<String,Set<String>>>>>();
        Set<String> subSubValues = null;
        Node subArrNode, subSubNode, subSubSubNode = null;
        NodeIterator subnodeIter = queryNode.getNodes();
        while (subnodeIter.hasNext()) {
            Node subnode = subnodeIter.nextNode();
            String subnodeName = subnode.getName();
            if (this.nestedQueryParams.contains(subnodeName)) {
                NodeIterator subnodeArrIter = subnode.getNodes("__array*");
                String subSubNodeName = null;
                subNodesData = new ArrayList<Map<String,Map<String,Set<String>>>>();
                while (subnodeArrIter.hasNext()) {
                    subArrNode = subnodeArrIter.nextNode();
                    NodeIterator subSubNodesIter = subArrNode.getNodes();
                    subNodeData = new HashMap<String, Map<String,Set<String>>>();
                    while (subSubNodesIter.hasNext()) {
                        subSubNodeData = new HashMap<String, Set<String>>();
                        subSubNode = subSubNodesIter.nextNode();
                        subSubNodeName = subSubNode.getName();
                        String subSubSubNodeName = null;
                        NodeIterator subSubValuesIter = subSubNode.getNodes();
                        subSubValues = new HashSet<String>();
                        while (subSubValuesIter.hasNext()) {
                            subSubSubNode = subSubValuesIter.nextNode();
                            subSubSubNodeName = subSubSubNode.getName();
                            PropertyIterator subSubSubPropsIter = subSubSubNode.getProperties("__array*");
                            while (subSubSubPropsIter.hasNext()) {
                                String subSubValue = subSubSubPropsIter.nextProperty().getString();
                                subSubValues.add(subSubValue);
                            }
                            subSubNodeData.put(subSubSubNodeName, subSubValues);  // {major=[LANDSCAPE ARCH, ARCHITECTURE]}
                        }
                        subNodeData.put(subSubNodeName, subSubNodeData);  // {undergrad={major=[LANDSCAPE ARCH, ARCHITECTURE]}}                        
                    }
                    subNodesData.add(subNodeData); // [{undergrad={major=[LANDSCAPE ARCH, ARCHITECTURE]}}, {grad={major=[DESIGN]}}]
                }
                nestedNodesData.put(subnodeName, subNodesData); // {standing=[{undergrad={major=[LANDSCAPE ARCH, ARCHITECTURE]}}, {grad={major=[DESIGN]}}]}
            }
        }
        return nestedNodesData;
    }
}
