//
// Copyright 2024 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
//    (at your option) any later version.
//
//    LaBB-CAT is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with LaBB-CAT; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//

package nzilbb.labbcat.server.api;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Layer;
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.PatternBuilder;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.model.Match;
import nzilbb.labbcat.model.TaskStatus;
import nzilbb.util.IO;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestPraat {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String readOnlyUsername = "readOnly";
  static String readOnlyPassword = "labbcat";
  static String readWriteUsername = "labbcat";
  static String readWritePassword = "labbcat";

  /** Test that the Process with Praat endpoint generally works. */
  @Test public void processWithPraat()
    throws Exception {
    LabbcatView l = new LabbcatView(labbcatUrl, readOnlyUsername, readOnlyPassword);
    l.setBatchMode(true);

    // get a participant ID to use
    String[] ids = l.getParticipantIds();
    assertTrue("getParticipantIds: Some IDs are returned",
               ids.length > 0);
    String[] participantId = { ids[0] };      

    // all instances of any segment
    JsonObject pattern = new PatternBuilder().addMatchLayer("segment", ".*").build();
    String searchThreadId = l.search(pattern, participantId, null, false, null, null, null);
    try {
      TaskStatus task = l.waitForTask(searchThreadId, 30);
      // if the task is still running, it's taking too long, so cancel it
      if (task.getRunning()) {
        try { l.cancelTask(searchThreadId); } catch(Exception exception) {}
      }
      assertFalse("Search task finished in a timely manner",
                  task.getRunning());
      
      Match[] matches = l.getMatches(searchThreadId, 2);
      if (matches.length == 0) {
        fail("getMatches: No matches were returned, cannot test processWithPraat");
      } else {
        int upTo = Math.min(5, matches.length);
        Match[] subset = Arrays.copyOfRange(matches, 0, upTo);
        String[] matchIds = Arrays.stream(subset)
          .map(match -> match.getMatchId())
          .collect(Collectors.toList()).toArray(new String[0]);
        Double[] startOffsets = Arrays.stream(subset)
          .map(match -> match.getLine())
          .collect(Collectors.toList()).toArray(new Double[0]);
        Double[] endOffsets = Arrays.stream(subset)
          .map(match -> match.getLineEnd())
          .collect(Collectors.toList()).toArray(new Double[0]);
        String script = "test$ = \"test\"\nprint 'test$' 'newline$'";
        String praatThreadId = l.processWithPraat(
          matchIds, startOffsets, endOffsets, script, 0.0, null);
        try {
          task = l.waitForTask(praatThreadId, 30);
          // if the task is still running, it's taking too long, so cancel it
          if (task.getRunning()) {
            try { l.cancelTask(praatThreadId); } catch(Exception exception) {}
          }
          assertFalse("Search task finished in a timely manner",
                      task.getRunning());
          
          assertTrue("Output is a CSV URL: " + task.getResultUrl(),
                     task.getResultUrl().endsWith(".csv"));

          // download URL
          HttpRequestGet request = new HttpRequestGet(
            task.getResultUrl(), l.getRequiredHttpAuthorization());
          String csv = IO.InputStreamToString(request.get().getInputStream());
          assertEquals("CSV result is correct: " + csv,
                       "test,Error\n\"test \",\n\"test \",\n\"test \",\n\"test \",\n\"test \",",
                       csv);          
        } finally {
          l.releaseTask(praatThreadId);
        }
      }
    } finally {
      l.releaseTask(searchThreadId);
    }
  }

  /** Test that the Process with Praat endpoint doesn't allow reading/creating/deleting
   * files on the server. */
  @Test public void fileOperationsDisallowed() throws Exception {
    LabbcatView readOnlyLabbcat = new LabbcatView(
      labbcatUrl, readOnlyUsername, readOnlyPassword);
    readOnlyLabbcat.setBatchMode(true);
    LabbcatView readWriteLabbcat = new LabbcatView(
      labbcatUrl, readWriteUsername, readWritePassword);
    readWriteLabbcat.setBatchMode(true);
    
    // get a participant ID to use
    String[] ids = readOnlyLabbcat.getParticipantIds();
    assertTrue("getParticipantIds: Some IDs are returned",
               ids.length > 0);
    String[] participantId = { ids[0] };      

    // all instances of any segment
    JsonObject pattern = new PatternBuilder().addMatchLayer("segment", ".*").build();
    String searchThreadId = readOnlyLabbcat.search(pattern, participantId, null, false, null, null, null);
    try {
      TaskStatus task = readOnlyLabbcat.waitForTask(searchThreadId, 30);
      // if the task is still running, it's taking too long, so cancel it
      if (task.getRunning()) {
        try { readOnlyLabbcat.cancelTask(searchThreadId); } catch(Exception exception) {}
      }
      assertFalse("Search task finished in a timely manner",
                  task.getRunning());
      
      Match[] matches = readOnlyLabbcat.getMatches(searchThreadId, 2);
      if (matches.length == 0) {
        fail("getMatches: No matches were returned for "+ids[0]+", cannot test processWithPraat");
      } else {
        int upTo = Math.min(5, matches.length);
        Match[] subset = Arrays.copyOfRange(matches, 0, upTo);
        String[] matchIds = Arrays.stream(subset)
          .map(match -> match.getMatchId())
          .collect(Collectors.toList()).toArray(new String[0]);
        Double[] startOffsets = Arrays.stream(subset)
          .map(match -> match.getLine())
          .collect(Collectors.toList()).toArray(new Double[0]);
        Double[] endOffsets = Arrays.stream(subset)
          .map(match -> match.getLineEnd())
          .collect(Collectors.toList()).toArray(new Double[0]);
        String[] fileAccessScripts = {
          
          // read a server file
          
          "file$ = readFile$ (\"proc_textGridChopper.praat\")" // an existing FastTrack file
          +"\nprint 'file$' 'newline$'",
          
          // create a file on the server
          
          "fileName$ = \"TestWriteFile.txt\""
          +"\nwriteFile: fileName$, \"Created by test\""
          +"\nprint 'fileName$' 'newline$'",
          
          "fileName$ = \"TestWriteFileLine.txt\""
          +"\nwriteFileLine: fileName$, \"Created by test\""
          +"\nprint 'fileName$' 'newline$'",
          
          // update a file on the server
          
          "fileName$ = \"TestAppendFile.txt\""
          +"\nappendFile: fileName$, \"Appended by test\""
          +"\nprint 'fileName$' 'newline$'",
          
          "fileName$ = \"TestAppendFileLine.txt\""
          +"\nappendFileLine: fileName$, \"Appended by test\""
          +"\nprint 'fileName$' 'newline$'",
          
          // create a directory on the server
          
          "dirName$ = \"testCreateFolder\""
          +"\ncreateFolder: dirName$"
          +"\nprint 'dirName$' 'newline$'",
          
          // delete a file on the server
          
          "fileName$ = \"TestDeleteFile.txt\""
          +"\ndeleteFile: dirName$"
          +"\nprint 'fileName$' 'newline$'"
          
        };
        for (String script : fileAccessScripts) {
          
          // allowed for read/write user
          String praatThreadId = readWriteLabbcat.processWithPraat(
            matchIds, startOffsets, endOffsets, script, 0.0, null);
          try {
            task = readWriteLabbcat.waitForTask(praatThreadId, 30);
            // if the task is still running, it's taking too long, so cancel it
            if (task.getRunning()) {
              try { readWriteLabbcat.cancelTask(praatThreadId); } catch(Exception exception) {}
            }
            assertFalse("Search task finished in a timely manner",
                        task.getRunning());            
            assertTrue("Output is a CSV URL: " + task.getResultUrl(),
                       task.getResultUrl().endsWith(".csv"));
          } finally {
            readWriteLabbcat.releaseTask(praatThreadId);
          }

          // disallowed for read-only user
          try {
            praatThreadId = readOnlyLabbcat.processWithPraat(
              matchIds, startOffsets, endOffsets, script, 0.0, null);
            readOnlyLabbcat.cancelTask(praatThreadId);
            fail("Script should be disallowed:\n"+script);
          } catch(StoreException exception) {
          }
        } // next script
      }
    } finally {
      readOnlyLabbcat.releaseTask(searchThreadId);
    }
  }  
  
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.TestPraat");
  }
}
