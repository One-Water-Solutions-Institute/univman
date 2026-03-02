/*
 * $Id: 0.18+5 IrrPeriod.java 6168fcdc1eff 2025-11-06 od $
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Irrigation Period
 *
 * @author od
 */
// do not use the default record constructor, use the alternative below.
public record IrrPeriod(LocalDate start, Ref<LocalDate> stopRef, String crop, int crop_id, String irrType, int amtPerIrr, int maxNoIrr) {

  private static class Ref<T> {

    T val;

    void set(T val) {
      this.val = val;
    }

    T get() {
      return val;
    }
  }

  public IrrPeriod(LocalDate start, String crop, int crop_id, String irrType, int amtPerIrr, int maxNoIrr) {
    this(start, new Ref<LocalDate>(), crop, crop_id, irrType, amtPerIrr, maxNoIrr);
  }

  public LocalDate stop() {
    // this is the little price to pay for using immutable records
    return stopRef.get();
  }

  public boolean isSingleYearSummerCrop() {
    int startY = startYear();
    int stopY = stopYear();
    return startY == stopY;
  }

  public boolean isSingleYearSummerCrop(int year) {
    int startY = startYear();
    int stopY = stopYear();
    return startY == stopY && startY == year;
  }

  public boolean isFallWinterCrop(int year) {
    int startY = startYear();
    int stopY = stopYear();
    return startY == year && startY < stopY;
  }

  public boolean isSpringWinterCrop(int year) {
    int startY = startYear();
    int stopY = stopYear();
    return stopY == year && startY < stopY;
  }

  public boolean isFullYearCrop(int year) {
    int startY = startYear();
    int stopY = stopYear();
    return startY < year && stopY > year && startY < stopY;
  }

  public void setStopIrrigation(LocalDate stop, int crop_id) {
    if (this.crop_id != crop_id)
      throw new RuntimeException("Crop id mismatch at " + stop.toString() + "  plant: " + this.crop_id + " != harv: " + crop_id);
    if (!stop.isAfter(start))
      throw new RuntimeException("Stop irrigation at " + stop.toString() + " not after start at " + start.toString());
    stopRef.set(stop);
  }

  private int stopYear() {
    if (!isCompletePeriod())
      throw new RuntimeException("Incomplete Irrigation Period: " + this);
    return stop().getYear();
  }

  private int startYear() {
    return start.getYear();
  }

  public boolean isCompletePeriod() {
    return stop() != null;
  }

  public boolean inYear(int year) {
    return startYear() <= year && year <= stopYear();
  }

  @Override
  public String toString() {
    return start.toString() + " -> "
        + (stopRef.get() != null ? stopRef.get().toString() : "?") + " for crop " + crop + "(id=" + crop_id + ")";
  }

  public static List<IrrPeriod> filterforYear(List<IrrPeriod> lip, int year) {
    return lip.stream()
        .filter(ip -> ip.inYear(year))
        .collect(Collectors.toList());
  }

  public static void checkCompletedIrrPeriods(List<IrrPeriod> irrPeriods) {
    Optional<IrrPeriod> foundIncomplete = irrPeriods.stream()
        .filter(ip -> !ip.isCompletePeriod())
        .findFirst();

    if (foundIncomplete.isPresent()) {
      IrrPeriod ip = foundIncomplete.get();
      throw new RuntimeException("Incomplete Irrigation period: " + ip);
    }
  }

}
