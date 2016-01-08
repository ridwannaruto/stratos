package org.apache.stratos.autoscaler.costmodel;

import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.stratos.autoscaler.costmodel.data.InstanceSpec;

/**
 * Created by ridwan on 1/7/16.
 */
public class PenaltyEstimator {

    private InstanceSpec instanceType;

    private static final double MANTISSA = 2.0;
    private static final double EXPONENT_FACTOR = 20.0;

    public PenaltyEstimator(InstanceSpec instanceType){
        this.instanceType = instanceType;
    }

    public float calculatePenaltyCost(PolynomialSplineFunction predictedPolynomial, int instanceCount, char type){
        float penaltyCost = 0;
        float penaltyPercentage = calculatePenaltyPercentage(predictedPolynomial, instanceCount,type);

        //Google Appengine SLA modified
        if (penaltyPercentage >= 0.05  && penaltyPercentage < 1){
            penaltyCost = 0.1f;
        }else if (penaltyPercentage >= 1 && penaltyPercentage > 5){
            penaltyCost = 0.25f;
        }else if (penaltyPercentage >= 5 && penaltyPercentage > 10){
            penaltyCost = 0.5f;
        }else{
            penaltyCost = (float)Math.pow(MANTISSA,penaltyPercentage/EXPONENT_FACTOR);
        }

        return penaltyCost*instanceType.getPerInstanceCost()*instanceCount;
    }

    private float calculatePenaltyPercentage(PolynomialSplineFunction predictedPolynomial, int instanceCount, char type){
        float penaltyPercentage = 0;
        float totalResourcePower = 0;
        switch (type){
            case CostModelParameters.PERF_MEASURE_TYPE_LA:
                totalResourcePower = instanceType.getOptimumLoadAverage() * instanceCount;
                break;
            case CostModelParameters.PERF_MEASURE_TYPE_MC:
                totalResourcePower = instanceType.getOptimumMemoryConsumption() * instanceCount;
                break;
            case CostModelParameters.PERF_MEASURE_TYPE_RIF:
                totalResourcePower = instanceType.getOptimumRequestCount() * instanceCount;
                break;
        }

        int violatedPoints = 0;

        for (double i=0; i< CostModelParameters.LIMIT_PREDICTION; i+= 0.1){
            if (predictedPolynomial.value(i) > totalResourcePower)
                violatedPoints++;
        }
        penaltyPercentage = (float)violatedPoints/(CostModelParameters.LIMIT_PREDICTION * 10);
        return penaltyPercentage;
    }




}
