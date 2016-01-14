/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.stratos.autoscaler.context.cluster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.context.InstanceContext;
import org.apache.stratos.autoscaler.context.partition.ClusterLevelPartitionContext;
import org.apache.stratos.autoscaler.pojo.policy.autoscale.LoadAverage;
import org.apache.stratos.autoscaler.pojo.policy.autoscale.MemoryConsumption;
import org.apache.stratos.autoscaler.pojo.policy.autoscale.RequestsInFlight;
import org.apache.stratos.autoscaler.rule.AutoscalerRuleEvaluator;
import org.apache.stratos.common.constants.StratosConstants;
import org.apache.stratos.messaging.domain.topology.Member;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.rule.FactHandle;

import java.util.*;

/*
 * It holds the runtime data of a VM cluster
 */
public class ClusterInstanceContext extends InstanceContext {
    private static final Log log = LogFactory.getLog(ClusterInstanceContext.class);
    //partition algorithm
    private final String partitionAlgorithm;
    // Map<PartitionId, Partition Context>
    protected Map<String, ClusterLevelPartitionContext> partitionCtxts;
    //boolean values to keep whether the requests in flight parameters are reset or not
    private boolean rifReset, averageRifReset, gradientRifReset, secondDerivativeRifRest,predictedRifReset;
    //boolean values to keep whether the memory consumption parameters are reset or not
    private boolean memoryConsumptionReset, averageMemoryConsumptionReset,
            gradientMemoryConsumptionReset, secondDerivativeMemoryConsumptionRest,predictedMemoryConsumptionReset;
    //boolean values to keep whether the load average parameters are reset or not
    private boolean loadAverageReset, averageLoadAverageReset, gradientLoadAverageReset,
            secondDerivativeLoadAverageRest,predictedLoadAverageReset;
    //boolean values to keep whether average requests served per instance parameters are reset or not
    private boolean averageRequestServedPerInstanceReset;
    //Following information will keep events details
    private RequestsInFlight requestsInFlight;
    private MemoryConsumption memoryConsumption;
    private LoadAverage loadAverage;
    private int scaleDownRequestsCount = 0;
    private float averageRequestsServedPerInstance;
    private float requestsServedPerInstance;
    private int minInstanceCount = 0, maxInstanceCount = 0;
    private int requiredInstanceCountBasedOnStats;
    private int requiredInstanceCountBasedOnDependencies;
    //details required for partition selection algorithms
    private int currentPartitionIndex;

    private String networkPartitionId;
    private String clusterId;

    private boolean hasScalingDependants;
    private boolean groupScalingEnabledSubtree;
    private StatefulKnowledgeSession minCheckKnowledgeSession;
    private StatefulKnowledgeSession maxCheckKnowledgeSession;
    private StatefulKnowledgeSession obsoleteCheckKnowledgeSession;
    private StatefulKnowledgeSession scaleCheckKnowledgeSession;
    private StatefulKnowledgeSession dependentScaleCheckKnowledgeSession;
    private AutoscalerRuleEvaluator autoscalerRuleEvaluator;
    private FactHandle minCheckFactHandle;
    private FactHandle maxCheckFactHandle;
    private FactHandle obsoleteCheckFactHandle;
    private FactHandle scaleCheckFactHandle;
    private FactHandle dependentScaleCheckFactHandle;

    public ClusterInstanceContext(String clusterInstanceId, String partitionAlgo,
                                  int min, int max, String networkPartitionId, String clusterId,
                                  boolean hasScalingDependants, boolean groupScalingEnabledSubtree) {

        super(clusterInstanceId);
        this.networkPartitionId = networkPartitionId;
        this.clusterId = clusterId;
        this.minInstanceCount = min;
        this.maxInstanceCount = max;
        this.partitionAlgorithm = partitionAlgo;
        partitionCtxts = new HashMap<String, ClusterLevelPartitionContext>();
        requestsInFlight = new RequestsInFlight();
        loadAverage = new LoadAverage();
        memoryConsumption = new MemoryConsumption();
        requiredInstanceCountBasedOnStats = minInstanceCount;
        requiredInstanceCountBasedOnDependencies = minInstanceCount;
        this.hasScalingDependants = hasScalingDependants;
        this.groupScalingEnabledSubtree = groupScalingEnabledSubtree;

        autoscalerRuleEvaluator = AutoscalerRuleEvaluator.getInstance();

        this.obsoleteCheckKnowledgeSession = autoscalerRuleEvaluator.getStatefulSession(
                StratosConstants.OBSOLETE_CHECK_DROOL_FILE);
        this.scaleCheckKnowledgeSession = autoscalerRuleEvaluator.getStatefulSession(
                StratosConstants.SCALE_CHECK_DROOL_FILE);
        this.minCheckKnowledgeSession = autoscalerRuleEvaluator.getStatefulSession(
                StratosConstants.MIN_CHECK_DROOL_FILE);
        this.maxCheckKnowledgeSession = autoscalerRuleEvaluator.getStatefulSession(
                StratosConstants.MAX_CHECK_DROOL_FILE);
        this.dependentScaleCheckKnowledgeSession = autoscalerRuleEvaluator.getStatefulSession(
                StratosConstants.DEPENDENT_SCALE_CHECK_DROOL_FILE);
    }

