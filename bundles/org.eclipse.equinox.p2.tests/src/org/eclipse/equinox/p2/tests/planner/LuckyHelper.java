/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 * Sonatype, Inc. - initial implementation and ideas 
 ******************************************************************************/
package org.eclipse.equinox.p2.tests.planner;

import java.util.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.internal.p2.director.ProfileChangeRequest;
import org.eclipse.equinox.internal.p2.metadata.query.UpdateQuery;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.engine.query.IUProfilePropertyQuery;
import org.eclipse.equinox.p2.metadata.*;
import org.eclipse.equinox.p2.planner.*;
import org.eclipse.equinox.p2.query.*;

public class LuckyHelper {
	ProfileChangeRequest computeProfileChangeRequest(IProfile profile, IPlanner planner, ProfileChangeRequest request, ProvisioningContext context, IProgressMonitor monitor) {
		//		IProfileRegistry profileRegistry = (IProfileRegistry) session.getProvisioningAgent().getService(IProfileRegistry.SERVICE_NAME);
		IPlanner plan = planner; //(IPlanner) session.getProvisioningAgent().getService(IPlanner.SERVICE_NAME);
		IProfile prof = profile; //profileRegistry.getProfile(getProfileId());

		final String INCLUSION_RULES = "org.eclipse.equinox.p2.internal.inclusion.rules"; //$NON-NLS-1$
		final String INCLUSION_OPTIONAL = "OPTIONAL"; //$NON-NLS-1$
		final String INCLUSION_STRICT = "STRICT"; //$NON-NLS-1$

		IQueryResult<IInstallableUnit> strictRoots = prof.query(new IUProfilePropertyQuery(INCLUSION_RULES, INCLUSION_STRICT), null);
		IQueryResult<IInstallableUnit> optionalRoots = prof.query(new IUProfilePropertyQuery(INCLUSION_RULES, INCLUSION_OPTIONAL), null);
		Set<IInstallableUnit> tmpRoots = new HashSet<IInstallableUnit>(strictRoots.toUnmodifiableSet());
		tmpRoots.addAll(optionalRoots.toUnmodifiableSet());
		CollectionResult<IInstallableUnit> allInitialRoots = new CollectionResult<IInstallableUnit>(tmpRoots);

		ProfileChangeRequest updateFinderRequest = (ProfileChangeRequest) plan.createChangeRequest(prof);
		Collection<IRequirement> limitingRequirements = new ArrayList<IRequirement>();

		//Create a profile change request that attempts at installing updates for all the existing roots.
		for (Iterator<IInstallableUnit> iterator = allInitialRoots.query(QueryUtil.ALL_UNITS, null).iterator(); iterator.hasNext();) {
			IInstallableUnit currentlyInstalled = iterator.next();

			//find all the potential updates for the currentlyInstalled iu
			IQueryResult<IInstallableUnit> updatesAvailable = plan.updatesFor(currentlyInstalled, context, null);
			for (Iterator<IInstallableUnit> iterator2 = updatesAvailable.iterator(); iterator2.hasNext();) {
				IInstallableUnit update = iterator2.next();
				updateFinderRequest.add(update);
				updateFinderRequest.setInstallableUnitInclusionRules(update, ProfileInclusionRules.createOptionalInclusionRule(update));
			}

			//force the original IU to optional, but make sure that the solution at least includes it
			updateFinderRequest.setInstallableUnitInclusionRules(currentlyInstalled, ProfileInclusionRules.createOptionalInclusionRule(currentlyInstalled));
			limitingRequirements.add(MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, currentlyInstalled.getId(), new VersionRange(currentlyInstalled.getVersion(), true, Version.MAX_VERSION, true), null, false, false));
		}
		updateFinderRequest.addExtraRequirements(limitingRequirements);

		IProvisioningPlan updateFinderPlan = plan.getProvisioningPlan(updateFinderRequest, context, null);
		if (updateFinderPlan.getAdditions().query(QueryUtil.ALL_UNITS, null).isEmpty()) {
			return null;
		}

		//Take into account all the removals
		IProfileChangeRequest finalChangeRequest = plan.createChangeRequest(prof);
		IQueryResult<IInstallableUnit> removals = updateFinderPlan.getRemovals().query(QueryUtil.ALL_UNITS, null);
		for (Iterator<IInstallableUnit> iterator = removals.iterator(); iterator.hasNext();) {
			IInstallableUnit iu = iterator.next();
			if (!allInitialRoots.query(QueryUtil.createIUQuery(iu), null).isEmpty()) {
				finalChangeRequest.remove(iu);
			}
		}

		//Take into account the additions for stricts
		for (Iterator<IInstallableUnit> iterator = strictRoots.iterator(); iterator.hasNext();) {
			IInstallableUnit formerRoot = iterator.next();
			IQueryResult<IInstallableUnit> update = updateFinderPlan.getAdditions().query(new UpdateQuery(formerRoot), null);
			if (!update.isEmpty())
				finalChangeRequest.addAll(update.toUnmodifiableSet());
		}

		//Take into account the additions for optionals
		for (Iterator<IInstallableUnit> iterator = optionalRoots.iterator(); iterator.hasNext();) {
			IInstallableUnit formerRoot = iterator.next();
			IQueryResult<IInstallableUnit> update = updateFinderPlan.getAdditions().query(new UpdateQuery(formerRoot), null);
			if (!update.isEmpty()) {
				for (Iterator<IInstallableUnit> it = update.iterator(); it.hasNext();) {
					IInstallableUnit updatedOptionalIU = it.next();
					finalChangeRequest.add(updatedOptionalIU);
					finalChangeRequest.setInstallableUnitInclusionRules(updatedOptionalIU, ProfileInclusionRules.createOptionalInclusionRule(updatedOptionalIU));
				}
			}
		}
		return (ProfileChangeRequest) finalChangeRequest;
	}

}
