//
// Copyright 2021-2024 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.util.Date;
import java.util.ResourceBundle;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import nzilbb.util.MonitorableTask;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.StoreCache;

/**
 * Base class for all long-running server-side tasks.
 */
public class Task extends Thread implements MonitorableTask {

  protected static final ThreadGroup taskThreadGroup = new ThreadGroup("Tasks");
  /** Access the Task thread group */
  public static ThreadGroup getTaskThreadGroup() { return taskThreadGroup; }

  private static SimpleDateFormat logTimeFormat = new SimpleDateFormat("dd MMM HH:mm:ss");

  /** Determines how far through the task is. */
  protected int iPercentComplete = 1; // TODO rename as percentComplete
  /**
   * Determines how far through the task is is.
   * @return An integer between 0 and 100 (inclusive), or null if progress can not be calculated.
   */
  public Integer getPercentComplete() {
    return iPercentComplete;
  }
  
  /** Has {@link #cancel()} been called? */
  protected boolean bCancelling = false;
  /** Cancels the task. */
  public void cancel() {
    if (!bCancelling) {
      setStatus("Cancelling...");
      bCancelling = true;
    }
  }
  
  /** Reveals whether the task is still running or not. */
  protected boolean bRunning = true;
  /**
   * Reveals whether the task is still running or not.
   * @return true if the task is currently running, false otherwise.
   */
  public boolean getRunning() {
    return bRunning;
  }
    
  private String sStatus = "Initialising... ";
  /**
   * The current status of the thread.
   * @return a description of the current status of the task.
   */
  public String getStatus() { return sStatus; }
  /**
   * Sets the thread status.
   * @param sMessage a status message to display to anyone who's watching the thread.
   */
  public void setStatus(String sMessage) {
    sStatus = sMessage;
    try {
      log.append(logTimeFormat.format(new Date()) + "\t" + sMessage + "\n");
      // log too big?
      if (log.length() > maxLogSize) log.delete(0, (3 * maxLogSize) / 4);
    } catch(Exception exception) {
      // StringBuilder.append() can throw an ArrayIndexOutOfBoundsException
      // not mentioned in the docs
    }
  } // end of status()

  /** Username or something to identify the person who started the thread */
  protected String sWho = "";
  /**
   * Gets the user associated with the task, if any.
   * @return the username or hostname of the person who started the task, if available.
   */
  public String getWho() { return sWho; }
  /**
   * Sets the user associated with the task.
   * @param who the username or hostname of the person who started the task
   */
  public void setWho(String who) { sWho = who; }

  private Throwable lastException = null;
  /**
   * Gets the last exception that occurred during the task.
   * @return the last task exception, or null if no exception has occurred.
   */
  public Throwable getLastException() { return lastException; }
  /**
   * Sets the last exception that occurred during the task.
   * @param ex the last task exception, or null if no exception has occurred.
   */
  public void setLastException(Throwable ex) {
    lastException = ex; 
  }

  /**
   * Full URL to the final results of the task, or null if the task isn't finished or has no URL for final results.
   */
  private String sResultUrl;
  /**
   * ResultUrl accessor 
   * @return Full URL to the final results of the task, or null if the task isn't finished or has no URL for final results.
   */
  public String getResultUrl() { return sResultUrl; }
  /**
   * ResultUrl mutator
   * @param sNewResultUrl Full URL to the final results of the task, or null if the task isn't finished or has no URL for final results. Instances of "+" in the string will be replaced with "%20".
   */
  public void setResultUrl(String sNewResultUrl) {
    sResultUrl = sNewResultUrl;
    if (sResultUrl != null) {
      // use %20 instead of + for spaces
      // because the "default" servlet doesn't handle + well
      sResultUrl = sResultUrl.replace("+","%20");
    }
  }

