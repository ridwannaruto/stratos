/*
 *     Licensed to the Apache Software Foundation (ASF) under one
 *     or more contributor license agreements.  See the NOTICE file
 *     distributed with this work for additional information
 *     regarding copyright ownership.  The ASF licenses this file
 *     to you under the Apache License, Version 2.0 (the
 *     "License"); you may not use this file except in compliance
 *     with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing,
 *     software distributed under the License is distributed on an
 *     "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *     KIND, either express or implied.  See the License for the
 *     specific language governing permissions and limitations
 *     under the License.
 */
package org.apache.stratos.cep.extension;

import org.apache.log4j.Logger;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngine;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.wso2.siddhi.core.config.SiddhiContext;
import org.wso2.siddhi.core.event.StreamEvent;
import org.wso2.siddhi.core.event.in.InEvent;
import org.wso2.siddhi.core.event.in.InListEvent;
import org.wso2.siddhi.core.event.remove.RemoveEvent;
import org.wso2.siddhi.core.event.remove.RemoveListEvent;
import org.wso2.siddhi.core.query.QueryPostProcessingElement;
import org.wso2.siddhi.core.query.processor.window.RunnableWindowProcessor;
import org.wso2.siddhi.core.query.processor.window.WindowProcessor;
import org.wso2.siddhi.core.snapshot.ThreadBarrier;
import org.wso2.siddhi.core.util.collection.queue.scheduler.ISchedulerSiddhiQueue;
import org.wso2.siddhi.core.util.collection.queue.scheduler.SchedulerSiddhiQueue;
import org.wso2.siddhi.core.util.collection.queue.scheduler.SchedulerSiddhiQueueGrid;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.expression.Expression;
import org.wso2.siddhi.query.api.expression.Variable;
import org.wso2.siddhi.query.api.expression.constant.IntConstant;
import org.wso2.siddhi.query.api.expression.constant.LongConstant;
import org.wso2.siddhi.query.api.extension.annotation.SiddhiExtension;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@SiddhiExtension(namespace = "stratos", function = "workloadPrediction")
public class PredictorWindowProcessor extends WindowProcessor implements RunnableWindowProcessor {

    static final Logger log = Logger.getLogger(PredictorWindowProcessor.class);

    private ScheduledExecutorService eventRemoverScheduler;
    private long timeToKeep;
    private ScheduledFuture<?> lastSchedule = null;
    private ThreadBarrier threadBarrier;
    private int subjectAttrIndex;
    private Attribute.Type subjectAttrType;
    private int outputIndex;
    private List<InEvent> newEventList;
    private List<RemoveEvent> oldEventList;
    private ISchedulerSiddhiQueue<StreamEvent> window;
    private Queue<Double> globalData=new LinkedList<>();
    private int MAX_TRAIN_SET_SIZE=1000;


    private String elementID;
    private String streamId;
    private String type;
    private long hash;
    private String context="";


    private ISchedulerSiddhiQueue<StreamEvent> globalWindow;
    private long[] timeStamps;
    private double[] dataValues;

    private RConnection rEngine;

    @Override
    protected void processEvent(InEvent event) {
        acquireLock();
        try{

            if(newEventList.isEmpty() || !checkEqual(newEventList.get(newEventList.size() - 1),event)) {
                    newEventList.add(event);

                 if(type==null || type.equals(""))
                 {
                    type=(String)event.getData(4);
                    context=context+" \n\t eventType:"+type;
                 }
                String localContext=context+"\n\tThread:"+Thread.currentThread().getId()+"\\n\\t method:processEvent()\n\n";
                if(type.equals("memory_consumption")||type.equals("load_average"))
                    log.info("+++++ Received event:"+event.getStreamId()+" :"+ event.getData(5)+context);
                else
                log.info("+++++ Received event:"+event.getStreamId()+" :"+ event.getData(3)+context);
            }
            } finally {
            releaseLock();
        }
    }


    protected boolean checkEqual(InEvent e1,InEvent e2){

        for(int i=0;i<e1.getData().length;i++)
        {

            if(!e1.getData(i).equals(e2.getData(i)))
                return false;
        }
        return true;
    }

    @Override
    protected void processEvent(InListEvent listEvent) {
        acquireLock();
        try {
            System.out.println(listEvent);
            for (int i = 0, size = listEvent.getActiveEvents(); i < size; i++) {
                newEventList.add((InEvent) listEvent.getEvent(i));
            }
        } finally {
            releaseLock();
        }
    }

    @Override
    public Iterator<StreamEvent> iterator() {
        return window.iterator();
    }

    @Override
    public Iterator<StreamEvent> iterator(String predicate) {
        if (siddhiContext.isDistributedProcessingEnabled()) {
            return ((SchedulerSiddhiQueueGrid<StreamEvent>) window).iterator(predicate);
        } else {
            return window.iterator();
        }
    }


