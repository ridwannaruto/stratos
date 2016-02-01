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

package org.apache.stratos.autoscaler.context.member;

import org.apache.stratos.autoscaler.pojo.policy.autoscale.LoadAverage;
import org.apache.stratos.autoscaler.pojo.policy.autoscale.MemoryConsumption;

import java.util.Arrays;

/**
 * This class will keep additional parameters such as load average and memory consumption
 */

public class MemberStatsContext {
    private LoadAverage loadAverage;
    private MemoryConsumption memoryConsumption;
    private String memberId;
    private String instanceId;

    public MemberStatsContext(String memberId) {
        this.memberId = memberId;
        memoryConsumption = new MemoryConsumption();
        loadAverage = new LoadAverage();
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public LoadAverage getLoadAverage() {
        return loadAverage;
    }

    public MemoryConsumption getMemoryConsumption() {
        return memoryConsumption;
    }

    public float getAverageLoadAverage() {
        return loadAverage.getAverage();
    }

    public void setAverageLoadAverage(float value) {
        loadAverage.setAverage(value);
    }

    public float getAverageMemoryConsumption() {
        return memoryConsumption.getAverage();
    }

    public void setAverageMemoryConsumption(float value) {
        memoryConsumption.setAverage(value);
    }

    public float getGradientOfLoadAverage() {
        return loadAverage.getGradient();
    }

    public void setGradientOfLoadAverage(float value) {
        loadAverage.setGradient(value);
    }

    public float getGradientOfMemoryConsumption() {
        return memoryConsumption.getGradient();
    }

    public void setGradientOfMemoryConsumption(float value) {
        memoryConsumption.setGradient(value);
    }

    public float getSecondDerivativeOfLoadAverage() {
        return loadAverage.getSecondDerivative();
    }

    public void setSecondDerivativeOfLoadAverage(float value) {
        loadAverage.setSecondDerivative(value);
    }

    public float getSecondDerivativeOfMemoryConsumption() {
        return memoryConsumption.getSecondDerivative();
    }

    public void setSecondDerivativeOfMemoryConsumption(float value) {
        memoryConsumption.setSecondDerivative(value);
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }


    public void setPredictedMemoryConsumption(double[] predictions) {


        memoryConsumption.setPredictions(predictions);
       //Todo : add rest of the logic of handling synchronization between Other memory updates
    }

    public void setPredictedLoadAverage(double[] predictions) {


        loadAverage.setPredictions(predictions);
        //Todo : add rest of the logic of handling synchronization between Other memory updates
    }
}
