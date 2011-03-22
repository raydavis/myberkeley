package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.Status;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.sling.commons.json.JSONArray;
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
        json.put("uri", getUri().toString());
        json.put("etag", DateUtils.iso8601(getEtag()));

        JSONObject icalData = new JSONObject();
        JSONArray categoriesArray = new JSONArray();
        boolean isRequired = false;
        boolean isArchived = false;
        boolean isCompleted = false;

        Component component = getCalendar().getComponent(Component.VEVENT);
        if (component == null) {
            component = getCalendar().getComponent(Component.VTODO);
        }
        PropertyList propertyList = component.getProperties();
        for (Object o : propertyList) {
            Property property = (Property) o;
            String value = property.getValue();

            if (property instanceof DateProperty) {
                DateProperty start = (DateProperty) property;
                value = DateUtils.iso8601(start.getDate());
            } else if (property instanceof Status) {
                if (Status.VTODO_COMPLETED.getValue().equals(value)) {
                    isCompleted = true;
                }
            } else if (property instanceof Categories) {
                categoriesArray.put(value);
                if (CalDavConnector.MYBERKELEY_ARCHIVED.getValue().equals(value)) {
                    isArchived = true;
                }
                if (CalDavConnector.MYBERKELEY_REQUIRED.getValue().equals(value)) {
                    isRequired = true;
                }
            }
            icalData.put(property.getName(), value);
        }

        icalData.put(Property.CATEGORIES, categoriesArray);

        json.put("isRequired", isRequired);
        json.put("isArchived", isArchived);
        json.put("isCompleted", isCompleted);
        json.put("icalData", icalData);
        return json;
    }
}
