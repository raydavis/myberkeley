package edu.berkeley.myberkeley.caldav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CalendarResultProcessor {

    private List<CalendarWrapper> results;

    private CalendarSearchCriteria criteria;

    public CalendarResultProcessor(List<CalendarWrapper> results, CalendarSearchCriteria criteria) {
        this.results = results;
        this.criteria = criteria;
    }

    public List<CalendarWrapper> processResults() {
        filter();
        sort();
        return this.results;
    }

    // filter in memory for now because Bedework has bugs searching on categories.
    // TODO do the searching on the Bedework side if bugs get fixed.
    private void filter() {
        List<CalendarWrapper> filteredResults = new ArrayList<CalendarWrapper>(this.results.size());
        for (CalendarWrapper wrapper : this.results) {
            switch (criteria.getMode()) {
                case REQUIRED:
                    if (wrapper.isRequired() && !wrapper.isArchived()) {
                        filteredResults.add(wrapper);
                    }
                    break;
                case UNREQUIRED:
                    if (!wrapper.isRequired() && !wrapper.isArchived()) {
                        filteredResults.add(wrapper);
                    }
                    break;
                case ALL_UNARCHIVED:
                    if (!wrapper.isArchived()) {
                        filteredResults.add(wrapper);
                    }
                    break;
                case ALL_ARCHIVED:
                    if (wrapper.isArchived()) {
                        filteredResults.add(wrapper);
                    }
                    break;
            }
        }
        this.results = filteredResults;
    }

    private void sort() {
        Collections.sort(this.results, criteria.getSort().getComparator());
    }

}
