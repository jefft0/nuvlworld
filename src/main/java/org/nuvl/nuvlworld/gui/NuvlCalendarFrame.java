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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import org.jdatepicker.impl.JDatePanelImpl;
import org.jdatepicker.impl.UtilCalendarModel;
import org.nuvl.nuvlworld.NuvlWorldPreferences;
import org.nuvl.nuvlworld.NuvlWorldStore;
import org.nuvl.nuvlworld.NuvlWorldStore.EventTimeInterval;

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

    // Initialize the day panel grid. A month has up to six rows of weeks.
    for (int iWeek = 0; iWeek < 6; ++iWeek) {
      ArrayList<DayPanel> week = new ArrayList<>();
      daysPanelGrid_.add(week);

      for (int iDay = 0; iDay < 7; ++iDay) {
        DayPanel dayPanel = new DayPanel(dayPopupMenu_);
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

    eventsList_.setModel(eventsListModel_);

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
    datePanel_.setSize(190, 170);
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

    setUpDaysPanel();

    pack();
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

            Matcher labelMatcher = store_.findFirst
              ("description", NuvlWorldStore.stringPattern_, 2, event);
            String label = labelMatcher == null ? "unnamed"
              : NuvlWorldStore.removeQuotes(labelMatcher.group(3));

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
             (timeInterval, displayTime + label);
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

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {

    dayPopupMenu_ = new javax.swing.JPopupMenu();
    newEventMenuItem_ = new javax.swing.JMenuItem();
    topHorizontalSplitPane_ = new javax.swing.JSplitPane();
    calendarControlsPanel_ = new javax.swing.JPanel();
    eventsAndCalendarVerticalSplitPane_ = new javax.swing.JSplitPane();
    calendarPanel_ = new javax.swing.JPanel();
    daysPanel_ = new javax.swing.JPanel();
    decrementButton_ = new javax.swing.JButton();
    todayButton_ = new javax.swing.JButton();
    incrementButton_ = new javax.swing.JButton();
    daysPanelLabel_ = new javax.swing.JLabel();
    eventsScrollPane_ = new javax.swing.JScrollPane();
    eventsList_ = new javax.swing.JList<>();

    newEventMenuItem_.setText("New Event...");
    dayPopupMenu_.add(newEventMenuItem_);

    setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
    setTitle("Calendar - Nuvl");

    topHorizontalSplitPane_.setDividerLocation(190);

    javax.swing.GroupLayout calendarControlsPanel_Layout = new javax.swing.GroupLayout(calendarControlsPanel_);
    calendarControlsPanel_.setLayout(calendarControlsPanel_Layout);
    calendarControlsPanel_Layout.setHorizontalGroup(
      calendarControlsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 189, Short.MAX_VALUE)
    );
    calendarControlsPanel_Layout.setVerticalGroup(
      calendarControlsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGap(0, 689, Short.MAX_VALUE)
    );

    topHorizontalSplitPane_.setLeftComponent(calendarControlsPanel_);

    eventsAndCalendarVerticalSplitPane_.setDividerLocation(100);
    eventsAndCalendarVerticalSplitPane_.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

    calendarPanel_.addComponentListener(new java.awt.event.ComponentAdapter()
    {
      public void componentResized(java.awt.event.ComponentEvent evt)
      {
        calendarPanel_ComponentResized(evt);
      }
    });

    daysPanel_.addComponentListener(new java.awt.event.ComponentAdapter()
    {
      public void componentResized(java.awt.event.ComponentEvent evt)
      {
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
    decrementButton_.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        decrementButton_ActionPerformed(evt);
      }
    });

    todayButton_.setText("Today");
    todayButton_.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        todayButton_ActionPerformed(evt);
      }
    });

    incrementButton_.setText(">");
    incrementButton_.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
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
        .addContainerGap(714, Short.MAX_VALUE))
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

    eventsScrollPane_.setBorder(null);

    eventsList_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    eventsScrollPane_.setViewportView(eventsList_);

    eventsAndCalendarVerticalSplitPane_.setLeftComponent(eventsScrollPane_);

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
    daysPanel_.setSize
      (calendarPanel_.getSize().width,
       calendarPanel_.getSize().height - daysPanel_.getLocation().y);
  }//GEN-LAST:event_calendarPanel_ComponentResized

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

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel calendarControlsPanel_;
  private javax.swing.JPanel calendarPanel_;
  private javax.swing.JPopupMenu dayPopupMenu_;
  private javax.swing.JLabel daysPanelLabel_;
  private javax.swing.JPanel daysPanel_;
  private javax.swing.JButton decrementButton_;
  private javax.swing.JSplitPane eventsAndCalendarVerticalSplitPane_;
  private javax.swing.JList<String> eventsList_;
  private javax.swing.JScrollPane eventsScrollPane_;
  private javax.swing.JButton incrementButton_;
  private javax.swing.JMenuItem newEventMenuItem_;
  private javax.swing.JButton todayButton_;
  private javax.swing.JSplitPane topHorizontalSplitPane_;
  // End of variables declaration//GEN-END:variables
  private final NuvlWorldStore store_;
  private final NuvlWorldPreferences preferences_;
  private final DefaultListModel<String> eventsListModel_ = new DefaultListModel<>();
  private final ArrayList<ArrayList<DayPanel>> daysPanelGrid_ = new ArrayList();
  private final ArrayList<JLabel> daysPanelHeaders_ = new ArrayList();
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
}

/**
 * A DayPanel holds the main panel for a day plus its contained components.
 */
class DayPanel {
  public DayPanel(JPopupMenu popupMenu)
  {
    panel_.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
    panel_.setComponentPopupMenu(popupMenu);

    final int labelHeight = 20;
    dayLabel_.setForeground(new Color(100, 100, 100));
    dayLabel_.setLocation(0, 0);
    dayLabel_.setSize(50, labelHeight);
    panel_.add(dayLabel_);

    scrollPane_.setBorder(BorderFactory.createEmptyBorder());
    scrollPane_.setViewportView(entries_);
    scrollPane_.setLocation(0, dayLabel_.getLocation().y + labelHeight);
    scrollPane_.getViewport().setInheritsPopupMenu(true);
    panel_.add(scrollPane_);

    setChildrenInheritsPopupMenu(panel_, true);
  }

  /**
   * Recursively call setInheritsPopupMenu(value) on all children of component.
   */
  public static void
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

  public static class Entry implements Comparable<Entry> {
    public Entry(EventTimeInterval timeInterval, String label)
    {
      this.timeInterval = timeInterval;
      this.label = label;

      if (label.startsWith("<-> "))
        labelRank = 1;
      else if (label.startsWith("> "))
        labelRank = 2;
      else
        labelRank = 3;
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

      int rankComparison = Integer.compare(labelRank, other.labelRank);
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
    private final int labelRank;
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

  public static final Color BORDER_COLOR = new Color(200, 200, 200);
  private final JPanel panel_ = new JPanel(null);
  private final JLabel dayLabel_ = new JLabel();
  private final JScrollPane scrollPane_ = new JScrollPane();
  private final JList<Entry> entries_ = new JList<>();
}
