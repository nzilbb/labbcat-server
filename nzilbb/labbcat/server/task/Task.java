//
// Copyright 2021 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.util.Date;
import nzilbb.util.MonitorableTask;
import nzilbb.labbcat.server.db.SqlGraphStore;

/**
 * Base class for all long-running server-side tasks.
 */
public class Task extends Thread implements MonitorableTask {

  protected static final ThreadGroup taskThreadGroup = new ThreadGroup("Tasks");
  /** Access the Task thread group */
  public static ThreadGroup getTaskThreadGroup() { return taskThreadGroup; }
  
  /** Determines how far through the task is is. */
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
   * Sets the thread status, and adds the satus to the task's log.
   * @param sMessage a status message to display to anyone who'swatching the thread.
   */
  public void setStatus(String sMessage) {
    sStatus = sMessage;
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
   * @param sNewResultUrl Full URL to the final results of the task, or null if the task isn't finished or has no URL for final results.
   */
  public void setResultUrl(String sNewResultUrl) { sResultUrl = sNewResultUrl; }

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
  protected SqlGraphStore store;
  /**
   * Getter for {@link #store}: Graph store.
   * @return Graph store.
   */
  public SqlGraphStore getStore() { return store; }
  /**
   * Setter for {@link #store}: Graph store.
   * @param newStore Graph store.
   */
  public Task setStore(SqlGraphStore newStore) { store = newStore; return this; }  
   
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
   * Allows the finished-and-waiting thread to exit.
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
  public static Task findTask(String sName)
  {
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
   * @param lThreadId the thread's ID
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
  protected void runStart()
  {
    // cancel any other threads running that have the same name
    Task thread = null;
    int iThreadCount = taskThreadGroup.activeCount();
    Thread[] threads = new Thread[iThreadCount];
    taskThreadGroup.enumerate(threads);
    for (Thread otherThread : threads)
    {
      if (otherThread == null) continue;
      if (otherThread.getName() != null
          && otherThread.getName().equals(getName())
          && otherThread != this
          && ((Task)otherThread).getRunning())
      {
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
  public void waitToDie(long lDelay)
  {
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
}
