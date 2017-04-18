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

import org.nuvl.nuvlworld.gui.NuvlCalendarFrame;

/**
 * NuvlWorldApp has the main method which creates the main window and starts the
 * application.
 * @author Jeff Thompson, jeff@thefirst.org
 */
public class NuvlWorldApp {
  /**
   * This the main entry for the application.
   * @param args The command line arguments.
   */
  public static void main (String args[])
  {
    NuvlWorldPreferences preferences = new NuvlWorldPreferences("nuvl:jefft0");
    NuvlWorldStore store = new NuvlWorldStore();

    try {
      NuvlCalendarFrame frame = new NuvlCalendarFrame();
      frame.pack();
      frame.setVisible(true);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}
