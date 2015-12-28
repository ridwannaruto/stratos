/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.stratos.haproxy.extension;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.common.constants.StratosConstants;
import org.apache.stratos.common.util.CommandUtils;
import org.apache.stratos.load.balancer.common.domain.Cluster;
import org.apache.stratos.load.balancer.common.domain.Member;
import org.apache.stratos.load.balancer.common.domain.Port;
import org.apache.stratos.load.balancer.common.domain.Service;
import org.apache.stratos.load.balancer.common.statistics.LoadBalancerStatisticsReader;
import org.apache.stratos.load.balancer.common.topology.TopologyProvider;

import java.io.IOException;

/**
 * HAProxy statistics reader.
 */
public class HAProxyStatisticsReader implements LoadBalancerStatisticsReader {

    private static final Log log = LogFactory.getLog(HAProxyStatisticsReader.class);

    private String scriptsPath;
    private String statsSocketFilePath;
    private TopologyProvider topologyProvider;
    private String clusterInstanceId;

    public HAProxyStatisticsReader(TopologyProvider topologyProvider) {
        this.scriptsPath = HAProxyContext.getInstance().getScriptsPath();
        this.statsSocketFilePath = HAProxyContext.getInstance().getStatsSocketFilePath();
        this.topologyProvider = topologyProvider;
        this.clusterInstanceId = System.getProperty(StratosConstants.CLUSTER_INSTANCE_ID, StratosConstants.NOT_DEFINED);
    }

    @Override
    public String getClusterInstanceId() {
        return clusterInstanceId;
    }

    @Override
    public int getInFlightRequestCount(String clusterId) {
        String command = String.format("%s/get-qcur.sh %s", scriptsPath, statsSocketFilePath);
        try {
            String output = CommandUtils.executeCommand(command);
            if ((output != null) && (output.length() > 0)) {
                int weight = Integer.parseInt(output.trim());
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Current queue length found: %d", weight));
                }
                return weight;
            }
        } catch (IOException e) {
            if (log.isErrorEnabled()) {
                log.error(e);
            }
        }
        return 0;
    }
}
