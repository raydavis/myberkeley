package edu.berkeley.myberkeley.caldav.report;

import java.util.ArrayList;
import java.util.List;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * <!ELEMENT comp ((allprop | prop*), (allcomp | comp*))>
 * <!ATTLIST comp name CDATA #REQUIRED>
 * <!ELEMENT allcomp EMPTY>
 * <!ELEMENT allprop EMPTY>
 *
 * @author ricky
 */
public class Comp implements XmlSerializable {

    private String name = null;
    private boolean allProp = false;
    private boolean allComp = false;
    private List<Prop> prop = new ArrayList<Prop>();
    private List<Comp> comp = new ArrayList<Comp>();

    public Comp(String name) {
        this.name = name;
    }

    public boolean isAllComp() {
        return allComp;
    }

    public void setAllComp(boolean allComp) {
        this.allComp = allComp;
    }

    public boolean isAllProp() {
        return allProp;
    }

    public void setAllProp(boolean allProp) {
        this.allProp = allProp;
    }

    public List<Comp> getComp() {
        return comp;
    }

    public List<Prop> getProp() {
        return prop;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Element toXml(Document factory) {
        Element e = DomUtil.createElement(factory, CalDavConstants.CALDAV_XML_COMP, CalDavConstants.CALDAV_NAMESPACE);
        e.setAttribute(CalDavConstants.CALDAV_XML_COMP_NAME, name);
        // (allprop | prop*)
        if (allProp) {
            e.appendChild(DomUtil.createElement(factory,
                CalDavConstants.CALDAV_XML_ALL_PROP, CalDavConstants.CALDAV_NAMESPACE));
        } else {
            for (Prop p: prop) {
                e.appendChild(p.toXml(factory));
            }
        }
        // (allcomp | comp*)
        if (allComp) {
            e.appendChild(DomUtil.createElement(factory,
                CalDavConstants.CALDAV_XML_ALL_COMP, CalDavConstants.CALDAV_NAMESPACE));
        } else {
            for (Comp c: comp) {
                e.appendChild(c.toXml(factory));
            }
        }
        // return
        return e;
    }
}
