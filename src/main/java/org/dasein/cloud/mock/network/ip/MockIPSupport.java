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

package org.dasein.cloud.mock.network.ip;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.AddressType;
import org.dasein.cloud.network.IPAddressCapabilities;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.IpAddress;
import org.dasein.cloud.network.IpAddressSupport;
import org.dasein.cloud.network.IpForwardingRule;
import org.dasein.cloud.network.Protocol;

import com.google.common.util.concurrent.Futures;

/**
 * Mock support for public IP address management in the cloud, including IPv4
 * and IPv6 support.
 * <p>
 * Created by George Reese: 10/18/12 3:01 PM
 * </p>
 * 
 * @author George Reese
 * @version 2012.09 initial version
 * @since 2012.09
 */
public class MockIPSupport implements IpAddressSupport {

	static private final TreeSet<String> allocatedIps = new TreeSet<String>();
	static private final HashMap<String, Map<String, Map<String, Collection<String>>>> allocations =
			new HashMap<String, Map<String, Map<String, Collection<String>>>>();
	static private final HashMap<String, String> vmAssignments = new HashMap<String, String>();
	static private final HashMap<String, String> lbAssignments = new HashMap<String, String>();

	static private int quad1 = 26;
	static private int quad2 = 0;
	static private int quad3 = 0;
	static private int quad4 = 0;

	static private final Random random = new Random();


	@SuppressWarnings("deprecation")
	static private @Nonnull String allocate(@Nonnull ProviderContext ctx, IPVersion version) throws CloudException {
		synchronized (allocatedIps) {
			String ip;

			do {
				if (version.equals(IPVersion.IPV4)) {
					quad4++;
					if (quad4 > 253) {
						quad3++;
						if (quad3 > 253) {
							quad2++;
							if (quad2 > 253) {
								quad1++;
								if (quad1 == 10 || quad1 == 25 || quad1 == 127 || quad1 == 172 || quad1 == 187
										|| quad1 == 192 || quad1 == 207) {
									quad1++;
								}
								if (quad1 > 253) {
									throw new CloudException("IPv4 address space exhausted");
								}
							}
						}
					}
					ip = (quad1 + "." + quad2 + "." + quad3 + "." + quad4);
				} else {
					StringBuilder str = new StringBuilder();

					str.append("2001");

					for (int i = 0; i < 7; i++) {
						str.append(":");
						str.append(Integer.toHexString(random.nextInt(65534)));
					}
					ip = str.toString();
				}
			} while (allocatedIps.contains(ip));
			allocatedIps.add(ip);

			Map<String, Map<String, Collection<String>>> cloud = allocations.get(ctx.getEndpoint());

			if (cloud == null) {
				cloud = new HashMap<String, Map<String, Collection<String>>>();
				allocations.put(ctx.getEndpoint(), cloud);
			}
			Map<String, Collection<String>> region = cloud.get(ctx.getRegionId());

			if (region == null) {
				region = new HashMap<String, Collection<String>>();
				cloud.put(ctx.getRegionId(), region);
			}
			Collection<String> account = region.get(ctx.getEffectiveAccountNumber());

			if (account == null) {
				account = new TreeSet<String>();
				region.put(ctx.getEffectiveAccountNumber(), account);
			}
			account.add(ip);
			return ip;
		}
	}

	@SuppressWarnings("deprecation")
	static public void assignToVM(@Nonnull ProviderContext ctx, @Nonnull String ipAddress, @Nonnull VirtualMachine vm)
			throws CloudException {
		synchronized (allocatedIps) {
			Map<String, Map<String, Collection<String>>> cloud = allocations.get(ctx.getEndpoint());

			if (cloud == null) {
				cloud = new HashMap<String, Map<String, Collection<String>>>();
				allocations.put(ctx.getEndpoint(), cloud);
			}
			Map<String, Collection<String>> region = cloud.get(ctx.getRegionId());

			if (region == null) {
				region = new HashMap<String, Collection<String>>();
				cloud.put(ctx.getRegionId(), region);
			}
			Collection<String> account = region.get(ctx.getEffectiveAccountNumber());

			if (account == null) {
				account = new TreeSet<String>();
				region.put(ctx.getEffectiveAccountNumber(), account);
			}
			if (!account.contains(ipAddress)) {
				throw new CloudException("That IP address is not allocated to you");
			}

			String current = null;

			for (Map.Entry<String, String> entry : vmAssignments.entrySet()) {
				if (entry.getKey().equals(ipAddress)) {
					throw new CloudException("IP address is already assigned");
				}
				if (entry.getValue().equals(vm.getProviderVirtualMachineId())) {
					current = entry.getKey();
				}
			}
			if (current == null) {
				if (lbAssignments.containsKey(ipAddress)) {
					throw new CloudException("IP address is already assigned");
				}
			}
			vmAssignments.put(ipAddress, vm.getProviderVirtualMachineId());
			if (current != null) {
				vmAssignments.remove(current);
			}
		}
	}

