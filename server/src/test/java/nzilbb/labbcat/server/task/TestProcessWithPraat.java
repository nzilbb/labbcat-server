//
// Copyright 2020-2024 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of nzilbb.ag.
//
//    nzilbb.ag is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 3 of the License, or
//    (at your option) any later version.
//
//    nzilbb.ag is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with nzilbb.ag; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//

package nzilbb.labbcat.server.task;

import org.junit.*;
import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import nzilbb.editpath.EditStep;
import nzilbb.editpath.MinimumEditPath;

/** Tests Praat script generation with different parameters. */
public class TestProcessWithPraat {

  /** Basic script with default settings - i.e. classic F1/F2 from midpoint */
  @Test public void defaultFormantsScript() throws Exception {
    File dir = getDir();
    File wav = new File(dir, "text.wav"); // doesn't exist, doesn't matter
    File actual = new File(dir, "default.praat");
    PrintWriter scriptWriter = new PrintWriter(actual, "UTF-8");
    File expected = new File(dir, "expected_default.praat");

    Vector<Vector<Double>> targets = new Vector<Vector<Double>>() {{
        add(new Vector<Double>(){{ add(0.1); add(0.2); }});
        add(new Vector<Double>(){{ add(3.0); add(4.0); }});
      }};
    HashMap<String,String> attributeValues = new HashMap<String,String>() {{
        put("participant_gender", "F");
      }};
    
    ProcessWithPraat p = new ProcessWithPraat();
    p.generateScript(
      scriptWriter, dir,
      wav, targets, p.getFormantCeilingDefault(),
      p.getPitchFloorDefault(), p.getPitchCeilingDefault(), p.getVoicingThresholdDefault(),
      p.getIntensityPitchFloorDefault(), p.getFastTrackLowestAnalysisFrequencyDefault(),
      p.getFastTrackHighestAnalysisFrequencyDefault(),
      attributeValues);
    scriptWriter.close();
    
    String differences = diff(expected, actual, ".*nzilbb/labbcat/server/task.*");
    if (differences != null) {
      fail(differences);
    } else {
      actual.delete();
    }
  }

  /** Formant measures from multiple sample points */
  @Test public void formants3SamplePointScript() throws Exception {
    File dir = getDir();
    File wav = new File(dir, "text.wav"); // doesn't exist, doesn't matter
    File actual = new File(dir, "samplepoints.praat");
    PrintWriter scriptWriter = new PrintWriter(actual, "UTF-8");
    File expected = new File(dir, "expected_samplepoints.praat");

    Vector<Vector<Double>> targets = new Vector<Vector<Double>>() {{
        add(new Vector<Double>(){{ add(0.1); add(0.2); }});
        add(new Vector<Double>(){{ add(3.0); add(4.0); }});
      }};
    HashMap<String,String> attributeValues = new HashMap<String,String>() {{
        put("participant_gender", "F");
      }};
    
    ProcessWithPraat p = new ProcessWithPraat()
      .setWindowOffset(0)
      // ensure an empty (as opposed to null) script doesn't generate extra code
      .setCustomScript("");
    
    p.getSamplePoints().add(0.25);
    // 0.5 is already there by default
    p.getSamplePoints().add(0.75);
    
    p.generateScript(
      scriptWriter, dir,
      wav, targets, p.getFormantCeilingDefault(),
      p.getPitchFloorDefault(), p.getPitchCeilingDefault(), p.getVoicingThresholdDefault(),
      p.getIntensityPitchFloorDefault(), p.getFastTrackLowestAnalysisFrequencyDefault(),
      p.getFastTrackHighestAnalysisFrequencyDefault(),
      attributeValues);
    scriptWriter.close();
    
    String differences = diff(expected, actual, ".*nzilbb/labbcat/server/task.*");
    if (differences != null) {
      fail(differences);
    } else {
      actual.delete();
    }
  }

  /** Min/mean/max pitch script */
  @Test public void pitchScript() throws Exception {
    File dir = getDir();
    File wav = new File(dir, "text.wav"); // doesn't exist, doesn't matter
    File actual = new File(dir, "pitch.praat");
    PrintWriter scriptWriter = new PrintWriter(actual, "UTF-8");
    File expected = new File(dir, "expected_pitch.praat");

    Vector<Vector<Double>> targets = new Vector<Vector<Double>>() {{
        add(new Vector<Double>(){{ add(0.1); add(0.2); }});
        add(new Vector<Double>(){{ add(3.0); add(4.0); }});
      }};
    HashMap<String,String> attributeValues = new HashMap<String,String>() {{
        put("participant_gender", "F");
      }};
    
    ProcessWithPraat p = new ProcessWithPraat()
      .setExtractF1(false)
      .setExtractF2(false)
      .setExtractMinimumPitch(true)
      .setExtractMeanPitch(true)
      .setExtractMaximumPitch(true)
      .setWindowOffset(0);
    p.generateScript(
      scriptWriter, dir,
      wav, targets, p.getFormantCeilingDefault(),
      p.getPitchFloorDefault(), p.getPitchCeilingDefault(), p.getVoicingThresholdDefault(),
      p.getIntensityPitchFloorDefault(), p.getFastTrackLowestAnalysisFrequencyDefault(),
      p.getFastTrackHighestAnalysisFrequencyDefault(),
      attributeValues);
    scriptWriter.close();
    
    String differences = diff(expected, actual, ".*nzilbb/labbcat/server/task.*");
    if (differences != null) {
      fail(differences);
    } else {
      actual.delete();
    }
  }
  
