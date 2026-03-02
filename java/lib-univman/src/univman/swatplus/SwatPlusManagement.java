/*
 * $Id$
 *
 * This file is part of the Cloud Services Integration Platform (CSIP),
 * a Model-as-a-Service framework, API, and application suite.
 *
 * 2012-2026, OMSLab, Colorado State University.
 *
 * OMSLab licenses this file to you under the MIT license.
 * See the LICENSE file in the project root for more information.
 */
package univman.swatplus;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import univman.IrrPeriod;
import univman.ManagementWriter;
import univman.UniEvent;
import static univman.UniEvent.Keys.*;
import static univman.UniEvent.*;

/**
 * Management and Operations handling for SWAT+
 *
 * @author od
 */
public class SwatPlusManagement implements ManagementWriter {

//  static final List<String> perennials = List.of("alfa", "almd", "appl",
//      "aspn", "aspr", "bana", "barn", "bbls",
//      "berm", "blue", "blug", "brom", "bros",
//      "bsvg", "cabg", "cang", "cash", "cedr",
//      "celr", "cher", "clva", "clvs", "cngr",
//      "coco", "coct", "coff", "crdy", "crir",
//      "crwo", "cwgr", "egam", "euca", "fesc",
//      "fodb", "fodn", "foeb", "foen", "fomi",
//      "frsd", "frsd_suhf", "frsd_sums", "frsd_sust",
//      "frsd_tecf", "frsd_tems", "frsd_teof", "frsd_test",
//      "frse", "frse_sudrf", "frse_suds", "frse_suhf",
//      "frse_sums", "frse_sust", "frse_tecf", "frse_teds",
//      "frse_tems", "frse_teof", "frse_test", "frst",
//      "frst_suhf", "frst_sums", "frst_sust", "frst_tecf",
//      "frst_tems", "frst_teof", "frst_test", "grap",
//      "grar", "gras", "hay", "indn", "jhgr",
//      "lbls", "ldgp", "mapl", "mesq", "migs",
//      "oak", "oilp", "oliv", "oran", "orcd",
//      "papa", "past", "pear", "pepp", "pine",
//      "pinp", "plan", "popl", "rasp", "rngb",
//      "rngb_sudrf", "rngb_suds", "rngb_suhf", "rngb_sums",
//      "rngb_sust", "rngb_tecf", "rngb_teds", "rngb_tems",
//      "rngb_teof", "rngb_test", "rnge", "rnge_sudrf",
//      "rnge_suds", "rnge_suhf", "rnge_sums", "rnge_sust",
//      "rnge_tecf", "rnge_teds", "rnge_tems", "rnge_teof",
//      "rnge_test", "rubr", "ryea", "ryer", "sava",
//      "sept", "shrb", "side", "spas", "strw",
//      "sugc", "swch", "swgr", "swrn", "timo",
//      "tubg", "tuhb", "tumi", "tuwo", "urbn_cool",
//      "urbn_warm", "waln", "wehb", "wetf", "wetl",
//      "wetm", "wetn", "wetw", "wewo", "will",
//      "wpas", "wspr", "wwgr");
  //  static boolean isPerennial(String crop) {
//    return perennials.contains(crop);
//  }
  static final double LBAC_TO_KGHA = 1.1208511557167;
  static final double IN_TO_MM = 25.4;

  /**
   * SWAT+ operation types
   */
  enum SpOp {
    fert,
    till,
    plnt,
    harv,
    kill,
    skip,
    burn,
    pest,
    graz,
    irrm
  }

  /**
   * Swat+ management event
   */
  public record SpEvent(SpOp op, LocalDate date, String op_d1, String op_d2, double op_d3) {

  }

  @FunctionalInterface
  public interface TriConsumer<T, U, V> {

    void accept(T k, U v, V s);

    default TriConsumer<T, U, V> andThen(final TriConsumer<? super T, ? super U, ? super V> after) {
      Objects.requireNonNull(after);
      return (t, u, v) -> {
        accept(t, u, v);
        after.accept(t, u, v);
      };
    }
  }

  static final TriConsumer<UniEvent, SwatPlusDomainDB, SwatPlusManagement> PASS = (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
    // do nothing
  };

  String name;

  List<SpEvent> events = new ArrayList<>();
  List<String> autoOps = new ArrayList<>();

