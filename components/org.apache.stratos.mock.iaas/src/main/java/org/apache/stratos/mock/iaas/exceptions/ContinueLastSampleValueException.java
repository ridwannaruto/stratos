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

package org.apache.stratos.mock.iaas.exceptions;

/**
 * Thrown when statistics pattern mode is set to continue and pattern reaches
 * the last sample value.
 */
public class ContinueLastSampleValueException extends Exception {
    private double lastSampleValue;

    public ContinueLastSampleValueException(double lastSampleValue) {
        this.lastSampleValue = lastSampleValue;
    }

    public double getLastSampleValue() {
        return lastSampleValue;
    }
}
