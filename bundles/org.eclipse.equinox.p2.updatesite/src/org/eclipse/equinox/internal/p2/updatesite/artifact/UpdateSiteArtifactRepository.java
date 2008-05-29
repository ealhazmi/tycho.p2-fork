/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.updatesite.artifact;

import java.io.File;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.internal.p2.updatesite.*;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.*;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.core.repository.IRepository;
import org.eclipse.equinox.internal.provisional.p2.metadata.IArtifactKey;
import org.eclipse.equinox.internal.provisional.p2.metadata.generator.*;
import org.eclipse.equinox.internal.provisional.spi.p2.artifact.repository.SimpleArtifactRepositoryFactory;
import org.eclipse.equinox.internal.provisional.spi.p2.core.repository.AbstractRepository;
import org.osgi.framework.BundleContext;

public class UpdateSiteArtifactRepository extends AbstractRepository implements IArtifactRepository {

	public static final String TYPE = "org.eclipse.equinox.p2.updatesite.artifactRepository"; //$NON-NLS-1$
	public static final Integer VERSION = new Integer(1);
	private static final String PROP_ARTIFACT_REFERENCE = "artifact.reference"; //$NON-NLS-1$
	private static final String PROP_FORCE_THREADING = "eclipse.p2.force.threading"; //$NON-NLS-1$
	private static final String PROP_SITE_CHECKSUM = "site.checksum"; //$NON-NLS-1$
	private static final String PROTOCOL_FILE = "file"; //$NON-NLS-1$

	private final IArtifactRepository artifactRepository;

	public UpdateSiteArtifactRepository(URL location, IProgressMonitor monitor) throws ProvisionException {
		super(Activator.getRepositoryName(location), TYPE, VERSION.toString(), location, null, null, null);

		// todo progress monitoring
		// loading validates before we create repositories
		UpdateSite updateSite = UpdateSite.load(location, null);

		BundleContext context = Activator.getBundleContext();
		URL localRepositoryURL = null;
		try {
			String stateDirName = Integer.toString(location.toExternalForm().hashCode());
			File bundleData = context.getDataFile(null);
			File stateDir = new File(bundleData, stateDirName);
			localRepositoryURL = stateDir.toURL();
		} catch (MalformedURLException e) {
			throw new ProvisionException(new Status(IStatus.ERROR, Activator.ID, Messages.ErrorCreatingRepository, e));
		}
		artifactRepository = initializeArtifactRepository(context, localRepositoryURL, "update site implementation - " + location.toExternalForm(), updateSite); //$NON-NLS-1$

		String savedChecksum = (String) artifactRepository.getProperties().get(PROP_SITE_CHECKSUM);
		if (savedChecksum != null && savedChecksum.equals(updateSite.getChecksum()))
			return;

		if (!location.getProtocol().equals(PROTOCOL_FILE))
			artifactRepository.setProperty(PROP_FORCE_THREADING, "true"); //$NON-NLS-1$
		artifactRepository.removeAll();
		generateArtifacts(updateSite);
		artifactRepository.setProperty(PROP_SITE_CHECKSUM, updateSite.getChecksum());
	}

