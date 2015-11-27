/*
 * Copyright 2013-2014 Veritomyx Inc.
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package com.veritomyx;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.parameters.dialogs.ParameterSetupDialog;
import net.sf.mzmine.util.ExitCode;
import net.sf.opensftp.SftpException;
import net.sf.opensftp.SftpResult;
import net.sf.opensftp.SftpSession;
import net.sf.opensftp.SftpUtil;
import net.sf.opensftp.SftpUtilFactory;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.swing.JOptionPane;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;

import java.util.ArrayList;

import com.veritomyx.PeakInvestigatorInitDialog;
/**
 * This class is used to access the Veritomyx SaaS servers
 * 
 * @author dschmidt
 */
public class PeakInvestigatorSaaS
{
	// Required CLI version (see https://secure.veritomyx.com/interface/API.php)
	public static final String reqVeritomyxCLIVersion = "2.10";

	// return codes from web pages
	public  static final int W_UNDEFINED =  0;
	public  static final int W_INFO      =  1;
	public  static final int W_RUNNING   =  2;
	public  static final int W_DONE      =  3;
	public  static final int W_SFTP      =  4;
	public  static final int W_PREP      =  5;
	public  static final int W_EXCEPTION = -99;
	public  static final int W_ERROR             = -1;	// these are pulled from API.php
	public  static final int W_ERROR_API         = -2;
	public  static final int W_ERROR_LOGIN       = -3;
	public  static final int W_ERROR_PID         = -4;
	public  static final int W_ERROR_SFTP        = -5;
	public  static final int W_ERROR_INPUT       = -6;
	public  static final int W_ERROR_FILE_WRITE  = -7;
	public  static final int W_ERROR_ACTION      = -8;
	public  static final int W_ERROR_PERMISSIONS = -9;
	public  static final int W_ERROR_JOB_CMD     = -10;
	public  static final int W_ERROR_JOB_RESULTS = -11;
	public  static final int W_ERROR_RECORD      = -12;
	public  static final int W_ERROR_INSUFFICIENT_CREDIT = -13;
	public  static final int W_ERROR_VALUE_MBGT_ZERO = -14;
	public  static final int W_ERROR_JOB_NOT_FOUND = -15;
	public  static final int W_ERROR_JOB_NOT_DONE = -16;
	public  static final int W_ERROR_INVALID_MASS = -17;
	public  static final int W_ERROR_INVALID_SLA = -18;
	public  static final int W_ERROR_INVALID_PI_VERSION = -19;
	public  static final int W_ERROR_USER_NOT_FOUND = -20;
	public  static final int W_ERROR_NUM_SCAN_FILES = -21;
	public  static final int W_ERROR_CANNOT_BE_BLACK = -22;

	// page actions
	private static final String JOB_INIT   = "INIT";
	private static final String JOB_SFTP   = "SFTP";
	private static final String JOB_PREP   = "PREP";
	private static final String JOB_RUN    = "RUN";
	private static final String JOB_STATUS = "STATUS";
	private static final String JOB_DONE   = "DELETE";

	private Logger log;
	private String username;
	private String password;
	private int    aid;
	private String jobID;				// name of the job and the scans tar file
	private String dir;
	private String funds;
	private String[] SLAs;
	private String[] PIversions;
	private String   SLA;
	private String   PIversion;

	private String    host;
	private String    sftp_host;
	private String    sftp_user;
	private String    sftp_pw;
	private int	  	  sftp_port;
	private SftpUtil  sftp;
	private String 	  sftp_file;
	
	public  enum 	prep_status_type {PREP_ANALYZING, PREP_READY };
	private prep_status_type prep_status;
	private int  	prep_scan_count;
	private String 	prep_ms_type;
 
	private int    web_result;
	private String web_str;

	/**
	 * Constructor
	 * 
	 * @param reqVersion
	 * @param live
	 */
	public PeakInvestigatorSaaS(boolean live)
	{
		log        = Logger.getLogger(this.getClass().getName());
		log.setLevel(live ? Level.INFO : Level.DEBUG);
		log.info(this.getClass().getName());
		jobID      = null;
		dir        = null;
		host       = live ? "gamma.veritomyx.com/api/" : "test.veritomyx.com";
		sftp_user  = null;
		sftp_pw    = null;
		sftp_port  = 22;
		sftp       = null;
		sftp_file  = null;
		web_result = W_UNDEFINED;
		web_str    = null;
		funds 	   = null;
		SLAs	   = null;
		PIversions = null;
		SLA	   	   = null;
		PIversion  = null;
		
		prep_status     = prep_status_type.PREP_ANALYZING;
		prep_scan_count = 0;
		prep_ms_type    = null;
	}

