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

package org.dasein.cloud.mock.compute.vm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nonnegative;
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
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.MachineImageSupport;
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
import org.dasein.cloud.compute.VirtualMachineCapabilities;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VirtualMachineProductFilterOptions;
import org.dasein.cloud.compute.VirtualMachineStatus;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VmStatistics;
import org.dasein.cloud.compute.VmStatusFilterOptions;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.mock.network.firewall.MockFirewallSupport;
import org.dasein.cloud.mock.network.ip.MockIPSupport;
import org.dasein.cloud.network.NetworkServices;
import org.dasein.cloud.network.RawAddress;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANSupport;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;

/**
 * Implements mock virtual machine features.
 * <p>
 * Created by George Reese: 10/17/12 6:13 PM
 * </p>
 * 
 * @author George Reese
 * @version 2012.09
 * @since 2012.09
 */
public class MockVMSupport implements VirtualMachineSupport {

	static private class MockVM {

		public String vmId;
		public String name;
		public String description;
		public VmState currentState;
		public long created;
		public String imageId;
		public long lastBoot;
		public long lastPaused;
		public long lastTouched;
		public Platform platform;
		public String owner;
		public String privateIpAddress;
		public String publicIpAddress;
		public String productId;
		public String vlanId;
		public String subnetId;
		public String rootUser;
		public String rootPassword;
		// public String shellKey;
	}


	static private final HashMap<String, Map<String, Map<String, Collection<MockVM>>>> mockList =
			new HashMap<String, Map<String, Map<String, Collection<MockVM>>>>();
	static private Thread monitor;
	static private long nextId = 1;
	static private int quad1 = 10;
	static private int quad2 = 0;
	static private int quad3 = 0;
	static private int quad4 = 0;


	static public String[] getNextIpPair() throws CloudException {
		synchronized (mockList) {
			quad4++;
			if (quad4 > 253) {
				quad4 = 1;
				quad3++;
				if (quad3 > 253) {
					quad3 = 0;
					if (quad1 == 10) {
						quad2++;
						if (quad2 > 253) {
							quad1 = 192;
							quad2 = 168;
						}
					} else if (quad1 == 192) {
						quad1 = 172;
						quad2 = 16;
					} else if (quad1 == 172) {
						quad2++;
						if (quad2 > 31) {
							throw new CloudException("Unable to allocate an IP address");
						}
					}
				}
			}
			return new String[] { quad1 + "." + quad2 + "." + quad3 + "." + quad4,
					(15 + quad1) + "." + quad2 + "." + quad3 + "." + quad4 };
		}
	}

	static private @Nonnull String getNextId(@Nonnull String regionId) {
		synchronized (mockList) {
			return (regionId + "-" + (nextId++));
		}
	}

	@SuppressWarnings("deprecation")
	static private @Nullable MockVM getMockVM(@Nonnull ProviderContext ctx, @Nonnull String vmId) {
		String endpoint = ctx.getEndpoint();
		String regionId = ctx.getRegionId();

		synchronized (mockList) {
			Map<String, Map<String, Collection<MockVM>>> cloud = mockList.get(endpoint);

			if (cloud != null) {
				Map<String, Collection<MockVM>> region = cloud.get(regionId);

				if (region != null) {
					for (Map.Entry<String, Collection<MockVM>> entry : region.entrySet()) {
						for (MockVM vm : entry.getValue()) {
							if (vm.vmId.equals(vmId)) {
								return vm;
							}
						}
					}
				}
			}
		}
		return null;
	}

	static private void checkMonitor() {
		synchronized (mockList) {
			if (monitor == null) {
				monitor = new Thread() {

					@Override
					public void run() {
						monitor();
					}
				};
				monitor.setDaemon(true);
				monitor.setName("VM Monitor");
				monitor.start();
			}
		}
	}


	static private final Random random = new Random();