	private void generateArtifacts(UpdateSite updateSite) throws ProvisionException {
		Feature[] features = updateSite.loadFeatures();

		Set allSiteArtifacts = new HashSet();
		for (int i = 0; i < features.length; i++) {
			Feature feature = features[i];
			IArtifactKey featureKey = MetadataGeneratorHelper.createFeatureArtifactKey(feature.getId(), feature.getVersion());
			ArtifactDescriptor featureArtifactDescriptor = new ArtifactDescriptor(featureKey);
			URL featureURL = updateSite.getFeatureURL(feature.getId(), feature.getVersion());
			featureArtifactDescriptor.setRepositoryProperty(PROP_ARTIFACT_REFERENCE, featureURL.toExternalForm());
			featureArtifactDescriptor.setProperty(IArtifactDescriptor.DOWNLOAD_CONTENTTYPE, IArtifactDescriptor.TYPE_ZIP);
			allSiteArtifacts.add(featureArtifactDescriptor);

			FeatureEntry[] featureEntries = feature.getEntries();
			for (int j = 0; j < featureEntries.length; j++) {
				FeatureEntry entry = featureEntries[j];
				if (entry.isPlugin() && !entry.isRequires()) {
					IArtifactKey key = MetadataGeneratorHelper.createBundleArtifactKey(entry.getId(), entry.getVersion());
					ArtifactDescriptor artifactDescriptor = new ArtifactDescriptor(key);
					URL pluginURL = updateSite.getPluginURL(entry);
					artifactDescriptor.setRepositoryProperty(PROP_ARTIFACT_REFERENCE, pluginURL.toExternalForm());
					artifactDescriptor.setProperty(IArtifactDescriptor.DOWNLOAD_CONTENTTYPE, IArtifactDescriptor.TYPE_ZIP);
					allSiteArtifacts.add(artifactDescriptor);
				}
			}
		}

		IArtifactDescriptor[] descriptors = (IArtifactDescriptor[]) allSiteArtifacts.toArray(new IArtifactDescriptor[allSiteArtifacts.size()]);
		artifactRepository.addDescriptors(descriptors);
	}

	public static void validate(URL url, IProgressMonitor monitor) throws ProvisionException {
		UpdateSite.validate(url, monitor);
	}

	private IArtifactRepository initializeArtifactRepository(BundleContext context, URL stateDirURL, String repositoryName, UpdateSite updateSite) {
		SimpleArtifactRepositoryFactory factory = new SimpleArtifactRepositoryFactory();
		try {
			return factory.load(stateDirURL, null);
		} catch (ProvisionException e) {
			//fall through and create a new repository
		}
		Map props = new HashMap(5);
		String mirrors = updateSite.getMirrorsURL();
		if (mirrors != null) {
			props.put(IRepository.PROP_MIRRORS_URL, mirrors);
			//set the mirror base URL relative to the real remote repository rather than our local cache
			props.put(IRepository.PROP_MIRRORS_BASE_URL, getLocation().toExternalForm());
		}
		return factory.create(stateDirURL, repositoryName, null, props);
	}

	public Map getProperties() {
		return artifactRepository.getProperties();
	}

	public String setProperty(String key, String value) {
		return artifactRepository.setProperty(key, value);
	}

	public void addDescriptor(IArtifactDescriptor descriptor) {
		artifactRepository.addDescriptor(descriptor);
	}

	public boolean contains(IArtifactDescriptor descriptor) {
		return artifactRepository.contains(descriptor);
	}

	public boolean contains(IArtifactKey key) {
		return artifactRepository.contains(key);
	}

	public IStatus getArtifact(IArtifactDescriptor descriptor, OutputStream destination, IProgressMonitor monitor) {
		return artifactRepository.getArtifact(descriptor, destination, monitor);
	}

	public IArtifactDescriptor[] getArtifactDescriptors(IArtifactKey key) {
		return artifactRepository.getArtifactDescriptors(key);
	}

	public IArtifactKey[] getArtifactKeys() {
		return artifactRepository.getArtifactKeys();
	}

	public IStatus getArtifacts(IArtifactRequest[] requests, IProgressMonitor monitor) {
		return artifactRepository.getArtifacts(requests, monitor);
	}

	public OutputStream getOutputStream(IArtifactDescriptor descriptor) throws ProvisionException {
		return artifactRepository.getOutputStream(descriptor);
	}

	public void removeAll() {
		artifactRepository.removeAll();
	}

	public void removeDescriptor(IArtifactDescriptor descriptor) {
		artifactRepository.removeDescriptor(descriptor);
	}

	public void removeDescriptor(IArtifactKey key) {
		artifactRepository.removeDescriptor(key);
	}

	public void addDescriptors(IArtifactDescriptor[] descriptors) {
		artifactRepository.addDescriptors(descriptors);
	}
}
