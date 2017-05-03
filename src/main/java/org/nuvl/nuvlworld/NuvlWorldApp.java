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

import java.io.File;
import java.io.IOException;
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
  public static void main (String args[]) throws IOException
  {
    NuvlWorldPreferences preferences = new NuvlWorldPreferences("Jefft0");
    NuvlWorldStore store = new NuvlWorldStore();

    String wikidataDir = "/home/jeff/wikidata";
    store.loadSchemeFile(new File(wikidataDir, "locationIanaTimeZone.scm").getAbsolutePath());
    store.loadSchemeFile(new File(wikidataDir, "iataAirportCode.scm").getAbsolutePath());
    store.loadSchemeFile(new File(wikidataDir, "ianaTimeZoneInstanceOf.scm").getAbsolutePath());
    store.loadSchemeFile(new File(wikidataDir, "jefft0.scm").getAbsolutePath());
    store.loadWikidataDescriptions
      (new File(wikidataDir, "itemEnLabel.tsv").getAbsolutePath());

    try {
      NuvlCalendarFrame frame = new NuvlCalendarFrame(store, preferences);
      frame.pack();
      frame.setVisible(true);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}
