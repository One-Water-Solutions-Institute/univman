/*
 * $Id: 0.20+2 UniEvent.java 07e744c0985c 2026-02-17 od $
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static univman.UniEvent.Keys.*;

/**
 * Unified Management Event.
 *
 * @author od
 */
public class UniEvent {

  // all potential keys for operation
  public enum Keys {
    op_id,
    op_name,
    crop_id,
    crop_name,
    //    amendment_id,
    //    N_amount,
    //    P_amount,
    //    unit,
    //    op_stir,
    //
    //    add_residue,
    //    begin_growth,
    //    kill_crop,
    //    stir,
    //    resAdded,
    //    fertilizer,
    min_n_lbs_ac,
    org_n_lbs_ac,
    min_p_lbs_ac,
    org_p_lbs_ac,
    fert_name,
    fert_amt_ac,
    nh4_fraction,
    //
    irr,
    irr_amt_year_in,
    irr_amt_in,
    irr_max_no,
    irr_type,
    irr_start_offs,
    irr_stop_offs
    //
//    defaultYield,
    //    yieldUnit,
    //    yield,
    //
  }

// operations
  public record Op(String typename, EnumSet keys) {

  }

  public static Op op_amendment_fertilizer = new Op("op_amendment_fertilizer", EnumSet.of(
      op_id,
      op_name,
      min_n_lbs_ac,
      min_p_lbs_ac,
      org_n_lbs_ac,
      org_p_lbs_ac,
      fert_name,
      fert_amt_ac
  ));

  public static Op op_plant = new Op("op_plant", EnumSet.of(
      op_id,
      op_name,
      crop_id,
      crop_name,
      irr,
      irr_amt_in,
      irr_max_no,
      irr_type
  ));

  public static Op op_kill = new Op("op_kill", EnumSet.of(
      op_id,
      op_name,
      crop_id,
      crop_name
  ));

  public static Op op_harvest = new Op("op_harvest", EnumSet.of(
      op_id,
      op_name,
      crop_id,
      crop_name
  ));

  public static Op op_tillage = new Op("op_tillage", EnumSet.of(
      op_id,
      op_name,
      crop_id,
      crop_name
  ));

  public static Op op_burn = new Op("op_burn", EnumSet.of(
      op_id,
      op_name
  ));

  public static Op op_amendment_pesticide = new Op("op_amendment_pesticide", EnumSet.of(
      op_id,
      op_name,
      crop_id,
      crop_name
  ));

  public static Op op_irrigation = new Op("op_irrigation", EnumSet.of(
      op_id,
      op_name
  ));

  public static Op op_irrigation_start = new Op("op_irrigation_start", EnumSet.of(op_id,
      op_name,
      irr_amt_year_in,
      irr_amt_in
  ));

  public static Op op_irrigation_start_stop = new Op("op_irrigation_start_stop", EnumSet.of(op_id,
      op_name,
      irr_amt_year_in,
      irr_amt_in
  ));

  public static Op op_irrigation_stop = new Op("op_irrigation_stop", EnumSet.of(
      op_id,
      op_name
  ));

  public static Op op_irrigation_allocation = new Op("op_irrigation_allocation", EnumSet.of(
      irr_amt_year_in
  ));

  public static Op op_graze = new Op("op_graze", EnumSet.of(
      op_id,
      op_name
  ));

  // common to all
  String name;
  String typename;
  LocalDate date;
  LocalDate date1;
  Long opId = -1l;

  Op op;

  // properies for each event
  Map<String, Object> evMap;

  static final String KEY_DATE = "date";
  static final String KEY_NAME = "name";
  static final String KEY_TYPE = "type";
  static final String KEY_OPID = "op_id";

