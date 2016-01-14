package org.apache.stratos.autoscaler.costmodel.data;

/**
 * Created by ridwan on 1/7/16.
 */
public class InstanceSpec {

    private float perInstanceCost;
    private float optimumMemoryConsumption;
    private float optimumRequestCount;
    private float optimumLoadAverage;

    public InstanceSpec(String instanceType, String regionName){
        this.optimumRequestCount = 100;
        this.optimumLoadAverage = 0.9f;
        this.optimumMemoryConsumption = 0.9f;
        this.perInstanceCost = 0.02f;
    }

    public float getPerInstanceCost() {
        return perInstanceCost;
    }

    public void setPerInstanceCost(float perInstanceCost) {
        this.perInstanceCost = perInstanceCost;
    }

    public float getOptimumMemoryConsumption() {
        return optimumMemoryConsumption;
    }

    public void setOptimumMemoryConsumption(float optimumMemoryConsumption) {
        this.optimumMemoryConsumption = optimumMemoryConsumption;
    }

    public float getOptimumLoadAverage() {
        return optimumLoadAverage;
    }

    public void setOptimumLoadAverage(float optimumLoadAverage) {
        this.optimumLoadAverage = optimumLoadAverage;
    }

    public float getOptimumRequestCount() {
        return optimumRequestCount;
    }

    public void setOptimumRequestCount(float optimumRequestCount) {
        this.optimumRequestCount = optimumRequestCount;
    }
}
