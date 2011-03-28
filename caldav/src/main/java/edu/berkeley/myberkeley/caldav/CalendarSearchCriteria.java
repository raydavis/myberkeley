package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VToDo;

import java.util.Comparator;

public class CalendarSearchCriteria {

    public enum MODE {
        REQUIRED,
        UNREQUIRED,
        ALL_UNARCHIVED,
        ALL_ARCHIVED
    }

    public enum TYPE {
        VEVENT,
        VTODO
    }

    public enum SORT {

        DATE_ASC(new DateComparator(true)),
        DATE_DESC(new DateComparator(false)),
        SUMMARY_ASC(new SummaryComparator(true)),
        SUMMARY_DESC(new SummaryComparator(false)),
        REQUIRED_ASC(new RequiredComparator(true)),
        REQUIRED_DESC(new RequiredComparator(false)),
        COMPLETED_ASC(new CompleteComparator(true)),
        COMPLETED_DESC(new CompleteComparator(false));

        private final Comparator<CalendarWrapper> comparator;

        SORT(Comparator<CalendarWrapper> comparator) {
            this.comparator = comparator;
        }

        public Comparator<CalendarWrapper> getComparator() {
            return comparator;
        }

        private static class DateComparator implements Comparator<CalendarWrapper> {

            private final boolean ascending;

            private DateComparator(boolean ascending) {
                this.ascending = ascending;
            }

            public int compare(CalendarWrapper a, CalendarWrapper b) {
                int result = 0;
                Component compA = a.getComponent();
                Component compB = b.getComponent();
                if (compA != null && compB != null && compA instanceof VToDo && compB instanceof VToDo) {
                    result = ((VToDo) compA).getDue().getDate().compareTo(((VToDo) compB).getDue().getDate());
                } else if (compA != null && compB != null && compA instanceof VEvent && compB instanceof VEvent) {
                    result = ((VEvent) compA).getStartDate().getDate().compareTo(((VEvent) compB).getStartDate().getDate());
                }
                if (this.ascending) {
                    return result;
                }
                return -1 * result;
            }
        }

        private static class SummaryComparator implements Comparator<CalendarWrapper> {
            private final boolean ascending;

            private SummaryComparator(boolean ascending) {
                this.ascending = ascending;
            }

            public int compare(CalendarWrapper a, CalendarWrapper b) {
                int result = 0;
                Component compA = a.getComponent();
                Component compB = b.getComponent();
                if (compA != null && compB != null) {
                    Property summaryA = compA.getProperty(Property.SUMMARY);
                    Property summaryB = compB.getProperty(Property.SUMMARY);
                    if (summaryA != null && summaryB != null) {
                        result = summaryA.getValue().compareTo(summaryB.getValue());
                    }
                }
                if (this.ascending) {
                    return result;
                }
                return -1 * result;
            }
        }

        private static class RequiredComparator implements Comparator<CalendarWrapper> {
            private final boolean ascending;

            private RequiredComparator(boolean ascending) {
                this.ascending = ascending;
            }

            public int compare(CalendarWrapper a, CalendarWrapper b) {
                int result = 0;
                if (a.isRequired()) {
                    if (!b.isRequired()) {
                        result = 1;
                    }
                } else {
                    if (b.isRequired()) {
                        result = -1;
                    }
                }
                if (this.ascending) {
                    return result;
                }
                return -1 * result;
            }
        }

        private static class CompleteComparator implements Comparator<CalendarWrapper> {
            private final boolean ascending;

            private CompleteComparator(boolean ascending) {
                this.ascending = ascending;
            }

            public int compare(CalendarWrapper a, CalendarWrapper b) {
                int result = 0;
                if (a.isCompleted()) {
                    if (!b.isCompleted()) {
                        result = 1;
                    }
                } else {
                    if (b.isCompleted()) {
                        result = -1;
                    }
                }
                if (this.ascending) {
                    return result;
                }
                return -1 * result;
            }
        }

    }

    private TYPE type;

    private MODE mode;

    private SORT sort = SORT.DATE_ASC;

    private Date start;

    private Date end;

    public CalendarSearchCriteria(TYPE type, Date start, Date end, MODE mode) {
        this.type = type;
        this.start = start;
        this.end = end;
        this.mode = mode;
    }

    public TYPE getType() {
        return type;
    }

    public void setType(TYPE type) {
        this.type = type;
    }

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public Date getEnd() {
        return end;
    }

    public void setEnd(Date end) {
        this.end = end;
    }

    public MODE getMode() {
        return mode;
    }

    public void setMode(MODE mode) {
        this.mode = mode;
    }

    public SORT getSort() {
        return sort;
    }

    public void setSort(SORT sort) {
        this.sort = sort;
    }

    @Override
    public String toString() {
        return "CalendarSearchCriteria{" +
                "type=" + type +
                ", mode=" + mode +
                ", sort=" + sort +
                ", start=" + start +
                ", end=" + end +
                '}';
    }

}

