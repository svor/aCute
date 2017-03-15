/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.acute;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.lsp4e.server.StreamConnectionProvider;

public class OmnisharpStreamConnectionProvider implements StreamConnectionProvider {

	private Process process;

	public OmnisharpStreamConnectionProvider() {
	}

	@Override
	public void start() throws IOException {
		String commandLine = System.getenv("OMNISHARP_LANGUAGE_SERVER_COMMAND");
		String omnisharpLocation = System.getenv("OMNISHARP_LANGUAGE_SERVER_LOCATION");
		if (commandLine != null) {
			String[] command = new String[] {"/bin/bash", "-c", commandLine};
			if (Platform.getOS().equals(Platform.OS_WIN32)) {
				command = new String[] {"cmd", "/c", commandLine};
			}
			this.process = Runtime.getRuntime().exec(command);
		} else if (omnisharpLocation != null) {
			ProcessBuilder builder = new ProcessBuilder(
				getNodeJsLocation().getAbsolutePath(),
				omnisharpLocation);
			process = builder.start();
		} else {
			URL serverFileUrl = getClass().getResource("/server/languageserver/server.js");
			if (serverFileUrl != null) {
				File serverFile = new File(FileLocator.toFileURL(serverFileUrl).getPath());
				if (serverFile.exists()) {
					File nodeJsLocation = getNodeJsLocation();
					if (nodeJsLocation == null || !nodeJsLocation.isFile()) {
						Activator.getDefault().getLog().log(new Status(IStatus.ERROR, Activator.getDefault().getBundle().getSymbolicName(), "Couldn't find nodejs in your machine. Please make sure it's part of your PATH."));
						return;
					}
					ProcessBuilder builder = new ProcessBuilder(
							nodeJsLocation.getAbsolutePath(),
							serverFile.getAbsolutePath());
					Activator.getDefault().getLog().log(new Status(IStatus.INFO, Activator.getDefault().getBundle().getSymbolicName(), "Omnisharp command-line: " + builder.command().toArray()));
					process = builder.start();
					return;
				}
			} else {
				Activator.getDefault().getLog().log(new Status(IStatus.ERROR, Activator.getDefault().getBundle().getSymbolicName(), "Omnisharp not found!\n" +
						"Main issue and remediation: The `org.eclipse.acute.omnisharpServer` fragment is missing. Please add it.\n" +
						"Possible alternative settings:\n" +
						"* set `OMNISHARP_LANGUAGE_SERVER_COMMAND` to the command that should be used to start the server (with full path).\n" +
						"* set `OMNISHARP_LANGUAGE_SERVER_LOCATION` to the full path of thel language server file."));
			}
		}
	}

	private static File getNodeJsLocation() {
		String location = null;
		String[] command = new String[] {"/bin/bash", "-c", "which node"};
		if (Platform.getOS().equals(Platform.OS_WIN32)) {
			command = new String[] {"cmd", "/c", "where node"};
		}
		BufferedReader reader = null;
		try {
			Process p = Runtime.getRuntime().exec(command);
			reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			location = reader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(reader);
		}

		// Try default install path as last resort
		if (location == null && Platform.getOS().equals(Platform.OS_MACOSX)) {
			location = "/usr/local/bin/node";
		}

		if (Files.exists(Paths.get(location))) {
			return new File(location);
		}
		return null;
	}


	@Override
	public InputStream getInputStream() {
		return process.getInputStream();
	}

	@Override
	public OutputStream getOutputStream() {
		return process.getOutputStream();
	}

	@Override
	public void stop() {
		process.destroy();
	}

}
