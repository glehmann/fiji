package fiji.pluginManager.logic;

import fiji.pluginManager.ui.MainUserInterface;

import fiji.pluginManager.util.PluginData;

import ij.IJ;

import ij.plugin.PlugIn;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import java.util.Observable;
import java.util.Observer;

import javax.swing.JOptionPane;

/*
 * Start up class of Plugin Manager Application:
 * Facade, Business logic, and overall-in-charge of providing the main user interface the
 * required list of PluginObjects that interface will use for display.
 */
public class PluginManager extends PluginData implements PlugIn, Observer {
	public static final String MAIN_URL = "http://pacific.mpi-cbg.de/uploads/incoming/plugins/";
	//public static final String MAIN_URL = "http://pacific.mpi-cbg.de/update/"; //TODO
	public static final String TXT_FILENAME = "current.txt";
	public static final String XML_LOCK = "db.xml.gz.lock";
	public static final String XML_COMPRESSED = "db.xml.gz";
	public static final String XML_FILENAME = "db.xml";
	public static final String XML_BACKUP = "db.bak";
	public static final String UPDATE_DIRECTORY = "update";

	// Key names for ij.Prefs for saved values ("cookies")
	// Note: ij.Prefs is only saved during shutdown of Fiji
	public static final String PREFS_XMLDATE = "fiji.updater.xmlDate";
	public static final String PREFS_USER = "fiji.updater.login";

	// Track when was file modified (Lock conflict purposes)
	private long xmlLastModified;

	//PluginObjects for output at User Interface
	public PluginCollection pluginCollection;

	//Used for generating Plugin information
	private XMLFileDownloader xmlFileDownloader;
	private PluginListBuilder pluginListBuilder;
	public XMLFileReader xmlFileReader;

	public void run(String arg) {
		try {
			IJ.showStatus("Starting up Plugin Manager...");
			xmlFileDownloader = new XMLFileDownloader(this);
			xmlFileDownloader.addObserver(this);
			xmlFileDownloader.start();
		} catch (Error e) {
			//Interface side: This should handle presentation side of exceptions
			IJ.error("Error: " + e);
		} catch (IOException e) {
			try {
				new File(PluginData.getFijiRootPath()
						+ PluginManager.XML_COMPRESSED)
					.deleteOnExit();
				IJ.error("Download/checksum failed: " + e);
				UpdateFiji updateFiji = new UpdateFiji();
				updateFiji.hasGUI = true;
				updateFiji.exec(UpdateFiji.defaultURL);
			} catch (SecurityException se) {
				IJ.error("Security exception: " + se);
			}
		}
	}

	public long getXMLLastModified() {
		return xmlLastModified;
	}

	//Show progress of startup at IJ bar, directs what actions to take after task is complete.
	public void update(Observable subject, Object arg) {
		try {
			if (subject == xmlFileDownloader) {
				IJ.showStatus("Downloading " + xmlFileDownloader.getTaskname() + "...");
				IJ.showProgress(xmlFileDownloader.getCurrentlyLoaded(),
						xmlFileDownloader.getTotalToLoad());
				//After required files are downloaded, read and retrieve them
				if (xmlFileDownloader.allTasksComplete()) {
					xmlLastModified = xmlFileDownloader.getXMLLastModified();
					xmlFileReader = new XMLFileReader(
							new ByteArrayInputStream(xmlFileDownloader.getXMLFileData()));

					pluginListBuilder = new PluginListBuilder(xmlFileReader, this);
					pluginListBuilder.addObserver(this);
					pluginListBuilder.buildFullPluginList();
				}
			} else if (subject == pluginListBuilder) {
				IJ.showStatus("Checksum " + pluginListBuilder.getTaskname() + "...");
				IJ.showProgress(pluginListBuilder.getCurrentlyLoaded(),
						pluginListBuilder.getTotalToLoad());

				//After plugin list is built successfully, retrieve it and show main interface
				if (pluginListBuilder.allTasksComplete()) {
					IJ.showStatus("");
					pluginCollection = pluginListBuilder.pluginCollection;

					MainUserInterface mainUserInterface = new MainUserInterface(this);
					mainUserInterface.setVisible(true);
				}

			}
		} catch (Throwable e) {
			throw new Error(e.getLocalizedMessage());
		}
	}
}