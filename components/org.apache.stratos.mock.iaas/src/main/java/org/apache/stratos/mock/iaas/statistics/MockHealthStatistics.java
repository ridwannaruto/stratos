/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.stratos.mock.iaas.statistics;

import org.apache.stratos.mock.iaas.exceptions.NoStatisticsFoundException;
import org.apache.stratos.mock.iaas.services.impl.MockScalingFactor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock health statistics singleton class.
 */
public class MockHealthStatistics {
    private final static int DEFAULT_MEMORY_CONSUMPTION = 20;
    private final static int DEFAULT_LOAD_AVERAGE = 20;
    private final static int DEFAULT_REQUESTS_IN_FLIGHT = 1;

    private static volatile MockHealthStatistics instance;

    private Map<String, Map<String, Double>> statisticsMap;

    private MockHealthStatistics() {
        statisticsMap = new ConcurrentHashMap<String, Map<String, Double>>();
    }

    public static MockHealthStatistics getInstance() {
        if (instance == null) {
            synchronized (MockHealthStatistics.class) {
                if (instance == null) {
                    instance = new MockHealthStatistics();
                }
            }
        }
        return instance;
    }

    /**
     * Add statistics value for a cartridge type, scaling factor
     *
     * @param cartridgeType
     * @param scalingFactor
     * @param value
     */
    public void addStatistics(String cartridgeType, MockScalingFactor scalingFactor, Double value) {
        Map<String, Double> factorValueMap = statisticsMap.get(cartridgeType);
        if (factorValueMap == null) {
            synchronized (MockHealthStatistics.class) {
                if (factorValueMap == null) {
                    factorValueMap = new ConcurrentHashMap<String, Double>();
                    statisticsMap.put(cartridgeType, factorValueMap);
                }
            }
        }
        factorValueMap.put(scalingFactor.toString(), value);
    }

    /**
     * Returns current statistics of the given cartridge type, scaling factor
     *
     * @param cartridgeType
     * @param scalingFactor
     * @return
     */
    public double getStatistics(String cartridgeType, MockScalingFactor scalingFactor) throws NoStatisticsFoundException {
        Map<String, Double> factorValueMap = statisticsMap.get(cartridgeType);
        if (factorValueMap != null) {
            if (factorValueMap.containsKey(scalingFactor.toString())) {
                return factorValueMap.get(scalingFactor.toString());
            } else {
                throw new NoStatisticsFoundException();
            }
        }
        // No statistics patterns found, return default
        return findDefault(scalingFactor);
    }

    /**
     * Remove statistics found for the cartridge type, scaling factor
     *
     * @param cartridgeType
     * @param scalingFactor
     */
    public void removeStatistics(String cartridgeType, MockScalingFactor scalingFactor) {
        Map<String, Double> factorValueMap = statisticsMap.get(cartridgeType);
        if (factorValueMap != null) {
            if (factorValueMap.containsKey(scalingFactor.toString())) {
                factorValueMap.remove(scalingFactor.toString());
            }
        }
    }

    /**
     * Find default statistics value of the given scaling factor
     *
     * @param scalingFactor
     * @return
     */
    private int findDefault(MockScalingFactor scalingFactor) {
        if (scalingFactor == MockScalingFactor.MemoryConsumption) {
            return DEFAULT_MEMORY_CONSUMPTION;
        } else if (scalingFactor == MockScalingFactor.LoadAverage) {
            return DEFAULT_LOAD_AVERAGE;
        } else if (scalingFactor == MockScalingFactor.RequestsInFlight) {
            return DEFAULT_REQUESTS_IN_FLIGHT;
        }
        throw new RuntimeException("An unknown scaling factor found: " + scalingFactor);
    }
}
