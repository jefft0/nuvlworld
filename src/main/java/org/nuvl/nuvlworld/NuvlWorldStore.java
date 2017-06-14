/*
Copyright (C) 2017 Jeff Thompson

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.nuvl.nuvlworld;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nuvl.argue.aba_plus.Sentence;

/**
 * A NuvlWorldStore holds a set of aba_plus Sentences plus other cached values
 * needed by the application.
 * @author Jeff Thompson, jeff@thefirst.org
 */
public class NuvlWorldStore {
  public NuvlWorldStore()
  {
  }

  /**
   * Read filePath as a list of Scheme triples where the first term is the
   * predicate, and add to sentencesByPredicate_ and sentencesByArg2_.
   * However, if the predicate is "description", then only add to descriptions_
   * if the  subject is already in sentencesByArg2_.
   * @param filePath The Scheme file to read.
   */
  public void
  loadSchemeFile(String filePath) throws FileNotFoundException, IOException
  {
    try (FileReader file = new FileReader(filePath);
            BufferedReader reader = new BufferedReader(file)) {
    int nLines = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        ++nLines;
        if (nLines % 1000000 == 0)
          System.out.println("Loading " + filePath + ", line " + nLines);

        if (line.equals("") || line.startsWith(";"))
          continue;

        String predicate;
        String arg2;
        Matcher matcher = termPattern_.matcher(line);
        if (matcher.find()) {
          predicate = matcher.group(1);
          arg2 = matcher.group(2);
        }
        else {
          matcher = termPattern4_.matcher(line);
          if (matcher.find()) {
            predicate = matcher.group(1);
            arg2 = matcher.group(2);
          }
          else {
            matcher = integerPattern_.matcher(line);
            if (matcher.find()) {
              predicate = matcher.group(1);
              arg2 = matcher.group(2);
            }
            else {
              matcher = stringPattern_.matcher(line);
              if (matcher.find()) {
                predicate = matcher.group(1);
                arg2 = matcher.group(2);

                if (predicate.equals("description")) {
                  if (!sentencesByArg2_.containsKey(arg2))
                    // Don't add extraneous descriptions, to save memory.
                    continue;

                  descriptions_.put
                    (matcher.group(2), fromEscapedString(matcher.group(3)));
                  // Don't add to sentencesByPredicate_, etc.
                  continue;
                }
              }
              else
                throw new Error("Unrecognized Scheme pattern: " + line);
            }
          }
        }

        Sentence sentence = new Sentence(line, false);
        Set<Sentence> sentenceSet = sentencesByPredicate_.get(predicate);
        if (sentenceSet == null)
          sentencesByPredicate_.put(predicate, (sentenceSet = new HashSet()));
        sentenceSet.add(sentence);

        sentenceSet = sentencesByArg2_.get(arg2);
        if (sentenceSet == null)
          sentencesByArg2_.put(arg2, (sentenceSet = new HashSet()));
        sentenceSet.add(sentence);
      }
    }
  }

  /**
   * Read filePath as a list of tab-separated values of ID and description,
   * and add to descriptions_ where the subject is "Q" + ID. But to save memory,
   * only add if the subject is already in sentencesByArg2_.
   * @param filePath The TSV file to read.
   */
  public void
  loadWikidataDescriptions(String filePath) throws FileNotFoundException, IOException
  {
    try (FileReader file = new FileReader(filePath);
            BufferedReader reader = new BufferedReader(file)) {
    int nLines = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        ++nLines;
        if (nLines % 10000000 == 0)
          System.out.println("Loading " + filePath + ", line " + nLines);

        int tabIndex = line.indexOf('\t');
        String subject = "Q" + line.substring(0, tabIndex);

        if (!sentencesByArg2_.containsKey(subject))
          // Don't add extraneous descriptions, to save memory.
          continue;

        descriptions_.put
          (subject, fromEscapedString(line.substring(tabIndex + 1)));
      }
    }
  }

  /**
   * A EventTimeInterval holds an event term and the start and end times of a
   * time interval as milliseconds since the UTC Unix epoch.
   */
  public class EventTimeInterval {

    public final String event;
    public final long startUtcMillis;
    public final long endUtcMillis;

    public EventTimeInterval(String event, long startUtcMillis,
            long endUtcMillis)
    {
      this.event = event;
      this.startUtcMillis = startUtcMillis;
      this.endUtcMillis = endUtcMillis;
    }
  }

  /**
   * Use the day start and end times $DayStart and $DayEnd according to the
   * given timeZone and return a set of EventTimeInterval which satisfy:
   * (AND (subAttrOf $Event (TimeIntervalFn $Start $End))
   *      (lessThan $Start $DayEnd) (greaterThanOrEqual $End $DayBegin)) .
   *
   * @param date The date.
   * @param timeZone The TimeZone to get the UTC day start and end. If timeZone
   * is different from the previous call (or this is the first call), this
   * clears the cache and caches all results. (A future implementation may
   * maintain results for different timeZone values, but we want to save
   * memory.)
   * @return A set of EventTimeInterval which match the query above (possibly
   * empty) with $Event plus $Start $End as milliseconds since the Unix
   * epoch.
   */
  public Set<EventTimeInterval>
  overlapsDate(LocalDate date, TimeZone timeZone)
  {
    if (timeZone != overlapsDateTimeZone_) {
      // TODO: Check if sentences_ has changed.
      // Set up overlapsDate_.
      Calendar calendar = Calendar.getInstance(timeZone);
      Pattern timePattern = Pattern.compile
        ("^\\(subAttrOf (" + TERM + ") \\(TimeIntervalFn (" + INT +
         ") (" + INT + ")\\)\\)$");

      overlapsDate_.clear();
      overlapsDateTimeZone_ = timeZone;

      for (Sentence sentence : sentencesByPredicate_.getOrDefault
           ("subAttrOf", emptySentences_)) {
        Matcher matcher = timePattern.matcher(sentence.symbol());
        if (!matcher.find())
          continue;
        String event = matcher.group(1);

        long startTimeUtcMillis = Long.parseLong(matcher.group(2));
        long endTimeUtcMillis = Long.parseLong(matcher.group(3));

        // Find dates with dayStartUtcMillis and dayEndUtcMillis where
        // (startTimeUtcMillis < dayEndUtcMillis &&
        //  endTimeUtcMillis >= dayStartUtcMillis)
        calendar.setTimeInMillis(startTimeUtcMillis);
        LocalDate startDate = getCalendarLocalDate(calendar);
        LocalDate endDate;
        if (endTimeUtcMillis <= startTimeUtcMillis)
          // A common and simple case (when they are equal).
          endDate = startDate;
        else {
          calendar.setTimeInMillis(endTimeUtcMillis);
          endDate = getCalendarLocalDate(calendar);
          if (calendar.get(Calendar.HOUR) == 0 &&
              calendar.get(Calendar.MINUTE) == 0 &&
              calendar.get(Calendar.SECOND) == 0)
            // Make the end be before midnight of the next day.
            endDate = endDate.plusDays(-1);
        }

        // Add entries to overlapsDate_ for startDate to endDate, inclusive.
        EventTimeInterval timeInterval = new EventTimeInterval
          (event, startTimeUtcMillis, endTimeUtcMillis);
        LocalDate key = startDate;
        while (true) {
          Set<EventTimeInterval> timeIntervalSet = overlapsDate_.get(key);
          if (timeIntervalSet == null) {
            timeIntervalSet = new HashSet<>();
            overlapsDate_.put(key, timeIntervalSet);
          }
          timeIntervalSet.add(timeInterval);

          if (key.equals(endDate))
            break;

          key = key.plusDays(1);
        }
      }
    }

    return overlapsDate_.getOrDefault(date, emptyEventTimeIntervalSet_);
  }

  /**
   * Find the first Sentence with the given predicate where the given regex
   * pattern matches and has the given group value.
   *
   * @param predicate The Sentence predicate.
   * @param pattern The regex pattern.
   * @param groupNumber The group number of the matched pattern.
   * @param group The value of the group of the patched pattern.
   * @return The regex Matcher object or null if not found.
   */
  public Matcher
  findFirstByPredicate
    (String predicate, Pattern pattern, int groupNumber, String group)
  {
    for (Sentence sentence : sentencesByPredicate_.getOrDefault
         (predicate, emptySentences_)) {
      Matcher matcher = pattern.matcher(sentence.symbol());
      if (matcher.find() && matcher.group(groupNumber).equals(group)) {
        return matcher;
      }
    }

    return null;
  }

  /**
   * Get a LocalDate for the calendar year, month and day.
   *
   * @param calendar The Calendar.
   * @return The LocalDate for the calendar.
   */
  public static LocalDate
  getCalendarLocalDate(Calendar calendar)
  {
    // Calendar months start from 0.
    return LocalDate.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH));
  }

  /**
   * Use the timeZone to convert the date and time to UTC millis.
   * @param timeZone The TimeZone object.
   * @param date The date. This ignores the hour/minute/second and time zone
   * fields.
   * @param time The time.
   * @return The milliseconds since January 1, 1970 UTC.
   */
  public static long
  toUtcMillis(TimeZone timeZone, Date date, LocalTime time)
  {
    Calendar calendar = Calendar.getInstance(timeZone);
    calendar.clear();
    calendar.set
      (1900 + date.getYear(), date.getMonth(), date.getDate(),
       time.getHour(), time.getMinute(), time.getSecond());
    return calendar.getTimeInMillis();
  }

  /**
   * Assume s is a JSON string with begin and end quotes, so remove them and
   * unescape.
   * @param s The JSON string.
   * @return The unescaped result without quotes.
   */
  public static String
  fromEscapedString(String s) { return gson_.fromJson(s, String.class); }

  /**
   * Convert s to the escaped JSON string including begin and end quotes.
   * @param s The string to convert to JSON.
   * @return The unescaped result without quotes.
   */
  public static String
  toEscapedString(String s) { return gson_.toJson(s); }

  /** key: predicate, value: set of Sentence. */
  public final Map<String, Set<Sentence>> sentencesByPredicate_ = new HashMap<>();
  /** key: arg2, value: set of Sentence. */
  public final Map<String, Set<Sentence>> sentencesByArg2_ = new HashMap<>();
  /** key: subject, value: the description string (unescaped). */
  public final Map<String, String> descriptions_ = new HashMap<>();
  public static final String TERM = "[a-zA-Z_]\\w*";
  public static final String INT = "-?\\d+";
  public static final String UNARY_FN =
    "\\(" + TERM + "Fn (?:" + TERM + "|" + INT + ")\\)";
  public static final String BINARY_FN =
    "\\(" + TERM + "Fn (?:" + TERM + "|" + INT + ") (?:" + TERM + "|" + INT + ")\\)";
  public static final Pattern termPattern_ = Pattern.compile
    ("^\\((" + TERM + ") (" + TERM + ") " +
     "(" + TERM + "|" + UNARY_FN + "|" + BINARY_FN + ")\\)$");
  public static final Pattern termPattern4_ = Pattern.compile
    ("^\\((" + TERM + ") (" + TERM + ") (" + TERM + ") " +
     "(" + TERM + "|" + UNARY_FN + "|" + BINARY_FN + ")\\)$");
  public static final Pattern integerPattern_ = Pattern.compile
    ("^\\((" + TERM + ") (" + TERM + ") (" + INT + ")\\)$");
  public static final Pattern stringPattern_ = Pattern.compile
    ("^\\((" + TERM + ") (" + TERM + ") (\".*\")\\)$");

  private TimeZone overlapsDateTimeZone_ = null;
  private final Map<LocalDate, Set<EventTimeInterval>> overlapsDate_ = new HashMap<>();
  private static final Set<EventTimeInterval> emptyEventTimeIntervalSet_ = new HashSet<>();
  private static final Set<Sentence> emptySentences_ = new HashSet<>();
  private static final Gson gson_ = new GsonBuilder().disableHtmlEscaping().create();;
}
