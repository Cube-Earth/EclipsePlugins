package earth.cube.eclipse.darbuilder;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class Application implements IApplication {

	@Override
	public Object start(IApplicationContext context) throws Exception {
		context.applicationRunning();

		String[] saArgs = (String[]) context.getArguments().get("application.args");
		Map<String,String> args = new HashMap<>();
		for(int i = 0; i < saArgs.length; i+=2) {
			String sName = saArgs[i];
			if(!sName.startsWith("-"))
				throw new IllegalArgumentException("Malformed parameter name '" + sName + "'!");
			args.put(sName.substring(1), saArgs[i+1]);
		}

		Builder builder = new Builder();
		builder.setProjectsDir(args.get("projectsDir"));
		builder.setOutputDir(args.get("outputDir")); builder.execute();
		return EXIT_OK;
	}

	@Override
	public void stop() {
	}
}
