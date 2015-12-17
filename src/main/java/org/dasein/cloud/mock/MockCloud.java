/**
 * Copyright (C) 2009-2012 enStratus Networks Inc.
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.mock;

import java.io.UnsupportedEncodingException;

import javax.annotation.Nonnull;

import org.dasein.cloud.AbstractCloud;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.mock.compute.MockComputeServices;
import org.dasein.cloud.mock.network.MockNetworkServices;
import org.dasein.cloud.network.NetworkServices;

/**
 * Injectable Mock Cloud provider for unit testing.
 * <p>
 * Created by George Reese: 8/23/12 5:19 PM
 * </p>
 * 
 * @author George Reese
 * @version 2012.07
 * @since 2012.07
 */
public class MockCloud extends AbstractCloud {

	private ComputeServices computeServices;

	private DataCenterServices dataCenterServices;

	private NetworkServices networkServices;


	public MockCloud() {
		computeServices = new MockComputeServices(this);
		dataCenterServices = new MockDataCenterServices();
		networkServices = new MockNetworkServices(this);
	}

	@Override
	public @Nonnull ComputeServices getComputeServices() {
		return computeServices;
	}

	@Override
	public @Nonnull DataCenterServices getDataCenterServices() {
		return dataCenterServices;
	}

	@Override
	public @Nonnull NetworkServices getNetworkServices() {
		return networkServices;
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nonnull String getCloudName() {
		ProviderContext ctx = getContext();
		String name = (ctx == null ? null : ctx.getCloudName());

		return (name == null ? "Mock Cloud" : name);
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nonnull String getProviderName() {
		ProviderContext ctx = getContext();
		String name = (ctx == null ? null : ctx.getProviderName());

		return (name == null ? "Dasein" : name);
	}

	@SuppressWarnings("deprecation")
	@Override
	public String testContext() {
		ProviderContext ctx = getContext();

		if (ctx == null) {
			return null;
		}
		try {
			String access = new String(ctx.getAccessPublic(), "utf-8");
			String secret = new String(ctx.getAccessPrivate(), "utf-8");

			if (!access.equals("6789")) {
				return null;
			}
			if (!secret.equals("abcdefghijkl")) {
				return null;
			}
			return "12345";
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}
}