  Map<Integer, Double> irrAlloc = new HashMap<>();
  List<IrrPeriod> irrPeriods = new ArrayList<>();
  IrrPeriod currIrr;

  String dtlStr; // the dtl string
  String manStr; // man schedule 
  int numOps; // number of operations in the man schedule file

  LocalDate lastTillOp;

  private void setLastTillOp(LocalDate lastTillOp) {
    this.lastTillOp = lastTillOp;
  }

  public LocalDate getLastTillOp() {
    return lastTillOp;
  }

  void addIrrAlloc(int year, double amt) {
    Double prev = irrAlloc.put(year, amt);
    if (prev != null)
      throw new RuntimeException("irrigation allocation provided twice for year " + year);
  }

  boolean hasIrrCrops() {
    return irrPeriods.size() > 0;
  }

  void setName(String name) {
    this.name = name;
  }

  void addAutoOperation(String op) {
    autoOps.add(op);
  }

  void addEvent(SpEvent event) {
    events.add(event);
  }

  void addEvents(List<SpEvent> events) {
    for (SpEvent e : events) {
      addEvent(e);
    }
  }

  @Override
  public String toString() {
    StringBuffer b = new StringBuffer();
    for (SpEvent e : events) {
      b.append(e).append('\n');
    }
    return b.toString();
  }

  //// Plants.ini file
  
  /**
   * Get all the crops (plant operation)
   *
   * @return
   */
  private Set<String> getCrops() {
    return events.stream()
        .filter(e -> e.op == SpOp.plnt || e.op == SpOp.harv || e.op == SpOp.kill)
        .map(SpEvent::op_d1)
        .collect(Collectors.toSet());
  }

  private static void writePlantIni(File file, Set<String> crops, String scenario, String pcommName) throws IOException {
    String h1 = "pcom_name             plt_cnt     rot_yr_ini          plt_name     lc_status      lai_init       bm_init      phu_init      plnt_pop      yrs_init      rsd_init";
    String h2 = "%-13s       %9d              1";
    String ro = "                                                      %-12s         n           0.0           0.0           0.0           0.0           0.0        1000.0";

    try (PrintWriter pw = new PrintWriter(file)) {
      pw.println(file.getName() + ": " + scenario + " (generated " + new Date() + ")");
      pw.println(h1);
      pw.println(String.format(h2, pcommName, crops.size(), 1));
      for (String crop : crops) {
        pw.println(String.format(ro, crop));
      }
    }
  }

  //// management.sch file

  static final String fmt0 = "%56s %10d %10d %10d %12s %12s %12.0f";
  static final String fmt3 = "%56s %10d %10d %10d %12s %12s %12.3f";

  private static void writeOp(PrintWriter pw, SpOp op, int mo, int day, String opData1, String opData2, double opData3) {
    String f = String.format((opData3 > 0) ? fmt3 : fmt0,
        op, mo, day, 0, opData1, opData2, opData3);
    pw.println(f);
  }

  private static void writeSkipOp(PrintWriter pw, int yr) {
    String f = String.format(fmt0, SpOp.skip, 0, 0, 0, null, null, 0.0);
    pw.println(f + "  # " + (yr + 1));
  }

  private static void writeOp(PrintWriter pw, SpOp op, LocalDate date, String opData1, String opData2, double opData3) {
    writeOp(pw, op, date.getMonthValue(), date.getDayOfMonth(), opData1, opData2, opData3);
  }

  private static void writeOp(PrintWriter pw, SpEvent ev) {
    writeOp(pw, ev.op(), ev.date(), ev.op_d1(), ev.op_d2(), ev.op_d3());
  }

  private void processManagementSch() throws IOException {
    numOps = events.size(); // start with the size, then add skip ops
    StringWriter evStr = new StringWriter();
    try (PrintWriter pw = new PrintWriter(evStr)) {
      // set year to first year
      int year = events.get(0).date().getYear();
      for (SpEvent ev : events) {
        if (ev.date().getYear() > year) {
          // year change, write skip operation(s)
          int nextEvYear = ev.date().getYear();
          // this could be multiple years.
          for (int yr = year; yr < nextEvYear; yr++) {
            writeSkipOp(pw, yr);
            numOps++;
          }
          year = nextEvYear;
        }
        writeOp(pw, ev);
      }
      writeSkipOp(pw, 0); // last entry must be skipop
      numOps++;
    }
    manStr = evStr.toString();
  }