  /**
   * Text to describe the link to the ResultUrl. This will be the contents of the
   * &lt;a&gt; tag if ResultUrl is set. 
   */
  private String sResultText;
  /**
   * ResultText accessor 
   * @return Text to describe the link to the ResultUrl. This will be the contents of the
   * &lt;a&gt; tag if ResultUrl is set. 
   */
  public String getResultText() { 
    if (sResultText != null) {
      return sResultText; 
    } else {
      return sResultUrl;
    }
  }
  /**
   * ResultText mutator
   * @param sNewResultText Text to describe the link to the ResultUrl. This will be the
   * contents of the &lt;a&gt; tag if ResultUrl is set. 
   */
  public void setResultText(String sNewResultText) { sResultText = sNewResultText; }

  /**
   * The target HTML frame/window for the results to open in.
   * @see #getResultTarget()
   * @see #setResultTarget(String)
   */
  protected String sResultTarget;
  /**
   * Getter for {@link #sResultTarget}: The target HTML frame/window for the results to open in.
   * @return The target HTML frame/window for the results to open in.
   */
  public String getResultTarget() { return sResultTarget; }
  /**
   * Setter for {@link #sResultTarget}: The target HTML frame/window for the results to open in.
   * @param sNewResultTarget The target HTML frame/window for the results to open in.
   */
  public void setResultTarget(String sNewResultTarget) { sResultTarget = sNewResultTarget; }

  /**
   * Last time keepAlive() was called.
   */
  private Date dtLastKeepAlive = new Date();
  /**
   * LastKeepAlive accessor 
   * @return Last time keepAlive() was called.
   */
  public Date getLastKeepAlive() { return dtLastKeepAlive; }
  /**
   * LastKeepAlive mutator
   * @param dtNewLastKeepAlive Last time keepAlive() was called.
   */
  public void setLastKeepAlive(Date dtNewLastKeepAlive) { dtLastKeepAlive = dtNewLastKeepAlive; }

  /**
   * Time to wait after finishing or the last keepalive, before dying. Default is 120000ms
   * (2 minutes). 
   * @see #getWaitToDieMilliseconds()
   * @see #setWaitToDieMilliseconds(long)
   */
  protected long waitToDieMilliseconds = 120000;
  /**
   * Getter for {@link #waitToDieMilliseconds}: Time to wait after finishing or the last
   * keepalive, before dying. 
   * @return Time to wait after finishing or the last keepalive, before dying.
   */
  public long getWaitToDieMilliseconds() { return waitToDieMilliseconds; }
  /**
   * Setter for {@link #waitToDieMilliseconds}: Time to wait after finishing or the last
   * keepalive, before dying. 
   * @param newWaitToDieMilliseconds Time to wait after finishing or the last keepalive,
   * before dying. 
   */
  public void setWaitToDieMilliseconds(long newWaitToDieMilliseconds) { waitToDieMilliseconds = newWaitToDieMilliseconds; }
  
  /**
   * Graph store.
   * @see #getStore()
   * @see #setStore(SqlGraphStore)
   */
  private SqlGraphStore store;
  /**
   * Getter for {@link #store}: Graph store.
   * @return Graph store.
   */
  public SqlGraphStore getStore() {
    if (store == null && storeCache != null) {
      store = storeCache.get();
    }
    return store;
  }
  /**
   * Setter for {@link #store}: Graph store.
   * @param newStore Graph store.
   */
  public Task setStore(SqlGraphStore newStore) { store = newStore; return this; }
  
  /**
   * A supplier/consumer of graph stores, so that a store can be obtained if necessary,
   * and resources can be shared/closed when appropriate. 
   * @see #getStoreCache()
   * @see #setStoreCache(StoreCache)
   */
  protected StoreCache storeCache;
  /**
   * Getter for {@link #storeCache}: A supplier/consumer of graph stores, so that a store
   * can be obtained if necessary, and resources can be shared/closed when appropriate. 
   * @return A supplier/consumer of graph stores, so that a store can be obtained if
   * necessary, and resources can be shared/closed when appropriate. 
   */
  public StoreCache getStoreCache() { return storeCache; }
  /**
   * Setter for {@link #storeCache}: A supplier/consumer of graph stores, so that a store
   * can be obtained if necessary, and resources can be shared/closed when appropriate. 
   * @param newStoreCache A supplier/consumer of graph stores, so that a store can be
   * obtained if necessary, and resources can be shared/closed when appropriate. 
   */
  public Task setStoreCache(StoreCache newStoreCache) { storeCache = newStoreCache; return this; }
   
