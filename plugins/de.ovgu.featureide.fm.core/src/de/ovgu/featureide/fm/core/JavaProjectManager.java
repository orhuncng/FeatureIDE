/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2016  FeatureIDE team, University of Magdeburg, Germany
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
 * See http://featureide.cs.ovgu.de/ for further information.
 */
package de.ovgu.featureide.fm.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;

import javax.annotation.CheckForNull;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.io.ProblemList;
import de.ovgu.featureide.fm.core.io.manager.ConfigurationManager;
import de.ovgu.featureide.fm.core.io.manager.FeatureModelManager;
import de.ovgu.featureide.fm.core.io.manager.IFileManager;
import de.ovgu.featureide.fm.core.io.manager.VirtualFileManager;
import de.ovgu.featureide.fm.core.io.xml.XmlFeatureModelFormat;
import de.ovgu.featureide.fm.core.job.LongRunningMethod;
import de.ovgu.featureide.fm.core.job.LongRunningWrapper;
import de.ovgu.featureide.fm.core.job.util.JobSequence;

/**
 * 
 * @author Sebastian Krieter
 */
public class JavaProjectManager {

	protected final HashMap<Path, FeatureProject> rootPathProjectMap = new HashMap<>();
	protected final HashMap<Path, FeatureProject> modelPathProjectMap = new HashMap<>();
	
	protected final HashMap<IFeatureModel, FeatureProject> modelFeatureProjectMap = new HashMap<>();
	protected final WeakHashMap<IFeatureModel, Path> modelPathMap = new WeakHashMap<>();

	protected JavaProjectManager() {
	}

//	/**
//	 * Creates a {@link LongRunningMethod} for every project with the given arguments.
//	 * 
//	 * @param projects the list of projects
//	 * @param arguments the arguments for the job
//	 * @param autostart if {@code true} the jobs is started automatically.
//	 * @return the created job or a {@link JobSequence} if more than one project is given.
//	 *         Returns {@code null} if {@code projects} is empty.
//	 */
//	public LongRunningMethod<?> startJobs(List<LongRunningMethod<?>> projects, String jobName, boolean autostart) {
//		LongRunningMethod<?> ret;
//		switch (projects.size()) {
//		case 0:
//			return null;
//		case 1:
//			ret = projects.get(0);
//			break;
//		default:
//			final JobSequence jobSequence = new JobSequence();
//			jobSequence.setIgnorePreviousJobFail(true);
//			for (LongRunningMethod<?> p : projects) {
//				jobSequence.addJob(p);
//			}
//			ret = jobSequence;
//		}
//		if (autostart) {
//			LongRunningWrapper.getRunner(ret, jobName).schedule();
//		}
//		return ret;
//	}

	public void addProject(Path root, Path featureModelFile, Path configurations) {
		synchronized (rootPathProjectMap) {
			if (rootPathProjectMap.containsKey(root)) {
				return;
			}
			final IFileManager<IFeatureModel> featureModelManager = FeatureModelManager.getInstance(featureModelFile);
			final IFeatureModel featureModel = featureModelManager.getObject();
			final FeatureProject data = new FeatureProject(featureModelManager);
			data.addConfigurationManager(getConfigurationManager(configurations, featureModel));
			rootPathProjectMap.put(root, data);
			modelFeatureProjectMap.put(featureModel, data);
			modelPathMap.put(featureModel, root);
		}
	}

	public FeatureProject addProject(Path root, Path featureModelFile) {
		synchronized (rootPathProjectMap) {
			FeatureProject featureProject = rootPathProjectMap.get(root);
			if (featureProject == null) {
				final IFileManager<IFeatureModel> featureModelManager = FeatureModelManager.getInstance(featureModelFile);
				final IFeatureModel featureModel = featureModelManager.getObject();
				featureProject = new FeatureProject(featureModelManager);
				rootPathProjectMap.put(root, featureProject);
				modelFeatureProjectMap.put(featureModel, featureProject);
				modelPathMap.put(featureModel, root);
			}
			return featureProject;
		}
	}

