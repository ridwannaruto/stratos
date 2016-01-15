package org.apache.stratos.autoscaler.costmodel;

import org.apache.commons.math3.analysis.polynomials.*;
import org.apache.stratos.autoscaler.costmodel.data.InstanceSpec;

/**
 * Created by ridwan on 1/7/16.
 */
public class CostEstimator {

    private InstanceSpec instanceType;
    private PenaltyEstimator penaltyEstimator;
    public CostEstimator(String instanceType, String regionName){
        this.instanceType = new InstanceSpec(instanceType,regionName);
        penaltyEstimator = new PenaltyEstimator(this.instanceType);
    }

    public float calculateTotalCostBasedOnRIF(PolynomialSplineFunction polynomial, int instanceCount){
        float totalCost = 0;
        float acquisitionCost = instanceType.getPerInstanceCost() * instanceCount;
        float penaltyCost = penaltyEstimator.calculatePenaltyCost(polynomial,instanceCount,CostModelParameters.PERF_MEASURE_TYPE_RIF);

        totalCost = acquisitionCost + penaltyCost;
        return totalCost;
    }

    public float calculateTotalCostBasedOnLA(PolynomialSplineFunction polynomial, int instanceCount){
        float totalCost = 0;
        float acquisitionCost = instanceType.getPerInstanceCost() * instanceCount;
        float penaltyCost = penaltyEstimator.calculatePenaltyCost(polynomial,instanceCount,CostModelParameters.PERF_MEASURE_TYPE_LA);

        totalCost = acquisitionCost + penaltyCost;
        return totalCost;
    }


    public float calculateTotalCostBasedOnMC(PolynomialSplineFunction polynomial, int instanceCount){
        float totalCost = 0;
        float acquisitionCost = instanceType.getPerInstanceCost() * instanceCount;
        float penaltyCost = penaltyEstimator.calculatePenaltyCost(polynomial,instanceCount,CostModelParameters.PERF_MEASURE_TYPE_MC);

        totalCost = acquisitionCost + penaltyCost;
        return totalCost;
    }




}
