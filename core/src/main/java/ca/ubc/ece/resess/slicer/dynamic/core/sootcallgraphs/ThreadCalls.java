package ca.ubc.ece.resess.slicer.dynamic.core.sootcallgraphs;

/*-
 * #%L
 * Soot - a J*va Optimization Framework
 * %%
 * Copyright (C) 2003 Ondrej Lhotak
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import soot.Body;
import soot.Kind;
import soot.Local;
import soot.PhaseOptions;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.DynamicInvokeExpr;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.options.CGOptions;
import soot.options.Options;
import soot.util.Chain;
import soot.util.LargeNumberedMap;
import soot.util.NumberedString;
import soot.jimple.toolkits.callgraph.VirtualCallSite;
/**
 * Models the call graph.
 *
 * @author Ondrej Lhotak
 */
public class ThreadCalls {

  protected final NumberedString sigFinalize = Scene.v().getSubSigNumberer().findOrAdd("void finalize()");
  protected final NumberedString sigInit = Scene.v().getSubSigNumberer().findOrAdd("void <init>()");
  protected final NumberedString sigStart = Scene.v().getSubSigNumberer().findOrAdd("void start()");
  protected final NumberedString sigRun = Scene.v().getSubSigNumberer().findOrAdd("void run()");
  protected final NumberedString sigExecute
      = Scene.v().getSubSigNumberer().findOrAdd("android.os.AsyncTask execute(java.lang.Object[])");
  protected final NumberedString sigExecutorExecute
      = Scene.v().getSubSigNumberer().findOrAdd("void execute(java.lang.Runnable)");
  protected final NumberedString sigHandlerPost
      = Scene.v().getSubSigNumberer().findOrAdd("boolean post(java.lang.Runnable)");
  protected final NumberedString sigHandlerPostAtFrontOfQueue
      = Scene.v().getSubSigNumberer().findOrAdd("boolean postAtFrontOfQueue(java.lang.Runnable)");
  // Method from android.app.Activity
  protected final NumberedString sigRunOnUiThread
      = Scene.v().getSubSigNumberer().findOrAdd("void runOnUiThread(java.lang.Runnable)");

  // type based reflection resolution state
  protected final NumberedString sigHandlerPostAtTime
      = Scene.v().getSubSigNumberer().findOrAdd("boolean postAtTime(java.lang.Runnable,long)");
  protected final NumberedString sigHandlerPostAtTimeWithToken
      = Scene.v().getSubSigNumberer().findOrAdd("boolean postAtTime(java.lang.Runnable,java.lang.Object,long)");
  protected final NumberedString sigHandlerPostDelayed
      = Scene.v().getSubSigNumberer().findOrAdd("boolean postDelayed(java.lang.Runnable,long)");
  protected final NumberedString sigHandlerSendEmptyMessage
      = Scene.v().getSubSigNumberer().findOrAdd("boolean sendEmptyMessage(int)");
  protected final NumberedString sigHandlerSendEmptyMessageAtTime
      = Scene.v().getSubSigNumberer().findOrAdd("boolean sendEmptyMessageAtTime(int,long)");
  protected final NumberedString sigHandlerSendEmptyMessageDelayed
      = Scene.v().getSubSigNumberer().findOrAdd("boolean sendEmptyMessageDelayed(int,long)");
  protected final NumberedString sigHandlerSendMessage
      = Scene.v().getSubSigNumberer().findOrAdd("boolean postAtTime(java.lang.Runnable,long)");
  protected final NumberedString sigHandlerSendMessageAtFrontOfQueue
      = Scene.v().getSubSigNumberer().findOrAdd("boolean sendMessageAtFrontOfQueue(android.os.Message)");
  protected final NumberedString sigHandlerSendMessageAtTime
      = Scene.v().getSubSigNumberer().findOrAdd("boolean sendMessageAtTime(android.os.Message,long)");
  protected final NumberedString sigHandlerSendMessageDelayed
      = Scene.v().getSubSigNumberer().findOrAdd("boolean sendMessageDelayed(android.os.Message,long)");
  protected final NumberedString sigHandlerHandleMessage
      = Scene.v().getSubSigNumberer().findOrAdd("void handleMessage(android.os.Message)");
  protected final NumberedString sigObjRun = Scene.v().getSubSigNumberer().findOrAdd("java.lang.Object run()");
  protected final NumberedString sigDoInBackground
      = Scene.v().getSubSigNumberer().findOrAdd("java.lang.Object doInBackground(java.lang.Object[])");
  protected final NumberedString sigForName
      = Scene.v().getSubSigNumberer().findOrAdd("java.lang.Class forName(java.lang.String)");
  protected final RefType clRunnable = RefType.v("java.lang.Runnable");
  protected final RefType clAsyncTask = RefType.v("android.os.AsyncTask");
  protected final RefType clHandler = RefType.v("android.os.Handler");
  

  // end type based reflection resolution
  protected final LargeNumberedMap<Local, List<VirtualCallSite>> receiverToSites
      = new LargeNumberedMap<Local, List<VirtualCallSite>>(Scene.v().getLocalNumberer()); // Local -> List(VirtualCallSite)
  protected final LargeNumberedMap<SootMethod, List<Local>> methodToReceivers
      = new LargeNumberedMap<SootMethod, List<Local>>(Scene.v().getMethodNumberer()); // SootMethod -> List(Local)


