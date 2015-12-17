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

package org.dasein.cloud.mock.network;

import javax.annotation.Nonnull;

import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.mock.network.firewall.MockFirewallSupport;
import org.dasein.cloud.mock.network.ip.MockIPSupport;
import org.dasein.cloud.network.AbstractNetworkServices;
import org.dasein.cloud.network.FirewallSupport;
import org.dasein.cloud.network.IpAddressSupport;

/**
 * Implements mock network services for Dasein Cloud support.
 * <p>
 * Created by George Reese: 10/18/12 3:00 PM
 * </p>
 * 
 * @author George Reese
 * @version 2012.09 initial version
 * @since 2012.09
 */
public class MockNetworkServices extends AbstractNetworkServices<CloudProvider> {

	private CloudProvider provider;


	public MockNetworkServices(CloudProvider provider) {
		super(provider);
		this.provider = provider;
	}

	@Override
	public @Nonnull FirewallSupport getFirewallSupport() {
		return new MockFirewallSupport(provider);
	}

	@Override
	public @Nonnull IpAddressSupport getIpAddressSupport() {
		return new MockIPSupport(provider);
	}
}
