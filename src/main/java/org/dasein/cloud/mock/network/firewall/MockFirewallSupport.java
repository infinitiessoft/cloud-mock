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

package org.dasein.cloud.mock.network.firewall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

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
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.Direction;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.network.FirewallCapabilities;
import org.dasein.cloud.network.FirewallConstraints;
import org.dasein.cloud.network.FirewallConstraints.Constraint;
import org.dasein.cloud.network.FirewallCreateOptions;
import org.dasein.cloud.network.FirewallRule;
import org.dasein.cloud.network.FirewallRuleCreateOptions;
import org.dasein.cloud.network.FirewallSupport;
import org.dasein.cloud.network.NetworkServices;
import org.dasein.cloud.network.Permission;
import org.dasein.cloud.network.Protocol;
import org.dasein.cloud.network.RuleTarget;
import org.dasein.cloud.network.RuleTargetType;

/**
 * Implements bi-directional mock firewall support.
 * <p>
 * Created by George Reese: 10/19/12 12:30 PM
 * </p>
 * 
 * @author George Reese
 * @version 2012.09 initial version
 * @since 2012.09
 */
public class MockFirewallSupport implements FirewallSupport {

	static private final Map<String, Map<String, Map<String, Collection<Firewall>>>> firewalls =
			new HashMap<String, Map<String, Map<String, Collection<Firewall>>>>();
	static private final Map<String, Collection<FirewallRule>> rules = new HashMap<String, Collection<FirewallRule>>();
	static private final Map<String, Collection<String>> vmMap = new HashMap<String, Collection<String>>();


	static private @Nonnull Firewall copy(@Nonnull Firewall fw) {
		Firewall copy = new Firewall();

		copy.setActive(fw.isActive());
		copy.setAvailable(fw.isAvailable());
		copy.setDescription(fw.getDescription());
		copy.setName(fw.getName());
		copy.setProviderFirewallId(fw.getProviderFirewallId());
		copy.setProviderVlanId(fw.getProviderVlanId());
		copy.setRegionId(fw.getRegionId());
		return copy;
	}

	static public void vmTerminated(@Nonnull String vmId) {
		synchronized (firewalls) {
			vmMap.remove(vmId);
		}
	}