    public List<ClusterLevelPartitionContext> getPartitionCtxts() {
        return new ArrayList<ClusterLevelPartitionContext>(partitionCtxts.values());
    }

    public void setPartitionCtxt(Map<String, ClusterLevelPartitionContext> partitionCtxt) {
        this.partitionCtxts = partitionCtxt;
    }

//    public ClusterLevelPartitionContext getNetworkPartitionCtxt(String PartitionId) {
//        return partitionCtxts.get(PartitionId);
//    }

    public ClusterLevelPartitionContext[] getPartitionCtxtsAsAnArray() {
        return partitionCtxts.values().toArray(new ClusterLevelPartitionContext[partitionCtxts.size()]);
    }

    public boolean partitionCtxtAvailable(String partitionId) {
        return partitionCtxts.containsKey(partitionId);
    }

    public void addPartitionCtxt(ClusterLevelPartitionContext ctxt) {
        this.partitionCtxts.put(ctxt.getPartitionId(), ctxt);
    }

    public void removePartitionCtxt(String partitionId) {
        if (partitionCtxts.containsKey(partitionId)) {
            partitionCtxts.remove(partitionId);
        }
    }

    public ClusterLevelPartitionContext getPartitionCtxt(String id) {
        return partitionCtxts.get(id);
    }

    public ClusterLevelPartitionContext getPartitionCtxt(Member member) {
        log.info("Getting [Partition] " + member.getPartitionId());
        String partitionId = member.getPartitionId();

        return partitionCtxts.get(partitionId);
    }

    public int getActiveMemberCount() {

        int activeMemberCount = 0;
        for (ClusterLevelPartitionContext partitionContext : partitionCtxts.values()) {

            activeMemberCount += partitionContext.getActiveMemberCount();
        }
        return activeMemberCount;
    }

    public int getPendingMemberCount() {

        int activeMemberCount = 0;
        for (ClusterLevelPartitionContext partitionContext : partitionCtxts.values()) {

            activeMemberCount += partitionContext.getPendingMembers().size();
        }
        return activeMemberCount;
    }

    public int getNonTerminatedMemberCount() {

        int nonTerminatedMemberCount = 0;
        for (ClusterLevelPartitionContext partitionContext : partitionCtxts.values()) {

            nonTerminatedMemberCount += partitionContext.getNonTerminatedMemberCount();
        }
        return nonTerminatedMemberCount;
    }

    public int getMinInstanceCount() {
        return minInstanceCount;
    }

    public void setMinInstanceCount(int minInstanceCount) {
        this.minInstanceCount = minInstanceCount;
    }

    public int getMaxInstanceCount() {
        return maxInstanceCount;
    }

    public void setMaxInstanceCount(int maxInstanceCount) {
        this.maxInstanceCount = maxInstanceCount;
    }

    @Override
    public String toString() {
        return "NetworkPartitionContext [id=" + id + "partitionAlgorithm=" + partitionAlgorithm + ", minInstanceCount=" +
                minInstanceCount + ", maxInstanceCount=" + maxInstanceCount + "]";
    }

    public int getCurrentPartitionIndex() {
        return currentPartitionIndex;
    }

    public void setCurrentPartitionIndex(int currentPartitionIndex) {
        this.currentPartitionIndex = currentPartitionIndex;
    }

    public float getAverageRequestsServedPerInstance() {
        return averageRequestsServedPerInstance;
    }

    public void setAverageRequestsServedPerInstance(float averageRequestServedPerInstance) {
        this.averageRequestsServedPerInstance = averageRequestServedPerInstance;
        averageRequestServedPerInstanceReset = true;

        if (log.isDebugEnabled()) {
            log.debug(String.format("Average Requesets Served Per Instance stats are reset, ready to do scale check " +
                    "[network partition] %s", this.id));

        }
    }

