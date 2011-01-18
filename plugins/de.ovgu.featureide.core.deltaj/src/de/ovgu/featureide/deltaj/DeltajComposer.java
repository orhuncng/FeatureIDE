package de.ovgu.featureide.deltaj;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.xtext.example.util.DJIdeProperties;
import org.xtext.example.util.ValidationStatus;

import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.IComposerExtensionClass;
import de.ovgu.featureide.fm.core.Feature;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.ConfigurationReader;
import djtemplates.DJStandaloneCompiler;

/**
 * DeltaJ Composer
 * 
 * @author Fabian Benduhn
 */
public class DeltajComposer implements IComposerExtensionClass {
	public static final String JAVA_NATURE = "org.eclipse.jdt.core.javanature";
	private static final String XTEXT_NATURE = "org.eclipse.xtext.ui.shared.xtextNature";
	private String equationPath;
	private String basePath;
	private String outputPath;
	private String filename;
	private IFeatureProject featureProject = null;

	private Set<String> selectedFeatures;
	private Boolean sourceFilesAdded;
	private Set<String> featureNames;

	public void run() {
	
		DJIdeProperties.changeValidationStatus(ValidationStatus.VALIDATE_ALL);
		DJStandaloneCompiler compiler = new DJStandaloneCompiler(filename);
		String uriPrefix = getUriPrefix();
		boolean error;
		try {
			error = compiler.compile(basePath, outputPath, uriPrefix);
		} catch (Exception e) {

			error = true;
		}
		if (error)
			System.out.println("error: " + compiler.getErrorReport());
		try {
			ResourcesPlugin.getWorkspace().getRoot()
					.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (CoreException e) {
			DeltajCorePlugin.getDefault().logError(e);
		}

	}

	@Override
	public void initialize(IFeatureProject project) {

		featureProject = project;

	}

	@Override
	public void performFullBuild(IFile equation) {

		assert (featureProject != null) : "Invalid project given";

		equationPath = equation.getRawLocation().toOSString();
		basePath = featureProject.getSourcePath().replace('\\', '/') + "/";
		outputPath = featureProject.getBuildPath().replace('\\', '/') + "/";

		if (equationPath == null || basePath == null || outputPath == null)
			return;

		Configuration configuration = new Configuration(
				featureProject.getFeatureModel());
		ConfigurationReader reader = new ConfigurationReader(configuration);
		try {
			reader.readFromFile(equation);
		} catch (CoreException e) {
			DeltajCorePlugin.getDefault().logError(e);
		} catch (IOException e) {
			DeltajCorePlugin.getDefault().logError(e);
		}
		updateSelectedFeatures(configuration);

		featureNames = configuration.getFeatureModel().getFeatureNames();
		sourceFilesAdded = false;

		try {
			handleSourceFiles(featureProject.getSourceFolder());
		} catch (CoreException e) {
			DeltajCorePlugin.getDefault().logError(e);
		}

		if (!sourceFilesAdded) {

			return;
		}

		run();
	}

	private void updateSelectedFeatures(Configuration configuration) {
		selectedFeatures = new TreeSet<String>();

		for (Feature feature : configuration.getSelectedFeatures()) {

			selectedFeatures.add(feature.getName());
		}
	}

	@Override
	public ArrayList<String> extensions() {
		ArrayList<String> extensions = new ArrayList<String>();
		extensions.add(".dj");
		return extensions;
	}

	public String getEditorID(String extension) {
		if (extension.equals("dj")) {

			return "org.xtext.example.DJ";
		}
		return "";
	}

	@Override
	public boolean clean() {
		return true;
	}

	@Override
	public boolean copyNotComposedFiles() {
	copyFolderMembers(featureProject.getSourceFolder());
		return false;
	}
	private void copyFolderMembers(IFolder folder){
	
		try {
			for (IResource res : folder.members()) {
		
				if ((res.getFileExtension() == null || !res.getFileExtension()
						.equals("dj")))
					
					res.copy(new Path(featureProject.getBuildFolder()
							.getFullPath().toString()
							+ "/" + res.getName()), true, null);
				if(res instanceof IFolder){
					copyFolderMembers((IFolder)res);
				}
			}
		} catch (CoreException e) {
			DeltajCorePlugin.getDefault().logError(e);
		}
	}
	@Override
	public void buildFSTModel() {

	}

	@Override
	public ArrayList<String[]> getTemplates() {

		String[] core = {
				"DeltaJ (Core Module)",
				"dj",
				"features #featurename#\nconfigurations\n#featurename#;\n\n\ncore #featurename# {\n\tclass #classname#{\n\n\t}\n}" };
		String[] delta = {
				"DeltaJ (Delta Module)",
				"dj",
				"delta #featurename# when #featurename# {\n\tmodifies class #classname#{\n\n\t}\n}" };
		ArrayList<String[]> list = new ArrayList<String[]>();
		list.add(core);
		list.add(delta);
		return list;
	}

	@Override
	public int getDefaultTemplateIndex() {
		return 1;
	}

	@Override
	public void addCompiler(IProject project, String sourcePath,
			String equationPath, String buildPath) {
		addNature(project,JAVA_NATURE);
	
		addClasspathFile(project, sourcePath, equationPath, buildPath);
	addNature(project,XTEXT_NATURE);
	}
	
	
	private void addClasspathFile(IProject project, String sourcePath,
			String equationPath, String buildPath) {
		IFile iClasspathFile = project.getFile(".classpath");
		if (!iClasspathFile.exists()) {
			String bin = "bin";
			if (sourcePath.equals(bin) || equationPath.equals(bin)
					|| buildPath.equals(bin)) {
				bin = "bin2";
			}
			if (sourcePath.equals(bin) || equationPath.equals(bin)
					|| buildPath.equals(bin)) {
				bin = "bin3";
			}
			try {
				String text = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
			 				  "<classpath>\n" +  
			 				  "<classpathentry kind=\"src\" path=\"" + buildPath + "\"/>\n" + 
			 				  "<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6\"/>\n" + 
			 				  "<classpathentry kind=\"output\" path=\"" + bin + "\"/>\n" + 
			 				  "</classpath>"; 
				InputStream source = new ByteArrayInputStream(text.getBytes());
				iClasspathFile.create(source, true, null);
			} catch (CoreException e) {
				DeltajCorePlugin.getDefault().logError(e);
			}

		}
	}
	private void addNature(IProject project,String nature) {
		try {
	
			if (!project.isAccessible() || project.hasNature(nature))
				return;

			IProjectDescription description = project.getDescription();
			String[] natures = description.getNatureIds();
			String[] newNatures = new String[natures.length + 1];
			System.arraycopy(natures, 0, newNatures, 0, natures.length);
			newNatures[natures.length] = nature;
			description.setNatureIds(newNatures);
		
			
			project.setDescription(description, null);
		} catch (CoreException e) {
			DeltajCorePlugin.getDefault().logError(e);
		}
	}
	@Override
	public boolean hasFeatureFolders() {

		return false;
	}

	private String getUriPrefix() {
		String uriPrefix = "platform:/resource/"
				+ featureProject.getProjectName() + "/"
				+ featureProject.getProjectSourcePath() + "/";
		return uriPrefix;
	}

	private void handleSourceFiles(IFolder folder) throws CoreException {

		for (IResource res : folder.members()) {

			
		 if (res instanceof IFile) {
				if (res.getName().endsWith(".dj")) {
					updateFile(((IFile) res).getRawLocation().toFile());
					res.refreshLocal(IResource.DEPTH_ZERO, null);
					filename = res.getName();
					sourceFilesAdded = true;
				}
				
			}
		}
	}

	private void updateFile(final File file) {
	
		String newFileText = null;
		String oldFileText = fileToString(file.getAbsolutePath());
		if (isCoreFile(oldFileText)) {
			
			newFileText = getNewFileStringCore(file);

		} else if (isDeltaFile(oldFileText)) {
			
			newFileText = getNewFileStringDelta(file);
		}
		if(!newFileText.equals(oldFileText))
		SaveStringToFile(newFileText, file);
	}

	private String getImportsString(String fileName) {
		IFolder folder = featureProject.getSourceFolder();
		StringBuffer strBuf = new StringBuffer();

		try {
			for (IResource res : folder.members()) {

				if (res instanceof IFile) {
					if (res.getName().endsWith(".dj")
							&& !res.getName().equals(fileName)) {

						strBuf.append("import \"" + res.getName() + "\"\n");

					}

				}
			}
			strBuf.append("\n");
		} catch (CoreException e) {
			DeltajCorePlugin.getDefault().logError(e);
		}

		return strBuf.toString();
	}

	private static Matcher getMatcherFromFileTextCore(String fileText) {
	
		String patternString = "^(.*)features(.*)configurations(.*)core(.*?)\\{(.*)\\}.*$";
		Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
		return pattern.matcher(fileText);
	
	}

	private Matcher getMatcherFromFileTextDelta(String fileText) {
		String patternString = "(.*)delta(.*)";
		Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);

		return pattern.matcher(fileText);

	}