	static public @Nullable String getIPAddressForVM(@Nonnull String vmId) {
		synchronized (allocatedIps) {
			for (Map.Entry<String, String> entry : vmAssignments.entrySet()) {
				if (entry.getValue().equals(vmId)) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	static public @Nullable String getIPAddressForLB(@Nonnull String lbId) {
		synchronized (allocatedIps) {
			for (Map.Entry<String, String> entry : lbAssignments.entrySet()) {
				if (entry.getValue().equals(lbId)) {
					return entry.getKey();
				}
			}
		}
		return null;
	}


	private CloudProvider provider;


	public MockIPSupport(@Nonnull CloudProvider provider) {
		this.provider = provider;
	}

	@Override
	public void assign(@Nonnull String addressId, @Nonnull String serverId) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was set for this request");
		}
		ComputeServices compute = provider.getComputeServices();

		if (compute == null) {
			throw new CloudException("This cloud does not support compute services");
		}
		VirtualMachineSupport vmSupport = compute.getVirtualMachineSupport();

		if (vmSupport == null) {
			throw new CloudException("This cloud does not support virtual machines");
		}
		VirtualMachine vm = vmSupport.getVirtualMachine(serverId);

		if (vm == null) {
			throw new CloudException("No such virtual machine: " + serverId);
		}
		assignToVM(ctx, addressId, vm);
	}

	@Override
	public void assignToNetworkInterface(@Nonnull String addressId, @Nonnull String nicId) throws InternalException,
			CloudException {
		throw new OperationNotSupportedException("No support for network interfaces");
	}

	@Override
	public @Nonnull String forward(@Nonnull String addressId, int publicPort, @Nonnull Protocol protocol, int privatePort,
			@Nonnull String onServerId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("No support for IP forwarding");
	}

	@Override
	public IpAddress getIpAddress(@Nonnull String addressId) throws InternalException, CloudException {
		for (IpAddress addr : listIpPool(IPVersion.IPV4, false)) {
			if (addr.getProviderIpAddressId().equals(addressId)) {
				return addr;
			}
		}
		for (IpAddress addr : listIpPool(IPVersion.IPV6, false)) {
			if (addr.getProviderIpAddressId().equals(addressId)) {
				return addr;
			}
		}
		return null;
	}

	@Override
	public @Nonnull String getProviderTermForIpAddress(@Nonnull Locale locale) {
		return "IP Address";
	}

	@Override
	public @Nonnull Requirement identifyVlanForVlanIPRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public boolean isAssigned(@Nonnull AddressType type) {
		return (type.equals(AddressType.PUBLIC));
	}

	@Override
	public boolean isAssigned(@Nonnull IPVersion version) throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isAssignablePostLaunch(@Nonnull IPVersion version) throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isForwarding() {
		return false;
	}

	@Override
	public boolean isForwarding(IPVersion version) throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean isRequestable(@Nonnull AddressType type) {
		return type.equals(AddressType.PUBLIC);
	}

	@Override
	public boolean isRequestable(@Nonnull IPVersion version) throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public @Nonnull Iterable<IpAddress> listPrivateIpPool(boolean unassignedOnly) throws InternalException, CloudException {
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public Iterable<IpAddress> listPublicIpPool(boolean unassignedOnly) throws InternalException, CloudException {
		return listIpPool(IPVersion.IPV4, unassignedOnly);
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nonnull Iterable<IpAddress> listIpPool(@Nonnull IPVersion version, boolean unassignedOnly)
			throws InternalException, CloudException {
		ArrayList<IpAddress> addresses = new ArrayList<IpAddress>();
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was set for this request");
		}
		synchronized (allocatedIps) {
			Map<String, Map<String, Collection<String>>> cloud = allocations.get(ctx.getEndpoint());

			if (cloud == null) {
				cloud = new HashMap<String, Map<String, Collection<String>>>();
				allocations.put(ctx.getEndpoint(), cloud);
			}
			Map<String, Collection<String>> region = cloud.get(ctx.getRegionId());

			if (region == null) {
				region = new HashMap<String, Collection<String>>();
				cloud.put(ctx.getRegionId(), region);
			}
			Collection<String> account = region.get(ctx.getEffectiveAccountNumber());

			if (account == null) {
				account = new TreeSet<String>();
				region.put(ctx.getEffectiveAccountNumber(), account);
			}
			for (String ip : account) {
				boolean v4 = (ip.split("\\.").length == 4);

				if (v4 != version.equals(IPVersion.IPV4)) {
					continue;
				}
				if (unassignedOnly && (vmAssignments.containsKey(ip) || lbAssignments.containsKey(ip))) {
					continue;
				}
				IpAddress address = new IpAddress();

				address.setAddress(ip);
				address.setAddressType(AddressType.PUBLIC);
				address.setForVlan(false);
				address.setIpAddressId(ip);
				address.setProviderLoadBalancerId(lbAssignments.get(ip));
				address.setProviderNetworkInterfaceId(null);
				// noinspection ConstantConditions
				address.setRegionId(ctx.getRegionId());
				address.setServerId(vmAssignments.get(ip));
				address.setVersion(version);
				addresses.add(address);
			}
		}
		return addresses;
	}

	@Override
	public @Nonnull Iterable<ResourceStatus> listIpPoolStatus(@Nonnull IPVersion version) throws InternalException,
			CloudException {
		ArrayList<ResourceStatus> status = new ArrayList<ResourceStatus>();

		for (IpAddress addr : listIpPool(version, false)) {
			status.add(new ResourceStatus(addr.getProviderIpAddressId(), !addr.isAssigned()));
		}
		return status;
	}

	@Override
	public @Nonnull Iterable<IpForwardingRule> listRules(@Nonnull String addressId) throws InternalException, CloudException {
		return Collections.emptyList();
	}

	@Override
	public @Nonnull Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
		ArrayList<IPVersion> versions = new ArrayList<IPVersion>();

		versions.add(IPVersion.IPV4);
		versions.add(IPVersion.IPV6);
		return versions;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void releaseFromPool(@Nonnull String ip) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was set for this request");
		}
		synchronized (allocatedIps) {
			if (vmAssignments.containsKey(ip) || lbAssignments.containsKey(ip)) {
				throw new CloudException("That IP is currently assigned to a resource");
			}
			Map<String, Map<String, Collection<String>>> cloud = allocations.get(ctx.getEndpoint());

			if (cloud == null) {
				cloud = new HashMap<String, Map<String, Collection<String>>>();
				allocations.put(ctx.getEndpoint(), cloud);
			}
			Map<String, Collection<String>> region = cloud.get(ctx.getRegionId());

			if (region == null) {
				region = new HashMap<String, Collection<String>>();
				cloud.put(ctx.getRegionId(), region);
			}
			Collection<String> account = region.get(ctx.getEffectiveAccountNumber());

			if (account == null) {
				account = new TreeSet<String>();
				region.put(ctx.getEffectiveAccountNumber(), account);
			}
			account.remove(ip);
			allocatedIps.remove(ip);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void releaseFromServer(@Nonnull String ip) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was set for this request");
		}
		synchronized (allocatedIps) {
			if (!vmAssignments.containsKey(ip)) {
				throw new CloudException("That IP is not currently assigned to a resource");
			}
			Map<String, Map<String, Collection<String>>> cloud = allocations.get(ctx.getEndpoint());

			if (cloud == null) {
				cloud = new HashMap<String, Map<String, Collection<String>>>();
				allocations.put(ctx.getEndpoint(), cloud);
			}
			Map<String, Collection<String>> region = cloud.get(ctx.getRegionId());

			if (region == null) {
				region = new HashMap<String, Collection<String>>();
				cloud.put(ctx.getRegionId(), region);
			}
			Collection<String> account = region.get(ctx.getEffectiveAccountNumber());

			if (account == null) {
				account = new TreeSet<String>();
				region.put(ctx.getEffectiveAccountNumber(), account);
			}
			if (!account.contains(ip)) {
				throw new CloudException("Not your IP address");
			}
			vmAssignments.remove(ip);
		}
	}

