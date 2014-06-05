/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2013  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 * 
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://www.fosd.de/featureide/ for further information.
 */
package de.ovgu.featureide.core.mpl.builder;

import java.io.IOException;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.IComposerExtension;
import de.ovgu.featureide.core.mpl.MPLPlugin;
import de.ovgu.featureide.core.mpl.job.MPLBuildExternalJob;
import de.ovgu.featureide.core.mpl.job.MPLCopyExternalJob;
import de.ovgu.featureide.core.mpl.job.MPLRenameExternalJob;
import de.ovgu.featureide.core.mpl.job.util.AJobArguments;
import de.ovgu.featureide.core.mpl.job.util.IChainJob;
import de.ovgu.featureide.core.mpl.job.util.JobManager;
import de.ovgu.featureide.fm.core.ExtendedFeatureModel;
import de.ovgu.featureide.fm.core.Feature;
import de.ovgu.featureide.fm.core.FeatureModel;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.ConfigurationReader;

/**
 * A simple multi product line builder.
 * 
 * @author Sebastian Krieter
 */
public class MSPLBuilder extends IncrementalProjectBuilder {

	public static final String BUILDER_ID = MPLPlugin.PLUGIN_ID + ".MSPLBuilder";
	public static final String COMPOSER_KEY = "composer";
	
	public MSPLBuilder() {
		super();
	}

	protected void clean(IProgressMonitor monitor) throws CoreException {
		// not implemented yet
	}

	@SuppressWarnings({ "rawtypes" })
	@Override
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor) {
		IProject project = getProject();
		if (project == null) {
			MPLPlugin.getDefault().logWarning("no project got");
			return null;
		}
		IFeatureProject featureProject = CorePlugin.getFeatureProject(project);
		if (!featureProject.buildRelavantChanges()) {
			return null;
		}
		FeatureModel featureModel = featureProject.getFeatureModel();
		if (!(featureModel instanceof ExtendedFeatureModel)) {
			return null;
		}
		ExtendedFeatureModel extFeatureModel = (ExtendedFeatureModel) featureModel;

		// build own project
		tempConfigFile = featureProject.getCurrentConfiguration();
		buildProject(featureProject, kind, monitor);

		// get mapping of other projects
		final Configuration mappedProjects = new Configuration(extFeatureModel.getMappingModel());
		final Configuration config = new Configuration(extFeatureModel);
		
		try {
			new ConfigurationReader(mappedProjects).readFromFile(project.getFile("InterfaceMapping/default.config"));
			new ConfigurationReader(config).readFromFile(tempConfigFile);
		} catch (CoreException e) {
			MPLPlugin.getDefault().logError(e);
			return null;
		} catch (IOException e) {
			MPLPlugin.getDefault().logError(e);
			return null;
		}
		
		// build other projects
		for (final Feature mappedProject : mappedProjects.getSelectedFeatures()) {
			if (mappedProject.isConcrete()) {
				final int splittIndex = mappedProject.getName().lastIndexOf('.') + 1;
				if (splittIndex == 0) {
					// can this happen???
				}
				final String projectName = mappedProject.getName().substring(splittIndex);
				final String configName = mappedProject.getName().substring(0, splittIndex);
				
				final IFeatureProject externalFeatureProject = CorePlugin.getFeatureProject(
						ResourcesPlugin.getWorkspace().getRoot().getProject(projectName));
				
				//build
				startJob(project, new MPLBuildExternalJob.Arguments(externalFeatureProject, config, configName));
				
				// rename
				startJob(project, new MPLRenameExternalJob.Arguments(externalFeatureProject.getProject(), configName));
				
				// copy
				startJob(project, new MPLCopyExternalJob.Arguments(
						externalFeatureProject.getBuildFolder().getFolder("tempConfig"), 
						featureProject.getBuildFolder().getFolder(tempConfigFile.getName().split("[.]")[0])));
			}
		}
		return null;
	}

	private void startJob(IProject project, AJobArguments arg) {
		IChainJob curJob = arg.createJob();
		curJob.setIgnorePreviousJobFail(false);
		JobManager.addJob(project, curJob);
	}
	
	private IFile tempConfigFile = null;
	
	private void buildProject(IFeatureProject featureProject, int kind, IProgressMonitor monitor) {
		if (tempConfigFile == null) {
			return;
		}
		featureProject.deleteBuilderMarkers(featureProject.getProject(), IResource.DEPTH_INFINITE);

		try {
			for (IResource res : featureProject.getConfigFolder().members())
				res.refreshLocal(IResource.DEPTH_ZERO, null);
			featureProject.getProject().refreshLocal(IResource.DEPTH_ONE, null);
		} catch (CoreException e) {
			MPLPlugin.getDefault().logError(e);
		}
		
		IComposerExtension composerExtension = featureProject.getComposer();
		if ((composerExtension) == null) {
			CorePlugin.getDefault().logWarning("No composition tool found");
			featureProject.createBuilderMarker(featureProject.getProject(),
					"Could not load the assigned composition engine: "
							+ featureProject.getComposerID(), 0,
					IMarker.SEVERITY_ERROR);
			return;
		}

		composerExtension.loadComposerExtension();
		composerExtension.performFullBuild(tempConfigFile);
		featureProject.built();

		try {
			featureProject.getProject().refreshLocal(IResource.DEPTH_INFINITE,	monitor);
			CorePlugin.getDefault().fireBuildUpdated(featureProject);
		} catch (CoreException e) {
			CorePlugin.getDefault().logError(e);
		}
		Configuration c = new Configuration(featureProject.getFeatureModel());
		ConfigurationReader reader = new ConfigurationReader(c);
		try {
			reader.readFromFile(tempConfigFile);
		} catch (Exception e) {
			CorePlugin.getDefault().logError(e);
		}
		composerExtension.copyNotComposedFiles(c,
				featureProject.getBuildFolder());
		try {
			featureProject.getProject().refreshLocal(IResource.DEPTH_INFINITE,	monitor);
		} catch (CoreException e) {
			CorePlugin.getDefault().logError(e);
		}
		return;
	}
}