  /** Max intensity script */
  @Test public void intensityScript() throws Exception {
    File dir = getDir();
    File wav = new File(dir, "text.wav"); // doesn't exist, doesn't matter
    File actual = new File(dir, "intensity.praat");
    PrintWriter scriptWriter = new PrintWriter(actual, "UTF-8");
    File expected = new File(dir, "expected_intensity.praat");

    Vector<Vector<Double>> targets = new Vector<Vector<Double>>() {{
        add(new Vector<Double>(){{ add(0.1); add(0.2); }});
        add(new Vector<Double>(){{ add(3.0); add(4.0); }});
      }};
    HashMap<String,String> attributeValues = new HashMap<String,String>() {{
        put("participant_gender", "F");
      }};
    
    ProcessWithPraat p = new ProcessWithPraat()
      .setExtractF1(false)
      .setExtractF2(false)
      .setExtractMaximumIntensity(true)
      .setWindowOffset(0);
    p.generateScript(
      scriptWriter, dir,
      wav, targets, p.getFormantCeilingDefault(),
      p.getPitchFloorDefault(), p.getPitchCeilingDefault(), p.getVoicingThresholdDefault(),
      p.getIntensityPitchFloorDefault(), p.getFastTrackLowestAnalysisFrequencyDefault(),
      p.getFastTrackHighestAnalysisFrequencyDefault(),
      attributeValues);
    scriptWriter.close();
    
    String differences = diff(expected, actual, ".*nzilbb/labbcat/server/task.*");
    if (differences != null) {
      fail(differences);
    } else {
      actual.delete();
    }
  }

  /** Centre of Gravity script */
  @Test public void cogScript() throws Exception {
    File dir = getDir();
    File wav = new File(dir, "text.wav"); // doesn't exist, doesn't matter
    File actual = new File(dir, "cog.praat");
    PrintWriter scriptWriter = new PrintWriter(actual, "UTF-8");
    File expected = new File(dir, "expected_cog.praat");

    Vector<Vector<Double>> targets = new Vector<Vector<Double>>() {{
        add(new Vector<Double>(){{ add(0.1); add(0.2); }});
        add(new Vector<Double>(){{ add(3.0); add(4.0); }});
      }};
    HashMap<String,String> attributeValues = new HashMap<String,String>() {{
        put("participant_gender", "F");
      }};
    
    ProcessWithPraat p = new ProcessWithPraat()
      .setExtractF1(false)
      .setExtractF2(false)
      .setExtractCOG1(true)
      .setExtractCOG2(true)
      .setExtractCOG23(true)
      .setWindowOffset(0);
    p.generateScript(
      scriptWriter, dir,
      wav, targets, p.getFormantCeilingDefault(),
      p.getPitchFloorDefault(), p.getPitchCeilingDefault(), p.getVoicingThresholdDefault(),
      p.getIntensityPitchFloorDefault(), p.getFastTrackLowestAnalysisFrequencyDefault(),
      p.getFastTrackHighestAnalysisFrequencyDefault(),
      attributeValues);
    scriptWriter.close();
    
    String differences = diff(expected, actual, ".*nzilbb/labbcat/server/task.*");
    if (differences != null) {
      fail(differences);
    } else {
      actual.delete();
    }
  }
  
  /** Centre of Gravity script */
  @Test public void fastTrack() throws Exception {
    File dir = getDir();
    File wav = new File(dir, "text.wav"); // doesn't exist, doesn't matter
    File actual = new File(dir, "fasttrack.praat");
    PrintWriter scriptWriter = new PrintWriter(actual, "UTF-8");
    File expected = new File(dir, "expected_fasttrack.praat");

    Vector<Vector<Double>> targets = new Vector<Vector<Double>>() {{
        add(new Vector<Double>(){{ add(0.1); add(0.2); }});
        add(new Vector<Double>(){{ add(3.0); add(4.0); }});
      }};
    HashMap<String,String> attributeValues = new HashMap<String,String>() {{
        put("participant_gender", "F");
      }};
    
    ProcessWithPraat p = new ProcessWithPraat()
      .setExtractF1(true)
      .setExtractF2(true)
      .setExtractF3(true)
      .setUseFastTrack(true)
      .setWindowOffset(0.1);
    p.generateScript(
      scriptWriter, dir,
      wav, targets, p.getFormantCeilingDefault(),
      p.getPitchFloorDefault(), p.getPitchCeilingDefault(), p.getVoicingThresholdDefault(),
      p.getIntensityPitchFloorDefault(), p.getFastTrackLowestAnalysisFrequencyDefault(),
      p.getFastTrackHighestAnalysisFrequencyDefault(),
      attributeValues);
    scriptWriter.close();
    
    String differences = diff(expected, actual, ".*nzilbb/labbcat/server/task.*");
    if (differences != null) {
      fail(differences);
    } else {
      actual.delete();
    }
  }