	@SuppressWarnings("incomplete-switch")
	static private void monitor() {
		// noinspection InfiniteLoopStatement
		while (true) {
			try {
				Thread.sleep(15000L);
			} catch (InterruptedException ignore) {
			}
			synchronized (mockList) {
				for (Map.Entry<String, Map<String, Map<String, Collection<MockVM>>>> clouds : mockList.entrySet()) {
					for (Map.Entry<String, Map<String, Collection<MockVM>>> regions : clouds.getValue().entrySet()) {
						for (Map.Entry<String, Collection<MockVM>> dcs : regions.getValue().entrySet()) {
							for (MockVM vm : dcs.getValue()) {
								switch (vm.currentState) {
								case RUNNING:
									if (random.nextInt(5760) == 5) {
										// whoops, crashed
										vm.currentState = VmState.TERMINATED;
										MockFirewallSupport.vmTerminated(vm.vmId);
									}
									break;
								case PENDING:
									if (random.nextInt(10) == 5) {
										vm.currentState = VmState.RUNNING;
									}
									break;
								case PAUSING:
									if (random.nextInt(10) == 5) {
										vm.currentState = VmState.PAUSED;
									}
									break;
								case SUSPENDING:
									if (random.nextInt(10) == 5) {
										vm.currentState = VmState.SUSPENDED;
									}
									break;
								case STOPPING:
									if (random.nextInt(10) == 5) {
										vm.currentState = VmState.STOPPED;
									}
									break;
								case REBOOTING:
									if (random.nextInt(10) == 5) {
										vm.currentState = VmState.PENDING;
									}
									break;
								}
							}
						}
					}
				}
			}
		}
	}


	private CloudProvider provider;


	public MockVMSupport(CloudProvider provider) {
		this.provider = provider;
		checkMonitor();
	}

	@Override
	public VirtualMachine alterVirtualMachine(@Nonnull String vmId, @Nonnull VMScalingOptions options)
			throws InternalException, CloudException {
		throw new OperationNotSupportedException("No alter currently supported");
	}

	@Override
	public @Nonnull VirtualMachine clone(@Nonnull String vmId, @Nonnull String intoDcId, @Nonnull String name,
			@Nonnull String description, boolean powerOn, @Nullable String... firewallIds) throws InternalException,
			CloudException {
		throw new OperationNotSupportedException("No ability to clone in the mock cloud");
	}

	@Override
	public VMScalingCapabilities describeVerticalScalingCapabilities() throws CloudException, InternalException {
		return null;
	}

	@Override
	public void disableAnalytics(String vmId) throws InternalException, CloudException {
		// NO-OP
	}

	@Override
	public void enableAnalytics(String vmId) throws InternalException, CloudException {
		// NO-OP
	}

	@Override
	public @Nonnull String getConsoleOutput(@Nonnull String vmId) throws InternalException, CloudException {
		return "";
	}

	@Override
	public int getCostFactor(@Nonnull VmState state) throws InternalException, CloudException {
		return 100;
	}

	@Override
	public int getMaximumVirtualMachineCount() throws CloudException, InternalException {
		return 100;
	}

	@Override
	public @Nullable VirtualMachineProduct getProduct(@Nonnull String productId) throws InternalException, CloudException {
		for (Architecture architecture : listSupportedArchitectures()) {
			for (VirtualMachineProduct prd : listProducts(architecture)) {
				if (productId.equals(prd.getProviderProductId())) {
					return prd;
				}
			}
		}
		return null;
	}

	@Override
	public @Nonnull String getProviderTermForServer(@Nonnull Locale locale) {
		return "virtual machine";
	}

	@Override
	public @Nullable VirtualMachine getVirtualMachine(@Nonnull String vmId) throws InternalException, CloudException {
		for (VirtualMachine vm : listVirtualMachines()) {
			if (vmId.equals(vm.getProviderVirtualMachineId())) {
				return vm;
			}
		}
		return null;
	}

	@Override
	public VmStatistics getVMStatistics(String vmId, long from, long to) throws InternalException, CloudException {
		return null;
	}

	@Override
	public @Nonnull Iterable<VmStatistics> getVMStatisticsForPeriod(@Nonnull String vmId, @Nonnegative long from,
			@Nonnegative long to) throws InternalException, CloudException {
		return Collections.emptyList();
	}

