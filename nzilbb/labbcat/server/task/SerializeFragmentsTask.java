//
// Copyright 2017-2021 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.task;

import java.io.*;
import java.lang.*;
import java.lang.reflect.*;
import java.net.*;
import java.sql.*;
import java.text.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import nzilbb.ag.Graph;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.db.ConsolidatedGraphSeries;
import nzilbb.labbcat.server.db.FragmentSeries;
import nzilbb.labbcat.server.db.ResultSeries;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.servlet.SerializeFragments;
import nzilbb.util.IO;
import nzilbb.util.MonitorableSeries;

/**
 * Task for converting graph fragments.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class SerializeFragmentsTask extends Task {

  private SerializeFragments exporter = new SerializeFragments();
  private MonitorableSeries<Graph> fragments = null;
   
  /**
   * Collection of utterances to convert.
   * @see #getUtterances()
   * @see #setUtterances(IUtteranceCollection)
   */
  protected List<String> utterances;
  /**
   * Getter for {@link #utterances}: Collection of utterances to convert.
   * @return Collection of utterances to convert.
   */
  public List<String> getUtterances() { return utterances; }
  /**
   * Setter for {@link #utterances}: Collection of utterances to convert.
   * @param vNewUtterances Collection of utterances to convert.
   */
  public SerializeFragmentsTask setUtterances(List<String> vNewUtterances) {
    utterances = vNewUtterances;
    return this;
  }
  
  /**
   * Layers selected for serialization.
   * @see #getLayers()
   * @see #setLayers(Collection)
   */
  protected Collection<String> layers;
  /**
   * Getter for {@link #layers}: Layers selected for serialization.
   * @return Layers selected for serialization.
   */
  public Collection<String> getLayers() { return layers; }
  /**
   * Setter for {@link #layers}: Layers selected for serialization.
   * @param newLayers Layers selected for serialization.
   */
  public SerializeFragmentsTask setLayers(Collection<String> newLayers) { layers = newLayers; return this; }
  
  /**
   * Directory into which the resulting zip file should be copied.
   */
  private File fResultDirectory;
  /**
   * ResultDirectory accessor 
   * @return Directory into which the resulting zip file should be copied.
   */
  public File getResultDirectory() { return fResultDirectory; }
  /**
   * ResultDirectory mutator
   * @param fNewResultDirectory Directory into which the resulting zip file should be copied.
   */
  public SerializeFragmentsTask setResultDirectory(File fNewResultDirectory) { fResultDirectory = fNewResultDirectory; return this; }
   
  /**
   * Full base URL for the result - i.e. http://server.name/path/to/result/directory/ -
   * this must not contain the file name and must have a trailing slash. 
   */
  private String sResultBaseUrl;
  /**
   * ResultBaseUrl accessor 
   * @return Full base URL for the result 
   * - i.e. http://server.name/path/to/result/directory/ - this must not contain the file
   * name and must have a trailing slash. 
   */
  public String getResultBaseUrl() { return sResultBaseUrl; }
  /**
   * ResultBaseUrl mutator
   * @param sNewResultBaseUrl Full base URL for the result
   * - i.e. http://server.name/path/to/result/directory/ - this must not contain the file
   * name and must have a trailing slash. 
   */
  public SerializeFragmentsTask setResultBaseUrl(String sNewResultBaseUrl) { sResultBaseUrl = sNewResultBaseUrl; return this; }
   
  /**
   * MIME type for export
   * @see #getMimeType()
   * @see #setMimeType(String)
   */
  protected String sMimeType;
  /**
   * Getter for {@link #sMimeType}: MIME type for export
   * @return MIME type for export
   */
  public String getMimeType() { return sMimeType; }
  /**
   * Setter for {@link #sMimeType}: MIME type for export
   * @param sNewMimeType MIME type for export
   */
  public SerializeFragmentsTask setMimeType(String sNewMimeType) { sMimeType = sNewMimeType; return this; }
   
  /**
   * Base URL.
   * @see #getBaseUrl()
   * @see #setBaseUrl(String)
   */
  protected String baseUrl;
  /**
   * Getter for {@link #baseUrl}: Base URL.
   * @return Base URL.
   */
  public String getBaseUrl() { return baseUrl; }
  /**
   * Setter for {@link #baseUrl}: Base URL.
   * @param newBaseUrl Base URL.
   */
  public SerializeFragmentsTask setBaseUrl(String newBaseUrl) { baseUrl = newBaseUrl; return this; }

  /**
   * Whether to include the layers required by the serializer, or only the selected
   * layers. Default is <var>false</var>. 
   * @see #getIncludeRequiredLayers()
   * @see #setIncludeRequiredLayers(boolean)
   */
  protected boolean includeRequiredLayers = false;
  /**
   * Getter for {@link #includeRequiredLayers}: Whether to include the layers required by
   * the serializer, or only the selected layers. 
   * @return Whether to include the layers required by the serializer, or only the
   * selected layers. 
   */
  public boolean getIncludeRequiredLayers() { return includeRequiredLayers; }
  /**
   * Setter for {@link #includeRequiredLayers}: Whether to include the layers required by
   * the serializer, or only the selected layers. 
   * @param newIncludeRequiredLayers Whether to include the layers required by the
   * serializer, or only the selected layers. 
   */
  public SerializeFragmentsTask setIncludeRequiredLayers(boolean newIncludeRequiredLayers) { includeRequiredLayers = newIncludeRequiredLayers; return this; }

  /**
   * The search results ID, if all matches are for export - i.e. result.search_id.
   * @see #getSearchId()
   * @see #setSearchId(long)
   */
  protected long searchId;
  /**
   * Getter for {@link #searchId}: The search results ID, if all matches are for export -
   * i.e. result.search_id. 
   * @return The search results ID, if all matches are for export - i.e. result.search_id.
   */
  public long getSearchId() { return searchId; }
  /**
   * Setter for {@link #searchId}: The search results ID, if all matches are for export -
   * i.e. result.search_id. 
   * @param newSearchId The search results ID, if all matches are for export -
   * i.e. result.search_id. 
   */
  public SerializeFragmentsTask setSearchId(long newSearchId) { searchId = newSearchId; return this; }
  /**
   * Collection Name
   * @see #getCollectionName()
   * @see #setCollectionName(String)
   */
  protected String collectionName;
  /**
   * Getter for {@link #collectionName}: Collection Name
   * @return Collection Name
   */
  public String getCollectionName() { return collectionName; }
  /**
   * Setter for {@link #collectionName}: Collection Name
   * @param newCollectionName Collection Name
   */
  public SerializeFragmentsTask setCollectionName(String newCollectionName) { collectionName = newCollectionName; return this; }
   
  /**
   * Whether to prefix fragment names with a numeric serial number or not.
   * @see #getPrefixNames()
   * @see #setPrefixNames(boolean)
   */
  protected boolean prefixNames = true;
  /**
   * Getter for {@link #prefixNames}: Whether to prefix fragment names with a numeric
   * serial number or not.
   * @return Whether to prefix fragment names with a numeric serial number or not.
   */
  public boolean getPrefixNames() { return prefixNames; }
  /**
   * Setter for {@link #prefixNames}: Whether to prefix fragment names with a numeric
   * serial number or not.
   * @param newPrefixNames Whether to prefix fragment names with a numeric serial number or not.
   */
  public SerializeFragmentsTask setPrefixNames(boolean newPrefixNames) { prefixNames = newPrefixNames; return this; }

  /**
   * Whether to add an tag identifying the target annotation or not.
   * @see #getTagTarget()
   * @see #setTagTarget(boolean)
   */
  protected boolean tagTarget = false;
  /**
   * Getter for {@link #tagTarget}: Whether to add an tag identifying the target
   * annotation or not. 
   * @return Whether to add an tag identifying the target annotation or not.
   */
  public boolean getTagTarget() { return tagTarget; }
  /**
   * Setter for {@link #tagTarget}: Whether to add an tag identifying the target
   * annotation or not. 
   * @param newTagTarget Whether to add an tag identifying the target annotation or not.
   */
  public SerializeFragmentsTask setTagTarget(boolean newTagTarget) { tagTarget = newTagTarget; return this; }
  
  /**
   * Graph store.
   * @see #getStore()
   * @see #setStore(SqlGraphStoreAdministration)
   */
  protected SqlGraphStoreAdministration store;
  /**
   * Getter for {@link #store}: Graph store.
   * @return Graph store.
   */
  public SqlGraphStoreAdministration getStore() { return store; }
  /**
   * Setter for {@link #store}: Graph store.
   * @param newStore Graph store.
   */
  public SerializeFragmentsTask setStore(SqlGraphStoreAdministration newStore) { store = newStore; return this; }

  /** 
   * Constructor
   */
  public SerializeFragmentsTask(
    String name, long searchId, Collection<String> layers, String mimeType, 
    SqlGraphStoreAdministration store) throws SQLException {
    setCollectionName(name);
    setSearchId(searchId);
    setLayers(layers);
    setResultDirectory(store.getFiles());
    setResultBaseUrl(store.getBaseUrl() + "/" + store.getFiles().getName());
    setMimeType(mimeType);
    setStore(store);
    setName(name.replaceAll("[^a-zA-Z0-9_\\-.]","") + " " + hashCode());
  }   
   
  /** Run the export */
  public void run() {
    runStart();
    
    bCancelling = false;
    long startTime = new java.util.Date().getTime();
    File outFile = null;
    try {
      outFile = File.createTempFile(
        IO.SafeFileNameUrl(getCollectionName()) + "_", ".zip", getResultDirectory());
    } catch(IOException exception) {
      setLastException(exception);
    }
    final File zipFile = outFile;
    final StringBuffer finalName = new StringBuffer();
    try {
      setStatus("Converting fragments to " + sMimeType + "...");

      LinkedHashSet<String> toExport = new LinkedHashSet<String>(layers);
      toExport.add("target");
      String[] layersToExport = toExport.toArray(new String[0]);

      GraphSerializer serializer = store.serializerForMimeType(sMimeType);
      if (serializer == null) {
        throw new Exception("Invalid MIME type: " + sMimeType);
      }
      Schema schema = store.getSchema();
      // configure serializer
      ParameterSet configuration = new ParameterSet();
      // default values
      serializer.configure(configuration, schema);
      // load saved ones
      ConfigurationHelper.LoadConfiguration(
        serializer.getDescriptor(), configuration, store.getSerializersDirectory(), schema);
      serializer.configure(configuration, schema);
      for (String l : serializer.getRequiredLayers()) layers.add(l);
      String[] layersToLoad = layers.toArray(new String[0]);
      if (includeRequiredLayers) {
        toExport = new LinkedHashSet<String>(layers);
        toExport.add("target");
        layersToExport = toExport.toArray(new String[0]);
      }
      MonitorableSeries<Graph> fragmentSource = utterances != null
        ? new FragmentSeries(utterances, store, layersToLoad)
        .setPrefixNames(getPrefixNames())
        .setTagTarget(getTagTarget())
        : new ResultSeries(searchId, store, layersToLoad)
        .setPrefixNames(getPrefixNames())
        .setTagTarget(getTagTarget());
      setStatus("Converting "+fragmentSource.getExactSizeIfKnown()
                + " fragment"+(fragmentSource.getExactSizeIfKnown()==1?"":"s")
                + " to " + sMimeType + "...");
      // if we're not prefixing names, and we're tagging targets
      if (!getPrefixNames() && getTagTarget()) {
        // then we need to consolidate graphs - i.e. catch consecutive fragments that
        // are the same ID, and copy the target tags into the winning version of the graph
        fragmentSource = new ConsolidatedGraphSeries(fragmentSource)
          .copyLayer("target");
      }
      final ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile));
      final HashSet<String> fileNames = new HashSet<String>();
      Consumer<NamedStream> streamConsumer = new Consumer<NamedStream>() {
          public void accept(NamedStream stream) {
            try {
              String name = IO.SafeFileNameUrl(stream.getName());
              if (!fileNames.contains(name)) {
                fileNames.add(name);
                setStatus(name);
                // create the zip entry
                zipOut.putNextEntry(new ZipEntry(name));
                IO.Pump(stream.getStream(), zipOut, false);
              }
            } catch (Exception zx) {
              System.err.println("SerializeFragmentsTask: " + zx);
            } finally {
              try { stream.getStream().close(); } catch(IOException exception) {}
            }
          }
        };
      if (serializer.getCardinality() == GraphSerializer.Cardinality.NToOne
          || (serializer.getCardinality() == GraphSerializer.Cardinality.NToN
              && fragmentSource.estimateSize() == 1)) {
        // there will only be one resulting stream 
        streamConsumer = new Consumer<NamedStream>() {
            public void accept(NamedStream stream) {
              try {
                // not a zipped collection of files
                zipOut.close();
                zipFile.delete();
                
                /// a single file
                IO.Pump(stream.getStream(), new FileOutputStream(zipFile), true);
                finalName.append(stream.getName());
                setResultText("File");
              } catch (Exception zx) {
                System.out.println("SerializeFragmentsTask: " + zx);
              } finally {
                try { stream.getStream().close(); } catch(IOException exception) {}
              }
            }
          };
      } // there will only be one resulting stream 
      
      exporter.serializeFragments(
        getCollectionName(), fragmentSource, serializer,
        streamConsumer,
        new Consumer<SerializationException>() {
          public void accept(SerializationException exception) {
            setLastException(exception);
          }},
        layersToExport, sMimeType, store);
      
      if (serializer.getCardinality() == GraphSerializer.Cardinality.NToOne
          || (serializer.getCardinality() == GraphSerializer.Cardinality.NToN
              && fragmentSource.estimateSize() == 1)) {
        // there will only be one resulting stream 
        setResultText("File");
        // rename the zip file to the correct name
        outFile = new File(zipFile.getParentFile(), finalName.toString());
        zipFile.renameTo(outFile);
      } else {
        zipOut.close();
        setResultText("Files");
      }
      setResultUrl(getResultBaseUrl() + "/" + URLEncoder.encode(outFile.getName(), "UTF-8"));
      setStatus("Done.");
      
      iPercentComplete = 100;
    } catch (Exception ex) {
      setLastException(ex);
      setStatus("ERROR: " + ex.getClass().getName() + " - " + ex.getMessage());
    } finally {
      try { store.getConnection().close(); } catch(Exception eClose) {}
      runEnd();
    }
      
    if (bCancelling) {
      setStatus(getStatus() + " - cancelled.");
    }
      
    waitToDie();
    outFile.delete();
  }
   
  public void cancel() {
    exporter.cancel();
    super.cancel();
  } // end of cancel()
   
  public Integer getPercentComplete() {
    if (iPercentComplete != 100) {
      iPercentComplete = exporter.getPercentComplete();
    }
    return super.getPercentComplete(); 
  }
   
} // end of class SerializeFragmentsTask