	private void SaveStringToFile(String text, File file) {
		if (text == null || text.equals(""))
			return;
		FileWriter fw = null;
		try {
			fw = new FileWriter(file);
			fw.write(text);

		} catch (IOException e) {
			DeltajCorePlugin.getDefault().logError(e);

		} finally {
			if (fw != null) {
				try {
					fw.close();
				} catch (IOException e) {

					DeltajCorePlugin.getDefault().logError(e);
				}
			}
		}

	}

	private boolean isCoreFile(String fileText) {
		Matcher matcher = getMatcherFromFileTextCore(fileText);
		return matcher.matches();
	}

	private boolean isDeltaFile(String fileText) {
		Matcher matcher = getMatcherFromFileTextDelta(fileText);
		return matcher.matches();
	}

	private String getNewFileStringDelta(File file) {
		String fileString = fileToString(file.getAbsolutePath());
	
		Matcher matcher = getMatcherFromFileTextDelta(fileString);
	
		StringBuffer buf = new StringBuffer(fileString);
		if(matcher.matches())
		buf.replace(matcher.start(1), matcher.end(1), getImportsString(file.getName()));
	
		return buf.toString();
	}

	private String getNewFileStringCore(File file) {
		String fileString = fileToString(file.getAbsolutePath());
		Matcher matcher = getMatcherFromFileTextCore(fileString);
		matcher.matches();
		StringBuffer buf = new StringBuffer(matcher.group(0));
		String configurationString = getConfigurationString(selectedFeatures);
		String features = getFeatureString(featureNames);

		buf.replace(matcher.start(2), matcher.end(2), features + "\n");
		Matcher matcher2 = getMatcherFromFileTextCore(buf.toString());
		matcher2.matches();
		buf.replace(matcher2.start(3), matcher2.end(3), configurationString
				+ "\n");

		buf.replace(0, buf.indexOf("features"), getImportsString(file.getName()));

		return buf.toString();

	}

