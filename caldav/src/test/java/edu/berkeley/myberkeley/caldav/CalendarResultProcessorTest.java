package edu.berkeley.myberkeley.caldav;

import net.fortuna.ical4j.model.Calendar;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class CalendarResultProcessorTest extends CalDavTests {

  @Test
  public void defaultCriteria() throws URIException, ParseException, CalDavException {
    CalendarResultProcessor processor = new CalendarResultProcessor(getWrappers(), new CalendarSearchCriteria());
    processor.processResults();
  }

  @Test
  public void nonDefaultCriteria() throws URIException, ParseException, CalDavException {
    CalendarSearchCriteria criteria = new CalendarSearchCriteria();
    new CalendarResultProcessor(getWrappers(), criteria).processResults();

    criteria.setSort(CalendarSearchCriteria.SORT.SUMMARY_ASC);
    new CalendarResultProcessor(getWrappers(), criteria).processResults();
    criteria.setSort(CalendarSearchCriteria.SORT.SUMMARY_DESC);
    new CalendarResultProcessor(getWrappers(), criteria).processResults();
    criteria.setSort(CalendarSearchCriteria.SORT.DATE_ASC);
    new CalendarResultProcessor(getWrappers(), criteria).processResults();
    criteria.setSort(CalendarSearchCriteria.SORT.DATE_DESC);
    new CalendarResultProcessor(getWrappers(), criteria).processResults();
    criteria.setSort(CalendarSearchCriteria.SORT.COMPLETED_ASC);
    new CalendarResultProcessor(getWrappers(), criteria).processResults();
    criteria.setSort(CalendarSearchCriteria.SORT.COMPLETED_DESC);
    new CalendarResultProcessor(getWrappers(), criteria).processResults();
    criteria.setSort(CalendarSearchCriteria.SORT.REQUIRED_ASC);
    new CalendarResultProcessor(getWrappers(), criteria).processResults();
    criteria.setSort(CalendarSearchCriteria.SORT.REQUIRED_DESC);
    new CalendarResultProcessor(getWrappers(), criteria).processResults();
  }

  private List<CalendarWrapper> getWrappers() throws URIException, ParseException, CalDavException {
    List<CalendarWrapper> wrappers = new ArrayList<CalendarWrapper>();
    Calendar c1 = buildVTodo("c1");
    wrappers.add(new CalendarWrapper(c1, new URI("/c1", false), RANDOM_ETAG));
    Calendar c2 = buildVTodo("c2");
    wrappers.add(new CalendarWrapper(c2, new URI("/c2", false), RANDOM_ETAG));
    Calendar c3 = buildVTodo("c3");
    wrappers.add(new CalendarWrapper(c3, new URI("/c3", false), RANDOM_ETAG));
    return wrappers;
  }
}