  /**
   *
   * @param evMap
   */
  public UniEvent(Map<String, Object> evMap) {
    this.evMap = evMap;

    String d = (String) evMap.get(KEY_DATE);
    name = (String) evMap.get(KEY_NAME);
    typename = (String) evMap.get(KEY_TYPE);
//    opId = (Long) evMap.get(KEY_OPID);

    typename = typename.replace('-', '_').toLowerCase().trim();
    if (typename == null)
      throw new IllegalArgumentException("Missing 'type'");
//    if (opId == null)
//      throw new IllegalArgumentException("Missing 'op_id'");

    if (d == null)
      throw new IllegalArgumentException("Missing 'date'");

    if (d.contains("/")) {
      date = LocalDate.parse(d.substring(0, d.indexOf('/')));
      date1 = LocalDate.parse(d.substring(d.indexOf('/') + 1));

      if (!date1.isAfter(date))
        throw new IllegalArgumentException("Not a valid interval date: " + d);
    } else
      date = LocalDate.parse(d);

    op = enumsetOf(typename);

    if (op_requires(op_id)) {
      opId = (Long) evMap.get(KEY_OPID);
      if (opId == null)
        throw new IllegalArgumentException("Missing 'op_id'");
    }

    if (!typename.equals(op.typename()))
      throw new RuntimeException("typename lookup failed for " + typename);
  }