	private String getFeatureString(Set<String> selectedFeatures) {
		Configuration configuration = new Configuration(
				featureProject.getFeatureModel());
		updateSelectedFeatures(configuration);
		StringBuffer features = new StringBuffer();

		for (String s : selectedFeatures) {
			features.append(" " + s + ",");
		}

		features.deleteCharAt(features.length() - 1);

		return features.toString();
	}

	private String getConfigurationString(Set<String> selectedFeatures) {

		return getFeatureString(selectedFeatures).concat(";");
	}



	private static String fileToString(String filePath) {
		byte[] buffer = new byte[(int) new File(filePath).length()];
		FileInputStream f = null;
		try {
			f = new FileInputStream(filePath);
			f.read(buffer);
		} catch (FileNotFoundException e) {
			DeltajCorePlugin.getDefault().logError(e);

		} catch (IOException e) {
			DeltajCorePlugin.getDefault().logError(e);
		} finally {
			if (f != null)
				try {
					f.close();
				} catch (IOException e) {
					DeltajCorePlugin.getDefault().logError(e);
				}

		}

		return new String(buffer);
	}

	@Override
	public void postCompile(IFile file) {

	}

	@Override
	public String replaceMarker(String text, List<String> list) {

		return text;
	}

	@Override
	public boolean postAddNature(IFolder source, IFolder destination) {
		return false;
	}

	@Override
	public void postModelChanged() {

	}

	@Override
	public boolean hasCustomFilename() {
		return true;
	}

	@Override
	public boolean hasFeatureFolder() {
		return true;
	}

}