  protected StringBuilder log = new StringBuilder(1024);
  /**
   * Provides a timestamped log of activity.
   * @return a string containing all of the values that setStatus has had since the task started.
   * @throws Exception
   */
  public String getLog() { return log.toString(); }
  
  /**
   * Maximum size of the task's log, in characters.
   * @see #getMaxLogSize()
   * @see #setMaxLogSize(int)
   */
  protected int maxLogSize = 51200;
  /**
   * Getter for {@link #maxLogSize}: Maximum size of the task's log, in characters.
   * @return Maximum size of the task's log, in characters.
   */
  public int getMaxLogSize() { return maxLogSize; }
  /**
   * Setter for {@link #maxLogSize}: Maximum size of the task's log, in characters.
   * @param newMaxLogSize Maximum size of the task's log, in characters.
   */
  public Task setMaxLogSize(int newMaxLogSize) { maxLogSize = newMaxLogSize; return this; }  
  
  /**
   * Localization resource bundle.
   * @see #getResources()
   * @see #setResources(ResourceBundle)
   */
  protected ResourceBundle resources;
  /**
   * Getter for {@link #resources}: Localization resource bundle.
   * @return Localization resource bundle.
   */
  public ResourceBundle getResources() {
    if (resources == null) resources = ResourceBundle.getBundle(
      "nzilbb.labbcat.server.locale.Resources", java.util.Locale.UK);
    return resources;
  }
  /**
   * Setter for {@link #resources}: Localization resource bundle.
   * @param newResources Localization resource bundle.
   */
  public Task setResources(ResourceBundle newResources) { resources = newResources; return this; }
  
  /**
   * Constructor
   */
  public Task() {
    super(taskThreadGroup, "");
    setName(defaultThreadName());
  } // end of constructor
  
  /**
   * A default unique name for the thread. Implementors can use or add this this as they like.
   */
  public String defaultThreadName() {
    return getClass().getSimpleName()+"-"+hashCode();
  } // end of defaultThreadName()
  
  /**
   * Release resources.
   */
  public void release() {
    // make sure it dies...
    dtLastKeepAlive = new Date(0);
    try { 
      synchronized(this) {
        this.notifyAll();
      }
    } catch (Exception ex) {
      lastException = ex;
      setStatus("release(): " + ex.getClass().getName() 
                + " - " + ex.getMessage());
    }
    
    // minimise possible vestigal memory usage TODO remove this
    try {
      System.gc();
    } catch (Throwable t) {}
  } // end of release()

  /**
   * Finds the named thread.
   * @param sName the thread's name
   * @return the named thread, or null if it can't be found.
   */
  public static Task findTask(String sName) {
    Task task = null;
    int iThreadCount = taskThreadGroup.activeCount();
    Thread[] threads = new Thread[iThreadCount];
    taskThreadGroup.enumerate(threads);
    for (int i = 0; i < iThreadCount; i++) {
      if (threads[i] != null && threads[i].getName().equals(sName)) {
        task = (Task)threads[i];
        break;
      }
    } // next thread
    return task;
  }

  /**
   * Finds the named task.
   * @param id the thread's ID
   * @return The identified thread, or null if it can't be found.
   */
  public static Task findTask(long id) {
    Task task = null;
    int iThreadCount = taskThreadGroup.activeCount();
    Thread[] threads = new Thread[iThreadCount];
    taskThreadGroup.enumerate(threads);
    for (int i = 0; i < iThreadCount; i++) {
      if (threads[i] != null && threads[i].getId() == id) {
        task = (Task)threads[i];
        break;
      }
    } // next thread
    return task;
  }