  private void writeManagementSch(File file, String scenario) throws IOException {
    try (PrintWriter pw = new PrintWriter(file)) {
      pw.println(file.getName() + ": " + scenario + " (generated " + new Date() + ")");
      pw.println("                          NAME NUMB_OPS NUMB_AUTO OP_TYP        MON        DAY     HU_SCH     OP_DATA1     OP_DATA2     OP_DATA3");
      pw.println(String.format("%30s %8d %9d", name, numOps, autoOps.size()));
      for (String ao : autoOps) {
        pw.println(String.format("%48s", ao));
      }
      pw.print(manStr);
    }
  }

  ///////////////////////////////  lum.dtl
  
  static String getCond(IrrPeriod p, int alt_idx, int total_alt, int year) {
    int startDay = 0;
    int stopDay = 0;

    if (p.isSingleYearSummerCrop(year)) {
      startDay = p.start().getDayOfYear();
      stopDay = p.stop().getDayOfYear();
    } else if (p.isFallWinterCrop(year)) {
      startDay = p.start().getDayOfYear();
      stopDay = 365;
    } else if (p.isSpringWinterCrop(year)) {
      startDay = 1;
      stopDay = p.stop().getDayOfYear();
    } else if (p.isFullYearCrop(year)) {
      startDay = 1;
      stopDay = 365;
    }

    LocalDate commentStartDate = LocalDate.ofYearDay(year, startDay);
    LocalDate commentStopDate = LocalDate.ofYearDay(year, stopDay);

    return String.format(""
        + " plant_name_gro   hru          0                 null                -      %6s                 %s\n"
        + " jday             hru          0                 null                -      %6d                 %s  # %s\n"
        + " jday             hru          0                 null                -      %6d                 %s  # %s\n",
        p.crop(),
        genCondAlt(alt_idx, total_alt, '='),
        startDay,
        genCondAlt(alt_idx, total_alt, '>'),
        commentStartDate.toString(),
        stopDay,
        genCondAlt(alt_idx, total_alt, '<'),
        commentStopDate.toString()
    );
  }

  static String getAction(IrrPeriod ip, int alt_idx, int total_alt) {
    return String.format(
        " irrigate         hru          0     %16s %16s      %6d   %6d  null  %s\n",
        ip.irrType(),
        ip.irrType(),
        ip.amtPerIrr(),
        ip.maxNoIrr(),
        genActEntries(alt_idx, total_alt));
  }

  static String genCondAlt(int alt_idx, int total_alt, char cond) {
    StringBuffer b = new StringBuffer();
    for (int i = 0; i < alt_idx; i++) {
      b.append("-    ");
    }
    b.append(cond + "    ");
    for (int i = 0; i < (total_alt - alt_idx - 1); i++) {
      b.append("-    ");
    }
    return b.toString();
  }

  static String genCondAlt(int total_alt, char cond) {
    StringBuffer b = new StringBuffer();
    for (int i = 0; i < total_alt; i++) {
      b.append(cond + "    ");
    }
    return b.toString();
  }

  static String genCondAltHeader(int total_alt) {
    StringBuffer b = new StringBuffer();
    for (int i = 0; i < total_alt; i++) {
      b.append("A" + i + "   ");
    }
    return b.toString();
  }

  static String genActEntries(int alt_idx, int total_alt) {
    StringBuffer b = new StringBuffer();
    for (int i = 0; i < alt_idx; i++) {
      b.append("n    ");
    }
    b.append("y    ");
    for (int i = 0; i < (total_alt - alt_idx - 1); i++) {
      b.append("n    ");
    }
    return b.toString();
  }

  static List<Integer> range(int start, int stop) {
    return IntStream.rangeClosed(start, stop)
        .boxed()
        .collect(Collectors.toList());
  }

  /**
   * sanity check: every irrigated crop must have a water allocation for that
   * year.
   *
   */
  void checkIrrigatedCropsAgainstAllocations() {
    Set<Integer> irrYears = new HashSet<>();
    for (IrrPeriod ip : irrPeriods) {
      List<Integer> ipy = range(ip.start().getYear(), ip.stop().getYear());
      irrYears.addAll(ipy);
    }
    for (Integer irrYear : irrYears) {
      if (irrAlloc.get(irrYear) == null)
        throw new RuntimeException("A crop wants to be irrigated, but no irrigation water allocation for year: " + irrYear);
    }
  }