  /** Custom script */
  @Test public void customScript() throws Exception {
    File dir = getDir();
    File wav = new File(dir, "text.wav"); // doesn't exist, doesn't matter
    File actual = new File(dir, "custom.praat");
    PrintWriter scriptWriter = new PrintWriter(actual, "UTF-8");
    File expected = new File(dir, "expected_custom.praat");

    Vector<Vector<Double>> targets = new Vector<Vector<Double>>() {{
        add(new Vector<Double>(){{ add(0.1); add(0.2); }});
        add(new Vector<Double>(){{ add(3.0); add(4.0); }});
      }};
    HashMap<String,String> attributeValues = new HashMap<String,String>() {{
        put("participant_gender", "F");
      }};
    
    ProcessWithPraat p = new ProcessWithPraat()
      .setExtractF1(false)
      .setExtractF2(false)
      .setWindowOffset(0.5)
      .setCustomScript(
        "# get centre of gravity and spread from spectrum"
        +"\nspectrum = To Spectrum... yes"
        +"\n# filter it"
        +"\nFilter (pass Hann band)... 1000 22000 100"
        +"\n# get centre of gravity"
        +"\ncog = Get centre of gravity... 2"
        +"\n# extract the result back out into a CSV column called 'cog'"
        +"\nprint 'cog:0' 'newline$'"
        +"\n# tidy up objects"
        +"\nselect spectrum"
        +"\nRemove");
    p.generateScript(
      scriptWriter, dir,
      wav, targets, p.getFormantCeilingDefault(),
      p.getPitchFloorDefault(), p.getPitchCeilingDefault(), p.getVoicingThresholdDefault(),
      p.getIntensityPitchFloorDefault(), p.getFastTrackLowestAnalysisFrequencyDefault(),
      p.getFastTrackHighestAnalysisFrequencyDefault(),
      attributeValues);
    scriptWriter.close();
    
    String differences = diff(expected, actual, ".*nzilbb/labbcat/server/task.*");
    if (differences != null) {
      fail(differences);
    } else {
      actual.delete();
    }
  }

  /** Script with everything turned on */
  @Test public void kitchenSink() throws Exception {
    File dir = getDir();
    File wav = new File(dir, "text.wav"); // doesn't exist, doesn't matter
    File actual = new File(dir, "kitchensink.praat");
    PrintWriter scriptWriter = new PrintWriter(actual, "UTF-8");
    File expected = new File(dir, "expected_kitchensink.praat");

    Vector<Vector<Double>> targets = new Vector<Vector<Double>>() {{
        add(new Vector<Double>(){{ add(0.1); add(0.2); }});
        add(new Vector<Double>(){{ add(3.0); add(4.0); }});
      }};
    HashMap<String,String> attributeValues = new HashMap<String,String>() {{
        put("participant_gender", "F");
      }};
    
    ProcessWithPraat p = new ProcessWithPraat()
      .setExtractF1(true)
      .setExtractF2(true)
      .setExtractF3(true)
      .setUseFastTrack(true)
      .setFastTrackCoefficients(true)
      .setExtractMinimumPitch(true)
      .setExtractMeanPitch(true)
      .setExtractMaximumPitch(true)
      .setExtractMaximumIntensity(true)
      .setExtractCOG1(true)
      .setExtractCOG2(true)
      .setExtractCOG23(true)
      .setCustomScript(
        "# get centre of gravity and spread from spectrum"
        +"\nspectrum = To Spectrum... yes"
        +"\n# filter it"
        +"\nFilter (pass Hann band)... 1000 22000 100"
        +"\n# get centre of gravity"
        +"\ncog = Get centre of gravity... 2"
        +"\n# extract the result back out into a CSV column called 'cog'"
        +"\nprint 'cog:0' 'newline$'"
        +"\n# tidy up objects"
        +"\nselect spectrum"
        +"\nRemove");
    p.generateScript(
      scriptWriter, dir,
      wav, targets,
      5000, // <- custom threshold isn't used because fastTrack is used instead
      // custom thresholds used:
      30, 250, 0.4, 40, 450, 6500,
      attributeValues);
    scriptWriter.close();
    
    String differences = diff(expected, actual, ".*nzilbb/labbcat/server/task.*");
    if (differences != null) {
      fail(differences);
    } else {
      actual.delete();
    }
  }

