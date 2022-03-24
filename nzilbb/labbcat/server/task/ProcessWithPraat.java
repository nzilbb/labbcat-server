//
// Copyright 2004-2021 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License as published by
//    the Free Software Foundation; either version 3 of the License, or
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

package nzilbb.labbcat.server.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import nzilbb.ag.GraphNotFoundException;
import nzilbb.util.Execution;
import nzilbb.util.IO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/**
 * Class for processing a CSV file with targets to extract formant values for each line.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class ProcessWithPraat extends Task {

  /**
   * CSV data file, which will be deleted after processing.
   * @see #getDataFile()
   * @see #setDataFile(File)
   */
  protected File dataFile;
  /**
   * Getter for {@link #dataFile}: CSV data file
   * @return CSV data file
   */
  public File getDataFile() { return dataFile; }
  /**
   * Setter for {@link #dataFile}: CSV data file, which will be deleted after processing.
   * @param fNewDataFile CSV data file, which will be deleted after processing.
   */
  public ProcessWithPraat setDataFile(File fNewDataFile) { dataFile = fNewDataFile; return this; }
   
  /**
   * The field delimiter character for the CSV file.
   * @see #getFieldDelimiter()
   * @see #setFieldDelimiter(char)
   */
  protected char fieldDelimiter = ',';
  /**
   * Getter for {@link #fieldDelimiter}: The field delimiter character for the CSV file.
   * @return The field delimiter character for the CSV file.
   */
  public char getFieldDelimiter() { return fieldDelimiter; }
  /**
   * Setter for {@link #fieldDelimiter}: The field delimiter character for the CSV file.
   * @param cNewFieldDelimiter The field delimiter character for the CSV file.
   */
  public ProcessWithPraat setFieldDelimiter(char cNewFieldDelimiter) { fieldDelimiter = cNewFieldDelimiter; return this; }
      
  /**
   * Colum that contains the transcript_id
   * @see #getTranscriptIdColumn()
   * @see #setTranscriptIdColumn(int)
   */
  protected int transcriptIdColumn;
  /**
   * Getter for {@link #transcriptIdColumn}: Colum that contains the transcript_id
   * @return Colum that contains the transcript_id
   */
  public int getTranscriptIdColumn() { return transcriptIdColumn; }
  /**
   * Setter for {@link #transcriptIdColumn}: Colum that contains the transcript_id
   * @param iNewTranscriptIdColumn Colum that contains the transcript_id
   */
  public ProcessWithPraat setTranscriptIdColumn(int iNewTranscriptIdColumn) { transcriptIdColumn = iNewTranscriptIdColumn; return this; }

  /**
   * Column that identifies the time, or start time if an end column is specified, at
   * which the formant should be evaluated. 
   * @see #getMarkColumn()
   * @see #setMarkColumn(int)
   */
  protected int markColumn;
  /**
   * Getter for {@link #markColumn}: Column that identifies the time, or start time if an
   * end column is specified, at which the formant should be evaluated. 
   * @return Column that identifies the time, or start time if an end column is specified,
   * at which the formant should be evaluated. 
   */
  public int getMarkColumn() { return markColumn; }
  /**
   * Setter for {@link #markColumn}: Column that identifies the time, or start time if a
   * {@link #markEndColumn} is specified, at which the formant should be evaluated. 
   * @param iNewMarkColumn Column that identifies the time, or start time if an end column
   * is specified, at which the formant should be evaluated. 
   */
  public ProcessWithPraat setMarkColumn(int iNewMarkColumn) { markColumn = iNewMarkColumn; return this; }

  /**
   * Column that identifies the end time of the span for which formants should be
   * evaluated. null if MarkColumn is the target time, or non-null if MarkColumn and
   * MarkEndColumn should be used to define a mid-point at which the formants should be
   * extracted. 
   * @see #getMarkEndColumn()
   * @see #setMarkEndColumn(Integer)
   */
  protected Integer markEndColumn;
  /**
   * Getter for {@link #markEndColumn}: Column that identifies the end time of the span
   * for which formants should be evaluated. null if MarkColumn is the target time, or
   * non-null if {@link #markColumn} and MarkEndColumn should be used to define a
   * mid-point at which the formants should be extracted. 
   * @return Column that identifies the end time of the span for which formants should be
   * evaluated. null if {@link #markColumn} is the target time, or non-null if 
   * {@link #markColumn} and MarkEndColum n should be used to define a mid-point at which the
   * formants should be extracted. 
   */
  public Integer getMarkEndColumn() { return markEndColumn; }
  /**
   * Setter for {@link #markEndColumn}: Column that identifies the end time of the span
   * for which formants should be evaluated. null if {@link #markColumn} is the target
   * time, or non-null if {@link #markColumn} and MarkEndColumn should be used to define
   * a mid-point at which the formants should be extracted. 
   * @param iNewMarkEndColumn Column that identifies the end time of the span for which
   * formants should be evaluated. null if {@link #markColumn} is the target time, or
   * non-null if {@link #markColumn} and MarkEndColumn should be used to define a
   * mid-point at which the formants should be extracted. 
   */
  public ProcessWithPraat setMarkEndColumn(Integer iNewMarkEndColumn) { markEndColumn = iNewMarkEndColumn; return this; }
      
  /**
   * Name to use as the basis for an output file name
   * @see #getFileName()
   * @see #setFileName(String)
   */
  protected String fileName = "formants";
  /**
   * Getter for {@link #fileName}: Name to use as the basis for an output file name
   * @return Name to use as the basis for an output file name
   */
  public String getFileName() { return fileName; }
  /**
   * Setter for {@link #fileName}: Name to use as the basis for an output file name
   * @param sNewFileName Name to use as the basis for an output file name
   */
  public ProcessWithPraat setFileName(String sNewFileName) { fileName = sNewFileName; return this; }

  /**
   * The index of the column that specifies the name of the speaker.
   * @see #getSpeakerNameColumn()
   * @see #setSpeakerNameColumn(int)
   */
  protected int participantNameColumn;
  /**
   * Getter for {@link #participantNameColumn}: The index of the column that specifies the
   * name of the speaker. 
   * @return The index of the column that specifies the name of the speaker.
   */
  public int getParticipantNameColumn() { return participantNameColumn; }
  /**
   * Setter for {@link #participantNameColumn}: The index of the column that specifies the
   * name of the speaker. 
   * @param iNewParticipantNameColumn The index of the column that specifies the name of the speaker.
   */
  public ProcessWithPraat setParticipantNameColumn(int iNewParticipantNameColumn) { participantNameColumn = iNewParticipantNameColumn; return this; }

  /**
   * Whether to extract F1 - default is true
   * @see #getExtractF1()
   * @see #setExtractF1(boolean)
   */
  protected boolean extractF1 = true;
  /**
   * Getter for {@link #extractF1}: Whether to extract F1
   * @return Whether to extract F1
   */
  public boolean getExtractF1() { return extractF1; }
  /**
   * Setter for {@link #extractF1}: Whether to extract F1
   * @param bNewExtractF1 Whether to extract F1
   */
  public ProcessWithPraat setExtractF1(boolean bNewExtractF1) { extractF1 = bNewExtractF1; return this; }

  /**
   * Whether to extract F2 - default is true
   * @see #getExtractF2()
   * @see #setExtractF2(boolean)
   */
  protected boolean extractF2 = true;
  /**
   * Getter for {@link #extractF2}: Whether to extract F2
   * @return Whether to extract F2
   */
  public boolean getExtractF2() { return extractF2; }
  /**
   * Setter for {@link #extractF2}: Whether to extract F2
   * @param bNewExtractF2 Whether to extract F2
   */
  public ProcessWithPraat setExtractF2(boolean bNewExtractF2) { extractF2 = bNewExtractF2; return this; }

  /**
   * Whether to extract F3 - default is false
   * @see #getExtractF3()
   * @see #setExtractF3(boolean)
   */
  protected boolean extractF3 = false;
  /**
   * Getter for {@link #extractF3}: Whether to extract F3
   * @return Whether to extract F3
   */
  public boolean getExtractF3() { return extractF3; }
  /**
   * Setter for {@link #extractF3}: Whether to extract F3
   * @param bNewExtractF3 Whether to extract F3
   */
  public ProcessWithPraat setExtractF3(boolean bNewExtractF3) { extractF3 = bNewExtractF3; return this; }

  /**
   * Whether to extract the minimum pitch - default is false
   * @see #getExtractMinimumPitch()
   * @see #setExtractMinimumPitch(boolean)
   */
  protected boolean extractMinimumPitch = false;
  /**
   * Getter for {@link #extractMinimumPitch}: Whether to extract the minimum pitch
   * @return Whether to extract the minimum pitch
   */
  public boolean getExtractMinimumPitch() { return extractMinimumPitch; }
  /**
   * Setter for {@link #extractMinimumPitch}: Whether to extract the minimum pitch
   * @param bNewExtractMinimumPitch Whether to extract the minimum pitch
   */
  public ProcessWithPraat setExtractMinimumPitch(boolean bNewExtractMinimumPitch) { extractMinimumPitch = bNewExtractMinimumPitch; return this; }

  /**
   * Whether to extract the maximum pitch - default is false
   * @see #getExtractMaximumPitch()
   * @see #setExtractMaximumPitch(boolean)
   */
  protected boolean extractMaximumPitch = false;
  /**
   * Getter for {@link #extractMaximumPitch}: Whether to extract the maximum pitch
   * @return Whether to extract the maximum pitch
   */
  public boolean getExtractMaximumPitch() { return extractMaximumPitch; }
  /**
   * Setter for {@link #extractMaximumPitch}: Whether to extract the maximum pitch
   * @param bNewExtractMaximumPitch Whether to extract the maximum pitch
   */
  public ProcessWithPraat setExtractMaximumPitch(boolean bNewExtractMaximumPitch) { extractMaximumPitch = bNewExtractMaximumPitch; return this; }

  /**
   * Whether to extract the mean pitch - default is false
   * @see #getExtractMeanPitch()
   * @see #setExtractMeanPitch(boolean)
   */
  protected boolean extractMeanPitch = false;
  /**
   * Getter for {@link #extractMeanPitch}: Whether to extract the mean pitch
   * @return Whether to extract the mean pitch
   */
  public boolean getExtractMeanPitch() { return extractMeanPitch; }
  /**
   * Setter for {@link #extractMeanPitch}: Whether to extract the mean pitch
   * @param bNewExtractMeanPitch Whether to extract the mean pitch
   */
  public ProcessWithPraat setExtractMeanPitch(boolean bNewExtractMeanPitch) { extractMeanPitch = bNewExtractMeanPitch; return this; }

  /**
   * Whether to extract the maximum intensity - default is false
   * @see #getExtractMaximumIntensity()
   * @see #setExtractMaximumIntensity(boolean)
   */
  protected boolean extractMaximumIntensity = false;
  /**
   * Getter for {@link #extractMaximumIntensity}: Whether to extract the maximum intensity
   * @return Whether to extract the maximum intensity
   */
  public boolean getExtractMaximumIntensity() { return extractMaximumIntensity; }
  /**
   * Setter for {@link #extractMaximumIntensity}: Whether to extract the maximum intensity
   * @param bNewExtractMaximumIntensity Whether to extract the maximum intensity
   */
  public ProcessWithPraat setExtractMaximumIntensity(boolean bNewExtractMaximumIntensity) { extractMaximumIntensity = bNewExtractMaximumIntensity; return this; }

  /**
   * Whether to extract the centre of gravity, with p = 1 - default is false.
   * @see #getExtractCOG1()
   * @see #setExtractCOG1(boolean)
   */
  protected boolean extractCOG1 = false;
  /**
   * Getter for {@link #extractCOG1}: Whether to extract the centre of gravity, with p = 1.
   * @return Whether to extract the centre of gravity, with p = 1.
   */
  public boolean getExtractCOG1() { return extractCOG1; }
  /**
   * Setter for {@link #extractCOG1}: Whether to extract the centre of gravity, with p = 1.
   * @param newExtractCOG1 Whether to extract the centre of gravity, with p = 1.
   */
  public ProcessWithPraat setExtractCOG1(boolean newExtractCOG1) { extractCOG1 = newExtractCOG1; return this; }

  /**
   * Whether to extract the centre of gravity, with p = 2 - default is false.
   * @see #getExtractCOG2()
   * @see #setExtractCOG2(boolean)
   */
  protected boolean extractCOG2 = false;
  /**
   * Getter for {@link #extractCOG2}: Whether to extract the centre of gravity, with p = 2.
   * @return Whether to extract the centre of gravity, with p = 2.
   */
  public boolean getExtractCOG2() { return extractCOG2; }
  /**
   * Setter for {@link #extractCOG2}: Whether to extract the centre of gravity, with p = 2.
   * @param newExtractCOG2 Whether to extract the centre of gravity, with p = 2.
   */
  public ProcessWithPraat setExtractCOG2(boolean newExtractCOG2) { extractCOG2 = newExtractCOG2; return this; }

  /**
   * Whether to extract the centre of gravity, with p = 2/3 - default is false.
   * @see #getExtractCOG23()
   * @see #setExtractCOG23(boolean)
   */
  protected boolean extractCOG23 = false;
  /**
   * Getter for {@link #extractCOG23}: Whether to extract the centre of gravity, with p = 2/3.
   * @return Whether to extract the centre of gravity, with p = 2/3.
   */
  public boolean getExtractCOG23() { return extractCOG23; }
  /**
   * Setter for {@link #extractCOG23}: Whether to extract the centre of gravity, with p = 2/3.
   * @param newExtractCOG23 Whether to extract the centre of gravity, with p = 2/3.
   */
  public ProcessWithPraat setExtractCOG23(boolean newExtractCOG23) { extractCOG23 = newExtractCOG23; return this; }

  /**
   * How long before in seconds before the start and after the end times to take as a
   * sample window. Default is 0.025s (25ms).
   * @see #getWindowOffset()
   * @see #setWindowOffset(double)
   */
  protected double windowOffset = 0.025;
  /**
   * Getter for {@link #windowOffset}: How long before in seconds before the start and
   * after the end times to take as a sample window. Default is 0.025s (25ms).
   * @return How long before in seconds before the start and after the end times to take
   * as a sample window. 
   */
  public double getWindowOffset() { return windowOffset; }
  /**
   * Setter for {@link #windowOffset}: How long before in seconds before the start and
   * after the end times to take as a sample window. 
   * @param dNewWindowOffset How long before in seconds before the start and after the end
   * times to take as a sample window. 
   */
  public ProcessWithPraat setWindowOffset(double dNewWindowOffset) { windowOffset = dNewWindowOffset; return this; }

  /**
   * Command to send to Praat for creating a formant track.
   * @see #getScriptFormant()
   * @see #setScriptFormant(String)
   */
  protected String scriptFormant = "To Formant (burg)... 0.0025 5 formantCeiling 0.025 50";
  /**
   * Getter for {@link #scriptFormant}: Command to send to Praat for creating a formant track.
   * @return Command to send to Praat for creating a formant track.
   */
  public String getScriptFormant() {
    return scriptFormant;
  }
  /**
   * Setter for {@link #scriptFormant}: Command to send to Praat for creating a formant track.
   * @param sNewScriptFormant Command to send to Praat for creating a formant track.
   */
  public ProcessWithPraat setScriptFormant(String sNewScriptFormant) { scriptFormant = sNewScriptFormant; return this; }
  
  /**
   * Formant ceiling by default. Default value is 5500.
   * @see #getFormantCeilingDefault()
   * @see #setFormantCeilingDefault(int)
   */
  protected int formantCeilingDefault = 5500;
  /**
   * Getter for {@link #formantCeilingDefault}: Formant ceiling by default. Default value
   * is 5500.
   * @return Formant ceiling by default.
   */
  public int getFormantCeilingDefault() { return formantCeilingDefault; }
  /**
   * Setter for {@link #formantCeilingDefault}: Formant ceiling by default.
   * @param newFormantCeilingDefault Formant ceiling by default.
   */
  public ProcessWithPraat setFormantCeilingDefault(int newFormantCeilingDefault) { formantCeilingDefault = newFormantCeilingDefault; return this; }

  /**
   * Participant attribute layer ID for differentiating formant settings. This will
   * typically be "participant_gender" but can be any participant attribute layer. 
   * @see #getFormantDifferentiationLayerId()
   * @see #setFormantDifferentiationLayerId(String)
   */
  protected String formantDifferentiationLayerId = "participant_gender";
  /**
   * Getter for {@link #formantDifferentiationLayerId}: Participant attribute layer ID
   * for differentiating formant settings. This will typically be "participant_gender"
   * but can be any participant attribute layer. 
   * @return Participant attribute layer ID for differentiating formant settings. This
   * will typically be "participant_gender" but can be any participant attribute layer. 
   */
  public String getFormantDifferentiationLayerId() { return formantDifferentiationLayerId; }
  /**
   * Setter for {@link #formantDifferentiationLayerId}: Participant attribute layer ID
   * for differentiating formant settings. This will typically be "participant_gender"
   * but can be any participant attribute layer. 
   * @param newFormantDifferentiationLayerId Participant attribute layer ID for
   * differentiating formant settings. This will typically be "participant_gender" but
   * can be any participant attribute layer. 
   */
  public ProcessWithPraat setFormantDifferentiationLayerId(String newFormantDifferentiationLayerId) { formantDifferentiationLayerId = newFormantDifferentiationLayerId; return this; }
  
  /**
   * List of regular expression strings to match against the value of that attribute
   * identified by {@link #formantDifferentiationLayerId}. If the participant's
   * attribute value matches the pattern for an element in this array, the corresponding
   * element in {@link #formantCeilingOther} will be used for that participant. 
   * <p> By default, it includes one entry: "M" to match male participants.
   * @see #getFormantOtherPattern()
   */
  protected Vector<Pattern> formantOtherPattern = new Vector<Pattern>() {{ add(Pattern.compile("M")); }};
  /**
   * Getter for {@link #formantOtherPattern}: List of regular expression strings to match
   * against the value of that attribute identified by
   * {@link #formantDifferentiationLayerId}. If the participant's attribute value
   * matches the pattern for an element in this array, the corresponding element in
   * {@link #formantCeilingOther} will be used for that participant. 
   * <p> By default, it includes one entry: "M" to match male participants.
   * @return List of regular expression strings to match against the value of that
   * attribute identified by {@link #formantDifferentiationLayerId}. If the
   * participant's attribute value matches the pattern for an element in this array, the
   * corresponding element in {@link #formantCeilingOther} will be used for that
   * participant. 
   */
  public Vector<Pattern> getFormantOtherPattern() { return formantOtherPattern; }
  
  /**
   * Values to use as the formant ceiling for participants who's attribute value matches
   * the corresponding regular expression in {@link #formantOtherPattern}. 
   * <p> By default, it includes one entry: 5000 for male participants.
   * @see #getFormantCeilingOther()
   * @see #setFormantCeilingOther(Vector<Integer>)
   */
  protected Vector<Integer> formantCeilingOther = new Vector<Integer>() {{ add(5000); }};;
  /**
   * Getter for {@link #formantCeilingOther}: Values to use as the formant ceiling for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #formantOtherPattern}. 
   * <p> By default, it includes one entry: 5000 for male participants.
   * @return Values to use as the formant ceiling for participants who's attribute value
   * matches the corresponding regular expression in {@link #formantOtherPattern}. 
   */
  public Vector<Integer> getFormantCeilingOther() { return formantCeilingOther; }
  /**
   * Setter for {@link #formantCeilingOther}: Values to use as the formant ceiling for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #formantOtherPattern}. 
   * @param newFormantCeilingOther Values to use as the formant ceiling for participants
   * who's attribute value matches the corresponding regular expression in
   * {@link #formantOtherPattern}. 
   */
  public ProcessWithPraat setFormantCeilingOther(Vector<Integer> newFormantCeilingOther) { formantCeilingOther = newFormantCeilingOther; return this; }  

  /**
   * List of positions in the segment to take values - 0.0 = at the beginning of the
   * intervale, 1.0 = at the end of the interval, 0.5 in the middle, etc. 
   * <p> By default, it includes one entry: 0.5 to measure at the mid-point.
   * @see #getSamplePoints()
   * @see #setSamplePoints(Vector)
   */
  protected Vector<Double> samplePoints = new Vector<Double>() {{ add(0.5); }};
  /**
   * Getter for {@link #samplePoints}: List of positions in the segment to take values -
   * 0.0 = at the beginning of the intervale, 1.0 = at the end of the interval, 0.5 in the
   * middle, etc. 
   * <p> By default, it includes one entry: 0.5 to measure at the mid-point.
   * @return List of positions in the segment to take values - 0.0 = at the beginning of
   * the intervale, 1.0 = at the end of the interval, 0.5 in the middle, etc. 
   */
  public Vector<Double> getSamplePoints() { return samplePoints; }
  /**
   * Setter for {@link #samplePoints}: List of positions in the segment to take values -
   * 0.0 = at the beginning of the intervale, 1.0 = at the end of the interval, 0.5 in the
   * middle, etc. 
   * @param vNewSamplePoints List of positions in the segment to take values - 0.0 = at
   * the beginning of the intervale, 1.0 = at the end of the interval, 0.5 in the middle,
   * etc. 
   */
  public ProcessWithPraat setSamplePoints(Vector<Double> vNewSamplePoints) { samplePoints = vNewSamplePoints; return this; }

  /**
   * Command to send to Praat for creating a pitch track.
   * @see #getScriptPitch()
   * @see #setScriptPitch(String)
   */
  protected String scriptPitch = "To Pitch (ac)...  0 pitchFloor 15 no 0.03 voicingThreshold 0.01 0.35 0.14 pitchCeiling";
  /**
   * Getter for {@link #scriptPitch}: Command to send to Praat for creating a pitch track.
   * @return Command to send to Praat for creating a pitch track.
   */
  public String getScriptPitch() { return scriptPitch; }
  /**
   * Setter for {@link #scriptPitch}: Command to send to Praat for creating a pitch track.
   * @param sNewScriptPitch Command to send to Praat for creating a pitch track.
   */
  public ProcessWithPraat setScriptPitch(String sNewScriptPitch) { scriptPitch = sNewScriptPitch; return this; }

  /**
   * Participant attribute layer ID for differentiating pitch settings. This will
   * typically be "participant_gender" but can be any participant attribute layer. 
   * @see #getPitchDifferentiationLayerId()
   * @see #setPitchDifferentiationLayerId(String)
   */
  protected String pitchDifferentiationLayerId = "participant_gender";
  /**
   * Getter for {@link #pitchDifferentiationLayerId}: Participant attribute layer ID
   * for differentiating pitch settings. This will typically be "participant_gender"
   * but can be any participant attribute layer. 
   * @return Participant attribute layer ID for differentiating pitch settings. This
   * will typically be "participant_gender" but can be any participant attribute layer. 
   */
  public String getPitchDifferentiationLayerId() { return pitchDifferentiationLayerId; }
  /**
   * Setter for {@link #pitchDifferentiationLayerId}: Participant attribute layer ID
   * for differentiating pitch settings. This will typically be "participant_gender"
   * but can be any participant attribute layer. 
   * @param newPitchDifferentiationLayerId Participant attribute layer ID for
   * differentiating pitch settings. This will typically be "participant_gender" but
   * can be any participant attribute layer. 
   */
  public ProcessWithPraat setPitchDifferentiationLayerId(String newPitchDifferentiationLayerId) { pitchDifferentiationLayerId = newPitchDifferentiationLayerId; return this; }
  
  /**
   * List of regular expression strings to match against the value of that attribute
   * identified by {@link #pitchDifferentiationLayerId}. If the participant's
   * attribute value matches the pattern for an element in this array, the corresponding
   * element in {@link #pitchCeilingOther} will be used for that participant. 
   * <p> By default, it includes one entry: "M" to match male participants.
   * @see #getPitchOtherPattern()
   */
  protected Vector<Pattern> pitchOtherPattern = new Vector<Pattern>() {{ add(Pattern.compile("M")); }};
  /**
   * Getter for {@link #pitchOtherPattern}: List of regular expression strings to match
   * against the value of that attribute identified by
   * {@link #pitchDifferentiationLayerId}. If the participant's attribute value
   * matches the pattern for an element in this array, the corresponding element in
   * {@link #pitchCeilingOther} will be used for that participant. 
   * <p> By default, it includes one entry: "M" to match male participants.
   * @return List of regular expression strings to match against the value of that
   * attribute identified by {@link #pitchDifferentiationLayerId}. If the
   * participant's attribute value matches the pattern for an element in this array, the
   * corresponding element in {@link #pitchCeilingOther} will be used for that
   * participant. 
   */
  public Vector<Pattern> getPitchOtherPattern() { return pitchOtherPattern; }

  /**
   * Pitch Floor by default. Default value is 60.
   * @see #getPitchFloorDefault()
   * @see #setPitchFloorDefault(int)
   */
  protected int pitchFloorDefault = 60;
  /**
   * Getter for {@link #pitchFloorDefault}: Pitch Floor by default. Default value is 60.
   * @return Pitch Floor by default.
   */
  public int getPitchFloorDefault() { return pitchFloorDefault; }
  /**
   * Setter for {@link #pitchFloorDefault}: Pitch Floor by default.
   * @param newPitchFloorDefault Pitch Floor by default.
   */
  public ProcessWithPraat setPitchFloorDefault(int newPitchFloorDefault) { pitchFloorDefault = newPitchFloorDefault; return this; }
  
  /**
   * Values to use as the pitch floor for participants who's attribute value matches the
   * corresponding regular expression in {@link #pitchOtherPattern}. 
   * <p> By default, it includes one entry: 30 for male participants.
   * @see #getPitchFloorOther()
   * @see #setPitchFloorOther(Vector<Integer>)
   */
  protected Vector<Integer> pitchFloorOther = new Vector<Integer>() {{ add(30); }};
  /**
   * Getter for {@link #pitchFloorOther}: Values to use as the pitch floor for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #pitchOtherPattern}. 
   * <p> By default, it includes one entry: 30 for male participants.
   * @return Values to use as the pitch floor for participants who's attribute value
   * matches the corresponding regular expression in {@link #pitchOtherPattern}. 
   */
  public Vector<Integer> getPitchFloorOther() { return pitchFloorOther; }
  /**
   * Setter for {@link #pitchFloorOther}: Values to use as the pitch floor for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #pitchOtherPattern}. 
   * @param newPitchFloorOther Values to use as the pitch floor for participants who's
   * attribute value matches the corresponding regular expression in 
   * {@link #pitchOtherPattern}. 
   */
  public ProcessWithPraat setPitchFloorOther(Vector<Integer> newPitchFloorOther) { pitchFloorOther = newPitchFloorOther; return this; }

  /**
   * Pitch Ceiling by default. Default value is 500.
   * @see #getPitchCeilingDefault()
   * @see #setPitchCeilingDefault(int)
   */
  protected int pitchCeilingDefault = 500;
  /**
   * Getter for {@link #pitchCeilingDefault}: Pitch Ceiling by default. Default value is 500.
   * @return Pitch Ceiling by default.
   */
  public int getPitchCeilingDefault() { return pitchCeilingDefault; }
  /**
   * Setter for {@link #pitchCeilingDefault}: Pitch Ceiling by default.
   * @param newPitchCeilingDefault Pitch Ceiling by default.
   */
  public ProcessWithPraat setPitchCeilingDefault(int newPitchCeilingDefault) { pitchCeilingDefault = newPitchCeilingDefault; return this; }

  /**
   * Values to use as the pitch ceiling for participants who's attribute value matches the
   * corresponding regular expression in {@link #pitchOtherPattern}. 
   * <p> By default, it includes one entry: 250 for male participants.
   * @see #getPitchCeilingOther()
   * @see #setPitchCeilingOther(Vector<Integer>)
   */
  protected Vector<Integer> pitchCeilingOther = new Vector<Integer>(){{ add(250); }};
  /**
   * Getter for {@link #pitchCeilingOther}: Values to use as the pitch ceiling for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #pitchOtherPattern}. 
   * <p> By default, it includes one entry: 250 for male participants.
   * @return Values to use as the pitch ceiling for participants who's attribute value
   * matches the corresponding regular expression in {@link #pitchOtherPattern}. 
   */
  public Vector<Integer> getPitchCeilingOther() { return pitchCeilingOther; }
  /**
   * Setter for {@link #pitchCeilingOther}: Values to use as the pitch ceiling for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #pitchOtherPattern}. 
   * @param newPitchCeilingOther Values to use as the pitch ceiling for participants who's
   * attribute value matches the corresponding regular expression in
   * {@link #pitchOtherPattern}. 
   */
  public ProcessWithPraat setPitchCeilingOther(Vector<Integer> newPitchCeilingOther) { pitchCeilingOther = newPitchCeilingOther; return this; }

  /**
   * Voicing Threshold by default. Default value is 0.5.
   * @see #getVoicingThresholdDefault()
   * @see #setVoicingThresholdDefault(double)
   */
  protected double voicingThresholdDefault = 0.5;
  /**
   * Getter for {@link #voicingThresholdDefault}: Voicing Threshold by default. Default value 0.5.
   * @return Voicing Threshold by default.
   */
  public double getVoicingThresholdDefault() { return voicingThresholdDefault; }
  /**
   * Setter for {@link #voicingThresholdDefault}: Voicing Threshold by default.
   * @param newVoicingThresholdDefault Voicing Threshold by default.
   */
  public ProcessWithPraat setVoicingThresholdDefault(double newVoicingThresholdDefault) { voicingThresholdDefault = newVoicingThresholdDefault; return this; }

  /**
   * Values to use as the voicing threshold for participants who's attribute value matches
   * the corresponding regular expression in {@link #pitchOtherPattern}. 
   * <p> By default, it includes one entry: 0.4 for male participants.
   * @see #getVoicingThresholdOther()
   * @see #setVoicingThresholdOther(Vector<Double>)
   */
  protected Vector<Double> voicingThresholdOther = new Vector<Double>(){{ add(0.4); }};
  /**
   * Getter for {@link #voicingThresholdOther}: Values to use as the voicing threshold for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #pitchOtherPattern}. 
   * <p> By default, it includes one entry: 0.4 for male participants.
   * @return Values to use as the voicing threshold for participants who's attribute value
   * matches the corresponding regular expression in {@link #pitchOtherPattern}. 
   */
  public Vector<Double> getVoicingThresholdOther() { return voicingThresholdOther; }
  /**
   * Setter for {@link #voicingThresholdOther}: Values to use as the voicing threshold for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #pitchOtherPattern}. 
   * @param newVoicingThresholdOther Values to use as the voicing threshold for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #pitchOtherPattern}. 
   */
  public ProcessWithPraat setVoicingThresholdOther(Vector<Double> newVoicingThresholdOther) { voicingThresholdOther = newVoicingThresholdOther; return this; }

  /**
   * Command to send to Praat for creating an intensity track.
   * @see #getScriptIntensity()
   * @see #setScriptIntensity(String)
   */
  protected String scriptIntensity = "To Intensity... intensityPitchFloor 0 yes";
  /**
   * Getter for {@link #scriptIntensity}: Command to send to Praat for creating an
   * intensity track. 
   * @return Command to send to Praat for creating an intensity track.
   */
  public String getScriptIntensity() { return scriptIntensity; }
  /**
   * Setter for {@link #scriptIntensity}: Command to send to Praat for creating an
   * intensity track. 
   * @param sNewScriptIntensity Command to send to Praat for creating an intensity track.
   */
  public ProcessWithPraat setScriptIntensity(String sNewScriptIntensity) { scriptIntensity = sNewScriptIntensity; return this; }
  
  /**
   * Participant attribute layer ID for differentiating intensity settings. This will
   * typically be "participant_gender" but can be any participant attribute layer. 
   * @see #getIntensityDifferentiationLayerId()
   * @see #setIntensityDifferentiationLayerId(String)
   */
  protected String intensityDifferentiationLayerId = "participant_gender";
  /**
   * Getter for {@link #intensityDifferentiationLayerId}: Participant attribute layer ID
   * for differentiating intensity settings. This will typically be "participant_gender"
   * but can be any participant attribute layer. 
   * @return Participant attribute layer ID for differentiating intensity settings. This
   * will typically be "participant_gender" but can be any participant attribute layer. 
   */
  public String getIntensityDifferentiationLayerId() { return intensityDifferentiationLayerId; }
  /**
   * Setter for {@link #intensityDifferentiationLayerId}: Participant attribute layer ID
   * for differentiating intensity settings. This will typically be "participant_gender"
   * but can be any participant attribute layer. 
   * @param newIntensityDifferentiationLayerId Participant attribute layer ID for
   * differentiating intensity settings. This will typically be "participant_gender" but
   * can be any participant attribute layer. 
   */
  public ProcessWithPraat setIntensityDifferentiationLayerId(String newIntensityDifferentiationLayerId) { intensityDifferentiationLayerId = newIntensityDifferentiationLayerId; return this; }
  
  /**
   * List of regular expression strings to match against the value of that attribute
   * identified by {@link #intensityDifferentiationLayerId}. If the participant's
   * attribute value matches the pattern for an element in this array, the corresponding
   * element in {@link #intensityCeilingOther} will be used for that participant. 
   * <p> By default, it includes one entry: "M" to match male participants.
   * @see #getIntensityOtherPattern()
   */
  protected Vector<Pattern> intensityOtherPattern = new Vector<Pattern>() {{ add(Pattern.compile("M")); }};
  /**
   * Getter for {@link #intensityOtherPattern}: List of regular expression strings to match
   * against the value of that attribute identified by
   * {@link #intensityDifferentiationLayerId}. If the participant's attribute value
   * matches the pattern for an element in this array, the corresponding element in
   * {@link #intensityCeilingOther} will be used for that participant. 
   * <p> By default, it includes one entry: "M" to match male participants.
   * @return List of regular expression strings to match against the value of that
   * attribute identified by {@link #intensityDifferentiationLayerId}. If the
   * participant's attribute value matches the pattern for an element in this array, the
   * corresponding element in {@link #intensityCeilingOther} will be used for that
   * participant. 
   */
  public Vector<Pattern> getIntensityOtherPattern() { return intensityOtherPattern; }

  /**
   * Intensity Pitch Floor by default. Default value is 60.
   * @see #getIntensityPitchFloorDefault()
   * @see #setIntensityPitchFloorDefault(int)
   */
  protected int intensityPitchFloorDefault = 60;
  /**
   * Getter for {@link #intensityPitchFloorDefault}: Intensity Pitch Floor by default. Default value is 60.
   * @return Intensity Pitch Floor by default.
   */
  public int getIntensityPitchFloorDefault() { return intensityPitchFloorDefault; }
  /**
   * Setter for {@link #intensityPitchFloorDefault}: Intensity Pitch Floor by default.
   * @param newIntensityPitchFloorDefault Intensity Pitch Floor by default.
   */
  public ProcessWithPraat setIntensityPitchFloorDefault(int newIntensityPitchFloorDefault) { intensityPitchFloorDefault = newIntensityPitchFloorDefault; return this; }
  
  /**
   * Values to use as the intensity pitch floor for participants who's attribute value matches the
   * corresponding regular expression in {@link #intensityPitchOtherPattern}. 
   * <p> By default, it includes one entry: 30 for male participants.
   * @see #getIntensityPitchFloorOther()
   * @see #setIntensityPitchFloorOther(Vector<Integer>)
   */
  protected Vector<Integer> intensityPitchFloorOther = new Vector<Integer>() {{ add(30); }};
  /**
   * Getter for {@link #intensityPitchFloorOther}: Values to use as the intensity pitch floor for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #intensityPitchOtherPattern}. 
   * <p> By default, it includes one entry: 30 for male participants.
   * @return Values to use as the intensity pitch floor for participants who's attribute value
   * matches the corresponding regular expression in {@link #intensityPitchOtherPattern}. 
   */
  public Vector<Integer> getIntensityPitchFloorOther() { return intensityPitchFloorOther; }
  /**
   * Setter for {@link #intensityPitchFloorOther}: Values to use as the intensity pitch floor for
   * participants who's attribute value matches the corresponding regular expression in
   * {@link #intensityPitchOtherPattern}. 
   * @param newIntensityPitchFloorOther Values to use as the intensity pitch floor for
   * participants who's attribute value matches the corresponding regular expression in 
   * {@link #intensityPitchOtherPattern}. 
   */
  public ProcessWithPraat setIntensityPitchFloorOther(Vector<Integer> newIntensityPitchFloorOther) { intensityPitchFloorOther = newIntensityPitchFloorOther; return this; }
  
  /**
   * Command to send to Praat for creating a spectrum object.
   * @see #getScriptSpectrum()
   * @see #setScriptSpectrum(String)
   */
  protected String scriptSpectrum = "To Spectrum... yes";
  /**
   * Getter for {@link #ScriptSpectrum}: Command to send to Praat for creating a spectrum object.
   * @return Command to send to Praat for creating a spectrum object.
   */
  public String getScriptSpectrum() { return scriptSpectrum; }
  /**
   * Setter for {@link #ScriptSpectrum}: Command to send to Praat for creating a spectrum object.
   * @param newScriptSpectrum Command to send to Praat for creating a spectrum object.
   */
  public ProcessWithPraat setScriptSpectrum(String newScriptSpectrum) { scriptSpectrum = newScriptSpectrum; return this; }

  /**
   * Custom script specified by the user to execute on each sample.
   * @see #getCustomScript()
   * @see #setCustomScript(String)
   */
  protected String customScript;
  /**
   * Getter for {@link #customScript}: Custom script specified by the user to execute on
   * each sample. 
   * @return Custom script specified by the user to execute on each sample.
   */
  public String getCustomScript() { return customScript; }
  /**
   * Setter for {@link #customScript}: Custom script specified by the user to execute on
   * each sample. 
   * @param newCustomScript Custom script specified by the user to execute on each sample.
   */
  public ProcessWithPraat setCustomScript(String newCustomScript) { customScript = newCustomScript; return this; }
   
  /**
   * Headers for custom script outputs.
   * @see #getCustomScriptHeaders()
   */
  protected Vector<String> customScriptHeaders = new Vector<String>();
  /**
   * Getter for {@link #customScriptHeaders}: Headers for custom script outputs.
   * @return Headers for custom script outputs.
   */
  public Vector<String> getCustomScriptHeaders() { return customScriptHeaders; }
  /**
   * Setter for {@link #customScriptHeaders}: Headers for custom script outputs.
   * @param newCustomScriptHeaders Headers for custom script outputs.
   */
  public ProcessWithPraat setCustomScriptHeaders(Vector<String> newCustomScriptHeaders) { customScriptHeaders = newCustomScriptHeaders; return this; }
   
  /**
   * Attributes to make available to custom script.
   * @see #getAttributes()
   */
  protected HashSet<String> attributes = new HashSet<String>();
  /**
   * Getter for {@link #attributes}: Attributes to make available to custom script.
   * @return Attributes to make available to custom script, or null if no attributes are
   * to be made available. 
   */
  public HashSet<String> getAttributes() { return attributes; }
   
  /**
   * Whether to pass data in the original CSV file to the output. Default is true.
   * @see #getPassThroughData()
   * @see #setPassThroughData(boolean)
   */
  protected boolean passThroughData = true;
  /**
   * Getter for {@link #passThroughData}: Whether to pass data in the original CSV file 
   * to the output. Default is true.
   * @return Whether to pass data in the original CSV file to the output.
   */
  public boolean getPassThroughData() { return passThroughData; }
  /**
   * Setter for {@link #passThroughData}: Whether to pass data in the original CSV file 
   * to the output.
   * @param newPassThroughData Whether to pass data in the original CSV file to the output.
   */
  public ProcessWithPraat setPassThroughData(boolean newPassThroughData) { passThroughData = newPassThroughData; return this; }

  /**
   * Use the FastTrack plugin to generate optimum, smoothed formant tracks.
   * @see #getUseFastTrack()
   * @see #setUseFastTrack(boolean)
   */
  protected boolean useFastTrack = false;
  /**
   * Getter for {@link #useFastTrack}: Use the FastTrack plugin to generate optimum,
   * smoothed formant tracks. 
   * @return Use the FastTrack plugin to generate optimum, smoothed formant tracks.
   */
  public boolean getUseFastTrack() { return useFastTrack; }
  /**
   * Setter for {@link #useFastTrack}: Use the FastTrack plugin to generate optimum,
   * smoothed formant tracks. 
   * @param newUseFastTrack Use the FastTrack plugin to generate optimum, smoothed formant tracks.
   */
  public ProcessWithPraat setUseFastTrack(boolean newUseFastTrack) { useFastTrack = newUseFastTrack; return this; }
  
  /**
   * Fast Track time_step global setting - time step in seconds.
   * @see #getFastTrackTimeStep()
   * @see #setFastTrackTimeStep(double)
   */
  protected double fastTrackTimeStep = 0.002;
  /**
   * Getter for {@link #fastTrackTimeStep}: Fast Track time_step global setting - time
   * step in seconds. 
   * @return Fast Track time_step global setting.
   */
  public double getFastTrackTimeStep() { return fastTrackTimeStep; }
  /**
   * Setter for {@link #fastTrackTimeStep}: Fast Track time_step global setting.
   * @param newFastTrackTimeStep Fast Track time_step global setting.
   */
  public ProcessWithPraat setFastTrackTimeStep(double newFastTrackTimeStep) { fastTrackTimeStep = newFastTrackTimeStep; return this; }

  /**
   * Fast Track basis_functions global setting.
   * @see #getFastTrackBasisFunctions()
   * @see #setFastTrackBasisFunctions(String)
   */
  protected String fastTrackBasisFunctions = "dct";
  /**
   * Getter for {@link #fastTrackBasisFunctions}: Fast Track basis_functions global setting.
   * @return Fast Track basis_functions global setting.
   */
  public String getFastTrackBasisFunctions() { return fastTrackBasisFunctions; }
  /**
   * Setter for {@link #fastTrackBasisFunctions}: Fast Track basis_functions global setting.
   * @param newFastTrackBasisFunctions Fast Track basis_functions global setting.
   */
  public ProcessWithPraat setFastTrackBasisFunctions(String newFastTrackBasisFunctions) { fastTrackBasisFunctions = newFastTrackBasisFunctions; return this; }

  /**
   * Fast Track error_method global setting.
   * @see #getFastTrackErrorMethod()
   * @see #setFastTrackErrorMethod(String)
   */
  protected String fastTrackErrorMethod = "mae";
  /**
   * Getter for {@link #fastTrackErrorMethod}: Fast Track error_method global setting.
   * @return Fast Track error_method global setting.
   */
  public String getFastTrackErrorMethod() { return fastTrackErrorMethod; }
  /**
   * Setter for {@link #fastTrackErrorMethod}: Fast Track error_method global setting.
   * @param newFastTrackErrorMethod Fast Track error_method global setting.
   */
  public ProcessWithPraat setFastTrackErrorMethod(String newFastTrackErrorMethod) { fastTrackErrorMethod = newFastTrackErrorMethod; return this; }

  /**
   * Fast Track tracking_method parameter for trackAutoselectProcedure; "burg" or "robust".
   * @see #getFastTrackTrackingMethod()
   * @see #setFastTrackTrackingMethod(String)
   */
  protected String fastTrackTrackingMethod = "burg";
  /**
   * Getter for {@link #fastTrackTrackingMethod}: Fast Track tracking_method parameter for
   * trackAutoselectProcedure; "burg" or "robust". 
   * @return Fast Track tracking_method parameter for trackAutoselectProcedure; "burg" or
   * "robust". 
   */
  public String getFastTrackTrackingMethod() { return fastTrackTrackingMethod; }
  /**
   * Setter for {@link #fastTrackTrackingMethod}: Fast Track tracking_method parameter for
   * trackAutoselectProcedure; "burg" or "robust". 
   * @param newFastTrackTrackingMethod Fast Track tracking_method parameter for
   * trackAutoselectProcedure; "burg" or "robust". 
   */
  public ProcessWithPraat setFastTrackTrackingMethod(String newFastTrackTrackingMethod) { fastTrackTrackingMethod = newFastTrackTrackingMethod; return this; }

  /**
   * Fast Track enable_F1_frequency_heuristic global setting.
   * @see #getFastTrackEnableF1FrequencyHeuristic()
   * @see #setFastTrackEnableF1FrequencyHeuristic(boolean)
   * @see #getFastTrackMaximumF1FrequencyValue()
   */
  protected boolean fastTrackEnableF1FrequencyHeuristic = true;
  /**
   * Getter for {@link #fastTrackEnableF1FrequencyHeuristic}: Fast Track
   * enable_F1_frequency_heuristic global setting. 
   * @return Fast Track enable_F1_frequency_heuristic global setting.
   * @see #getFastTrackMaximumF1FrequencyValue()
   */
  public boolean getFastTrackEnableF1FrequencyHeuristic() { return fastTrackEnableF1FrequencyHeuristic; }
  /**
   * Setter for {@link #fastTrackEnableF1FrequencyHeuristic}: Fast Track
   * enable_F1_frequency_heuristic global setting. 
   * @param newFastTrackEnableF1FrequencyHeuristic Fast Track
   * enable_F1_frequency_heuristic global setting. 
   * @see #getFastTrackMaximumF1FrequencyValue()
   */
  public ProcessWithPraat setFastTrackEnableF1FrequencyHeuristic(boolean newFastTrackEnableF1FrequencyHeuristic) { fastTrackEnableF1FrequencyHeuristic = newFastTrackEnableF1FrequencyHeuristic; return this; }

  /**
   * Fast Track maximum_F1_frequency_value global setting; Median F1 frequency should not
   * be higher than this value.
   * @see #getFastTrackMaximumF1FrequencyValue()
   * @see #setFastTrackMaximumF1FrequencyValue(int)
   */
  protected int fastTrackMaximumF1FrequencyValue = 1200;
  /**
   * Getter for {@link #fastTrackMaximumF1FrequencyValue}: Fast Track
   * maximum_F1_frequency_value global setting. Median F1 frequency should not be higher
   * than this value. 
   * @return Fast Track maximum_F1_frequency_value global setting.
   */
  public int getFastTrackMaximumF1FrequencyValue() { return fastTrackMaximumF1FrequencyValue; }
  /**
   * Setter for {@link #fastTrackMaximumF1FrequencyValue}: Fast Track
   * maximum_F1_frequency_value global setting. Median F1 frequency should not be higher
   * than this value. 
   * @param newFastTrackMaximumF1FrequencyValue Fast Track maximum_F1_frequency_value
   * global setting. 
   */
  public ProcessWithPraat setFastTrackMaximumF1FrequencyValue(int newFastTrackMaximumF1FrequencyValue) { fastTrackMaximumF1FrequencyValue = newFastTrackMaximumF1FrequencyValue; return this; }

  /**
   * Fast Track enable_F1_bandwidth_heuristic global setting.
   * @see #getFastTrackEnableF1BandwidthHeuristic()
   * @see #setFastTrackEnableF1BandwidthHeuristic(boolean)
   * @see #getFastTrackMaximumF1BandwidthValue()
   */
  protected boolean fastTrackEnableF1BandwidthHeuristic = false;
  /**
   * Getter for {@link #fastTrackEnableF1BandwidthHeuristic}: Fast Track
   * enable_F1_bandwidth_heuristic global setting. 
   * @return Fast Track enable_F1_bandwidth_heuristic global setting.
   * @see #getFastTrackMaximumF1BandwidthValue()
   */
  public boolean getFastTrackEnableF1BandwidthHeuristic() { return fastTrackEnableF1BandwidthHeuristic; }
  /**
   * Setter for {@link #fastTrackEnableF1BandwidthHeuristic}: Fast Track
   * enable_F1_bandwidth_heuristic global setting. 
   * @param newFastTrackEnableF1BandwidthHeuristic Fast Track
   * enable_F1_bandwidth_heuristic global setting. 
   * @see #getFastTrackMaximumF1BandwidthValue()
   */
  public ProcessWithPraat setFastTrackEnableF1BandwidthHeuristic(boolean newFastTrackEnableF1BandwidthHeuristic) { fastTrackEnableF1BandwidthHeuristic = newFastTrackEnableF1BandwidthHeuristic; return this; }
  
  /**
   * Fast Track maximum_F1_bandwidth_value global setting. Median F1 bandwidth should not
   * be higher than this value. 
   * @see #getFastTrackMaximumF1BandwidthValue()
   * @see #setFastTrackMaximumF1BandwidthValue(int)
   */
  protected int fastTrackMaximumF1BandwidthValue = 500;
  /**
   * Getter for {@link #fastTrackMaximumF1BandwidthValue}: Fast Track
   * maximum_F1_bandwidth_value global setting. Median F1 bandwidth should not
   * be higher than this value. 
   * @return Fast Track maximum_F1_bandwidth_value global setting.
   */
  public int getFastTrackMaximumF1BandwidthValue() { return fastTrackMaximumF1BandwidthValue; }
  /**
   * Setter for {@link #fastTrackMaximumF1BandwidthValue}: Fast Track
   * maximum_F1_bandwidth_value global setting. Median F1 bandwidth should not
   * be higher than this value. 
   * @param newFastTrackMaximumF1BandwidthValue Fast Track maximum_F1_bandwidth_value
   * global setting. 
   */
  public ProcessWithPraat setFastTrackMaximumF1BandwidthValue(int newFastTrackMaximumF1BandwidthValue) { fastTrackMaximumF1BandwidthValue = newFastTrackMaximumF1BandwidthValue; return this; }

  /**
   * Fast Track enable_F2_bandwidth_heuristic global setting.
   * @see #getFastTrackEnableF2BandwidthHeuristic()
   * @see #setFastTrackEnableF2BandwidthHeuristic(boolean)
   * @see #getFastTrackMaximumF2BandwidthValue()
   */
  protected boolean fastTrackEnableF2BandwidthHeuristic = false;
  /**
   * Getter for {@link #fastTrackEnableF2BandwidthHeuristic}: Fast Track
   * enable_F2_bandwidth_heuristic global setting. 
   * @return Fast Track enable_F2_bandwidth_heuristic global setting.
   * @see #getFastTrackMaximumF2BandwidthValue()
   */
  public boolean getFastTrackEnableF2BandwidthHeuristic() { return fastTrackEnableF2BandwidthHeuristic; }
  /**
   * Setter for {@link #fastTrackEnableF2BandwidthHeuristic}: Fast Track
   * enable_F2_bandwidth_heuristic global setting. 
   * @param newFastTrackEnableF2BandwidthHeuristic Fast Track
   * enable_F2_bandwidth_heuristic global setting. 
   * @see #getFastTrackMaximumF2BandwidthValue()
   */
  public ProcessWithPraat setFastTrackEnableF2BandwidthHeuristic(boolean newFastTrackEnableF2BandwidthHeuristic) { fastTrackEnableF2BandwidthHeuristic = newFastTrackEnableF2BandwidthHeuristic; return this; }

  /**
   * Fast Track maximum_F2_bandwidth_value global setting. Median F2 bandwidth should not
   * be higher than this value. Default is 600.
   * @see #getFastTrackMaximumF2BandwidthValue()
   * @see #setFastTrackMaximumF2BandwidthValue(int)
   */
  protected int fastTrackMaximumF2BandwidthValue = 600;
  /**
   * Getter for {@link #fastTrackMaximumF2BandwidthValue}: Fast Track
   * maximum_F2_bandwidth_value global setting. Median F2 bandwidth should not
   * be higher than this value. Default is 600. 
   * @return Fast Track maximum_F2_bandwidth_value global setting.
   */
  public int getFastTrackMaximumF2BandwidthValue() { return fastTrackMaximumF2BandwidthValue; }
  /**
   * Setter for {@link #fastTrackMaximumF2BandwidthValue}: Fast Track
   * maximum_F2_bandwidth_value global setting. Median F2 bandwidth should not
   * be higher than this value. Default is 600.
   * @param newFastTrackMaximumF2BandwidthValue Fast Track maximum_F2_bandwidth_value
   * global setting. 
   */
  public ProcessWithPraat setFastTrackMaximumF2BandwidthValue(int newFastTrackMaximumF2BandwidthValue) { fastTrackMaximumF2BandwidthValue = newFastTrackMaximumF2BandwidthValue; return this; }

  /**
   * Fast Track enable_F3_bandwidth_heuristic global setting.
   * @see #getFastTrackEnableF3BandwidthHeuristic()
   * @see #setFastTrackEnableF3BandwidthHeuristic(boolean)
   * @see #getFastTrackMaximumF3BandwidthValue()
   */
  protected boolean fastTrackEnableF3BandwidthHeuristic = false;
  /**
   * Getter for {@link #fastTrackEnableF3BandwidthHeuristic}: Fast Track
   * enable_F3_bandwidth_heuristic global setting. 
   * @return Fast Track enable_F3_bandwidth_heuristic global setting.
   * @see #getFastTrackMaximumF3BandwidthValue()
   */
  public boolean getFastTrackEnableF3BandwidthHeuristic() { return fastTrackEnableF3BandwidthHeuristic; }
  /**
   * Setter for {@link #fastTrackEnableF3BandwidthHeuristic}: Fast Track
   * enable_F3_bandwidth_heuristic global setting. 
   * @param newFastTrackEnableF3BandwidthHeuristic Fast Track
   * enable_F3_bandwidth_heuristic global setting. 
   * @see #getFastTrackMaximumF3BandwidthValue()
   */
  public ProcessWithPraat setFastTrackEnableF3BandwidthHeuristic(boolean newFastTrackEnableF3BandwidthHeuristic) { fastTrackEnableF3BandwidthHeuristic = newFastTrackEnableF3BandwidthHeuristic; return this; }

  /**
   * Fast Track maximum_F3_bandwidth_value global setting. Median F3 bandwidth should not
   * be higher than this value. Default is 900.
   * @see #getFastTrackMaximumF3BandwidthValue()
   * @see #setFastTrackMaximumF3BandwidthValue(int)
   */
  protected int fastTrackMaximumF3BandwidthValue = 900;
  /**
   * Getter for {@link #fastTrackMaximumF3BandwidthValue}: Fast Track
   * maximum_F3_bandwidth_value global setting. Median F3 bandwidth should not
   * be higher than this value. Default is 900. 
   * @return Fast Track maximum_F3_bandwidth_value global setting.
   */
  public int getFastTrackMaximumF3BandwidthValue() { return fastTrackMaximumF3BandwidthValue; }
  /**
   * Setter for {@link #fastTrackMaximumF3BandwidthValue}: Fast Track
   * maximum_F3_bandwidth_value global setting. Median F3 bandwidth should not
   * be higher than this value. Default is 900. 
   * @param newFastTrackMaximumF3BandwidthValue Fast Track maximum_F3_bandwidth_value
   * global setting. 
   */
  public ProcessWithPraat setFastTrackMaximumF3BandwidthValue(int newFastTrackMaximumF3BandwidthValue) { fastTrackMaximumF3BandwidthValue = newFastTrackMaximumF3BandwidthValue; return this; }

  /**
   * Fast Track enable_F4_frequency_heuristic global setting.
   * @see #getFastTrackEnableF4FrequencyHeuristic()
   * @see #setFastTrackEnableF4FrequencyHeuristic(boolean)
   * @see #getFastTrackMinimumF4FrequencyValue()
   */
  protected boolean fastTrackEnableF4FrequencyHeuristic = true;
  /**
   * Getter for {@link #fastTrackEnableF4FrequencyHeuristic}: Fast Track
   * enable_F4_frequency_heuristic global setting. 
   * @return Fast Track enable_F4_frequency_heuristic global setting.
   * @see #getFastTrackMinimumF4FrequencyValue()
   */
  public boolean getFastTrackEnableF4FrequencyHeuristic() { return fastTrackEnableF4FrequencyHeuristic; }
  /**
   * Setter for {@link #fastTrackEnableF4FrequencyHeuristic}: Fast Track
   * enable_F4_frequency_heuristic global setting. 
   * @param newFastTrackEnableF4FrequencyHeuristic Fast Track
   * enable_F4_frequency_heuristic global setting. 
   * @see #getFastTrackMinimumF4FrequencyValue()
   */
  public ProcessWithPraat setFastTrackEnableF4FrequencyHeuristic(boolean newFastTrackEnableF4FrequencyHeuristic) { fastTrackEnableF4FrequencyHeuristic = newFastTrackEnableF4FrequencyHeuristic; return this; }

  /**
   * Fast Track minimum_F4_frequency_value global setting. Median F4 frequency should not
   * be lower than this value. Default is 2900.
   * @see #getFastTrackMinimumF4FrequencyValue()
   * @see #setFastTrackMinimumF4FrequencyValue(int)
   */
  protected int fastTrackMinimumF4FrequencyValue = 2900;
  /**
   * Getter for {@link #fastTrackMinimumF4FrequencyValue}: Fast Track
   * minimum_F4_frequency_value global setting. Median F4 frequency should not
   * be lower than this value. Default is 2900.
   * @return Fast Track minimum_F4_frequency_value global setting.
   */
  public int getFastTrackMinimumF4FrequencyValue() { return fastTrackMinimumF4FrequencyValue; }
  /**
   * Setter for {@link #fastTrackMinimumF4FrequencyValue}: Fast Track
   * minimum_F4_frequency_value global setting. Median F4 frequency should not
   * be lower than this value. Default is 2900. 
   * @param newFastTrackMinimumF4FrequencyValue Fast Track minimum_F4_frequency_value
   * global setting. 
   */
  public ProcessWithPraat setFastTrackMinimumF4FrequencyValue(int newFastTrackMinimumF4FrequencyValue) { fastTrackMinimumF4FrequencyValue = newFastTrackMinimumF4FrequencyValue; return this; }

  /**
   * Fast Track enable_rhotic_heuristic global setting. If F3 < 2000 Hz, F1 and F2 should
   * be at least 500 Hz apart. Enabled by default. 
   * @see #getFastTrackEnableRhoticHeuristic()
   * @see #setFastTrackEnableRhoticHeuristic(boolean)
   */
  protected boolean fastTrackEnableRhoticHeuristic = true;
  /**
   * Getter for {@link #fastTrackEnableRhoticHeuristic}: Fast Track
   * enable_rhotic_heuristic global setting. If F3 < 2000 Hz, F1 and F2 should
   * be at least 500 Hz apart. Enabled by default. 
   * @return Fast Track enable_rhotic_heuristic global setting.
   */
  public boolean getFastTrackEnableRhoticHeuristic() { return fastTrackEnableRhoticHeuristic; }
  /**
   * Setter for {@link #fastTrackEnableRhoticHeuristic}: Fast Track
   * enable_rhotic_heuristic global setting. If F3 < 2000 Hz, F1 and F2 should
   * be at least 500 Hz apart. Enabled by default. 
   * @param newFastTrackEnableRhoticHeuristic Fast Track enable_rhotic_heuristic global setting.
   */
  public ProcessWithPraat setFastTrackEnableRhoticHeuristic(boolean newFastTrackEnableRhoticHeuristic) { fastTrackEnableRhoticHeuristic = newFastTrackEnableRhoticHeuristic; return this; }

  /**
   * Fast Track enable_F3F4_proximity_heuristic global setting. If (F4 - F3) < 500 Hz, F1 and F2
   * should be at least 1500 Hz apart. Enabled by default.
   * @see #getFastTrackEnableF3F4ProximityHeuristic()
   * @see #setFastTrackEnableF3F4ProximityHeuristic(boolean)
   */
  protected boolean fastTrackEnableF3F4ProximityHeuristic = true;
  /**
   * Getter for {@link #fastTrackEnableF3F4ProximityHeuristic}: Fast Track
   * enable_F3F4_proximity_heuristic global setting. If (F4 - F3) < 500 Hz, F1 and F2
   * should be at least 1500 Hz apart. Enabled by default.
   * @return Fast Track enable_F3F4_proximity_heuristic global setting.
   */
  public boolean getFastTrackEnableF3F4ProximityHeuristic() { return fastTrackEnableF3F4ProximityHeuristic; }
  /**
   * Setter for {@link #fastTrackEnableF3F4ProximityHeuristic}: Fast Track
   * enable_F3F4_proximity_heuristic global setting. If (F4 - F3) < 500 Hz, F1 and F2
   * should be at least 1500 Hz apart. Enabled by default.
   * @param newFastTrackEnableF3F4ProximityHeuristic Fast Track
   * enable_F3F4_proximity_heuristic global setting. 
   */
  public ProcessWithPraat setFastTrackEnableF3F4ProximityHeuristic(boolean newFastTrackEnableF3F4ProximityHeuristic) { fastTrackEnableF3F4ProximityHeuristic = newFastTrackEnableF3F4ProximityHeuristic; return this; }

  /**
   * Fast Track number of steps. Default value is 20. Number of analyses between low and
   * high analysis limits. More analysis steps may improve results, but will increase
   * analysis time (50% more steps = around 50% longer to analyze). 
   * @see #getFastTrackNumberOfSteps()
   * @see #setFastTrackNumberOfSteps(int)
   */
  protected int fastTrackNumberOfSteps = 20;
  /**
   * Getter for {@link #fastTrackNumberOfSteps}: Fast Track number of steps. Default value is 20.
   * @return Fast Track number of steps.
   */
  public int getFastTrackNumberOfSteps() { return fastTrackNumberOfSteps; }
  /**
   * Setter for {@link #fastTrackNumberOfSteps}: Fast Track number of steps.
   * @param newFastTrackNumberOfSteps Fast Track number of steps.
   */
  public ProcessWithPraat setFastTrackNumberOfSteps(int newFastTrackNumberOfSteps) { fastTrackNumberOfSteps = newFastTrackNumberOfSteps; return this; }
  
  /**
   * Fast Track number of coefficients for the regression function. Default value is
   * 5. Number of coefficients for formant prediction. More coefficients allow for more
   * sudden, and 'wiggly' formant motion. 
   * @see #getFastTrackNumberOfCoefficients()
   * @see #setFastTrackNumberOfCoefficients(int)
   */
  protected int fastTrackNumberOfCoefficients = 5;
  /**
   * Getter for {@link #fastTrackNumberOfCoefficients}: Fast Track number of coefficients
   * for the regression function.  Default value is 5.
   * @return Fast Track number of coefficients for the regression function.
   */
  public int getFastTrackNumberOfCoefficients() { return fastTrackNumberOfCoefficients; }
  /**
   * Setter for {@link #fastTrackNumberOfCoefficients}: Fast Track number of coefficients
   * for the regression function. 
   * @param newFastTrackNumberOfCoefficients Fast Track number of coefficients for the
   * regression function. 
   */
  public ProcessWithPraat setFastTrackNumberOfCoefficients(int newFastTrackNumberOfCoefficients) { fastTrackNumberOfCoefficients = newFastTrackNumberOfCoefficients; return this; }
  
  /**
   * Fast Track number of formants. Default value is 3.
   * @see #getFastTrackNumberOfFormants()
   * @see #setFastTrackNumberOfFormants(int)
   */
  protected int fastTrackNumberOfFormants = 3;
  /**
   * Getter for {@link #fastTrackNumberOfFormants}: Fast Track number of formants. Default
   * value is 3. 
   * @return Fast Track number of formants.
   */
  public int getFastTrackNumberOfFormants() { return fastTrackNumberOfFormants; }
  /**
   * Setter for {@link #fastTrackNumberOfFormants}: Fast Track number of formants.
   * @param newFastTrackNumberOfFormants Fast Track number of formants.
   */
  public ProcessWithPraat setFastTrackNumberOfFormants(int newFastTrackNumberOfFormants) { fastTrackNumberOfFormants = newFastTrackNumberOfFormants; return this; }

  /**
   * Whether to return the regression coefficients from FastTrack.
   * @see #getFastTrackCoefficients()
   * @see #setFastTrackCoefficients(boolean)
   */
  protected boolean fastTrackCoefficients = false;
  /**
   * Getter for {@link #fastTrackCoefficients}: Whether to return the regression coefficients from FastTrack.
   * @return Whether to return the regression coefficients from FastTrack.
   */
  public boolean getFastTrackCoefficients() { return fastTrackCoefficients; }
  /**
   * Setter for {@link #fastTrackCoefficients}: Whether to return the regression coefficients from FastTrack.
   * @param newFastTrackCoefficients Whether to return the regression coefficients from FastTrack.
   */
  public ProcessWithPraat setFastTrackCoefficients(boolean newFastTrackCoefficients) { fastTrackCoefficients = newFastTrackCoefficients; return this; }
  
  /**
   * Participant attribute layer ID for differentiating fastTrack settings. This will
   * typically be "participant_gender" but can be any participant attribute layer. 
   * @see #getFastTrackDifferentiationLayerId()
   * @see #setFastTrackDifferentiationLayerId(String)
   */
  protected String fastTrackDifferentiationLayerId = "participant_gender";
  /**
   * Getter for {@link #fastTrackDifferentiationLayerId}: Participant attribute layer ID
   * for differentiating fastTrack settings. This will typically be "participant_gender"
   * but can be any participant attribute layer. 
   * @return Participant attribute layer ID for differentiating fastTrack settings. This
   * will typically be "participant_gender" but can be any participant attribute layer. 
   */
  public String getFastTrackDifferentiationLayerId() { return fastTrackDifferentiationLayerId; }
  /**
   * Setter for {@link #fastTrackDifferentiationLayerId}: Participant attribute layer ID
   * for differentiating fastTrack settings. This will typically be "participant_gender"
   * but can be any participant attribute layer. 
   * @param newFastTrackDifferentiationLayerId Participant attribute layer ID for
   * differentiating fastTrack settings. This will typically be "participant_gender" but
   * can be any participant attribute layer. 
   */
  public ProcessWithPraat setFastTrackDifferentiationLayerId(String newFastTrackDifferentiationLayerId) { fastTrackDifferentiationLayerId = newFastTrackDifferentiationLayerId; return this; }
  
  /**
   * List of regular expression strings to match against the value of that attribute
   * identified by {@link #fastTrackDifferentiationLayerId}. If the participant's
   * attribute value matches the pattern for an element in this array, the corresponding
   * element in {@link #fastTrackCeilingOther} will be used for that participant. 
   * <p> By default, it includes one entry: "M" to match male participants.
   * @see #getFastTrackOtherPattern()
   */
  protected Vector<Pattern> fastTrackOtherPattern = new Vector<Pattern>() {{ add(Pattern.compile("M")); }};
  /**
   * Getter for {@link #fastTrackOtherPattern}: List of regular expression strings to match
   * against the value of that attribute identified by
   * {@link #fastTrackDifferentiationLayerId}. If the participant's attribute value
   * matches the pattern for an element in this array, the corresponding element in
   * {@link #fastTrackCeilingOther} will be used for that participant. 
   * <p> By default, it includes one entry: "M" to match male participants.
   * @return List of regular expression strings to match against the value of that
   * attribute identified by {@link #fastTrackDifferentiationLayerId}. If the
   * participant's attribute value matches the pattern for an element in this array, the
   * corresponding element in {@link #fastTrackCeilingOther} will be used for that
   * participant. 
   */
  public Vector<Pattern> getFastTrackOtherPattern() { return fastTrackOtherPattern; }

  /**
   * Fast Track lowest analysis frequency by default.
   * <p> The default value is 5000.
   * @see #getFastTrackLowestAnalysisFrequencyDefault()
   * @see #setFastTrackLowestAnalysisFrequencyDefault(int)
   */
  protected int fastTrackLowestAnalysisFrequencyDefault = 5000;
  /**
   * Getter for {@link #fastTrackLowestAnalysisFrequencyDefault}: Fast Track lowest
   * analysis frequency by default. 
   * <p> The default value is 5000.
   * @return Fast Track lowest analysis frequency by default.
   */
  public int getFastTrackLowestAnalysisFrequencyDefault() { return fastTrackLowestAnalysisFrequencyDefault; }
  /**
   * Setter for {@link #fastTrackLowestAnalysisFrequencyDefault}: Fast Track lowest
   * analysis frequency by default. 
   * @param newFastTrackLowestAnalysisFrequencyDefault Fast Track lowest analysis frequency by default.
   */
  public ProcessWithPraat setFastTrackLowestAnalysisFrequencyDefault(int newFastTrackLowestAnalysisFrequencyDefault) { fastTrackLowestAnalysisFrequencyDefault = newFastTrackLowestAnalysisFrequencyDefault; return this; }
  
  /**
   * Values to use as the Fast Track lowest analysis frequency for participants who's
   * attribute value matches the corresponding regular expression in
   * {@link #fastTrackOtherPattern}. 
   * <p> By default, it includes one entry: 4500 for male participants.
   * @see #getFastTrackLowestAnalysisFrequencyOther()
   * @see #setFastTrackLowestAnalysisFrequencyOther(Vector<Integer>)
   */
  protected Vector<Integer> fastTrackLowestAnalysisFrequencyOther = new Vector<Integer>(){{ add(4500); }};
  /**
   * Getter for {@link #fastTrackLowestAnalysisFrequencyOther}: Values to use as the Fast
   * Track lowest analysis frequency for participants who's attribute value matches the
   * corresponding regular expression in {@link #fastTrackOtherPattern}. 
   * <p> By default, it includes one entry: 4500 for male participants.
   * @return Values to use as the Fast Track lowest analysis frequency for participants who's attribute value matches the corresponding regular expression in {@link #fastTrackOtherPattern}.
   */
  public Vector<Integer> getFastTrackLowestAnalysisFrequencyOther() { return fastTrackLowestAnalysisFrequencyOther; }
  /**
   * Setter for {@link #fastTrackLowestAnalysisFrequencyOther}: Values to use as the Fast
   * Track lowest analysis frequency for participants who's attribute value matches the
   * corresponding regular expression in {@link #fastTrackOtherPattern}. 
   * @param newFastTrackLowestAnalysisFrequencyOther Values to use as the Fast Track
   * lowest analysis frequency for participants who's attribute value matches the
   * corresponding regular expression in {@link #fastTrackOtherPattern}. 
   */
  public ProcessWithPraat setFastTrackLowestAnalysisFrequencyOther(Vector<Integer> newFastTrackLowestAnalysisFrequencyOther) { fastTrackLowestAnalysisFrequencyOther = newFastTrackLowestAnalysisFrequencyOther; return this; }

  /**
   * Fast Track highest analysis frequency by default.
   * <p> The default value is 7000.
   * @see #getFastTrackHighestAnalysisFrequencyDefault()
   * @see #setFastTrackHighestAnalysisFrequencyDefault(int)
   */
  protected int fastTrackHighestAnalysisFrequencyDefault = 7000;
  /**
   * Getter for {@link #fastTrackHighestAnalysisFrequencyDefault}: Fast Track highest
   * analysis frequency by default. 
   * <p> The default value is 7000.
   * @return Fast Track highest analysis frequency by default.
   */
  public int getFastTrackHighestAnalysisFrequencyDefault() { return fastTrackHighestAnalysisFrequencyDefault; }
  /**
   * Setter for {@link #fastTrackHighestAnalysisFrequencyDefault}: Fast Track highest
   * analysis frequency by default. 
   * @param newFastTrackHighestAnalysisFrequencyDefault Fast Track highest analysis frequency by default.
   */
  public ProcessWithPraat setFastTrackHighestAnalysisFrequencyDefault(int newFastTrackHighestAnalysisFrequencyDefault) { fastTrackHighestAnalysisFrequencyDefault = newFastTrackHighestAnalysisFrequencyDefault; return this; }
  
  /**
   * Values to use as the Fast Track highest analysis frequency for participants who's
   * attribute value matches the corresponding regular expression in
   * {@link #formantOtherPattern}. 
   * <p> By default, it includes one entry: 6500 for male participants.
   * @see #getFastTrackHighestAnalysisFrequencyOther()
   * @see #setFastTrackHighestAnalysisFrequencyOther(Vector<Integer>)
   */
  protected Vector<Integer> fastTrackHighestAnalysisFrequencyOther = new Vector<Integer>(){{ add(6500); }};
  /**
   * Getter for {@link #fastTrackHighestAnalysisFrequencyOther}: Values to use as the Fast
   * Track highest analysis frequency for participants who's attribute value matches the
   * corresponding regular expression in {@link #formantOtherPattern}. 
   * <p> By default, it includes one entry: 6500 for male participants.
   * @return Values to use as the Fast Track highest analysis frequency for participants who's attribute value matches the corresponding regular expression in {@link #formantOtherPattern}.
   */
  public Vector<Integer> getFastTrackHighestAnalysisFrequencyOther() { return fastTrackHighestAnalysisFrequencyOther; }
  /**
   * Setter for {@link #fastTrackHighestAnalysisFrequencyOther}: Values to use as the Fast
   * Track highest analysis frequency for participants who's attribute value matches the
   * corresponding regular expression in {@link #formantOtherPattern}. 
   * @param newFastTrackHighestAnalysisFrequencyOther Values to use as the Fast Track
   * highest analysis frequency for participants who's attribute value matches the
   * corresponding regular expression in {@link #formantOtherPattern}. 
   */
  public ProcessWithPraat setFastTrackHighestAnalysisFrequencyOther(Vector<Integer> newFastTrackHighestAnalysisFrequencyOther) { fastTrackHighestAnalysisFrequencyOther = newFastTrackHighestAnalysisFrequencyOther; return this; }

  
  /**
   * Minimum duration of a segment for FastTrack analysis. Default is 0.030000000000001s. Segments
   * shorter than this will be ignored, and empty values returned.
   * <p> <em>NB</em> The FastTrack limit is 0.03s (30ms), but because of possible rounding
   * errors in Praat arithemitic, the default is set slightly higher than this.
   * @see #getFastTrackMinimumDuration()
   * @see #setFastTrackMinimumDuration(double)
   */
  protected double fastTrackMinimumDuration = 0.030000000000001;
  /**
   * Getter for {@link #fastTrackMinimumDuration}: Minimum duration of a segment for
   * FastTrack analysis. Default is 0.030000000000001s. Segments shorter than this will be
   * ignored, and empty values returned. 
   * <p> <em>NB</em> The FastTrack limit is 0.03s (30ms), but because of possible rounding
   * errors in Praat arithemitic, the default is set slightly higher than this.
   * @return Minimum duration of a segment for FastTrack analysis. Segments shorter than
   * this will be ignored, and empty values returned. 
   */
  public double getFastTrackMinimumDuration() { return fastTrackMinimumDuration; }
  /**
   * Setter for {@link #fastTrackMinimumDuration}: Minimum duration of a segment for
   * FastTrack analysis. Segments shorter than this will be ignored, and empty values
   * returned. 
   * @param newFastTrackMinimumDuration Minimum duration of a segment for FastTrack
   * analysis. Segments shorter than this will be ignored, and empty values returned. 
   */
  public ProcessWithPraat setFastTrackMinimumDuration(double newFastTrackMinimumDuration) { fastTrackMinimumDuration = newFastTrackMinimumDuration; return this; }


  // Methods:
      
  /**
   * Default constructor.
   */
  public ProcessWithPraat() {
  } // end of constructor
   
  /**
   * Adds an array of attribute names to {@link #attributes}.
   * @param attributes
   */
  public void addAttributes(String[] attributes) {
    if (attributes != null) {
      for(String a : attributes) this.attributes.add(a);
    }
  } // end of addAttributes()

  /**
   * Run the task.
   */
  public void run() {
    CSVParser in = null;
    CSVPrinter out = null;
    File outputFile = null;
    PreparedStatement sqlSpeakerAttribute = null;
    
    String baseUrl = store.getBaseUrl();
    // we want file URLs, not http/https, so unset the base URL
    store.setBaseUrl(null);
    
    try {
      runStart();

      setStatus("Counting records...");
      long iRecordCount = -1; // don't count header row
      BufferedReader r =  new BufferedReader(new FileReader(dataFile));
      while (r.readLine() != null) iRecordCount++;
      r.close();

      setStatus(
        "Extracting measurements for "+iRecordCount+" record"+(iRecordCount==1?"":"s")+"...");
      iPercentComplete = 1; // so the progress bar stops displaying as 'indeterminate'

      sqlSpeakerAttribute = store.getConnection().prepareStatement(
        "SELECT annotation_participant.label"
        +" FROM speaker"
        +" INNER JOIN annotation_participant"
        +"  ON annotation_participant.speaker_number = speaker.speaker_number"
        +"  AND annotation_participant.layer = ?"
        +" WHERE speaker.name = ?");
      if (formantDifferentiationLayerId != null)
        attributes.add(formantDifferentiationLayerId);
      if (pitchDifferentiationLayerId != null)
        attributes.add(pitchDifferentiationLayerId);
      if (intensityDifferentiationLayerId != null)
        attributes.add(intensityDifferentiationLayerId);
      if (fastTrackDifferentiationLayerId != null)
        attributes.add(fastTrackDifferentiationLayerId);

      outputFile = File.createTempFile(
        IO.WithoutExtension(fileName) + "-", ".csv", store.getFiles());
      CSVFormat format = CSVFormat.EXCEL.withDelimiter(fieldDelimiter);
      out = new CSVPrinter(new FileWriter(outputFile), format);
      in = new CSVParser(new FileReader(dataFile), format);
      Iterator<CSVRecord> records = in.iterator();

      // headers
         
      CSVRecord headers = records.next();
      if (passThroughData) for (String sHeader : headers) out.print(sHeader);

      if (extractF1 || extractF2 || extractF3) {
        if (useFastTrack && fastTrackCoefficients) {
          for (int f = 1; f <= Math.max(3, fastTrackNumberOfFormants); f++) {
            for (int c = 0; c <= fastTrackNumberOfCoefficients; c++) {
              out.print("F"+f+"-coeff-"+c);
            }
          }
        }
        for (Double point : getSamplePoints()) {
          out.print("time_"+point);
          if (extractF1) out.print("F1-time_"+point);
          if (extractF2) out.print("F2-time_"+point);
          if (extractF3) out.print("F3-time_"+point);
        } // next sample point
      } // extracting formants
      if (extractMinimumPitch) out.print("MinPitch");
      if (extractMeanPitch) out.print("MeanPitch");
      if (extractMaximumPitch) out.print("MaxPitch");
      if (extractMaximumIntensity) out.print("MaxIntensity");
      if (extractCOG1) out.print("COG1");
      if (extractCOG2) out.print("COG2");
      if (extractCOG23) out.print("COG2/3");
      if (customScript != null && customScript.trim().length() > 0)
      { // figure out headers for the custom script output
        customScriptHeaders = new Vector<String>();
        // for each line	    
        for (String line : customScript.split("\n")) {
          line = line.trim();
          if (line.startsWith("print ")) {
            // figure out a name
            String header = line
              // remove function call
              .replaceAll("^print ","") 
              // remove any newline variable references
              .replace("newline","") 
              // remove formatting specifiers lik :1
              .replaceAll(":\\p{javaDigit}","") 
              // remove anything that's not alphanumeric (or _)
              .replaceAll("[^\\p{javaLetter}\\p{javaDigit}_]",""); 
            if (header.length() == 0) { // nothing left, so invent one
              header = "output" + (customScriptHeaders.size()+1);
            }
            customScriptHeaders.add(header);
            out.print(header);
          }
        }
      }
      out.print("Error");

      String currentFile = null;
      String currentSpeaker = null;
      Vector<CSVRecord> batch = new Vector<CSVRecord>();
      while (records.hasNext()) {
        if (bCancelling) break;
	       
        CSVRecord record = records.next();

        // detect change in file 
        String transcript = record.get(transcriptIdColumn);
        String speaker = record.get(participantNameColumn);
        if (!transcript.equals(currentFile)
            || !speaker.equals(currentSpeaker)) {
          if (currentFile != null && currentSpeaker != null) {
            processBatch(currentFile, sqlSpeakerAttribute, batch, out);
            iPercentComplete = (int)((record.getRecordNumber() * 100) / iRecordCount);
          }
          currentFile = transcript;
          currentSpeaker = speaker;
          batch.clear();
        }
        batch.add(record);
      } // next line
      processBatch(currentFile, sqlSpeakerAttribute, batch, out);
      setStatus("Finished.");
      iPercentComplete = 100;
    } catch (Exception x) {
      setLastException(x);
      if (out != null) {
        try {
          out.println();
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          x.printStackTrace(pw);
          out.print(x.getClass().getSimpleName() + ": " + x.getMessage() + "\n"+sw);
          sw.close();
          pw.close();
          out.println();
        } catch(IOException exception) {
          System.err.println("ProcessWithPraat: " + x.toString());
        }
      }
    } finally {
      if (out != null) try {out.close();} catch(IOException exception) {}
      if (in != null) try {in.close();} catch(IOException exception) {}
      if (outputFile != null) {
        try {
          setResultUrl(baseUrl +"/" + store.getFiles().getName() + "/"
                       + URLEncoder.encode(outputFile.getName(), "UTF-8"));
        } catch(UnsupportedEncodingException impossible) { 
          setResultUrl(baseUrl +"/" + store.getFiles().getName() + "/"
                       + outputFile.getName());
        }
        setResultText("CSV file with measurements");
      }
      if (sqlSpeakerAttribute != null) {
        try {sqlSpeakerAttribute.close();} catch(SQLException exception) {}
      }
      runEnd();
    }
    if (bCancelling) {
      setStatus(getStatus() + " - cancelled.");
    }
    waitToDie();
    outputFile.delete();
    dataFile.delete();
  }

  /**
   * Process a list of lines, all from the same transcript file
   * @param transcript Name of the transcript
   * @param sqlSpeakerAttribute Prepared query that returns a 'label' field that
   * identifies a given participant attribute (parameter 1) given a participant name
   * (parameter 2). 
   * @param batch Collection of source CSV records that make up this batch
   * @param out
   * @throws Exception
   */
  public void processBatch(
    String transcript, PreparedStatement sqlSpeakerAttribute, Vector<CSVRecord> batch,
    CSVPrinter out) throws Exception {
    
    setStatus("processBatch("+transcript+", "+batch.size()+" records)");
    File wav = null;
    String wavError = null;
    try {
      String sWav = store.getMedia(transcript, "", "audio/wav");
      if (sWav != null) {
        wav = new File(new URI(sWav));
      } else {
        wavError = "Transcript has no accessible audio";
      }
    } catch (GraphNotFoundException x) {
      wavError = "Transcript not found";
    }
      
    // get participant attribute values we will need
    HashMap<String,String> attributeValues = new HashMap<String,String>();
    sqlSpeakerAttribute.setString(2, batch.elementAt(0).get(participantNameColumn));
    if (getAttributes() != null) {
      for (String layer : getAttributes()) {
        String attribute = layer.replaceFirst("participant_","");
        sqlSpeakerAttribute.setString(1, attribute);
        ResultSet rs = sqlSpeakerAttribute.executeQuery();
        try {
          String value = "";
          if (rs.next()) {
            value = rs.getString("label");
          }
          attributeValues.put(layer, value);
        } catch(Throwable exception) {
          System.err.println("ERROR "+exception);
        } finally {
          rs.close();
        }	    
      } // next attribute
    }
    
    // participant-differentiatable parameters...
    int formantCeiling = formantCeilingDefault;
    if (formantDifferentiationLayerId != null && formantDifferentiationLayerId.length() > 0) {
      for (int i = 0; i < formantOtherPattern.size(); i++) {
        Pattern pattern = formantOtherPattern.elementAt(i);
        String value = attributeValues.get(formantDifferentiationLayerId);
        if (pattern.matcher(value).matches()) {
          formantCeiling = formantCeilingOther.get(i);
          break;
        }
      } // next pattern
    } // differentiate by participant attribute
    int pitchFloor = pitchFloorDefault;
    int pitchCeiling = pitchCeilingDefault;
    double voicingThreshold = voicingThresholdDefault;
    if (pitchDifferentiationLayerId != null && pitchDifferentiationLayerId.length() > 0) {
      for (int i = 0; i < pitchOtherPattern.size(); i++) {
        Pattern pattern = pitchOtherPattern.elementAt(i);
        String value = attributeValues.get(pitchDifferentiationLayerId);
        if (pattern.matcher(value).matches()) {
          pitchCeiling = pitchCeilingOther.get(i);
          pitchFloor = pitchFloorOther.get(i);
          pitchCeiling = pitchCeilingOther.get(i);
          voicingThreshold = voicingThresholdOther.get(i);
          break;
        }
      } // next pattern
    } // differentiate by participant attribute
    int intensityPitchFloor = intensityPitchFloorDefault;
    if (intensityDifferentiationLayerId != null && intensityDifferentiationLayerId.length() > 0) {
      for (int i = 0; i < intensityOtherPattern.size(); i++) {
        Pattern pattern = intensityOtherPattern.elementAt(i);
        String value = attributeValues.get(intensityDifferentiationLayerId);
        if (pattern.matcher(value).matches()) {
          intensityPitchFloor = intensityPitchFloorOther.get(i);
          break;
        }
      } // next pattern
    } // differentiate by participant attribute
    int fastTrackLowestAnalysisFrequency = fastTrackLowestAnalysisFrequencyDefault;
    int fastTrackHighestAnalysisFrequency = fastTrackHighestAnalysisFrequencyDefault;
    if (fastTrackDifferentiationLayerId != null && fastTrackDifferentiationLayerId.length() > 0) {
      for (int i = 0; i < fastTrackOtherPattern.size(); i++) {
        Pattern pattern = fastTrackOtherPattern.elementAt(i);
        String value = attributeValues.get(fastTrackDifferentiationLayerId);
        if (pattern.matcher(value).matches()) {
          fastTrackLowestAnalysisFrequency = fastTrackLowestAnalysisFrequencyOther.get(i);
          fastTrackHighestAnalysisFrequency = fastTrackHighestAnalysisFrequencyOther.get(i);
          break;
        }
      } // next pattern
    } // differentiate by participant attribute
      
    Vector<Vector<Double>> vTargets = new Vector<Vector<Double>>();
    Vector<String> vErrors = new Vector<String>();
    for (CSVRecord record : batch) {
      if (bCancelling) break;
      String error = "";
      String mark = record.get(markColumn);
      String markEnd = null;
      if (markEndColumn != null) markEnd = record.get(markEndColumn);
      try {
        Double startTime = Double.valueOf(mark);
        Double endTime = Double.valueOf(mark);
        if (markEnd != null) endTime = Double.valueOf(markEnd);
        Vector<Double> tuple = new Vector<Double>();
        tuple.add(startTime);
        tuple.add(endTime);
        vTargets.add(tuple);
        if (useFastTrack
            && (endTime+windowOffset)-(startTime-windowOffset) < fastTrackMinimumDuration) {
          // we know this will be skipped, so add an error
          error = "Duration too short for FastTrack";
        }
      } catch (Exception x) {
        error = "ERROR: " + x.getClass().getSimpleName() + ": " + x.getMessage();
        Vector<Double> tuple = new Vector<Double>();
        tuple.add(Double.valueOf(-1));
        tuple.add(Double.valueOf(-1));
        vTargets.add(tuple);
      } 
      if (wav == null) {
        error = wavError;
      }
      vErrors.add(error);
    } // next batch line

    // run praat
    Vector<Vector<String>> results = measurementsFromFile(
      wav, vTargets, formantCeiling, pitchFloor, pitchCeiling, voicingThreshold,
      intensityPitchFloor, fastTrackLowestAnalysisFrequency, fastTrackHighestAnalysisFrequency,
      attributeValues);
    
    // collate the results
    Enumeration<Vector<Double>> enTargets = vTargets.elements();
    Enumeration<String> enErrors = vErrors.elements();
    Enumeration<Vector<String>> enResults = results.elements();
    for (CSVRecord record : batch) {
      if (bCancelling) break;
      out.println();
      // copy through all columns
      if (passThroughData) for (String sValue : record) out.print(sValue);

      // formants etc.
      String error = "";
      try {
        Vector<String> rowResults = enResults.nextElement();
        // last 'result' is always the 'error' if any, so pass through all but the last result
        for (int r = 0 ; r < rowResults.size() - 1; r++) {
          out.print(rowResults.elementAt(r));
        } // next result
        error = rowResults.lastElement();
      } catch(Exception exception) {
        out.print(exception.getMessage());
      }

      // report error if any, and both errors if there was one earlier
      try {
        if (error.length() > 0) {
          error += "\n" + enErrors.nextElement().toString();
        } else {
          error = enErrors.nextElement().toString();
        }
        out.print(error);
      } catch(Exception exception) {
        out.print("missing");
      }
	    
    } // next result
  } // end of processBatch()
      
  /**
   * Extracts the formants at the given times for the given WAV file
   * @param wav
   * @param vTargets
   * @param formantCeiling
   * @param pitchFloor
   * @param pitchCeiling
   * @param voicingThreshold
   * @param intensityPitchFloor
   * @param fastTrackLowestAnalysisFrequency
   * @param fastTrackHighestAnalysisFrequency
   * @param attributeValues Attribute names (keys) and values for this batch.
   * @return An array lines, where each line contains a string for each extraction
   * datum. e.g. two strings - F1 and F2 
   * @throws Exception
   */
  protected Vector<Vector<String>> measurementsFromFile(
    File wav, Vector<Vector<Double>> targets, Integer formantCeiling,
    Integer pitchFloor, Integer pitchCeiling, Double voicingThreshold,
    Integer intensityPitchFloor, Integer fastTrackLowestAnalysisFrequency,
    Integer fastTrackHighestAnalysisFrequency,
    HashMap<String,String> attributeValues)
    throws Exception {
    Vector<Vector<String>> results = new Vector<Vector<String>>();
    if (wav != null)
    {
      setStatus(
        "Extracting measurements for " + targets.size() 
        + " target" + (targets.size()==1?"":"s")
        + " from: " + wav.getName());

      // if we have the FastTrack source, run scripts in the Fast Track functions
      // directory, so that FastTrack files can be included
      
      File functions = new File(
        new File(new File(store.getFiles(), "FastTrack-master"), "Fast Track"), "functions");
      
      File script = File.createTempFile("ProcessWithPraat-", ".praat", functions);
      PrintWriter scriptWriter = new PrintWriter(script, "UTF-8");
      File tempDirectory = Files.createTempDirectory("ProcessWithPraat-").toFile();
      generateScript(
        scriptWriter, tempDirectory, wav, targets, formantCeiling,
        pitchFloor, pitchCeiling, voicingThreshold,
        intensityPitchFloor, fastTrackLowestAnalysisFrequency, fastTrackHighestAnalysisFrequency,
        attributeValues);
      scriptWriter.close();      
      //setStatus(script);
      
      try {
        String sResult = executeScript(script);
        
        BufferedReader reader = new BufferedReader(
          new StringReader(sResult));
        for (Vector<Double> tuple : targets) {
          Double startTime = tuple.elementAt(0);
          Double endTime = tuple.elementAt(1);
          Vector<String> result = new Vector<String>();
          if (startTime >= 0.0) {
            if (extractF1 || extractF2 || extractF3) {
              if (useFastTrack && fastTrackCoefficients) {
                for (int f = 1; f <= Math.max(3, fastTrackNumberOfFormants); f++) {
                  for (int c = 0; c <= fastTrackNumberOfCoefficients; c++) {
                    result.add(reader.readLine());
                  }
                }
              }
              for (Double point : getSamplePoints()) {
                Double targetTime = Double.valueOf(
                  startTime + ((endTime - startTime) * point));
                result.add(targetTime.toString());
                if (extractF1) result.add(reader.readLine());
                if (extractF2) result.add(reader.readLine());
                if (extractF3) result.add(reader.readLine());
              } // next sample point
            } // extracting formants
            if (extractMinimumPitch) result.add(reader.readLine());
            if (extractMeanPitch) result.add(reader.readLine());
            if (extractMaximumPitch) result.add(reader.readLine());
            if (extractMaximumIntensity) result.add(reader.readLine());
            if (extractCOG1) result.add(reader.readLine());
            if (extractCOG2) result.add(reader.readLine());
            if (extractCOG23) result.add(reader.readLine());
            for (String field : customScriptHeaders) result.add(reader.readLine());
          } else {
            if (extractF1 || extractF2 || extractF3) {
              if (useFastTrack && fastTrackCoefficients) {
                for (int f = 1; f <= Math.max(3, fastTrackNumberOfFormants); f++) {
                  for (int c = 0; c <= fastTrackNumberOfCoefficients; c++) {
                    result.add("");
                  }
                }
              }
              for (Double point : getSamplePoints()) {
                result.add("");
                if (extractF1) result.add("");
                if (extractF2) result.add("");
                if (extractF3) result.add("");
              } // next sample point
            } // extracting formants
            if (extractMinimumPitch) result.add("");
            if (extractMeanPitch) result.add("");
            if (extractMaximumPitch) result.add("");
            if (extractMaximumIntensity) result.add("");
            if (extractCOG1) result.add("");
            if (extractCOG2) result.add("");
            if (extractCOG23) result.add("");
            for (String field : customScriptHeaders) result.add("");
          }
          // no error
          result.add("");
          results.add(result);
        } // next target
        IO.RecursivelyDelete(tempDirectory);
      } catch (Exception stderr) {
        String error = stderr.getMessage();
        for (Vector<Double> tuple : targets) {
          Vector<String> result = new Vector<String>();
          if (extractF1 || extractF2 || extractF3) {
            if (useFastTrack && fastTrackCoefficients) {
              for (int f = 1; f <= Math.max(3, fastTrackNumberOfFormants); f++) {
                for (int c = 0; c <= fastTrackNumberOfCoefficients; c++) {
                  result.add("");
                }
              }
            }
            for (Double point : getSamplePoints()) {
              result.add("");
              if (extractF1) result.add("");
              if (extractF2) result.add("");
              if (extractF3) result.add("");
            } // next sample point
          } // extracting formants
          if (extractMinimumPitch) result.add("");
          if (extractMeanPitch) result.add("");
          if (extractMaximumPitch) result.add("");
          if (extractMaximumIntensity) result.add("");
          if (extractCOG1) result.add("");
          if (extractCOG2) result.add("");
          if (extractCOG23) result.add("");
          for (String field : customScriptHeaders) result.add("");
          // error only once
          if (error != null) {
            result.add(error);
            error = null;
          } else {
            result.add("");
          }
          results.add(result);
        } // next target
      } finally {
        script.delete();
      }
    } else { // no media file - return a well-formed result anyway
      for (Vector<Double> tuple : targets) {
        Vector<String> result = new Vector<String>();
        if (extractF1 || extractF2 || extractF3) {
          if (useFastTrack && fastTrackCoefficients) {
            for (int f = 1; f <= Math.max(3, fastTrackNumberOfFormants); f++) {
              for (int c = 0; c <= fastTrackNumberOfCoefficients; c++) {
                result.add("");
              }
            }
          }
          for (Double point : getSamplePoints()) {
            result.add("");
            if (extractF1) result.add("");
            if (extractF2) result.add("");
            if (extractF3) result.add("");
          } // next sample point
        } // extracting formants
        if (extractMinimumPitch) result.add("");
        if (extractMeanPitch) result.add("");
        if (extractMaximumPitch) result.add("");
        if (extractMaximumIntensity) result.add("");
        if (extractCOG1) result.add("");
        if (extractCOG2) result.add("");
        if (extractCOG23) result.add("");
        for (String field : customScriptHeaders) result.add("");
        results.add(result);
      } // next target
    }
    return results;
  } // end of measurementsFromFile()
  
  /**
   * Extracts the formants at the given times for the given WAV file
   * @param scriptWriter Writer to write the script to.
   * @param tempDirectory A directory that can be used for temporary files generated by the script.
   * @param wav WAV file to analyse.
   * @param vTargets 
   * @param formantCeiling
   * @param pitchFloor
   * @param pitchCeiling
   * @param voicingThreshold
   * @param intensityPitchFloor
   * @param fastTrackLowestAnalysisFrequency
   * @param fastTrackHighestAnalysisFrequency
   * @param attributeValues Attribute names (keys) and values for this batch.
   * @return An array lines, where each line contains a string for each extraction
   * datum. e.g. two strings - F1 and F2 
   * @throws Exception
   */
  public void generateScript(
    PrintWriter scriptWriter, File tempDirectory,
    File wav, Vector<Vector<Double>> targets, Integer formantCeiling,
    Integer pitchFloor, Integer pitchCeiling, Double voicingThreshold,
    Integer intensityPitchFloor, Integer fastTrackLowestAnalysisFrequency,
    Integer fastTrackHighestAnalysisFrequency,
    HashMap<String,String> attributeValues)
    throws Exception {
    
      scriptWriter.write("Open long sound file... " + wav.getPath());
      scriptWriter.write("\nRename... soundfile");
      // variables: formantCeiling, pitchFloor, voicingThreshold, pitchCeiling, intensityPitchFloor
     
      if (useFastTrack) {
        scriptWriter.write("\n# FastTrack:");
        scriptWriter.write("\ninclude utils/trackAutoselectProcedure.praat");
        // load default global settings
        scriptWriter.write("\n@getSettings");
        // override global settings that we know about
        scriptWriter.write("\ntime_step = " + fastTrackTimeStep);
        scriptWriter.write("\nbasis_functions$ = \""+fastTrackBasisFunctions+"\"");
        scriptWriter.write("\nerror_method$ = \""+fastTrackErrorMethod+"\"");
        scriptWriter.write("\nmethod$ = \""+fastTrackTrackingMethod+"\"");
        scriptWriter.write(
          "\nenable_F1_frequency_heuristic = " + (fastTrackEnableF1FrequencyHeuristic?"1":"0"));
        scriptWriter.write("\nmaximum_F1_frequency_value = " + fastTrackMaximumF1FrequencyValue);
        scriptWriter.write(
          "\nenable_F1_bandwidth_heuristic = " + (fastTrackEnableF1BandwidthHeuristic?"1":"0"));
        scriptWriter.write("\nmaximum_F1_bandwidth_value = " + fastTrackMaximumF1BandwidthValue);
        scriptWriter.write(
          "\nenable_F2_bandwidth_heuristic = " + (fastTrackEnableF2BandwidthHeuristic?"1":"0"));
        scriptWriter.write("\nmaximum_F2_bandwidth_value = " + fastTrackMaximumF2BandwidthValue);
        scriptWriter.write(
          "\nenable_F3_bandwidth_heuristic = " + (fastTrackEnableF3BandwidthHeuristic?"1":"0"));
        scriptWriter.write("\nmaximum_F3_bandwidth_value = " + fastTrackMaximumF3BandwidthValue);
        scriptWriter.write(
          "\nenable_F4_frequency_heuristic = " + (fastTrackEnableF4FrequencyHeuristic?"1":"0"));
        scriptWriter.write("\nminimum_F4_frequency_value = " + fastTrackMinimumF4FrequencyValue);
        scriptWriter.write("\nenable_rhotic_heuristic = " + (fastTrackEnableRhoticHeuristic?"1":"0"));
        scriptWriter.write(
          "\nenable_F3F4_proximity_heuristic = "
          + (fastTrackEnableF3F4ProximityHeuristic?"1":"0"));
        scriptWriter.write("\noutput_bandwidth = 1");
        scriptWriter.write("\noutput_predictions = 1");
        scriptWriter.write("\noutput_pitch = 1");
        scriptWriter.write("\noutput_intensity = 1");
        scriptWriter.write("\noutput_harmonicity = 1");
        scriptWriter.write("\noutput_normalized_time = 1");
        scriptWriter.write("\ndir$ = \"");
        scriptWriter.write(tempDirectory.getPath());
        scriptWriter.write("\"");
        scriptWriter.write("\nsteps = " + fastTrackNumberOfSteps);
        scriptWriter.write("\ncoefficients = " + fastTrackNumberOfCoefficients);
        scriptWriter.write("\nformants = " + fastTrackNumberOfFormants);
        scriptWriter.write("\nout_formant = 2"); // give use the formant object
        scriptWriter.write("\nimage = 0"); // and nothing else...
        scriptWriter.write("\nmax_plot = 4000");
        scriptWriter.write("\nout_table = 0");
        scriptWriter.write("\nout_all = 0");
        scriptWriter.write("\ncurrent_view = 0");
        scriptWriter.write("\nfastTrackMinimumDuration = " + fastTrackMinimumDuration);        
      }
      MessageFormat fmtFormantScript = new MessageFormat(
        (useFastTrack?
         // FastTrack has a limit of 30ms, below which it throws errors
         "\nif windowDuration >= fastTrackMinimumDuration":"")
        +(extractF1?
          "\n  result = Get value at time... 1 {0,number,#.###} Hertz Linear"
          +"\n  print ''result:0''"
          +"\n  printline":"")
        +(extractF2?
          "\n  result = Get value at time... 2 {0,number,#.###} Hertz Linear"
          +"\n  print ''result:0''"
          +"\n  printline":"")
        +(extractF3?
          "\n  result = Get value at time... 3 {0,number,#.###} Hertz Linear"
          +"\n  print ''result:0''"
          +"\n  printline":"")
        +(useFastTrack?
          "\nelse"
          +"\n  # sample is too short, output blank values"
          +"\n  result$ = \"\""
          +(extractF1?
            "\n  print ''result$''"
            +"\n  printline":"")
          +(extractF2?
            "\n  print ''result$''"
            +"\n  printline":"")
          +(extractF3?
            "\n  print ''result$''"
            +"\n  printline":"")
          +"\nendif":""), 
        Locale.UK);
      StringBuilder customAttributes = new StringBuilder();
      for (String attribute : attributeValues.keySet()) {
        customAttributes.append("\n");
        customAttributes.append(attribute.replaceAll("[^A-Za-z0-9]", "_"));
        customAttributes.append("$ = \"");
        customAttributes.append(attributeValues.get(attribute).replace("\"","\\\""));
        customAttributes.append("\"");
      }
      String customScriptSnippet = // TODO participant attributes
        "\n######### CUSTOM SCRIPT STARTS HERE #########"
        // these for backwards compatibility
        +"\nsampleStartTime = {0,number,#.###}" 
        +"\nsampleEndTime = {1,number,#.###}"
        +"\nsampleDuration = {7,number,#.###}"
        // absolute start/end time (relative to beginning of original recording)
        +"\nwindowOffset = {15,number,#.###}"
        +"\nwindowAbsoluteStart = {0,number,#.###}"
        +"\nwindowAbsoluteEnd = {1,number,#.###}"
        +"\nwindowDuration = {7,number,#.###}"
        +"\ntargetAbsoluteStart = {12,number,#.###}"
        +"\ntargetAbsoluteEnd = {13,number,#.###}"
        // start/end time within the extracted sample (the target +/- context)
        +"\ntargetStart = {8,number,#.###}"
        +"\ntargetEnd = {9,number,#.###}"
        +"\ntargetDuration = {14,number,#.###}"
        +"\nsampleNumber = {10,number,#0}"
        +"\nsampleName$ = \"sample{10,number,#0}\""
        +customAttributes.toString()
        +"\nselect Sound sample{10,number,#0}"
        +"\n{11}"
        +"\n##########  CUSTOM SCRIPT ENDS HERE  #########";
      MessageFormat fmtScript = new MessageFormat(
        "\nselect LongSound soundfile"
        +"\nExtract part... {0,number,#.###} {1,number,#.###} 0"
        +"\nRename... sample{10,number,#0}"
        +(extractF3 || extractF2 || extractF1?
          "\nselect Sound sample{10,number,#0}"
          +"\n"
          + (useFastTrack?
             "windowDuration = {1,number,#.###} - {0,number,#.###}"
             // FastTrack has a limit of 30ms, below which it throws errors
             +"\nif windowDuration >= fastTrackMinimumDuration"
             +"\n  @trackAutoselect: selected(), dir$, {17,number,#0}, {18,number,#0},"
             +" steps, coefficients, formants, method$, image, selected(), current_view, max_plot,"
             +" out_formant, out_table, out_all"
             +(fastTrackCoefficients?
               // F1
               "\n  for c from 1 to coefficients + 1"
               +"\n    coeff = trackAutoselect.f1coeffs#[c]"
               +"\n    print ''coeff:0''"
               +"\n    printline"
               +"\n  endfor"
               // F2
               +"\n  for c from 1 to coefficients + 1"
               +"\n    coeff = trackAutoselect.f2coeffs#[c]"
               +"\n    print ''coeff:0''"
               +"\n    printline"
               +"\n  endfor"
               // F3
               +"\n  for c from 1 to coefficients + 1"
               +"\n    coeff = trackAutoselect.f3coeffs#[c]"
               +"\n    print ''coeff:0''"
               +"\n    printline"
               +"\n  endfor"
               +(fastTrackNumberOfFormants>=4?
                 // F4
                 "\n  for c from 1 to coefficients + 1"
                 +"\n    coeff = trackAutoselect.f4coeffs#[c]"
                 +"\n    print ''coeff:0''"
                 +"\n    printline"
                 +"\n  endfor"
                 :"")
               :"")
             +(fastTrackCoefficients?
               "\nelse"
               +"\n  # sample is too short, output blank values"
               +"\n  coeff$ = \"\""
               +"\n  for c from 1 to coefficients + 1"
               +"\n    print ''coeff$''"
               +"\n    printline"
               +"\n  endfor"
               +"\n  for c from 1 to coefficients + 1"
               +"\n    print ''coeff$''"
               +"\n    printline"
               +"\n  endfor"
               +"\n  for c from 1 to coefficients + 1"
               +"\n    print ''coeff$''"
               +"\n    printline"
               +"\n  endfor"
               +(fastTrackNumberOfFormants>=4?
                 // F4
                 "\n  for c from 1 to coefficients + 1"
                 +"\n    print ''coeff$''"
                 +"\n    printline"
                 +"\n  endfor"
                 :"")
               :"")
             +"\nendif" // sample too short
             // !useFastTrack:
             :"formantCeiling = {2,number,#0}"
             +"\n"+getScriptFormant()):"")
        +"{3}" // from fmtFormantScript
        +(extractF3 || extractF2 || extractF1 || fastTrackCoefficients?
          (useFastTrack?"\nif windowDuration >= fastTrackMinimumDuration":"")
          +"\n  Remove" // formant object
          +(useFastTrack?"\nendif":"")
          :"")
        +(extractMinimumPitch || extractMeanPitch || extractMaximumPitch?
          "\nselect Sound sample{10,number,#0}"
          +"\npitchFloor = {4,number,#0}"
          +"\nvoicingThreshold = {6,number,#.#}"
          +"\npitchCeiling = {5,number,#0}"
          +"\n" + getScriptPitch()
          +(extractMinimumPitch?
            "\nresult = Get minimum... {8,number,#.###} {9,number,#.###} Hertz Parabolic"
            +"\nprint ''result:0''"
            +"\nprintline":"")
          +(extractMeanPitch?
            "\nresult = Get mean... {8,number,#.###} {9,number,#.###} Hertz" 
            +"\nprint ''result:0''"
            +"\nprintline":"")
          +(extractMaximumPitch?
            "\nresult = Get maximum... {8,number,#.###} {9,number,#.###} Hertz Parabolic"
            +"\nprint ''result:0''"
            +"\nprintline":"")
          +"\nRemove":"") // pitch object
        +(extractMaximumIntensity?
          "\nselect Sound sample{10,number,#0}"
          +"\nintensityPitchFloor = {16,number,#0}"
          +"\n"+getScriptIntensity()
          +"\nresult = Get maximum... {8,number,#.###} {9,number,#.###} Parabolic"
          +"\nprint ''result''"
          +"\nprintline"
          +"\nRemove":"") // intensity object
        +(extractCOG1 || extractCOG2 || extractCOG23?
          "\nselect Sound sample{10,number,#0}"
          +"\n" + getScriptSpectrum()
          +(extractCOG1?
            "\nresult = Get centre of gravity... 1"
            +"\nprint ''result:0''"
            +"\nprintline":"")
          +(extractCOG2?
            "\nresult = Get centre of gravity... 2"
            +"\nprint ''result:0''"
            +"\nprintline":"")
          +(extractCOG23?
            "\nresult = Get centre of gravity... 2/3"
            +"\nprint ''result:0''"
            +"\nprintline":"")
          +"\nRemove":"") // spectrum object
        +(customScript != null && customScript.trim().length() > 0?customScriptSnippet:"")
        +"\nselect Sound sample{10,number,#0}"
        +"\nRemove", // sound sample
        Locale.UK);
      int count = 0;
      for (Vector<Double> tuple : targets) {
        Double startTime = tuple.elementAt(0);
        Double endTime = tuple.elementAt(1);
        if (startTime >= 0.0) {
          Double startWindow = startTime - windowOffset;
          if (startWindow < 0.0) {
            startWindow = 0.0;
          }
          Double endWindow = endTime + windowOffset;
          Double relativeEndWindow = endWindow - startWindow;
          Double relativeStartTime = startTime - startWindow;
          Double relativeEndTime = endTime - startWindow;
          // generate formant script for multiple targets
          String sFormantScript = "";
          if (extractF1 || extractF2 || extractF3) {
            for (Double point : getSamplePoints()) {
              Double targetTime = Double.valueOf(
                startTime + ((endTime - startTime) * point));
              Double relativeTime = targetTime - startWindow;
              if (startWindow < 0.0) {
                relativeTime += startWindow;
              }
              Object[] oFormantArg = { relativeTime };
              sFormantScript += fmtFormantScript.format(oFormantArg);
            } // next sample point
          } // extracting formants
		  
          Object[] oArgs = {
            /* 0=*/ startWindow, endWindow, formantCeiling, 
            /* 3=*/ sFormantScript, 
            /* 4=*/ pitchFloor, pitchCeiling, voicingThreshold, 
            /* 7=*/ relativeEndWindow, relativeStartTime, relativeEndTime,
            /*10=*/ Integer.valueOf(count++),
            /*11=*/ customScript,
            /*12=*/ startTime, endTime, (endTime - startTime), windowOffset,
            /*16=*/ intensityPitchFloor, 
            /*17=*/ fastTrackLowestAnalysisFrequency, fastTrackHighestAnalysisFrequency
          };
          scriptWriter.write(fmtScript.format(oArgs));
        } // valid interval
      } // next target
  }
  
  /**
   * Executes the given script in Praat.
   * @param script The script to execute.
   * @return Output of the script
   * @throws Exception
   */
  protected String executeScript(File script) throws Exception {
    
    // set up Praat execution
    File praatPath = new File(store.getSystemAttribute("praatPath"));
    File executableFile = new File(praatPath, "praat");
    String osName = java.lang.System.getProperty("os.name");
    if (osName.startsWith("Windows")) {
      executableFile = new File(praatPath, "Praat.exe");
    } else if (osName.startsWith("Mac")) {
      executableFile = new File(
        new File(new File(new File(praatPath, "Praat.app"), "Contents"), "MacOS"), "Praat");
    }
    Execution praat = new Execution().setExe(executableFile);
    praat.arg("--no-pref-files");
    if (osName.startsWith("Windows")) praat.arg("-a");
    praat.arg("--run");
    praat.arg(script.getPath());

    // run Praat
    praat.run();

    // get output
    String stderr = praat.getError().toString();
    if (stderr.trim().length() > 0) throw new Exception(stderr);

    return praat.getInput().toString();
    
  } // end of executeScript()  
  
} // end of class ProcessWithPraat
