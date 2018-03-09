package sharpfix.patchgen;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import sharpfix.util.*;
import sharpfix.global.*;
import sharpfix.rename.RenameFactory;

public class CandidateReuse
{
    private static Options options;

    static {
	options = new Options();
	options.addOption("faulocflag", true, "Fault Localization Flag: 0: (Full Fault Localization), 1: (Fault Localization by Package), 2: (Fault Localization by Class), 3: (Fault Localization by Method)");
	options.addOption("repairflag", true, "Repair Flag: 0: (Local Full Repair), 1: (Global Full Repair), 2: (Local & Global Full Repair), 3: (Local Stmt Repair), 4: (Global Stmt Repair), & 5: (Local & Global Stmt Repair)");
	options.addOption("bfpath", true, "Buggy File Path");
	options.addOption("bstmtloc", true, "Buggy Statement Loc");
	options.addOption("cfpath", true, "Candidate File Path");
	options.addOption("cstmtloc", true, "Candidate Statement Loc");
	options.addOption("bugid", true, "Bug ID");
	options.addOption("dependjpath", true, "The Dependency Jar Path");
	options.addOption("projdpath", true, "Faulty Project's Directory Path");
	options.addOption("projsrcdpath", true, "Faulty Project's Source Directory Path");
	options.addOption("projbuilddpath", true, "Faulty Project's Build Directory Path (where the binaries for the source files are saved)");
	options.addOption("projtestbuilddpath", true, "Faulty Project's Test Build Directory Path (where the binaries for the test source files are saved)");
	options.addOption("tsuitefpath", true, "The File Containing the Class Names of All the Test Cases (Separated by Semi-colon)");
	options.addOption("tpackage", true, "The package for fault localization");
	options.addOption("failedtestcases", true, "The Full Class Name of the Failed Test Cases (if more than one exist, put them together connected by colons)");
	options.addOption("outputdpath", true, "Output Directory");
	options.addOption("cockerdpath", true, "Cocker Directory Path");
	options.addOption("sharpfixdpath", true, "sharpfix Directory Path"); 
	options.addOption("maxfaultylines", true, "The Maximum Number of Faulty Lines to be Looked at for Repair");
	options.addOption("maxcandidates", true, "The Maximum Number of Candidate Chunks to be Looked at for Repair");
	options.addOption("analysismethod", true, "The Cocker Search Method");
	options.addOption("faulocfpath", true, "The Path of the Fault Localization Result File");
	options.addOption("faulocaddstacktrace", false, "Use the Stack Trace Information for Fault Localization?");
	options.addOption("usesearchcache", false, "Use Cached Search Result?");
	options.addOption("cignoreflag", true, "Candidate Ignore Flag (0: Ignore Methods by Class Name and Method Signature; 1: Ignore Methods by Class Name only; 2: Ignore Methods by Method Signature Only).");
	options.addOption("useextendedcodebase", false, "Use Extended Code Database (including Manually Retrieved Projects from GitHub)?");
	options.addOption("runparallel", false, "Run in parallel?");
	options.addOption("deletefailedpatches", false, "Delete Failed Patches?");
	options.addOption("paralleltesting", true, "The number of patches generated by a candidate that will be tested in parallel.");
	options.addOption("maxvalidatepatch", true, "The max number of patches generated by a candidate for validation.");
	options.addOption("maxglobalstatement", true, "The number of statements in a globally-found candidate that will be looked at for repair.");
	options.addOption("trialid", true, "The trial id.");
    }