	public ArrayList<IFileManager<Configuration>> getConfigurationManager(Path configurations, final IFeatureModel featureModel) {
		final ArrayList<IFileManager<Configuration>> configurationManagerList = new ArrayList<>();
		try {
			Files.walkFileTree(configurations, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					final Configuration c = new Configuration(featureModel);
					ConfigurationManager configurationManager = ConfigurationManager.getInstance(file);
					if (configurationManager != null) {
						configurationManager.setObject(c);
						configurationManager.read();
					} else {
						configurationManager = ConfigurationManager.getInstance(file, c);
					}

					final ProblemList lastProblems = configurationManager.getLastProblems();
					if (lastProblems.containsError()) {
						ConfigurationManager.removeInstance(file);
					} else {
						configurationManagerList.add(configurationManager);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			Logger.logError(e);
		}
		return configurationManagerList;
	}

	public void addProject(IFeatureModel featureModel) {
		synchronized (rootPathProjectMap) {
			if (modelFeatureProjectMap.containsKey(featureModel)) {
				return;
			}
			modelFeatureProjectMap.put(featureModel, createVirtualFeatureProject(featureModel));
		}
	}

	private FeatureProject createVirtualFeatureProject(IFeatureModel featureModel) {
		final FeatureProject data = new FeatureProject(new VirtualFileManager<>(featureModel, new XmlFeatureModelFormat()));
		data.addConfigurationManager(new ArrayList<IFileManager<Configuration>>());
		return data;
	}

	@CheckForNull
	public FeatureProject removeProject(Path root) {
		synchronized (rootPathProjectMap) {
			final FeatureProject project = rootPathProjectMap.remove(root);
			if (project != null) {
				final IFeatureModel fm = project.getFeatureModelManager().getObject();
				modelPathMap.remove(fm);
				modelFeatureProjectMap.remove(fm);
			}
			return project;
		}
	}

	@CheckForNull
	public FeatureProject removeProject(IFeatureModel featureModel) {
		synchronized (rootPathProjectMap) {
			final FeatureProject project = modelFeatureProjectMap.remove(featureModel);
			if (project != null) {
				final Path path = modelPathMap.get(featureModel);
				if (path != null) {
					rootPathProjectMap.remove(path);
				}
			}
			return project;
		}
	}

	/**
	 * returns an unmodifiable Collection of all ProjectData items, or <code>null</code> if plugin is not loaded
	 * 
	 * @return
	 */
	public Collection<FeatureProject> getFeatureProjects() {
		synchronized (rootPathProjectMap) {
			return Collections.unmodifiableCollection(modelFeatureProjectMap.values());
		}
	}

	/**
	 * returns the ProjectData object associated with the given resource
	 * 
	 * @param res
	 * @return <code>null</code> if there is no associated project, no active
	 *         instance of this plug-in or resource is the workspace root
	 */
	@CheckForNull
	public FeatureProject getProject(Path path) {
		synchronized (rootPathProjectMap) {
			return rootPathProjectMap.get(path);
		}
	}

	@CheckForNull
	public FeatureProject getProject(IFeatureModel model) {
		synchronized (rootPathProjectMap) {
			
			final Path sourceFile = model.getSourceFile();

			if (sourceFile != null) {
				
			if (modelPathProjectMap.containsKey(sourceFile)) {
				
			}
			} 
			
			
			return modelFeatureProjectMap.get(model);
		}
	}

	public boolean hasProjectData(Path path) {
		synchronized (rootPathProjectMap) {
			return rootPathProjectMap.containsKey(path);
		}
	}

	public boolean hasProjectData(IFeatureModel model) {
		synchronized (rootPathProjectMap) {
			return rootPathProjectMap.containsKey(model);
		}
	}

}