    public float getRequestsServedPerInstance() {
        return requestsServedPerInstance;
    }

    public float getAverageRequestsInFlight() {
        return requestsInFlight.getAverage();
    }

    public void setAverageRequestsInFlight(float averageRequestsInFlight) {
        requestsInFlight.setAverage(averageRequestsInFlight);
        averageRifReset = true;
        if (secondDerivativeRifRest && gradientRifReset) {
            rifReset = true;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Requests in flights stats are reset, ready to do scale check [network partition] %s"
                        , this.id));
            }
        }
    }

    public float getRequestsInFlightSecondDerivative() {
        return requestsInFlight.getSecondDerivative();
    }

    public void setRequestsInFlightSecondDerivative(float requestsInFlightSecondDerivative) {
        requestsInFlight.setSecondDerivative(requestsInFlightSecondDerivative);
        secondDerivativeRifRest = true;
        if (averageRifReset && gradientRifReset) {
            rifReset = true;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Requests in flights stats are reset, ready to do scale check [network partition] %s"
                        , this.id));
            }
        }
    }

    public float getRequestsInFlightGradient() {
        return requestsInFlight.getGradient();
    }

    public void setRequestsInFlightGradient(float requestsInFlightGradient) {
        requestsInFlight.setGradient(requestsInFlightGradient);
        gradientRifReset = true;
        if (secondDerivativeRifRest && averageRifReset) {
            rifReset = true;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Requests in flights stats are reset, ready to do scale check [network partition] %s"
                        , this.id));
            }
        }
    }

    public boolean isRifReset() {
        return rifReset;
    }

    public void setRifReset(boolean rifReset) {
        this.rifReset = rifReset;
        this.averageRifReset = rifReset;
        this.gradientRifReset = rifReset;
        this.secondDerivativeRifRest = rifReset;
    }


    public float getAverageMemoryConsumption() {
        return memoryConsumption.getAverage();
    }

    public void setAverageMemoryConsumption(float averageMemoryConsumption) {
        memoryConsumption.setAverage(averageMemoryConsumption);
        averageMemoryConsumptionReset = true;
        if (secondDerivativeMemoryConsumptionRest && gradientMemoryConsumptionReset) {
            memoryConsumptionReset = true;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Memory consumption stats are reset, ready to do scale check [network partition] %s"
                        , this.id));
            }
        }
    }

    public void setPredictedMemoryConsumption(double[] predictions) {

        log.info("\n\n+++++ ClusterInstanceContext.setPredictedMemoryConsumption+++++\n" + predictions + "\n\n");

        memoryConsumption.setPredictions(predictions);
        predictedMemoryConsumptionReset = true;
        //Todo : add rest of the logic of handling synchronization between Other memory updates
    }

    public double[] getMemoryConsumptionPredictedArray(){
        return memoryConsumption.getPredictions();
    }

    public ArrayList<Double> convertArrayToArrayList(double[] predictionArray){
        ArrayList<Double> predictionArrayList = new ArrayList<>();
        for (int i=0; i< predictionArray.length;i++)
            predictionArrayList.add(predictionArray[i]);
        return predictionArrayList;
    }

    public void setPredictedLoadAverage(double[] predictions) {

        log.info("\n\n+++++ ClusterInstanceContext.setPredctedLoadAverage+++++\n"+predictions+"\n\n");

        loadAverage.setPredictions(predictions);
        predictedLoadAverageReset = true;
        //Todo : add rest of the logic of handling synchronization between Other memory updates
    }

    public double[] getLoadAveragePredictedArray(){
        return loadAverage.getPredictions();
    }


    public void setPredictedRequestInFlight(double[] predictions) {

        log.info("\n\n+++++ ClusterInstanceContext.hsetPredctedRequest Inflight+++++\n"+predictions+"\n\n");

        requestsInFlight.setPredictions(predictions);
        predictedRifReset = true;
        //Todo : add rest of the logic of handling synchronization between Other memory updates
    }

    public double[] getRequestInFlightPredictedArray(){
        return requestsInFlight.getPredictions();
    }



    public float getMemoryConsumptionSecondDerivative() {
        return memoryConsumption.getSecondDerivative();
    }

    public void setMemoryConsumptionSecondDerivative(float memoryConsumptionSecondDerivative) {
        memoryConsumption.setSecondDerivative(memoryConsumptionSecondDerivative);
        secondDerivativeMemoryConsumptionRest = true;
        if (averageMemoryConsumptionReset && gradientMemoryConsumptionReset) {
            memoryConsumptionReset = true;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Memory consumption stats are reset, ready to do scale check [network partition] %s"
                        , this.id));
            }
        }
    }

    public float getMemoryConsumptionGradient() {
        return memoryConsumption.getGradient();
    }

    public void setMemoryConsumptionGradient(float memoryConsumptionGradient) {
        memoryConsumption.setGradient(memoryConsumptionGradient);
        gradientMemoryConsumptionReset = true;
        if (secondDerivativeMemoryConsumptionRest && averageMemoryConsumptionReset) {
            memoryConsumptionReset = true;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Memory consumption stats are reset, ready to do scale check [network partition] %s"
                        , this.id));
            }
        }
    }

    public boolean isMemoryConsumptionReset() {
        return memoryConsumptionReset;
    }

    public void setMemoryConsumptionReset(boolean memoryConsumptionReset) {
        this.memoryConsumptionReset = memoryConsumptionReset;
        this.averageMemoryConsumptionReset = memoryConsumptionReset;
        this.gradientMemoryConsumptionReset = memoryConsumptionReset;
        this.secondDerivativeMemoryConsumptionRest = memoryConsumptionReset;
    }


    public float getAverageLoadAverage() {
        return loadAverage.getAverage();
    }

    public void setAverageLoadAverage(float averageLoadAverage) {
        loadAverage.setAverage(averageLoadAverage);
        averageLoadAverageReset = true;
        if (secondDerivativeLoadAverageRest && gradientLoadAverageReset) {
            loadAverageReset = true;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Load average stats are reset, ready to do scale check [network partition] %s"
                        , this.id));
            }
        }
    }

    public float getLoadAverageSecondDerivative() {
        return loadAverage.getSecondDerivative();
    }

    public void setLoadAverageSecondDerivative(float loadAverageSecondDerivative) {
        loadAverage.setSecondDerivative(loadAverageSecondDerivative);
        secondDerivativeLoadAverageRest = true;
        if (averageLoadAverageReset && gradientLoadAverageReset) {
            loadAverageReset = true;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Load average stats are reset, ready to do scale check [network partition] %s"
                        , this.id));
            }
        }
    }

    public float getLoadAverageGradient() {
        return loadAverage.getGradient();
    }

    public void setLoadAverageGradient(float loadAverageGradient) {
        loadAverage.setGradient(loadAverageGradient);
        gradientLoadAverageReset = true;
        if (secondDerivativeLoadAverageRest && averageLoadAverageReset) {
            loadAverageReset = true;
            if (log.isDebugEnabled()) {
                log.debug(String.format("Load average stats are reset, ready to do scale check [network partition] %s"
                        , this.id));
            }
        }
    }

    public boolean isLoadAverageReset() {
        return loadAverageReset;
    }

    public void setLoadAverageReset(boolean loadAverageReset) {
        this.loadAverageReset = loadAverageReset;
        this.averageLoadAverageReset = loadAverageReset;
        this.gradientLoadAverageReset = loadAverageReset;
        this.secondDerivativeLoadAverageRest = loadAverageReset;
    }

   /* public Map<String, ClusterLevelPartitionContext> getPartitionCtxts() {
        return partitionCtxts;
    }

    public ClusterLevelPartitionContext getPartitionCtxt(String partitionId) {
        return partitionCtxts.get(partitionId);
    }

    public void addPartitionContext(ClusterLevelPartitionContext partitionContext) {
        partitionCtxts.put(partitionContext.getPartitionId(), partitionContext);
    }*/

    public String getPartitionAlgorithm() {
        return partitionAlgorithm;
    }

    /*public int getNonTerminatedMemberCountOfPartition(String partitionId) {
        if (partitionCtxts.containsKey(partitionId)) {
            return getPartitionCtxt(partitionId).getNonTerminatedMemberCount();
        }
        return 0;
    }

    public int getActiveMemberCount(String currentPartitionId) {
        if (partitionCtxts.containsKey(currentPartitionId)) {
            return getPartitionCtxt(currentPartitionId).getActiveMemberCount();
        }
        return 0;
    }
*/
    public int getScaleDownRequestsCount() {
        return scaleDownRequestsCount;
    }

    public void resetScaleDownRequestsCount() {
        this.scaleDownRequestsCount = 0;
    }

    public void increaseScaleDownRequestsCount() {
        this.scaleDownRequestsCount += 1;
    }

    public float getRequiredInstanceCountBasedOnStats() {
        return requiredInstanceCountBasedOnStats;
    }

    public void setRequiredInstanceCountBasedOnStats(int requiredInstanceCountBasedOnStats) {
        this.requiredInstanceCountBasedOnStats = requiredInstanceCountBasedOnStats;
    }

    public int getRequiredInstanceCountBasedOnDependencies() {
        return requiredInstanceCountBasedOnDependencies;
    }

    public void setRequiredInstanceCountBasedOnDependencies(int requiredInstanceCountBasedOnDependencies) {
        this.requiredInstanceCountBasedOnDependencies = requiredInstanceCountBasedOnDependencies;
    }

    public String getNetworkPartitionId() {
        return networkPartitionId;
    }

    public int getActiveMembers() {
        int activeMembers = 0;
        for (ClusterLevelPartitionContext partitionContext : partitionCtxts.values()) {
            activeMembers += partitionContext.getActiveInstanceCount();
        }
        return activeMembers;
    }

    public boolean isAverageRequestServedPerInstanceReset() {
        return averageRequestServedPerInstanceReset;
    }

    public boolean hasScalingDependants() {
        return hasScalingDependants;
    }

    public String getClusterId() {
        return clusterId;
    }

    public boolean isInGroupScalingEnabledSubtree() {
        return groupScalingEnabledSubtree;
    }

    public StatefulKnowledgeSession getMinCheckKnowledgeSession() {
        return minCheckKnowledgeSession;
    }

    public void setMinCheckKnowledgeSession(
            StatefulKnowledgeSession minCheckKnowledgeSession) {
        this.minCheckKnowledgeSession = minCheckKnowledgeSession;
    }

    public StatefulKnowledgeSession getMaxCheckKnowledgeSession() {
        return maxCheckKnowledgeSession;
    }

    public StatefulKnowledgeSession getObsoleteCheckKnowledgeSession() {
        return obsoleteCheckKnowledgeSession;
    }

    public void setObsoleteCheckKnowledgeSession(
            StatefulKnowledgeSession obsoleteCheckKnowledgeSession) {
        this.obsoleteCheckKnowledgeSession = obsoleteCheckKnowledgeSession;
    }

    public StatefulKnowledgeSession getScaleCheckKnowledgeSession() {
        return scaleCheckKnowledgeSession;
    }

    public void setScaleCheckKnowledgeSession(
            StatefulKnowledgeSession scaleCheckKnowledgeSession) {
        this.scaleCheckKnowledgeSession = scaleCheckKnowledgeSession;
    }

    public StatefulKnowledgeSession getDependentScaleCheckKnowledgeSession() {
        return dependentScaleCheckKnowledgeSession;
    }

    public void setDependentScaleCheckKnowledgeSession(StatefulKnowledgeSession dependentScaleCheckKnowledgeSession) {
        this.dependentScaleCheckKnowledgeSession = dependentScaleCheckKnowledgeSession;
    }

    public FactHandle getMinCheckFactHandle() {
        return minCheckFactHandle;
    }

    public void setMinCheckFactHandle(FactHandle minCheckFactHandle) {
        this.minCheckFactHandle = minCheckFactHandle;
    }

    public FactHandle getObsoleteCheckFactHandle() {
        return obsoleteCheckFactHandle;
    }

    public void setObsoleteCheckFactHandle(FactHandle obsoleteCheckFactHandle) {
        this.obsoleteCheckFactHandle = obsoleteCheckFactHandle;
    }

    public FactHandle getScaleCheckFactHandle() {
        return scaleCheckFactHandle;
    }

    public void setScaleCheckFactHandle(FactHandle scaleCheckFactHandle) {
        this.scaleCheckFactHandle = scaleCheckFactHandle;
    }

    public FactHandle getMaxCheckFactHandle() {
        return maxCheckFactHandle;
    }

    public void setMaxCheckFactHandle(FactHandle maxCheckFactHandle) {
        this.maxCheckFactHandle = maxCheckFactHandle;
    }

    public FactHandle getDependentScaleCheckFactHandle() {
        return dependentScaleCheckFactHandle;
    }

    public void setDependentScaleCheckFactHandle(FactHandle dependentScaleCheckFactHandle) {
        this.dependentScaleCheckFactHandle = dependentScaleCheckFactHandle;
    }
}
