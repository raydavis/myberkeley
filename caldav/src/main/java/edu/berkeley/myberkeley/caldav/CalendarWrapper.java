package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.property.DateProperty;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.util.DateUtils;

import java.text.ParseException;

public class CalendarWrapper {

    private Calendar calendar;

    private CalendarURI calendarUri;

    public CalendarWrapper(Calendar calendar, URI uri, String etag) throws CalDavException {
        this.calendar = calendar;
        try {
            this.calendarUri = new CalendarURI(uri, etag);
        } catch (ParseException pe) {
            throw new CalDavException("Exception parsing date '" + etag + "'", pe);
        } catch (URIException uie) {
            throw new CalDavException("Exception parsing uri '" + uri + "'", uie);
        }
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public CalendarURI getUri() {
        return calendarUri;
    }

    public Date getEtag() {
        return this.calendarUri.getEtag();
    }

    @Override
    public String toString() {
        return "CalendarWrapper{" +
                "uri='" + getUri().toString() + '\'' +
                "etag=" + getUri().getEtag() +
                ",calendar=" + calendar +
                '}';
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("URI", getUri().toString());
        json.put("ETAG", DateUtils.iso8601(getEtag()));

        ComponentList components = getCalendar().getComponents();
        for (Object object : components) {
            Component component = (Component) object;
            PropertyList propertyList = component.getProperties();
            for (Object prop : propertyList) {
                Property property = (Property) prop;
                // Check if it is a date
                String value = property.getValue();
                if (property instanceof DateProperty) {
                    DateProperty start = (DateProperty) property;
                    value = DateUtils.iso8601(start.getDate());
                }
                json.put(property.getName(), value);
            }
        }

        return json;
    }
}