	@Override
	public @Nonnull Requirement identifyImageRequirement(@Nonnull ImageClass cls) throws CloudException, InternalException {
		return (cls.equals(ImageClass.MACHINE) ? Requirement.REQUIRED : Requirement.NONE);
	}

	@Override
	public @Nonnull Requirement identifyPasswordRequirement() throws CloudException, InternalException {
		return Requirement.OPTIONAL;
	}

	@Override
	public @Nonnull Requirement identifyPasswordRequirement(Platform platform) throws CloudException, InternalException {
		return (platform.isWindows() ? Requirement.REQUIRED : Requirement.OPTIONAL);
	}

	@Override
	public @Nonnull Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public @Nonnull Requirement identifyShellKeyRequirement() throws CloudException, InternalException {
		return Requirement.OPTIONAL;
	}

	@Override
	public @Nonnull Requirement identifyShellKeyRequirement(Platform platform) throws CloudException, InternalException {
		return (platform.isWindows() ? Requirement.NONE : Requirement.OPTIONAL);
	}

	@Override
	public @Nonnull Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public @Nonnull Requirement identifyVlanRequirement() throws CloudException, InternalException {
		NetworkServices network = provider.getNetworkServices();

		if (network == null) {
			return Requirement.NONE;
		}
		if (network.hasVlanSupport()) {
			return Requirement.REQUIRED;
		}
		return Requirement.NONE;
	}

	@Override
	public boolean isAPITerminationPreventable() throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean isBasicAnalyticsSupported() throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean isExtendedAnalyticsSupported() throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isUserDataSupported() throws CloudException, InternalException {
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nonnull VirtualMachine launch(@Nonnull VMLaunchOptions withLaunchOptions) throws CloudException,
			InternalException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was provided for this request");
		}
		String regionId = ctx.getRegionId();

		if (regionId == null) {
			throw new CloudException("No region was provided for this request");
		}
		String dcId = withLaunchOptions.getDataCenterId();

		if (dcId == null) {
			for (DataCenter dc : provider.getDataCenterServices().listDataCenters(regionId)) {
				if (dc.isActive() && dc.isAvailable()) {
					dcId = dc.getProviderDataCenterId();
					break;
				}
			}
		}
		if (dcId == null) {
			throw new CloudException("Unable to identify an available data center into which a VM may be launched");
		}
		MockVM newVm = new MockVM();

		newVm.vmId = getNextId(regionId);
		newVm.name = withLaunchOptions.getHostName();
		newVm.description = withLaunchOptions.getDescription();
		newVm.lastBoot = -1L;
		newVm.lastPaused = -1L;
		newVm.lastTouched = System.currentTimeMillis();
		newVm.created = newVm.lastTouched;
		newVm.owner = ctx.getAccountNumber();
		newVm.currentState = VmState.PENDING;

		if (!Requirement.NONE.equals(identifyPasswordRequirement())) {
			if (Requirement.REQUIRED.equals(identifyPasswordRequirement())) {
				if (newVm.rootUser == null || newVm.rootPassword == null) {
					throw new CloudException("No user or password was provided for bootstrapping your VM");
				}
			}
			newVm.rootUser = withLaunchOptions.getBootstrapUser();
			newVm.rootPassword = withLaunchOptions.getBootstrapPassword();
		}
		if (!Requirement.NONE.equals(identifyShellKeyRequirement())) {
			if (Requirement.REQUIRED.equals(identifyShellKeyRequirement())) {
				throw new CloudException("No shell key was provided for bootstrapping your VM");
			}
			// newVm.shellKey = withLaunchOptions.getBootstrapKey();
		}

		VirtualMachineProduct prd = getProduct(withLaunchOptions.getStandardProductId());

		if (prd == null) {
			throw new CloudException("No such VM product: " + withLaunchOptions.getStandardProductId());
		}
		newVm.productId = prd.getProviderProductId();

		String vlanId = withLaunchOptions.getVlanId();