	/**
	 * Define the JobID
	 *  
	 * @param email
	 * @param passwd
	 * @param account
	 * @param existingJobName
	 * @param scanCount
	 * @return
	 */
	public int init(String email, String passwd, int account, String existingJobName, int scanCount, int minMass, int maxMass)
	{
		jobID    = null;	// if jobID is set, this is a valid job
		username = email;
		password = passwd;
		aid      = account;
		boolean pickup = ((existingJobName != null) && (existingJobName.startsWith("job-") == true));

		// make sure we have access to the Veritomyx Server
		// this also gets the job_id and SFTP credentials
		if (!pickup)
		{
			if (getPage(JOB_INIT, scanCount, minMass, maxMass) != W_INFO)
				return web_result;
			// Ask user which SLA and PIversion
			PeakInvestigatorInitDialog dialog = new PeakInvestigatorInitDialog(MZmineCore
                    .getDesktop().getMainWindow(),
	                SLAs, PIversions);
			if(dialog.getExitCode() == ExitCode.OK) 
			{
				SLA = dialog.getSLA();
				PIversion = dialog.getPIversion();
			}	
			
	        dialog.setVisible(true);
			if (getPage(JOB_SFTP, 0) != W_INFO)
				return web_result;
			if (getPage(JOB_PREP, 0) != W_INFO)
				return web_result;
		}
		else
		{
			// check this job ID
			jobID = existingJobName;
			if (getPage(JOB_INIT, 0) != W_INFO)
			{
				jobID = null;
				return web_result;
			}
			// Ask user which SLA and PIversion
			if (getPage(JOB_SFTP, 0) != W_INFO)
			{
				jobID = null;
				return web_result;
			}
			if (getPage(JOB_PREP, 0) != W_INFO)
			{
				jobID = null;
				return web_result;
			}
			scanCount = 0;	// job pickup has no scan cost
		}

		if (jobID != null)	// we have a valid job
		{
			sftp = SftpUtilFactory.getSftpUtil();
			SftpSession session = openSession();	// open to verify we can
			if (session == null)
			{
				jobID      = null;
				web_str    = "SFTP access not available";
				web_result = W_ERROR_SFTP;
				return web_result;
			}
			closeSession(session);
		}
		return web_result;
	}

	/**
	 * Provide access to some private data
	 * 
	 * @return
	 */
	public String getJobID()            { return jobID; }
	public int    getPageStatus()       { return getPage(JOB_STATUS,     0); }
	public int    getPageRun(int count) { return getPage(JOB_RUN,    count); }
	public int    getPageDone()         { return getPage(JOB_DONE,       0); }
	public String getPageStr()          { return web_str; }
	public int	  getPage(String action, int count) { return getPage(action, count, 0, Integer.MAX_VALUE); }
	
	public String	getFunds()			{ return funds; }
	public String[] getSLAs()			{ return SLAs; }
	public String[] getPIversions()		{ return PIversions; }