    public static void main(String[] args) {
	CommandLineParser clparser = new DefaultParser();
	CommandLine cmd_line = null;
	try { cmd_line = clparser.parse(options, args); }
	catch (ParseException exp) {
	    System.err.println("CommandLine Parsing Failed: " + exp);
	}
	if (cmd_line == null) { return; }

	RepairInfo ri = new RepairInfo();
	int faulocflag = -1;
	int repairflag = -1;
	int cignoreflag = -1;
	String bfpath=null, bstmtloc=null, cfpath=null, cstmtloc=null, bugid=null, outputdpath=null, projdpath=null, projsrcdpath=null, sharpfixdpath=null, projtestbuilddpath=null, tsuitefpath=null;

	if (cmd_line.hasOption("faulocflag")) {
	    String value = cmd_line.getOptionValue("faulocflag");
	    System.out.println("Fault Localization Flag: 0: (Full Fault Localization), 1: (Fault Localization by Package), 2: (Fault Localization by Class), 3: (Fault Localization by Method)" + value);
	    faulocflag = Integer.parseInt(value);
	    ri.faulocflag = faulocflag;
	}
	else {
	    System.out.println("Fault Localization Flag: 0: (Full Fault Localization), 1: (Fault Localization by Package), 2: (Fault Localization by Class), 3: (Fault Localization by Method)" + ri.faulocflag);
	}
	
	if (cmd_line.hasOption("repairflag")) {
	    String value = cmd_line.getOptionValue("repairflag");
	    System.out.println("Repair Flag (-1: Invalid, 0: Local Full Repair, 1: Global Full Repair, 2: Local+Global Full Repair, 3: Local Stmt Repair, 4: Global Stmt Repair, 5: Local+Global Stmt Repair)): " + value);
	    repairflag = Integer.parseInt(value);
	    ri.repairflag = repairflag;
	}
	else {
	    System.out.println("Repair Flag (-1: Invalid, 0: Local Full Repair, 1: Global Full Repair, 2: Local+Global Full Repair, 3: Local Stmt Repair, 4: Global Stmt Repair, 5: Local+Global Stmt Repair)): " + ri.repairflag);
	}

	if (cmd_line.hasOption("bfpath")) {
	    String value = cmd_line.getOptionValue("bfpath");
	    System.out.println("Buggy File Path: " + value);
	    bfpath = value;
	    ri.bfpath = value;
	}
	else {
	    System.out.println("Buggy File Path is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("bstmtloc")) {
	    String value = cmd_line.getOptionValue("bstmtloc");
	    System.out.println("Buggy Statement Loc: " + value);
	    bstmtloc = value;
	    ri.bstmtloc = value;
	}
	else {
	    System.out.println("Buggy Statement Loc is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("cfpath")) {
	    String value = cmd_line.getOptionValue("cfpath");
	    System.out.println("Candidate File Path: " + value);
	    cfpath = value;
	}
	else {
	    System.out.println("Candidate File Path is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("cstmtloc")) { //Either a statement or a method
	    String value = cmd_line.getOptionValue("cstmtloc");
	    System.out.println("Candidate Statement Loc: " + value);
	    cstmtloc = value;
	}
	else {
	    System.out.println("Candidate Statement Loc is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("bugid")) {
	    String value = cmd_line.getOptionValue("bugid");
	    System.out.println("Bug ID: " + value);
	    ri.bugid = value;
	    bugid = value;
	}
	else {
	    System.out.println("Bug ID is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("dependjpath")) {
	    String value = cmd_line.getOptionValue("dependjpath");
	    System.out.println("Dependency Jar Path: " + value);
	    ri.dependjpath = value;
	}
	else {
	    System.out.println("Dependency Jar Path is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("projdpath")) {
	    String value = cmd_line.getOptionValue("projdpath");
	    System.out.println("Project Directory Path: " + value);
	    ri.projdpath = value;
	    projdpath = value;	    
	}
	else {
	    System.out.println("Project Directory Path is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("projsrcdpath")) {
	    String value = cmd_line.getOptionValue("projsrcdpath");
	    System.out.println("Project Source Directory Path: " + value);
	    ri.projsrcdpath = value;
	    projsrcdpath = value;
	}
	else {
	    System.out.println("Project Source Directory Path is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("projbuilddpath")) {
	    String value = cmd_line.getOptionValue("projbuilddpath");
	    System.out.println("Project Build Directory Path: " + value);
	    ri.projbuilddpath = value;
	}
	else {
	    System.out.println("Project Build Directory Path is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("projtestbuilddpath")) {
	    projtestbuilddpath = cmd_line.getOptionValue("projtestbuilddpath");
	    System.out.println("Project Test Build Directory Path: " + projtestbuilddpath);
	    ri.projtestbuilddpath = projtestbuilddpath;
	}
	else {
	    System.out.println("Project Test Build Directory Path is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("failedtestcases")) {
	    String value = cmd_line.getOptionValue("failedtestcases");
	    System.out.println("The Full Class Name of the Failed Test Case(s): " + value);
	    ri.failedtestcases = value;
	}
	else {
	    System.out.println("The Full Class Name of the Failed Test Case(s): " + ri.failedtestcases);
	    return;
	}
	
	if (cmd_line.hasOption("outputdpath")) {
	    String value = cmd_line.getOptionValue("outputdpath");
	    System.out.println("Output Directory Path: " + value);
	    outputdpath = value;
	    ri.outputdpath = value;
	}
	else {
	    System.out.println("Output Directory Path is Not Available.");
	    return;
	}

	if (cmd_line.hasOption("cockerdpath")) {
	    String value = cmd_line.getOptionValue("cockerdpath");
	    System.out.println("Cocker Directory Path: " + value);
	    ri.cockerdpath = value;
	}
	else {
	    System.out.println("Cocker Directory Path: " + ri.cockerdpath);
	}

	if (cmd_line.hasOption("sharpfixdpath")) {
	    String value = cmd_line.getOptionValue("sharpfixdpath");
	    System.out.println("sharpfix Directory Path: " + value);
	    ri.sharpfixdpath = value;
	    sharpfixdpath = value;
	}
	else {
	    System.out.println("sharpfix Directory Path is Not Available.");
	    return;
	}	
	
	if (cmd_line.hasOption("maxfaultylines")) {
	    String value = cmd_line.getOptionValue("maxfaultylines");
	    System.out.println("The Maximum Number of Faulty Lines to be Considered for Repair " + value);
	    ri.maxfaultylines = Integer.parseInt(value);
	}
	else {
	    System.out.println("The Maximum Number of Faulty Lines to be Considered for Repair: " + ri.maxfaultylines);
	}

	if (cmd_line.hasOption("maxcandidates")) {
	    String value = cmd_line.getOptionValue("maxcandidates");
	    System.out.println("The Maximum Number of Candidates to be Used for Repair: " + value);
	    ri.maxcandidates = Integer.parseInt(value);
	}
	else {
	    System.out.println("The Maximum Number of Candidates to be Used for Repair: " + ri.maxcandidates);
	}

	if (cmd_line.hasOption("analysismethod")) {
	    String value = cmd_line.getOptionValue("analysismethod");
	    System.out.println("Cocker Analysis Method: " + value);
	    ri.analysismethod = value;
	}
	else {
	    System.out.println("Cocker Analysis Method: " + ri.analysismethod);
	}

	//If unavailable, sharpfix will do fault localization later.
	if (cmd_line.hasOption("faulocfpath")) {
	    String value = cmd_line.getOptionValue("faulocfpath");
	    System.out.println("The Path of Fault Localization Result File: " + value);
	    ri.faulocfpath = value;
	}
	else {
	    System.out.println("The Path of Fault Localization Result File: " + ri.faulocfpath);
	}

	if (cmd_line.hasOption("usesearchcache")) {
	    System.out.println("Use Search Cache? " + true);
	    ri.usesearchcache = true;
	}
	else {
	    System.out.println("Use Search Cache? " + ri.usesearchcache);
	}

	if (cmd_line.hasOption("cignoreflag")) {
	    String value = cmd_line.getOptionValue("cignoreflag");
	    System.out.println("Candidate Ignore Flag (0: Ignore Methods by Class Name and Method Signature; 1: Ignore Methods by Class Name only; 2: Ignore Methods by Method Signature Only): " + value);
	    ri.cignoreflag = Integer.parseInt(value);
	}
	else {
	    System.out.println("Candidate Ignore Flag (0: Ignore Methods by Class Name and Method Signature; 1: Ignore Methods by Class Name only; 2: Ignore Methods by Method Signature Only): " + ri.cignoreflag);
	}
	
	if (cmd_line.hasOption("useextendedcodebase")) {
	    System.out.println("Use the Extended Code Database? " + true);
	    ri.useextendedcodebase = true;
	}
	else {
	    System.out.println("Use the Extended Code Database? " + ri.useextendedcodebase);
	}
	
	if (cmd_line.hasOption("runparallel")) {
	    System.out.println("Run the Repair in Parallel " + true);
	    ri.runparallel = true;
	}
	else {
	    System.out.println("Run the Repair in Parallel " + ri.runparallel);
	}

	if (cmd_line.hasOption("deletefailedpatches")) {
	    System.out.println("Delete Failed Patches " + true);
	    ri.deletefailedpatches = true;
	}
	else {
	    System.out.println("Delete Failed Patches? " + ri.deletefailedpatches);
	}

	if (cmd_line.hasOption("paralleltesting")) {
	    String value = cmd_line.getOptionValue("paralleltesting");
	    System.out.println("The number of patches generated by a candidate that will be tested in parallel: " + value);
	    ri.paralleltesting = Integer.parseInt(value);
	}
	else {
	    System.out.println("The number of patches generated by a candidate that will be tested in parallel: " + ri.paralleltesting);
	}

	if (cmd_line.hasOption("maxvalidatepatch")) {
	    String value = cmd_line.getOptionValue("maxvalidatepatch");
	    System.out.println("The max number of patches generated by a candidate for validation: " + value);
	    ri.maxvalidatepatch = Integer.parseInt(value);
	}
	else {
	    System.out.println("The max number of patches generated by a candidate for validation: " + ri.maxvalidatepatch);
	}

	if (cmd_line.hasOption("maxglobalstatement")) {
	    String value = cmd_line.getOptionValue("maxglobalstatement");
	    System.out.println("The number of statements in a globally-found candidate that will be looked at for repair: " + value);
	    ri.maxglobalstatement = Integer.parseInt(value);
	}
	else {
	    System.out.println("The number of statements in a globally-found candidate that will be looked at for repair: " + ri.maxglobalstatement);
	}

	String trialid = null;
	if (cmd_line.hasOption("trialid")) {
	    trialid = cmd_line.getOptionValue("trialid");
	    System.out.println("The trial id: " + trialid);
	}
	else {
	    System.out.println("The trial id: " + trialid);
	}

	File outputdir = new File(outputdpath);
	if (!outputdir.exists()) {
	    System.err.println("Output Directory Not Found: " + outputdpath);
	    return;
	}
	File sharpfixdir = new File(sharpfixdpath);
	if (!sharpfixdir.exists()) {
	    System.err.println("sharpfix Directory Not Found: " + sharpfixdpath);
	    return;
	}

	String output_bugid_dpath = null;
	if (trialid == null) { output_bugid_dpath = outputdpath+"/"+bugid; }
	else { output_bugid_dpath = outputdpath+"/"+bugid+"/"+trialid; }
	File output_bugid_dir = new File(output_bugid_dpath);
	if (!output_bugid_dir.exists()) { output_bugid_dir.mkdirs(); }
	ri.outputbugiddpath = output_bugid_dpath;
	
	if (cmd_line.hasOption("tsuitefpath")) {
	    tsuitefpath = cmd_line.getOptionValue("tsuitefpath");
	    System.out.println("Test Suite File Path: " + tsuitefpath);
	    ri.tsuitefpath = tsuitefpath;

	    //Copy the file to the bug's output directory
	    File tsuitef0 = new File(output_bugid_dpath+"/testsuite_classes");
	    if (!tsuitef0.exists()) {
		File tsuitef = new File(tsuitefpath);
		if (tsuitef.exists()) {
		    try { FileUtils.copyFile(tsuitef, tsuitef0); }
		    catch (Throwable t) { System.err.println(t); t.printStackTrace(); }
		}
		else {
		    System.err.println("Test Suite File Unavailable: " + tsuitefpath);
		    return;
		}
	    }
	}
	else {
	    //Generate a test suite file including all the test classes in the binary test directory
	    tsuitefpath = output_bugid_dpath + "/testsuite_classes";
	    File tsuitef = new File(tsuitefpath);
	    if (!tsuitef.exists()) {
		String[] path_convert_cmds = new String[]{
		    "ant", "-f", "pathconverter.xml",
		    "-Dtestbuilddir="+projtestbuilddpath,
		    "-Doutputdir="+output_bugid_dpath,
		    "-Doutputfname=testsuite_classes",
		    "write_tsuitefile"
		};
		CommandExecutor.execute(path_convert_cmds, sharpfixdir);
	    }
	    System.out.println("Test Suite File Path: " + tsuitefpath);
	    ri.tsuitefpath = tsuitefpath;
	}

	System.out.println();
	LocalPatchGenerator lpgen = new LocalPatchGenerator(ri);
	BuggyChunk bchunk = ChunkFactory.getBuggyChunk(bfpath, bstmtloc);
	if (!bchunk.isValid()) {
	    System.err.println("Failed in producing a valid buggy chunk.");
	    return;
	}
	CandidateChunk cchunk = ChunkFactory.getCandidateChunk(cfpath, cstmtloc);
	if (!cchunk.isValid()) {
	    System.err.println("Failed in producing a valid candidate chunk.");
	    return;
	}
	CandidateChunk rcchunk = RenameFactory.getRenamedCandidateChunk(bchunk, cchunk, true);
	if (!rcchunk.isValid()) {
	    System.err.println("Failed in producing a valid, translated candidate chunk.");
	    return;
	}
	
	Patch patch = null;
	Set<String> tested_pset = new HashSet<String>();
	long start_time = System.currentTimeMillis();
	patch = lpgen.generatePatch(bchunk, rcchunk, output_bugid_dpath, tested_pset, true, true);
	long end_time = System.currentTimeMillis();
	if (patch.isCorrect()) {
	    System.out.println("Found Plausible Patch at " + patch.getFilePath());
	    System.out.println("Modification Type: " + patch.getModificationType());
	    System.out.println("Patch Height Size: " + patch.getHeightSize());
	    System.out.println("Patch String Similarity: " + patch.getStringSimilarity());
	}
	System.out.println("Repair execution time: " + (end_time - start_time));
    }
}