	static public @Nonnull Collection<String> getFirewallsForVM(@Nonnull String vmId) {
		Collection<String> ids;

		synchronized (firewalls) {
			ids = vmMap.get(vmId);
		}
		if (ids == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableCollection(ids);
	}

	static public void saveFirewallsForVM(@Nonnull CloudProvider provider, @Nonnull String vmId,
			@Nonnull String... firewallIds) throws CloudException, InternalException {
		NetworkServices network = provider.getNetworkServices();

		if (network == null) {
			throw new CloudException("No firewall services supported in this cloud");
		}
		FirewallSupport support = network.getFirewallSupport();

		if (support == null) {
			throw new CloudException("No firewall services supported in this cloud");
		}
		if (support instanceof MockFirewallSupport) {
			ArrayList<String> flist = new ArrayList<String>();

			for (String id : firewallIds) {
				Firewall fw = support.getFirewall(id);

				if (fw == null) {
					throw new CloudException("No such firewall: " + id);
				}
				flist.add(id);
			}
			synchronized (firewalls) {
				vmMap.put(vmId, flist);
			}
		}
	}


	/*
	 * static private @Nonnull String toRuleId(@Nonnull FirewallRule rule) {
	 * return (rule.getFirewallId() + "_:_" + rule.getPermission().toString() +
	 * "_:_" + rule.getDirection().toString() + "_:_" +
	 * rule.getPermission().toString() + "_:_" + rule.getSourceEndpoint() +
	 * "_:_" + rule.getDestinationEndpoint() + "_:_" + rule.getStartPort() + ":"
	 * + rule.getEndPort()); }
	 */

	private CloudProvider provider;


	public MockFirewallSupport(CloudProvider provider) {
		this.provider = provider;
	}

	@Override
	public @Nonnull String authorize(@Nonnull String firewallId, @Nonnull String cidr, @Nonnull Protocol protocol,
			int beginPort, int endPort) throws CloudException, InternalException {
		return authorize(firewallId, Direction.INGRESS, cidr, protocol, beginPort, endPort);
	}

	@Override
	public @Nonnull String authorize(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull String cidr,
			@Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
		RuleTarget sourceEndpoint, destinationEndpoint;

		if (direction.equals(Direction.INGRESS)) {
			sourceEndpoint = RuleTarget.getCIDR(cidr);
			destinationEndpoint = RuleTarget.getGlobal(firewallId);
		} else {
			sourceEndpoint = RuleTarget.getGlobal(firewallId);
			destinationEndpoint = RuleTarget.getCIDR(cidr);
		}
		return authorize(firewallId, direction, Permission.ALLOW, sourceEndpoint, protocol, destinationEndpoint, beginPort,
				endPort, 0);
	}

	@Override
	public @Nonnull String authorize(@Nonnull String firewallId, @Nonnull Direction direction,
			@Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort)
			throws CloudException, InternalException {
		RuleTarget sourceEndpoint, destinationEndpoint;

		if (direction.equals(Direction.INGRESS)) {
			sourceEndpoint = RuleTarget.getCIDR(source);
			destinationEndpoint = RuleTarget.getGlobal(firewallId);
		} else {
			sourceEndpoint = RuleTarget.getGlobal(firewallId);
			destinationEndpoint = RuleTarget.getCIDR(source);
		}
		return authorize(firewallId, direction, permission, sourceEndpoint, protocol, destinationEndpoint, beginPort,
				endPort, 0);

	}

	@Override
	public @Nonnull String authorize(@Nonnull String firewallId, @Nonnull Direction direction,
			@Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, @Nonnull RuleTarget target,
			int beginPort, int endPort) throws CloudException, InternalException {
		RuleTarget sourceEndpoint, destinationEndpoint;

		if (direction.equals(Direction.INGRESS)) {
			sourceEndpoint = RuleTarget.getCIDR(source);
			destinationEndpoint = target;
		} else {
			sourceEndpoint = target;
			destinationEndpoint = RuleTarget.getCIDR(source);
		}
		return authorize(firewallId, direction, permission, sourceEndpoint, protocol, destinationEndpoint, beginPort,
				endPort, 0);
	}

	@Override
	public @Nonnull String authorize(@Nonnull String firewallId, @Nonnull Direction direction,
			@Nonnull Permission permission, @Nonnull RuleTarget sourceEndpoint, @Nonnull Protocol protocol,
			@Nonnull RuleTarget destinationEndpoint, int beginPort, int endPort, @Nonnegative int precedence)
			throws CloudException, InternalException {
		if (getFirewall(firewallId) == null) {
			throw new CloudException("No such firewall: " + firewallId);
		}

		FirewallRule rule =
				FirewallRule.getInstance(null, firewallId, sourceEndpoint, direction, protocol, permission,
						destinationEndpoint, beginPort, endPort);

		synchronized (firewalls) {
			Collection<FirewallRule> list = rules.get(firewallId);

			if (list == null) {
				list = new ArrayList<FirewallRule>();
				rules.put(firewallId, list);
			}
			list.add(rule);
		}
		return rule.getProviderRuleId();
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nonnull String create(@Nonnull String name, @Nonnull String description) throws InternalException,
			CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was specified for this request");
		}
		String regionId = ctx.getRegionId();

		if (regionId == null) {
			throw new CloudException("No region was specified for this request");
		}
		Firewall fw = new Firewall();

		fw.setActive(true);
		fw.setAvailable(true);
		fw.setDescription(description);
		fw.setName(name);
		fw.setProviderFirewallId(UUID.randomUUID().toString());
		fw.setRegionId(regionId);
		synchronized (firewalls) {
			Map<String, Map<String, Collection<Firewall>>> cloud = firewalls.get(ctx.getEndpoint());

			if (cloud == null) {
				cloud = new HashMap<String, Map<String, Collection<Firewall>>>();
				firewalls.put(ctx.getEndpoint(), cloud);
			}
			Map<String, Collection<Firewall>> region = cloud.get(regionId);

			if (region == null) {
				region = new HashMap<String, Collection<Firewall>>();
				cloud.put(regionId, region);
			}
			Collection<Firewall> account = region.get(ctx.getAccountNumber());

			if (account == null) {
				account = new ArrayList<Firewall>();
				region.put(ctx.getAccountNumber(), account);
			}
			account.add(fw);
		}
		// noinspection ConstantConditions
		return copy(fw).getProviderFirewallId();
	}

	@Override
	public @Nonnull String createInVLAN(@Nonnull String name, @Nonnull String description, @Nonnull String providerVlanId)
			throws InternalException, CloudException {
		throw new OperationNotSupportedException("VLANs not yet supported");
	}

