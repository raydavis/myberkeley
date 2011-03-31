package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Version;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.util.DateUtils;
import org.sakaiproject.nakamura.util.ISO8601Date;

import java.text.ParseException;

public class CalendarWrapper {

    public enum JSON_PROPERTY_NAMES {
        uri,
        etag,
        isRequired,
        isCompleted,
        isArchived,
        icalData
    }

    public enum ICAL_DATA_PROPERTY_NAMES {
        DTSTAMP,
        DTSTART,
        DUE,
        SUMMARY
    }

    private Calendar calendar;

    private CalendarURI calendarUri;

    private Component component;

    public CalendarWrapper(Calendar calendar, URI uri, String etag) throws CalDavException {
        this.calendar = calendar;
        this.component = calendar.getComponent(Component.VEVENT);
        if (this.component == null) {
            this.component = calendar.getComponent(Component.VTODO);
        }
        if (this.component == null) {
            throw new CalDavException("Unsupported ical data - passed calendar had no VTODO or VEVENT", null);
        }
        try {
            this.calendarUri = new CalendarURI(uri, etag);
        } catch (ParseException pe) {
            throw new CalDavException("Exception parsing date '" + etag + "'", pe);
        } catch (URIException uie) {
            throw new CalDavException("Exception parsing uri '" + uri + "'", uie);
        }
    }

    public Calendar getCalendar() {
        return this.calendar;
    }

    public Component getComponent() {
        return this.component;
    }

    public CalendarURI getUri() {
        return this.calendarUri;
    }

    public Date getEtag() {
        return this.calendarUri.getEtag();
    }

    public boolean isCompleted() {
        PropertyList propList = this.component.getProperties(Property.STATUS);
        return propList != null && propList.contains(Status.VTODO_COMPLETED);
    }

    public boolean isRequired() {
        PropertyList propList = this.component.getProperties(Property.CATEGORIES);
        return propList != null && propList.contains(CalDavConnector.MYBERKELEY_REQUIRED);
    }

    public boolean isArchived() {
        PropertyList propList = this.component.getProperties(Property.CATEGORIES);
        return propList != null && propList.contains(CalDavConnector.MYBERKELEY_ARCHIVED);
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
        json.put(JSON_PROPERTY_NAMES.uri.toString(), getUri().toString());
        json.put(JSON_PROPERTY_NAMES.etag.toString(), DateUtils.iso8601(getEtag()));

        JSONObject icalData = new JSONObject();
        JSONArray categoriesArray = new JSONArray();
        boolean isRequired = false;
        boolean isArchived = false;
        boolean isCompleted = false;

        PropertyList propertyList = this.component.getProperties();
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

        json.put(JSON_PROPERTY_NAMES.isRequired.toString(), isRequired);
        json.put(JSON_PROPERTY_NAMES.isArchived.toString(), isArchived);
        json.put(JSON_PROPERTY_NAMES.isCompleted.toString(), isCompleted);
        json.put(JSON_PROPERTY_NAMES.icalData.toString(), icalData);
        return json;
    }

    public static CalendarWrapper fromJSON(JSONObject json) throws CalDavException {
        try {
            String uri = json.getString(JSON_PROPERTY_NAMES.uri.toString());
            String etag = json.getString(JSON_PROPERTY_NAMES.etag.toString());

            // build the ical Calendar object itself
            CalendarBuilder builder = new CalendarBuilder();
            Calendar calendar = new Calendar();
            calendar.getProperties().add(new ProdId("-//Ben Fortuna//iCal4j 1.0//EN"));
            calendar.getProperties().add(Version.VERSION_2_0);
            calendar.getProperties().add(CalScale.GREGORIAN);
            TimeZoneRegistry registry = builder.getRegistry();
            VTimeZone tz = registry.getTimeZone("America/Los_Angeles").getVTimeZone();
            calendar.getComponents().add(tz);

            JSONObject icalData = json.getJSONObject(JSON_PROPERTY_NAMES.icalData.toString());
            if (icalData == null) {
                throw new CalDavException("No valid icalData found in JSON", null);
            }

            // build the vtodo
            // TODO handle vevents as well

            DateTime startDate = new DateTime(new ISO8601Date(icalData.getString(ICAL_DATA_PROPERTY_NAMES.DTSTART.toString())).getTime());
            DateTime due = new DateTime(new ISO8601Date(icalData.getString(ICAL_DATA_PROPERTY_NAMES.DUE.toString())).getTime());

            String summary = icalData.getString(ICAL_DATA_PROPERTY_NAMES.SUMMARY.toString());
            VToDo vtodo = new VToDo(startDate, due, summary);

            try {
                String stampString = icalData.getString(ICAL_DATA_PROPERTY_NAMES.DTSTAMP.toString());
                if (stampString != null) {
                    DateTime stamp = new DateTime(new ISO8601Date(stampString).getTime());
                    vtodo.getProperties().add(new DtStamp(stamp));
                }
            } catch (JSONException ignored) {
                // no DTSTAMP, that's ok, the default will work
            }

            calendar.getComponents().add(vtodo);

            return new CalendarWrapper(calendar, new URI(uri, false), etag);

        } catch (JSONException je) {
            throw new CalDavException("Invalid json data passed", je);
        } catch (URIException uie) {
            throw new CalDavException("Invalid URI passed", uie);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CalendarWrapper that = (CalendarWrapper) o;

        if (calendar != null ? !calendar.equals(that.calendar) : that.calendar != null) return false;
        if (calendarUri != null ? !calendarUri.equals(that.calendarUri) : that.calendarUri != null) return false;
        if (component != null ? !component.equals(that.component) : that.component != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = calendar != null ? calendar.hashCode() : 0;
        result = 31 * result + (calendarUri != null ? calendarUri.hashCode() : 0);
        result = 31 * result + (component != null ? component.hashCode() : 0);
        return result;
    }
}
