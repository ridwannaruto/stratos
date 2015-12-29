package org.apache.stratos.cep.extension;

import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngine;
import org.rosuda.REngine.REngineException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;


public class JRIConnection {
	private static volatile REngine rConection;
	private static final Object lock = new Object();

	private JRIConnection() {

	}

	/**
	 * Getting a single connection to R engine spawned for this JVM
	 *
	 * Important : only single Rengine instance can be created for single JVM instance.
	 * Singleton object creation need to be synchronised since multiple threads will access this method simultaneously
	 *
	 * @return REngine : connection to R-engine
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 * @throws IOException
	 * @throws REXPMismatchException
	 * @throws REngineException
	 */
	public static REngine getConnection()
			throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
			       IllegalAccessException, IOException, REXPMismatchException, REngineException {
		REngine r = rConection;
		if (r == null) {
			String[] Rargs = ExtensionConstants.R_ARGS;
			synchronized (lock) {
				r = rConection;
				if (r == null) {
					r = REngine.engineForClass(ExtensionConstants.JRI_ENGINE, Rargs, null,false);
					String script = readScript(ExtensionConstants.MODEL_PATH);
					r.parseAndEval(script);
					rConection = r;
				}
			}
		}
		return r;
	}

	/**
	 * Read the R script which contains the code for generating Predictive model
	 *
	 * @param fileName : name of the R-script
	 * @return
	 * @throws IOException
	 */
	public static String readScript(String fileName) throws IOException {
		InputStream in = null;
		BufferedReader reader = null;
		StringBuilder out = new StringBuilder();
		try {
			//
			in = JRIConnection.class.getResourceAsStream(fileName);
			reader = new BufferedReader(new InputStreamReader(in));
			String line;
			while ((line = reader.readLine()) != null) {
				out.append(line).append("\n");
			}
			return out.toString();
		} catch (IOException e) {
			throw e;
		} finally {
			if (reader != null)
				reader.close();
			if (in != null)
				in.close();
		}
	}

	/**
	 * Close the connection upon the deletion of this object
	 * @throws Throwable
	 */
	@Override protected void finalize() throws Throwable {
		super.finalize();
		rConection.close();
	}
}