	@SuppressWarnings("deprecation")
	@Override
	public void delete(@Nonnull String firewallId) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was specified for this request");
		}
		String regionId = ctx.getRegionId();

		if (regionId == null) {
			throw new CloudException("No region was specified for this request");
		}
		synchronized (firewalls) {
			ComputeServices compute = provider.getComputeServices();

			if (compute != null) {
				VirtualMachineSupport vmSupport = compute.getVirtualMachineSupport();

				if (vmSupport != null) {
					for (Map.Entry<String, Collection<String>> entry : vmMap.entrySet()) {
						for (String id : entry.getValue()) {
							if (firewallId.equals(id)) {
								VirtualMachine vm = vmSupport.getVirtualMachine(entry.getKey());

								if (vm != null && !vm.getCurrentState().equals(VmState.TERMINATED)) {
									throw new CloudException("Firewall " + firewallId + " is currently in use");
								}
							}
						}
					}
				}
			}
			Map<String, Map<String, Collection<Firewall>>> cloud = firewalls.get(ctx.getEndpoint());

			if (cloud == null) {
				cloud = new HashMap<String, Map<String, Collection<Firewall>>>();
				firewalls.put(ctx.getEndpoint(), cloud);
			}
			Map<String, Collection<Firewall>> region = cloud.get(regionId);

			if (region == null) {
				region = new HashMap<String, Collection<Firewall>>();
				cloud.put(regionId, region);
			}
			Collection<Firewall> account = region.get(ctx.getAccountNumber());

			if (account == null) {
				return;
			}
			ArrayList<Firewall> replacement = new ArrayList<Firewall>();

			for (Firewall fw : account) {
				if (!firewallId.equals(fw.getProviderFirewallId())) {
					replacement.add(fw);
				}
			}
			region.put(ctx.getAccountNumber(), replacement);
			rules.remove(firewallId);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public Firewall getFirewall(@Nonnull String firewallId) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was set for this request");
		}
		String regionId = ctx.getRegionId();

		if (regionId == null) {
			throw new CloudException("No region was set for this request");
		}
		synchronized (firewalls) {
			Map<String, Map<String, Collection<Firewall>>> cloud = firewalls.get(ctx.getEndpoint());

			if (cloud == null) {
				return null;
			}
			Map<String, Collection<Firewall>> region = cloud.get(regionId);

			if (region == null) {
				return null;
			}
			Collection<Firewall> account = region.get(ctx.getAccountNumber());

			if (account == null) {
				return null;
			}
			for (Firewall fw : account) {
				if (firewallId.equals(fw.getProviderFirewallId())) {
					return fw;
				}
			}
			return null;
		}
	}

	@Override
	public @Nonnull String getProviderTermForFirewall(@Nonnull Locale locale) {
		return "firewall";
	}

	@Override
	public @Nonnull Collection<FirewallRule> getRules(@Nonnull String firewallId) throws InternalException, CloudException {
		Firewall fw = getFirewall(firewallId);

		if (fw == null) {
			throw new CloudException("No such firewall: " + firewallId);
		}
		synchronized (firewalls) {
			Collection<FirewallRule> matches = rules.get(firewallId);

			if (matches == null) {
				return Collections.emptyList();
			}
			return Collections.unmodifiableCollection(matches);
		}
	}

	@Override
	public @Nonnull Requirement identifyPrecedenceRequirement(boolean inVlan) throws InternalException, CloudException {
		return Requirement.NONE;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isZeroPrecedenceHighest() throws InternalException, CloudException {
		return true; // nonsense
	}

	@SuppressWarnings("deprecation")
	@Override
	public @Nonnull Collection<Firewall> list() throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if (ctx == null) {
			throw new CloudException("No context was set for this request");
		}
		String regionId = ctx.getRegionId();

		if (regionId == null) {
			throw new CloudException("No region was set for this request");
		}
		synchronized (firewalls) {
			Map<String, Map<String, Collection<Firewall>>> cloud = firewalls.get(ctx.getEndpoint());

			if (cloud == null) {
				create("default", "Default Firewall");
				cloud = firewalls.get(ctx.getEndpoint());
				if (cloud == null) {
					return Collections.emptyList();
				}
			}
			Map<String, Collection<Firewall>> region = cloud.get(regionId);

			if (region == null) {
				create("default", "Default Firewall");
				region = cloud.get(regionId);
				if (region == null) {
					return Collections.emptyList();
				}
			}
			Collection<Firewall> account = region.get(ctx.getAccountNumber());

			if (account == null || account.isEmpty()) {
				create("default", "Default Firewall");
				account = region.get(ctx.getAccountNumber());
				if (account == null) {
					return Collections.emptyList();
				}
			}
			return Collections.unmodifiableCollection(account);
		}
	}

	@Override
	public @Nonnull Iterable<ResourceStatus> listFirewallStatus() throws InternalException, CloudException {
		ArrayList<ResourceStatus> status = new ArrayList<ResourceStatus>();

		for (Firewall fw : list()) {
			// noinspection ConstantConditions
			status.add(new ResourceStatus(fw.getProviderFirewallId(), true));
		}
		return status;
	}

	@Override
	public @Nonnull Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan) throws InternalException,
			CloudException {
		if (inVlan) {
			return Collections.emptyList();
		}
		return Collections.singletonList(RuleTargetType.GLOBAL);
	}

	@Override
	public @Nonnull Iterable<Direction> listSupportedDirections(boolean inVlan) throws InternalException, CloudException {
		if (inVlan) {
			return Collections.emptyList();
		}
		ArrayList<Direction> directions = new ArrayList<Direction>();

		directions.add(Direction.INGRESS);
		directions.add(Direction.EGRESS);
		return directions;
	}

	@Override
	public @Nonnull Iterable<Permission> listSupportedPermissions(boolean inVlan) throws InternalException, CloudException {
		if (inVlan) {
			return Collections.emptyList();
		}
		return Collections.singletonList(Permission.ALLOW);
	}

	@Override
	public @Nonnull Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan) throws InternalException,
			CloudException {
		if (inVlan) {
			return Collections.emptyList();
		}
		ArrayList<RuleTargetType> sources = new ArrayList<RuleTargetType>();

		sources.add(RuleTargetType.CIDR);
		sources.add(RuleTargetType.GLOBAL);
		return sources;
	}

	@Override
	public void revoke(@Nonnull String providerFirewallRuleId) throws InternalException, CloudException {
		synchronized (firewalls) {
			for (String fwId : rules.keySet()) {
				Collection<FirewallRule> list = rules.get(fwId);

				if (list == null) {
					continue;
				}
				ArrayList<FirewallRule> replacement = new ArrayList<FirewallRule>();

				for (FirewallRule r : list) {
					if (!r.getProviderRuleId().equals(providerFirewallRuleId)) {
						replacement.add(r);
					}
				}
				rules.put(fwId, replacement);
			}
		}
	}

	@Override
	public void revoke(@Nonnull String firewallId, @Nonnull String cidr, @Nonnull Protocol protocol, int beginPort,
			int endPort) throws CloudException, InternalException {
		revoke(firewallId, Direction.INGRESS, cidr, protocol, beginPort, endPort);
	}

	@Override
	public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull String cidr,
			@Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
		revoke(firewallId, direction, Permission.ALLOW, cidr, protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort);
	}

	@Override
	public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission,
			@Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException,
			InternalException {
		revoke(firewallId, direction, permission, source, protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort);
	}

	@Override
	public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission,
			@Nonnull String source, @Nonnull Protocol protocol, @Nonnull RuleTarget target, int beginPort, int endPort)
			throws CloudException, InternalException {
		RuleTarget sourceEndpoint, destinationEndpoint;

		if (direction.equals(Direction.INGRESS)) {
			sourceEndpoint = RuleTarget.getCIDR(source);
			destinationEndpoint = target;
		} else {
			sourceEndpoint = target;
			destinationEndpoint = RuleTarget.getCIDR(source);
		}
		revoke(FirewallRule.getInstance(null, firewallId, sourceEndpoint, direction, protocol, permission,
				destinationEndpoint, beginPort, endPort).getProviderRuleId());
	}

	@Override
	public boolean supportsRules(@Nonnull Direction direction, @Nonnull Permission permission, boolean inVlan)
			throws CloudException, InternalException {
		return (!inVlan && permission.equals(Permission.ALLOW));
	}

	@Override
	public boolean supportsFirewallSources() throws CloudException, InternalException {
		return true;
	}

	@Override
	public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
		return new String[0];
	}

	@Override
	public String authorize(String firewallId, FirewallRuleCreateOptions ruleOptions) throws CloudException,
			InternalException {
		return this.authorize(firewallId, ruleOptions.getDirection(), ruleOptions.getPermission(),
				ruleOptions.getSourceEndpoint(), ruleOptions.getProtocol(), ruleOptions.getDestinationEndpoint(),
				ruleOptions.getPortRangeStart(), ruleOptions.getPortRangeEnd(), ruleOptions.getPrecedence());
	}

	@Override
	public String create(FirewallCreateOptions options) throws InternalException, CloudException {
		return this.create(options.getName(), options.getDescription());
	}

	@Override
	public Map<Constraint, Object> getActiveConstraintsForFirewall(String firewallId) throws InternalException,
			CloudException {

		return null;
	}

	@Override
	public FirewallCapabilities getCapabilities() throws CloudException, InternalException {

		return null;
	}

	@Override
	public FirewallConstraints getFirewallConstraintsForCloud() throws InternalException, CloudException {

		return null;
	}

	@Override
	public void removeTags(String firewallId, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void removeTags(String[] firewallIds, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public boolean supportsFirewallCreation(boolean inVlan) throws CloudException, InternalException {

		return false;
	}

	@Override
	public boolean requiresRulesOnCreation() throws CloudException, InternalException {

		return false;
	}

	@Override
	public boolean supportsFirewallDeletion() throws CloudException, InternalException {

		return false;
	}

	@Override
	public void updateTags(String firewallId, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void updateTags(String[] firewallIds, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void setTags(String firewallId, Tag... tags) throws CloudException, InternalException {

	}

	@Override
	public void setTags(String[] firewallIds, Tag... tags) throws CloudException, InternalException {

	}
}
