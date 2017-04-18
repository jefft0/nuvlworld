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

import java.time.DayOfWeek;
import java.util.TimeZone;

/**
 * A NuvlWorldPreferences holds user preferences for the Nuvl World application.
 * @author Jeff Thompson, jeff@thefirst.org
 */
public class NuvlWorldPreferences {
  public NuvlWorldPreferences(String username)
  {
    username_ = username;
  }

  public String getUsername() { return username_; }

  public TimeZone getTimeZone() { return timeZone_; }

  /**
   * Get the start day of the week for displaying a week.
   * @return The start day as a DayOfWeek.
   */
  public DayOfWeek getStartOfWeek() { return startOfWeek_; }

  private final String username_;
  private TimeZone timeZone_ = TimeZone.getDefault();
  private DayOfWeek startOfWeek_ = DayOfWeek.MONDAY;
}
