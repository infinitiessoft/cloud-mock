/*******************************************************************************
 * Copyright 2015 InfinitiesSoft Solutions Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package com.infinities.skyport.mock;

import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.dasein.cloud.mock.MockCloud;

import com.infinities.skyport.ServiceProvider;
import com.infinities.skyport.compute.SkyportComputeServices;
import com.infinities.skyport.mock.compute.SkyportMockComputeServices;

public class MockServiceProvider extends MockCloud implements ServiceProvider {

	private SkyportMockComputeServices computeServices;


	public MockServiceProvider() {
		super();
		this.computeServices = new SkyportMockComputeServices(this);
	}

	@Override
	public void initialize() {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.infinities.skyport.ServiceProvider#getSkyportComputeServices()
	 */
	@Override
	public SkyportComputeServices getSkyportComputeServices() throws ConcurrentException {
		return computeServices;
	}
}
