package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;

import java.util.ArrayList;
import java.util.List;

public class CalendarResultProcessor {

    private List<CalendarWrapper> results;

    private CalendarSearchCriteria criteria;

    public CalendarResultProcessor(List<CalendarWrapper> results, CalendarSearchCriteria criteria) {
        this.results = results;
        this.criteria = criteria;
    }

    public List<CalendarWrapper> processResults() {
        return filterResults();
    }

    // filter in memory for now because Bedework has bugs searching on categories.
    // TODO do the searching on the Bedework side if bugs get fixed.
    private List<CalendarWrapper> filterResults() {
        List<CalendarWrapper> filteredResults = new ArrayList<CalendarWrapper>(this.results.size());
        for (CalendarWrapper wrapper : this.results) {
            Component component = wrapper.getCalendar().getComponent(criteria.getType().toString());
            switch (criteria.getMode()) {
                case REQUIRED:
                    if (isRequired(component) && !isArchived(component)) {
                        filteredResults.add(wrapper);
                    }
                    break;
                case UNREQUIRED:
                    if (!isRequired(component) && !isArchived(component)) {
                        filteredResults.add(wrapper);
                    }
                    break;
                case ALL_UNARCHIVED:
                    if (!isArchived(component)) {
                        filteredResults.add(wrapper);
                    }
                    break;
                case ALL_ARCHIVED:
                    if (isArchived(component)) {
                        filteredResults.add(wrapper);
                    }
                    break;
            }
        }
        return filteredResults;
    }

    private boolean isRequired(Component comp) {
        PropertyList propList = comp.getProperties(Property.CATEGORIES);
        return propList != null && propList.contains(CalDavConnector.MYBERKELEY_REQUIRED);
    }

    private boolean isArchived(Component comp) {
        PropertyList propList = comp.getProperties(Property.CATEGORIES);
        return propList != null && propList.contains(CalDavConnector.MYBERKELEY_ARCHIVED);
    }

}