	@Override
	public @Nonnull String request(@Nonnull AddressType typeOfAddress) throws InternalException, CloudException {
		if (typeOfAddress.equals(AddressType.PRIVATE)) {
			throw new OperationNotSupportedException("No support for private IP address requests");
		}
		return request(IPVersion.IPV4);
	}

	@Override
	public @Nonnull String request(@Nonnull IPVersion version) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was set for this request");
		}
		return allocate(ctx, version);
	}

	@Override
	public @Nonnull String requestForVLAN(@Nonnull IPVersion version) throws InternalException, CloudException {
		throw new OperationNotSupportedException("VLAN IP addresses are not yet supported");
	}

	@Override
	public @Nonnull String requestForVLAN(@Nonnull IPVersion version, @Nonnull String vlanId) throws InternalException,
			CloudException {
		throw new OperationNotSupportedException("No support for VLAN IP addresses");
	}

	@Override
	public void stopForward(@Nonnull String ruleId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("IP forwarding is not supported");
	}

	@Override
	public boolean supportsVLANAddresses(@Nonnull IPVersion ofVersion) throws InternalException, CloudException {
		return false;
	}

	@Override
	public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
		return new String[0];
	}

	@Override
	public IPAddressCapabilities getCapabilities() throws CloudException, InternalException {

		return null;
	}

	@Override
	public Future<Iterable<IpAddress>> listIpPoolConcurrently(IPVersion version, boolean unassignedOnly)
			throws InternalException, CloudException {
		return Futures.immediateFuture(listIpPool(version, unassignedOnly));
	}

	@Override
	public Iterable<IpForwardingRule> listRulesForServer(String serverId) throws InternalException, CloudException {
		return listRules(null);
	}

	@Override
	public void stopForwardToServer(String ruleId, String serverId) throws InternalException, CloudException {
		stopForward(ruleId);
	}

	@Override
	public void removeTags(String addressId, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void removeTags(String[] addressIds, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void updateTags(String addressId, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void updateTags(String[] addressIds, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void setTags(String addressId, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void setTags(String[] addressIds, Tag... tags) throws CloudException, InternalException {

	}
}