    @Override
    public void run() {
        String localContext=context+"\n\tThread:"+Thread.currentThread().getId()+"\\n\\t method:processEvent()\n\n";

        acquireLock();
        try {
            long scheduledTime = System.currentTimeMillis();
            try {
                oldEventList.clear();
                while (true) {
                    threadBarrier.pass();
                    RemoveEvent removeEvent = (RemoveEvent) window.poll();
                    if (removeEvent == null) {
                        if (oldEventList.size() > 0) {
                            nextProcessor.process(new RemoveListEvent(
                                    oldEventList.toArray(new RemoveEvent[oldEventList.size()])));
                            oldEventList.clear();
                        }

                        if (newEventList.size() > 0) {
                            InEvent[] inEvents = newEventList.toArray(new InEvent[newEventList.size()]);
                            for (InEvent inEvent : inEvents) {
                                window.put(new RemoveEvent(inEvent, -1));
                            }

                           double dataSet[] = extractDataset();
                             InEvent[] predictions = getPredictions(dataSet);
//                            InEvent[] predictions =gradient(inEvents[0],inEvents[newEventList.size() - 1]);
                            for (InEvent inEvent : predictions) {
                                window.put(new RemoveEvent(inEvent, -1));
                            }

                            nextProcessor.process(new InListEvent(predictions));
                            newEventList.clear();
                        }

                        long diff = timeToKeep - (System.currentTimeMillis() - scheduledTime);
                        if (diff > 0) {
                            try {
                                if (lastSchedule != null) {
                                    lastSchedule.cancel(false);
                                }
                                lastSchedule = eventRemoverScheduler.schedule(this, diff, TimeUnit.MILLISECONDS);
                            } catch (RejectedExecutionException ex) {
                                log.warn("scheduling cannot be accepted for execution: elementID " +
                                        elementId);
                            }
                            break;
                        }
                        scheduledTime = System.currentTimeMillis();
                    } else {
                        oldEventList.add(new RemoveEvent(removeEvent, System.currentTimeMillis()));
                    }
                }
            } catch (Throwable t) {
                log.error(t.getMessage(), t);
            }
        } finally {
            releaseLock();
        }
    }



    public double[] extractDataset(){

        String localContext=context+"\n\tThread:"+Thread.currentThread().getId()+"\\n\\t method:extractDataset\n\n";

        log.info("++++ global list"+context+" \n "+globalData.toString());
        globalData.add(getAverage());
        log.info("++++ global list"+context+" \n "+globalData.toString());

        if(globalData.size()>MAX_TRAIN_SET_SIZE) {
            globalData.poll();
        }

        double[] dataSet=new double[globalData.size()];

        Iterator<Double> iterator=globalData.iterator();
        int counter=0;
        while(iterator.hasNext()){
            dataSet[counter++]=iterator.next();
        }
        return  dataSet;
    }
    public double getAverage(){

        String localContext=context+"\n\tThread:"+Thread.currentThread().getId()+"\\n\\t method:average()\n\n";
        collectLastWindow();
        int i=0;
        double sum=0;

        log.info("GET AVERAGE"+Arrays.toString(dataValues)+localContext);
        for( i=0;i<dataValues.length;i++)
        {
                sum+=dataValues[i];
        }

        if(i==0)
        return  0.0;

        return sum/i;
    }

    public InEvent[] equalEventsHandler(double datavalues[])
    {


        boolean flag = false;
        for(int i=0;i<datavalues.length-1;i++)
        {
            if(datavalues[i]!=datavalues[i+1])
            {    flag=true;
                break;
            }
        }

        if(!flag)
        {
            Object[] data3 = newEventList.get(0).getData().clone();
            InEvent[] inEvents3 = new InEvent[1];
            double rvalue=dataValues[dataValues.length-1];
            String s=dataValues[dataValues.length-1]+"";
            for(int i=1;i<15;i++) {
                s+=",";
                s+=rvalue;
            }
            data3[outputIndex]=s;
            inEvents3[0] = new InEvent(newEventList.get(0).getStreamId(), newEventList.get(0).getTimeStamp(), data3);
            return inEvents3;
        }
        return null;
    }


