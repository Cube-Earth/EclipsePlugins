package earth.cube.eclipse.headless_builder;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;

public class DmProject {

	private File _projectDir;
	private String _sName;
	private long _nLastModified;
	private IProject _project;
	private IProjectDescription _description;
	private Set<String> _referencedProjectNames = new HashSet<>();
	private boolean _bCore;

	public DmProject(File projectDir) throws CoreException {
		_projectDir = projectDir;
		_sName = projectDir.getName();
		initProject();
	}
	
	
	public DmProject(IProject eclipseProject) throws CoreException {
		_project = eclipseProject;
		_description = _project.getDescription();
		_sName = _description.getName();
		_projectDir = _project.getLocation().toFile();
		_bCore = _project.hasNature("com.emc.ide.project.dmCoreProjectNatureId");
		for(IProject project : _project.getReferencedProjects())
			_referencedProjectNames.add(project.getName());
	}


	private void initProject() throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		File projectFile = new File(_projectDir, ".project");
		_description = workspace.loadProjectDescription(new Path(projectFile.toString()));
		_project = workspace.getRoot().getProject(_description.getName());
		String sContent = FileUtils.readContent(projectFile);
		_bCore = sContent.indexOf("<nature>com.emc.ide.project.dmCoreProjectNatureId</nature>") != -1;
		
		Pattern p = Pattern.compile("<project>(.*)</project>");
		Matcher m = p.matcher(sContent);
		while(m.find()) {
			_referencedProjectNames.add(m.group(1));
		}
		
		p = Pattern.compile("<classpathentry (?:.+ )?kind=\"src\" (?:.+ )?path=\"/(.*)\"/>");
		m = p.matcher(FileUtils.readContent(new File(_projectDir, ".classpath")));
		while(m.find()) {
			_referencedProjectNames.add(m.group(1));
		}
	}

	public boolean isCore() throws CoreException {
		return _bCore;
	}

	public String getName() {
		return _sName;
	}


	public Set<String> getReferencedProjectNames() throws CoreException {
		return Collections.unmodifiableSet(_referencedProjectNames);
	}

	private long getLastModified(File dir) throws IOException {
		long nTime = 0;
		for (File file : dir.listFiles(new FileFilter() {

			@Override
			public boolean accept(File file) {
				String sRelPath = file.isDirectory() ? "" : FileUtils.getRelativePath(_projectDir, file);
				return file.isDirectory() || ((sRelPath.startsWith("Artifacts/") || sRelPath.startsWith("content/")
						|| sRelPath.startsWith("dar/")) && !sRelPath.equals("dar/default.dardef"));
			}
		})) {
			nTime = Math.max(nTime, file.isDirectory() ? getLastModified(file) : file.lastModified());
		}
		return nTime;
	}

	private void determineLastModified() throws IOException {
		long nTime = getLastModified(_projectDir);
		
		File currDarDef = new File(_projectDir, "dar/default.dardef");
		File savedDarDef = new File(_projectDir, "dar/default.dardef.ref");

		if (!savedDarDef.exists())
			FileUtils.writeContent(savedDarDef, FileUtils.readContent(currDarDef), nTime);
		else if (savedDarDef.lastModified() < currDarDef.lastModified() && nTime < currDarDef.lastModified()) {
			String sCurrContent = FileUtils.readContent(currDarDef);
			String sSavedContent = FileUtils.readContent(savedDarDef);
			if (!sCurrContent.equals(sSavedContent)) {
				nTime = currDarDef.lastModified();
				FileUtils.writeContent(savedDarDef, sCurrContent, nTime);
			}
		}

		_nLastModified = nTime;
	}

	public void importOrRefresh() throws IOException, CoreException {
		if (_project.isOpen() == false) {
			_project.create(_description, null);
			_project.open(null);
		} else {
			_project.refreshLocal(IResource.DEPTH_INFINITE, null);
		}

		determineLastModified();
	}

	private void copy(File file, File outputDir) throws IOException {
		if (!file.exists())
			throw new FileNotFoundException(file.getAbsolutePath());
		file.setLastModified(_nLastModified);
		Files.copy(file.toPath(), Paths.get(outputDir.getAbsolutePath(), file.getName()),
				StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
	}

	private void buildReferencedProject(IProject project, IProgressMonitor monitor) throws CoreException {
		for (IProject refProj: project.getReferencedProjects())
			buildReferencedProject(refProj, monitor);
		project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
	}

	
	private void buildProject(IProgressMonitor monitor) {
		IProgressMonitor subMonitor = new SubProgressMonitor(monitor, -1);

		try {
			for (IProject refProj : _project.getReferencedProjects()) {
				buildReferencedProject(refProj, subMonitor);
			}

			_project.build(IncrementalProjectBuilder.CLEAN_BUILD, subMonitor);
			_project.build(IncrementalProjectBuilder.FULL_BUILD, subMonitor);

			for (IMarker marker : _project.findMarkers("org.eclipse.core.resources.problemmarker", true, 2)) {
				if (marker.getAttribute("severity", IMarker.SEVERITY_INFO) == IMarker.SEVERITY_ERROR) {
					throw new IllegalStateException("Build failed for project '" + _sName + "'!");
				}
			}
		} catch (CoreException e) {
			throw new IllegalStateException("Build failed for project '" + _sName + "'!", e);
		}
	}


	public void build(File outputDir) throws IOException, CoreException {
		buildProject(new NullProgressMonitor());

		copy(new File(_projectDir, "bin-dar/" + _sName + ".dar"), outputDir);
		copy(new File(_projectDir, "bin-dar/" + _sName + ".installparam"), outputDir);
	}


}
