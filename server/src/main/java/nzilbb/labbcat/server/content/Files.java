//
// Copyright 2026 New Zealand Institute of Language, Brain and Behaviour, 
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
package nzilbb.labbcat.server.content;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import nzilbb.ag.Graph;
import nzilbb.ag.GraphNotFoundException;
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.StoreException;
import nzilbb.ag.PermissionException;
import nzilbb.configure.Parameter;
import nzilbb.configure.ParameterSet;
import nzilbb.util.IO;
import nzilbb.labbcat.server.api.APIRequestContext;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.media.ffmpeg.FfmpegConverter;
import nzilbb.media.MediaException;
import nzilbb.media.MediaThread;

/**
 * Provides checked access to media files under the "files" directory.
 * <p> Only GET is supported. If the request path specifies a media or transcript
 * file, the handler checks the user has access to that media.
 * <p> If the file doesn't exist, and the <q>from</q> request parameters specifies the
 * name of an existing file from which the requested file could be generated, then
 * generation is attempted on the fly.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Files extends APIRequestHandler {

  private File filesDir;
  private Pattern backupFile;

  /**
   * Constructor
   * @param files The real path of the webapp's "files" directory.
   */
  public Files(File files) {
    this.filesDir = files;
    // like: AP511_MikeThorpe.mp4.bak-2025-06-23-18-00-10.mp4
    this.backupFile = Pattern.compile(
      ".*\\.bak-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{2}\\.[^.]*$");
  }

  /**
   * GET handler: checks whether the given is accessible by the current users,
   * whether file exists, can be generated from the file specified by the <q>from</q>
   * request parameter if not, and if all ok, forwards the request to the <q>default</q>
   * servlets for serving content, handling partial content requests, etc.
   * @param pathInfo The URL path.
   * @param parameters Request parameter map.
   * @param realPath Function for translating an absolute URL path into a File.
   * @param fileName Receives the filename for specification in the response headers.
   * @param contentType Receives the content type for specification in the
   * response headers. 
   * @param httpStatus Receives the response status code, in case of error.
   * @param forward Receives a servlet name for the request to be forwarded to.
   */
  public void get(
    String pathInfo, RequestParameters parameters, Function<String,File> realPath,
    Consumer<String> fileName, Consumer<String> contentType, Consumer<Integer> httpStatus,
    Consumer<String> forward) {

    if (pathInfo == null) { // root directory with no slash
      httpStatus.accept(SC_BAD_REQUEST);
      return;
    }
    
    File f = realPath.apply("/files/"+pathInfo);
    if (f.isDirectory()) { // can't list directories
      httpStatus.accept(SC_BAD_REQUEST);
      return;
    }
    try {
      SqlGraphStoreAdministration store = getStore();
      try { // then cache store
        
        // figure out what kind of content it is
        MediaFile media = new MediaFile(f);
        String entity = media.getType().substring(0,1); // audio -> a, video -> v, etc.
        
        // figure out the transcript ID
        String id = null;
        MediaTrackDefinition track = null;
        // is it the transcript file?
        try {
          // check they're allowed access (throws PermissionException if not)
          Graph graph = store.getTranscript(f.getName(), null);
          // getTranscript("transcript.wav") can return "transcript.eaf", check exact match
          if (f.getName().equals(graph.getId())) { // the file name is the same as the ID
            id = graph.getId();
          }
        } catch (GraphNotFoundException notFound) { // nope
        }
        
        if (id == null) { // not the transcript itself
          
          // if it's media, it will be of the form {id}{trackSuffix}.{extension}
          // (compare case-insensitively to avoid a loophole on Windows)
          String lowerCaseName = f.getName().toLowerCase(); 
          for (MediaTrackDefinition t : store.getMediaTracks()) {
            String possibleSuffix = (""+t.getSuffix()+"."+media.getExtension());
            if (lowerCaseName.endsWith(possibleSuffix.toLowerCase())) {
              String possibleId = f.getName().substring(
                0, f.getName().length() - possibleSuffix.length());
              try { // is this an existing, accessible transcript ID?
                Graph graph = store.getTranscript(possibleId, null);
                id = graph.getId();
                track = t;
                break;
              } catch (GraphNotFoundException notFound) { // nope
              }
            } // track suffix seems possible
          } // next track
          if (id != null && track != null) { // media file
            // check they're allowed to access it (throws PermissionException if not)
            String url = store.getMedia(id, track.getSuffix(), media.getMimeType());
            
            if (!context.isUserInRole("admin")) { // they're not an admin user
              // also only provide access to the original file if we don't censor transcripts
              String censorshipRegexp = store.getSystemAttribute("censorshipRegexp");
              if (censorshipRegexp != null && censorshipRegexp.length() > 0
                  // audio files are already censored, but we don't censor video
                  && !entity.equals("audio")) {
                httpStatus.accept(SC_FORBIDDEN);
                return;
              }
            } // not an admin user
            
            if (!f.exists() && parameters.getString("from") != null) {
              // find source file
              MediaFile fromMedia = new MediaFile(new File(parameters.getString("from")));
              if (!fromMedia.getNameWithoutSuffix().equals(media.getNameWithoutSuffix())) {
                context.servletLog(
                  "Trying to generate: " + f.getName() + " from " + parameters.getString("from")
                  + " - wrong name.");
                httpStatus.accept(SC_BAD_REQUEST);
                return;
              }
              File fromDir = new File(
                f.getParentFile().getParentFile(), fromMedia.getExtension());
              File from = new File(fromDir, fromMedia.getName());
              if (from.exists()) {
                // ensure parent directory exists
                f.getParentFile().mkdir();
                convert(new File(store.getSystemAttribute("ffmpegPath")),
                        new MediaFile(from), new MediaFile(f));
              } // source file exists
            } // file doesn't exist, but maybe can be generated
          } // media file
        } // not the transcript itelf
        
        // if we got this far, either it's transcript media the user has access to
        // or it's something unrelated to a specific transcript
        if (!f.exists()) {
          httpStatus.accept(SC_NOT_FOUND);
          return;
        }

        // don't serve backup files like AP511_MikeThorpe.mp4.bak-2025-06-23-18-00-10.mp4
        if (backupFile.matcher(f.getName()).matches()) {
          context.servletLog("Access to backup file blocked: " + f.getName());
          httpStatus.accept(SC_FORBIDDEN);
        }
        
        if (f.getParentFile().equals(filesDir)) { // it's in .../files
          // might be a processWithPraat results temp file
          if (f.getName().matches(".*-__-.*-__\\..*")) {
            // the file name includes -__-xxx-__\\. which we strip out for the download name
            // this temp file name ugliness e.g. long strings of digits
            fileName.accept(IO.SafeFileNameUrl(
                              f.getName().replaceAll("-__-.*-__\\.", ".")));
          }
          // or a zip file of results
          if (f.getName().endsWith(".zip")) {
            contentType.accept("application/zip");
            fileName.accept(f.getName());
          }
        } else { // ensure the content-type and attachment name are set
          contentType.accept(media.getMimeType());
          fileName.accept(f.getName());
        }        
        
        forward.accept("default");
      } finally {
        cacheStore(store);
      }
    } catch (GraphNotFoundException x) {          
      httpStatus.accept(SC_NOT_FOUND);
    } catch (PermissionException x) { // transcript/media they don't have access to
      httpStatus.accept(SC_FORBIDDEN);
    } catch (StoreException x) {
      httpStatus.accept(SC_BAD_REQUEST);
    } catch (MediaException x) {
      context.servletLog("Conversion error: " + x.getMessage());
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
    } catch (SQLException x) {
      context.servletLog("Cannot connect to database: " + x.getMessage());
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
    } catch (IOException x) {
      context.servletLog("Communications error: " + x.getMessage());
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
    }
  }
  
  /**
   * Converts from the given file to the given file.
   * @param ffmpegDir Directory where ffmpeg is installed.
   * @param from The the existing media file.
   * @param to The desired medi file.
   * @throws IOException
   * @throws MediaExceptionxs
   */
  public void convert(File ffmpegDir, MediaFile from, MediaFile to) throws IOException, MediaException {

    FfmpegConverter converter = new FfmpegConverter();
    ParameterSet config = new ParameterSet();
    config.addParameter(new Parameter("ffmpegPath", ffmpegDir));
    converter.configure(config);
    context.servletLog("Converting " + from.getName() + " to " + to.getName() + " ...");
    MediaThread thread = converter.start(
      from.getMimeType(), from.getFile(), to.getMimeType(), to.getFile());
    thread.getExecution().addStdoutObserver(s->context.servletLog(s));
    thread.getExecution().addStderrObserver(e->context.servletLog("strerr: "+e));

    // wait for the conversion to finish
    try {
      thread.join();
    } catch(InterruptedException exception) {
    }
    
    if (to.getFile().length() == 0) {
      context.servletLog("ERROR Conversion of " + from.getName() + " to " + to.getName()
                 + " produced an empty file, which will be deleted.");
      to.getFile().delete(); // something failed, ensure there's no 0 byte file left
    } else {
      context.servletLog(
        "Conversion of " + from.getName() + " to " + to.getName() + " complete.");
    }
    
  } // end of convert()
}
