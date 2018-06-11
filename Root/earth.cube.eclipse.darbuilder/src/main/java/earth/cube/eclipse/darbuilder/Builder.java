package earth.cube.eclipse.darbuilder;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.emc.ide.project.IDmProjectFactory;

public class Builder {
	
	private File _projectsDir;
	private File _outputDir;
	private Set<DmProject> _allProjects = new HashSet<>();
	private List<DmProject> _projects = new ArrayList<>();
	private boolean _bHasCore;
	
	public void setProjectsDir(String sProjectsDir) {
		if(sProjectsDir == null)
			throw new IllegalArgumentException("Projects directory is mandatory!");
		_projectsDir = new File(sProjectsDir);
		if(!_projectsDir.exists())
			throw new IllegalArgumentException("Projects directory does not exist!");
	}
	
	public void setOutputDir(String sOutputDir) {
		if(sOutputDir == null)
			throw new IllegalArgumentException("Output directory is mandatory!");
		_outputDir = new File(sOutputDir);
		if(!_outputDir.exists())
			_outputDir.mkdirs();
	}

	private void determineForeignProjects() throws CoreException {
		for(File file : _projectsDir.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return file.isDirectory() && new File(file, ".project").exists() && !file.getName().equals("");
				}
			}))
		{
			DmProject project = new DmProject(file);
			if(!project.isCore())
				_allProjects.add(project);
		}
	}

	private void determineOwnProjects() throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		for(IProject eclipseProject : workspace.getRoot().getProjects())
		{
			DmProject project = new DmProject(eclipseProject);
			_allProjects.add(project);
			_bHasCore |= project.isCore();
		}
	}
	
	protected String join(Collection<String> c, char d) {
		StringBuilder sb = new StringBuilder();
		boolean bFirst = true;
		for(String s : c) {
			if(bFirst)
				bFirst = false;
			else
				sb.append(d);
			sb.append(s);
		}
		return sb.toString();
	}
	
	private DmProject findSatisfiedProject(List<DmProject> pendingProjects, Set<String> loadedProjects) throws CoreException {
		for(DmProject project : pendingProjects) {
			
			Set<String> expectedDependencies = new HashSet<>(project.getReferencedProjectNames());
			if(expectedDependencies.size() == 0 || !expectedDependencies.retainAll(loadedProjects)) {
				return project;
			}
		}
		return null;
	}
	
	private void reorderProjects() throws CoreException {
		List<DmProject> remaining = new ArrayList<>(_allProjects);
		Set<String> loaded = new HashSet<>();
		_projects = new ArrayList<>();
		
		while(remaining.size() > 0) {
			DmProject project = findSatisfiedProject(remaining, loaded);
			if(project == null)
				throw new IllegalStateException("Project dependencies are inconsistent!");
			if(!project.isCore())
				_projects.add(project);
			remaining.remove(project);
			loaded.add(project.getName());
		}
		
	}
	
	private boolean setWorkspaceAutoBuild(boolean bAutoBuild) throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceDescription description = workspace.getDescription();

		boolean bCurrAutoBuild = description.isAutoBuilding();
		if (bCurrAutoBuild != bAutoBuild) {
			description.setAutoBuilding(bAutoBuild);
			workspace.setDescription(description);
		}

		return bCurrAutoBuild;
	}
	
	private void createCoreProject() throws CoreException {
		IDmProjectFactory.INSTANCE.createRequiredCoreProjects(new NullProgressMonitor());
	}

	public void execute() throws IOException, CoreException {
		_outputDir.mkdirs();
		setWorkspaceAutoBuild(false);

		determineOwnProjects();
		if(!_bHasCore) {
			createCoreProject();
			determineOwnProjects();
			if(!_bHasCore)
				throw new IllegalStateException("Missing core project!");
		}
		determineForeignProjects();
		reorderProjects();

		for(DmProject project : _projects) {
			project.importOrRefresh();
			project.build(_outputDir);
		}

		setWorkspaceAutoBuild(true);
	}

}
