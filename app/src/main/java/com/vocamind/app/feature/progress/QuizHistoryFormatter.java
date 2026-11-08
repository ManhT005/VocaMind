package com.vocamind.app.feature.progress;
import java.text.DateFormat;
import java.util.*;
public final class QuizHistoryFormatter {
 private QuizHistoryFormatter() { }
 public static String completedAt(long epochMillis,Locale locale,TimeZone zone) {
  DateFormat f=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,locale);
  f.setTimeZone(zone);return f.format(new Date(epochMillis));
 }
}
