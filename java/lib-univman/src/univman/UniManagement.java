/*
 * $Id: 0.20+2 UniManagement.java 07e744c0985c 2026-02-17 od $
 *
 * This file is part of the Cloud Services Integration Platform (CSIP),
 * a Model-as-a-Service framework, API, and application suite.
 *
 * 2012-2026, OMSLab, Colorado State University.
 *
 * OMSLab licenses this file to you under the MIT license.
 * See the LICENSE file in the project root for more information.
 */
package univman;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.simple.parser.JSONParser;

/**
 * Universal Management
 *
 * @author od
 */
public class UniManagement {

  static final String KEY_EVENTS = "events";
  static final String KEY_NAME = "name";
  static final String KEY_UNTIL = "until_end_of";

  String name;
  int year;
  List<UniEvent> events;

  public int getYear() {
    return year;
  }

  public void setYear(int year) {
    this.year = year;
  }

  /**
   *
   * @param file
   * @param idx the n-th management to read
   * @return
   * @throws Exception
   */
  public static UniManagement from(File file, int idx) throws Exception {
    String r = Files.readString(file.toPath(), StandardCharsets.UTF_8);
    return from(r, idx);
  }

  public static List<UniManagement> from(File file) throws Exception {
    String r = Files.readString(file.toPath(), StandardCharsets.UTF_8);
    return from(r);
  }

  /**
   *
   * @param s
   * @param idx
   * @return
   * @throws Exception
   */
  public static UniManagement from(String s, int idx) throws Exception {
    List<UniManagement> um = from(s);
    return um.get(idx);
  }

  /**
   *
   * @param s
   * @param idx
   * @return
   * @throws Exception
   */
  public static List<UniManagement> from(String s) throws Exception {
    Object json = new JSONParser().parse(s);
    List<Map> mans = null;

    if (json instanceof Map<?, ?> m) {
      // single management
      mans = List.of(m);
      // multiple managements
    } else if (json instanceof List l) {
      mans = l;
    } else
      throw new RuntimeException("Parse Error.");

    List<UniManagement> um = new ArrayList<>();

    int i = 1;
    for (Map<String, Object> man : mans) {
      String name = (String) man.getOrDefault(KEY_NAME, "Man #" + i++);
      Long until = (Long) man.getOrDefault(KEY_UNTIL, -1l);

      List<Map> events = (List<Map>) man.get(KEY_EVENTS);
      if (events == null || events.size() == 0)
        throw new IllegalArgumentException("no event(s) found.");

      List<UniEvent> uevents = new ArrayList<>();
      for (Map<String, Object> e : events) {
        uevents.add(new UniEvent(e));
      }
      um.add(new UniManagement(name, until.intValue(), uevents));
    }
    return um;
  }

  /**
   *
   * @param name
   * @param year
   * @param events
   */
  UniManagement(String name, int year, List<UniEvent> events) {
    if (year != -1 && year < 1900)
      throw new IllegalArgumentException("invalid year: " + year);

    this.name = name;
    this.events = events;
    this.year = year;

    UniEvent.sortByDate(events);
  }

  public String getName() {
    return name;
  }

  public List<UniEvent> getEvents() {
    return events;
  }

  public List<UniEvent> getClonedEvents() {
    List<UniEvent> cl = new ArrayList<>();
    for (UniEvent ue : events) {
      cl.add(ue.clone());
    }
    return cl;
  }

  /**
   * Create Event sequence
   *
   * @param ums Universal Managements
   * @param start inclusive
   * @param end inclusive
   * @return
   */
  public static List<UniEvent> createEventSequence(List<UniManagement> ums, String start, String end) {
    return createEventSequence(ums, LocalDate.parse(start), LocalDate.parse(end));
  }

  /**
   * Create Event sequence
   *
   * @param ums Universal Managements
   * @param start inclusive
   * @param end inclusive
   * @return
   */
  public static List<UniEvent> createEventSequence(List<UniManagement> ums, LocalDate start, LocalDate end) {
    if (!end.isAfter(start))
      throw new IllegalArgumentException("'end' is not after 'start' date.");

    int startYear = start.getYear();
    int endYear = end.getYear();

    int currYear = startYear;
    ums.get(ums.size() - 1).setYear(endYear);

    // check that the years make sense.
    int checkYear = startYear;
    for (UniManagement um : ums) {
      if (um.getYear() == -1)
        throw new RuntimeException("No year (until_end_of) set for " + um.getName());
      if (um.getYear() < checkYear || um.getYear() > endYear)
        throw new RuntimeException("Invalid year " + um.getYear() + " for " + um.getName());
      checkYear = um.getYear();
    }

    List<UniEvent> sequence = new ArrayList<>();
    for (UniManagement um : ums) {
      // check first event 
      UniEvent firstUE = um.getEvents().get(0);
      int ueYear = firstUE.getDate().getYear();
      // absolute year assumed if ueYesr is greater than 100
      if (ueYear > 100) {
        // absolute, just take dates as is.
        int prevueYear = ueYear;
        for (UniEvent ue : um.getClonedEvents()) {
          LocalDate date = ue.getDate();
          ueYear = date.getYear();
          if (ueYear < 100)
            throw new RuntimeException("invalid absolute year: " + ueYear);
          if (ueYear < prevueYear)
            throw new RuntimeException("invalid event year in sequence: " + ue.toString());

          prevueYear = ueYear;
          sequence.add(ue);
        }
        currYear = ueYear;
      } else {
        // relative
        if (ueYear > 0)
          throw new RuntimeException("invalid first event year in sequence, it should start with '0': " + firstUE.toString());

        while (currYear <= um.getYear()) {
          ueYear = 0;
          for (UniEvent ue : um.getClonedEvents()) {
            LocalDate date = ue.getDate();

            if (date.getYear() > 100)
              throw new RuntimeException("invalid offset year (must be something like '0000-05-01'): " + date.getYear());
            if (currYear + date.getYear() > um.getYear())
              break;

            ueYear = date.getYear();
            LocalDate adj = LocalDate.of(ueYear + currYear, date.getMonth(), date.getDayOfMonth());
            ue.setDate(adj);
            sequence.add(ue);
          }
          currYear += ueYear + 1;
        }
      }
    }

    // check if all generated dates are correct
    UniEvent.checkEventDates(sequence, start, end);

    return sequence;
  }

  int getSequenceLength() {
    LocalDate last = events.get(events.size() - 1).getDate();
    return last.getYear();
  }

  @Override
  public String toString() {
    StringBuffer b = new StringBuffer();
    b.append(getName()).append(" ").append(" --> \n");
    for (UniEvent e : events) {
      b.append("   ").append(e.toString()).append("\n");
    }
    return b.toString();
  }
}