    public synchronized InEvent[]  getPredictions(double [] dataValues) {
        String localContext=context+"\n\tThread:"+Thread.currentThread().getId()+"\\n\\t method:getPredictions()\n\n";
        InEvent[] e1= equalEventsHandler(dataValues);
      if(e1!=null) {
          log.info("+++ EQUAL or 1'st"+Arrays.toString(dataValues));

          return e1;
      }
          try {
            long id=Thread.currentThread().getId();
            long startTime=System.currentTimeMillis();
            rEngine.assign("data" + id, dataValues);
            log.info("ID:"+id+ "array is assigned to R:\n"+dataValues+localContext);
            double array1[] = rEngine.parseAndEval("data"+id).asDoubles();
//            log.info("ID:"+id+"Array is read back successfully");
//            log.info("ID:" + id +" " + Arrays.toString(array1));

            double results[] = rEngine.parseAndEval("prediction(ts(data"+id+"),15);").asDoubles();
            log.info("ID:"+id+" predictions are generated!!!"+Arrays.toString(results)+localContext);
            long endTime = System.currentTimeMillis();
//
//            log.info("ID:" + id + " time:" + (endTime - startTime) + " " + Arrays.toString(results));
            Object[] data = newEventList.get(0).getData().clone();

            StringBuffer stringBuffer =new StringBuffer();
            for (int i = 0; i < results.length; i++) {
               stringBuffer.append(results[i]);
               if(i!=results.length-1)
                    stringBuffer.append(",");
            }


            data[outputIndex] = stringBuffer.toString();
            InEvent[] inEvents = new InEvent[1];
            inEvents[0] = new InEvent(newEventList.get(0).getStreamId(), newEventList.get(0).getTimeStamp(), data);
            return inEvents;
        } catch (Exception e) {
            log.error("++++EXCEPTION RECOVERD +++" + localContext);
            Object[] data2 = newEventList.get(0).getData().clone();
            InEvent[] inEvents2 = new InEvent[1];
            double rvalue=dataValues[dataValues.length-1];
            String s=dataValues[dataValues.length-1]+"";
            for(int i=1;i<15;i++)
            {
                s+=",";
                s+=rvalue;
            }

            data2[outputIndex]=s;
            inEvents2[0] = new InEvent(newEventList.get(0).getStreamId(), newEventList.get(0).getTimeStamp(), data2);

            return inEvents2;
        }

    }


    private synchronized  void collectLastWindow() {
        long id = Thread.currentThread().getId();
        Attribute.Type attrType = subjectAttrType;
        timeStamps = new long[newEventList.size()];
        dataValues = new double[newEventList.size()];
        int indexOfEvent = 0;
        for (indexOfEvent = 0; indexOfEvent < newEventList.size(); indexOfEvent++) {
            InEvent eventToPredict = newEventList.get(indexOfEvent);
            timeStamps[indexOfEvent] = eventToPredict.getTimeStamp();
            if (Attribute.Type.DOUBLE.equals(attrType)) {
                dataValues[indexOfEvent] = (Double) eventToPredict.getData()[subjectAttrIndex];
            } else if (Attribute.Type.INT.equals(attrType)) {
                dataValues[indexOfEvent] = (Integer) eventToPredict.getData()[subjectAttrIndex];
            } else if (Attribute.Type.LONG.equals(attrType)) {
                dataValues[indexOfEvent] = (Long) eventToPredict.getData()[subjectAttrIndex];
            } else if (Attribute.Type.FLOAT.equals(attrType)) {
                dataValues[indexOfEvent] = (Float) eventToPredict.getData()[subjectAttrIndex];
            }
        }
        if(timeStamps.length == 0){
            timeStamps = new long[1];
            dataValues = new double[1];
            timeStamps[0] = System.currentTimeMillis();
            dataValues[0] = 40;
        }

    }

    @Override
    protected Object[] currentState() {
        return new Object[]{window.currentState(), oldEventList, newEventList};
    }

    @Override
    protected void restoreState(Object[] data) {
        window.restoreState(data);
        window.restoreState((Object[]) data[0]);
        oldEventList = ((ArrayList<RemoveEvent>) data[1]);
        newEventList = ((ArrayList<InEvent>) data[2]);
        window.reSchedule();
    }