		if (vlanId != null) {
			NetworkServices network = provider.getNetworkServices();

			if (network == null) {
				throw new CloudException("This cloud does not support network services");
			}
			VLANSupport support = network.getVlanSupport();

			if (support == null) {
				throw new CloudException("This cloud does not support VLANs");
			}
			if (support.getSubnetSupport().equals(Requirement.NONE)) {
				VLAN vlan = support.getVlan(vlanId);

				if (vlan == null) {
					throw new CloudException("No such VLAN: " + vlanId);
				}
				newVm.vlanId = vlanId;
			} else if (support.getSubnetSupport().equals(Requirement.REQUIRED)) {
				Subnet subnet = support.getSubnet(vlanId);

				if (subnet == null) {
					throw new CloudException("No such subnet: " + vlanId);
				}
				newVm.subnetId = vlanId;
				newVm.vlanId = subnet.getProviderVlanId();
			} else {
				Subnet subnet = support.getSubnet(vlanId);
				VLAN vlan;

				if (subnet == null) {
					vlan = support.getVlan(vlanId);
					if (vlan == null) {
						throw new CloudException("No such VLAN or subnet: " + vlanId);
					}
					newVm.vlanId = vlanId;
				} else {
					newVm.subnetId = vlanId;
					newVm.vlanId = subnet.getProviderVlanId();
				}
			}
		} else {
			String[] ips = getNextIpPair();

			newVm.privateIpAddress = ips[0];
			newVm.publicIpAddress = ips[1];
		}
		String[] firewalls = withLaunchOptions.getFirewallIds();

		if (firewalls.length > 0) {
			MockFirewallSupport.saveFirewallsForVM(provider, newVm.vmId, firewalls);
		}

		String imageId = withLaunchOptions.getMachineImageId();

		ComputeServices services = provider.getComputeServices();
		MachineImageSupport support = services.getImageSupport();
		MachineImage image = support.getImage(imageId);

		if (image == null) {
			throw new CloudException("No such machine image: " + imageId);
		}
		if (!image.getCurrentState().equals(MachineImageState.ACTIVE)) {
			throw new CloudException("Machine image " + imageId + " is not active");
		}
		newVm.imageId = imageId;
		newVm.platform = image.getPlatform();

		synchronized (mockList) {
			Map<String, Map<String, Collection<MockVM>>> cloud = mockList.get(ctx.getEndpoint());

			if (cloud == null) {
				cloud = new HashMap<String, Map<String, Collection<MockVM>>>();
				mockList.put(ctx.getEndpoint(), cloud);
			}
			Map<String, Collection<MockVM>> region = cloud.get(regionId);

			if (region == null) {
				region = new HashMap<String, Collection<MockVM>>();
				cloud.put(regionId, region);
			}
			Collection<MockVM> list = region.get(dcId);

			if (list == null) {
				list = new ArrayList<MockVM>();
				region.put(dcId, list);
			}
			list.add(newVm);
		}
		VirtualMachine vm = toVM(dcId, newVm);