  /**
   * To be run by derived classes at the start of their 'run' method.
   */
  protected void runStart() {
    // cancel any other threads running that have the same name
    Task thread = null;
    int iThreadCount = taskThreadGroup.activeCount();
    Thread[] threads = new Thread[iThreadCount];
    taskThreadGroup.enumerate(threads);
    for (Thread otherThread : threads) {
      if (otherThread == null) continue;
      if (otherThread.getName() != null
          && otherThread.getName().equals(getName())
          && otherThread != this
          && ((Task)otherThread).getRunning()) {
        // cancel the other thread
        setStatus("Cancelling other task: " + otherThread.getId());
        ((Task)otherThread).cancel();
      }
    } // next thread

    bRunning = true;
    bCancelling = false;
    lStartTime = new java.util.Date().getTime();
  } // end of runStart()

  /**
   * To be run by derived classes at the end of their 'run' method.
   */
  protected void runEnd() {
    // set final duration
    getDuration();
    bRunning = false;

    if (storeCache != null && store != null) {
      // return the store to the cache
      storeCache.accept(store);
      store = null;
    }
    
    // minimise possible vestigal memory usage // TODO remove this
    try {
      System.gc();
    }
    catch (Throwable t) {}
  } // end of runEnd()
      
  /**
   * Somebody is still interested in the thread, so keep it from dying.
   */
  public void keepAlive() {
    dtLastKeepAlive = new Date();
  } // end of keepAlive()
      
  /**
   * Called at the end of the task's run() method, this method blocks until there is
   * nobody interested in the thread any more - i.e. there have been no recent calls to
   * keepAlive().  The default delay is 120000ms, i.e. 2 minutes; 
   */
  public void waitToDie() {
    waitToDie(waitToDieMilliseconds);
  }
  /**
   * Called at the end of the task's run() method, this method blocks until there is
   * nobody interested in the thread any more - i.e. there have been no recent calls to
   * keepAlive() 
   * @param lDelay Delay time - i.e. the minimum amount of time to wait before dying.
   */
  public void waitToDie(long lDelay) {
    if (dtLastKeepAlive.getTime() > 0) { // haven't already been released
      try { 
        synchronized(this) {
          this.wait(lDelay); // wait for up to 2 minutes
        }
        while (new Date().getTime() - lDelay < dtLastKeepAlive.getTime()) {
          synchronized(this) {
            this.wait(lDelay); // wait for up to 2 minutes
          }
        }
      } catch (Throwable t) {
      } // drops out
    }
  } // end of waitToDie()
  
  protected long lStartTime = 0;
  /**
   * The creation time of the task.
   * @return the creation time of the task
   */
  public Date getCreationTime() { return new Date(lStartTime); }
  protected long lDuration;
  /**
   * The duration of the tasks execution so far.
   * @return the number of millisecond that the thread ran for if it is finished, or the
   * amount of time the thread has been running for so far, if it is still executing. 
   */
  public long getDuration() {
    if (lStartTime == 0) {
      // haven't started yet
      return 0; 
    } else if (bRunning) {
      // still running - update the running duration
      long lEndTime = new java.util.Date().getTime();
      lDuration = lEndTime - lStartTime;
    }
    return lDuration;
  }

  /**
   * Localizes the given message to the language found in the "Accept-Language" header of
   * the given request, substituting in the given arguments if any.
   * <p> The message is assumed to be a MessageFormat template like 
   * "Row could not be added: {0}"
   * @param request The request, for discovering the locale.
   * @param message The message format to localize.
   * @param args Arguments to be substituted into the message. 
   * @return The localized message (or if the messages couldn't be localized, the
   * original message) with the given arguments substituted. 
   */
  protected String localize(String message, Object... args) {

    // determine the Locale/ResourceBundle
      
    // get the localized version of the message
    String localizedString = message;
    try {
      localizedString = getResources().getString(message);
    } catch(Throwable exception) {
      System.err.println(
        "Task: i18n: missing resource in " + resources.getLocale() + ": " + message);
    }

    // do we need to substitute in arguments?
    if (args.length > 0) {
      localizedString = new MessageFormat(localizedString).format(args);
    }
    return localizedString;
  } // end of localize()
}