    @Override
    protected void init(Expression[] parameters, QueryPostProcessingElement nextProcessor, AbstractDefinition streamDefinition, String elementId, boolean async, SiddhiContext siddhiContext) {
        this.elementID=elementId;
        this.streamId=streamDefinition.getId();
        this.hash=System.identityHashCode(this);
        this.context="\n\tElementID:"+elementID+"\n\tStreamId:"+streamId+"\n\twindowProcessor:"+hash;

        String localContext=context+"\n\tThread:"+Thread.currentThread().getId()+"\\n\\t method:intit()\n\n";

        log.info("\n!!!! Predictor Finder  window Processor created !!!!" + localContext);
        if (parameters[0] instanceof IntConstant) {
            timeToKeep = ((IntConstant) parameters[0]).getValue();
        } else {
            timeToKeep = ((LongConstant) parameters[0]).getValue();
        }

        try {
            rEngine = JRIConnection.getRserverConnection();
            log.info("ID:"+ Thread.currentThread().getId()+"\nrEngine connection Established"+localContext);
        } catch (IOException e) {
            e.printStackTrace();
            e.printStackTrace();
        } catch (REngineException e) {
            e.printStackTrace();
        } catch (REXPMismatchException e) {
            e.printStackTrace();
        }



        String subjectedAttr = ((Variable) parameters[1]).getAttributeName();
        subjectAttrIndex = streamDefinition.getAttributePosition(subjectedAttr);
        subjectAttrType = streamDefinition.getAttributeType(subjectedAttr);

        subjectedAttr = ((Variable) parameters[2]).getAttributeName();
        outputIndex = streamDefinition.getAttributePosition(subjectedAttr);

        oldEventList = new ArrayList<RemoveEvent>();
        if (this.siddhiContext.isDistributedProcessingEnabled()) {
            newEventList = this.siddhiContext.getHazelcastInstance().getList(elementId + "-newEventList");
        } else {
            newEventList = new ArrayList<InEvent>();
        }

        if (this.siddhiContext.isDistributedProcessingEnabled()) {
            window = new SchedulerSiddhiQueueGrid<StreamEvent>(elementId, this, this.siddhiContext, this.async);
            globalWindow=new SchedulerSiddhiQueueGrid<StreamEvent>(elementId, this, this.siddhiContext, this.async);
        } else {
            window = new SchedulerSiddhiQueue<StreamEvent>(this);
            globalWindow = new SchedulerSiddhiQueue<StreamEvent>(this);
        }
        //Ordinary scheduling
        window.schedule();

    }

//    @Override
//    protected void init(Expression[] parameters, QueryPostProcessingElement nextProcessor, AbstractDefinition streamDefinition, String elementId, boolean async, SiddhiContext siddhiContext) {
//        log.info("\n\n!!!! Predictor window Processor created !!!!"+this.siddhiContext.isDistributedProcessingEnabled()+"\n\n");
//        if (parameters[0] instanceof IntConstant) {
//            timeToKeep = ((IntConstant) parameters[0]).getValue();
//        } else {
//            timeToKeep = ((LongConstant) parameters[0]).getValue();
//        }
//
//        try {
//            rEngine = JRIConnection.getConnection();
//            log.info("ElementID: "+ elementId+" streamId:"+streamDefinition.getId() +"  streamObject"+streamDefinition+"  ID:"+ Thread.currentThread().getId()+"rEngine connection Established");
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (REngineException e) {
//            e.printStackTrace();
//        } catch (REXPMismatchException e) {
//            e.printStackTrace();
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//
//
//        String subjectedAttr = ((Variable) parameters[1]).getAttributeName();
//        subjectAttrIndex = streamDefinition.getAttributePosition(subjectedAttr);
//        subjectAttrType = streamDefinition.getAttributeType(subjectedAttr);
//
//        subjectedAttr = ((Variable) parameters[2]).getAttributeName();
//        outputIndex = streamDefinition.getAttributePosition(subjectedAttr);
//
//        oldEventList = new ArrayList<RemoveEvent>();
//        if (this.siddhiContext.isDistributedProcessingEnabled()) {
//            newEventList = this.siddhiContext.getHazelcastInstance().getList(elementId + "-newEventList");
//        } else {
//            newEventList = new ArrayList<InEvent>();
//        }
//
//        if (this.siddhiContext.isDistributedProcessingEnabled()) {
//            window = new SchedulerSiddhiQueueGrid<StreamEvent>(elementId, this, this.siddhiContext, this.async);
//            globalWindow=new SchedulerSiddhiQueueGrid<StreamEvent>(elementId, this, this.siddhiContext, this.async);
//        } else {
//            window = new SchedulerSiddhiQueue<StreamEvent>(this);
//            globalWindow = new SchedulerSiddhiQueue<StreamEvent>(this);
//        }
//        //Ordinary scheduling
//        window.schedule();
//
//    }

    @Override
    public void schedule() {
        if (lastSchedule != null) {
            lastSchedule.cancel(false);
        }
        lastSchedule = eventRemoverScheduler.schedule(this, timeToKeep, TimeUnit.MILLISECONDS);
    }

    public void scheduleNow() {
        if (lastSchedule != null) {
            lastSchedule.cancel(false);
        }
    }

    @Override
    public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        this.eventRemoverScheduler = scheduledExecutorService;
    }

    public void setThreadBarrier(ThreadBarrier threadBarrier) {
        this.threadBarrier = threadBarrier;
    }

    @Override
    public void destroy() {
        oldEventList = null;
        newEventList = null;
        window = null;
        log.info("\n\n!!!! Predictor window processor deleted !!!!\n\n"+elementId+"  "+streamId+" ");

        rEngine.close();
    }

}