  /**
   * Gen Irrigation DTLs.
   *
   * @return
   */
  private void processIrrDTLs() throws IOException {

    IrrPeriod.checkCompletedIrrPeriods(irrPeriods);
    checkIrrigatedCropsAgainstAllocations();

    StringBuffer dtls = new StringBuffer();
    for (Map.Entry<Integer, Double> alloc : irrAlloc.entrySet()) {
      int year = alloc.getKey();
      Double amt = alloc.getValue();

      if (amt == null || amt.doubleValue() <= 0.0)
        throw new RuntimeException("No irrigation water allocation for year: " + year);

      // find the Irrigation periods for that year
      List<IrrPeriod> iy = IrrPeriod.filterforYear(irrPeriods, year);
      if (iy.size() > 0) {
        // create DTL for that year
        StringBuffer dtl = genDTL(iy, year, (int) (amt * IN_TO_MM));

        // Add to prev DTLs
        dtls.append(dtl);
        dtls.append('\n');
      }
    }
    dtlStr = dtls.toString();
  }

  private void writeIrrDTLs(File file) throws IOException {
    try (PrintWriter pw = new PrintWriter(file)) {
      pw.println("Generated by CSIP service: " + new Date());
      pw.println(autoOps.size());
      pw.println();
      pw.println(dtlStr);
    }
  }

  private StringBuffer genDTL(List<IrrPeriod> lp, int year, int amt) {
    String dtlName = "irr_" + year;
    addAutoOperation(dtlName);

    String conds = "";
    String actions = "";
    int nip = lp.size();

    for (int i = 0; i < nip; i++) {
      IrrPeriod p = lp.get(i);
      conds += getCond(p, i, nip, year);
      actions += getAction(p, i, nip);
    }

    int nconds = nip * 3 + 3;
    int nalts = nip;
    int nacts = nip;

    // Conditions
    StringBuffer b = new StringBuffer();
    b.append(String.format("NAME                       CONDS      ALTS       ACTS\n"));
    b.append(String.format(" %-10s                   %d         %d          %d\n", dtlName, nconds, nalts, nacts));
    b.append(String.format("VAR               OBJ    OBJ_NUM              LIM_VAR           LIM_OP   LIM_CONST                %s\n", genCondAltHeader(nalts)));
    b.append(String.format(" year_cal         hru          0                 null                -        %4d                 %s\n", year, genCondAlt(nalts, '=')));
    b.append(String.format(" irr_year         hru          0                 null                -      %6d                 %s  # %s inches\n", amt, genCondAlt(nalts, '<'), amt / IN_TO_MM));
    b.append(String.format(" w_stress         hru          0                 null                -         0.8                 %s\n", genCondAlt(nalts, '<')));
    b.append(conds);
    // Actions
    b.append(String.format("ACT_TYP           OBJ    OBJ_NUM                 NAME           OPTION       CONST   CONST2    FP  OUTCOMES\n"));
    b.append(actions);
    return b;
  }

  /**
   * Write all management related SP inputs
   *
   * @param dir
   * @param scenario
   * @throws IOException
   */
  @Override
  public void writeFiles(File dir, String scenario) throws IOException {
    // lum.dtl file for irrigation.
    if (hasIrrCrops())
      writeIrrDTLs(new File(dir, "lum.dtl"));

    // management schedule file.
    writeManagementSch(new File(dir, "management.sch"), scenario);

    // plants file.
    writePlantIni(new File(dir, "plant.ini"), getCrops(), scenario, "this_comm");
  }

  /**
   * Transform from Universal Management to SP management
   *
   * @param um Universal Management to transform
   * @return
   */
  public static SwatPlusManagement from(List<UniEvent> uEvents, String name, SwatPlusDomainDB db) throws Exception {
    SwatPlusManagement spm = new SwatPlusManagement();
    spm.setName(name);

    LocalDate lastTillOp = UniEvent.getLastOp(uEvents, op_tillage);
    spm.setLastTillOp(lastTillOp);

    for (UniEvent ue : uEvents) {
      TriConsumer<UniEvent, SwatPlusDomainDB, SwatPlusManagement> evTransformer = conv.get(ue.getTypeName());
      if (evTransformer == null)
        throw new RuntimeException("No management event transformer for " + ue.getTypeName());
      evTransformer.accept(ue, db, spm);
    }

    if (spm.hasIrrCrops())
      spm.processIrrDTLs();

    spm.processManagementSch();
    return spm;
  }