		if (vm == null) {
			throw new CloudException("VM launch failed without comment");
		}
		return vm;
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nonnull VirtualMachine launch(@Nonnull String imageId, @Nonnull VirtualMachineProduct product,
			@Nullable String inZoneId, @Nonnull String name, @Nonnull String description, @Nullable String keypair,
			@Nullable String inVlanId, boolean withMonitoring, boolean asSandbox, @Nullable String... firewallIds)
			throws InternalException, CloudException {
		VMLaunchOptions cfg = VMLaunchOptions.getInstance(product.getProviderProductId(), imageId, name, description);

		if (keypair != null) {
			cfg.withBoostrapKey(keypair);
		}
		if (inVlanId != null) {
			if (inZoneId == null) {
				NetworkServices svc = provider.getNetworkServices();

				if (svc != null) {
					VLANSupport support = svc.getVlanSupport();

					if (support != null) {
						Subnet subnet = support.getSubnet(inVlanId);

						if (subnet == null) {
							throw new CloudException("No such VPC subnet: " + inVlanId);
						}
						inZoneId = subnet.getProviderDataCenterId();
					}
				}
			}
			if (inZoneId == null) {
				throw new CloudException("Unable to match zone to subnet");
			}
			cfg.inVlan(null, inZoneId, inVlanId);
		} else if (inZoneId != null) {
			cfg.inDataCenter(inZoneId);
		}
		if (withMonitoring) {
			cfg.withExtendedAnalytics();
		}
		if (firewallIds != null && firewallIds.length > 0) {
			cfg.behindFirewalls(firewallIds);
		}
		return launch(cfg);
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nonnull VirtualMachine launch(@Nonnull String imageId, @Nonnull VirtualMachineProduct product,
			@Nullable String inZoneId, @Nonnull String name, @Nonnull String description, @Nullable String keypair,
			@Nullable String inVlanId, boolean withMonitoring, boolean asSandbox, @Nullable String[] firewallIds,
			@Nullable Tag... tags) throws InternalException, CloudException {
		VMLaunchOptions cfg = VMLaunchOptions.getInstance(product.getProviderProductId(), imageId, name, description);

		if (keypair != null) {
			cfg.withBoostrapKey(keypair);
		}
		if (inVlanId != null) {
			if (inZoneId == null) {
				NetworkServices svc = provider.getNetworkServices();

				if (svc != null) {
					VLANSupport support = svc.getVlanSupport();

					if (support != null) {
						Subnet subnet = support.getSubnet(inVlanId);

						if (subnet == null) {
							throw new CloudException("No such VPC subnet: " + inVlanId);
						}
						inZoneId = subnet.getProviderDataCenterId();
					}
				}
			}
			if (inZoneId == null) {
				throw new CloudException("Unable to match zone to subnet");
			}
			cfg.inVlan(null, inZoneId, inVlanId);
		} else if (inZoneId != null) {
			cfg.inDataCenter(inZoneId);
		}
		if (withMonitoring) {
			cfg.withExtendedAnalytics();
		}
		if (firewallIds != null && firewallIds.length > 0) {
			cfg.behindFirewalls(firewallIds);
		}
		return launch(cfg);
	}

	@Override
	public @Nonnull Iterable<String> listFirewalls(@Nonnull String vmId) throws InternalException, CloudException {
		VirtualMachine vm = getVirtualMachine(vmId);

		if (vm == null) {
			throw new CloudException("No such VM: " + vmId);
		}
		if (vm.getCurrentState().equals(VmState.TERMINATED)) {
			return Collections.emptyList();
		}
		return MockFirewallSupport.getFirewallsForVM(vmId);
	}


	private transient Collection<VirtualMachineProduct> products;


	public @Nonnull Iterable<VirtualMachineProduct> listProducts(Architecture architecture) throws InternalException,
			CloudException {
		if (!architecture.equals(Architecture.I64)) {
			return Collections.emptyList();
		}
		if (products == null) {
			ArrayList<VirtualMachineProduct> list = new ArrayList<VirtualMachineProduct>();

			VirtualMachineProduct p;

			p = new VirtualMachineProduct();
			p.setDescription("1 GB RAM/1 CPU/20 GB Disk");
			p.setName("Small");
			p.setCpuCount(1);
			p.setProviderProductId("small");
			p.setRamSize(new Storage<Gigabyte>(1, Storage.GIGABYTE));
			p.setRootVolumeSize(new Storage<Gigabyte>(20, Storage.GIGABYTE));
			p.setStandardHourlyRate(0.10f);
			list.add(p);

			p = new VirtualMachineProduct();
			p.setDescription("4 GB RAM/2 CPU/40 GB Disk");
			p.setName("Medium");
			p.setCpuCount(2);
			p.setProviderProductId("medium");
			p.setRamSize(new Storage<Gigabyte>(4, Storage.GIGABYTE));
			p.setRootVolumeSize(new Storage<Gigabyte>(40, Storage.GIGABYTE));
			p.setStandardHourlyRate(0.15f);
			list.add(p);

			p = new VirtualMachineProduct();
			p.setDescription("8 GB RAM/4 CPU/80 GB Disk");
			p.setName("Large");
			p.setCpuCount(4);
			p.setProviderProductId("large");
			p.setRamSize(new Storage<Gigabyte>(8, Storage.GIGABYTE));
			p.setRootVolumeSize(new Storage<Gigabyte>(80, Storage.GIGABYTE));
			p.setStandardHourlyRate(0.25f);
			list.add(p);

			products = Collections.unmodifiableList(list);
		}
		return products;
	}

	@Override
	public @Nonnull Iterable<Architecture> listSupportedArchitectures() throws InternalException, CloudException {
		return Collections.singletonList(Architecture.I64);
	}

	@Override
	public @Nonnull Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
		ArrayList<ResourceStatus> status = new ArrayList<ResourceStatus>();

		for (VirtualMachine vm : listVirtualMachines()) {
			status.add(new ResourceStatus(vm.getProviderVirtualMachineId(), vm.getCurrentState()));
		}
		return status;
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nonnull Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was set for this request");
		}
		String endpoint = ctx.getEndpoint();
		String regionId = ctx.getRegionId();

