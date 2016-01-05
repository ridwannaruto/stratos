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
        String frontendId, backendId, command, output;
        int totalWeight, weight;

        for (Service service : topologyProvider.getTopology().getServices()) {
            for (Cluster cluster : service.getClusters()) {
                if (cluster.getClusterId().equals(clusterId)) {
                    totalWeight = 0;
                    if ((service.getPorts() == null) || (service.getPorts().size() == 0)) {
                        throw new RuntimeException(String.format("No ports found in service: %s", service.getServiceName()));
                    }

                    for (Port port : service.getPorts()) {
                        for(String hostname : cluster.getHostNames()) {
                            backendId = hostname + "_http_" + port.getValue() + "_backend";
                            for (Member member : cluster.getMembers()) {
                                // echo "show stat" | sudo socat stdio $2 | grep $1 | awk -F "\"*,\"*" '{print $3}'
                                command = String.format("%s/get-qcur.sh %s,%s %s", scriptsPath, backendId,
                                        member.getMemberId(), statsSocketFilePath);
                                try {
                                    output = CommandUtils.executeCommand(command);
                                    if (output != null && output.length() > 0) {
                                        weight = Integer.parseInt(output.trim());
                                        if (log.isDebugEnabled()) {
                                            log.debug(String.format("Member queue length found: [member] %s [weight] %d",
                                                    member.getMemberId(), weight));
                                        }
                                        totalWeight += weight;
                                    }
                                } catch (IOException e) {
                                    if (log.isErrorEnabled()) {
                                        log.error(e);
                                    }
                                }
                            }
                        }
                    }
                    if (log.isInfoEnabled()) {
                        log.info(String.format("Cluster queue length found: [cluster] %s [weight] %d",
                                cluster.getClusterId(), totalWeight));
                    }
                    return totalWeight;
                }
            }
        }
        return 0;
    }
}
