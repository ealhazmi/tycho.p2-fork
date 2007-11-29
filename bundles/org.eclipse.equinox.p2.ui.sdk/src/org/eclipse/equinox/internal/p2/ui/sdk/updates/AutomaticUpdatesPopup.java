/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.ui.sdk.updates;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Preferences.IPropertyChangeListener;
import org.eclipse.core.runtime.Preferences.PropertyChangeEvent;
import org.eclipse.equinox.internal.p2.ui.sdk.ProvSDKMessages;
import org.eclipse.equinox.internal.p2.ui.sdk.ProvSDKUIActivator;
import org.eclipse.equinox.internal.p2.ui.sdk.prefs.PreferenceConstants;
import org.eclipse.equinox.p2.engine.Profile;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.ui.dialogs.UpdateDialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.progress.WorkbenchJob;

/**
 * AutomaticUpdatesPopup is an async popup dialog for notifying
 * the user of updates.
 * 
 * @since 3.4
 */
public class AutomaticUpdatesPopup extends PopupDialog {
	public static final String[] ELAPSED = {ProvSDKMessages.AutomaticUpdateScheduler_5Minutes, ProvSDKMessages.AutomaticUpdateScheduler_15Minutes, ProvSDKMessages.AutomaticUpdateScheduler_30Minutes, ProvSDKMessages.AutomaticUpdateScheduler_60Minutes};
	private static final long MINUTE = 60 * 1000L;
	Preferences prefs;
	long remindDelay = -1L;
	IPropertyChangeListener listener;
	WorkbenchJob remindJob;

	private static final String REMIND_HREF = "RMD"; //$NON-NLS-1$
	private static final String PREFS_HREF = "PREFS"; //$NON-NLS-1$
	private static final String DIALOG_SETTINGS_SECTION = "AutomaticUpdatesPopup"; //$NON-NLS-1$
	IInstallableUnit[] toUpdate;
	Profile profile;
	boolean downloaded;

	public AutomaticUpdatesPopup(IInstallableUnit[] toUpdate, Profile profile, boolean alreadyDownloaded, Preferences prefs) {
		super((Shell) null, PopupDialog.INFOPOPUPRESIZE_SHELLSTYLE | SWT.MODELESS, true, true, false, false, ProvSDKMessages.AutomaticUpdatesDialog_UpdatesAvailableTitle, null);
		downloaded = alreadyDownloaded;
		this.profile = profile;
		this.toUpdate = toUpdate;
		this.prefs = prefs;
		remindDelay = computeRemindDelay();
		listener = new IPropertyChangeListener() {

			public void propertyChange(PropertyChangeEvent event) {
				if (PreferenceConstants.PREF_REMIND_SCHEDULE.equals(event.getProperty()) || PreferenceConstants.PREF_REMIND_ELAPSED.equals(event.getProperty())) {
					computeRemindDelay();
					scheduleRemindJob();
				}
			}
		};
		prefs.addPropertyChangeListener(listener);

	}

	protected Control createDialogArea(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		composite.setLayout(layout);
		Link infoLink = new Link(parent, SWT.MULTI | SWT.WRAP);
		if (downloaded)
			infoLink.setText(ProvSDKMessages.AutomaticUpdatesDialog_ClickToReviewDownloaded);
		else
			infoLink.setText(ProvSDKMessages.AutomaticUpdatesDialog_ClickToReviewNotDownloaded);
		infoLink.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				new UpdateDialog(null, toUpdate, profile).open();
			}
		});
		infoLink.setLayoutData(new GridData(GridData.FILL_BOTH));

		// spacer
		new Label(parent, SWT.NONE);

		Link remindLink = new Link(parent, SWT.MULTI | SWT.WRAP);
		remindLink.setText(NLS.bind(ProvSDKMessages.AutomaticUpdatesPopup_RemindAndPrefLink, new String[] {REMIND_HREF, prefs.getString(PreferenceConstants.PREF_REMIND_ELAPSED), PREFS_HREF}));
		remindLink.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (REMIND_HREF.equals(e.text)) {
					if (prefs.getBoolean(PreferenceConstants.PREF_REMIND_SCHEDULE)) {
						// We are already on a remind schedule, so just set up the reminder
						getShell().setVisible(false);
						scheduleRemindJob();
					} else {
						// We were not on a schedule.  Setting the pref value
						// will activate our listener and start the remind job
						getShell().setVisible(false);
						prefs.setValue(PreferenceConstants.PREF_REMIND_SCHEDULE, true);
					}
				}
				if (PREFS_HREF.equals(e.text)) {
					PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(getShell(), PreferenceConstants.PREF_PAGE_AUTO_UPDATES, null, null);
					dialog.open();
				}
			}
		});
		infoLink.setLayoutData(new GridData(GridData.FILL_BOTH));

		return composite;

	}

	protected IDialogSettings getDialogBoundsSettings() {
		IDialogSettings settings = ProvSDKUIActivator.getDefault().getDialogSettings();
		IDialogSettings section = settings.getSection(DIALOG_SETTINGS_SECTION);
		if (section == null) {
			section = settings.addNewSection(DIALOG_SETTINGS_SECTION);
		}
		return section;
	}

	public boolean close() {
		prefs.removePropertyChangeListener(listener);
		cancelRemindJob();
		remindJob = null;
		listener = null;
		return super.close();
	}

	void scheduleRemindJob() {
		// Cancel any pending remind job if there is one
		if (remindJob != null)
			remindJob.cancel();
		// If no updates have been found, there is nothing to remind
		if (toUpdate == null)
			return;
		if (remindDelay < 0)
			return;
		remindJob = new WorkbenchJob(ProvSDKMessages.AutomaticUpdatesPopup_ReminderJobTitle) {
			public IStatus runInUIThread(IProgressMonitor monitor) {
				Shell shell = getShell();
				if (shell != null && !shell.isDisposed())
					shell.setVisible(true);
				return Status.OK_STATUS;
			}
		};
		remindJob.setSystem(true);
		remindJob.schedule(remindDelay);

	}

	/*
	 * Computes the number of milliseconds for the delay
	 * in reminding the user of updates
	 */
	long computeRemindDelay() {
		if (prefs.getBoolean(PreferenceConstants.PREF_REMIND_SCHEDULE)) {
			String elapsed = prefs.getString(PreferenceConstants.PREF_REMIND_ELAPSED);
			for (int d = 0; d < ELAPSED.length; d++)
				if (ELAPSED[d].equals(elapsed))
					switch (d) {
						case 0 :
							// 5 minutes
							//return 5 * MINUTE;
							return 2000L;
						case 1 :
							// 15 minutes
							return 15 * MINUTE;
						case 2 :
							// 30 minutes
							return 30 * MINUTE;
						case 3 :
							// 1 hour
							return 60 * MINUTE;
					}
		}
		return -1L;
	}

	private void cancelRemindJob() {
		if (remindJob != null) {
			remindJob.cancel();
		}
	}

	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(ProvSDKMessages.AutomaticUpdatesDialog_UpdatesAvailableTitle);
	}
}