		synchronized (mockList) {
			if (!mockList.containsKey(endpoint)) {
				return Collections.emptyList();
			}
			Map<String, Map<String, Collection<MockVM>>> list = mockList.get(endpoint);

			if (!list.containsKey(regionId)) {
				return Collections.emptyList();
			}
			Map<String, Collection<MockVM>> vms = list.get(regionId);
			ArrayList<VirtualMachine> matches = new ArrayList<VirtualMachine>();

			for (String dc : vms.keySet()) {
				Collection<MockVM> c = vms.get(dc);

				if (c != null) {
					ArrayList<MockVM> replacement = new ArrayList<MockVM>();

					for (MockVM mock : c) {
						VirtualMachine vm = toVM(dc, mock);

						if (vm != null) {
							matches.add(vm);
						}
						if (!mock.currentState.equals(VmState.TERMINATED)
								|| ((System.currentTimeMillis() - mock.lastTouched) < (CalendarWrapper.MINUTE * 10L))) {
							replacement.add(mock);
						}
					}
					vms.put(dc, replacement);
				}
			}
			return matches;
		}
	}

	@Override
	public void pause(@Nonnull String vmId) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was provider for this request");
		}
		synchronized (mockList) {
			MockVM vm = getMockVM(ctx, vmId);

			if (vm == null) {
				throw new CloudException("No such VM: " + vmId);
			}
			if (!vm.currentState.equals(VmState.RUNNING)) {
				throw new CloudException("The virtual machine must be running in order to be paused");
			}
			vm.currentState = VmState.PAUSING;
		}
	}

	@Override
	public void reboot(@Nonnull String vmId) throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was provider for this request");
		}
		synchronized (mockList) {
			MockVM vm = getMockVM(ctx, vmId);

			if (vm == null) {
				throw new CloudException("No such VM: " + vmId);
			}
			if (!vm.currentState.equals(VmState.RUNNING)) {
				throw new CloudException("The virtual machine must be running in order to be rebooted");
			}
			vm.currentState = VmState.REBOOTING;
		}
	}

	@Override
	public void resume(@Nonnull String vmId) throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was provider for this request");
		}
		synchronized (mockList) {
			MockVM vm = getMockVM(ctx, vmId);

			if (vm == null) {
				throw new CloudException("No such VM: " + vmId);
			}
			if (!vm.currentState.equals(VmState.SUSPENDED)) {
				throw new CloudException("The virtual machine must be suspended in order to be resumed");
			}
			vm.currentState = VmState.PENDING;
		}
	}

	@Override
	public void start(@Nonnull String vmId) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was provider for this request");
		}
		synchronized (mockList) {
			MockVM vm = getMockVM(ctx, vmId);

			if (vm == null) {
				throw new CloudException("No such VM: " + vmId);
			}
			if (!vm.currentState.equals(VmState.STOPPED)) {
				throw new CloudException("The virtual machine must be stopped in order to be started");
			}
			vm.currentState = VmState.PENDING;
		}
	}

	@Override
	public void stop(@Nonnull String vmId) throws InternalException, CloudException {
		stop(vmId, false);
	}

	@Override
	public void stop(@Nonnull String vmId, boolean force) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was provider for this request");
		}
		synchronized (mockList) {
			MockVM vm = getMockVM(ctx, vmId);

			if (vm == null) {
				throw new CloudException("No such VM: " + vmId);
			}
			if (!vm.currentState.equals(VmState.RUNNING)) {
				throw new CloudException("The virtual machine must be running in order to be stopped");
			}
			vm.currentState = VmState.STOPPING;
		}
	}

	@Override
	public boolean supportsAnalytics() throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean supportsPauseUnpause(@Nonnull VirtualMachine vm) {
		return true;
	}

	@Override
	public boolean supportsStartStop(@Nonnull VirtualMachine vm) {
		return true;
	}

	@Override
	public boolean supportsSuspendResume(@Nonnull VirtualMachine vm) {
		return true;
	}

	@Override
	public void suspend(@Nonnull String vmId) throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was provider for this request");
		}
		synchronized (mockList) {
			MockVM vm = getMockVM(ctx, vmId);

			if (vm == null) {
				throw new CloudException("No such VM: " + vmId);
			}
			if (!vm.currentState.equals(VmState.RUNNING)) {
				throw new CloudException("The virtual machine must be running in order to be suspended");
			}
			vm.currentState = VmState.SUSPENDING;
		}
	}

	@Override
	public void terminate(@Nonnull String vmId) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was provider for this request");
		}
		String ip;

		synchronized (mockList) {
			MockVM vm = getMockVM(ctx, vmId);

			if (vm == null) {
				throw new CloudException("No such VM: " + vmId);
			}
			ip = MockIPSupport.getIPAddressForVM(vm.vmId);
			if (vm.currentState.equals(VmState.TERMINATED)) {
				throw new CloudException("The virtual machine is already terminated.");
			}
			vm.currentState = VmState.TERMINATED;
			vm.lastTouched = System.currentTimeMillis();
		}
		if (ip != null) {
			// noinspection ConstantConditions
			provider.getNetworkServices().getIpAddressSupport().releaseFromServer(ip);
		}
		MockFirewallSupport.vmTerminated(vmId);
	}

	@Override
	public void unpause(@Nonnull String vmId) throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was provider for this request");
		}
		synchronized (mockList) {
			MockVM vm = getMockVM(ctx, vmId);

			if (vm == null) {
				throw new CloudException("No such VM: " + vmId);
			}
			if (!vm.currentState.equals(VmState.PAUSED)) {
				throw new CloudException("The virtual machine must be paused in order to be unpaused");
			}
			vm.currentState = VmState.PAUSING;
		}
	}

	@Override
	public void updateTags(@Nonnull String vmId, @Nonnull Tag... tags) throws CloudException, InternalException {
		// NO-OP
	}

	@Override
	public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
		return new String[0];
	}

	private @Nullable VirtualMachine toVM(@Nonnull String dcId, @Nullable MockVM mock) throws CloudException {
		if (mock == null) {
			return null;
		}
		if (mock.currentState.equals(VmState.TERMINATED)
				&& ((System.currentTimeMillis() - mock.lastTouched) > (CalendarWrapper.MINUTE * 10L))) {
			return null;
		}
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was set for this request");
		}
		if (!mock.owner.equals(ctx.getAccountNumber())) {
			return null;
		}
		VirtualMachine vm = new VirtualMachine();

		vm.setImagable(true);
		vm.setLastBootTimestamp(mock.lastBoot);
		vm.setLastPauseTimestamp(mock.lastPaused);
		vm.setName(mock.name);
		vm.setPausable(true);
		vm.setPersistent(true);
		vm.setPlatform(mock.platform);
		vm.setPrivateAddresses(new RawAddress(mock.privateIpAddress));
		vm.setProductId(mock.productId);
		vm.setProviderDataCenterId(dcId);
		vm.setProviderMachineImageId(mock.imageId);
		vm.setProviderOwnerId(mock.owner);
		vm.setProviderRegionId(ctx.getRegionId());
		vm.setProviderSubnetId(mock.subnetId);
		vm.setProviderVirtualMachineId(mock.vmId);
		vm.setProviderVlanId(mock.vlanId);
		vm.setPublicAddresses(new RawAddress(mock.publicIpAddress));
		vm.setRebootable(true);
		vm.setRootPassword(mock.rootPassword);
		vm.setRootUser(mock.rootUser);
		vm.setArchitecture(Architecture.I64);
		if (mock.currentState.equals(VmState.TERMINATED)) {
			vm.setTerminationTimestamp(mock.lastTouched);
		}
		vm.setClonable(false);
		vm.setCreationTimestamp(mock.created);
		vm.setCurrentState(mock.currentState);
		vm.setDescription(mock.description);
		vm.setProviderAssignedIpAddressId(MockIPSupport.getIPAddressForVM(mock.vmId));
		if (vm.getProviderAssignedIpAddressId() != null) {
			vm.setPublicAddresses(new RawAddress(vm.getProviderAssignedIpAddressId()));
		}
		return vm;
	}

	@Override
	public VirtualMachine modifyInstance(String vmId, String[] firewalls) throws InternalException, CloudException {
		return null;
	}

	@Override
	public VirtualMachine alterVirtualMachineProduct(String virtualMachineId, String productId) throws InternalException,
			CloudException {
		return null;
	}

	@Override
	public VirtualMachine alterVirtualMachineSize(String virtualMachineId, String cpuCount, String ramInMB)
			throws InternalException, CloudException {
		return null;
	}

	@Override
	public VirtualMachine alterVirtualMachineFirewalls(String virtualMachineId, String[] firewalls)
			throws InternalException, CloudException {
		return null;
	}

	@Override
	public void cancelSpotDataFeedSubscription() throws CloudException, InternalException {

	}

	@Override
	public void cancelSpotVirtualMachineRequest(String providerSpotVirtualMachineRequestID) throws CloudException,
			InternalException {

	}

	@Override
	public SpotVirtualMachineRequest createSpotVirtualMachineRequest(SpotVirtualMachineRequestCreateOptions options)
			throws CloudException, InternalException {
		return null;
	}

	@Override
	public void enableSpotDataFeedSubscription(String bucketName) throws CloudException, InternalException {

	}

	@Override
	public VirtualMachineCapabilities getCapabilities() throws InternalException, CloudException {
		return null;
	}

	@Override
	public String getPassword(String vmId) throws InternalException, CloudException {

		return null;
	}

	@Override
	public String getUserData(String vmId) throws InternalException, CloudException {

		return null;
	}

	@Override
	public Iterable<VirtualMachineStatus> getVMStatus(String... vmIds) throws InternalException, CloudException {

		return null;
	}

	@Override
	public Iterable<VirtualMachineStatus> getVMStatus(VmStatusFilterOptions filterOptions) throws InternalException,
			CloudException {

		return null;
	}

	@Override
	public Iterable<String> launchMany(VMLaunchOptions withLaunchOptions, int count) throws CloudException,
			InternalException {

		return null;
	}

	@Override
	public Iterable<VirtualMachineProduct> listProducts(String machineImageId, VirtualMachineProductFilterOptions options)
			throws InternalException, CloudException {
		return listProducts(options.getArchitecture());
	}

	@Override
	public Iterable<VirtualMachineProduct> listAllProducts() throws InternalException, CloudException {
		List<VirtualMachineProduct> products = new ArrayList<VirtualMachineProduct>();
		for (Architecture architecture : listSupportedArchitectures()) {
			for (VirtualMachineProduct prd : listProducts(architecture)) {
				products.add(prd);
			}
		}
		return products;
	}

	@Override
	public Iterable<SpotPriceHistory> listSpotPriceHistories(SpotPriceHistoryFilterOptions options) throws CloudException,
			InternalException {

		return null;
	}

	@Override
	public Iterable<SpotVirtualMachineRequest>
			listSpotVirtualMachineRequests(SpotVirtualMachineRequestFilterOptions options) throws CloudException,
					InternalException {

		return null;
	}

	@Override
	public Iterable<VirtualMachine> listVirtualMachines(VMFilterOptions options) throws InternalException, CloudException {
		return this.listVirtualMachines();
	}

	@Override
	public void terminate(String vmId, String explanation) throws InternalException, CloudException {
		this.terminate(vmId);
	}

	@Override
	public void updateTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void setTags(String vmId, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void setTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void removeTags(String vmId, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void removeTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {

	}
}
