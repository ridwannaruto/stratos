package org.apache.stratos.autoscaler.costmodel;

import org.apache.commons.math3.analysis.polynomials.*;
import org.apache.stratos.autoscaler.context.cluster.ClusterContext;
import org.apache.stratos.autoscaler.pojo.policy.autoscale.LoadAverage;
import org.apache.stratos.autoscaler.pojo.policy.autoscale.MemoryConsumption;

/**
 * Created by ridwan on 1/7/16.
 */
public class PriceEstimator {

    public float calculateTotalCost(char type,PolynomialSplineFunction polynomial, int instanceCount){
        float totalCost = 0;
        switch (type){
            case GlobalParameters.PERF_MEASURE_TYPE_LA:

                break;
            case GlobalParameters.PERF_MEASURE_TYPE_MC:

                break;
            case GlobalParameters.PERF_MEASURE_TYPE_RIF:

                break;
        }


        return totalCost;
    }

}