  protected CGOptions options;

  public ThreadCalls() {
    options = new CGOptions(PhaseOptions.v().getPhaseOptions("cg"));
    if (!options.verbose()) {
      AnalysisLogger.log(false, "[Call Graph] For information on where the call graph may be incomplete,"
          + "use the verbose option to the cg phase.");
    }
  }


  public LargeNumberedMap<SootMethod, List<Local>> getMethodToReceivers() {
    return methodToReceivers;
  }

  public LargeNumberedMap<Local, List<VirtualCallSite>> getReceiverToSites() {
    return receiverToSites;
  }


  public void process() {
    Chain<SootClass> chain = Scene.v().getApplicationClasses();
    Iterator<SootClass> iterator = chain.snapshotIterator();	
		while(iterator.hasNext()) {            
            SootClass sc = iterator.next();
			List<SootMethod> methods = sc.getMethods();
			for(SootMethod mt:methods) {
        processNewMethod(mt);
      }
    }
  }

  private void addVirtualCallSite(Stmt s, SootMethod m, Local receiver, InstanceInvokeExpr iie, NumberedString subSig,
      Kind kind) {
    List<VirtualCallSite> sites = receiverToSites.get(receiver);
    if (sites == null) {
      receiverToSites.put(receiver, sites = new ArrayList<VirtualCallSite>());
      List<Local> receivers = methodToReceivers.get(m);
      if (receivers == null) {
        methodToReceivers.put(m, receivers = new ArrayList<Local>());
      }
      receivers.add(receiver);
    }
    sites.add(new VirtualCallSite(s, m, iie, subSig, kind));
  }

  private void processNewMethod(SootMethod m) {
    if (!m.isConcrete()) {
      return;
    }
    Body b = m.retrieveActiveBody();
    findReceivers(m, b);
  }

  private void findReceivers(SootMethod m, Body b) {
    for (final Unit u : b.getUnits()) {
      final Stmt s = (Stmt) u;
      if (s.containsInvokeExpr()) {
        InvokeExpr ie = s.getInvokeExpr();

        if (ie instanceof InstanceInvokeExpr) {
          InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
          Local receiver = (Local) iie.getBase();
          NumberedString subSig = iie.getMethodRef().getSubSignature();
          if (subSig == sigStart) {
            addVirtualCallSite(s, m, receiver, iie, sigRun, Kind.THREAD);
          } else if (subSig == sigExecutorExecute || subSig == sigHandlerPost || subSig == sigHandlerPostAtFrontOfQueue
              || subSig == sigHandlerPostAtTime || subSig == sigHandlerPostAtTimeWithToken || subSig == sigHandlerPostDelayed
              || subSig == sigRunOnUiThread) {
            if (iie.getArgCount() > 0) {
              Value runnable = iie.getArg(0);
              if (runnable instanceof Local) {
                addVirtualCallSite(s, m, (Local) runnable, iie, sigRun, Kind.EXECUTOR);
              }
            }
          } else if (subSig == sigHandlerSendEmptyMessage || subSig == sigHandlerSendEmptyMessageAtTime
              || subSig == sigHandlerSendEmptyMessageDelayed || subSig == sigHandlerSendMessage
              || subSig == sigHandlerSendMessageAtFrontOfQueue || subSig == sigHandlerSendMessageAtTime
              || subSig == sigHandlerSendMessageDelayed) {
            addVirtualCallSite(s, m, receiver, iie, sigHandlerHandleMessage, Kind.HANDLER);
          } else if (subSig == sigExecute) {
            addVirtualCallSite(s, m, receiver, iie, sigDoInBackground, Kind.ASYNCTASK);
          }
        } else if (ie instanceof DynamicInvokeExpr) {
          if (options.verbose()) {
            AnalysisLogger.log(true, "" + "WARNING: InvokeDynamic to " + ie + " not resolved during call-graph construction.");
          }
        } else {
          SootMethod tgt = ie.getMethod();
          if (tgt != null) {
            String signature = tgt.getSignature();
            if (signature
                .equals("<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedAction)>")
                || signature.equals("<java.security.AccessController: java.lang.Object doPrivileged"
                    + "(java.security.PrivilegedExceptionAction)>")
                || signature.equals("<java.security.AccessController: java.lang.Object doPrivileged"
                    + "(java.security.PrivilegedAction,java.security.AccessControlContext)>")
                || signature.equals("<java.security.AccessController: java.lang.Object doPrivileged"
                    + "(java.security.PrivilegedExceptionAction,java.security.AccessControlContext)>")) {

              Local receiver = (Local) ie.getArg(0);
              addVirtualCallSite(s, m, receiver, null, sigObjRun, Kind.PRIVILEGED);
            }
          } else {
            if (!Options.v().ignore_resolution_errors()) {
              throw new InternalError(
                  "Unresolved target " + ie.getMethod() + ". Resolution error should have occured earlier.");
            }
          }
        }
      }
    }
  }
}


 