  /**
   * Operation transformation table.
   */
  private static final Map<String, TriConsumer<UniEvent, SwatPlusDomainDB, SwatPlusManagement>> conv = new HashMap<>() {
    {
      /*
       * Fertilizer

        inp ? gal/ac  -> 99/1000 * inp
        '_lq' and '_sl'
        example:
        5000:   99/1000 * 5000 = lb solids/ac

        lb solids/ac -> kg/ha
        '_ss' and '_sd'
        inp = 10
        inp ?  266 * 10 -> lb solids /ac
        lb solids/ac -> kg/ha

       */
      put(op_amendment_fertilizer.typename(), (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
        SwatPlusDomainDB.OP op = db.getOpData(ue.getOpId(), SpOp.fert.name());

        // optionally add "n" or "p" to fertilizer.frt and change op_data1 below
        // opdata[2] is application type, e.g. "inject", "broadcast", ...
        List<SpEvent> l = new ArrayList<>();

        String fertName = ue.getString(fert_name, null);
        if (fertName != null) {
          fertName = fertName.toLowerCase();
          double fertAmount = ue.getDouble(fert_amt_ac);
          if (fertAmount <= 0.0)
            throw new RuntimeException("invalid fertilizer amount: " + fertAmount);

          // get the solids for the fertilizer name
          // fert[0] = solids
          double[] fert = db.getFertFor(fertName);
          if (fertName.endsWith("_lq") || fertName.endsWith("_sl")) {
            fertAmount = fert[0] / 1000 * fertAmount;
          } else if (fertName.endsWith("_ss") || fertName.endsWith("_sd")) {
            fertAmount = fert[0] / 2000 * fertAmount;
          } else
            throw new RuntimeException("Cannot process fertilizer: " + fertName);

          l.add(new SpEvent(SpOp.fert, ue.getDate(), fertName, op.data2(), fertAmount * LBAC_TO_KGHA));
          sm.addEvents(l);
          return;
        }

        double min_n = ue.getDouble(min_n_lbs_ac, 0.0);
        if (min_n > 0)
          l.add(new SpEvent(SpOp.fert, ue.getDate(), "mi_n", op.data2(), min_n * LBAC_TO_KGHA));

        double min_p = ue.getDouble(min_p_lbs_ac, 0.0);
        if (min_p > 0)
          l.add(new SpEvent(SpOp.fert, ue.getDate(), "mi_p", op.data2(), min_p * LBAC_TO_KGHA));

//        double org_n = ue.getDouble(org_n_lbs_ac, 0.0);
//        if (org_n > 0)
//          l.add(new SpEvent(SpOp.fert, ue.getDate(), "or_n", op.data2(), org_n / LBAC_IN_KGHA));
//
//        double org_p = ue.getDouble(org_p_lbs_ac, 0.0);
//        if (org_p > 0)
//          l.add(new SpEvent(SpOp.fert, ue.getDate(), "or_p", op.data2(), org_p / LBAC_IN_KGHA));
        if (l.isEmpty())
          throw new RuntimeException("no fertilizer information (min_p , min_n) for event: " + ue.getDate());

        sm.addEvents(l);
      });

      /*
       * Planting
       */
      put(op_plant.typename(), (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
        // just to check the op_id
        SwatPlusDomainDB.OP op = db.getOpData(ue.getOpId(), SpOp.plnt.name());
        int cropId = ue.getInt(crop_id);
        String[] crop = db.getCrop(cropId);
        sm.addEvent(new SpEvent(SpOp.plnt, ue.getDate(), crop[0], null, 0));

        // irrigation
        if (ue.getBool(irr, false)) {
          // planting with irrigation
          if (sm.currIrr == null || sm.currIrr.isCompletePeriod()) {
            int irrMax = ue.getInt(irr_max_no, 99);
            double irrAmt = ue.getDouble(irr_amt_in, 1.5);
            String irrType = ue.getString(irr_type, "sprinkler_med");

            // start new period
            IrrPeriod p = new IrrPeriod(ue.getDate(), crop[0], cropId,
                irrType, (int) (irrAmt * IN_TO_MM), irrMax);
            sm.currIrr = p;
          } else
            throw new RuntimeException("Planting+Irrigation scheduled twice for: " + ue);
        } else {
          // planting with no irrigation
          if (sm.currIrr != null && !sm.currIrr.isCompletePeriod())
            throw new RuntimeException("Planting+Irrigation not completed with harvest: " + ue);
          sm.currIrr = null;
        }
      });

      /*
       * Harvest (and  Kill)
       */
      put(op_harvest.typename(), (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
        SwatPlusDomainDB.OP op = db.getOpData(ue.getOpId(), SpOp.harv.name());
        int cropId = ue.getInt(crop_id);
        String[] crop = db.getCrop(cropId);
        if (crop[0] == null)
          throw new RuntimeException("No SP crop for crop_id " + ue.getInt(crop_id));

        sm.addEvent(new SpEvent(SpOp.harv, ue.getDate(), crop[0], op.data2(), 0));

        if (op.kill_crop())
          sm.addEvent(new SpEvent(SpOp.kill, ue.getDate(), crop[0], null, 0));

        // irrigation
        if (sm.currIrr != null) {
          sm.currIrr.setStopIrrigation(ue.getDate(), cropId);
          if (!sm.irrPeriods.contains(sm.currIrr))
            sm.irrPeriods.add(sm.currIrr);
        }
      });

      /*
       * Kill
       */
      put(op_kill.typename(), (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
        // just to check the op_id
        SwatPlusDomainDB.OP op = db.getOpData(ue.getOpId(), SpOp.kill.name());
        String[] crop = db.getCrop(ue.getInt(crop_id));
        sm.addEvent(new SpEvent(SpOp.kill, ue.getDate(), crop[0], null, 0));
      });

      /*
       * Tillage
       */
      put(op_tillage.typename(), (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
        SwatPlusDomainDB.OP op = db.getOpData(ue.getOpId(), SpOp.till.name());

        sm.addEvent(new SpEvent(SpOp.till, ue.getDate(), op.data1(), null, 0));
        // if a crop_id is provided in the event then check the kill crop flag
        // to kill the crop after tillage.
        if (ue.ev_contains(crop_id) && op.kill_crop()) {
          String[] crop = db.getCrop(ue.getInt(crop_id));
          sm.addEvent(new SpEvent(SpOp.kill, ue.getDate(), crop[0], null, 0));
        }
      });

      /*
       * Pesticide application
       */
      put(op_amendment_pesticide.typename(), (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
        SwatPlusDomainDB.OP op = db.getOpData(ue.getOpId(), SpOp.pest.name());

        // herbi -> dummy pesticide
        // TODO: resolve pest, currently ingnored for its application, just processed
        // for killig the crop
//        l.add(new SpEvent(SpOp.pest, ue.getDate(), "weedar", op.data2(), 0));
        if (op.kill_crop() && ue.op_requires(crop_id)) {
          String[] crop = db.getCrop(ue.getInt(crop_id));
          sm.addEvent(new SpEvent(SpOp.kill, ue.getDate(), crop[0], null, 0));
        }
      });

      /*
       * Burn
       */
      put(op_burn.typename(), (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
        SwatPlusDomainDB.OP op = db.getOpData(ue.getOpId(), SpOp.burn.name());
        sm.addEvent(new SpEvent(SpOp.burn, ue.getDate(), op.data1(), null, 0));
      });

      /*
       * Grazing
       */
      put(op_graze.typename(), (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
        SwatPlusDomainDB.OP op = db.getOpData(ue.getOpId(), SpOp.graz.name());
        sm.addEvent(new SpEvent(SpOp.graz, ue.getDate(), op.data1(), null, 0));
      });

      /*
       * Irrigation amount allocation per year
       */
      put(op_irrigation_allocation.typename(), (UniEvent ue, SwatPlusDomainDB db, SwatPlusManagement sm) -> {
        sm.addIrrAlloc(ue.getDate().getYear(), ue.getDouble(irr_amt_year_in));
      });

      // Ignore the operations below
      put(op_irrigation_start_stop.typename(), PASS);
      put(op_irrigation_start.typename(), PASS);
      put(op_irrigation_stop.typename(), PASS);
    }
  };
}
