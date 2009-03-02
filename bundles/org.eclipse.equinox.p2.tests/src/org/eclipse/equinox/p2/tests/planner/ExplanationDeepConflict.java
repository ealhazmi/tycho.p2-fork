package org.eclipse.equinox.p2.tests.planner;

import org.eclipse.equinox.internal.provisional.p2.core.Version;
import org.eclipse.equinox.internal.provisional.p2.core.VersionRange;
import org.eclipse.equinox.internal.provisional.p2.director.*;
import org.eclipse.equinox.internal.provisional.p2.engine.*;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class ExplanationDeepConflict extends AbstractProvisioningTest {
	private IProfile profile;
	private IPlanner planner;
	private IEngine engine;
	private IInstallableUnit sdk;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		sdk = createIU("SDK", Version.fromOSGiVersion(new org.osgi.framework.Version("1.0.0")), createRequiredCapabilities(IInstallableUnit.NAMESPACE_IU_ID, "SDKPart", new VersionRange("[1.0.0, 1.0.0]"), null));
		IInstallableUnit sdkPart = createIU("SDKPart", Version.fromOSGiVersion(new org.osgi.framework.Version("1.0.0")), createRequiredCapabilities(IInstallableUnit.NAMESPACE_IU_ID, "InnerSDKPart", new VersionRange("[1.0.0, 1.0.0]"), null));
		IInstallableUnit innerSdkPart = createIU("InnerSDKPart", Version.fromOSGiVersion(new org.osgi.framework.Version("1.0.0")), createRequiredCapabilities(IInstallableUnit.NAMESPACE_IU_ID, "InnerInnerSDKPart", new VersionRange("[1.0.0, 1.0.0]"), null));
		IInstallableUnit innerInnerSDKPart = createIU("InnerInnerSDKPart", Version.fromOSGiVersion(new org.osgi.framework.Version("1.0.0")), true);

		createTestMetdataRepository(new IInstallableUnit[] {sdk, sdkPart, innerSdkPart, innerInnerSDKPart});

		profile = createProfile("TestProfile." + getName());
		planner = createPlanner();
		engine = createEngine();

		ProfileChangeRequest pcr = new ProfileChangeRequest(profile);
		pcr.addInstallableUnits(new IInstallableUnit[] {sdk});
		engine.perform(profile, new DefaultPhaseSet(), planner.getProvisioningPlan(pcr, null, null).getOperands(), null, null);
		assertProfileContains("1.0", profile, new IInstallableUnit[] {sdk, sdkPart, innerSdkPart, innerInnerSDKPart});
	}

	public void testDeepSingletonConflict() {
		//CDT will have a singleton conflict with SDK
		IInstallableUnit cdt = createIU("CDT", Version.fromOSGiVersion(new org.osgi.framework.Version("1.0.0")), createRequiredCapabilities(IInstallableUnit.NAMESPACE_IU_ID, "CDTPart", new VersionRange("[1.0.0, 1.0.0]"), null));
		IInstallableUnit cdtPart = createIU("CDTPart", Version.fromOSGiVersion(new org.osgi.framework.Version("1.0.0")), createRequiredCapabilities(IInstallableUnit.NAMESPACE_IU_ID, "InnerInnerSDKPart", new VersionRange("[2.0.0, 2.0.0]"), null));
		IInstallableUnit innerInnerSDKPart2 = createIU("InnerInnerSDKPart", Version.fromOSGiVersion(new org.osgi.framework.Version("2.0.0")), true);

		createTestMetdataRepository(new IInstallableUnit[] {cdt, cdtPart, innerInnerSDKPart2});
		ProfileChangeRequest pcr = new ProfileChangeRequest(profile);
		pcr.addInstallableUnits(new IInstallableUnit[] {cdt});
		ProvisioningPlan plan = planner.getProvisioningPlan(pcr, null, null);
		System.out.println(plan.getRequestStatus().getExplanations());
		assertTrue(plan.getRequestStatus().getConflictsWithInstalledRoots().contains(cdt));
		//Here we verify that we only return the roots we asked the installation of. The SDK is installable since it is already installed
		assertFalse(plan.getRequestStatus().getConflictsWithInstalledRoots().contains(sdk));
		assertTrue(plan.getRequestStatus().getConflictsWithAnyRoots().contains(sdk));
		//		assertTrue(plan.getRequestStatus(cdt).getConflictsWithAnyRoots().contains(sdk));
		//		assertTrue(plan.getRequestStatus(cdt).getConflictsWithInstalledRoots().contains(sdk));
	}
}