  /** Ensure that script fragments that read/write files on the server can be detected. */
  @Test public void fileAccessDetection() throws Exception {
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
      ProcessWithPraat p = new ProcessWithPraat()
        .setCustomScript(script);
      assertTrue("Custom script file access: " + script,
                 p.filesAccessed());
      p = new ProcessWithPraat()
        .setScriptFormant(script+"\n"+p.getScriptFormant());
      assertTrue("Formant script file access: " + p.getScriptFormant(),
                 p.filesAccessed());
      p = new ProcessWithPraat()
        .setScriptPitch(script+"\n"+p.getScriptPitch());
      assertTrue("Pitch script file access: " + p.getScriptPitch(),
                 p.filesAccessed());
      p = new ProcessWithPraat()
        .setScriptIntensity(script+"\n"+p.getScriptIntensity());
      assertTrue("Intensity script file access: " + p.getScriptIntensity(),
                 p.filesAccessed());
      p = new ProcessWithPraat()
        .setScriptSpectrum(script+"\n"+p.getScriptSpectrum());
      assertTrue("Spectrum script file access: " + p.getScriptSpectrum(),
                 p.filesAccessed());
    } // next file access script
  }

  /**
   * Diffs two files.
   * @param expected
   * @param actual
   * @return null if the files are the same, and a String describing differences if not.
   */
  public String diff(File expected, File actual) {
    return diff(expected, actual, null);
  }
  
  /**
   * Diffs two files.
   * @param expected
   * @param actual
   * @param ignorePattern An optional regular expression identifying changes to ignore.
   * @return null if the files are the same, and a String describing differences if not.
   */
  public String diff(File expected, File actual, String ignorePattern) {
    
    StringBuffer d = new StringBuffer();      
    try {
      // compare with what we expected
      Vector<String> actualLines = new Vector<String>();
      BufferedReader reader = new BufferedReader(new FileReader(actual));
      String line = reader.readLine();
      while (line != null) {
        actualLines.add(line);
        line = reader.readLine();
      }
      Vector<String> expectedLines = new Vector<String>();
      reader = new BufferedReader(new FileReader(expected));
      line = reader.readLine();
      while (line != null) {
        expectedLines.add(line);
        line = reader.readLine();
      }
      MinimumEditPath<String> comparator = new MinimumEditPath<String>();
      List<EditStep<String>> path = comparator.minimumEditPath(expectedLines, actualLines);
      for (EditStep<String> step : path) {
        
        // ignore this difference?
        if (ignorePattern != null && step.getFrom().matches(ignorePattern)) continue;
        
        // report the difference
        switch (step.getOperation()) {
          case CHANGE:
            d.append("\n"+expected.getPath()+":"+(step.getFromIndex()+1)+": Expected:\n" 
                     + step.getFrom() 
                     + "\n"+actual.getPath()+":"+(step.getToIndex()+1)+": Found:\n" + step.getTo());
            break;
          case DELETE:
            d.append("\n"+expected.getPath()+":"+(step.getFromIndex()+1)+": Deleted:\n" 
                     + step.getFrom()
                     + "\n"+actual.getPath()+":"+(step.getToIndex()+1)+": Missing");
            break;
          case INSERT:
            d.append("\n"+expected.getPath()+":"+(step.getFromIndex()+1)+": Missing" 
                     + "\n"+actual.getPath()+":"+(step.getToIndex()+1)+": Inserted:\n" 
                     + step.getTo());
            break;
        }
      } // next step
    } catch(Exception exception) {
      d.append("\n" + exception);
    }
    if (d.length() > 0) return d.toString();
    return null;
  } // end of diff()
  
  /**
   * Directory for text files.
   * @see #getDir()
   * @see #setDir(File)
   */
  protected File fDir;
  /**
   * Getter for {@link #fDir}: Directory for text files.
   * @return Directory for text files.
   */
  public File getDir() { 
    if (fDir == null) {
      try {
        URL urlThisClass = getClass().getResource(getClass().getSimpleName() + ".class");
        File fThisClass = new File(urlThisClass.toURI());
        fDir = fThisClass.getParentFile();
      } catch(Throwable t) {
        System.out.println("" + t);
      }
    }
    return fDir; 
  }
  /**
   * Setter for {@link #fDir}: Directory for text files.
   * @param fNewDir Directory for text files.
   */
  public void setDir(File fNewDir) { fDir = fNewDir; }

  public static void main(String args[])  {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.task.test.TestProcessWithPraat");
  }

}
