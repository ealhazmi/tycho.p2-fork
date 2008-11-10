package org.eclipse.equinox.p2.tests.planner;

import java.io.File;
import java.util.ArrayList;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.engine.SimpleProfileRegistry;
import org.eclipse.equinox.internal.provisional.p2.director.*;
import org.eclipse.equinox.internal.provisional.p2.engine.IProfile;
import org.eclipse.equinox.internal.provisional.p2.engine.ProvisioningContext;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.InstallableUnitQuery;
import org.eclipse.equinox.internal.provisional.p2.query.Collector;
import org.eclipse.equinox.p2.tests.AbstractProvisioningTest;

public class Bug252682 extends AbstractProvisioningTest {
	IProfile profile = null;
	ArrayList newIUs = new ArrayList();

	protected void setUp() throws Exception {
		super.setUp();
		File reporegistry1 = getTestData("test data bug 252682", "testData/bug252682/p2/org.eclipse.equinox.p2.engine/profileRegistry");
		SimpleProfileRegistry registry = new SimpleProfileRegistry(reporegistry1, null, false);
		profile = registry.getProfile("SDKProfile");
		assertNotNull(profile);
		//		newIUs.add(createEclipseIU("org.eclipse.equinox.p2.core", new Version("1.0.100.v20081024")));
	}

	public void testInstallFeaturePatch() {
		Collector c = profile.query(new InstallableUnitQuery("org.eclipse.equinox.p2.core.patch"), new Collector(), new NullProgressMonitor());
		assertEquals(1, c.size());
		ProvisioningContext ctx = new ProvisioningContext();
		ctx.setExtraIUs(newIUs);
		IInstallableUnit patch = (IInstallableUnit) c.iterator().next();
		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		request.removeInstallableUnits(new IInstallableUnit[] {patch});
		IPlanner planner = createPlanner();
		ProvisioningPlan plan = planner.getProvisioningPlan(request, ctx, new NullProgressMonitor());
		assertInstallOperand(plan, patch);
		//[[R]com.ibm.rational.test.lt.arm 7.0.250.v200810021504 --> [R]com.ibm.rational.test.lt.arm 7.0.300.200811041300, 
		assertEquals(1, plan.getAdditions().query(new InstallableUnitQuery("com.ibm.rational.test.lt.arm"), new Collector(), null).size());
		//[R]com.ibm.rational.test.lt.armbroker 7.0.250.v200810021504 --> [R]com.ibm.rational.test.lt.armbroker 7.0.300.200811041300, 
		assertEquals(1, plan.getAdditions().query(new InstallableUnitQuery("com.ibm.rational.test.lt.armbroker"), new Collector(), null).size());
		//[R]com.ibm.rational.test.lt.kernel 7.2.151.v200810021605 --> [R]com.ibm.rational.test.lt.kernel 7.2.200.200811041300, 
		assertEquals(1, plan.getAdditions().query(new InstallableUnitQuery("com.ibm.rational.test.lt.kernel"), new Collector(), null).size());
	}
}