package earth.cube.eclipse.darbuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

public class FileUtils {
	
	public static String readContent(File file) {
		if(!file.exists())
			return null;
		String s;
		try {
			Reader in = new InputStreamReader(new FileInputStream(file), "iso-8859-1");		
			try {
				char[] buf = new char[(int) file.length()];
				int n = in.read(buf);
				s = new String(buf, 0, n);
			}
			finally {
				in.close();
			}
		}
		catch(IOException e) {
			throw new RuntimeException(e);
		}
		return s;
	}
	
	public static void writeContent(File file, String sContent, long nLastModified) throws IOException {
		Writer out = new OutputStreamWriter(new FileOutputStream(file), "iso-8859-1");		
		try {
			out.write(sContent);
		}
		finally {
			out.close();
		}
		if(!file.setLastModified(nLastModified))
			throw new IllegalStateException("Could not set file time!");
	}
	

	public static String getRelativePath(File dir, File file) {
		String sDirPath = dir.getAbsolutePath() + File.separatorChar;
		String sFilePath = file.getAbsolutePath();
		if(!sFilePath.startsWith(sDirPath))
			throw new IllegalArgumentException();
		String sRelPath =  sFilePath.substring(sDirPath.length());
		if(File.separatorChar != '/')
			sRelPath = sRelPath.replace(File.separatorChar, '/');
		return sRelPath;
	}
	
}
