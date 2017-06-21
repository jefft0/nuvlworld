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
package org.nuvl.nuvlworld.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jdatepicker.impl.JDatePanelImpl;
import org.jdatepicker.impl.UtilCalendarModel;
import org.nuvl.nuvlworld.NuvlWorldPreferences;
import org.nuvl.nuvlworld.NuvlWorldStore;
import org.nuvl.nuvlworld.NuvlWorldStore.EventTimeInterval;
import org.nuvl.argue.aba_plus.Sentence;
import org.nuvl.argue.aba_plus.Rule;
import org.nuvl.argue.aba_plus.ABA_Plus;
import org.nuvl.argue.NuvlFramework;
import static org.nuvl.nuvlworld.NuvlWorldStore.INT;
import static org.nuvl.nuvlworld.NuvlWorldStore.TERM;
import scala.collection.JavaConversions;

/**
 * NuvlCalendarFrame displays events on a calendar and shows conflicts using
 * argumentation.
 * @author Jeff Thompson, jeff@thefirst.org
 */
public class NuvlCalendarFrame extends javax.swing.JFrame {
  /**
   * Create a new NuvlCalendarFrame to use the given store and preferences.
   */
  public NuvlCalendarFrame
    (NuvlWorldStore store, NuvlWorldPreferences preferences)
  {
    super("Calendar");
    preferences_ = preferences;
    store_ = store;

    initComponents();
    scenariosTextPane_.addHyperlinkListener
      (new HyperlinkListener(){
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          if (e.getDescription().startsWith("scenario"))
            selectScenario(Integer.parseInt
              (e.getDescription().substring("scenario".length())));
        }
      }
    });

    // Initialize the day panel grid. A month has up to six rows of weeks.
    for (int iWeek = 0; iWeek < 6; ++iWeek) {
      ArrayList<DayPanel> week = new ArrayList<>();
      daysPanelGrid_.add(week);

      for (int iDay = 0; iDay < 7; ++iDay) {
        DayPanel dayPanel = new DayPanel(this);
        week.add(dayPanel);
        dayPanel.addTo(daysPanel_);
      }
    }

    // Initialize the headers.
    for (int i = 0; i < 7; ++i) {
      JLabel header = new JLabel();
      daysPanelHeaders_.add(header);

      header.setBorder(BorderFactory.createLineBorder(DayPanel.BORDER_COLOR));
      header.setHorizontalAlignment(SwingConstants.CENTER);
      header.setFont(new Font("Tahoma", 0, 11));
      // Add to whatever is the parent of daysPanel_.
      daysPanel_.getParent().add(header);
    }

    // Set up the datePanel_.
    Calendar calendar = Calendar.getInstance(preferences_.getTimeZone());
    calendar.clear();
    // Calendar object: months start at 0.
    calendar.set
      (selectedDate_.getYear(), selectedDate_.getMonthValue() - 1,
       selectedDate_.getDayOfMonth());
    UtilCalendarModel model = new UtilCalendarModel(calendar);
    Properties p = new Properties();
    p.put("text.today", "Today");
    p.put("text.month", "Month");
    p.put("text.year", "Year");
    datePanel_ = new JDatePanelImpl(model, p);
    datePanel_.setLocation(0, 0);
    datePanel_.setSize(190, 175);
    // This is a hack to change the month label background from the unreadable dark blue.
    ((Container)datePanel_.getComponents()[0]).getComponents()[0].setBackground
      (new Color(200, 200, 255));
    calendarControlsPanel_.add(datePanel_);

    datePanel_.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent ae) {
        if (ae.getActionCommand().equals("Date selected")) {
          Calendar calendar = (Calendar)datePanel_.getModel().getValue();
          if (calendar != null) {
            // Calendar months start from 0.
            selectedDate_ = LocalDate.of
              (calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
               calendar.get(Calendar.DAY_OF_MONTH));
            setUpDaysPanel();
          }
        }
      }
    });

    setUpScenarios();
    setUpDaysPanel();

    pack();
  }

  /**
   * Compute conflicts and set up scenariosTextPane_.
   */
  private void setUpScenarios()
  {
    HashSet<Sentence> assumptions = new HashSet<>();
    HashSet<Rule> rules = new HashSet<>();

    // Add rules for (implies (task $InAttr) (attr $OutAttr)).
    // Also add each task as an assumption.
    Pattern rulePattern = Pattern.compile
      ("^\\(implies \\(task (" + TERM + ")\\) \\(attr (" + TERM + ")\\)\\)$");
    for (Sentence sentence : store_.sentencesByPredicate_.getOrDefault
         ("implies", new HashSet<>())) {
      Matcher matcher = rulePattern.matcher(sentence.symbol());
      if (matcher.find()) {
        Sentence task = new Sentence("(task " + matcher.group(1) + ")");
        Sentence inAttr =  new Sentence("(attr " + matcher.group(1) + ")");
        Sentence outAttr = new Sentence("(attr " + matcher.group(2) + ")");

        rules.add(new Rule(task, inAttr));
        rules.add(new Rule(task, outAttr));
        assumptions.add(task);
      }
    }

    // Add disjoint attributes.
    Pattern disjointPattern = Pattern.compile
      ("^\\(disjointAttrs (" + TERM + ") (" + TERM + ")\\)$");
    for (Sentence sentence : store_.sentencesByPredicate_.getOrDefault
         ("disjointAttrs", new HashSet<>())) {
      Matcher matcher = disjointPattern.matcher(sentence.symbol());
      if (matcher.find())
        rules.add(new Rule(new Sentence("(attr " + matcher.group(1) + ")"),
                           new Sentence("(attr " + matcher.group(2) + ")", true)));
    }

    // TODO: Derive these from loaded location data.
    rules.add(new Rule(new Sentence("(attr LondonWet)"), new Sentence("(attr ImperialWet)")));
    rules.add(new Rule(new Sentence("(attr LondonWet)"), new Sentence("(attr ScienceMuseumWet)")));

    // Compute the framework.
    NuvlFramework framework = new NuvlFramework(assumptions, rules);
    HashSet<Sentence> groundedExtension = new HashSet<>
      (JavaConversions.asJavaCollection(framework.groundedExtension()));

    // Get all $Attr in the deductions of the grounded extension which match (attr $Attr).
    groundedAttrs_.clear();
    for (Sentence deduction : JavaConversions.asJavaCollection
         (framework.aba().generate_all_deductions(framework.groundedExtension()))) {
      if (deduction.is_contrary())
        continue;

      Matcher matcher = attrPattern_.matcher(deduction.symbol());
      if (matcher.find())
        groundedAttrs_.add(matcher.group(1));
    }

    // Create the scenarios.
    scenarios_.clear();
    for (scala.collection.immutable.Set<Sentence> extension : JavaConversions.asJavaCollection
         (framework.preferredExtensions()))
      scenarios_.add(new Scenario(extension, framework, groundedExtension));

    System.out.println("groundedExtension: " + groundedExtension);
    System.out.println("groundedAttrs: " + groundedAttrs_);

    String text = "";
    for (int i = 0; i < scenarios_.size(); ++i) {
      int scenarioNumber = i + 1;
      text += "<a href=\"scenario" + scenarioNumber + "\">Scenario " +
        scenarioNumber + "</a><br/>";
      text += scenarios_.get(i).deducedAttrs + "<br/><br/>";
    }

    scenariosTextPane_.setText(text);
  }

  /**
   * Set up the dayPanelGrid_ based on selectedDate_.
   */
  private void
  setUpDaysPanel()
  {
    boolean monthChanged = !(daysPanelPreviousDate_.getYear() == selectedDate_.getYear() &&
        daysPanelPreviousDate_.getMonthValue() == selectedDate_.getMonthValue());
    daysPanelPreviousDate_ = selectedDate_;
    if (!monthChanged)
      return;

    daysPanelLabel_.setText(selectedDate_.format(monthAndYearFormatter_));
    TimeZone timeZone = preferences_.getTimeZone();
    Calendar calendar = Calendar.getInstance(timeZone);
    calendar.clear();
    // Calendar object: months start at 0.
    calendar.set
      (selectedDate_.getYear(), selectedDate_.getMonthValue() - 1, selectedDate_.getDayOfMonth());
    // Make the date panel track the change.
    ((UtilCalendarModel)datePanel_.getModel()).setValue(calendar);

    LocalDate firstDayOfMonth = LocalDate.of
      (selectedDate_.getYear(), selectedDate_.getMonthValue(), 1);
    LocalDate lastDayOfLastMonth = firstDayOfMonth.plusDays(-1);
    LocalDate firstDayOfNextMonth = firstDayOfMonth.plusMonths(1);
    LocalDate lastDayOfMonth = firstDayOfNextMonth.plusDays(-1);

    LocalDate date = firstDayOfMonth;
    // Back up to the start of the week.
    while (date.getDayOfWeek() != preferences_.getStartOfWeek())
      date = date.plusDays(-1);

    calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());

    // We'll adjust nWeekRows_ below.
    nWeekRows_ = 6;
    for (int iWeek = 0; iWeek < 6; ++iWeek) {
      ArrayList<DayPanel> week = daysPanelGrid_.get(iWeek);

      for (int iDay = 0; iDay < 7; ++iDay) {
        DayPanel dayPanel = week.get(iDay);

        // To know the end of this date, get the beginning of the next date. We
        // do this instead of adding 24 hours because of daylight saving time.
        LocalDate nextDate = date.plusDays(1);
        calendar.clear();
        // Calendar object: months start at 0.
        calendar.set
          (nextDate.getYear(), nextDate.getMonthValue() - 1, nextDate.getDayOfMonth());

        if (iWeek == 0)
          // Set the header using LocalDate.format which can be localized.
          daysPanelHeaders_.get(iDay).setText(date.format(dayOfWeekFormatter_));

        if (iWeek >= nWeekRows_)
          dayPanel.setVisible(false);
        else {
          dayPanel.setVisible(true);

          // Set the label.
          if (date.equals(lastDayOfLastMonth) ||
              date.equals(firstDayOfMonth) ||
              date.equals(lastDayOfMonth) ||
              date.equals(firstDayOfNextMonth))
            // Include the month.
            dayPanel.setDayText(date.format(monthAndDayFormatter_));
          else
            dayPanel.setDayText("" + date.getDayOfMonth());

          Set<EventTimeInterval> timeIntervals = store_.overlapsDate
            (date, timeZone);
          DayPanel.Entry[] panelEntries = new DayPanel.Entry[timeIntervals.size()];
          int entryCount = 0;
          for (EventTimeInterval timeInterval : timeIntervals) {
            // TODO: Check that event is a event in the argument set.
            String event = timeInterval.event;

            String title = store_.descriptions_.getOrDefault(event, event);

            calendar.clear();
            calendar.setTimeInMillis(timeInterval.startUtcMillis);
            int beginHour = calendar.get(Calendar.HOUR_OF_DAY);
            int beginMinute = calendar.get(Calendar.MINUTE);
            String displayTime;
            // TODO: Preference for 12/24 hour display.
            String beginTime = String.format("%02d:%02d ", beginHour, beginMinute);

            if (timeInterval.endUtcMillis == timeInterval.startUtcMillis)
              // A common and simple case.
              displayTime = beginTime;
            else {
              LocalDate beginDate = NuvlWorldStore.getCalendarLocalDate(calendar);
              calendar.clear();
              calendar.setTimeInMillis(timeInterval.endUtcMillis);
              int endHour = calendar.get(Calendar.HOUR_OF_DAY);
              int endMinute = calendar.get(Calendar.MINUTE);
              int endSecond = calendar.get(Calendar.SECOND);
              boolean endsAtMidnight =
                (endHour == 0 && endMinute == 0 && endSecond == 0);

              LocalDate endDate = NuvlWorldStore.getCalendarLocalDate(calendar);
              if (date.equals(beginDate)) {
                if (endDate.equals(beginDate) ||
                    endsAtMidnight && endDate.equals(beginDate.plusDays(1)))
                  displayTime = beginTime;
                else
                  // Prefix a left arrow.
                  displayTime = "< " + beginTime;
              }
              else if (date.equals(endDate))
                // Prefix a right arrow.
                // TODO: Preference for 12/24 hour display.
                displayTime = String.format("> %02d:%02d ", endHour, endMinute);
              else
                // Prefix a left-right arrow without the time.
                displayTime = "<-> ";
            }

            panelEntries[entryCount++] = new DayPanel.Entry
             (timeInterval, displayTime + title);
          }

          // Sort according to DayPanel.Entry.compareTo.
          Arrays.sort(panelEntries);
          dayPanel.setEntries(panelEntries);

          if (date.equals(lastDayOfMonth))
            // This is the last row.
            nWeekRows_ = iWeek + 1;
        }

        // Get ready for the next iteration.
        date = nextDate;
      }
    }

    // Set the day panel sizes and locations.
    daysPanel_ComponentResized(null);
  }

  private void selectScenario(int scenarioNumber)
  {
  }

  /**
   * Set up the eventDetailTextPane_ for the selected event entry.
   */
  private void setUpEventDetail(DayPanel.Entry entry)
  {
    SimpleDateFormat format = new SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm:ss");
    format.setTimeZone(preferences_.getTimeZone());
    String event = entry.timeInterval.event;

    String text = "";
    String title = store_.descriptions_.getOrDefault(event, event);
    boolean isGrounded = groundedAttrs_.contains(event);
    String color = isGrounded ? "black" : "red";
    // TODO: HTML escape the title.
    text += "<font color=\"" + color + "\">" + title + "</font>";

    text += "<br/>Start: " + format.format(new Date(entry.timeInterval.startUtcMillis));
    text += "<br/>End:&nbsp; " + format.format(new Date(entry.timeInterval.endUtcMillis));

    String scenarioNumbers = "";
    for (int i = 0; i < scenarios_.size(); ++i) {
      if (scenarios_.get(i).deducedAttrs.contains(event)) {
        if (!scenarioNumbers.equals(""))
          scenarioNumbers += ", ";
        scenarioNumbers += (i + 1);
      }
    }
    text += "<br/>In scenario " + scenarioNumbers;

    eventDetailTextPane_.setText(text);
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    dayPopupMenu_ = new javax.swing.JPopupMenu();
    newEventMenuItem_ = new javax.swing.JMenuItem();
    topHorizontalSplitPane_ = new javax.swing.JSplitPane();
    calendarControlsPanel_ = new javax.swing.JPanel();
    jScrollPane2 = new javax.swing.JScrollPane();
    scenariosTextPane_ = new javax.swing.JTextPane();
    eventsAndCalendarVerticalSplitPane_ = new javax.swing.JSplitPane();
    calendarPanel_ = new javax.swing.JPanel();
    daysPanel_ = new javax.swing.JPanel();
    decrementButton_ = new javax.swing.JButton();
    todayButton_ = new javax.swing.JButton();
    incrementButton_ = new javax.swing.JButton();
    daysPanelLabel_ = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    eventDetailTextPane_ = new javax.swing.JTextPane();

    newEventMenuItem_.setText("New Event...");
    newEventMenuItem_.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        newEventMenuItem_ActionPerformed(evt);
      }
    });
    dayPopupMenu_.add(newEventMenuItem_);

    setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
    setTitle("Calendar - Nuvl");

    topHorizontalSplitPane_.setDividerLocation(190);

    scenariosTextPane_.setEditable(false);
    scenariosTextPane_.setContentType("text/html"); // NOI18N
    jScrollPane2.setViewportView(scenariosTextPane_);

    javax.swing.GroupLayout calendarControlsPanel_Layout = new javax.swing.GroupLayout(calendarControlsPanel_);
    calendarControlsPanel_.setLayout(calendarControlsPanel_Layout);
    calendarControlsPanel_Layout.setHorizontalGroup(
      calendarControlsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 188, Short.MAX_VALUE)
    );
    calendarControlsPanel_Layout.setVerticalGroup(
      calendarControlsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(calendarControlsPanel_Layout.createSequentialGroup()
        .addGap(180, 180, 180)
        .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 542, Short.MAX_VALUE)
        .addGap(1, 1, 1))
    );

    topHorizontalSplitPane_.setLeftComponent(calendarControlsPanel_);

    eventsAndCalendarVerticalSplitPane_.setDividerLocation(140);
    eventsAndCalendarVerticalSplitPane_.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

    calendarPanel_.addComponentListener(new java.awt.event.ComponentAdapter() {
      public void componentResized(java.awt.event.ComponentEvent evt) {
        calendarPanel_ComponentResized(evt);
      }
    });

    daysPanel_.addComponentListener(new java.awt.event.ComponentAdapter() {
      public void componentResized(java.awt.event.ComponentEvent evt) {
        daysPanel_ComponentResized(evt);
      }
    });

    javax.swing.GroupLayout daysPanel_Layout = new javax.swing.GroupLayout(daysPanel_);
    daysPanel_.setLayout(daysPanel_Layout);
    daysPanel_Layout.setHorizontalGroup(
      daysPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 0, Short.MAX_VALUE)
    );
    daysPanel_Layout.setVerticalGroup(
      daysPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 523, Short.MAX_VALUE)
    );

    decrementButton_.setText("<");
    decrementButton_.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        decrementButton_ActionPerformed(evt);
      }
    });

    todayButton_.setText("Today");
    todayButton_.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        todayButton_ActionPerformed(evt);
      }
    });

    incrementButton_.setText(">");
    incrementButton_.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        incrementButton_ActionPerformed(evt);
      }
    });

    daysPanelLabel_.setFont(new java.awt.Font("Dialog", 0, 14)); // NOI18N
    daysPanelLabel_.setText("September 2017");

    javax.swing.GroupLayout calendarPanel_Layout = new javax.swing.GroupLayout(calendarPanel_);
    calendarPanel_.setLayout(calendarPanel_Layout);
    calendarPanel_Layout.setHorizontalGroup(
      calendarPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(daysPanel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      .addGroup(calendarPanel_Layout.createSequentialGroup()
        .addGap(5, 5, 5)
        .addComponent(decrementButton_)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(todayButton_)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(incrementButton_)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(daysPanelLabel_)
        .addContainerGap(715, Short.MAX_VALUE))
    );
    calendarPanel_Layout.setVerticalGroup(
      calendarPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, calendarPanel_Layout.createSequentialGroup()
        .addGap(5, 5, 5)
        .addGroup(calendarPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(decrementButton_)
          .addComponent(todayButton_)
          .addComponent(incrementButton_)
          .addComponent(daysPanelLabel_))
        .addGap(25, 25, 25)
        .addComponent(daysPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
    );

    eventsAndCalendarVerticalSplitPane_.setBottomComponent(calendarPanel_);

    jScrollPane1.setBorder(null);

    eventDetailTextPane_.setEditable(false);
    eventDetailTextPane_.setContentType("text/html"); // NOI18N
    jScrollPane1.setViewportView(eventDetailTextPane_);

    eventsAndCalendarVerticalSplitPane_.setLeftComponent(jScrollPane1);

    topHorizontalSplitPane_.setRightComponent(eventsAndCalendarVerticalSplitPane_);

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
    getContentPane().setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(topHorizontalSplitPane_)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(topHorizontalSplitPane_)
    );

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void daysPanel_ComponentResized(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_daysPanel_ComponentResized
  {//GEN-HEADEREND:event_daysPanel_ComponentResized
    if (nWeekRows_ == 0)
      // Not initialized yet.
      return;

    int width = daysPanel_.getSize().width;
    int height = daysPanel_.getSize().height;
    if (width <= 0 || height <= 0)
      // We don't expect this to happen. Nothing to show.
      return;

    int nDaysInWeek = 7;
    int dayPanelWidth = width / nDaysInWeek;
    int dayPanelHeight = height / nWeekRows_;

    // Resize all visible day panels.
    for (int iWeek = 0; iWeek < nWeekRows_; ++iWeek) {
      ArrayList<DayPanel> week = daysPanelGrid_.get(iWeek);

      for (int iDay = 0; iDay < 7; ++iDay) {
        DayPanel dayPanel = week.get(iDay);
        dayPanel.setSize(dayPanelWidth, dayPanelHeight);
        dayPanel.setLocation(dayPanelWidth * iDay, dayPanelHeight * iWeek);
      }
    }

    // Resize the headers.
    int headerHeight = 20;
    int headerY = daysPanel_.getLocation().y - headerHeight;
    int firstHeaderX = daysPanel_.getLocation().x;
    for (int i = 0; i < 7; ++i) {
      JLabel header = daysPanelHeaders_.get(i);
      header.setSize(dayPanelWidth, headerHeight);
      header.setLocation(firstHeaderX + dayPanelWidth * i, headerY);
    }
  }//GEN-LAST:event_daysPanel_ComponentResized

  private void decrementButton_ActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_decrementButton_ActionPerformed
  {//GEN-HEADEREND:event_decrementButton_ActionPerformed
    selectedDate_ = selectedDate_.plusMonths(-1);
    setUpDaysPanel();
  }//GEN-LAST:event_decrementButton_ActionPerformed

  private void incrementButton_ActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_incrementButton_ActionPerformed
  {//GEN-HEADEREND:event_incrementButton_ActionPerformed
    selectedDate_ = selectedDate_.plusMonths(1);
    setUpDaysPanel();
  }//GEN-LAST:event_incrementButton_ActionPerformed

  private void todayButton_ActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_todayButton_ActionPerformed
  {//GEN-HEADEREND:event_todayButton_ActionPerformed
    selectedDate_ = LocalDate.now();
    setUpDaysPanel();
  }//GEN-LAST:event_todayButton_ActionPerformed

  private void calendarPanel_ComponentResized(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_calendarPanel_ComponentResized
  {//GEN-HEADEREND:event_calendarPanel_ComponentResized
    if (!isVisible())
      // Only resize after the form is laid out.
      return;

    daysPanel_.setSize
      (calendarPanel_.getSize().width,
       calendarPanel_.getSize().height - daysPanel_.getLocation().y);
  }//GEN-LAST:event_calendarPanel_ComponentResized

  private void newEventMenuItem_ActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_newEventMenuItem_ActionPerformed
  {//GEN-HEADEREND:event_newEventMenuItem_ActionPerformed
    new NewEventDialog
      (this, store_, preferences_, selectedDate_).setVisible(true);
  }//GEN-LAST:event_newEventMenuItem_ActionPerformed

  /**
   * @param args the command line arguments
   */
  public static void main(String args[])
  {
    /* Set the Nimbus look and feel */
    //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
    /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
     */
    try {
      for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
        if ("Nimbus".equals(info.getName())) {
          javax.swing.UIManager.setLookAndFeel(info.getClassName());
          break;
        }
      }
    } catch (ClassNotFoundException ex) {
      java.util.logging.Logger.getLogger(NuvlCalendarFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    } catch (InstantiationException ex) {
      java.util.logging.Logger.getLogger(NuvlCalendarFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    } catch (IllegalAccessException ex) {
      java.util.logging.Logger.getLogger(NuvlCalendarFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    } catch (javax.swing.UnsupportedLookAndFeelException ex) {
      java.util.logging.Logger.getLogger(NuvlCalendarFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    }
    //</editor-fold>

    /* Create and display the form */
    java.awt.EventQueue.invokeLater(new Runnable() {
      public void run()
      {
        new NuvlCalendarFrame(null, new NuvlWorldPreferences("")).setVisible(true);
      }
    });
  }

  /**
   * A DayPanel holds the main panel for a day plus its contained components.
   */
  private static class DayPanel implements ListSelectionListener {
    public DayPanel(NuvlCalendarFrame parent)
    {
      parent_ = parent;

      panel_.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
      panel_.setComponentPopupMenu(parent.dayPopupMenu_);

      final int labelHeight = 20;
      dayLabel_.setForeground(new Color(100, 100, 100));
      dayLabel_.setLocation(0, 0);
      dayLabel_.setSize(50, labelHeight);
      panel_.add(dayLabel_);

      entries_.setCellRenderer(new EntryCellRenderer());
      entries_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      entries_.addListSelectionListener(this);

      scrollPane_.setBorder(BorderFactory.createEmptyBorder());
      scrollPane_.setViewportView(entries_);
      scrollPane_.setLocation(0, dayLabel_.getLocation().y + labelHeight);
      scrollPane_.getViewport().setInheritsPopupMenu(true);
      panel_.add(scrollPane_);

      setChildrenInheritsPopupMenu(panel_, true);
    }

    public static class Entry implements Comparable<Entry> {
      public Entry(EventTimeInterval timeInterval, String label)
      {
        this.timeInterval = timeInterval;
        this.label = label;

        if (label.startsWith("<-> "))
          labelRank_ = 1;
        else if (label.startsWith("> "))
          labelRank_ = 2;
        else
          labelRank_ = 3;
      }

      // Define toString for display.
      @Override
      public String toString() { return label; }

      // Choose the compare order for display.
      @Override
      public int compareTo(Entry other)
      {
        if (other == this)
          return 0;

        int rankComparison = Integer.compare(labelRank_, other.labelRank_);
        if (rankComparison != 0)
          return rankComparison;

        if (label.startsWith("<-> "))
          return label.compareTo(other.label);
        else if (label.startsWith("> "))
          return Long.compare(timeInterval.endUtcMillis, other.timeInterval.endUtcMillis);
        else
          return Long.compare(timeInterval.startUtcMillis, other.timeInterval.startUtcMillis);
      }

      public final EventTimeInterval timeInterval;
      public final String label;
      private final int labelRank_;
    }

    private class EntryCellRenderer extends JLabel implements ListCellRenderer {
      public EntryCellRenderer()
      {
        setOpaque(true);
        //setIconTextGap(12);
      }

      public Component getListCellRendererComponent
        (JList list, Object value, int index, boolean isSelected,
         boolean cellHasFocus) {
        Entry entry = (Entry) value;
        setText(entry.toString());
        //setIcon(entry.getIcon());

        boolean isGrounded = parent_.groundedAttrs_.contains
          (entry.timeInterval.event);
        Color color = isGrounded ? Color.black : Color.red;
        if (isSelected) {
          setBackground(color);
          setForeground(Color.white);
        }
        else {
          setBackground(Color.white);
          setForeground(color);
        }

        return this;
      }
    }

    public void addTo(Container container) { container.add(panel_); }

    public void setVisible(boolean visible) { panel_.setVisible(visible); }

    public void
    setSize(int width, int height)
    {
      panel_.setSize(width, height);
      scrollPane_.setSize
        (width - 1,
         height - 1 - (dayLabel_.getLocation().y + dayLabel_.getSize().height));
    }

    public void
    setLocation(int x, int y) { panel_.setLocation(x, y); }

    public void
    setDayText(String text) { dayLabel_.setText(text); }

    public void
    setEntries(Entry[] entries) { entries_.setListData(entries); }

    /**
     * This is called when the entries_ selection changes. 
     */
    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (e.getValueIsAdjusting())
        return;
      if (entries_.getSelectedIndex() < 0)
        // This also prevents recursive calls.
        return;

      // Clear the selection for other DayPanel instances.
      for (ArrayList<DayPanel> row : parent_.daysPanelGrid_) {
        for (DayPanel day : row) {
          if (day != this)
            day.entries_.clearSelection();
        }
      }

      parent_.setUpEventDetail(entries_.getSelectedValue());
    }

    /**
     * Recursively call setInheritsPopupMenu(value) on all children of component.
     */
    private static void
    setChildrenInheritsPopupMenu(JComponent component, boolean value)
    {
      for (Component child : component.getComponents()) {
        if (child instanceof JComponent) {
          JComponent jChild = (JComponent)child;
          ((JComponent) child).setInheritsPopupMenu(value);

          setChildrenInheritsPopupMenu(jChild, value);
        }
      }
    }

    private final NuvlCalendarFrame parent_;
    public static final Color BORDER_COLOR = new Color(200, 200, 200);
    private final JPanel panel_ = new JPanel(null);
    private final JLabel dayLabel_ = new JLabel();
    private final JScrollPane scrollPane_ = new JScrollPane();
    private final JList<Entry> entries_ = new JList<>();
  }

  /**
   * A Scenario holds the scenario results based on a preferred extension.
   */
  private static class Scenario {
    /**
     * Create a new Scenario for the preferredExtension.
     * @param preferredExtensionScala The preferred extension as a Scala set.
     * This is converted to a Java Set and saved as preferredExtension.
     * @param framework The NuvlFramework that the preferred extension came from.
     * @param groundedExtension The pre-computed grounded extension which is the
     * intersection of the preferred extensions, and converted to a Java Set.
     */
    public Scenario
      (scala.collection.immutable.Set<Sentence> preferredExtensionScala,
       NuvlFramework framework, Set<Sentence> groundedExtension) {
      preferredExtension = new HashSet<>(JavaConversions.asJavaCollection
        (preferredExtensionScala));

      // TODO: Compute this directly from framework.groundedExtension().
      conflictingAssumptions = new HashSet<>(preferredExtension);
      conflictingAssumptions.removeAll(groundedExtension);

      // Get all $Attr in the deductions of the extension which match (attr $Attr).
      for (Sentence deduction : JavaConversions.asJavaCollection
           (framework.aba().generate_all_deductions(preferredExtensionScala))) {
        if (deduction.is_contrary())
          continue;

        Matcher matcher = attrPattern_.matcher(deduction.symbol());
        if (matcher.find())
          deducedAttrs.add(matcher.group(1));
      }
    }

    public final Set<Sentence> preferredExtension;
    public final Set<Sentence> conflictingAssumptions;
    public final Set<String> deducedAttrs = new HashSet<>();
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel calendarControlsPanel_;
  private javax.swing.JPanel calendarPanel_;
  private javax.swing.JPopupMenu dayPopupMenu_;
  private javax.swing.JLabel daysPanelLabel_;
  private javax.swing.JPanel daysPanel_;
  private javax.swing.JButton decrementButton_;
  private javax.swing.JTextPane eventDetailTextPane_;
  private javax.swing.JSplitPane eventsAndCalendarVerticalSplitPane_;
  private javax.swing.JButton incrementButton_;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JMenuItem newEventMenuItem_;
  private javax.swing.JTextPane scenariosTextPane_;
  private javax.swing.JButton todayButton_;
  private javax.swing.JSplitPane topHorizontalSplitPane_;
  // End of variables declaration//GEN-END:variables
  private final NuvlWorldStore store_;
  private final NuvlWorldPreferences preferences_;
  private final ArrayList<ArrayList<DayPanel>> daysPanelGrid_ = new ArrayList<>();
  private final ArrayList<JLabel> daysPanelHeaders_ = new ArrayList<>();
  private final ArrayList<Scenario> scenarios_ = new ArrayList<>();
  private final HashSet<String> groundedAttrs_ = new HashSet<>();
  private int selectedScenarioNumber = 1;
  private LocalDate selectedDate_ = LocalDate.now();
  private LocalDate daysPanelPreviousDate_ = LocalDate.of(1900, 1, 1);
  private int nWeekRows_ = 0;
  private final JDatePanelImpl datePanel_;
  private static final DateTimeFormatter monthAndDayFormatter_ =
    DateTimeFormatter.ofPattern("MMM d");
  private static final DateTimeFormatter monthAndYearFormatter_ =
    DateTimeFormatter.ofPattern("MMMM y");
  private static final DateTimeFormatter dayOfWeekFormatter_ =
    DateTimeFormatter.ofPattern("EEEE");
  private static Pattern attrPattern_ =
    Pattern.compile("^\\(attr (" + TERM + ")\\)$");
}