	/**
	 * Get the first line of a web page from the Veritomyx server
	 * Puts first line of results into web_results String
	 * 
	 * @param action
	 * @param count
	 * @return int
	 */
	private int getPage(String action, int count, int minMass, int maxMass)
	{
		web_result = W_UNDEFINED;
		web_str    = "";
		if ((action != JOB_INIT) && (action != JOB_SFTP) && (action != JOB_PREP) && (action != JOB_RUN) && (action != JOB_STATUS) && (action != JOB_DONE))
		{
			web_result = W_ERROR_ACTION;
			web_str    = "Invalid action";
			return web_result;
		}

		BufferedReader    in = null;
		HttpURLConnection uc = null;
		try {
			// build the URL with parameters
			String page = "https://" + host; // +
			String params = "Version=" + reqVeritomyxCLIVersion + // online CLI version that matches this interface
					"&User="	+ URLEncoder.encode(username, "UTF-8") + 
					"&Code="    + URLEncoder.encode(password, "UTF-8") + 
					"&Action="  + action;
			
			if ((action == JOB_INIT) && (jobID == null))	// new job request
			{
				params += "&ID=" + aid +
						"&ScanCount=" + count +
				//		",\"CalibrationCount\": " + calibrationCount + "\"" +
						"&MinMass=" + minMass +
						"&MaxMass=" + maxMass;
			}
			else if (action == JOB_PREP)
			{
				params += "&ID=" + jobID +
						"&File=" + sftp_file;
			}
			else if (action == JOB_RUN)
			{
				params += "&ID=" + jobID +
						"&InputFile=" + sftp_file +
						"&SLA=" + SLA +
						"&PIVersion=" + PIversion;
				// TODO:  Add CalibrationFile when available
			}
			else if (jobID != null)	// all the rest require a jobID
			{
				params += "&ID=" + jobID;
			}
			else
			{
				web_result = W_ERROR_JOB_CMD;
				web_str    = "Job ID, " + jobID + ", not defined";
				return web_result;
			}
			log.debug(page);
			log.debug(params);

			URL url = new URL(page);
			uc = (HttpURLConnection)url.openConnection();
			uc.setUseCaches(false);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Content-Type", 
			           "application/x-www-form-urlencoded");
			uc.setRequestProperty("Content-Length", "" + 
			               Integer.toString(params.getBytes().length));
			uc.setRequestProperty("Content-Language", "en-US");  
			uc.setReadTimeout(240 * 1000);	// give it 4 minutes to respond
			System.setProperty("java.net.preferIPv4Stack", "true");	// without this we get exception in getInputStream
			uc.setDoInput(true);
			uc.setDoOutput(true);
			
			//Send request
			OutputStream os = uc.getOutputStream();
			BufferedWriter writer = new BufferedWriter(
			        new OutputStreamWriter(os, "UTF-8"));
			writer.write(params);
			writer.flush();
			writer.close();
			os.close();
			
			uc.connect();

			// Read the response from the HTTP server
			in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			String line;
			StringBuilder builder = new StringBuilder();
			while ((line = in.readLine()) != null)
			{
				builder.append(line);
			}

			String decodedString = builder.toString();

			log.debug(decodedString);
			if (web_result == W_UNDEFINED) {
				web_str = decodedString;
				JSONParser parser = new JSONParser();
				JSONObject obj = new JSONObject();
				try {
					obj = (JSONObject) parser.parse(web_str);
				} catch (ParseException pe) {

					log.error("JSON Parse Error at position: "
							+ pe.getPosition());
					log.error(pe);
				}
				
				if(!obj.containsKey("Error")) {

				String cmd = (String)obj.get("Action");
				if (cmd.equals("INIT")) {
					web_result = W_INFO;
					aid = (Integer) obj.get("Job");
					// jobID = obj.get("Job"));
					funds = (String) obj.get("Funds");
					// JSON Version 
					/* JSONArray rtos  = (JSONArray)obj.get("RTOs");
					 * SLAs  = new String[rtos.size()];
					 * int r = 0;
					 * for(r = 0; r < rtos.size(); r++) {
					 *	SLAs[r] = rtos.get(r).toString();
					 * }
					 * PIversions = ((String)obj.get("PI_versions")).split(",");
					 * JSONArray pis  = (JSONArray)obj.get("PI_versions");
					 * PIversions  = new String[pis.size()];
					 * int p = 0;
					 * for(p = 0; p < pis.size(); p++) {
					 *	PIversions[p] = pis.get(p).toString();
					 * }
					 */
					// CSV Version
					String rtos = (String)obj.get("RTOs");
					SLAs = rtos.split(",");
					String pis = (String)obj.get("PI_versions");
					PIversions = pis.split(",");
				} else if (cmd.equals("SFTP")) {
					web_result = W_SFTP;
					sftp_host = (String) obj.get("Host");
					sftp_port = (Integer) obj.get("Port");
					dir = (String) obj.get("Directory");
					sftp_user = (String) obj.get("Login");
					sftp_pw = (String) obj.get("Password");
				} else if (cmd.equals("PREP")) {
					web_result = W_PREP;

					if (obj.get("Status") == "Ready") {
						prep_status = prep_status_type.PREP_READY;
					} else if (obj.get("Analysis") == "Ready") {
						prep_status = prep_status_type.PREP_ANALYZING;
					} else // error
					{
						JOptionPane
								.showMessageDialog(
										MZmineCore.getDesktop().getMainWindow(),
										"Peak Investigator Saas returned an error in the PREP phase.",
										MZmineCore.MZmineName,
										JOptionPane.ERROR_MESSAGE);
					}
					prep_scan_count = (Integer) obj.get("ScanCount");
					prep_ms_type = (String) obj.get("MSType");

					if (prep_scan_count != count) {
						JOptionPane
								.showMessageDialog(
										MZmineCore.getDesktop().getMainWindow(),
										"Peak Investigator Saas returned a Scan Count that does not match the Scan Count determined by MZMine.  Do you want to continue submitting the job?",
										MZmineCore.MZmineName,
										JOptionPane.QUESTION_MESSAGE);
					}
				} else if (cmd.equals("Running"))
					web_result = W_RUNNING;
				else if (cmd.equals("Deleted"))
					web_result = W_DONE;
				} else {
					long err = (long)obj.get("Error"); // "Error":#
					web_result = -(int)err;
				}
			}
		}
		catch (Exception e)
		{
			log.error(e.getMessage());
			web_result = W_EXCEPTION;
			web_str    = "Web exception - Unabled to connect to server";
		}
		try { in.close();      } catch (Exception e) { }
		try { uc.disconnect(); } catch (Exception e) { }
		log.debug("Web results: [" + web_result + "] '" + web_str + "'");
		return web_result;
	}

	/**
	 * Open the SFTP session
	 * 
	 * @return boolean
	 */
	private SftpSession openSession()
	{
		SftpSession session;
		try {
			session = sftp.connectByPasswdAuth(host, sftp_port, sftp_user, sftp_pw, SftpUtil.STRICT_HOST_KEY_CHECKING_OPTION_NO, 3000);
		} catch (SftpException e) {
			session = null;
			web_result = W_ERROR_SFTP;
			web_str    = "Cannot connect to SFTP server " + sftp_user + "@" + host;
			return null;
		}
		dir = "accounts/" + aid;
		SftpResult result = sftp.cd(session, dir);	// cd into the account directory
		if (!result.getSuccessFlag())
		{
			result = sftp.mkdir(session, dir);
			if (!result.getSuccessFlag())
			{
				web_result = W_ERROR_SFTP;
				web_str    = "Cannot create remote directory, " + dir;
				return null;
			}
			sftp.chmod(session, 0770, dir);
			result = sftp.cd(session, dir);
			result = sftp.mkdir(session, "batches");
			if (!result.getSuccessFlag())
			{
				web_result = W_ERROR_SFTP;
				web_str    = "Cannot create remote directory, " + dir + "/batches";
				return null;
			}
			result = sftp.mkdir(session, "results");
			if (!result.getSuccessFlag())
			{
				web_result = W_ERROR_SFTP;
				web_str    = "Cannot create remote directory, " + dir + "/results";
				return null;
			}
			sftp.chmod(session, 0770, "batches");
			sftp.chmod(session, 0770, "results");
		}
		return session;
	}

	/**
	 * Close the SFTP session
	 * Call this when we are closing the instance
	 */
	private void closeSession(SftpSession session)
	{
		if ((sftp != null) && (session != null))
			sftp.disconnect(session);
	}

	/**
	 * Transfer the given file to SFTP drop
	 * 
	 * @param fname
	 */
	public boolean putFile(String fname)
	{
		SftpResult result;
		log.info("Transmit " + sftp_user + "@" + host + ":" + dir + "/" + fname);
		SftpSession session = openSession();
		if (session == null)
			return false;

		sftp.cd(session, "batches");
		try { sftp.rm(session, fname); } catch (Exception e) {}
		try { sftp.rm(session, fname + ".filepart"); } catch (Exception e) {}
		result = sftp.put(session, fname, fname + ".filepart");
		sftp.cd(session, "..");
		if (!result.getSuccessFlag())
		{
			closeSession(session);
			web_result = W_ERROR_SFTP;
			web_str    = "Cannot write file: " + fname;
			return false;
		}
		else
		{
			sftp.cd(session, "batches");
			result = sftp.rename(session, fname + ".filepart", fname); //rename a remote file
			sftp.cd(session, "..");
			if (!result.getSuccessFlag())
			{
				closeSession(session);
				web_result = W_ERROR_SFTP;
				web_str    = "Cannot rename file: " + fname;
				return false;
			}
		}
		closeSession(session);
		return true;
	}

	/**
	 * Transfer the given file from SFTP drop
	 * 
	 * @param fname
	 */
	public boolean getFile(String fname)
	{
		SftpResult result;
		log.info("Retrieve " + sftp_user + "@" + host + ":" + dir + "/" + fname);
		SftpSession session = openSession();
		if (session == null)
			return false;

		sftp.cd(session, "results");
		result = sftp.get(session, fname);
		if (!result.getSuccessFlag())
		{
			sftp.cd(session, "..");
			closeSession(session);
			web_result = W_ERROR_SFTP;
			web_str    = "Cannot read file: " + fname;
			return false;
		}
		sftp.rm(session, fname);
		sftp.cd(session, "..");
		closeSession(session);
		return true;
	}
}
