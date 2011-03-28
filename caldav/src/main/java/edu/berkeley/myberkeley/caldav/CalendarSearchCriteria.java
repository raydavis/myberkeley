package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Date;

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

        DATE_ASC(new Comparator<CalendarWrapper>() {
            public int compare(CalendarWrapper calendarWrapper, CalendarWrapper calendarWrapper1) {
                return 0;  //To change body of implemented methods use File | Settings | File Templates.
            }
        }),
        DATE_DESC(new Comparator<CalendarWrapper>() {
            public int compare(CalendarWrapper calendarWrapper, CalendarWrapper calendarWrapper1) {
                return 0;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });

        private final Comparator<CalendarWrapper> comparator;

        SORT(Comparator<CalendarWrapper> comparator) {
            this.comparator = comparator;
        }

        public Comparator<CalendarWrapper> getComparator() {
            return comparator;
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
                ", start=" + start +
                ", end=" + end +
                '}';
    }

}

