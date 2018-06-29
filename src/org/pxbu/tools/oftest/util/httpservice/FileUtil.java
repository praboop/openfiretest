package org.pxbu.tools.oftest.util.httpservice;

import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

public class FileUtil {

	/**
	 * Load a file from disk
	 * 
	 * @param file
	 * @return file content
	 */
	public static String loadFile(String file) {
		try {
			return new String(IOUtils.toByteArray(new FileInputStream(file)));
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return "";
	}

}