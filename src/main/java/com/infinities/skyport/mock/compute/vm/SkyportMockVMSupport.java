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
package com.infinities.skyport.mock.compute.vm;

import java.util.ArrayList;
import java.util.Locale;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.SpotPriceHistory;
import org.dasein.cloud.compute.SpotPriceHistoryFilterOptions;
import org.dasein.cloud.compute.SpotVirtualMachineRequest;
import org.dasein.cloud.compute.SpotVirtualMachineRequestCreateOptions;
import org.dasein.cloud.compute.SpotVirtualMachineRequestFilterOptions;
import org.dasein.cloud.compute.VMFilterOptions;
import org.dasein.cloud.compute.VMLaunchOptions;
import org.dasein.cloud.compute.VMScalingCapabilities;
import org.dasein.cloud.compute.VMScalingOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VirtualMachineProductFilterOptions;
import org.dasein.cloud.compute.VirtualMachineStatus;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VmStatistics;
import org.dasein.cloud.compute.VmStatusFilterOptions;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.mock.compute.vm.MockVMSupport;
import org.dasein.cloud.network.RawAddress;

import com.infinities.skyport.compute.SkyportVirtualMachineCapabilities;
import com.infinities.skyport.compute.SkyportVirtualMachineSupport;
import com.infinities.skyport.compute.VMUpdateOptions;
import com.infinities.skyport.compute.entity.MinimalResource;
import com.infinities.skyport.compute.entity.NovaStyleVirtualMachine;
import com.infinities.skyport.network.SkyportRawAddress;

/**
 * @author pohsun
 *
 */
public class SkyportMockVMSupport implements SkyportVirtualMachineSupport {

	private MockVMSupport inner;
	private CloudProvider provider;