  static Op enumsetOf(String type) {
    try {
      return (Op) UniEvent.class.getField(type).get(null);
    } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
      throw new RuntimeException("Unknown operation type: " + type);
    }
  }

  @Override
  protected UniEvent clone() {
    return new UniEvent(new HashMap<>(evMap));
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public LocalDate getDate() {
    return date;
  }

  public LocalDate getDate1() {
    return date1;
  }

  public boolean isIntervalEvent() {
    return date1 != null;
  }

  public String getName() {
    return name;
  }

  public String getTypeName() {
    return typename;
  }

  public Op getOp() {
    return op;
  }

  public int getOpId() {
    return opId.intValue();
  }

  public boolean op_requires(Keys key) {
    return op.keys().contains(key);
  }

  public boolean ev_contains(Keys key) {
    return evMap.containsKey(key.name());
  }

  Object get(Keys key) {
    // check for the allowable operation keys
    if (!op_requires(key))
      throw new IllegalArgumentException("Invalid '" + key.name() + " for this op '" + this + ", expected  " + op.keys().toString());
    // check the event input
    if (!ev_contains(key))
      throw new IllegalArgumentException("Missing '" + key + "' in event: " + this);
    return evMap.get(key.name());
  }

  Object get(Keys key, Object def) {
    // check for the allowable operation keys
    if (!op_requires(key))
      throw new IllegalArgumentException("Invalid '" + key.name() + " for this op '" + this + ", expected  " + op.keys().toString());
    // check the event input
    if (!ev_contains(key))
      return def;
    return evMap.get(key.name());
  }

  public boolean getBool(Keys key) {
    return (Boolean) get(key);
  }

  public boolean getBool(Keys key, boolean def) {
    return (Boolean) get(key, def);
  }

  public String getString(Keys key) {
    return (String) get(key);
  }

  public String getString(Keys key, String def) {
    return (String) get(key, def);
  }

  public int getInt(Keys key) {
    return ((Number) get(key)).intValue();
  }

  public int getInt(Keys key, int def) {
    return ((Number) get(key, def)).intValue();
  }

  public double getDouble(Keys key) {
    return ((Number) get(key)).doubleValue();
  }

  public double getDouble(Keys key, double def) {
    return ((Number) get(key, def)).doubleValue();
  }

  @Override
  public String toString() {
    if (isIntervalEvent())
      return String.format("%10s/%10s: %-25s %d %-50s", getDate(), getDate1(), getTypeName(), getOpId(), getName());
    else
      return String.format("%10s:            %-25s %d %-50s", getDate(), getTypeName(), getOpId(), getName());
  }

  /////////////////////// static utils

  public static String toString(List<UniEvent> ev) {
    StringBuffer b = new StringBuffer();
    for (UniEvent e : ev) {
      b.append("   ").append(e.toString()).append("\n");
    }
    return b.toString();
  }

  public static List<UniEvent> filterByOps(List<UniEvent> uel, Op... op) {
    List<Op> lop = Arrays.asList(op);
    List<UniEvent> fevents = uel.stream()
        .filter(e -> lop.contains(e.getOp()))
        .collect(Collectors.toList());
    return fevents;
  }

  public static List<UniEvent> filterByYearOps(List<UniEvent> uel, int year, Op... op) {
    List<Op> lop = Arrays.asList(op);
    List<UniEvent> fevents = uel.stream()
        .filter(e -> lop.contains(e.getOp()) && e.getDate().getYear() == year)
        .collect(Collectors.toList());
    return fevents;
  }

  public static List<UniEvent> filterByOffsOps(List<UniEvent> uel, int offs, Op... op) {
    List<UniEvent> l = uel.subList(offs, uel.size());
    return filterByOps(l, op);
  }

  public static List<UniEvent> filterByOffsYearOps(List<UniEvent> uel, int offs, int year, Op... op) {
    List<UniEvent> l = uel.subList(offs, uel.size());
    return filterByYearOps(l, year, op);
  }

  public static List<UniEvent> filterByKeyOps(List<UniEvent> uel, Keys key, Op... op) {
    List<Op> lop = Arrays.asList(op);
    List<UniEvent> fevents = uel.stream()
        .filter(e -> lop.contains(e.getOp()) && e.ev_contains(key))
        .collect(Collectors.toList());
    return fevents;
  }

  public static List<UniEvent> filterByBoolKeyOps(List<UniEvent> uel, Keys key, Op... op) {
    List<Op> lop = Arrays.asList(op);
    List<UniEvent> fevents = uel.stream()
        .filter(e -> lop.contains(e.getOp()) && e.ev_contains(key) && e.getBool(key))
        .collect(Collectors.toList());
    return fevents;
  }

  /*
    get the allocation as map: year->alloc
   */
  public static Map<Integer, Double> getIrrCrops(List<UniEvent> uel) {
    Map<Integer, Double> m = new HashMap<>();
    List<UniEvent> ia = filterByBoolKeyOps(uel, irr, op_plant);
    for (UniEvent ue : ia) {
      m.put(ue.getDate().getYear(), ue.getDouble(irr_amt_year_in));
    }
    return m;
  }

  /*
    get the allocation as map: year->alloc
   */
  public static Map<Integer, Double> getIrrAllocations(List<UniEvent> uel) {
    Map<Integer, Double> m = new HashMap<>();
    List<UniEvent> ia = filterByOps(uel, op_irrigation_allocation);
    for (UniEvent ue : ia) {
      m.put(ue.getDate().getYear(), ue.getDouble(irr_amt_year_in));
    }
    return m;
  }

  public static List<UniEvent> filterByKeyYearOps(List<UniEvent> uel, Keys key, int year, Op... op) {
    List<Op> lop = Arrays.asList(op);
    List<UniEvent> fevents = uel.stream()
        .filter(e -> lop.contains(e.getOp()) && e.ev_contains(key) && e.getDate().getYear() == year)
        .collect(Collectors.toList());
    return fevents;
  }

  public static void sortByDate(List<UniEvent> uel) {
    uel.sort((ue1, ue2) -> ue1.getDate().compareTo(ue2.getDate()));
  }

  public static LocalDate getLastOp(List<UniEvent> uel, Op op) {
    List<UniEvent> ul = filterByOps(uel, op);
    return (ul.size() == 0) ? null : ul.get(ul.size() - 1).getDate();
  }


  public static void checkEventDates(List<UniEvent> uel, LocalDate start, LocalDate end) {
    LocalDate prevDate = uel.get(0).getDate();
    if (prevDate.isBefore(start))
      throw new RuntimeException("Invalid start in sequence for first event: " + uel.get(0));

    for (UniEvent ue : uel) {
      if (ue.getDate().isBefore(prevDate))
        throw new RuntimeException("Invalid date in sequence for: " + ue.toString());
      prevDate = ue.getDate();
    }

    LocalDate lastDate = uel.get(uel.size() - 1).getDate();
    if (lastDate.isAfter(end))
      throw new RuntimeException("Invalid end in sequence for last event: " + uel.get(uel.size() - 1));
  }
}