	/**
	 * @param provider
	 */
	public SkyportMockVMSupport(CloudProvider provider) {
		this.provider = provider;
		this.inner = new MockVMSupport(provider);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.infinities.skyport.compute.SkyportVirtualMachineSupport#
	 * listMinimalVirtualMachine()
	 */
	@Override
	public Iterable<MinimalResource> listMinimalVirtualMachines() throws InternalException, CloudException {
		ArrayList<MinimalResource> resources = new ArrayList<MinimalResource>();

		for (VirtualMachine vm : listVirtualMachines()) {
			resources.add(new MinimalResource(vm.getProviderVirtualMachineId(), vm.getName()));
		}
		return resources;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.infinities.skyport.compute.SkyportVirtualMachineSupport#
	 * listNovaStyleVirtualMachines()
	 */
	@Override
	public Iterable<NovaStyleVirtualMachine> listNovaStyleVirtualMachines() throws InternalException, CloudException {
		ArrayList<NovaStyleVirtualMachine> resources = new ArrayList<NovaStyleVirtualMachine>();

		for (VirtualMachine vm : listVirtualMachines()) {
			resources.add(toNovaStyleVirtualMachine(vm));
		}
		return resources;
	}

	/**
	 * @param providerVirtualMachineId
	 * @param name
	 * @return
	 */
	private NovaStyleVirtualMachine toNovaStyleVirtualMachine(VirtualMachine vm) {
		NovaStyleVirtualMachine ret = new NovaStyleVirtualMachine();
		ret.setAffinityGroupId(vm.getAffinityGroupId());
		ret.setArchitecture(vm.getArchitecture());
		ret.setClientRequestToken(vm.getClientRequestToken());
		ret.setClonable(vm.isClonable());
		ret.setCreationTimestamp(vm.getCreationTimestamp());
		ret.setCurrentState(vm.getCurrentState());
		ret.setDescription(vm.getDescription());
		ret.setImagable(vm.isImagable());
		ret.setIoOptimized(vm.isIoOptimized());
		ret.setIpForwardingAllowed(vm.isIpForwardingAllowed());
		ret.setLabels(vm.getLabels());
		ret.setLastBootTimestamp(vm.getLastBootTimestamp());
		ret.setLastPauseTimestamp(vm.getLastPauseTimestamp());
		ret.setLifecycle(vm.getLifecycle());
		ret.setName(vm.getName());
		ret.setPausable(vm.isPausable());
		ret.setPersistent(vm.isPersistent());
		ret.setPlatform(vm.getPlatform());
		SkyportRawAddress[] priv = new SkyportRawAddress[vm.getPrivateAddresses().length];
		int privKey = 0;
		for (RawAddress address : vm.getPrivateAddresses()) {
			SkyportRawAddress p = new SkyportRawAddress(address.getIpAddress(), address.getVersion(), "private", null);
			priv[privKey++] = p;
		}
		ret.setPrivateAddresses(priv);
		ret.setPrivateDnsAddress(vm.getPrivateDnsAddress());
		ret.setProductId(vm.getProductId());
		ret.setProviderAssignedIpAddressId(vm.getProviderAssignedIpAddressId());
		ret.setProviderDataCenterId(vm.getProviderDataCenterId());
		ret.setProviderFirewallIds(vm.getProviderFirewallIds());
		ret.setProviderHostStatus(vm.getProviderHostStatus());
		ret.setProviderKernelImageId(vm.getProviderKernelImageId());
		ret.setProviderKeypairId(vm.getProviderKeypairId());
		ret.setProviderMachineImageId(vm.getProviderMachineImageId());
		ret.setProviderNetworkInterfaceIds(vm.getProviderNetworkInterfaceIds());
		ret.setProviderOwnerId(vm.getProviderOwnerId());
		ret.setProviderRamdiskImageId(vm.getProviderRamdiskImageId());
		ret.setProviderRegionId(vm.getProviderRegionId());
		ret.setProviderRoleId(vm.getProviderRoleId());
		ret.setProviderShellKeyIds(vm.getProviderShellKeyIds());
		ret.setProviderSubnetId(vm.getProviderSubnetId());
		ret.setProviderVirtualMachineId(vm.getProviderVirtualMachineId());
		ret.setProviderVlanId(vm.getProviderVlanId());
		ret.setProviderVmStatus(vm.getProviderVmStatus());
		SkyportRawAddress[] pub = new SkyportRawAddress[vm.getPublicAddresses().length];
		int pubKey = 0;
		for (RawAddress address : vm.getPublicAddresses()) {
			SkyportRawAddress p = new SkyportRawAddress(address.getIpAddress(), address.getVersion(), "private", null);
			pub[pubKey++] = p;
		}
		ret.setPublicAddresses(pub);
		ret.setPublicDnsAddress(vm.getPublicDnsAddress());
		ret.setRebootable(vm.isRebootable());
		ret.setResourcePoolId(vm.getResourcePoolId());
		ret.setRootPassword(vm.getRootPassword());
		ret.setRootUser(vm.getRootUser());
		ret.setSpotRequestId(vm.getSpotRequestId());
		ret.setStateReasonMessage(vm.getStateReasonMessage());
		ret.setTags(vm.getTags());
		ret.setTerminationTimestamp(vm.getTerminationTimestamp());
		ret.setVisibleScope(vm.getVisibleScope());
		ret.setVolumes(vm.getVolumes());
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.infinities.skyport.compute.SkyportVirtualMachineSupport#
	 * updateVirtualMachine(java.lang.String,
	 * com.infinities.skyport.compute.VMUpdateOptions)
	 */
	@Override
	public VirtualMachine updateVirtualMachine(String virtualMachineId, VMUpdateOptions options) throws InternalException,
			CloudException {
		return null;
	}

	/**
	 * @return
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return inner.hashCode();
	}

	/**
	 * @param obj
	 * @return
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		return inner.equals(obj);
	}

	/**
	 * @param vmId
	 * @param options
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#alterVirtualMachine(java.lang.String,
	 *      org.dasein.cloud.compute.VMScalingOptions)
	 */
	@Override
	public VirtualMachine alterVirtualMachine(String vmId, VMScalingOptions options) throws InternalException,
			CloudException {
		return inner.alterVirtualMachine(vmId, options);
	}

	/**
	 * @param vmId
	 * @param intoDcId
	 * @param name
	 * @param description
	 * @param powerOn
	 * @param firewallIds
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#clone(java.lang.String,
	 *      java.lang.String, java.lang.String, java.lang.String, boolean,
	 *      java.lang.String[])
	 */
	@Override
	public VirtualMachine clone(String vmId, String intoDcId, String name, String description, boolean powerOn,
			String... firewallIds) throws InternalException, CloudException {
		return inner.clone(vmId, intoDcId, name, description, powerOn, firewallIds);
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#describeVerticalScalingCapabilities()
	 */
	@Override
	public VMScalingCapabilities describeVerticalScalingCapabilities() throws CloudException, InternalException {
		return inner.describeVerticalScalingCapabilities();
	}

	/**
	 * @param vmId
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#disableAnalytics(java.lang.String)
	 */
	@Override
	public void disableAnalytics(String vmId) throws InternalException, CloudException {
		inner.disableAnalytics(vmId);
	}

	/**
	 * @param vmId
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#enableAnalytics(java.lang.String)
	 */
	@Override
	public void enableAnalytics(String vmId) throws InternalException, CloudException {
		inner.enableAnalytics(vmId);
	}

	/**
	 * @param vmId
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getConsoleOutput(java.lang.String)
	 */
	@Override
	public String getConsoleOutput(String vmId) throws InternalException, CloudException {
		return inner.getConsoleOutput(vmId);
	}

	/**
	 * @param state
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getCostFactor(org.dasein.cloud.compute.VmState)
	 */
	@Override
	public int getCostFactor(VmState state) throws InternalException, CloudException {
		return inner.getCostFactor(state);
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getMaximumVirtualMachineCount()
	 */
	@Override
	public int getMaximumVirtualMachineCount() throws CloudException, InternalException {
		return inner.getMaximumVirtualMachineCount();
	}

	/**
	 * @return
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return inner.toString();
	}

	/**
	 * @param productId
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getProduct(java.lang.String)
	 */
	@Override
	public VirtualMachineProduct getProduct(String productId) throws InternalException, CloudException {
		return inner.getProduct(productId);
	}

	/**
	 * @param locale
	 * @return
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getProviderTermForServer(java.util.Locale)
	 */
	@Override
	public String getProviderTermForServer(Locale locale) {
		return inner.getProviderTermForServer(locale);
	}

	/**
	 * @param vmId
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getVirtualMachine(java.lang.String)
	 */
	@Override
	public VirtualMachine getVirtualMachine(String vmId) throws InternalException, CloudException {
		return inner.getVirtualMachine(vmId);
	}

	/**
	 * @param vmId
	 * @param from
	 * @param to
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getVMStatistics(java.lang.String,
	 *      long, long)
	 */
	@Override
	public VmStatistics getVMStatistics(String vmId, long from, long to) throws InternalException, CloudException {
		return inner.getVMStatistics(vmId, from, to);
	}

	/**
	 * @param vmId
	 * @param from
	 * @param to
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getVMStatisticsForPeriod(java.lang.String,
	 *      long, long)
	 */
	@Override
	public Iterable<VmStatistics> getVMStatisticsForPeriod(String vmId, long from, long to) throws InternalException,
			CloudException {
		return inner.getVMStatisticsForPeriod(vmId, from, to);
	}

	/**
	 * @param cls
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#identifyImageRequirement(org.dasein.cloud.compute.ImageClass)
	 */
	@Override
	public Requirement identifyImageRequirement(ImageClass cls) throws CloudException, InternalException {
		return inner.identifyImageRequirement(cls);
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#identifyPasswordRequirement()
	 */
	@Override
	public Requirement identifyPasswordRequirement() throws CloudException, InternalException {
		return inner.identifyPasswordRequirement();
	}

	/**
	 * @param platform
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#identifyPasswordRequirement(org.dasein.cloud.compute.Platform)
	 */
	@Override
	public Requirement identifyPasswordRequirement(Platform platform) throws CloudException, InternalException {
		return inner.identifyPasswordRequirement(platform);
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#identifyRootVolumeRequirement()
	 */
	@Override
	public Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
		return inner.identifyRootVolumeRequirement();
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#identifyShellKeyRequirement()
	 */
	@Override
	public Requirement identifyShellKeyRequirement() throws CloudException, InternalException {
		return inner.identifyShellKeyRequirement();
	}

	/**
	 * @param platform
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#identifyShellKeyRequirement(org.dasein.cloud.compute.Platform)
	 */
	@Override
	public Requirement identifyShellKeyRequirement(Platform platform) throws CloudException, InternalException {
		return inner.identifyShellKeyRequirement(platform);
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#identifyStaticIPRequirement()
	 */
	@Override
	public Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
		return inner.identifyStaticIPRequirement();
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#identifyVlanRequirement()
	 */
	@Override
	public Requirement identifyVlanRequirement() throws CloudException, InternalException {
		return inner.identifyVlanRequirement();
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#isAPITerminationPreventable()
	 */
	@Override
	public boolean isAPITerminationPreventable() throws CloudException, InternalException {
		return inner.isAPITerminationPreventable();
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#isBasicAnalyticsSupported()
	 */
	@Override
	public boolean isBasicAnalyticsSupported() throws CloudException, InternalException {
		return inner.isBasicAnalyticsSupported();
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#isExtendedAnalyticsSupported()
	 */
	@Override
	public boolean isExtendedAnalyticsSupported() throws CloudException, InternalException {
		return inner.isExtendedAnalyticsSupported();
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#isSubscribed()
	 */
	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return inner.isSubscribed();
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#isUserDataSupported()
	 */
	@Override
	public boolean isUserDataSupported() throws CloudException, InternalException {
		return inner.isUserDataSupported();
	}

	/**
	 * @param withLaunchOptions
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#launch(org.dasein.cloud.compute.VMLaunchOptions)
	 */
	@Override
	public VirtualMachine launch(VMLaunchOptions withLaunchOptions) throws CloudException, InternalException {
		return inner.launch(withLaunchOptions);
	}

	/**
	 * @param imageId
	 * @param product
	 * @param inZoneId
	 * @param name
	 * @param description
	 * @param keypair
	 * @param inVlanId
	 * @param withMonitoring
	 * @param asSandbox
	 * @param firewallIds
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#launch(java.lang.String,
	 *      org.dasein.cloud.compute.VirtualMachineProduct, java.lang.String,
	 *      java.lang.String, java.lang.String, java.lang.String,
	 *      java.lang.String, boolean, boolean, java.lang.String[])
	 */
	@Override
	public VirtualMachine launch(String imageId, VirtualMachineProduct product, String inZoneId, String name,
			String description, String keypair, String inVlanId, boolean withMonitoring, boolean asSandbox,
			String... firewallIds) throws InternalException, CloudException {
		return inner.launch(imageId, product, inZoneId, name, description, keypair, inVlanId, withMonitoring, asSandbox,
				firewallIds);
	}

	/**
	 * @param imageId
	 * @param product
	 * @param inZoneId
	 * @param name
	 * @param description
	 * @param keypair
	 * @param inVlanId
	 * @param withMonitoring
	 * @param asSandbox
	 * @param firewallIds
	 * @param tags
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#launch(java.lang.String,
	 *      org.dasein.cloud.compute.VirtualMachineProduct, java.lang.String,
	 *      java.lang.String, java.lang.String, java.lang.String,
	 *      java.lang.String, boolean, boolean, java.lang.String[],
	 *      org.dasein.cloud.Tag[])
	 */
	@Override
	public VirtualMachine launch(String imageId, VirtualMachineProduct product, String inZoneId, String name,
			String description, String keypair, String inVlanId, boolean withMonitoring, boolean asSandbox,
			String[] firewallIds, Tag... tags) throws InternalException, CloudException {
		return inner.launch(imageId, product, inZoneId, name, description, keypair, inVlanId, withMonitoring, asSandbox,
				firewallIds, tags);
	}

	/**
	 * @param vmId
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listFirewalls(java.lang.String)
	 */
	@Override
	public Iterable<String> listFirewalls(String vmId) throws InternalException, CloudException {
		return inner.listFirewalls(vmId);
	}

	/**
	 * @param architecture
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listProducts(org.dasein.cloud.compute.Architecture)
	 */
	public Iterable<VirtualMachineProduct> listProducts(Architecture architecture) throws InternalException, CloudException {
		return inner.listProducts(architecture);
	}

	/**
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listSupportedArchitectures()
	 */
	@Override
	public Iterable<Architecture> listSupportedArchitectures() throws InternalException, CloudException {
		return inner.listSupportedArchitectures();
	}

	/**
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listVirtualMachineStatus()
	 */
	@Override
	public Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
		return inner.listVirtualMachineStatus();
	}

	/**
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listVirtualMachines()
	 */
	@Override
	public Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
		return inner.listVirtualMachines();
	}

	/**
	 * @param vmId
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#pause(java.lang.String)
	 */
	@Override
	public void pause(String vmId) throws InternalException, CloudException {
		inner.pause(vmId);
	}

	/**
	 * @param vmId
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#reboot(java.lang.String)
	 */
	@Override
	public void reboot(String vmId) throws CloudException, InternalException {
		inner.reboot(vmId);
	}

	/**
	 * @param vmId
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#resume(java.lang.String)
	 */
	@Override
	public void resume(String vmId) throws CloudException, InternalException {
		inner.resume(vmId);
	}

	/**
	 * @param vmId
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#start(java.lang.String)
	 */
	@Override
	public void start(String vmId) throws InternalException, CloudException {
		inner.start(vmId);
	}

	/**
	 * @param vmId
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#stop(java.lang.String)
	 */
	@Override
	public void stop(String vmId) throws InternalException, CloudException {
		inner.stop(vmId);
	}

	/**
	 * @param vmId
	 * @param force
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#stop(java.lang.String,
	 *      boolean)
	 */
	@Override
	public void stop(String vmId, boolean force) throws InternalException, CloudException {
		inner.stop(vmId, force);
	}

	/**
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#supportsAnalytics()
	 */
	@Override
	public boolean supportsAnalytics() throws CloudException, InternalException {
		return inner.supportsAnalytics();
	}

	/**
	 * @param vm
	 * @return
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#supportsPauseUnpause(org.dasein.cloud.compute.VirtualMachine)
	 */
	@Override
	public boolean supportsPauseUnpause(VirtualMachine vm) {
		return inner.supportsPauseUnpause(vm);
	}

	/**
	 * @param vm
	 * @return
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#supportsStartStop(org.dasein.cloud.compute.VirtualMachine)
	 */
	@Override
	public boolean supportsStartStop(VirtualMachine vm) {
		return inner.supportsStartStop(vm);
	}

	/**
	 * @param vm
	 * @return
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#supportsSuspendResume(org.dasein.cloud.compute.VirtualMachine)
	 */
	@Override
	public boolean supportsSuspendResume(VirtualMachine vm) {
		return inner.supportsSuspendResume(vm);
	}

	/**
	 * @param vmId
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#suspend(java.lang.String)
	 */
	@Override
	public void suspend(String vmId) throws CloudException, InternalException {
		inner.suspend(vmId);
	}

	/**
	 * @param vmId
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#terminate(java.lang.String)
	 */
	@Override
	public void terminate(String vmId) throws InternalException, CloudException {
		inner.terminate(vmId);
	}

	/**
	 * @param vmId
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#unpause(java.lang.String)
	 */
	@Override
	public void unpause(String vmId) throws CloudException, InternalException {
		inner.unpause(vmId);
	}

	/**
	 * @param vmId
	 * @param tags
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#updateTags(java.lang.String,
	 *      org.dasein.cloud.Tag[])
	 */
	@Override
	public void updateTags(String vmId, Tag... tags) throws CloudException, InternalException {
		inner.updateTags(vmId, tags);
	}

	/**
	 * @param action
	 * @return
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#mapServiceAction(org.dasein.cloud.identity.ServiceAction)
	 */
	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return inner.mapServiceAction(action);
	}

	/**
	 * @param vmId
	 * @param firewalls
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#modifyInstance(java.lang.String,
	 *      java.lang.String[])
	 */
	@Override
	public VirtualMachine modifyInstance(String vmId, String[] firewalls) throws InternalException, CloudException {
		return inner.modifyInstance(vmId, firewalls);
	}

	/**
	 * @param virtualMachineId
	 * @param productId
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#alterVirtualMachineProduct(java.lang.String,
	 *      java.lang.String)
	 */
	@Override
	public VirtualMachine alterVirtualMachineProduct(String virtualMachineId, String productId) throws InternalException,
			CloudException {
		return inner.alterVirtualMachineProduct(virtualMachineId, productId);
	}

	/**
	 * @param virtualMachineId
	 * @param cpuCount
	 * @param ramInMB
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#alterVirtualMachineSize(java.lang.String,
	 *      java.lang.String, java.lang.String)
	 */
	@Override
	public VirtualMachine alterVirtualMachineSize(String virtualMachineId, String cpuCount, String ramInMB)
			throws InternalException, CloudException {
		return inner.alterVirtualMachineSize(virtualMachineId, cpuCount, ramInMB);
	}

	/**
	 * @param virtualMachineId
	 * @param firewalls
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#alterVirtualMachineFirewalls(java.lang.String,
	 *      java.lang.String[])
	 */
	@Override
	public VirtualMachine alterVirtualMachineFirewalls(String virtualMachineId, String[] firewalls)
			throws InternalException, CloudException {
		return inner.alterVirtualMachineFirewalls(virtualMachineId, firewalls);
	}

	/**
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#cancelSpotDataFeedSubscription()
	 */
	@Override
	public void cancelSpotDataFeedSubscription() throws CloudException, InternalException {
		inner.cancelSpotDataFeedSubscription();
	}

	/**
	 * @param providerSpotVirtualMachineRequestID
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#cancelSpotVirtualMachineRequest(java.lang.String)
	 */
	@Override
	public void cancelSpotVirtualMachineRequest(String providerSpotVirtualMachineRequestID) throws CloudException,
			InternalException {
		inner.cancelSpotVirtualMachineRequest(providerSpotVirtualMachineRequestID);
	}

	/**
	 * @param options
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#createSpotVirtualMachineRequest(org.dasein.cloud.compute.SpotVirtualMachineRequestCreateOptions)
	 */
	@Override
	public SpotVirtualMachineRequest createSpotVirtualMachineRequest(SpotVirtualMachineRequestCreateOptions options)
			throws CloudException, InternalException {
		return inner.createSpotVirtualMachineRequest(options);
	}

	/**
	 * @param bucketName
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#enableSpotDataFeedSubscription(java.lang.String)
	 */
	@Override
	public void enableSpotDataFeedSubscription(String bucketName) throws CloudException, InternalException {
		inner.enableSpotDataFeedSubscription(bucketName);
	}

	/**
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getCapabilities()
	 */
	@Override
	public SkyportVirtualMachineCapabilities getCapabilities() throws InternalException, CloudException {
		return null;
	}

	/**
	 * @param vmId
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getPassword(java.lang.String)
	 */
	@Override
	public String getPassword(String vmId) throws InternalException, CloudException {
		return inner.getPassword(vmId);
	}

	/**
	 * @param vmId
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getUserData(java.lang.String)
	 */
	@Override
	public String getUserData(String vmId) throws InternalException, CloudException {
		return inner.getUserData(vmId);
	}

	/**
	 * @param vmIds
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getVMStatus(java.lang.String[])
	 */
	@Override
	public Iterable<VirtualMachineStatus> getVMStatus(String... vmIds) throws InternalException, CloudException {
		return inner.getVMStatus(vmIds);
	}

	/**
	 * @param filterOptions
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#getVMStatus(org.dasein.cloud.compute.VmStatusFilterOptions)
	 */
	@Override
	public Iterable<VirtualMachineStatus> getVMStatus(VmStatusFilterOptions filterOptions) throws InternalException,
			CloudException {
		return inner.getVMStatus(filterOptions);
	}

	/**
	 * @param withLaunchOptions
	 * @param count
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#launchMany(org.dasein.cloud.compute.VMLaunchOptions,
	 *      int)
	 */
	@Override
	public Iterable<String> launchMany(VMLaunchOptions withLaunchOptions, int count) throws CloudException,
			InternalException {
		return inner.launchMany(withLaunchOptions, count);
	}

	/**
	 * @param machineImageId
	 * @param options
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listProducts(java.lang.String,
	 *      org.dasein.cloud.compute.VirtualMachineProductFilterOptions)
	 */
	@Override
	public Iterable<VirtualMachineProduct> listProducts(String machineImageId, VirtualMachineProductFilterOptions options)
			throws InternalException, CloudException {
		return inner.listProducts(machineImageId, options);
	}

	/**
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listAllProducts()
	 */
	@Override
	public Iterable<VirtualMachineProduct> listAllProducts() throws InternalException, CloudException {
		return inner.listAllProducts();
	}

	/**
	 * @param options
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listSpotPriceHistories(org.dasein.cloud.compute.SpotPriceHistoryFilterOptions)
	 */
	@Override
	public Iterable<SpotPriceHistory> listSpotPriceHistories(SpotPriceHistoryFilterOptions options) throws CloudException,
			InternalException {
		return inner.listSpotPriceHistories(options);
	}

	/**
	 * @param options
	 * @return
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listSpotVirtualMachineRequests(org.dasein.cloud.compute.SpotVirtualMachineRequestFilterOptions)
	 */
	@Override
	public Iterable<SpotVirtualMachineRequest> listSpotVirtualMachineRequests(SpotVirtualMachineRequestFilterOptions options)
			throws CloudException, InternalException {
		return inner.listSpotVirtualMachineRequests(options);
	}

	/**
	 * @param options
	 * @return
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#listVirtualMachines(org.dasein.cloud.compute.VMFilterOptions)
	 */
	@Override
	public Iterable<VirtualMachine> listVirtualMachines(VMFilterOptions options) throws InternalException, CloudException {
		return inner.listVirtualMachines(options);
	}

	/**
	 * @param vmId
	 * @param explanation
	 * @throws InternalException
	 * @throws CloudException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#terminate(java.lang.String,
	 *      java.lang.String)
	 */
	@Override
	public void terminate(String vmId, String explanation) throws InternalException, CloudException {
		inner.terminate(vmId, explanation);
	}

	/**
	 * @param vmIds
	 * @param tags
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#updateTags(java.lang.String[],
	 *      org.dasein.cloud.Tag[])
	 */
	@Override
	public void updateTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
		inner.updateTags(vmIds, tags);
	}

	/**
	 * @param vmId
	 * @param tags
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#setTags(java.lang.String,
	 *      org.dasein.cloud.Tag[])
	 */
	@Override
	public void setTags(String vmId, Tag... tags) throws CloudException, InternalException {
		inner.setTags(vmId, tags);
	}

	/**
	 * @param vmIds
	 * @param tags
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#setTags(java.lang.String[],
	 *      org.dasein.cloud.Tag[])
	 */
	@Override
	public void setTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
		inner.setTags(vmIds, tags);
	}

	/**
	 * @param vmId
	 * @param tags
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#removeTags(java.lang.String,
	 *      org.dasein.cloud.Tag[])
	 */
	@Override
	public void removeTags(String vmId, Tag... tags) throws CloudException, InternalException {
		inner.removeTags(vmId, tags);
	}

	/**
	 * @param vmIds
	 * @param tags
	 * @throws CloudException
	 * @throws InternalException
	 * @see org.dasein.cloud.mock.compute.vm.MockVMSupport#removeTags(java.lang.String[],
	 *      org.dasein.cloud.Tag[])
	 */
	@Override
	public void removeTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
		inner.removeTags(vmIds, tags);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.infinities.skyport.compute.SkyportVirtualMachineSupport#
	 * getNovaStyleVirtualMachine(java.lang.String)
	 */
	@Override
	public NovaStyleVirtualMachine getNovaStyleVirtualMachine(String vmId) throws InternalException, CloudException {
		for (NovaStyleVirtualMachine vm : listNovaStyleVirtualMachines()) {
			if (vmId.equals(vm.getProviderVirtualMachineId())) {
				return vm;
			}
		}
		return null;
	}

}
